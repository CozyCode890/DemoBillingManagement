-- =====================================================================
--  02_seed.sql  --  Dữ liệu mẫu
--  Chạy bằng:  mysql -u root -p retail_billing < 02_seed.sql
-- =====================================================================
--  MẸO: các ngày ở đây được tính TƯƠNG ĐỐI so với hôm nay (CURDATE()),
--  nên dù bạn chạy vào lúc nào thì báo cáo "7 ngày gần nhất" vẫn có dữ liệu.
-- =====================================================================

USE retail_billing;

-- Biến tạm để ngày tháng luôn hợp lý:
--   @old_day  = một ngày của NĂM NGOÁI (giá cũ)
--   @new_day  = một ngày của NĂM NAY, và luôn <= hôm nay (giá mới)
SET @old_day = DATE_SUB(MAKEDATE(YEAR(CURDATE()), 1), INTERVAL 400 DAY);
SET @new_day = GREATEST(MAKEDATE(YEAR(CURDATE()), 1),
                        DATE_SUB(CURDATE(), INTERVAL 60 DAY));


-- ---------------------------------------------------------------------
-- Cửa hàng & quầy
-- ---------------------------------------------------------------------
INSERT INTO Shop (Shop_ID, Address) VALUES
('S01', '123 Nguyen Hue, Quan 1, TP.HCM'),
('S02', '45 Nguyen Van Linh, Quan 7, TP.HCM'),
('S03', '78 Vo Van Ngan, Thu Duc, TP.HCM');

INSERT INTO Counter (Counter_ID, Shop_ID) VALUES
('C01', 'S01'),
('C02', 'S01'),
('C03', 'S02'),
('C04', 'S03');


-- ---------------------------------------------------------------------
-- Thu ngân (E01 là quản lý, không có sếp -> Supervisor_ID = NULL)
-- ---------------------------------------------------------------------
INSERT INTO Cashier (Cashier_ID, Name, Supervisor_ID) VALUES
('E01', 'Tran Van An',    NULL),
('E02', 'Nguyen Thi Binh','E01'),
('E03', 'Le Minh Cuong',  'E01'),
('E04', 'Pham Thu Dung',  'E01'),
('E05', 'Vo Hoang Em',    'E01');


-- ---------------------------------------------------------------------
-- Khách hàng
-- ---------------------------------------------------------------------
INSERT INTO Customer (Customer_ID, Name, Phone) VALUES
('KH001', 'Hoang Van Kien', '0901000001'),
('KH002', 'Do Thi Lan',     '0901000002'),
('KH003', 'Bui Quoc Manh',  '0901000003'),
('KH004', 'Ngo Thi Nga',    '0901000004');


-- ---------------------------------------------------------------------
-- Sản phẩm  (KHÔNG có cột giá -- giá nằm ở Price_History)
-- ---------------------------------------------------------------------
INSERT INTO Product (Barcode, Name, Unit, Tax_Rate) VALUES
('8934001', 'Sua tuoi Vinamilk 1L',      'LITER',  8.00),
('8934002', 'Banh mi sandwich',          'PACK',   8.00),
('8934003', 'Gao ST25 tui 5kg',          'PACK',   5.00),
('8934004', 'Trung ga hop 10 qua',       'BOX',    5.00),
('8934005', 'Nuoc mam Nam Ngu 500ml',    'PIECE',  8.00),
('8934006', 'Dau an Neptune 1L',         'LITER',  8.00),
('8934007', 'Mi Hao Hao thung 30 goi',   'BOX',    8.00),
('8934008', 'Thit ba chi',               'KG',     5.00),
('8934009', 'Ca phe G7 hop 20 goi',      'BOX',    8.00),
('8934010', 'Nuoc ngot Coca 1.5L',       'PIECE', 10.00);


