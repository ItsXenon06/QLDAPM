export const state = { user: null, route: '', page: 0, refresh: () => {} };
export const $ = (q, root = document) => root.querySelector(q);
export const $$ = (q, root = document) => [...root.querySelectorAll(q)];
export const h = (v) =>
  String(v ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c],
  );
export const money = (v) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(Number(v || 0));
export const date = (v) => (v ? new Date(v).toLocaleString('vi-VN', { hour12: false }) : '—');
export const today = () =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
export const can = (...permissions) => permissions.some((p) => state.user?.quyens.includes(p));
export function toast(message, bad = false) {
  const el = document.createElement('div');
  el.className = 'toast' + (bad ? ' bad' : '');
  el.textContent = message;
  $('#toasts').append(el);
  setTimeout(() => el.remove(), 6500);
}
export const labels = {
  hoat_dong: 'Hoạt động',
  bi_khoa: 'Bị khóa',
  vo_hieu: 'Vô hiệu',
  dang_kinh_doanh: 'Đang kinh doanh',
  ngung_kinh_doanh: 'Ngừng kinh doanh',
  trong_kho: 'Trong kho',
  da_ban: 'Đã bán',
  bao_hanh: 'Bảo hành',
  mat: 'Thất lạc',
  hong: 'Hỏng',
  loi: 'Lỗi',
  tra_ncc: 'Trả NCC',
  nhap: 'Bản nháp',
  mot_phan: 'Nhận một phần',
  hoan_thanh: 'Hoàn thành',
  huy: 'Đã hủy',
  dang_kiem: 'Đang kiểm kê',
  dang_lap: 'Đang lập',
  tam_giu: 'Tạm giữ',
  da_thanh_toan: 'Đã thanh toán',
  da_huy: 'Đã hủy',
  con_han: 'Còn hạn',
  het_han: 'Hết hạn',
  da_huy: 'Đã hủy',
  tiep_nhan: 'Tiếp nhận',
  dang_xu_ly: 'Đang xử lý',
  hoan_tat: 'Hoàn tất',
  da_tra_khach: 'Đã trả khách',
  tu_choi: 'Từ chối',
  cho_duyet: 'Chờ duyệt',
  da_duyet: 'Đã duyệt',
  tam_dung: 'Tạm dừng',
  chua_dung: 'Chưa dùng',
  da_dung: 'Đã dùng',
  bao_tri: 'Bảo trì',
  ngung_hoat_dong: 'Ngừng hoạt động',
  tien_mat: 'Tiền mặt',
  the_ngan_hang: 'Thẻ ngân hàng',
  qr_chuyen_khoan: 'Chuyển khoản QR',
  tra_gop: 'Trả góp',
  giam_phan_tram: 'Giảm phần trăm',
  giam_tien: 'Giảm số tiền',
  mua_kem_gia_soc: 'Mua kèm giá sốc',
  tang_qua: 'Tặng quà',
  tang_voucher: 'Tặng voucher',
};
export const label = (v) => labels[v] || v || '—';
export const badge = (v) =>
  `<span class="badge ${['hoat_dong', 'da_thanh_toan', 'hoan_thanh', 'con_han', 'trong_kho', 'hoan_tat', 'da_tra_khach'].includes(v) ? 'good' : ['dang_kiem', 'cho_duyet', 'mot_phan', 'tam_giu', 'tiep_nhan', 'dang_xu_ly'].includes(v) ? 'warn' : ['bi_khoa', 'vo_hieu', 'huy', 'da_huy', 'mat', 'loi', 'het_han'].includes(v) ? 'bad' : ''}">${h(label(v))}</span>`;
