-- 05_invariants.sql -- Data checks

USE retail_billing;

DROP PROCEDURE IF EXISTS sp_check_seed;

DELIMITER $$

-- sp_check_seed: run all data checks and return the violations
CREATE PROCEDURE sp_check_seed(OUT p_violations INT)
BEGIN
    DECLARE v_days INT DEFAULT 90;

    DROP TEMPORARY TABLE IF EXISTS tmp_check_seed;
    CREATE TEMPORARY TABLE tmp_check_seed (
        STT      INT          NOT NULL,
        Quy_tac  VARCHAR(5)   NOT NULL,
        Noi_dung VARCHAR(90)  NOT NULL,
        Vi_pham  INT          NOT NULL,
        Hop_dong VARCHAR(28)  NOT NULL
    ) ENGINE=InnoDB;

    -- R1 -- BR6 / BR8: line price equals the product price on the sale date
    INSERT INTO tmp_check_seed VALUES (1, 'R1',
        'Don gia = gia tai ngay ban (fn_get_product_price)',
        (SELECT COUNT(*)
         FROM   Invoice_Line l
         JOIN   Invoice      i ON i.Invoice_ID = l.Invoice_ID
         WHERE  fn_get_product_price(l.Barcode, i.`Date`) IS NULL
            OR  l.Unit_Price <> fn_get_product_price(l.Barcode, i.`Date`)),
        'BR6 / BR8');

    -- R2 -- BR5 / BR9 / BR11: line discount matches the formula
    INSERT INTO tmp_check_seed VALUES (2, 'R2',
        'Giam gia = cong thuc tai ngay ban (fn_line_discount)',
        (SELECT COUNT(*)
         FROM   Invoice_Line l
         JOIN   Invoice      i ON i.Invoice_ID = l.Invoice_ID
         WHERE  l.Discount <> fn_line_discount(l.Barcode, i.`Date`,
                                               l.Quantity, l.Unit_Price)),
        'BR5 / BR9 / BR11');

    -- R3 -- BR3 / BR14: a PAID invoice is fully paid and has at least 1 line
    INSERT INTO tmp_check_seed VALUES (3, 'R3',
        'PAID thi da thu du tien VA co dong hang',
        (SELECT COUNT(*)
         FROM   Invoice i
         WHERE  i.Status = 'PAID'
           AND (NOT EXISTS (SELECT 1 FROM Invoice_Line l
                            WHERE l.Invoice_ID = i.Invoice_ID)
                OR fn_invoice_amount_paid(i.Invoice_ID)
                 < fn_invoice_amount_due(i.Invoice_ID))),
        'BR3 / BR14');

    -- R4 -- BR3 / BR14: a VOIDED invoice was also fully paid before
    INSERT INTO tmp_check_seed VALUES (4, 'R4',
        'VOIDED cung da thu du tien luc lap (khong xoa Payment)',
        (SELECT COUNT(*)
         FROM   Invoice i
         WHERE  i.Status = 'VOIDED'
           AND  EXISTS (SELECT 1 FROM Invoice_Line l
                        WHERE l.Invoice_ID = i.Invoice_ID)
           AND  fn_invoice_amount_paid(i.Invoice_ID)
              < fn_invoice_amount_due(i.Invoice_ID)),
        'BR3 / BR14');

    -- R5 -- BR13: cash tendered >= amount for cash payments
    INSERT INTO tmp_check_seed VALUES (5, 'R5',
        'Tien khach dua >= tien ghi nhan (Cash_Payment)',
        (SELECT COUNT(*)
         FROM   Cash_Payment c
         JOIN   Payment      p ON p.Payment_ID = c.Payment_ID
         WHERE  c.Tendered_Amount < p.Amount),
        'BR13');

    -- R6 -- BR12 / BR13: each payment has exactly one type
    INSERT INTO tmp_check_seed VALUES (6, 'R6',
        'Moi Payment nam o dung MOT bang con (ISA Total & Disjoint)',
        (SELECT COUNT(*)
         FROM   Payment p
         LEFT JOIN Cash_Payment    ca ON ca.Payment_ID = p.Payment_ID
         LEFT JOIN Card_Payment    cd ON cd.Payment_ID = p.Payment_ID
         LEFT JOIN EWallet_Payment ew ON ew.Payment_ID = p.Payment_ID
         WHERE  (ca.Payment_ID IS NOT NULL)
              + (cd.Payment_ID IS NOT NULL)
              + (ew.Payment_ID IS NOT NULL) <> 1),
        'BR12 / BR13');

    -- R7 -- BR7 / BR16: total returned quantity <= quantity bought
    INSERT INTO tmp_check_seed VALUES (7, 'R7',
        'Tong SL tra cua mot dong <= SL da mua',
        (SELECT COUNT(*)
         FROM  (SELECT r.Invoice_ID, r.Line_Number,
                       SUM(r.Return_Quantity) AS da_tra
                FROM   `Return` r
                GROUP BY r.Invoice_ID, r.Line_Number) t
         JOIN   Invoice_Line l ON l.Invoice_ID  = t.Invoice_ID
                              AND l.Line_Number = t.Line_Number
         WHERE  t.da_tra > l.Quantity),
        'BR7 / BR16');

    -- R8 -- BR2 / BR16: return date is between sale date and today
    INSERT INTO tmp_check_seed VALUES (8, 'R8',
        'Ngay ban <= ngay tra <= hom nay',
        (SELECT COUNT(*)
         FROM   `Return` r
         JOIN   Invoice  i ON i.Invoice_ID = r.Invoice_ID
         WHERE  r.Return_Date < i.`Date`
            OR  r.Return_Date > CURDATE()),
        'BR2 / BR16');

    -- R9 -- BR15 / BR16: invoices with returns stay PAID
    INSERT INTO tmp_check_seed VALUES (9, 'R9',
        'Hoa don tra hang phai PAID VA do MANAGER duyet',
        (SELECT COUNT(*)
         FROM   `Return` r
         JOIN   Invoice  i ON i.Invoice_ID = r.Invoice_ID
         JOIN   Cashier  c ON c.Cashier_ID = r.Approved_By
         WHERE  i.Status <> 'PAID'
            OR  c.Role   <> 'MANAGER'),
        'BR15 / BR16');

    -- R10 -- BR2: no invoice is in the future
    INSERT INTO tmp_check_seed VALUES (10, 'R10',
        'Khong co hoa don o ngay tuong lai',
        (SELECT COUNT(*) FROM Invoice WHERE `Date` > CURDATE()),
        'BR2');

    -- R11 -- BR1: invoice ID has the format <Shop>-<Counter>-<YYYYMMDD>-<SEQ>
    INSERT INTO tmp_check_seed VALUES (11, 'R11',
        'Ma hoa don khop <Shop>-<Counter>-<YYYYMMDD>-<SEQ>',
        (SELECT COUNT(*)
         FROM   Invoice i
         JOIN   Counter c ON c.Counter_ID = i.Counter_ID
         WHERE  i.Invoice_ID NOT REGEXP CONCAT(
                    '^', c.Shop_ID, '-', i.Counter_ID, '-',
                    DATE_FORMAT(i.`Date`, '%Y%m%d'), '-[0-9]{4,}$'
                )),
        'BR1');

    -- R12 -- every day in the seed window has at least 1 invoice
    INSERT INTO tmp_check_seed VALUES (12, 'R12',
        CONCAT(v_days + 1, ' ngay gan nhat deu co hoa don'),
        (SELECT (v_days + 1) - COUNT(DISTINCT `Date`)
         FROM   Invoice
         WHERE  `Date` BETWEEN DATE_SUB(CURDATE(), INTERVAL v_days DAY)
                           AND CURDATE()),
        'Seed Coverage');

    -- R13 -- BR16: return numbers of each line go 1, 2, ..., n
    INSERT INTO tmp_check_seed VALUES (13, 'R13',
        'Return_ID cua moi dong lien tuc tu 1',
        (SELECT COUNT(*)
         FROM  (SELECT Invoice_ID, Line_Number,
                       COUNT(*) AS so_luot, MAX(Return_ID) AS so_lon_nhat
                FROM   `Return`
                GROUP BY Invoice_ID, Line_Number) t
         WHERE  t.so_luot <> t.so_lon_nhat),
        'BR16');

    -- R14 -- BR11: promotion Expiry_Date >= Promo_End_date
    INSERT INTO tmp_check_seed VALUES (14, 'R14',
        'Expiry_Date cua khuyen mai >= Promo_End_date',
        (SELECT COUNT(*)
         FROM   Promotion  p
         JOIN   Applies_To a ON a.Promo_ID = p.Promo_ID
         WHERE  p.Expiry_Date < a.Promo_End_date),
        'BR11');

    -- R15 -- BR4 / BR10: Cashier_ID and Customer_ID (if any) must exist
    INSERT INTO tmp_check_seed VALUES (15, 'R15',
        'Cashier_ID hop le va Customer_ID (neu co) khong rong',
        (SELECT COUNT(*)
         FROM   Invoice i
         LEFT JOIN Cashier  ca ON ca.Cashier_ID  = i.Cashier_ID
         LEFT JOIN Customer cu ON cu.Customer_ID = i.Customer_ID
         WHERE  ca.Cashier_ID IS NULL
            OR (i.Customer_ID IS NOT NULL
                AND (TRIM(i.Customer_ID) = '' OR cu.Customer_ID IS NULL))),
        'BR4 / BR10');

    -- R16 -- BR7 / BR9: packed units (PIECE/PACK/BOX) must have whole quantities
    INSERT INTO tmp_check_seed VALUES (16, 'R16',
        'SL nguyen cho hang dong goi va thanh tien dong >= 0',
        (SELECT COUNT(*)
         FROM   Invoice_Line l
         JOIN   Product      p ON p.Barcode = l.Barcode
         WHERE  (p.Unit IN ('PIECE', 'PACK', 'BOX')
                 AND l.Quantity <> FLOOR(l.Quantity))
            OR  (l.Quantity * l.Unit_Price - l.Discount) < 0),
        'BR7 / BR9');

    -- R17 (BR17): invoice status is only PAID or VOIDED, no draft in DB
    INSERT INTO tmp_check_seed VALUES (17, 'R17',
        'Trang thai chi PAID/VOIDED (khong luu Draft trong CSDL)',
        (SELECT COUNT(*)
         FROM   Invoice i
         WHERE  i.Status NOT IN ('PAID', 'VOIDED')
            OR  TRIM(COALESCE(i.Status, '')) = ''),
        'BR17');

    -- Result table and total number of violations
    SELECT  Quy_tac                                  AS Quy_tac,
            Noi_dung                                 AS Noi_dung,
            Vi_pham                                  AS Vi_pham,
            IF(Vi_pham = 0, 'dat', 'SAI')            AS Ket_luan,
            Hop_dong                                 AS Hop_dong
    FROM    tmp_check_seed
    ORDER BY STT;

    SELECT COALESCE(SUM(Vi_pham), 0) INTO p_violations FROM tmp_check_seed;

    DROP TEMPORARY TABLE tmp_check_seed;
END$$

DELIMITER ;

SELECT 'sp_check_seed da tao xong. Chay: CALL sp_check_seed(@v); SELECT @v;' AS ket_qua;
CALL sp_check_seed(@v); SELECT @v;
