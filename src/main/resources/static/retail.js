import { state, $, h, money, can, api, qs, table, date, badge, bind, btn, today } from './core.js';

export function deviceKind(product) {
  const text = (product.tenDanhMuc + ' ' + product.tenSanPham + ' ' + product.maSKU).toLocaleLowerCase('vi');
  if (/tủ lạnh|bespoke|fr\d/.test(text)) return 'fridge';
  if (/máy giặt|wm\d/.test(text)) return 'washer';
  if (/điện thoại|iphone|galaxy|reno|ph\d/.test(text)) return 'phone';
  if (/tivi|qled|bravia|uhd|tv\d/.test(text)) return /jbl|loa/.test(text) ? 'speaker' : 'tv';
  if (/tai nghe|mdr/.test(text)) return 'headphones';
  if (/vệ sinh|làm sạch/.test(text)) return 'cleaning';
  if (/cáp|hdmi/.test(text)) return 'cable';
  return 'accessory';
}

export function deviceArt(product) {
  const kind = deviceKind(product);
  const shapes = {
    tv: '<rect x="22" y="19" width="116" height="64" rx="3" fill="var(--device-paper)"/><path d="M27 24h106v53H27z" fill="var(--device-screen)" stroke="none"/><path d="m64 84-5 11m37-11 5 11M50 96h60"/><path d="m29 68 34-24 28 20 40-29" stroke="var(--device-accent)" stroke-width="3"/><path d="M30 72h99" opacity=".25"/>',
    phone:
      '<rect x="52" y="10" width="46" height="87" rx="8" fill="var(--device-paper)"/><path d="M57 23h36v59H57z" fill="var(--device-screen)" stroke="none"/><path d="M68 17h13m-9 72h6"/><path d="m58 69 33-36v24L59 82" fill="var(--device-accent)" opacity=".65" stroke="none"/><rect x="95" y="23" width="20" height="67" rx="5" fill="var(--device-paper)"/><circle cx="102" cy="32" r="2"/><circle cx="108" cy="37" r="2"/>',
    fridge:
      '<rect x="50" y="9" width="62" height="90" rx="4" fill="var(--device-paper)"/><path d="M50 60h62M78 10v49M72 26v17m13-17v17m-23 26h17"/><path d="M54 64h54v30H54z" fill="var(--device-accent)" stroke="none" opacity=".16"/><path d="M56 101h8m35 0h8"/>',
    washer:
      '<rect x="44" y="16" width="75" height="82" rx="4" fill="var(--device-paper)"/><path d="M45 35h73M53 25h18"/><circle cx="105" cy="26" r="4"/><circle cx="82" cy="65" r="23" fill="var(--device-screen)"/><circle cx="82" cy="65" r="17"/><path d="M67 67c9-9 19 9 30-1" stroke="var(--device-accent)" stroke-width="3"/>',
    speaker:
      '<rect x="50" y="18" width="63" height="76" rx="17" fill="var(--device-screen)"/><path d="M59 27v56m9-60v63m10-64v65m10-65v65m10-63v60m8-54v48" opacity=".25"/><rect x="69" y="52" width="25" height="17" rx="2" fill="var(--device-accent)"/><path d="M75 59h12m-9 5h6" stroke="white"/>',
    headphones:
      '<path d="M46 66V49a35 35 0 0 1 70 0v17" stroke-width="8"/><path d="M53 62V48a28 28 0 0 1 56 0v14" stroke="var(--device-accent)" stroke-width="3"/><rect x="38" y="55" width="19" height="35" rx="8" fill="var(--device-paper)"/><rect x="105" y="55" width="19" height="35" rx="8" fill="var(--device-paper)"/>',
    cable:
      '<path d="M56 30v30c0 28 52 28 52 3V46c0-22-35-22-35 1v11" stroke-width="5"/><rect x="47" y="11" width="18" height="24" rx="4" fill="var(--device-accent)"/><path d="M52 9V4m8 5V4"/><rect x="65" y="57" width="17" height="22" rx="4" fill="var(--device-paper)"/>',
    cleaning:
      '<rect x="46" y="42" width="33" height="51" rx="6" fill="var(--device-paper)"/><path d="M56 41V27h14v14m-12-14V17h24v7H69m3 10h12l7 12"/><rect x="50" y="60" width="25" height="18" rx="2" fill="var(--device-accent)" opacity=".5"/><path d="m89 69 34 5-6 23-33-5z" fill="var(--device-screen)"/><path d="m92 75 23 4m-25 3 23 4" opacity=".4"/>',
    accessory:
      '<rect x="50" y="37" width="64" height="49" rx="10" fill="var(--device-paper)"/><path d="M66 35V19m29 16V19" stroke-width="5"/><rect x="73" y="52" width="19" height="9" rx="3" fill="var(--device-accent)"/><path d="M63 74h39" opacity=".2"/>',
  };
  return `<svg class="device-svg" viewBox="0 0 160 110" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${shapes[kind]}</svg>`;
}