-- ---------------------------------------------------------------------
-- Lịch sử giá
--   5 sản phẩm tăng giá HƠN 10% trong năm nay: 001, 003, 006, 008, 010
--   3 sản phẩm tăng nhẹ dưới 10%:              002, 004, 007
--   2 sản phẩm chưa bao giờ đổi giá:           005, 009
-- ---------------------------------------------------------------------
INSERT INTO Price_History (Barcode, Valid_From_Date, Price) VALUES
-- giá cũ (năm ngoái)
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
-- giá mới (năm nay)
('8934001', @new_day,  33000.00),   -- +17.86%  <-- tang > 10%
('8934002', @new_day,  19000.00),   --  +5.56%
('8934003', @new_day, 175000.00),   -- +16.67%  <-- tang > 10%
('8934004', @new_day,  35000.00),   --  +9.38%
('8934006', @new_day,  62000.00),   -- +19.23%  <-- tang > 10%
('8934007', @new_day, 128000.00),   --  +6.67%
('8934008', @new_day, 158000.00),   -- +21.54%  <-- tang > 10%
('8934010', @new_day,  21000.00);   -- +16.67%  <-- tang > 10%


-- ---------------------------------------------------------------------
-- Khuyến mãi
-- ---------------------------------------------------------------------
INSERT INTO Promotion (Promo_ID, Rule_Description, Status, Expiry_Date) VALUES
('PR01', 'Giam 10% sua tuoi va nuoc ngot', 'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 30 DAY)),
('PR02', 'Giam 5% gao ST25',               'ACTIVE',  DATE_ADD(CURDATE(), INTERVAL 60 DAY)),
('PR03', 'Khuyen mai Tet (da ket thuc)',   'EXPIRED', DATE_SUB(CURDATE(), INTERVAL 100 DAY));

INSERT INTO Applies_To (Barcode, Promo_ID, Promo_Start_date, Promo_End_date, Discount_Percent) VALUES
('8934001', 'PR01', DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_ADD(CURDATE(), INTERVAL 15 DAY), 10.00),
('8934010', 'PR01', DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_ADD(CURDATE(), INTERVAL 15 DAY), 10.00),
('8934003', 'PR02', DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY),  5.00),
('8934007', 'PR03', DATE_SUB(CURDATE(), INTERVAL 130 DAY), DATE_SUB(CURDATE(), INTERVAL 100 DAY), 15.00);


-- ---------------------------------------------------------------------
-- Hóa đơn (phần đầu)
-- ---------------------------------------------------------------------
INSERT INTO Invoice (Invoice_ID, `Date`, `Time`, Status, Counter_ID, Cashier_ID, Customer_ID) VALUES
('INV-0001', CURDATE(),                              '08:15:00', 'PAID',   'C01', 'E02', 'KH001'),
('INV-0002', CURDATE(),                              '09:40:00', 'PAID',   'C03', 'E03', NULL),
('INV-0003', DATE_SUB(CURDATE(), INTERVAL 1 DAY),    '10:05:00', 'PAID',   'C01', 'E02', 'KH002'),
('INV-0004', DATE_SUB(CURDATE(), INTERVAL 1 DAY),    '16:20:00', 'PAID',   'C04', 'E04', NULL),
('INV-0005', DATE_SUB(CURDATE(), INTERVAL 2 DAY),    '11:30:00', 'PAID',   'C02', 'E05', 'KH001'),
('INV-0006', DATE_SUB(CURDATE(), INTERVAL 2 DAY),    '18:45:00', 'PAID',   'C03', 'E03', NULL),
('INV-0007', DATE_SUB(CURDATE(), INTERVAL 3 DAY),    '07:50:00', 'PAID',   'C01', 'E02', NULL),
('INV-0008', DATE_SUB(CURDATE(), INTERVAL 3 DAY),    '14:10:00', 'PAID',   'C04', 'E04', 'KH003'),
('INV-0009', DATE_SUB(CURDATE(), INTERVAL 4 DAY),    '12:00:00', 'PAID',   'C02', 'E05', 'KH004'),
('INV-0010', DATE_SUB(CURDATE(), INTERVAL 5 DAY),    '19:25:00', 'PAID',   'C03', 'E03', NULL),
('INV-0011', DATE_SUB(CURDATE(), INTERVAL 6 DAY),    '08:05:00', 'PAID',   'C01', 'E02', 'KH002'),
('INV-0012', DATE_SUB(CURDATE(), INTERVAL 6 DAY),    '17:35:00', 'PAID',   'C04', 'E04', NULL),
('INV-0013', DATE_SUB(CURDATE(), INTERVAL 2 DAY),    '13:15:00', 'PAID',   'C01', 'E02', 'KH003'),
('INV-0014', DATE_SUB(CURDATE(), INTERVAL 4 DAY),    '15:55:00', 'PAID',   'C03', 'E03', NULL),
('INV-0015', CURDATE(),                              '10:00:00', 'VOIDED', 'C01', 'E02', NULL),
-- Hai hóa đơn CŨ (năm ngoái) dùng GIÁ CŨ -- đây là minh chứng cho
-- yêu cầu "hóa đơn cũ phải giữ giá cũ".
('INV-OLD-01', DATE_SUB(CURDATE(), INTERVAL 400 DAY),'09:00:00', 'PAID',   'C01', 'E02', NULL),
('INV-OLD-02', DATE_SUB(CURDATE(), INTERVAL 400 DAY),'15:30:00', 'PAID',   'C03', 'E03', NULL);


