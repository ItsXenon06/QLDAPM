-- QLDAPM DEMO DATA v1 — run in your selected application/test database, NOT master.
-- Six logins below share the demo password: Demo@2026!
-- Execute after 001_initial_schema.sql. Rerunnable: does not reset passwords, stock or test transactions.
-- All new business identifiers are DEMO- prefixed. Names, prices and contacts are synthetic samples.
-- No DROP, DELETE, TRUNCATE, identity reset or updates to pre-existing business records.
SET NOCOUNT ON; SET XACT_ABORT ON;
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON; SET ARITHABORT ON; SET NUMERIC_ROUNDABORT OFF;
IF DB_NAME() IN (N'master',N'model',N'msdb',N'tempdb') THROW 51001,'Select an application/test database first.',1;
IF OBJECT_ID(N'dbo.SerialSanPham') IS NULL OR OBJECT_ID(N'dbo.VaiTroQuyen') IS NULL
    THROW 51002,'Install 001_initial_schema.sql before demo data.',1;
BEGIN TRY
BEGIN TRANSACTION;
DECLARE @LockResult int;
EXEC @LockResult=sys.sp_getapplock @Resource=N'qldapm:demo-seed',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
IF @LockResult<0 THROW 51003,'Another seed is running. Retry later.',1;
DECLARE @Now datetime2=SYSDATETIME(), @Password nvarchar(500)=N'{bcrypt}$2a$10$3lWxOCulkaTO4XPaXJop9OFc/IcwO9vn0Y2SgwW1sTiR3XQ4yFp1K';
DECLARE @Permissions TABLE(Code nvarchar(60),Name nvarchar(150),GroupName nvarchar(50));
INSERT @Permissions VALUES
(N'SAN_PHAM_XEM',N'Xem danh mục sản phẩm',N'kho'),
(N'SAN_PHAM_QUAN_LY',N'Quản lý sản phẩm, danh mục, thương hiệu',N'kho'),
(N'GIA_QUAN_LY',N'Thay đổi giá niêm yết / giá sàn',N'kho'),
(N'NHA_CUNG_CAP_QUAN_LY',N'Quản lý nhà cung cấp',N'kho'),
(N'KHO_XEM_TON',N'Xem tồn kho và Serial/IMEI',N'kho'),
(N'KHO_NHAP',N'Lập phiếu và nhận hàng nhập kho',N'kho'),
(N'KHO_KIEM_KE',N'Kiểm kê và điều chỉnh tồn kho',N'kho'),
(N'POS_BAN_HANG',N'Bán hàng tại quầy POS',N'pos'),
(N'POS_XEM_HOA_DON',N'Xem/tra cứu hóa đơn',N'pos'),
(N'POS_TRA_HANG',N'Lập phiếu trả hàng',N'pos'),
(N'POS_DUYET_TRA_HANG',N'Duyệt phiếu trả hàng',N'pos'),
(N'POS_HUY_HOA_DON',N'Hủy hóa đơn',N'pos'),
(N'KHUYEN_MAI_XEM',N'Xem chương trình khuyến mãi',N'khuyen_mai'),
(N'KHUYEN_MAI_QUAN_LY',N'Quản lý chương trình khuyến mãi, voucher',N'khuyen_mai'),
(N'BAO_HANH_TRA_CUU',N'Tra cứu bảo hành theo Serial/IMEI/SĐT',N'bao_hanh'),
(N'BAO_HANH_TIEP_NHAN',N'Tiếp nhận yêu cầu bảo hành',N'bao_hanh'),
(N'BAO_HANH_CHINH_SACH',N'Quản lý chính sách bảo hành',N'bao_hanh'),
(N'BAO_CAO_XEM',N'Xem báo cáo - thống kê',N'bao_cao'),
(N'BAO_CAO_XUAT',N'Xuất báo cáo Excel/PDF',N'bao_cao'),
(N'HE_THONG_NGUOI_DUNG',N'Quản lý nhân viên và tài khoản',N'he_thong'),
(N'HE_THONG_PHAN_QUYEN',N'Quản lý vai trò và phân quyền',N'he_thong'),
(N'HE_THONG_QUAY_POS',N'Quản lý quầy POS',N'he_thong'),
(N'HE_THONG_NHAT_KY',N'Xem nhật ký hệ thống',N'he_thong');
INSERT Quyen(MaCode,TenQuyen,Nhom) SELECT Code,Name,GroupName FROM @Permissions p WHERE NOT EXISTS(SELECT 1 FROM Quyen q WHERE q.MaCode=p.Code);
DECLARE @Roles TABLE(Name nvarchar(50),Description nvarchar(500));
INSERT @Roles VALUES (N'Quản trị hệ thống',N'Quản trị ứng dụng'),
(N'Ban quản lý siêu thị',N'Báo cáo, chính sách giá, khuyến mãi, duyệt trả/hủy hóa đơn'),
(N'Nhân viên quản lý kho',N'Danh mục, nhập kho, kiểm kê, Serial/IMEI'),
(N'Nhân viên thu ngân',N'Bán hàng, trả hàng tại quầy POS'),
(N'Nhân viên CSKH / bảo hành',N'Tra cứu lịch sử mua, tiếp nhận bảo hành');
DECLARE @NewRoles TABLE(Id int);
INSERT VaiTro(TenVaiTro,MoTa) OUTPUT inserted.MaVaiTro INTO @NewRoles
SELECT Name,Description FROM @Roles r WHERE NOT EXISTS(SELECT 1 FROM VaiTro v WHERE v.TenVaiTro=r.Name);
DECLARE @Grants TABLE(RoleName nvarchar(50),Code nvarchar(60));
INSERT @Grants VALUES (N'Quản trị hệ thống',N'SAN_PHAM_XEM'),
(N'Quản trị hệ thống',N'SAN_PHAM_QUAN_LY'),
(N'Quản trị hệ thống',N'GIA_QUAN_LY'),
(N'Quản trị hệ thống',N'NHA_CUNG_CAP_QUAN_LY'),
(N'Quản trị hệ thống',N'KHO_XEM_TON'),
(N'Quản trị hệ thống',N'KHO_NHAP'),
(N'Quản trị hệ thống',N'KHO_KIEM_KE'),
(N'Quản trị hệ thống',N'POS_BAN_HANG'),
(N'Quản trị hệ thống',N'POS_XEM_HOA_DON'),
(N'Quản trị hệ thống',N'POS_TRA_HANG'),
(N'Quản trị hệ thống',N'POS_DUYET_TRA_HANG'),
(N'Quản trị hệ thống',N'POS_HUY_HOA_DON'),
(N'Quản trị hệ thống',N'KHUYEN_MAI_XEM'),
(N'Quản trị hệ thống',N'KHUYEN_MAI_QUAN_LY'),
(N'Quản trị hệ thống',N'BAO_HANH_TRA_CUU'),
(N'Quản trị hệ thống',N'BAO_HANH_TIEP_NHAN'),
(N'Quản trị hệ thống',N'BAO_HANH_CHINH_SACH'),
(N'Quản trị hệ thống',N'BAO_CAO_XEM'),
(N'Quản trị hệ thống',N'BAO_CAO_XUAT'),
(N'Quản trị hệ thống',N'HE_THONG_NGUOI_DUNG'),
(N'Quản trị hệ thống',N'HE_THONG_PHAN_QUYEN'),
(N'Quản trị hệ thống',N'HE_THONG_QUAY_POS'),
(N'Quản trị hệ thống',N'HE_THONG_NHAT_KY'),
(N'Ban quản lý siêu thị',N'SAN_PHAM_XEM'),
(N'Ban quản lý siêu thị',N'GIA_QUAN_LY'),
(N'Ban quản lý siêu thị',N'KHO_XEM_TON'),
(N'Ban quản lý siêu thị',N'POS_XEM_HOA_DON'),
(N'Ban quản lý siêu thị',N'POS_DUYET_TRA_HANG'),
(N'Ban quản lý siêu thị',N'POS_HUY_HOA_DON'),
(N'Ban quản lý siêu thị',N'KHUYEN_MAI_XEM'),
(N'Ban quản lý siêu thị',N'KHUYEN_MAI_QUAN_LY'),
(N'Ban quản lý siêu thị',N'BAO_HANH_TRA_CUU'),
(N'Ban quản lý siêu thị',N'BAO_HANH_CHINH_SACH'),
(N'Ban quản lý siêu thị',N'BAO_CAO_XEM'),
(N'Ban quản lý siêu thị',N'BAO_CAO_XUAT'),
(N'Ban quản lý siêu thị',N'HE_THONG_NHAT_KY'),
(N'Nhân viên quản lý kho',N'SAN_PHAM_XEM'),
(N'Nhân viên quản lý kho',N'SAN_PHAM_QUAN_LY'),
(N'Nhân viên quản lý kho',N'NHA_CUNG_CAP_QUAN_LY'),
(N'Nhân viên quản lý kho',N'KHO_XEM_TON'),
(N'Nhân viên quản lý kho',N'KHO_NHAP'),
(N'Nhân viên quản lý kho',N'KHO_KIEM_KE'),
(N'Nhân viên quản lý kho',N'BAO_CAO_XEM'),
(N'Nhân viên thu ngân',N'SAN_PHAM_XEM'),
(N'Nhân viên thu ngân',N'KHO_XEM_TON'),
(N'Nhân viên thu ngân',N'POS_BAN_HANG'),
(N'Nhân viên thu ngân',N'POS_XEM_HOA_DON'),
(N'Nhân viên thu ngân',N'POS_TRA_HANG'),
(N'Nhân viên thu ngân',N'KHUYEN_MAI_XEM'),
(N'Nhân viên thu ngân',N'BAO_HANH_TRA_CUU'),
(N'Nhân viên CSKH / bảo hành',N'SAN_PHAM_XEM'),
(N'Nhân viên CSKH / bảo hành',N'POS_XEM_HOA_DON'),
(N'Nhân viên CSKH / bảo hành',N'BAO_HANH_TRA_CUU'),
(N'Nhân viên CSKH / bảo hành',N'BAO_HANH_TIEP_NHAN');
-- Existing role grants are respected. New roles receive the application defaults.
INSERT VaiTroQuyen(MaVaiTro,MaQuyen)
SELECT v.MaVaiTro,q.MaQuyen FROM @Grants g JOIN VaiTro v ON v.TenVaiTro=g.RoleName JOIN Quyen q ON q.MaCode=g.Code
WHERE v.MaVaiTro IN(SELECT Id FROM @NewRoles) AND NOT EXISTS(SELECT 1 FROM VaiTroQuyen x WHERE x.MaVaiTro=v.MaVaiTro AND x.MaQuyen=q.MaQuyen);
DECLARE @Users TABLE(Code nvarchar(20),Login nvarchar(100),Name nvarchar(150),RoleName nvarchar(50));
INSERT @Users VALUES (N'DEMO-NV001',N'admin.demo',N'Trần Minh Anh',N'Quản trị hệ thống'),
(N'DEMO-NV002',N'quanly.demo',N'Nguyễn Hoàng Nam',N'Ban quản lý siêu thị'),
(N'DEMO-NV003',N'kho.demo',N'Lê Thanh Bình',N'Nhân viên quản lý kho'),
(N'DEMO-NV004',N'thungan01.demo',N'Phạm Ngọc Linh',N'Nhân viên thu ngân'),
(N'DEMO-NV005',N'thungan02.demo',N'Võ Minh Khang',N'Nhân viên thu ngân'),
(N'DEMO-NV006',N'baohanh.demo',N'Đặng Thu Hà',N'Nhân viên CSKH / bảo hành');
IF EXISTS(SELECT 1 FROM TaiKhoan t JOIN NhanVien n ON n.MaNhanVien=t.MaNhanVien JOIN @Users u ON u.Login=t.TenDangNhap WHERE t.NgayXoa IS NULL AND n.MaSoNhanVien<>u.Code)
    THROW 51008,'A demo login already belongs to a non-demo employee. Use a fresh test database.',1;
