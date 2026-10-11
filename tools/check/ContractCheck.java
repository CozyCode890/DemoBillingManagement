// Package billing.ui, nhung file nay nam o tools/check/ chu khong o src/:
// no CAN package nay de goi duoc NewInvoicePanel.splitPayment va lop Split
// (package-private), nhung KHONG duoc nam trong src/ vi run.ps1 / run.sh
// bien dich toan bo src/**/*.java vao app -- mot lop kiem thu khong nen di
// theo ban giao hang.
//
// Chay bang:  .\scripts\ps1\check-contracts.ps1   (hoac scripts/bash/check-contracts.sh)
package billing.ui;

import billing.dao.InvoiceDao;
import billing.dao.LookupDao;
import billing.dao.ProductDao;
import billing.db.QueryResult;
import billing.model.*;

import java.awt.HeadlessException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ====================================================================
 *  ContractCheck -- kiem BON HOP DONG cua tang giao dien bang code
 * ====================================================================
 *
 *  Bon hop dong (plan/14 muc 3, va docs/05 muc 7):
 *    1. TIEN MAT     -- tendered / recorded / change la ba con so o ba cho
 *    2. GIAM GIA     -- discount la ham cua (san pham, ngay, SO LUONG)
 *    3. DOC / HIEN SO -- hai quy tac doc so, mot ban chep moi phep dinh dang
 *    4. MA QR        -- tai khoan lay tu config.properties va CO TAC DUNG
 *
 *  NHOM SEED, them o khoi 6 (plan/15 muc 5.6): ba phep kiem cuoi file.
 *  Chung PHAI nam o day chu khong trong 05_invariants.sql vi hai trong ba
 *  so SQL VOI JAVA, nen chi chay duoc o cho goi duoc ca hai.
 *
 *  VI SAO CO FILE NAY: ba trong bon hop dong tren la "thu kiem duoc bang
 *  mat nhung sai thi khong ai thay". Vi du loi #1 cua plan/14: thu ngan go
 *  500.000 va app ghi vao database 500 dong -- sai dung 1000 lan, khong mot
 *  ngoai le, khong mot canh bao. Loai sai do phai co mot cai chot bang so.
 *
 *  PHAM VI: chi cac kich ban KHONG can bam chuot. Cac kich ban tuong tac
 *  Swing (popup khach hang, hoa don in ra, bam luu hai lan...) van phai kiem
 *  bang tay -- xem plan/14 muc 9.3 de biet cai nao da chay cai nao chua.
 *
 *  YEU CAU: MySQL dang chay va database retail_billing co du lieu seed.
 *
 *  CO GHI VAO DATABASE: phan e2e() lap 3 hoa don that roi XOA chung o khoi
 *  finally. No khong cham du lieu seed. Neu chuong trinh bi kill giua duong
 *  thi 3 hoa don do con lai -- xoa bang tab 3, hoac chay lai db-setup.
 *
 *  ---------------------------------------------------------------------
 *  MEO KY THUAT -- doc truoc khi sua file nay:
 *
 *  Ui.readMoney / Ui.readQuantity "tra null SAU KHI da hien thong bao", nen
 *  khong the phan biet "tu choi" voi "nhan gia tri null" ma khong bam duoc
 *  hop thoai. Cach giai: chay voi -Djava.awt.headless=true, luc do moi loi
 *  goi JOptionPane nem HeadlessException. Bat exception do = "da hien thong
 *  bao" = TU CHOI.
 *
 *  Nghia la: CHUONG TRINH NAY PHAI CHAY HEADLESS, khong thi cac phep kiem
 *  duong "tu choi" se treo o mot hop thoai modal khong ai bam. Hai script
 *  check-contracts.* da truyen san tham so do.
 *  ---------------------------------------------------------------------
 */
public class ContractCheck {

    static int pass = 0, fail = 0;

    static void check(String id, Object actual, Object expected) {
        boolean ok = String.valueOf(actual).equals(String.valueOf(expected));
        if (ok) pass++; else fail++;
        System.out.printf("%-5s %-6s %-34s got=%s%n",
                ok ? "  ok" : "FAIL", id, "expected=" + expected, actual);
    }

