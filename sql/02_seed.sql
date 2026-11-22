-- =====================================================================
--  02_seed.sql  --  Dữ liệu mẫu cho hệ thống Retail Invoicing (Topic 19)
--  Chạy bằng:  mysql -u root -p retail_billing < 02_seed.sql
-- =====================================================================
--  BẮT BUỘC: File này phải chạy SAU 01_schema.sql và 04_routines.sql.
--  Đảm bảo:
--   1. Mọi bảng (15/15 bảng) đều có >= 15 dòng dữ liệu thực tế.
--   2. Tuân thủ 100% Triggers & Stored Procedures trong 04_routines.sql
--      và 17 Business Rules (BR1 - BR17).
--   3. Giữ nguyên cấu trúc dữ liệu (đầu số SĐT, mã sản phẩm, các mốc
--      tăng giá > 10%, trả hàng > 50%...) để giao diện Java hoạt động
--      không cần chỉnh sửa.
-- =====================================================================

USE retail_billing;

-- ---------------------------------------------------------------------
-- 0. DỌN SẠCH DỮ LIỆU CŨ (chạy lại nhiều lần không lỗi)
-- ---------------------------------------------------------------------
SET SQL_SAFE_UPDATES = 0;
CALL sp_wipe_all_data();


-- ---------------------------------------------------------------------
-- 1. SHOP (15 cửa hàng: S01 -> S15)
-- ---------------------------------------------------------------------
INSERT INTO Shop (Shop_ID, Address) VALUES
('S01', '123 Nguyen Hue, Quan 1, TP.HCM'),
('S02', '45 Nguyen Van Linh, Quan 7, TP.HCM'),
('S03', '78 Vo Van Ngan, Thu Duc, TP.HCM'),
('S04', '210 Le Loi, Quan 1, TP.HCM'),
('S05', '89 Cach Mang Thang 8, Quan 3, TP.HCM'),
('S06', '312 Hai Ba Trung, Quan 1, TP.HCM'),
('S07', '156 Phan Xich Long, Phu Nhuan, TP.HCM'),
('S08', '402 Quang Trung, Go Vap, TP.HCM'),
('S09', '55 Nguyen Trai, Quan 5, TP.HCM'),
('S10', '190 Ba Thang Hai, Quan 10, TP.HCM'),
('S11', '72 Dien Bien Phu, Binh Thanh, TP.HCM'),
('S12', '88 Hoang Van Thu, Tan Binh, TP.HCM'),
('S13', '145 Le Van Sy, Quan 3, TP.HCM'),
('S14', '260 Au Co, Tan Phu, TP.HCM'),
('S15', '330 Kinh Duong Vuong, Binh Tan, TP.HCM');


-- ---------------------------------------------------------------------
-- 2. COUNTER (30 quầy: C01 -> C30, mỗi Shop có đúng 2 quầy)
-- ---------------------------------------------------------------------
INSERT INTO Counter (Counter_ID, Shop_ID) VALUES
('C01', 'S01'), ('C02', 'S01'),
('C03', 'S02'), ('C04', 'S02'),
('C05', 'S03'), ('C06', 'S03'),
('C07', 'S04'), ('C08', 'S04'),
('C09', 'S05'), ('C10', 'S05'),
('C11', 'S06'), ('C12', 'S06'),
('C13', 'S07'), ('C14', 'S07'),
('C15', 'S08'), ('C16', 'S08'),
('C17', 'S09'), ('C18', 'S09'),
('C19', 'S10'), ('C20', 'S10'),
('C21', 'S11'), ('C22', 'S11'),
('C23', 'S12'), ('C24', 'S12'),
('C25', 'S13'), ('C26', 'S13'),
('C27', 'S14'), ('C28', 'S14'),
('C29', 'S15'), ('C30', 'S15');


-- ---------------------------------------------------------------------
-- 3. CASHIER (30 nhân viên: 15 MANAGER E01..E15, 15 CASHIER E16..E30)
--    BR15: Chỉ MANAGER mới được duyệt trả hàng và hủy hóa đơn (VOIDED).
-- ---------------------------------------------------------------------
INSERT INTO Cashier (Cashier_ID, Name, Role, Supervisor_ID) VALUES
-- 15 Managers (quản lý 15 cửa hàng, cấp cao nhất nên Supervisor_ID = NULL)
('E01', 'Tran Van An',       'MANAGER', NULL),
('E02', 'Nguyen Minh Tuan',  'MANAGER', NULL),
('E03', 'Le Hoang Nam',      'MANAGER', NULL),
('E04', 'Pham Quoc Bao',     'MANAGER', NULL),
('E05', 'Vo Thanh Tung',     'MANAGER', NULL),
('E06', 'Dang Huu Nghia',    'MANAGER', NULL),
('E07', 'Bui Tien Dung',     'MANAGER', NULL),
('E08', 'Do Quang Huy',      'MANAGER', NULL),
('E09', 'Hoang Duc Thang',   'MANAGER', NULL),
('E10', 'Ngo Gia Kiet',      'MANAGER', NULL),
('E11', 'Huynh Tan Phat',    'MANAGER', NULL),
('E12', 'Truong Cong Vinh',  'MANAGER', NULL),
('E13', 'Ly Thai To',        'MANAGER', NULL),
('E14', 'Cao Minh Triet',    'MANAGER', NULL),
('E15', 'Doan Van Hau',      'MANAGER', NULL),
-- 15 Cashiers (trực thuộc 15 Managers tương ứng)
('E16', 'Nguyen Thi Binh',   'CASHIER', 'E01'),
('E17', 'Le Minh Cuong',     'CASHIER', 'E02'),
('E18', 'Pham Thu Dung',     'CASHIER', 'E03'),
('E19', 'Vo Hoang Em',       'CASHIER', 'E04'),
('E20', 'Tran Thi Hong',     'CASHIER', 'E05'),
('E21', 'Nguyen Van Khanh',  'CASHIER', 'E06'),
('E22', 'Le Thi Kim Lien',   'CASHIER', 'E07'),
('E23', 'Pham Minh Long',    'CASHIER', 'E08'),
('E24', 'Vo Thi Mai',        'CASHIER', 'E09'),
('E25', 'Dang Van Minh',     'CASHIER', 'E10'),
('E26', 'Bui Thi Ngoc',      'CASHIER', 'E11'),
('E27', 'Do Van Phuc',       'CASHIER', 'E12'),
('E28', 'Hoang Thi Quynh',   'CASHIER', 'E13'),
('E29', 'Ngo Van Son',       'CASHIER', 'E14'),
('E30', 'Huynh Thi Trang',   'CASHIER', 'E15');