INSERT NhanVien(MaSoNhanVien,HoTen,MaVaiTro)
SELECT u.Code,u.Name,v.MaVaiTro FROM @Users u JOIN VaiTro v ON v.TenVaiTro=u.RoleName
WHERE NOT EXISTS(SELECT 1 FROM NhanVien n WHERE n.MaSoNhanVien=u.Code AND n.NgayXoa IS NULL)
AND NOT EXISTS(SELECT 1 FROM TaiKhoan t WHERE t.TenDangNhap=u.Login AND t.NgayXoa IS NULL);
INSERT TaiKhoan(MaNhanVien,TenDangNhap,MatKhauHash)
SELECT n.MaNhanVien,u.Login,@Password FROM @Users u JOIN NhanVien n ON n.MaSoNhanVien=u.Code AND n.NgayXoa IS NULL
WHERE NOT EXISTS(SELECT 1 FROM TaiKhoan t WHERE (t.TenDangNhap=u.Login OR t.MaNhanVien=n.MaNhanVien) AND t.NgayXoa IS NULL);
DECLARE @Admin int=(SELECT MaNhanVien FROM TaiKhoan WHERE TenDangNhap=N'admin.demo' AND NgayXoa IS NULL),
@Manager int=(SELECT MaNhanVien FROM TaiKhoan WHERE TenDangNhap=N'quanly.demo' AND NgayXoa IS NULL),
@Warehouse int=(SELECT MaNhanVien FROM TaiKhoan WHERE TenDangNhap=N'kho.demo' AND NgayXoa IS NULL),
@Cashier int=(SELECT MaNhanVien FROM TaiKhoan WHERE TenDangNhap=N'thungan01.demo' AND NgayXoa IS NULL),
@Cashier2 int=(SELECT MaNhanVien FROM TaiKhoan WHERE TenDangNhap=N'thungan02.demo' AND NgayXoa IS NULL),
@Support int=(SELECT MaNhanVien FROM TaiKhoan WHERE TenDangNhap=N'baohanh.demo' AND NgayXoa IS NULL);
IF @Admin IS NULL OR @Cashier IS NULL OR @Warehouse IS NULL THROW 51004,'Demo account identifiers conflict with existing records.',1;
DECLARE @Products TABLE(Code nvarchar(60),Name nvarchar(255),Category nvarchar(100),Brand nvarchar(100),Price decimal(18,2),Cost decimal(18,2),Warranty int,Tracked bit,Qty int,Minimum int,Location nvarchar(100),Specs nvarchar(max));
INSERT @Products VALUES
(N'DEMO-TV01',N'Samsung QLED 55 inch Q60D',N'Tivi & âm thanh',N'Samsung',13990000,10900000,24,1,8,3,N'A-01',N'{"Màn hình":"55 inch QLED","Độ phân giải":"4K","Hệ điều hành":"Tizen"}'),
(N'DEMO-TV02',N'LG UHD 55 inch UT80',N'Tivi & âm thanh',N'LG',11990000,9400000,24,1,6,2,N'A-02',N'{"Màn hình":"55 inch","Độ phân giải":"4K UHD","Hệ điều hành":"webOS"}'),
(N'DEMO-TV03',N'Sony BRAVIA 65 inch X80L',N'Tivi & âm thanh',N'Sony',18990000,15500000,24,1,2,3,N'A-03',N'{"Màn hình":"65 inch","Độ phân giải":"4K","Âm thanh":"Dolby Atmos"}'),
(N'DEMO-FR01',N'Panasonic Inverter 325 lít',N'Tủ lạnh',N'Panasonic',10490000,8200000,24,1,6,2,N'B-01',N'{"Dung tích":"325 lít","Công nghệ":"Inverter","Kiểu tủ":"Ngăn đá dưới"}'),
(N'DEMO-FR02',N'Samsung Bespoke 360 lít',N'Tủ lạnh',N'Samsung',15990000,12600000,24,1,4,2,N'B-02',N'{"Dung tích":"360 lít","Màu sắc":"Trắng kem","Công nghệ":"Digital Inverter"}'),
(N'DEMO-WM01',N'LG AI DD 9 kg cửa trước',N'Máy giặt',N'LG',9990000,7800000,24,1,7,3,N'C-01',N'{"Khối lượng giặt":"9 kg","Kiểu máy":"Cửa trước","Động cơ":"Truyền động trực tiếp"}'),
(N'DEMO-WM02',N'Electrolux UltimateCare 10 kg',N'Máy giặt',N'Electrolux',12490000,9800000,24,1,3,3,N'C-02',N'{"Khối lượng giặt":"10 kg","Công nghệ":"HygienicCare","Tốc độ vắt":"1.400 vòng/phút"}'),
(N'DEMO-PH01',N'Samsung Galaxy A55 256 GB',N'Điện thoại',N'Samsung',8990000,7100000,12,1,12,4,N'D-01',N'{"Bộ nhớ":"256 GB","RAM":"8 GB","Màu sắc":"Xanh đậm"}'),
(N'DEMO-PH02',N'iPhone 15 128 GB · Xanh',N'Điện thoại',N'Apple',16990000,14500000,12,1,8,3,N'D-02',N'{"Bộ nhớ":"128 GB","Màn hình":"6.1 inch","Màu sắc":"Xanh"}'),
(N'DEMO-PH03',N'OPPO Reno12 256 GB',N'Điện thoại',N'OPPO',10990000,8700000,12,1,6,2,N'D-03',N'{"Bộ nhớ":"256 GB","RAM":"12 GB","Sạc nhanh":"80W"}'),
(N'DEMO-AU01',N'JBL Flip 6 · Xanh biển',N'Tivi & âm thanh',N'JBL',2490000,1720000,12,1,9,3,N'A-05',N'{"Kết nối":"Bluetooth","Chống nước":"IP67","Pin":"12 giờ"}'),
(N'DEMO-AC01',N'Anker Nano II 65W USB-C',N'Phụ kiện',N'Anker',690000,390000,12,0,30,8,N'E-01',N'{"Công suất":"65W","Cổng sạc":"USB-C","Công nghệ":"GaN II"}'),
(N'DEMO-AC02',N'Cáp USB-C 100W · 1.5 mét',N'Phụ kiện',N'Anker',190000,80000,6,0,40,10,N'E-02',N'{"Chiều dài":"1.5 mét","Công suất":"100W","Vỏ":"Dây bện"}'),
(N'DEMO-AC03',N'Bộ vệ sinh màn hình 3 trong 1',N'Phụ kiện',N'Baseus',99000,35000,0,0,25,5,N'E-03',N'{"Bộ sản phẩm":"Dung dịch, khăn, chổi","Dung tích":"100 ml"}'),
(N'DEMO-AC04',N'Cáp HDMI 2.1 · 2 mét',N'Phụ kiện',N'Baseus',290000,125000,6,0,10,5,N'E-04',N'{"Chuẩn":"HDMI 2.1","Chiều dài":"2 mét","Hỗ trợ":"8K / 60Hz"}'),
(N'DEMO-AC05',N'Sony MDR-ZX110 · Đen',N'Phụ kiện',N'Sony',490000,290000,12,0,15,5,N'E-05',N'{"Kiểu tai nghe":"Chụp tai","Kết nối":"Jack 3.5 mm","Màu sắc":"Đen"}');
INSERT DanhMuc(TenDanhMuc,ThuTuHienThi)
SELECT DISTINCT Category,1 FROM @Products p WHERE NOT EXISTS(SELECT 1 FROM DanhMuc d WHERE d.TenDanhMuc=p.Category AND d.MaDanhMucCha IS NULL AND d.NgayXoa IS NULL);
INSERT ThuongHieu(TenThuongHieu) SELECT DISTINCT Brand FROM @Products p WHERE NOT EXISTS(SELECT 1 FROM ThuongHieu b WHERE b.TenThuongHieu=p.Brand);
INSERT SanPham(MaSKU,MaVach,TenSanPham,MaDanhMuc,MaThuongHieu,GiaNiemYet,GiaToiThieu,BaoHanhThang,CoTheoDoiSerial,ThongSoKyThuat,MoTa)
SELECT p.Code,CASE WHEN p.Tracked=0 THEN p.Code ELSE NULL END,p.Name,d.MaDanhMuc,b.MaThuongHieu,p.Price,ROUND(p.Price*0.85,0),p.Warranty,p.Tracked,p.Specs,N'Sản phẩm trưng bày dữ liệu mẫu [QLDAPM-DEMO]'
FROM @Products p JOIN DanhMuc d ON d.TenDanhMuc=p.Category AND d.MaDanhMucCha IS NULL AND d.NgayXoa IS NULL JOIN ThuongHieu b ON b.TenThuongHieu=p.Brand
WHERE NOT EXISTS(SELECT 1 FROM SanPham s WHERE s.MaSKU=p.Code AND s.NgayXoa IS NULL);
INSERT TonKho(MaSanPham,TonToiThieu,TonToiDa,ViTriKho)
SELECT s.MaSanPham,p.Minimum,p.Qty*3,p.Location FROM @Products p JOIN SanPham s ON s.MaSKU=p.Code AND s.NgayXoa IS NULL
WHERE NOT EXISTS(SELECT 1 FROM TonKho t WHERE t.MaSanPham=s.MaSanPham);
INSERT QuayPOS(MaQuayCode,TenQuay,ViTri,TrangThai)
SELECT code,name,location,status FROM(VALUES(N'DEMO-Q01',N'Quầy 01',N'Tầng trệt · Cửa ra',N'hoat_dong'),(N'DEMO-Q02',N'Quầy 02',N'Tầng trệt · Cửa ra',N'hoat_dong'),(N'DEMO-Q03',N'Quầy 03',N'Khu điện thoại',N'hoat_dong'),(N'DEMO-Q04',N'Quầy 04',N'Khu gia dụng',N'bao_tri'))q(code,name,location,status)
WHERE NOT EXISTS(SELECT 1 FROM QuayPOS x WHERE x.MaQuayCode=q.code);
IF NOT EXISTS(SELECT 1 FROM NhaCungCap WHERE MaSoThue=N'DEMO-NCC01' AND NgayXoa IS NULL)
INSERT NhaCungCap(TenNCC,MaSoThue,DiaChi,NguoiLienHe) VALUES(N'Phân phối thiết bị Miền Trung',N'DEMO-NCC01',N'Địa chỉ mẫu · Nha Trang',N'Bộ phận giao nhận');
IF NOT EXISTS(SELECT 1 FROM NhaCungCap WHERE MaSoThue=N'DEMO-NCC02' AND NgayXoa IS NULL)
INSERT NhaCungCap(TenNCC,MaSoThue,DiaChi,NguoiLienHe) VALUES(N'Phụ kiện & công nghệ Việt',N'DEMO-NCC02',N'Địa chỉ mẫu · Đà Nẵng',N'Bộ phận kinh doanh');
DECLARE @Customers TABLE(Phone nvarchar(20),Name nvarchar(150));
INSERT @Customers VALUES(N'0900000101',N'Nguyễn Hải Yến'),(N'0900000102',N'Trần Quốc Huy'),(N'0900000103',N'Lê Ngọc Mai'),(N'0900000104',N'Phạm Anh Tuấn'),(N'0900000105',N'Võ Thảo Vy'),(N'0900000106',N'Đặng Minh Đức'),(N'0900000107',N'Hoàng Bảo Châu'),(N'0900000108',N'Bùi Gia Hân');
IF EXISTS(SELECT 1 FROM KhachHang k JOIN @Customers c ON c.Phone=k.SoDienThoai WHERE k.NgayXoa IS NULL AND ISNULL(k.GhiChu,N'') NOT LIKE N'%[[]QLDAPM-DEMO]%')
    THROW 51006,'A demo phone number belongs to an existing non-demo customer. Use a fresh test database.',1;
