-- ================= UPDATED 18/10/2026 (Lộc) =================
--    - Shop      : S04..S15            (12 dòng mới, tổng 15)
--    - Counter   : C07..C30            (24 dòng mới, tổng 30)
--    - Cashier   : E06..E15            (10 dòng mới, tổng 15)  ++ Thêm 17 lên 32       21'11
--    - Promotion : PR07..PR15          (09 dòng mới, tổng 15)
--    - Applies_To: PR07..PR15          (10 dòng mới, tổng 21)
--    - Account   : 13 tài khoản mới    (tổng 15)               ++ Thêm lên tổng 32     21'11

--    UPDATED LATER:
--    - sp_seed_demo_data(90, 6): Có nên đổi thành (90, 30) do đã có 30 Counter?
--                                Nếu làm vậy thì sẽ sinh ra khoảng 2k7 hơn hóa đơn, có thể nạp hơi lâu -> Có thể sẽ tìm cách khác

--    v_counter, v_cashier (Line 620,621) : có thể thay đổi thuật toán do tăng số lượng Counter và Cashier

--    [WARNING]: Vì số lượng data tăng, có thể các thuật toán cũ cần được thay đổi để phù hợp hơn

-- =====================================================================
--  02_seed.sql  --  Dữ liệu mẫu: 91 ngày liên tục, sinh bằng vòng lặp
--  Chạy bằng:  mysql -u root -p retail_billing < 02_seed.sql
-- =====================================================================
--  BẮT BUỘC: file này phải chạy SAU 04_routines.sql.
--  Nó GỌI các hàm và thủ tục trong đó để sinh dữ liệu, nên nạp seed trước
--  routine là lỗi "FUNCTION retail_billing.fn_get_product_price does not
--  exist". Hai đường nạp của dự án đều đã theo thứ tự 01 -> 04 -> 02:
--    - scripts/db-setup.*            (3 bước)
--    - sql-create-db.* + sql-seed-data.*  (đường của Setup App)
--  Xem plan/15 mục 4.1.
-- ---------------------------------------------------------------------
--  NGUYÊN TẮC CỦA FILE NÀY (plan/15 mục 2.2):
--
--    "Seed là dữ liệu mà app có thể tự tạo ra. Nếu một thủ tục của app
--     từ chối một dòng, seed không được có dòng đó."
--
--  Hệ quả, và đây là điều quan trọng nhất cần biết trước khi sửa file này:
--
--  1. BẢY BẢNG GIAO DỊCH (Invoice, Invoice_Line, Payment + 3 bảng con,
--     `Return`) KHÔNG có một câu INSERT trực tiếp nào. Mọi dòng đi qua
--     sp_create_invoice_header, sp_add_invoice_line, sp_add_payment,
--     sp_settle_invoice, sp_void_invoice, sp_add_return -- đúng những thủ
--     tục mà tab 1 "Lập hóa đơn" đi qua.
--
--  2. CHÍN BẢNG DANH MỤC (Shop, Counter, Cashier, Customer, Product,
--     Price_History, Promotion, Applies_To, Account) vẫn INSERT ... VALUES
--     trực tiếp: KHÔNG có thủ tục nào ghi chúng, nên không có hợp đồng nào
--     để đi qua. Đây cũng là phần người học ĐỌC, nên nó giữ lối viết dày
--     comment như cũ.
--
--  3. BỐN CON SỐ KHÔNG AI ĐƯỢC GÕ TAY: Status, Unit_Price, Discount,
--     Return_ID. Chủ của chúng là SQL:
--       Status     <- sp_settle_invoice / sp_void_invoice
--       Unit_Price <- fn_get_product_price(barcode, ngày bán)
--       Discount   <- fn_line_discount(barcode, ngày bán, SL, đơn giá)
--       Return_ID  <- fn_next_return_id (do sp_add_return gọi)
--
--  4. MỌI MỐC NGÀY LÀ CURDATE() - n. Có đúng MỘT ngoại lệ, là mốc đổi giá
--     @new_day, và nó có comment riêng giải thích (xem bên dưới).
--
--  CHỐT BẰNG SỐ: sql/05_invariants.sql có 14 bất biến, chạy bằng
--  .\scripts\ps1\check-seed.ps1 (bash: ./scripts/bash/check-seed.sh).
--  Sửa file này thì chạy lại lệnh đó -- tất cả phải về 0.
-- =====================================================================

USE retail_billing;


-- ---------------------------------------------------------------------
-- 0. DON SACH -- de file nay chay lai duoc bao nhieu lan cung xong,
--    dung nhu khoi DROP o dau 04_routines.sql.
--    Thu tu la NGUOC CHIEU KHOA NGOAI: bang con truoc, bang cha sau.
--    Doc tu duoi len la doc dung thu tu CREATE TABLE cua 01_schema.sql.
--
--    KHONG dung SET FOREIGN_KEY_CHECKS = 0: ngan hon 14 dong, nhung no
--    day nguoi hoc tat toan ven du lieu de do phai nghi ve thu tu -- nguoc
--    han muc dich cua repo nay. Va no IM LANG khi thu tu sai, trong khi
--    cach duoi day BAO LOI (plan/15 muc 4.2).
--    TRUNCATE cung khong dung duoc: MySQL tu choi TRUNCATE tren bang dang
--    duoc mot khoa ngoai tham chieu.
-- ---------------------------------------------------------------------
DELETE FROM `Return`;             -- con cua Invoice_Line
DELETE FROM Cash_Payment;         -- 3 bang con cua Payment
DELETE FROM Card_Payment;
DELETE FROM EWallet_Payment;
DELETE FROM Payment;              -- con cua Invoice
DELETE FROM Invoice_Line;         -- con cua Invoice (va cua Product)
DELETE FROM Invoice;
DELETE FROM Account;              -- con cua Cashier
DELETE FROM Applies_To;           -- con cua Product va Promotion
DELETE FROM Promotion;
DELETE FROM Price_History;        -- con cua Product
DELETE FROM Product;
DELETE FROM Customer;
DELETE FROM Counter;              -- con cua Shop
DELETE FROM Shop;

-- Cashier TU THAM CHIEU chinh no (Supervisor_ID -> Cashier_ID), nen mot
-- cau DELETE duy nhat se that bai: InnoDB kiem khoa ngoai TUNG DONG, va
-- xoa E01 trong khi E02..E05 con tro vao no la vi pham.
-- Thao lien ket truoc, roi moi xoa.
UPDATE Cashier SET Supervisor_ID = NULL;
DELETE FROM Cashier;


-- ---------------------------------------------------------------------
-- Hai mốc ngày của lịch sử giá
--
--   @old_day = CURDATE() - 400  -- "giá cũ", trước cả cửa sổ 91 ngày
--   @new_day = ngày ĐỔI GIÁ, nằm GIỮA cửa sổ hóa đơn (CURDATE() - 45)
--
-- @new_day LÀ NGOẠI LỆ DUY NHẤT của quy ước "mọi mốc là CURDATE() - n",
-- và phép kẹp theo NĂM dưới đây là CỐ Ý -- đừng "dọn dẹp" nó:
--   sp_report_price_rise lọc YEAR(Valid_From_Date) = YEAR(CURDATE())
--   (04_routines.sql mục 4.2), nên mốc đổi giá PHẢI nằm trong năm dương
--   lịch hiện tại, nếu không báo cáo câu 3 rỗng.
-- Trong 45 ngày đầu năm, GREATEST kẹp nó về 1 tháng 1. Điều đó KHÔNG làm
-- sai một dòng hàng nào: đơn giá của mọi dòng được TRA bằng
-- fn_get_product_price tại ngày bán (bất biến G1), chứ không phải giả định
-- sản phẩm nào mang giá nào. Cái nó đổi chỉ là TỶ LỆ hóa đơn mang giá cũ.
-- Xem plan/15 mục 3.2, và kịch bản #93 kiểm đúng trường hợp đó.
--
-- Vì @new_day = CURDATE() - 45 nằm GIỮA cửa sổ hóa đơn (91 ngày), CÙNG MỘT
-- sản phẩm xuất hiện ở HAI đơn giá trên hai hóa đơn mà người dùng thấy
-- cạnh nhau trong tab 3. Trước khối 6 phải lùi 400 ngày mới thấy.
-- ---------------------------------------------------------------------
SET @old_day = DATE_SUB(CURDATE(), INTERVAL 400 DAY);
SET @new_day = GREATEST(MAKEDATE(YEAR(CURDATE()), 1),
                        DATE_SUB(CURDATE(), INTERVAL 45 DAY));


