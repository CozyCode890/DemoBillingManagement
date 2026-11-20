package billing.ui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.text.JTextComponent;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

/** Small, runtime language switcher for the Swing interface. */
public final class I18n {
    public enum Language { VI, EN }

    private static final String DYNAMIC_TEXT = "billing.i18n.dynamicText";
    private static final String TEXT_SOURCE = "billing.i18n.textSource";
    private static final String TEXT_LAST = "billing.i18n.textLast";
    private static final String TEXT_LISTENER = "billing.i18n.textListener";
    private static final String UPDATING_TEXT = "billing.i18n.updatingText";
    private static final String TIP_SOURCE = "billing.i18n.tipSource";
    private static final String TIP_LAST = "billing.i18n.tipLast";
    private static final String TIP_LISTENER = "billing.i18n.tipListener";
    private static final String UPDATING_TIP = "billing.i18n.updatingTip";
    private static final String BORDER_SOURCE = "billing.i18n.borderSource";
    private static final String BORDER_LAST = "billing.i18n.borderLast";
    private static final String COMBO_RENDERER = "billing.i18n.comboRenderer";
    private static final String TABLE_LISTENER = "billing.i18n.tableListener";
    private static final String TEXT_COMPONENT = "billing.i18n.localizedTextComponent";
    private static final String KEY_VALUE_TEXT = "billing.i18n.keyValueText";
    private static final Map<String, String> TO_EN = new LinkedHashMap<>();
    private static final Map<String, String> TO_VI = new HashMap<>();
    private static final Map<String, String> TO_VI_MARKS = new LinkedHashMap<>();
    private static final Map<TableColumn, String[]> COLUMN_TEXT = new WeakHashMap<>();
    private static final Map<Window, String[]> WINDOW_TITLES = new WeakHashMap<>();
    private static final Map<JComponent, Runnable> LANGUAGE_CHANGE_CALLBACKS = new WeakHashMap<>();

    private static Language language = Language.VI;
    /** Bo nho dem cho {@link #sortedKeys}; null = phai tinh lai. */
    private static Map<Language, java.util.List<String>> sortedKeys;
    private static boolean windowListenerInstalled;

