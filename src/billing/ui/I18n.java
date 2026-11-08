package billing.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.text.Normalizer;
import java.util.*;
import java.util.List;

/** Small runtime translator for the app's Vietnamese and English UI. */
public final class I18n {
    public enum Language { VIETNAMESE, ENGLISH }

    private static Language language = Language.VIETNAMESE;
    private static final String SOURCE_TEXT = "billing.ui.sourceText";
    private static final String SHOWN_TEXT = "billing.ui.shownText";
    private static final String SOURCE_TOOLTIP = "billing.ui.sourceTooltip";
    private static final String SHOWN_TOOLTIP = "billing.ui.shownTooltip";
    private static final String PRESERVE_TEXT = "billing.ui.preserveText";
    private static final String PRESERVE_TOOLTIP = "billing.ui.preserveTooltip";
    private static final Map<String, String> EN = new LinkedHashMap<>();
    private static final Map<String, String> VI = new HashMap<>();
    private static final Map<String, String> EN_SOURCE = new HashMap<>();

    static {
        add("Demo Retail Invoicing  --  Java Swing + JDBC + MySQL 8", "Demo Retail Invoicing  --  Java Swing + JDBC + MySQL 8");
        add("Doi vai tro ▾", "Switch role ▾");
        add("Dang xuat", "Log out");
        add("Nguoi dung: ", "User: ");
        add("Nguoi dung:", "User:");
        add("[Vai tro: ", "[Role: ");
        add("Vai tro:", "Role:");
        add("]", "]");
        add("(Chua dang nhap)", "(Not signed in)");
        add("(DANG NHAP TU DONG)", "(AUTO LOGIN)");
        add("App da dang nhap san, khong qua man hinh dang nhap. Dat quick.login.auto= (de trong) trong config.properties de tat.",
                "The app signed in automatically. Set quick.login.auto= (empty) in config.properties to disable this.");
        add("Doi sang tai khoan khac ma KHONG mat du lieu dang nhap o tab 1", "Switch to another account without losing the invoice in tab 1");
        add("1. Lap hoa don", "1. New invoice");
        add("2. Tra cứu dữ liệu", "2. Browse data");
        add("2. Tra cuu du lieu", "2. Browse data");
        add("3. Xem hoa don (Database)", "3. View invoices (Database)");
        add("4. Bao cao", "4. Reports");
        add("Ngon ngu", "Language");
        add("Chuyen ngon ngu giao dien", "Switch the interface language");
        add("Xac nhan dang xuat", "Confirm log out");
        add("Ban co chac chan muon dang xuat khoi he thong?", "Are you sure you want to log out?");
        add("Tab \"Lap hoa don\" dang co ", "Tab \"New invoice\" has ");
        add(" chua luu.\nDang xuat se mat toan bo. Van dang xuat?\n\nGoi y: muon xem giao dien cua vai tro khac ma khong mat gio hang\nthi dung nut \"Doi vai tro\" ben canh.",
                " unsaved. Logging out will discard it. Log out anyway?\n\nTo view another role without losing this invoice,\nuse the \"Switch role\" button beside this one.");
        add("Tai khoan '", "Account '");
        add("' trong config.properties khong dang nhap duoc.\nCo the mat khau trong bang Account da bi doi.\n\nPhien hien tai van duoc giu nguyen.",
                "' from config.properties could not sign in.\nThe password in the Account table may have changed.\n\nYour current session is unchanged.");
        add("Doi vai tro that bai", "Could not switch role");
        add(" dong hang", " invoice line(s)");
        add(" lan thanh toan", " payment(s)");

        add("Dang nhap he thong -- Retail Invoicing", "Sign in -- Retail Invoicing");
        add("Ten dang nhap:", "Username:");
        add("Mat khau:", "Password:");
        add("Dang nhap", "Sign in");
        add("Thoat", "Exit");
        add("Dang nhap nhanh (tai khoan mau cua ban demo)", "Quick sign in (demo accounts)");
        add("Bam mot nut de dien san va dang nhap ngay.", "Select an account to fill in the credentials and sign in.");
        add("Phim tat: Alt+1 ... Alt+", "Shortcuts: Alt+1 ... Alt+");
        add(".  Enter = Dang nhap.", ".  Enter = Sign in.");
        add("Dien san va dang nhap bang tai khoan '", "Fill in and sign in with account '");
        add("Dien san va dang nhap bang tai khoan '", "Fill in and sign in with account '");
        add("Vui long nhap day du Ten dang nhap va Mat khau.", "Enter both a username and password.");
        add("Dang kiem tra tai khoan...", "Checking account...");
        add("Sai ten dang nhap hoac mat khau.", "Incorrect username or password.");

        add("1. Thong tin hoa don", "1. Invoice details");
        add("Quay:", "Counter:");
        add("Thu ngan:", "Cashier:");
        add("Ngay ban:", "Sale date:");
        add("Khach (SDT):", "Customer (phone):");
        add("Meo: doi 'Ngay ban' ve nam ngoai roi them san pham -> don gia se lay theo GIA CU trong Price_History.",
                "Tip: set the sale date to last year, then add a product to see its historical price from Price_History.");
        add("2. Them san pham", "2. Add a product");
        add("San pham:", "Product:");
        add("So luong:", "Quantity:");
        add("3. Cac dong hang (se thanh bang Invoice_Line)", "3. Invoice lines (stored in Invoice_Line)");
        add("4. Thanh toan (mot hoa don co the co NHIEU dong thanh toan)", "4. Payments (an invoice can have multiple payments)");
        add("Phuong thuc:", "Method:");
        add("Tai lai", "Reload");
        add("Them vao hoa don", "Add to invoice");
        add("Sua so luong", "Edit quantity");
        add("Xoa dong dang chon", "Remove selected row");
        add("Them thanh toan", "Add payment");
        add("Khach tra du bang tien mat", "Customer pays the balance in cash");
        add("Tao ma QR", "Create QR code");
        add("Xoa het", "Clear all");
        add("XUAT HOA DON  (INSERT + COMMIT)", "SAVE INVOICE  (INSERT + COMMIT)");
        add("Dang luu...", "Saving...");
        add("Gia ngay ", "Price on ");
        add(" = ", " = ");
        add("Tong: ", "Total: ");
        add("Da thu: ", "Paid: ");
        add("Con thieu: ", "Balance due: ");
        add("Tien thoi: ", "Change: ");
        add("Gia ngay ", "Price on ");
        add("  (khong co KM)", "  (no promotion)");
        add("Cot VAT % la thue suat cua san pham; don gia trong Price_History DA GOM VAT.",
                "VAT % is the product tax rate; prices in Price_History already include VAT.");
        add("<html>Dau <b>,</b> hoac <b>.</b> la dau THAP PHAN (toi da 3 chu so) -- KHONG phai dau tach nghin.<br>Dung: <code>2</code> | <code>1,5</code> | <code>1.5</code> | <code>0,25</code><br><i>Khac hẳn o tien ben duoi, noi dau <b>.</b> la dau tach nghin.</i><br>Enter = Them vao hoa don.</html>",
                "<html>Use <b>,</b> or <b>.</b> as the decimal separator (up to 3 digits). Neither is a thousands separator.<br>Examples: <code>2</code> | <code>1,5</code> | <code>1.5</code> | <code>0,25</code><br><i>Money fields below use <b>.</b> as a thousands separator.</i><br>Enter = Add to invoice.</html>");
        add("<html>Dau <b>.</b> la dau TACH NGHIN (phai du 3 chu so), dau <b>,</b> la dau thap phan.<br>Dung: <code>500.000</code> | <code>1.234,50</code> | <code>500000</code><br>Sai : <code>500.00</code> | <code>1.2.3</code> | <code>1,234</code><br><i>Khac hẳn o so luong o tren, noi dau <b>.</b> la dau thap phan.</i><br>Enter = Them thanh toan.</html>",
                "<html><b>.</b> is the thousands separator (groups of 3 digits); <b>,</b> is the decimal separator.<br>Examples: <code>500.000</code> | <code>1.234,50</code> | <code>500000</code><br>Invalid: <code>500.00</code> | <code>1.2.3</code> | <code>1,234</code><br><i>Quantity above uses <b>.</b> as a decimal separator.</i><br>Enter = Add payment.</html>");
        add("Khach dua", "Tendered");
        add("So tien", "Amount");
        add("Tien mat", "Cash");
        add("The ngan hang", "Bank card");
        add("Vi dien tu", "E-wallet");
        add("Dang lap", "Open");
        add("Da thanh toan", "Paid");
        add("Da huy", "Voided");
        add("Dang lap (OPEN)", "Open (OPEN)");
        add("Da thanh toan (PAID)", "Paid (PAID)");
        add("Da huy (VOIDED)", "Voided (VOIDED)");
        add("Tất cả", "All");
        add("Tiền mặt", "Cash");
        add("Thẻ", "Card");
        add("Ví điện tử", "E-wallet");
        add("Khoảng", "Custom range");

        add("Tìm kiếm", "Search");
        add("Tải lại / Xem tất cả", "Reload / Show all");
        add("Chọn bảng: ", "Table: ");
        add("Từ khóa: ", "Keyword: ");
        add("Tổng số hàng: 0 dòng", "Total rows: 0");
        add("Chưa cấu hình bộ lọc cho bảng ", "No filters configured for table ");
        add("Hình thức thanh toán: ", "Payment method: ");
        add("Số tiền: ", "Amount: ");
        add("Từ: ", "From: ");
        add("Đến: ", "To: ");
        add("Trạng thái hóa đơn: ", "Invoice status: ");
        add("Thời gian: ", "Date and time: ");
        add("Loại khách: ", "Customer type: ");
        add("Đơn vị tính: ", "Unit: ");
        add("Khoáng giá sản phẩm: ", "Product price range: ");
        add("Thuế suất: ", "Tax rate: ");
        add("Thời gian trả hàng: ", "Return date and time: ");
        add("Số lượng hoàn trả: ", "Return quantity: ");
        add("Đang hiện ", "Showing ");
        add(" dòng", " rows");
        add("Tổng số hàng: ", "Total rows: ");
        add(" dòng đầu (bảng còn nhiều hơn — dùng bộ lọc để thu hẹp)", " rows (more are available; use a filter to narrow the results)");

        add("Sua hoa don", "Edit invoice");
        add("Huy hoa don", "Void invoice");
        add("Xoa hoa don", "Delete invoice");
        add("Sua dong hang", "Edit line");
        add("Xoa dong hang", "Delete line");
        add("Thu xoa gia tri (Set NULL)", "Try setting value to NULL");
        add("Tra hang", "Return items");
        add("Huy luot tra", "Delete return");
        add("Tai lai danh sach", "Reload list");
        add("Khoang ngay:", "Date range:");
        add("Danh sach hoa don (bam de xem chi tiet)", "Invoices (select one to view details)");
        add("Thong tin chung + tong tien", "Summary and total");
        add("Cac dong hang (Invoice_Line)", "Invoice lines (Invoice_Line)");
        add("Thanh toan (Payment + bang con)", "Payments (Payment and child tables)");
        add("Tra hang (Return)", "Returns (Return)");
        add("7 ngay gan nhat", "Last 7 days");
        add("30 ngay gan nhat", "Last 30 days");
        add("90 ngay gan nhat", "Last 90 days");
        add("Tat ca", "All");
        add("Thong tin chung", "Summary");
        add("Cac dong cua hoa don", "Invoice lines");
        add("Se tra lai", "To be returned");
        add("Them >>", "Add >>");
        add("<< Xoa", "<< Remove");
        add("Xac nhan tra hang", "Confirm return");
        add("Huy", "Cancel");
        add("Tong tien phai tra lai: ", "Total refund: ");
        add("Nguoi duyet: ", "Approved by: ");
        add("Loc theo ngay ban. 'Tat ca' nap moi hoa don, ke ca hai hoa don INV-OLD-* tu nam truoc.",
                "Filter by sale date. 'All' loads every invoice, including both INV-OLD-* invoices from last year.");
        add("Chinh sua thong tin hoa don truc tiep trong DB", "Edit invoice details directly in the DB");
        add("Huy hoa don: du lieu van con, chi khong tinh vao bao cao", "Void an invoice: data remains but is excluded from reports");
        add("Xoa hoa don va toan bo du lieu lien quan khoi DB", "Delete the invoice and all related data from the DB");
        add("Chinh sua so luong, don gia dong hang trong DB", "Edit line quantity and unit price in the DB");
        add("Xoa dong hang khoi DB", "Delete an invoice line from the DB");
        add("Thu nghiem co che canh bao xoa gia tri NOT NULL", "Try the warning for clearing a NOT NULL value");
        add("Ghi mot luot tra hang cho cac dong cua hoa don dang chon", "Record a return for lines in the selected invoice");
        add("Huy mot luot tra hang da ghi (chon dong trong bang Tra hang)", "Delete a recorded return (select a row in the Returns table)");
        add("Trang thai doi tu dong khi thu du tien, hoac qua nut \"Huy hoa don\" o tab Xem hoa don", "Status changes automatically when the invoice is fully paid or voided from the View invoices tab");
        add("Loc theo ngay ban. 'Tat ca' nap moi hoa don, ke ca hai hoa don INV-OLD-* tu nam truoc.", "Filter by sale date. 'All' loads every invoice, including both INV-OLD-* invoices from last year.");
        add("Go SO DIEN THOAI cua khach (tu 3 chu so tro len) roi chon tu danh sach goi y.<br>Chi khach <b>duoc chon tu danh sach</b> moi duoc gan vao hoa don -- go du so ma khong chon van la khach vang lai.</html>",
                "Type at least 3 digits of the customer's phone number, then select a suggestion.<br>Only a customer <b>selected from the list</b> is added to the invoice; typing a full number without selecting keeps it as a walk-in customer.</html>");
        add("San pham", "Product");
        add("SL", "Qty");
        add("DVT", "Unit");
        add("Don gia", "Unit price");
        add("Thanh tien", "Line total");
        add("Phuong thuc", "Method");
        add("Chi tiet", "Details");
        add("Khach dua", "Tendered");
        add("Tien thoi", "Change");

        add("Khach hang", "Customer");
        add("(khach vang lai)", "(walk-in customer)");
        add("Co loi xay ra", "An error occurred");
        add("Loi ket noi", "Connection error");
        add("Khong ket noi duoc MySQL.\n\n", "Could not connect to MySQL.\n\n");
        add("Kiem tra lan luot:\n", "Check the following:\n");
        add("  1. MySQL 8 da chay chua?  (Services -> MySQL80 -> Running)\n", "  1. Is MySQL 8 running? (Services -> MySQL80 -> Running)\n");
        add("  2. Da tao database chua?  Chay file sql/01_schema.sql va sql/02_seed.sql\n", "  2. Has the database been created? Run sql/01_schema.sql and sql/02_seed.sql\n");
        add("  3. Mat khau trong file config.properties da dung chua?\n", "  3. Is the password in config.properties correct?\n");
        add("  4. Neu loi 'No suitable driver': thieu file mysql-connector-j.jar\n     trong thu muc lib/. Chay setup.ps1 de tai ve.\n", "  4. For 'No suitable driver': mysql-connector-j.jar is missing from lib/. Run setup.ps1 to download it.\n");
        add("Chon bao cao", "Select a report");
        add("Y nghia cau truy van", "About this query");
        add("Cau SQL dang chay", "SQL query");
        add("Ket qua", "Results");
        add("2. Doanh thu 7 ngay / cua hang", "2. Revenue, last 7 days / store");
        add("3. San pham tang gia > 10%", "3. Products with price increases > 10%");
        add("4. Thu ngan gio hang TB cao nhat", "4. Highest average invoice total / thu ngan");
        add("5. Hoa don tra hang > 50%", "5. Invoices with returns > 50%");
        add("Bonus: hoa don cu giu gia cu", "Bonus: historical invoices keep their original prices");
        add(" -- dang chay...", " -- running...");
        add("Cau lenh chay xong nhung khong co dong nao thoa dieu kien.", "The query completed but returned no matching rows.");
        add("Doanh thu tung NGAY cua tung CUA HANG trong 7 ngay gan nhat. Hoa don khong noi thang toi Shop nen phai di vong: Invoice -> Counter -> Shop. GROUP BY hai cot (shop, ngay) nen moi dong ket qua la mot o trong bang cheo.",
                "Daily revenue for each store in the last 7 days. An invoice is linked to a Shop through Counter, so the query joins Invoice -> Counter -> Shop. GROUP BY the shop and date to get one row per cross-tab cell.");
        add("SELF JOIN: noi bang Price_History voi CHINH NO de dat gia moi canh gia cu. Subquery MAX(...) tim moc gia LIEN TRUOC cua cung san pham. Dieu kien: gia moi > gia cu * 1.10 va nam trong nam nay.",
                "SELF JOIN: join Price_History to itself to compare a new price with the previous price. The MAX(...) subquery finds the immediately preceding price for the same product. The new price must be over 110% of the old price and dated this year.");
        add("Bay pho bien: AVG thang tren Invoice_Line se ra trung binh moi DONG chu khong phai moi HOA DON. Phai tinh tong tien tung hoa don truoc (subquery), roi moi AVG theo thu ngan.",
                "Common mistake: AVG over Invoice_Line gives an average per LINE, not per INVOICE. First total each invoice in a subquery, then calculate AVG by cashier.");
        add("Hai subquery: mot tinh tong hoa don, mot tinh tien tra lai. Tien tra lai tinh theo TY LE (SL tra / SL mua) nhan thanh tien, de phan giam gia cung duoc hoan dung ty le.",
                "Two subqueries: one totals each invoice and the other totals refunds. A refund is calculated proportionally (returned quantity / purchased quantity) times the line total, so discounts are refunded proportionally too.");
        add("Y nghia cau truy van", "About this query");
        add("Cau 'Gia_ghi_tren_HD' lay tu Invoice_Line (da dong bang luc ban). Cot 'Gia_hom_nay' goi fn_get_product_price(barcode, CURDATE()). Bao cao GOM theo (san pham, don gia da dong bang), nen moi dong tra loi: 'co bao nhieu dong hang dang ghi gia nay, ban tu ngay nao den ngay nao, va gia hom nay la bao nhieu'. Dong co 'Ban_tu_ngay' xa nhat chinh la hai hoa don INV-OLD-* tu nam truoc.",
                "'Gia_ghi_tren_HD' comes from Invoice_Line and is frozen at sale time. 'Gia_hom_nay' calls fn_get_product_price(barcode, CURDATE()). The report groups by product and frozen price. Each row shows how many lines used that price, the date range, and today's price. The oldest 'Ban_tu_ngay' rows are the two INV-OLD-* invoices from last year.");

        add("Sua hoa don:", "Edit invoice:");
        add("Chinh sua Hoa don: ", "Edit invoice: ");
        add("Chinh sua Dong hang: ", "Edit invoice line: ");
        add("Sua dong hang:", "Edit invoice line:");
        add("Ma hoa don (PK):", "Invoice ID (PK):");
        add("Ngay lap (YYYY-MM-DD) *:", "Invoice date (YYYY-MM-DD) *:");
        add("Gio lap (HH:MM:SS) *:", "Invoice time (HH:MM:SS) *:");
        add("Trang thai:", "Status:");
        add("(chi doc)", "(read only)");
        add("Ma quay (Counter_ID) *:", "Counter ID (Counter_ID) *:");
        add("Ma thu ngan (Cashier_ID) *:", "Cashier ID (Cashier_ID) *:");
        add("Ma khach hang:", "Customer ID:");
        add("Xoa ma khach (Set NULL)", "Clear customer ID (set NULL)");
        add("Luu vao Database", "Save to Database");
        add("Huy bo", "Cancel");
        add("Ma vach (Barcode) *:", "Barcode *:");
        add("So luong (Quantity) *:", "Quantity *:");
        add("Don gia (Unit_Price) *:", "Unit price (Unit_Price) *:");
        add("Giam gia (Discount) *:", "Discount *:");
        add("Thu xoa (Set NULL)", "Try setting NULL");
        add("Ma hoa don", "Invoice ID");
        add("Ngay lap", "Invoice date");
        add("Gio lap", "Invoice time");
        add("Thanh cong", "Success");
        add("Thong bao", "Notice");
        add("Loi", "Error");
        add("Loi dinh dang", "Invalid format");
        add("Canh bao rang buoc NOT NULL", "NOT NULL constraint warning");
        add("CANH BAO DATABASE: Truong '", "DATABASE WARNING: Field '");
        add("CANH BAO DATABASE: Cot '", "DATABASE WARNING: Column '");
        add("' co rang buoc NOT NULL!\nBan khong duoc phep xoa gia tri nay ve NULL hoac de trong.",
                "' has a NOT NULL constraint.\nThis value cannot be cleared or left empty.");
        add("' co rang buoc NOT NULL trong Database!\nBan khong duoc phep xoa gia tri nay ve NULL hoac de trong.",
                "' has a NOT NULL constraint in the Database.\nThis value cannot be cleared or left empty.");
        add("Customer_ID la truong cho phep NULL (khach vang lai).\nGia tri nay se duoc cap nhat thanh NULL trong database.",
                "Customer_ID allows NULL for walk-in customers.\nThis value will be updated to NULL in the database.");
        add(" thanh cong trong Database!", " saved to the Database.");
        add("<html>Dau <b>,</b> hoac <b>.</b> la dau THAP PHAN, toi da 3 chu so (cot DECIMAL(10,3)).<br>Dung: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>",
                "<html>Use <b>,</b> or <b>.</b> as the decimal separator, with up to 3 digits (DECIMAL(10,3)).<br>Examples: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>");
        add("<html>Dau <b>.</b> la dau TACH NGHIN: <code>158.000</code> = 158 nghin.<br>Dau <b>,</b> la dau thap phan (toi da 2 chu so).</html>",
                "<html><b>.</b> is the thousands separator: <code>158.000</code> = 158 thousand.<br><b>,</b> is the decimal separator (up to 2 digits).</html>");
        add("<html><i style='color:#c0392b;'>* Toan bo cac cot trong Invoice_Line deu co rang buoc NOT NULL.<br>Nut 'Thu xoa (Set NULL)' se minh hoa co che canh bao cua Database.</i></html>",
                "<html><i style='color:#c0392b;'>* All Invoice_Line columns have a NOT NULL constraint.<br>'Try setting NULL' demonstrates the Database warning.</i></html>");
        add("<html><i style='color:#c0392b;'>* Cac truong danh dau (*) co rang buoc NOT NULL. Neu xoa trang se bi he thong canh bao!</i></html>",
                "<html><i style='color:#c0392b;'>* Fields marked with (*) have a NOT NULL constraint. Clearing one will show a warning.</i></html>");
        add("Chua co dong hang", "No invoice lines");
        add("Hoa don chua co dong hang nao.", "The invoice has no lines yet.");
        add("Hoa don chua co dong hang nao, chua biet phai thu bao nhieu.", "The invoice has no lines, so there is nothing to collect.");
        add("Hoa don nay da thu du tien.", "The invoice is fully paid.");
        add("Hoa don nay da thu du tien (", "The invoice is fully paid (");
        add(").\nKhong can them dong thanh toan nao nua.", ").\nNo additional payment is needed.");
        add("Da thu du tien", "Fully paid");
        add("Chua co dong thanh toan nao.", "No payments have been added.");
        add("Chua co thanh toan", "No payments");
        add("Chua nap duoc danh sach quay / thu ngan.\nKiem tra ket noi MySQL roi bam \"Tai lai\" o khung 1.",
                "Could not load the counter / cashier list.\nCheck the MySQL connection, then click \"Reload\" in section 1.");
        add("Thieu du lieu danh muc", "Missing lookup data");
        add("So tien ", "Amount ");
        add(" nhieu hon so con thieu ", " exceeds the balance due of ");
        add("Khong co duong tra lai cho ", "There is no way to return excess funds for ");
        add(": bang Payment khong co cot tien thoi cho phuong thuc nay,\nva phieu hoan tien nam ngoai pham vi ban demo.\n\nHay go dung ",
                ": the Payment table has no change column for this method,\nand refunds are outside this demo.\n\nEnter exactly ");
        add(", hoac chon \"Tien mat\" neu khach dua thua.", ", or choose \"Cash\" if the customer pays extra.");
        add("So tien vuot qua so con thieu", "Amount exceeds balance due");
        add("Vi dien tu khong co tien thoi, hay go dung ", "E-wallet payments cannot give change; enter exactly ");
        add("Hoa don chua co dong hang nao, chua biet phai thu bao nhieu.", "The invoice has no lines, so there is nothing to collect.");
        add("Chua nhap ", "Please enter ");
        add("Chua nhap so tien", "Amount is required");
        add("Chua nhap so luong.", "Quantity is required.");
        add("So luong khong hop le", "Invalid quantity");
        add("So tien khong hop le", "Invalid amount");
        add(" khong doc duoc: \"", " could not be read: \"");
        add("Dau . la dau tach nghin (phai du 3 chu so),\n", "Use . as the thousands separator (groups of 3 digits),\n");
        add("dau , la dau thap phan (toi da 2 chu so).\n\n", "and , as the decimal separator (up to 2 digits).\n\n");
        add("Dung:  500.000   |   1.234,50   |   500000\n", "Valid: 500.000 | 1.234,50 | 500000\n");
        add("Sai :  500.00    |   1.2.3      |   1,234", "Invalid: 500.00 | 1.2.3 | 1,234");
        add(" khong duoc am.", " cannot be negative.");
        add(" phai lon hon 0.", " must be greater than 0.");
        add("Chi duoc mot dau thap phan trong so luong: ", "Use only one decimal separator in the quantity: ");
        add("So luong khong hop le: ", "Invalid quantity: ");
        add("Dau , hoac . la dau thap phan, KHONG phai dau tach nghin.\n", "Use , or . as the decimal separator, not a thousands separator.\n");
        add("Dung:  2   |   1,5   |   1.5   |   0,25", "Valid: 2 | 1,5 | 1.5 | 0,25");
        add("Toi da 3 chu so thap phan (cot DECIMAL(10,3)).\n", "Use at most 3 decimal places (DECIMAL(10,3)).\n");
        add("Ban go: ", "You entered: ");
        add("So luong phai lon hon 0.", "Quantity must be greater than 0.");
        add("Chi con ", "Only ");
        add(" co the tra.", " can be returned.");
        add("Da luu hoa don ", "Saved invoice ");
        add(" nhung khong doc lai duoc de in.", " but could not reload it for printing.");
        add("Da luu hoa don", "Saved invoice");
        add("Khong cap nhat duoc hoa don", "Could not update invoice");
        add("Khong tim thay hoa don", "Invoice not found");
        add("Khong tim thay dong hang", "Invoice line not found");
        add("Khong co dong hang", "No invoice lines");
        add("Chua co dong hang nao", "No invoice lines");
        add("Khong doc duoc hoa don", "Could not load invoice");
        add("In hoa don", "Print receipt");
        add("Dong", "Close");
        add("Xac nhan da nhan tien", "Confirm payment received");
        add("Nguoi duyet", "Approver");
        add("Gia", "Price");
        add("Don gia", "Unit price");
        add("So luong", "Quantity");
        add("Giam gia", "Discount");
        add("Ngay lap phai dung dinh dang YYYY-MM-DD!", "Invoice date must use YYYY-MM-DD format.");
        add("Gio lap phai dung dinh dang HH:MM:SS!", "Invoice time must use HH:MM:SS format.");
        add("Vui long chon mot hoa don de chinh sua!", "Select an invoice to edit.");
        add("Chua chon hoa don", "No invoice selected");
        add("Vui long chon mot hoa don de huy!", "Select an invoice to void.");
        add("Vui long chon mot hoa don de xoa!", "Select an invoice to delete.");
        add("Vui long chon mot dong hang trong bang 'Cac dong hang' de chinh sua!", "Select an invoice line in the 'Invoice lines' table to edit it.");
        add("Vui long chon mot dong hang de xoa!", "Select an invoice line to delete.");
        add("Vui long chon mot hoa don de tra hang!", "Select an invoice to return items.");
        add("Hoa don khong co dong hang nao de tra!", "This invoice has no lines to return.");
        add("Vui long chon mot dong hang de tra!", "Select a line to return.");
        add("Vui long chon mot luot tra!", "Select a return.");
        add("Chua chon luot tra", "No return selected");
        add("Khong tim thay hoa don ", "Invoice not found: ");
        add("Khong tim thay dong hang so ", "Invoice line not found: ");
        add("Da xoa thanh cong hoa don ", "Deleted invoice ");
        add(" khoi Database!", " from the Database.");
        add("Da xoa thanh cong dong hang ", "Deleted invoice line ");
        add("Da huy hoa don", "Invoice voided");
        add("Xac nhan xoa dong hang", "Confirm line deletion");
        add("Xac nhan", "Confirm");
        add("Xoa thanh cong", "Deleted successfully");
        add("Dang hien ", "Showing ");
        add(" hoa don (", " invoices (");
        add("Khong co gi bi huy", "Nothing was voided");
        add("Canh bao", "Warning");
        add("Loi co so du lieu", "Database error");
        add("Lỗi cơ sở dữ liệu", "Database error");
        add("Lỗi khi tải dữ liệu từ bảng '", "Could not load data from table '");
        add("Lỗi khi xác định cột tìm kiếm:\n", "Could not determine the search column:\n");
        add("Lỗi khi thực hiện tìm kiếm:\n", "Search failed:\n");
        add("Tìm kiếm không hỗ trợ bảng ", "Search is not supported for table ");
        add("So luong moi cho \"", "New quantity for \"");
        add("Dau , hoac . la dau thap phan, toi da 3 chu so.", "Use comma or period as the decimal separator, up to 3 decimal places.");
        add("Tong hoa don ", "Invoice total ");
        add(" nhung chi thanh toan ", " but only ");
        add(" (thieu ", " paid (balance due: ");
        add(").\n\nVan luu, nhung hoa don se o trang thai DANG LAP (OPEN):\n  - Khong duoc tinh vao bao cao doanh thu\n  - Khong tra hang duoc\n  - Nhan vien can thu tiep o tab 3", ").\n\nSave it anyway? The invoice will remain OPEN:\n  - It will not count toward revenue reports\n  - Items cannot be returned\n  - Collect the remaining balance from tab 3");
        add("Huy hoa don ", "Void invoice ");
        add("Huy hoa don ", "Void invoice ");
        add("Huy KHAC Xoa:\n  - Du lieu van con nguyen: dong hang, thanh toan, tra hang\n  - Hoa don khong con duoc tinh vao bao cao doanh thu\n  - Khong tra hang duoc nua\n\nBan co chac chan?", "Voiding is different from deleting:\n  - Invoice lines, payments, and returns remain\n  - The invoice is excluded from revenue reports\n  - No more items can be returned\n\nContinue?");
        add("Da huy hoa don ", "Voided invoice ");
        add(".\nDu lieu van con trong database, chi khong tinh vao bao cao nua.", ".\nThe data remains in the database but is excluded from reports.");
        add("Ban co chac chan muon XOA VINH VIEN hoa don ", "Permanently delete invoice ");
        add(" khoi Database?\n\nThao tac nay se xoa sach:\n  1. Cac luot tra hang lien quan (Return)\n  2. Cac chi tiet thanh toan (Cash/Card/EWallet_Payment)\n  3. Cac luot thanh toan (Payment)\n  4. Cac dong hang (Invoice_Line)\n  5. Chinh hoa don (Invoice)\n\nThao tac nay KHONG THE HOAN TAC. Ban co chac chan?", " from the Database?\n\nThis will permanently delete:\n  1. Related returns\n  2. Payment details (Cash/Card/EWallet_Payment)\n  3. Payments\n  4. Invoice lines (Invoice_Line)\n  5. The invoice itself\n\nThis action cannot be undone. Continue?");
        add("Ban co chac chan muon xoa dong hang so ", "Delete invoice line ");
        add(" khoi hoa don ", " from invoice ");
        add("Vui long chon mot dong hang o bang ben trai de them!", "Select an invoice line from the left table to add it.");
        add("Dong ", "Line ");
        add(" da co trong danh sach se tra.\n\nMuon doi so luong: chon muc do o bang ben phai, bam '<< Xoa',\nroi 'Them >>' lai voi so luong khac.", " is already in the return list.\n\nTo change the quantity, select the line on the right, click '<< Remove',\nthen add it again with the new quantity.");
        add(" da tra het ", " has already been returned in full (");
        add(" da mua,\nkhong con gi de tra.", " purchased);\nthere is nothing left to return.");
        add("San pham: ", "Product: ");
        add("Da mua: ", "Purchased: ");
        add("   |   Da tra: ", "   |   Returned: ");
        add("   |   Con lai: ", "   |   Remaining: ");
        add("Vui long chon mot muc o bang ben phai de xoa!", "Select a line from the right table to remove it.");
        add("Khong co san pham nao de tra!", "There are no items available to return.");
        add("Ghi ", "Record ");
        add(" luot tra hang cho hoa don ", " return(s) for invoice ");
        add("Tong tien phai tra lai: ", "Total refund: ");
        add("Nguoi duyet: ", "Approved by: ");
        add("Xac nhan xu ly tra hang", "Confirm returns");
        add("Da ghi ", "Recorded ");
        add(" luot tra hang cho hoa don ", " return(s) for invoice ");
        add("Tong tien tra lai: ", "Total refund: ");
        add("Vui long chon mot luot tra trong bang 'Tra hang (Return)' de huy!", "Select a return in the 'Returns (Return)' table to delete it.");
        add("Khong doc duoc so dong / lan tra cua hang dang chon.", "Could not read the selected invoice line / return number.");
        add("Huy luot tra nay khoi Database?\n\n  Hoa don : ", "Delete this return from the Database?\n\n  Invoice: ");
        add("  Dong    : ", "  Line: ");
        add("  Lan tra : ", "  Return: ");
        add("  SL tra  : ", "  Qty returned: ");
        add("Khong tim thay luot tra lan ", "Return ");
        add(" cua dong ", " for line ");
        add(" (co the ai khac da huy truoc).", " was not found (someone else may have deleted it).");
        add("Da huy luot tra lan ", "Deleted return ");
        add(" cua dong ", " for line ");
        add("Huy luot tra nay khoi Database?", "Delete this return from the Database?");
        add("Chuc nang nay bi khoa voi Thu ngan (Cashier). Chi Quan ly (Manager) moi co quyen.",
                "This action is disabled for Thu ngan (Cashier). Only Quan ly (Manager) can use it.");
        add("Khong tra hang duoc", "Cannot return items");
        add("Dong nay da duoc chon", "Line already selected");
        add("Da tra het", "Fully returned");
        add("Chua chon dong hang", "No invoice line selected");
        add("Chua chon muc nao", "No line selected");
        add("Danh sach trong", "Empty list");
        add("Xac nhan xu ly tra hang", "Confirm returns");
        add("Tất cả", "All");
        add("Tất cả", "All");
        add("Tong tien ", "Invoice total ");
        add(" nhung chi thanh toan ", " but only ");
        add(" (thieu ", " paid (balance due: ");
        add("Van luu, nhung hoa don se o trang thai DANG LAP (OPEN):\n", "Save it anyway? The invoice will remain OPEN:\n");
        add("  - Khong duoc tinh vao bao cao doanh thu\n", "  - It will not count toward revenue reports\n");
        add("  - Khong tra hang duoc\n", "  - Items cannot be returned\n");
        add("  - Nhan vien can thu tiep o tab 3", "  - Collect the remaining balance from tab 3");
        add("Tong hoa don ", "Invoice total ");
        add("Giam gia", "Discount");
        add("Tang gia", "Price increase");
        add("Khong co", "None");
        add("Dang hien ", "Showing ");
        add("hoa don", "invoice");
        add("hoa don", "invoice");

        add("Ma khach", "Customer ID");
        add("Khong co", "None");
        add("Xoa ma khach", "Clear customer ID");
        add("Xac nhan xoa", "Confirm deletion");
        add("Ten hang", "Item");
        add("Thoi gian", "Date and time");
        add("Quay", "Counter");
        add("Khach hang", "Customer");
        add("Ma HD", "Invoice ID");
        add("HOA DON BAN LE", "RETAIL RECEIPT");
        add("Thanh toan bang ma QR (VietQR)", "QR payment (VietQR)");
        add("So tien trong ma QR: ", "Amount in QR code: ");
        add("Chuyen toi: ", "Transfer to: ");
        add("Tai khoan: ", "Account: ");
        add("Noi dung CK (Transaction_Ref): ", "Transfer reference (Transaction_Ref): ");
        add("Ban demo: viec doi soat la THU CONG -- bam \"Xac nhan\" khi da thay tien vao.",
                "Demo only: verify payments manually. Click \"Confirm\" after the money arrives.");
        add("Tra hang -- hoa don ", "Return items -- invoice ");
        add("Hoa don: ", "Invoice: ");
        add("  |  Ngay: ", "  |  Date: ");
        add("  |  Trang thai: ", "  |  Status: ");
        add("Thoi gian", "Date and time");
        add("Ma HD", "Invoice ID");
        add("Ten hang", "Item");
        add("Tong cong", "Grand total");
        add("Tong tien", "Total");
        add("Da thu", "Paid");
        add("Con thieu", "Balance due");
        add("Tien thoi", "Change");
        add("Cac dong", "Lines");
        add("Don gia", "Unit price");
        add("Thanh tien", "Line total");
        add("Phuong thuc", "Method");
        add("So tien", "Amount");
        add("Da gui lenh in hoa don.", "The print command was sent.");
        updateSwingLanguageDefaults();
    }

