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
  opt,
  options,
  label,
  toast,
  confirmAction,
  today,
} from './core.js';
const withStatus = (root, statuses) =>
  `<select name="trangThai" aria-label="Lọc trạng thái" style="max-width:200px"><option value="">Mọi trạng thái</option>${options(
    statuses,
  )
    .map((o) => `<option value="${o.value}">${h(o.text)}</option>`)
    .join('')}</select>`;
async function listPage(root, { title, desc, path, columns, create, statuses, view }) {
  let rows = [];
  root.innerHTML =
    head(title, desc, create ? btn('+ Tạo mới', 'new', '', 'primary') : '') +
    `<section class="card"><div class="toolbar">${withStatus(root, statuses)}${btn('Làm mới', 'refresh', '', 'small')}</div><div id="list"></div></section>`;
  const load = async (page = 0) => {
    const result = await api(
      path + '?' + qs({ page, size: 20, trangThai: $('[name=trangThai]', root).value }),
    );
    rows = result.items;
    const box = $('#list', root);
    box.innerHTML = table(
      [
        ...columns,
        { title: 'Trạng thái', render: (r) => badge(r.trangThai) },
        {
          title: '',
          render: (r) =>
            btn(
              'Chi tiết',
              'view',
              r.id || r.maPhieuNhap || r.maPhieuKiemKe || r.maKhuyenMai || r.maYeuCau || r.maPhieuTra,
              'small',
            ),
        },
      ],
      rows,
    );
    pager(box, result, load);
  };
  $('[name=trangThai]', root).onchange = () => load().catch((err) => toast(err.message, true));
  bind(root, { new: create, view, refresh: () => load() });
  await load();
}