-- ---------------------------------------------------------------------
-- Các dòng hóa đơn
--   Unit_Price là giá TẠI THỜI ĐIỂM bán (chép từ Price_History).
--   Discount là số tiền giảm của CẢ DÒNG (không phải %).
-- ---------------------------------------------------------------------
INSERT INTO Invoice_Line (Invoice_ID, Line_Number, Barcode, Quantity, Unit_Price, Discount) VALUES
-- INV-0001 : tong 135,100
('INV-0001', 1, '8934001', 2.000,  33000.00,  6600.00),
('INV-0001', 2, '8934002', 1.000,  19000.00,     0.00),
('INV-0001', 3, '8934010', 3.000,  21000.00,  6300.00),
-- INV-0002 : tong 307,000
('INV-0002', 1, '8934008', 1.500, 158000.00,     0.00),
('INV-0002', 2, '8934004', 2.000,  35000.00,     0.00),
-- INV-0003 : tong 322,250
('INV-0003', 1, '8934003', 1.000, 175000.00,  8750.00),
('INV-0003', 2, '8934009', 2.000,  78000.00,     0.00),
-- INV-0004 : tong 128,000
('INV-0004', 1, '8934007', 1.000, 128000.00,     0.00),
-- INV-0005 : tong 377,800
('INV-0005', 1, '8934006', 2.000,  62000.00,     0.00),
('INV-0005', 2, '8934005', 3.000,  45000.00,     0.00),
('INV-0005', 3, '8934001', 4.000,  33000.00, 13200.00),
-- INV-0006 : tong 95,000
('INV-0006', 1, '8934002', 5.000,  19000.00,     0.00),
-- INV-0007 : tong 113,400
('INV-0007', 1, '8934010', 6.000,  21000.00, 12600.00),
-- INV-0008 : tong 367,500
('INV-0008', 1, '8934003', 2.000, 175000.00, 17500.00),
('INV-0008', 2, '8934004', 1.000,  35000.00,     0.00),
-- INV-0009 : tong 78,000
('INV-0009', 1, '8934009', 1.000,  78000.00,     0.00),
-- INV-0010 : tong 378,000
('INV-0010', 1, '8934008', 2.000, 158000.00,     0.00),
('INV-0010', 2, '8934006', 1.000,  62000.00,     0.00),
-- INV-0011 : tong 90,000
('INV-0011', 1, '8934005', 2.000,  45000.00,     0.00),
-- INV-0012 : tong 313,000
('INV-0012', 1, '8934007', 2.000, 128000.00,     0.00),
('INV-0012', 2, '8934002', 3.000,  19000.00,     0.00),
-- INV-0013 : tong 316,000  (se bi tra lai TOAN BO)
('INV-0013', 1, '8934008', 2.000, 158000.00,     0.00),
-- INV-0014 : tong 231,700  (se bi tra lai ~75%)
('INV-0014', 1, '8934003', 1.000, 175000.00,     0.00),
('INV-0014', 2, '8934010', 3.000,  21000.00,  6300.00),
-- INV-0015 : hoa don bi HUY
('INV-0015', 1, '8934009', 1.000,  78000.00,     0.00),
-- Hoa don cu: chu y gia thap hon han (28,000 va 130,000 va 52,000)
('INV-OLD-01', 1, '8934001', 2.000,  28000.00,   0.00),
('INV-OLD-01', 2, '8934008', 1.000, 130000.00,   0.00),
('INV-OLD-02', 1, '8934006', 3.000,  52000.00,   0.00);


