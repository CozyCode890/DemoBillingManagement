-- =====================================================================
--  04_routines.sql  --  Tập hợp Hàm (Functions) và Thủ tục (Procedures)
--  Dành riêng cho DEV SQL quản lý và tối ưu hóa
--  Chạy bằng:  mysql -u root -p retail_billing < 04_routines.sql
-- =====================================================================
--  QUY ƯỚC CHUNG (CONTRACT VỚI DEV JAVA BACKEND):
--  1. Tiền tố hàm trả về giá trị đơn (Scalar Function): fn_<ten_ham>
--  2. Tiền tố thủ tục (Stored Procedure):             sp_<ten_thu_tuc>
--  3. Tiền tố tham số đầu vào:                        p_<ten_tham_so>
--  4. Tiền tố biến nội bộ:                            v_<ten_bien>
--  5. Tên cột trả về (Alias) trong câu SELECT phải giữ cố định vì Java
--     sẽ dùng đúng tên cột đó để ánh xạ vào Model/JTable.
-- =====================================================================

USE retail_billing;

-- Xóa các hàm và thủ tục cũ nếu đã tồn tại để tránh lỗi trùng lặp khi chạy lại
DROP FUNCTION IF EXISTS fn_get_product_price;
DROP FUNCTION IF EXISTS fn_next_invoice_id;
DROP FUNCTION IF EXISTS fn_next_payment_id;
DROP FUNCTION IF EXISTS fn_return_refund;
DROP FUNCTION IF EXISTS fn_next_return_id;
DROP FUNCTION IF EXISTS fn_invoice_amount_due;
DROP FUNCTION IF EXISTS fn_invoice_amount_paid;

DROP PROCEDURE IF EXISTS sp_lookup_counters;
DROP PROCEDURE IF EXISTS sp_lookup_cashiers;
DROP PROCEDURE IF EXISTS sp_lookup_customers;
DROP PROCEDURE IF EXISTS sp_get_all_products;
DROP PROCEDURE IF EXISTS sp_get_active_promo;

DROP PROCEDURE IF EXISTS sp_get_recent_invoices;
DROP PROCEDURE IF EXISTS sp_get_invoice_header;
DROP PROCEDURE IF EXISTS sp_get_invoice_lines;
DROP PROCEDURE IF EXISTS sp_get_invoice_payments;
DROP PROCEDURE IF EXISTS sp_get_invoice_returns;
DROP PROCEDURE IF EXISTS sp_get_returnable_lines;
DROP PROCEDURE IF EXISTS sp_get_invoice_edit_fields;

DROP PROCEDURE IF EXISTS sp_report_daily_takings;
DROP PROCEDURE IF EXISTS sp_report_price_rise;
DROP PROCEDURE IF EXISTS sp_report_best_cashier;
DROP PROCEDURE IF EXISTS sp_report_big_returns;
DROP PROCEDURE IF EXISTS sp_report_old_price_proof;

DROP PROCEDURE IF EXISTS sp_create_invoice_header;
DROP PROCEDURE IF EXISTS sp_add_invoice_line;
DROP PROCEDURE IF EXISTS sp_add_payment;
DROP PROCEDURE IF EXISTS sp_settle_invoice;
DROP PROCEDURE IF EXISTS sp_void_invoice;
DROP PROCEDURE IF EXISTS sp_add_return;
DROP PROCEDURE IF EXISTS sp_delete_return;

DROP PROCEDURE IF EXISTS sp_authenticate_account;
DROP PROCEDURE IF EXISTS sp_delete_invoice;
DROP PROCEDURE IF EXISTS sp_update_invoice_header;
DROP PROCEDURE IF EXISTS sp_update_invoice_line;
DROP PROCEDURE IF EXISTS sp_delete_invoice_line;

DELIMITER $$

-- =====================================================================
-- PHẦN 1: HÀM SQL (STORED FUNCTIONS - TRẢ VỀ GIÁ TRỊ ĐƠN)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1.1 fn_get_product_price: Tìm giá hợp lệ của sản phẩm tại một ngày
--     (Trái tim của bài toán lịch sử giá)
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_get_product_price(
    p_barcode VARCHAR(20),
    p_date    DATE
)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_price DECIMAL(12,2) DEFAULT NULL;

    SELECT Price INTO v_price
    FROM   Price_History
    WHERE  Barcode = p_barcode
      AND  Valid_From_Date <= p_date
    ORDER BY Valid_From_Date DESC
    LIMIT 1;

    RETURN v_price;
END$$


-- ---------------------------------------------------------------------
-- 1.2 fn_next_invoice_id: Tự động sinh mã hóa đơn tiếp theo (INV-xxxx)
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_next_invoice_id()
RETURNS VARCHAR(30)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_max_seq INT DEFAULT 0;

    SELECT COALESCE(MAX(CAST(SUBSTRING(Invoice_ID, 5) AS UNSIGNED)), 0)
    INTO   v_max_seq
    FROM   Invoice
    WHERE  Invoice_ID REGEXP '^INV-[0-9]+$';

    RETURN CONCAT('INV-', LPAD(v_max_seq + 1, 4, '0'));
END$$


-- ---------------------------------------------------------------------
-- 1.3 fn_next_payment_id: Tự động sinh mã thanh toán tiếp theo (PAY-xxxx)
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_next_payment_id()
RETURNS VARCHAR(20)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_max_seq INT DEFAULT 0;

    SELECT COALESCE(MAX(CAST(SUBSTRING(Payment_ID, 5) AS UNSIGNED)), 0)
    INTO   v_max_seq
    FROM   Payment
    WHERE  Payment_ID REGEXP '^PAY-[0-9]+$';

    RETURN CONCAT('PAY-', LPAD(v_max_seq + 1, 4, '0'));
END$$


