SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
GO
-- =====================================================================
-- CƠ SỞ DỮ LIỆU: HỆ THỐNG QUẢN LÝ BÁN HÀNG SIÊU THỊ ĐIỆN MÁY
-- Dự án : Xây dựng hệ thống quản lý bán hàng của 01 siêu thị điện máy
-- Phạm vi: 4 phân hệ
--   (1) Danh mục hàng hóa & Kho (Serial/IMEI)
--   (2) Bán hàng & Quầy thu ngân (POS, ~100 quầy)
--   (3) Khuyến mãi & Bảo hành
--   (4) Báo cáo - Thống kê
-- Ngoài phạm vi (KHÔNG có trong schema): bán hàng online/giỏ hàng/giao hàng,
--   nhân sự-chấm công-lương, hạch toán kế toán, quy trình sửa chữa/linh kiện.
-- Quy ước: tiền DECIMAL(18,2); giá bán lẻ ĐÃ BAO GỒM VAT;
--   xóa mềm bằng NgayXoa (cùng ràng buộc cohesion với TrangThai).
-- =====================================================================

IF DB_ID(N'QLSieuThiDienMay') IS NULL
    CREATE DATABASE QLSieuThiDienMay COLLATE Vietnamese_CI_AS;
GO
USE QLSieuThiDienMay;
GO
IF EXISTS (SELECT 1 FROM sys.tables)
    THROW 51000, 'Initial schema requires an empty database. Do not run against existing operational data.', 1;
GO
-- Đọc không chặn ghi: 100 quầy POS bán hàng song song với báo cáo thời gian thực
ALTER DATABASE QLSieuThiDienMay SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
GO

-- =====================================================================
-- 1. NGƯỜI DÙNG HỆ THỐNG & PHÂN QUYỀN
--    (chỉ là tài khoản đăng nhập hệ thống, không phải quản lý nhân sự)
-- =====================================================================

CREATE TABLE VaiTro (
    MaVaiTro    INT             IDENTITY(1,1) PRIMARY KEY,
    TenVaiTro   NVARCHAR(50)    NOT NULL,
    MoTa        NVARCHAR(500),
    TrangThai   BIT             NOT NULL DEFAULT 1,
    CONSTRAINT uq_vt_ten UNIQUE (TenVaiTro)
);

CREATE TABLE Quyen (
    MaQuyen     INT             IDENTITY(1,1) PRIMARY KEY,
    MaCode      NVARCHAR(60)    NOT NULL,       -- vd: POS_BAN_HANG, KHO_NHAP, BAO_CAO_XEM
    TenQuyen    NVARCHAR(150)   NOT NULL,
    Nhom        NVARCHAR(50)    NOT NULL,       -- kho | pos | khuyen_mai | bao_hanh | bao_cao | he_thong
    MoTa        NVARCHAR(500),
    CONSTRAINT uq_quyen_code UNIQUE (MaCode),
    CONSTRAINT chk_quyen_nhom CHECK (Nhom IN (
        N'kho', N'pos', N'khuyen_mai', N'bao_hanh', N'bao_cao', N'he_thong'
    ))
);

CREATE TABLE VaiTroQuyen (
    MaVaiTro    INT NOT NULL,
    MaQuyen     INT NOT NULL,
    CONSTRAINT pk_vtq PRIMARY KEY (MaVaiTro, MaQuyen),
    CONSTRAINT fk_vtq_vaitro FOREIGN KEY (MaVaiTro)
        REFERENCES VaiTro(MaVaiTro) ON DELETE CASCADE,
    CONSTRAINT fk_vtq_quyen  FOREIGN KEY (MaQuyen)
        REFERENCES Quyen(MaQuyen)   ON DELETE CASCADE
);
CREATE INDEX ix_vtq_quyen ON VaiTroQuyen (MaQuyen);

CREATE TABLE NhanVien (
    MaNhanVien      INT             IDENTITY(1,1) PRIMARY KEY,
    MaSoNhanVien    NVARCHAR(20)    NOT NULL,   -- mã hiển thị, vd: NV0001
    HoTen           NVARCHAR(150)   NOT NULL,
    Email           NVARCHAR(255),
    SoDienThoai     NVARCHAR(20),
    MaVaiTro        INT             NOT NULL,
    TrangThai       NVARCHAR(20)    NOT NULL DEFAULT N'hoat_dong',
    NgayTao         DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    NgayXoa         DATETIME2,
    CONSTRAINT fk_nv_vaitro FOREIGN KEY (MaVaiTro)
        REFERENCES VaiTro(MaVaiTro) ON DELETE NO ACTION,
    CONSTRAINT chk_nv_trangthai CHECK (TrangThai IN (N'hoat_dong', N'tam_khoa', N'da_nghi')),
    CONSTRAINT chk_nv_xoa_cohesion CHECK (NgayXoa IS NULL OR TrangThai = N'da_nghi')
);
CREATE UNIQUE INDEX uq_nv_maso  ON NhanVien (MaSoNhanVien) WHERE NgayXoa IS NULL;
CREATE UNIQUE INDEX uq_nv_email ON NhanVien (Email) WHERE NgayXoa IS NULL AND Email IS NOT NULL;
CREATE INDEX ix_nv_vaitro ON NhanVien (MaVaiTro);

CREATE TABLE TaiKhoan (
    MaTaiKhoan          INT             IDENTITY(1,1) PRIMARY KEY,
    MaNhanVien          INT             NOT NULL,
    TenDangNhap         NVARCHAR(100)   NOT NULL,
    MatKhauHash         NVARCHAR(500)   NOT NULL,
    TrangThai           NVARCHAR(20)    NOT NULL DEFAULT N'hoat_dong',
    SoLanDangNhapSai    INT             NOT NULL DEFAULT 0,
    KhoaDen             DATETIME2,
    DangNhapLanCuoi     DATETIME2,
    DoiMatKhauLuc       DATETIME2,
    NgayTao             DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    NgayXoa             DATETIME2,
    CONSTRAINT fk_tk_nhanvien FOREIGN KEY (MaNhanVien)
        REFERENCES NhanVien(MaNhanVien) ON DELETE NO ACTION,
    CONSTRAINT chk_tk_trangthai CHECK (TrangThai IN (N'hoat_dong', N'bi_khoa', N'vo_hieu')),
    CONSTRAINT chk_tk_sai_login CHECK (SoLanDangNhapSai >= 0),
    CONSTRAINT chk_tk_xoa_cohesion CHECK (NgayXoa IS NULL OR TrangThai = N'vo_hieu')
);
CREATE UNIQUE INDEX uq_tk_tendangnhap ON TaiKhoan (TenDangNhap) WHERE NgayXoa IS NULL;
CREATE UNIQUE INDEX uq_tk_nhanvien    ON TaiKhoan (MaNhanVien)  WHERE NgayXoa IS NULL;  -- 1 NV : 1 tài khoản đang dùng

-- Nhật ký thao tác nhạy cảm: đổi giá, hủy hóa đơn, điều chỉnh tồn kho, đổi quyền...
CREATE TABLE NhatKyHeThong (
    MaNhatKy        BIGINT          IDENTITY(1,1) PRIMARY KEY,
    MaNhanVien      INT,
    HanhDong        NVARCHAR(50)    NOT NULL,   -- dang_nhap, doi_gia, huy_hoa_don, dieu_chinh_kho...
    BangTacDong     NVARCHAR(100),
    MaBanGhi        NVARCHAR(50),
    GiaTriCu        NVARCHAR(MAX),
    GiaTriMoi       NVARCHAR(MAX),
    DiaChiIP        VARCHAR(45),
    ThoiGian        DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT fk_nk_nhanvien FOREIGN KEY (MaNhanVien)
        REFERENCES NhanVien(MaNhanVien) ON DELETE NO ACTION
);
CREATE INDEX ix_nk_thoigian  ON NhatKyHeThong (ThoiGian DESC);
CREATE INDEX ix_nk_nhanvien  ON NhatKyHeThong (MaNhanVien, ThoiGian DESC) WHERE MaNhanVien IS NOT NULL;
CREATE INDEX ix_nk_bang      ON NhatKyHeThong (BangTacDong, MaBanGhi);
GO

-- =====================================================================
-- 2. QUẦY POS (~100 quầy)
-- =====================================================================

CREATE TABLE QuayPOS (
    MaQuay          INT             IDENTITY(1,1) PRIMARY KEY,
    MaQuayCode      NVARCHAR(20)    NOT NULL,   -- vd: POS-001
    TenQuay         NVARCHAR(100)   NOT NULL,
    ViTri           NVARCHAR(100),              -- khu vực/tầng trong siêu thị
    DiaChiIP        VARCHAR(45),
    TenMayQuet      NVARCHAR(100),              -- máy quét mã vạch
    TenMayIn        NVARCHAR(100),              -- máy in hóa đơn
    TrangThai       NVARCHAR(20)    NOT NULL DEFAULT N'hoat_dong',
    NgayTao         DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT uq_quay_code UNIQUE (MaQuayCode),
    CONSTRAINT chk_quay_trangthai CHECK (TrangThai IN (N'hoat_dong', N'bao_tri', N'ngung_hoat_dong'))
);
CREATE UNIQUE INDEX uq_quay_ip ON QuayPOS (DiaChiIP) WHERE DiaChiIP IS NOT NULL;
GO