export async function receiving(root) {
  const path = '/nhap-kho';
  const create = async () => {
    const [suppliers, products] = await Promise.all([api('/nha-cung-cap'), all('/san-pham')]);
    if (!suppliers.length || !products.length)
      throw new Error('Cần có nhà cung cấp và sản phẩm trước khi lập phiếu.');
    const fields = [
      {
        name: 'maNCC',
        label: 'Nhà cung cấp',
        required: true,
        numeric: true,
        options: opt(suppliers.filter(s => s.trangThai !== false), 'maNCC', 'tenNCC'),
      },
      { name: 'soChungTuNCC', label: 'Số chứng từ nhà cung cấp' },
      { name: 'ngayDuKien', label: 'Dự kiến nhận', type: 'datetime-local' },
      { name: 'ghiChu', label: 'Ghi chú' },
    ];
    const lineFields = [
      {
        name: 'maSanPham',
        label: 'Sản phẩm',
        required: true,
        numeric: true,
        options: products.map((p) => ({ value: p.maSanPham, text: p.maSKU + ' • ' + p.tenSanPham })),
      },
      { name: 'slDatHang', label: 'Số lượng đặt', type: 'integer', min: 1, required: true, default: 1 },
      { name: 'giaNhap', label: 'Giá nhập chưa VAT (đ)', type: 'number', min: 0.01, required: true },
      { name: 'chietKhauPhanTram', label: 'Chiết khấu (%)', type: 'number', min: 0, max: 100, default: 0 },
      { name: 'thueSuat', label: 'VAT nhập (%)', type: 'number', min: 0, max: 100, default: 10 },
    ];
    formDialog(
      'Lập phiếu nhập kho',
      fields,
      {},
      async (data, form) => {
        data.chiTiets = $$('.receive-line', form).map((row) => values(row, lineFields));
        if (!data.chiTiets.length) throw new Error('Cần ít nhất một dòng sản phẩm.');
        await api(path, 'POST', data);
      },
      `<h3 class="section-gap">Hàng hóa đặt nhập</h3><div id="receive-lines"></div>${btn('+ Thêm sản phẩm', 'add-line', '', 'small')}`,
      (form) => {
        const add = () => {
          const row = document.createElement('div');
          row.className = 'repeat-row receive-line';
          row.innerHTML = `<div class="form-grid">${lineFields.map((f) => field(f)).join('')}</div>${btn('Bỏ dòng', 'remove-line', '', 'small ghost danger')}`;
          $('#receive-lines', form).append(row);
        };
        bind(form, { 'add-line': add, 'remove-line': (_, b) => b.closest('.receive-line').remove() });
        add();
      },
    );
  };
  const view = async (id) => {
    const p = await api(path + '/' + id);
    const editable = can('KHO_NHAP') && ['nhap', 'mot_phan'].includes(p.trangThai);
    const dialog = modal(
      'Phiếu nhập ' + p.maSoPhieu,
      `<div class="status-line">${badge(p.trangThai)}<b>${h(p.tenNCC)}</b></div><p class="note">Người lập: ${h(p.nguoiLap)} • ${date(p.taoLuc)}<br>${h(p.ghiChu || '')}</p>${table(
        [
          { title: 'Sản phẩm', key: 'tenSanPham' },
          { title: 'Đặt', num: true, key: 'slDatHang' },
          { title: 'Đã nhận', num: true, key: 'slDaNhan' },
          { title: 'Giá nhập', num: true, render: (r) => money(r.giaNhap) },
        ],
        p.chiTiets,
      )}<div class="total-row"><span>Tổng cộng</span><b>${money(p.tongCong)}</b></div>${editable ? `<div class="form-actions">${p.trangThai === 'nhap' ? btn('Hủy phiếu', 'cancel', '', 'danger') : btn('Kết thúc nhận thiếu', 'finish', '', 'small')}${btn('Nhận hàng', 'receive', '', 'primary')}</div>` : ''}`,
    );
    bind(dialog, {
      cancel: () =>
        confirmAction('Hủy phiếu nhập', 'Chỉ phiếu chưa nhận hàng mới có thể hủy.', () =>
          api(path + '/' + id + '/huy', 'POST'),
        ),
      finish: () =>
        confirmAction(
          'Kết thúc nhận thiếu',
          'Đóng phiếu với số lượng đã nhận; không nhận thêm trên phiếu này.',
          () => api(path + '/' + id + '/ket-thuc', 'POST'),
        ),
      receive: () => {
        const outstanding = p.chiTiets.filter((r) => r.slDaNhan < r.slDatHang);
        formDialog(
          'Ghi nhận hàng thực nhận',
          [],
          {},
          async (_, form) => {
            const dongs = [];
            for (const row of $$('.receipt-input', form)) {
              const line = outstanding.find((l) => l.maChiTiet === Number(row.dataset.id));
              const quantity = Number($('[name=quantity]', row).value);
              if (!quantity) continue;
              let serials = [];
              if (line.coTheoDoiSerial)
                serials = $('[name=serials]', row)
                  .value.split(/\r?\n/)
                  .filter((s) => s.trim())
                  .map((s) => {
                    const [soSerial, imei, imei2] = s.split(',').map((x) => x.trim());
                    return { soSerial, imei: imei || null, imei2: imei2 || null };
                  });
              dongs.push({ maChiTiet: line.maChiTiet, soLuong: quantity, serials });
            }
            if (!dongs.length) throw new Error('Nhập số lượng nhận ít nhất một sản phẩm.');
            await api(path + '/' + id + '/nhan-hang', 'POST', { dongs });
          },
          `<p class="note">Nhận từng phần được hỗ trợ. Với hàng Serial, mỗi dòng nhập: Serial, IMEI, IMEI2. IMEI có thể để trống.</p>${outstanding.map((l) => `<div class="repeat-row receipt-input" data-id="${l.maChiTiet}"><h3>${h(l.tenSanPham)} <span class="muted">• Còn ${l.slDatHang - l.slDaNhan}</span></h3><label class="field">Số lượng nhận<input name="quantity" type="number" min="0" max="${l.slDatHang - l.slDaNhan}" step="1" value="0"></label>${l.coTheoDoiSerial ? '<label class="field section-gap">Danh sách Serial / IMEI<textarea name="serials" rows="4" placeholder="SN001, 123456789012345\nSN002"></textarea></label>' : ''}</div>`).join('')}`,
        );
      },
    });
  };
  await listPage(root, {
    title: 'Nhập kho',
    desc: 'Lập phiếu thủ công và nhận từng thiết bị theo Serial / IMEI.',
    path,
    statuses: ['nhap', 'mot_phan', 'hoan_thanh', 'huy'],
    create: can('KHO_NHAP') ? create : null,
    view,
    columns: [
      { title: 'Số phiếu', key: 'maSoPhieu' },
      { title: 'Nhà cung cấp', key: 'tenNCC' },
      { title: 'Ngày lập', render: (r) => date(r.taoLuc) },
      { title: 'Tổng cộng', num: true, render: (r) => money(r.tongCong) },
    ],
  });
}