-- ---------------------------------------------------------------------
-- 1.4 fn_return_refund: Tien hoan lai cho p_quantity don vi cua MOT dong hoa don
--     Tra bao nhieu phan cua dong thi hoan lai dung bay nhieu phan tien cua dong
--     (giam gia cua dong cung duoc chia theo ty le do).
--     DAY LA BAN DUY NHAT CUA CONG THUC NAY -- xem plan/12 muc 3.1.
--     sp_get_invoice_returns va sp_report_big_returns deu goi vao day.
--
--     Luu y cho nguoi hoc: tien hoan KHONG bang SL_tra * Don_gia khi dong co
--     giam gia. Vi du INV-0001 dong 1 (mua 2, don gia 33.000, giam 6.600):
--     tra 1 don vi hoan 29.700 chu khong phai 26.400.
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_return_refund(
    p_invoice_id  VARCHAR(30),
    p_line_number INT,
    p_quantity    DECIMAL(10,3)
)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_refund DECIMAL(12,2) DEFAULT NULL;

    SELECT ROUND(p_quantity / l.Quantity
                 * (l.Quantity * l.Unit_Price - l.Discount), 2)
    INTO   v_refund
    FROM   Invoice_Line l
    WHERE  l.Invoice_ID  = p_invoice_id
      AND  l.Line_Number = p_line_number;

    -- Khong tim thay dong: tra 0 thay vi NULL, de SUM() o bao cao khong bi
    -- NULL hoa ca nhom.
    RETURN COALESCE(v_refund, 0.00);
END$$


-- ---------------------------------------------------------------------
-- 1.5 fn_next_return_id: So thu tu lan tra ke tiep CUA MOT DONG hoa don
--     Khoa chinh bang `Return` la (Invoice_ID, Line_Number, Return_ID) nen
--     Return_ID chi can duy nhat trong pham vi mot dong -- dung nhu seed dang
--     danh so (moi dong deu bat dau tu 1). AUTO_INCREMENT khong lam duoc viec nay.
--
--     MAX + 1 an toan VI sp_add_return chi goi ham nay SAU khi da khoa dong
--     Invoice_Line bang FOR UPDATE.
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_next_return_id(
    p_invoice_id  VARCHAR(30),
    p_line_number INT
)
RETURNS INT
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_max INT DEFAULT 0;

    SELECT COALESCE(MAX(Return_ID), 0)
    INTO   v_max
    FROM   `Return`
    WHERE  Invoice_ID  = p_invoice_id
      AND  Line_Number = p_line_number;

    RETURN v_max + 1;
END$$


-- ---------------------------------------------------------------------
-- 1.6 fn_invoice_amount_due: Tong so tien hoa don PHAI thu
--     Dung dung cong thuc cua sp_get_invoice_header (Tong_phai_tra).
--     Hoa don khong co dong hang nao -> tra 0.00 (khong phai NULL), nen
--     NGUOI GOI phai tu kiem "co dong hang hay khong" truoc khi so sanh:
--     0 >= 0 la TRUE, va mot hoa don rong KHONG bao gio duoc coi la da thu
--     du (plan/13 muc 3.4 diem 2).
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_invoice_amount_due(
    p_invoice_id VARCHAR(30)
)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_due DECIMAL(12,2) DEFAULT NULL;

    SELECT SUM(l.Quantity * l.Unit_Price - l.Discount)
    INTO   v_due
    FROM   Invoice_Line l
    WHERE  l.Invoice_ID = p_invoice_id;

    RETURN COALESCE(v_due, 0.00);
END$$


-- ---------------------------------------------------------------------
-- 1.7 fn_invoice_amount_paid: Tong so tien DA GHI NHAN cho hoa don
--     Payment.Amount la so tien ghi nhan, KHONG phai tien khach dua
--     (tien khach dua nam o Cash_Payment.Tendered_Amount).
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_invoice_amount_paid(
    p_invoice_id VARCHAR(30)
)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_paid DECIMAL(12,2) DEFAULT NULL;

    SELECT SUM(p.Amount)
    INTO   v_paid
    FROM   Payment p
    WHERE  p.Invoice_ID = p_invoice_id;

    RETURN COALESCE(v_paid, 0.00);
END$$


-- =====================================================================
-- PHẦN 2: THỦ TỤC DANH MỤC & TRA CỨU (LOOKUP & PRODUCTS)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 2.1 sp_lookup_counters: Danh sách quầy thu ngân kèm địa chỉ shop
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_lookup_counters()
BEGIN
    SELECT c.Counter_ID,
           CONCAT(s.Shop_ID, ' - ', s.Address) AS Mo_ta
    FROM   Counter c
    JOIN   Shop    s ON s.Shop_ID = c.Shop_ID
    ORDER BY c.Counter_ID;
END$$


-- ---------------------------------------------------------------------
-- 2.2 sp_lookup_cashiers: Danh sách thu ngân
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_lookup_cashiers()
BEGIN
    SELECT Cashier_ID, Name
    FROM   Cashier
    ORDER BY Cashier_ID;
END$$


-- ---------------------------------------------------------------------
-- 2.3 sp_lookup_customers: Danh sách khách hàng thành viên
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_lookup_customers()
BEGIN
    SELECT Customer_ID,
           CONCAT(Name, ' - ', Phone) AS Mo_ta
    FROM   Customer
    ORDER BY Customer_ID;
END$$


-- ---------------------------------------------------------------------
-- 2.4 sp_get_all_products: Lấy toàn bộ sản phẩm để đổ vào danh sách chọn
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_get_all_products()
BEGIN
    SELECT Barcode, Name, Unit, Tax_Rate
    FROM   Product
    ORDER BY Name;
END$$


-- ---------------------------------------------------------------------
-- 2.5 sp_get_active_promo: Tra cứu chương trình khuyến mãi tốt nhất
--     đang áp dụng cho sản phẩm tại một ngày cụ thể
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_get_active_promo(
    IN p_barcode VARCHAR(20),
    IN p_date    DATE
)
BEGIN
    SELECT a.Promo_ID, a.Discount_Percent, p.Rule_Description
    FROM   Applies_To a
    JOIN   Promotion  p ON p.Promo_ID = a.Promo_ID
    WHERE  a.Barcode = p_barcode
      AND  p_date BETWEEN a.Promo_Start_date AND a.Promo_End_date
      AND  p.Status = 'ACTIVE'
      AND  p.Expiry_Date >= p_date
    ORDER BY a.Discount_Percent DESC
    LIMIT 1;