-- ---------------------------------------------------------------------
-- 4. CUSTOMER (30 khách hàng thành viên - BR10)
--    22 số bắt đầu '09' để test LIMIT 20 của sp_search_customers_by_phone
-- ---------------------------------------------------------------------
INSERT INTO Customer (Customer_ID, Name, Phone) VALUES
('KH001', 'Hoang Van Kien',   '0901000001'),
('KH002', 'Do Thi Lan',       '0901000002'),
('KH003', 'Bui Quoc Manh',    '0901000003'),
('KH004', 'Ngo Thi Nga',      '0901000004'),
('KH005', 'Tran Thi Oanh',    '0902111222'),
('KH006', 'Le Van Phuc',      '0903222333'),
('KH007', 'Pham Thi Quyen',   '0905333444'),
('KH008', 'Vo Minh Quan',     '0907444555'),
('KH009', 'Dang Thi Sen',     '0908555666'),
('KH010', 'Huynh Van Tai',    '0909666777'),
('KH011', 'Nguyen Thi Thuy',  '0912777888'),
('KH012', 'Truong Van Uy',    '0913888999'),
('KH013', 'Ly Thi Van',       '0914999000'),
('KH014', 'Cao Van Xuan',     '0916101112'),
('KH015', 'Doan Thi Yen',     '0917121314'),
('KH016', 'Ha Minh Bao',      '0918131415'),
('KH017', 'Mai Thi Cuc',      '0919141516'),
('KH018', 'Phan Van Dat',     '0931151617'),
('KH019', 'Vu Thi Em',        '0932161718'),
('KH020', 'Chu Van Giang',    '0933171819'),
('KH021', 'Ta Thi Hoa',       '0934181920'),
('KH022', 'Dinh Van Khanh',   '0935192021'),
('KH023', 'Luu Thi Lieu',     '0362202122'),
('KH024', 'Trinh Van Minh',   '0363212223'),
('KH025', 'Bach Thi Nhung',   '0703222324'),
('KH026', 'Kieu Van Oai',     '0705232425'),
('KH027', 'Thai Thi Phuong',  '0707242526'),
('KH028', 'Quach Van Quy',    '0812252627'),
('KH029', 'Nghiem Thi Suong', '0813262728'),
('KH030', 'Lam Van Thinh',    '0815272829');


-- ---------------------------------------------------------------------
-- 5. PRODUCT (20 sản phẩm - đủ 5 loại Unit và 3 mức VAT 5%, 8%, 10%)
-- ---------------------------------------------------------------------
INSERT INTO Product (Barcode, Name, Unit, Tax_Rate) VALUES
('8934001', 'Sua tuoi Vinamilk 1L',          'PIECE',  8.00),
('8934002', 'Banh mi sandwich',              'PACK',   8.00),
('8934003', 'Gao ST25 tui 5kg',              'PACK',   5.00),
('8934004', 'Trung ga hop 10 qua',           'BOX',    5.00),
('8934005', 'Nuoc mam Nam Ngu 500ml',        'PIECE',  8.00),
('8934006', 'Dau an Neptune 1L',             'LITER',  8.00),
('8934007', 'Mi Hao Hao thung 30 goi',       'BOX',    8.00),
('8934008', 'Thit ba chi',                   'KG',     5.00),
('8934009', 'Ca phe G7 hop 20 goi',          'BOX',    8.00),
('8934010', 'Nuoc ngot Coca 1.5L',           'PIECE', 10.00),
('8934011', 'Thit bo thai lat',              'KG',     5.00),
('8934012', 'Sua dau nanh Fami thung 36',    'BOX',    8.00),
('8934013', 'Nuoc suoi Lavie 1.5L',          'PIECE', 10.00),
('8934014', 'Bot giat Omo tui 3kg',          'PACK',  10.00),
('8934015', 'Nuoc tuong Maggi 700ml',        'PIECE',  8.00),
('8934016', 'Ca chua',                       'KG',     5.00),
('8934017', 'Sua chua Vinamilk lo 4 hop',    'PACK',   8.00),
('8934018', 'Nuoc rua tay Lifebuoy 500ml',   'PIECE', 10.00),
('8934019', 'Nuoc cam Teppy 1L',             'PIECE',  8.00),
('8934020', 'Bia Saigon thung 24 lon',       'BOX',   10.00);