export async function counts(root) {
  const path = '/kiem-ke';
  const create = async () => {
    const products = await all('/san-pham');
    formDialog(
      'Mở đợt kiểm kê',
      [{ name: 'ghiChu', label: 'Ghi chú / phạm vi', wide: true }],
      {},
      async (data, form) => {
        data.maSanPhams = $$('[name=product]:checked', form).map((el) => Number(el.value));
        if (!data.maSanPhams.length && !$('[name=all-products]', form).checked)
          throw new Error('Chọn sản phẩm hoặc xác nhận kiểm kê toàn kho.');
        await api(path, 'POST', data);
      },
      `<p class="note">Sản phẩm được chọn sẽ tạm khóa nhập, xuất và đổi trạng thái thiết bị đến khi hoàn thành hoặc hủy phiếu. Dừng di chuyển hàng thực tế trước khi bắt đầu.</p><label class="check-field"><input name="all-products" type="checkbox"> Kiểm kê toàn bộ sản phẩm</label><div class="permission-grid">${products.map((p) => `<label class="check-field"><input name="product" type="checkbox" value="${p.maSanPham}">${h(p.maSKU)} • ${h(p.tenSanPham)}</label>`).join('')}</div>`,
      (form) => {
        $('[name=all-products]', form).onchange = (e) => {
          $$('[name=product]', form).forEach((el) => {
            el.disabled = e.target.checked;
            if (e.target.checked) el.checked = false;
          });
        };
      },
    );
  };
  const view = async (id) => {
    const count = await api(path + '/' + id);
    const open = count.trangThai === 'dang_kiem';
    const dialog = formDialog(
      'Kiểm kê ' + count.maSoPhieu,
      [],
      {},
      async (_, form) => {
        if (!open) return;
        const dongs = count.chiTiets.map((c) => {
          const row = $(`[data-count-line="${c.maChiTiet}"]`, form);
          return {
            maChiTiet: c.maChiTiet,
            slThucTe: c.coTheoDoiSerial ? null : Number($('[name=actual]', row).value),
            lyDoChenhLech: $('[name=reason]', row).value || null,
            serials: c.coTheoDoiSerial
              ? c.serials.map((s) => ({
                  maSerial: s.maSerial,
                  timThay: $(`[name="serial-${s.maSerial}"]`, row).value === 'true',
                  ghiChu: null,
                }))
              : null,
          };
        });
        await api(path + '/' + id + '/ket-qua', 'PUT', { dongs });
      },
      `<div class="status-line">${badge(count.trangThai)}<span class="note">${h(count.nguoiLap)} • ${date(count.ngayKiemKe)}</span></div>${count.chiTiets.map((c) => `<div class="repeat-row" data-count-line="${c.maChiTiet}"><h3>${h(c.tenSanPham)} <span class="mono">${h(c.maSKU)}</span></h3><p class="note">Tồn hệ thống khi mở phiếu: <b>${c.slHeThong}</b></p>${c.coTheoDoiSerial ? c.serials.map((s) => `<label class="field">${h(s.soSerial)} ${h(s.imei || '')}<select name="serial-${s.maSerial}" ${open ? 'required' : 'disabled'}><option value="">Chưa kiểm</option><option value="true" ${s.timThay === true ? 'selected' : ''}>Tìm thấy</option><option value="false" ${s.timThay === false ? 'selected' : ''}>Không tìm thấy</option></select></label>`).join('') || '<p class="note">Không có thiết bị Serial trong kho.</p>' : `<label class="field">Số lượng đếm thực tế<input name="actual" type="number" min="0" step="1" value="${h(c.slThucTe ?? '')}" ${open ? 'required' : 'disabled'}></label>`}<label class="field section-gap">Lý do chênh lệch (bắt buộc nếu lệch)<input name="reason" value="${h(c.lyDoChenhLech || '')}" ${open ? '' : 'disabled'}></label></div>`).join('')}${open ? `<div class="row section-gap">${btn('Hoàn thành & điều chỉnh kho', 'complete', '', 'primary')}${btn('Hủy đợt kiểm kê', 'cancel', '', 'danger')}</div><p class="note">Lưu kết quả trước khi hoàn thành. Không tìm thấy Serial sẽ ghi nhận thất lạc.</p>` : ''}`,
      (form) => {
        if (!open) $('[type=submit]', form).hidden = true;
        bind(form, {
          complete: () =>
            confirmAction(
              'Hoàn thành kiểm kê',
              'Áp dụng các kết quả đã lưu, điều chỉnh chênh lệch và mở lại giao dịch kho cho các sản phẩm.',
              () => api(path + '/' + id + '/hoan-thanh', 'POST'),
            ),
          cancel: () =>
            confirmAction('Hủy kiểm kê', 'Không điều chỉnh tồn kho; giải phóng các sản phẩm đang kiểm.', () =>
              api(path + '/' + id + '/huy', 'POST'),
            ),
        });
      },
    );
  };
  await listPage(root, {
    title: 'Kiểm kê hàng hóa',
    desc: 'Đếm thực tế theo sản phẩm và đối chiếu từng Serial.',
    path,
    statuses: ['dang_kiem', 'hoan_thanh', 'huy'],
    create,
    view,
    columns: [
      { title: 'Số phiếu', key: 'maSoPhieu' },
      { title: 'Người lập', key: 'nguoiLap' },
      { title: 'Mở phiếu', render: (r) => date(r.ngayKiemKe) },
      { title: 'Ghi chú', key: 'ghiChu' },
    ],
  });
}