export const icon = (name = 'box') =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${{ grid: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>', pos: '<rect x="3" y="3" width="18" height="13" rx="2"/><path d="M8 21h8m-4-5v5M7 7h10m-10 4h4"/>', box: '<path d="m3 7 9-4 9 4v10l-9 4-9-4Zm0 0 9 4 9-4M12 11v10m-4-16 9 4"/>', list: '<path d="M8 5h13M8 12h13M8 19h13M3 5h.01M3 12h.01M3 19h.01"/>', chart: '<path d="M3 3v18h18M7 16v-5m5 5V7m5 9V4"/>', shield: '<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z"/><path d="m8 12 3 3 5-6"/>', users: '<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3m1-16a3 3 0 0 1 0 6m3 10v-3a6 6 0 0 0-2-4"/>', tag: '<path d="M3 3h8l10 10-8 8L3 11Z"/><circle cx="7" cy="7" r="1"/>', check: '<path d="m5 12 4 4L20 5M20 12v8H4V4h10"/>', arrow: '<path d="M12 3v14m-5-5 5 5 5-5M4 21h16"/>', settings: '<circle cx="12" cy="12" r="3"/><path d="m9 3-1 3-3 1-2 5 2 5 3 1 1 3h6l1-3 3-1 2-5-2-5-3-1-1-3Z"/>' }[name] || '<path d="M4 5h16v15H4Zm4-3v6m8-6v6M4 11h16"/>'}</svg>`;
export function qs(data = {}) {
  return new URLSearchParams(
    Object.entries(data).filter(([, v]) => v !== null && v !== undefined && v !== ''),
  );
}
export async function api(path, method = 'GET', body) {
  const token = document.cookie
    .split('; ')
    .find((x) => x.startsWith('XSRF-TOKEN='))
    ?.split('=')
    .slice(1)
    .join('=');
  let res;
  try {
    res = await fetch('/api' + path, {
      method,
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {}),
      },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    });
  } catch {
    throw new Error('Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.');
  }
  if (!res.ok) {
    let data;
    try {
      data = await res.json();
    } catch {
      data = { message: 'Yêu cầu không thành công (' + res.status + ')' };
    }
    if (res.status === 401 && state.user) {
      state.user = null;
      window.dispatchEvent(new Event('session-expired'));
    }
    const details = Object.entries(data.errors || {})
      .map(([k, v]) => `${k}: ${v}`)
      .join('\n');
    const error = new Error((data.message || 'Yêu cầu không thành công') + (details ? '\n' + details : ''));
    error.status = res.status;
    error.code = data.code;
    throw error;
  }
  if (res.status === 204 || res.headers.get('content-length') === '0') return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}
export async function all(path) {
  let page = 0,
    rows = [];
  for (;;) {
    const result = await api(path + (path.includes('?') ? '&' : '?') + qs({ page, size: 100 }));
    if (Array.isArray(result)) return result;
    rows.push(...result.items);
    if (++page >= result.totalPages) return rows;
  }
}
export const btn = (text, action, id = '', style = '') =>
  `<button type="button" class="${style}" data-act="${h(action)}" data-id="${h(id)}">${h(text)}</button>`;
export const table = (columns, rows) =>
  `<div class="table-wrap"><table><thead><tr>${columns.map((c) => `<th class="${c.num ? 'num' : ''}">${h(c.title)}</th>`).join('')}</tr></thead><tbody>${rows.length ? rows.map((r) => `<tr>${columns.map((c) => `<td class="${c.num ? 'num' : ''}">${c.render ? c.render(r) : h(r[c.key] ?? '—')}</td>`).join('')}</tr>`).join('') : `<tr><td colspan="${columns.length}" class="empty">Chưa có dữ liệu phù hợp.</td></tr>`}</tbody></table></div>`;
export const head = (title, desc, actions = '') =>
  `<div class="page-head"><div><h1>${h(title)}</h1><p>${h(desc)}</p></div><div class="actions">${actions}</div></div>`;
export function bind(root, handlers) {
  root.onclick = async (e) => {
    const b = e.target.closest('[data-act]');
    if (!b || !root.contains(b) || !handlers[b.dataset.act]) return;
    const original = b.disabled;
    b.disabled = true;
    try {
      await handlers[b.dataset.act](b.dataset.id, b);
    } catch (err) {
      toast(err.message, true);
    } finally {
      if (b.isConnected) b.disabled = original;
    }
  };
}
export function pager(root, result, load) {
  const el = document.createElement('div');
  el.className = 'pager';
  el.innerHTML = `<span>${result.totalElements} kết quả • Trang ${result.page + 1}/${Math.max(1, result.totalPages)}</span><div class="row">${btn('← Trước', 'prev', '', 'small')}${btn('Sau →', 'next', '', 'small')}</div>`;
  root.append(el);
  $('[data-act=prev]', el).disabled = result.page === 0;
  $('[data-act=next]', el).disabled = result.page + 1 >= result.totalPages;
  bind(el, { prev: () => load(result.page - 1), next: () => load(result.page + 1) });
  el.addEventListener('click', (e) => e.stopPropagation());
}
export const opt = (values, key, name) => values.map((v) => ({ value: v[key], text: v[name] }));
export const options = (values) =>
  values.map((v) => (typeof v === 'string' ? { value: v, text: label(v) } : v));