    private I18n() { }

    private static void add(String source, String english) {
        EN.put(normalize(source), english);
        VI.put(normalize(english), source);
        EN_SOURCE.put(normalize(source), source);
    }

    public static Language language() { return language; }
    public static Language otherLanguage() {
        return language == Language.VIETNAMESE ? Language.ENGLISH : Language.VIETNAMESE;
    }
    public static void setLanguage(Language next) {
        language = Objects.requireNonNull(next);
        updateSwingLanguageDefaults();
    }
    public static String otherLanguageName() {
        return language == Language.VIETNAMESE ? "English" : "Tiếng Việt";
    }
    public static String text(String source) {
        if (source == null) return null;
        // "Đồng" is also the Vietnamese currency name; keep currency values intact.
        if (normalize(source).equals("dong") && source.toLowerCase(Locale.ROOT).contains("đồng")) return source;
        String mapped = language == Language.ENGLISH ? EN.get(normalize(source)) : VI.get(normalize(source));
        return mapped == null ? source : mapped;
    }

    public static void setPreservedText(JLabel label, String text) {
        label.putClientProperty(PRESERVE_TEXT, Boolean.TRUE);
        label.setText(text);
    }
    public static void setPreservedToolTip(JComponent component, String text) {
        component.putClientProperty(PRESERVE_TOOLTIP, Boolean.TRUE);
        component.setToolTipText(text);
    }