const promotionFields = [
  { name: 'tenChuongTrinh', label: 'Tên chương trình', required: true, wide: true },
  { name: 'maCode', label: 'Mã khuyến mãi (để trống nếu tự động)' },
  {
    name: 'loaiKhuyenMai',
    label: 'Loại ưu đãi',
    required: true,
    options: ['giam_phan_tram', 'giam_tien', 'mua_kem_gia_soc', 'tang_qua', 'tang_voucher'],
  },
  { name: 'phanTramGiam', label: 'Phần trăm giảm (%)', type: 'number', min: 0.01, max: 100 },
  { name: 'soTienGiamToiDa', label: 'Trần giảm theo % (đ)', type: 'number', min: 0.01 },
  { name: 'giamCodinh', label: 'Giảm cố định (đ)', type: 'number', min: 0.01 },
  { name: 'giaTriVoucher', label: 'Giá trị voucher tặng (đ)', type: 'number', min: 0.01 },
  { name: 'giaTriDonToiThieu', label: 'Giá trị đơn tối thiểu (đ)', type: 'number', min: 0, default: 0 },
  { name: 'soLuongToiDa', label: 'Số lượt tối đa (trống = không giới hạn)', type: 'integer', min: 1 },
  { name: 'doUuTien', label: 'Độ ưu tiên (số lớn xét trước)', type: 'integer', default: 0 },
  { name: 'ngayBatDau', label: 'Bắt đầu', type: 'datetime-local', required: true },
  { name: 'ngayKetThuc', label: 'Kết thúc', type: 'datetime-local', required: true },
  { name: 'tuDongApDung', label: 'Tự động áp dụng', type: 'check', default: true },
  { name: 'choPhepCongDon', label: 'Cho phép cộng dồn', type: 'check', default: false },
  { name: 'apDungToanBo', label: 'Áp dụng toàn bộ sản phẩm', type: 'check', default: true },
  { name: 'moTa', label: 'Mô tả', type: 'textarea', wide: true },
];
export async function promotions(root) {
  const path = '/khuyen-mai';
  const edit = async (id) => {
    const [products, categories, row] = await Promise.all([
      all('/san-pham'),
      api('/danh-muc'),
      id ? api(path + '/' + id) : Promise.resolve({}),
    ]);
    const itemFields = [
      {
        name: 'vaiTro',
        label: 'Vai trò',
        required: true,
        options: [
          { value: 'dieu_kien', text: 'Điều kiện / sản phẩm được giảm' },
          { value: 'uu_dai', text: 'Quà tặng / sản phẩm mua kèm' },
        ],
      },
      {
        name: 'maSanPham',
        label: 'Sản phẩm (chọn sản phẩm hoặc danh mục)',
        numeric: true,
        options: products.map((p) => ({ value: p.maSanPham, text: p.maSKU + ' • ' + p.tenSanPham })),
      },
      {
        name: 'maDanhMuc',
        label: 'Danh mục',
        numeric: true,
        options: opt(categories, 'maDanhMuc', 'tenDanhMuc'),
      },
      { name: 'soLuong', label: 'Số lượng', type: 'integer', required: true, min: 1, default: 1 },
      { name: 'giaUuDai', label: 'Giá mua kèm (đ)', type: 'number', min: 0 },
    ];
    formDialog(
      id ? 'Chỉnh sửa chương trình' : 'Tạo chương trình khuyến mãi',
      promotionFields,
      row,
      async (data, form) => {
        data.items = $$('.promotion-item', form).map((el) => values(el, itemFields));
        const type = data.loaiKhuyenMai;
        if (type !== 'giam_phan_tram') {
          data.phanTramGiam = null;
          data.soTienGiamToiDa = null;
        }
        if (type !== 'giam_tien') data.giamCodinh = null;
        if (type !== 'tang_voucher') data.giaTriVoucher = null;
        await api(path + (id ? '/' + id : ''), id ? 'PUT' : 'POST', data);
      },
      `<h3 class="section-gap">Điều kiện và sản phẩm ưu đãi</h3><p class="note">Giảm tiền / %: chọn toàn bộ hoặc thêm các dòng điều kiện. Mua kèm cần cả điều kiện và sản phẩm ưu đãi. Quà tặng chỉ dùng sản phẩm không theo dõi Serial.</p><div id="promotion-items"></div>${btn('+ Thêm điều kiện / ưu đãi', 'add-item', '', 'small')}`,
      (form) => {
        const add = (data) => {
          const el = document.createElement('div');
          el.className = 'repeat-row promotion-item';
          el.innerHTML = `<div class="form-grid">${itemFields.map((f) => field(f, data?.[f.name])).join('')}</div>${btn('Bỏ dòng', 'remove-item', '', 'small ghost danger')}`;
          $('#promotion-items', form).append(el);
        };
        bind(form, {
          'add-item': () => add(),
          'remove-item': (_, b) => b.closest('.promotion-item').remove(),
        });
        (row.items || []).forEach(add);
        const update = () => {
          const type = form.elements.loaiKhuyenMai.value;
          for (const name of ['phanTramGiam', 'soTienGiamToiDa', 'giamCodinh', 'giaTriVoucher']) {
            const enabled =
              name === 'phanTramGiam' || name === 'soTienGiamToiDa'
                ? type === 'giam_phan_tram'
                : name === 'giamCodinh'
                  ? type === 'giam_tien'
                  : type === 'tang_voucher';
            form.elements[name].closest('label').hidden = !enabled;
            form.elements[name].disabled = !enabled;
          }
          form.elements.phanTramGiam.required = type === 'giam_phan_tram';
          form.elements.giamCodinh.required = type === 'giam_tien';
          form.elements.giaTriVoucher.required = type === 'tang_voucher';
        };
        form.elements.loaiKhuyenMai.onchange = update;
        update();
      },
    );
  };
  const view = async (id) => {
    const row = await api(path + '/' + id);
    const dialog = modal(
      row.tenChuongTrinh,
      `<div class="status-line">${badge(row.trangThai)}<b>${h(label(row.loaiKhuyenMai))}</b></div><p>${h(row.moTa || '')}</p><p class="note">${date(row.ngayBatDau)} → ${date(row.ngayKetThuc)}<br>Mã: ${h(row.maCode || 'Tự động')} • Lượt dùng: ${row.soLuongDaDung}/${row.soLuongToiDa ?? '∞'}<br>Đơn tối thiểu: ${money(row.giaTriDonToiThieu)}</p>${table(
        [
          { title: 'Vai trò', render: (r) => (r.vaiTro === 'uu_dai' ? 'Ưu đãi' : 'Điều kiện') },
          { title: 'Sản phẩm / danh mục', render: (r) => h(r.tenSanPham || r.tenDanhMuc) },
          { title: 'Số lượng', key: 'soLuong' },
          { title: 'Giá ưu đãi', render: (r) => (r.giaUuDai === null ? '—' : money(r.giaUuDai)) },
        ],
        row.items || [],
      )}${can('KHUYEN_MAI_QUAN_LY') ? `<div class="form-actions">${btn('Sửa', 'edit', '', 'small')}${btn(row.trangThai === 'hoat_dong' ? 'Tạm dừng' : 'Kích hoạt', 'status', '', 'primary')}${btn('Hủy chương trình', 'cancel', '', 'danger')}</div>` : ''}`,
    );
    bind(dialog, {
      edit: () => edit(id),
      status: async () => {
        await api(path + '/' + id + '/trang-thai', 'PUT', {
          trangThai: row.trangThai === 'hoat_dong' ? 'tam_dung' : 'hoat_dong',
        });
        dialog.close();
        await state.refresh();
      },
      cancel: () =>
        confirmAction('Hủy khuyến mãi', 'Chương trình sẽ không được áp dụng cho giao dịch mới.', () =>
          api(path + '/' + id + '/trang-thai', 'PUT', { trangThai: 'huy' }),
        ),
    });
  };
  await listPage(root, {
    title: 'Khuyến mãi',
    desc: 'Chính sách ưu đãi tự động tại quầy và chương trình nhập mã.',
    path,
    statuses: ['hoat_dong', 'tam_dung', 'het_han', 'huy'],
    create: can('KHUYEN_MAI_QUAN_LY') ? () => edit() : null,
    view,
    columns: [
      { title: 'Chương trình', key: 'tenChuongTrinh' },
      { title: 'Loại', render: (r) => h(label(r.loaiKhuyenMai)) },
      { title: 'Bắt đầu', render: (r) => date(r.ngayBatDau) },
      { title: 'Kết thúc', render: (r) => date(r.ngayKetThuc) },
      { title: 'Đã dùng', key: 'soLuongDaDung' },
    ],
  });
}

