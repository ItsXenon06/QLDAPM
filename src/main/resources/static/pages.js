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
  formDialog,
  modal,
  badge,
  opt,
  options,
  label,
  toast,
  download,
  today,
  confirmAction,
} from './core.js';
const permissionAction = (permission, text, action, id = '', style = 'small') =>
  can(permission) ? btn(text, action, id, style) : '';
const productFields = (categories, brands, edit = false) => [
  { name: 'maSKU', label: 'Mã SKU', required: true, maxlength: 60 },
  { name: 'maVach', label: 'Mã vạch', maxlength: 60 },
  { name: 'tenSanPham', label: 'Tên sản phẩm', required: true, wide: true, maxlength: 255 },
  {
    name: 'maDanhMuc',
    label: 'Danh mục',
    required: true,
    numeric: true,
    options: opt(categories, 'maDanhMuc', 'tenDanhMuc'),
  },
  {
    name: 'maThuongHieu',
    label: 'Thương hiệu',
    required: true,
    numeric: true,
    options: opt(brands, 'maThuongHieu', 'tenThuongHieu'),
  },
  { name: 'donViTinh', label: 'Đơn vị tính', default: 'cái' },
  { name: 'baoHanhThang', label: 'Bảo hành (tháng)', type: 'integer', default: 12, min: 0, required: true },
  {
    name: 'thueSuatVAT',
    label: 'VAT (%) đã trong giá bán',
    type: 'number',
    default: 10,
    min: 0,
    max: 100,
    required: true,
  },
  ...(edit
    ? [
        {
          name: 'trangThai',
          label: 'Trạng thái',
          required: true,
          options: ['dang_kinh_doanh', 'ngung_kinh_doanh'],
        },
      ]
    : [
        { name: 'giaNiemYet', label: 'Giá niêm yết (đ)', type: 'number', min: 0, required: true },
        { name: 'giaToiThieu', label: 'Giá sàn giảm tay (đ)', type: 'number', min: 0 },
        { name: 'tonToiThieu', label: 'Tồn tối thiểu', type: 'integer', default: 0, min: 0 },
      ]),
  { name: 'coTheoDoiSerial', label: 'Theo dõi từng thiết bị bằng Serial/IMEI', type: 'check', default: true },
  { name: 'moTa', label: 'Mô tả', type: 'textarea', wide: true },
  {
    name: 'thongSoKyThuat',
    label: 'Thông số kỹ thuật (mỗi dòng một thuộc tính)',
    type: 'textarea',
    wide: true,
    placeholder: 'Màn hình: 55 inch\nMàu sắc: Đen',
  },
];
export async function products(root) {
  root.innerHTML =
    head(
      'Sản phẩm',
      'Danh mục thiết bị, giá bán và thời hạn bảo hành.',
      permissionAction('SAN_PHAM_QUAN_LY', '+ Thêm sản phẩm', 'new', '', 'primary'),
    ) +
    `<section class="card"><form class="toolbar"><input name="keyword" type="search" placeholder="Tìm tên sản phẩm, SKU, mã vạch…"><button>Tìm kiếm</button></form><div id="product-table"></div></section>`;
  let rows = [];
  const load = async (page = 0) => {
    state.page = page;
    const result = await api('/san-pham?' + qs({ keyword: $('[name=keyword]', root).value, page, size: 20 }));
    rows = result.items;
    const box = $('#product-table', root);
    box.innerHTML = table(
      [
        {
          title: 'Sản phẩm',
          render: (r) =>
            `<b>${h(r.tenSanPham)}</b><span class="sub mono">${h(r.maSKU)}${r.coTheoDoiSerial ? ' • Serial/IMEI' : ''}</span>`,
        },
        { title: 'Danh mục', key: 'tenDanhMuc' },
        { title: 'Thương hiệu', key: 'tenThuongHieu' },
        { title: 'Giá bán', num: true, render: (r) => money(r.giaNiemYet) },
        {
          title: 'Tồn kho',
          num: true,
          render: (r) =>
            `<b>${h(r.slTon)}</b>${r.slTon <= r.tonToiThieu ? '<span class="sub" style="color:#b77924">Cần bổ sung</span>' : ''}`,
        },
        { title: 'Trạng thái', render: (r) => badge(r.trangThai) },
        {
          title: '',
          render: (r) =>
            btn('Chi tiết', 'view-product', r.maSanPham, 'small') +
            permissionAction('SAN_PHAM_QUAN_LY', 'Sửa', 'edit', r.maSanPham) +
            permissionAction('GIA_QUAN_LY', 'Giá', 'price', r.maSanPham) +
            permissionAction('SAN_PHAM_QUAN_LY', 'Xóa', 'delete', r.maSanPham, 'small ghost danger'),
        },
      ],
      rows,
    );
    pager(box, result, load);
  };
  $('form', root).onsubmit = (e) => {
    e.preventDefault();
    load().catch((err) => toast(err.message, true));
  };
  const edit = async (id) => {
    const [cats, brands] = await Promise.all([api('/danh-muc'), api('/thuong-hieu')]);
    if (!cats.length || !brands.length) throw new Error('Hãy tạo danh mục và thương hiệu trước.');
    const item = id ? { ...rows.find((r) => r.maSanPham === Number(id)) } : {};
    const originalSpecs = item.thongSoKyThuat;
    if (originalSpecs) {
      const specs = JSON.parse(originalSpecs);
      item.thongSoKyThuat = Object.entries(specs)
        .map(([key, value]) => `${key}: ${typeof value === 'object' ? JSON.stringify(value) : value}`)
        .join('\n');
    }
    const originalInput = item.thongSoKyThuat;
    formDialog(
      id ? 'Cập nhật sản phẩm' : 'Thêm sản phẩm',
      productFields(cats, brands, !!id),
      item,
      async (data) => {
        if (data.thongSoKyThuat === originalInput) data.thongSoKyThuat = originalSpecs;
        else if (data.thongSoKyThuat) {
          const specs = Object.create(null);
          for (const line of data.thongSoKyThuat.split(/\r?\n/).filter((line) => line.trim())) {
            const separator = line.indexOf(':');
            if (separator < 1 || !line.slice(separator + 1).trim())
              throw new Error('Mỗi thông số cần có dạng Tên thuộc tính: Giá trị.');
            const key = line.slice(0, separator).trim();
            if (Object.hasOwn(specs, key)) throw new Error('Thuộc tính bị trùng: ' + key);
            specs[key] = line.slice(separator + 1).trim();
          }
          data.thongSoKyThuat = JSON.stringify(specs);
        }
        await api('/san-pham' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', data);
      },
    );
  };
  bind(root, {
    new: () => edit(),
    edit,
    'view-product': (id) => {
      const product = rows.find((row) => row.maSanPham === Number(id));
      const specs = product.thongSoKyThuat ? Object.entries(JSON.parse(product.thongSoKyThuat)) : [];
      modal(
        product.tenSanPham,
        `<div class="status-line">${badge(product.trangThai)}<span class="mono">${h(product.maSKU)}</span></div>
        <p>${h(product.moTa || 'Chưa có mô tả.')}</p><div class="total-row"><span>Giá bán đã gồm VAT</span><b>${money(product.giaNiemYet)}</b></div>
        <p class="note">${h(product.tenThuongHieu)} • ${h(product.tenDanhMuc)}<br>Bảo hành ${product.baoHanhThang} tháng • Tồn kho ${product.slTon} ${h(product.donViTinh)}<br>${product.coTheoDoiSerial ? 'Theo dõi Serial / IMEI từng thiết bị' : 'Theo dõi theo số lượng'}</p>
        ${table(
          [
            { title: 'Thuộc tính', render: (row) => h(row[0]) },
            {
              title: 'Giá trị',
              render: (row) => h(typeof row[1] === 'object' ? JSON.stringify(row[1]) : row[1]),
            },
          ],
          specs,
        )}`,
      );
    },
    price: async (id) => {
      const item = rows.find((r) => r.maSanPham === Number(id));
      const history = await api(`/san-pham/${id}/lich-su-gia`);
      formDialog(
        'Điều chỉnh giá • ' + item.maSKU,
        [
          { name: 'giaNiemYet', label: 'Giá bán đã gồm VAT (đ)', type: 'number', required: true, min: 0 },
          { name: 'giaToiThieu', label: 'Giá sàn (đ)', type: 'number', min: 0 },
          { name: 'ghiChu', label: 'Lý do thay đổi', wide: true },
        ],
        item,
        (data) => api(`/san-pham/${id}/gia`, 'PUT', data),
        `<div class="section-gap">${table(
          [
            { title: 'Hiệu lực', render: (r) => date(r.ngayHieuLuc) },
            { title: 'Giá bán', num: true, render: (r) => money(r.giaNiemYet) },
            { title: 'Người đổi', key: 'nguoiThayDoi' },
          ],
          history,
        )}</div>`,
      );
    },
    delete: (id) =>
      confirmAction(
        'Xóa sản phẩm',
        'Sản phẩm sẽ ngừng xuất hiện trong danh mục đang dùng. Lịch sử giao dịch được giữ lại.',
        () => api('/san-pham/' + id, 'DELETE'),
      ),
  });
  await load(state.page);
}

const catalogTypes = {
  categories: {
    name: 'Danh mục',
    path: '/danh-muc',
    id: 'maDanhMuc',
    nameKey: 'tenDanhMuc',
    fields: [
      { name: 'tenDanhMuc', label: 'Tên danh mục', required: true },
      { name: 'maDanhMucCha', label: 'Danh mục cha', type: 'integer', min: 1 },
      { name: 'thuTuHienThi', label: 'Thứ tự hiển thị', type: 'integer', default: 0, min: 0 },
      { name: 'moTa', label: 'Mô tả', type: 'textarea', wide: true },
    ],
  },
  brands: {
    name: 'Thương hiệu',
    path: '/thuong-hieu',
    id: 'maThuongHieu',
    nameKey: 'tenThuongHieu',
    fields: [
      { name: 'tenThuongHieu', label: 'Tên thương hiệu', required: true },
      { name: 'quocGia', label: 'Quốc gia' },
      { name: 'moTa', label: 'Mô tả', type: 'textarea', wide: true },
    ],
  },
  suppliers: {
    name: 'Nhà cung cấp',
    path: '/nha-cung-cap',
    id: 'maNCC',
    nameKey: 'tenNCC',
    fields: [
      { name: 'tenNCC', label: 'Tên nhà cung cấp', required: true },
      { name: 'maSoThue', label: 'Mã số thuế' },
      { name: 'soDienThoai', label: 'Số điện thoại' },
      { name: 'email', label: 'Email', type: 'email' },
      { name: 'nguoiLienHe', label: 'Người liên hệ' },
      { name: 'diaChi', label: 'Địa chỉ' },
    ],
  },
};
export async function catalog(root) {
  let selected = 'categories';
  root.innerHTML =
    head('Danh mục & đối tác', 'Sắp xếp ngành hàng và quản lý nguồn cung.') +
    `<div class="row" style="margin-bottom:20px">${Object.entries(catalogTypes)
      .filter(([k]) => k !== 'suppliers' || can('NHA_CUNG_CAP_QUAN_LY', 'KHO_NHAP'))
      .map(([k, v]) => btn(v.name, 'tab', k))
      .join('')}</div><div id="catalog"></div>`;
  let rows = [];
  const load = async () => {
    const cfg = catalogTypes[selected];
    rows = await api(cfg.path);
    const writable = can(selected === 'suppliers' ? 'NHA_CUNG_CAP_QUAN_LY' : 'SAN_PHAM_QUAN_LY');
    const fields = cfg.fields.map((f) =>
      f.name === 'maDanhMucCha'
        ? { ...f, type: 'select', numeric: true, options: opt(rows, cfg.id, cfg.nameKey) }
        : f,
    );
    const box = $('#catalog', root);
    box.innerHTML = `<section class="card"><div class="card-head"><h2>${h(cfg.name)}</h2>${writable ? btn('+ Thêm mới', 'new', '', 'primary') : ''}</div>${table(
      [
        { title: 'Tên', key: cfg.nameKey },
        ...cfg.fields
          .filter((f) => f.name !== cfg.nameKey)
          .slice(0, 3)
          .map((f) => ({ title: f.label, key: f.name })),
        {
          title: '',
          render: (r) =>
            writable
              ? btn('Sửa', 'edit', r[cfg.id], 'small') + btn('Xóa', 'delete', r[cfg.id], 'small ghost danger')
              : '',
        },
      ],
      rows,
    )}</section>`;
    bind(box, {
      new: () =>
        formDialog('Thêm ' + cfg.name.toLowerCase(), fields, {}, (data) => api(cfg.path, 'POST', data)),
      edit: (id) =>
        formDialog(
          'Sửa ' + cfg.name.toLowerCase(),
          fields,
          rows.find((r) => r[cfg.id] === Number(id)),
          (data) => api(cfg.path + '/' + id, 'PUT', data),
        ),
      delete: (id) =>
        confirmAction('Xóa ' + cfg.name, 'Xác nhận xóa mục này khỏi danh mục đang dùng?', () =>
          api(cfg.path + '/' + id, 'DELETE'),
        ),
    });
    box.addEventListener('click', (e) => e.stopPropagation());
  };
  bind(root, {
    tab: async (id) => {
      selected = id;
      await load();
    },
  });
  await load();
}

export const customerFields = [
  { name: 'hoTen', label: 'Họ và tên', required: true },
  { name: 'soDienThoai', label: 'Số điện thoại', required: true },
  { name: 'email', label: 'Email', type: 'email' },
  { name: 'diaChi', label: 'Địa chỉ' },
  { name: 'tenCongTy', label: 'Tên công ty' },
  { name: 'maSoThue', label: 'Mã số thuế' },
];
export async function customers(root) {
  let rows = [];
  root.innerHTML =
    head(
      'Khách hàng',
      'Thông tin liên hệ phục vụ hóa đơn, voucher và bảo hành.',
      can('POS_BAN_HANG', 'BAO_HANH_TIEP_NHAN') ? btn('+ Thêm khách hàng', 'new', '', 'primary') : '',
    ) +
    `<section class="card"><form class="toolbar"><input type="search" name="keyword" placeholder="Tìm theo tên hoặc số điện thoại"><button>Tìm kiếm</button></form><div id="list"></div></section>`;
  const load = async (page = 0) => {
    const result = await api(
      '/khach-hang?' + qs({ keyword: $('[name=keyword]', root).value, page, size: 20 }),
    );
    rows = result.items;
    const box = $('#list', root);
    box.innerHTML = table(
      [
        { title: 'Khách hàng', key: 'hoTen' },
        { title: 'Điện thoại', key: 'soDienThoai' },
        { title: 'Email', key: 'email' },
        { title: 'Địa chỉ', key: 'diaChi' },
        {
          title: '',
          render: (r) =>
            can('POS_BAN_HANG', 'BAO_HANH_TIEP_NHAN') ? btn('Sửa', 'edit', r.maKhachHang, 'small') : '',
        },
      ],
      rows,
    );
    pager(box, result, load);
  };
  $('form', root).onsubmit = (e) => {
    e.preventDefault();
    load().catch((err) => toast(err.message, true));
  };
  bind(root, {
    new: () => formDialog('Thêm khách hàng', customerFields, {}, (data) => api('/khach-hang', 'POST', data)),
    edit: (id) =>
      formDialog(
        'Cập nhật khách hàng',
        customerFields,
        rows.find((r) => r.maKhachHang === Number(id)),
        (data) => api('/khach-hang/' + id, 'PUT', data),
      ),
  });
  await load();
}

export async function stock(root) {
  const [products, alerts] = await Promise.all([all('/san-pham'), api('/kho/canh-bao')]);
  const ids = new Set(alerts.map((r) => r.maSanPham));
  root.innerHTML =
    head('Tồn kho & cảnh báo', 'Theo dõi tồn hiện tại, ngưỡng bổ sung và vị trí xếp hàng.') +
    `<div class="grid stats"><div class="card stat accent"><div class="stat-label">Mặt hàng cần bổ sung</div><strong>${alerts.length}</strong><small>Tồn kho chạm hoặc dưới ngưỡng tối thiểu</small></div><div class="card stat"><div class="stat-label">Tổng số lượng trong kho</div><strong>${products.reduce((s, r) => s + r.slTon, 0).toLocaleString('vi-VN')}</strong><small>${products.length} mã sản phẩm</small></div></div><section class="card"><div class="toolbar"><input id="stock-search" type="search" placeholder="Tìm tên hoặc SKU…"><label class="check-field"><input id="only-alert" type="checkbox"> Chỉ hiện cảnh báo</label></div><div id="stock-list"></div></section>`;
  const render = () => {
    const q = $('#stock-search', root).value.toLocaleLowerCase();
    $('#stock-list', root).innerHTML = table(
      [
        {
          title: 'Sản phẩm',
          render: (r) => `<b>${h(r.tenSanPham)}</b><span class="sub mono">${h(r.maSKU)}</span>`,
        },
        { title: 'Tồn hiện tại', num: true, key: 'slTon' },
        { title: 'Tối thiểu', num: true, key: 'tonToiThieu' },
        {
          title: 'Tình trạng',
          render: (r) =>
            ids.has(r.maSanPham)
              ? '<span class="badge warn">Cần bổ sung</span>'
              : '<span class="badge good">Đủ tồn</span>',
        },
        { title: '', render: (r) => btn('Xem vị trí / ngưỡng', 'threshold', r.maSanPham, 'small') },
      ],
      products.filter(
        (r) =>
          (!$('#only-alert', root).checked || ids.has(r.maSanPham)) &&
          (r.tenSanPham + ' ' + r.maSKU).toLocaleLowerCase().includes(q),
      ),
    );
  };
  $('#stock-search', root).oninput = render;
  $('#only-alert', root).onchange = render;
  bind(root, {
    threshold: async (id) => {
      const row = await api('/kho/ton/' + id);
      const editable = can('KHO_NHAP', 'KHO_KIEM_KE');
      if (!editable) {
        modal(
          'Vị trí tồn kho',
          `<p>${h(row.tenSanPham)}</p><p>Vị trí: ${h(row.viTriKho || 'Chưa xếp vị trí')}</p><p>Tối đa: ${h(row.tonToiDa ?? 'Không giới hạn')}</p>`,
        );
        return;
      }
      formDialog(
        'Ngưỡng tồn & vị trí',
        [
          { name: 'tonToiThieu', label: 'Tồn tối thiểu', type: 'integer', min: 0, required: true },
          { name: 'tonToiDa', label: 'Tồn tối đa', type: 'integer', min: 0 },
          { name: 'viTriKho', label: 'Kệ / vị trí kho', wide: true },
        ],
        row,
        (data) => api(`/kho/ton/${id}/nguong`, 'PUT', data),
      );
    },
  });
  render();
}

export async function serials(root) {
  root.innerHTML =
    head('Serial / IMEI', 'Tra cứu vòng đời từng thiết bị từ nhập kho đến bảo hành.') +
    `<section class="card"><form class="toolbar"><input type="search" name="ma" placeholder="Nhập Serial hoặc IMEI để tra cứu chính xác"><button>Tìm thiết bị</button><select id="serial-status" style="max-width:170px"><option value="">Mọi trạng thái</option>${options(
      ['trong_kho', 'da_ban', 'bao_hanh', 'hong', 'mat'],
    )
      .map((o) => `<option value="${o.value}">${h(o.text)}</option>`)
      .join('')}</select></form><div id="list"></div></section>`;
  const load = async (page = 0) => {
    const result = await api(
      '/kho/serial?' + qs({ page, size: 20, trangThai: $('#serial-status', root).value }),
    );
    const box = $('#list', root);
    box.innerHTML = table(
      [
        {
          title: 'Serial',
          render: (r) => `<b class="mono">${h(r.soSerial)}</b><span class="sub">${h(r.imei)}</span>`,
        },
        { title: 'Sản phẩm', key: 'tenSanPham' },
        { title: 'SKU', key: 'maSKU' },
        { title: 'Trạng thái', render: (r) => badge(r.trangThai) },
        { title: 'Nhập kho', render: (r) => date(r.ngayNhap) },
        { title: '', render: (r) => btn('Lịch sử', 'history', r.soSerial, 'small') },
      ],
      result.items,
    );
    pager(box, result, load);
  };
  const show = async (code) => {
    const result = await api('/kho/serial/tra-cuu?' + qs({ ma: code }));
    modal(
      'Lịch sử thiết bị',
      `<div class="status-line"><b>${h(result.serial.soSerial)}</b>${badge(result.serial.trangThai)}</div><p>${h(result.serial.tenSanPham)} <span class="mono">${h(result.serial.maSKU)}</span></p><p class="note">Hóa đơn: ${h(result.soHoaDon || 'Chưa bán')} • Ngày mua: ${date(result.ngayMua)}</p><div class="timeline">${result.lichSu.map((r) => `<p><b>${h(label(r.trangThaiCu))} → ${h(label(r.trangThaiMoi))}</b><span class="sub">${date(r.thoiGian)} • ${h(r.thucHienBoi || 'Hệ thống')}</span>${h(r.ghiChu)}</p>`).join('') || '<p>Chưa có lịch sử.</p>'}</div>`,
    );
  };
  $('form', root).onsubmit = (e) => {
    e.preventDefault();
    show($('[name=ma]', root).value).catch((err) => toast(err.message, true));
  };
  $('#serial-status', root).onchange = () => load().catch((err) => toast(err.message, true));
  bind(root, { history: show });
  await load();
}

export async function reports(root) {
  const reportOptions = [
    ['doanh-thu', 'Doanh thu theo thời gian'],
    ['doanh-thu-quay', 'Doanh thu theo quầy POS'],
    ['doanh-thu-thu-ngan', 'Doanh thu theo thu ngân'],
    ['doanh-thu-nganh-hang', 'Doanh thu theo ngành hàng'],
    ['doanh-thu-san-pham', 'Sản phẩm bán chạy'],
    ['ton-kho', 'Tồn kho tổng hợp'],
    ['ton-kho-serial', 'Serial còn trong kho'],
    ['ton-lau', 'Hàng tồn lâu'],
    ['chenh-lech-ton', 'Đối soát tồn kho'],
  ];
  root.innerHTML =
    head('Báo cáo & thống kê', 'Doanh thu, hiệu suất bán hàng và tình hình hàng hóa.') +
    `<section class="card"><form class="toolbar">${field({ name: 'loai', label: 'Báo cáo', required: true, options: reportOptions.map(([value, text]) => ({ value, text })) }, 'doanh-thu')}${field({ name: 'tu', label: 'Từ ngày', type: 'date' }, today())}${field({ name: 'den', label: 'Đến ngày', type: 'date' }, today())}${field(
      {
        name: 'nhom',
        label: 'Nhóm thời gian',
        options: [
          { value: 'gio', text: 'Theo giờ' },
          { value: 'ngay', text: 'Theo ngày' },
          { value: 'thang', text: 'Theo tháng' },
        ],
      },
      'ngay',
    )}${field({ name: 'soNgay', label: 'Ngưỡng tồn lâu (ngày)', type: 'integer', min: 1 }, 90)}<button class="primary">Xem báo cáo</button></form><div class="card-head"><h2 id="report-title">Dữ liệu báo cáo</h2><div class="row">${can('BAO_CAO_XUAT') ? btn('↓ Excel', 'xlsx', '', 'small') + btn('↓ PDF', 'pdf', '', 'small') : ''}</div></div><p id="report-note" class="note" style="padding:0 24px"></p><div id="report-table"></div></section>`;
  let query;
  const load = async () => {
    const values = Object.fromEntries(new FormData($('form', root)));
    if (values.tu && values.den && values.tu > values.den)
      throw new Error('Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.');
    const type = values.loai;
    delete values.loai;
    query = '/bao-cao/' + type + '?' + qs(values);
    const result = await api(query);
    $('#report-title', root).textContent = result.tieuDe;
    $('#report-note', root).textContent = result.moTa || '';
    const rows = [...result.dong, ...(result.tong ? [result.tong] : [])];
    $('#report-table', root).innerHTML = table(
      result.cot.map((c, i) => ({
        title: c,
        render: (r) => (typeof r[i] === 'number' ? Number(r[i]).toLocaleString('vi-VN') : h(r[i] ?? '—')),
      })),
      rows,
    );
  };
  $('form', root).onsubmit = (e) => {
    e.preventDefault();
    load().catch((err) => toast(err.message, true));
  };
  bind(root, {
    xlsx: () => download(query.replace('?', '/xuat?') + '&dinhDang=xlsx'),
    pdf: () => download(query.replace('?', '/xuat?') + '&dinhDang=pdf'),
  });
  await load();
}

export async function admin(root) {
  const tabs = [
    ['users', 'Người dùng', 'HE_THONG_NGUOI_DUNG'],
    ['roles', 'Vai trò & quyền', 'HE_THONG_PHAN_QUYEN'],
    ['counters', 'Quầy POS', 'HE_THONG_QUAY_POS'],
    ['logs', 'Nhật ký', 'HE_THONG_NHAT_KY'],
  ].filter((t) => can(t[2]));
  let selected = tabs[0][0],
    rows = [];
  root.innerHTML =
    head('Quản trị hệ thống', 'Tài khoản vận hành, phân quyền và cấu hình quầy POS.') +
    `<div class="row" style="margin-bottom:20px">${tabs.map(([k, n]) => btn(n, 'tab', k)).join('')}</div><div id="admin-list"></div>`;
  const cfgs = {
    users: {
      path: '/he-thong/nguoi-dung',
      id: 'maNhanVien',
      columns: [
        { title: 'Tên người dùng', key: 'hoTen' },
        { title: 'Đăng nhập', key: 'tenDangNhap' },
        { title: 'Vai trò', key: 'tenVaiTro' },
        { title: 'Trạng thái', render: (r) => badge(r.trangThai) },
      ],
    },
    roles: {
      path: '/he-thong/vai-tro',
      id: 'maVaiTro',
      columns: [
        { title: 'Vai trò', key: 'tenVaiTro' },
        { title: 'Mô tả', key: 'moTa' },
        { title: 'Số quyền', render: (r) => r.maQuyens.length },
        { title: 'Trạng thái', render: (r) => badge(r.trangThai ? 'hoat_dong' : 'vo_hieu') },
      ],
    },
    counters: {
      path: '/he-thong/quay?all=true',
      id: 'maQuay',
      columns: [
        { title: 'Mã quầy', key: 'maQuayCode' },
        { title: 'Tên quầy', key: 'tenQuay' },
        { title: 'Vị trí', key: 'viTri' },
        { title: 'Địa chỉ IP', key: 'diaChiIP' },
        { title: 'Trạng thái', render: (r) => badge(r.trangThai) },
      ],
    },
    logs: {
      path: '/he-thong/nhat-ky',
      id: 'maNhatKy',
      columns: [
        { title: 'Thời gian', render: (r) => date(r.thoiGian) },
        { title: 'Người thực hiện', key: 'hoTen' },
        { title: 'Thao tác', key: 'hanhDong' },
        { title: 'Đối tượng', key: 'bangTacDong' },
        { title: 'Ghi nhận', key: 'giaTriMoi' },
      ],
    },
  };
  let logPage = 0;
  const load = async () => {
    const cfg = cfgs[selected];
    rows = await api(cfg.path + (selected === 'logs' ? '?page=' + logPage : ''));
    const box = $('#admin-list', root);
    box.innerHTML = `<section class="card"><div class="card-head"><h2>${h(tabs.find((t) => t[0] === selected)[1])}</h2>${selected === 'logs' ? btn('Trước', 'prev', '', 'small') + btn('Sau', 'next', '', 'small') : btn('+ Thêm mới', 'new', '', 'primary')}</div>${table([...cfg.columns, ...(selected === 'logs' ? [] : [{ title: '', render: (r) => (selected === 'roles' && r.tenVaiTro === 'Quản trị hệ thống' ? '<span class="muted">Được bảo vệ</span>' : btn('Cập nhật', 'edit', r[cfg.id], 'small')) }])], rows)}</section>`;
    bind(box, {
      new: () => edit(),
      edit,
      prev: async () => {
        logPage = Math.max(0, logPage - 1);
        await load();
      },
      next: async () => {
        logPage++;
        await load();
      },
    });
    box.addEventListener('click', (e) => e.stopPropagation());
  };
  const edit = async (id) => {
    const cfg = cfgs[selected],
      row = rows.find((r) => r[cfg.id] === Number(id)) || {};
    let fields = [],
      extra = '',
      setup;
    if (selected === 'users') {
      const roles = await api('/he-thong/vai-tro');
      fields = [
        { name: 'maSoNhanVien', label: 'Mã nhân viên', required: true },
        { name: 'hoTen', label: 'Họ tên', required: true },
        { name: 'tenDangNhap', label: 'Tên đăng nhập', required: true },
        {
          name: 'matKhau',
          label: id ? 'Mật khẩu mới (để trống để giữ)' : 'Mật khẩu (ít nhất 8 ký tự)',
          type: 'password',
          required: !id,
        },
        { name: 'email', label: 'Email', type: 'email' },
        { name: 'soDienThoai', label: 'Điện thoại' },
        {
          name: 'maVaiTro',
          label: 'Vai trò',
          options: opt(
            roles.filter((r) => r.trangThai),
            'maVaiTro',
            'tenVaiTro',
          ),
          required: true,
          numeric: true,
        },
        {
          name: 'trangThai',
          label: 'Trạng thái',
          options: ['hoat_dong', 'bi_khoa', 'vo_hieu'],
          required: true,
          default: 'hoat_dong',
        },
      ];
    }
    if (selected === 'counters')
      fields = [
        { name: 'maQuayCode', label: 'Mã quầy (chữ, số, _ hoặc -)', required: true },
        { name: 'tenQuay', label: 'Tên quầy', required: true },
        { name: 'viTri', label: 'Vị trí' },
        { name: 'diaChiIP', label: 'IP máy POS (không bắt buộc)' },
        { name: 'tenMayQuet', label: 'Tên máy quét' },
        { name: 'tenMayIn', label: 'Tên máy in' },
        {
          name: 'trangThai',
          label: 'Trạng thái',
          options: ['hoat_dong', 'bao_tri', 'ngung_hoat_dong'],
          required: true,
          default: 'hoat_dong',
        },
      ];
    if (selected === 'roles') {
      const permissions = await api('/he-thong/quyen');
      fields = [
        { name: 'tenVaiTro', label: 'Tên vai trò', required: true },
        { name: 'moTa', label: 'Mô tả' },
        { name: 'trangThai', label: 'Vai trò đang hoạt động', type: 'check', default: true },
      ];
      extra = `<div class="section-gap"><h3>Quyền chức năng</h3><div class="permission-grid">${permissions.map((p) => `<label class="check-field"><input name="permission" type="checkbox" value="${p.maQuyen}" ${(row.maQuyens || []).includes(p.maQuyen) ? 'checked' : ''}>${h(p.tenQuyen)}</label>`).join('')}</div></div>`;
    }
    formDialog(
      id ? 'Cập nhật cấu hình' : 'Thêm cấu hình',
      fields,
      row,
      async (data, form) => {
        if (selected === 'roles')
          data.maQuyens = $$('input[name=permission]:checked', form).map((el) => Number(el.value));
        await api(cfg.path.split('?')[0] + (id ? '/' + id : ''), id ? 'PUT' : 'POST', data);
      },
      extra,
      setup,
    );
  };
  bind(root, {
    tab: async (id) => {
      selected = id;
      await load();
    },
  });
  await load();
}