    static {
        pairs(
            "Ngôn ngữ", "Language",
            "Dang xuat", "Log out",
            "Doi vai tro", "Switch role",
            "1. Lap hoa don", "1. New invoice",
            "2. Tra cứu dữ liệu", "2. Data search",
            "3. Xem hoa don (Database)", "3. Invoice database",
            "4. Bao cao", "4. Reports",
            "(DANG NHAP TU DONG)", "(AUTOMATIC LOGIN)",
            "App da dang nhap san, khong qua man hinh dang nhap. Dat quick.login.auto= (de trong) trong config.properties de tat.",
                "The app signed in automatically without showing the login screen. Set quick.login.auto= (empty) in config.properties to turn this off.",
            "Doi sang tai khoan khac ma KHONG mat du lieu dang nhap o tab 1", "Switch accounts without losing the invoice in tab 1",
            "Nguoi dung: ", "User: ",
            "  Nguoi dung: ", "  User: ",
            "Vai tro: ", "Role: ",
            "(Chua dang nhap)", "(Not signed in)",
            "Tai khoan ", "Account ",
            " trong config.properties khong dang nhap duoc.\nCo the mat khau trong bang Account da bi doi.\n\nPhien hien tai van duoc giu nguyen.",
                " in config.properties could not sign in.\nThe password in the Account table may have changed.\n\nThe current session remains active.",
            "Doi vai tro that bai", "Could not switch account",
            "Ban co chac chan muon dang xuat khoi he thong?", "Are you sure you want to log out?",
            "Tab \"Lap hoa don\" dang co ", "Tab \"New invoice\" has ",
            " chua luu.\nDang xuat se mat toan bo. Van dang xuat?\n\nGoi y: muon xem giao dien cua vai tro khac ma khong mat gio hang\nthi dung nut \"Doi vai tro\" ben canh.",
                " unsaved.\nLogging out will discard it. Continue?\n\nTip: use \"Switch role\" to view another role without losing the cart.",
            "Xac nhan dang xuat", "Confirm log out",

            "Tai lai", "Reload",
            "Them vao hoa don", "Add to invoice",
            "Sua so luong", "Edit quantity",
            "Xoa dong dang chon", "Remove selected row",
            "Them thanh toan", "Add payment",
            "Khach tra du bang tien mat", "Customer pays exact cash amount",
            "Tao ma QR", "Create QR code",
            "Xoa het", "Clear all",
            "XUAT HOA DON  (INSERT + COMMIT)", "CREATE INVOICE  (INSERT + COMMIT)",
            "Dang luu...", "Saving...",
            "Quay:", "Counter:",
            "Thu ngan:", "Cashier:",
            "Ngay ban:", "Sale date:",
            "Khach (SDT):", "Customer (phone):",
            "San pham:", "Product:",
            "So luong:", "Quantity:",
            "Phuong thuc:", "Payment method:",
            "1. Thong tin hoa don", "1. Invoice details",
            "2. Them san pham", "2. Add products",
            "3. Cac dong hang (se thanh bang Invoice_Line)", "3. Items (saved as Invoice_Line)",
            "4. Thanh toan (mot hoa don co the co NHIEU dong thanh toan)", "4. Payments (an invoice can have multiple payments)",
            "Meo: doi 'Ngay ban' ve nam ngoai roi them san pham -> don gia se lay theo GIA CU trong Price_History.",
                "Tip: set the sale date to a past year, then add a product to use its historical Price_History price.",
            "Dau , hoac . la dau thap phan, toi da 3 chu so (cot DECIMAL(10,3)). Dung: 2 | 1,5 | 1.5 | 0,25 Khac hẳn o tien ben duoi, noi dau . la dau tach nghin. Enter = Them vao hoa don.",
                "Use , or . as the decimal separator (up to 3 digits; DECIMAL(10,3)). Examples: 2 | 1.5 | 1,5 | 0.25. In the amount field below, . separates thousands. Press Enter to add to the invoice.",
            "Dau . la dau TACH NGHIN (phai du 3 chu so), dau , la dau thap phan. Dung: 500.000 | 1.234,50 | 500000 Sai : 500.00 | 1.2.3 | 1,234 Khac hẳn o so luong o tren, noi dau . la dau thap phan. Enter = Them thanh toan.",
                "Use . as the thousands separator (followed by 3 digits) and , as the decimal separator. Examples: 500.000 | 1.234,50 | 500000. Invalid: 500.00 | 1.2.3 | 1,234. Unlike quantity above, . is a decimal separator there. Press Enter to add payment.",
            "Cot VAT % la thue suat cua san pham; don gia trong Price_History DA GOM VAT.", "VAT % is the product tax rate; the Price_History unit price already includes VAT.",
            "Tong: ", "Total: ",
            "Da thu: ", "Paid: ",
            "Con thieu: ", "Remaining: ",
            "Tien thoi: ", "Change: ",

            "Tìm kiếm", "Search",
            "Tải lại / Xem tất cả", "Reload / Show all",
            "Chọn bảng: ", "Table: ",
            "Từ khóa: ", "Search term: ",
            "Tổng số hàng: 0 dòng", "Total rows: 0 rows",
            "Tổng số hàng: ", "Total rows: ",
            " dòng", " rows",
            "Đang hiện ", "Showing ",
            " dòng đầu (bảng còn nhiều hơn — dùng bộ lọc để thu hẹp)", " first rows (more exist; use filters to narrow results)",
            "Chưa cấu hình bộ lọc cho bảng ", "No filters are configured for table ",
            "Hình thức thanh toán: ", "Payment method: ",
            "Số tiền: ", "Amount: ",
            "Từ: ", "From: ",
            "Đến: ", "To: ",
            "Trạng thái hóa đơn: ", "Invoice status: ",
            "Thời gian: ", "Date range: ",
            "Loại khách: ", "Customer type: ",
            "Đơn vị tính: ", "Unit: ",
            "Khoáng giá sản phẩm: ", "Product price range: ",
            "Thuế suất: ", "Tax rate: ",
            "Thời gian trả hàng: ", "Return date: ",
            "Số lượng hoàn trả: ", "Return quantity: ",
            "Tất cả", "All",
            "Tiền mặt", "Cash",
            "Tien mat", "Cash",
            "Thẻ", "Card",
            "The ngan hang", "Bank card",
            "Ví điện tử", "E-wallet",
            "Vi dien tu", "E-wallet",
            "Khoảng", "Custom range",
            "Đang lập", "Open",
            "Đã hoàn tất", "Paid",
            "Đã hủy", "Voided",
            "Hôm nay", "Today",
            "7 ngày qua", "Last 7 days",
            "Tháng này", "This month",
            "Tuần này", "This week",
            "Khách vãng lai", "Walk-in customer",
            "Thành viên", "Member",
            "Tất cả", "All",
            "< 100k", "< 100k",
            "100k - 500k", "100k - 500k",
            "> 500k", "> 500k",
            "< 20k", "< 20k",
            "20k - 50k", "20k - 50k",
            "> 50k", "> 50k",
            "7 ngay gan nhat", "Last 7 days",
            "30 ngay gan nhat", "Last 30 days",
            "90 ngay gan nhat", "Last 90 days",
            "Tat ca", "All",
            "Khoang ngay:", "Date range:",
            "Tai lai danh sach", "Reload list",
            "Loc theo ngay ban. 'Tat ca' nap moi hoa don, ke ca hai hoa don tu nam truoc.",
                "Filter by sale date. 'All' loads every invoice, including the two invoices from last year.",
            "Danh sach hoa don (bam de xem chi tiet)", "Invoices (select one to view details)",
            "Thong tin chung + tong tien", "Summary and total",
            "Cac dong hang (Invoice_Line)", "Items (Invoice_Line)",
            "Thanh toan (Payment + bang con)", "Payments (Payment and child tables)",
            "Tra hang (Return)", "Returns (Return)",
            "Sua hoa don", "Edit invoice",
            "Huy hoa don", "Void invoice",
            "Xoa hoa don", "Delete invoice",
            "Sua dong hang", "Edit item",
            "Xoa dong hang", "Delete item",
            "Thu xoa gia tri (Set NULL)", "Test clearing value (Set NULL)",
            "Tra hang", "Process return",
            "Huy luot tra", "Cancel return",
            "Chuc nang nay bi khoa voi Thu ngan (Cashier). Chi Quan ly (Manager) moi co quyen.",
                "This feature is disabled for the CASHIER role. Only the MANAGER role has access.",
            "Chinh sua thong tin hoa don truc tiep trong DB", "Edit invoice details in the database",
            "Huy hoa don: du lieu van con, chi khong tinh vao bao cao", "Void an invoice: keep its data but exclude it from reports",
            "Xoa hoa don va toan bo du lieu lien quan khoi DB", "Delete an invoice and all related database data",
            "Chinh sua so luong, don gia dong hang trong DB", "Edit item quantity and unit price in the database",
            "Xoa dong hang khoi DB", "Delete an item from the database",
            "Thu nghiem co che canh bao xoa gia tri NOT NULL", "Test the warning for clearing a NOT NULL value",
            "Ghi mot luot tra hang cho cac dong cua hoa don dang chon", "Record a return for items in the selected invoice",
            "Huy mot luot tra hang da ghi (chon dong trong bang Tra hang)", "Cancel a recorded return (select a row in the Returns table)",
            "Dang hien ", "Showing ",
            " hoa don (", " invoices (",
            "tat ca", "all",
            " ngay gan nhat", " most recent days",
            "Vui long chon mot hoa don de chinh sua!", "Select an invoice to edit.",
            "Chua chon hoa don", "No invoice selected",
            "Khong tim thay hoa don ", "Invoice not found: ",
            "Loi", "Error",
            "Vui long chon mot hoa don de huy!", "Select an invoice to void.",
            "Xac nhan huy hoa don", "Confirm void invoice",
            "Huy hoa don ", "Void invoice ",
            "?\n\nHuy KHAC Xoa:\n  - Du lieu van con nguyen: dong hang, thanh toan, tra hang\n  - Hoa don khong con duoc tinh vao bao cao doanh thu\n  - Khong tra hang duoc nua\n  - KHONG mo lai duoc: VOIDED la trang thai cuoi\n\nMuon xoa sach khoi database thi dung nut \"Xoa hoa don\".",
                "?\n\nVoiding is different from deleting:\n  - Items, payments, and returns remain\n  - The invoice is excluded from revenue reports\n  - No more returns can be processed\n  - It cannot be reopened: VOIDED is final\n\nTo permanently remove it, use \"Delete invoice\".",
            "Khong huy duoc hoa don", "Could not void invoice",
            "Da huy hoa don ", "Voided invoice ",
            ".\nDu lieu van con trong database, chi khong tinh vao bao cao nua.", ".\nIts data remains in the database but is excluded from reports.",
            "Da huy hoa don", "Invoice voided",
            "Vui long chon mot hoa don de xoa!", "Select an invoice to delete.",
            "Xac nhan xoa hoa don khoi Database", "Confirm permanent invoice deletion",
            "Ban co chac chan muon XOA VINH VIEN hoa don ", "Permanently delete invoice ",
            " khoi Database?\n\nThao tac nay se xoa sach:\n  1. Cac luot tra hang lien quan (Return)\n  2. Cac chi tiet thanh toan (Cash/Card/EWallet_Payment)\n  3. Cac luot thanh toan (Payment)\n  4. Toan bo dong hang (Invoice_Line)\n  5. Phan dau hoa don (Invoice)\n\nHanh dong khong the hoan tac!",
                " from the database?\n\nThis permanently removes:\n  1. Related returns (Return)\n  2. Payment details (Cash/Card/EWallet_Payment)\n  3. Payments (Payment)\n  4. All items (Invoice_Line)\n  5. The invoice header (Invoice)\n\nThis action cannot be undone!",
            "Da xoa thanh cong hoa don ", "Successfully deleted invoice ",
            " khoi Database!", " from the database!",
            "Thanh cong", "Success",
            "Vui long chon mot dong hang trong bang 'Cac dong hang' de chinh sua!", "Select an item in the Items table to edit.",
            "Chua chon dong hang", "No item selected",
            "Khong tim thay dong hang so ", "Item not found: ",
            "Vui long chon mot dong hang de xoa!", "Select an item to delete.",
            "Ban co chac chan muon xoa dong hang so ", "Are you sure you want to delete item ",
            " khoi hoa don ", " from invoice ",
            "Xac nhan xoa dong hang", "Confirm item deletion",
            "Da xoa thanh cong dong hang ", "Successfully deleted item ",
            "Vui long chon mot hoa don de tra hang!", "Select an invoice to process a return.",
            "Tai khoan '", "Account '",
            "' chua duoc gan voi nhan vien\nnao trong bang Cashier, nen khong the dung lam nguoi duyet tra hang.\n\nCach sua: bam \"Nap lai database\" trong Setup App (xoa sach du lieu),\nhoac chay doan ALTER/UPDATE o plan/12 muc 4.1 de giu du lieu dang co.",
                "' is not linked to an employee in the Cashier table, so it cannot approve returns.\n\nTo fix: use \"Reload database\" in Setup App (clears all data), or run the ALTER/UPDATE statements in plan/12 section 4.1 to keep existing data.",
            "Chua xac dinh duoc nguoi duyet", "Could not determine approver",
            "Hoa don khong co dong hang nao de tra!", "This invoice has no items to return.",
            "Thong bao", "Notice",
            "Tra hang -- hoa don ", "Return items -- invoice ",
            "Hoa don: ", "Invoice: ",
            "   |  Ngay: ", "   |  Date: ",
            "   |  Trang thai: ", "   |  Status: ",
            "Cac dong cua hoa don", "Invoice items",
            "Them >>", "Add >>",
            "<< Xoa", "<< Remove",
            "Xac nhan tra hang", "Confirm return",
            "Huy", "Cancel",
            "Tong tien phai tra lai: ", "Total refund: ",
            "Nguoi duyet: ", "Approved by: ",
            "Vui long chon mot dong hang o bang ben trai de them!", "Select an item in the left table to add it.",
            "Dong ", "Item ",
            ") da co trong danh sach se tra.\n\nMuon doi so luong: chon muc do o bang ben phai, bam '<< Xoa',\nroi 'Them >>' lai voi so luong khac.",
                ") is already on the return list.\n\nTo change the quantity, select it in the right table, click '<< Remove', then add it again with a different quantity.",
            "Dong nay da duoc chon", "Item already selected",
            " da tra het ", " has already been fully returned (",
            " da mua,\nkhong con gi de tra.", " purchased), so there is nothing left to return.",
            "Da tra het", "Already returned",
            "San pham: ", "Product: ",
            "Da mua: ", "Purchased: ",
            "    |   Da tra: ", "    |   Returned: ",
            "    |   Con lai: ", "    |   Remaining: ",
            "Nhap so luong tra (toi da 3 chu so thap phan, dung ',' hoac '.'):", "Enter the return quantity (up to 3 decimal places; use ',' or '.'): ",
            "Nhap so luong tra cho dong ", "Enter the return quantity for item ",
            "Vui long chon mot muc o bang ben phai de xoa!", "Select an item in the right table to remove it.",
            "Chua chon muc nao", "No item selected",
            "Khong co san pham nao de tra!", "There are no items to return.",
            "Danh sach trong", "Empty list",
            "Ghi ", "Record ",
            " luot tra hang cho hoa don ", " return(s) for invoice ",
            "Tong tien tra lai: ", "Total refund: ",
            "Da ghi ", "Recorded ",
            " luot tra hang cho hoa don ", " return(s) for invoice ",
            "Vui long chon mot luot tra trong bang 'Tra hang (Return)' de huy!", "Select a return in the Returns (Return) table to cancel it.",
            "Chua chon luot tra", "No return selected",
            "Khong doc duoc so dong / lan tra cua hang dang chon.", "Could not read the selected item's row/return number.",
            "Huy luot tra nay khoi Database?\n\n   Hoa don : ", "Cancel this return in the database?\n\n   Invoice: ",
            "   Dong    : ", "   Item: ",
            "   Lan tra : ", "   Return #: ",
            "   SL tra  : ", "   Quantity: ",
            "   Tien hoan da ghi nhan: ", "   Recorded refund: ",
            "\n\nSau khi huy, so luong nay duoc tra lai vao phan con tra duoc cua dong.\nHanh dong khong the hoan tac!", "\n\nAfter cancellation, this quantity becomes available to return again.\nThis action cannot be undone!",
            "Xac nhan huy luot tra", "Confirm return cancellation",
            "Khong tim thay luot tra lan ", "Return number not found: ",
            " cua dong ", " for item ",
            " (co the ai khac da huy truoc).", " (someone else may have cancelled it already).",
            "Khong co gi bi huy", "Nothing was cancelled",
            "Da huy luot tra lan ", "Cancelled return ",

            "Dang nhap", "Sign in",
            "Thoat", "Exit",
            "Dang nhap he thong -- Retail Invoicing", "Sign in -- Retail Invoicing",
            "Ten dang nhap:", "Username:",
            "Mat khau:", "Password:",
            "Dang nhap nhanh (tai khoan mau cua ban demo)", "Quick sign-in (demo accounts)",
            "Dien san va dang nhap bang tai khoan '", "Fill in and sign in with account '",
            "Bam mot nut de dien san va dang nhap ngay.", "Click a button to fill in the account and sign in.",
            "Phim tat: Alt+1 ... Alt+", "Shortcut: Alt+1 ... Alt+",
            ".  Enter = Dang nhap.", ".  Enter = Sign in.",
            "Vui long nhap day du Ten dang nhap va Mat khau.", "Enter both a username and password.",
            "Dang kiem tra tai khoan...", "Checking account...",
            "Sai ten dang nhap hoac mat khau.", "Incorrect username or password.",
            "Đăng nhập nhanh", "Quick sign-in",

            "Chinh sua Hoa don: ", "Edit invoice: ",
            "Ma hoa don (PK):", "Invoice ID (PK):",
            "(Khong the thay doi)", "(Cannot be changed)",
            "Ngay lap (YYYY-MM-DD) *:", "Invoice date (YYYY-MM-DD) *:",
            "Gio lap (HH:MM:SS) *:", "Invoice time (HH:MM:SS) *:",
            "Trang thai:", "Status:",
            "(chi doc)", "(read-only)",
            "Ma quay (Counter_ID) *:", "Counter ID (Counter_ID) *:",
            "Ma thu ngan (Cashier_ID) *:", "Cashier ID (Cashier_ID) *:",
            "Ma khach hang:", "Customer ID:",
            "Xoa ma khach (Set NULL)", "Clear customer ID (Set NULL)",
            "Customer_ID la truong cho phep NULL (khach vang lai). Gia tri nay se duoc cap nhat thanh NULL trong database.",
                "Customer_ID allows NULL (walk-in customer). This value will be set to NULL in the database.",
            "Thong bao", "Notice",
            "* Cac truong danh dau (*) co rang buoc NOT NULL. Neu xoa trang se bi he thong canh bao!", "* Fields marked (*) are NOT NULL. The app will warn you if you clear one.",
            "Luu vao Database", "Save to database",
            "Huy bo", "Cancel",
            "Ngay lap (Date)", "Invoice date (Date)",
            "Gio lap (Time)", "Invoice time (Time)",
            "Ma quay (Counter_ID)", "Counter ID (Counter_ID)",
            "Ma thu ngan (Cashier_ID)", "Cashier ID (Cashier_ID)",
            "Ngay lap phai dung dinh dang YYYY-MM-DD!", "Date must use the YYYY-MM-DD format.",
            "Loi dinh dang", "Format error",
            "Gio lap phai dung dinh dang HH:MM:SS!", "Time must use the HH:MM:SS format.",
            "Cap nhat hoa don ", "Updated invoice ",
            " thanh cong trong Database!", " successfully in the database!",
            "Khong cap nhat duoc hoa don", "Could not update invoice",
            "CANH BAO DATABASE: Truong '", "DATABASE WARNING: Field '",
            "' co rang buoc NOT NULL!\nBan khong duoc phep xoa gia tri nay ve NULL hoac de trong.", "' is NOT NULL.\nThis value cannot be cleared or left empty.",
            "Canh bao rang buoc NOT NULL", "NOT NULL constraint warning",

            "Chinh sua Dong hang: ", "Edit item: ",
            " (HD: ", " (Invoice: ",
            "Ma vach (Barcode) *:", "Barcode *:",
            "Ma vach (Barcode)", "Barcode",
            "So luong (Quantity) *:", "Quantity *:",
            "So luong (Quantity)", "Quantity",
            "Don gia (Unit_Price) *:", "Unit price (Unit_Price) *:",
            "Don gia (Unit_Price)", "Unit price (Unit_Price)",
            "Giam gia (Discount) *:", "Discount *:",
            "Giam gia (Discount)", "Discount",
            "Thu xoa (Set NULL)", "Try clearing (Set NULL)",
            "Toan bo cac cot trong Invoice_Line deu co rang buoc NOT NULL. Nut 'Thu xoa (Set NULL)' se minh hoa co che canh bao cua Database.",
                "All Invoice_Line columns are NOT NULL. The 'Try clearing (Set NULL)' button demonstrates the database warning.",
            "Cap nhat dong hang ", "Updated item ",
            " thanh cong trong Database!", " successfully in the database!",
            "CANH BAO DATABASE: Cot '", "DATABASE WARNING: Column '",
            "' co rang buoc NOT NULL trong Database!\nBan khong duoc phep xoa gia tri nay ve NULL hoac de trong.", "' is NOT NULL in the database.\nThis value cannot be cleared or left empty.",
            "Trang thai doi tu dong khi thu du tien, hoac qua nut \"Huy hoa don\" o tab Xem hoa don", "Status changes automatically after full payment or by using \"Void invoice\" on the Invoice Database tab",
            "Dau <b>,</b> hoac <b>.</b> la dau THAP PHAN, toi da 3 chu so (cot DECIMAL(10,3)).<br>Dung: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>",
                "Use <b>,</b> or <b>.</b> as the decimal separator, up to 3 digits (DECIMAL(10,3)).<br>Examples: <code>2</code> | <code>1.5</code> | <code>1,5</code></html>",
            "Dau <b>.</b> la dau TACH NGHIN: <code>158.000</code> = 158 nghin.<br>Dau <b>,</b> la dau thap phan (toi da 2 chu so).</html>",
                "<b>.</b> separates thousands: <code>158.000</code> = 158 thousand.<br><b>,</b> is the decimal separator (up to 2 digits).</html>",

            "Chon bao cao", "Select a report",
            "2. Doanh thu 7 ngay / cua hang", "2. Revenue by day / store (last 7 days)",
            "3. San pham tang gia > 10%", "3. Products with price increases > 10%",
            "4. Thu ngan gio hang TB cao nhat", "4. Cashier with highest average invoice",
            "5. Hoa don tra hang > 50%", "5. Invoices with > 50% returned",
            "Bonus: hoa don cu giu gia cu", "Bonus: historical invoice prices",
            "Y nghia cau truy van", "Query explanation",
            "Cau SQL dang chay", "SQL query",
            "Ket qua", "Results",
            " -- dang chay...", " -- running...",
            "Cau lenh chay xong nhung khong co dong nao thoa dieu kien.", "The query finished but returned no matching rows.",

            "Thanh toan bang ma QR (VietQR)", "Pay by QR code (VietQR)",
            "So tien trong ma QR: ", "Amount in QR code: ",
            "Chuyen toi: ", "Transfer to: ",
            "Tai khoan: ", "Account: ",
            "Noi dung CK (Transaction_Ref): ", "Transfer note (Transaction_Ref): ",
            "Ban demo: viec doi soat la THU CONG -- bam \"Xac nhan\" khi da thay tien vao.", "Demo mode: verify the transfer MANUALLY, then click \"Confirm\" after the money arrives.",
            "Xac nhan da nhan tien", "Confirm payment received",
            "In hoa don", "Print receipt",
            "Dong", "Close",
            "HOA DON BAN LE", "RETAIL RECEIPT",
            "Ma HD", "Invoice ID",
            "Thoi gian", "Date and time",
            "Khach hang", "Customer",
            "Ten hang", "Item",
            "SL", "Qty",
            "DVT", "Unit",
            "Don gia", "Unit price",
            "Giam gia / Khuyen mai", "Discount / Promotion",
            "Thanh tien", "Line total",
            "don gia da gom VAT -- thue suat chi la thong tin", "Unit prices include VAT; the tax rate is shown for reference only.",
            "Tong tien hang", "Subtotal",
            "TONG CONG", "TOTAL",
            "Khach dua", "Tendered",
            "Chi tiet", "Details",
            "(chua co dong thanh toan nao)", "(no payments yet)",
            "Tien khach dua", "Customer tendered",
            "Da ghi nhan", "Received",
            "Tien thoi lai", "Change due",
            "Cam on Quy khach &amp; Hen gap lai!", "Thank you, and see you again!",
            "Da luu hoa don ", "Saved invoice ",
            " nhung khong doc lai duoc de in.", " but it could not be reloaded for printing.",
            "Khong doc duoc hoa don", "Could not load invoice",
            "Loi khi thuc hien tim kiem:\n", "Search failed:\n",
            "Loi co so du lieu", "Database error",
            "Co loi xay ra", "An error occurred",
            "Lỗi khi tải dữ liệu từ bảng '", "Could not load data from table '",
            "':\n", "':\n",
            "Lỗi khi xác định cột tìm kiếm:\n", "Could not determine search columns:\n",
            "Lỗi khi thực hiện tìm kiếm:\n", "Search failed:\n"
        );

        pairs(
            "Tat ca", "All",
            "Tất cả", "All",
            "Hôm nay", "Today",
            "7 ngày qua", "Last 7 days",
            "Tháng này", "This month",
            "Đang lập", "Open",
            "Đã hoàn tất", "Paid",
            "Đã hủy", "Voided"
        );

        pairs(
            "Có", "Yes",
            "Không", "No",
            "Đồng ý", "OK",
            "Hủy", "Cancel",
            "So tien", "Amount",
            "Khach dua", "Tendered",
            "Hoa don", "Invoice",
            "Ngay", "Date",
            "Gio", "Time",
            "Trang thai", "Status",
            "Ma_HD", "Invoice ID",
            "Ngay", "Date",
            "Shop", "Shop",
            "Thu_ngan", "Cashier",
            "Khach", "Customer",
            "Tong_tien", "Total",
            "San_pham", "Product",
            "SL_mua", "Qty purchased",
            "SL_da_tra", "Qty returned",
            "SL_con_lai", "Qty remaining",
            "Don_gia", "Unit price",
            "Giam_gia", "Discount",
            "Thanh_tien", "Line total",
            "SL_tra", "Qty returned",
            "Tien_hoan", "Refund",
            "Lan_tra", "Return #",
            "Nguoi_duyet", "Approver",
            "Ma_TT", "Payment ID",
            "So_tien", "Amount",
            "Phuong_thuc", "Payment method",
            "Chi_tiet", "Details",
            "Tien_khach_dua", "Tendered",
            "VAT_pct", "VAT %",
            "Tax_Rate", "Tax rate",
            "Valid_From_Date", "Valid from",
            "Valid_To_Date", "Valid to",
            "Product_Name", "Product name",
            "Unit_Price", "Unit price",
            "Quantity", "Quantity",
            "Discount", "Discount",
            "Barcode", "Barcode",
            "Return_Date", "Return date",
            "Return_Quantity", "Return quantity",
            "Counter_ID", "Counter ID",
            "Cashier_ID", "Cashier ID",
            "Customer_ID", "Customer ID",
            "Supervisor_ID", "Supervisor ID",
            "Invoice_ID", "Invoice ID",
            "Payment_ID", "Payment ID",
            "Return_ID", "Return ID",
            "Line_Number", "Line number",
            "Promotion_ID", "Promotion ID",
            "Promo_ID", "Promotion ID",
            "Amount", "Amount",
            "Payment_Method", "Payment method",
            "Method", "Method",
            "Unit", "Unit",
            "Price", "Price",
            "Name", "Name",
            "Phone", "Phone",
            "Full_Name", "Full name",
            "Username", "Username",
            "Role", "Role",
            "Account", "Account",
            "Tong: ", "Total: ",
            "Da thu: ", "Paid: ",
            "Con thieu: ", "Remaining: ",
            "Tien thoi: ", "Change: ",
            "Gia ngay ", "Price on ",
            " = ", " = ",
            " (khong co KM)", " (no promotion)",
            "Gia ngay ", "Price on ",
            "Ghi ", "Record ",
            " luot tra hang cho hoa don ", " return(s) for invoice ",
            "Tong tien phai tra lai: ", "Total refund: ",
            "Nguoi duyet: ", "Approved by: ",
            "Hoa don: ", "Invoice: ",
            "   |  Ngay: ", "   |  Date: ",
            "   |  Trang thai: ", "   |  Status: ",
            "Da tra het ", "Already returned ",
            "da mua,\nkhong con gi de tra.", "purchased), so there is nothing left to return.",
            "Ngay lap phai dung dinh dang YYYY-MM-DD!", "Date must use the YYYY-MM-DD format.",
            "Gio lap phai dung dinh dang HH:MM:SS!", "Time must use the HH:MM:SS format.",
            "So tien vuot qua so con thieu", "Amount exceeds the outstanding balance",
            "So tien khong hop le", "Invalid amount",
            "So luong khong hop le", "Invalid quantity",
            "Chua nhap so tien", "No amount entered",
            "Chua nhap so luong.", "No quantity entered.",
            "So luong phai lon hon 0.", "Quantity must be greater than zero.",
            "Khong co gia tai ngay ban", "No price is available on the sale date",
            "Khong co duong tra lai cho ", "There is no change/refund path for ",
            "Hay go dung ", "Enter the exact amount ",
            "Thanh toan QR", "QR payment",
            "Quay", "Counter",
            "Ngay ban", "Sale date",
            "Khach", "Customer",
            "Phuong thuc thanh toan", "Payment method",
            "Nhom", "Category",
            "Dien thoai", "Phone",
            "Chon mot ", "Select a ",
            "Vui long chon mot ", "Please select a ",
            "de xoa!", "to delete.",
            "de sua!", "to edit.",
            "de tra hang!", "to process a return.",
            "khong doc duoc", "could not be read",
            "khong hop le", "is invalid",
            "Chinh sua", "Edit",
            "Da luu", "Saved",
            " thanh cong", " successfully",
            " trong Database", " in the database",
            "Ma khach hang:", "Customer ID:",
            "(NOT NULL)", "(NOT NULL)",
            "Customer_ID", "Customer_ID",
            "Invoice_Line", "Invoice_Line",
            "Price_History", "Price_History",
            "Cashier", "Cashier",
            "Manager", "Manager",
            "VND", "VND",
            "USD", "USD"
        );

        pairs(
            "Doanh thu tung NGAY cua tung CUA HANG trong 7 ngay gan nhat. Hoa don khong noi thang toi Shop nen phai di vong: Invoice -> Counter -> Shop. GROUP BY hai cot (shop, ngay) nen moi dong ket qua la mot o trong bang cheo.",
                "Revenue for each DAY and STORE over the last 7 days. An invoice is not linked directly to a Shop, so join through Invoice -> Counter -> Shop. Grouping by (store, date) makes each result row one cell in a cross-tab.",
            "SELF JOIN: noi bang Price_History voi CHINH NO de dat gia moi canh gia cu. Subquery MAX(...) tim moc gia LIEN TRUOC cua cung san pham. Dieu kien: gia moi > gia cu * 1.10 va nam trong nam nay.",
                "SELF JOIN: join Price_History to itself to compare each new price with the previous one. The MAX(...) subquery finds the immediately preceding price for the same product. Condition: the new price is over 10% higher and dated this year.",
            "Bay pho bien: AVG thang tren Invoice_Line se ra trung binh moi DONG chu khong phai moi HOA DON. Phai tinh tong tien tung hoa don truoc (subquery), roi moi AVG theo thu ngan.",
                "A common mistake: averaging Invoice_Line gives an average per ITEM, not per INVOICE. Calculate each invoice total first in a subquery, then average by cashier.",
            "Hai subquery: mot tinh tong hoa don, mot tinh tien tra lai. Tien tra lai tinh theo TY LE (SL tra / SL mua) nhan thanh tien, de phan giam gia cung duoc hoan dung ty le.",
                "Two subqueries calculate the invoice total and refund amount. Refunds are proportional (qty returned / qty purchased × line total), so discounts are refunded at the same rate.",
            "Cot 'Gia_ghi_tren_HD' lay tu Invoice_Line (da dong bang luc ban). Cot 'Gia_hom_nay' goi fn_get_product_price(barcode, CURDATE()). Bao cao GOM theo (san pham, don gia da dong bang), nen moi dong tra loi: 'co bao nhieu dong hang dang ghi gia nay, ban tu ngay nao den ngay nao, va gia hom nay la bao nhieu'. Dong co 'Ban_tu_ngay' xa nhat chinh la hai hoa don tu nam truoc.",
                "'Gia_ghi_tren_HD' comes from Invoice_Line and is frozen at the time of sale. 'Gia_hom_nay' calls fn_get_product_price(barcode, CURDATE()). The report groups by product and frozen price; each row shows how many items were sold at that price, the date range, and today's price. The oldest 'Ban_tu_ngay' rows are the two invoices from last year."
        );

        pairs(
            "Thoi gian", "Date and time",
            "Thu ngan", "Cashier",
            "Khach hang", "Customer",
            "Ten hang", "Item",
            "SL", "Qty",
            "DVT", "Unit",
            "Ma HD", "Invoice ID",
            "Tien khach dua", "Customer tendered",
            "Da ghi nhan", "Received",
            "Tien thoi lai", "Change due",
            "Khach dua:", "Customer tendered:",
            "So tien:", "Amount:",
            "Tai khoan:", "Account:",
            "Hoa don", "Invoice",
            "Thu ngan:", "Cashier:",
            "Ma khach hang:", "Customer ID:",
            "Ten khach hang", "Customer name",
            "Thong tin", "Details",
            "Tat ca", "All",
            "Cho", "For",
            "Ghi chu", "Notes",
            "(khach vang lai)", "(walk-in customer)",
            "HOA DON BAN LE -- ", "RETAIL RECEIPT -- ",
            "Dang lap (OPEN)", "Open (OPEN)",
            "Da thanh toan (PAID)", "Paid (PAID)",
            "Da huy (VOIDED)", "Voided (VOIDED)",
            " -- con thieu ", " -- remaining "
        );

        pairs(
            "Giam gia", "Discount",
            "Phuong thuc", "Payment method",
            "Tien thoi", "Change",
            "tat ca", "all",
            "30 ngay gan nhat", "last 30 days",
            "90 ngay gan nhat", "last 90 days",
            "<html>Go SO DIEN THOAI cua khach (tu 3 chu so tro len) roi chon tu danh sach goi y.<br>Chi khach <b>duoc chon tu danh sach</b> moi duoc gan vao hoa don -- go du so ma khong chon van la khach vang lai.</html>",
                "<html>Enter the customer's PHONE NUMBER (at least 3 digits), then choose a suggestion.<br>Only a customer <b>chosen from the list</b> is linked to the invoice; typing a complete number without choosing a suggestion leaves it as a walk-in customer.</html>",
            "<html><i style='color:#c0392b;'>* Cac truong danh dau (*) co rang buoc NOT NULL. Neu xoa trang se bi he thong canh bao!</i></html>",
                "<html><i style='color:#c0392b;'>* Fields marked (*) are NOT NULL. The app will warn you if you clear one.</i></html>",
            "<html><i style='color:#c0392b;'>* Toan bo cac cot trong Invoice_Line deu co rang buoc NOT NULL.<br>Nut 'Thu xoa (Set NULL)' se minh hoa co che canh bao cua Database.</i></html>",
                "<html><i style='color:#c0392b;'>* Every Invoice_Line column is NOT NULL.<br>The 'Try clearing (Set NULL)' button demonstrates the database warning.</i></html>",
            "<html><i>Meo: doi 'Ngay ban' ve nam ngoai roi them san pham -> don gia se lay theo GIA CU trong Price_History.</i></html>",
                "<html><i>Tip: set the sale date to a past year, then add a product to use its historical Price_History price.</i></html>",
            "<html>Dau <b>,</b> hoac <b>.</b> la dau THAP PHAN (toi da 3 chu so) -- KHONG phai dau tach nghin.<br>Dung: <code>2</code> | <code>1,5</code> | <code>1.5</code> | <code>0,25</code><br><i>Khac hẳn o tien ben duoi, noi dau <b>.</b> la dau tach nghin.</i><br>Enter = Them vao hoa don.</html>",
                "<html>Use <b>,</b> or <b>.</b> as the decimal separator (up to 3 digits), not as a thousands separator.<br>Examples: <code>2</code> | <code>1.5</code> | <code>1,5</code> | <code>0.25</code><br><i>In the amount field below, <b>.</b> separates thousands.</i><br>Press Enter to add to the invoice.</html>",
            "<html>Dau <b>.</b> la dau TACH NGHIN (phai du 3 chu so), dau <b>,</b> la dau thap phan.<br>Dung: <code>500.000</code> | <code>1.234,50</code> | <code>500000</code><br>Sai : <code>500.00</code> | <code>1.2.3</code> | <code>1,234</code><br><i>Khac hẳn o so luong o tren, noi dau <b>.</b> la dau thap phan.</i><br>Enter = Them thanh toan.</html>",
                "<html>Use <b>.</b> as the thousands separator (followed by 3 digits), and <b>,</b> as the decimal separator.<br>Examples: <code>500.000</code> | <code>1.234,50</code> | <code>500000</code><br>Invalid: <code>500.00</code> | <code>1.2.3</code> | <code>1,234</code><br><i>In the quantity field above, <b>.</b> is a decimal separator.</i><br>Press Enter to add payment.</html>",
            "<html>Dau <b>,</b> hoac <b>.</b> la dau THAP PHAN, toi da 3 chu so (cot DECIMAL(10,3)).<br>Dung: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>",
                "<html>Use <b>,</b> or <b>.</b> as the decimal separator, up to 3 digits (DECIMAL(10,3)).<br>Examples: <code>2</code> | <code>1.5</code> | <code>1,5</code></html>",
            "<html>Dau <b>.</b> la dau TACH NGHIN: <code>158.000</code> = 158 nghin.<br>Dau <b>,</b> la dau thap phan (toi da 2 chu so).</html>",
                "<html><b>.</b> separates thousands: <code>158.000</code> = 158 thousand.<br><b>,</b> is the decimal separator (up to 2 digits).</html>",
            "Cot VAT % la thue suat cua san pham; don gia trong Price_History DA GOM VAT.",
                "VAT % is the product tax rate; the Price_History unit price already includes VAT."
        );

        field("Invoice_ID", "Mã hóa đơn", "Invoice ID");
        field("Date", "Ngày", "Date");
        field("Time", "Giờ", "Time");
        field("Status", "Trạng thái", "Status");
        field("Counter_ID", "Mã quầy", "Counter ID");
        field("Shop_ID", "Mã cửa hàng", "Shop ID");
        field("Cashier_ID", "Mã thu ngân", "Cashier ID");
        field("Customer_ID", "Mã khách hàng", "Customer ID");
        field("Product_Name", "Tên sản phẩm", "Product name");
        field("Barcode", "Mã vạch", "Barcode");
        field("Quantity", "Số lượng", "Quantity");
        field("Unit", "Đơn vị", "Unit");
        field("Unit_Price", "Đơn giá", "Unit price");
        field("Discount", "Giảm giá", "Discount");
        field("Tax_Rate", "Thuế suất", "Tax rate");
        field("Payment_ID", "Mã thanh toán", "Payment ID");
        field("Payment_Method", "Phương thức thanh toán", "Payment method");
        field("Amount", "Số tiền", "Amount");
        field("Return_ID", "Mã trả hàng", "Return ID");
        field("Return_Date", "Ngày trả hàng", "Return date");
        field("Return_Quantity", "Số lượng trả", "Return quantity");
        field("Promotion_ID", "Mã khuyến mãi", "Promotion ID");
        field("Promo_ID", "Mã khuyến mãi", "Promotion ID");
        field("Full_Name", "Họ tên", "Full name");
        field("Username", "Tên đăng nhập", "Username");
        field("Role", "Vai trò", "Role");
        field("Ma_HD", "Mã hóa đơn", "Invoice ID");
        field("Ngay", "Ngày", "Date");
        field("Gio", "Giờ", "Time");
        field("Trang_thai", "Trạng thái", "Status");
        field("Thu_ngan", "Thu ngân", "Cashier");
        field("Khach", "Khách hàng", "Customer");
        field("Tong_tien", "Tổng tiền", "Total");
        field("Ma_TT", "Mã thanh toán", "Payment ID");
        field("So_tien", "Số tiền", "Amount");
        field("Phuong_thuc", "Phương thức", "Payment method");
        field("Chi_tiet", "Chi tiết", "Details");
        field("Tien_khach_dua", "Khách đưa", "Tendered");
        field("San_pham", "Sản phẩm", "Product");
        field("SL_mua", "SL mua", "Qty purchased");
        field("SL_da_tra", "SL đã trả", "Qty returned");
        field("SL_con_lai", "SL còn lại", "Qty remaining");
        field("Don_gia", "Đơn giá", "Unit price");
        field("Giam_gia", "Giảm giá", "Discount");
        field("Thanh_tien", "Thành tiền", "Line total");
        field("SL_tra", "SL trả", "Qty returned");
        field("Tien_hoan", "Tiền hoàn", "Refund");
        field("Lan_tra", "Lần trả", "Return #");
        field("Nguoi_duyet", "Người duyệt", "Approver");
        field("VAT_pct", "VAT %", "VAT %");

        // Ten cot cua cac bao cao / hoa don dieu chinh (them o feat/i18n)
        field("Ap_dung_tu", "Áp dụng từ", "Effective from");
        field("Ban_den_ngay", "Bán đến ngày", "Sold until");
        field("Ban_tu_ngay", "Bán từ ngày", "Sold from");
        field("Chenh_lech", "Chênh lệch", "Change vs. previous day");
        field("Cua_hang", "Cửa hàng", "Shop");
        field("Shop", "Cửa hàng", "Shop");
        field("Dia_chi", "Địa chỉ", "Address");
        field("Dien_thoai", "Điện thoại", "Phone");
        field("Doanh_thu", "Doanh thu", "Revenue");
        field("Gia_cu", "Giá cũ", "Old price");
        field("Gia_moi", "Giá mới", "New price");
        field("Gia_ghi_tren_HD", "Giá ghi trên hóa đơn", "Price on invoice");
        field("Gia_hom_nay", "Giá hôm nay", "Today's price");
        field("Gio_hang_TB", "Giỏ hàng TB", "Average basket");
        field("KM_phan_tram", "Khuyến mãi %", "Promotion %");
        field("Khach_hang", "Khách hàng", "Customer");
        field("Ma_KH", "Mã khách hàng", "Customer ID");
        field("Ma_TN", "Mã thu ngân", "Cashier ID");
        field("Ma_vach", "Mã vạch", "Barcode");
        field("Mo_ta", "Mô tả", "Description");
        field("Ngay_tra", "Ngày trả", "Return date");
        field("Phan_tram_tra", "% trả lại", "% returned");
        field("Quay", "Quầy", "Counter");
        field("SL", "SL", "Qty");
        field("DVT", "ĐVT", "Unit");
        field("So_dong_hang", "Số dòng hàng", "Item lines");
        field("So_hoa_don", "Số hóa đơn", "Invoices");
        field("Tang_phan_tram", "Tăng %", "Increase %");
        field("Ten", "Tên", "Name");
        field("Ten_thu_ngan", "Tên thu ngân", "Cashier name");
        field("Thanh_tien_SL1", "Thành tiền (SL 1)", "Total (qty 1)");
        field("Thanh_tien_goc", "Thành tiền gốc", "Original total");
        field("Thanh_tien_moi", "Thành tiền mới", "New total");
        field("Tien_tra", "Tiền trả", "Refund");
        field("Tien_tra_lai", "Tiền trả lại", "Refund");
        field("Tien_thoi", "Tiền thối", "Change");
        field("Tong", "Tổng", "Total");
        field("Tong_HD", "Tổng hóa đơn", "Invoice total");
        field("Tong_hoa_don", "Tổng hóa đơn", "Invoice total");
        field("Tong_doanh_thu", "Tổng doanh thu", "Total revenue");
        field("Tong_giam", "Tổng giảm", "Total discount");
        field("Tong_phai_tra", "Tổng phải trả", "Total due");
        field("Tong_sau_dieu_chinh", "Tổng sau điều chỉnh", "Total after adjustment");
        field("Tong_truoc_giam", "Tổng trước giảm", "Total before discount");

        marks(
            "Doi vai tro", "Đổi vai trò",
            "Dang xuat", "Đăng xuất",
            "1. Lap hoa don", "1. Lập hóa đơn",
            "2. Tra cứu dữ liệu", "2. Tra cứu dữ liệu",
            "3. Xem hoa don (Database)", "3. Xem hóa đơn (Database)",
            "4. Bao cao", "4. Báo cáo",
            "(DANG NHAP TU DONG)", "(ĐĂNG NHẬP TỰ ĐỘNG)",
            "Doi sang tai khoan khac ma KHONG mat du lieu dang nhap o tab 1", "Đổi sang tài khoản khác mà KHÔNG mất dữ liệu đang nhập ở tab 1",
            "Nguoi dung: ", "Người dùng: ",
            "  Nguoi dung: ", "  Người dùng: ",
            "Vai tro: ", "Vai trò: ",
            "Nguoi dung: (Chua dang nhap)", "Người dùng: (Chưa đăng nhập)",
            "  Nguoi dung: (Chua dang nhap)", "  Người dùng: (Chưa đăng nhập)",
            "(Chua dang nhap)", "(Chưa đăng nhập)",
            "Tai khoan ", "Tài khoản ",
            "Doi vai tro that bai", "Đổi vai trò thất bại",
            "Ban co chac chan muon dang xuat khoi he thong?", "Bạn có chắc chắn muốn đăng xuất khỏi hệ thống?",
            "Tab \"Lap hoa don\" dang co ", "Tab \"Lập hóa đơn\" đang có ",
            " chua luu.\nDang xuat se mat toan bo. Van dang xuat?\n\nGoi y: muon xem giao dien cua vai tro khac ma khong mat gio hang\nthi dung nut \"Doi vai tro\" ben canh.",
                " chưa lưu.\nĐăng xuất sẽ mất toàn bộ. Vẫn đăng xuất?\n\nGợi ý: muốn xem giao diện của vai trò khác mà không mất giỏ hàng\nthì dùng nút \"Đổi vai trò\" bên cạnh.",
            "Xac nhan dang xuat", "Xác nhận đăng xuất",

            "Tai lai", "Tải lại",
            "Them vao hoa don", "Thêm vào hóa đơn",
            "Sua so luong", "Sửa số lượng",
            "Xoa dong dang chon", "Xóa dòng đang chọn",
            "Them thanh toan", "Thêm thanh toán",
            "Khach tra du bang tien mat", "Khách trả đủ bằng tiền mặt",
            "Tao ma QR", "Tạo mã QR",
            "Xoa het", "Xóa hết",
            "XUAT HOA DON  (INSERT + COMMIT)", "XUẤT HÓA ĐƠN  (INSERT + COMMIT)",
            "Dang luu...", "Đang lưu...",
            "Quay:", "Quầy:",
            "Thu ngan:", "Thu ngân:",
            "Ngay ban:", "Ngày bán:",
            "Khach (SDT):", "Khách (SĐT):",
            "San pham:", "Sản phẩm:",
            "So luong:", "Số lượng:",
            "Phuong thuc:", "Phương thức:",
            "1. Thong tin hoa don", "1. Thông tin hóa đơn",
            "2. Them san pham", "2. Thêm sản phẩm",
            "3. Cac dong hang (se thanh bang Invoice_Line)", "3. Các dòng hàng (sẽ thành bảng Invoice_Line)",
            "4. Thanh toan (mot hoa don co the co NHIEU dong thanh toan)", "4. Thanh toán (một hóa đơn có thể có NHIỀU dòng thanh toán)",
            "Meo: doi 'Ngay ban' ve nam ngoai roi them san pham -> don gia se lay theo GIA CU trong Price_History.",
                "Mẹo: đổi 'Ngày bán' về năm ngoái rồi thêm sản phẩm -> đơn giá sẽ lấy theo GIÁ CŨ trong Price_History.",
            "Tong: ", "Tổng: ",
            "Da thu: ", "Đã thu: ",
            "Con thieu: ", "Còn thiếu: ",
            "Tien thoi: ", "Tiền thối: ",
            "Gia ngay ", "Giá ngày ",
            " (khong co KM)", " (không có khuyến mãi)",

            "Tìm kiếm", "Tìm kiếm",
            "Tải lại / Xem tất cả", "Tải lại / Xem tất cả",
            "Chọn bảng: ", "Chọn bảng: ",
            "Từ khóa: ", "Từ khóa: ",
            "Tổng số hàng: 0 dòng", "Tổng số hàng: 0 dòng",
            "Tổng số hàng: ", "Tổng số hàng: ",
            " dòng", " dòng",
            "Đang hiện ", "Đang hiển thị ",
            " dòng đầu (bảng còn nhiều hơn — dùng bộ lọc để thu hẹp)", " dòng đầu (bảng còn nhiều hơn — dùng bộ lọc để thu hẹp)",
            "Chưa cấu hình bộ lọc cho bảng ", "Chưa cấu hình bộ lọc cho bảng ",
            "Hình thức thanh toán: ", "Hình thức thanh toán: ",
            "Số tiền: ", "Số tiền: ",
            "Từ: ", "Từ: ",
            "Đến: ", "Đến: ",
            "Trạng thái hóa đơn: ", "Trạng thái hóa đơn: ",
            "Thời gian: ", "Thời gian: ",
            "Loại khách: ", "Loại khách: ",
            "Đơn vị tính: ", "Đơn vị tính: ",
            "Khoáng giá sản phẩm: ", "Khoảng giá sản phẩm: ",
            "Thuế suất: ", "Thuế suất: ",
            "Thời gian trả hàng: ", "Thời gian trả hàng: ",
            "Số lượng hoàn trả: ", "Số lượng hoàn trả: ",
            "Tat ca", "Tất cả",
            "Tất cả", "Tất cả",
            "Tiền mặt", "Tiền mặt",
            "Thẻ", "Thẻ",
            "Ví điện tử", "Ví điện tử",
            "Khoảng", "Khoảng",
            "Đang lập", "Đang lập",
            "Đã hoàn tất", "Đã hoàn tất",
            "Đã hủy", "Đã hủy",
            "Hôm nay", "Hôm nay",
            "7 ngày qua", "7 ngày qua",
            "Tháng này", "Tháng này",
            "Tuần này", "Tuần này",
            "Khách vãng lai", "Khách vãng lai",
            "Thành viên", "Thành viên",

            "Khoang ngay:", "Khoảng ngày:",
            "Tai lai danh sach", "Tải lại danh sách",
            "Danh sach hoa don (bam de xem chi tiet)", "Danh sách hóa đơn (bấm để xem chi tiết)",
            "Thong tin chung + tong tien", "Thông tin chung + tổng tiền",
            "Cac dong hang (Invoice_Line)", "Các dòng hàng (Invoice_Line)",
            "Thanh toan (Payment + bang con)", "Thanh toán (Payment + bảng con)",
            "Tra hang (Return)", "Trả hàng (Return)",
            "Sua hoa don", "Sửa hóa đơn",
            "Huy hoa don", "Hủy hóa đơn",
            "Xoa hoa don", "Xóa hóa đơn",
            "Sua dong hang", "Sửa dòng hàng",
            "Xoa dong hang", "Xóa dòng hàng",
            "Thu xoa gia tri (Set NULL)", "Thử xóa giá trị (Set NULL)",
            "Tra hang", "Trả hàng",
            "Huy luot tra", "Hủy lượt trả",
            "Dang hien ", "Đang hiển thị ",
            " hoa don (", " hóa đơn (",
            "Vui long chon mot hoa don de chinh sua!", "Vui lòng chọn một hóa đơn để chỉnh sửa!",
            "Chua chon hoa don", "Chưa chọn hóa đơn",
            "Khong tim thay hoa don ", "Không tìm thấy hóa đơn ",
            "Loi", "Lỗi",
            "Vui long chon mot hoa don de huy!", "Vui lòng chọn một hóa đơn để hủy!",
            "Xac nhan huy hoa don", "Xác nhận hủy hóa đơn",
            "Huy hoa don ", "Hủy hóa đơn ",
            "Khong huy duoc hoa don", "Không hủy được hóa đơn",
            "Da huy hoa don ", "Đã hủy hóa đơn ",
            "Da huy hoa don", "Đã hủy hóa đơn",
            "Vui long chon mot hoa don de xoa!", "Vui lòng chọn một hóa đơn để xóa!",
            "Xac nhan xoa hoa don khoi Database", "Xác nhận xóa hóa đơn khỏi Database",
            "Ban co chac chan muon XOA VINH VIEN hoa don ", "Bạn có chắc chắn muốn XÓA VĨNH VIỄN hóa đơn ",
            "Da xoa thanh cong hoa don ", "Đã xóa thành công hóa đơn ",
            "Thanh cong", "Thành công",
            "Vui long chon mot dong hang trong bang 'Cac dong hang' de chinh sua!", "Vui lòng chọn một dòng hàng trong bảng 'Các dòng hàng' để chỉnh sửa!",
            "Chua chon dong hang", "Chưa chọn dòng hàng",
            "Khong tim thay dong hang so ", "Không tìm thấy dòng hàng số ",
            "Vui long chon mot dong hang de xoa!", "Vui lòng chọn một dòng hàng để xóa!",
            "Ban co chac chan muon xoa dong hang so ", "Bạn có chắc chắn muốn xóa dòng hàng số ",
            "Xac nhan xoa dong hang", "Xác nhận xóa dòng hàng",
            "Da xoa thanh cong dong hang ", "Đã xóa thành công dòng hàng ",
            "CANH BAO DATABASE: ", "CẢNH BÁO DATABASE: ",
            "Canh bao rang buoc NOT NULL", "Cảnh báo ràng buộc NOT NULL",
            "Thong bao", "Thông báo",
            "Tong tien phai tra lai: ", "Tổng tiền phải trả lại: ",
            "Nguoi duyet: ", "Người duyệt: ",

            "Dang nhap", "Đăng nhập",
            "Thoat", "Thoát",
            "Dang nhap he thong -- Retail Invoicing", "Đăng nhập hệ thống -- Retail Invoicing",
            "Ten dang nhap:", "Tên đăng nhập:",
            "Mat khau:", "Mật khẩu:",
            "Dang nhap nhanh (tai khoan mau cua ban demo)", "Đăng nhập nhanh (tài khoản mẫu của bản demo)",
            "Dien san va dang nhap bang tai khoan '", "Điền sẵn và đăng nhập bằng tài khoản '",
            "Bam mot nut de dien san va dang nhap ngay.", "Bấm một nút để điền sẵn và đăng nhập ngay.",
            "Phim tat: Alt+1 ... Alt+", "Phím tắt: Alt+1 ... Alt+",
            ".  Enter = Dang nhap.", ".  Enter = Đăng nhập.",
            "Vui long nhap day du Ten dang nhap va Mat khau.", "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.",
            "Dang kiem tra tai khoan...", "Đang kiểm tra tài khoản...",
            "Sai ten dang nhap hoac mat khau.", "Sai tên đăng nhập hoặc mật khẩu.",
            "Đăng nhập nhanh", "Đăng nhập nhanh",

            "Chinh sua Hoa don: ", "Chỉnh sửa hóa đơn: ",
            "Ma hoa don (PK):", "Mã hóa đơn (PK):",
            "Ngay lap (YYYY-MM-DD) *:", "Ngày lập (YYYY-MM-DD) *:",
            "Gio lap (HH:MM:SS) *:", "Giờ lập (HH:MM:SS) *:",
            "Trang thai:", "Trạng thái:",
            "(chi doc)", "(chỉ đọc)",
            "Ma quay (Counter_ID) *:", "Mã quầy (Counter_ID) *:",
            "Ma thu ngan (Cashier_ID) *:", "Mã thu ngân (Cashier_ID) *:",
            "Ma khach hang:", "Mã khách hàng:",
            "Xoa ma khach (Set NULL)", "Xóa mã khách (Set NULL)",
            "Customer_ID la truong cho phep NULL (khach vang lai). Gia tri nay se duoc cap nhat thanh NULL trong database.",
                "Customer_ID là trường cho phép NULL (khách vãng lai). Giá trị này sẽ được cập nhật thành NULL trong database.",
            "* Cac truong danh dau (*) co rang buoc NOT NULL. Neu xoa trang se bi he thong canh bao!", "* Các trường đánh dấu (*) có ràng buộc NOT NULL. Nếu xóa trắng sẽ bị hệ thống cảnh báo!",
            "Luu vao Database", "Lưu vào Database",
            "Huy bo", "Hủy bỏ",
            "Ngay lap (Date)", "Ngày lập (Date)",
            "Gio lap (Time)", "Giờ lập (Time)",
            "Ma quay (Counter_ID)", "Mã quầy (Counter_ID)",
            "Ma thu ngan (Cashier_ID)", "Mã thu ngân (Cashier_ID)",
            "Ngay lap phai dung dinh dang YYYY-MM-DD!", "Ngày lập phải đúng định dạng YYYY-MM-DD!",
            "Loi dinh dang", "Lỗi định dạng",
            "Gio lap phai dung dinh dang HH:MM:SS!", "Giờ lập phải đúng định dạng HH:MM:SS!",
            "Cap nhat hoa don ", "Cập nhật hóa đơn ",
            " thanh cong trong Database!", " thành công trong Database!",
            "Khong cap nhat duoc hoa don", "Không cập nhật được hóa đơn",
            "CANH BAO DATABASE: Truong '", "CẢNH BÁO DATABASE: Trường '",
            "' co rang buoc NOT NULL!\nBan khong duoc phep xoa gia tri nay ve NULL hoac de trong.", "' có ràng buộc NOT NULL!\nBạn không được phép xóa giá trị này về NULL hoặc để trống.",

            "Chinh sua Dong hang: ", "Chỉnh sửa dòng hàng: ",
            " (HD: ", " (HĐ: ",
            "Ma vach (Barcode) *:", "Mã vạch (Barcode) *:",
            "So luong (Quantity) *:", "Số lượng (Quantity) *:",
            "Don gia (Unit_Price) *:", "Đơn giá (Unit_Price) *:",
            "Giam gia (Discount) *:", "Giảm giá (Discount) *:",
            "Thu xoa (Set NULL)", "Thử xóa (Set NULL)",
            "Toan bo cac cot trong Invoice_Line deu co rang buoc NOT NULL. Nut 'Thu xoa (Set NULL)' se minh hoa co che canh bao cua Database.",
                "Toàn bộ các cột trong Invoice_Line đều có ràng buộc NOT NULL. Nút 'Thử xóa (Set NULL)' sẽ minh họa cơ chế cảnh báo của Database.",
            "Cap nhat dong hang ", "Cập nhật dòng hàng ",
            "CANH BAO DATABASE: Cot '", "CẢNH BÁO DATABASE: Cột '",
            "' co rang buoc NOT NULL trong Database!\nBan khong duoc phep xoa gia tri nay ve NULL hoac de trong.", "' có ràng buộc NOT NULL trong Database!\nBạn không được phép xóa giá trị này về NULL hoặc để trống.",

            "Chon bao cao", "Chọn báo cáo",
            "2. Doanh thu 7 ngay / cua hang", "2. Doanh thu 7 ngày / cửa hàng",
            "3. San pham tang gia > 10%", "3. Sản phẩm tăng giá > 10%",
            "4. Thu ngan gio hang TB cao nhat", "4. Thu ngân có giá trị giỏ hàng trung bình cao nhất",
            "5. Hoa don tra hang > 50%", "5. Hóa đơn trả hàng > 50%",
            "Bonus: hoa don cu giu gia cu", "Thêm: hóa đơn cũ giữ giá cũ",
            "Y nghia cau truy van", "Ý nghĩa câu truy vấn",
            "Cau SQL dang chay", "Câu SQL đang chạy",
            "Ket qua", "Kết quả",
            " -- dang chay...", " -- đang chạy...",
            "Cau lenh chay xong nhung khong co dong nao thoa dieu kien.", "Câu lệnh chạy xong nhưng không có dòng nào thỏa điều kiện.",

            "Thanh toan bang ma QR (VietQR)", "Thanh toán bằng mã QR (VietQR)",
            "So tien trong ma QR: ", "Số tiền trong mã QR: ",
            "Chuyen toi: ", "Chuyển tới: ",
            "Tai khoan: ", "Tài khoản: ",
            "Noi dung CK (Transaction_Ref): ", "Nội dung CK (Transaction_Ref): ",
            "Ban demo: viec doi soat la THU CONG -- bam \"Xac nhan\" khi da thay tien vao.", "Bản demo: việc đối soát là THỦ CÔNG -- bấm \"Xác nhận\" khi đã thấy tiền vào.",
            "Xac nhan da nhan tien", "Xác nhận đã nhận tiền",
            "In hoa don", "In hóa đơn",
            "Dong", "Đóng",
            "HOA DON BAN LE", "HÓA ĐƠN BÁN LẺ",
            "Ma HD", "Mã HĐ",
            "Thoi gian", "Thời gian",
            "Khach hang", "Khách hàng",
            "Ten hang", "Tên hàng",
            "SL", "SL",
            "DVT", "ĐVT",
            "Don gia", "Đơn giá",
            "Giam gia / Khuyen mai", "Giảm giá / Khuyến mãi",
            "Thanh tien", "Thành tiền",
            "Tong tien hang", "Tổng tiền hàng",
            "TONG CONG", "TỔNG CỘNG",
            "Khach dua", "Khách đưa",
            "Chi tiet", "Chi tiết",
            "Tien khach dua", "Tiền khách đưa",
            "Da ghi nhan", "Đã ghi nhận",
            "Tien thoi lai", "Tiền thối lại",
            "Cam on Quy khach &amp; Hen gap lai!", "Cảm ơn Quý khách &amp; Hẹn gặp lại!",
            "Tat ca", "Tất cả",
            "7 ngay gan nhat", "7 ngày gần nhất",
            "30 ngay gan nhat", "30 ngày gần nhất",
            "90 ngay gan nhat", "90 ngày gần nhất"
        );

        marks(
            "Chuyen giao dien sang ", "Chuyển giao diện sang ",
            "App da dang nhap san, khong qua man hinh dang nhap. Dat quick.login.auto= (de trong) trong config.properties de tat.",
                "Ứng dụng đã đăng nhập sẵn, không qua màn hình đăng nhập. Đặt quick.login.auto= (để trống) trong config.properties để tắt.",
            "Dang nhap nhanh (tai khoan mau cua ban demo)", "Đăng nhập nhanh (tài khoản mẫu của bản demo)",
            "Dien san va dang nhap bang tai khoan '", "Điền sẵn và đăng nhập bằng tài khoản '",
            "Trang thai doi tu dong khi thu du tien, hoac qua nut \"Huy hoa don\" o tab Xem hoa don", "Trạng thái đổi tự động khi thu đủ tiền hoặc qua nút \"Hủy hóa đơn\" ở tab Xem hóa đơn",
            "* Cac truong danh dau (*) co rang buoc NOT NULL. Neu xoa trang se bi he thong canh bao!", "* Các trường đánh dấu (*) có ràng buộc NOT NULL. Nếu xóa trắng sẽ bị hệ thống cảnh báo!",
            "<html><i style='color:#c0392b;'>* Cac truong danh dau (*) co rang buoc NOT NULL. Neu xoa trang se bi he thong canh bao!</i></html>",
                "<html><i style='color:#c0392b;'>* Các trường đánh dấu (*) có ràng buộc NOT NULL. Nếu xóa trắng sẽ bị hệ thống cảnh báo!</i></html>",
            "<html><i style='color:#c0392b;'>* Toan bo cac cot trong Invoice_Line deu co rang buoc NOT NULL.<br>Nut 'Thu xoa (Set NULL)' se minh hoa co che canh bao cua Database.</i></html>",
                "<html><i style='color:#c0392b;'>* Toàn bộ các cột trong Invoice_Line đều có ràng buộc NOT NULL.<br>Nút 'Thử xóa (Set NULL)' sẽ minh họa cơ chế cảnh báo của Database.</i></html>",
            "<html><i>Meo: doi 'Ngay ban' ve nam ngoai roi them san pham -> don gia se lay theo GIA CU trong Price_History.</i></html>",
                "<html><i>Mẹo: đổi 'Ngày bán' về năm ngoái rồi thêm sản phẩm -> đơn giá sẽ lấy theo GIÁ CŨ trong Price_History.</i></html>",
            "<html>Dau <b>,</b> hoac <b>.</b> la dau THAP PHAN (toi da 3 chu so) -- KHONG phai dau tach nghin.<br>Dung: <code>2</code> | <code>1,5</code> | <code>1.5</code> | <code>0,25</code><br><i>Khac hẳn o tien ben duoi, noi dau <b>.</b> la dau tach nghin.</i><br>Enter = Them vao hoa don.</html>",
                "<html>Dấu <b>,</b> hoặc <b>.</b> là dấu THẬP PHÂN (tối đa 3 chữ số) -- KHÔNG phải dấu tách nghìn.<br>Đúng: <code>2</code> | <code>1,5</code> | <code>1.5</code> | <code>0,25</code><br><i>Khác hẳn ô tiền bên dưới, nơi dấu <b>.</b> là dấu tách nghìn.</i><br>Enter = Thêm vào hóa đơn.</html>",
            "<html>Dau <b>.</b> la dau TACH NGHIN (phai du 3 chu so), dau <b>,</b> la dau thap phan.<br>Dung: <code>500.000</code> | <code>1.234,50</code> | <code>500000</code><br>Sai : <code>500.00</code> | <code>1.2.3</code> | <code>1,234</code><br><i>Khac hẳn o so luong o tren, noi dau <b>.</b> la dau thap phan.</i><br>Enter = Them thanh toan.</html>",
                "<html>Dấu <b>.</b> là dấu TÁCH NGHÌN (phải đủ 3 chữ số), dấu <b>,</b> là dấu thập phân.<br>Đúng: <code>500.000</code> | <code>1.234,50</code> | <code>500000</code><br>Sai: <code>500.00</code> | <code>1.2.3</code> | <code>1,234</code><br><i>Khác hẳn ô số lượng bên trên, nơi dấu <b>.</b> là dấu thập phân.</i><br>Enter = Thêm thanh toán.</html>",
            "<html>Go SO DIEN THOAI cua khach (tu 3 chu so tro len) roi chon tu danh sach goi y.<br>Chi khach <b>duoc chon tu danh sach</b> moi duoc gan vao hoa don -- go du so ma khong chon van la khach vang lai.</html>",
                "<html>Gõ SỐ ĐIỆN THOẠI của khách (từ 3 chữ số trở lên) rồi chọn từ danh sách gợi ý.<br>Chỉ khách <b>được chọn từ danh sách</b> mới được gắn vào hóa đơn -- gõ đủ số mà không chọn vẫn là khách vãng lai.</html>",
            "Dau <b>,</b> hoac <b>.</b> la dau THAP PHAN, toi da 3 chu so (cot DECIMAL(10,3)).<br>Dung: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>",
                "Dấu <b>,</b> hoặc <b>.</b> là dấu THẬP PHÂN, tối đa 3 chữ số (cột DECIMAL(10,3)).<br>Đúng: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>",
            "Dau <b>.</b> la dau TACH NGHIN: <code>158.000</code> = 158 nghin.<br>Dau <b>,</b> la dau thap phan (toi da 2 chu so).</html>",
                "Dấu <b>.</b> là dấu TÁCH NGHÌN: <code>158.000</code> = 158 nghìn.<br>Dấu <b>,</b> là dấu thập phân (tối đa 2 chữ số).</html>",
            "Cot VAT % la thue suat cua san pham; don gia trong Price_History DA GOM VAT.", "Cột VAT % là thuế suất của sản phẩm; đơn giá trong Price_History ĐÃ GỒM VAT.",
            "CANH BAO DATABASE: Truong '", "CẢNH BÁO DATABASE: Trường '",
            "CANH BAO DATABASE: Cot '", "CẢNH BÁO DATABASE: Cột '",
            "' co rang buoc NOT NULL!\nBan khong duoc phep xoa gia tri nay ve NULL hoac de trong.", "' có ràng buộc NOT NULL!\nBạn không được phép xóa giá trị này về NULL hoặc để trống.",
            "' co rang buoc NOT NULL trong Database!\nBan khong duoc phep xoa gia tri nay ve NULL hoac de trong.", "' có ràng buộc NOT NULL trong Database!\nBạn không được phép xóa giá trị này về NULL hoặc để trống.",
            "dang lap (OPEN)", "đang lập (OPEN)",
            "Da thanh toan (PAID)", "Đã thanh toán (PAID)",
            "Da huy (VOIDED)", "Đã hủy (VOIDED)",
            "Tat ca", "Tất cả",
            "tat ca", "tất cả",
            "7 ngay gan nhat", "7 ngày gần nhất",
            "30 ngay gan nhat", "30 ngày gần nhất",
            "90 ngay gan nhat", "90 ngày gần nhất"
        );

        marks(
            "Tien mat", "Tiền mặt",
            "The ngan hang", "Thẻ ngân hàng",
            "Vi dien tu", "Ví điện tử"
        );

        marks(
            "Tra hang -- hoa don ", "Trả hàng -- hóa đơn ",
            "HOA DON BAN LE -- ", "HÓA ĐƠN BÁN LẺ -- ",
            " -- con thieu ", " -- còn thiếu ",
            "Da thanh toan (PAID)", "Đã thanh toán (PAID)",
            "Da huy (VOIDED)", "Đã hủy (VOIDED)",
            "Dang lap (OPEN)", "Đang lập (OPEN)",
            "Thu nghiem co che canh bao xoa gia tri NOT NULL", "Thử nghiệm cơ chế cảnh báo xóa giá trị NOT NULL",
            "Ghi ", "Ghi ",
            " luot tra hang cho hoa don ", " lượt trả hàng cho hóa đơn ",
            "Vui long chon mot hoa don de tra hang!", "Vui lòng chọn một hóa đơn để trả hàng!",
            "Tai khoan '", "Tài khoản '",
            "' chua duoc gan voi nhan vien\nnao trong bang Cashier, nen khong the dung lam nguoi duyet tra hang.\n\nCach sua: bam \"Nap lai database\" trong Setup App (xoa sach du lieu),\nhoac chay doan ALTER/UPDATE o plan/12 muc 4.1 de giu du lieu dang co.",
                "' chưa được gắn với nhân viên\nnào trong bảng Cashier, nên không thể dùng làm người duyệt trả hàng.\n\nCách sửa: bấm \"Nạp lại database\" trong Setup App (xóa sạch dữ liệu),\nhoặc chạy đoạn ALTER/UPDATE ở plan/12 mục 4.1 để giữ dữ liệu đang có.",
            "Chua xac dinh duoc nguoi duyet", "Chưa xác định được người duyệt",
            "Hoa don khong co dong hang nao de tra!", "Hóa đơn không có dòng hàng nào để trả!",
            "Tra hang -- hoa don ", "Trả hàng -- hóa đơn ",
            "Hoa don: ", "Hóa đơn: ",
            "   |  Ngay: ", "   |  Ngày: ",
            "   |  Trang thai: ", "   |  Trạng thái: ",
            "Cac dong cua hoa don", "Các dòng của hóa đơn",
            "Them >>", "Thêm >>",
            "<< Xoa", "<< Xóa",
            "Xac nhan tra hang", "Xác nhận trả hàng",
            "Huy", "Hủy",
            "Vui long chon mot dong hang o bang ben trai de them!", "Vui lòng chọn một dòng hàng ở bảng bên trái để thêm!",
            "Dong ", "Dòng ",
            "Dong nay da duoc chon", "Dòng này đã được chọn",
            "Da tra het", "Đã trả hết",
            "San pham: ", "Sản phẩm: ",
            "Da mua: ", "Đã mua: ",
            "    |   Da tra: ", "    |   Đã trả: ",
            "    |   Con lai: ", "    |   Còn lại: ",
            "Nhap so luong tra (toi da 3 chu so thap phan, dung ',' hoac '.'):", "Nhập số lượng trả (tối đa 3 chữ số thập phân, dùng ',' hoặc '.'):",
            "Nhap so luong tra cho dong ", "Nhập số lượng trả cho dòng ",
            "Vui long chon mot muc o bang ben phai de xoa!", "Vui lòng chọn một mục ở bảng bên phải để xóa!",
            "Chua chon muc nao", "Chưa chọn mục nào",
            "Khong co san pham nao de tra!", "Không có sản phẩm nào để trả!",
            "Danh sach trong", "Danh sách trống",
            "Tong tien tra lai: ", "Tổng tiền trả lại: ",
            "Xac nhan xu ly tra hang", "Xác nhận xử lý trả hàng",
            "Khong tra hang duoc", "Không trả hàng được",
            "Da ghi ", "Đã ghi ",
            "Thanh toan bang ma QR (VietQR)", "Thanh toán bằng mã QR (VietQR)",
            "So tien trong ma QR: ", "Số tiền trong mã QR: ",
            "Chuyen toi: ", "Chuyển tới: ",
            "Tai khoan: ", "Tài khoản: ",
            "Noi dung CK (Transaction_Ref): ", "Nội dung CK (Transaction_Ref): ",
            "Xac nhan da nhan tien", "Xác nhận đã nhận tiền",
            "In hoa don", "In hóa đơn",
            "Ten hang", "Tên hàng",
            "Ma HD", "Mã HĐ",
            "Thoi gian", "Thời gian",
            "Don gia", "Đơn giá",
            "Thanh tien", "Thành tiền",
            "Tien khach dua", "Tiền khách đưa",
            "Da ghi nhan", "Đã ghi nhận",
            "Tien thoi lai", "Tiền thối lại"
        );

        // Keep accents on the remaining dynamic messages and explanations too.
        // These entries translate only fixed UI wording; IDs, names, amounts,
        // currencies, and role values remain supplied by the application.
        marks(
            " trong config.properties khong dang nhap duoc.\nCo the mat khau trong bang Account da bi doi.\n\nPhien hien tai van duoc giu nguyen.",
                " trong config.properties không đăng nhập được.\nCó thể mật khẩu trong bảng Account đã bị đổi.\n\nPhiên hiện tại vẫn được giữ nguyên.",
            "Loc theo ngay ban. 'Tat ca' nap moi hoa don, ke ca hai hoa don tu nam truoc.",
                "Lọc theo ngày bán. 'Tất cả' nạp mọi hóa đơn, kể cả hai hóa đơn từ năm trước.",
            "Chuc nang nay bi khoa voi Thu ngan (Cashier). Chi Quan ly (Manager) moi co quyen.",
                "Chức năng này bị khóa với Thu ngân (Cashier). Chỉ Quản lý (Manager) mới có quyền.",
            "Chinh sua thong tin hoa don truc tiep trong DB", "Chỉnh sửa thông tin hóa đơn trực tiếp trong DB",
            "Huy hoa don: du lieu van con, chi khong tinh vao bao cao", "Hủy hóa đơn: dữ liệu vẫn còn, chỉ không tính vào báo cáo",
            "Xoa hoa don va toan bo du lieu lien quan khoi DB", "Xóa hóa đơn và toàn bộ dữ liệu liên quan khỏi DB",
            "Chinh sua so luong, don gia dong hang trong DB", "Chỉnh sửa số lượng, đơn giá dòng hàng trong DB",
            "Xoa dong hang khoi DB", "Xóa dòng hàng khỏi DB",
            "Ghi mot luot tra hang cho cac dong cua hoa don dang chon", "Ghi một lượt trả hàng cho các dòng của hóa đơn đang chọn",
            "Huy mot luot tra hang da ghi (chon dong trong bang Tra hang)", "Hủy một lượt trả hàng đã ghi (chọn dòng trong bảng Trả hàng)",
            " ngay gan nhat", " ngày gần nhất",
            "?\n\nHuy KHAC Xoa:\n  - Du lieu van con nguyen: dong hang, thanh toan, tra hang\n  - Hoa don khong con duoc tinh vao bao cao doanh thu\n  - Khong tra hang duoc nua\n  - KHONG mo lai duoc: VOIDED la trang thai cuoi\n\nMuon xoa sach khoi database thi dung nut \"Xoa hoa don\".",
                "?\n\nHủy KHÁC Xóa:\n  - Dữ liệu vẫn còn nguyên: dòng hàng, thanh toán, trả hàng\n  - Hóa đơn không còn được tính vào báo cáo doanh thu\n  - Không trả hàng được nữa\n  - KHÔNG mở lại được: VOIDED là trạng thái cuối\n\nMuốn xóa sạch khỏi database thì dùng nút \"Xóa hóa đơn\".",
            ".\nDu lieu van con trong database, chi khong tinh vao bao cao nua.",
                ".\nDữ liệu vẫn còn trong database, chỉ không tính vào báo cáo nữa.",
            " khoi Database?\n\nThao tac nay se xoa sach:\n  1. Cac luot tra hang lien quan (Return)\n  2. Cac chi tiet thanh toan (Cash/Card/EWallet_Payment)\n  3. Cac luot thanh toan (Payment)\n  4. Toan bo dong hang (Invoice_Line)\n  5. Phan dau hoa don (Invoice)\n\nHanh dong khong the hoan tac!",
                " khỏi Database?\n\nThao tác này sẽ xóa sạch:\n  1. Các lượt trả hàng liên quan (Return)\n  2. Các chi tiết thanh toán (Cash/Card/EWallet_Payment)\n  3. Các lượt thanh toán (Payment)\n  4. Toàn bộ dòng hàng (Invoice_Line)\n  5. Phần đầu hóa đơn (Invoice)\n\nHành động không thể hoàn tác!",
            " khoi Database!", " khỏi Database!",
            " da co trong danh sach se tra.\n\nMuon doi so luong: chon muc do o bang ben phai, bam '<< Xoa',\nroi 'Them >>' lai voi so luong khac.",
                " đã có trong danh sách sẽ trả.\n\nMuốn đổi số lượng: chọn mục đó ở bảng bên phải, bấm '<< Xóa',\nrồi 'Thêm >>' lại với số lượng khác.",
            " da mua,\nkhong con gi de tra.", " đã mua,\nkhông còn gì để trả.",
            "So tien vuot qua so con thieu", "Số tiền vượt quá số còn thiếu",
            "So tien khong hop le", "Số tiền không hợp lệ",
            "So luong khong hop le", "Số lượng không hợp lệ",
            "Chua nhap so tien", "Chưa nhập số tiền",
            "Chua nhap so luong.", "Chưa nhập số lượng.",
            "So luong phai lon hon 0.", "Số lượng phải lớn hơn 0.",
            "Khong co gia tai ngay ban", "Không có giá tại ngày bán",
            "Khong co duong tra lai cho ", "Không có đường trả lại cho ",
            "Hay go dung ", "Hãy gõ đúng ",
            "Thanh toan QR", "Thanh toán QR",
            "Quay", "Quầy",
            "Ngay ban", "Ngày bán",
            "Phuong thuc thanh toan", "Phương thức thanh toán",
            "Nhom", "Nhóm",
            "Dien thoai", "Điện thoại",
            "Chon mot ", "Chọn một ",
            "Vui long chon mot ", "Vui lòng chọn một ",
            "de xoa!", "để xóa!",
            "de sua!", "để sửa!",
            "de tra hang!", "để trả hàng!",
            "khong doc duoc", "không đọc được",
            "khong hop le", "không hợp lệ",
            "Chinh sua", "Chỉnh sửa",
            "Da luu", "Đã lưu",
            " thanh cong", " thành công",
            " trong Database", " trong Database",
            "(NOT NULL)", "(NOT NULL)",
            "Da tra het ", "Đã trả hết ",
            "Hoa don chua co dong hang nao, chua biet phai thu bao nhieu.",
                "Hóa đơn chưa có dòng hàng nào, chưa biết phải thu bao nhiêu.",
            "Hoa don chua co dong hang nao.", "Hóa đơn chưa có dòng hàng nào.",
            "Hoa don nay da thu du tien", "Hóa đơn này đã thu đủ tiền",
            "Khong can them dong thanh toan nao nua.", "Không cần thêm dòng thanh toán nào nữa.",
            "Khong co duong tra lai cho ", "Không có đường trả lại cho ",
            "bang Payment khong co cot tien thoi cho phuong thuc nay,\nva phieu hoan tien nam ngoai pham vi ban demo.",
                "bảng Payment không có cột tiền thối cho phương thức này,\nvà phiếu hoàn tiền nằm ngoài phạm vi bản demo.",
            "Vi dien tu khong co tien thoi, hay go dung ", "Ví điện tử không có tiền thối, hãy gõ đúng ",
            "khong co dong thanh toan nao", "không có dòng thanh toán nào",
            "Chua co dong hang", "Chưa có dòng hàng",
            "Chua nhap so luong", "Chưa nhập số lượng",
            "Khong doc duoc hoa don", "Không đọc được hóa đơn",
            "Loi khi thuc hien tim kiem:\n", "Lỗi khi thực hiện tìm kiếm:\n",
            "Loi co so du lieu", "Lỗi cơ sở dữ liệu",
            "Co loi xay ra", "Có lỗi xảy ra",
            "Ten khach hang", "Tên khách hàng",
            "Thong tin", "Thông tin",
            "Cho", "Chờ",
            "Ghi chu", "Ghi chú",
            "(khach vang lai)", "(khách vãng lai)",
            "Giam gia", "Giảm giá",
            "Phuong thuc", "Phương thức",
            "Tien thoi", "Tiền thối",
            "Thu ngan", "Thu ngân",
            "Khach dua:", "Khách đưa:",
            "So tien:", "Số tiền:",
            "Tai khoan:", "Tài khoản:",
            "<html>Dau <b>,</b> hoac <b>.</b> la dau THAP PHAN, toi da 3 chu so (cot DECIMAL(10,3)).<br>Dung: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>",
                "<html>Dấu <b>,</b> hoặc <b>.</b> là dấu THẬP PHÂN, tối đa 3 chữ số (cột DECIMAL(10,3)).<br>Đúng: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>",
            "<html>Dau <b>.</b> la dau TACH NGHIN: <code>158.000</code> = 158 nghin.<br>Dau <b>,</b> la dau thap phan (toi da 2 chu so).</html>",
                "<html>Dấu <b>.</b> là dấu TÁCH NGHÌN: <code>158.000</code> = 158 nghìn.<br>Dấu <b>,</b> là dấu thập phân (tối đa 2 chữ số).</html>",
            "Doanh thu tung NGAY cua tung CUA HANG trong 7 ngay gan nhat. Hoa don khong noi thang toi Shop nen phai di vong: Invoice -> Counter -> Shop. GROUP BY hai cot (shop, ngay) nen moi dong ket qua la mot o trong bang cheo.",
                "Doanh thu từng NGÀY của từng CỬA HÀNG trong 7 ngày gần nhất. Hóa đơn không nối thẳng tới Shop nên phải đi vòng: Invoice -> Counter -> Shop. GROUP BY hai cột (shop, ngày) nên mỗi dòng kết quả là một ô trong bảng chéo.",
            "SELF JOIN: noi bang Price_History voi CHINH NO de dat gia moi canh gia cu. Subquery MAX(...) tim moc gia LIEN TRUOC cua cung san pham. Dieu kien: gia moi > gia cu * 1.10 va nam trong nam nay.",
                "SELF JOIN: nối bảng Price_History với CHÍNH NÓ để đặt giá mới cạnh giá cũ. Subquery MAX(...) tìm mốc giá LIỀN TRƯỚC của cùng sản phẩm. Điều kiện: giá mới > giá cũ * 1.10 và nằm trong năm nay.",
            "Bay pho bien: AVG thang tren Invoice_Line se ra trung binh moi DONG chu khong phai moi HOA DON. Phai tinh tong tien tung hoa don truoc (subquery), roi moi AVG theo thu ngan.",
                "Bẫy phổ biến: AVG thẳng trên Invoice_Line sẽ ra trung bình mỗi DÒNG chứ không phải mỗi HÓA ĐƠN. Phải tính tổng tiền từng hóa đơn trước (subquery), rồi mới AVG theo thu ngân.",
            "Hai subquery: mot tinh tong hoa don, mot tinh tien tra lai. Tien tra lai tinh theo TY LE (SL tra / SL mua) nhan thanh tien, de phan giam gia cung duoc hoan dung ty le.",
                "Hai subquery: một tính tổng hóa đơn, một tính tiền trả lại. Tiền trả lại tính theo TỶ LỆ (SL trả / SL mua) nhân thành tiền, để phần giảm giá cũng được hoàn đúng tỷ lệ.",
            "Cot 'Gia_ghi_tren_HD' lay tu Invoice_Line (da dong bang luc ban). Cot 'Gia_hom_nay' goi fn_get_product_price(barcode, CURDATE()). Bao cao GOM theo (san pham, don gia da dong bang), nen moi dong tra loi: 'co bao nhieu dong hang dang ghi gia nay, ban tu ngay nao den ngay nao, va gia hom nay la bao nhieu'. Dong co 'Ban_tu_ngay' xa nhat chinh la hai hoa don tu nam truoc.",
                "Cột 'Gia_ghi_tren_HD' lấy từ Invoice_Line (đã đóng băng lúc bán). Cột 'Gia_hom_nay' gọi fn_get_product_price(barcode, CURDATE()). Báo cáo GOM theo (sản phẩm, đơn giá đã đóng băng), nên mỗi dòng trả lời: 'có bao nhiêu dòng hàng đang ghi giá này, bán từ ngày nào đến ngày nào, và giá hôm nay là bao nhiêu'. Dòng có 'Ban_tu_ngay' xa nhất chính là hai hóa đơn từ năm trước.",
            " khoi hoa don ", " khỏi hóa đơn ",
            ") da co trong danh sach se tra.\n\nMuon doi so luong: chon muc do o bang ben phai, bam '<< Xoa',\nroi 'Them >>' lai voi so luong khac.",
                ") đã có trong danh sách sẽ trả.\n\nMuốn đổi số lượng: chọn mục đó ở bảng bên phải, bấm '<< Xóa',\nrồi 'Thêm >>' lại với số lượng khác.",
            ") da tra het ", ") đã trả hết ",
            " da mua,\nkhong con gi de tra.", " đã mua,\nkhông còn gì để trả.",
            "Vui long chon mot luot tra trong bang 'Tra hang (Return)' de huy!",
                "Vui lòng chọn một lượt trả trong bảng 'Trả hàng (Return)' để hủy!",
            "Khong doc duoc so dong / lan tra cua hang dang chon.", "Không đọc được số dòng / lần trả của hàng đang chọn.",
            "Huy luot tra nay khoi Database?\n\n   Hoa don : ", "Hủy lượt trả này khỏi Database?\n\n   Hóa đơn : ",
            "   Dong    : ", "   Dòng    : ",
            "   Lan tra : ", "   Lần trả : ",
            "   SL tra  : ", "   SL trả  : ",
            "   Tien hoan da ghi nhan: ", "   Tiền hoàn đã ghi nhận: ",
            "\n\nSau khi huy, so luong nay duoc tra lai vao phan con tra duoc cua dong.\nHanh dong khong the hoan tac!",
                "\n\nSau khi hủy, số lượng này được trả lại vào phần còn trả được của dòng.\nHành động không thể hoàn tác!",
            "Xac nhan huy luot tra", "Xác nhận hủy lượt trả",
            "Khong tim thay luot tra lan ", "Không tìm thấy lượt trả lần ",
            " cua dong ", " của dòng ",
            " (co the ai khac da huy truoc).", " (có thể ai khác đã hủy trước).",
            "Khong co gi bi huy", "Không có gì bị hủy",
            "Da huy luot tra lan ", "Đã hủy lượt trả lần ",
            "(Khong the thay doi)", "(Không thể thay đổi)",
            "Ma vach (Barcode)", "Mã vạch (Barcode)",
            "So luong (Quantity)", "Số lượng (Quantity)",
            "Don gia (Unit_Price)", "Đơn giá (Unit_Price)",
            "Giam gia (Discount)", "Giảm giá (Discount)",
            "don gia da gom VAT -- thue suat chi la thong tin", "đơn giá đã gồm VAT -- thuế suất chỉ là thông tin",
            "(chua co dong thanh toan nao)", "(chưa có dòng thanh toán nào)",
            "Da luu hoa don ", "Đã lưu hóa đơn ",
            " nhung khong doc lai duoc de in.", " nhưng không đọc lại được để in."
        );

        updateOptionPaneDefaults();
    }