-- ---------------------------------------------------------------------
-- Cửa hàng & quầy -- 3 shop, 6 quầy, ĐÚNG 2 QUẦY MỖI SHOP
--
-- Con số 2 không phải tình cờ: vòng lặp chọn quầy bằng
-- MOD(k - 1, 6) + 1 với k = 1..6, nên mỗi quầy nhận đúng 1 hóa đơn mỗi
-- ngày => mỗi SHOP nhận đúng 2 hóa đơn mỗi ngày => bảng chéo (shop x ngày)
-- của báo cáo câu 2 KHÔNG CÓ Ô RỖNG nào. plan/15 mục 4.3.
-- ---------------------------------------------------------------------
INSERT INTO Shop (Shop_ID, Address) VALUES
('S01', '123 Nguyen Hue, Quan 1, TP.HCM'),
('S02', '45 Nguyen Van Linh, Quan 7, TP.HCM'),
('S03', '78 Vo Van Ngan, Thu Duc, TP.HCM'),
-- [UPDATED 18/10/2026]: Thêm 12 shops 'S04' -> 'S15'
('S04', '210 Le Van Viet, Thu Duc, TP.HCM'),
('S05', '56 Cach Mang Thang Tam, Quan 3, TP.HCM'),
('S06', '312 Nguyen Thi Minh Khai, Quan 3, TP.HCM'),
('S07', '15 Pham Van Dong, Go Vap, TP.HCM'),
('S08', '89 Quang Trung, Go Vap, TP.HCM'),
('S09', '402 Hoang Van Thu, Tan Binh, TP.HCM'),
('S10', '27 Truong Chinh, Tan Phu, TP.HCM'),
('S11', '150 Phan Van Tri, Binh Thanh, TP.HCM'),
('S12', '66 Xo Viet Nghe Tinh, Binh Thanh, TP.HCM'),
('S13', '9 Nguyen Duy Trinh, Thu Duc, TP.HCM'),
('S14', '233 Ly Thuong Kiet, Quan 10, TP.HCM'),
('S15', '71 Tran Hung Dao, Quan 5, TP.HCM');
-- /[UPDATED 18/10/2026]: Thêm 12 shops 'S04' -> 'S15'

INSERT INTO Counter (Counter_ID, Shop_ID) VALUES
('C01', 'S01'),
('C02', 'S01'),
('C03', 'S02'),
('C04', 'S02'),
('C05', 'S03'),
('C06', 'S03'),
-- [UPDATED 18/10/2026]: Thêm quầy cho shop 'S04' -> 'S15'
('C07', 'S04'),
('C08', 'S04'),
('C09', 'S05'),
('C10', 'S05'),
('C11', 'S06'),
('C12', 'S06'),
('C13', 'S07'),
('C14', 'S07'),
('C15', 'S08'),
('C16', 'S08'),
('C17', 'S09'),
('C18', 'S09'),
('C19', 'S10'),
('C20', 'S10'),
('C21', 'S11'),
('C22', 'S11'),
('C23', 'S12'),
('C24', 'S12'),
('C25', 'S13'),
('C26', 'S13'),
('C27', 'S14'),
('C28', 'S14'),
('C29', 'S15'),
('C30', 'S15');
-- /[UPDATED 18/10/2026]: Thêm quầy cho shop 'S04' -> 'S15'

-- ---------------------------------------------------------------------
-- Thu ngân (E01 là quản lý, không có sếp -> Supervisor_ID = NULL)
-- E01 cũng là "người duyệt" của mọi lượt trả hàng mà seed sinh ra.
-- ---------------------------------------------------------------------
INSERT INTO Cashier (Cashier_ID, Name, Supervisor_ID) VALUES
('E01', 'Tran Van An',       NULL),
('E02', 'Nguyen Thi Binh',   'E01'),
('E03', 'Le Minh Cuong',     'E01'),
('E04', 'Pham Thu Dung',     'E01'),
('E05', 'Vo Hoang Em',       'E01'),
-- [UPDATED 18/10/2026]: Thêm 2 quản lý E06 va E11
('E06', 'Dang Quoc Phong',   NULL),
('E07', 'Bui Thi Giang',     'E06'),
('E08', 'Ngo Van Hai',       'E06' ),
('E09', 'Ly Thi Huong',      'E06'),
('E10', 'Truong Minh Khoa',  'E06'),
('E11', 'Huynh Thi Lan',     NULL),
('E12', 'Do Van Long',       'E11'),
('E13', 'Cao Thi Mai',       'E11'),
('E14', 'Phan Van Nam',      'E11'),
('E15', 'Vu Thi Oanh',       'E11'),
-- /[UPDATED 18/10/2026]: Thêm 2 quản lý E06 va E11
('E16', 'Ha Minh Phuc',      'E11'),
('E17', 'Tran Thi Nhu',      'E06'),
('E18', 'Le Van Phu',        'E06'),
('E19', 'Nguyen Minh Quan',  'E11'),
('E20', 'Pham Thi Tu',       'E11'),
('E21', 'Vo Van Tai',        'E01'),
('E22', 'Dang Thi Yen',      'E01'),
('E23', 'Bui Minh Tuan',     'E06'),
('E24', 'Ngo Thi Bich',      'E06'),
('E25', 'Ly Van Chinh',      'E06'),
('E26', 'Truong Thi Dao',    'E06'),
('E27', 'Huynh Van Hao',     'E11'),
('E28', 'Do Thi Kieu',       'E11'),
('E29', 'Cao Van Lam',       'E01'),
('E30', 'Phan Thi My',       'E01'),
('E31', 'Vu Van Ninh',       'E06'),
('E32', 'Ha Thi Phong',      'E06');

-- ---------------------------------------------------------------------
-- Khách hàng -- 30 khách, và phân bố SỐ ĐIỆN THOẠI là có chủ ý:
--
--   22 số bắt đầu '09'   -> gõ '09' vào ô khách hàng của tab 1 phải trả
--                           ĐÚNG 20 dòng, tức là CHẠM TRẦN LIMIT 20 của
--                           sp_search_customers_by_phone (04_routines.sql
--                           mục 2.4). Trước khối 6 có 4 khách nên cái
--                           LIMIT đó chưa bao giờ được chạm tới.
--    4 số bắt đầu '0901' -> gõ thêm một chữ số là kết quả hẹp lại, nên
--                           người dùng thấy ô tìm kiếm thật sự lọc.
--    8 số bắt đầu '03' / '07' / '08' -> ô tìm kiếm có nhiều nhánh.
--
-- KH001..KH004 GIỮ NGUYÊN tên và số của bản cũ.
-- ---------------------------------------------------------------------
INSERT INTO Customer (Customer_ID, Name, Phone) VALUES
('KH001', 'Hoang Van Kien',   '0901000001'),
('KH002', 'Do Thi Lan',       '0901000002'),
('KH003', 'Bui Quoc Manh',    '0901000003'),
('KH004', 'Ngo Thi Nga',      '0901000004'),
-- 18 khach '09' con lai: tien to da dang (0902..0935) de go '0901' thi
-- ket qua hep lai, con go '09' thi cham tran LIMIT 20.
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
-- 8 khach khong dung tien to '09'
('KH023', 'Luu Thi Lieu',     '0362202122'),
('KH024', 'Trinh Van Minh',   '0363212223'),
('KH025', 'Bach Thi Nhung',   '0703222324'),
('KH026', 'Kieu Van Oai',     '0705232425'),
('KH027', 'Thai Thi Phuong',  '0707242526'),
('KH028', 'Quach Van Quy',    '0812252627'),
('KH029', 'Nghiem Thi Suong', '0813262728'),
('KH030', 'Lam Van Thinh',    '0815272829');