INSERT KhachHang(HoTen,SoDienThoai,GhiChu) SELECT Name,Phone,N'Liên hệ giả lập, không gọi [QLDAPM-DEMO]' FROM @Customers c
WHERE NOT EXISTS(SELECT 1 FROM KhachHang k WHERE k.SoDienThoai=c.Phone AND k.NgayXoa IS NULL);
IF NOT EXISTS(SELECT 1 FROM ChinhSachBaoHanh WHERE TenChinhSach=N'DEMO · Gia hạn cửa hàng 12 tháng')
INSERT ChinhSachBaoHanh(TenChinhSach,ThoiHanThang,NoiDung) VALUES(N'DEMO · Gia hạn cửa hàng 12 tháng',12,N'Chính sách thử nghiệm; tra cứu và tiếp nhận theo Serial, không bao gồm sửa chữa chi tiết.');


-- Additional catalog and partner fixtures; insert-only and safe to rerun.
DECLARE @ExtraCategories TABLE(Name nvarchar(100),Parent nvarchar(100),Sort int);
INSERT @ExtraCategories VALUES
(N'Máy lạnh',NULL,6),(N'Gia dụng nhà bếp',NULL,7),(N'Chăm sóc nhà cửa',NULL,8),
(N'Máy tính & máy tính bảng',NULL,9),(N'Thiết bị mạng',NULL,10),(N'Điện gia dụng',NULL,11),
(N'Tivi 4K',N'Tivi & âm thanh',1),(N'Loa Bluetooth',N'Tivi & âm thanh',2),
(N'Tủ lạnh Inverter',N'Tủ lạnh',1),(N'Máy giặt cửa trước',N'Máy giặt',1),
(N'Sạc & cáp',N'Phụ kiện',1),(N'Tai nghe',N'Phụ kiện',2),
(N'Nồi cơm & nồi chiên',N'Gia dụng nhà bếp',1),(N'Máy hút bụi',N'Chăm sóc nhà cửa',1);
INSERT DanhMuc(TenDanhMuc,ThuTuHienThi,MoTa)
SELECT Name,Sort,N'Danh mục mẫu [QLDAPM-DEMO]' FROM @ExtraCategories e
WHERE Parent IS NULL AND NOT EXISTS(SELECT 1 FROM DanhMuc d WHERE d.TenDanhMuc=e.Name AND d.MaDanhMucCha IS NULL AND d.NgayXoa IS NULL);
INSERT DanhMuc(TenDanhMuc,MaDanhMucCha,ThuTuHienThi,MoTa)
SELECT e.Name,p.MaDanhMuc,e.Sort,N'Danh mục con mẫu [QLDAPM-DEMO]' FROM @ExtraCategories e
JOIN DanhMuc p ON p.TenDanhMuc=e.Parent AND p.MaDanhMucCha IS NULL AND p.NgayXoa IS NULL
WHERE NOT EXISTS(SELECT 1 FROM DanhMuc d WHERE d.TenDanhMuc=e.Name AND d.MaDanhMucCha=p.MaDanhMuc AND d.NgayXoa IS NULL);
INSERT ThuongHieu(TenThuongHieu,QuocGia,MoTa)
SELECT Name,Country,N'Thương hiệu cho danh mục mở rộng [QLDAPM-DEMO]' FROM (VALUES
(N'Daikin',N'Nhật Bản'),(N'Philips',N'Hà Lan'),(N'Sharp',N'Nhật Bản'),
(N'Xiaomi',N'Trung Quốc'),(N'ASUS',N'Đài Loan'),(N'TP-Link',N'Trung Quốc')) b(Name,Country)
WHERE NOT EXISTS(SELECT 1 FROM ThuongHieu t WHERE t.TenThuongHieu=b.Name);
INSERT NhaCungCap(TenNCC,MaSoThue,Email,DiaChi,NguoiLienHe,TrangThai)
SELECT Name,Code,Email,Address,Contact,Active FROM (VALUES
(N'Điện lạnh An Phát (demo)',N'DEMO-NCC03',N'dienlanh@example.invalid',N'Kho mẫu · TP. Hồ Chí Minh',N'Nguyễn An · mua hàng',1),
(N'Gia dụng Bình Minh (demo)',N'DEMO-NCC04',N'giadung@example.invalid',N'Kho mẫu · Bình Dương',N'Trần Mai · kinh doanh',1),
(N'Công nghệ Sao Việt (demo)',N'DEMO-NCC05',N'congnghe@example.invalid',N'Kho mẫu · Hà Nội',N'Lê Huy · giao nhận',1),
(N'Kết nối Việt (demo)',N'DEMO-NCC06',N'mang@example.invalid',N'Kho mẫu · Đà Nẵng',N'Phạm Hà · kinh doanh',1),
(N'Âm thanh Đại Dương (demo)',N'DEMO-NCC07',N'amthanh@example.invalid',N'Kho mẫu · Cần Thơ',N'Võ Minh · bảo hành',1),
(N'Gia dụng Nam Sơn (demo)',N'DEMO-NCC08',N'namsom@example.invalid',N'Kho mẫu · Đồng Nai',N'Đặng Lan · tạm ngừng hợp tác',0)
) s(Name,Code,Email,Address,Contact,Active)
WHERE NOT EXISTS(SELECT 1 FROM NhaCungCap n WHERE n.MaSoThue=s.Code AND n.NgayXoa IS NULL);
DECLARE @ExtraCustomers TABLE(Phone nvarchar(20),Name nvarchar(150),Company nvarchar(200),Address nvarchar(500));
INSERT @ExtraCustomers VALUES
(N'0900000109',N'Nguyễn Thanh Trúc',NULL,N'Địa chỉ mẫu · Quận 7'),
(N'0900000110',N'Trần Bảo Minh',NULL,N'Địa chỉ mẫu · Thủ Đức'),
(N'0900000111',N'Lê Phương Anh',NULL,N'Địa chỉ mẫu · Bình Thạnh'),
(N'0900000112',N'Phạm Gia Bảo',NULL,N'Địa chỉ mẫu · Gò Vấp'),
(N'0900000113',N'Võ Hoàng Long',N'Văn phòng An Khang (demo)',N'Địa chỉ mẫu · Quận 3'),
(N'0900000114',N'Đặng Khánh Linh',N'Nhà nghỉ Bình An (demo)',N'Địa chỉ mẫu · Quận 10');
IF EXISTS(SELECT 1 FROM KhachHang k JOIN @ExtraCustomers c ON c.Phone=k.SoDienThoai WHERE k.NgayXoa IS NULL AND ISNULL(k.GhiChu,N'') NOT LIKE N'%[[]QLDAPM-DEMO]%')
THROW 51006,'Extra demo phone conflicts with a non-demo customer.',1;
INSERT KhachHang(HoTen,SoDienThoai,TenCongTy,DiaChi,GhiChu)
SELECT Name,Phone,Company,Address,N'Liên hệ giả lập, không gọi [QLDAPM-DEMO]' FROM @ExtraCustomers c
WHERE NOT EXISTS(SELECT 1 FROM KhachHang k WHERE k.SoDienThoai=c.Phone AND k.NgayXoa IS NULL);