    private I18n() { }

    private static void pairs(String... values) {
        for (int i = 0; i + 1 < values.length; i += 2) {
            TO_EN.put(values[i], values[i + 1]);
            TO_VI.put(values[i + 1], values[i]);
        }
    }

    private static void field(String key, String vietnamese, String english) {
        TO_EN.put(key, english);
        TO_VI.put(key, vietnamese);
        TO_EN.put(vietnamese, english);
        TO_VI.put(english, vietnamese);
    }

    private static void marks(String... values) {
        for (int i = 0; i + 1 < values.length; i += 2) {
            String source = values[i];
            String marked = values[i + 1];
            TO_VI_MARKS.put(source, marked);
            String english = TO_EN.get(source);
            if (english != null && !english.equals(source)) TO_VI_MARKS.put(english, marked);
        }
    }

    public static Language getLanguage() { return language; }

    public static boolean isEnglish() { return language == Language.EN; }

    /**
     * Chu tinh (nhan nut, nhan, tieu de khung, tooltip...): tra ve ban dung voi ngon
     * ngu hien tai VA ghi cap (vi, en) vao tu dien, de khi bam doi ngon ngu bo duyet
     * giao dien dich duoc ca thanh phan da tao truoc do.
     *
     * <p>Viet {@code vietnamese} CO DAU ngay tai cho dung -- khong can them dong nao
     * vao bang tu dien o tren. Chuoi khong dau cu (bang tu dien cua Neve) van chay.
     */
    public static String text(String vietnamese, String english) {
        register(vietnamese, english);
        return isEnglish() ? english : translate(vietnamese);
    }