export async function vouchers(root) {
  root.innerHTML =
    head('Voucher', 'Theo dõi voucher đã phát hành từ các giao dịch đủ điều kiện.') +
    `<section class="card"><div class="toolbar">${withStatus(root, ['chua_dung', 'da_dung', 'het_han', 'huy'])}</div><div id="list"></div></section>`;
  const load = async (page = 0) => {
    const result = await api(
      '/voucher?' + qs({ page, size: 20, trangThai: $('[name=trangThai]', root).value }),
    );
    const box = $('#list', root);
    box.innerHTML = table(
      [
        { title: 'Mã voucher', key: 'maCode' },
        { title: 'Giá trị', num: true, render: (r) => money(r.giaTri) },
        { title: 'Khách hàng', render: (r) => `<b>${h(r.tenKhachHang || 'Không giới hạn khách')}</b><span class="sub">${h(r.soDienThoai || '')}</span>` },
        { title: 'Nguồn phát hành', render: (r) => `<span>${h(r.tenChuongTrinh || '—')}</span><span class="sub mono">${h(r.soHoaDonPhatHanh || 'Không có hóa đơn nguồn')}</span>` },
        { title: 'Phát hành', render: (r) => date(r.ngayPhatHanh) },
        { title: 'Hạn dùng', render: (r) => date(r.hanSuDung) },
        { title: 'Trạng thái', render: (r) => badge(r.trangThai) },
        {
          title: '',
          render: (r) =>
            r.trangThai === 'chua_dung' ? btn('Hủy', 'cancel', r.maVoucher, 'small danger') : '',
        },
      ],
      result.items,
    );
    pager(box, result, load);
  };
  $('[name=trangThai]', root).onchange = () => load().catch((err) => toast(err.message, true));
  bind(root, {
    cancel: (id) =>
      formDialog(
        'Hủy voucher',
        [{ name: 'lyDo', label: 'Lý do hủy', required: true, wide: true }],
        {},
        (data) => api('/voucher/' + id + '/huy', 'POST', data),
      ),
  });
  await load();
}

