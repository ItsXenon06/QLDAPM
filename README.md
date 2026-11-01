# QLDAPM — Electronics store management

Internal web application for one electronics store: Serial/IMEI and quantity-based inventory, receiving, stocktake, POS, promotions/vouchers, warranty, returns, reports and role-based administration. The interface is Vietnamese.

**Stack:** Java 21, Spring Boot 4.1.2, SQL Server, plain HTML/CSS/JavaScript. Spring Boot serves the frontend directly: no Node.js/npm or separate frontend server is needed.

## Requirements

- Git, **JDK 21** and PowerShell.
- **SQL Server 2019+** (Express/Developer works for the demo), SQL authentication, and a login allowed to create the demo database/tables and access its data.
- Microsoft **sqlcmd** ODBC command-line tools for the database helper. SSMS is useful for configuration/manual setup.
- Maven **3.9+** or included `mvnw.cmd` (downloads Maven 3.9.9).
- Internet for initial Maven downloads. Python 3 and Cloudflare Tunnel are optional for SePay callbacks.

## Clone and run the populated demo

```powershell
git clone <repository-url>
cd <cloned-folder>
Copy-Item .env.example .env
```

Edit `.env` with your own SQL connection:

```dotenv
DB_HOST=localhost
DB_PORT=1433
DB_NAME=QLDAPM_Demo
DB_USERNAME=your_sql_login
DB_PASSWORD=your_sql_password
ADMIN_PASSWORD=choose_a_unique_bootstrap_password
SHOW_SQL=false
SEPAY_ENABLED=false
SEPAY_TEST_MODE=true
```

Use your actual SQL Server TCP port: **1433** is the template example; the original developer uses **6000**. Enable TCP/IP, configure a known static port, enable SQL authentication/Mixed Mode if needed, and restart SQL Server after changing those settings. Verify the same login connects in SSMS to `localhost,<port>` first.

From the project root:

```powershell
.\scripts\Create-Demo-Database.ps1
$env:DB_NAME='QLDAPM_Demo'
.\scripts\Start-App.ps1 -Build
```

Open **http://localhost:8081**. Keep the application terminal running; Ctrl+C stops it. Run from the project root so `.env` is found. Environment variables override `.env`.

The helper creates the database using `database/001_initial_schema.sql` and populates it with `database/002_demo_seed.sql`. The full seed includes all category/partner improvements. It never drops a database or resets existing sales, stock or changed passwords. Never run the initial schema over an existing populated database.

Equivalent manual build/run:

```powershell
.\mvnw.cmd clean package
java -jar .\target\qldapm-0.0.1-SNAPSHOT.jar
```

After pulling code changes, stop the app and use `Start-App.ps1 -Build`; starting without `-Build` reuses an existing JAR.

For a fresh test baseline without deleting previous work:

```powershell
.\scripts\Create-Demo-Database.ps1 -Database QLDAPM_Demo_Run2
$env:DB_NAME='QLDAPM_Demo_Run2'
.\scripts\Start-App.ps1
```

SSMS alternative: open a copy of `001_initial_schema.sql`, replace `QLSieuThiDienMay` throughout that copy with your intended new demo database name, then execute it. Select that new database before executing `002_demo_seed.sql`.

## Demo logins

Initial password for all newly seeded demo accounts: **`Demo@2026!`**.

| Role | Login | Responsibilities |
|---|---|---|
| Administrator | `admin.demo` | All modules, users, roles/permissions, counters, audit |
| Manager | `quanly.demo` | Prices/promotions, approvals, reports, warranty policies, audit |
| Warehouse | `kho.demo` | Catalog, suppliers, receiving, stock, Serial/IMEI, stocktake |
| Cashier 1 | `thungan01.demo` | POS, customers, invoices and return requests |
| Cashier 2 | `thungan02.demo` | Independent cashier/counter testing |
| Customer service / warranty | `baohanh.demo` | Purchase/warranty lookup and warranty processing |

These are public synthetic demo credentials. Reseeding does not reset a password you changed. The separate bootstrap account `admin` uses `ADMIN_PASSWORD` and is created only if the app starts with no accounts. When seeding first, use `admin.demo`; the environment password is not its password.

The seed provides **16 SKUs, 19 categories/subcategories, 16 brands, 8 suppliers, 14 customers, 24 paid invoices, 2 held bills and 5 promotions**, plus voucher, receiving, warranty and return cases. Three counters are active; one is under maintenance. Empty example categories/brands are intentional and labelled clearly.

## One acceptance test set per role

Use a fresh demo database for the exact amounts below. Tests change data. Use separate browser profiles/private sessions for simultaneous roles; ordinary tabs share login cookies. At narrow widths, use **Mở menu**.

Run in this order: **admin → warehouse → cashier sale → warranty → cashier return request → manager approval → cashier completion**. Record generated invoice, Serial and return numbers.

### Administrator

