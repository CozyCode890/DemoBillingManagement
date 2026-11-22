-- 04_routines.sql -- Functions, procedures, triggers and views

USE retail_billing;

DROP FUNCTION IF EXISTS fn_get_product_price;
DROP FUNCTION IF EXISTS fn_active_promo_pct;
DROP FUNCTION IF EXISTS fn_line_discount;
DROP FUNCTION IF EXISTS fn_next_invoice_id;
DROP FUNCTION IF EXISTS fn_next_payment_id;
DROP FUNCTION IF EXISTS fn_return_refund;
DROP FUNCTION IF EXISTS fn_next_return_id;
DROP FUNCTION IF EXISTS fn_invoice_amount_due;
DROP FUNCTION IF EXISTS fn_invoice_amount_paid;
DROP FUNCTION IF EXISTS fn_line_returned_qty;
DROP FUNCTION IF EXISTS fn_line_refund;
DROP FUNCTION IF EXISTS fn_invoice_refund;
DROP FUNCTION IF EXISTS fn_invoice_in_revenue;

DROP PROCEDURE IF EXISTS sp_lookup_counters;
DROP PROCEDURE IF EXISTS sp_lookup_cashiers;
DROP PROCEDURE IF EXISTS sp_lookup_managers;
DROP PROCEDURE IF EXISTS sp_lookup_customers;
DROP PROCEDURE IF EXISTS sp_search_customers_by_phone;
DROP PROCEDURE IF EXISTS sp_get_all_products;
DROP PROCEDURE IF EXISTS sp_get_active_promo;
DROP PROCEDURE IF EXISTS sp_add_price_history;

DROP PROCEDURE IF EXISTS sp_get_recent_invoices;
DROP PROCEDURE IF EXISTS sp_get_invoice_header;
DROP PROCEDURE IF EXISTS sp_get_invoice_lines;
DROP PROCEDURE IF EXISTS sp_get_invoice_payments;
DROP PROCEDURE IF EXISTS sp_get_invoice_returns;
DROP PROCEDURE IF EXISTS sp_get_invoice_adjusted;
DROP PROCEDURE IF EXISTS sp_get_returnable_lines;
DROP PROCEDURE IF EXISTS sp_get_invoice_edit_fields;
DROP PROCEDURE IF EXISTS sp_get_invoice_line_edit_fields;

DROP PROCEDURE IF EXISTS sp_report_daily_takings;
DROP PROCEDURE IF EXISTS sp_report_price_rise;
DROP PROCEDURE IF EXISTS sp_report_best_cashier;
DROP PROCEDURE IF EXISTS sp_report_big_returns;
DROP PROCEDURE IF EXISTS sp_report_old_price_proof;
DROP PROCEDURE IF EXISTS sp_report_price_30_days;

DROP PROCEDURE IF EXISTS sp_create_invoice_header;
DROP PROCEDURE IF EXISTS sp_add_invoice_line;
DROP PROCEDURE IF EXISTS sp_add_payment;
DROP PROCEDURE IF EXISTS sp_settle_invoice;
DROP PROCEDURE IF EXISTS sp_void_invoice;
DROP PROCEDURE IF EXISTS sp_add_return;
DROP PROCEDURE IF EXISTS sp_delete_return;

DROP PROCEDURE IF EXISTS sp_delete_invoice;
DROP PROCEDURE IF EXISTS sp_update_invoice_header;
DROP PROCEDURE IF EXISTS sp_update_invoice_line;
DROP PROCEDURE IF EXISTS sp_delete_invoice_line;

DROP PROCEDURE IF EXISTS sp_wipe_transactions;
DROP PROCEDURE IF EXISTS sp_wipe_all_data;

DROP TRIGGER IF EXISTS trg_price_history_bi;
DROP TRIGGER IF EXISTS trg_price_history_bu;
DROP TRIGGER IF EXISTS trg_price_history_bd;
DROP TRIGGER IF EXISTS trg_invoice_bi;
DROP TRIGGER IF EXISTS trg_invoice_bu;
DROP TRIGGER IF EXISTS trg_invoice_line_bi;
DROP TRIGGER IF EXISTS trg_invoice_line_bu;
DROP TRIGGER IF EXISTS trg_invoice_line_bd;
DROP TRIGGER IF EXISTS trg_return_bi;
DROP TRIGGER IF EXISTS trg_return_bu;
DROP TRIGGER IF EXISTS trg_return_bd;
DROP TRIGGER IF EXISTS trg_payment_bi;
DROP TRIGGER IF EXISTS trg_payment_bu;
DROP TRIGGER IF EXISTS trg_payment_bd;
DROP TRIGGER IF EXISTS trg_cash_payment_bi;
DROP TRIGGER IF EXISTS trg_cash_payment_bu;
DROP TRIGGER IF EXISTS trg_cash_payment_bd;
DROP TRIGGER IF EXISTS trg_card_payment_bi;
DROP TRIGGER IF EXISTS trg_card_payment_bu;
DROP TRIGGER IF EXISTS trg_card_payment_bd;
DROP TRIGGER IF EXISTS trg_ewallet_payment_bi;
DROP TRIGGER IF EXISTS trg_ewallet_payment_bu;
DROP TRIGGER IF EXISTS trg_ewallet_payment_bd;

DROP VIEW IF EXISTS vw_cash_change_due;
DROP VIEW IF EXISTS vw_invoice_line_total;
DELIMITER $$

-- PART 1: FUNCTIONS

-- 1.1 fn_get_product_price (BR6, BR8): product price on a given date
CREATE FUNCTION fn_get_product_price(
    p_barcode VARCHAR(20),
    p_date    DATE
)
RETURNS DECIMAL(12,2)
NOT DETERMINISTIC
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

-- 1.2 fn_active_promo_pct (BR11): best promotion percent on a given date
CREATE FUNCTION fn_active_promo_pct(
    p_barcode VARCHAR(20),
    p_date    DATE
)
RETURNS DECIMAL(5,2)
NOT DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_pct DECIMAL(5,2) DEFAULT NULL;

    SELECT MAX(a.Discount_Percent)
    INTO   v_pct
    FROM   Applies_To a
    JOIN   Promotion  p ON p.Promo_ID = a.Promo_ID
    WHERE  a.Barcode = p_barcode
      AND  p_date BETWEEN a.Promo_Start_date AND a.Promo_End_date
      AND  p.Status = 'ACTIVE'
      AND  p.Expiry_Date >= p_date;

    RETURN COALESCE(v_pct, 0.00);
END$$

-- 1.3 fn_line_discount (BR5, BR9, BR11): discount amount of a line
CREATE FUNCTION fn_line_discount(
    p_barcode    VARCHAR(20),
    p_date       DATE,
    p_quantity   DECIMAL(10,3),
    p_unit_price DECIMAL(12,2)
)
RETURNS DECIMAL(12,2)
NOT DETERMINISTIC
READS SQL DATA
BEGIN
    RETURN ROUND(p_quantity * p_unit_price
                 * fn_active_promo_pct(p_barcode, p_date) / 100, 2);
END$$

-- 1.4 fn_next_payment_id: next payment ID (PAY-xxxx)
CREATE FUNCTION fn_next_payment_id()
RETURNS VARCHAR(20)
NOT DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_max_seq INT DEFAULT 0;

    SELECT COALESCE(MAX(CAST(SUBSTRING(Payment_ID, 5) AS UNSIGNED)), 0)
    INTO   v_max_seq
    FROM   Payment
    WHERE  Payment_ID REGEXP '^PAY-[0-9]+$';

    RETURN CONCAT('PAY-', LPAD(v_max_seq + 1, 4, '0'));
END$$