-- Clarify untouched sample catalog metadata; preserve user-edited descriptions.
DECLARE @CategoryExamples TABLE(Name nvarchar(100),Description nvarchar(2000));
INSERT @CategoryExamples VALUES
(N'Tivi & âm thanh',N'Tivi và thiết bị phát âm thanh: tivi Samsung, LG, Sony; loa JBL. [QLDAPM-DEMO]'),
(N'Tủ lạnh',N'Tủ lạnh bảo quản thực phẩm, gồm dòng Inverter và ngăn đá dưới. [QLDAPM-DEMO]'),
(N'Máy giặt',N'Máy giặt gia đình theo khối lượng giặt và kiểu cửa. [QLDAPM-DEMO]'),
(N'Điện thoại',N'Điện thoại thông minh; từng máy được quản lý bằng Serial/IMEI. [QLDAPM-DEMO]'),
(N'Phụ kiện',N'Sạc, cáp, tai nghe và bộ vệ sinh; thường bán theo số lượng. [QLDAPM-DEMO]'),
(N'Máy lạnh',N'Máy lạnh treo tường, điều hòa Inverter và phụ kiện lắp đặt. [QLDAPM-DEMO]'),
(N'Gia dụng nhà bếp',N'Nồi cơm điện, nồi chiên không dầu, bếp và thiết bị chế biến. [QLDAPM-DEMO]'),
(N'Chăm sóc nhà cửa',N'Máy hút bụi, robot lau nhà và thiết bị vệ sinh gia đình. [QLDAPM-DEMO]'),
(N'Máy tính & máy tính bảng',N'Laptop và máy tính bảng theo cấu hình, bộ nhớ và hãng. [QLDAPM-DEMO]'),
(N'Thiết bị mạng',N'Router Wi-Fi, bộ mở rộng sóng và thiết bị kết nối mạng. [QLDAPM-DEMO]'),
(N'Điện gia dụng',N'Quạt, bàn ủi, máy sấy tóc và thiết bị điện dùng trong gia đình. [QLDAPM-DEMO]'),
(N'Tivi 4K',N'Nhóm con của Tivi & âm thanh: tivi có độ phân giải 4K. [QLDAPM-DEMO]'),
(N'Loa Bluetooth',N'Nhóm con của Tivi & âm thanh: loa không dây và loa di động. [QLDAPM-DEMO]'),
(N'Tủ lạnh Inverter',N'Nhóm con của Tủ lạnh: thiết bị dùng máy nén Inverter. [QLDAPM-DEMO]'),
(N'Máy giặt cửa trước',N'Nhóm con của Máy giặt: máy lồng ngang, ví dụ LG AI DD 9 kg. [QLDAPM-DEMO]'),
(N'Sạc & cáp',N'Nhóm con của Phụ kiện: củ sạc USB-C, cáp sạc và cáp truyền dữ liệu. [QLDAPM-DEMO]'),
(N'Tai nghe',N'Nhóm con của Phụ kiện: tai nghe có dây, không dây và chụp tai. [QLDAPM-DEMO]'),
(N'Nồi cơm & nồi chiên',N'Nhóm con của Gia dụng nhà bếp: nồi cơm điện và nồi chiên không dầu. [QLDAPM-DEMO]'),
(N'Máy hút bụi',N'Nhóm con của Chăm sóc nhà cửa: máy hút bụi cầm tay và robot. [QLDAPM-DEMO]');
UPDATE d SET MoTa=e.Description FROM DanhMuc d JOIN @CategoryExamples e ON e.Name=d.TenDanhMuc
WHERE d.NgayXoa IS NULL AND (NULLIF(LTRIM(RTRIM(d.MoTa)),N'') IS NULL
OR d.MoTa IN(N'Danh mục mẫu [QLDAPM-DEMO]',N'Danh mục con mẫu [QLDAPM-DEMO]'));
DECLARE @BrandExamples TABLE(Name nvarchar(100),Country nvarchar(100),Description nvarchar(2000));
INSERT @BrandExamples VALUES
(N'Samsung',N'Hàn Quốc',N'Tivi, điện thoại và tủ lạnh.'),(N'LG',N'Hàn Quốc',N'Tivi và máy giặt gia đình.'),
(N'Sony',N'Nhật Bản',N'Tivi BRAVIA và tai nghe.'),(N'Panasonic',N'Nhật Bản',N'Tủ lạnh và thiết bị gia dụng.'),
(N'Electrolux',N'Thụy Điển',N'Máy giặt và đồ gia dụng.'),(N'Apple',N'Hoa Kỳ',N'Điện thoại iPhone và thiết bị cá nhân.'),
(N'OPPO',N'Trung Quốc',N'Điện thoại thông minh.'),(N'JBL',N'Hoa Kỳ',N'Loa Bluetooth và thiết bị âm thanh.'),
(N'Anker',N'Trung Quốc',N'Củ sạc và cáp USB-C.'),(N'Baseus',N'Trung Quốc',N'Phụ kiện kết nối và vệ sinh thiết bị.'),
(N'Daikin',N'Nhật Bản',N'Máy lạnh và điều hòa Inverter.'),(N'Philips',N'Hà Lan',N'Thiết bị gia dụng và chăm sóc cá nhân.'),
(N'Sharp',N'Nhật Bản',N'Điện lạnh và đồ gia dụng.'),(N'Xiaomi',N'Trung Quốc',N'Thiết bị thông minh và chăm sóc nhà cửa.'),
(N'ASUS',N'Đài Loan',N'Laptop và thiết bị máy tính.'),(N'TP-Link',N'Trung Quốc',N'Router Wi-Fi và thiết bị mạng.');
UPDATE b SET QuocGia=CASE WHEN NULLIF(b.QuocGia,N'') IS NULL THEN e.Country ELSE b.QuocGia END,
MoTa=CASE WHEN NULLIF(b.MoTa,N'') IS NULL OR b.MoTa=N'Thương hiệu cho danh mục mở rộng [QLDAPM-DEMO]' THEN e.Description+N' [QLDAPM-DEMO]' ELSE b.MoTa END
FROM ThuongHieu b JOIN @BrandExamples e ON e.Name=b.TenThuongHieu WHERE b.NgayXoa IS NULL;

