-- 01_schema.sql -- Create the database and its 15 tables

DROP DATABASE IF EXISTS retail_billing;
CREATE DATABASE retail_billing
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE retail_billing;

-- 1. Shop -- one store in the chain
CREATE TABLE Shop (
    Shop_ID   VARCHAR(10)  NOT NULL,
    Address   VARCHAR(255) NOT NULL,
    CONSTRAINT PK_shop PRIMARY KEY (Shop_ID)
) ENGINE=InnoDB;

-- 2. Counter -- a checkout counter, belongs to one shop
CREATE TABLE Counter (
    Counter_ID VARCHAR(10) NOT NULL,
    Shop_ID    VARCHAR(10) NOT NULL,
    CONSTRAINT PK_counter PRIMARY KEY (Counter_ID),
    CONSTRAINT FK_counter_shop
        FOREIGN KEY (Shop_ID) REFERENCES Shop (Shop_ID)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB;

-- 3. Cashier -- staff member; Supervisor_ID points back to this table
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

-- 4. Customer -- member customers only
CREATE TABLE Customer (
    Customer_ID VARCHAR(15)  NOT NULL,
    Name        VARCHAR(100) NOT NULL,
    Phone       VARCHAR(20)  NOT NULL,
    CONSTRAINT PK_customer PRIMARY KEY (Customer_ID),
    UNIQUE KEY UQ_customer_phone (Phone)
) ENGINE=InnoDB;

-- 5. Product -- products (prices are kept in Price_History)
CREATE TABLE Product (
    Barcode  VARCHAR(20)  NOT NULL,
    Name     VARCHAR(150) NOT NULL,
    Unit     ENUM('PIECE','KG','LITER','PACK','BOX') NOT NULL,
    Tax_Rate DECIMAL(5,2) NOT NULL,
    CONSTRAINT PK_product PRIMARY KEY (Barcode),
    CONSTRAINT CK_product_tax CHECK (Tax_Rate >= 0 AND Tax_Rate <= 100)
) ENGINE=InnoDB;

-- 6. Price_History -- each row: from date X, product Y costs Z
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

-- 7. Promotion -- promotion programs
CREATE TABLE Promotion (
    Promo_ID         VARCHAR(15)  NOT NULL,
    Rule_Description VARCHAR(255) NOT NULL,
    Status           ENUM('ACTIVE','EXPIRED') NOT NULL,
    Expiry_Date      DATE NOT NULL,
    CONSTRAINT PK_promotion PRIMARY KEY (Promo_ID)
) ENGINE=InnoDB;

-- 8. Applies_To -- links products and promotions (many-to-many)
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

-- 9. Invoice -- invoice header
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

CREATE INDEX idx_invoice_date_counter ON Invoice (`Date`, Counter_ID);

-- 10. Invoice_Line -- invoice lines, key (Invoice_ID, Line_Number)
CREATE TABLE Invoice_Line (
    Invoice_ID  VARCHAR(30)   NOT NULL,
    Line_Number INT           NOT NULL,
    Barcode     VARCHAR(20)   NOT NULL,
    Quantity    DECIMAL(10,3) NOT NULL,
    Unit_Price  DECIMAL(12,2) NOT NULL,
    Discount    DECIMAL(12,2) NOT NULL DEFAULT 0,
    Tax_Rate    DECIMAL(5,2)  NOT NULL,
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

-- 11. Return -- a return, linked to one invoice line
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

-- 12. Payment -- one invoice can have many payments (BR12)
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

-- 13/14/15. The three payment types (cash, card, e-wallet)
CREATE TABLE Cash_Payment (
    Payment_ID      VARCHAR(20)   NOT NULL,
    Tendered_Amount DECIMAL(12,2) NOT NULL,
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