-- =====================================================================
-- 3. DANH MỤC HÀNG HÓA
-- =====================================================================

CREATE TABLE ThuongHieu (
    MaThuongHieu    INT             IDENTITY(1,1) PRIMARY KEY,
    TenThuongHieu   NVARCHAR(100)   NOT NULL,
    QuocGia         NVARCHAR(100),
    MoTa            NVARCHAR(2000),
    TrangThai       BIT             NOT NULL DEFAULT 1,   -- 1 = đang dùng, 0 = ẩn/đã xóa
    NgayTao         DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    NgayXoa         DATETIME2,
    CONSTRAINT uq_th_ten UNIQUE (TenThuongHieu),
    CONSTRAINT chk_th_xoa_cohesion CHECK (NgayXoa IS NULL OR TrangThai = 0)
);

-- Ngành hàng dạng cây: Tivi, Tủ lạnh, Máy giặt, Điện thoại, Phụ kiện...
CREATE TABLE DanhMuc (
    MaDanhMuc       INT             IDENTITY(1,1) PRIMARY KEY,
    TenDanhMuc      NVARCHAR(100)   NOT NULL,
    MaDanhMucCha    INT,
    MaDanhMucChaNN  AS ISNULL(MaDanhMucCha, -1) PERSISTED,
    MoTa            NVARCHAR(2000),
    ThuTuHienThi    INT             NOT NULL DEFAULT 0,
    NgayTao         DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    NgayXoa         DATETIME2,
    CONSTRAINT fk_dm_cha FOREIGN KEY (MaDanhMucCha)
        REFERENCES DanhMuc(MaDanhMuc) ON DELETE NO ACTION,
    CONSTRAINT chk_dm_no_self_loop CHECK (MaDanhMucCha IS NULL OR MaDanhMucCha <> MaDanhMuc)
);
CREATE INDEX ix_dm_cha ON DanhMuc (MaDanhMucCha);
CREATE UNIQUE INDEX uq_dm_ten_parent ON DanhMuc (TenDanhMuc, MaDanhMucChaNN) WHERE NgayXoa IS NULL;

-- Mỗi dòng SanPham là MỘT SKU bán được (vd: "iPhone 15 128GB Đen" là 1 SKU riêng).
CREATE TABLE SanPham (
    MaSanPham           INT             IDENTITY(1,1) PRIMARY KEY,
    MaSKU               NVARCHAR(60)    NOT NULL,
    MaVach              NVARCHAR(60),                       -- barcode (hàng không theo serial / phụ kiện)
    TenSanPham          NVARCHAR(255)   NOT NULL,
    MaDanhMuc           INT             NOT NULL,
    MaThuongHieu        INT             NOT NULL,
    DonViTinh           NVARCHAR(20)    NOT NULL DEFAULT N'cái',
    MoTa                NVARCHAR(4000),
    ThongSoKyThuat      NVARCHAR(MAX),                      -- JSON: {"Màn hình":"6.1 inch","RAM":"8GB",...}
    GiaNiemYet          DECIMAL(18,2)   NOT NULL,           -- giá hiện hành (lịch sử ở LichSuGiaBan)
    GiaToiThieu         DECIMAL(18,2),                      -- sàn giá: chặn giảm giá thủ công dưới mức này
    ThueSuatVAT         DECIMAL(5,2)    NOT NULL DEFAULT 10,
    BaoHanhThang        INT             NOT NULL DEFAULT 0, -- thời hạn bảo hành mặc định
    CoTheoDoiSerial     BIT             NOT NULL DEFAULT 0, -- 1 = bắt buộc quản lý từng Serial/IMEI
    TrangThai           NVARCHAR(30)    NOT NULL DEFAULT N'dang_kinh_doanh',
    NgayTao             DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CapNhatLuc          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    NgayXoa             DATETIME2,
    CONSTRAINT fk_sp_danhmuc    FOREIGN KEY (MaDanhMuc)
        REFERENCES DanhMuc(MaDanhMuc)       ON DELETE NO ACTION,
    CONSTRAINT fk_sp_thuonghieu FOREIGN KEY (MaThuongHieu)
        REFERENCES ThuongHieu(MaThuongHieu) ON DELETE NO ACTION,
    CONSTRAINT chk_sp_trangthai CHECK (TrangThai IN (N'dang_kinh_doanh', N'ngung_kinh_doanh')),
    CONSTRAINT chk_sp_gia       CHECK (GiaNiemYet >= 0),
    CONSTRAINT chk_sp_giasan    CHECK (GiaToiThieu IS NULL OR (GiaToiThieu >= 0 AND GiaToiThieu <= GiaNiemYet)),
    CONSTRAINT chk_sp_vat       CHECK (ThueSuatVAT BETWEEN 0 AND 100),
    CONSTRAINT chk_sp_baohanh   CHECK (BaoHanhThang >= 0),
    CONSTRAINT chk_sp_thongso   CHECK (ThongSoKyThuat IS NULL OR ISJSON(ThongSoKyThuat) = 1),
    CONSTRAINT chk_sp_xoa_cohesion CHECK (NgayXoa IS NULL OR TrangThai = N'ngung_kinh_doanh')
);
CREATE UNIQUE INDEX uq_sp_sku   ON SanPham (MaSKU)  WHERE NgayXoa IS NULL;
CREATE UNIQUE INDEX uq_sp_mavach ON SanPham (MaVach) WHERE MaVach IS NOT NULL AND NgayXoa IS NULL;
CREATE INDEX ix_sp_danhmuc    ON SanPham (MaDanhMuc);
CREATE INDEX ix_sp_thuonghieu ON SanPham (MaThuongHieu);
CREATE INDEX ix_sp_trangthai  ON SanPham (TrangThai);
CREATE INDEX ix_sp_ten        ON SanPham (TenSanPham);

-- Lịch sử chính sách giá (ban quản lý đổi giá niêm yết / giá sàn)
CREATE TABLE LichSuGiaBan (
    MaLichSuGia     INT             IDENTITY(1,1) PRIMARY KEY,
    MaSanPham       INT             NOT NULL,
    GiaNiemYet      DECIMAL(18,2)   NOT NULL,
    GiaToiThieu     DECIMAL(18,2),
    NgayHieuLuc     DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    NguoiThayDoi    INT,
    GhiChu          NVARCHAR(255),
    CONSTRAINT fk_lsg_sanpham  FOREIGN KEY (MaSanPham)
        REFERENCES SanPham(MaSanPham)   ON DELETE NO ACTION,
    CONSTRAINT fk_lsg_nhanvien FOREIGN KEY (NguoiThayDoi)
        REFERENCES NhanVien(MaNhanVien) ON DELETE NO ACTION,
    CONSTRAINT chk_lsg_gia    CHECK (GiaNiemYet >= 0),
    CONSTRAINT chk_lsg_giasan CHECK (GiaToiThieu IS NULL OR (GiaToiThieu >= 0 AND GiaToiThieu <= GiaNiemYet))
);
CREATE INDEX ix_lsg_sanpham_hieuluc ON LichSuGiaBan (MaSanPham, NgayHieuLuc DESC);
GO

-- =====================================================================
-- 4. NHẬP KHO
-- =====================================================================

CREATE TABLE NhaCungCap (
    MaNCC               INT             IDENTITY(1,1) PRIMARY KEY,
    TenNCC              NVARCHAR(150)   NOT NULL,
    Email               NVARCHAR(255),
    SoDienThoai         NVARCHAR(20),
    DiaChi              NVARCHAR(500),
    NguoiLienHe         NVARCHAR(150),
    MaSoThue            NVARCHAR(20),
    TrangThai           BIT             NOT NULL DEFAULT 1,
    NgayTao             DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    NgayXoa             DATETIME2,
    CONSTRAINT chk_ncc_xoa_cohesion CHECK (NgayXoa IS NULL OR TrangThai = 0)
);
CREATE UNIQUE INDEX uq_ncc_masothue ON NhaCungCap (MaSoThue) WHERE MaSoThue IS NOT NULL AND NgayXoa IS NULL;

