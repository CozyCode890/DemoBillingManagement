-- =====================================================================
--  05_invariants.sql -- Kiem tra bat bien du lieu cho Retail Invoicing (Topic 19)
--  Chay bang:  mysql -u root -p retail_billing < 05_invariants.sql
--              roi:  CALL sp_check_seed(@v); SELECT @v;
--  Doi chieu 01_schema.sql, 02_seed.sql, 04_routines.sql (BR1 - BR17).
--  Moi quy tac tra ve SO DONG VI PHAM -- tat ca phai bang 0.
-- =====================================================================
--  VI SAO LA MOT FILE RIENG, KHONG NAM TRONG 04_routines.sql:
--  04_routines.sql la HOP DONG voi tang Java (docs/05 muc 6) -- moi routine
--  trong do co mot cho goi trong app. sp_check_seed khong co: no la mot
--  dung cu kiem, giong tools/check/ContractCheck.java cua khoi 5, chi khac
--  la bat bien ve DU LIEU thi kiem bang SQL ngan hon nhieu.
--
--  Chay duoc BAT CU LUC NAO, ke ca sau khi nguoi dung da bam chuot lam ban
--  database -- do la diem khac quan trong nhat so voi "kiem luc nap seed".
--
--  KHONG DUNG SIGNAL, dung OUT p_violations (plan/15 muc 4.7):
--  mot thu tuc vua tra bang vua SIGNAL thi phia Java nhan exception SAU KHI
--  da doc xong bang, va chi goi phai bat loi o giua vong lap doc ket qua.
--  Voi OUT, script doc MOT SO NGUYEN de quyet dinh exit code, con
--  ContractCheck doc CA HAI: bang de in ra, so de cong vao fail.
-- =====================================================================

USE retail_billing;

DROP PROCEDURE IF EXISTS sp_check_seed;

DELIMITER $$