-- The receipt is the checkpoint for the initial commercial fixture. Never reset it on rerun.
IF NOT EXISTS(SELECT 1 FROM PhieuNhapKho WHERE MaSoPhieu=N'DEMO-PNK-001')
BEGIN
IF EXISTS(SELECT 1 FROM TonKho t JOIN SanPham s ON s.MaSanPham=t.MaSanPham JOIN @Products p ON p.Code=s.MaSKU WHERE t.SLTon>0)
OR EXISTS(SELECT 1 FROM SerialSanPham s JOIN @Products p ON p.Code=(SELECT MaSKU FROM SanPham WHERE MaSanPham=s.MaSanPham))
    THROW 51007,'DEMO SKU identifiers already contain stock without the seed checkpoint. Use a fresh test database.',1;
DECLARE @Receipt int,@OldReceipt int,@Supplier int=(SELECT TOP(1) MaNCC FROM NhaCungCap WHERE MaSoThue=N'DEMO-NCC01' AND NgayXoa IS NULL);
INSERT PhieuNhapKho(MaSoPhieu,MaNCC,TrangThai,NguoiLap,NguoiNhanHang,NgayNhanThuc,TongTienHang,TongTienThue,GhiChu,TaoLuc)
SELECT N'DEMO-PNK-001',@Supplier,N'hoan_thanh',@Warehouse,@Warehouse,DATEADD(DAY,-14,@Now),SUM(Qty*Cost),SUM(Qty*Cost)*0.1,N'Lô hàng khởi tạo [QLDAPM-DEMO]',DATEADD(DAY,-15,@Now) FROM @Products WHERE Code<>N'DEMO-TV03';
SET @Receipt=SCOPE_IDENTITY();
INSERT PhieuNhapKho(MaSoPhieu,MaNCC,TrangThai,NguoiLap,NguoiNhanHang,NgayNhanThuc,TongTienHang,TongTienThue,GhiChu,TaoLuc)
SELECT N'DEMO-PNK-AGE',@Supplier,N'hoan_thanh',@Warehouse,@Warehouse,DATEADD(DAY,-120,@Now),SUM(Qty*Cost),SUM(Qty*Cost)*0.1,N'Lô tồn lâu để thử báo cáo 90 ngày [QLDAPM-DEMO]',DATEADD(DAY,-121,@Now) FROM @Products WHERE Code=N'DEMO-TV03';
SET @OldReceipt=SCOPE_IDENTITY();
INSERT ChiTietNhapKho(MaPhieuNhap,MaSanPham,SLDatHang,SLDaNhan,GiaNhap,ThueSuat)
SELECT CASE WHEN p.Code=N'DEMO-TV03' THEN @OldReceipt ELSE @Receipt END,s.MaSanPham,p.Qty,p.Qty,p.Cost,10 FROM @Products p JOIN SanPham s ON s.MaSKU=p.Code AND s.NgayXoa IS NULL;
DECLARE @Numbers TABLE(N int);INSERT @Numbers SELECT TOP(60) ROW_NUMBER() OVER(ORDER BY (SELECT NULL)) FROM sys.all_objects;
INSERT SerialSanPham(MaSanPham,SoSerial,IMEI,MaChiTietNhap,GiaVon,NgayNhap)
SELECT s.MaSanPham,p.Code+N'-'+RIGHT(N'000'+CAST(n.N AS nvarchar(3)),3),
CASE WHEN p.Category=N'Điện thoại' THEN N'990'+RIGHT(N'000000000'+CAST(s.MaSanPham AS nvarchar(9)),9)+RIGHT(N'000'+CAST(n.N AS nvarchar(3)),3) ELSE NULL END,
c.MaChiTiet,p.Cost,DATEADD(DAY,CASE WHEN p.Code=N'DEMO-TV03' THEN -120 ELSE -14 END,@Now)
FROM @Products p JOIN SanPham s ON s.MaSKU=p.Code AND s.NgayXoa IS NULL JOIN ChiTietNhapKho c ON c.MaSanPham=s.MaSanPham AND c.MaPhieuNhap IN(@Receipt,@OldReceipt) CROSS JOIN @Numbers n
WHERE p.Tracked=1 AND n.N<=p.Qty;
UPDATE t SET SLTon=t.SLTon+p.Qty FROM TonKho t JOIN SanPham s ON s.MaSanPham=t.MaSanPham JOIN @Products p ON p.Code=s.MaSKU;
INSERT LichSuTonKho(MaTonKho,LoaiBienDong,SoBienDong,SLSau,LoaiTaiLieu,MaTaiLieu,ThucHienBoi,GhiChu,ThoiGian)
SELECT t.MaTonKho,N'nhap_hang',p.Qty,t.SLTon,N'phieu_nhap',CASE WHEN p.Code=N'DEMO-TV03' THEN @OldReceipt ELSE @Receipt END,@Warehouse,N'Nhập lô mẫu',DATEADD(DAY,CASE WHEN p.Code=N'DEMO-TV03' THEN -120 ELSE -14 END,@Now) FROM TonKho t JOIN SanPham s ON s.MaSanPham=t.MaSanPham JOIN @Products p ON p.Code=s.MaSKU;
INSERT LichSuSerial(MaSerial,TrangThaiMoi,LoaiTaiLieu,MaTaiLieu,ThucHienBoi,GhiChu,ThoiGian)
SELECT x.MaSerial,N'trong_kho',N'phieu_nhap',c.MaPhieuNhap,@Warehouse,N'Nhập kho mẫu',x.NgayNhap FROM SerialSanPham x JOIN ChiTietNhapKho c ON c.MaChiTiet=x.MaChiTietNhap WHERE c.MaPhieuNhap IN(@Receipt,@OldReceipt);
INSERT LichSuGiaBan(MaSanPham,GiaNiemYet,GiaToiThieu,NguoiThayDoi,GhiChu,NgayHieuLuc)
SELECT s.MaSanPham,s.GiaNiemYet,s.GiaToiThieu,@Manager,N'Giá khởi tạo demo',DATEADD(DAY,-150,@Now) FROM SanPham s JOIN @Products p ON p.Code=s.MaSKU;