-- 1.5 fn_return_refund (BR16): refund amount of one return
CREATE FUNCTION fn_return_refund(
    p_invoice_id  VARCHAR(30),
    p_line_number INT,
    p_quantity    DECIMAL(10,3)
)
RETURNS DECIMAL(12,2)
NOT DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_refund DECIMAL(12,2) DEFAULT NULL;

    SELECT ROUND(p_quantity / l.Quantity
                 * (l.Quantity * l.Unit_Price - l.Discount), 2)
    INTO   v_refund
    FROM   Invoice_Line l
    WHERE  l.Invoice_ID  = p_invoice_id
      AND  l.Line_Number = p_line_number;

    RETURN COALESCE(v_refund, 0.00);
END$$

-- 1.6 fn_next_return_id: next return number of an invoice line
CREATE FUNCTION fn_next_return_id(
    p_invoice_id  VARCHAR(30),
    p_line_number INT
)
RETURNS INT
NOT DETERMINISTIC
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

-- 1.7 fn_invoice_amount_due (BR9): total amount due of an invoice
CREATE FUNCTION fn_invoice_amount_due(
    p_invoice_id VARCHAR(30)
)
RETURNS DECIMAL(12,2)
NOT DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_due DECIMAL(12,2) DEFAULT NULL;

    SELECT SUM(l.Quantity * l.Unit_Price - l.Discount)
    INTO   v_due
    FROM   Invoice_Line l
    WHERE  l.Invoice_ID = p_invoice_id;

    RETURN COALESCE(v_due, 0.00);
END$$

-- 1.8 fn_invoice_amount_paid (BR12, BR14): total amount paid of an invoice
CREATE FUNCTION fn_invoice_amount_paid(
    p_invoice_id VARCHAR(30)
)
RETURNS DECIMAL(12,2)
NOT DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_paid DECIMAL(12,2) DEFAULT NULL;

    SELECT SUM(p.Amount)
    INTO   v_paid
    FROM   Payment p
    WHERE  p.Invoice_ID = p_invoice_id;

    RETURN COALESCE(v_paid, 0.00);
END$$

-- 1.9 fn_line_returned_qty: total returned quantity of a line
CREATE FUNCTION fn_line_returned_qty(
    p_invoice_id  VARCHAR(30),
    p_line_number INT
)
RETURNS DECIMAL(10,3)
NOT DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_qty DECIMAL(10,3) DEFAULT NULL;

    SELECT SUM(r.Return_Quantity)
    INTO   v_qty
    FROM   `Return` r
    WHERE  r.Invoice_ID  = p_invoice_id
      AND  r.Line_Number = p_line_number;

    RETURN COALESCE(v_qty, 0.000);
END$$

-- 1.10 fn_line_refund: total refund of a line
CREATE FUNCTION fn_line_refund(
    p_invoice_id  VARCHAR(30),
    p_line_number INT
)
RETURNS DECIMAL(12,2)
NOT DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_refund DECIMAL(12,2) DEFAULT NULL;

    SELECT SUM(fn_return_refund(r.Invoice_ID, r.Line_Number, r.Return_Quantity))
    INTO   v_refund
    FROM   `Return` r
    WHERE  r.Invoice_ID  = p_invoice_id
      AND  r.Line_Number = p_line_number;

    RETURN COALESCE(v_refund, 0.00);
END$$

-- 1.11 fn_invoice_refund: total refund of an invoice
CREATE FUNCTION fn_invoice_refund(
    p_invoice_id VARCHAR(30)
)
RETURNS DECIMAL(12,2)
NOT DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_refund DECIMAL(12,2) DEFAULT NULL;

    SELECT SUM(fn_return_refund(r.Invoice_ID, r.Line_Number, r.Return_Quantity))
    INTO   v_refund
    FROM   `Return` r
    WHERE  r.Invoice_ID = p_invoice_id;

    RETURN COALESCE(v_refund, 0.00);
END$$

-- 1.12 fn_invoice_in_revenue (BR3, BR16): 1 if the invoice counts as revenue
CREATE FUNCTION fn_invoice_in_revenue(
    p_invoice_id VARCHAR(30)
)
RETURNS TINYINT
NOT DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_status VARCHAR(10) DEFAULT NULL;
    SELECT Status INTO v_status FROM Invoice WHERE Invoice_ID = p_invoice_id;
    RETURN IF(v_status = 'PAID', 1, 0);
END$$

-- PART 2: LOOKUP AND PRODUCT PROCEDURES

-- 2.1 sp_lookup_counters: list counters with shop address
CREATE PROCEDURE sp_lookup_counters()
BEGIN
    SELECT c.Counter_ID,
           CONCAT(s.Shop_ID, ' - ', s.Address) AS Mo_ta
    FROM   Counter c
    JOIN   Shop    s ON s.Shop_ID = c.Shop_ID
    ORDER BY c.Counter_ID;
END$$

-- 2.2 sp_lookup_cashiers: list cashiers
CREATE PROCEDURE sp_lookup_cashiers()
BEGIN
    SELECT Cashier_ID, Name
    FROM   Cashier
    ORDER BY Cashier_ID;
END$$

-- 2.2b sp_lookup_managers (BR3, BR15): list managers
CREATE PROCEDURE sp_lookup_managers()
BEGIN
    SELECT Cashier_ID, Name
    FROM   Cashier
    WHERE  Role = 'MANAGER'
    ORDER BY Cashier_ID;
END$$

-- 2.3 sp_lookup_customers: list member customers
CREATE PROCEDURE sp_lookup_customers()
BEGIN
    SELECT Customer_ID,
           CONCAT(Name, ' - ', Phone) AS Mo_ta
    FROM   Customer
    ORDER BY Customer_ID;
END$$

-- 2.4 sp_search_customers_by_phone (BR10): find customers by phone prefix
CREATE PROCEDURE sp_search_customers_by_phone(
    IN p_prefix VARCHAR(20)
)
BEGIN
    SELECT  Customer_ID AS Ma_KH,
            Name        AS Ten,
            Phone       AS Dien_thoai
    FROM    Customer
    WHERE   Phone LIKE CONCAT(p_prefix, '%')
    ORDER BY Phone
    LIMIT   20;
END$$

-- 2.5 sp_get_all_products: list all products
CREATE PROCEDURE sp_get_all_products()
BEGIN
    SELECT Barcode, Name, Unit, Tax_Rate
    FROM   Product
    ORDER BY Name;
END$$

-- 2.6 sp_add_price_history (BR6, BR8): add a new price
CREATE PROCEDURE sp_add_price_history(
    IN p_barcode         VARCHAR(20),
    IN p_valid_from_date DATE,
    IN p_price           DECIMAL(12,2)
)
BEGIN
    DECLARE v_product VARCHAR(20) DEFAULT NULL;
    DECLARE v_latest  DATE        DEFAULT NULL;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET @retail_price_write = NULL;
        RESIGNAL;
    END;

    IF p_barcode IS NULL OR p_valid_from_date IS NULL OR p_price IS NULL OR p_price <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Barcode, effective date and positive price are required';
    END IF;

    SELECT Barcode INTO v_product
    FROM   Product
    WHERE  Barcode = p_barcode
    FOR UPDATE;

    IF v_product IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unknown product barcode';
    END IF;

    SELECT MAX(Valid_From_Date) INTO v_latest
    FROM   Price_History
    WHERE  Barcode = p_barcode;

    IF v_latest IS NOT NULL AND p_valid_from_date <= v_latest THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Valid_From_Date must be strictly greater than all prior price records (BR6/BR8)';
    END IF;

    SET @retail_price_write = 1;
    INSERT INTO Price_History (Barcode, Valid_From_Date, Price)
    VALUES (p_barcode, p_valid_from_date, p_price);
    SET @retail_price_write = NULL;