-- Phiếu nhập kho (lập thủ công)
CREATE TABLE PhieuNhapKho (
    MaPhieuNhap     INT             IDENTITY(1,1) PRIMARY KEY,
    MaSoPhieu       NVARCHAR(50)    NOT NULL,
    MaNCC           INT             NOT NULL,
    SoChungTuNCC    NVARCHAR(50),                   -- số hóa đơn/phiếu giao hàng của NCC
    TrangThai       NVARCHAR(20)    NOT NULL DEFAULT N'nhap',
    NguoiLap        INT             NOT NULL,
    NguoiNhanHang   INT,
    NgayDuKien      DATETIME2,
    NgayNhanThuc    DATETIME2,
    TongTienHang    DECIMAL(18,2)   NOT NULL DEFAULT 0,
    TongTienThue    DECIMAL(18,2)   NOT NULL DEFAULT 0,
    TongCong        AS (CAST(TongTienHang + TongTienThue AS DECIMAL(18,2))) PERSISTED,
    GhiChu          NVARCHAR(500),
    TaoLuc          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CapNhatLuc      DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT fk_pnk_ncc      FOREIGN KEY (MaNCC)
        REFERENCES NhaCungCap(MaNCC)    ON DELETE NO ACTION,
    CONSTRAINT fk_pnk_nguoilap FOREIGN KEY (NguoiLap)
        REFERENCES NhanVien(MaNhanVien) ON DELETE NO ACTION,
    CONSTRAINT fk_pnk_nguoinhan FOREIGN KEY (NguoiNhanHang)
        REFERENCES NhanVien(MaNhanVien) ON DELETE NO ACTION,
    CONSTRAINT uq_pnk_sophieu UNIQUE (MaSoPhieu),
    CONSTRAINT chk_pnk_trangthai CHECK (TrangThai IN (N'nhap', N'mot_phan', N'hoan_thanh', N'huy')),
    CONSTRAINT chk_pnk_tienhang  CHECK (TongTienHang >= 0),
    CONSTRAINT chk_pnk_thue      CHECK (TongTienThue >= 0),
    CONSTRAINT chk_pnk_hoanthanh CHECK (TrangThai <> N'hoan_thanh' OR NgayNhanThuc IS NOT NULL)
);
CREATE INDEX ix_pnk_ncc       ON PhieuNhapKho (MaNCC);
CREATE INDEX ix_pnk_trangthai ON PhieuNhapKho (TrangThai);
CREATE INDEX ix_pnk_taoluc    ON PhieuNhapKho (TaoLuc DESC);

CREATE TABLE ChiTietNhapKho (
    MaChiTiet           INT             IDENTITY(1,1) PRIMARY KEY,
    MaPhieuNhap         INT             NOT NULL,
    MaSanPham           INT             NOT NULL,
    SLDatHang           INT             NOT NULL,
    SLDaNhan            INT             NOT NULL DEFAULT 0,
    GiaNhap             DECIMAL(18,2)   NOT NULL,       -- giá nhập chưa VAT
    ChietKhauPhanTram   DECIMAL(5,2)    NOT NULL DEFAULT 0,
    ThueSuat            DECIMAL(5,2)    NOT NULL DEFAULT 10,
    ThanhTien           AS (CAST(
        SLDaNhan * GiaNhap * (1 - ChietKhauPhanTram / 100.0) * (1 + ThueSuat / 100.0)
    AS DECIMAL(18,2))) PERSISTED,
    GhiChu              NVARCHAR(255),
    CONSTRAINT fk_ctnk_phieu   FOREIGN KEY (MaPhieuNhap)
        REFERENCES PhieuNhapKho(MaPhieuNhap) ON DELETE CASCADE,
    CONSTRAINT fk_ctnk_sanpham FOREIGN KEY (MaSanPham)
        REFERENCES SanPham(MaSanPham)        ON DELETE NO ACTION,
    CONSTRAINT uq_ctnk_item       UNIQUE (MaPhieuNhap, MaSanPham),
    CONSTRAINT chk_ctnk_sldat     CHECK (SLDatHang > 0),
    CONSTRAINT chk_ctnk_slnhan    CHECK (SLDaNhan >= 0),
    CONSTRAINT chk_ctnk_khong_qua CHECK (SLDaNhan <= SLDatHang),
    CONSTRAINT chk_ctnk_gia       CHECK (GiaNhap > 0),
    CONSTRAINT chk_ctnk_chietkhau CHECK (ChietKhauPhanTram BETWEEN 0 AND 100),
    CONSTRAINT chk_ctnk_thue      CHECK (ThueSuat BETWEEN 0 AND 100)
);
CREATE INDEX ix_ctnk_sanpham ON ChiTietNhapKho (MaSanPham);
GO

-- =====================================================================
-- 5. SERIAL/IMEI & TỒN KHO
-- =====================================================================

-- Mỗi thiết bị vật lý = 1 dòng. Serial/IMEI là duy nhất toàn hệ thống (rủi ro R01).
CREATE TABLE SerialSanPham (
    MaSerial        INT             IDENTITY(1,1) PRIMARY KEY,
    MaSanPham       INT             NOT NULL,
    SoSerial        NVARCHAR(100)   NOT NULL,
    IMEI            NVARCHAR(15),
    IMEI2           NVARCHAR(15),                   -- điện thoại 2 SIM
    MaChiTietNhap   INT,                            -- truy vết lô nhập (NULL = tồn đầu kỳ)
    GiaVon          DECIMAL(18,2),                  -- giá vốn của chính thiết bị này
    TrangThai       NVARCHAR(20)    NOT NULL DEFAULT N'trong_kho',
    NgayNhap        DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CapNhatLuc      DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT fk_ssp_sanpham  FOREIGN KEY (MaSanPham)
        REFERENCES SanPham(MaSanPham)          ON DELETE NO ACTION,
    CONSTRAINT fk_ssp_ctnhap   FOREIGN KEY (MaChiTietNhap)
        REFERENCES ChiTietNhapKho(MaChiTiet)   ON DELETE NO ACTION,
    CONSTRAINT uq_ssp_serial   UNIQUE (SoSerial),
    CONSTRAINT chk_ssp_trangthai CHECK (TrangThai IN (
        N'trong_kho', N'da_ban', N'bao_hanh', N'hong', N'mat'
    )),
    CONSTRAINT chk_ssp_imei  CHECK (IMEI  IS NULL OR (LEN(IMEI)  = 15 AND IMEI  NOT LIKE N'%[^0-9]%')),
    CONSTRAINT chk_ssp_imei2 CHECK (IMEI2 IS NULL OR (LEN(IMEI2) = 15 AND IMEI2 NOT LIKE N'%[^0-9]%')),
    CONSTRAINT chk_ssp_imei_khac CHECK (IMEI2 IS NULL OR (IMEI IS NOT NULL AND IMEI <> IMEI2)),
    CONSTRAINT chk_ssp_giavon CHECK (GiaVon IS NULL OR GiaVon >= 0)
);
CREATE UNIQUE INDEX uq_ssp_imei  ON SerialSanPham (IMEI)  WHERE IMEI  IS NOT NULL;
CREATE UNIQUE INDEX uq_ssp_imei2 ON SerialSanPham (IMEI2) WHERE IMEI2 IS NOT NULL;
CREATE INDEX ix_ssp_sanpham_tt ON SerialSanPham (MaSanPham, TrangThai);
CREATE INDEX ix_ssp_ctnhap     ON SerialSanPham (MaChiTietNhap) WHERE MaChiTietNhap IS NOT NULL;
-- Báo cáo hàng tồn lâu ngày
CREATE INDEX ix_ssp_tonlau     ON SerialSanPham (NgayNhap) INCLUDE (MaSanPham) WHERE TrangThai = N'trong_kho';

-- Vòng đời từng Serial: nhập kho -> bán -> bảo hành -> ...
CREATE TABLE LichSuSerial (
    MaLichSu        BIGINT          IDENTITY(1,1) PRIMARY KEY,
    MaSerial        INT             NOT NULL,
    TrangThaiCu     NVARCHAR(20),
    TrangThaiMoi    NVARCHAR(20)    NOT NULL,
    LoaiTaiLieu     NVARCHAR(30),
    MaTaiLieu       INT,
    ThucHienBoi     INT,
    GhiChu          NVARCHAR(255),
    ThoiGian        DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT fk_lss_serial   FOREIGN KEY (MaSerial)
        REFERENCES SerialSanPham(MaSerial) ON DELETE NO ACTION,
    CONSTRAINT fk_lss_nhanvien FOREIGN KEY (ThucHienBoi)
        REFERENCES NhanVien(MaNhanVien)    ON DELETE NO ACTION,
    CONSTRAINT chk_lss_cu  CHECK (TrangThaiCu IS NULL OR TrangThaiCu IN (
        N'trong_kho', N'da_ban', N'bao_hanh', N'hong', N'mat')),
    CONSTRAINT chk_lss_moi CHECK (TrangThaiMoi IN (
        N'trong_kho', N'da_ban', N'bao_hanh', N'hong', N'mat')),
    CONSTRAINT chk_lss_loai CHECK (LoaiTaiLieu IS NULL OR LoaiTaiLieu IN (
        N'phieu_nhap', N'hoa_don', N'phieu_tra', N'phieu_kiem_ke', N'yeu_cau_bao_hanh'))
);
CREATE INDEX ix_lss_serial_tg ON LichSuSerial (MaSerial, ThoiGian DESC);
CREATE INDEX ix_lss_tailieu   ON LichSuSerial (LoaiTaiLieu, MaTaiLieu) WHERE MaTaiLieu IS NOT NULL;