-- ---------------------------------------------------------------------
-- Trả hàng
-- ---------------------------------------------------------------------
INSERT INTO `Return` (Invoice_ID, Line_Number, Return_ID, Return_Date, Return_Quantity, Approved_By) VALUES
-- Tra 1/2 hop ca phe cua INV-0003 -> chi ~24% gia tri hoa don (KHONG qua nua)
('INV-0003', 2, 1, DATE_SUB(CURDATE(), INTERVAL 0 DAY), 1.000, 'E01'),
-- Tra TOAN BO thit cua INV-0013 -> 100% gia tri hoa don
('INV-0013', 1, 1, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 2.000, 'E01'),
-- Tra 1 tui gao cua INV-0014 -> ~75% gia tri hoa don
('INV-0014', 1, 1, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 1.000, 'E01');


-- ---------------------------------------------------------------------
-- Thanh toán
--   INV-0005 được trả bằng HAI phương thức (tiền mặt + thẻ).
-- ---------------------------------------------------------------------
INSERT INTO Payment (Payment_ID, Amount, Invoice_ID) VALUES
('PAY-0001', 135100.00, 'INV-0001'),
('PAY-0002', 307000.00, 'INV-0002'),
('PAY-0003', 322250.00, 'INV-0003'),
('PAY-0004', 128000.00, 'INV-0004'),
('PAY-0005', 200000.00, 'INV-0005'),   -- phan 1: tien mat
('PAY-0006', 177800.00, 'INV-0005'),   -- phan 2: the
('PAY-0007',  95000.00, 'INV-0006'),
('PAY-0008', 113400.00, 'INV-0007'),
('PAY-0009', 367500.00, 'INV-0008'),
('PAY-0010',  78000.00, 'INV-0009'),
('PAY-0011', 378000.00, 'INV-0010'),
('PAY-0012',  90000.00, 'INV-0011'),
('PAY-0013', 313000.00, 'INV-0012'),
('PAY-0014', 316000.00, 'INV-0013'),
('PAY-0015', 231700.00, 'INV-0014'),
('PAY-0016', 186000.00, 'INV-OLD-01'),
('PAY-0017', 156000.00, 'INV-OLD-02');

INSERT INTO Cash_Payment (Payment_ID, Tendered_Amount) VALUES
('PAY-0001', 150000.00),
('PAY-0004', 130000.00),
('PAY-0005', 200000.00),
('PAY-0007', 100000.00),
('PAY-0010',  80000.00),
('PAY-0012',  90000.00),
('PAY-0016', 200000.00);

INSERT INTO Card_Payment (Payment_ID, Card_number, Auth_code) VALUES
('PAY-0002', '4111-****-****-1234', 'AUTH8801'),
('PAY-0006', '5500-****-****-9911', 'AUTH8802'),
('PAY-0009', '4111-****-****-3321', 'AUTH8803'),
('PAY-0011', '4111-****-****-7777', 'AUTH8804'),
('PAY-0014', '5500-****-****-1010', 'AUTH8805'),
('PAY-0017', '4111-****-****-2468', 'AUTH8806');

INSERT INTO EWallet_Payment (Payment_ID, Wallet_provider, Transaction_Ref) VALUES
('PAY-0003', 'MoMo',     'MOMO-TXN-000001'),
('PAY-0008', 'ZaloPay',  'ZALO-TXN-000002'),
('PAY-0013', 'VNPay',    'VNPAY-TXN-000003'),
('PAY-0015', 'MoMo',     'MOMO-TXN-000004');


SELECT 'Da nap du lieu mau!' AS ket_qua,
       (SELECT COUNT(*) FROM Invoice)      AS so_hoa_don,
       (SELECT COUNT(*) FROM Invoice_Line) AS so_dong,
       (SELECT COUNT(*) FROM Product)      AS so_san_pham;