    /**
     * Chu dong (thong bao ghep so lieu, hop thoai...): chi chon ban theo ngon ngu,
     * KHONG ghi vao tu dien -- tranh tu dien phinh ra theo moi ma hoa don / so tien.
     */
    public static String msg(String vietnamese, String english) {
        return isEnglish() ? english : vietnamese;
    }

    private static void register(String vietnamese, String english) {
        if (vietnamese == null || english == null || vietnamese.equals(english)) return;
        if (english.equals(TO_EN.get(vietnamese))) return;
        TO_EN.put(vietnamese, english);
        TO_VI.put(english, vietnamese);
        sortedKeys = null;
    }

    public static String languageSwitchLabel() {
        return isEnglish() ? "Tiếng Việt" : "English";
    }

    /** Localize the known role labels used by quick-account configuration. */
    public static String quickAccountLabel(String label) {
        if ("Quan ly".equals(label)) return isEnglish() ? "Manager" : "Quản lý";
        return translate(label);
    }

    public static void markDynamicText(JComponent component) {
        component.putClientProperty(DYNAMIC_TEXT, Boolean.TRUE);
    }

    public static void markLocalizedText(JTextComponent component) {
        component.putClientProperty(TEXT_COMPONENT, Boolean.TRUE);
    }

    public static void markKeyValueText(JTextComponent component) {
        component.putClientProperty(TEXT_COMPONENT, Boolean.TRUE);
        component.putClientProperty(KEY_VALUE_TEXT, Boolean.TRUE);
    }