-- Tồn kho theo SKU (với hàng có serial: SLTon phải = số serial 'trong_kho' -> xem vw_ChenhLechTonKho)
CREATE TABLE TonKho (
    MaTonKho        INT             IDENTITY(1,1) PRIMARY KEY,
    MaSanPham       INT             NOT NULL,
    SLTon           INT             NOT NULL DEFAULT 0,
    TonToiThieu     INT             NOT NULL DEFAULT 0,     -- chạm ngưỡng này -> cảnh báo
    TonToiDa        INT,
    ViTriKho        NVARCHAR(100),
    CapNhatLuc      DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT fk_tk_sanpham FOREIGN KEY (MaSanPham)
        REFERENCES SanPham(MaSanPham) ON DELETE NO ACTION,
    CONSTRAINT uq_tk_sanpham    UNIQUE (MaSanPham),
    CONSTRAINT chk_tk_slton     CHECK (SLTon >= 0),
    CONSTRAINT chk_tk_toithieu  CHECK (TonToiThieu >= 0),
    CONSTRAINT chk_tk_toida     CHECK (TonToiDa IS NULL OR TonToiDa >= TonToiThieu)
);

-- Sổ cái biến động tồn kho (chỉ thêm, không sửa)
CREATE TABLE LichSuTonKho (
    MaLichSu        BIGINT          IDENTITY(1,1) PRIMARY KEY,
    MaTonKho        INT             NOT NULL,
    LoaiBienDong    NVARCHAR(30)    NOT NULL,
    SoBienDong      INT             NOT NULL,       -- +nhập / -xuất
    SLSau           INT             NOT NULL,       -- tồn sau biến động, tiện đối chiếu
    LoaiTaiLieu     NVARCHAR(30),
    MaTaiLieu       INT,
    ThucHienBoi     INT,
    GhiChu          NVARCHAR(255),
    ThoiGian        DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT fk_lstk_tonkho   FOREIGN KEY (MaTonKho)
        REFERENCES TonKho(MaTonKho)     ON DELETE NO ACTION,
    CONSTRAINT fk_lstk_nhanvien FOREIGN KEY (ThucHienBoi)
        REFERENCES NhanVien(MaNhanVien) ON DELETE NO ACTION,
    CONSTRAINT chk_lstk_loai CHECK (LoaiBienDong IN (
        N'nhap_hang', N'xuat_ban', N'nhap_tra_hang', N'huy_hoa_don', N'dieu_chinh'
    )),
    CONSTRAINT chk_lstk_sobd CHECK (SoBienDong <> 0),
    CONSTRAINT chk_lstk_sau  CHECK (SLSau >= 0),
    CONSTRAINT chk_lstk_tailieu CHECK (LoaiTaiLieu IS NULL OR LoaiTaiLieu IN (
        N'phieu_nhap', N'hoa_don', N'phieu_tra', N'phieu_kiem_ke'))
);
CREATE INDEX ix_lstk_tonkho_tg ON LichSuTonKho (MaTonKho, ThoiGian DESC);
CREATE INDEX ix_lstk_thoigian  ON LichSuTonKho (ThoiGian DESC);
CREATE INDEX ix_lstk_tailieu   ON LichSuTonKho (LoaiTaiLieu, MaTaiLieu) WHERE MaTaiLieu IS NOT NULL;
GO

-- =====================================================================
-- 6. KIỂM KÊ KHO (đối soát sổ sách với kho thực tế)
-- =====================================================================

CREATE TABLE PhieuKiemKe (
    MaPhieuKiemKe   INT             IDENTITY(1,1) PRIMARY KEY,
    MaSoPhieu       NVARCHAR(50)    NOT NULL,
    NguoiLap        INT             NOT NULL,
    TrangThai       NVARCHAR(20)    NOT NULL DEFAULT N'dang_kiem',
    NgayKiemKe      DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    HoanThanhLuc    DATETIME2,
    GhiChu          NVARCHAR(500),
    CONSTRAINT fk_pkk_nguoilap FOREIGN KEY (NguoiLap)
        REFERENCES NhanVien(MaNhanVien) ON DELETE NO ACTION,
    CONSTRAINT uq_pkk_sophieu UNIQUE (MaSoPhieu),
    CONSTRAINT chk_pkk_trangthai CHECK (TrangThai IN (N'dang_kiem', N'hoan_thanh', N'huy')),
    CONSTRAINT chk_pkk_hoanthanh CHECK (TrangThai <> N'hoan_thanh' OR HoanThanhLuc IS NOT NULL)
);
CREATE INDEX ix_pkk_trangthai ON PhieuKiemKe (TrangThai);

CREATE TABLE ChiTietKiemKe (
    MaChiTiet       INT             IDENTITY(1,1) PRIMARY KEY,
    MaPhieuKiemKe   INT             NOT NULL,
    MaSanPham       INT             NOT NULL,
    SLHeThong       INT             NOT NULL,       -- chụp tồn hệ thống lúc bắt đầu kiểm
    SLThucTe        INT,                            -- NULL = chưa đếm
    ChenhLech       AS (SLThucTe - SLHeThong) PERSISTED,
    LyDoChenhLech   NVARCHAR(255),
    DaDieuChinh     BIT             NOT NULL DEFAULT 0,
    CONSTRAINT fk_ctkk_phieu   FOREIGN KEY (MaPhieuKiemKe)
        REFERENCES PhieuKiemKe(MaPhieuKiemKe) ON DELETE CASCADE,
    CONSTRAINT fk_ctkk_sanpham FOREIGN KEY (MaSanPham)
        REFERENCES SanPham(MaSanPham)         ON DELETE NO ACTION,
    CONSTRAINT uq_ctkk_item     UNIQUE (MaPhieuKiemKe, MaSanPham),
    CONSTRAINT chk_ctkk_hethong CHECK (SLHeThong >= 0),
    CONSTRAINT chk_ctkk_thucte  CHECK (SLThucTe IS NULL OR SLThucTe >= 0)
);
CREATE INDEX ix_ctkk_sanpham ON ChiTietKiemKe (MaSanPham);

-- Kiểm kê đến từng Serial/IMEI cho hàng có theo dõi serial
CREATE TABLE ChiTietKiemKeSerial (
    MaChiTietSerial INT             IDENTITY(1,1) PRIMARY KEY,
    MaChiTiet       INT             NOT NULL,
    MaSerial        INT             NOT NULL,
    TrangThaiHeThong NVARCHAR(20)   NOT NULL,
    TimThay         BIT,                            -- NULL = chưa kiểm
    GhiChu          NVARCHAR(255),
    CONSTRAINT fk_ctkks_chitiet FOREIGN KEY (MaChiTiet)
        REFERENCES ChiTietKiemKe(MaChiTiet)  ON DELETE CASCADE,
    CONSTRAINT fk_ctkks_serial  FOREIGN KEY (MaSerial)
        REFERENCES SerialSanPham(MaSerial)   ON DELETE NO ACTION,
    CONSTRAINT uq_ctkks_item UNIQUE (MaChiTiet, MaSerial),
    CONSTRAINT chk_ctkks_tt CHECK (TrangThaiHeThong IN (
        N'trong_kho', N'da_ban', N'bao_hanh', N'hong', N'mat'))
);
CREATE INDEX ix_ctkks_serial ON ChiTietKiemKeSerial (MaSerial);
GO

-- =====================================================================
-- 7. KHÁCH HÀNG (khách mua tại quầy; có thể bỏ trống nếu khách vãng lai)
-- =====================================================================

CREATE TABLE KhachHang (
    MaKhachHang     INT             IDENTITY(1,1) PRIMARY KEY,
    HoTen           NVARCHAR(150)   NOT NULL,
    SoDienThoai     NVARCHAR(20)    NOT NULL,       -- khóa tra cứu chính khi bảo hành
    Email           NVARCHAR(255),
    DiaChi          NVARCHAR(500),
    TenCongTy       NVARCHAR(200),                  -- khi xuất hóa đơn VAT
    MaSoThue        NVARCHAR(20),
    GhiChu          NVARCHAR(500),
    NgayTao         DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    NgayXoa         DATETIME2
);
CREATE UNIQUE INDEX uq_kh_sdt ON KhachHang (SoDienThoai) WHERE NgayXoa IS NULL;
CREATE INDEX ix_kh_hoten ON KhachHang (HoTen);
GO

-- =====================================================================
-- 8. KHUYẾN MÃI
--    Loại: giảm % | giảm tiền | mua kèm giá sốc | tặng quà | tặng voucher
-- =====================================================================

