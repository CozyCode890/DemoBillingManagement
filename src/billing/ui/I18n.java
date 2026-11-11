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
        add("Đổi vai trò ▾", "Switch role ▾");
        add("Đăng xuất", "Log out");
        add("Người dùng: ", "User: ");
        add("Vai trò:", "Role:");
        add("]", "]");
        add("(Chưa đăng nhập)", "(Not signed in)");
        add("(ĐĂNG NHẬP TỰ ĐỘNG)", "(AUTO LOGIN)");
        add("App đã đăng nhập sẵn, không qua màn hình đăng nhập. Đặt quick.login.auto= (để trống) trong config.properties để tắt.",
                "The app signed in automatically. Set quick.login.auto= (empty) in config.properties to disable this.");
        add("Đổi sang tài khoản khác mà KHÔNG mất dữ liệu đang nhập ở tab 1", "Switch to another account without losing the invoice in tab 1");
        add("1. Lập hóa đơn", "1. New invoice");
        add("2. Tra cứu dữ liệu", "2. Browse data");
        add("3. Xem hóa đơn (Database)", "3. View invoices (Database)");
        add("4. Báo cáo", "4. Reports");
        add("Ngôn ngữ", "Language");
        add("Chuyển ngôn ngữ giao diện", "Switch the interface language");
        add("Xác nhận đăng xuất", "Confirm log out");
        add("Bạn có chắc chắn muốn đăng xuất khỏi hệ thống?", "Are you sure you want to log out?");
        add("Tab \"Lập hóa đơn\" đang có ", "Tab \"New invoice\" has ");
        add(" chưa lưu.\nĐăng xuất sẽ mất toàn bộ. Vẫn đăng xuất?\n\nGợi ý: muốn xem giao diện của vai trò khác mà không mất giỏ hàng\nthì dùng nút \"Đổi vai trò\" bên cạnh.",
                " unsaved. Logging out will discard it. Log out anyway?\n\nTo view another role without losing this invoice,\nuse the \"Switch role\" button beside this one.");
        add("Tài khoản '", "Account '");
        add("' trong config.properties không đăng nhập được.\nCó thể mật khẩu trong bảng Account đã bị đổi.\n\nPhiên hiện tại vẫn được giữ nguyên.",
                "' from config.properties could not sign in.\nThe password in the Account table may have changed.\n\nYour current session is unchanged.");
        add("Đổi vai trò thất bại", "Could not switch role");
        add(" dòng hàng", " invoice line(s)");
        add(" lần thanh toán", " payment(s)");

        add("Đăng nhập hệ thống -- Retail Invoicing", "Sign in -- Retail Invoicing");
        add("Tên đăng nhập:", "Username:");
        add("Mật khẩu:", "Password:");
        add("Đăng nhập", "Sign in");
        add("Thoát", "Exit");
        add("Đăng nhập nhanh (tài khoản mẫu của bản demo)", "Quick sign in (demo accounts)");
        add("Bấm một nút để điền sẵn và đăng nhập ngay.", "Select an account to fill in the credentials and sign in.");
        add("Phím tắt: Alt+1 ... Alt+", "Shortcuts: Alt+1 ... Alt+");
        add(".  Enter = Đăng nhập.", ".  Enter = Sign in.");
        add("Điền sẵn và đăng nhập bằng tài khoản '", "Fill in and sign in with account '");
        add("Vui lòng nhập đầy đủ Tên đăng nhập và Mật khẩu.", "Enter both a username and password.");
        add("Đang kiểm tra tài khoản...", "Checking account...");
        add("Sai tên đăng nhập hoặc mật khẩu.", "Incorrect username or password.");

        add("1. Thông tin hóa đơn", "1. Invoice details");
        add("Quầy:", "Counter:");
        add("Thu ngân:", "Cashier:");
        add("Ngày bán:", "Sale date:");
        add("Khách (SDT):", "Customer (phone):");
        add("Mẹo: đổi 'Ngày bán' về năm ngoái rồi thêm sản phẩm -> đơn giá sẽ lấy theo GIÁ CŨ trong Price_History.",
                "Tip: set the sale date to last year, then add a product to see its historical price from Price_History.");
        add("2. Thêm sản phẩm", "2. Add a product");
        add("Sản phẩm:", "Product:");
        add("Số lượng:", "Quantity:");
        add("3. Các dòng hàng (sẽ thành bảng Invoice_Line)", "3. Invoice lines (stored in Invoice_Line)");
        add("4. Thanh toán (một hóa đơn có thể có NHIỀU dòng thanh toán)", "4. Payments (an invoice can have multiple payments)");
        add("Phương thức:", "Method:");
        add("Tải lại", "Reload");
        add("Thêm vào hóa đơn", "Add to invoice");
        add("Sửa số lượng", "Edit quantity");
        add("Xóa dòng đang chọn", "Remove selected row");
        add("Thêm thanh toán", "Add payment");
        add("Khách trả đủ bằng tiền mặt", "Customer pays the balance in cash");
        add("Tạo mã QR", "Create QR code");
        add("Xóa hết", "Clear all");
        add("XUẤT HÓA ĐƠN  (INSERT + COMMIT)", "SAVE INVOICE  (INSERT + COMMIT)");
        add("Đang lưu...", "Saving...");
        add(" = ", " = ");
        add("Tổng: ", "Total: ");
        add("Đã thu: ", "Paid: ");
        add("Còn thiếu: ", "Balance due: ");
        add("Tiền thối: ", "Change: ");
        add("Giá ngày ", "Price on ");
        add("  (không có KM)", "  (no promotion)");
        add("Cột VAT % là thuế suất của sản phẩm; đơn giá trong Price_History ĐÃ GỒM VAT.",
                "VAT % is the product tax rate; prices in Price_History already include VAT.");
        add("<html>Dấu <b>,</b> hoặc <b>.</b> là dấu THẬP PHÂN (tối đa 3 chữ số) -- KHÔNG phải dấu tách nghìn.<br>Đúng: <code>2</code> | <code>1,5</code> | <code>1.5</code> | <code>0,25</code><br><i>Khác hẳn ở tiền bên dưới, nơi dấu <b>.</b> là dấu tách nghìn.</i><br>Enter = Thêm vào hóa đơn.</html>",
                "<html>Use <b>,</b> or <b>.</b> as the decimal separator (up to 3 digits). Neither is a thousands separator.<br>Examples: <code>2</code> | <code>1,5</code> | <code>1.5</code> | <code>0,25</code><br><i>Money fields below use <b>.</b> as a thousands separator.</i><br>Enter = Add to invoice.</html>");
        add("<html>Dấu <b>.</b> là dấu TÁCH NGHÌN (phải đủ 3 chữ số), dấu <b>,</b> là dấu thập phân.<br>Đúng: <code>500.000</code> | <code>1.234,50</code> | <code>500000</code><br>Sai : <code>500.00</code> | <code>1.2.3</code> | <code>1,234</code><br><i>Khác hẳn ở số lượng ở trên, nơi dấu <b>.</b> là dấu thập phân.</i><br>Enter = Thêm thanh toán.</html>",
                "<html><b>.</b> is the thousands separator (groups of 3 digits); <b>,</b> is the decimal separator.<br>Examples: <code>500.000</code> | <code>1.234,50</code> | <code>500000</code><br>Invalid: <code>500.00</code> | <code>1.2.3</code> | <code>1,234</code><br><i>Quantity above uses <b>.</b> as a decimal separator.</i><br>Enter = Add payment.</html>");
        add("Thẻ ngân hàng", "Bank card");
        add("Đang lập", "Open");
        add("Đã thanh toán", "Paid");
        add("Đã hủy", "Voided");
        add("Đang lập (OPEN)", "Open (OPEN)");
        add("Đã thanh toán (PAID)", "Paid (PAID)");
        add("Đã hủy (VOIDED)", "Voided (VOIDED)");
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
        add("Khoảng giá sản phẩm: ", "Product price range: ");
        add("Thuế suất: ", "Tax rate: ");
        add("Thời gian trả hàng: ", "Return date and time: ");
        add("Số lượng hoàn trả: ", "Return quantity: ");
        add(" dòng", " rows");
        add("Tổng số hàng: ", "Total rows: ");
        add(" dòng đầu (bảng còn nhiều hơn — dùng bộ lọc để thu hẹp)", " rows (more are available; use a filter to narrow the results)");

        add("Sửa hóa đơn", "Edit invoice");
        add("Hủy hóa đơn", "Void invoice");
        add("Xóa hóa đơn", "Delete invoice");
        add("Sửa dòng hàng", "Edit line");
        add("Xóa dòng hàng", "Delete line");
        add("Thử xóa giá trị (Set NULL)", "Try setting value to NULL");
        add("Trả hàng", "Return items");
        add("Hủy lượt trả", "Delete return");
        add("Tải lại danh sách", "Reload list");
        add("Khoảng ngày:", "Date range:");
        add("Danh sách hóa đơn (bấm để xem chi tiết)", "Invoices (select one to view details)");
        add("Thông tin chung + tổng tiền", "Summary and total");
        add("Các dòng hàng (Invoice_Line)", "Invoice lines (Invoice_Line)");
        add("Thanh toán (Payment + bảng con)", "Payments (Payment and child tables)");
        add("Trả hàng (Return)", "Returns (Return)");
        add("7 ngày gần nhất", "Last 7 days");
        add("30 ngày gần nhất", "Last 30 days");
        add("90 ngày gần nhất", "Last 90 days");
        add("Thông tin chung", "Summary");
        add("Các dòng của hóa đơn", "Invoice lines");
        add("Sẽ trả lại", "To be returned");
        add("Thêm >>", "Add >>");
        add("<< Xóa", "<< Remove");
        add("Xác nhận trả hàng", "Confirm return");
        add("Hủy", "Cancel");
        add("Chỉnh sửa thông tin hóa đơn trực tiếp trong DB", "Edit invoice details directly in the DB");
        add("Hủy hóa đơn: dữ liệu vẫn còn, chỉ không tính vào báo cáo", "Void an invoice: data remains but is excluded from reports");
        add("Xóa hóa đơn và toàn bộ dữ liệu liên quan khỏi DB", "Delete the invoice and all related data from the DB");
        add("Chỉnh sửa số lượng, đơn giá dòng hàng trong DB", "Edit line quantity and unit price in the DB");
        add("Xóa dòng hàng khỏi DB", "Delete an invoice line from the DB");
        add("Thử nghiệm cơ chế cảnh báo xóa giá trị NOT NULL", "Try the warning for clearing a NOT NULL value");
        add("Ghi một lượt trả hàng cho các dòng của hóa đơn đang chọn", "Record a return for lines in the selected invoice");
        add("Hủy một lượt trả hàng đã ghi (chọn dòng trong bảng Trả hàng)", "Delete a recorded return (select a row in the Returns table)");
        add("Trạng thái đổi tự động khi thu đủ tiền, hoặc qua nút \"Hủy hóa đơn\" ở tab Xem hóa đơn", "Status changes automatically when the invoice is fully paid or voided from the View invoices tab");
        add("Lọc theo ngày bán. 'Tất cả' nạp mọi hóa đơn, kể cả hai hóa đơn INV-OLD-* từ năm trước.", "Filter by sale date. 'All' loads every invoice, including both INV-OLD-* invoices from last year.");
        add("Gõ SỐ ĐIỆN THOẠI của khách (từ 3 chữ số trở lên) rồi chọn từ danh sách gợi ý.<br>Chỉ khách <b>được chọn từ danh sách</b> mới được gắn vào hóa đơn -- gõ đủ số mà không chọn vẫn là khách vãng lai.</html>",
                "Type at least 3 digits of the customer's phone number, then select a suggestion.<br>Only a customer <b>selected from the list</b> is added to the invoice; typing a full number without selecting keeps it as a walk-in customer.</html>");
        add("Sản phẩm", "Product");
        add("SL", "Qty");
        add("DVT", "Unit");
        add("Chi tiết", "Details");
        add("Khách đưa", "Tendered");

        add("(khách vãng lai)", "(walk-in customer)");
        add("Có lỗi xảy ra", "An error occurred");
        add("Lỗi kết nối", "Connection error");
        add("Không kết nối được MySQL.\n\n", "Could not connect to MySQL.\n\n");
        add("Kiểm tra lần lượt:\n", "Check the following:\n");
        add("  1. MySQL 8 đã chạy chưa?  (Services -> MySQL80 -> Running)\n", "  1. Is MySQL 8 running? (Services -> MySQL80 -> Running)\n");
        add("  2. Đã tạo database chưa?  Chạy file sql/01_schema.sql và sql/02_seed.sql\n", "  2. Has the database been created? Run sql/01_schema.sql and sql/02_seed.sql\n");
        add("  3. Mật khẩu trong file config.properties đã đúng chưa?\n", "  3. Is the password in config.properties correct?\n");
        add("  4. Nếu lỗi 'No suitable driver': thiếu file mysql-connector-j.jar\n     trong thư mục lib/. Chạy setup.ps1 để tải về.\n", "  4. For 'No suitable driver': mysql-connector-j.jar is missing from lib/. Run setup.ps1 to download it.\n");
        add("Chọn báo cáo", "Select a report");
        add("Câu SQL đang chạy", "SQL query");
        add("Kết quả", "Results");
        add("2. Doanh thu 7 ngày / cửa hàng", "2. Revenue, last 7 days / store");
        add("3. Sản phẩm tăng giá > 10%", "3. Products with price increases > 10%");
        add("4. Thu ngân có giá trị hóa đơn trung bình cao nhất", "4. Highest average invoice total / thu ngan");
        add("5. Hóa đơn trả hàng > 50%", "5. Invoices with returns > 50%");
        add("Bonus: hóa đơn cũ giữ giá cũ", "Bonus: historical invoices keep their original prices");
        add(" -- đang chạy...", " -- running...");
        add("Câu lệnh chạy xong nhưng không có dòng nào thỏa điều kiện.", "The query completed but returned no matching rows.");
        add("Doanh thu từng NGÀY của từng CỬA HÀNG trong 7 ngày gần nhất. Hóa đơn không nối thẳng tới Shop nên phải đi vòng: Invoice -> Counter -> Shop. GROUP BY hai cột (shop, ngày) nên mỗi dòng kết quả là một ô trong bảng chéo.",
                "Daily revenue for each store in the last 7 days. An invoice is linked to a Shop through Counter, so the query joins Invoice -> Counter -> Shop. GROUP BY the shop and date to get one row per cross-tab cell.");
        add("SELF JOIN: nối bảng Price_History với CHÍNH NÓ để đặt giá mới cạnh giá cũ. Subquery MAX(...) tìm mốc giá LIỀN TRƯỚC của cùng sản phẩm. Điều kiện: giá mới > giá cũ * 1.10 và nằm trong năm nay.",
                "SELF JOIN: join Price_History to itself to compare a new price with the previous price. The MAX(...) subquery finds the immediately preceding price for the same product. The new price must be over 110% of the old price and dated this year.");
        add("Bẫy phổ biến: AVG thẳng trên Invoice_Line sẽ ra trung bình mỗi DÒNG chứ không phải mỗi HÓA ĐƠN. Phải tính tổng tiền từng hóa đơn trước (subquery), rồi mới AVG theo thu ngân.",
                "Common mistake: AVG over Invoice_Line gives an average per LINE, not per INVOICE. First total each invoice in a subquery, then calculate AVG by cashier.");
        add("Hai subquery: một tính tổng hóa đơn, một tính tiền trả lại. Tiền trả lại tính theo TỶ LỆ (SL trả / SL mua) nhân thành tiền, để phần giảm giá cũng được hoàn đúng tỷ lệ.",
                "Two subqueries: one totals each invoice and the other totals refunds. A refund is calculated proportionally (returned quantity / purchased quantity) times the line total, so discounts are refunded proportionally too.");
        add("Ý nghĩa câu truy vấn", "About this query");

        add("Sửa hóa đơn:", "Edit invoice:");
        add("Chỉnh sửa Hóa đơn: ", "Edit invoice: ");
        add("Chỉnh sửa Dòng hàng: ", "Edit invoice line: ");
        add("Sửa dòng hàng:", "Edit invoice line:");
        add("Mã hóa đơn (PK):", "Invoice ID (PK):");
        add("Ngày lập (YYYY-MM-DD) *:", "Invoice date (YYYY-MM-DD) *:");
        add("Giờ lập (HH:MM:SS) *:", "Invoice time (HH:MM:SS) *:");
        add("Trạng thái:", "Status:");
        add("(chỉ đọc)", "(read only)");
        add("Mã quầy (Counter_ID) *:", "Counter ID (Counter_ID) *:");
        add("Mã thu ngân (Cashier_ID) *:", "Cashier ID (Cashier_ID) *:");
        add("Mã khách hàng:", "Customer ID:");
        add("Xóa mã khách (Set NULL)", "Clear customer ID (set NULL)");
        add("Lưu vào Database", "Save to Database");
        add("Hủy bỏ", "Cancel");
        add("Mã vạch (Barcode) *:", "Barcode *:");
        add("Số lượng (Quantity) *:", "Quantity *:");
        add("Đơn giá (Unit_Price) *:", "Unit price (Unit_Price) *:");
        add("Giảm giá (Discount) *:", "Discount *:");
        add("Thử xóa (Set NULL)", "Try setting NULL");
        add("Mã hóa đơn", "Invoice ID");
        add("Ngày lập", "Invoice date");
        add("Giờ lập", "Invoice time");
        add("Thành công", "Success");
        add("Thông báo", "Notice");
        add("Lỗi", "Error");
        add("Lỗi định dạng", "Invalid format");
        add("Cảnh báo ràng buộc NOT NULL", "NOT NULL constraint warning");
        add("CẢNH BÁO DATABASE: Trường '", "DATABASE WARNING: Field '");
        add("CẢNH BÁO DATABASE: Cột '", "DATABASE WARNING: Column '");
        add("' có ràng buộc NOT NULL!\nBạn không được phép xóa giá trị này về NULL hoặc để trống.",
                "' has a NOT NULL constraint.\nThis value cannot be cleared or left empty.");
        add("' có ràng buộc NOT NULL trong Database!\nBạn không được phép xóa giá trị này về NULL hoặc để trống.",
                "' has a NOT NULL constraint in the Database.\nThis value cannot be cleared or left empty.");
        add("Customer_ID là trường cho phép NULL (khách vãng lai).\nGiá trị này sẽ được cập nhật thành NULL trong database.",
                "Customer_ID allows NULL for walk-in customers.\nThis value will be updated to NULL in the database.");
        add(" thành công trong Database!", " saved to the Database.");
        add("<html>Dấu <b>,</b> hoặc <b>.</b> là dấu THẬP PHÂN, tối đa 3 chữ số (cột DECIMAL(10,3)).<br>Đúng: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>",
                "<html>Use <b>,</b> or <b>.</b> as the decimal separator, with up to 3 digits (DECIMAL(10,3)).<br>Examples: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>");
        add("<html>Dấu <b>.</b> là dấu TÁCH NGHÌN: <code>158.000</code> = 158 nghìn.<br>Dấu <b>,</b> là dấu thập phân (tối đa 2 chữ số).</html>",
                "<html><b>.</b> is the thousands separator: <code>158.000</code> = 158 thousand.<br><b>,</b> is the decimal separator (up to 2 digits).</html>");
        add("<html><i style='color:#c0392b;'>* Toàn bộ các cột trong Invoice_Line đều có ràng buộc NOT NULL.<br>Nút 'Thử xóa (Set NULL)' sẽ minh họa cơ chế cảnh báo của Database.</i></html>",
                "<html><i style='color:#c0392b;'>* All Invoice_Line columns have a NOT NULL constraint.<br>'Try setting NULL' demonstrates the Database warning.</i></html>");
        add("<html><i style='color:#c0392b;'>* Các trường đánh dấu (*) có ràng buộc NOT NULL. Nếu xóa trắng sẽ bị hệ thống cảnh báo!</i></html>",
                "<html><i style='color:#c0392b;'>* Fields marked with (*) have a NOT NULL constraint. Clearing one will show a warning.</i></html>");
        add("Chưa có dòng hàng", "No invoice lines");
        add("Hóa đơn chưa có dòng hàng nào.", "The invoice has no lines yet.");
        add("Hóa đơn này đã thu đủ tiền.", "The invoice is fully paid.");
        add("Hóa đơn này đã thu đủ tiền (", "The invoice is fully paid (");
        add(").\nKhông cần thêm dòng thanh toán nào nữa.", ").\nNo additional payment is needed.");
        add("Đã thu đủ tiền", "Fully paid");
        add("Chưa có dòng thanh toán nào.", "No payments have been added.");
        add("Chưa có thanh toán", "No payments");
        add("Chưa nạp được danh sách quầy / thu ngân.\nKiểm tra kết nối MySQL rồi bấm \"Tải lại\" ở khung 1.",
                "Could not load the counter / cashier list.\nCheck the MySQL connection, then click \"Reload\" in section 1.");
        add("Thiếu dữ liệu danh mục", "Missing lookup data");
        add("Số tiền ", "Amount ");
        add(" nhiều hơn số còn thiếu ", " exceeds the balance due of ");
        add("Không có đường trả lại cho ", "There is no way to return excess funds for ");
        add(": bảng Payment không có cột tiền thối cho phương thức này,\nvà phiếu hoàn tiền nằm ngoài phạm vi bản demo.\n\nHãy gõ đúng ",
                ": the Payment table has no change column for this method,\nand refunds are outside this demo.\n\nEnter exactly ");
        add(", hoặc chọn \"Tiền mặt\" nếu khách đưa thừa.", ", or choose \"Cash\" if the customer pays extra.");
        add("Số tiền vượt quá số còn thiếu", "Amount exceeds balance due");
        add("Ví điện tử không có tiền thối, hãy gõ đúng ", "E-wallet payments cannot give change; enter exactly ");
        add("Hóa đơn chưa có dòng hàng nào, chưa biết phải thu bao nhiêu.", "The invoice has no lines, so there is nothing to collect.");
        add("Chưa nhập ", "Please enter ");
        add("Chưa nhập số tiền", "Amount is required");
        add("Chưa nhập số lượng.", "Quantity is required.");
        add("Số lượng không hợp lệ", "Invalid quantity");
        add("Số tiền không hợp lệ", "Invalid amount");
        add(" không đọc được: \"", " could not be read: \"");
        add("Dấu . là dấu tách nghìn (phải đủ 3 chữ số),\n", "Use . as the thousands separator (groups of 3 digits),\n");
        add("dấu , là dấu thập phân (tối đa 2 chữ số).\n\n", "and , as the decimal separator (up to 2 digits).\n\n");
        add("Đúng:  500.000   |   1.234,50   |   500000\n", "Valid: 500.000 | 1.234,50 | 500000\n");
        add("Sai :  500.00    |   1.2.3      |   1,234", "Invalid: 500.00 | 1.2.3 | 1,234");
        add(" không được âm.", " cannot be negative.");
        add(" phải lớn hơn 0.", " must be greater than 0.");
        add("Chỉ được một dấu thập phân trong số lượng: ", "Use only one decimal separator in the quantity: ");
        add("Số lượng không hợp lệ: ", "Invalid quantity: ");
        add("Dấu , hoặc . là dấu thập phân, KHÔNG phải dấu tách nghìn.\n", "Use , or . as the decimal separator, not a thousands separator.\n");
        add("Đúng:  2   |   1,5   |   1.5   |   0,25", "Valid: 2 | 1,5 | 1.5 | 0,25");
        add("Tối đa 3 chữ số thập phân (cột DECIMAL(10,3)).\n", "Use at most 3 decimal places (DECIMAL(10,3)).\n");
        add("Bạn gõ: ", "You entered: ");
        add("Số lượng phải lớn hơn 0.", "Quantity must be greater than 0.");
        add("Chỉ còn ", "Only ");
        add(" có thể trả.", " can be returned.");
        add("Đã lưu hóa đơn ", "Saved invoice ");
        add(" nhưng không đọc lại được để in.", " but could not reload it for printing.");
        add("Đã lưu hóa đơn", "Saved invoice");
        add("Không cập nhật được hóa đơn", "Could not update invoice");
        add("Không tìm thấy hóa đơn", "Invoice not found");
        add("Không tìm thấy dòng hàng", "Invoice line not found");
        add("Không có dòng hàng", "No invoice lines");
        add("Chưa có dòng hàng nào", "No invoice lines");
        add("Không đọc được hóa đơn", "Could not load invoice");
        add("In hóa đơn", "Print receipt");
        add("Đóng", "Close");
        add("Xác nhận đã nhận tiền", "Confirm payment received");
        add("Người duyệt", "Approver");
        add("Giá", "Price");
        add("Số lượng", "Quantity");
        add("Ngày lập phải dùng định dạng YYYY-MM-DD!", "Invoice date must use YYYY-MM-DD format.");
        add("Giờ lập phải dùng định dạng HH:MM:SS!", "Invoice time must use HH:MM:SS format.");
        add("Vui lòng chọn một hóa đơn để chỉnh sửa!", "Select an invoice to edit.");
        add("Chưa chọn hóa đơn", "No invoice selected");
        add("Vui lòng chọn một hóa đơn để hủy!", "Select an invoice to void.");
        add("Vui lòng chọn một hóa đơn để xóa!", "Select an invoice to delete.");
        add("Vui lòng chọn một dòng hàng trong bảng 'Các dòng hàng' để chỉnh sửa!", "Select an invoice line in the 'Invoice lines' table to edit it.");
        add("Vui lòng chọn một dòng hàng để xóa!", "Select an invoice line to delete.");
        add("Vui lòng chọn một hóa đơn để trả hàng!", "Select an invoice to return items.");
        add("Hóa đơn không có dòng hàng nào để trả!", "This invoice has no lines to return.");
        add("Chưa chọn lượt trả", "No return selected");
        add("Không tìm thấy hóa đơn ", "Invoice not found: ");
        add("Không tìm thấy dòng hàng số ", "Invoice line not found: ");
        add("Đã xóa thành công hóa đơn ", "Deleted invoice ");
        add(" khỏi Database!", " from the Database.");
        add("Đã xóa thành công dòng hàng ", "Deleted invoice line ");
        add("Đã hủy hóa đơn", "Invoice voided");
        add("Xác nhận xóa dòng hàng", "Confirm line deletion");
        add("Xác nhận", "Confirm");
        add("Xóa thành công", "Deleted successfully");
        add(" hóa đơn (", " invoices (");
        add("Không có gì bị hủy", "Nothing was voided");
        add("Cảnh báo", "Warning");
        add("Lỗi cơ sở dữ liệu", "Database error");
        add("Lỗi khi tải dữ liệu từ bảng '", "Could not load data from table '");
        add("Lỗi khi xác định cột tìm kiếm:\n", "Could not determine the search column:\n");
        add("Lỗi khi thực hiện tìm kiếm:\n", "Search failed:\n");
        add("Số lượng mới cho \"", "New quantity for \"");
        add("Dấu , hoặc . là dấu thập phân, tối đa 3 chữ số.", "Use comma or period as the decimal separator, up to 3 decimal places.");
        add("Hủy hóa đơn ", "Void invoice ");
        add("Đã hủy hóa đơn ", "Voided invoice ");
        add(".\nDữ liệu vẫn còn trong database, chỉ không tính vào báo cáo nữa.", ".\nThe data remains in the database but is excluded from reports.");
        add("Bạn có chắc chắn muốn XÓA VĨNH VIỄN hóa đơn ", "Permanently delete invoice ");
        add("Bạn có chắc chắn muốn xóa dòng hàng số ", "Delete invoice line ");
        add(" khỏi hóa đơn ", " from invoice ");
        add("Vui lòng chọn một dòng hàng ở bảng bên trái để thêm!", "Select an invoice line from the left table to add it.");
        add("Dòng ", "Line ");
        add(" đã có trong danh sách sẽ trả.\n\nMuốn đổi số lượng: chọn mục đó ở bảng bên phải, bấm '<< Xóa',\nrồi 'Thêm >>' lại với số lượng khác.", " is already in the return list.\n\nTo change the quantity, select the line on the right, click '<< Remove',\nthen add it again with the new quantity.");
        add(" đã trả hết ", " has already been returned in full (");
        add(" đã mua,\nkhông còn gì để trả.", " purchased);\nthere is nothing left to return.");
        add("Sản phẩm: ", "Product: ");
        add("Đã mua: ", "Purchased: ");
        add("   |   Đã trả: ", "   |   Returned: ");
        add("   |   Còn lại: ", "   |   Remaining: ");
        add("Vui lòng chọn một mục ở bảng bên phải để xóa!", "Select a line from the right table to remove it.");
        add("Không có sản phẩm nào để trả!", "There are no items available to return.");
        add("Ghi ", "Record ");
        add("Tổng tiền phải trả lại: ", "Total refund: ");
        add("Người duyệt: ", "Approved by: ");
        add("Đã ghi ", "Recorded ");
        add(" lượt trả hàng cho hóa đơn ", " return(s) for invoice ");
        add("Tổng tiền trả lại: ", "Total refund: ");
        add("Vui lòng chọn một lượt trả trong bảng 'Trả hàng (Return)' để hủy!", "Select a return in the 'Returns (Return)' table to delete it.");
        add("Không đọc được số dòng hàng / lần trả của mục đang chọn.", "Could not read the selected invoice line / return number.");
        add("Hủy lượt trả này khỏi Database?\n\n  Hóa đơn : ", "Delete this return from the Database?\n\n  Invoice: ");
        add("  Dòng    : ", "  Line: ");
        add("  Lần trả : ", "  Return: ");
        add("  SL trả  : ", "  Qty returned: ");
        add("Không tìm thấy lượt trả lần ", "Return ");
        add(" (có thể ai khác đã hủy trước).", " was not found (someone else may have deleted it).");
        add("Đã hủy lượt trả lần ", "Deleted return ");
        add(" của dòng ", " for line ");
        add("Hủy lượt trả này khỏi Database?", "Delete this return from the Database?");
        add("Chức năng này bị khóa với Thu ngân (Cashier). Chỉ Quản lý (Manager) mới có quyền.",
                "This action is disabled for Thu ngan (Cashier). Only Quan ly (Manager) can use it.");
        add("Không trả hàng được", "Cannot return items");
        add("Dòng này đã được chọn", "Line already selected");
        add("Đã trả hết", "Fully returned");
        add("Chưa chọn dòng hàng", "No invoice line selected");
        add("Chưa chọn mục nào", "No line selected");
        add("Danh sách trống", "Empty list");
        add("Xác nhận xử lý trả hàng", "Confirm returns");
        add("Tất cả", "All");
        add("Tổng tiền ", "Invoice total ");
        add(" nhưng chỉ thanh toán ", " but only ");
        add(" (thiếu ", " paid (balance due: ");
        add("Vẫn lưu, nhưng hóa đơn sẽ ở trạng thái ĐANG LẬP (OPEN):\n", "Save it anyway? The invoice will remain OPEN:\n");
        add("  - Không được tính vào báo cáo doanh thu\n", "  - It will not count toward revenue reports\n");
        add("  - Không trả hàng được\n", "  - Items cannot be returned\n");
        add("Tổng hóa đơn ", "Invoice total ");
        add("Giảm giá", "Discount");
        add("Tăng giá", "Price increase");
        add("Đang hiện ", "Showing ");
        add("hóa đơn", "invoice");

        add("Mã khách", "Customer ID");
        add("Không có", "None");
        add("Xóa mã khách", "Clear customer ID");
        add("Xác nhận xóa", "Confirm deletion");
        add("Quầy", "Counter");
        add("Khách hàng", "Customer");
        add("HÓA ĐƠN BÁN LẺ", "RETAIL RECEIPT");
        add("Thanh toán bằng mã QR (VietQR)", "QR payment (VietQR)");
        add("Số tiền trong mã QR: ", "Amount in QR code: ");
        add("Chuyển tới: ", "Transfer to: ");
        add("Tài khoản: ", "Account: ");
        add("Nội dung CK (Transaction_Ref): ", "Transfer reference (Transaction_Ref): ");
        add("Bản demo: việc đối soát là THU CÔNG -- bấm \"Xác nhận\" khi đã thấy tiền vào.",
                "Demo only: verify payments manually. Click \"Confirm\" after the money arrives.");
        add("Trả hàng -- hóa đơn ", "Return items -- invoice ");
        add("Hóa đơn: ", "Invoice: ");
        add("  |  Ngày: ", "  |  Date: ");
        add("  |  Trạng thái: ", "  |  Status: ");
        add("Thời gian", "Date and time");
        add("Mã HD", "Invoice ID");
        add("Tên hàng", "Item");
        add("Tổng cộng", "Grand total");
        add("Tổng tiền", "Total");
        add("Đã thu", "Paid");
        add("Còn thiếu", "Balance due");
        add("Tiền thối", "Change");
        add("Các dòng", "Lines");
        add("Đơn giá", "Unit price");
        add("Thành tiền", "Line total");
        add("Phương thức", "Method");
        add("Số tiền", "Amount");
        add("Đã gửi lệnh in hóa đơn.", "The print command was sent.");
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
        String mapped;
        if (language == Language.ENGLISH) {
            mapped = EN.get(normalize(source));
        } else {
            mapped = EN_SOURCE.get(normalize(source));
            if (mapped == null) mapped = VI.get(normalize(source));
        }
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
            return language == Language.ENGLISH ? "Line" : "Dòng";
        }
        return text(source);
    }

    /** Translate longer messages built from fixed UI phrases and dynamic values. */
    public static String richText(String source) {
        if (source == null) return null;
        String exact = text(source);
        if (!Objects.equals(exact, source)) return exact;
        if (language == Language.ENGLISH) {
            return translatePhrases(source, EN, true);
        }
        Map<String, String> englishToSource = new HashMap<>();
        for (Map.Entry<String, String> entry : VI.entrySet()) {
            englishToSource.put(entry.getKey(), entry.getValue());
        }
        String translated = translatePhrases(source, englishToSource, false);
        return translatePhrases(translated, EN_SOURCE, true);
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