    /** null tra ve = nhan duoc; "TU CHOI" = da hien thong bao. */
    static Object money(String in) {
        try {
            BigDecimal v = Ui.readMoney(null, in, "So tien");
            return v == null ? "TU CHOI" : v.toPlainString();
        } catch (HeadlessException e) { return "TU CHOI"; }
    }

    static Object qty(String in, BigDecimal max) {
        try {
            BigDecimal v = Ui.readQuantity(null, in, max, "KG");
            return v == null ? "TU CHOI" : v.toPlainString();
        } catch (HeadlessException e) { return "TU CHOI"; }
    }

    static Object moneyZ(String in) {
        try {
            BigDecimal v = Ui.readMoneyAllowZero(null, in, "Giam gia");
            return v == null ? "TU CHOI" : v.toPlainString();
        } catch (HeadlessException e) { return "TU CHOI"; }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== 7.2  Doc so tien (hop dong muc 3.3) ===");
        check("#10", money("500.000"),  "500000.00");
        check("#11", money("500.00"),   "TU CHOI");
        check("#12", money("1.234,50"), "1234.50");
        check("#13", money("35 000 đ"), "35000.00");
        check("#14a", money("-5"),      "TU CHOI");
        check("#14b", money("0"),       "TU CHOI");
        check("#10b", money("500000"),  "500000.00");
        check("#10c", money("1.234.567"), "1234567.00");
        check("#10d", money("0,5"),     "0.50");
        check("#11b", money("1.2.3"),   "TU CHOI");
        check("#11c", money("1,234"),   "TU CHOI");
        check("#11d", money("500,000.00"), "TU CHOI");   // kieu My: co y tu choi
        check("#11e", money(""),        "TU CHOI");

        System.out.println();
        System.out.println("=== 7.2  Doc so luong (hai quy tac, co y khac nhau) ===");
        check("#15a", qty("1,5", null),   "1.5");
        check("#15b", qty("1.5", null),   "1.5");
        check("#16",  qty("1.2345", null), "TU CHOI");
        check("#17",  qty("1.000", null),  "1.000");   // MOT PHAY KHONG, khac o tien
        check("#qa",  qty("2", null),      "2");
        check("#qb",  qty("0", null),      "TU CHOI");
        check("#qc",  qty("1.2.3", null),  "TU CHOI");
        check("#qd",  qty("3", new BigDecimal("1.5")), "TU CHOI");  // vuot max
        check("#qe",  qty("1.5", new BigDecimal("1.5")), "1.5");    // bang max: compareTo

        System.out.println();
        System.out.println("=== EditLineDialog: Discount = 0 phai NHAN duoc ===");
        check("#dz", moneyZ("0"),        "0.00");
        check("#dn", moneyZ("-1"),       "TU CHOI");
        check("#dp", moneyZ("158.000"),  "158000.00");  // #62

        System.out.println();
        System.out.println("=== 7.4/7.7  Hien so (hop dong muc 3.4) ===");
        check("#57", Ui.percent(new BigDecimal("8.00")),  "8%");
        check("#57b", Ui.percent(new BigDecimal("7.50")), "7,5%");
        check("#63", Ui.money(new BigDecimal("135100")),  "135.100đ");
        check("#m0", Ui.money(null),                      "0đ");
        check("#q1", Ui.qty(new BigDecimal("1.500")),     "1,5");
        check("#q2", Ui.qty(new BigDecimal("2.000")),     "2");
        check("#61", Ui.qtyInput(new BigDecimal("1.500")), "1.5");
        check("#mi", Ui.moneyInput(new BigDecimal("158000.00")), "158.000");

        System.out.println();
        System.out.println("=== 7.3  Cong thuc giam gia (hop dong muc 3.2) ===");
        Method ld = NewInvoicePanel.class.getDeclaredMethod(
                "lineDiscount", BigDecimal.class, BigDecimal.class, BigDecimal.class);
        ld.setAccessible(true);
        BigDecimal p20 = new BigDecimal("20000");
        BigDecimal pct10 = new BigDecimal("10.00");
        BigDecimal d1  = (BigDecimal) ld.invoke(null, new BigDecimal("1"),  p20, pct10);
        BigDecimal d10 = (BigDecimal) ld.invoke(null, new BigDecimal("10"), p20, pct10);
        check("#18", d1.toPlainString(), "2000.00");
        check("#19a", d10.toPlainString(), "20000.00");     // tang gap 10
        // loi #2: ban cu giu discount=2000 khi doi SL len 10 -> 198.000
        BigDecimal totalOld = p20.multiply(new BigDecimal("10")).subtract(d1);
        BigDecimal totalNew = p20.multiply(new BigDecimal("10")).subtract(d10);
        check("#19b", totalNew.toPlainString(), "180000.00");
        check("#19c", totalOld.toPlainString(), "198000.00");  // con so SAI cua ban cu
        check("#19d", ((BigDecimal) ld.invoke(null, new BigDecimal("3"), p20, BigDecimal.ZERO))
                .toPlainString(), "0");

        System.out.println();
        System.out.println("=== 7.8  Ma QR (loi #27) ===");
        qr();

        System.out.println();
        System.out.println("=== 7.4  Hop dong tien mat, GHI THAT xuong database ===");
        e2e();

        System.out.println();
        System.out.println("=== SEED (khoi 6)  14 bat bien du lieu + hai ban chep ===");
        seed();

        System.out.println();
        System.out.printf("TONG: %d dat, %d sai%n", pass, fail);
        if (fail > 0) System.exit(1);
    }