-- ---------------------------------------------------------------------
-- 6. PRICE_HISTORY (118 dòng - đi qua sp_add_price_history theo BR6/BR8)
--    Trigger trg_price_history_bi chặn INSERT trực tiếp, nên bắt buộc
--    gọi sp_add_price_history với Valid_From_Date tăng dần nghiêm ngặt.
-- ---------------------------------------------------------------------
SET @old_day = DATE_SUB(CURDATE(), INTERVAL 400 DAY);
SET @new_day = GREATEST(MAKEDATE(YEAR(CURDATE()), 1),
                        DATE_SUB(CURDATE(), INTERVAL 45 DAY));

-- 6.1 Giá gốc tại @old_day cho cả 20 sản phẩm
CALL sp_add_price_history('8934001', @old_day,  28000.00);
CALL sp_add_price_history('8934002', @old_day,  18000.00);
CALL sp_add_price_history('8934003', @old_day, 150000.00);
CALL sp_add_price_history('8934004', @old_day,  32000.00);
CALL sp_add_price_history('8934005', @old_day,  45000.00);
CALL sp_add_price_history('8934006', @old_day,  52000.00);
CALL sp_add_price_history('8934007', @old_day, 120000.00);
CALL sp_add_price_history('8934008', @old_day, 130000.00);
CALL sp_add_price_history('8934009', @old_day,  78000.00);
CALL sp_add_price_history('8934010', @old_day,  18000.00);
CALL sp_add_price_history('8934011', @old_day, 185000.00);
CALL sp_add_price_history('8934012', @old_day, 175000.00);
CALL sp_add_price_history('8934013', @old_day,   9000.00);
CALL sp_add_price_history('8934014', @old_day,  92000.00);
CALL sp_add_price_history('8934015', @old_day,  25000.00);
CALL sp_add_price_history('8934016', @old_day,  32000.00);
CALL sp_add_price_history('8934017', @old_day,  28000.00);
CALL sp_add_price_history('8934018', @old_day,  48000.00);
CALL sp_add_price_history('8934019', @old_day,  35000.00);
CALL sp_add_price_history('8934020', @old_day, 390000.00);

-- 6.2 Giá mới tại @new_day (5 sản phẩm tăng > 10% phục vụ Query Q3)
CALL sp_add_price_history('8934001', @new_day,  33000.00); -- +17.86% (>10%)
CALL sp_add_price_history('8934002', @new_day,  19000.00); --  +5.56%
CALL sp_add_price_history('8934003', @new_day, 175000.00); -- +16.67% (>10%)
CALL sp_add_price_history('8934004', @new_day,  35000.00); --  +9.38%
CALL sp_add_price_history('8934006', @new_day,  62000.00); -- +19.23% (>10%)
CALL sp_add_price_history('8934007', @new_day, 128000.00); --  +6.67%
CALL sp_add_price_history('8934008', @new_day, 158000.00); -- +21.54% (>10%)
CALL sp_add_price_history('8934010', @new_day,  21000.00); -- +16.67% (>10%)

-- 6.3 Giá biến động 30 ngày gần nhất cho 3 sản phẩm (90 dòng) phục vụ sp_report_price_30_days
DELIMITER $$
CREATE PROCEDURE sp_seed_30d_prices()
BEGIN
    DECLARE v_n INT DEFAULT 29;
    DECLARE v_d DATE;
    WHILE v_n >= 0 DO
        SET v_d = DATE_SUB(CURDATE(), INTERVAL v_n DAY);
        CALL sp_add_price_history('8934005', v_d, ROUND(45000 * (1 + 0.004 * (MOD(v_n * 7 + 0, 11) - 5)), -2));
        CALL sp_add_price_history('8934009', v_d, ROUND(78000 * (1 + 0.004 * (MOD(v_n * 7 + 4, 11) - 5)), -2));
        CALL sp_add_price_history('8934018', v_d, ROUND(48000 * (1 + 0.004 * (MOD(v_n * 7 + 8, 11) - 5)), -2));
        SET v_n = v_n - 1;
    END WHILE;
END$$
DELIMITER ;

CALL sp_seed_30d_prices();
DROP PROCEDURE sp_seed_30d_prices;