export async function policies(root) {
  const path = '/chinh-sach-bao-hanh';
  const rows = await api(path + '/tat-ca');
  const fields = [
    { name: 'tenChinhSach', label: 'Tên chính sách', required: true, wide: true },
    { name: 'thoiHanThang', label: 'Thời hạn (tháng)', type: 'integer', min: 1, max: 120, required: true },
    { name: 'hoatDong', label: 'Đang hoạt động', type: 'check', default: true },
    { name: 'noiDung', label: 'Điều kiện bảo hành', type: 'textarea', wide: true, rows: 5 },
  ];
  root.innerHTML =
    head(
      'Chính sách bảo hành',
      'Cấu hình thời hạn và điều kiện phục vụ tra cứu, gia hạn.',
      btn('+ Thêm chính sách', 'new', '', 'primary'),
    ) +
    `<section class="card">${table(
      [
        { title: 'Chính sách', key: 'tenChinhSach' },
        { title: 'Tháng', num: true, key: 'thoiHanThang' },
        { title: 'Nội dung', key: 'noiDung' },
        { title: 'Trạng thái', render: (r) => badge(r.hoatDong ? 'hoat_dong' : 'vo_hieu') },
        {
          title: '',
          render: (r) =>
            btn('Sửa', 'edit', r.maChinhSach, 'small') +
            btn('Ngừng dùng', 'delete', r.maChinhSach, 'small ghost danger'),
        },
      ],
      rows,
    )}</section>`;
  bind(root, {
    new: () => formDialog('Thêm chính sách', fields, {}, (data) => api(path, 'POST', data)),
    edit: (id) =>
      formDialog(
        'Cập nhật chính sách',
        fields,
        rows.find((r) => r.maChinhSach === Number(id)),
        (data) => api(path + '/' + id, 'PUT', data),
      ),
    delete: (id) =>
      confirmAction('Ngừng dùng chính sách', 'Chính sách cũ vẫn được giữ trên các bảo hành đã cấp.', () =>
        api(path + '/' + id, 'DELETE'),
      ),
  });
}

