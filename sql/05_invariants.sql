-- =====================================================================
--  05_invariants.sql -- 14 bat bien cua du lieu, va mot thu tuc de kiem
--  Chay bang:  mysql -u root -p retail_billing < 05_invariants.sql
--              roi:  CALL sp_check_seed(@v); SELECT @v;
--              hoac: .\scripts\ps1\check-seed.ps1  (bash: check-seed.sh)
--  Dac ta: plan/15 muc 3 va muc 4.7. Moi quy tac tra ve SO DONG VI PHAM --
--  tat ca phai bang 0.
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
-- sp_check_seed: chay 14 quy tac, tra ve mot bang (moi quy tac mot dong)
--                va tong so vi pham qua tham so OUT.
--
--   Doc ket qua: cot Vi_pham phai bang 0 o CA 14 DONG. Dong nao khac 0 thi
--   cot Hop_dong chi ra muc nao cua plan/15 dang bi vi pham.
--
--   R1 va R2 goi ham tren TUNG DONG HANG (~1.100 dong x 2 ham). Day la phep
--   kiem dat nhat trong file, va la cach duy nhat de kiem DUNG CAI APP SE
--   TINH thay vi kiem lai mot bieu thuc chep tay. Nguong do: duoi 10 giay
--   (kich ban #88).
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_check_seed(OUT p_violations INT)
BEGIN
    -- So ngay ma seed cam ket phu kin, PHAI khop tham so p_days cua
    -- CALL sp_seed_demo_data(...) o cuoi 02_seed.sql. Doi mot ben thi R12
    -- bao vi pham -- va do la dung: hai con so nay la MOT cam ket.
    DECLARE v_days INT DEFAULT 90;

    DROP TEMPORARY TABLE IF EXISTS tmp_check_seed;
    CREATE TEMPORARY TABLE tmp_check_seed (
        STT      INT          NOT NULL,
        Quy_tac  VARCHAR(5)   NOT NULL,
        Noi_dung VARCHAR(80)  NOT NULL,
        Vi_pham  INT          NOT NULL,
        Hop_dong VARCHAR(24)  NOT NULL
    ) ENGINE=InnoDB;

    -- -----------------------------------------------------------------
    -- R1 -- bat bien G1 (muc 3.2): don gia cua moi dong hang BANG gia tai
    --       ngay ban. Gia NULL (san pham chua co gia tai ngay do) cung la
    --       vi pham: vong lap sinh seed phai BO QUA dong do, khong ghi no.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (1, 'R1',
        'Don gia = gia tai ngay ban (fn_get_product_price)',
        (SELECT COUNT(*)
         FROM   Invoice_Line l
         JOIN   Invoice      i ON i.Invoice_ID = l.Invoice_ID
         WHERE  fn_get_product_price(l.Barcode, i.`Date`) IS NULL
            OR  l.Unit_Price <> fn_get_product_price(l.Barcode, i.`Date`)),
        'muc 3.2 (G1)');

    -- -----------------------------------------------------------------
    -- R2 -- bat bien G2 (muc 3.3): tien giam cua moi dong hang BANG cong
    --       thuc tai ngay ban. Ca seed va phep kiem goi CUNG MOT ham
    --       (fn_line_discount), nen rui ro #1/#2 cua muc 8 la that -- cai
    --       chot cho no la kich ban #76 (so ham voi sp_get_active_promo)
    --       va #77 (so voi NewInvoicePanel.lineDiscount cua Java).
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (2, 'R2',
        'Giam gia = cong thuc tai ngay ban (fn_line_discount)',
        (SELECT COUNT(*)
         FROM   Invoice_Line l
         JOIN   Invoice      i ON i.Invoice_ID = l.Invoice_ID
         WHERE  l.Discount <> fn_line_discount(l.Barcode, i.`Date`,
                                               l.Quantity, l.Unit_Price)),
        'muc 3.3 (G2)');

    -- -----------------------------------------------------------------
    -- R3 -- bat bien G3: 'PAID' nghia la DA THU DU VA CO DONG HANG.
    --       Hoa don rong khong bao gio PAID: fn_invoice_amount_due tra
    --       0.00 cho no nen phep so sanh mot minh se noi "da thu du"
    --       (04_routines.sql muc 1.8, plan/13 muc 3.4).
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
        'muc 3.4 (G3)');

    -- -----------------------------------------------------------------
    -- R4 -- mat con lai cua G3: 'OPEN' co dong hang va da thu du tien
    --       nghia la sp_settle_invoice dang ra DA phai chuyen no sang
    --       'PAID'. Quy tac nay bat loi "quen goi sp_settle_invoice".
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (4, 'R4',
        'OPEN thi chua thu du tien (neu khong sp_settle_invoice da chuyen)',
        (SELECT COUNT(*)
         FROM   Invoice i
         WHERE  i.Status = 'OPEN'
           AND  EXISTS (SELECT 1 FROM Invoice_Line l
                        WHERE l.Invoice_ID = i.Invoice_ID)
           AND  fn_invoice_amount_paid(i.Invoice_ID)
             >= fn_invoice_amount_due(i.Invoice_ID)),
        'muc 3.4 (G3)');

    -- -----------------------------------------------------------------
    -- R5 -- bat bien G4 (plan/14 muc 3.1): tien khach dua >= tien ghi nhan.
    --       DAY CHINH LA kich ban #4 cua khoi 5, va an le cua no la loi #1
    --       cua plan/14: thu ngan go 500.000 va app ghi 500 dong.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (5, 'R5',
        'Tien khach dua >= tien ghi nhan (Cash_Payment)',
        (SELECT COUNT(*)
         FROM   Cash_Payment c
         JOIN   Payment      p ON p.Payment_ID = c.Payment_ID
         WHERE  c.Tendered_Amount < p.Amount),
        'muc 3.4 (G4)');

    -- -----------------------------------------------------------------
    -- R6 -- bat bien G5 (01_schema.sql:212-216): moi dong Payment nam o
    --       DUNG MOT trong ba bang con. Khong o bang nao = thanh toan
    --       khong ro phuong thuc; o hai bang = hai phuong thuc cho mot lan.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (6, 'R6',
        'Moi Payment nam o dung MOT bang con (thua ke)',
        (SELECT COUNT(*)
         FROM   Payment p
         LEFT JOIN Cash_Payment    ca ON ca.Payment_ID = p.Payment_ID
         LEFT JOIN Card_Payment    cd ON cd.Payment_ID = p.Payment_ID
         LEFT JOIN EWallet_Payment ew ON ew.Payment_ID = p.Payment_ID
         WHERE  (ca.Payment_ID IS NOT NULL)
              + (cd.Payment_ID IS NOT NULL)
              + (ew.Payment_ID IS NOT NULL) <> 1),
        'muc 3.4 (G5)');

    -- -----------------------------------------------------------------
    -- R7 -- bat bien G6: khong tra qua so da mua, tinh THEO TUNG DONG
    --       (khoa chinh bang `Return` la (Invoice_ID, Line_Number,
    --       Return_ID) nen mot dong co the co nhieu luot tra).
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
        'muc 3.4 (G6)');

    -- -----------------------------------------------------------------
    -- R8 -- bat bien G6: ngay tra nam trong [ngay ban, hom nay]. Tra hang
    --       truoc khi ban, hoac tra o tuong lai, deu la du lieu bay.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (8, 'R8',
        'Ngay ban <= ngay tra <= hom nay',
        (SELECT COUNT(*)
         FROM   `Return` r
         JOIN   Invoice  i ON i.Invoice_ID = r.Invoice_ID
         WHERE  r.Return_Date < i.`Date`
            OR  r.Return_Date > CURDATE()),
        'muc 3.4 (G6)');

    -- -----------------------------------------------------------------
    -- R9 -- bat bien G6: chi hoa don 'PAID' moi co luot tra. Day chinh la
    --       phep kiem ma sp_add_return SIGNAL o :931 -- quy tac nay bat
    --       truong hop du lieu di vao bang `Return` KHONG qua thu tuc do.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (9, 'R9',
        'Chi hoa don PAID moi co luot tra hang',
        (SELECT COUNT(*)
         FROM   `Return` r
         JOIN   Invoice  i ON i.Invoice_ID = r.Invoice_ID
         WHERE  i.Status <> 'PAID'),
        'muc 3.4 (G6)');

    -- -----------------------------------------------------------------
    -- R10 -- khong ban o tuong lai. Moi moc ngay cua seed la CURDATE() - n
    --        (muc 3.2), nen mot hoa don o ngay mai nghia la mot dau tru bi
    --        go thanh dau cong.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (10, 'R10',
        'Khong co hoa don o ngay tuong lai',
        (SELECT COUNT(*) FROM Invoice WHERE `Date` > CURDATE()),
        'muc 3.5');

    -- -----------------------------------------------------------------
    -- R11 -- ma INV-nnnn lien tuc tu 1: so hoa don khop so lon nhat.
    --        Lech nghia la seed da bo mot ma (goi fn_next_invoice_id roi
    --        khong dung ket qua) hoac da go tay mot ma.
    --        INV-OLD-* khong tinh: chung khong khop '^INV-[0-9]+$', y nhu
    --        fn_next_invoice_id bo qua chung (04_routines.sql:106).
    --        Khong co hoa don nao: 0 = 0 -> khong vi pham.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (11, 'R11',
        'Ma INV-nnnn lien tuc (COUNT = MAX so thu tu)',
        (SELECT CASE WHEN COUNT(*) <> COALESCE(
                          MAX(CAST(SUBSTRING(Invoice_ID, 5) AS UNSIGNED)), 0)
                     THEN 1 ELSE 0 END
         FROM   Invoice
         WHERE  Invoice_ID REGEXP '^INV-[0-9]+$'),
        'muc 3.1');

    -- -----------------------------------------------------------------
    -- R12 -- MUC TIEU CUA KHOI 6, DUOI DANG MOT PHEP KIEM (muc 2.1 diem 1):
    --        moi ngay trong [hom nay - v_days, hom nay] deu co hoa don.
    --        Cua so co dung v_days + 1 ngay, nen "so ngay trong khong co
    --        hoa don" = (v_days + 1) - so ngay KHAC NHAU co hoa don.
    --        Phep tru nay dung vi R10 da chot rang khong co ngay tuong lai.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (12, 'R12',
        CONCAT(v_days + 1, ' ngay gan nhat deu co hoa don'),
        (SELECT (v_days + 1) - COUNT(DISTINCT `Date`)
         FROM   Invoice
         WHERE  `Date` BETWEEN DATE_SUB(CURDATE(), INTERVAL v_days DAY)
                           AND CURDATE()),
        'muc 2.1 diem 1');

    -- -----------------------------------------------------------------
    -- R13 -- Return_ID cua MOI DONG lien tuc tu 1, dung nhu fn_next_return_id
    --        danh so (04_routines.sql muc 1.7). Lech nghia la co ai do go
    --        tay Return_ID thay vi de thu tuc sinh -- loi #4 cua plan/15.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (13, 'R13',
        'Return_ID cua moi dong lien tuc tu 1',
        (SELECT COUNT(*)
         FROM  (SELECT Invoice_ID, Line_Number,
                       COUNT(*) AS so_luot, MAX(Return_ID) AS so_lon_nhat
                FROM   `Return`
                GROUP BY Invoice_ID, Line_Number) t
         WHERE  t.so_luot <> t.so_lon_nhat),
        'muc 3.4');

    -- -----------------------------------------------------------------
    -- R14 -- khuyen mai khong het hieu luc TRUOC khi cua so dong.
    --        sp_get_active_promo loc ca hai: p_date trong cua so Applies_To
    --        VA p.Expiry_Date >= p_date. Nen mot dot ap dung ket thuc SAU
    --        Expiry_Date se "hut o cuoi": tra cuu lich su o nhung ngay cuoi
    --        cua so tra ve rong du bang Applies_To co dong phu dung ngay do.
    --        Phuong an A cua muc 3.3 dua tren dieu nay, nen no phai co chot.
    -- -----------------------------------------------------------------
    INSERT INTO tmp_check_seed VALUES (14, 'R14',
        'Expiry_Date cua khuyen mai >= ngay dong cua so Applies_To',
        (SELECT COUNT(*)
         FROM   Promotion  p
         JOIN   Applies_To a ON a.Promo_ID = p.Promo_ID
         WHERE  p.Expiry_Date < a.Promo_End_date),
        'muc 3.3 phuong an A');

    -- -----------------------------------------------------------------
    -- Bang ket qua + tong. Cot ket luan de doc nhanh bang mat; con so la
    -- cai quyet dinh.
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