-- ---------------------------------------------------------------------
-- 7. PROMOTION (15 chương trình) & APPLIES_TO (20 dòng) -- BR11
--    Lưu ý: 01_schema.sql định nghĩa Status ENUM('ACTIVE','EXPIRED').
--    Có chương trình trùng nhau (PR01 và PR05 cùng giảm 10% cho 8934001)
--    để kiểm chứng BR11: không cộng dồn, chọn mức giảm cao nhất và nếu
--    bằng nhau thì chọn Expiry_Date sớm nhất.
-- ---------------------------------------------------------------------
INSERT INTO Promotion (Promo_ID, Rule_Description, Status, Expiry_Date) VALUES
('PR01', 'Giam 10% sua tuoi va nuoc ngot',           'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 30 DAY)),
('PR02', 'Giam 5% gao ST25',                         'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 60 DAY)),
('PR03', 'Khuyen mai Tet (da ket thuc)',             'EXPIRED', DATE_SUB(CURDATE(), INTERVAL 100 DAY)),
('PR04', 'Giam 8% sua tuoi va trung ga (dot dau)',   'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 45 DAY)),
('PR05', 'Giam 10% sua tuoi (uu tien han som)',      'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 15 DAY)),
('PR06', 'Giam 12% dau an Neptune',                  'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 25 DAY)),
('PR07', 'Giam 15% thit bo thai lat cuoi tuan',     'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 20 DAY)),
('PR08', 'Giam 7% sua dau nanh Fami',                'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 40 DAY)),
('PR09', 'Giam 10% bot giat Omo tui 3kg',            'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 35 DAY)),
('PR10', 'Giam 6% nuoc tuong Maggi',                 'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 50 DAY)),
('PR11', 'Giam 12% ca chua tuoi',                    'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 18 DAY)),
('PR12', 'Giam 8% sua chua Vinamilk',                'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 28 DAY)),
('PR13', 'Giam 10% nuoc cam Teppy',                  'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 33 DAY)),
('PR14', 'Giam 5% bia Saigon thung 24 lon',          'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 55 DAY)),
('PR15', 'Khuyen mai Trung Thu (da ket thuc)',       'EXPIRED', DATE_SUB(CURDATE(), INTERVAL 40 DAY));

INSERT INTO Applies_To (Barcode, Promo_ID, Promo_Start_date, Promo_End_date, Discount_Percent) VALUES
('8934001', 'PR01', DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_ADD(CURDATE(), INTERVAL 15 DAY), 10.00),
('8934010', 'PR01', DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_ADD(CURDATE(), INTERVAL 15 DAY), 10.00),
('8934003', 'PR02', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY),  5.00),
('8934007', 'PR03', DATE_SUB(CURDATE(), INTERVAL 130 DAY), DATE_SUB(CURDATE(), INTERVAL 100 DAY), 15.00),
('8934001', 'PR04', DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_ADD(CURDATE(), INTERVAL 10 DAY),  8.00),
('8934004', 'PR04', DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_ADD(CURDATE(), INTERVAL 10 DAY),  8.00),
('8934001', 'PR05', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 15 DAY), 10.00),
('8934006', 'PR06', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 25 DAY), 12.00),
('8934011', 'PR07', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY), 15.00),
('8934012', 'PR08', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 40 DAY),  7.00),
('8934014', 'PR09', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 35 DAY), 10.00),
('8934015', 'PR10', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 50 DAY),  6.00),
('8934016', 'PR11', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 18 DAY), 12.00),
('8934017', 'PR12', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 28 DAY),  8.00),
('8934019', 'PR13', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 33 DAY), 10.00),
('8934020', 'PR14', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 55 DAY),  5.00),
('8934002', 'PR15', DATE_SUB(CURDATE(), INTERVAL 60 DAY), DATE_SUB(CURDATE(), INTERVAL 40 DAY), 10.00),
('8934008', 'PR15', DATE_SUB(CURDATE(), INTERVAL 60 DAY), DATE_SUB(CURDATE(), INTERVAL 40 DAY), 12.00),
('8934013', 'PR01', DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_ADD(CURDATE(), INTERVAL 15 DAY), 10.00),
('8934018', 'PR09', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 35 DAY), 10.00);


-- =====================================================================
--  8 sp_seed_demo_data: sinh hoa don cho p_days ngay gan nhat.
--   * Sinh du lieu trai dai nhieu ngay (mac dinh 90 -> 91 ngay, 6 HD/ngay
--     = 546 hoa don) giong thuat toan ban dau nhung TUAN THU 100% schema/
--     routines moi:
--       - Ma hoa don <Shop>-<Counter>-<YYYYMMDD>-<SEQ> qua sp_create_invoice_header
--       - Xoay vong du 30 quay (C01..C30) & 30 thu ngan (E01..E30)
--       - E03, E18 duoc tang so dong hang de dung dau gio hang TB (Q4)
--       - Kiem tra BR15 (chi MANAGER E01..E15 duyet huy/tra hang) & BR16
--         (hoa don tra hang van giu trang thai PAID)
--       - Sau khi sinh xong, cap nhat lui ngay & dong bo lai YYYYMMDD trong
--         Invoice_ID (tam tat FK check trong luc doi ma) de ma hoa don khop
--         tuyet doi voi ngay giao dich va co hoa don cu giu gia cu (BR8).
-- =====================================================================
DROP PROCEDURE IF EXISTS sp_seed_demo_data;
DELIMITER $$

CREATE PROCEDURE sp_seed_demo_data(
    IN p_days        INT, -- So ngay lui ve qua khu (90 => 91 ngay: -90..hom nay)
    IN p_inv_per_day INT  -- So hoa don moi ngay (6 => 546 hoa don trong 91 ngay)
)
BEGIN
    DECLARE v_d          INT;
    DECLARE v_k          INT;
    DECLARE v_j          INT;
    DECLARE v_lines      INT;
    DECLARE v_id         VARCHAR(30);
    DECLARE v_pay_id     VARCHAR(20);
    DECLARE v_date       DATE;
    DECLARE v_time       TIME;
    DECLARE v_counter    VARCHAR(10);
    DECLARE v_cashier    VARCHAR(10);
    DECLARE v_manager    VARCHAR(10);
    DECLARE v_customer   VARCHAR(15);
    DECLARE v_barcode    VARCHAR(20);
    DECLARE v_unit       VARCHAR(10);
    DECLARE v_qty        DECIMAL(10,3);
    DECLARE v_due        DECIMAL(12,2);
    DECLARE v_recorded   DECIMAL(12,2);
    DECLARE v_tendered   DECIMAL(12,2);
    DECLARE v_rest       DECIMAL(12,2);
    DECLARE v_method     VARCHAR(10);
    DECLARE v_status     VARCHAR(10);
    DECLARE v_line1_qty  DECIMAL(10,3);
    DECLARE v_line1_unit VARCHAR(10);
    DECLARE v_sample     INT;

    -- Bang tam anh xa Invoice_ID tam thoi -> Ngay & Gio lich su that
    DROP TEMPORARY TABLE IF EXISTS tmp_inv_schedule;
    CREATE TEMPORARY TABLE tmp_inv_schedule (
        Old_ID      VARCHAR(30) PRIMARY KEY,
        Counter_ID  VARCHAR(10) NOT NULL,
        Target_Date DATE        NOT NULL,
        Target_Time TIME        NOT NULL,
        New_ID      VARCHAR(30) NULL
    );

    -- Duyet tu ngay cu nhat (-p_days) den hom nay (0)
    SET v_d = p_days;
    WHILE v_d >= 0 DO
        SET v_date = DATE_SUB(CURDATE(), INTERVAL v_d DAY);

        SET v_k = 1;
        WHILE v_k <= p_inv_per_day DO
            -- Hoa don mau dau tien cua ca du an (tai ngay -p_days, luot 1)
            SET v_sample = IF(v_d = p_days AND v_k = 1, 1, 0);

            -- Gio ban ngau nhien co dinh trong khung 07:00..21:59
            SET v_time = MAKETIME(7 + MOD(v_d * 7 + v_k * 13, 15),
                                  MOD(v_d * 29 + v_k * 17, 60),
                                  MOD(v_d * 11 + v_k * 19, 60));

            -- Phan bo deu qua 30 quay (C01..C30) thuoc 15 Shop (S01..S15)
            SET v_counter = CONCAT('C', LPAD(MOD(v_d * p_inv_per_day + v_k - 1, 30) + 1, 2, '0'));
            -- Phan bo qua 30 nhan vien (E01..E30)
            SET v_cashier = CONCAT('E', LPAD(MOD(v_d + v_k, 30) + 1, 2, '0'));
            -- Manager quan ly Shop tuong ung (E01..E15)
            SET v_manager = CONCAT('E', LPAD(MOD(v_d * p_inv_per_day + v_k - 1, 15) + 1, 2, '0'));

            -- 1/3 khach vang lai (NULL), 2/3 khach thanh vien (KH001..KH030) - BR10
            SET v_customer = IF(MOD(v_d * 3 + v_k, 3) = 0, NULL,
                                CONCAT('KH', LPAD(MOD(v_d * 11 + v_k * 7, 30) + 1, 3, '0')));

            -- So dong hang moi hoa don: 1..3 dong; E03 & E18 them dong de dung dau Q4
            SET v_lines = 1 + MOD(v_d + v_k, 3);
            IF v_cashier IN ('E03', 'E18') THEN
                SET v_lines = v_lines + 1;
            END IF;
            IF v_sample = 1 AND v_lines < 3 THEN
                SET v_lines = 3;
            END IF;

            -- Tao dau hoa don qua routine chuan (tu sinh ma <Shop>-<Counter>-<YYYYMMDD>-<SEQ>)
            CALL sp_create_invoice_header(NULL, NULL, v_counter, v_cashier, v_customer, v_id);

            -- Ghi lai lich ngay/gio muc tieu cua hoa don nay
            INSERT INTO tmp_inv_schedule (Old_ID, Counter_ID, Target_Date, Target_Time)
            VALUES (v_id, v_counter, v_date, v_time);

            -- Them cac dong hang
            SET v_j = 1;
            WHILE v_j <= v_lines DO
                SET v_barcode = CONCAT('89340',
                                LPAD(MOD(v_d * 3 + v_k * 7 + v_j * 11, 20) + 1, 2, '0'));

                -- Hoa don mau ep dong 1 la 8934001 (co KM) voi SL = 2
                IF v_sample = 1 AND v_j = 1 THEN
                    SET v_barcode = '8934001';
                END IF;

                SELECT Unit INTO v_unit FROM Product WHERE Barcode = v_barcode;

                -- BR7: Hang can/dong (KG, LITER) cho phep thap phan; hang dong goi so nguyen
                SET v_qty = CASE
                    WHEN v_unit IN ('KG', 'LITER') THEN
                        CASE MOD(v_d + v_k + v_j, 4)
                            WHEN 0 THEN 0.500
                            WHEN 1 THEN 1.000
                            WHEN 2 THEN 1.500
                            ELSE        2.000
                        END
                    ELSE 1 + MOD(v_d + v_k + v_j, 3)
                END;

                IF v_sample = 1 AND v_j = 1 THEN
                    SET v_qty = 2.000;
                END IF;

                CALL sp_add_invoice_line(v_id, v_j, v_barcode, v_qty, NULL, NULL);
                SET v_j = v_j + 1;
            END WHILE;

            -- Thanh toan (BR12, BR13, BR14)
            SET v_due = fn_invoice_amount_due(v_id);

            IF MOD(v_d + v_k, 7) = 0 AND v_sample = 0 THEN
                -- Khach dua dung so tien (tien thoi = 0)
                CALL sp_add_payment(fn_next_payment_id(), v_id, v_due,
                                    'CASH', CAST(v_due AS CHAR), NULL);

            ELSEIF MOD(v_d + v_k, 5) = 0 THEN
                -- Thanh toan tach 2 phuong thuc tren cung 1 hoa don (BR12)
                SET v_recorded = ROUND(v_due * 0.40, 2);
                SET v_tendered = CEILING(v_recorded / 10000) * 10000;
                CALL sp_add_payment(fn_next_payment_id(), v_id, v_recorded,
                                    'CASH', CAST(v_tendered AS CHAR), NULL);

                SET v_rest   = v_due - v_recorded;
                SET v_pay_id = fn_next_payment_id();
                IF MOD(v_d + v_k, 2) = 0 THEN
                    CALL sp_add_payment(v_pay_id, v_id, v_rest, 'CARD',
                        CONCAT('4111-****-****-', LPAD(MOD(v_d * 97 + v_k * 13, 10000), 4, '0')),
                        CONCAT('AUTH', LPAD(MOD(v_d * 31 + v_k * 7, 10000), 4, '0')));
                ELSE
                    CALL sp_add_payment(v_pay_id, v_id, v_rest, 'EWALLET',
                        ELT(MOD(v_d + v_k, 3) + 1, 'MoMo', 'ZaloPay', 'VNPay'),
                        CONCAT('TXN-', v_pay_id));
                END IF;

            ELSE
                -- Thanh toan 1 phuong thuc xoay vong CASH / CARD / EWALLET
                SET v_method = ELT(MOD(v_d * 3 + v_k, 3) + 1, 'CASH', 'CARD', 'EWALLET');
                SET v_pay_id = fn_next_payment_id();

                IF v_method = 'CASH' THEN
                    SET v_tendered = CEILING(v_due / 10000) * 10000;
                    CALL sp_add_payment(v_pay_id, v_id, v_due, 'CASH',
                                        CAST(v_tendered AS CHAR), NULL);
                ELSEIF v_method = 'CARD' THEN
                    CALL sp_add_payment(v_pay_id, v_id, v_due, 'CARD',
                        CONCAT('5500-****-****-', LPAD(MOD(v_d * 89 + v_k * 11, 10000), 4, '0')),
                        CONCAT('AUTH', LPAD(MOD(v_d * 17 + v_k * 23, 10000), 4, '0')));
                ELSE
                    CALL sp_add_payment(v_pay_id, v_id, v_due, 'EWALLET',
                        ELT(MOD(v_d * 2 + v_k, 3) + 1, 'MoMo', 'ZaloPay', 'VNPay'),
                        CONCAT('TXN-', v_pay_id));
                END IF;
            END IF;

            -- Chot hoa don PAID (BR3, BR14)
            CALL sp_settle_invoice(v_id);

            SET v_status = NULL;
            SELECT Status INTO v_status FROM Invoice WHERE Invoice_ID = v_id;

            -- Nghiep vu sau thanh toan: Huy hoa don (VOIDED) hoac Tra hang (Return)
            IF v_status = 'PAID' AND v_sample = 0
               AND MOD(v_d * 7 + v_k * 11, 37) = 0 THEN
                -- BR3 & BR15: Huy hoa don boi MANAGER
                CALL sp_void_invoice(v_id, v_manager);

            ELSEIF v_status = 'PAID' AND MOD(v_d + v_k * 3, 12) = 0 THEN
                -- BR15 & BR16: Tra hang tren hoa don PAID, giu nguyen trang thai PAID

                SELECT l.Quantity, p.Unit
                INTO   v_line1_qty, v_line1_unit
                FROM   Invoice_Line l
                JOIN   Product p ON p.Barcode = l.Barcode
                WHERE  l.Invoice_ID = v_id AND l.Line_Number = 1;

                IF MOD(v_d, 7) = 0 THEN
                    -- Tra toan bo dong 1 (hoan > 50% gia tri cho Query Q5)
                    CALL sp_add_return(v_id, 1, CURDATE(), v_line1_qty, v_manager);
                ELSE
                    -- Tra mot phan dong 1 (ton trong so nguyen cho hang dong goi)
                    IF v_line1_unit IN ('KG', 'LITER') THEN
                        CALL sp_add_return(v_id, 1, CURDATE(), ROUND(v_line1_qty / 2, 3), v_manager);
                    ELSEIF v_line1_qty >= 2 THEN
                        CALL sp_add_return(v_id, 1, CURDATE(), 1.000, v_manager);
                    ELSE
                        CALL sp_add_return(v_id, 1, CURDATE(), v_line1_qty, v_manager);
                    END IF;
                END IF;
            END IF;

            SET v_k = v_k + 1;
        END WHILE;

        SET v_d = v_d - 1;
    END WHILE;

    -- =================================================================
    --  DONG BO LICH SU NGAY & MA HOA DON BR1]
    --   * Tinh lai ma chuan <Shop>-<Counter>-<YYYYMMDD>-<SEQ> theo tung ngay
    --     lich su Target_Date va cap nhat dong bo tren Invoice, Invoice_Line,
    --     Payment, Return (tam tat FOREIGN_KEY_CHECKS & trg_invoice_bu).
    -- =================================================================
    DROP TEMPORARY TABLE IF EXISTS tmp_inv_computed;
    CREATE TEMPORARY TABLE tmp_inv_computed AS
    SELECT t.Old_ID,
           CONCAT(
               c.Shop_ID, '-', t.Counter_ID, '-',
               DATE_FORMAT(t.Target_Date, '%Y%m%d'), '-',
               LPAD(ROW_NUMBER() OVER (
                   PARTITION BY c.Shop_ID, t.Counter_ID, t.Target_Date
                   ORDER BY t.Old_ID
               ), 4, '0')
           ) AS Computed_New_ID
    FROM tmp_inv_schedule t
    JOIN Counter c ON c.Counter_ID = t.Counter_ID;


    UPDATE tmp_inv_schedule s
    JOIN   tmp_inv_computed m ON m.Old_ID = s.Old_ID
    SET    s.New_ID = m.Computed_New_ID;

    DROP TEMPORARY TABLE IF EXISTS tmp_inv_computed;

    SET FOREIGN_KEY_CHECKS = 0;

    UPDATE Invoice_Line l
    JOIN   tmp_inv_schedule s ON s.Old_ID = l.Invoice_ID
    SET    l.Invoice_ID = s.New_ID;

    UPDATE Payment p
    JOIN   tmp_inv_schedule s ON s.Old_ID = p.Invoice_ID
    SET    p.Invoice_ID = s.New_ID;

    UPDATE `Return` r
    JOIN   tmp_inv_schedule s ON s.Old_ID = r.Invoice_ID
    SET    r.Invoice_ID = s.New_ID;

    UPDATE Invoice i
    JOIN   tmp_inv_schedule s ON s.Old_ID = i.Invoice_ID
    SET    i.Invoice_ID = s.New_ID,
           i.`Date`     = s.Target_Date,
           i.`Time`     = s.Target_Time;

    UPDATE `Return` r
    JOIN   Invoice i ON i.Invoice_ID = r.Invoice_ID
    SET    r.Return_Date = LEAST(
               DATE_ADD(i.`Date`, INTERVAL MOD(DAYOFYEAR(i.`Date`), 3) DAY),
               CURDATE()
           );

    -- Dong bo lai gia & khuyen mai lich su (BR8) cho cac hoa don ban truoc hom nay
    UPDATE Invoice_Line l
    JOIN   Invoice i ON i.Invoice_ID = l.Invoice_ID
    SET    l.Unit_Price = fn_get_product_price(l.Barcode, i.`Date`),
           l.Discount   = fn_line_discount(l.Barcode, i.`Date`, l.Quantity,
                                           fn_get_product_price(l.Barcode, i.`Date`))
    WHERE  i.`Date` < CURDATE();

    -- Dong bo lai Payment & Cash_Payment cho hoa don bi doi gia lich su (< @new_day)
    DROP TEMPORARY TABLE IF EXISTS tmp_inv_recalc_pay;
    CREATE TEMPORARY TABLE tmp_inv_recalc_pay AS
    SELECT p.Payment_ID,
           p.Invoice_ID,
           p.Amount AS Old_Amount,
           fn_invoice_amount_due(p.Invoice_ID) AS New_Due,
           SUM(p.Amount) OVER (PARTITION BY p.Invoice_ID) AS Sum_Old_Pay,
           ROW_NUMBER() OVER (PARTITION BY p.Invoice_ID ORDER BY p.Payment_ID) AS Rn,
           COUNT(*) OVER (PARTITION BY p.Invoice_ID) AS Cnt
    FROM   Payment p
    JOIN   Invoice i ON i.Invoice_ID = p.Invoice_ID
    WHERE  i.`Date` < CURDATE();

    DROP TEMPORARY TABLE IF EXISTS tmp_inv_pay_split;
    CREATE TEMPORARY TABLE tmp_inv_pay_split AS
    SELECT Payment_ID,
           Invoice_ID,
           New_Due,
           Rn,
           Cnt,
           ROUND(New_Due * (Old_Amount / NULLIF(Sum_Old_Pay, 0)), 2) AS Split_Amt
    FROM   tmp_inv_recalc_pay;

    DROP TEMPORARY TABLE IF EXISTS tmp_inv_prior_sum;
    CREATE TEMPORARY TABLE tmp_inv_prior_sum AS
    SELECT s.Invoice_ID,
           COALESCE(SUM(s.Split_Amt), 0) AS Prior_Sum
    FROM   tmp_inv_pay_split s
    WHERE  s.Rn < s.Cnt
    GROUP BY s.Invoice_ID;

    DROP TEMPORARY TABLE IF EXISTS tmp_inv_pay_final;
    CREATE TEMPORARY TABLE tmp_inv_pay_final AS
    SELECT s.Payment_ID,
           s.New_Due,
           CASE
               WHEN s.Cnt = 1 THEN s.New_Due
               WHEN s.Rn < s.Cnt THEN s.Split_Amt
               ELSE ROUND(s.New_Due - COALESCE(ps.Prior_Sum, 0), 2)
           END AS Final_Amount
    FROM   tmp_inv_pay_split s
    LEFT JOIN tmp_inv_prior_sum ps ON ps.Invoice_ID = s.Invoice_ID;

    UPDATE Payment p
    JOIN   tmp_inv_pay_final f ON f.Payment_ID = p.Payment_ID
    SET    p.Amount = f.Final_Amount
    WHERE  f.New_Due > 0;

    UPDATE Cash_Payment cp
    JOIN   Payment p ON p.Payment_ID = cp.Payment_ID
    JOIN   tmp_inv_pay_final f ON f.Payment_ID = p.Payment_ID
    SET    cp.Tendered_Amount = GREATEST(
               cp.Tendered_Amount,
               p.Amount,
               CEILING(p.Amount / 10000) * 10000
           );

    DROP TEMPORARY TABLE IF EXISTS tmp_inv_pay_final;
    DROP TEMPORARY TABLE IF EXISTS tmp_inv_prior_sum;
    DROP TEMPORARY TABLE IF EXISTS tmp_inv_pay_split;
    DROP TEMPORARY TABLE IF EXISTS tmp_inv_recalc_pay;

    SET FOREIGN_KEY_CHECKS = 1;
    DROP TEMPORARY TABLE IF EXISTS tmp_inv_schedule;
