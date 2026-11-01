import { dashboard, deviceArt } from './retail.js';
import { state, $, $$, h, api, can, icon, toast, formDialog, today } from './core.js';
import { products, catalog, customers, stock, serials, reports, admin } from './pages.js';
import { pos, invoices } from './pos.js';
import { receiving, counts, promotions, vouchers, warranty, policies, returns } from './operations.js';
const routes = [
  ['dashboard', 'Tổng quan', 'grid', 'Điều hành', ['BAO_CAO_XEM'], dashboard],
  ['pos', 'Bán hàng POS', 'pos', 'Điều hành', ['POS_BAN_HANG'], pos],
  ['invoices', 'Hóa đơn', 'list', 'Điều hành', ['POS_XEM_HOA_DON'], invoices],
  ['products', 'Sản phẩm', 'box', 'Hàng hóa & kho', ['SAN_PHAM_XEM'], products],
  ['catalog', 'Danh mục & đối tác', 'list', 'Hàng hóa & kho', ['SAN_PHAM_XEM'], catalog],
  ['receiving', 'Nhập kho', 'arrow', 'Hàng hóa & kho', ['KHO_NHAP', 'KHO_XEM_TON'], receiving],
  ['stock', 'Tồn kho & cảnh báo', 'box', 'Hàng hóa & kho', ['KHO_XEM_TON'], stock],
  ['serials', 'Serial / IMEI', 'list', 'Hàng hóa & kho', ['KHO_XEM_TON'], serials],
  ['counts', 'Kiểm kê', 'check', 'Hàng hóa & kho', ['KHO_KIEM_KE'], counts],
  [
    'customers',
    'Khách hàng',
    'users',
    'Dịch vụ',
    ['POS_BAN_HANG', 'BAO_HANH_TRA_CUU', 'POS_XEM_HOA_DON'],
    customers,
  ],
  ['promotions', 'Khuyến mãi', 'tag', 'Dịch vụ', ['KHUYEN_MAI_XEM', 'KHUYEN_MAI_QUAN_LY'], promotions],
  ['vouchers', 'Voucher', 'tag', 'Dịch vụ', ['KHUYEN_MAI_QUAN_LY'], vouchers],
  ['warranty', 'Bảo hành', 'shield', 'Dịch vụ', ['BAO_HANH_TRA_CUU', 'BAO_HANH_TIEP_NHAN'], warranty],
  ['policies', 'Chính sách bảo hành', 'shield', 'Dịch vụ', ['BAO_HANH_CHINH_SACH'], policies],
  [
    'returns',
    'Trả hàng',
    'arrow',
    'Dịch vụ',
    ['POS_TRA_HANG', 'POS_DUYET_TRA_HANG', 'POS_XEM_HOA_DON'],
    returns,
  ],
  ['reports', 'Báo cáo', 'chart', 'Quản lý', ['BAO_CAO_XEM'], reports],
  [
    'admin',
    'Quản trị hệ thống',
    'settings',
    'Quản lý',
    ['HE_THONG_NGUOI_DUNG', 'HE_THONG_PHAN_QUYEN', 'HE_THONG_QUAY_POS', 'HE_THONG_NHAT_KY'],
    admin,
  ],
];
let revision = 0;
function login(message = '') {
  if ($('#modal').open) $('#modal').close();
  $('#app').innerHTML =
    '<div class="login"><div class="login-art"><div class="brand"><span class="brand-mark"><i></i><i></i><i></i><i></i></span><span>ĐIỆN MÁY<small>HỆ THỐNG QUẢN LÝ NỘI BỘ</small></span></div><div class="login-edition"><span>ĐIỂM BÁN / 01</span><span>QLDAPM</span></div><h1>Sổ vận hành<br>siêu thị điện máy.</h1><div class="appliance-strip"><div>' +
    deviceArt({ tenDanhMuc: 'Tivi', tenSanPham: 'Tivi' }) +
    '</div><div>' +
    deviceArt({ tenDanhMuc: 'Tủ lạnh', tenSanPham: 'Tủ lạnh' }) +
    '</div><div>' +
    deviceArt({ tenDanhMuc: 'Điện thoại', tenSanPham: 'Điện thoại' }) +
    '</div></div><div class="login-index"><div><b>01</b><span>Hàng hóa & Serial / IMEI</span></div><div><b>02</b><span>Giao dịch tại quầy POS</span></div><div><b>03</b><span>Khuyến mãi & bảo hành</span></div><div><b>04</b><span>Báo cáo & đối soát</span></div></div><div class="login-imprint">MỘT SIÊU THỊ / DỮ LIỆU TẬP TRUNG</div></div><div class="login-panel"><form><div class="login-form-number">NHÂN VIÊN / ĐĂNG NHẬP</div><h2>Vào bàn làm việc</h2><p>Dùng tài khoản được cấp cho vai trò của bạn.</p><label class="field">Tên đăng nhập<input name="tenDangNhap" required autocomplete="username" autofocus maxlength="100" placeholder="Nhập tên tài khoản"></label><label class="field">Mật khẩu<input name="matKhau" type="password" required autocomplete="current-password" placeholder="Nhập mật khẩu"></label><div class="error" ' +
    (message ? '' : 'hidden') +
    '>' +
    h(message) +
    '</div><button type="submit" class="primary">Đăng nhập <span>↗</span></button><div class="login-access-note"><b>Quyền truy cập theo vai trò</b><p>Thu ngân, kho, CSKH và quản lý có các bàn làm việc riêng. Liên hệ quản trị nếu bạn chưa có tài khoản.</p></div></form><div class="login-footnote">QLDAPM <span>ỨNG DỤNG WEB NỘI BỘ</span></div></div></div>';
  const form = $('form');
  form.onsubmit = async (e) => {
    e.preventDefault();
    $('button', form).disabled = true;
    try {
      state.user = await api('/auth/login', 'POST', Object.fromEntries(new FormData(form)));
      await api('/auth/me');
      shell();
      await render();
    } catch (err) {
      const error = $('.error', form);
      error.hidden = false;
      error.textContent = err.message;
    } finally {
      if (form.isConnected) $('button', form).disabled = false;
    }
  };
}
function shell() {
  const available = routes.filter((r) => can(...r[4]));
  let group = '';
  $('#app').innerHTML =
    `<div class="layout"><aside class="sidebar"><div class="brand"><span class="brand-mark"><i></i><i></i><i></i><i></i></span><span>ĐIỆN MÁY<small>SỔ VẬN HÀNH</small></span></div><nav>${available
      .map((r) => {
        const heading = group !== r[3] ? `<div class="nav-group">${h(r[3].toUpperCase())}</div>` : '';
        group = r[3];
        return (
          heading + `<a href="#${r[0]}" class="nav-link" data-route="${r[0]}">${icon(r[2])}${h(r[1])}</a>`
        );
      })
      .join(
        '',
      )}</nav><div class="sidebar-foot"><span class="online-dot"></span>Dữ liệu tập trung<br><span class="sub">QLDAPM / WEB NỘI BỘ</span></div></aside><main class="main"><header class="topbar"><div class="row"><button class="mobile-menu ghost" aria-label="Mở menu">☰</button><span class="crumb">ĐIỂM BÁN 01 <span class="crumb-divider">/</span> <b id="crumb"></b></span></div><div class="top-actions"><span class="muted" style="font-size:11px">${h(today())}</span><div class="user-chip"><div class="avatar">${h(
      state.user.hoTen
        .split(' ')
        .slice(-2)
        .map((s) => s[0])
        .join(''),
    )}</div><div><b style="font-size:12px">${h(state.user.hoTen)}</b><small>${h(state.user.tenVaiTro)}</small></div></div><button class="ghost small" id="password" title="Đổi mật khẩu">Mật khẩu</button><button class="ghost small" id="logout">Thoát</button></div></header><div class="content" id="content"></div></main></div>`;
  $('.mobile-menu').onclick = () => $('.sidebar').classList.toggle('open');
  $('#logout').onclick = async () => {
    try {
      await api('/auth/logout', 'POST');
      state.user = null;
      login();
    } catch (err) {
      toast(err.message, true);
    }
  };
  $('#password').onclick = () =>
    formDialog(
      'Đổi mật khẩu',
      [
        { name: 'matKhauCu', label: 'Mật khẩu hiện tại', type: 'password', required: true },
        { name: 'matKhauMoi', label: 'Mật khẩu mới (ít nhất 8 ký tự)', type: 'password', required: true },
      ],
      {},
      async (data) => {
        await api('/auth/mat-khau', 'PUT', data);
        state.user = null;
        setTimeout(() => login('Mật khẩu đã đổi. Hãy đăng nhập lại.'), 0);
      },
    );
}
async function render() {
  if (!state.user) return;
  const r =
    routes.find((r) => r[0] === location.hash.slice(1) && can(...r[4])) || routes.find((r) => can(...r[4]));
  if (!r) {
    $('#content').innerHTML = '<p>Tài khoản chưa được cấp quyền. Liên hệ quản trị viên.</p>';
    return;
  }
  if (state.route !== r[0]) state.page = 0;
  state.route = r[0];
  document.body.dataset.view = r[0];
  const token = ++revision;
  $('#crumb').textContent = r[1];
  $$('.nav-link').forEach((a) => a.classList.toggle('active', a.dataset.route === r[0]));
  $('.sidebar').classList.remove('open');
  const root = $('#content');
  root.innerHTML = '<div class="loading-line"></div><div class="empty">Đang tải dữ liệu…</div>';
  try {
    const stage = document.createElement('div');
    await r[5](stage);
    if (token === revision) root.replaceChildren(stage);
  } catch (err) {
    if (token === revision && state.user) {
      root.innerHTML = `<div class="error">${h(err.message)}</div><button id="retry">Thử lại</button>`;
      $('#retry').onclick = render;
    }
  }
}
state.refresh = render;
window.addEventListener('hashchange', render);
window.addEventListener('session-expired', () => login('Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.'));
try {
  state.user = await api('/auth/me');
  shell();
  await render();
} catch {
  login();
}