END$$

-- 2.7 sp_get_active_promo (BR11): best promotion of a product on a date
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
    ORDER BY a.Discount_Percent DESC, p.Expiry_Date ASC, a.Promo_ID ASC
    LIMIT 1;
END$$

-- PART 3: INVOICE VIEW PROCEDURES

-- 3.1 sp_get_recent_invoices: list invoices with totals
CREATE PROCEDURE sp_get_recent_invoices(
    IN p_days INT
)
BEGIN
    SELECT  i.Invoice_ID                                             AS Ma_HD,
            i.`Date`                                                 AS Ngay,
            i.`Time`                                                 AS Gio,
            s.Shop_ID                                                AS Shop,
            c.Name                                                   AS Thu_ngan,
            COALESCE(cu.Name, '(vang lai)')                          AS Khach,
            i.Status                                                 AS Trang_thai,
            COALESCE(SUM(l.Quantity * l.Unit_Price - l.Discount), 0) AS Tong_tien
    FROM    Invoice      i
    JOIN    Counter      ct ON ct.Counter_ID  = i.Counter_ID
    JOIN    Shop         s  ON s.Shop_ID      = ct.Shop_ID
    JOIN    Cashier      c  ON c.Cashier_ID   = i.Cashier_ID
    LEFT JOIN Customer   cu ON cu.Customer_ID = i.Customer_ID
    LEFT JOIN Invoice_Line l ON l.Invoice_ID  = i.Invoice_ID
    WHERE   COALESCE(p_days, 0) <= 0
       OR   i.`Date` >= DATE_SUB(CURDATE(), INTERVAL p_days DAY)
    GROUP BY i.Invoice_ID, i.`Date`, i.`Time`, s.Shop_ID,
             c.Name, cu.Name, i.Status
    ORDER BY i.`Date` DESC, i.`Time` DESC;
END$$