DECLARE @Sales TABLE(Seq int,SKU nvarchar(60),DaysAgo int);
INSERT @Sales VALUES(1,N'DEMO-PH02',0),(2,N'DEMO-TV01',0),(3,N'DEMO-AC01',0),(4,N'DEMO-WM01',0),(5,N'DEMO-PH01',0),(6,N'DEMO-AU01',0),(7,N'DEMO-AC02',0),(8,N'DEMO-FR01',0),
(9,N'DEMO-PH03',1),(10,N'DEMO-TV02',1),(11,N'DEMO-AC05',2),(12,N'DEMO-PH01',2),(13,N'DEMO-FR02',3),(14,N'DEMO-AC04',3),(15,N'DEMO-WM02',4),(16,N'DEMO-TV03',4),
(17,N'DEMO-PH02',5),(18,N'DEMO-AU01',6),(19,N'DEMO-AC01',7),(20,N'DEMO-TV01',8),(21,N'DEMO-WM01',9),(22,N'DEMO-PH01',10),(23,N'DEMO-FR01',12),(24,N'DEMO-AC02',13);
DECLARE @Seq int,@SKU nvarchar(60),@Days int,@SoldAt datetime2,@SP int,@Serial int,@Invoice int,@Line int,@Price decimal(18,2),@Cost decimal(18,2),@Warranty int,@Tracked bit,@Customer int,@Till int,@Who int;
DECLARE @TodayStart datetime2=DATEADD(HOUR,CASE WHEN DATEPART(HOUR,@Now)>=8 THEN 8 ELSE 0 END,CAST(CAST(@Now AS date) AS datetime2));
DECLARE sales_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT Seq,SKU,DaysAgo FROM @Sales ORDER BY DaysAgo DESC,Seq;
OPEN sales_cursor;FETCH NEXT FROM sales_cursor INTO @Seq,@SKU,@Days;
WHILE @@FETCH_STATUS=0
BEGIN
SELECT @SP=MaSanPham,@Price=GiaNiemYet,@Warranty=BaoHanhThang,@Tracked=CoTheoDoiSerial FROM SanPham WHERE MaSKU=@SKU AND NgayXoa IS NULL;
SELECT @Cost=Cost FROM @Products WHERE Code=@SKU;
SET @Serial=NULL;
IF @Tracked=1 SELECT TOP(1) @Serial=MaSerial FROM SerialSanPham WHERE MaSanPham=@SP AND TrangThai=N'trong_kho' ORDER BY MaSerial;
IF @Tracked=1 AND @Serial IS NULL THROW 51005,'Demo fixture has insufficient serial inventory.',1;
SELECT @Customer=MaKhachHang FROM KhachHang WHERE SoDienThoai=N'090000010'+CAST((@Seq-1)%8+1 AS nvarchar(1)) AND NgayXoa IS NULL;
SELECT @Till=MaQuay FROM QuayPOS WHERE MaQuayCode=CASE WHEN @Seq%2=0 THEN N'DEMO-Q01' ELSE N'DEMO-Q02' END;
SET @Who=CASE WHEN @Seq%2=0 THEN @Cashier ELSE @Cashier2 END;
SET @SoldAt=CASE WHEN @Days=0 THEN DATEADD(MINUTE,CAST(DATEDIFF(MINUTE,@TodayStart,@Now)*(9-@Seq)/9.0 AS int),@TodayStart)
ELSE DATEADD(MINUTE,-(@Seq%8)*37-10,DATEADD(DAY,-@Days,@Now)) END;
INSERT HoaDon(SoHoaDon,MaQuay,MaThuNgan,MaKhachHang,TrangThai,TongTienHang,TongTienThue,ThoiGianTao,ThoiGianThanhToan,GhiChu)
VALUES(N'DEMO-HD-'+RIGHT(N'000'+CAST(@Seq AS nvarchar(3)),3),@Till,@Who,@Customer,N'da_thanh_toan',@Price,ROUND(@Price/11,2),DATEADD(MINUTE,-2,@SoldAt),@SoldAt,N'Giao dịch mô phỏng [QLDAPM-DEMO]');
SET @Invoice=SCOPE_IDENTITY();
INSERT ChiTietHoaDon(MaHoaDon,MaSanPham,MaSerial,TenHang,MaSKU,DonGia,GiaVon)
SELECT @Invoice,@SP,@Serial,TenSanPham,MaSKU,@Price,@Cost FROM SanPham WHERE MaSanPham=@SP;
SET @Line=SCOPE_IDENTITY();
INSERT ThanhToan(MaHoaDon,PhuongThuc,TrangThai,SoTien,TienKhachDua,MaThamChieu,ThanhToanLuc)
VALUES(@Invoice,CASE WHEN @Seq%3=0 THEN N'qr_chuyen_khoan' ELSE N'tien_mat' END,N'thanh_cong',@Price,CASE WHEN @Seq%3<>0 THEN CEILING(@Price/100000)*100000 ELSE NULL END,CASE WHEN @Seq%3=0 THEN N'DEMO-TT-'+CAST(@Seq AS nvarchar(10)) ELSE NULL END,@SoldAt);
IF @Serial IS NOT NULL BEGIN
UPDATE SerialSanPham SET TrangThai=N'da_ban',CapNhatLuc=@SoldAt WHERE MaSerial=@Serial;
INSERT LichSuSerial(MaSerial,TrangThaiCu,TrangThaiMoi,LoaiTaiLieu,MaTaiLieu,ThucHienBoi,GhiChu,ThoiGian)
VALUES(@Serial,N'trong_kho',N'da_ban',N'hoa_don',@Invoice,@Who,N'Bán hàng demo',@SoldAt);END;
UPDATE TonKho SET SLTon=SLTon-1,CapNhatLuc=@SoldAt WHERE MaSanPham=@SP;
INSERT LichSuTonKho(MaTonKho,LoaiBienDong,SoBienDong,SLSau,LoaiTaiLieu,MaTaiLieu,ThucHienBoi,GhiChu,ThoiGian)
SELECT MaTonKho,N'xuat_ban',-1,SLTon,N'hoa_don',@Invoice,@Who,N'Bán hàng demo',@SoldAt FROM TonKho WHERE MaSanPham=@SP;
IF @Warranty>0 INSERT BaoHanh(MaChiTietHD,MaSerial,NgayBatDau,NgayKetThuc) VALUES(@Line,@Serial,@SoldAt,DATEADD(MONTH,@Warranty,@SoldAt));
FETCH NEXT FROM sales_cursor INTO @Seq,@SKU,@Days;
END;
CLOSE sales_cursor;DEALLOCATE sales_cursor;

