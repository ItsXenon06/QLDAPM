import {
  state,
  $,
  $$,
  h,
  money,
  date,
  can,
  api,
  all,
  qs,
  head,
  btn,
  table,
  bind,
  pager,
  field,
  values,
  formDialog,
  modal,
  badge,
  label,
  toast,
  icon,
  confirmAction,
  today,
} from './core.js';
import { customerFields } from './pages.js';
import { deviceArt, deviceKind } from './retail.js';
const path = '/pos/hoa-don';
export async function receipt(invoice) {
  const paid = invoice.trangThai === 'da_thanh_toan';
  const dialog = modal(
    invoice.soHoaDon || 'Hóa đơn',
    `<article class="receipt"><h2>SIÊU THỊ ĐIỆN MÁY</h2><p class="center">HÓA ĐƠN BÁN HÀNG<br><b>${h(invoice.soHoaDon || 'BẢN NHÁP')}</b></p><p class="note">${date(invoice.thoiGianThanhToan || invoice.thoiGianTao)}<br>Quầy: ${h(invoice.tenQuay)} • Thu ngân: ${h(invoice.thuNgan)}<br>Khách: ${h(invoice.tenKhachHang || 'Khách vãng lai')} ${h(invoice.soDienThoai || '')}</p>${table(
      [
        {
          title: 'Hàng hóa',
          render: (r) =>
            `${h(r.tenHang)}<span class="sub">${h(r.soSerial || r.maSKU)}${r.laQuaTang ? ' • Quà tặng' : ''}</span>`,
        },
        { title: 'SL', num: true, key: 'soLuong' },
        { title: 'Thành tiền', num: true, render: (r) => money(r.thanhTien) },
      ],
      invoice.dongs || [],
    )}<div class="total-row"><span>Tiền hàng</span><b>${money(invoice.tongTienHang)}</b></div><div class="total-row"><span>Giảm giá</span><b>− ${money(invoice.tongGiamGia)}</b></div><div class="total-row grand"><span>Thanh toán</span><b>${money(invoice.tongThanhToan)}</b></div><p class="note">Đã bao gồm VAT: ${money(invoice.tongTienThue)}</p>${(invoice.thanhToans || []).map((p) => `<div class="total-row"><span>${h(label(p.phuongThuc))}</span><b>${money(p.soTien)}</b></div>${p.tienThoiLai ? `<div class="total-row"><span>Tiền thối lại</span><b>${money(p.tienThoiLai)}</b></div>` : ''}`).join('')}<footer>Cảm ơn quý khách!<br>Giữ hóa đơn để được hỗ trợ bảo hành.<br>Chứng từ bán hàng nội bộ.</footer></article><div class="form-actions no-print">${paid ? btn('In hóa đơn', 'print', '', 'primary') : badge(invoice.trangThai)}</div>`,
  );
  bind(dialog, {
    print: async () => {
      await api(`${path}/${invoice.maHoaDon}/in`, 'POST');
      window.print();
    },
  });
}
export async function pos(root) {
  let counters = await api('/he-thong/quay'),
    drafts = await api(path + '/nhap-cua-toi'),
    invoice = drafts.find((r) => r.trangThai === 'dang_lap') || null;
  let chosen = invoice?.maQuay || Number(localStorage.getItem('qldapm-counter')) || counters[0]?.maQuay;
  if (!counters.some((counter) => counter.maQuay === chosen)) chosen = counters[0]?.maQuay;
  const inventory = (await all('/san-pham')).filter((product) => product.trangThai === 'dang_kinh_doanh');
  inventory.sort((a, b) => {
    const order = ['Điện thoại', 'Tivi & âm thanh', 'Tủ lạnh', 'Máy giặt', 'Phụ kiện'];
    const rank = (p) => {
      const index = order.indexOf(p.tenDanhMuc);
      return index < 0 ? 99 : index;
    };
    return rank(a) - rank(b) || a.tenSanPham.localeCompare(b.tenSanPham, 'vi');
  });
  const categories = [
    ...new Map(inventory.map((product) => [product.maDanhMuc, product.tenDanhMuc])).entries(),
  ];
  let catalogueQuery = '',
    selectedCategory = '',
    paymentOpening = false;
  const drawCatalogue = () => {
    const query = catalogueQuery.toLocaleLowerCase('vi');
    const products = inventory.filter(
      (product) =>
        (!selectedCategory || String(product.maDanhMuc) === selectedCategory) &&
        (product.tenSanPham + ' ' + product.maSKU + ' ' + product.tenThuongHieu)
          .toLocaleLowerCase('vi')
          .includes(query),
    );
    $('#catalogue-count', root).textContent = products.length + ' sản phẩm';
    $('#product-grid', root).innerHTML = products.length
      ? products
          .map(
            (product) =>
              '<button type="button" class="product-tile" data-act="pick-product" data-id="' +
              product.maSanPham +
              '" ' +
              (product.slTon <= 0 ? 'disabled' : '') +
              '>' +
              '<span class="product-art" data-device="' +
              deviceKind(product) +
              '">' +
              deviceArt(product) +
              '<span class="product-brand">' +
              h(product.tenThuongHieu) +
              '</span></span>' +
              '<span class="product-ref">' +
              h(product.maSKU.replace(/^DEMO-/, '')) +
              '<span>' +
              (product.coTheoDoiSerial ? 'SERIAL / IMEI' : 'PHỤ KIỆN') +
              '</span></span>' +
              '<strong class="product-name">' +
              h(product.tenSanPham) +
              '</strong><span class="product-price">' +
              money(product.giaNiemYet) +
              '</span>' +
              '<span class="product-stock ' +
              (product.slTon <= product.tonToiThieu ? 'low' : '') +
              '"><i></i>' +
              (product.slTon > 0 ? 'Còn ' + product.slTon + ' ' + h(product.donViTinh) : 'Tạm hết hàng') +
              '<span class="product-add">+</span></span></button>',
          )
          .join('')
      : '<div class="empty">Không tìm thấy sản phẩm. Thử tên khác hoặc quét mã trực tiếp.</div>';
    $$('.catalogue-tab', root).forEach((button) =>
      button.classList.toggle('selected', button.dataset.id === selectedCategory),
    );
  };
  const chooseDevice = async (product) => {
    if (!product.coTheoDoiSerial) {
      await add(product.maSKU);
      return;
    }
    const serials = await all('/kho/serial?' + qs({ maSanPham: product.maSanPham, trangThai: 'trong_kho' }));
    const dialog = modal(
      'Chọn thiết bị xuất bán',
      '<div class="device-picker-heading"><div class="product-art">' +
        deviceArt(product) +
        '</div><div><span class="eyebrow">' +
        h(product.maSKU) +
        '</span><h3>' +
        h(product.tenSanPham) +
        '</h3><strong>' +
        money(product.giaNiemYet) +
        '</strong></div></div>' +
        '<p class="note">Đối chiếu Serial trên thân máy hoặc hộp trước khi thêm vào bill. Thiết bị trong bill tạm giữ có thể đang được quầy khác giữ chỗ.</p>' +
        table(
          [
            {
              title: 'Serial / IMEI',
              render: (row) =>
                '<b class="mono">' +
                h(row.soSerial) +
                '</b><span class="sub">' +
                h(row.imei || 'Không có IMEI') +
                '</span>',
            },
            { title: 'Ngày nhập', render: (row) => date(row.ngayNhap) },
            {
              title: '',
              render: (row) => btn('Chọn thiết bị', 'take-device', row.soSerial, 'small primary'),
            },
          ],
          serials,
        ),
    );
    bind(dialog, {
      'take-device': async (serial) => {
        await add(serial);
        dialog.close();
      },
    });
  };

  const render = () => {
    const lines = (invoice?.dongs || []).filter((r) => r.hieuLuc);
    root.innerHTML =
      head(
        'Bàn tính tiền',
        'Chọn sản phẩm ở bên trái, kiểm tra bill ở bên phải.',
        '<span class="terminal-label"><i></i>QUẦY ĐANG MỞ</span><select id="counter" aria-label="Chọn quầy" ' +
          (invoice ? 'disabled' : '') +
          '>' +
          counters
            .map(
              (counter) =>
                '<option value="' +
                counter.maQuay +
                '" ' +
                (counter.maQuay === chosen ? 'selected' : '') +
                '>' +
                h(counter.tenQuay) +
                '</option>',
            )
            .join('') +
          '</select>' +
          btn(
            'Bill tạm giữ (' + drafts.filter((row) => row.trangThai === 'tam_giu').length + ')',
            'held',
            '',
            'small',
          ),
      ) +
      '<div class="pos-grid retail-pos"><section class="catalogue-workspace"><div class="scan-station"><div class="scan-caption"><span class="scan-mark">▥</span><div><b>Quét thiết bị / nhập mã hàng</b><small>SKU, mã vạch, Serial hoặc IMEI</small></div><kbd>F2</kbd></div><form class="scan"><input name="code" required autocomplete="off" placeholder="Đặt con trỏ tại đây rồi quét mã…" aria-label="Mã sản phẩm"><button class="primary">Thêm vào bill ↵</button></form></div>' +
      '<div class="catalogue-heading"><div><span class="section-index">DANH MỤC TẠI QUẦY</span><h2>Chọn hàng</h2></div><label class="catalogue-search"><span>⌕</span><input id="catalogue-query" type="search" placeholder="Tên, hãng hoặc SKU" aria-label="Tìm trong danh mục" value="' +
      h(catalogueQuery) +
      '"></label></div>' +
      '<div class="catalogue-tabs">' +
      btn('Tất cả', 'category', '', 'catalogue-tab') +
      categories.map(([id, name]) => btn(name, 'category', id, 'catalogue-tab')).join('') +
      '</div><div class="catalogue-subhead"><span id="catalogue-count"></span><span>Giá niêm yết đã gồm VAT</span></div><div id="product-grid" class="product-grid"></div></section>' +
      '<aside class="bill-workspace"><div class="bill-heading"><div><span class="section-index">' +
      h(counters.find((c) => c.maQuay === chosen)?.maQuayCode || 'CHƯA CHỌN QUẦY') +
      '</span><h2>Hóa đơn hiện tại</h2></div><span class="bill-number">' +
      (invoice ? '#' + invoice.maHoaDon : 'MỚI') +
      '</span></div><div class="bill-customer"><div class="customer-initial">' +
      h(
        (invoice?.tenKhachHang || 'VL')
          .split(' ')
          .map((s) => s[0])
          .slice(-2)
          .join(''),
      ) +
      '</div><div><b>' +
      h(invoice?.tenKhachHang || 'Khách vãng lai') +
      '</b><small>' +
      h(invoice?.soDienThoai || 'Gắn khách để lưu bảo hành & voucher') +
      '</small></div>' +
      btn('Thay đổi', 'customer', '', 'small ghost') +
      '</div>' +
      '<div class="bill-section-label"><span>THIẾT BỊ / HÀNG HÓA</span><b>' +
      lines.reduce((sum, line) => sum + line.soLuong, 0) +
      ' món</b></div><div class="bill-lines">' +
      (lines.length
        ? lines
            .map(
              (line, index) =>
                '<article class="bill-line"><span class="bill-line-index">' +
                String(index + 1).padStart(2, '0') +
                '</span><div class="bill-line-body"><b>' +
                h(line.tenHang) +
                '</b><span class="mono">' +
                h(line.soSerial || line.maSKU) +
                (line.laQuaTang ? ' · QUÀ TẶNG' : '') +
                '</span><div class="bill-line-bottom"><div class="bill-quantity">' +
                (!line.maSerial && !line.laQuaTang
                  ? btn('−', 'quantity-minus', line.maChiTiet, 'quantity-control')
                  : '') +
                '<span>' +
                line.soLuong +
                '</span>' +
                (!line.maSerial && !line.laQuaTang
                  ? btn('+', 'quantity-plus', line.maChiTiet, 'quantity-control')
                  : '') +
                '</div><span class="unit-price">× ' +
                money(line.donGia) +
                '</span><strong>' +
                money(line.thanhTien) +
                '</strong></div>' +
                (line.giamGia > 0
                  ? '<small class="line-discount">Đã giảm ' + money(line.giamGia) + '</small>'
                  : '') +
                '</div>' +
                (!line.laQuaTang
                  ? '<div class="bill-line-actions">' +
                    btn('×', 'remove', line.maChiTiet, 'remove-line-button') +
                    btn('Sửa', 'line', line.maChiTiet, 'small ghost') +
                    '</div>'
                  : '') +
                '</article>',
            )
            .join('')
        : '<div class="bill-empty"><div class="empty-bill-paper"><span></span><span></span><span></span></div><b>Bill còn trống</b><p>Quét mã hoặc chọn một sản phẩm<br>để bắt đầu giao dịch.</p></div>') +
      '</div>' +
      '<div class="bill-footer"><form id="coupon-form" class="coupon-entry"><input name="coupon" required placeholder="Mã ưu đãi / voucher" aria-label="Mã giảm giá"><button>Áp dụng</button></form>' +
      (invoice?.khuyenMais || [])
        .map(
          (promo) =>
            '<div class="bill-promotion"><span>' +
            h(promo.tenChuongTrinh) +
            '</span><b>−' +
            money(promo.soTienGiam) +
            '</b>' +
            (promo.maCode ? btn('×', 'uncoupon', promo.maCode, 'small ghost') : '') +
            '</div>',
        )
        .join('') +
      '<div class="total-row"><span>Tiền hàng</span><b>' +
      money(invoice?.tongTienHang) +
      '</b></div><div class="total-row"><span>Giảm giá & khuyến mãi</span><b>− ' +
      money(invoice?.tongGiamGia) +
      '</b></div><div class="bill-grand"><span>Khách cần trả<small>Đã gồm VAT ' +
      money(invoice?.tongTienThue) +
      '</small></span><strong>' +
      money(invoice?.tongThanhToan) +
      '</strong></div><button class="pay-button" data-act="pay" ' +
      (!lines.length ? 'disabled' : '') +
      '><span>Thanh toán</span><span>F4 ↗</span></button><div class="bill-secondary">' +
      (invoice
        ? btn('Tạm giữ bill', 'hold', '', 'small ghost') +
          btn('Hủy bản nháp', 'discard', '', 'small ghost danger')
        : '<span>Chưa lập hóa đơn</span>') +
      '</div></div></aside></div>';
    drawCatalogue();
    $('#catalogue-query', root).oninput = (event) => {
      catalogueQuery = event.target.value;
      drawCatalogue();
    };
    $('#counter', root).onchange = (e) => {
      chosen = Number(e.target.value);
      localStorage.setItem('qldapm-counter', chosen);
    };
    $('.scan', root).onsubmit = async (e) => {
      e.preventDefault();
      const input = $('[name=code]', root),
        button = $('button', e.target);
      button.disabled = true;
      try {
        await add(input.value);
        input.value = '';
      } catch (err) {
        toast(err.message, true);
      } finally {
        if (button.isConnected) button.disabled = false;
      }
    };
    $('#coupon-form', root).onsubmit = async (e) => {
      e.preventDefault();
      try {
        await ensure();
        invoice = await api(`${path}/${invoice.maHoaDon}/ma-giam-gia`, 'POST', {
          ma: $('[name=coupon]', root).value,
        });
        render();
      } catch (err) {
        toast(err.message, true);
      }
    };
    bind(root, {
      category: (id) => {
        selectedCategory = id;
        drawCatalogue();
      },
      'pick-product': (id) => chooseDevice(inventory.find((product) => product.maSanPham === Number(id))),
      'quantity-plus': async (id) => {
        const line = lines.find((row) => row.maChiTiet === Number(id));
        invoice = await api(path + '/' + invoice.maHoaDon + '/dong/' + id, 'PUT', {
          soLuong: line.soLuong + 1,
          giamGia: line.giamGiaTay || 0,
        });
        render();
      },
      'quantity-minus': async (id) => {
        const line = lines.find((row) => row.maChiTiet === Number(id));
        if (line.soLuong <= 1) return;
        invoice = await api(path + '/' + invoice.maHoaDon + '/dong/' + id, 'PUT', {
          soLuong: line.soLuong - 1,
          giamGia: line.giamGiaTay || 0,
        });
        render();
      },
      held,
      search,
      customer,
      hold: async () => {
        invoice = await api(`${path}/${invoice.maHoaDon}/tam-giu`, 'POST');
        invoice = null;
        await reload();
      },
      discard: () =>
        confirmAction(
          'Hủy bản nháp',
          'Các Serial được giữ trong hóa đơn này sẽ được giải phóng.',
          async () => {
            await api(`${path}/${invoice.maHoaDon}`, 'DELETE');
            invoice = null;
          },
        ),
      remove: async (id) => {
        invoice = await api(`${path}/${invoice.maHoaDon}/dong/${id}`, 'DELETE');
        render();
      },
      line: (id) => {
        const line = lines.find((r) => r.maChiTiet === Number(id));
        formDialog(
          'Điều chỉnh dòng hàng',
          [
            {
              name: 'soLuong',
              label: 'Số lượng',
              type: 'integer',
              min: 1,
              max: line.maSerial ? 1 : 999,
              required: true,
            },
            {
              name: 'giamGia',
              label: 'Giảm giá tay toàn dòng (đ)',
              type: 'number',
              min: 0,
              default: 0,
              help: 'Không gồm giảm giá tự động; phải tuân thủ giá sàn.',
            },
          ],
          { soLuong: line.soLuong, giamGia: line.giamGiaTay || 0 },
          async (data) => {
            invoice = await api(`${path}/${invoice.maHoaDon}/dong/${id}`, 'PUT', data);
          },
        );
      },
      uncoupon: async (code) => {
        invoice = await api(`${path}/${invoice.maHoaDon}/ma-giam-gia?` + qs({ ma: code }), 'DELETE');
        render();
      },
      pay: openPayment,
    });
    requestAnimationFrame(() => {
      if (root.isConnected) $('[name=code]', root)?.focus();
    });
  };
  const reload = async () => {
    drafts = await api(path + '/nhap-cua-toi');
    invoice = drafts.find((r) => r.trangThai === 'dang_lap') || null;
    render();
  };
  const ensure = async () => {
    if (!invoice) {
      if (!chosen) throw new Error('Chưa có quầy hoạt động. Quản trị viên cần tạo quầy POS.');
      invoice = await api(path, 'POST', { maQuay: chosen });
    }
  };
  const add = async (code) => {
    await ensure();
    invoice = await api(`${path}/${invoice.maHoaDon}/dong`, 'POST', { ma: code, soLuong: 1 });
    render();
  };
  const held = () => {
    const dialog = modal(
      'Hóa đơn tạm giữ',
      table(
        [
          { title: 'Hóa đơn', render: (r) => '#' + r.maHoaDon },
          { title: 'Khách hàng', render: (r) => h(r.tenKhachHang || 'Khách vãng lai') },
          { title: 'Tổng tiền', render: (r) => money(r.tongThanhToan) },
          { title: '', render: (r) => btn('Tiếp tục', 'resume', r.maHoaDon, 'primary small') },
        ],
        drafts.filter((r) => r.trangThai === 'tam_giu'),
      ),
    );
    bind(dialog, {
      resume: async (id) => {
        invoice = await api(`${path}/${id}/tiep-tuc`, 'POST');
        chosen = invoice.maQuay;
        dialog.close();
        await reload();
      },
    });
  };
  const search = () => {
    const dialog = modal(
      'Tìm sản phẩm',
      `<form class="scan"><input name="q" type="search" placeholder="Tên, SKU hoặc mã vạch" required><button>Tìm kiếm</button></form><div id="results" class="section-gap"></div>`,
    );
    $('form', dialog).onsubmit = async (e) => {
      e.preventDefault();
      try {
        const result = await api('/san-pham?' + qs({ keyword: $('[name=q]', dialog).value, size: 30 }));
        $('#results', dialog).innerHTML = table(
          [
            {
              title: 'Sản phẩm',
              render: (r) =>
                `<b>${h(r.tenSanPham)}</b><span class="sub">${h(r.maSKU)} • Tồn ${r.slTon}</span>`,
            },
            { title: 'Giá bán', render: (r) => money(r.giaNiemYet) },
            {
              title: '',
              render: (r) =>
                btn(r.coTheoDoiSerial ? 'Chọn thiết bị' : 'Thêm', 'pick', r.maSanPham, 'small primary'),
            },
          ],
          result.items,
        );
        bind($('#results', dialog), {
          pick: async (id) => {
            const product = result.items.find((r) => r.maSanPham === Number(id));
            if (!product.coTheoDoiSerial) {
              await add(product.maSKU);
              dialog.close();
              return;
            }
            const serials = await all('/kho/serial?' + qs({ maSanPham: id, trangThai: 'trong_kho' }));
            $('#results', dialog).innerHTML = table(
              [
                { title: 'Serial', key: 'soSerial' },
                { title: 'IMEI', key: 'imei' },
                { title: '', render: (r) => btn('Thêm thiết bị', 'serial', r.soSerial, 'small primary') },
              ],
              serials,
            );
            bind($('#results', dialog), {
              serial: async (code) => {
                await add(code);
                dialog.close();
              },
            });
          },
        });
      } catch (err) {
        toast(err.message, true);
      }
    };
  };
  const customer = () => {
    const dialog = modal(
      'Khách hàng trên hóa đơn',
      `<form class="scan"><input name="phone" required placeholder="Số điện thoại khách hàng"><button>Tra cứu</button></form><div id="customer-result" class="section-gap"></div><div class="row">${btn('+ Tạo khách hàng', 'new', '', 'small')}${btn('Dùng khách vãng lai', 'clear', '', 'small')}</div>`,
    );
    $('form', dialog).onsubmit = async (e) => {
      e.preventDefault();
      try {
        const row = await api('/khach-hang/tra-cuu?' + qs({ sdt: $('[name=phone]', dialog).value }));
        $('#customer-result', dialog).innerHTML =
          `<p><b>${h(row.hoTen)}</b> • ${h(row.soDienThoai)}</p>${btn('Gắn vào hóa đơn', 'attach', row.maKhachHang, 'primary')}`;
      } catch (err) {
        toast(err.message, true);
      }
    };
    bind(dialog, {
      new: () =>
        formDialog('Tạo khách hàng', customerFields, {}, async (data) => {
          const row = await api('/khach-hang', 'POST', data);
          await ensure();
          invoice = await api(`${path}/${invoice.maHoaDon}/khach-hang`, 'PUT', {
            maKhachHang: row.maKhachHang,
          });
        }),
      attach: async (id) => {
        await ensure();
        invoice = await api(`${path}/${invoice.maHoaDon}/khach-hang`, 'PUT', { maKhachHang: Number(id) });
        dialog.close();
        render();
      },
      clear: async () => {
        await ensure();
        invoice = await api(`${path}/${invoice.maHoaDon}/khach-hang`, 'PUT', { maKhachHang: null });
        dialog.close();
        render();
      },
    });
  };
  const manualPay = async () => {
    invoice = await api(`${path}/${invoice.maHoaDon}/dinh-gia`, 'POST');
    render();
    const paymentFields = [
      {
        name: 'phuongThuc',
        label: 'Phương thức',
        required: true,
        options: ['tien_mat', 'the_ngan_hang', 'qr_chuyen_khoan', 'tra_gop'],
      },
      { name: 'soTien', label: 'Số tiền thanh toán (đ)', type: 'number', min: 0.01, required: true },
      { name: 'tienKhachDua', label: 'Tiền khách đưa (đ)', type: 'number', min: 0 },
      { name: 'maThamChieu', label: 'Mã giao dịch / tham chiếu' },
      { name: 'donViTraGop', label: 'Đơn vị trả góp' },
      { name: 'soKyHan', label: 'Số kỳ hạn', type: 'integer', min: 1 },
    ];
    const dialog = formDialog(
      'Xác nhận thanh toán',
      [],
      {},
      async (_, form) => {
        const payments = $$('.payment-row', form).map((row) => {
          const data = values(row, paymentFields);
          if (data.phuongThuc !== 'tien_mat') data.tienKhachDua = null;
          if (data.phuongThuc !== 'tra_gop') {
            data.donViTraGop = null;
            data.soKyHan = null;
          }
          return data;
        });
        const sum = payments.reduce((s, r) => s + Number(r.soTien), 0);
        if (Math.abs(sum - Number(invoice.tongThanhToan)) > 0.005)
          throw new Error('Tổng các khoản thanh toán phải đúng ' + money(invoice.tongThanhToan));
        if (!$('[name=confirmed]', form).checked)
          throw new Error('Cần xác nhận đã nhận tiền / kiểm tra thanh toán.');
        const paid = await api(`${path}/${invoice.maHoaDon}/thanh-toan`, 'POST', { thanhToans: payments });
        invoice = null;
        setTimeout(() => receipt(paid).catch((err) => toast(err.message, true)), 0);
      },
      `<div class="total-row grand"><span>Cần thu</span><strong>${money(invoice.tongThanhToan)}</strong></div><div id="payments"></div>${btn('+ Chia thêm phương thức', 'add-payment', '', 'small')}<p class="note">Thẻ, chuyển khoản QR và trả góp: đối chiếu giao dịch bên ngoài rồi nhập mã tham chiếu.</p><label class="check-field"><input name="confirmed" type="checkbox" required> Tôi đã nhận đủ tiền / xác nhận giao dịch thành công.</label>`,
      (form) => {
        const addPayment = () => {
          if ($$('.payment-row', form).length >= 4) throw new Error('Tối đa 4 phương thức thanh toán.');
          const row = document.createElement('div');
          row.className = 'repeat-row payment-row';
          const remaining =
            Number(invoice.tongThanhToan) -
            $$('.payment-row [name=soTien]', form).reduce((s, input) => s + Number(input.value), 0);
          row.innerHTML = `<div class="form-grid">${paymentFields.map((f) => field(f, f.name === 'soTien' || f.name === 'tienKhachDua' ? Math.max(0, remaining) : f.name === 'phuongThuc' ? 'tien_mat' : null)).join('')}</div>${btn('Bỏ khoản này', 'remove-payment', '', 'small ghost danger')}`;
          $('#payments', form).append(row);
          const update = () => {
            const method = $('[name=phuongThuc]', row).value;
            for (const name of ['tienKhachDua', 'maThamChieu', 'donViTraGop', 'soKyHan']) {
              const el = row.querySelector('[name=' + name + ']');
              const enabled =
                name === 'tienKhachDua'
                  ? method === 'tien_mat'
                  : name === 'maThamChieu'
                    ? method !== 'tien_mat'
                    : method === 'tra_gop';
              el.closest('label').hidden = !enabled;
              el.disabled = !enabled;
              el.required = enabled && name !== 'tienKhachDua';
            }
          };
          $('[name=phuongThuc]', row).onchange = update;
          update();
        };
        bind(form, {
          'add-payment': addPayment,
          'remove-payment': (_, b) => b.closest('.payment-row').remove(),
        });
        addPayment();
        $('[type=submit]', form).textContent = 'Hoàn tất thanh toán';
      },
    );
  };
  const sepaySettings = await api('/payments/sepay/settings');
  const showQr = async (payment) => {
    const invoiceId = invoice.maHoaDon;
    const dialog = modal('Chuyển khoản QR · SePay', `
      <section class="sepay-payment">
        <div class="sepay-mode">${payment.testMode ? 'CHẾ ĐỘ THỬ NGHIỆM · KHÔNG CHUYỂN TIỀN THẬT' : 'CHUYỂN KHOẢN NGÂN HÀNG'}</div>
        <div class="total-row grand"><span>Cần thu</span><strong>${money(payment.amount)}</strong></div>
        <div class="sepay-qr">${payment.qrUrl ? `<img src="${h(payment.qrUrl)}" alt="Mã QR thanh toán ${h(payment.reference)}" referrerpolicy="no-referrer"><p class="note">Nếu ảnh không tải được, dùng thông tin chuyển khoản bên dưới.</p>` : ''}</div>
        <dl class="sepay-details"><dt>Ngân hàng</dt><dd>${h(payment.bank)}</dd><dt>Số tài khoản</dt><dd>${h(payment.accountNumber)}</dd><dt>Tên tài khoản</dt><dd>${h(payment.accountName || '—')}</dd><dt>Nội dung chuyển khoản</dt><dd><strong>${h(payment.reference)}</strong></dd></dl>
        <p class="sepay-state" aria-live="polite"></p><p class="sepay-note note"></p>
        <p class="note">${payment.testMode ? 'Trong SePay Test mode → Mô phỏng giao dịch: chọn tiền vào, nhập đúng số tiền và mã bên trên. ' : ''}Hóa đơn chỉ chốt sau khi máy chủ xác minh thông báo SePay.</p>
        <div class="form-actions"><button type="button" data-act="check-qr">Kiểm tra lại</button><button type="button" data-act="cancel-qr">Hủy mã QR</button><button type="button" data-act="retry-qr" hidden>Thử chốt lại hóa đơn</button><button type="button" data-act="new-qr" hidden>Tạo mã QR mới</button></div>
        <p class="note">Đóng cửa sổ vẫn giữ mã QR. Mở Thanh toán để xem lại; hủy mã trước khi sửa giỏ hàng hoặc thu tiền bằng cách khác.</p>
      </section>`);
    const panel=$('.sepay-payment',dialog);
    let timer, busy=false, finished=false;
    const cleanup=()=>{clearTimeout(timer);finished=true;dialog.removeEventListener('close',onClose);};
    const onClose=()=>{if(!dialog.open) cleanup();};
    const display = (p) => {
      payment=p;
      const names={PENDING:'Đang chờ SePay xác nhận',PAID:'Đã thanh toán',REVIEW:'Đã nhận thông báo · Cần đối soát',EXPIRED:'Mã QR đã hết hạn',CANCELLED:'Mã QR đã hủy'};
      $('.sepay-state',dialog).textContent=(names[p.status]||p.status)+(p.status==='PENDING' ? ` · còn ${Math.floor(p.remainingSeconds/60)}:${String(p.remainingSeconds%60).padStart(2,'0')}` : '');
      $('.sepay-state',dialog).dataset.status=p.status;
      $('.sepay-note',dialog).textContent=(p.note||'')+(p.status==='REVIEW' ? ` Đã ghi nhận: ${money(p.receivedAmount)}. Không thu thêm / giao hàng trước khi đối soát.` : '');
      $('[data-act=cancel-qr]',dialog).hidden=p.status!=='PENDING';
      $('[data-act=retry-qr]',dialog).hidden=!p.retryAllowed;
      $('[data-act=new-qr]',dialog).hidden=!['EXPIRED','CANCELLED'].includes(p.status);
      if(p.status!=='PENDING') $('.sepay-qr',dialog).hidden=true;
    };
    const check = async () => {
      if(!dialog.open || !panel.isConnected || finished) {cleanup();return;}
      if(busy) return;
      busy=true;
      try {
        const p=await api(`${path}/${invoiceId}/sepay`);
        if(!dialog.open || !panel.isConnected || finished) return;
        display(p);
        if(p.status==='PAID') {
          cleanup();
          const paid=await api(`${path}/${invoiceId}`);
          invoice=null;
          dialog.close();
          await state.refresh();
          await receipt(paid);
          return;
        }
      } catch(error) {
        if(dialog.open && panel.isConnected && !finished) $('.sepay-note',dialog).textContent='Chưa kiểm tra được: '+error.message+'. Mã QR vẫn được lưu; không xác nhận thủ công khi chưa đối soát.';
      } finally {
        busy=false;
        clearTimeout(timer);
        if(dialog.open && panel.isConnected && !finished) timer=setTimeout(check,2500);
      }
    };
    dialog.addEventListener('close',onClose);
    display(payment);
    bind(dialog,{
      'check-qr':check,
      'cancel-qr':async()=>{display(await api(`${path}/${invoiceId}/sepay/${payment.id}/cancel`,'POST'));},
      'retry-qr':async()=>{await api(`${path}/${invoiceId}/sepay/${payment.id}/retry`,'POST');await check();},
      'new-qr':async()=>{const next=await api(`${path}/${invoiceId}/sepay`,'POST');dialog.close();await showQr(next);},
    });
    timer=setTimeout(check,1000);
  };
  const pay = async () => {
    const existing=await api(`${path}/${invoice.maHoaDon}/sepay`);
    if(existing && ['PENDING','REVIEW'].includes(existing.status)) return showQr(existing);
    if(!sepaySettings.enabled) return manualPay();
    const dialog=modal('Chọn cách thanh toán',`<div class="sepay-choice"><button class="primary" data-act="sepay-start">QR chuyển khoản · SePay${sepaySettings.testMode ? ' (thử nghiệm)' : ''}</button><button data-act="manual-pay">Tiền mặt / thẻ / thanh toán đối chiếu tại quầy</button><p class="note">QR SePay thu toàn bộ hóa đơn và tự xác nhận. Các phương thức khác giữ cách ghi nhận tại quầy hiện tại.</p><div class="error" hidden></div></div>`);
    bind(dialog,{
      'sepay-start':async()=>{
        const button=$('[data-act=sepay-start]',dialog);button.disabled=true;
        try {const payment=await api(`${path}/${invoice.maHoaDon}/sepay`,'POST');dialog.close();await showQr(payment);}
        finally {button.disabled=false;}
      },
      'manual-pay':async()=>{dialog.close();await manualPay();},
    });
  };
  const openPayment = async () => {
    if (paymentOpening) return;
    paymentOpening = true;
    try {
      await pay();
    } finally {
      paymentOpening = false;
    }
  };
  const shortcuts = (event) => {
    if (!root.isConnected) {
      document.removeEventListener('keydown', shortcuts);
      return;
    }
    if ($('#modal').open) return;
    if (event.key === 'F2') {
      event.preventDefault();
      $('[name=code]', root)?.focus();
    }
    if (event.key === 'F4' && (invoice?.dongs || []).some((line) => line.hieuLuc)) {
      event.preventDefault();
      openPayment().catch((error) => toast(error.message, true));
    }
  };
  document.addEventListener('keydown', shortcuts);
  render();
}