CREATE TABLE KhuyenMai (
    MaKhuyenMai         INT             IDENTITY(1,1) PRIMARY KEY,
    TenChuongTrinh      NVARCHAR(255)   NOT NULL,
    MaCode              NVARCHAR(50),                   -- mã nhập tay (NULL nếu chỉ tự động áp dụng)
    LoaiKhuyenMai       NVARCHAR(30)    NOT NULL,
    PhanTramGiam        DECIMAL(5,2),
    SoTienGiamToiDa     DECIMAL(18,2),                  -- trần giảm khi giảm theo %
    GiamCodinh          DECIMAL(18,2),
    GiaTriVoucher       DECIMAL(18,2),                  -- giá trị voucher được tặng
    GiaTriDonToiThieu   DECIMAL(18,2)   NOT NULL DEFAULT 0,
    TuDongApDung        BIT             NOT NULL DEFAULT 1,
    ChoPhepCongDon      BIT             NOT NULL DEFAULT 0,
    DoUuTien            INT             NOT NULL DEFAULT 0,
    ApDungToanBo        BIT             NOT NULL DEFAULT 0, -- 1 = mọi sản phẩm; 0 = theo KhuyenMaiSanPham
    SoLuongToiDa        INT,
    SoLuongDaDung       INT             NOT NULL DEFAULT 0,
    NgayBatDau          DATETIME2       NOT NULL,
    NgayKetThuc         DATETIME2       NOT NULL,
    TrangThai           NVARCHAR(20)    NOT NULL DEFAULT N'hoat_dong',
    MoTa                NVARCHAR(2000),
    NguoiTao            INT             NOT NULL,
    NgayTao             DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT fk_km_nguoitao FOREIGN KEY (NguoiTao)
        REFERENCES NhanVien(MaNhanVien) ON DELETE NO ACTION,
    CONSTRAINT chk_km_loai CHECK (LoaiKhuyenMai IN (
        N'giam_phan_tram', N'giam_tien', N'mua_kem_gia_soc', N'tang_qua', N'tang_voucher'
    )),
    -- Mỗi loại chỉ được mang đúng các cột giá trị của nó
    CONSTRAINT chk_km_hinhdang CHECK (
           (LoaiKhuyenMai = N'giam_phan_tram' AND PhanTramGiam IS NOT NULL AND GiamCodinh IS NULL     AND GiaTriVoucher IS NULL)
        OR (LoaiKhuyenMai = N'giam_tien'      AND GiamCodinh   IS NOT NULL AND PhanTramGiam IS NULL   AND GiaTriVoucher IS NULL)
        OR (LoaiKhuyenMai IN (N'mua_kem_gia_soc', N'tang_qua')
                                              AND PhanTramGiam IS NULL AND GiamCodinh IS NULL         AND GiaTriVoucher IS NULL)
        OR (LoaiKhuyenMai = N'tang_voucher'   AND GiaTriVoucher IS NOT NULL AND PhanTramGiam IS NULL  AND GiamCodinh IS NULL)
    ),
    CONSTRAINT chk_km_phantram  CHECK (PhanTramGiam IS NULL OR (PhanTramGiam > 0 AND PhanTramGiam <= 100)),
    CONSTRAINT chk_km_giamcodinh CHECK (GiamCodinh IS NULL OR GiamCodinh > 0),
    CONSTRAINT chk_km_voucher   CHECK (GiaTriVoucher IS NULL OR GiaTriVoucher > 0),
    CONSTRAINT chk_km_tran      CHECK (SoTienGiamToiDa IS NULL OR (PhanTramGiam IS NOT NULL AND SoTienGiamToiDa > 0)),
    CONSTRAINT chk_km_dontoithieu CHECK (GiaTriDonToiThieu >= 0),
    CONSTRAINT chk_km_soluong   CHECK (SoLuongToiDa IS NULL OR (SoLuongToiDa > 0 AND SoLuongDaDung <= SoLuongToiDa)),
    CONSTRAINT chk_km_dadung    CHECK (SoLuongDaDung >= 0),
    CONSTRAINT chk_km_ngay      CHECK (NgayKetThuc > NgayBatDau),
    CONSTRAINT chk_km_code      CHECK (TuDongApDung = 1 OR MaCode IS NOT NULL),
    CONSTRAINT chk_km_trangthai CHECK (TrangThai IN (N'hoat_dong', N'tam_dung', N'het_han', N'huy'))
);
CREATE UNIQUE INDEX uq_km_code ON KhuyenMai (MaCode) WHERE MaCode IS NOT NULL;
CREATE INDEX ix_km_hieuluc ON KhuyenMai (TrangThai, NgayBatDau, NgayKetThuc) INCLUDE (TuDongApDung, ApDungToanBo);

-- Phạm vi & điều kiện của khuyến mãi:
--   VaiTro 'dieu_kien': sản phẩm/ngành hàng phải mua (hoặc được giảm, với giảm %/tiền khi ApDungToanBo = 0)
--   VaiTro 'uu_dai'   : quà tặng hoặc phụ kiện mua kèm; GiaUuDai = giá sốc (0 = tặng miễn phí)
CREATE TABLE KhuyenMaiSanPham (
    MaKMSP          INT             IDENTITY(1,1) PRIMARY KEY,
    MaKhuyenMai     INT             NOT NULL,
    VaiTro          NVARCHAR(20)    NOT NULL,
    MaSanPham       INT,
    MaDanhMuc       INT,
    SoLuong         INT             NOT NULL DEFAULT 1,
    GiaUuDai        DECIMAL(18,2),
    CONSTRAINT fk_kmsp_km      FOREIGN KEY (MaKhuyenMai)
        REFERENCES KhuyenMai(MaKhuyenMai) ON DELETE CASCADE,
    CONSTRAINT fk_kmsp_sanpham FOREIGN KEY (MaSanPham)
        REFERENCES SanPham(MaSanPham)     ON DELETE NO ACTION,
    CONSTRAINT fk_kmsp_danhmuc FOREIGN KEY (MaDanhMuc)
        REFERENCES DanhMuc(MaDanhMuc)     ON DELETE NO ACTION,
    CONSTRAINT chk_kmsp_vaitro  CHECK (VaiTro IN (N'dieu_kien', N'uu_dai')),
    CONSTRAINT chk_kmsp_doi     CHECK ((MaSanPham IS NOT NULL AND MaDanhMuc IS NULL)
                                    OR (MaSanPham IS NULL AND MaDanhMuc IS NOT NULL)),
    CONSTRAINT chk_kmsp_soluong CHECK (SoLuong > 0),
    CONSTRAINT chk_kmsp_giauudai CHECK (GiaUuDai IS NULL
        OR (VaiTro = N'uu_dai' AND MaSanPham IS NOT NULL AND GiaUuDai >= 0))
);
CREATE UNIQUE INDEX uq_kmsp_sp ON KhuyenMaiSanPham (MaKhuyenMai, VaiTro, MaSanPham) WHERE MaSanPham IS NOT NULL;
CREATE UNIQUE INDEX uq_kmsp_dm ON KhuyenMaiSanPham (MaKhuyenMai, VaiTro, MaDanhMuc) WHERE MaDanhMuc IS NOT NULL;
CREATE INDEX ix_kmsp_sanpham ON KhuyenMaiSanPham (MaSanPham) WHERE MaSanPham IS NOT NULL;
CREATE INDEX ix_kmsp_danhmuc ON KhuyenMaiSanPham (MaDanhMuc) WHERE MaDanhMuc IS NOT NULL;
GO

-- =====================================================================
-- 9. BÁN HÀNG TẠI QUẦY POS
-- =====================================================================