    public static void toggle() {
        language = isEnglish() ? Language.VI : Language.EN;
        billing.util.Lang.setEnglish(isEnglish());
        updateOptionPaneDefaults();
        localizeOpenWindows();
        for (Runnable callback : new ArrayList<>(LANGUAGE_CHANGE_CALLBACKS.values())) callback.run();
    }

    public static void onLanguageChange(JComponent owner, Runnable callback) {
        LANGUAGE_CHANGE_CALLBACKS.put(owner, callback);
    }

    public static void removeLanguageChangeListener(JComponent owner) {
        LANGUAGE_CHANGE_CALLBACKS.remove(owner);
    }

    public static void install(Window window) {
        installWindowListener();
        localize(window);
    }

    public static void localizeOpenWindows() {
        for (Window window : Window.getWindows()) {
            if (window.isDisplayable()) localize(window);
        }
    }

    private static void installWindowListener() {
        if (windowListenerInstalled) return;
        windowListenerInstalled = true;
        Toolkit.getDefaultToolkit().addAWTEventListener((AWTEventListener) event -> {
            if (event instanceof WindowEvent && event.getID() == WindowEvent.WINDOW_OPENED) {
                Window window = ((WindowEvent) event).getWindow();
                SwingUtilities.invokeLater(() -> localize(window));
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
    }

    private static void updateOptionPaneDefaults() {
        UIManager.put("OptionPane.yesButtonText", text("Có", "Yes"));
        UIManager.put("OptionPane.noButtonText", text("Không", "No"));
        UIManager.put("OptionPane.okButtonText", text("Đồng ý", "OK"));
        UIManager.put("OptionPane.cancelButtonText", text("Hủy", "Cancel"));
    }

    private static void localize(Component component) {
        if ((component instanceof AbstractButton && !(component instanceof JMenuItem))
                || component instanceof JMenuItem || component instanceof JLabel) {
            translateComponentText((JComponent) component);
        }

        if (component instanceof JComponent) {
            JComponent jc = (JComponent) component;
            translateTooltip(jc);
            translateBorder(jc);
            if (jc instanceof JComboBox<?>) installComboRenderer((JComboBox<?>) jc);
            if (jc instanceof JTable) localizeTable((JTable) jc);
            if (jc instanceof JTextComponent && Boolean.TRUE.equals(jc.getClientProperty(TEXT_COMPONENT))) {
                translateTextComponent((JTextComponent) jc);
            }
        }

        if (component instanceof Window) translateWindowTitle((Window) component);

        if (component instanceof JTabbedPane) localizeTabs((JTabbedPane) component);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) localize(child);
        }
        component.invalidate();
        component.repaint();
    }

    private static void translateTextComponent(JTextComponent component) {
        String current = component.getText();
        String source = originalText((JComponent) component, current, TEXT_SOURCE, TEXT_LAST);
        String translated = Boolean.TRUE.equals(component.getClientProperty(KEY_VALUE_TEXT))
                ? translateKeyValueLines(source) : translate(source);
        if (!Objects.equals(current, translated)) component.setText(translated);
        component.putClientProperty(TEXT_LAST, translated);
    }

    private static String translateKeyValueLines(String source) {
        StringBuilder translated = new StringBuilder(source.length());
        String[] lines = source.split("(?<=\\n)", -1);
        for (String line : lines) {
            int separator = line.indexOf(" : ");
            if (separator < 0) {
                translated.append(line);
                continue;
            }
            translated.append(String.format("%-18s", translate(line.substring(0, separator).trim())))
                    .append(line.substring(separator));
        }
        return translated.toString();
    }

    private static void translateWindowTitle(Window window) {
        String current;
        if (window instanceof Frame) current = ((Frame) window).getTitle();
        else if (window instanceof Dialog) current = ((Dialog) window).getTitle();
        else return;
        String[] state = WINDOW_TITLES.get(window);
        if (state == null || !Objects.equals(current, state[1])) state = new String[] {current, current};
        String translated = translate(state[0]);
        if (!Objects.equals(current, translated)) {
            if (window instanceof Frame) ((Frame) window).setTitle(translated);
            else ((Dialog) window).setTitle(translated);
        }
        state[1] = translated;
        WINDOW_TITLES.put(window, state);
    }

    private static void translateComponentText(JComponent component) {
        if (Boolean.TRUE.equals(component.getClientProperty(DYNAMIC_TEXT))) return;
        bindTextListener(component);
        String current = getText(component);
        if (current == null) return;
        String source = originalText(component, current, TEXT_SOURCE, TEXT_LAST);
        String translated = translate(source);
        component.putClientProperty(TEXT_LAST, translated);
        if (!translated.equals(current)) setComponentText(component, translated);
    }

    private static String originalText(JComponent component, String current, String sourceKey, String lastKey) {
        String source = (String) component.getClientProperty(sourceKey);
        String last = (String) component.getClientProperty(lastKey);
        if (source == null || !Objects.equals(current, last)) {
            source = current;
            component.putClientProperty(sourceKey, source);
        }
        return source;
    }

    private static String getText(JComponent component) {
        if (component instanceof AbstractButton) return ((AbstractButton) component).getText();
        if (component instanceof JLabel) return ((JLabel) component).getText();
        return null;
    }

    private static void setText(JComponent component, String text) {
        if (component instanceof AbstractButton) ((AbstractButton) component).setText(text);
        else if (component instanceof JLabel) ((JLabel) component).setText(text);
    }

    private static void setComponentText(JComponent component, String text) {
        component.putClientProperty(UPDATING_TEXT, Boolean.TRUE);
        setText(component, text);
        component.putClientProperty(UPDATING_TEXT, Boolean.FALSE);
    }

    private static void bindTextListener(JComponent component) {
        if (Boolean.TRUE.equals(component.getClientProperty(TEXT_LISTENER))) return;
        component.addPropertyChangeListener("text", event -> {
            if (Boolean.TRUE.equals(component.getClientProperty(UPDATING_TEXT))) return;
            String changed = (String) event.getNewValue();
            if (changed == null || Objects.equals(changed, component.getClientProperty(TEXT_LAST))) return;
            component.putClientProperty(TEXT_SOURCE, changed);
            String translated = translate(changed);
            component.putClientProperty(TEXT_LAST, translated);
            if (!Objects.equals(changed, translated)) setComponentText(component, translated);
        });
        component.putClientProperty(TEXT_LISTENER, Boolean.TRUE);
    }

    private static void translateTooltip(JComponent component) {
        bindTooltipListener(component);
        String current = component.getToolTipText();
        if (current == null) return;
        String source = originalText(component, current, TIP_SOURCE, TIP_LAST);
        String translated = translate(source);
        component.putClientProperty(TIP_LAST, translated);
        if (!translated.equals(current)) setTooltip(component, translated);
    }

    private static void bindTooltipListener(JComponent component) {
        if (Boolean.TRUE.equals(component.getClientProperty(TIP_LISTENER))) return;
        component.addPropertyChangeListener("toolTipText", event -> {
            if (Boolean.TRUE.equals(component.getClientProperty(UPDATING_TIP))) return;
            String changed = (String) event.getNewValue();
            if (changed == null || Objects.equals(changed, component.getClientProperty(TIP_LAST))) return;
            component.putClientProperty(TIP_SOURCE, changed);
            String translated = translate(changed);
            component.putClientProperty(TIP_LAST, translated);
            if (!Objects.equals(changed, translated)) setTooltip(component, translated);
        });
        component.putClientProperty(TIP_LISTENER, Boolean.TRUE);
    }

    private static void setTooltip(JComponent component, String text) {
        component.putClientProperty(UPDATING_TIP, Boolean.TRUE);
        component.setToolTipText(text);
        component.putClientProperty(UPDATING_TIP, Boolean.FALSE);
    }

    private static void translateBorder(JComponent component) {
        if (!(component.getBorder() instanceof TitledBorder)) return;
        TitledBorder border = (TitledBorder) component.getBorder();
        String current = border.getTitle();
        if (current == null) return;
        String source = originalText(component, current, BORDER_SOURCE, BORDER_LAST);
        String translated = translate(source);
        if (!translated.equals(current)) {
            border.setTitle(translated);
            component.setBorder(border);
        }
        component.putClientProperty(BORDER_LAST, translated);
    }

    private static void localizeTabs(JTabbedPane tabs) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            String sourceKey = "billing.i18n.tabSource." + i;
            String lastKey = "billing.i18n.tabLast." + i;
            String current = tabs.getTitleAt(i);
            String source = (String) tabs.getClientProperty(sourceKey);
            String last = (String) tabs.getClientProperty(lastKey);
            if (source == null || !Objects.equals(current, last)) {
                source = current;
                tabs.putClientProperty(sourceKey, source);
            }
            String translated = translate(source);
            if (!Objects.equals(current, translated)) tabs.setTitleAt(i, translated);
            tabs.putClientProperty(lastKey, translated);
        }
    }

    @SuppressWarnings("unchecked")
    private static void installComboRenderer(JComboBox<?> combo) {
        if (Boolean.TRUE.equals(combo.getClientProperty(COMBO_RENDERER))) return;
        for (int i = 0; i < combo.getItemCount(); i++) {
            Object item = combo.getItemAt(i);
            if (item != null && !(item instanceof String || item instanceof Enum<?>)) return;
            if (item instanceof String && ((String) item).contains("_")) return;
        }
        ListCellRenderer<Object> delegate = (ListCellRenderer<Object>) combo.getRenderer();
        combo.setRenderer((list, value, index, selected, focus) -> {
            Component rendered = delegate.getListCellRendererComponent(list, value, index, selected, focus);
            if (rendered instanceof JLabel && (value instanceof String || value instanceof Enum<?>)) {
                ((JLabel) rendered).setText(translate(value.toString()));
            }
            return rendered;
        });
        combo.putClientProperty(COMBO_RENDERER, Boolean.TRUE);
    }

    private static void localizeTable(JTable table) {
        Object listenerFlag = table.getClientProperty(TABLE_LISTENER);
        if (!Boolean.TRUE.equals(listenerFlag)) {
            TableModelListener listener = (TableModelEvent event) ->
                    SwingUtilities.invokeLater(() -> translateTableHeaders(table));
            table.getModel().addTableModelListener(listener);
            PropertyChangeListener modelListener = event -> {
                if ("model".equals(event.getPropertyName()) && event.getNewValue() instanceof TableModel) {
                    ((TableModel) event.getNewValue()).addTableModelListener(listener);
                    SwingUtilities.invokeLater(() -> translateTableHeaders(table));
                }
            };
            table.addPropertyChangeListener("model", modelListener);
            table.putClientProperty(TABLE_LISTENER, Boolean.TRUE);
        }
        translateTableHeaders(table);
    }

    private static void translateTableHeaders(JTable table) {
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            Object header = column.getHeaderValue();
            if (header == null) continue;
            String current = header.toString();
            String[] state = COLUMN_TEXT.get(column);
            if (state == null || !Objects.equals(state[1], current)) {
                state = new String[] {current, current};
            }
            String translated = "Dong".equals(state[0]) ? text("Dòng", "Row") : translate(state[0]);
            column.setHeaderValue(translated);
            state[1] = translated;
            COLUMN_TEXT.put(column, state);
        }
        table.getTableHeader().repaint();
    }

    public static String translate(String source) {
        if (source == null || source.isEmpty()) return source;
        if (language == Language.VI) {
            String marked = TO_VI_MARKS.get(source);
            if (marked != null) return marked;
        }
        Map<String, String> dictionary = language == Language.EN ? TO_EN : TO_VI;
        String exact = dictionary.get(source);
        if (exact != null) return exact;
        String formatted = translateKnownDynamicText(source);
        if (formatted != null) return formatted;

        // Only translate a dynamic label's full leading or trailing phrase.
        // This keeps user data (names, IDs, currency values) intact.
        java.util.List<String> keys = sortedKeys(dictionary);
        String result = source;
        boolean changed = false;
        for (String key : keys) {
            if (key.endsWith(" ") && result.startsWith(key)) {
                String translatedPrefix = language == Language.VI && TO_VI_MARKS.containsKey(key)
                        ? TO_VI_MARKS.get(key) : dictionary.get(key);
                if (translatedPrefix == null) continue;
                result = translatedPrefix + result.substring(key.length());
                changed = true;
                break;
            }
        }
        for (String key : keys) {
            if (key.startsWith(" ") && result.endsWith(key)) {
                String translatedSuffix = language == Language.VI && TO_VI_MARKS.containsKey(key)
                        ? TO_VI_MARKS.get(key) : dictionary.get(key);
                if (translatedSuffix == null) continue;
                result = result.substring(0, result.length() - key.length()) + translatedSuffix;
                changed = true;
                break;
            }
        }
        return changed ? result : source;
    }

    /** Khoa co dau cach o dau/cuoi, dai truoc -- tinh lai chi khi tu dien doi. */
    private static java.util.List<String> sortedKeys(Map<String, String> dictionary) {
        Map<Language, java.util.List<String>> cache = sortedKeys;
        if (cache == null) {
            cache = new EnumMap<>(Language.class);
            sortedKeys = cache;
        }
        java.util.List<String> keys = cache.get(language);
        if (keys == null) {
            keys = new ArrayList<>();
            for (String k : dictionary.keySet()) if (k.startsWith(" ") || k.endsWith(" ")) keys.add(k);
            if (language == Language.VI) {
                for (String k : TO_VI_MARKS.keySet()) if (k.startsWith(" ") || k.endsWith(" ")) keys.add(k);
            }
            keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
            cache.put(language, keys);
        }
        return keys;
    }

    private static String translateKnownDynamicText(String source) {
        if (language == Language.EN) {
            java.util.regex.Matcher invoiceHeader = java.util.regex.Pattern
                    .compile("^Hoa don: (.*)  \\|  Ngay: (.*)  \\|  Trang thai: (.*)$").matcher(source);
            if (invoiceHeader.matches()) {
                return "Invoice: " + invoiceHeader.group(1) + "  |  Date: "
                        + invoiceHeader.group(2) + "  |  Status: " + invoiceHeader.group(3);
            }

            java.util.regex.Matcher accountTip = java.util.regex.Pattern
                    .compile("^Dien san va dang nhap bang tai khoan '(.+)'$").matcher(source);
            if (accountTip.matches()) return "Fill in and sign in with account '" + accountTip.group(1) + "'";

            java.util.regex.Matcher user = java.util.regex.Pattern
                    .compile("^\\s*Nguoi dung: (.*)  \\[Vai tro: (.*)\\]$").matcher(source);
            if (user.matches()) return "  User: " + knownDemoAccountName(user.group(1))
                    + "  [Role: " + user.group(2) + "]";

            if (source.trim().equals("Nguoi dung: (Chua dang nhap)")) return "  User: (Not signed in)";

            java.util.regex.Matcher count = java.util.regex.Pattern
                    .compile("^Dang hien (.+) hoa don \\((.*)\\)$").matcher(source);
            if (count.matches()) return "Showing " + count.group(1) + " invoices (" + translate(count.group(2)) + ")";

            java.util.regex.Matcher rows = java.util.regex.Pattern
                    .compile("^Tổng số hàng: (.+) dòng$").matcher(source);
            if (rows.matches()) return "Total rows: " + rows.group(1) + " rows";

            java.util.regex.Matcher truncatedRows = java.util.regex.Pattern
                    .compile("^Đang hiện (.+) dòng đầu \\(bảng còn nhiều hơn — dùng bộ lọc để thu hẹp\\)$").matcher(source);
            if (truncatedRows.matches()) return "Showing " + truncatedRows.group(1)
                    + " rows (more results are available; use filters to narrow the results)";

            java.util.regex.Matcher returnRows = java.util.regex.Pattern
                    .compile("^Đang hiện (.+) dòng đầu (.*)$").matcher(source);
            if (returnRows.matches()) return "Showing " + returnRows.group(1) + " first rows " + returnRows.group(2);
        } else {
            java.util.regex.Matcher invoiceHeader = java.util.regex.Pattern
                    .compile("^Hoa don: (.*)  \\|  Ngay: (.*)  \\|  Trang thai: (.*)$").matcher(source);
            if (invoiceHeader.matches()) {
                return "Hóa đơn: " + invoiceHeader.group(1) + "  |  Ngày: "
                        + invoiceHeader.group(2) + "  |  Trạng thái: " + invoiceHeader.group(3);
            }

            java.util.regex.Matcher asciiUser = java.util.regex.Pattern
                    .compile("^\\s*Nguoi dung: (.*)  \\[Vai tro: (.*)\\]$").matcher(source);
            if (asciiUser.matches()) {
                return "  Người dùng: " + knownDemoAccountName(asciiUser.group(1))
                        + "  [Vai trò: " + asciiUser.group(2) + "]";
            }
            if (source.trim().equals("Nguoi dung: (Chua dang nhap)")) return "  Người dùng: (Chưa đăng nhập)";

            java.util.regex.Matcher asciiCount = java.util.regex.Pattern
                    .compile("^Dang hien (.+) hoa don \\((.*)\\)$").matcher(source);
            if (asciiCount.matches()) {
                return "Đang hiển thị " + asciiCount.group(1) + " hóa đơn (" + translate(asciiCount.group(2)) + ")";
            }

            java.util.regex.Matcher accountTip = java.util.regex.Pattern
                    .compile("^Fill in and sign in with account '(.+)'$").matcher(source);
            if (accountTip.matches()) return "Điền sẵn và đăng nhập bằng tài khoản '" + accountTip.group(1) + "'";

            java.util.regex.Matcher user = java.util.regex.Pattern
                    .compile("^\\s*User: (.*)  \\[Role: (.*)\\]$").matcher(source);
            if (user.matches()) return "  Người dùng: " + knownDemoAccountName(user.group(1))
                    + "  [Vai trò: " + user.group(2) + "]";

            if (source.trim().equals("User: (Not signed in)")) return "  Người dùng: (Chưa đăng nhập)";

            java.util.regex.Matcher count = java.util.regex.Pattern
                    .compile("^Showing (.+) invoices \\((.*)\\)$").matcher(source);
            if (count.matches()) return "Đang hiển thị " + count.group(1) + " hóa đơn (" + translate(count.group(2)) + ")";

            java.util.regex.Matcher rows = java.util.regex.Pattern
                    .compile("^Total rows: (.+) rows$").matcher(source);
            if (rows.matches()) return "Tổng số hàng: " + rows.group(1) + " dòng";

            java.util.regex.Matcher truncatedRows = java.util.regex.Pattern
                    .compile("^Showing (.+) rows \\(more results are available; use filters to narrow the results\\)$").matcher(source);
            if (truncatedRows.matches()) return "Đang hiện " + truncatedRows.group(1)
                    + " dòng đầu (bảng còn nhiều hơn — dùng bộ lọc để thu hẹp)";

            java.util.regex.Matcher returnRows = java.util.regex.Pattern
                    .compile("^Showing (.+) first rows (.*)$").matcher(source);
            if (returnRows.matches()) return "Đang hiển thị " + returnRows.group(1) + " dòng đầu " + returnRows.group(2);
        }
        return null;
    }
    private static String knownDemoAccountName(String name) {
        if ("Quan Ly He Thong".equals(name)) return "Quản Lý Hệ Thống";
        if ("Nhan Vien Thu Ngan".equals(name)) return "Nhân Viên Thu Ngân";
        return name;
    }
}