    // =================================================================
    //  NHOM SEED -- plan/15 muc 5.6
    // =================================================================

    /**
     * Ba phep kiem cua khoi 6:
     *   #80  sp_check_seed tra 0 vi pham tren CA 14 quy tac
     *   #76  fn_active_promo_pct == Discount_Percent cua sp_get_active_promo
     *   #77  fn_line_discount (SQL) == NewInvoicePanel.lineDiscount (Java)
     *
     * #76 va #77 la CAI CHOT ma plan/15 muc 3.1 hua khi chap nhan ban chep
     * thu hai cua dieu kien chon khuyen mai. Rui ro #1/#2 cua muc 8 la:
     * ca seed va bat bien R2 deu hoi fn_line_discount, nen neu HAM sai thi
     * R2 van tra 0 -- hai ben sai khop nhau. Hai phep kiem duoi day pha
     * vong tron do: #76 so ham voi THU TUC (ban chep thu nhat), #77 so no
     * voi JAVA (ban chep ma tab 1 that su dung).
     */
    static void seed() {
        // --- #80: 14 bat bien du lieu -------------------------------
        check("#80", checkSeedViolations(true), 0);

        // --- #76: ham khop thu tuc tren luoi ma vach x ngay ----------
        // Bay ngay moc, chon de cat qua CA NAM vung khuyen mai cua seed:
        // PR04 don (-90), PR04+PR05 goi nhau (-60), PR06 PAUSED (-45, -30),
        // khe trong (-15 cho san pham khong co PR01), PR01/PR02 (0, +10).
        int[] offsets = { -90, -60, -45, -30, -15, 0, 10 };
        int cells = 0, mismatch = 0;
        QueryResult prods = QueryResult.run("SELECT Barcode FROM Product ORDER BY Barcode");
        for (Object[] p : prods.rows) {
            String barcode = String.valueOf(p[0]);
            for (int off : offsets) {
                LocalDate d = LocalDate.now().plusDays(off);

                // ban thu nhat: THU TUC (co ORDER BY ... DESC LIMIT 1)
                QueryResult sp = QueryResult.call("{call sp_get_active_promo(?, ?)}",
                        barcode, java.sql.Date.valueOf(d));
                BigDecimal fromSp = sp.rows.isEmpty()
                        ? BigDecimal.ZERO
                        : new BigDecimal(String.valueOf(sp.rows.get(0)[1]));

                // ban thu hai: HAM (co MAX)
                QueryResult fn = QueryResult.run(
                        "SELECT fn_active_promo_pct(?, ?)", barcode, java.sql.Date.valueOf(d));
                BigDecimal fromFn = new BigDecimal(String.valueOf(fn.rows.get(0)[0]));

                cells++;
                if (fromFn.compareTo(fromSp) != 0) {
                    mismatch++;
                    System.out.printf("  LECH %s @ %s: ham=%s thu tuc=%s%n",
                            barcode, d, fromFn, fromSp);
                }
            }
        }
        System.out.println("  luoi #76: " + cells + " o");
        check("#76", mismatch, 0);

        // Ba o CU THE, go bang tay, de phep kiem khong chi dong y voi chinh
        // no: hai ban chep co the cung sai mot cach, nhung ca hai cung sai
        // DUNG BA CON SO nay thi kho hon nhieu.
        check("#76a", pct("8934001", -50), "12.00");  // PR04 (8) + PR05 (12) -> chon 12
        check("#76b", pct("8934001", -70), "8.00");   // chi PR04
        check("#76c", pct("8934006", -30), "0.00");   // PR06 PAUSED phu, van 0

        // --- #77: SQL khop Java tung dong ---------------------------
        try {
            Method ld = NewInvoicePanel.class.getDeclaredMethod(
                    "lineDiscount", BigDecimal.class, BigDecimal.class, BigDecimal.class);
            ld.setAccessible(true);

            String[] qtys  = { "1", "2", "1.5", "0.5" };
            String[] prices = { "20000", "33000", "158000" };
            String[] pcts   = { "0", "5", "8", "10", "12" };
            int grid = 0, bad = 0;
            for (String q : qtys) {
                for (String pr : prices) {
                    for (String pc : pcts) {
                        BigDecimal java_ = (BigDecimal) ld.invoke(null,
                                new BigDecimal(q), new BigDecimal(pr), new BigDecimal(pc));
                        // Cong thuc cua SQL, voi pct truyen vao thay vi tra tu
                        // bang: so CONG THUC, khong so dieu kien chon khuyen
                        // mai (cai do la viec cua #76).
                        QueryResult sql = QueryResult.run(
                                "SELECT ROUND(? * ? * ? / 100, 2)", q, pr, pc);
                        BigDecimal sql_ = new BigDecimal(String.valueOf(sql.rows.get(0)[0]));
                        grid++;
                        if (java_.compareTo(sql_) != 0) {
                            bad++;
                            System.out.printf("  LECH qty=%s gia=%s pct=%s: java=%s sql=%s%n",
                                    q, pr, pc, java_, sql_);
                        }
                    }
                }
            }
            System.out.println("  luoi #77: " + grid + " o");
            check("#77", bad, 0);

            // Va mot o di qua DUNG fn_line_discount that -- de khong ai sua
            // ham do ma hai phep kiem tren van xanh. INV-0001 dong 1 cua seed:
            // 8934001, SL 2, don gia 28.000 tai ngay -90, PR04 8% -> 4.480.
            QueryResult real = QueryResult.run(
                    "SELECT fn_line_discount('8934001', ?, 2.000, 28000.00)",
                    java.sql.Date.valueOf(LocalDate.now().minusDays(90)));
            check("#77b", String.valueOf(real.rows.get(0)[0]), "4480.00");

        } catch (Exception e) {
            check("#77", "loi: " + e.getMessage(), 0);
        }

        // --- #110 / #111: du lieu do APP sinh ra thoa CUNG bo bat bien ---
        // Day la noi dung cua plan/15 muc 3.1 duoi dang mot phep kiem CHAY
        // DUOC: seed di qua thu tuc cua app, nen mot hoa don do tab 1 lap ra
        // va mot hoa don do seed sinh ra phai thoa CUNG 14 quy tac.
        //
        //   #110 ban hom nay   -> gia hom nay, khuyen mai hom nay (PR01/PR02)
        //   #111 ban o qua khu -> gia CU (truoc moc doi gia -45) va khuyen
        //                         mai CUA NGAY DO (PR04/PR05), khong phai
        //                         cua hom nay
        //
        // Hoa don duoc XOA ngay sau khi kiem -- khong cham du lieu seed.
        appInvoiceKeepsInvariants("#110", LocalDate.now());
        appInvoiceKeepsInvariants("#111", LocalDate.now().minusDays(60));
    }