-- A real receiving draft to try partial deliveries; no stock was added for this document.
INSERT PhieuNhapKho(MaSoPhieu,MaNCC,NguoiLap,NgayDuKien,GhiChu) VALUES(N'DEMO-PNK-002',@Supplier,@Warehouse,DATEADD(DAY,2,@Now),N'Chờ nhận 5 máy giặt; dùng để thử nhận từng phần [QLDAPM-DEMO]');
DECLARE @PendingReceipt int=SCOPE_IDENTITY();
INSERT ChiTietNhapKho(MaPhieuNhap,MaSanPham,SLDatHang,GiaNhap) SELECT @PendingReceipt,MaSanPham,5,7800000 FROM SanPham WHERE MaSKU=N'DEMO-WM01' AND NgayXoa IS NULL;

-- Held bills reserve devices, exactly as POS does. Each cashier can still start a new bill.
DECLARE @Held int,@HeldSP int,@HeldSerial int,@HeldPrice decimal(18,2),@HeldUser int,@H int=1;
WHILE @H<=2 BEGIN
SET @HeldUser=CASE WHEN @H=1 THEN @Cashier ELSE @Cashier2 END;
SELECT @HeldSP=MaSanPham,@HeldPrice=GiaNiemYet FROM SanPham WHERE MaSKU=CASE WHEN @H=1 THEN N'DEMO-PH01' ELSE N'DEMO-TV02' END AND NgayXoa IS NULL;
SELECT TOP(1) @HeldSerial=s.MaSerial FROM SerialSanPham s WHERE s.MaSanPham=@HeldSP AND s.TrangThai=N'trong_kho' AND NOT EXISTS(SELECT 1 FROM ChiTietHoaDon c WHERE c.MaSerial=s.MaSerial AND c.HieuLuc=1) ORDER BY s.MaSerial;
INSERT HoaDon(SoHoaDon,MaQuay,MaThuNgan,TrangThai,TongTienHang,TongTienThue,GhiChu)
SELECT N'DEMO-HOLD-0'+CAST(@H AS nvarchar(1)),MaQuay,@HeldUser,N'tam_giu',@HeldPrice,ROUND(@HeldPrice/11,2),N'Khách chờ xác nhận [QLDAPM-DEMO]' FROM QuayPOS WHERE MaQuayCode=N'DEMO-Q01';
SET @Held=SCOPE_IDENTITY();
INSERT ChiTietHoaDon(MaHoaDon,MaSanPham,MaSerial,TenHang,MaSKU,DonGia,GiaVon) SELECT @Held,@HeldSP,@HeldSerial,TenSanPham,MaSKU,@HeldPrice,(SELECT Cost FROM @Products WHERE Code=MaSKU) FROM SanPham WHERE MaSanPham=@HeldSP;
SET @H=@H+1;END;

-- Two warranty requests: received and awaiting customer collection.
DECLARE @BH int,@WSerial int,@Origin int,@WP int,@Stage int=1;
WHILE @Stage<=2 BEGIN
SELECT TOP(1) @BH=b.MaBaoHanh,@WSerial=b.MaSerial,@Origin=h.MaHoaDon,@WP=c.MaSanPham
FROM BaoHanh b JOIN ChiTietHoaDon c ON c.MaChiTiet=b.MaChiTietHD JOIN HoaDon h ON h.MaHoaDon=c.MaHoaDon
WHERE h.SoHoaDon=CASE WHEN @Stage=1 THEN N'DEMO-HD-009' ELSE N'DEMO-HD-017' END;
INSERT YeuCauBaoHanh(MaSoTiepNhan,MaBaoHanh,NhanVienTiepNhan,TenNguoiGui,SoDienThoaiNguoiGui,MoTaSuCo,TinhTrangNhanMay,TrangThai,KetQuaXuLy,NgayHenTra,TiepNhanLuc,HoanTatLuc)
SELECT N'DEMO-BH-00'+CAST(@Stage AS nvarchar(1)),@BH,@Support,k.HoTen,k.SoDienThoai,CASE WHEN @Stage=1 THEN N'Máy nóng khi sạc' ELSE N'Kiểm tra kết nối không ổn định' END,N'Đủ phụ kiện; có trầy nhẹ',CASE WHEN @Stage=1 THEN N'tiep_nhan' ELSE N'hoan_tat' END,CASE WHEN @Stage=2 THEN N'Đã kiểm tra; chờ khách đến nhận' ELSE NULL END,DATEADD(DAY,5,@Now),DATEADD(HOUR,-4,@Now),CASE WHEN @Stage=2 THEN DATEADD(HOUR,-1,@Now) ELSE NULL END
FROM HoaDon h JOIN KhachHang k ON k.MaKhachHang=h.MaKhachHang WHERE h.MaHoaDon=@Origin;
DECLARE @Request int=SCOPE_IDENTITY();
UPDATE SerialSanPham SET TrangThai=N'bao_hanh' WHERE MaSerial=@WSerial;
INSERT LichSuSerial(MaSerial,TrangThaiCu,TrangThaiMoi,LoaiTaiLieu,MaTaiLieu,ThucHienBoi,GhiChu) VALUES(@WSerial,N'da_ban',N'bao_hanh',N'yeu_cau_bao_hanh',@Request,@Support,N'Tiếp nhận bảo hành mẫu');
SET @Stage=@Stage+1;END;