    private static String tableHeaderText(String source) {
        if (source != null && normalize(source).equals("dong")
                && source.toLowerCase(Locale.ROOT).contains("đồng")) return source;
        if (source != null && normalize(source).equals("dong")) {
            return language == Language.ENGLISH ? "Line" : "Dong";
        }
        return text(source);
    }

    /** Translate longer messages built from fixed UI phrases and dynamic values. */
    public static String richText(String source) {
        if (source == null) return null;
        String exact = text(source);
        if (!Objects.equals(exact, source)) return exact;
        Map<String, String> replacements = language == Language.ENGLISH ? EN : VI;
        if (language == Language.VIETNAMESE) {
            Map<String, String> englishToSource = new HashMap<>();
            for (Map.Entry<String, String> entry : VI.entrySet()) {
                englishToSource.put(entry.getKey(), entry.getValue());
            }
            replacements = englishToSource;
        }
        return translatePhrases(source, replacements, language == Language.ENGLISH);
    }

    public static void showMessageDialog(Component parent, Object message) {
        JOptionPane.showMessageDialog(parent, localizeMessage(message));
    }
    public static void showMessageDialog(Component parent, Object message, String title, int messageType) {
        JOptionPane.showMessageDialog(parent, localizeMessage(message), richText(title), messageType);
    }
    public static void showMessageDialog(Component parent, Object message, String title, int messageType, Icon icon) {
        JOptionPane.showMessageDialog(parent, localizeMessage(message), richText(title), messageType, icon);
    }
    public static int showConfirmDialog(Component parent, Object message) {
        return JOptionPane.showConfirmDialog(parent, localizeMessage(message));
    }
    public static int showConfirmDialog(Component parent, Object message, String title, int optionType) {
        return JOptionPane.showConfirmDialog(parent, localizeMessage(message), richText(title), optionType);
    }
    public static int showConfirmDialog(Component parent, Object message, String title, int optionType, int messageType) {
        return JOptionPane.showConfirmDialog(parent, localizeMessage(message), richText(title), optionType, messageType);
    }
    public static int showConfirmDialog(Component parent, Object message, String title, int optionType, int messageType, Icon icon) {
        return JOptionPane.showConfirmDialog(parent, localizeMessage(message), richText(title), optionType, messageType, icon);
    }
    public static String showInputDialog(Component parent, Object message) {
        return JOptionPane.showInputDialog(parent, localizeMessage(message));
    }
    public static String showInputDialog(Component parent, Object message, Object initialValue) {
        return JOptionPane.showInputDialog(parent, localizeMessage(message), initialValue);
    }
    public static String showInputDialog(Component parent, Object message, String title, int messageType) {
        return JOptionPane.showInputDialog(parent, localizeMessage(message), richText(title), messageType);
    }
    public static Object showInputDialog(Component parent, Object message, String title, int messageType,
                                         Icon icon, Object[] selectionValues, Object initialValue) {
        return JOptionPane.showInputDialog(parent, localizeMessage(message), richText(title), messageType,
                icon, selectionValues, initialValue);
    }
    private static Object localizeMessage(Object message) {
        return message instanceof String s ? richText(s) : message;
    }