    /**
     * Goi sp_check_seed va tra ve TONG so vi pham.
     *
     * <p>Thu tuc do vua tra ve BANG vua co tham so {@code OUT}, nen phai doc
     * ca hai: bang de in ra cho nguoi doc, so de cong vao {@code fail}.
     * {@code QueryResult.call} khong ho tro {@code OUT} nen cho nay dung
     * {@code CallableStatement} truc tiep -- day la mot chuong trinh kiem,
     * khong phai tang DAO.
     *
     * <p>Tra ve {@code -1} neu khong goi duoc (chua nap 05_invariants.sql).
     */
    static int checkSeedViolations(boolean print) {
        try (Connection conn = billing.db.Db.getConnection();
             CallableStatement cs = conn.prepareCall("{call sp_check_seed(?)}")) {

            cs.registerOutParameter(1, Types.INTEGER);
            boolean hasRs = cs.execute();
            while (hasRs) {
                try (ResultSet rs = cs.getResultSet()) {
                    while (rs.next()) {
                        int bad = rs.getInt("Vi_pham");
                        if (print || bad != 0) {
                            System.out.printf("  %-4s %-5s %-62s %s%n",
                                    bad == 0 ? "ok" : "FAIL",
                                    rs.getString("Quy_tac"),
                                    rs.getString("Noi_dung"),
                                    bad == 0 ? "" : "vi pham=" + bad);
                        }
                    }
                }
                hasRs = cs.getMoreResults();
            }
            return cs.getInt(1);

        } catch (java.sql.SQLException e) {
            System.out.println("  (khong goi duoc sp_check_seed -- da nap "
                    + "sql/05_invariants.sql chua?)  " + e.getMessage());
            return -1;
        }
    }

