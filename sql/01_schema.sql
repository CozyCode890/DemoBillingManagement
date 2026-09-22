-- =====================================================================
--  01_schema.sql  --  Tạo database và 15 bảng cho hệ thống Retail Invoicing
--  Chạy bằng:  mysql -u root -p < 01_schema.sql
-- =====================================================================
--  LƯU Ý CHO NGƯỜI MỚI:
--  - ENGINE=InnoDB là bắt buộc nếu muốn dùng FOREIGN KEY và TRANSACTION.
--  - Thứ tự CREATE TABLE quan trọng: bảng cha phải có trước bảng con,
--    vì bảng con tham chiếu (REFERENCES) tới bảng cha.
--  - Bảng Return phải bọc trong dấu backtick vì RETURN là từ khóa MySQL.
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
    PRIMARY KEY (Shop_ID)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 2. Counter -- quầy tính tiền, thuộc về đúng 1 shop
-- ---------------------------------------------------------------------
CREATE TABLE Counter (
    Counter_ID VARCHAR(10) NOT NULL,
    Shop_ID    VARCHAR(10) NOT NULL,
    PRIMARY KEY (Counter_ID),
    CONSTRAINT fk_counter_shop
        FOREIGN KEY (Shop_ID) REFERENCES Shop (Shop_ID)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 3. Cashier -- thu ngân. Supervisor_ID trỏ ngược về chính bảng này
--    (quan hệ "tự tham chiếu": nhân viên có thể có người quản lý là
--     một nhân viên khác). Cho phép NULL vì sếp to nhất không có sếp.
-- ---------------------------------------------------------------------
CREATE TABLE Cashier (
    Cashier_ID    VARCHAR(10)  NOT NULL,
    Name          VARCHAR(100) NOT NULL,
    Supervisor_ID VARCHAR(10)  NULL,
    PRIMARY KEY (Cashier_ID),
    CONSTRAINT fk_cashier_supervisor
        FOREIGN KEY (Supervisor_ID) REFERENCES Cashier (Cashier_ID)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 4. Customer -- khách hàng (chỉ lưu khi khách có thẻ thành viên)
-- ---------------------------------------------------------------------
CREATE TABLE Customer (
    Customer_ID VARCHAR(15)  NOT NULL,
    Name        VARCHAR(100) NOT NULL,
    Phone       VARCHAR(20)  NOT NULL,
    PRIMARY KEY (Customer_ID),
    UNIQUE KEY uq_customer_phone (Phone)   -- số điện thoại không trùng
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 5. Product -- sản phẩm. KHÔNG có cột Price ở đây!
--    Giá nằm riêng ở bảng Price_History (xem giải thích bên dưới).
-- ---------------------------------------------------------------------
CREATE TABLE Product (
    Barcode  VARCHAR(20)  NOT NULL,
    Name     VARCHAR(150) NOT NULL,
    Unit     ENUM('PIECE','KG','LITER','PACK','BOX') NOT NULL,
    Tax_Rate DECIMAL(5,2) NOT NULL,      -- ví dụ 8.00 nghĩa là VAT 8%
    PRIMARY KEY (Barcode)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 6. Price_History -- *** BẢNG QUAN TRỌNG NHẤT CỦA ĐỀ BÀI ***
--    Mỗi dòng = "từ ngày X, sản phẩm Y có giá Z".
--    Khóa chính là (Barcode, Valid_From_Date): một sản phẩm chỉ có
--    đúng 1 giá bắt đầu từ 1 ngày cụ thể.
--    Nhờ bảng này mà hóa đơn cũ VẪN GIỮ giá cũ.
-- ---------------------------------------------------------------------
CREATE TABLE Price_History (
    Barcode         VARCHAR(20)   NOT NULL,
    Valid_From_Date DATE          NOT NULL,
    Price           DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (Barcode, Valid_From_Date),
    CONSTRAINT fk_price_product
        FOREIGN KEY (Barcode) REFERENCES Product (Barcode)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 7. Promotion -- chương trình khuyến mãi
-- ---------------------------------------------------------------------
CREATE TABLE Promotion (
    Promo_ID         VARCHAR(15)  NOT NULL,
    Rule_Description VARCHAR(255) NOT NULL,
    Status           ENUM('ACTIVE','PAUSED','EXPIRED') NOT NULL,
    Expiry_Date      DATE NOT NULL,
    PRIMARY KEY (Promo_ID)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 8. Applies_To -- bảng trung gian N-N: 1 khuyến mãi áp cho nhiều sản
--    phẩm, 1 sản phẩm có thể dính nhiều khuyến mãi.
--    Discount_Percent nằm Ở ĐÂY chứ không nằm ở Promotion, vì mức giảm
--    phụ thuộc vào CẢ CẶP (sản phẩm + khuyến mãi).
-- ---------------------------------------------------------------------
CREATE TABLE Applies_To (
    Barcode          VARCHAR(20)  NOT NULL,
    Promo_ID         VARCHAR(15)  NOT NULL,
    Promo_Start_date DATE         NOT NULL,
    Promo_End_date   DATE         NOT NULL,
    Discount_Percent DECIMAL(5,2) NOT NULL,
    PRIMARY KEY (Barcode, Promo_ID),
    CONSTRAINT fk_applies_product
        FOREIGN KEY (Barcode)  REFERENCES Product   (Barcode),
    CONSTRAINT fk_applies_promo
        FOREIGN KEY (Promo_ID) REFERENCES Promotion (Promo_ID)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 9. Invoice -- phần ĐẦU hóa đơn (thông tin chung, chưa có sản phẩm)
--    Customer_ID cho phép NULL = khách vãng lai không xuất trình thẻ.
-- ---------------------------------------------------------------------
CREATE TABLE Invoice (
    Invoice_ID  VARCHAR(30) NOT NULL,
    `Date`      DATE NOT NULL,
    `Time`      TIME NOT NULL,
    Status      ENUM('OPEN','PAID','VOIDED') NOT NULL,
    Counter_ID  VARCHAR(10) NOT NULL,
    Cashier_ID  VARCHAR(10) NOT NULL,
    Customer_ID VARCHAR(15) NULL,
    PRIMARY KEY (Invoice_ID),
    CONSTRAINT fk_invoice_counter
        FOREIGN KEY (Counter_ID)  REFERENCES Counter  (Counter_ID),
    CONSTRAINT fk_invoice_cashier
        FOREIGN KEY (Cashier_ID)  REFERENCES Cashier  (Cashier_ID),
    CONSTRAINT fk_invoice_customer
        FOREIGN KEY (Customer_ID) REFERENCES Customer (Customer_ID)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 10. Invoice_Line -- từng dòng sản phẩm trên hóa đơn
--     *** WEAK ENTITY ***: Line_Number chỉ có ý nghĩa BÊN TRONG 1 hóa đơn.
--     Dòng số 1 của HD001 và dòng số 1 của HD002 là hai thứ khác nhau.
--     => Khóa chính phải là CẶP (Invoice_ID, Line_Number).
--
--     Unit_Price được CHÉP LẠI từ Price_History lúc lập hóa đơn.
--     Đây không phải dư thừa dữ liệu -- đây là "giá lịch sử đóng băng".
-- ---------------------------------------------------------------------
CREATE TABLE Invoice_Line (
    Invoice_ID  VARCHAR(30)   NOT NULL,
    Line_Number INT           NOT NULL,
    Barcode     VARCHAR(20)   NOT NULL,
    Quantity    DECIMAL(10,3) NOT NULL,
    Unit_Price  DECIMAL(12,2) NOT NULL,
    Discount    DECIMAL(12,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (Invoice_ID, Line_Number),
    CONSTRAINT fk_line_invoice
        FOREIGN KEY (Invoice_ID) REFERENCES Invoice (Invoice_ID)
        ON DELETE CASCADE,          -- xóa hóa đơn thì xóa luôn các dòng
    CONSTRAINT fk_line_product
        FOREIGN KEY (Barcode) REFERENCES Product (Barcode)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 11. Return -- trả hàng, gắn với DÒNG hóa đơn gốc (không phải hóa đơn)
--     Khóa ngoại là CẶP (Invoice_ID, Line_Number) vì khóa chính bên
--     Invoice_Line cũng là một cặp.
-- ---------------------------------------------------------------------
CREATE TABLE `Return` (
    Invoice_ID      VARCHAR(30)   NOT NULL,
    Line_Number     INT           NOT NULL,
    Return_ID       INT           NOT NULL,
    Return_Date     DATE          NOT NULL,
    Return_Quantity DECIMAL(10,3) NOT NULL,
    Approved_By     VARCHAR(10)   NOT NULL,
    PRIMARY KEY (Invoice_ID, Line_Number, Return_ID),
    CONSTRAINT fk_return_line
        FOREIGN KEY (Invoice_ID, Line_Number)
        REFERENCES Invoice_Line (Invoice_ID, Line_Number),
    CONSTRAINT fk_return_cashier
        FOREIGN KEY (Approved_By) REFERENCES Cashier (Cashier_ID)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 12. Payment -- 1 hóa đơn có thể có NHIỀU dòng thanh toán
--     (trả 200k tiền mặt + 300k thẻ = 2 dòng Payment cùng Invoice_ID)
-- ---------------------------------------------------------------------
CREATE TABLE Payment (
    Payment_ID VARCHAR(20)   NOT NULL,
    Amount     DECIMAL(12,2) NOT NULL,
    Invoice_ID VARCHAR(30)   NOT NULL,
    PRIMARY KEY (Payment_ID),
    CONSTRAINT fk_payment_invoice
        FOREIGN KEY (Invoice_ID) REFERENCES Invoice (Invoice_ID)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 13/14/15. Ba bảng con của Payment -- đây là kỹ thuật "THỪA KẾ"
--     trong CSDL quan hệ.
--     Payment_ID vừa là PRIMARY KEY vừa là FOREIGN KEY -> quan hệ 1-1.
--     Mỗi dòng Payment chỉ xuất hiện ở ĐÚNG MỘT trong ba bảng này.
-- ---------------------------------------------------------------------
CREATE TABLE Cash_Payment (
    Payment_ID      VARCHAR(20)   NOT NULL,
    Tendered_Amount DECIMAL(12,2) NOT NULL,   -- khách đưa bao nhiêu tiền
    PRIMARY KEY (Payment_ID),
    CONSTRAINT fk_cash_payment
        FOREIGN KEY (Payment_ID) REFERENCES Payment (Payment_ID)
) ENGINE=InnoDB;

CREATE TABLE Card_Payment (
    Payment_ID  VARCHAR(20) NOT NULL,
    Card_number VARCHAR(25) NOT NULL,
    Auth_code   VARCHAR(20) NOT NULL,
    PRIMARY KEY (Payment_ID),
    CONSTRAINT fk_card_payment
        FOREIGN KEY (Payment_ID) REFERENCES Payment (Payment_ID)
) ENGINE=InnoDB;

CREATE TABLE EWallet_Payment (
    Payment_ID      VARCHAR(20) NOT NULL,
    Wallet_provider VARCHAR(50) NOT NULL,
    Transaction_Ref VARCHAR(60) NOT NULL,
    PRIMARY KEY (Payment_ID),
    UNIQUE KEY uq_wallet_txn (Transaction_Ref),
    CONSTRAINT fk_wallet_payment
        FOREIGN KEY (Payment_ID) REFERENCES Payment (Payment_ID)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- 16. Account -- Bảng lưu tài khoản người dùng và phân quyền hệ thống
-- ---------------------------------------------------------------------
CREATE TABLE Account (
    Username   VARCHAR(50)  NOT NULL,
    Password   VARCHAR(100) NOT NULL,
    Role       ENUM('cashier', 'manager') NOT NULL,
    Full_Name  VARCHAR(100) NULL,
    PRIMARY KEY (Username)
) ENGINE=InnoDB;


SELECT 'Schema da tao xong! 16 bang (bao gom Account).' AS ket_qua;