-- 3.2 sp_get_invoice_header: header and totals of one invoice
CREATE PROCEDURE sp_get_invoice_header(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    SELECT  i.Invoice_ID                                             AS Ma_HD,
            i.`Date`                                                 AS Ngay,
            i.`Time`                                                 AS Gio,
            i.Status                                                 AS Trang_thai,
            i.Counter_ID                                             AS Quay,
            s.Shop_ID                                                AS Shop,
            s.Address                                                AS Dia_chi,
            c.Name                                                   AS Thu_ngan,
            COALESCE(cu.Name, '(khach vang lai)')                    AS Khach_hang,
            COALESCE(SUM(l.Quantity * l.Unit_Price), 0)              AS Tong_truoc_giam,
            COALESCE(SUM(l.Discount), 0)                             AS Tong_giam,
            COALESCE(SUM(l.Quantity * l.Unit_Price - l.Discount), 0) AS Tong_phai_tra,
            fn_invoice_refund(i.Invoice_ID)                          AS Tien_hoan,
            COALESCE(SUM(l.Quantity * l.Unit_Price - l.Discount), 0)
              - fn_invoice_refund(i.Invoice_ID)                      AS Tong_sau_dieu_chinh
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

-- 3.3 sp_get_invoice_lines (BR9): lines of one invoice
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
            l.Tax_Rate                               AS VAT_pct
    FROM    Invoice_Line l
    JOIN    Product      p ON p.Barcode = l.Barcode
    WHERE   l.Invoice_ID = p_invoice_id
    ORDER BY l.Line_Number;
END$$

-- 3.4 sp_get_invoice_payments (BR12, BR13): payments of one invoice
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
              WHEN cd.Payment_ID IS NOT NULL THEN CONCAT(cd.Card_number, ' / ', cd.Auth_code)
              WHEN ew.Payment_ID IS NOT NULL THEN CONCAT(ew.Wallet_provider, ' / ', ew.Transaction_Ref)
              ELSE '' END           AS Chi_tiet,
            ca.Tendered_Amount      AS Tien_khach_dua
    FROM    Payment p
    LEFT JOIN Cash_Payment    ca ON ca.Payment_ID = p.Payment_ID
    LEFT JOIN Card_Payment    cd ON cd.Payment_ID = p.Payment_ID
    LEFT JOIN EWallet_Payment ew ON ew.Payment_ID = p.Payment_ID
    WHERE   p.Invoice_ID = p_invoice_id
    ORDER BY p.Payment_ID;
END$$

-- 3.5 sp_get_invoice_adjusted (BR16): invoice after returns
CREATE PROCEDURE sp_get_invoice_adjusted(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    SELECT  l.Invoice_ID                                          AS Ma_HD,
            l.Line_Number                                         AS Dong,
            p.Name                                                AS San_pham,
            p.Unit                                                AS DVT,
            l.Quantity                                            AS SL_mua,
            fn_line_returned_qty(l.Invoice_ID, l.Line_Number)     AS SL_tra,
            l.Quantity
              - fn_line_returned_qty(l.Invoice_ID, l.Line_Number) AS SL_con_lai,
            l.Unit_Price                                          AS Don_gia,
            (l.Quantity * l.Unit_Price - l.Discount)              AS Thanh_tien_goc,
            fn_line_refund(l.Invoice_ID, l.Line_Number)           AS Tien_hoan,
            (l.Quantity * l.Unit_Price - l.Discount)
              - fn_line_refund(l.Invoice_ID, l.Line_Number)       AS Thanh_tien_moi,
            (SELECT MAX(r.Return_Date)
             FROM   `Return` r
             WHERE  r.Invoice_ID  = l.Invoice_ID
               AND  r.Line_Number = l.Line_Number)                AS Ngay_tra
    FROM    Invoice_Line l
    JOIN    Product      p ON p.Barcode = l.Barcode
    WHERE   l.Invoice_ID = p_invoice_id
    ORDER BY l.Line_Number;
END$$

-- 3.6 sp_get_returnable_lines (BR16): lines and quantity that can still be returned
CREATE PROCEDURE sp_get_returnable_lines(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    SELECT  l.Line_Number                                    AS Dong,
            p.Name                                           AS San_pham,
            p.Unit                                           AS DVT,
            l.Quantity                                       AS SL_mua,
            COALESCE(SUM(r.Return_Quantity), 0)              AS SL_da_tra,
            l.Quantity - COALESCE(SUM(r.Return_Quantity), 0) AS SL_con_lai,
            l.Unit_Price                                     AS Don_gia,
            l.Discount                                       AS Giam_gia,
            (l.Quantity * l.Unit_Price - l.Discount)         AS Thanh_tien
    FROM    Invoice_Line l
    JOIN    Product      p ON p.Barcode = l.Barcode
    LEFT JOIN `Return`   r ON r.Invoice_ID  = l.Invoice_ID
                          AND r.Line_Number = l.Line_Number
    WHERE   l.Invoice_ID = p_invoice_id
    GROUP BY l.Line_Number, p.Name, p.Unit,
             l.Quantity, l.Unit_Price, l.Discount
    ORDER BY l.Line_Number;
END$$

-- 3.7 sp_get_invoice_edit_fields: raw fields of one invoice
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

-- 3.8 sp_get_invoice_line_edit_fields: raw fields of one invoice line
CREATE PROCEDURE sp_get_invoice_line_edit_fields(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT
)
BEGIN
    SELECT  l.Barcode,
            l.Quantity,
            l.Unit_Price,
            l.Discount
    FROM    Invoice_Line l
    WHERE   l.Invoice_ID  = p_invoice_id
      AND   l.Line_Number = p_line_number;
END$$

-- PART 4: REPORT PROCEDURES (Q2, Q3, Q4, Q5 and BONUS)

-- 4.1 sp_report_daily_takings (Q2): daily revenue by shop
CREATE PROCEDURE sp_report_daily_takings()
BEGIN
    SELECT  s.Shop_ID                                     AS Cua_hang,
            s.Address                                     AS Dia_chi,
            i.`Date`                                      AS Ngay,
            COUNT(DISTINCT i.Invoice_ID)                  AS So_hoa_don,
            ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount
                - fn_line_refund(l.Invoice_ID, l.Line_Number)), 2) AS Doanh_thu
    FROM    Invoice      i
    JOIN    Counter      ct ON ct.Counter_ID = i.Counter_ID
    JOIN    Shop         s  ON s.Shop_ID     = ct.Shop_ID
    JOIN    Invoice_Line l  ON l.Invoice_ID  = i.Invoice_ID
    WHERE   fn_invoice_in_revenue(i.Invoice_ID) = 1
      AND   i.`Date` BETWEEN DATE_SUB(CURDATE(), INTERVAL 7 DAY) AND DATE_SUB(CURDATE(), INTERVAL 1 DAY)
    GROUP BY s.Shop_ID, s.Address, i.`Date`
    ORDER BY i.`Date` DESC, s.Shop_ID;
END$$

-- 4.2 sp_report_price_rise (Q3): products with price increase > 10%
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

-- 4.3 sp_report_best_cashier (Q4): cashier with the highest average invoice
CREATE PROCEDURE sp_report_best_cashier()
BEGIN
    SELECT  c.Cashier_ID           AS Ma_TN,
            c.Name                 AS Ten_thu_ngan,
            COUNT(*)               AS So_hoa_don,
            ROUND(SUM(t.Tong), 2)  AS Tong_doanh_thu,
            ROUND(AVG(t.Tong), 2)  AS Gio_hang_TB
    FROM    Cashier c
    JOIN    Invoice i ON i.Cashier_ID = c.Cashier_ID
                     AND fn_invoice_in_revenue(i.Invoice_ID) = 1
    JOIN   (SELECT Invoice_ID,
                   SUM(Quantity * Unit_Price - Discount
                       - fn_line_refund(Invoice_ID, Line_Number)) AS Tong
            FROM   Invoice_Line
            GROUP BY Invoice_ID) t  ON t.Invoice_ID = i.Invoice_ID
    GROUP BY c.Cashier_ID, c.Name
    ORDER BY Gio_hang_TB DESC;
END$$

-- 4.4 sp_report_big_returns (Q5, BR16): invoices with more than half returned
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
    WHERE   fn_invoice_in_revenue(i.Invoice_ID) = 1
      AND   tra.Tien_tra > tong.Tong_HD / 2
    ORDER BY Phan_tram_tra DESC;
END$$

-- 4.5 sp_report_old_price_proof (BR8): proof that old invoices keep old prices
CREATE PROCEDURE sp_report_old_price_proof()
BEGIN
    SELECT  p.Name                                     AS San_pham,
            l.Unit_Price                               AS Gia_ghi_tren_HD,
            fn_get_product_price(l.Barcode, CURDATE()) AS Gia_hom_nay,
            COUNT(*)                                   AS So_dong_hang,
            MIN(i.`Date`)                              AS Ban_tu_ngay,
            MAX(i.`Date`)                              AS Ban_den_ngay
    FROM    Invoice_Line l
    JOIN    Invoice      i ON i.Invoice_ID = l.Invoice_ID
    JOIN    Product      p ON p.Barcode    = l.Barcode
    WHERE   l.Unit_Price <> fn_get_product_price(l.Barcode, CURDATE())
    GROUP BY p.Name, l.Unit_Price, l.Barcode
    ORDER BY p.Name, l.Unit_Price;
END$$

-- 4.6 sp_report_price_30_days (BR6, BR11): price and promotion changes in 30 days
CREATE PROCEDURE sp_report_price_30_days(
    IN p_barcode VARCHAR(20)
)
BEGIN
    WITH RECURSIVE d (n) AS (
        SELECT 29
        UNION ALL
        SELECT n - 1 FROM d WHERE n > 0
    ),
    gia AS (
        SELECT  DATE_SUB(CURDATE(), INTERVAL d.n DAY)                                  AS Ngay,
                fn_get_product_price(p_barcode, DATE_SUB(CURDATE(), INTERVAL d.n DAY)) AS Don_gia
        FROM    d
    ),
    tien AS (
        SELECT  g.Ngay,
                g.Don_gia,
                fn_active_promo_pct(p_barcode, g.Ngay)                         AS KM_phan_tram,
                g.Don_gia - fn_line_discount(p_barcode, g.Ngay, 1, g.Don_gia)  AS Thanh_tien_SL1
        FROM    gia g
    )
    SELECT  t.Ngay,
            t.Don_gia,
            t.KM_phan_tram,
            t.Thanh_tien_SL1,
            t.Thanh_tien_SL1 - LAG(t.Thanh_tien_SL1) OVER (ORDER BY t.Ngay)    AS Chenh_lech
    FROM    tien t
    ORDER BY t.Ngay;
END$$

-- PART 5: CREATE, UPDATE AND DELETE PROCEDURES (IN TRANSACTIONS)

-- 5.1 sp_create_invoice_header (BR1, BR2, BR3, BR4): create an invoice header
CREATE PROCEDURE sp_create_invoice_header(
    IN  p_date        DATE,
    IN  p_time        TIME,
    IN  p_counter_id  VARCHAR(10),
    IN  p_cashier_id  VARCHAR(10),
    IN  p_customer_id VARCHAR(15),
    OUT p_invoice_id  VARCHAR(30)
)
BEGIN
    DECLARE v_shop_id       VARCHAR(10)     DEFAULT NULL;
    DECLARE v_now           DATETIME;
    DECLARE v_date_server   DATE;
    DECLARE v_prefix        VARCHAR(64);
    DECLARE v_last_invoice  VARCHAR(30)     DEFAULT NULL;
    DECLARE v_max_sequence  BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_next_sequence BIGINT UNSIGNED;
    DECLARE v_generated_id  VARCHAR(64);
    DECLARE v_not_found     BOOLEAN         DEFAULT FALSE;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_not_found = TRUE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET @retail_create_invoice = NULL;
        RESIGNAL;
    END;
    IF p_counter_id IS NULL OR p_cashier_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Counter and logged-in cashier are required (BR1/BR4)';
    END IF;

    SET v_now = NOW();
    SET v_date_server = DATE(v_now);

    SELECT Shop_ID INTO v_shop_id
    FROM   Counter
    WHERE  Counter_ID = p_counter_id
    FOR UPDATE;

    IF v_not_found OR v_shop_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Unknown counter or counter has no shop (BR1)';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM Cashier WHERE Cashier_ID = p_cashier_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unknown cashier (BR4)';
    END IF;

    IF p_customer_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM Customer WHERE Customer_ID = p_customer_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unknown customer (BR10)';
    END IF;

    SET v_prefix = CONCAT(v_shop_id, '-', p_counter_id, '-', DATE_FORMAT(v_date_server, '%Y%m%d'), '-');
    SET v_not_found = FALSE;

    SELECT Invoice_ID INTO v_last_invoice
    FROM   Invoice
    WHERE  Counter_ID = p_counter_id
      AND  `Date` = v_date_server
      AND  LEFT(Invoice_ID, CHAR_LENGTH(v_prefix)) = v_prefix
    ORDER BY CAST(SUBSTRING_INDEX(Invoice_ID, '-', -1) AS UNSIGNED) DESC
    LIMIT 1
    FOR UPDATE;

    IF NOT v_not_found THEN
        SET v_max_sequence = CAST(SUBSTRING_INDEX(v_last_invoice, '-', -1) AS UNSIGNED);
    END IF;

    SET v_next_sequence = v_max_sequence + 1;
    SET v_generated_id = CONCAT(
        v_prefix,
        LPAD(CAST(v_next_sequence AS CHAR),
             GREATEST(4, CHAR_LENGTH(CAST(v_next_sequence AS CHAR))), '0')
    );

    IF CHAR_LENGTH(v_generated_id) > 30 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Generated invoice code exceeds Invoice_ID VARCHAR(30)';
    END IF;

    SET p_invoice_id = v_generated_id;

    SET @retail_create_invoice = 1;
    INSERT INTO Invoice (Invoice_ID, `Date`, `Time`, Status, Counter_ID, Cashier_ID, Customer_ID)
    VALUES (v_generated_id, v_date_server, TIME(v_now), 'PAID',
            p_counter_id, p_cashier_id, p_customer_id);
    SET @retail_create_invoice = NULL;
END$$

-- 5.2 sp_add_invoice_line (BR5, BR7, BR8, BR9, BR11): add a line to an invoice
CREATE PROCEDURE sp_add_invoice_line(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT,
    IN p_barcode     VARCHAR(20),
    IN p_quantity    DECIMAL(10,3),
    IN p_unit_price  DECIMAL(12,2),
    IN p_discount    DECIMAL(12,2)
)
BEGIN
    DECLARE v_invoice_date DATE          DEFAULT NULL;
    DECLARE v_status       VARCHAR(10)   DEFAULT NULL;
    DECLARE v_unit         VARCHAR(10)   DEFAULT NULL;
    DECLARE v_price        DECIMAL(12,2) DEFAULT NULL;
    DECLARE v_tax_rate     DECIMAL(5,2)  DEFAULT NULL;
    DECLARE v_pct          DECIMAL(5,2)  DEFAULT 0;
    DECLARE v_discount     DECIMAL(12,2) DEFAULT 0;
    DECLARE v_old_qty      DECIMAL(10,3) DEFAULT NULL;
    DECLARE v_line_number  INT           DEFAULT NULL;
    DECLARE v_not_found    BOOLEAN       DEFAULT FALSE;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_not_found = TRUE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET @retail_line_write = NULL;
        RESIGNAL;
    END;
    IF p_quantity IS NULL OR p_quantity <= 0 OR p_barcode IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Barcode is required and quantity must be greater than zero (BR7)';
    END IF;

    SELECT `Date`, Status INTO v_invoice_date, v_status
    FROM   Invoice
    WHERE  Invoice_ID = p_invoice_id
    FOR UPDATE;

    IF v_not_found OR v_invoice_date IS NULL OR v_status <> 'PAID' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invoice not found or is not editable';
    END IF;

    SET v_not_found = FALSE;
    SELECT Unit, Tax_Rate INTO v_unit, v_tax_rate
    FROM   Product
    WHERE  Barcode = p_barcode;

    IF v_not_found OR v_unit IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unknown product barcode';
    END IF;

    IF v_unit IN ('PIECE','PACK','BOX') AND p_quantity <> FLOOR(p_quantity) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Packaged products require an integer quantity (BR7)';
    END IF;

    SET v_price = fn_get_product_price(p_barcode, v_invoice_date);
    IF v_price IS NULL OR v_price <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No valid price exists for this product on invoice date (BR6/BR8)';
    END IF;

    SET v_pct = fn_active_promo_pct(p_barcode, v_invoice_date);

    SET v_not_found = FALSE;
    SELECT Line_Number, Quantity INTO v_line_number, v_old_qty
    FROM   Invoice_Line
    WHERE  Invoice_ID = p_invoice_id AND Barcode = p_barcode
    FOR UPDATE;

    IF NOT v_not_found THEN
        SET v_old_qty = v_old_qty + p_quantity;
        SET v_discount = ROUND(v_old_qty * v_price * v_pct / 100, 2);
        SET @retail_line_write = 1;
        UPDATE Invoice_Line
        SET    Quantity = v_old_qty,
               Discount = v_discount
        WHERE  Invoice_ID = p_invoice_id AND Line_Number = v_line_number;
        SET @retail_line_write = NULL;
    ELSE
        SELECT COALESCE(MAX(Line_Number), 0) + 1 INTO v_line_number
        FROM   Invoice_Line
        WHERE  Invoice_ID = p_invoice_id;
        SET v_discount = ROUND(p_quantity * v_price * v_pct / 100, 2);
        SET @retail_line_write = 1;
        INSERT INTO Invoice_Line
            (Invoice_ID, Line_Number, Barcode, Quantity, Unit_Price, Discount, Tax_Rate)
        VALUES
            (p_invoice_id, v_line_number, p_barcode, p_quantity, v_price, v_discount, v_tax_rate);
        SET @retail_line_write = NULL;
    END IF;
END$$

-- 5.3 sp_add_payment (BR12, BR13): add a payment
CREATE PROCEDURE sp_add_payment(
    IN p_payment_id VARCHAR(20),
    IN p_invoice_id VARCHAR(30),
    IN p_amount     DECIMAL(12,2),
    IN p_method     VARCHAR(20),
    IN p_detail1    VARCHAR(100),
    IN p_detail2    VARCHAR(100)
)
BEGIN
    DECLARE v_status VARCHAR(10)   DEFAULT NULL;
    DECLARE v_due    DECIMAL(12,2) DEFAULT 0;
    DECLARE v_paid   DECIMAL(12,2) DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET @retail_payment_write = NULL;
        RESIGNAL;
    END;
    IF p_payment_id IS NULL OR p_amount IS NULL OR p_amount <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Payment ID and positive amount are required';
    END IF;

    SELECT Status INTO v_status
    FROM   Invoice
    WHERE  Invoice_ID = p_invoice_id
    FOR UPDATE;

    IF v_status IS NULL OR v_status <> 'PAID' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Payment requires an existing checkout invoice';
    END IF;

    SET v_due = fn_invoice_amount_due(p_invoice_id);
    SET v_paid = fn_invoice_amount_paid(p_invoice_id);
    IF v_due <= 0 OR p_amount > v_due - v_paid THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Applied payment exceeds the remaining invoice balance (BR13)';
    END IF;

    IF p_method = 'CASH' THEN
        IF p_detail1 IS NULL OR CAST(p_detail1 AS DECIMAL(12,2)) < p_amount THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Cash tendered must be at least the applied amount (BR13)';
        END IF;
    ELSEIF p_method = 'CARD' THEN
        IF p_detail1 IS NULL OR p_detail2 IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Card number and authorization code are required';
        END IF;
    ELSEIF p_method = 'EWALLET' THEN
        IF p_detail1 IS NULL OR p_detail2 IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Wallet provider and transaction reference are required';
        END IF;
    ELSE
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unsupported payment method';
    END IF;

    SET @retail_payment_write = 1;
    INSERT INTO Payment (Payment_ID, Amount, Invoice_ID)
    VALUES (p_payment_id, p_amount, p_invoice_id);

    IF p_method = 'CASH' THEN
        INSERT INTO Cash_Payment (Payment_ID, Tendered_Amount)
        VALUES (p_payment_id, CAST(p_detail1 AS DECIMAL(12,2)));
    ELSEIF p_method = 'CARD' THEN
        INSERT INTO Card_Payment (Payment_ID, Card_number, Auth_code)
        VALUES (p_payment_id, p_detail1, p_detail2);
    ELSE
        INSERT INTO EWallet_Payment (Payment_ID, Wallet_provider, Transaction_Ref)
        VALUES (p_payment_id, p_detail1, p_detail2);
    END IF;
    SET @retail_payment_write = NULL;
END$$

-- 5.4 sp_settle_invoice (BR3, BR14): finish an invoice
CREATE PROCEDURE sp_settle_invoice(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    DECLARE v_status   VARCHAR(10)  DEFAULT NULL;
    DECLARE v_has_line INT          DEFAULT 0;
    DECLARE v_msg      VARCHAR(255);
    SELECT Status INTO v_status
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

    IF v_has_line = 0 THEN
        SET v_msg = CONCAT('Hoa don ', p_invoice_id, ' khong co dong hang nao (BR3)');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    IF fn_invoice_amount_paid(p_invoice_id) < fn_invoice_amount_due(p_invoice_id) THEN
        SET v_msg = CONCAT('Hoa don ', p_invoice_id, ' chua thu du tien: phai thu ',
                           fn_invoice_amount_due(p_invoice_id), ', moi ghi nhan ',
                           fn_invoice_amount_paid(p_invoice_id), ' (BR14)');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;
END$$

-- 5.5 sp_void_invoice (BR3): a manager cancels a paid invoice
CREATE PROCEDURE sp_void_invoice(
    IN p_invoice_id    VARCHAR(30),
    IN p_supervisor_id VARCHAR(10)
)
BEGIN
    DECLARE v_status VARCHAR(10) DEFAULT NULL;
    DECLARE v_role   VARCHAR(10) DEFAULT NULL;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET @retail_manager_void = NULL;
        RESIGNAL;
    END;
    SELECT Status INTO v_status
    FROM   Invoice
    WHERE  Invoice_ID = p_invoice_id
    FOR UPDATE;

    IF v_status IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invoice not found';
    END IF;

    SELECT Role INTO v_role
    FROM   Cashier
    WHERE  Cashier_ID = p_supervisor_id;

    IF v_role IS NULL OR v_role <> 'MANAGER' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Only a MANAGER may void a settled invoice (BR3)';
    END IF;

    IF v_status <> 'PAID' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invoice is already VOIDED';
    END IF;

    IF EXISTS (SELECT 1 FROM `Return` WHERE Invoice_ID = p_invoice_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invoice has returns and must stay PAID; it cannot be voided (BR16)';
    END IF;

    SET @retail_manager_void = 1;
    UPDATE Invoice SET Status = 'VOIDED' WHERE Invoice_ID = p_invoice_id;
    SET @retail_manager_void = NULL;
END$$

-- 5.6 sp_add_return (BR7, BR15, BR16): record a return
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
    DECLARE v_unit      VARCHAR(10)   DEFAULT NULL;
    DECLARE v_returned  DECIMAL(10,3) DEFAULT 0;
    DECLARE v_remaining DECIMAL(10,3) DEFAULT 0;
    DECLARE v_return_id INT           DEFAULT 1;
    DECLARE v_role      VARCHAR(10)   DEFAULT NULL;
    DECLARE v_ret_date  DATE          DEFAULT NULL;
    DECLARE v_not_found BOOLEAN       DEFAULT FALSE;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_not_found = TRUE;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET @retail_return_write = NULL;
        RESIGNAL;
    END;
    IF p_quantity IS NULL OR p_quantity <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Return quantity must be greater than zero (BR16)';
    END IF;

    SELECT Role INTO v_role
    FROM   Cashier
    WHERE  Cashier_ID = p_approved_by;

    IF v_role IS NULL OR v_role <> 'MANAGER' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Only a MANAGER may approve a return (BR15)';
    END IF;

    SET v_not_found = FALSE;
    SELECT l.Quantity, i.Status, p.Unit
    INTO   v_line_qty, v_status, v_unit
    FROM   Invoice_Line l
    JOIN   Invoice      i ON i.Invoice_ID = l.Invoice_ID
    JOIN   Product      p ON p.Barcode    = l.Barcode
    WHERE  l.Invoice_ID  = p_invoice_id
      AND  l.Line_Number = p_line_number
    FOR UPDATE;

    IF v_not_found OR v_line_qty IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Original invoice line not found';
    END IF;

    IF v_status <> 'PAID' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Only original PAID invoices may be returned (BR16)';
    END IF;

    IF v_unit IN ('PIECE','PACK','BOX') AND p_quantity <> FLOOR(p_quantity) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Packaged products (PIECE/PACK/BOX) require an integer return quantity (BR7/BR16)';
    END IF;

    SELECT COALESCE(SUM(Return_Quantity), 0) INTO v_returned
    FROM   `Return`
    WHERE  Invoice_ID = p_invoice_id AND Line_Number = p_line_number;

    SET v_remaining = v_line_qty - v_returned;
    IF p_quantity > v_remaining THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Return quantity exceeds remaining purchased quantity (BR16)';
    END IF;

    SET v_ret_date = COALESCE(p_return_date, CURDATE());
    SET v_return_id = fn_next_return_id(p_invoice_id, p_line_number);
    SET @retail_return_write = 1;
    INSERT INTO `Return`
        (Invoice_ID, Line_Number, Return_ID, Return_Date, Return_Quantity, Approved_By)
    VALUES
        (p_invoice_id, p_line_number, v_return_id, v_ret_date, p_quantity, p_approved_by);
    SET @retail_return_write = NULL;
END$$

-- 5.7 sp_delete_return: block deleting returns
CREATE PROCEDURE sp_delete_return(
    IN  p_invoice_id  VARCHAR(30),
    IN  p_line_number INT,
    OUT p_deleted     INT
)
BEGIN
    SET p_deleted = 0;
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Return records are immutable for audit; use an approved correction workflow';
END$$

-- 5.8 sp_delete_invoice: block deleting finished invoices
CREATE PROCEDURE sp_delete_invoice(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Finalized invoices and their payment/return history cannot be deleted; use maintenance wipe only';
END$$

-- 5.9 sp_update_invoice_header (BR2, BR3, BR4, BR10): edit an invoice header
CREATE PROCEDURE sp_update_invoice_header(
    IN p_invoice_id  VARCHAR(30),
    IN p_date        DATE,
    IN p_time        TIME,
    IN p_status      ENUM('PAID','VOIDED'),
    IN p_counter_id  VARCHAR(10),
    IN p_cashier_id  VARCHAR(10),
    IN p_customer_id VARCHAR(15)
)
BEGIN
    DECLARE v_date    DATE        DEFAULT NULL;
    DECLARE v_time    TIME        DEFAULT NULL;
    DECLARE v_status  VARCHAR(10) DEFAULT NULL;
    DECLARE v_counter VARCHAR(10) DEFAULT NULL;
    DECLARE v_cashier VARCHAR(10) DEFAULT NULL;

    SELECT `Date`, `Time`, Status, Counter_ID, Cashier_ID
    INTO   v_date, v_time, v_status, v_counter, v_cashier
    FROM   Invoice
    WHERE  Invoice_ID = p_invoice_id
    FOR UPDATE;

    IF v_status IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invoice not found';
    END IF;

    IF NOT (p_date <=> v_date) OR NOT (p_time <=> v_time)
       OR NOT (p_status <=> v_status) OR NOT (p_counter_id <=> v_counter)
       OR NOT (p_cashier_id <=> v_cashier) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invoice timestamp, counter, cashier and status are immutable (BR2/BR3/BR4)';
    END IF;

    IF p_customer_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM Customer WHERE Customer_ID = p_customer_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unknown customer (BR10)';
    END IF;

    UPDATE Invoice
    SET    Customer_ID = p_customer_id
    WHERE  Invoice_ID = p_invoice_id;
END$$

-- 5.10 sp_update_invoice_line (BR8): block editing lines after checkout
CREATE PROCEDURE sp_update_invoice_line(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT,
    IN p_barcode     VARCHAR(20),
    IN p_quantity    DECIMAL(10,3),
    IN p_unit_price  DECIMAL(12,2),
    IN p_discount    DECIMAL(12,2)
)
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Invoice lines are immutable after checkout; price and discount cannot be edited (BR8)';
END$$

-- 5.11 sp_delete_invoice_line (BR16): block deleting lines after checkout
CREATE PROCEDURE sp_delete_invoice_line(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT
)
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'A finalized invoice line cannot be deleted; preserve the original sale and return history (BR16)';
END$$

-- PART 6: DATA CLEANUP PROCEDURES

-- 6.1 sp_wipe_transactions: delete the 7 transaction tables, keep the 8 catalog tables
CREATE PROCEDURE sp_wipe_transactions()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET @retail_maintenance = NULL;
        RESIGNAL;
    END;

    SET @retail_maintenance = 1;
    DELETE FROM `Return`;
    DELETE FROM Cash_Payment;
    DELETE FROM Card_Payment;
    DELETE FROM EWallet_Payment;
    DELETE FROM Payment;
    DELETE FROM Invoice_Line;
    DELETE FROM Invoice;
    SET @retail_maintenance = NULL;
END$$

-- 6.2 sp_wipe_all_data: delete all 15 tables
CREATE PROCEDURE sp_wipe_all_data()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SET @retail_maintenance = NULL;
        RESIGNAL;
    END;

    SET @retail_maintenance = 1;
    CALL sp_wipe_transactions();
    SET @retail_maintenance = 1;

    DELETE FROM Applies_To;
    DELETE FROM Promotion;
    DELETE FROM Price_History;
    DELETE FROM Product;
    DELETE FROM Customer;
    DELETE FROM Counter;
    DELETE FROM Shop;

    UPDATE Cashier SET Supervisor_ID = NULL;
    DELETE FROM Cashier;
    SET @retail_maintenance = NULL;
END$$

-- PART 7: TRIGGERS FOR THE 16 BUSINESS RULES

-- BR6/BR8: Price_History rows cannot be updated or deleted
CREATE TRIGGER trg_price_history_bi
BEFORE INSERT ON Price_History
FOR EACH ROW
BEGIN
    IF COALESCE(@retail_price_write, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Use sp_add_price_history to append a new effective-dated price (BR6/BR8)';
    END IF;
END$$

CREATE TRIGGER trg_price_history_bu
BEFORE UPDATE ON Price_History
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Price history is immutable; insert a new effective-dated price instead (BR6/BR8)';
END$$

CREATE TRIGGER trg_price_history_bd
BEFORE DELETE ON Price_History
FOR EACH ROW
BEGIN
    IF COALESCE(@retail_maintenance, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Price history cannot be deleted (BR6/BR8)';
    END IF;
END$$

-- BR1/BR2: server clock is used, invoice ID must have the right format
CREATE TRIGGER trg_invoice_bi
BEFORE INSERT ON Invoice
FOR EACH ROW
BEGIN
    DECLARE v_shop_id         VARCHAR(10) DEFAULT NULL;
    DECLARE v_expected_prefix VARCHAR(64);
    DECLARE v_server_now      DATETIME;

    IF COALESCE(@retail_create_invoice, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invoices must be created through sp_create_invoice_header (BR1/BR3)';
    END IF;

    SELECT Shop_ID INTO v_shop_id
    FROM   Counter
    WHERE  Counter_ID = NEW.Counter_ID;

    SET v_server_now = NOW();
    SET NEW.`Date` = DATE(v_server_now);
    SET NEW.`Time` = TIME(v_server_now);
    SET v_expected_prefix = CONCAT(v_shop_id, '-', NEW.Counter_ID, '-',
                                   DATE_FORMAT(NEW.`Date`, '%Y%m%d'), '-');

    IF v_shop_id IS NULL
       OR LEFT(NEW.Invoice_ID, CHAR_LENGTH(v_expected_prefix)) <> v_expected_prefix THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invoice_ID must contain the server shop, counter and date prefix (BR1)';
    END IF;
END$$

-- BR1/BR2/BR3/BR4: invoice header cannot change; only a manager can void a paid invoice
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

-- BR5/BR7/BR8/BR9/BR11: lock price, tax and best promotion when a line is added
CREATE TRIGGER trg_invoice_line_bi
BEFORE INSERT ON Invoice_Line
FOR EACH ROW
BEGIN
    DECLARE v_invoice_date DATE          DEFAULT NULL;
    DECLARE v_status       VARCHAR(10)   DEFAULT NULL;
    DECLARE v_unit         VARCHAR(10)   DEFAULT NULL;
    DECLARE v_price        DECIMAL(12,2) DEFAULT NULL;
    DECLARE v_tax          DECIMAL(5,2)  DEFAULT NULL;
    DECLARE v_pct          DECIMAL(5,2)  DEFAULT 0;

    IF COALESCE(@retail_line_write, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invoice lines must be created or accumulated through sp_add_invoice_line (BR5/BR7)';
    END IF;

    SELECT `Date`, Status INTO v_invoice_date, v_status
    FROM   Invoice
    WHERE  Invoice_ID = NEW.Invoice_ID;

    IF v_status IS NULL OR v_status <> 'PAID' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invoice line requires an active checkout invoice';
    END IF;

    IF EXISTS (SELECT 1 FROM Payment WHERE Invoice_ID = NEW.Invoice_ID) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invoice lines cannot be added after payment begins';
    END IF;

    SELECT Unit, Tax_Rate INTO v_unit, v_tax
    FROM   Product
    WHERE  Barcode = NEW.Barcode;

    IF v_unit IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unknown product barcode';
    END IF;

    IF NEW.Quantity <= 0
       OR (v_unit IN ('PIECE','PACK','BOX') AND NEW.Quantity <> FLOOR(NEW.Quantity)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid quantity for product unit (BR7)';
    END IF;

    SET v_price = fn_get_product_price(NEW.Barcode, v_invoice_date);
    IF v_price IS NULL OR v_price <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No valid price on invoice date (BR6/BR8)';
    END IF;

    SET v_pct = fn_active_promo_pct(NEW.Barcode, v_invoice_date);
    SET NEW.Unit_Price = v_price;
    SET NEW.Tax_Rate   = v_tax;
    SET NEW.Discount   = ROUND(NEW.Quantity * v_price * v_pct / 100, 2);
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

CREATE TRIGGER trg_invoice_line_bd
BEFORE DELETE ON Invoice_Line
FOR EACH ROW
BEGIN
    IF COALESCE(@retail_maintenance, 0) <> 1
       AND (EXISTS (SELECT 1 FROM Payment WHERE Invoice_ID = OLD.Invoice_ID)
            OR EXISTS (SELECT 1 FROM `Return`
                       WHERE Invoice_ID = OLD.Invoice_ID AND Line_Number = OLD.Line_Number)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Paid or returned invoice lines cannot be deleted (BR16)';
    END IF;
END$$

-- BR7/BR15/BR16: return rules (manager approval, paid invoice, valid date and quantity)
CREATE TRIGGER trg_return_bi
BEFORE INSERT ON `Return`
FOR EACH ROW
BEGIN
    DECLARE v_role     VARCHAR(10)   DEFAULT NULL;
    DECLARE v_status   VARCHAR(10)   DEFAULT NULL;
    DECLARE v_inv_date DATE          DEFAULT NULL;
    DECLARE v_unit     VARCHAR(10)   DEFAULT NULL;
    DECLARE v_qty      DECIMAL(10,3) DEFAULT NULL;
    DECLARE v_returned DECIMAL(10,3) DEFAULT 0;

    IF COALESCE(@retail_return_write, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Returns must be recorded through sp_add_return';
    END IF;

    SELECT Role INTO v_role
    FROM   Cashier
    WHERE  Cashier_ID = NEW.Approved_By;

    IF v_role IS NULL OR v_role <> 'MANAGER' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Return approver must have MANAGER role (BR15)';
    END IF;

    SELECT i.Status, i.`Date`, l.Quantity, p.Unit
    INTO   v_status, v_inv_date, v_qty, v_unit
    FROM   Invoice      i
    JOIN   Invoice_Line l ON l.Invoice_ID = i.Invoice_ID
    JOIN   Product      p ON p.Barcode    = l.Barcode
    WHERE  l.Invoice_ID  = NEW.Invoice_ID
      AND  l.Line_Number = NEW.Line_Number;

    IF v_status IS NULL OR v_status <> 'PAID' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Returns are allowed only for original PAID invoices (BR16)';
    END IF;

    IF NEW.Return_Date < v_inv_date THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Return_Date cannot be earlier than original Invoice Date (BR16)';
    END IF;

    IF NEW.Return_Quantity <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Return quantity must be greater than zero (BR16)';
    END IF;

    IF v_unit IN ('PIECE','PACK','BOX') AND NEW.Return_Quantity <> FLOOR(NEW.Return_Quantity) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Packaged products (PIECE/PACK/BOX) require an integer return quantity (BR7/BR16)';
    END IF;

    SELECT COALESCE(SUM(Return_Quantity), 0) INTO v_returned
    FROM   `Return`
    WHERE  Invoice_ID = NEW.Invoice_ID AND Line_Number = NEW.Line_Number;

    IF v_returned + NEW.Return_Quantity > v_qty THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cumulative return quantity exceeds the purchased quantity (BR16)';
    END IF;
END$$

CREATE TRIGGER trg_return_bu
BEFORE UPDATE ON `Return`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Return records are immutable for audit (BR16)';
END$$

CREATE TRIGGER trg_return_bd
BEFORE DELETE ON `Return`
FOR EACH ROW
BEGIN
    IF COALESCE(@retail_maintenance, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Return records cannot be deleted outside maintenance';
    END IF;
END$$

-- Payment types: each payment has exactly one type (BR12, BR13)
CREATE TRIGGER trg_payment_bi
BEFORE INSERT ON Payment
FOR EACH ROW
BEGIN
    IF COALESCE(@retail_payment_write, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Payment must be created through sp_add_payment (ISA Total)';
    END IF;
END$$

CREATE TRIGGER trg_payment_bu
BEFORE UPDATE ON Payment
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Payment records are immutable';
END$$

CREATE TRIGGER trg_payment_bd
BEFORE DELETE ON Payment
FOR EACH ROW
BEGIN
    IF COALESCE(@retail_maintenance, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Payment records cannot be deleted outside maintenance';
    END IF;
END$$

CREATE TRIGGER trg_cash_payment_bi
BEFORE INSERT ON Cash_Payment
FOR EACH ROW
BEGIN
    DECLARE v_amount DECIMAL(12,2) DEFAULT NULL;
    IF COALESCE(@retail_payment_write, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Use sp_add_payment for cash payments';
    END IF;
    IF EXISTS (SELECT 1 FROM Card_Payment WHERE Payment_ID = NEW.Payment_ID)
       OR EXISTS (SELECT 1 FROM EWallet_Payment WHERE Payment_ID = NEW.Payment_ID) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payment subtype violates ISA Disjoint';
    END IF;
    SELECT Amount INTO v_amount FROM Payment WHERE Payment_ID = NEW.Payment_ID;
    IF v_amount IS NULL OR NEW.Tendered_Amount < v_amount THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cash tendered must be >= applied payment amount (BR13)';
    END IF;
END$$

CREATE TRIGGER trg_card_payment_bi
BEFORE INSERT ON Card_Payment
FOR EACH ROW
BEGIN
    DECLARE v_invoice_id VARCHAR(30) DEFAULT NULL;
    IF COALESCE(@retail_payment_write, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Use sp_add_payment for card payments';
    END IF;
    IF EXISTS (SELECT 1 FROM Cash_Payment WHERE Payment_ID = NEW.Payment_ID)
       OR EXISTS (SELECT 1 FROM EWallet_Payment WHERE Payment_ID = NEW.Payment_ID) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payment subtype violates ISA Disjoint';
    END IF;
    SELECT Invoice_ID INTO v_invoice_id FROM Payment WHERE Payment_ID = NEW.Payment_ID;
    IF v_invoice_id IS NULL OR fn_invoice_amount_paid(v_invoice_id) > fn_invoice_amount_due(v_invoice_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Card payment cannot exceed invoice balance (BR13)';
    END IF;
END$$

CREATE TRIGGER trg_ewallet_payment_bi
BEFORE INSERT ON EWallet_Payment
FOR EACH ROW
BEGIN
    DECLARE v_invoice_id VARCHAR(30) DEFAULT NULL;
    IF COALESCE(@retail_payment_write, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Use sp_add_payment for e-wallet payments';
    END IF;
    IF EXISTS (SELECT 1 FROM Cash_Payment WHERE Payment_ID = NEW.Payment_ID)
       OR EXISTS (SELECT 1 FROM Card_Payment WHERE Payment_ID = NEW.Payment_ID) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payment subtype violates ISA Disjoint';
    END IF;
    SELECT Invoice_ID INTO v_invoice_id FROM Payment WHERE Payment_ID = NEW.Payment_ID;
    IF v_invoice_id IS NULL OR fn_invoice_amount_paid(v_invoice_id) > fn_invoice_amount_due(v_invoice_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'E-wallet payment cannot exceed invoice balance (BR13)';
    END IF;
END$$

CREATE TRIGGER trg_cash_payment_bu BEFORE UPDATE ON Cash_Payment FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payment subtype records are immutable'; END$$
CREATE TRIGGER trg_card_payment_bu BEFORE UPDATE ON Card_Payment FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payment subtype records are immutable'; END$$
CREATE TRIGGER trg_ewallet_payment_bu BEFORE UPDATE ON EWallet_Payment FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payment subtype records are immutable'; END$$
CREATE TRIGGER trg_cash_payment_bd BEFORE DELETE ON Cash_Payment FOR EACH ROW
BEGIN IF COALESCE(@retail_maintenance, 0) <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payment subtype records cannot be deleted outside maintenance'; END IF; END$$
CREATE TRIGGER trg_card_payment_bd BEFORE DELETE ON Card_Payment FOR EACH ROW
BEGIN IF COALESCE(@retail_maintenance, 0) <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payment subtype records cannot be deleted outside maintenance'; END IF; END$$
CREATE TRIGGER trg_ewallet_payment_bd BEFORE DELETE ON EWallet_Payment FOR EACH ROW
BEGIN IF COALESCE(@retail_maintenance, 0) <> 1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payment subtype records cannot be deleted outside maintenance'; END IF; END$$

DELIMITER ;

-- View 1: change given back for each cash payment
CREATE OR REPLACE VIEW vw_cash_change_due AS
SELECT ca.Payment_ID,
       p.Invoice_ID,
       ca.Tendered_Amount,
       p.Amount                                 AS Paid_Amount,
       (ca.Tendered_Amount - p.Amount)          AS Change_Due
FROM Cash_Payment ca
JOIN Payment p ON p.Payment_ID = ca.Payment_ID;

-- View 2: total of each invoice line
CREATE OR REPLACE VIEW vw_invoice_line_total AS
SELECT l.Invoice_ID,
       l.Line_Number,
       l.Barcode,
       l.Quantity,
       l.Unit_Price,
       l.Discount,
       (l.Quantity * l.Unit_Price - l.Discount) AS Total
FROM Invoice_Line l;

SELECT '04_routines.sql created successfully' AS ket_qua;