END$$

DELIMITER ;

-- =====================================================================
--  9 TAM TAT TRIGGER KHI GOI sp_seed_demo_data(90, 6)
--   * Sinh 91 ngay (-90..hom nay) x 6 hoa don/ngay = 546 hoa don trai deu
--     30 quay va 15 cua hang, sau do khoi phuc nguyen ban trg_invoice_bu.
-- =====================================================================
DROP TRIGGER IF EXISTS trg_invoice_bu;
DROP TRIGGER IF EXISTS trg_invoice_line_bu;
DROP TRIGGER IF EXISTS trg_payment_bu;
DROP TRIGGER IF EXISTS trg_cash_payment_bu;
DROP TRIGGER IF EXISTS trg_return_bu;

CALL sp_seed_demo_data(90, 6);

DROP PROCEDURE sp_seed_demo_data;

-- Khôi phục lại trigger trg_invoice_bu nguyên bản từ 04_routines.sql
DELIMITER $$

CREATE TRIGGER trg_cash_payment_bu
BEFORE UPDATE ON Cash_Payment
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Payment subtype records are immutable';
END$$

CREATE TRIGGER trg_invoice_bu
BEFORE UPDATE ON Invoice
FOR EACH ROW
BEGIN
    IF NEW.Invoice_ID <> OLD.Invoice_ID
       OR NEW.`Date` <> OLD.`Date`
       OR NEW.`Time` <> OLD.`Time`
       OR NEW.Counter_ID <> OLD.Counter_ID
       OR NEW.Cashier_ID <> OLD.Cashier_ID THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invoice identity, timestamp, counter and cashier are immutable (BR1/BR2/BR4)';
    END IF;

    IF NEW.Status <> OLD.Status THEN
        IF NOT (OLD.Status = 'PAID' AND NEW.Status = 'VOIDED'
                AND COALESCE(@retail_manager_void, 0) = 1) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Only an authorized manager may change PAID to VOIDED (BR3)';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_invoice_line_bu