    /** Refresh visible labels without recreating panels or losing entered data. */
    public static void refreshTree(Component root) {
        if (root == null) return;
        if (root instanceof JLabel label && !Boolean.TRUE.equals(label.getClientProperty(PRESERVE_TEXT))) {
            label.setText(localizeCached(label, label.getText(), SOURCE_TEXT, SHOWN_TEXT));
        } else if (root instanceof AbstractButton button && !Boolean.TRUE.equals(button.getClientProperty(PRESERVE_TEXT))) {
            button.setText(localizeCached(button, button.getText(), SOURCE_TEXT, SHOWN_TEXT));
        }
        if (root instanceof JComponent jc) {
            if (!Boolean.TRUE.equals(jc.getClientProperty(PRESERVE_TOOLTIP))) {
                jc.setToolTipText(localizeCached(jc, jc.getToolTipText(), SOURCE_TOOLTIP, SHOWN_TOOLTIP));
            }
            localizeBorder(jc.getBorder());
            if (jc instanceof JTable table) installTableTranslation(table);
            if (jc instanceof JComboBox<?> combo) installComboTranslation(combo);
            if (jc instanceof JList<?> list) installListTranslation(list);
        }
        if (root instanceof JTabbedPane tabs) {
            for (int i = 0; i < tabs.getTabCount(); i++) {
                String source = cachedTabText(tabs, i, tabs.getTitleAt(i));
                tabs.setTitleAt(i, richText(source));
                rememberTabShown(tabs, i, tabs.getTitleAt(i));
            }
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) refreshTree(child);
        }
        if (root instanceof JComponent jc) jc.revalidate();
        root.repaint();
    }

    private static String localizeCached(JComponent component, String current, String sourceKey, String shownKey) {
        if (current == null) return null;
        String source = (String) component.getClientProperty(sourceKey);
        String lastShown = (String) component.getClientProperty(shownKey);
        if (source == null || !Objects.equals(current, lastShown)) {
            source = language == Language.ENGLISH ? richTextFor(current, Language.VIETNAMESE) : current;
            component.putClientProperty(sourceKey, source);
        }
        String shown = richText(source);
        component.putClientProperty(shownKey, shown);
        return shown;
    }

    private static String richTextFor(String source, Language target) {
        Language previous = language;
        language = target;
        try { return richText(source); }
        finally { language = previous; }
    }

    private static void localizeBorder(Border border) {
        if (border instanceof TitledBorder titled && titled.getTitle() != null) {
            String title = titled.getTitle();
            titled.setTitle(richText(title));
        } else if (border instanceof CompoundBorder compound) {
            localizeBorder(compound.getOutsideBorder());
            localizeBorder(compound.getInsideBorder());
        }
    }

    private static final Map<JTabbedPane, List<String>> tabSources = new WeakHashMap<>();
    private static final Map<JTabbedPane, List<String>> tabShown = new WeakHashMap<>();
    private static String cachedTabText(JTabbedPane tabs, int index, String current) {
        List<String> sources = tabSources.computeIfAbsent(tabs, k -> new ArrayList<>());
        List<String> shown = tabShown.computeIfAbsent(tabs, k -> new ArrayList<>());
        while (sources.size() <= index) sources.add(null);
        while (shown.size() <= index) shown.add(null);
        if (sources.get(index) == null || !Objects.equals(current, shown.get(index))) {
            sources.set(index, language == Language.ENGLISH ? richTextFor(current, Language.VIETNAMESE) : current);
        }
        return sources.get(index);
    }
    private static void rememberTabShown(JTabbedPane tabs, int index, String current) {
        List<String> shown = tabShown.get(tabs);
        while (shown.size() <= index) shown.add(null);
        shown.set(index, current);
    }

    static void installTableTranslation(JTable table) {
        TableCellRenderer header = table.getTableHeader().getDefaultRenderer();
        if (!(header instanceof TranslatingRenderer)) {
            table.getTableHeader().setDefaultRenderer(new TranslatingRenderer(header, true));
        }
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            var column = table.getColumnModel().getColumn(i);
            TableCellRenderer renderer = column.getCellRenderer();
            if (renderer == null) renderer = table.getDefaultRenderer(Object.class);
            if (!(renderer instanceof TranslatingRenderer)) column.setCellRenderer(new TranslatingRenderer(renderer, false));
        }
    }

    @SuppressWarnings("unchecked")
    private static void installComboTranslation(JComboBox<?> combo) {
        ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) combo.getRenderer();
        if (renderer != null && !(renderer instanceof TranslatingListRenderer)) {
            combo.setRenderer(new TranslatingListRenderer(renderer));
        }
    }

    @SuppressWarnings("unchecked")
    private static void installListTranslation(JList<?> list) {
        ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) list.getCellRenderer();
        if (renderer != null && !(renderer instanceof TranslatingListRenderer)) {
            list.setCellRenderer(new TranslatingListRenderer(renderer));
        }
    }

    private static final class TranslatingRenderer implements TableCellRenderer {
        private final TableCellRenderer delegate;
        private final boolean header;
        private TranslatingRenderer(TableCellRenderer delegate, boolean header) {
            this.delegate = delegate;
            this.header = header;
        }
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focus, int row, int column) {
            Component result = delegate.getTableCellRendererComponent(table,
                    value instanceof String s ? (header ? tableHeaderText(s) : text(s)) : value,
                    selected, focus, row, column);
            if (result instanceof JLabel label) {
                label.setText(header ? tableHeaderText(label.getText()) : text(label.getText()));
            }
            return result;
        }
    }

    private static final class TranslatingListRenderer implements ListCellRenderer<Object> {
        private final ListCellRenderer<Object> delegate;
        private TranslatingListRenderer(ListCellRenderer<Object> delegate) { this.delegate = delegate; }
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            Component result = delegate.getListCellRendererComponent(list, value, index, selected, focus);
            if (result instanceof JLabel label) label.setText(text(label.getText()));
            return result;
        }
    }

    private static void updateSwingLanguageDefaults() {
        UIManager.put("OptionPane.yesButtonText", language == Language.ENGLISH ? "Yes" : "Có");
        UIManager.put("OptionPane.noButtonText", language == Language.ENGLISH ? "No" : "Không");
        UIManager.put("OptionPane.cancelButtonText", language == Language.ENGLISH ? "Cancel" : "Hủy");
        UIManager.put("OptionPane.okButtonText", language == Language.ENGLISH ? "OK" : "Đồng ý");
    }

    private static String normalize(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String ascii = decomposed.replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D');
        return ascii.toLowerCase(Locale.ROOT);
    }

    private static String translatePhrases(String source, Map<String, String> map, boolean sourceIsVietnamese) {
        NormalizedText normalized = normalizeWithOffsets(source);
        StringBuilder out = new StringBuilder();
        int sourceCursor = 0;
        int i = 0;
        while (i < normalized.text.length()) {
            String found = null;
            for (String candidate : map.keySet()) {
                boolean phrase = candidate.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
                if (!phrase || candidate.length() < 4 || candidate.length() <= (found == null ? 0 : found.length())) continue;
                if (!normalized.text.startsWith(candidate, i)) continue;
                int end = i + candidate.length();
                int coreStart = i;
                int coreEnd = end;
                while (coreStart < coreEnd && Character.isWhitespace(normalized.text.charAt(coreStart))) coreStart++;
                while (coreEnd > coreStart && Character.isWhitespace(normalized.text.charAt(coreEnd - 1))) coreEnd--;
                boolean leftBoundary = coreStart > i || coreStart == 0
                        || !Character.isLetterOrDigit(normalized.text.charAt(coreStart - 1));
                boolean rightBoundary = coreEnd < end || coreEnd == normalized.text.length()
                        || !Character.isLetterOrDigit(normalized.text.charAt(coreEnd));
                if (leftBoundary && rightBoundary) found = candidate;
            }
            if (found == null) {
                i++;
                continue;
            }
            int sourceStart = normalized.starts[i];
            int sourceEnd = normalized.ends[i + found.length() - 1];
            out.append(source, sourceCursor, sourceStart);
            String replacement = map.get(found);
            if (sourceIsVietnamese && language == Language.VIETNAMESE) replacement = EN_SOURCE.getOrDefault(found, replacement);
            out.append(replacement);
            sourceCursor = sourceEnd;
            i += found.length();
        }
        out.append(source, sourceCursor, source.length());
        return out.toString();
    }

    private static NormalizedText normalizeWithOffsets(String value) {
        StringBuilder normalized = new StringBuilder();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            int next = i + Character.charCount(cp);
            String part = new String(Character.toChars(cp));
            String ascii = Normalizer.normalize(part, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
            for (int j = 0; j < ascii.length(); j++) {
                normalized.append(ascii.charAt(j));
                starts.add(i);
                ends.add(next);
            }
            i = next;
        }
        return new NormalizedText(normalized.toString(), toIntArray(starts), toIntArray(ends));
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    private record NormalizedText(String text, int[] starts, int[] ends) { }
}