END$$


-- =====================================================================
-- PHẦN 3: THỦ TỤC XEM THÔNG TIN HÓA ĐƠN
-- =====================================================================

-- ---------------------------------------------------------------------
-- 3.1 sp_get_recent_invoices: Danh sách hóa đơn kèm tổng tiền
--     LEFT JOIN Invoice_Line (khong phai JOIN) + COALESCE: hoa don chua co
--     dong hang nao van PHAI hien ra, voi Tong_tien = 0. Ban cu dung inner
--     join nen hoa don bi xoa het dong hang bien mat khoi bang -> khong chon
--     duoc -> khong sua va khong xoa duoc bang giao dien (plan/13 loi #9).
--     Tu khoi 4, 'OPEN' la trang thai thuc su nen day la duong di binh thuong.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_get_recent_invoices()
BEGIN
    SELECT  i.Invoice_ID                                  AS Ma_HD,
            i.`Date`                                      AS Ngay,
            i.`Time`                                      AS Gio,
            s.Shop_ID                                     AS Shop,
            c.Name                                        AS Thu_ngan,
            COALESCE(cu.Name, '(vang lai)')               AS Khach,
            i.Status                                      AS Trang_thai,
            COALESCE(SUM(l.Quantity * l.Unit_Price - l.Discount), 0) AS Tong_tien
    FROM    Invoice      i
    JOIN    Counter      ct ON ct.Counter_ID  = i.Counter_ID
    JOIN    Shop         s  ON s.Shop_ID      = ct.Shop_ID
    JOIN    Cashier      c  ON c.Cashier_ID   = i.Cashier_ID
    LEFT JOIN Customer   cu ON cu.Customer_ID = i.Customer_ID
    LEFT JOIN Invoice_Line l ON l.Invoice_ID  = i.Invoice_ID
    GROUP BY i.Invoice_ID, i.`Date`, i.`Time`, s.Shop_ID,
             c.Name, cu.Name, i.Status
    ORDER BY i.`Date` DESC, i.`Time` DESC;
END$$


-- ---------------------------------------------------------------------
-- 3.2 sp_get_invoice_header: Phần đầu và các tổng tiền của một hóa đơn
--     LEFT JOIN Invoice_Line + COALESCE tren CA BA cot tong: hoa don chua co
--     dong hang nao van tra ve 1 dong voi cac tong bang 0, thay vi khong tra
--     dong nao (plan/13 loi #9). Thieu COALESCE o day thi
--     InvoiceViewPanel.showDetail nhan NULL cho Tong_phai_tra.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_get_invoice_header(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    SELECT  i.Invoice_ID                                  AS Ma_HD,
            i.`Date`                                      AS Ngay,
            i.`Time`                                      AS Gio,
            i.Status                                      AS Trang_thai,
            i.Counter_ID                                  AS Quay,
            s.Shop_ID                                     AS Shop,
            s.Address                                     AS Dia_chi,
            c.Name                                        AS Thu_ngan,
            COALESCE(cu.Name, '(khach vang lai)')         AS Khach_hang,
            COALESCE(SUM(l.Quantity * l.Unit_Price), 0)              AS Tong_truoc_giam,
            COALESCE(SUM(l.Discount), 0)                             AS Tong_giam,
            COALESCE(SUM(l.Quantity * l.Unit_Price - l.Discount), 0) AS Tong_phai_tra
    FROM    Invoice      i
    JOIN    Counter      ct ON ct.Counter_ID  = i.Counter_ID
    JOIN    Shop         s  ON s.Shop_ID      = ct.Shop_ID
    JOIN    Cashier      c  ON c.Cashier_ID   = i.Cashier_ID
    LEFT JOIN Customer   cu ON cu.Customer_ID = i.Customer_ID
    LEFT JOIN Invoice_Line l ON l.Invoice_ID  = i.Invoice_ID
    WHERE   i.Invoice_ID = p_invoice_id
    GROUP BY i.Invoice_ID, i.`Date`, i.`Time`, i.Status, i.Counter_ID,
             s.Shop_ID, s.Address, c.Name, cu.Name;
END$$


-- ---------------------------------------------------------------------
-- 3.3 sp_get_invoice_lines: Các dòng sản phẩm chi tiết của hóa đơn
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_get_invoice_lines(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    SELECT  l.Line_Number                            AS Dong,
            p.Name                                   AS San_pham,
            l.Quantity                               AS SL,
            p.Unit                                   AS DVT,
            l.Unit_Price                             AS Don_gia,
            l.Discount                               AS Giam_gia,
            (l.Quantity * l.Unit_Price - l.Discount) AS Thanh_tien,
            p.Tax_Rate                               AS VAT_pct
    FROM    Invoice_Line l
    JOIN    Product      p ON p.Barcode = l.Barcode
    WHERE   l.Invoice_ID = p_invoice_id
    ORDER BY l.Line_Number;
END$$


-- ---------------------------------------------------------------------
-- 3.4 sp_get_invoice_payments: Chi tiết các đợt thanh toán của hóa đơn
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_get_invoice_payments(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    SELECT  p.Payment_ID            AS Ma_TT,
            p.Amount                AS So_tien,
            CASE
              WHEN ca.Payment_ID IS NOT NULL THEN 'Tien mat'
              WHEN cd.Payment_ID IS NOT NULL THEN 'The ngan hang'
              WHEN ew.Payment_ID IS NOT NULL THEN 'Vi dien tu'
              ELSE '(khong ro)' END AS Phuong_thuc,
            CASE
              WHEN ca.Payment_ID IS NOT NULL THEN CONCAT('Khach dua: ', ca.Tendered_Amount)
              WHEN cd.Payment_ID IS NOT NULL THEN CONCAT(cd.Card_number, ' / ', cd.Auth_code)
              WHEN ew.Payment_ID IS NOT NULL THEN CONCAT(ew.Wallet_provider, ' / ', ew.Transaction_Ref)
              ELSE '' END           AS Chi_tiet
    FROM    Payment p
    LEFT JOIN Cash_Payment    ca ON ca.Payment_ID = p.Payment_ID
    LEFT JOIN Card_Payment    cd ON cd.Payment_ID = p.Payment_ID
    LEFT JOIN EWallet_Payment ew ON ew.Payment_ID = p.Payment_ID
    WHERE   p.Invoice_ID = p_invoice_id
    ORDER BY p.Payment_ID;
END$$


-- ---------------------------------------------------------------------
-- 3.5 sp_get_invoice_returns: Các lần trả hàng của hóa đơn
--     Cot Lan_tra (Return_ID) de phan biet cac luot tra cua CUNG MOT dong.
--     Doi thu tu / so luong cot o day la doi hop dong voi Java:
--     InvoiceViewPanel.fillFormatted(tbReturns, ...) -- plan/12 muc 3.5.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_get_invoice_returns(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    SELECT  r.Line_Number                       AS Dong,
            r.Return_ID                         AS Lan_tra,
            pr.Name                             AS San_pham,
            r.Return_Date                       AS Ngay_tra,
            r.Return_Quantity                   AS SL_tra,
            fn_return_refund(r.Invoice_ID, r.Line_Number, r.Return_Quantity) AS Tien_hoan,
            c.Name                              AS Nguoi_duyet
    FROM    `Return` r
    JOIN    Invoice_Line l ON l.Invoice_ID = r.Invoice_ID
                          AND l.Line_Number = r.Line_Number
    JOIN    Product pr ON pr.Barcode   = l.Barcode
    JOIN    Cashier c  ON c.Cashier_ID = r.Approved_By
    WHERE   r.Invoice_ID = p_invoice_id
    ORDER BY r.Line_Number, r.Return_ID;
END$$


-- ---------------------------------------------------------------------
-- 3.6 sp_get_returnable_lines: Cac dong cua hoa don kem SO LUONG CON TRA DUOC
--     Ten cot la hop dong voi Java (plan/12 muc 5.6) -- doi ten phai sua ca hai ben.
--     LEFT JOIN (khong phai JOIN) de dong chua bi tra lan nao van hien ra
--     voi SL_da_tra = 0.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_get_returnable_lines(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    SELECT  l.Line_Number                                      AS Dong,
            p.Name                                             AS San_pham,
            p.Unit                                             AS DVT,
            l.Quantity                                         AS SL_mua,
            COALESCE(SUM(r.Return_Quantity), 0)                AS SL_da_tra,
            l.Quantity - COALESCE(SUM(r.Return_Quantity), 0)   AS SL_con_lai,
            l.Unit_Price                                       AS Don_gia,
            l.Discount                                         AS Giam_gia,
            (l.Quantity * l.Unit_Price - l.Discount)           AS Thanh_tien
    FROM    Invoice_Line l
    JOIN    Product      p ON p.Barcode = l.Barcode
    LEFT JOIN `Return`   r ON r.Invoice_ID  = l.Invoice_ID
                          AND r.Line_Number = l.Line_Number
    WHERE   l.Invoice_ID = p_invoice_id
    GROUP BY l.Line_Number, p.Name, p.Unit,
             l.Quantity, l.Unit_Price, l.Discount
    ORDER BY l.Line_Number;
END$$


-- ---------------------------------------------------------------------
-- 3.7 sp_get_invoice_edit_fields: Cac truong THO cua mot hoa don, dung cho
--     hop thoai sua (EditInvoiceDialog).
--     Khac sp_get_invoice_header: tra ve MA (Counter_ID, Cashier_ID,
--     Customer_ID) chu khong phai TEN, va KHONG join Invoice_Line nen hoa
--     don chua co dong hang nao van doc duoc.
--     Thu tu 7 cot la hop dong voi Java (InvoiceViewPanel doc theo chi so
--     r[1]..r[6]) -- plan/13 muc 3.5.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_get_invoice_edit_fields(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    SELECT  i.Invoice_ID   AS Ma_HD,
            i.`Date`       AS Ngay,
            i.`Time`       AS Gio,
            i.Status       AS Trang_thai,
            i.Counter_ID   AS Quay,
            i.Cashier_ID   AS Thu_ngan,
            i.Customer_ID  AS Khach
    FROM    Invoice i
    WHERE   i.Invoice_ID = p_invoice_id;
END$$


-- =====================================================================
-- PHẦN 4: THỦ TỤC BÁO CÁO (REPORTS)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 4.1 sp_report_daily_takings: Doanh thu theo ngày của từng shop (7 ngày)
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_report_daily_takings()
BEGIN
    SELECT  s.Shop_ID                                     AS Cua_hang,
            s.Address                                     AS Dia_chi,
            i.`Date`                                      AS Ngay,
            COUNT(DISTINCT i.Invoice_ID)                  AS So_hoa_don,
            SUM(l.Quantity * l.Unit_Price - l.Discount)   AS Doanh_thu
    FROM    Invoice      i
    JOIN    Counter      ct ON ct.Counter_ID = i.Counter_ID
    JOIN    Shop         s  ON s.Shop_ID     = ct.Shop_ID
    JOIN    Invoice_Line l  ON l.Invoice_ID  = i.Invoice_ID
    WHERE   i.Status = 'PAID'
      AND   i.`Date` >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
    GROUP BY s.Shop_ID, s.Address, i.`Date`
    ORDER BY i.`Date` DESC, s.Shop_ID;
END$$


-- ---------------------------------------------------------------------
-- 4.2 sp_report_price_rise: Sản phẩm tăng giá > 10% trong năm nay
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_report_price_rise()
BEGIN
    SELECT  p.Barcode                                                      AS Ma_vach,
            p.Name                                                         AS San_pham,
            gia_cu.Price                                                   AS Gia_cu,
            gia_moi.Price                                                  AS Gia_moi,
            gia_moi.Valid_From_Date                                        AS Ap_dung_tu,
            ROUND((gia_moi.Price - gia_cu.Price) / gia_cu.Price * 100, 2)  AS Tang_phan_tram
    FROM       Price_History gia_moi
    JOIN       Price_History gia_cu
            ON gia_cu.Barcode = gia_moi.Barcode
           AND gia_cu.Valid_From_Date = (
                   SELECT MAX(h.Valid_From_Date)
                   FROM   Price_History h
                   WHERE  h.Barcode = gia_moi.Barcode
                     AND  h.Valid_From_Date < gia_moi.Valid_From_Date)
    JOIN       Product p ON p.Barcode = gia_moi.Barcode
    WHERE   YEAR(gia_moi.Valid_From_Date) = YEAR(CURDATE())
      AND   gia_moi.Price > gia_cu.Price * 1.10
    ORDER BY Tang_phan_tram DESC;
END$$


-- ---------------------------------------------------------------------
-- 4.3 sp_report_best_cashier: Thu ngân có giỏ hàng trung bình cao nhất
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_report_best_cashier()
BEGIN
    SELECT  c.Cashier_ID           AS Ma_TN,
            c.Name                 AS Ten_thu_ngan,
            COUNT(*)               AS So_hoa_don,
            ROUND(SUM(t.Tong), 2)  AS Tong_doanh_thu,
            ROUND(AVG(t.Tong), 2)  AS Gio_hang_TB
    FROM    Cashier c
    JOIN    Invoice i ON i.Cashier_ID = c.Cashier_ID AND i.Status = 'PAID'
    JOIN   (SELECT Invoice_ID,
                   SUM(Quantity * Unit_Price - Discount) AS Tong
            FROM   Invoice_Line
            GROUP BY Invoice_ID) t  ON t.Invoice_ID = i.Invoice_ID
    GROUP BY c.Cashier_ID, c.Name
    ORDER BY Gio_hang_TB DESC;
END$$


-- ---------------------------------------------------------------------
-- 4.4 sp_report_big_returns: Hóa đơn bị trả hàng quá 50% giá trị
--     Tien hoan goi fn_return_refund -- khong chep lai cong thuc
--     (plan/12 muc 2.2). JOIN Invoice_Line trong bang dan xuat bo duoc
--     vi ham da tu tra Invoice_Line.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_report_big_returns()
BEGIN
    SELECT  i.Invoice_ID                                 AS Ma_HD,
            i.`Date`                                     AS Ngay,
            tong.Tong_HD                                 AS Tong_hoa_don,
            ROUND(tra.Tien_tra, 2)                       AS Tien_tra_lai,
            ROUND(tra.Tien_tra / tong.Tong_HD * 100, 2)  AS Phan_tram_tra
    FROM    Invoice i
    JOIN   (SELECT Invoice_ID,
                   SUM(Quantity * Unit_Price - Discount) AS Tong_HD
            FROM   Invoice_Line
            GROUP BY Invoice_ID) tong  ON tong.Invoice_ID = i.Invoice_ID
    JOIN   (SELECT r.Invoice_ID,
                   SUM(fn_return_refund(r.Invoice_ID, r.Line_Number, r.Return_Quantity)) AS Tien_tra
            FROM   `Return` r
            GROUP BY r.Invoice_ID) tra ON tra.Invoice_ID = i.Invoice_ID
    WHERE   tra.Tien_tra > tong.Tong_HD / 2
    ORDER BY Phan_tram_tra DESC;
END$$


-- ---------------------------------------------------------------------
-- 4.5 sp_report_old_price_proof: Chứng minh hóa đơn cũ giữ giá cũ
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_report_old_price_proof()
BEGIN
    SELECT  l.Invoice_ID     AS Ma_HD,
            i.`Date`         AS Ngay_ban,
            p.Name           AS San_pham,
            l.Unit_Price     AS Gia_ghi_tren_HD,
            (SELECT h.Price
             FROM   Price_History h
             WHERE  h.Barcode = l.Barcode
               AND  h.Valid_From_Date <= CURDATE()
             ORDER BY h.Valid_From_Date DESC
             LIMIT 1)        AS Gia_hom_nay
    FROM    Invoice_Line l
    JOIN    Invoice i ON i.Invoice_ID = l.Invoice_ID
    JOIN    Product p ON p.Barcode    = l.Barcode
    ORDER BY i.`Date`, l.Invoice_ID, l.Line_Number;
END$$


-- =====================================================================
-- PHẦN 5: THỦ TỤC GHI DỮ LIỆU (INSERT TRONG TRANSACTION)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 5.1 sp_create_invoice_header: Tạo thông tin đầu hóa đơn
--     Hoa don MOI LUON la 'OPEN' -- chua thu du tien thi chua PAID.
--     Chi sp_settle_invoice moi duoc chuyen sang 'PAID', va no tu doc bang
--     Payment de quyet dinh (plan/13 muc 2.2). Dung ghi 'PAID' o day.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_create_invoice_header(
    IN p_invoice_id  VARCHAR(30),
    IN p_date        DATE,
    IN p_time        TIME,
    IN p_counter_id  VARCHAR(10),
    IN p_cashier_id  VARCHAR(10),
    IN p_customer_id VARCHAR(15)
)
BEGIN
    INSERT INTO Invoice (Invoice_ID, `Date`, `Time`, Status, Counter_ID, Cashier_ID, Customer_ID)
    VALUES (p_invoice_id, p_date, p_time, 'OPEN', p_counter_id, p_cashier_id, p_customer_id);
END$$


-- ---------------------------------------------------------------------
-- 5.2 sp_add_invoice_line: Thêm một dòng sản phẩm vào hóa đơn
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_add_invoice_line(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT,
    IN p_barcode     VARCHAR(20),
    IN p_quantity    DECIMAL(10,3),
    IN p_unit_price  DECIMAL(12,2),
    IN p_discount    DECIMAL(12,2)
)
BEGIN
    INSERT INTO Invoice_Line (Invoice_ID, Line_Number, Barcode, Quantity, Unit_Price, Discount)
    VALUES (p_invoice_id, p_line_number, p_barcode, p_quantity, p_unit_price, p_discount);
END$$


-- ---------------------------------------------------------------------
-- 5.3 sp_add_payment: Ghi thông tin thanh toán & tự động rẽ nhánh
--     vào bảng con (Cash_Payment, Card_Payment, EWallet_Payment)
--     Đây là ví dụ điển hình SQL Dev che giấu cấu trúc bảng con cho Java!
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_add_payment(
    IN p_payment_id VARCHAR(20),
    IN p_invoice_id VARCHAR(30),
    IN p_amount     DECIMAL(12,2),
    IN p_method     VARCHAR(20),
    IN p_detail1    VARCHAR(100),
    IN p_detail2    VARCHAR(100)
)
BEGIN
    -- 1. Ghi vào bảng cha Payment
    INSERT INTO Payment (Payment_ID, Amount, Invoice_ID)
    VALUES (p_payment_id, p_amount, p_invoice_id);

    -- 2. Dựa vào phương thức, rẽ nhánh ghi vào đúng bảng con
    IF p_method = 'CASH' THEN
        INSERT INTO Cash_Payment (Payment_ID, Tendered_Amount)
        VALUES (p_payment_id, CAST(p_detail1 AS DECIMAL(12,2)));

    ELSEIF p_method = 'CARD' THEN
        INSERT INTO Card_Payment (Payment_ID, Card_number, Auth_code)
        VALUES (p_payment_id, p_detail1, p_detail2);

    ELSEIF p_method = 'EWALLET' THEN
        INSERT INTO EWallet_Payment (Payment_ID, Wallet_provider, Transaction_Ref)
        VALUES (p_payment_id, p_detail1, p_detail2);
    END IF;
END$$


-- ---------------------------------------------------------------------
-- 5.4 sp_settle_invoice: Chuyen hoa don sang 'PAID' NEU da thu du tien
--     Java goi thu tuc nay o cuoi transaction lap hoa don, TRUOC commit,
--     tren cung mot Connection (InvoiceDao.createInvoice) -- dung nhu
--     sp_add_invoice_line va sp_add_payment.
--
--     KHONG tra ResultSet: Java goi bang cs.executeUpdate(), va
--     Connector/J nem loi "Can not issue SELECT via executeUpdate()" neu
--     thu tuc tra ve bang. Muon biet trang thai sau khi chay thi doc lai
--     bang sp_get_invoice_edit_fields.
--
--     Khong thu du tien -> KHONG loi, chi la khong chuyen (hoa don o lai
--     'OPEN'). Tra thieu la mot trang thai hop le, khong phai mot loi.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_settle_invoice(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    DECLARE v_status   VARCHAR(10)   DEFAULT NULL;
    DECLARE v_has_line INT           DEFAULT 0;
    DECLARE v_msg      VARCHAR(255);

    -- Khoa dong hoa don trong suot transaction: hai cua so app khong the
    -- cung di qua phep kiem ben duoi (cung ly do nhu sp_add_return).
    SELECT Status
    INTO   v_status
    FROM   Invoice
    WHERE  Invoice_ID = p_invoice_id
    FOR UPDATE;

    IF v_status IS NULL THEN
        SET v_msg = CONCAT('Khong tim thay hoa don ', p_invoice_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    IF v_status = 'VOIDED' THEN
        SET v_msg = CONCAT('Hoa don ', p_invoice_id,
                           ' da bi huy (VOIDED), khong the chuyen sang PAID');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SELECT EXISTS (SELECT 1 FROM Invoice_Line WHERE Invoice_ID = p_invoice_id)
    INTO   v_has_line;

    -- Hoa don rong KHONG bao gio PAID, du co bao nhieu tien: fn_invoice_amount_due
    -- tra 0.00 cho hoa don rong nen phep so sanh mot minh se noi "da thu du".
    IF v_status = 'OPEN'
       AND v_has_line = 1
       AND fn_invoice_amount_paid(p_invoice_id) >= fn_invoice_amount_due(p_invoice_id) THEN

        UPDATE Invoice
        SET    Status = 'PAID'
        WHERE  Invoice_ID = p_invoice_id;
    END IF;
END$$


-- ---------------------------------------------------------------------
-- 5.5 sp_void_invoice: Huy mot hoa don
--     HUY KHAC XOA: du lieu van con nguyen (dong hang, thanh toan, tra
--     hang), chi khong duoc tinh vao bao cao nua vi 4 cau truy van bao cao
--     deu loc WHERE Status = 'PAID'. Muon xoa sach thi dung sp_delete_invoice.
--
--     CAC DONG Payment DA GHI KHONG BI XOA: huy khong xoa dau vet tien.
--     Viec tra lai so tien do la nghiep vu hoan tien -- plan/13 muc 2.3.
--
--     Huy mot hoa don DA huy la LOI (khac sp_settle_invoice im lang): o day
--     nguoi dung vua bam mot nut va can biet viec ho tuong minh lam da xay
--     ra tu truoc.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_void_invoice(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    DECLARE v_status VARCHAR(10) DEFAULT NULL;
    DECLARE v_msg    VARCHAR(255);

    SELECT Status
    INTO   v_status
    FROM   Invoice
    WHERE  Invoice_ID = p_invoice_id
    FOR UPDATE;

    IF v_status IS NULL THEN
        SET v_msg = CONCAT('Khong tim thay hoa don ', p_invoice_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    IF v_status = 'VOIDED' THEN
        SET v_msg = CONCAT('Hoa don ', p_invoice_id, ' da bi huy truoc do');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    UPDATE Invoice
    SET    Status = 'VOIDED'
    WHERE  Invoice_ID = p_invoice_id;
END$$


-- ---------------------------------------------------------------------
-- 5.6 sp_add_return: Ghi MOT luot tra hang cho mot dong hoa don
--     Java quan ly ranh gioi transaction (InvoiceDao.processReturn goi thu tuc
--     nay N lan tren cung mot Connection) -- dung nhu sp_add_invoice_line.
--     Thu tuc nay la CHOT CUOI cua cac quy tac nghiep vu: plan/12 muc 3.4.
--
--     GHI CHU CUA KHOI 4 (trang thai hoa don): khoi 4 da xet va QUYET DINH
--     KHONG them RETURNED / PARTIALLY_RETURNED vao ENUM (plan/13 muc 3.3):
--     Status chi mang mot truc nghia "tien da vao hay chua". Hang da tra bao
--     nhieu thi doc tu bang `Return` (sp_get_returnable_lines.SL_con_lai).
--     Dung them trang thai moi vao day.
--
--     Thu tuc nay CHI DOC cot Invoice.Status, khong bao gio ghi. Chi hoa don
--     'PAID' moi tra hang duoc: 'OPEN' nghia la chua thu du tien, ma hoan
--     tien cho mot hoa don chua thu tien la vo nghia.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_add_return(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT,
    IN p_return_date DATE,
    IN p_quantity    DECIMAL(10,3),
    IN p_approved_by VARCHAR(10)
)
BEGIN
    DECLARE v_line_qty  DECIMAL(10,3) DEFAULT NULL;
    DECLARE v_status    VARCHAR(10)   DEFAULT NULL;
    DECLARE v_returned  DECIMAL(10,3) DEFAULT 0;
    DECLARE v_remaining DECIMAL(10,3) DEFAULT 0;
    DECLARE v_return_id INT           DEFAULT 1;
    DECLARE v_msg       VARCHAR(255);

    IF p_quantity IS NULL OR p_quantity <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'So luong tra phai lon hon 0';
    END IF;

    -- Khoa dong hoa don goc trong suot transaction. Hai muc dich:
    --   1. hai cua so app khong the cung di qua phep kiem "con lai" ben duoi
    --   2. fn_next_return_id (MAX + 1) nho the moi an toan
    SELECT l.Quantity, i.Status
    INTO   v_line_qty, v_status
    FROM   Invoice_Line l
    JOIN   Invoice      i ON i.Invoice_ID = l.Invoice_ID
    WHERE  l.Invoice_ID  = p_invoice_id
      AND  l.Line_Number = p_line_number
    FOR UPDATE;

    IF v_line_qty IS NULL THEN
        SET v_msg = CONCAT('Khong tim thay dong ', p_line_number,
                           ' cua hoa don ', p_invoice_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    -- Mot IF thay cho hai: '<> PAID' bao ca 'OPEN' va 'VOIDED', va khong co
    -- ke ho neu sau nay ENUM co them gia tri. Thong diep neu dung trang thai
    -- dang gap de nguoi dung biet vi sao bi tu choi (plan/13 muc 4.9).
    IF v_status <> 'PAID' THEN
        SET v_msg = CONCAT('Hoa don ', p_invoice_id, ' dang o trang thai ', v_status,
                           ', chi hoa don da thanh toan (PAID) moi tra hang duoc');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SELECT COALESCE(SUM(Return_Quantity), 0)
    INTO   v_returned
    FROM   `Return`
    WHERE  Invoice_ID  = p_invoice_id
      AND  Line_Number = p_line_number;

    SET v_remaining = v_line_qty - v_returned;

    IF p_quantity > v_remaining THEN
        SET v_msg = CONCAT('Dong ', p_line_number, ': chi con ', v_remaining,
                           ' co the tra (da mua ', v_line_qty,
                           ', da tra ', v_returned, '), khong tra duoc ', p_quantity);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SET v_return_id = fn_next_return_id(p_invoice_id, p_line_number);

    INSERT INTO `Return` (Invoice_ID, Line_Number, Return_ID,
                          Return_Date, Return_Quantity, Approved_By)
    VALUES (p_invoice_id, p_line_number, v_return_id,
            p_return_date, p_quantity, p_approved_by);
END$$


-- ---------------------------------------------------------------------
-- 5.7 sp_delete_return: Huy MOT luot tra hang da ghi
--     Khong can SIGNAL: xoa khong co gi thi DELETE tra 0 dong, Java bao lai.
--     Va KHONG danh lai so Return_ID cua cac luot con lai -- so thu tu la
--     danh tinh, khong phai vi tri (plan/12 muc 4.8).
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_delete_return(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT,
    IN p_return_id   INT
)
BEGIN
    DELETE FROM `Return`
    WHERE Invoice_ID  = p_invoice_id
      AND Line_Number = p_line_number
      AND Return_ID   = p_return_id;
END$$


-- =====================================================================
-- PHẦN 5: THỦ TỤC XÁC THỰC TÀI KHOẢN & SỬA/XÓA TRỰC TIẾP DATABASE
-- =====================================================================

-- ---------------------------------------------------------------------
-- 5.1 sp_authenticate_account: Kiểm tra đăng nhập
--     Cot Cashier_ID cho Java biet tai khoan nay ung voi nhan vien nao
--     (dung lam nguoi duyet tra hang -- plan/12 muc 3.3). Co the NULL.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_authenticate_account(
    IN p_username VARCHAR(50),
    IN p_password VARCHAR(100)
)
BEGIN
    SELECT Username, Role, Full_Name, Cashier_ID
    FROM   Account
    WHERE  Username = p_username
      AND  Password = p_password;
END$$


-- ---------------------------------------------------------------------
-- 5.2 sp_delete_invoice: Xóa an toàn hóa đơn và các quan hệ khóa ngoại
--     Thứ tự xóa bắt buộc theo quan hệ cha - con:
--     1. Return (bảng con của Invoice_Line)
--     2. Cash_Payment / Card_Payment / EWallet_Payment (bảng con của Payment)
--     3. Payment (bảng con của Invoice)
--     4. Invoice_Line (bảng con của Invoice)
--     5. Invoice (bảng cha)
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_delete_invoice(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    -- 1. Xóa các lượt trả hàng thuộc các dòng của hóa đơn này
    DELETE FROM `Return`
    WHERE Invoice_ID = p_invoice_id;

    -- 2. Xóa các chi tiết phương thức thanh toán bảng con
    DELETE FROM Cash_Payment
    WHERE Payment_ID IN (SELECT Payment_ID FROM Payment WHERE Invoice_ID = p_invoice_id);

    DELETE FROM Card_Payment
    WHERE Payment_ID IN (SELECT Payment_ID FROM Payment WHERE Invoice_ID = p_invoice_id);

    DELETE FROM EWallet_Payment
    WHERE Payment_ID IN (SELECT Payment_ID FROM Payment WHERE Invoice_ID = p_invoice_id);

    -- 3. Xóa các lượt thanh toán chính
    DELETE FROM Payment
    WHERE Invoice_ID = p_invoice_id;

    -- 4. Xóa các dòng hàng
    DELETE FROM Invoice_Line
    WHERE Invoice_ID = p_invoice_id;

    -- 5. Xóa phần đầu hóa đơn
    DELETE FROM Invoice
    WHERE Invoice_ID = p_invoice_id;
END$$


-- ---------------------------------------------------------------------
-- 5.3 sp_update_invoice_header: Chỉnh sửa thông tin chung của hóa đơn
--     p_status KHONG con la "trang thai muon dat" ma la MOT PHEP KIEM:
--     no phai BANG trang thai dang co trong database. Moi chuyen doi trang
--     thai chi di qua sp_settle_invoice (-> PAID) hoac sp_void_invoice
--     (-> VOIDED) -- plan/13 muc 3.2.
--     Giu nguyen 7 tham so de khong pha hop dong voi Java (docs/05 Quy tac 2).
--
--     GHI CHU TRUNG THUC: InvoiceDao.updateInvoiceHeader chay o che do
--     autocommit nen phep kiem va cau UPDATE KHONG nguyen tu -- ve ly thuyet
--     co khe ho neu hai cua so app cung sua mot hoa don trong vai micro-giay.
--     Khong bit bang FOR UPDATE (vo ich duoi autocommit) cung khong bang
--     WHERE ... AND Status = p_status (vi luu ma khong sua gi thi ROW_COUNT()
--     tra 0 va ta se bao mot loi khong co that). Hau qua xau nhat cua khe ho
--     la ghi de ngay/gio/quay -- khong phai tien, khong phai trang thai.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_update_invoice_header(
    IN p_invoice_id  VARCHAR(30),
    IN p_date        DATE,
    IN p_time        TIME,
    IN p_status      ENUM('OPEN','PAID','VOIDED'),
    IN p_counter_id  VARCHAR(10),
    IN p_cashier_id  VARCHAR(10),
    IN p_customer_id VARCHAR(15)
)
BEGIN
    DECLARE v_old VARCHAR(10) DEFAULT NULL;
    DECLARE v_msg VARCHAR(255);

    SELECT Status INTO v_old
    FROM   Invoice
    WHERE  Invoice_ID = p_invoice_id;

    IF v_old IS NULL THEN
        SET v_msg = CONCAT('Khong tim thay hoa don ', p_invoice_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    IF p_status IS NULL OR p_status <> v_old THEN
        IF v_old = 'VOIDED' THEN
            SET v_msg = CONCAT('Hoa don ', p_invoice_id,
                   ' da bi huy, khong doi trang thai duoc nua');
        ELSEIF p_status = 'VOIDED' THEN
            SET v_msg = 'Khong huy hoa don bang hop thoai sua. Dung nut "Huy hoa don" o tab Xem hoa don';
        ELSEIF p_status = 'PAID' THEN
            SET v_msg = 'Khong tu dat PAID. Hoa don tu chuyen sang PAID khi da thu du tien';
        ELSE
            SET v_msg = CONCAT('Khong mo lai hoa don da thu tien (',
                   v_old, ' -> ', COALESCE(p_status, 'NULL'), ')');
        END IF;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    UPDATE Invoice
    SET    `Date`      = p_date,
           `Time`      = p_time,
           Status      = p_status,   -- = v_old, giu lai cho ro rang
           Counter_ID  = p_counter_id,
           Cashier_ID  = p_cashier_id,
           Customer_ID = p_customer_id
    WHERE  Invoice_ID  = p_invoice_id;
END$$


-- ---------------------------------------------------------------------
-- 5.4 sp_update_invoice_line: Chỉnh sửa dòng hàng trong hóa đơn
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_update_invoice_line(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT,
    IN p_barcode     VARCHAR(20),
    IN p_quantity    DECIMAL(10,3),
    IN p_unit_price  DECIMAL(12,2),
    IN p_discount    DECIMAL(12,2)
)
BEGIN
    UPDATE Invoice_Line
    SET    Barcode    = p_barcode,
           Quantity   = p_quantity,
           Unit_Price = p_unit_price,
           Discount   = p_discount
    WHERE  Invoice_ID  = p_invoice_id
      AND  Line_Number = p_line_number;
END$$


-- ---------------------------------------------------------------------
-- 5.5 sp_delete_invoice_line: Xóa 1 dòng hàng cụ thể trong hóa đơn
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_delete_invoice_line(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT
)
BEGIN
    -- Xóa lượt trả hàng tham chiếu đến dòng này nếu có
    DELETE FROM `Return`
    WHERE Invoice_ID = p_invoice_id
      AND Line_Number = p_line_number;

    -- Xóa dòng hàng
    DELETE FROM Invoice_Line
    WHERE Invoice_ID = p_invoice_id
      AND Line_Number = p_line_number;
END$$


DELIMITER ;

SELECT 'Cac ham va thu tuc SQL (04_routines.sql) da tao thanh cong!' AS ket_qua;

