-- =====================================================================
--  01_schema.sql  --  Tạo database và 15 bảng cho hệ thống Retail Invoicing.
--  Chạy bằng:  mysql -u root -p < 01_schema.sql
-- =====================================================================

DROP DATABASE IF EXISTS retail_billing;
CREATE DATABASE retail_billing
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE retail_billing;


-- ---------------------------------------------------------------------
-- 1. Shop -- một cửa hàng trong chuỗi
-- ---------------------------------------------------------------------
CREATE TABLE Shop (
    Shop_ID   VARCHAR(10)  NOT NULL,
    Address   VARCHAR(255) NOT NULL,
    CONSTRAINT PK_shop PRIMARY KEY (Shop_ID)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 2. Counter -- quầy tính tiền, thuộc về đúng 1 shop
-- ---------------------------------------------------------------------
CREATE TABLE Counter (
    Counter_ID VARCHAR(10) NOT NULL,
    Shop_ID    VARCHAR(10) NOT NULL,
    CONSTRAINT PK_counter PRIMARY KEY (Counter_ID),
    CONSTRAINT FK_counter_shop
        FOREIGN KEY (Shop_ID) REFERENCES Shop (Shop_ID)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 3. Cashier -- thu ngân. Supervisor_ID trỏ ngược về chính bảng này
--    (quan hệ tự tham chiếu). Cho phép NULL vì cấp cao nhất không có sếp.
--    Role: BR15 yêu cầu chỉ người có Role = MANAGER mới duyệt trả hàng
--    (được kiểm tra bằng trigger ở file 04_routines).
-- ---------------------------------------------------------------------
CREATE TABLE Cashier (
    Cashier_ID    VARCHAR(10)  NOT NULL,
    Name          VARCHAR(100) NOT NULL,
    Role          ENUM('CASHIER', 'MANAGER') NOT NULL,
    Supervisor_ID VARCHAR(10)  NULL,
    CONSTRAINT PK_cashier PRIMARY KEY (Cashier_ID),
    CONSTRAINT FK_cashier_supervisor
        FOREIGN KEY (Supervisor_ID) REFERENCES Cashier (Cashier_ID)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 4. Customer -- khách hàng (chỉ lưu khi khách có thẻ thành viên)
-- ---------------------------------------------------------------------
CREATE TABLE Customer (
    Customer_ID VARCHAR(15)  NOT NULL,
    Name        VARCHAR(100) NOT NULL,
    Phone       VARCHAR(20)  NOT NULL,
    CONSTRAINT PK_customer PRIMARY KEY (Customer_ID),
    UNIQUE KEY UQ_customer_phone (Phone)   -- số điện thoại không trùng
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 5. Product -- sản phẩm. KHÔNG có cột Price ở đây (giá ở Price_History).
-- ---------------------------------------------------------------------
CREATE TABLE Product (
    Barcode  VARCHAR(20)  NOT NULL,
    Name     VARCHAR(150) NOT NULL,
    Unit     ENUM('PIECE','KG','LITER','PACK','BOX') NOT NULL,
    Tax_Rate DECIMAL(5,2) NOT NULL,      -- ví dụ 8.00 nghĩa là VAT 8%
    CONSTRAINT PK_product PRIMARY KEY (Barcode),
    CONSTRAINT CK_product_tax CHECK (Tax_Rate >= 0 AND Tax_Rate <= 100)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 6. Price_History -- mỗi dòng = "từ ngày X, sản phẩm Y có giá Z".
--    Khóa chính (Barcode, Valid_From_Date) => hóa đơn cũ giữ giá cũ.
--    BR6/BR8: Valid_From_Date mới phải lớn hơn mọi ngày cũ của cùng
--    sản phẩm -> kiểm tra bằng trigger BEFORE INSERT (04_routines).
-- ---------------------------------------------------------------------
CREATE TABLE Price_History (
    Barcode         VARCHAR(20)   NOT NULL,
    Valid_From_Date DATE          NOT NULL,
    Price           DECIMAL(12,2) NOT NULL,
    CONSTRAINT PK_price_history PRIMARY KEY (Barcode, Valid_From_Date),
    CONSTRAINT CK_price_value CHECK (Price > 0),
    CONSTRAINT FK_price_product
        FOREIGN KEY (Barcode) REFERENCES Product (Barcode)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 7. Promotion -- chương trình khuyến mãi
-- ---------------------------------------------------------------------
CREATE TABLE Promotion (
    Promo_ID         VARCHAR(15)  NOT NULL,
    Rule_Description VARCHAR(255) NOT NULL,
    Status           ENUM('ACTIVE','EXPIRED') NOT NULL,
    Expiry_Date      DATE NOT NULL,
    CONSTRAINT PK_promotion PRIMARY KEY (Promo_ID)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 8. Applies_To -- bảng trung gian N-N giữa Product và Promotion.
--    Discount_Percent nằm ở đây vì mức giảm phụ thuộc cả cặp.
-- ---------------------------------------------------------------------
CREATE TABLE Applies_To (
    Barcode          VARCHAR(20)  NOT NULL,
    Promo_ID         VARCHAR(15)  NOT NULL,
    Promo_Start_date DATE         NOT NULL,
    Promo_End_date   DATE         NOT NULL,
    Discount_Percent DECIMAL(5,2) NOT NULL,
    CONSTRAINT PK_applies_to PRIMARY KEY (Barcode, Promo_ID),
    CONSTRAINT CK_promo_dates CHECK (Promo_Start_date <= Promo_End_date),
    CONSTRAINT CK_discount_percent CHECK (Discount_Percent >= 0 AND Discount_Percent <= 100),
    CONSTRAINT FK_applies_product
        FOREIGN KEY (Barcode)  REFERENCES Product   (Barcode)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT FK_applies_promo
        FOREIGN KEY (Promo_ID) REFERENCES Promotion (Promo_ID)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 9. Invoice -- phần ĐẦU hóa đơn.
--    Customer_ID NULL = khách vãng lai.
--    BR2: Date/Time mặc định lấy từ đồng hồ của SERVER (không phải máy
--    quầy). Trigger BEFORE INSERT (04_routines) ghi đè giá trị client gửi lên.
--    BR3: Status chỉ có hai giá trị PAID / VOIDED (ENUM tự chặn giá trị
--    khác với chế độ STRICT mặc định của MySQL 8.0).
-- ---------------------------------------------------------------------
CREATE TABLE Invoice (
    Invoice_ID  VARCHAR(30) NOT NULL,
    `Date`      DATE NOT NULL DEFAULT (CURRENT_DATE),
    `Time`      TIME NOT NULL DEFAULT (CURRENT_TIME),
    Status      ENUM('PAID','VOIDED') NOT NULL,
    Counter_ID  VARCHAR(10) NOT NULL,
    Cashier_ID  VARCHAR(10) NOT NULL,
    Customer_ID VARCHAR(15) NULL,
    CONSTRAINT PK_invoice PRIMARY KEY (Invoice_ID),
    CONSTRAINT FK_invoice_counter
        FOREIGN KEY (Counter_ID)  REFERENCES Counter  (Counter_ID)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT FK_invoice_cashier
        FOREIGN KEY (Cashier_ID)  REFERENCES Cashier  (Cashier_ID)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT FK_invoice_customer
        FOREIGN KEY (Customer_ID) REFERENCES Customer (Customer_ID)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB;

-- INDEX (yêu cầu mục 2.3): hỗ trợ CÂU 2 trong 03_queries.sql và
-- sp_report_daily_takings -- "Daily takings per shop for the last week".
-- Truy vấn lọc Invoice theo `Date` rồi nối sang Counter -> Shop qua Counter_ID.
CREATE INDEX idx_invoice_date_counter ON Invoice (`Date`, Counter_ID);


-- ---------------------------------------------------------------------
-- 10. Invoice_Line -- WEAK ENTITY, khóa chính (Invoice_ID, Line_Number).
--     BR7: UNIQUE (Invoice_ID, Barcode) -> mỗi sản phẩm tối đa 1 dòng
--          trên 1 hóa đơn (cộng dồn số lượng); Quantity > 0.
--     BR9: dòng không âm: Discount <= Quantity * Unit_Price.
--     Unit_Price và Tax_Rate được CHÉP LẠI lúc lập hóa đơn (giá đóng băng).
-- ---------------------------------------------------------------------
CREATE TABLE Invoice_Line (
    Invoice_ID  VARCHAR(30)   NOT NULL,
    Line_Number INT           NOT NULL,
    Barcode     VARCHAR(20)   NOT NULL,
    Quantity    DECIMAL(10,3) NOT NULL,
    Unit_Price  DECIMAL(12,2) NOT NULL,
    Discount    DECIMAL(12,2) NOT NULL DEFAULT 0,
    Tax_Rate    DECIMAL(5,2)  NOT NULL,                         -- Khóa cứng Tax lúc hoàn thành giao dịch
    CONSTRAINT PK_invoice_line PRIMARY KEY (Invoice_ID, Line_Number),
    UNIQUE KEY UQ_line_invoice_barcode (Invoice_ID, Barcode),
    CONSTRAINT CK_line_number   CHECK (Line_Number > 0),
    CONSTRAINT CK_line_tax      CHECK (Tax_Rate >= 0 AND Tax_Rate <= 100),
    CONSTRAINT CK_line_quantity CHECK (Quantity > 0),
    CONSTRAINT CK_line_price    CHECK (Unit_Price > 0),
    CONSTRAINT CK_line_discount CHECK (Discount >= 0),
    CONSTRAINT CK_line_disc_max CHECK (Discount <= Quantity * Unit_Price),
    CONSTRAINT FK_line_invoice
        FOREIGN KEY (Invoice_ID) REFERENCES Invoice (Invoice_ID)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT FK_line_product
        FOREIGN KEY (Barcode) REFERENCES Product (Barcode)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 11. Return -- trả hàng, gắn với DÒNG hóa đơn gốc.
--     Amount_refunded là thuộc tính DẪN XUẤT (nét đứt trong ERD) nên
--     không lưu: = Return_Quantity * ((Quantity*Unit_Price - Discount)/Quantity).
--     BR15: Approved_By phải là manager      -> trigger (04_routines)
--     BR16: Return_Quantity <= số lượng còn lại -> trigger (04_routines)
-- ---------------------------------------------------------------------
CREATE TABLE `Return` (
    Invoice_ID      VARCHAR(30)   NOT NULL,
    Line_Number     INT           NOT NULL,
    Return_ID       INT           NOT NULL,
    Return_Date     DATE          NOT NULL,
    Return_Quantity DECIMAL(10,3) NOT NULL,
    Approved_By     VARCHAR(10)   NOT NULL,
    CONSTRAINT PK_return PRIMARY KEY (Invoice_ID, Line_Number, Return_ID),
    CONSTRAINT CK_return_id       CHECK (Return_ID > 0),
    CONSTRAINT CK_return_quantity CHECK (Return_Quantity > 0),
    CONSTRAINT FK_return_line
        FOREIGN KEY (Invoice_ID, Line_Number) REFERENCES Invoice_Line (Invoice_ID, Line_Number)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT FK_return_cashier
        FOREIGN KEY (Approved_By) REFERENCES Cashier (Cashier_ID)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 12. Payment -- 1 hóa đơn có thể có NHIỀU dòng thanh toán (BR12).
--     BR14: tổng Amount >= tổng hóa đơn mới được PAID -> procedure/trigger.
--     (trả 200k tiền mặt + 300k thẻ = 2 dòng Payment cùng Invoice_ID)
-- ---------------------------------------------------------------------
CREATE TABLE Payment (
    Payment_ID VARCHAR(20)   NOT NULL,
    Amount     DECIMAL(12,2) NOT NULL,
    Invoice_ID VARCHAR(30)   NOT NULL,
    CONSTRAINT PK_payment PRIMARY KEY (Payment_ID),
    CONSTRAINT CK_payment_amount CHECK (Amount > 0),
    CONSTRAINT FK_payment_invoice
        FOREIGN KEY (Invoice_ID) REFERENCES Invoice (Invoice_ID)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 13/14/15. Ba bảng con của Payment
--     Payment_ID vừa là PRIMARY KEY vừa là FOREIGN KEY -> quan hệ 1-1.
--     Xóa Payment thì xóa luôn dòng chuyên biệt hóa -> CASCADE.
--     Ràng buộc "mỗi Payment nằm trong đúng một bảng con" cần trigger (04_routines).
--     BR13: Cash_Payment.Tendered_Amount >= Payment.Amount (trigger);
--           Card/E-wallet không có tiền thừa.
-- ---------------------------------------------------------------------
CREATE TABLE Cash_Payment (
    Payment_ID      VARCHAR(20)   NOT NULL,
    Tendered_Amount DECIMAL(12,2) NOT NULL,   -- khách đưa bao nhiêu tiền
    CONSTRAINT PK_cash_payment PRIMARY KEY (Payment_ID),
    CONSTRAINT CK_cash_tendered CHECK (Tendered_Amount > 0),
    CONSTRAINT FK_cash_payment
        FOREIGN KEY (Payment_ID) REFERENCES Payment (Payment_ID)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE Card_Payment (
    Payment_ID  VARCHAR(20) NOT NULL,
    Card_number VARCHAR(25) NOT NULL,
    Auth_code   VARCHAR(20) NOT NULL,
    CONSTRAINT PK_card_payment PRIMARY KEY (Payment_ID),
    CONSTRAINT FK_card_payment
        FOREIGN KEY (Payment_ID) REFERENCES Payment (Payment_ID)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE EWallet_Payment (
    Payment_ID      VARCHAR(20) NOT NULL,
    Wallet_provider VARCHAR(50) NOT NULL,
    Transaction_Ref VARCHAR(60) NOT NULL,
    CONSTRAINT PK_ewallet_payment PRIMARY KEY (Payment_ID),
    UNIQUE KEY UQ_wallet_txn (Transaction_Ref),
    CONSTRAINT FK_wallet_payment
        FOREIGN KEY (Payment_ID) REFERENCES Payment (Payment_ID)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB;

SELECT 'Schema da tao xong! 15 bang' AS ket_qua;