export async function dashboard(root) {
  let summary = await api('/bao-cao/tong-quan');
  let recent = [],
    alerts = [],
    requests = [],
    detailsError = '';
  const details = async () => {
    try {
      [recent, alerts, requests] = await Promise.all([
        can('POS_XEM_HOA_DON')
          ? api('/pos/hoa-don?' + qs({ tu: today(), den: today(), size: 6 })).then((r) => r.items)
          : [],
        can('KHO_XEM_TON') ? api('/kho/canh-bao') : [],
        can('BAO_HANH_TRA_CUU', 'BAO_HANH_TIEP_NHAN')
          ? api('/bao-hanh/yeu-cau?size=4').then((r) => r.items)
          : [],
      ]);
      detailsError = '';
    } catch (error) {
      detailsError = error.message;
    }
  };
  await details();
  const draw = () => {
    const now = new Date(),
      day = now.toLocaleDateString('vi-VN', { day: '2-digit' }),
      month = now.toLocaleDateString('vi-VN', { month: '2-digit' });
    const max = Math.max(1, ...summary.theoGio.map((r) => Number(r.doanhThu)));
    const bars = summary.theoGio
      .map(
        (r, i) =>
          `<rect x="${i * 26 + 8}" y="${148 - (Number(r.doanhThu) / max) * 129}" width="13" height="${Math.max(2, (Number(r.doanhThu) / max) * 129)}" rx="1" fill="${Number(r.doanhThu) > 0 ? '#ba5b30' : '#e8e4dc'}"><title>${h(r.nhan)}:00 · ${h(money(r.doanhThu))}</title></rect>`,
      )
      .join('');
    root.innerHTML = `<header class="retail-page-head"><div class="calendar-stamp"><strong>${h(day)}</strong><span>THÁNG ${h(month)}</span></div><div><div class="eyebrow">ĐIỂM BÁN 01 / NHẬT KÝ TRONG NGÀY</div><h1>Sổ bán hàng</h1><p>${h(now.toLocaleDateString('vi-VN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }))}</p></div><div class="sync-label"><i></i><span>Đã đồng bộ<br><b>${h(now.toLocaleTimeString('vi-VN', { hour12: false }))}</b></span>${btn('↻', 'refresh', '', 'small sync-button')}</div></header>
    <section class="sales-ledger"><div class="ledger-total"><div class="eyebrow">DOANH THU THUẦN · HÔM NAY</div><strong>${money(summary.doanhThuThuan)}</strong><span>Doanh thu gộp ${money(summary.doanhThuGop)} <em>/</em> Hoàn trả ${money(summary.tienHoan)}</span></div><div class="ledger-cell"><span>Hóa đơn đã chốt</span><strong>${summary.soHoaDon.toLocaleString('vi-VN')}</strong><small>${summary.hoaDonDangLap} đang lập / tạm giữ</small></div><div class="ledger-cell"><span>Cần bổ sung hàng</span><strong class="${summary.matHangCanhBao ? 'attention' : ''}">${summary.matHangCanhBao.toLocaleString('vi-VN')}<sup>SKU</sup></strong><small>Chạm ngưỡng tồn tối thiểu</small></div><div class="ledger-cell"><span>Tiếp nhận bảo hành</span><strong>${summary.yeuCauBaoHanhDangMo.toLocaleString('vi-VN')}<sup>phiếu</sup></strong><small>Chưa bàn giao lại cho khách</small></div></section>
    <div class="retail-overview-grid"><section class="card sales-chart"><div class="card-head"><div><span class="section-index">01 /</span><h2>Nhịp giao dịch</h2></div><span class="chart-key"><i></i>Doanh thu theo giờ</span></div><div class="chart-main"><div class="chart-scale"><span>${money(max)}</span><span>${money(max / 2)}</span><span>0 đ</span></div><div class="chart-drawing"><svg viewBox="0 0 640 162" preserveAspectRatio="none" role="img" aria-label="Biểu đồ doanh thu 24 giờ"><path d="M0 19H640M0 83H640M0 148H640" stroke="#e9e5dd" stroke-dasharray="3 4"/>${bars}</svg><div class="chart-labels"><span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>23:00</span></div></div></div><div class="chart-foot"><span>Tổng tiền bán hàng đã gồm VAT.</span>${can('BAO_CAO_XEM') ? '<a href="#reports">Mở sổ báo cáo ↗</a>' : ''}</div></section>
    <section class="card work-register"><div class="card-head"><div><span class="section-index">02 /</span><h2>Việc tại quầy</h2></div><span class="tiny-label">ĐANG THEO DÕI</span></div><div class="work-register-body">${can('POS_BAN_HANG') ? '<a href="#pos" class="work-item"><span class="work-number">POS</span><div><b>Mở một giao dịch mới</b><small>Chọn thiết bị, gắn khách, chốt bill</small></div><span>↗</span></a>' : ''}${can('KHO_XEM_TON') ? `<a href="#stock" class="work-item"><span class="work-number attention">${String(summary.matHangCanhBao).padStart(2, '0')}</span><div><b>Mặt hàng dưới ngưỡng</b><small>Đối chiếu và lên phiếu nhập</small></div><span>↗</span></a>` : ''}${can('BAO_HANH_TRA_CUU') ? `<a href="#warranty" class="work-item"><span class="work-number">${String(summary.yeuCauBaoHanhDangMo).padStart(2, '0')}</span><div><b>Thiết bị tại CSKH</b><small>Tiếp nhận, xử lý, hẹn trả</small></div><span>↗</span></a>` : ''}${can('POS_XEM_HOA_DON') ? `<a href="#invoices" class="work-item"><span class="work-number">HD</span><div><b>Tra lại một hóa đơn</b><small>In lại hoặc đối chiếu giao dịch</small></div><span>↗</span></a>` : ''}</div></section></div>
    ${detailsError ? `<p class="error">${h(detailsError)}</p>` : ''}
    <div class="retail-bottom-grid">${
      can('POS_XEM_HOA_DON')
        ? `<section class="card"><div class="card-head"><div><span class="section-index">03 /</span><h2>Hóa đơn vừa chốt</h2></div><a href="#invoices">Xem tất cả ↗</a></div>${table(
            [
              {
                title: 'Số hóa đơn / thời gian',
                render: (r) =>
                  `<b class="mono invoice-number">${h(r.soHoaDon)}</b><span class="sub">${date(r.thoiGianThanhToan)}</span>`,
              },
              {
                title: 'Khách / thu ngân',
                render: (r) =>
                  `<b>${h(r.tenKhachHang || 'Khách vãng lai')}</b><span class="sub">${h(r.tenQuay)} · ${h(r.thuNgan)}</span>`,
              },
              { title: 'Thành tiền', num: true, render: (r) => `<b>${money(r.tongThanhToan)}</b>` },
            ],
            recent,
          )}</section>`
        : ''
    }
    <section class="card stock-watch"><div class="card-head"><div><span class="section-index">04 /</span><h2>${can('KHO_XEM_TON') ? 'Sổ nhắc nhập hàng' : 'Bàn giao CSKH'}</h2></div><span class="count-label">${can('KHO_XEM_TON') ? alerts.length : requests.length} mục</span></div>${
      can('KHO_XEM_TON')
        ? alerts
            .slice(0, 5)
            .map(
              (r) =>
                `<a href="#stock" class="stock-watch-row"><div class="watch-sku">${h(r.maSKU.replace(/^DEMO-/, ''))}</div><div><b>${h(r.tenSanPham)}</b><small>${h(r.viTriKho || 'Chưa xếp vị trí')} · Mức tối thiểu ${r.tonToiThieu}</small></div><span class="stock-quantity">${r.slTon}<small>còn lại</small></span></a>`,
            )
            .join('') || '<p class="empty">Tồn kho đang trên các ngưỡng đã đặt.</p>'
        : requests
            .map(
              (r) =>
                `<a href="#warranty" class="stock-watch-row"><div><b>${h(r.tenHang)}</b><small>${h(r.maSoTiepNhan)} · ${h(r.tenNguoiGui)}</small></div>${badge(r.trangThai)}</a>`,
            )
            .join('') || '<p class="empty">Chưa có phiếu tiếp nhận.</p>'
    }<div class="chart-foot"><span>Dữ liệu ghi nhận trực tiếp.</span><a href="#${can('KHO_XEM_TON') ? 'stock' : 'warranty'}">Mở sổ ↗</a></div></section></div>`;
    bind(root, { refresh: () => state.refresh() });
  };
  draw();
  let busy = false,
    tick = 0;
  const timer = setInterval(async () => {
    if (!root.isConnected) {
      clearInterval(timer);
      return;
    }
    if (busy) return;
    busy = true;
    try {
      summary = await api('/bao-cao/tong-quan');
      if (++tick % 10 === 0) await details();
      if (root.isConnected) draw();
    } catch {
      const status = root.querySelector('.sync-label span');
      if (status) status.textContent = 'Mất kết nối · thử làm mới';
    } finally {
      busy = false;
    }
  }, 1500);
}