-- One pending return, one approved return, and one completed refund.
DECLARE @R int=1,@Return int,@RHD int,@RLine int,@RSerial int,@RSP int,@RPrice decimal(18,2),@PurchaseTime datetime2,@ReturnCreated datetime2,@ReturnApproved datetime2;
WHILE @R<=3 BEGIN
SELECT @RHD=h.MaHoaDon,@RLine=c.MaChiTiet,@RSerial=c.MaSerial,@RSP=c.MaSanPham,@RPrice=c.ThanhTien,@PurchaseTime=h.ThoiGianThanhToan FROM HoaDon h JOIN ChiTietHoaDon c ON c.MaHoaDon=h.MaHoaDon
WHERE h.SoHoaDon=CASE WHEN @R=1 THEN N'DEMO-HD-002' WHEN @R=2 THEN N'DEMO-HD-005' ELSE N'DEMO-HD-010' END;
SET @ReturnCreated=DATEADD(SECOND,DATEDIFF(SECOND,@PurchaseTime,@Now)/3,@PurchaseTime);
SET @ReturnApproved=DATEADD(SECOND,DATEDIFF(SECOND,@PurchaseTime,@Now)*2/3,@PurchaseTime);
INSERT PhieuTraHang(MaSoPhieu,MaHoaDon,NguoiXuLy,NguoiDuyet,LyDo,PhuongThucHoan,TongTienHoan,TrangThai,DuyetLuc,HoanTatLuc,TaoLuc)
VALUES(N'DEMO-TRA-00'+CAST(@R AS nvarchar(1)),@RHD,@Cashier,CASE WHEN @R>1 THEN @Manager ELSE NULL END,N'Khách đổi nhu cầu, hàng nguyên hộp [QLDAPM-DEMO]',N'tien_mat',@RPrice,CASE @R WHEN 1 THEN N'cho_duyet' WHEN 2 THEN N'da_duyet' ELSE N'hoan_tat' END,CASE WHEN @R>1 THEN @ReturnApproved ELSE NULL END,CASE WHEN @R=3 THEN @Now ELSE NULL END,@ReturnCreated);
SET @Return=SCOPE_IDENTITY();
INSERT ChiTietTraHang(MaPhieuTra,MaChiTietHD,MaSerial,SoLuong,TinhTrangHang,SoTienHoan,NhapLaiKho) VALUES(@Return,@RLine,@RSerial,1,N'nguyen_hop',@RPrice,1);
IF @R=3 BEGIN
UPDATE ChiTietHoaDon SET HieuLuc=0 WHERE MaChiTiet=@RLine;
UPDATE BaoHanh SET TrangThai=N'da_huy' WHERE MaChiTietHD=@RLine;
UPDATE SerialSanPham SET TrangThai=N'trong_kho' WHERE MaSerial=@RSerial;
UPDATE TonKho SET SLTon=SLTon+1 WHERE MaSanPham=@RSP;
INSERT LichSuSerial(MaSerial,TrangThaiCu,TrangThaiMoi,LoaiTaiLieu,MaTaiLieu,ThucHienBoi,GhiChu) VALUES(@RSerial,N'da_ban',N'trong_kho',N'phieu_tra',@Return,@Cashier,N'Nhận lại hàng demo');
INSERT LichSuTonKho(MaTonKho,LoaiBienDong,SoBienDong,SLSau,LoaiTaiLieu,MaTaiLieu,ThucHienBoi,GhiChu)
SELECT MaTonKho,N'nhap_tra_hang',1,SLTon,N'phieu_tra',@Return,@Cashier,N'Hoàn trả mẫu' FROM TonKho WHERE MaSanPham=@RSP;
END;SET @R=@R+1;END;

DECLARE @Promo TABLE(Code nvarchar(50),Name nvarchar(255),Type nvarchar(30),Pct decimal(5,2),Fixed decimal(18,2),Voucher decimal(18,2),Minimum decimal(18,2),Auto bit,Priority int);
INSERT @Promo VALUES(N'DEMO-CAP10',N'Phụ kiện đi kèm · giảm 10%',N'giam_phan_tram',10,NULL,NULL,0,1,10),
(N'DEMO-TIVI300',N'Ưu đãi tivi · bớt 300.000đ',N'giam_tien',NULL,300000,NULL,10000000,0,20),
(N'DEMO-COMBO',N'Mua Galaxy A55 · sạc Anker 390.000đ',N'mua_kem_gia_soc',NULL,NULL,NULL,0,1,30),
(N'DEMO-QUA',N'Mua tivi · tặng bộ vệ sinh',N'tang_qua',NULL,NULL,NULL,10000000,1,40),
(N'DEMO-VOUCHER',N'Mua máy giặt · nhận voucher 200.000đ',N'tang_voucher',NULL,NULL,200000,9000000,1,50);
INSERT KhuyenMai(TenChuongTrinh,MaCode,LoaiKhuyenMai,PhanTramGiam,GiamCodinh,GiaTriVoucher,GiaTriDonToiThieu,TuDongApDung,ChoPhepCongDon,DoUuTien,ApDungToanBo,SoLuongToiDa,NgayBatDau,NgayKetThuc,NguoiTao,MoTa)
SELECT Name,Code,Type,Pct,Fixed,Voucher,Minimum,Auto,1,Priority,0,100,DATEADD(DAY,-7,@Now),DATEADD(DAY,90,@Now),@Manager,N'Chương trình mô phỏng [QLDAPM-DEMO]' FROM @Promo;
DECLARE @Items TABLE(Code nvarchar(50),SKU nvarchar(60),RoleName nvarchar(20),Price decimal(18,2));
INSERT @Items VALUES(N'DEMO-CAP10',N'DEMO-AC02',N'dieu_kien',NULL),(N'DEMO-TIVI300',N'DEMO-TV01',N'dieu_kien',NULL),(N'DEMO-COMBO',N'DEMO-PH01',N'dieu_kien',NULL),(N'DEMO-COMBO',N'DEMO-AC01',N'uu_dai',390000),(N'DEMO-QUA',N'DEMO-TV01',N'dieu_kien',NULL),(N'DEMO-QUA',N'DEMO-AC03',N'uu_dai',0),(N'DEMO-VOUCHER',N'DEMO-WM01',N'dieu_kien',NULL);
INSERT KhuyenMaiSanPham(MaKhuyenMai,VaiTro,MaSanPham,SoLuong,GiaUuDai)
SELECT k.MaKhuyenMai,i.RoleName,s.MaSanPham,1,i.Price FROM @Items i JOIN KhuyenMai k ON k.MaCode=i.Code JOIN SanPham s ON s.MaSKU=i.SKU AND s.NgayXoa IS NULL;
DECLARE @VCPromo int=(SELECT MaKhuyenMai FROM KhuyenMai WHERE MaCode=N'DEMO-VOUCHER'),@VCHD int=(SELECT MaHoaDon FROM HoaDon WHERE SoHoaDon=N'DEMO-HD-004');
INSERT Voucher(MaCode,MaKhuyenMai,GiaTri,MaKhachHang,HoaDonPhatHanh,NgayPhatHanh,HanSuDung)
SELECT N'VC-DEMO-200K',@VCPromo,200000,MaKhachHang,@VCHD,ThoiGianThanhToan,DATEADD(DAY,30,ThoiGianThanhToan) FROM HoaDon WHERE MaHoaDon=@VCHD;
UPDATE KhuyenMai SET SoLuongDaDung=1 WHERE MaKhuyenMai=@VCPromo;
INSERT HoaDonKhuyenMai(MaHoaDon,MaKhuyenMai,SoTienGiam,ApDungLuc) SELECT @VCHD,@VCPromo,0,ThoiGianThanhToan FROM HoaDon WHERE MaHoaDon=@VCHD;
INSERT NhatKyHeThong(MaNhanVien,HanhDong,BangTacDong,MaBanGhi,GiaTriMoi)
VALUES(@Admin,N'khoi_tao_demo',N'HeThong',N'DEMO-v1',N'Khởi tạo dữ liệu demo; giao dịch tài chính hoàn toàn mô phỏng');
END;
COMMIT;
END TRY
BEGIN CATCH
IF @@TRANCOUNT>0 ROLLBACK;
THROW;
END CATCH;
SELECT N'Demo data ready. Existing credentials and stock were not reset.' AS Result;
SELECT u.Login AS DemoLogin,u.Name AS DisplayName,u.RoleName FROM @Users u;
SELECT N'Demo@2026!' AS InitialDemoPassword,N'Only applies to accounts inserted by this seed.' AS Note;