export async function invoices(root) {
  root.innerHTML =
    head('Hóa đơn bán hàng', 'Tra cứu giao dịch, in lại hóa đơn và xử lý hủy trong ngày.') +
    `<section class="card"><form class="toolbar"><input name="soHoaDon" type="search" placeholder="Số hóa đơn chính xác">${field({ name: 'tu', label: 'Từ ngày', type: 'date' }, today())}${field({ name: 'den', label: 'Đến ngày', type: 'date' }, today())}${field({ name: 'trangThai', label: 'Trạng thái', options: ['da_thanh_toan', 'da_huy'] }, 'da_thanh_toan')}<button>Tìm kiếm</button></form><div id="list"></div></section>`;
  const load = async (page = 0) => {
    const filters = Object.fromEntries(new FormData($('form', root)));
    const result = await api(path + '?' + qs({ ...filters, page, size: 20 }));
    const box = $('#list', root);
    box.innerHTML = table(
      [
        { title: 'Số hóa đơn', key: 'soHoaDon' },
        { title: 'Thanh toán lúc', render: (r) => date(r.thoiGianThanhToan) },
        {
          title: 'Quầy / thu ngân',
          render: (r) => `${h(r.tenQuay)}<span class="sub">${h(r.thuNgan)}</span>`,
        },
        { title: 'Khách hàng', render: (r) => h(r.tenKhachHang || 'Vãng lai') },
        { title: 'Tổng tiền', num: true, render: (r) => money(r.tongThanhToan) },
        { title: 'Trạng thái', render: (r) => badge(r.trangThai) },
        {
          title: '',
          render: (r) =>
            btn('Xem / in', 'view', r.maHoaDon, 'small') +
            (can('POS_HUY_HOA_DON') && r.trangThai === 'da_thanh_toan'
              ? btn('Hủy', 'cancel', r.maHoaDon, 'small ghost danger')
              : ''),
        },
      ],
      result.items,
    );
    pager(box, result, load);
  };
  $('form', root).onsubmit = (e) => {
    e.preventDefault();
    load().catch((err) => toast(err.message, true));
  };
  bind(root, {
    view: async (id) => receipt(await api(path + '/' + id)),
    cancel: (id) =>
      formDialog(
        'Hủy hóa đơn đã thanh toán',
        [{ name: 'lyDo', label: 'Lý do hủy', type: 'textarea', required: true, wide: true }],
        {},
        (data) => api(`${path}/${id}/huy`, 'POST', data),
        '<p class="note">Chỉ hủy trong cùng ngày và khi chưa có phiếu trả. Kho, bảo hành và ưu đãi được hoàn tác.</p>',
      ),
  });
  await load();
}