CREATE TABLE HoaDon (
    MaHoaDon            INT             IDENTITY(1,1) PRIMARY KEY,
    SoHoaDon            NVARCHAR(30)    NOT NULL,   -- vd: HD-POS001-20270105-0042
    MaQuay              INT             NOT NULL,
    MaThuNgan           INT             NOT NULL,
    MaKhachHang         INT,                        -- NULL = khách vãng lai
    TrangThai           NVARCHAR(20)    NOT NULL DEFAULT N'dang_lap',
    TongTienHang        DECIMAL(18,2)   NOT NULL DEFAULT 0,   -- trước giảm giá, đã gồm VAT
    TongGiamGia         DECIMAL(18,2)   NOT NULL DEFAULT 0,
    TongThanhToan       AS (CAST(TongTienHang - TongGiamGia AS DECIMAL(18,2))) PERSISTED,
    TongTienThue        DECIMAL(18,2)   NOT NULL DEFAULT 0,   -- phần VAT nằm trong TongThanhToan
    SoLanIn             INT             NOT NULL DEFAULT 0,
    ThoiGianTao         DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    ThoiGianThanhToan   DATETIME2,
    HuyBoi              INT,
    LyDoHuy             NVARCHAR(255),
    GhiChu              NVARCHAR(500),
    CONSTRAINT fk_hd_quay      FOREIGN KEY (MaQuay)
        REFERENCES QuayPOS(MaQuay)         ON DELETE NO ACTION,
    CONSTRAINT fk_hd_thungan   FOREIGN KEY (MaThuNgan)
        REFERENCES NhanVien(MaNhanVien)    ON DELETE NO ACTION,
    CONSTRAINT fk_hd_khachhang FOREIGN KEY (MaKhachHang)
        REFERENCES KhachHang(MaKhachHang)  ON DELETE NO ACTION,
    CONSTRAINT fk_hd_huyboi    FOREIGN KEY (HuyBoi)
        REFERENCES NhanVien(MaNhanVien)    ON DELETE NO ACTION,
    CONSTRAINT uq_hd_sohoadon  UNIQUE (SoHoaDon),
    CONSTRAINT chk_hd_trangthai CHECK (TrangThai IN (N'dang_lap', N'tam_giu', N'da_thanh_toan', N'da_huy')),
    CONSTRAINT chk_hd_tienhang  CHECK (TongTienHang >= 0),
    CONSTRAINT chk_hd_giamgia   CHECK (TongGiamGia >= 0 AND TongGiamGia <= TongTienHang),
    CONSTRAINT chk_hd_thue      CHECK (TongTienThue >= 0),
    CONSTRAINT chk_hd_solanin   CHECK (SoLanIn >= 0),
    CONSTRAINT chk_hd_thanhtoan_cohesion CHECK (TrangThai <> N'da_thanh_toan' OR ThoiGianThanhToan IS NOT NULL),
    CONSTRAINT chk_hd_huy_cohesion CHECK (TrangThai <> N'da_huy' OR (HuyBoi IS NOT NULL AND LyDoHuy IS NOT NULL))
);
CREATE INDEX ix_hd_quay      ON HoaDon (MaQuay, ThoiGianTao DESC);
CREATE INDEX ix_hd_thungan   ON HoaDon (MaThuNgan, ThoiGianTao DESC);
CREATE INDEX ix_hd_khachhang ON HoaDon (MaKhachHang) WHERE MaKhachHang IS NOT NULL;
CREATE INDEX ix_hd_trangthai ON HoaDon (TrangThai, ThoiGianTao DESC);
-- Phục vụ báo cáo doanh thu thời gian thực (< 2s) theo giờ/ngày/tháng/quầy/thu ngân
CREATE INDEX ix_hd_doanhthu  ON HoaDon (ThoiGianThanhToan DESC)
    INCLUDE (MaQuay, MaThuNgan, TongTienHang, TongGiamGia, TongThanhToan, TongTienThue)
    WHERE TrangThai = N'da_thanh_toan';

CREATE TABLE ChiTietHoaDon (
    MaChiTiet       INT             IDENTITY(1,1) PRIMARY KEY,
    MaHoaDon        INT             NOT NULL,
    MaSanPham       INT             NOT NULL,
    MaSerial        INT,                            -- bắt buộc (ở tầng ứng dụng) với hàng CoTheoDoiSerial
    TenHang         NVARCHAR(255)   NOT NULL,       -- snapshot tại thời điểm bán
    MaSKU           NVARCHAR(60)    NOT NULL,
    SoLuong         INT             NOT NULL DEFAULT 1,
    DonGia          DECIMAL(18,2)   NOT NULL,       -- đã gồm VAT
    GiamGia         DECIMAL(18,2)   NOT NULL DEFAULT 0,
    ThueSuat        DECIMAL(5,2)    NOT NULL DEFAULT 10,
    GiaVon          DECIMAL(18,2),                  -- snapshot để tính lợi nhuận
    LaQuaTang       BIT             NOT NULL DEFAULT 0,
    HieuLuc         BIT             NOT NULL DEFAULT 1,   -- 0 khi hóa đơn hủy / dòng đã trả
    ThanhTien       AS (CAST(SoLuong * DonGia - GiamGia AS DECIMAL(18,2))) PERSISTED,
    TienThue        AS (CAST((SoLuong * DonGia - GiamGia) * ThueSuat / (100 + ThueSuat) AS DECIMAL(18,2))) PERSISTED,
    CONSTRAINT fk_cthd_hoadon  FOREIGN KEY (MaHoaDon)
        REFERENCES HoaDon(MaHoaDon)            ON DELETE CASCADE,
    CONSTRAINT fk_cthd_sanpham FOREIGN KEY (MaSanPham)
        REFERENCES SanPham(MaSanPham)          ON DELETE NO ACTION,
    CONSTRAINT fk_cthd_serial  FOREIGN KEY (MaSerial)
        REFERENCES SerialSanPham(MaSerial)     ON DELETE NO ACTION,
    CONSTRAINT chk_cthd_soluong CHECK (SoLuong > 0),
    CONSTRAINT chk_cthd_serial1 CHECK (MaSerial IS NULL OR SoLuong = 1),
    CONSTRAINT chk_cthd_gia     CHECK (DonGia >= 0),
    CONSTRAINT chk_cthd_giamgia CHECK (GiamGia >= 0 AND GiamGia <= SoLuong * DonGia),
    CONSTRAINT chk_cthd_thue    CHECK (ThueSuat BETWEEN 0 AND 100),
    CONSTRAINT chk_cthd_giavon  CHECK (GiaVon IS NULL OR GiaVon >= 0)
);
CREATE INDEX ix_cthd_hoadon  ON ChiTietHoaDon (MaHoaDon);
CREATE INDEX ix_cthd_serial  ON ChiTietHoaDon (MaSerial) WHERE MaSerial IS NOT NULL;   -- tra cứu lịch sử mua theo Serial
CREATE INDEX ix_cthd_sanpham ON ChiTietHoaDon (MaSanPham) INCLUDE (SoLuong, ThanhTien, GiaVon) WHERE HieuLuc = 1;
-- Chốt chặn cuối: 1 Serial không thể nằm trên 2 dòng hóa đơn còn hiệu lực
-- (chặn 2 quầy cùng quét 1 thiết bị, bán trùng Serial)
CREATE UNIQUE INDEX uq_cthd_serial_hieuluc ON ChiTietHoaDon (MaSerial)
    WHERE MaSerial IS NOT NULL AND HieuLuc = 1;

-- Thanh toán: một hóa đơn có thể chia nhiều phương thức (vd: tiền mặt + thẻ)
CREATE TABLE ThanhToan (
    MaThanhToan     INT             IDENTITY(1,1) PRIMARY KEY,
    MaHoaDon        INT             NOT NULL,
    PhuongThuc      NVARCHAR(20)    NOT NULL,
    TrangThai       NVARCHAR(20)    NOT NULL DEFAULT N'cho_xu_ly',
    SoTien          DECIMAL(18,2)   NOT NULL,
    TienKhachDua    DECIMAL(18,2),                  -- chỉ với tiền mặt
    TienThoiLai     AS (CAST(TienKhachDua - SoTien AS DECIMAL(18,2))) PERSISTED,
    MaThamChieu     NVARCHAR(100),                  -- mã giao dịch thẻ / QR / hợp đồng trả góp
    DonViTraGop     NVARCHAR(100),                  -- chỉ với trả góp
    SoKyHan         INT,
    ThanhToanLuc    DATETIME2,
    TaoLuc          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT fk_tt_hoadon FOREIGN KEY (MaHoaDon)
        REFERENCES HoaDon(MaHoaDon) ON DELETE NO ACTION,
    CONSTRAINT chk_tt_phuongthuc CHECK (PhuongThuc IN (
        N'tien_mat', N'the_ngan_hang', N'qr_chuyen_khoan', N'tra_gop'
    )),
    CONSTRAINT chk_tt_trangthai CHECK (TrangThai IN (N'cho_xu_ly', N'thanh_cong', N'that_bai')),
    CONSTRAINT chk_tt_sotien    CHECK (SoTien > 0),
    CONSTRAINT chk_tt_tienmat   CHECK (
        (PhuongThuc = N'tien_mat' AND (TienKhachDua IS NULL OR TienKhachDua >= SoTien))
        OR (PhuongThuc <> N'tien_mat' AND TienKhachDua IS NULL)
    ),
    CONSTRAINT chk_tt_tragop    CHECK (
        (PhuongThuc = N'tra_gop' AND DonViTraGop IS NOT NULL AND SoKyHan > 0)
        OR (PhuongThuc <> N'tra_gop' AND DonViTraGop IS NULL AND SoKyHan IS NULL)
    ),
    CONSTRAINT chk_tt_thanhcong CHECK (TrangThai <> N'thanh_cong' OR ThanhToanLuc IS NOT NULL)
);
CREATE UNIQUE INDEX uq_tt_thamchieu ON ThanhToan (MaThamChieu) WHERE MaThamChieu IS NOT NULL;
CREATE INDEX ix_tt_hoadon ON ThanhToan (MaHoaDon) INCLUDE (PhuongThuc, SoTien, TrangThai);