export async function warranty(root) {
  root.innerHTML =
    head('Bảo hành & chăm sóc', 'Tra lịch sử mua hàng và tiếp nhận thiết bị cần hỗ trợ.') +
    `<section class="card"><form class="toolbar"><input name="ma" type="search" required placeholder="Serial, IMEI, số hóa đơn hoặc số điện thoại"><button class="primary">Tra cứu bảo hành</button></form><div id="lookup"></div></section><section class="card"><div class="card-head"><h2>Yêu cầu bảo hành</h2>${withStatus(root, ['tiep_nhan', 'dang_xu_ly', 'hoan_tat', 'da_tra_khach', 'tu_choi'])}</div><div id="requests"></div></section>`;
  let warranties = [];
  const lookup = async () => {
    const result = await api('/bao-hanh/tra-cuu?' + qs({ ma: $('[name=ma]', root).value }));
    warranties = result.baoHanhs;
    $('#lookup', root).innerHTML = table(
      [
        {
          title: 'Thiết bị',
          render: (r) => `<b>${h(r.tenHang)}</b><span class="sub mono">${h(r.soSerial || r.maSKU)}</span>`,
        },
        { title: 'Hóa đơn', key: 'soHoaDon' },
        { title: 'Khách hàng', key: 'tenKhachHang' },
        { title: 'Hạn bảo hành', render: (r) => date(r.ngayKetThuc) },
        { title: 'Trạng thái', render: (r) => badge(r.trangThai) },
        {
          title: '',
          render: (r) =>
            (can('BAO_HANH_TIEP_NHAN') && r.dangCoHieuLuc
              ? btn('Tiếp nhận', 'intake', r.maBaoHanh, 'primary small')
              : '') + (can('BAO_HANH_CHINH_SACH') ? btn('Gia hạn', 'extend', r.maChiTietHD, 'small') : ''),
        },
      ],
      result.baoHanhs,
    );
  };
  const load = async (page = 0) => {
    const result = await api(
      '/bao-hanh/yeu-cau?' + qs({ page, size: 20, trangThai: $('[name=trangThai]', root).value }),
    );
    const box = $('#requests', root);
    box.innerHTML = table(
      [
        { title: 'Mã tiếp nhận', key: 'maSoTiepNhan' },
        { title: 'Sản phẩm', render: (r) => `${h(r.tenHang)}<span class="sub mono">${h(r.soSerial)}</span>` },
        {
          title: 'Người gửi',
          render: (r) => `${h(r.tenNguoiGui)}<span class="sub">${h(r.soDienThoaiNguoiGui)}</span>`,
        },
        { title: 'Hẹn trả', render: (r) => date(r.ngayHenTra) },
        { title: 'Trạng thái', render: (r) => badge(r.trangThai) },
        { title: '', render: (r) => btn('Chi tiết', 'request', r.maYeuCau, 'small') },
      ],
      result.items,
    );
    pager(box, result, load);
  };
  $('form', root).onsubmit = (e) => {
    e.preventDefault();
    lookup().catch((err) => toast(err.message, true));
  };
  $('[name=trangThai]', root).onchange = () => load().catch((err) => toast(err.message, true));
  bind(root, {
    intake: (id) => {
      const row = warranties.find((r) => r.maBaoHanh === Number(id));
      formDialog(
        'Tiếp nhận • ' + row.tenHang,
        [
          { name: 'tenNguoiGui', label: 'Họ tên người gửi', required: true },
          { name: 'soDienThoaiNguoiGui', label: 'Số điện thoại', required: true },
          { name: 'moTaSuCo', label: 'Mô tả sự cố', type: 'textarea', wide: true, required: true },
          { name: 'tinhTrangNhanMay', label: 'Tình trạng khi nhận' },
          { name: 'phuKienKemTheo', label: 'Phụ kiện kèm theo' },
          { name: 'ngayHenTra', label: 'Ngày hẹn trả', type: 'datetime-local' },
        ],
        { tenNguoiGui: row.tenKhachHang, soDienThoaiNguoiGui: row.soDienThoai },
        (data) => api('/bao-hanh/yeu-cau', 'POST', { ...data, maBaoHanh: Number(id) }),
      );
    },
    extend: async (id) => {
      const policies = await api('/chinh-sach-bao-hanh');
      formDialog(
        'Gia hạn bảo hành',
        [
          {
            name: 'maChinhSach',
            label: 'Chính sách gia hạn',
            required: true,
            numeric: true,
            options: opt(policies, 'maChinhSach', 'tenChinhSach'),
          },
        ],
        {},
        (data) => api('/bao-hanh/gia-han', 'POST', { maChiTietHD: Number(id), ...data }),
      );
    },
    request: async (id) => {
      const row = await api('/bao-hanh/yeu-cau/' + id);
      const transitions = {
        tiep_nhan: ['dang_xu_ly', 'tu_choi'],
        dang_xu_ly: ['hoan_tat', 'tu_choi'],
        hoan_tat: ['da_tra_khach'],
        tu_choi: ['da_tra_khach'],
      };
      const fields = [
        {
          name: 'trangThai',
          label: 'Chuyển trạng thái (trống = chỉ lưu ghi chú)',
          options: transitions[row.trangThai] || [],
        },
        { name: 'ngayHenTra', label: 'Ngày hẹn trả', type: 'datetime-local' },
        { name: 'ketQuaXuLy', label: 'Kết quả xử lý / lý do từ chối', type: 'textarea', wide: true },
      ];
      if (can('BAO_HANH_TIEP_NHAN') && row.trangThai !== 'da_tra_khach')
        formDialog(
          'Yêu cầu ' + row.maSoTiepNhan,
          fields,
          { ...row, trangThai: null },
          (data) => api('/bao-hanh/yeu-cau/' + id, 'PUT', data),
          `<p class="note">${h(row.tenHang)} • ${h(row.soSerial)}<br>${h(row.tenNguoiGui)} • ${h(row.soDienThoaiNguoiGui)}<br>Sự cố: ${h(row.moTaSuCo)}<br>Phụ kiện: ${h(row.phuKienKemTheo || 'Không có')}</p>`,
        );
      else
        modal(
          'Yêu cầu ' + row.maSoTiepNhan,
          `<p>${h(row.tenHang)}</p>${badge(row.trangThai)}<p class="note">${h(row.moTaSuCo)}</p><p>${h(row.ketQuaXuLy || 'Chưa ghi kết quả')}</p>`,
        );
    },
  });
  await load();
}