1. Sign in as `admin.demo`. Inspect **Quản trị hệ thống → Người dùng / Vai trò & quyền / Quầy POS / Nhật ký**.
2. Create a temporary cashier user with unique employee code/login and password of at least 8 characters. Sign in as that user in another profile: cashier screens should appear, user/role management should not.
3. Disable the temporary user from the admin session; its old session must lose access on a subsequent request. Check the audit log.
4. Confirm the built-in administrator role is protected. `DEMO-Q04` is under maintenance and must not be offered for a new sale. Do not disable your own administrator account.

### Warehouse

1. Sign in as `kho.demo`. Under **Danh mục & đối tác**, inspect parent names, child grouping, brand examples and supplier contacts/status. Inactive `DEMO-NCC08` must not be offered for new receiving.
2. Open **Nhập kho → DEMO-PNK-002 → Chi tiết → Nhận hàng**. It orders **5 × DEMO-WM01 (LG AI DD 9 kg)**. Receive 1 with a unique Serial, e.g. `MANUAL-WM-001`; leave IMEI blank.
3. Expect **Nhận một phần**, received quantity and stock increased by 1, and that Serial **Trong kho**.
4. Create a **Kiểm kê** for only `DEMO-AC04`. A cashier attempting checkout of that SKU while the count is open must be blocked without stock changing. Enter the same actual quantity as the system count, save and complete it. Expect zero discrepancy and released freeze.

### Cashier

1. Sign in as `thungan01.demo`, choose **Quầy 01**, and use a new bill rather than a seeded held bill. Attach **Nguyễn Hải Yến / 0900000101**.
2. Add **DEMO-PH01-005** and **2 × DEMO-AC02**. Expect cable discount **38,000đ**, total **9,332,000đ**. If the Serial was sold/reserved during an earlier test, use a fresh demo database or another available A55 Serial and record it.
3. Select **Thanh toán**, choose the ordinary cashier-confirmed payment option if SePay is enabled, use **Tiền mặt**, tender **9,500,000đ**, and confirm payment. Expect change **168,000đ**, a paid receipt, phone **Đã bán**, and cable stock reduced by 2. Record the generated `HD-...` invoice number.
4. Inspect print preview. Selling the same Serial again must fail. Cashier 2 (`thungan02.demo`, **Quầy 02**) must operate independently and cannot edit cashier 1's draft.
5. After the warranty test, create a **Trả hàng** request for the recorded invoice: **1 cable**, **Nguyên hộp**, refund **Tiền mặt**. Expect **171,000đ**, **Chờ duyệt**, and no cashier approval ability. After manager approval, choose **Xác nhận đã hoàn tiền & nhận hàng**. Expect **Hoàn tất** and exactly 1 cable restored.

### Customer service / warranty

1. After the cashier sale, sign in as `baohanh.demo`; open **Bảo hành** and search the recorded sold Serial.
2. Verify the new invoice, customer, purchase date and warranty period. Also check lookup by invoice/customer phone.
3. Click **Tiếp nhận**, enter a test issue, customer and incoming condition, then save.
4. Open the request's **Chi tiết** and save each transition: **Đang xử lý → Hoàn tất → Đã trả khách**. Enter the processing result when completing it. The sold device must not remain stuck in warranty after collection.
5. This role must not manage users, prices, promotions or refund approvals.

### Manager