-- Voucher được tặng từ chương trình 'tang_voucher'
CREATE TABLE Voucher (
    MaVoucher           INT             IDENTITY(1,1) PRIMARY KEY,
    MaCode              NVARCHAR(50)    NOT NULL,
    MaKhuyenMai         INT             NOT NULL,
    GiaTri              DECIMAL(18,2)   NOT NULL,
    MaKhachHang         INT,
    HoaDonPhatHanh      INT,
    HoaDonSuDung        INT,
    TrangThai           NVARCHAR(20)    NOT NULL DEFAULT N'chua_dung',
    NgayPhatHanh        DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    HanSuDung           DATETIME2       NOT NULL,
    NgaySuDung          DATETIME2,
    CONSTRAINT fk_vc_km        FOREIGN KEY (MaKhuyenMai)
        REFERENCES KhuyenMai(MaKhuyenMai)  ON DELETE NO ACTION,
    CONSTRAINT fk_vc_khachhang FOREIGN KEY (MaKhachHang)
        REFERENCES KhachHang(MaKhachHang)  ON DELETE NO ACTION,
    CONSTRAINT fk_vc_hd_phat   FOREIGN KEY (HoaDonPhatHanh)
        REFERENCES HoaDon(MaHoaDon)        ON DELETE NO ACTION,
    CONSTRAINT fk_vc_hd_dung   FOREIGN KEY (HoaDonSuDung)
        REFERENCES HoaDon(MaHoaDon)        ON DELETE NO ACTION,
    CONSTRAINT uq_vc_code UNIQUE (MaCode),
    CONSTRAINT chk_vc_giatri    CHECK (GiaTri > 0),
    CONSTRAINT chk_vc_trangthai CHECK (TrangThai IN (N'chua_dung', N'da_dung', N'het_han', N'huy')),
    CONSTRAINT chk_vc_han       CHECK (HanSuDung > NgayPhatHanh),
    CONSTRAINT chk_vc_dadung    CHECK (TrangThai <> N'da_dung' OR (HoaDonSuDung IS NOT NULL AND NgaySuDung IS NOT NULL))
);
CREATE INDEX ix_vc_km        ON Voucher (MaKhuyenMai);
CREATE INDEX ix_vc_khachhang ON Voucher (MaKhachHang) WHERE MaKhachHang IS NOT NULL;
CREATE INDEX ix_vc_hd_dung   ON Voucher (HoaDonSuDung) WHERE HoaDonSuDung IS NOT NULL;

-- Khuyến mãi đã áp dụng thực tế vào hóa đơn (để đối soát và thống kê hiệu quả CTKM)
CREATE TABLE HoaDonKhuyenMai (
    MaHDKM          INT             IDENTITY(1,1) PRIMARY KEY,
    MaHoaDon        INT             NOT NULL,
    MaChiTiet       INT,                            -- NULL = khuyến mãi trên cả hóa đơn
    MaKhuyenMai     INT             NOT NULL,
    MaVoucher       INT,
    SoTienGiam      DECIMAL(18,2)   NOT NULL DEFAULT 0,
    ApDungLuc       DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT fk_hdkm_hoadon  FOREIGN KEY (MaHoaDon)
        REFERENCES HoaDon(MaHoaDon)           ON DELETE CASCADE,
    CONSTRAINT fk_hdkm_chitiet FOREIGN KEY (MaChiTiet)
        REFERENCES ChiTietHoaDon(MaChiTiet)   ON DELETE NO ACTION,
    CONSTRAINT fk_hdkm_km      FOREIGN KEY (MaKhuyenMai)
        REFERENCES KhuyenMai(MaKhuyenMai)     ON DELETE NO ACTION,
    CONSTRAINT fk_hdkm_voucher FOREIGN KEY (MaVoucher)
        REFERENCES Voucher(MaVoucher)         ON DELETE NO ACTION,
    CONSTRAINT chk_hdkm_sotien CHECK (SoTienGiam >= 0)
);
CREATE INDEX ix_hdkm_hoadon ON HoaDonKhuyenMai (MaHoaDon);
CREATE INDEX ix_hdkm_km     ON HoaDonKhuyenMai (MaKhuyenMai) INCLUDE (SoTienGiam);
GO

-- =====================================================================
-- 10. TRẢ HÀNG TẠI QUẦY (hoàn tác Serial/tồn kho chính xác)
-- =====================================================================

CREATE TABLE PhieuTraHang (
    MaPhieuTra      INT             IDENTITY(1,1) PRIMARY KEY,
    MaSoPhieu       NVARCHAR(50)    NOT NULL,
    MaHoaDon        INT             NOT NULL,
    NguoiXuLy       INT             NOT NULL,
    NguoiDuyet      INT,
    LyDo            NVARCHAR(500)   NOT NULL,
    PhuongThucHoan  NVARCHAR(20),
    TongTienHoan    DECIMAL(18,2)   NOT NULL DEFAULT 0,
    TrangThai       NVARCHAR(20)    NOT NULL DEFAULT N'cho_duyet',
    TaoLuc          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    DuyetLuc        DATETIME2,
    HoanTatLuc      DATETIME2,
    CONSTRAINT fk_pth_hoadon   FOREIGN KEY (MaHoaDon)
        REFERENCES HoaDon(MaHoaDon)     ON DELETE NO ACTION,
    CONSTRAINT fk_pth_xuly     FOREIGN KEY (NguoiXuLy)
        REFERENCES NhanVien(MaNhanVien) ON DELETE NO ACTION,
    CONSTRAINT fk_pth_duyet    FOREIGN KEY (NguoiDuyet)
        REFERENCES NhanVien(MaNhanVien) ON DELETE NO ACTION,
    CONSTRAINT uq_pth_sophieu UNIQUE (MaSoPhieu),
    CONSTRAINT chk_pth_trangthai CHECK (TrangThai IN (N'cho_duyet', N'da_duyet', N'hoan_tat', N'tu_choi')),
    CONSTRAINT chk_pth_phuongthuc CHECK (PhuongThucHoan IS NULL OR PhuongThucHoan IN (
        N'tien_mat', N'the_ngan_hang', N'qr_chuyen_khoan')),
    CONSTRAINT chk_pth_tien     CHECK (TongTienHoan >= 0),
    CONSTRAINT chk_pth_duyet_cohesion CHECK (TrangThai = N'cho_duyet' OR (NguoiDuyet IS NOT NULL AND DuyetLuc IS NOT NULL)),
    CONSTRAINT chk_pth_hoantat  CHECK (TrangThai <> N'hoan_tat' OR HoanTatLuc IS NOT NULL)
);
CREATE INDEX ix_pth_hoadon   ON PhieuTraHang (MaHoaDon);
CREATE INDEX ix_pth_trangthai ON PhieuTraHang (TrangThai);

CREATE TABLE ChiTietTraHang (
    MaChiTiet       INT             IDENTITY(1,1) PRIMARY KEY,
    MaPhieuTra      INT             NOT NULL,
    MaChiTietHD     INT             NOT NULL,
    MaSerial        INT,
    SoLuong         INT             NOT NULL DEFAULT 1,
    TinhTrangHang   NVARCHAR(30)    NOT NULL,
    SoTienHoan      DECIMAL(18,2)   NOT NULL,
    NhapLaiKho      BIT             NOT NULL DEFAULT 0,   -- 1 = hàng còn bán được, trả về 'trong_kho'
    GhiChu          NVARCHAR(255),
    CONSTRAINT fk_ctth_phieu   FOREIGN KEY (MaPhieuTra)
        REFERENCES PhieuTraHang(MaPhieuTra)    ON DELETE CASCADE,
    CONSTRAINT fk_ctth_cthd    FOREIGN KEY (MaChiTietHD)
        REFERENCES ChiTietHoaDon(MaChiTiet)    ON DELETE NO ACTION,
    CONSTRAINT fk_ctth_serial  FOREIGN KEY (MaSerial)
        REFERENCES SerialSanPham(MaSerial)     ON DELETE NO ACTION,
    CONSTRAINT uq_ctth_item     UNIQUE (MaPhieuTra, MaChiTietHD),
    CONSTRAINT chk_ctth_soluong CHECK (SoLuong > 0),
    CONSTRAINT chk_ctth_serial1 CHECK (MaSerial IS NULL OR SoLuong = 1),
    CONSTRAINT chk_ctth_tinhtrang CHECK (TinhTrangHang IN (N'nguyen_hop', N'da_mo', N'loi', N'thieu_phu_kien')),
    CONSTRAINT chk_ctth_tien    CHECK (SoTienHoan >= 0)
);
CREATE INDEX ix_ctth_cthd   ON ChiTietTraHang (MaChiTietHD);
CREATE INDEX ix_ctth_serial ON ChiTietTraHang (MaSerial) WHERE MaSerial IS NOT NULL;
GO

-- =====================================================================
-- 11. BẢO HÀNH (chỉ tiếp nhận & tra cứu; không quản lý sửa chữa/linh kiện)
-- =====================================================================

CREATE TABLE ChinhSachBaoHanh (
    MaChinhSach     INT             IDENTITY(1,1) PRIMARY KEY,
    TenChinhSach    NVARCHAR(150)   NOT NULL,
    ThoiHanThang    INT             NOT NULL,
    NoiDung         NVARCHAR(4000),
    TrangThai       BIT             NOT NULL DEFAULT 1,
    CONSTRAINT uq_csbh_ten      UNIQUE (TenChinhSach),
    CONSTRAINT chk_csbh_thoihan CHECK (ThoiHanThang > 0)
);