export async function returns(root) {
  const path = '/tra-hang';
  const create = () => {
    formDialog(
      'Chọn hóa đơn trả hàng',
      [{ name: 'soHoaDon', label: 'Số hóa đơn gốc', required: true, wide: true }],
      {},
      async (data) => {
        const result = await api('/pos/hoa-don?' + qs({ soHoaDon: data.soHoaDon }));
        if (!result.items.length) throw new Error('Không tìm thấy hóa đơn.');
        const invoice = result.items[0];
        const lines = await api(path + '/hoa-don/' + invoice.maHoaDon + '/co-the-tra');
        if (!lines.length) throw new Error('Không có dòng hàng có thể trả.');
        setTimeout(
          () =>
            formDialog(
              'Lập phiếu trả • ' + invoice.soHoaDon,
              [
                { name: 'lyDo', label: 'Lý do trả', required: true, type: 'textarea', wide: true },
                {
                  name: 'phuongThucHoan',
                  label: 'Phương thức hoàn tiền',
                  required: true,
                  options: ['tien_mat', 'the_ngan_hang', 'qr_chuyen_khoan'],
                },
              ],
              {},
              async (data, form) => {
                data.maHoaDon = invoice.maHoaDon;
                data.chiTiets = $$('.return-line', form)
                  .map((el) => ({
                    maChiTietHD: Number(el.dataset.id),
                    soLuong: Number($('[name=quantity]', el).value),
                    tinhTrangHang: $('[name=condition]', el).value,
                    ghiChu: $('[name=note]', el).value || null,
                  }))
                  .filter((r) => r.soLuong > 0);
                if (!data.chiTiets.length) throw new Error('Chọn ít nhất một sản phẩm để trả.');
                await api(path, 'POST', data);
              },
              lines
                .map(
                  (l) =>
                    `<div class="repeat-row return-line" data-id="${l.maChiTietHD}"><h3>${h(l.tenHang)} <span class="mono">${h(l.soSerial || l.maSKU)}</span></h3><div class="form-grid">${field({ name: 'quantity', label: 'Số lượng trả (còn ' + l.soLuongConTra + ')', type: 'integer', min: 0, max: l.soLuongConTra }, 0)}${field(
                      {
                        name: 'condition',
                        label: 'Tình trạng hàng',
                        required: true,
                        options: [
                          { value: 'nguyen_hop', text: 'Nguyên hộp' },
                          { value: 'da_mo', text: 'Đã mở hộp' },
                          { value: 'loi', text: 'Bị lỗi' },
                          { value: 'thieu_phu_kien', text: 'Thiếu phụ kiện' },
                        ],
                      },
                      'nguyen_hop',
                    )}${field({ name: 'note', label: 'Ghi chú', wide: true })}</div></div>`,
                )
                .join(''),
            ),
          0,
        );
      },
    );
  };
  const view = async (id) => {
    const row = await api(path + '/' + id);
    const dialog = modal(
      row.maSoPhieu,
      `<div class="status-line">${badge(row.trangThai)}<b>${h(row.soHoaDon)}</b></div><p>${h(row.lyDo)}</p>${table(
        [
          { title: 'Hàng trả', render: (r) => `${h(r.tenHang)}<span class="sub">${h(r.soSerial)}</span>` },
          { title: 'SL', key: 'soLuong' },
          { title: 'Hoàn tiền', num: true, render: (r) => money(r.soTienHoan) },
          { title: 'Nhập lại kho', render: (r) => (r.nhapLaiKho ? 'Có' : 'Không') },
        ],
        row.chiTiets,
      )}<div class="total-row grand"><span>Tổng hoàn</span><b>${money(row.tongTienHoan)}</b></div><p class="note">${h(label(row.phuongThucHoan))} • ${h(row.nguoiXuLy)}</p><div class="form-actions">${row.trangThai === 'cho_duyet' && can('POS_DUYET_TRA_HANG') ? btn('Duyệt', 'approve', '', 'primary') + btn('Từ chối', 'reject', '', 'danger') : ''}${row.trangThai === 'da_duyet' && can('POS_TRA_HANG', 'POS_DUYET_TRA_HANG') ? btn('Xác nhận đã hoàn tiền & nhận hàng', 'complete', '', 'primary') : ''}</div>`,
    );
    bind(dialog, {
      approve: async () => {
        await api(path + '/' + id + '/duyet', 'POST');
        dialog.close();
        await state.refresh();
      },
      reject: () =>
        formDialog(
          'Từ chối trả hàng',
          [{ name: 'lyDo', label: 'Lý do', required: true, wide: true }],
          {},
          (data) => api(path + '/' + id + '/tu-choi', 'POST', data),
        ),
      complete: () =>
        confirmAction(
          'Hoàn tất trả hàng',
          'Chỉ xác nhận sau khi đã nhận hàng và hoàn tiền cho khách. Kho và bảo hành sẽ được cập nhật.',
          () => api(path + '/' + id + '/hoan-tat', 'POST'),
        ),
    });
  };
  await listPage(root, {
    title: 'Trả hàng',
    desc: 'Lập yêu cầu, duyệt hoàn tiền và ghi nhận nhập lại kho.',
    path,
    statuses: ['cho_duyet', 'da_duyet', 'hoan_tat', 'tu_choi'],
    create: can('POS_TRA_HANG') ? create : null,
    view,
    columns: [
      { title: 'Số phiếu', key: 'maSoPhieu' },
      { title: 'Hóa đơn gốc', key: 'soHoaDon' },
      { title: 'Ngày lập', render: (r) => date(r.taoLuc) },
      { title: 'Tiền hoàn', num: true, render: (r) => money(r.tongTienHoan) },
    ],
  });
}