    /**
     * Lap MOT hoa don qua {@code InvoiceDao.createInvoice} (dung duong tab 1
     * di), chay lai 14 bat bien, roi XOA hoa don do.
     *
     * <p>Don gia va tien giam KHONG duoc go tay o day: chung tra bang
     * {@code ProductDao.priceOn} va {@code NewInvoicePanel.lineDiscount} tai
     * {@code saleDate} -- dung hai nguon ma tab 1 dung. Do la toan bo y nghia
     * cua phep kiem: neu app tinh khac seed thi R1 hoac R2 bao ngay.
     */
    static void appInvoiceKeepsInvariants(String id, LocalDate saleDate) {
        LookupDao  lk = new LookupDao();
        ProductDao pd = new ProductDao();
        InvoiceDao dao = new InvoiceDao();
        String created = null;

        try {
            Method ld = NewInvoicePanel.class.getDeclaredMethod(
                    "lineDiscount", BigDecimal.class, BigDecimal.class, BigDecimal.class);
            ld.setAccessible(true);

            // Chon mot san pham CO khuyen mai tai saleDate, de phep kiem di
            // qua nhanh "Discount <> 0" chu khong chi nhanh 0.
            Product prod = null;
            BigDecimal price = null, pctv = BigDecimal.ZERO;
            for (Product p : pd.findAll()) {
                BigDecimal pr = pd.priceOn(p.barcode, saleDate);
                if (pr == null || pr.signum() <= 0) continue;
                Object[] promo = pd.activePromoOn(p.barcode, saleDate);
                if (promo != null) {
                    prod = p; price = pr; pctv = (BigDecimal) promo[1];
                    break;
                }
                if (prod == null) { prod = p; price = pr; }   // du phong
            }
            if (prod == null) {
                System.out.println("  (khong co san pham co gia -- bo qua " + id + ")");
                return;
            }

            BigDecimal qty = new BigDecimal("2");
            BigDecimal disc = (BigDecimal) ld.invoke(null, qty, price, pctv);
            BigDecimal due = price.multiply(qty).subtract(disc).setScale(2);

            List<CartLine> lines = new ArrayList<>();
            lines.add(new CartLine(prod, qty, price, disc, ""));

            NewInvoicePanel.Split sp = NewInvoicePanel.splitPayment(
                    PayMethod.CASH, due.add(new BigDecimal("50000.00")), due);
            List<PaymentEntry> pays = new ArrayList<>();
            pays.add(new PaymentEntry(PayMethod.CASH.code(), sp.recorded, sp.tendered, null, null));

            created = dao.createInvoice(null, saleDate, LocalTime.now().withNano(0),
                    lk.counters().get(0).id, lk.cashiers().get(0).id, null, lines, pays);
            System.out.println("  " + id + ": " + created + " ban ngay " + saleDate
                    + " (" + prod.barcode + ", gia " + price + ", giam " + disc + ")");

            check(id, checkSeedViolations(false), 0);

        } catch (Exception e) {
            check(id, "loi: " + e.getMessage(), 0);
        } finally {
            if (created != null) {
                try { new InvoiceDao().deleteInvoice(created); } catch (Exception ignore) { }
            }
        }
    }

