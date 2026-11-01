package com.xenon.qldapm.service;

import com.xenon.qldapm.config.SePayConfig;
import com.xenon.qldapm.dto.PosDtos.HoaDonResponse;
import com.xenon.qldapm.entity.HoaDon;
import com.xenon.qldapm.exception.BusinessException;
import com.xenon.qldapm.exception.NotFoundException;
import com.xenon.qldapm.repository.HoaDonRepository;
import com.xenon.qldapm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SePayService {
    private final SePayConfig config;
    private final JdbcTemplate jdbc;
    private final HoaDonRepository invoices;
    private final PosService pos;
    private final InventoryCountGuard inventory;
    private final SePayGuard guard;
    private final PlatformTransactionManager transactions;
    private static final Pattern CODE=Pattern.compile("(?i)(?<![A-Z0-9])QLD[0-9]{8}(?![A-Z0-9])");
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record Settings(boolean enabled,boolean testMode) {}
    public record Payment(Integer id,Integer invoiceId,String reference,BigDecimal amount,String status,
            String bank,String accountNumber,String accountName,boolean testMode,LocalDateTime expiresAt,
            long remainingSeconds,String qrUrl,String note,boolean retryAllowed,BigDecimal receivedAmount) {}
    private record Attempt(int id,int invoiceId,String reference,BigDecimal amount,String snapshot,String bank,
            String account,boolean testMode,String status,LocalDateTime created,LocalDateTime expires,String note,Long eventId) {}
    private record Event(long id,String reference,String bank,String account,BigDecimal amount,String direction,
            LocalDateTime date,String status,String note) {}

    public Settings settings(){return new Settings(config.isEnabled(),config.isTestMode());}

    public Payment latest(Integer invoiceId) {
        return tx(()->{ own(invoiceId); return latestAttempt(invoiceId).map(this::response).orElse(null); });
    }

    public Payment create(Integer invoiceId) {
        requireEnabled();
        return tx(()->{
            inventory.stockMovement();
            HoaDon invoice=own(invoiceId);
            Optional<Attempt> existing=latestAttempt(invoiceId);
            if(existing.isPresent() && (existing.get().status.equals("REVIEW") || pending(existing.get())))
                return response(existing.get());
            if(!"dang_lap".equals(invoice.getTrangThai())) throw BusinessException.conflict("HOA_DON_SAI_TRANG_THAI","Chỉ tạo QR cho hóa đơn đang lập.");
            guard.editable(invoiceId);
            HoaDonResponse quote=pos.dinhGia(invoiceId);
            if(quote.dongs().isEmpty()) throw BusinessException.badRequest("HOA_DON_TRONG","Hóa đơn chưa có hàng.");
            BigDecimal amount=quote.tongThanhToan().setScale(0,java.math.RoundingMode.UNNECESSARY);
            // Check frozen/out-of-stock products before showing a payable QR. Stock is rechecked at settlement.
            for(var line:quote.dongs()) {
                inventory.checkProduct(line.maSanPham(),"hoa_don",invoiceId);
                Integer stock=jdbc.queryForObject("SELECT SLTon FROM TonKho WHERE MaSanPham=?",Integer.class,line.maSanPham());
                int needed=quote.dongs().stream().filter(d->d.maSanPham().equals(line.maSanPham())).mapToInt(d->d.soLuong()).sum();
                if(stock==null || stock<needed) throw BusinessException.conflict("TON_KHO_KHONG_DU","Không đủ tồn kho để tạo QR.");
            }
            Integer id=jdbc.queryForObject("""
                INSERT SePayAttempt(InvoiceId,Amount,Snapshot,Bank,AccountNumber,TestMode,ExpiresAt)
                OUTPUT inserted.Id VALUES(?,?,?,?,?,?,?)
                """,Integer.class,invoiceId,amount,SePayFingerprint.of(quote),config.getBank(),config.getAccount(),
                    config.isTestMode(),LocalDateTime.now().plusMinutes(config.getExpireMinutes()));
            return response(attempt(id));
        });
    }

    public Payment cancel(Integer invoiceId,Integer attemptId) {
        return tx(()->{
            own(invoiceId);
            Attempt a=attempt(attemptId);
            if(a.invoiceId!=invoiceId) throw new NotFoundException("Không tìm thấy QR của hóa đơn.");
            if(a.status.equals("REVIEW") || received(a).signum()>0)
                throw BusinessException.conflict("SEPAY_CAN_DOI_SOAT","Đã có tiền vào; không thể hủy. Cần đối soát giao dịch.");
            if(a.status.equals("PENDING")) jdbc.update("UPDATE SePayAttempt SET Status='CANCELLED',Note=N'QR đã hủy tại quầy; không chuyển tiền vào mã này.' WHERE Id=?",a.id);
            return response(attempt(a.id));
        });
    }

    public Payment retry(Integer invoiceId,Integer attemptId) {
        Attempt a=tx(()->{
            own(invoiceId);
            Attempt found=attempt(attemptId);
            if(found.invoiceId!=invoiceId) throw new NotFoundException("Không tìm thấy QR của hóa đơn.");
            if(!retryAllowed(found)) throw BusinessException.conflict("SEPAY_CAN_DOI_SOAT","Giao dịch cần đối soát thủ công; không thể tự chốt lại.");
            return found;
        });
        settle(a.eventId,true);
        return latest(invoiceId);
    }

    /** Persist the authenticated notification before settlement, so a stock/price failure never loses a transfer. */
    public void receive(byte[] body,String timestamp,String signature) {
        requireEnabled();
        if(!SePaySignature.valid(config.getSecret(),timestamp,signature,body,Instant.now()))
            throw new BusinessException(HttpStatus.UNAUTHORIZED,"SEPAY_SIGNATURE","Chữ ký SePay không hợp lệ hoặc đã hết hạn.");
        Event incoming=parse(body);
        tx(()->{
            eventLock(incoming.id);
            Optional<Event> previous=findEvent(incoming.id);
            if(previous.isPresent()) {
                Event e=previous.get();
                if(!Objects.equals(e.reference,incoming.reference) || !e.bank.equals(incoming.bank)
                        || !e.account.equals(incoming.account) || e.amount.compareTo(incoming.amount)!=0
                        || !e.direction.equals(incoming.direction) || !e.date.equals(incoming.date))
                    throw BusinessException.conflict("SEPAY_ID_CONFLICT","ID giao dịch đã tồn tại với dữ liệu khác.");
            } else jdbc.update("INSERT SePayEvent(Id,Reference,Bank,AccountNumber,Amount,TransferType,TransactionAt) VALUES(?,?,?,?,?,?,?)",
                    incoming.id,incoming.reference,incoming.bank,incoming.account,incoming.amount,incoming.direction,incoming.date);
            return null;
        });
        settle(incoming.id,false);
    }

    private void settle(long eventId,boolean retry) {
        try {
            tx(()->{
                inventory.stockMovement();
                Event initial=findEvent(eventId).orElseThrow();
                var candidates=jdbc.query("SELECT * FROM SePayAttempt WHERE Reference=?",this::mapAttempt,initial.reference);
                if(candidates.isEmpty()) {
                    eventLock(eventId);
                    jdbc.update("UPDATE SePayEvent SET Status='IGNORED',Note=N'Không có mã QR tương ứng.' WHERE Id=? AND Status='RECEIVED'",eventId);
                    return null;
                }
                Attempt first=candidates.getFirst();
                invoices.findByIdForUpdate(first.invoiceId).orElseThrow();
                eventLock(eventId);
                Event event=findEvent(eventId).orElseThrow();
                Attempt a=attempt(first.id);
                if(!event.status.equals("RECEIVED") && !(retry && event.status.equals("REVIEW") && retryAllowed(a))) return null;
                if(!event.direction.equals("in") || !event.account.equals(a.account) || !event.bank.equalsIgnoreCase(a.bank)) {
                    jdbc.update("UPDATE SePayEvent SET Status='IGNORED',Note=N'Không khớp tài khoản/ngân hàng nhận hoặc không phải tiền vào.' WHERE Id=?",eventId);
                    return null;
                }
                BigDecimal received=received(a);
                String issue=null;
                if(received.compareTo(a.amount)!=0) issue="Số tiền nhận không đúng số phải thu. Cần đối soát; không tự cộng nhiều lần chuyển.";
                else if(!retry && (!a.status.equals("PENDING") || !LocalDateTime.now().isBefore(a.expires)))
                    issue="Tiền đến khi QR đã hủy/hết hạn hoặc hóa đơn đã xử lý. Cần đối soát trước khi giao hàng.";
                else if(event.date.isBefore(a.created.minusMinutes(1)) || event.date.isAfter(LocalDateTime.now().plusMinutes(5)))
                    issue="Thời gian giao dịch không phù hợp với QR. Cần đối soát.";
                if(issue!=null) { review(a.id,eventId,issue); return null; }
                pos.thanhToanSePay(a.invoiceId,a.amount,"SEPAY-"+event.id,a.snapshot);
                jdbc.update("UPDATE SePayAttempt SET Status='PAID',Note=N'Đã xác minh SePay và chốt hóa đơn.',SettledEventId=? WHERE Id=?",eventId,a.id);
                jdbc.update("UPDATE SePayEvent SET Status='SETTLED',Note=NULL WHERE Id=?",eventId);
                return null;
            });
        } catch(RuntimeException failure) {
            // A separate transaction records the failure after the sales transaction has fully rolled back.
            tx(()->{
                Event event=findEvent(eventId).orElseThrow();
                var rows=jdbc.query("SELECT * FROM SePayAttempt WHERE Reference=?",this::mapAttempt,event.reference);
                if(rows.isEmpty()) throw failure;
                Attempt a=rows.getFirst();
                invoices.findByIdForUpdate(a.invoiceId).orElseThrow();
                eventLock(eventId);
                Event current=findEvent(eventId).orElseThrow();
                if(current.status.equals("SETTLED") || current.status.equals("IGNORED")) return null;
                String reason=failure instanceof BusinessException ? failure.getMessage() : "Chưa thể chốt kho/hóa đơn; kiểm tra lại trước khi giao hàng.";
                review(a.id,eventId,"SETTLEMENT_FAILED: "+reason);
                return null;
            });
        }
    }

    private Event parse(byte[] body) {
        try {
            JsonNode node=JsonMapper.builder().build().readTree(body);
            long id=node.path("id").asLong(0);
            String bank=node.path("gateway").asString("");
            String account=node.path("accountNumber").asString("");
            String direction=node.path("transferType").asString("");
            BigDecimal amount=new BigDecimal(node.path("transferAmount").asText());
            if(id<=0 || !bank.matches("[A-Za-z0-9-]{2,40}") || !account.matches("[0-9]{6,40}")
                    || !Set.of("in","out").contains(direction) || amount.signum()<=0 || amount.scale()>0
                    || amount.precision()>16) throw new IllegalArgumentException();
            String code=node.path("code").asString("");
            if(!CODE.matcher(code).matches()) {
                var matcher=CODE.matcher(node.path("content").asString(""));
                code=matcher.find()?matcher.group():null;
                if(matcher.find()) throw new IllegalArgumentException(); // ambiguous payment descriptions
            }
            return new Event(id,code==null?null:code.toUpperCase(Locale.ROOT),bank,account,amount,direction,
                    LocalDateTime.parse(node.path("transactionDate").asString(""),DATE),"RECEIVED",null);
        } catch(Exception e){throw BusinessException.badRequest("SEPAY_PAYLOAD","Dữ liệu giao dịch SePay không hợp lệ.");}
    }

    private HoaDon own(Integer id) {
        HoaDon h=invoices.findByIdForUpdate(id).orElseThrow(()->new NotFoundException("Không tìm thấy hóa đơn."));
        if(!h.getThuNgan().getMaNhanVien().equals(CurrentUser.require().getMaNhanVien()))
            throw new AccessDeniedException("Hóa đơn thuộc thu ngân khác.");
        return h;
    }
    private Optional<Attempt> latestAttempt(Integer id) {
        return jdbc.query("SELECT TOP(1) * FROM SePayAttempt WHERE InvoiceId=? ORDER BY CASE WHEN Status='REVIEW' THEN 0 ELSE 1 END,Id DESC",this::mapAttempt,id).stream().findFirst();
    }
    private Attempt attempt(Integer id){return jdbc.query("SELECT * FROM SePayAttempt WHERE Id=?",this::mapAttempt,id).stream().findFirst().orElseThrow(()->new NotFoundException("Không tìm thấy QR."));}
    private Attempt mapAttempt(ResultSet r,int row) throws SQLException {
        return new Attempt(r.getInt("Id"),r.getInt("InvoiceId"),r.getString("Reference"),r.getBigDecimal("Amount"),r.getString("Snapshot"),
                r.getString("Bank"),r.getString("AccountNumber"),r.getBoolean("TestMode"),r.getString("Status"),
                r.getTimestamp("CreatedAt").toLocalDateTime(),r.getTimestamp("ExpiresAt").toLocalDateTime(),r.getString("Note"),r.getObject("SettledEventId",Long.class));
    }
    private Optional<Event> findEvent(long id) {
        return jdbc.query("SELECT * FROM SePayEvent WHERE Id=?",(r,n)->new Event(r.getLong("Id"),r.getString("Reference"),r.getString("Bank"),r.getString("AccountNumber"),
                r.getBigDecimal("Amount"),r.getString("TransferType"),r.getTimestamp("TransactionAt").toLocalDateTime(),r.getString("Status"),r.getString("Note")),id).stream().findFirst();
    }
    private BigDecimal received(Attempt a) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(Amount),0) FROM SePayEvent WHERE Reference=? AND AccountNumber=? AND Bank=? AND TransferType='in' AND Status<>'IGNORED'",
                BigDecimal.class,a.reference,a.account,a.bank);
    }
    private boolean pending(Attempt a){return a.status.equals("PENDING") && LocalDateTime.now().isBefore(a.expires);}
    private boolean retryAllowed(Attempt a){return a.status.equals("REVIEW") && a.eventId!=null && a.note!=null && a.note.startsWith("SETTLEMENT_FAILED:") && received(a).compareTo(a.amount)==0;}
    private Payment response(Attempt a) {
        String status=a.status.equals("PENDING") && !pending(a)?"EXPIRED":a.status;
        String qr="https://vietqr.app/img?acc="+enc(a.account)+"&bank="+enc(a.bank)+"&amount="+a.amount.toBigIntegerExact()+"&des="+a.reference;
        return new Payment(a.id,a.invoiceId,a.reference,a.amount,status,a.bank,a.account,config.getAccountName(),a.testMode,a.expires,
                Math.max(0,Duration.between(LocalDateTime.now(),a.expires).getSeconds()),status.equals("PENDING")?qr:null,
                a.note,retryAllowed(a),received(a));
    }
    private void review(int attemptId,long eventId,String note) {
        String safe=note.substring(0,Math.min(note.length(),500));
        jdbc.update("UPDATE SePayAttempt SET Status='REVIEW',Note=?,SettledEventId=? WHERE Id=?",safe,eventId,attemptId);
        jdbc.update("UPDATE SePayEvent SET Status='REVIEW',Note=? WHERE Id=?",safe,eventId);
    }
    private void eventLock(long id) {
        Integer result=jdbc.queryForObject("IF @@TRANCOUNT=0 BEGIN TRANSACTION; DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=?,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000; SELECT @r;",Integer.class,"qldapm:sepay:event:"+id);
        if(result==null || result<0) throw BusinessException.conflict("SEPAY_BUSY","Giao dịch đang được xử lý; thử lại.");
    }
    private void requireEnabled(){if(!config.isEnabled()) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"SEPAY_DISABLED","SePay chưa được cấu hình.");}
    private static String enc(String text){return URLEncoder.encode(text,StandardCharsets.UTF_8);}
    private <T> T tx(java.util.function.Supplier<T> work){return new TransactionTemplate(transactions).execute(status->work.get());}
}