1. Sign in as `quanly.demo`; inspect **Tổng quan**, **Khuyến mãi**, **Voucher** and warranty policies. Vouchers show customer names and issuing invoices. Leave the cable promotion unchanged until the cashier test is finished.
2. Open the cashier-created return, verify the correct invoice/one cable/**171,000đ**, and click **Duyệt**. Ask the cashier to complete it; confirm final status.
3. Open **Báo cáo** for today; check revenue by day, counter, cashier/category, stock, aged stock and reconciliation. Export/open **Excel** and **PDF**; Vietnamese text and figures should be readable.
4. Confirm refunds appear in the relevant net-revenue report. Daily reports date refunds by completion; category/product reports attribute them to original sales. Read report descriptions when reconciling dates.
5. The manager's system-management access is limited to assigned permissions (audit viewing by default), not full user/role administration.

## Optional SePay QR test

Basic POS works with `SEPAY_ENABLED=false`. Automatic QR confirmation requires your **own SePay Test-mode account and HMAC secret**, configured locally:

```dotenv
SEPAY_ENABLED=true
SEPAY_TEST_MODE=true
SEPAY_BANK=Vietcombank
SEPAY_ACCOUNT_NUMBER=0000000001
SEPAY_ACCOUNT_NAME=QLDAPM TEST
SEPAY_WEBHOOK_SECRET=your_own_generated_hmac_secret
SEPAY_EXPIRE_MINUTES=15
```

Restart the app. In another terminal:

```powershell
.\scripts\Start-SePay-Tunnel.ps1
```

The helper requires Python 3, downloads/verifies the official signed Cloudflare executable on first use, and exposes **only the webhook route**. Keep its terminal running. Save its complete printed URL in SePay; a restart may produce a new hostname. No permanent public URL or private key is supplied by this repository.

Configure the Test-mode webhook: **Tiền vào**, **JSON**, retry On, **HMAC-SHA256**, and the same secret as `.env`. Configure payment-code recognition **QLD + exactly 8 digits**. Select the matching test account (or all test accounts; the backend still checks its configured account). Create/select a test VA if SePay requires one for that account type.

In QLDAPM create a fresh bill → **QR chuyển khoản · SePay (thử nghiệm)**. In SePay **Mô phỏng giao dịch**, choose the same test bank account/VA, **Tiền vào**, exact displayed amount, and exact `QLD........` reference in transfer content. Expect QLDAPM to open a paid receipt. Replaying the same callback must not record payment or deduct stock twice.

HTTP 200 acknowledges a notification; verify the invoice actually became paid. Wrong amounts, expired references and changed baskets can require manual review. The image comes from SePay's external QR service. Real banking apps may reject the fake test account; use SePay simulation for this demo.

QR backend: `SePayService.java`; HMAC checks: `SePaySignature.java`; webhook: `SePayController.java`; display/polling: `static/pos.js`. Payment tables are created additively from `src/main/resources/db/sepay.sql` at startup.

## Rules and limitations

- Prices include VAT. Checkout recalculates prices/promotions. A tracked product requires an available individual Serial/IMEI; quantity products accept SKU/barcode. Typing a code followed by Enter simulates a keyboard-style USB scanner.
- One current bill per cashier, multiple held bills. A pending QR freezes its bill until cancelled/expired/resolved. Committed sales deduct stock and issue warranty automatically.
- One open stocktake per store; only selected products are frozen. Complete/cancel before moving their stock.
- Returns normally require a different approver and are allowed within 7 days. Unopened/manufacturer-fault returns have no condition fee; opened/missing-accessory returns deduct 10%. Only eligible goods return to saleable stock.
- SePay confirms full-invoice QR transfers. Other cash/card/manual QR/installment methods record cashier-confirmed payments. No real card gateway, full card/PIN/CVV collection, automatic bank refunds or full reconciliation dashboard is implemented.
- Printer/scanner drivers and physical hardware need separate configuration. Printing uses the browser/OS print dialog.
- Business dates use Vietnam time; SQL Server should use the same timezone because the schema uses `SYSDATETIME()`.
- Scope excludes ecommerce, HR/payroll, deep accounting, native mobile apps and repair-parts/labor management.

## Troubleshooting

| Symptom | Check |
|---|---|
| `sqlcmd` not found | Install Microsoft command-line tools; reopen PowerShell |
| SQL login/connection failure | TCP/IP, actual port, Mixed Mode, credentials/permissions; verify in SSMS |
| Demo login/data missing | Correct `DB_NAME`, demo seed installed; use `admin.demo` and initial demo password |
| Unexpected database | Terminal `DB_*` overrides; working directory must be project root |
| Old interface | Stop app, rebuild with `-Build`, restart and hard-refresh |
| Port 8081 occupied | Stop old app or pass `--server.port=8082` to JAR; tunnel accepts `-AppPort 8082` |
| Cashier cannot edit catalog | Expected; use warehouse/admin |
| QR remains pending | Current tunnel URL, matching Test/Live environment, secret, account/VA, reference filters and server clock |
| Reseeding does not reset tests | Expected; create another `QLDAPM_Demo_Run...` database |

## Source and validation

| Path | Contents |
|---|---|
| `src/main/java/com/xenon/qldapm/` | Backend, security and business rules |
| `src/main/resources/static/` | HTML/CSS/JavaScript frontend |
| `src/main/resources/db/` | Additive payment schema |
| `database/001_initial_schema.sql` | Empty database initialization |
| `database/002_demo_seed.sql` | Synthetic reproducible demo fixtures |
| `scripts/` | Selected public demo startup/tunnel helpers |
| `.env.example` | Configuration placeholders |

Production source, this README, clean schema, synthetic seed and selected helpers are intended for Git. `.env`, private credentials, personal documents, generated builds, internal tests and verification artifacts are excluded. Public demo credentials must not be used for real operational data.

Final local validation on **1 November 2026** included source-only clone provisioning/build, **13 passing automated tests**, **33 role workflow checks**, receiving/sale/warranty/stocktake/returns/promotion/report regression checks, browser checks of all 17 main screens and every demo login, and 100 concurrent synthetic cashier sessions. Checkout p95 was about **1.13 seconds**, report p95 about **90 ms**; stock and the last-unit race checks passed. The final review also fixed/retested rejection of inactive suppliers for new receipts.

Internal automated test files are intentionally not shipped in the public clone; use the role scenarios above to reproduce functional acceptance. Local synthetic results do not certify 100 physical tills, production-volume data, physical printers/scanners, Live bank payments or a deployment's backup/restore procedures. Verify those separately for actual deployment.