-- ---------------------------------------------------------------------
-- sp_check_seed: chay cac quy tac kiem tra bat bien du lieu, tra ve
--                bang ket qua tung quy tac va tong so vi pham qua OUT.
--
--   Doc ket qua: cot Vi_pham phai bang 0 o TAT CA CAC DONG. Dong nao
--   khac 0 thi cot Hop_dong chi ra Business Rule / pham vi vi pham.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_check_seed(OUT p_violations INT)
BEGIN
    -- So ngay ma seed cam ket phu kin, PHAI khop tham so p_days cua
    -- CALL sp_seed_demo_data(...) o cuoi 02_seed.sql (mac dinh 90 => 91 ngay).
    DECLARE v_days INT DEFAULT 90;

    DROP TEMPORARY TABLE IF EXISTS tmp_check_seed;
    CREATE TEMPORARY TABLE tmp_check_seed (
        STT      INT          NOT NULL,
        Quy_tac  VARCHAR(5)   NOT NULL,
        Noi_dung VARCHAR(90)  NOT NULL,
        Vi_pham  INT          NOT NULL,
        Hop_dong VARCHAR(28)  NOT NULL
    ) ENGINE=InnoDB;

    -- -----------------------------------------------------------------
    -- R1 -- BR6 / BR8: Don gia cua moi dong hang BANG gia hieu luc tai
    --       ngay ban (Valid_From_Date <= Invoice.Date gan nhat).
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (1, 'R1',
        'Don gia = gia tai ngay ban (fn_get_product_price)',
        (SELECT COUNT(*)
         FROM   Invoice_Line l
         JOIN   Invoice      i ON i.Invoice_ID = l.Invoice_ID
         WHERE  fn_get_product_price(l.Barcode, i.`Date`) IS NULL
            OR  l.Unit_Price <> fn_get_product_price(l.Barcode, i.`Date`)),
        'BR6 / BR8');

    -- -----------------------------------------------------------------
    -- R2 -- BR5 / BR9 / BR11: Tien giam cua moi dong hang BANG cong thuc
    --       khuyen mai tot nhat (khong cong don) tai ngay ban.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (2, 'R2',
        'Giam gia = cong thuc tai ngay ban (fn_line_discount)',
        (SELECT COUNT(*)
         FROM   Invoice_Line l
         JOIN   Invoice      i ON i.Invoice_ID = l.Invoice_ID
         WHERE  l.Discount <> fn_line_discount(l.Barcode, i.`Date`,
                                               l.Quantity, l.Unit_Price)),
        'BR5 / BR9 / BR11');

    -- -----------------------------------------------------------------
    -- R3 -- BR3 / BR14: 'PAID' nghia la DA THU DU VA CO >= 1 DONG HANG.
    -- -----------------------------------------------------------------
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

    -- -----------------------------------------------------------------
    -- R4 -- BR3 / BR14: Hoa don 'VOIDED' cung da duoc thu du tien truoc
    --       khi bi huy (huy hoa don khong xoa lich su Payment).
    -- -----------------------------------------------------------------
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

    -- -----------------------------------------------------------------
    -- R5 -- BR13: Tien khach dua >= tien ghi nhan voi thanh toan tien mat.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (5, 'R5',
        'Tien khach dua >= tien ghi nhan (Cash_Payment)',
        (SELECT COUNT(*)
         FROM   Cash_Payment c
         JOIN   Payment      p ON p.Payment_ID = c.Payment_ID
         WHERE  c.Tendered_Amount < p.Amount),
        'BR13');

    -- -----------------------------------------------------------------
    -- R6 -- BR12 / BR13 (Payment ISA Total & Disjoint): moi dong Payment
    --       nam o DUNG MOT trong ba bang con (Cash, Card, EWallet).
    -- -----------------------------------------------------------------
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

    -- -----------------------------------------------------------------
    -- R7 -- BR7 / BR16: Tong so luong tra cua moi dong hang <= so mua.
    -- -----------------------------------------------------------------
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

    -- -----------------------------------------------------------------
    -- R8 -- BR2 / BR16: Ngay tra hang nam trong [ngay ban, hom nay].
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (8, 'R8',
        'Ngay ban <= ngay tra <= hom nay',
        (SELECT COUNT(*)
         FROM   `Return` r
         JOIN   Invoice  i ON i.Invoice_ID = r.Invoice_ID
         WHERE  r.Return_Date < i.`Date`
            OR  r.Return_Date > CURDATE()),
        'BR2 / BR16');

    -- -----------------------------------------------------------------
    -- R9 -- BR15 / BR16: Hoa don co luot tra hang giu trang thai PAID
    --       VA nguoi duyet tra hang (Approved_By) phai la MANAGER.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (9, 'R9',
        'Hoa don tra hang phai PAID VA do MANAGER duyet',
        (SELECT COUNT(*)
         FROM   `Return` r
         JOIN   Invoice  i ON i.Invoice_ID = r.Invoice_ID
         JOIN   Cashier  c ON c.Cashier_ID = r.Approved_By
         WHERE  i.Status <> 'PAID'
            OR  c.Role   <> 'MANAGER'),
        'BR15 / BR16');

    -- -----------------------------------------------------------------
    -- R10 -- BR2: Khong co hoa don o ngay tuong lai so voi dong ho server.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (10, 'R10',
        'Khong co hoa don o ngay tuong lai',
        (SELECT COUNT(*) FROM Invoice WHERE `Date` > CURDATE()),
        'BR2');

    -- -----------------------------------------------------------------
    -- R11 -- BR1: Ma hoa don dung dinh dang <Shop>-<Counter>-<YYYYMMDD>-<SEQ>
    --        khop voi Counter_ID -> Shop_ID va Invoice.Date.
    -- -----------------------------------------------------------------
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

    -- -----------------------------------------------------------------
    -- R12 -- Do phu du lieu seed: moi ngay trong [CURDATE() - v_days, CURDATE()]
    --        deu co it nhat 1 hoa don (v_days + 1 ngay).
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (12, 'R12',
        CONCAT(v_days + 1, ' ngay gan nhat deu co hoa don'),
        (SELECT (v_days + 1) - COUNT(DISTINCT `Date`)
         FROM   Invoice
         WHERE  `Date` BETWEEN DATE_SUB(CURDATE(), INTERVAL v_days DAY)
                           AND CURDATE()),
        'Seed Coverage');

    -- -----------------------------------------------------------------
    -- R13 -- BR16: Return_ID cua moi dong hoa don lien tuc tu 1 (1..n).
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (13, 'R13',
        'Return_ID cua moi dong lien tuc tu 1',
        (SELECT COUNT(*)
         FROM  (SELECT Invoice_ID, Line_Number,
                       COUNT(*) AS so_luot, MAX(Return_ID) AS so_lon_nhat
                FROM   `Return`
                GROUP BY Invoice_ID, Line_Number) t
         WHERE  t.so_luot <> t.so_lon_nhat),
        'BR16');

    -- -----------------------------------------------------------------
    -- R14 -- BR11: Expiry_Date cua khuyen mai >= Promo_End_date.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (14, 'R14',
        'Expiry_Date cua khuyen mai >= Promo_End_date',
        (SELECT COUNT(*)
         FROM   Promotion  p
         JOIN   Applies_To a ON a.Promo_ID = p.Promo_ID
         WHERE  p.Expiry_Date < a.Promo_End_date),
        'BR11');

    -- -----------------------------------------------------------------
    -- R15 -- BR4 / BR10: Cashier_ID va Customer_ID (neu co) phai ton tai
    --        hop le, khong rong/ky tu trang.
    -- -----------------------------------------------------------------
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

    -- -----------------------------------------------------------------
    -- R16 -- BR7 / BR9: Don vi dong goi (PIECE/PACK/BOX) phai la so nguyen
    --        va thanh tien dong >= 0.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (16, 'R16',
        'SL nguyen cho hang dong goi va thanh tien dong >= 0',
        (SELECT COUNT(*)
         FROM   Invoice_Line l
         JOIN   Product      p ON p.Barcode = l.Barcode
         WHERE  (p.Unit IN ('PIECE', 'PACK', 'BOX')
                 AND l.Quantity <> FLOOR(l.Quantity))
            OR  (l.Quantity * l.Unit_Price - l.Discount) < 0),
        'BR7 / BR9');

    -- -----------------------------------------------------------------
    -- R17 -- BR3 / BR17: Trang thai hoa don chi thuoc PAID/VOIDED, khong
    --        co trang thai nhap/Draft hay hoa don PAID bi xoa sach dong hang.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (17, 'R17',
        'Trang thai chi PAID/VOIDED (khong luu Draft trong CSDL)',
        (SELECT COUNT(*)
         FROM   Invoice i
         WHERE  i.Status NOT IN ('PAID', 'VOIDED')
            OR  TRIM(COALESCE(i.Status, '')) = ''),
        'BR3 / BR17');

    -- -----------------------------------------------------------------
    -- Bang ket qua + tong so vi pham qua tham so OUT.
    -- -----------------------------------------------------------------
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