-- ---------------------------------------------------------------------
-- Sản phẩm -- 20 mã (KHÔNG có cột giá, giá nằm ở Price_History)
--
--   8934001..8934010 GIỮ NGUYÊN tên / Unit / Tax_Rate của bản cũ.
--   8934011..8934020 là mã mới, trải đủ CẢ 5 giá trị Unit
--   (PIECE / KG / LITER / PACK / BOX) và CẢ 3 mức Tax_Rate (5 / 8 / 10),
--   nên mọi nhánh định dạng số lượng của khối 5 (Ui.qty với KG/LITER cho
--   số thập phân, PIECE/PACK/BOX cho số nguyên) đều có dữ liệu.
-- ---------------------------------------------------------------------
INSERT INTO Product (Barcode, Name, Unit, Tax_Rate) VALUES
('8934001', 'Sua tuoi Vinamilk 1L',          'LITER',  8.00),
('8934002', 'Banh mi sandwich',              'PACK',   8.00),
('8934003', 'Gao ST25 tui 5kg',              'PACK',   5.00),
('8934004', 'Trung ga hop 10 qua',           'BOX',    5.00),
('8934005', 'Nuoc mam Nam Ngu 500ml',        'PIECE',  8.00),
('8934006', 'Dau an Neptune 1L',             'LITER',  8.00),
('8934007', 'Mi Hao Hao thung 30 goi',       'BOX',    8.00),
('8934008', 'Thit ba chi',                   'KG',     5.00),
('8934009', 'Ca phe G7 hop 20 goi',          'BOX',    8.00),
('8934010', 'Nuoc ngot Coca 1.5L',           'PIECE', 10.00),
-- 10 ma moi
('8934011', 'Thit bo thai lat',              'KG',     5.00),
('8934012', 'Sua dau nanh Fami thung 36',    'BOX',    8.00),
('8934013', 'Nuoc suoi Lavie 1.5L',          'PIECE', 10.00),
('8934014', 'Bot giat Omo tui 3kg',          'PACK',  10.00),
('8934015', 'Nuoc tuong Maggi 700ml',        'PIECE',  8.00),
('8934016', 'Ca chua',                       'KG',     5.00),
('8934017', 'Sua chua Vinamilk lo 4 hop',    'PACK',   8.00),
('8934018', 'Nuoc rua tay Lifebuoy 500ml',   'PIECE', 10.00),
('8934019', 'Nuoc cam Teppy 1L',             'LITER',  8.00),
('8934020', 'Bia Saigon thung 24 lon',       'BOX',   10.00);