    /** fn_active_promo_pct tai CURDATE() + offset, duoi dang chuoi de so sanh. */
    static String pct(String barcode, int offsetDays) {
        QueryResult r = QueryResult.run("SELECT fn_active_promo_pct(?, ?)",
                barcode, java.sql.Date.valueOf(LocalDate.now().plusDays(offsetDays)));
        return String.valueOf(r.rows.get(0)[0]);
    }

    static void qr() {
        // #68: so tai khoan trong payload la so TRUYEN VAO, khong phai hang so
        String mine = billing.util.VietQRHelper.buildEMVCoPayload(
                "970418", "0123456789", 198001L, "TXN1");
        check("#68a", mine.contains("0123456789"), true);
        check("#68b", mine.contains("970418"), true);
        check("#68c", mine.contains(billing.util.VietQRHelper.DEFAULT_ACCOUNT), false);

        // #69: thieu cau hinh -> dung mac dinh, khong exception
        String dflt = billing.util.VietQRHelper.buildEMVCoPayload(null, "  ", 1000L, "TXN2");
        check("#69a", dflt.contains(billing.util.VietQRHelper.DEFAULT_ACCOUNT), true);
        check("#69b", dflt.contains(billing.util.VietQRHelper.DEFAULT_BIN), true);

        // #71: 198.000,50 -> payload mang 198001 (setScale(0,HALF_UP) TRUOC longValue)
        BigDecimal odd = new BigDecimal("198000.50");
        long rounded = odd.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        check("#71a", rounded, 198001L);
        check("#71b", odd.longValue(), 198000L);   // con so SAI cua ban cu
        String p71 = billing.util.VietQRHelper.buildEMVCoPayload("970422", "1", rounded, "T");
        check("#71c", p71.contains("5406198001"), true);   // tag 54, len 06, "198001"

        // config.properties that su co 4 khoa qr.* va doc duoc
        check("#cfg1", billing.util.AppConfig.getBoolean("qr.enabled", false), true);
        check("#cfg2", billing.util.AppConfig.get("qr.bank.bin", "?"), "970422");
        check("#cfg3", billing.util.AppConfig.get("qr.account.no", "?"), "90902241107");
    }

    // -----------------------------------------------------------------