BEFORE UPDATE ON Invoice_Line
FOR EACH ROW
BEGIN
    DECLARE v_invoice_date DATE         DEFAULT NULL;
    DECLARE v_unit         VARCHAR(10)  DEFAULT NULL;
    DECLARE v_pct          DECIMAL(5,2) DEFAULT 0;

    IF COALESCE(@retail_line_write, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invoice lines may only be changed through sp_add_invoice_line quantity accumulation (BR7/BR8)';
    END IF;

    IF NEW.Barcode <> OLD.Barcode
       OR NEW.Unit_Price <> OLD.Unit_Price
       OR NEW.Tax_Rate <> OLD.Tax_Rate THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Barcode, frozen unit price and tax snapshot cannot be edited (BR8/BR9)';
    END IF;

    IF EXISTS (SELECT 1 FROM Payment WHERE Invoice_ID = OLD.Invoice_ID) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Paid invoice lines cannot be edited';
    END IF;

    SELECT `Date` INTO v_invoice_date FROM Invoice WHERE Invoice_ID = OLD.Invoice_ID;
    SELECT Unit   INTO v_unit         FROM Product WHERE Barcode    = OLD.Barcode;

    IF NEW.Quantity <= 0
       OR (v_unit IN ('PIECE','PACK','BOX') AND NEW.Quantity <> FLOOR(NEW.Quantity)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid quantity for product unit (BR7)';
    END IF;

    SET v_pct = fn_active_promo_pct(OLD.Barcode, v_invoice_date);
    SET NEW.Discount = ROUND(NEW.Quantity * OLD.Unit_Price * v_pct / 100, 2);
END$$

CREATE TRIGGER trg_return_bu
BEFORE UPDATE ON `Return`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Return records are immutable for audit (BR16)';
END$$

CREATE TRIGGER trg_payment_bu
BEFORE UPDATE ON Payment
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Payment records are immutable';
END$$
DELIMITER ;


-- ---------------------------------------------------------------------
-- 10. KIỂM TRA SỐ LƯỢNG DÒNG TỪNG BẢNG (ALL >= 15)
-- ---------------------------------------------------------------------
SELECT 'Shop'            AS Bang, COUNT(*) AS So_dong FROM Shop
UNION ALL SELECT 'Counter',         COUNT(*) FROM Counter
UNION ALL SELECT 'Cashier',         COUNT(*) FROM Cashier
UNION ALL SELECT 'Customer',        COUNT(*) FROM Customer
UNION ALL SELECT 'Product',         COUNT(*) FROM Product
UNION ALL SELECT 'Price_History',   COUNT(*) FROM Price_History
UNION ALL SELECT 'Promotion',       COUNT(*) FROM Promotion
UNION ALL SELECT 'Applies_To',      COUNT(*) FROM Applies_To
UNION ALL SELECT 'Invoice',         COUNT(*) FROM Invoice
UNION ALL SELECT 'Invoice_Line',    COUNT(*) FROM Invoice_Line
UNION ALL SELECT 'Return',          COUNT(*) FROM `Return`
UNION ALL SELECT 'Payment',         COUNT(*) FROM Payment
UNION ALL SELECT 'Cash_Payment',    COUNT(*) FROM Cash_Payment
UNION ALL SELECT 'Card_Payment',    COUNT(*) FROM Card_Payment
UNION ALL SELECT 'EWallet_Payment', COUNT(*) FROM EWallet_Payment;

SET SQL_SAFE_UPDATES = 1;

SELECT '02_seed.sql loaded successfully!' AS ket_qua;