-- ---------------------------------------------------------------------
-- Lịch sử giá -- 28 mốc
--
--   20 mốc "giá cũ" ở @old_day (CURDATE() - 400): MỌI sản phẩm đều có giá
--      từ ngày đó, nên fn_get_product_price không bao giờ trả NULL cho một
--      hóa đơn trong cửa sổ 91 ngày. (Vòng lặp vẫn có nhánh xử lý NULL --
--      xem sp_seed_demo_data -- vì nhánh đó là cái giữ bất biến G1 đúng
--      nếu người sau thêm một sản phẩm không có giá ở @old_day.)
--    8 mốc "giá mới" ở @new_day, giữ NGUYÊN phân bố của bản cũ:
--      5 sản phẩm tăng HƠN 10%:   001, 003, 006, 008, 010
--      3 sản phẩm tăng dưới 10%:  002, 004, 007
--      2 sản phẩm chưa đổi giá:   005, 009
--      (10 sản phẩm mới cũng thuộc nhóm "chưa đổi giá" -- mỗi cái 1 mốc)
--
--   Nhờ phân bố này mà sp_report_price_rise vẫn ra ĐÚNG 5 dòng như trước
--   khối 6 (kịch bản #92), dù số sản phẩm đã tăng gấp đôi.
--
--   8934008 giữ ĐÚNG cặp 130.000 -> 158.000: docs/03-SCHEMA.md mục 3 ghim
--   cặp số đó làm ví dụ của bảng Price_History.
-- ---------------------------------------------------------------------
INSERT INTO Price_History (Barcode, Valid_From_Date, Price) VALUES
-- giá cũ (@old_day = CURDATE() - 400)
('8934001', @old_day,  28000.00),
('8934002', @old_day,  18000.00),
('8934003', @old_day, 150000.00),
('8934004', @old_day,  32000.00),
('8934005', @old_day,  45000.00),
('8934006', @old_day,  52000.00),
('8934007', @old_day, 120000.00),
('8934008', @old_day, 130000.00),
('8934009', @old_day,  78000.00),
('8934010', @old_day,  18000.00),
('8934011', @old_day, 185000.00),
('8934012', @old_day, 175000.00),
('8934013', @old_day,   9000.00),
('8934014', @old_day,  92000.00),
('8934015', @old_day,  25000.00),
('8934016', @old_day,  32000.00),
('8934017', @old_day,  28000.00),
('8934018', @old_day,  48000.00),
('8934019', @old_day,  35000.00),
('8934020', @old_day, 390000.00),
-- giá mới (@new_day = CURDATE() - 45, kẹp trong năm nay)
('8934001', @new_day,  33000.00),   -- +17.86%  <-- tang > 10%
('8934002', @new_day,  19000.00),   --  +5.56%
('8934003', @new_day, 175000.00),   -- +16.67%  <-- tang > 10%
('8934004', @new_day,  35000.00),   --  +9.38%
('8934006', @new_day,  62000.00),   -- +19.23%  <-- tang > 10%
('8934007', @new_day, 128000.00),   --  +6.67%
('8934008', @new_day, 158000.00),   -- +21.54%  <-- tang > 10%
('8934010', @new_day,  21000.00);   -- +16.67%  <-- tang > 10%


-- ---------------------------------------------------------------------
-- Khuyến mãi -- 6 chương trình phủ kín 91 ngày
--
-- *** VẤN ĐỀ PHẢI HIỂU TRƯỚC KHI SỬA BẢNG NÀY ***
--
-- sp_get_active_promo (04_routines.sql mục 2.6) lọc BA điều kiện:
--     (a) p_date nằm trong cửa sổ Applies_To   <- sự thật LỊCH SỬ
--     (b) Promotion.Status = 'ACTIVE'          <- sự thật HÔM NAY
--     (c) Promotion.Expiry_Date >= p_date      <- sự thật LỊCH SỬ
--
-- (b) trộn hai trục thời gian: một khuyến mãi đã chạy xong và được đánh
-- 'EXPIRED' thì TRA CỨU Ở ĐÚNG NGÀY NÓ ĐANG CHẠY cũng trả về rỗng. Với
-- seed 7 ngày không ai gặp; với 91 ngày đây là câu hỏi đầu tiên.
--
-- CÁCH GIẢI (plan/15 mục 3.3 phương án A) -- KHÔNG sửa một dòng thủ tục
-- nào, giải bằng DỮ LIỆU:
--     Expiry_Date ở TƯƠNG LAI, cửa sổ Applies_To ở QUÁ KHỨ.
-- Đọc theo lược đồ thì đó đúng là nghĩa của hai cột: Expiry_Date là hạn
-- của CHƯƠNG TRÌNH, còn cửa sổ ngày + mức giảm nằm ở Applies_To vì "mức
-- giảm phụ thuộc vào CẢ CẶP" (01_schema.sql mục 8). Một chương trình còn
-- hiệu lực đăng ký, nhưng ĐỢT ÁP DỤNG cho sản phẩm này đã xong.
-- Tra ở ngày -60: (a) ✓ (b) ✓ (c) ✓ -> trả về phần trăm.
-- Tra ở hôm nay:  (a) ✗                -> rỗng. Đúng y nghĩa muốn có.
--
-- Bất biến R14 của 05_invariants.sql chốt điều kiện (c): Expiry_Date phải
-- >= ngày đóng cửa sổ, nếu không tra cứu lịch sử sẽ "hụt ở cuối".
-- ---------------------------------------------------------------------
INSERT INTO Promotion (Promo_ID, Rule_Description, Status, Expiry_Date) VALUES
-- Dang chay HOM NAY -- giu nguyen hai chuong trinh cua ban cu
('PR01', 'Giam 10% sua tuoi va nuoc ngot',    'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 30 DAY)),
('PR02', 'Giam 5% gao ST25',                  'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 60 DAY)),
-- Da ket thuc HAN, va cua so cung nam NGOAI vung 91 ngay: khong mot dong
-- hang nao phu thuoc no, nen no minh hoa bo loc Status ma khong lam bat
-- bien nao lung lay. Giu nguyen cua ban cu.
('PR03', 'Khuyen mai Tet (da ket thuc)',      'EXPIRED', DATE_SUB(CURDATE(), INTERVAL 100 DAY)),
-- PR04/PR05: DOT DA XONG NHUNG TRA CUU LICH SU VAN DUNG -- trai tim cua
-- phuong an A. Expiry_Date o TUONG LAI (+45) trong khi cua so Applies_To
-- da qua (-90..-46 va -60..-30). Doc giai thich o khoi comment tren.
('PR04', 'Giam 8% sua tuoi va trung ga (dot dau)',  'ACTIVE', DATE_ADD(CURDATE(), INTERVAL 45 DAY)),
('PR05', 'Giam 12% sua tuoi (dot cao diem)',        'ACTIVE', DATE_ADD(CURDATE(), INTERVAL 45 DAY)),
-- TAM DUNG: co khuyen mai, dung ngay, dung san pham, muc cao nhat bang --
-- ma Discount van 0 vi Status loai no. Day la du lieu day hoc tot nhat
-- trong ca bang: "bang co dong" KHAC "quy tac duoc ap dung".
('PR06', 'Giam 20% dau an va sua dau nanh (tam dung)', 'PAUSED', DATE_ADD(CURDATE(), INTERVAL 90 DAY)),

-- [UPDATED 18/10/2026]: Thêm Promotion 'PR07' -> 'PR15'
('PR07', 'Giam 15% thit bo thai lat',        'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 20 DAY)),
('PR08', 'Giam 7% nuoc suoi Lavie',                 'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL  5 DAY)),
('PR09', 'Giam 4% nuoc tuong Maggi va Lifebuoy',    'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 10 DAY)),
('PR10', 'Giam 6% bot giat Omo',                    'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 15 DAY)),
('PR11', 'Giam 25% ca chua (da ket thuc)',          'EXPIRED', DATE_SUB(CURDATE(), INTERVAL 170 DAY)),
('PR12', 'Giam 9% sua chua Vinamilk',               'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL  5 DAY)),
('PR13', 'Giam 30% bia Saigon (tam dung)',          'PAUSED',  DATE_ADD(CURDATE(), INTERVAL 40 DAY)),
('PR14', 'Giam 4% nuoc cam Teppy',                  'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 10 DAY)),
('PR15', 'Giam 10% ca phe G7',                      'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 10 DAY));
-- /[UPDATED 18/10/2026]: Thêm Promotion 'PR07' -> 'PR15'

INSERT INTO Applies_To (Barcode, Promo_ID, Promo_Start_date, Promo_End_date, Discount_Percent) VALUES
-- PR01: dang chay hom nay, nen cot Giam_gia cua cac hoa don GAN NHAT khac 0
('8934001', 'PR01', DATE_SUB(CURDATE(), INTERVAL  15 DAY), DATE_ADD(CURDATE(), INTERVAL 15 DAY), 10.00),
('8934010', 'PR01', DATE_SUB(CURDATE(), INTERVAL  15 DAY), DATE_ADD(CURDATE(), INTERVAL 15 DAY), 10.00),
('8934003', 'PR02', DATE_SUB(CURDATE(), INTERVAL  10 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY),  5.00),
-- PR03: cua so NGOAI vung 91 ngay -> khong dong hang nao phu thuoc
('8934007', 'PR03', DATE_SUB(CURDATE(), INTERVAL 130 DAY), DATE_SUB(CURDATE(), INTERVAL 100 DAY), 15.00),
-- PR04: phu NUA DAU cua so (-90..-46), nen INV-0001 -- hoa don cu nhat, va
-- la hoa don ma 03_queries.sql CAU 1 lay lam vi du mau -- CO khuyen mai.
('8934001', 'PR04', DATE_SUB(CURDATE(), INTERVAL  90 DAY), DATE_SUB(CURDATE(), INTERVAL 46 DAY),  8.00),
('8934004', 'PR04', DATE_SUB(CURDATE(), INTERVAL  90 DAY), DATE_SUB(CURDATE(), INTERVAL 46 DAY),  8.00),
-- PR05: GOI LEN PR04 dung 15 ngay (-60..-46) tren CUNG san pham 8934001,
-- voi phan tram CAO HON. Trong 15 ngay do, "ORDER BY Discount_Percent DESC
-- LIMIT 1" cua sp_get_active_promo lan dau tien THAT SU phai chon: phai ra
-- 12%, khong phai 8%.
-- PHAI la mot Promo_ID KHAC: Applies_To co khoa chinh (Barcode, Promo_ID)
-- nen mot san pham khong the vao CUNG mot khuyen mai o hai cua so ngay
-- (01_schema.sql mục 8, plan/15 lỗi #16).
('8934001', 'PR05', DATE_SUB(CURDATE(), INTERVAL  60 DAY), DATE_SUB(CURDATE(), INTERVAL 30 DAY), 12.00),
-- PR06 (PAUSED): cua so nam GIUA vung hoa don (-45..-20) va phu dung hai
-- san pham co ban. Cac hoa don trong khoang do co dong 8934006/8934012 se
-- co Giam_gia = 0 du bang nay CO dong phu dung ngay do.
-- Bat bien G2 VAN DUNG, vi ca seed va phep kiem R2 deu hoi CUNG MOT ham
-- (fn_line_discount -> fn_active_promo_pct), va ham do cung loc Status.
('8934006', 'PR06', DATE_SUB(CURDATE(), INTERVAL  45 DAY), DATE_SUB(CURDATE(), INTERVAL 20 DAY), 20.00),
('8934012', 'PR06', DATE_SUB(CURDATE(), INTERVAL  45 DAY), DATE_SUB(CURDATE(), INTERVAL 20 DAY), 20.00),

-- [UPDATED 18/10/2026]: Thêm Promotion 'PR07' -> 'PR15 vào Applies_To
('8934011', 'PR07', DATE_SUB(CURDATE(), INTERVAL  80 DAY), DATE_SUB(CURDATE(), INTERVAL 66 DAY), 15.00),
('8934013', 'PR08', DATE_SUB(CURDATE(), INTERVAL  25 DAY), DATE_SUB(CURDATE(), INTERVAL  1 DAY),  7.00),
('8934015', 'PR09', DATE_SUB(CURDATE(), INTERVAL  60 DAY), DATE_SUB(CURDATE(), INTERVAL 40 DAY),  4.00),
('8934018', 'PR09', DATE_SUB(CURDATE(), INTERVAL  60 DAY), DATE_SUB(CURDATE(), INTERVAL 40 DAY),  4.00),
('8934014', 'PR10', DATE_SUB(CURDATE(), INTERVAL  30 DAY), DATE_SUB(CURDATE(), INTERVAL 10 DAY),  6.00),
-- PR11: EXPIRED va cua so NGOAI vung 91 ngay (giong PR03): khong dong hang nao phu thuoc
('8934016', 'PR11', DATE_SUB(CURDATE(), INTERVAL 200 DAY), DATE_SUB(CURDATE(), INTERVAL 170 DAY), 25.00),
('8934017', 'PR12', DATE_SUB(CURDATE(), INTERVAL  88 DAY), DATE_SUB(CURDATE(), INTERVAL 50 DAY),  9.00),
-- PR13: TAM DUNG, cua so nam trong vung hoa don -> Discount van 0 (giong PR06)
('8934020', 'PR13', DATE_SUB(CURDATE(), INTERVAL  14 DAY), DATE_SUB(CURDATE(), INTERVAL  1 DAY), 30.00),
('8934019', 'PR14', DATE_SUB(CURDATE(), INTERVAL  20 DAY), DATE_SUB(CURDATE(), INTERVAL  5 DAY),  4.00),
('8934009', 'PR15', DATE_SUB(CURDATE(), INTERVAL  70 DAY), DATE_SUB(CURDATE(), INTERVAL 50 DAY), 10.00);
-- [UPDATED 18/10/2026]: Thêm Promotion 'PR07' -> 'PR15' vào Applies_To

-- ---------------------------------------------------------------------
-- Tài khoản đăng nhập mặc định: cashier và manager
--   Cashier_ID gan tai khoan voi mot nhan vien that trong bang Cashier.
--   Cot nay lam "nguoi duyet" khi tra hang -- xem plan/12 muc 3.3.
-- ---------------------------------------------------------------------
INSERT INTO Account (Username, Password, Role, Full_Name, Cashier_ID) VALUES
('cashier', 'cashier', 'cashier', 'Nhan Vien Thu Ngan', 'E02'),
('manager', 'manager', 'manager', 'Quan Ly He Thong',   'E01'),

-- [UPDATED 18/10/2026]: Thêm Account (WARNING: Cần thống nhất lại)
('e03', 'e03@123', 'cashier', 'Le Minh Cuong',      'E03'),
('e04', 'e04@123', 'cashier', 'Pham Thu Dung',      'E04'),
('e05', 'e05@123', 'cashier', 'Vo Hoang Em',        'E05'),
('e06', 'e06@123', 'manager', 'Dang Quoc Phong',    'E06'),
('e07', 'e07@123', 'cashier', 'Bui Thi Giang',      'E07'),
('e08', 'e08@123', 'cashier', 'Ngo Van Hai',        'E08'),
('e09', 'e09@123', 'cashier', 'Ly Thi Huong',       'E09'),
('e10', 'e10@123', 'cashier', 'Truong Minh Khoa',   'E10'),
('e11', 'e11@123', 'manager', 'Huynh Thi Lan',      'E11'),
('e12', 'e12@123', 'cashier', 'Do Van Long',        'E12'),
('e13', 'e13@123', 'cashier', 'Cao Thi Mai',        'E13'),
('e14', 'e14@123', 'cashier', 'Phan Van Nam',       'E14'),
('e15', 'e15@123', 'cashier', 'Vu Thi Oanh',        'E15'),
-- [UPDATED 18/10/2026]: Thêm Account (WARNING: Cần thống nhất lại)
('e16', 'e16@123', 'cashier', 'Ha Minh Phuc',       'E16'),
('e17', 'e17@123', 'cashier', 'Tran Thi Nhu',       'E17'),
('e18', 'e18@123', 'cashier', 'Le Van Phu',         'E18'),
('e19', 'e19@123', 'cashier', 'Nguyen Minh Quan',   'E19'),
('e20', 'e20@123', 'cashier', 'Pham Thi Tu',        'E20'),
('e21', 'e21@123', 'cashier', 'Vo Van Tai',         'E21'),
('e22', 'e22@123', 'cashier', 'Dang Thi Yen',       'E22'),
('e23', 'e23@123', 'cashier', 'Bui Minh Tuan',      'E23'),
('e24', 'e24@123', 'cashier', 'Ngo Thi Bich',       'E24'),
('e25', 'e25@123', 'cashier', 'Ly Van Chinh',       'E25'),
('e26', 'e26@123', 'cashier', 'Truong Thi Dao',     'E26'),
('e27', 'e27@123', 'cashier', 'Huynh Van Hao',      'E27'),
('e28', 'e28@123', 'cashier', 'Do Thi Kieu',        'E28'),
('e29', 'e29@123', 'cashier', 'Cao Van Lam',        'E29'),
('e30', 'e30@123', 'cashier', 'Phan Thi My',        'E30'),
('e31', 'e31@123', 'cashier', 'Vu Van Ninh',        'E31'),
('e32', 'e32@123', 'cashier', 'Ha Thi Phong',       'E32');

-- =====================================================================
--  HAI HÓA ĐƠN CŨ (CURDATE() - 400) -- BẰNG CHỨNG "HÓA ĐƠN CŨ GIỮ GIÁ CŨ"
-- =====================================================================
--  Giữ lại vì ba chỗ trỏ vào chúng: 03_queries.sql phần BONUS,
--  ReportPanel (chữ giải thích của báo cáo bonus), và sp_report_old_price_proof.
--  Nhưng từ khối 6 chúng KHÔNG CÒN là bằng chứng duy nhất: mốc đổi giá
--  @new_day nằm giữa cửa sổ 91 ngày, nên "giá cũ trên hóa đơn cũ" xảy ra ở
--  hàng trăm dòng mà người dùng thấy được trong tab 3 (plan/15 mục 3.2).
--
--  Tạo QUA THỦ TỤC như mọi hóa đơn khác, chỉ khác là TRUYỀN MÃ TƯỜNG MINH
--  thay vì gọi fn_next_invoice_id. Hàm đó bỏ qua hai mã này
--  (REGEXP '^INV-[0-9]+$' -- 04_routines.sql mục 1.4), nên INV-0001 vẫn là
--  hóa đơn đầu tiên của vòng lặp bên dưới.
--
--  Đơn giá KHÔNG gõ tay: fn_get_product_price('8934001', @old_day) trả về
--  28.000 vì mốc giá cũ nằm ĐÚNG ở @old_day và hàm so '<=' (biên bằng nhau
--  là có chủ ý -- nó kiểm luôn dấu '=' của điều kiện đó).
-- =====================================================================
SET @old_inv_date = DATE_SUB(CURDATE(), INTERVAL 400 DAY);

CALL sp_create_invoice_header('INV-OLD-01', @old_inv_date, '09:00:00', 'C01', 'E02', NULL);
CALL sp_add_invoice_line('INV-OLD-01', 1, '8934001', 2.000,
        fn_get_product_price('8934001', @old_inv_date),
        fn_line_discount('8934001', @old_inv_date, 2.000,
                         fn_get_product_price('8934001', @old_inv_date)));
CALL sp_add_invoice_line('INV-OLD-01', 2, '8934008', 1.000,
        fn_get_product_price('8934008', @old_inv_date),
        fn_line_discount('8934008', @old_inv_date, 1.000,
                         fn_get_product_price('8934008', @old_inv_date)));
-- Tien khach dua lam tron LEN toi to 10.000 gan nhat -> bat bien G4 dung
-- BOI CAU TAO (mot phep lam tron len khong the cho ra so nho hon).
SET @due = fn_invoice_amount_due('INV-OLD-01');
CALL sp_add_payment(fn_next_payment_id(), 'INV-OLD-01', @due, 'CASH',
                    CAST(CEILING(@due / 10000) * 10000 AS CHAR), NULL);
CALL sp_settle_invoice('INV-OLD-01');

CALL sp_create_invoice_header('INV-OLD-02', @old_inv_date, '15:30:00', 'C03', 'E03', NULL);
CALL sp_add_invoice_line('INV-OLD-02', 1, '8934006', 3.000,
        fn_get_product_price('8934006', @old_inv_date),
        fn_line_discount('8934006', @old_inv_date, 3.000,
                         fn_get_product_price('8934006', @old_inv_date)));
SET @due = fn_invoice_amount_due('INV-OLD-02');
CALL sp_add_payment(fn_next_payment_id(), 'INV-OLD-02', @due, 'CARD',
                    '4111-****-****-2468', 'AUTH8806');
CALL sp_settle_invoice('INV-OLD-02');


-- =====================================================================
--  VÒNG LẶP SINH HÓA ĐƠN
-- =====================================================================

DELIMITER $$

-- ---------------------------------------------------------------------
-- sp_seed_demo_data: sinh hoa don cho p_days ngay gan nhat.
--
--   KHONG phai mot thu tuc cua app: no duoc tao, goi, roi XOA ngay trong
--   file nay. Lam vay vi (a) app khong bao gio goi no nen no khong thuoc
--   hop dong nao trong docs/05 muc 6, va (b) de lai mot thu tuc SINH DU
--   LIEU trong database la mot cai bay: ai goi hai lan la du lieu nhan doi.
--
--   No KHONG INSERT truc tiep vao bay bang giao dich. Moi dong di qua dung
--   nhung thu tuc ma tab 1 di qua, va moi con so lay tu dung nhung ham ma
--   tab 1 goi -- plan/15 muc 3.1.
--
--   HOP DONG XAC DINH (plan/15 muc 3.5): KHONG CO RAND(), ke ca
--   RAND(hang so). Hai ly do: tinh xac dinh cua RAND(seed) khong duoc
--   MySQL bao dam giua CAC PHIEN BAN, va mot bieu thuc MOD thi DOC DUOC --
--   nguoi hoc thay ngay vi sao hoa don nay o quay C03. Hau qua: chay lai
--   seed hai lan trong cung mot ngay cho HAI DATABASE GIONG NHAU TUNG DONG
--   (kich ban #85 kiem bang CHECKSUM TABLE).
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_seed_demo_data(
    -- So ngay lui ve qua khu. 90 -> 91 ngay co hoa don (hom nay va 90
    -- ngay truoc). PHAI khop v_days trong sp_check_seed (quy tac R12):
    -- hai con so nay la MOT cam ket.
    IN p_days        INT,
    -- So hoa don moi ngay. 6 = 2 moi shop (co 6 quay, 2 quay moi shop),
    -- nen bang cheo cua bao cao cau 2 khong co o rong nao.
    -- CAN THAN KHI TANG: fn_next_invoice_id / fn_next_payment_id dung
    -- LPAD(..., 4, '0') nen tran la INV-9999 / PAY-9999. Cau hinh 90 x 6
    -- dung ~550 hoa don va ~700 thanh toan -- con xa tran. Nhung
    -- 365 x 20 la CHAM tran, va luc do sp_get_invoice_payments sap theo
    -- CHUOI (ORDER BY p.Payment_ID) nen PAY-10000 se dung TRUOC PAY-9999
    -- (plan/15 loi #22).
    IN p_inv_per_day INT
)
BEGIN
    DECLARE v_d, v_k, v_j, v_lines, v_ln  INT;
    DECLARE v_counters   INT;
    DECLARE v_id         VARCHAR(30);
    DECLARE v_pay_id     VARCHAR(20);
    DECLARE v_date       DATE;
    DECLARE v_time       TIME;
    DECLARE v_counter    VARCHAR(10);
    DECLARE v_cashier    VARCHAR(10);
    DECLARE v_customer   VARCHAR(15);
    DECLARE v_barcode    VARCHAR(20);
    DECLARE v_unit       VARCHAR(10);
    DECLARE v_price      DECIMAL(12,2);
    DECLARE v_qty        DECIMAL(10,3);
    DECLARE v_due        DECIMAL(12,2);
    DECLARE v_recorded   DECIMAL(12,2);
    DECLARE v_tendered   DECIMAL(12,2);
    DECLARE v_rest       DECIMAL(12,2);
    DECLARE v_method     VARCHAR(10);
    DECLARE v_status     VARCHAR(10);
    DECLARE v_line1_qty  DECIMAL(10,3);
    DECLARE v_ret_date   DATE;
    -- 1 cho DUNG MOT hoa don: INV-0001, hoa don dau tien cua vong lap.
    DECLARE v_sample     INT;

    SELECT COUNT(*) INTO v_counters FROM Counter;

    -- Di tu ngay CU NHAT den hom nay, de INV-0001 la hoa don cua ngay -90.
    SET v_d = p_days;
    WHILE v_d >= 0 DO
        SET v_date = DATE_SUB(CURDATE(), INTERVAL v_d DAY);

        SET v_k = 1;
        WHILE v_k <= p_inv_per_day DO

            -- =========================================================
            -- INV-0001 LA HOA DON MAU CUA CA DU AN, nen no la hoa don duy
            -- nhat trong ca vong lap co luat rieng. Ba cho ghim no:
            --   03_queries.sql CAU 1  -- "Chi tiet hoa don INV-0001"
            --   docs/03-SCHEMA.md     -- vi du weak entity
            --   04_routines.sql       -- vi du trong javadoc fn_return_refund
            -- Nen no phai thoa ba dieu kien, va ca ba duoc ep TUONG MINH o
            -- ba cho duoi (tim 'v_sample' la thay het):
            --   1. co >= 3 dong hang
            --   2. co >= 1 dong co giam gia  -> ep dong 1 la 8934001, SL 2
            --      (PR04 phu 8934001 o -90..-46, dung ngay cua hoa don nay)
            --   3. o trang thai 'PAID'       -> khong vao nhanh tra thieu,
            --      vi mot vi du ve TRA HANG tren mot hoa don 'OPEN' la vi du
            --      khong chay duoc (sp_add_return tu choi hoa don <> 'PAID')
            -- Kich ban #84 kiem dieu 1 va 2.
            SET v_sample = IF(v_d = p_days AND v_k = 1, 1, 0);

            -- =========================================================
            -- MOI LUA CHON "NGAU NHIEN" LA MOT BIEU THUC MOD (muc 3.5)
            -- =========================================================
            -- gio 07h..17h, phut 0..59
            SET v_time = MAKETIME(7 + MOD(v_d * 7 + v_k * 13, 11),
                                  MOD(v_d * 29 + v_k * 17, 60), 0);
            -- MOD(k-1, 6) + 1 voi k = 1..6: moi quay dung MOT hoa don moi
            -- ngay => moi SHOP dung HAI hoa don moi ngay.
            SET v_counter = CONCAT('C', LPAD(MOD(v_k - 1, v_counters) + 1, 2, '0'));
            SET v_cashier = CONCAT('E', LPAD(MOD(v_d + v_k, 5) + 1, 2, '0'));
            -- Mot phan ba hoa don la khach vang lai (Customer_ID NULL):
            -- cot Khach cua tab 3 phai co ca hai truong hop.
            SET v_customer = IF(MOD(v_d * 3 + v_k, 3) = 0, NULL,
                             CONCAT('KH', LPAD(MOD(v_d * 11 + v_k * 7, 30) + 1, 3, '0')));
            SET v_lines = 1 + MOD(v_d + v_k, 3);

            -- PHEP LECH CO CHU Y CHO BAO CAO CAU 4 (muc 4.5 buoc 9):
            -- chu ky cua MOD lam 5 thu ngan nhan gan nhu bang nhau, nen
            -- sp_report_best_cashier se cho 5 dong sat nut va khong ai
            -- biet no co sap dung hay khong. Mot dong code, va E03 dung
            -- dau RO RANG -- do CHU Y, khong do may man.
            IF v_cashier = 'E03' THEN
                SET v_lines = v_lines + 1;
            END IF;

            -- Dieu 1 cua hoa don mau: >= 3 dong hang
            IF v_sample = 1 AND v_lines < 3 THEN
                SET v_lines = 3;
            END IF;

            SET v_id = fn_next_invoice_id();
            -- Hoa don ra doi o 'OPEN'. sp_create_invoice_header KHONG nhan
            -- tham so trang thai: 'PAID' la KET LUAN cua sp_settle_invoice
            -- o buoc duoi, khong phai du lieu vao (plan/13 muc 2.2).
            CALL sp_create_invoice_header(v_id, v_date, v_time,
                                         v_counter, v_cashier, v_customer);

            -- --------------------------------------------------------
            -- Cac dong hang
            -- --------------------------------------------------------
            SET v_ln = 0;
            SET v_j  = 1;
            WHILE v_j <= v_lines DO
                SET v_barcode = CONCAT('89340',
                                LPAD(MOD(v_d * 3 + v_k * 7 + v_j * 11, 20) + 1, 2, '0'));

                -- Dieu 2 cua hoa don mau: dong 1 la 8934001 (san pham ma
                -- PR04 phu o -90..-46) voi SL = 2, de hoa don mau co giam
                -- gia va de vi du trong javadoc cua fn_return_refund co so
                -- tron. (SL duoc ep ngay sau khi tinh v_qty ben duoi.)
                IF v_sample = 1 AND v_j = 1 THEN
                    SET v_barcode = '8934001';
                END IF;

                -- BAT BIEN G1: don gia KHONG BAO GIO la hang so. Tra bang
                -- fn_get_product_price tai NGAY BAN -- dung ham ma tab 1 goi.
                SET v_price = fn_get_product_price(v_barcode, v_date);

                -- San pham chua co gia tai ngay do -> BO QUA dong nay,
                -- KHONG ghi 0. Mot dong hang gia 0 la mot con so sai, con
                -- mot dong hang khong ton tai thi khong noi doi gi.
                IF v_price IS NOT NULL THEN
                    SELECT Unit INTO v_unit FROM Product WHERE Barcode = v_barcode;

                    -- KG / LITER duoc ban theo so THAP PHAN that (0,5 /
                    -- 1,5), PIECE / PACK / BOX theo so nguyen. Nho vay cot
                    -- SL cua khoi 5 va Ui.qty co du lieu thap phan trai
                    -- khap 91 ngay, khong chi o mot hoa don.
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

                    SET v_ln = v_ln + 1;
                    -- BAT BIEN G2: tien giam cung KHONG phai hang so.
                    -- fn_line_discount goi fn_active_promo_pct tai ngay ban,
                    -- nen hoa don cu nhan khuyen mai cua ngay cu.
                    -- KHONG GOP DONG: hai dong cung ma vach la du lieu CO
                    -- ICH -- no bat hop thoai tra hang cua khoi 3 gap dung
                    -- truong hop no duoc sua de xu ly (plan/12 loi #9).
                    CALL sp_add_invoice_line(v_id, v_ln, v_barcode, v_qty, v_price,
                            fn_line_discount(v_barcode, v_date, v_qty, v_price));
                END IF;

                SET v_j = v_j + 1;
            END WHILE;

            -- --------------------------------------------------------
            -- Thanh toan -- doc SAU khi da ghi het dong hang
            -- --------------------------------------------------------
            SET v_due = fn_invoice_amount_due(v_id);

            IF v_ln > 0 AND v_due > 0 THEN
                IF MOD(v_d + v_k, 7) = 0 AND v_sample = 0 THEN
                    -- TRA THIEU CO CHU Y: ghi ~60% so phai thu. Hoa don
                    -- TU o lai 'OPEN' -- khong mot dong nao trong seed
                    -- UPDATE Invoice SET Status. ~78 hoa don 'OPEN' trai
                    -- deu 91 ngay, nen chip loc trang thai cua khoi 4 lan
                    -- dau co hang chuc dong thay vi 1 (plan/15 loi #12).
                    SET v_recorded = ROUND(v_due * 0.6, 2);
                    SET v_tendered = CEILING(v_recorded / 10000) * 10000;
                    CALL sp_add_payment(fn_next_payment_id(), v_id, v_recorded,
                                        'CASH', CAST(v_tendered AS CHAR), NULL);

                ELSEIF MOD(v_d + v_k, 5) = 0 THEN
                    -- HAI LUOT THANH TOAN cho mot hoa don: tien mat mot
                    -- phan, roi the hoac vi cho phan con thieu. Khung so 4
                    -- cua tab 1 mang ten "mot hoa don co the co NHIEU dong
                    -- thanh toan" va truoc khoi 6 seed co dung MOT vi du
                    -- (plan/15 loi #14).
                    SET v_recorded = ROUND(v_due * 0.4, 2);
                    SET v_tendered = CEILING(v_recorded / 10000) * 10000;
                    CALL sp_add_payment(fn_next_payment_id(), v_id, v_recorded,
                                        'CASH', CAST(v_tendered AS CHAR), NULL);

                    SET v_rest = v_due - v_recorded;
                    SET v_pay_id = fn_next_payment_id();
                    IF MOD(v_d + v_k, 2) = 0 THEN
                        CALL sp_add_payment(v_pay_id, v_id, v_rest, 'CARD',
                            CONCAT('4111-****-****-',
                                   LPAD(MOD(v_d * 97 + v_k * 13, 10000), 4, '0')),
                            CONCAT('AUTH', LPAD(MOD(v_d * 31 + v_k * 7, 10000), 4, '0')));
                    ELSE
                        CALL sp_add_payment(v_pay_id, v_id, v_rest, 'EWALLET',
                            ELT(MOD(v_d + v_k, 3) + 1, 'MoMo', 'ZaloPay', 'VNPay'),
                            -- Transaction_Ref PHAI duy nhat (uq_wallet_txn --
                            -- 01_schema.sql muc 15). Ghep ma thanh toan vao
                            -- la duy nhat BOI CAU TAO, vi Payment_ID la khoa
                            -- chinh cua bang Payment.
                            CONCAT('TXN-', v_pay_id));
                    END IF;

                ELSE
                    -- Mot luot, phuong thuc theo MOD -> ca ba bang con cua
                    -- Payment deu co hang tram dong.
                    SET v_method = ELT(MOD(v_d * 3 + v_k, 3) + 1, 'CASH', 'CARD', 'EWALLET');
                    SET v_pay_id = fn_next_payment_id();
                    IF v_method = 'CASH' THEN
                        -- TIEN KHACH DUA = lam tron LEN toi to 10.000 gan
                        -- nhat. Hai cai duoc cung luc: bat bien G4
                        -- (tendered >= recorded) dung BOI CAU TAO vi mot
                        -- phep lam tron LEN khong the cho ra so nho hon; va
                        -- tien thoi la con so THAT ma mot nguoi dua tien mat
                        -- se tao ra, nen cot "Tien thoi" cua khoi 5 co du
                        -- lieu that (plan/15 loi #13).
                        SET v_tendered = CEILING(v_due / 10000) * 10000;
                        CALL sp_add_payment(v_pay_id, v_id, v_due, 'CASH',
                                            CAST(v_tendered AS CHAR), NULL);
                    ELSEIF v_method = 'CARD' THEN
                        CALL sp_add_payment(v_pay_id, v_id, v_due, 'CARD',
                            CONCAT('5500-****-****-',
                                   LPAD(MOD(v_d * 89 + v_k * 11, 10000), 4, '0')),
                            CONCAT('AUTH', LPAD(MOD(v_d * 17 + v_k * 23, 10000), 4, '0')));
                    ELSE
                        CALL sp_add_payment(v_pay_id, v_id, v_due, 'EWALLET',
                            ELT(MOD(v_d * 2 + v_k, 3) + 1, 'MoMo', 'ZaloPay', 'VNPay'),
                            CONCAT('TXN-', v_pay_id));
                    END IF;
                END IF;

                -- 'PAID' NEU da thu du. Day la CHO DUY NHAT trong ca seed
                -- quyet dinh trang thai, va no quyet dinh bang cach DOC
                -- bang Payment -- bat bien G3 dung BOI CAU TAO.
                CALL sp_settle_invoice(v_id);
            END IF;

            SELECT Status INTO v_status FROM Invoice WHERE Invoice_ID = v_id;

            -- --------------------------------------------------------
            -- Huy hoa don (~15 hoa don VOIDED trai deu 91 ngay)
            -- Goi SAU sp_settle_invoice, nen hoa don bi huy la hoa don DA
            -- THU TIEN -- dung tinh huong nghiep vu ma khoi 4 mo ta:
            -- "huy khong xoa dau vet tien" (04_routines.sql muc 5.5).
            -- --------------------------------------------------------
            -- v_sample = 0: hoa don mau phai o lai 'PAID' (dieu 3). Khong co
            -- dieu kien nay thi dieu 3 chi dung TINH CO -- doi p_days la no
            -- co the roi vao nhanh huy.
            IF v_status = 'PAID' AND v_sample = 0
               AND MOD(v_d * 7 + v_k * 11, 37) = 0 THEN
                CALL sp_void_invoice(v_id);
                SET v_status = 'VOIDED';
            END IF;

            -- --------------------------------------------------------
            -- Tra hang
            -- sp_add_return la CHOT CUOI: no SIGNAL neu so luong sai, neu
            -- hoa don khong phai 'PAID', hoac neu tra qua so con lai. Tuc
            -- la SEED SAI THI SEED CHET NGAY, khong am tham sinh du lieu
            -- ban (plan/15 muc 4.2 chon autocommit de thay no chet o hoa
            -- don nao).
            -- Return_ID KHONG duoc truyen: sp_add_return goi
            -- fn_next_return_id. Nho vay goi HAI LAN cho cung mot dong la
            -- duoc 1 roi 2 -- cot Lan_tra cua khoi 3 lan dau hien so khac 1
            -- (plan/15 loi #4).
            -- --------------------------------------------------------
            IF v_status = 'PAID' AND v_ln > 0 AND MOD(v_d + v_k * 3, 12) = 0 THEN
                -- Ngay tra: sau ngay ban 0..2 ngay, KEP khong vuot hom nay
                -- -> bat bien G6 (R8) dung boi cau tao.
                SET v_ret_date = LEAST(DATE_ADD(v_date, INTERVAL MOD(v_k, 3) DAY), CURDATE());

                -- Hai dieu kien duoi day la MOD TREN v_d, nen chung GIAO voi
                -- dieu kien cua nhanh tra hang (MOD(v_d + 3*v_k, 12) = 0, chi
                -- dung khi v_d chia het 3). Vi the uoc so phai chon theo cai
                -- GIAO do, khong theo tan so cua rieng no: 7 -> v_d chia het
                -- 21 (5 ngay trong 91), 9 -> v_d chia het 9 (11 ngay).
                IF MOD(v_d, 7) = 0 THEN
                    -- Tra TOAN BO moi dong -> hoa don vao sp_report_big_returns
                    -- (> 50% gia tri). Line_Number lien tuc 1..v_ln vi v_ln
                    -- chi tang khi mot dong THAT SU duoc ghi.
                    SET v_j = 1;
                    WHILE v_j <= v_ln DO
                        CALL sp_add_return(v_id, v_j, v_ret_date,
                            (SELECT Quantity FROM Invoice_Line
                             WHERE Invoice_ID = v_id AND Line_Number = v_j), 'E01');
                        SET v_j = v_j + 1;
                    END WHILE;
                ELSE
                    -- Tra MOT PHAN dong 1 (sp_add_return tu sinh Return_ID = 1)
                    SELECT Quantity INTO v_line1_qty FROM Invoice_Line
                    WHERE Invoice_ID = v_id AND Line_Number = 1;
                    CALL sp_add_return(v_id, 1, v_ret_date,
                                       ROUND(v_line1_qty / 2, 3), 'E01');

                    IF MOD(v_d, 9) = 0 THEN
                        -- LAN THU HAI cho CUNG MOT dong -> Return_ID = 2.
                        -- SL = 1/4 dong goc, luon <= phan con lai (1/2).
                        CALL sp_add_return(v_id, 1, v_ret_date,
                                           ROUND(v_line1_qty / 4, 3), 'E01');
                    END IF;
                END IF;
            END IF;

            SET v_k = v_k + 1;
        END WHILE;

        SET v_d = v_d - 1;
    END WHILE;
END$$

DELIMITER ;


-- ---------------------------------------------------------------------
-- 91 ngay (hom nay va 90 ngay truoc) x 6 hoa don = khoang 550 hoa don.
--
-- KHONG BOC TRANSACTION cho ca lan sinh (plan/15 muc 4.2): sp_add_return
-- va sp_settle_invoice deu SIGNAL khi du lieu sai, nen mot lan nap that
-- bai giua duong la chuyen co that va DANG xay ra. De autocommit thi
-- thong diep loi di kem mot database dung lai DUNG O HOA DON GAY LOI --
-- SELECT COUNT(*) FROM Invoice la biet no chet o vong nao. Boc mot
-- transaction 8.000 lenh thi loi nao cung cho ra mot database rong va
-- khong mot dau vet nao ve cho sai. Khoi DON SACH o dau file lam cho
-- trang thai nua voi do vo hai: lan chay sau xoa sach roi lam lai.
-- ---------------------------------------------------------------------
CALL sp_seed_demo_data(90, 30);

-- XOA NGAY: de lai mot thu tuc SINH DU LIEU trong database la mot cai bay
-- (ai goi hai lan la du lieu nhan doi), va no khong thuoc hop dong nao
-- trong docs/05 muc 6 vi app khong bao gio goi no.
DROP PROCEDURE sp_seed_demo_data;


SELECT 'Da nap du lieu mau 91 ngay!'        AS ket_qua,
       (SELECT COUNT(*) FROM Invoice)       AS so_hoa_don,
       (SELECT COUNT(*) FROM Invoice_Line)  AS so_dong,
       (SELECT COUNT(*) FROM Payment)       AS so_thanh_toan,
       (SELECT COUNT(*) FROM `Return`)      AS so_luot_tra,
       (SELECT COUNT(*) FROM Product)       AS so_san_pham,
       (SELECT COUNT(*) FROM Customer)      AS so_khach,
       (SELECT COUNT(*) FROM Account)       AS so_taconi_khoan;

SELECT 'Kiem 14 bat bien: .\\scripts\\ps1\\check-seed.ps1 (bash: ./scripts/bash/check-seed.sh)' AS buoc_tiep;