    static void e2e() {
        LookupDao  lk = new LookupDao();
        ProductDao pd = new ProductDao();
        InvoiceDao dao = new InvoiceDao();

        String counterId = lk.counters().get(0).id;
        String cashierId = lk.cashiers().get(0).id;
        LocalDate today = LocalDate.now();

        Product prod = null;
        BigDecimal price = null;
        for (Product p : pd.findAll()) {
            BigDecimal pr = pd.priceOn(p.barcode, today);
            if (pr != null && pr.signum() > 0) { prod = p; price = pr; break; }
        }
        if (prod == null) { System.out.println("  (khong co san pham co gia -- bo qua e2e)"); return; }

        // Gio hang du lon de 100.000 KHONG tra het duoc (kich ban #31)
        BigDecimal qty = new BigDecimal("200000").divide(price, 0, java.math.RoundingMode.CEILING);
        if (qty.compareTo(BigDecimal.ONE) < 0) qty = BigDecimal.ONE;

        List<String> created = new ArrayList<>();
        try {
            // --- Hop dong tien mat o dang THUAN (muc 3.1) -----------------
            BigDecimal due198 = new BigDecimal("198000.00");
            // #27: hoa don 198.000, khach dua 500.000
            NewInvoicePanel.Split s27 = NewInvoicePanel.splitPayment(
                    PayMethod.CASH, new BigDecimal("500000.00"), due198);
            check("#27a", s27.recorded.toPlainString(), "198000.00");
            check("#27b", s27.tendered.toPlainString(), "500000.00");
            check("#27c", s27.change().toPlainString(),  "302000.00");

            // #31: tien mat 100.000 -> roi tien mat 150.000
            NewInvoicePanel.Split s31a = NewInvoicePanel.splitPayment(
                    PayMethod.CASH, new BigDecimal("100000.00"), due198);
            check("#31a", s31a.recorded.toPlainString(), "100000.00");
            check("#31b", s31a.change().toPlainString(),  "0.00");
            BigDecimal left31 = due198.subtract(s31a.recorded);
            NewInvoicePanel.Split s31b = NewInvoicePanel.splitPayment(
                    PayMethod.CASH, new BigDecimal("150000.00"), left31);
            check("#31c", s31b.recorded.toPlainString(), "98000.00");
            check("#31d", s31b.tendered.toPlainString(), "150000.00");
            check("#31e", s31b.change().toPlainString(),  "52000.00");
            check("#31f", s31a.recorded.add(s31b.recorded).toPlainString(), "198000.00");

            // #32: THE ngan hang 500.000 cho hoa don 198.000 -> TU CHOI
            check("#32", NewInvoicePanel.splitPayment(
                    PayMethod.CARD, new BigDecimal("500000.00"), due198), null);
            check("#32b", NewInvoicePanel.splitPayment(
                    PayMethod.CARD, due198, due198).recorded.toPlainString(), "198000.00");
            check("#32c", NewInvoicePanel.splitPayment(
                    PayMethod.CARD, due198, due198).tendered, null);
            // #72: vi dien tu cung khong co tien khach dua
            check("#72", NewInvoicePanel.splitPayment(
                    PayMethod.EWALLET, new BigDecimal("1000"), due198).tendered, null);
            // #34: tra du bang tien mat -> tien thoi 0
            check("#34", NewInvoicePanel.splitPayment(
                    PayMethod.CASH, due198, due198).change().toPlainString(), "0.00");

            System.out.println();
            // --- #27/#28 GHI THAT: ba con so nam dung cho ----------------
            List<CartLine> lines = new ArrayList<>();
            lines.add(new CartLine(prod, qty, price, BigDecimal.ZERO, ""));
            BigDecimal due = price.multiply(qty).setScale(2);
            BigDecimal tendered = due.add(new BigDecimal("302000.00"));

            NewInvoicePanel.Split sp = NewInvoicePanel.splitPayment(PayMethod.CASH, tendered, due);
            List<PaymentEntry> pays = new ArrayList<>();
            pays.add(new PaymentEntry(PayMethod.CASH.code(), sp.recorded, sp.tendered, null, null));

            String id = dao.createInvoice(null, today, LocalTime.now().withNano(0),
                    counterId, cashierId, null, lines, pays);
            created.add(id);
            System.out.println("  da luu " + id + " (due=" + due + ", khach dua=" + tendered + ")");

            QueryResult q = QueryResult.run(
                    "SELECT p.Amount, c.Tendered_Amount FROM Payment p "
                  + "JOIN Cash_Payment c ON c.Payment_ID=p.Payment_ID WHERE p.Invoice_ID=?", id);
            check("#28a", q.rows.get(0)[0].toString(), due.toPlainString());
            check("#28b", q.rows.get(0)[1].toString(), tendered.toPlainString());

            QueryResult st = QueryResult.run("SELECT Status FROM Invoice WHERE Invoice_ID=?", id);
            check("#29", st.rows.get(0)[0], "PAID");

            // cot Tien_khach_dua la SO, Chi_tiet RONG
            QueryResult pay = dao.invoicePayments(id);
            check("#5a", pay.columns.size(), 5);
            check("#5b", pay.columns.get(4), "Tien_khach_dua");
            check("#5c", String.valueOf(pay.rows.get(0)[3]), "");
            check("#5d", pay.rows.get(0)[4].toString(), tendered.toPlainString());

            // --- #31 GHI THAT: hai lan tien mat ---------------------------
            List<CartLine> l2 = new ArrayList<>();
            l2.add(new CartLine(prod, qty, price, BigDecimal.ZERO, ""));
            BigDecimal due2 = price.multiply(qty).setScale(2);

            NewInvoicePanel.Split a = NewInvoicePanel.splitPayment(
                    PayMethod.CASH, new BigDecimal("100000.00"), due2);
            BigDecimal left2 = due2.subtract(a.recorded);
            NewInvoicePanel.Split b = NewInvoicePanel.splitPayment(
                    PayMethod.CASH, left2.add(new BigDecimal("52000.00")), left2);

            List<PaymentEntry> p2 = new ArrayList<>();
            p2.add(new PaymentEntry(PayMethod.CASH.code(), a.recorded, a.tendered, null, null));
            p2.add(new PaymentEntry(PayMethod.CASH.code(), b.recorded, b.tendered, null, null));

            String id2 = dao.createInvoice(null, today, LocalTime.now().withNano(0),
                    counterId, cashierId, null, l2, p2);
            created.add(id2);
            check("#31g", a.recorded.toPlainString(), "100000.00");       // lan 1 ghi het
            check("#31h", b.recorded.toPlainString(), left2.toPlainString());
            check("#31i", b.change().toPlainString(),  "52000.00");
            check("#31j", a.recorded.add(b.recorded).toPlainString(), due2.toPlainString());
            QueryResult st2 = QueryResult.run("SELECT Status FROM Invoice WHERE Invoice_ID=?", id2);
            check("#31k", st2.rows.get(0)[0], "PAID");
            // #40: hai dong Payment co ma KHAC NHAU va lien tiep
            QueryResult ids = QueryResult.run(
                    "SELECT Payment_ID FROM Payment WHERE Invoice_ID=? ORDER BY Payment_ID", id2);
            check("#40a", ids.rows.size(), 2);
            int n1 = Integer.parseInt(ids.rows.get(0)[0].toString().substring(4));
            int n2 = Integer.parseInt(ids.rows.get(1)[0].toString().substring(4));
            check("#40b", n2 - n1, 1);

            // --- #33: tra thieu -> OPEN -----------------------------------
            List<CartLine> l3 = new ArrayList<>();
            l3.add(new CartLine(prod, qty, price, BigDecimal.ZERO, ""));
            BigDecimal part = due2.subtract(new BigDecimal("1000.00"));
            List<PaymentEntry> p3 = new ArrayList<>();
            p3.add(new PaymentEntry(PayMethod.CASH.code(), part, part, null, null));
            String id3 = dao.createInvoice(null, today, LocalTime.now().withNano(0),
                    counterId, cashierId, null, l3, p3);
            created.add(id3);
            QueryResult st3 = QueryResult.run("SELECT Status FROM Invoice WHERE Invoice_ID=?", id3);
            check("#33", st3.rows.get(0)[0], "OPEN");

            // --- #38: hai lan luu lien tiep -> hai ma KHAC NHAU -----------
            check("#38", id.equals(id2) || id2.equals(id3), false);

            // --- bat bien muc 3.1 tren TOAN BO database -------------------
            QueryResult bad = QueryResult.run(
                    "SELECT p.Payment_ID FROM Payment p JOIN Cash_Payment c "
                  + "ON c.Payment_ID=p.Payment_ID WHERE c.Tendered_Amount < p.Amount");
            check("#4", bad.rows.size(), 0);

        } finally {
            for (String id : created) {
                try { new InvoiceDao().deleteInvoice(id); } catch (Exception ignore) { }
            }
            System.out.println("  da xoa " + created.size() + " hoa don thu nghiem: " + created);
        }
    }
}