-- Phát sinh khi bán: 1 dòng cho mỗi thiết bị/dòng hàng được bảo hành
CREATE TABLE BaoHanh (
    MaBaoHanh       INT             IDENTITY(1,1) PRIMARY KEY,
    MaChiTietHD     INT             NOT NULL,
    MaSerial        INT,
    MaChinhSach     INT,
    LoaiBaoHanh     NVARCHAR(20)    NOT NULL DEFAULT N'hang',
    NgayBatDau      DATETIME2       NOT NULL,
    NgayKetThuc     DATETIME2       NOT NULL,
    TrangThai       NVARCHAR(20)    NOT NULL DEFAULT N'con_han',
    TaoLuc          DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT fk_bh_cthd      FOREIGN KEY (MaChiTietHD)
        REFERENCES ChiTietHoaDon(MaChiTiet)       ON DELETE NO ACTION,
    CONSTRAINT fk_bh_serial    FOREIGN KEY (MaSerial)
        REFERENCES SerialSanPham(MaSerial)        ON DELETE NO ACTION,
    CONSTRAINT fk_bh_chinhsach FOREIGN KEY (MaChinhSach)
        REFERENCES ChinhSachBaoHanh(MaChinhSach)  ON DELETE NO ACTION,
    CONSTRAINT chk_bh_loai      CHECK (LoaiBaoHanh IN (N'hang', N'cua_hang', N'mo_rong')),
    CONSTRAINT chk_bh_ngay      CHECK (NgayKetThuc > NgayBatDau),
    CONSTRAINT chk_bh_trangthai CHECK (TrangThai IN (N'con_han', N'het_han', N'da_huy'))
);
CREATE INDEX ix_bh_cthd      ON BaoHanh (MaChiTietHD);
CREATE INDEX ix_bh_serial    ON BaoHanh (MaSerial) WHERE MaSerial IS NOT NULL;
CREATE INDEX ix_bh_chinhsach ON BaoHanh (MaChinhSach) WHERE MaChinhSach IS NOT NULL;
CREATE INDEX ix_bh_ngayketthuc ON BaoHanh (NgayKetThuc) WHERE TrangThai = N'con_han';

CREATE TABLE YeuCauBaoHanh (
    MaYeuCau            INT             IDENTITY(1,1) PRIMARY KEY,
    MaSoTiepNhan        NVARCHAR(50)    NOT NULL,
    MaBaoHanh           INT             NOT NULL,
    NhanVienTiepNhan    INT             NOT NULL,
    TenNguoiGui         NVARCHAR(150)   NOT NULL,
    SoDienThoaiNguoiGui NVARCHAR(20)    NOT NULL,
    MoTaSuCo            NVARCHAR(2000)  NOT NULL,
    TinhTrangNhanMay    NVARCHAR(500),
    PhuKienKemTheo      NVARCHAR(500),
    TrangThai           NVARCHAR(20)    NOT NULL DEFAULT N'tiep_nhan',
    KetQuaXuLy          NVARCHAR(2000),
    NgayHenTra          DATETIME2,
    TiepNhanLuc         DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    HoanTatLuc          DATETIME2,
    TraKhachLuc         DATETIME2,
    CONSTRAINT fk_ycbh_baohanh FOREIGN KEY (MaBaoHanh)
        REFERENCES BaoHanh(MaBaoHanh)      ON DELETE NO ACTION,
    CONSTRAINT fk_ycbh_nhanvien FOREIGN KEY (NhanVienTiepNhan)
        REFERENCES NhanVien(MaNhanVien)    ON DELETE NO ACTION,
    CONSTRAINT uq_ycbh_somaso UNIQUE (MaSoTiepNhan),
    CONSTRAINT chk_ycbh_trangthai CHECK (TrangThai IN (
        N'tiep_nhan', N'dang_xu_ly', N'hoan_tat', N'tu_choi', N'da_tra_khach'
    )),
    CONSTRAINT chk_ycbh_hen     CHECK (NgayHenTra IS NULL OR NgayHenTra >= TiepNhanLuc),
    CONSTRAINT chk_ycbh_hoantat CHECK (TrangThai NOT IN (N'hoan_tat', N'da_tra_khach') OR HoanTatLuc IS NOT NULL),
    CONSTRAINT chk_ycbh_tra     CHECK (TrangThai <> N'da_tra_khach' OR TraKhachLuc IS NOT NULL)
);
CREATE INDEX ix_ycbh_baohanh   ON YeuCauBaoHanh (MaBaoHanh);
CREATE INDEX ix_ycbh_trangthai ON YeuCauBaoHanh (TrangThai, TiepNhanLuc DESC);
CREATE INDEX ix_ycbh_sdt       ON YeuCauBaoHanh (SoDienThoaiNguoiGui);
GO

-- =====================================================================
-- 12. VIEW HỖ TRỢ BÁO CÁO & ĐỐI SOÁT
-- =====================================================================

-- Tra cứu theo Serial/IMEI: thiết bị -> hóa đơn mua -> khách -> bảo hành
CREATE OR ALTER VIEW vw_TraCuuSerial AS
SELECT  s.MaSerial, s.SoSerial, s.IMEI, s.IMEI2, s.TrangThai AS TrangThaiSerial,
        sp.MaSKU, sp.TenSanPham,
        hd.SoHoaDon, hd.ThoiGianThanhToan AS NgayMua, hd.MaQuay,
        kh.HoTen AS TenKhachHang, kh.SoDienThoai,
        bh.MaBaoHanh, bh.LoaiBaoHanh, bh.NgayBatDau AS BaoHanhTu, bh.NgayKetThuc AS BaoHanhDen,
        CASE WHEN bh.MaBaoHanh IS NULL THEN NULL
             WHEN bh.TrangThai = N'con_han' AND bh.NgayKetThuc > SYSDATETIME() THEN N'Còn hạn'
             ELSE N'Hết hạn' END AS TinhTrangBaoHanh
FROM SerialSanPham s
JOIN SanPham sp ON sp.MaSanPham = s.MaSanPham
LEFT JOIN ChiTietHoaDon cthd ON cthd.MaSerial = s.MaSerial AND cthd.HieuLuc = 1
LEFT JOIN HoaDon hd          ON hd.MaHoaDon = cthd.MaHoaDon AND hd.TrangThai = N'da_thanh_toan'
LEFT JOIN KhachHang kh       ON kh.MaKhachHang = hd.MaKhachHang
LEFT JOIN BaoHanh bh         ON bh.MaChiTietHD = cthd.MaChiTiet;
GO

-- Doanh thu theo ngày / quầy / thu ngân
CREATE OR ALTER VIEW vw_DoanhThuNgay AS
SELECT  CAST(ThoiGianThanhToan AS DATE) AS Ngay,
        MaQuay, MaThuNgan,
        COUNT(*)            AS SoHoaDon,
        SUM(TongTienHang)   AS TongTienHang,
        SUM(TongGiamGia)    AS TongGiamGia,
        SUM(TongThanhToan)  AS DoanhThu
FROM HoaDon
WHERE TrangThai = N'da_thanh_toan'
GROUP BY CAST(ThoiGianThanhToan AS DATE), MaQuay, MaThuNgan;
GO

-- Hàng chạm/ dưới ngưỡng tồn tối thiểu
CREATE OR ALTER VIEW vw_CanhBaoTonKho AS
SELECT  sp.MaSanPham, sp.MaSKU, sp.TenSanPham, tk.SLTon, tk.TonToiThieu, tk.ViTriKho,
        CASE WHEN tk.SLTon = 0 THEN N'HẾT HÀNG' ELSE N'SẮP HẾT' END AS MucDoCanhBao
FROM TonKho tk
JOIN SanPham sp ON sp.MaSanPham = tk.MaSanPham
WHERE sp.NgayXoa IS NULL AND tk.SLTon <= tk.TonToiThieu;
GO

-- Phát hiện lệch giữa TonKho.SLTon và số Serial thực sự 'trong_kho'
CREATE OR ALTER VIEW vw_ChenhLechTonKho AS
SELECT  sp.MaSanPham, sp.MaSKU, sp.TenSanPham,
        tk.SLTon AS TonHeThong,
        ISNULL(sr.SoSerial, 0) AS SoSerialTrongKho,
        tk.SLTon - ISNULL(sr.SoSerial, 0) AS ChenhLech
FROM SanPham sp
JOIN TonKho tk ON tk.MaSanPham = sp.MaSanPham
LEFT JOIN (
    SELECT MaSanPham, COUNT(*) AS SoSerial
    FROM SerialSanPham WHERE TrangThai = N'trong_kho'
    GROUP BY MaSanPham
) sr ON sr.MaSanPham = sp.MaSanPham
WHERE sp.CoTheoDoiSerial = 1
  AND tk.SLTon <> ISNULL(sr.SoSerial, 0);
GO