export function field(f, value) {
  const name = h(f.name),
    valueText = h(value ?? f.default ?? ''),
    attrs = `name="${name}" ${f.required ? 'required' : ''} ${f.min !== undefined ? `min="${f.min}"` : ''} ${f.max !== undefined ? `max="${f.max}"` : ''} ${f.maxlength ? `maxlength="${f.maxlength}"` : ''}`;
  let input;
  if (f.type === 'check')
    return `<label class="check-field"><input type="checkbox" name="${name}" ${(value ?? f.default) ? 'checked' : ''}>${h(f.label)}</label>`;
  if (f.options)
    input = `<select ${attrs}>${!f.required ? '<option value="">— Không chọn —</option>' : ''}${options(
      f.options,
    )
      .map(
        (o) =>
          `<option value="${h(o.value)}" ${String(o.value) === String(value ?? f.default) ? 'selected' : ''}>${h(o.text)}</option>`,
      )
      .join('')}</select>`;
  else if (f.type === 'textarea')
    input = `<textarea ${attrs} rows="${f.rows || 3}" placeholder="${h(f.placeholder || '')}">${valueText}</textarea>`;
  else
    input = `<input type="${f.type === 'integer' ? 'number' : f.type || 'text'}" ${attrs} ${f.type === 'integer' ? 'step="1"' : f.type === 'number' ? 'step="0.01"' : ''} value="${valueText}" placeholder="${h(f.placeholder || '')}" ${f.type === 'password' ? 'autocomplete="new-password"' : ''}>`;
  return `<label class="field ${f.wide ? 'wide' : ''}">${h(f.label)}${f.required ? ' *' : ''}${input}${f.help ? `<span class="sub">${h(f.help)}</span>` : ''}</label>`;
}
export function values(form, fields) {
  const out = {};
  for (const f of fields) {
    const el = form.elements?.namedItem(f.name) || form.querySelector(`[name="${f.name}"]`);
    if (!el) continue;
    out[f.name] =
      f.type === 'check'
        ? el.checked
        : el.value === ''
          ? null
          : ['number', 'integer'].includes(f.type) || f.numeric
            ? Number(el.value)
            : el.value;
  }
  return out;
}
export function modal(title, body) {
  const dialog = $('#modal');
  if (dialog.open) dialog.close();
  dialog.innerHTML = `<div class="modal-head"><h2>${h(title)}</h2><button type="button" aria-label="Đóng" data-close>×</button></div><div class="modal-body">${body}</div>`;
  $('[data-close]', dialog).onclick = () => dialog.close();
  dialog.showModal();
  return dialog;
}
export function formDialog(title, fields, data = {}, save, extra = '', setup) {
  const dialog = modal(
    title,
    `<form id="dialog-form"><div class="form-grid">${fields.map((f) => field(f, data[f.name])).join('')}</div>${extra}<div class="error" hidden></div><div class="form-actions"><button type="button" data-cancel>Hủy</button><button class="primary" type="submit">Lưu thay đổi</button></div></form>`,
  );
  const form = $('form', dialog);
  $('[data-cancel]', dialog).onclick = () => dialog.close();
  form.onsubmit = async (e) => {
    e.preventDefault();
    const button = $('[type=submit]', form);
    button.disabled = true;
    $('.error', form).hidden = true;
    try {
      await save(values(form, fields), form);
      dialog.close();
      toast('Đã lưu thay đổi.');
      await state.refresh();
    } catch (err) {
      $('.error', form).textContent = err.message;
      $('.error', form).hidden = false;
    } finally {
      button.disabled = false;
    }
  };
  setup?.(form);
  return dialog;
}
export async function confirmAction(title, description, action) {
  return formDialog(title, [], {}, action, `<p>${h(description)}</p>`);
}
export async function download(path) {
  const response = await fetch('/api' + path, { credentials: 'same-origin' });
  if (!response.ok) throw new Error('Không xuất được báo cáo. Kiểm tra quyền và thử lại.');
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = decodeURIComponent(
    response.headers.get('Content-Disposition')?.match(/filename\*=UTF-8''([^;]+)/i)?.[1] ||
      'bao-cao.' + (path.includes('pdf') ? 'pdf' : 'xlsx'),
  );
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}
