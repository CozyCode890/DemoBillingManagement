# Kế Hoạch Chi Tiết — Khối 5: Giao Diện Tab Lập Hoá Đơn

> **Dành cho AI / lập trình viên tiếp theo:** đây là khối thứ năm trong đợt cải tiến gồm 6 khối
> (1 SQL log → Setup App ✅ xong, 2 đăng nhập nhanh ✅ xong, 3 sửa trả hàng ✅ xong,
> 4 trạng thái PAID/OPEN/VOIDED ✅ xong, **5 UI tab lập hoá đơn ✅ xong — xem mục 9**,
> 6 seed động 90 ngày).
>
> **Khối này SỞ HỮU `NewInvoicePanel`** (tab 1) và **lớp hiển thị/đọc số dùng chung** của mọi tab.
> Ba khối trước đã cố ý để lại ba việc cho khối này, có ghi chú chỉ đường trong code và trong plan:
> `VAT_pct` hiện là `"8đ"` ở tab 3 (`plan/12` mục 2.3, `plan/13` mục 2.3), ba chỗ
> `reload(); onSelect(null);` còn sót (`plan/12` mục 9.2 điểm 7), và `OPEN` giờ là trạng thái thật
> nên tab 1 phải nói đúng về nó (`plan/13` rủi ro #10).
>
> **Khối này CHẠM VÀO DATABASE, nhưng rất ít:** thêm 2 thủ tục, sửa 1 thủ tục, **không** đổi
> `sql/01_schema.sql`, **không** đổi `sql/02_seed.sql` (mục 4.6 — đây là kết luận, không phải bỏ
> sót). Vì vậy **thứ tự triển khai là bắt buộc: nạp SQL trước, biên dịch Java sau** (mục 6).
>
> **Phải đọc mục 3 trước khi gõ dòng code đầu tiên.** Khối này có bốn hợp đồng, và hợp đồng tiền mặt
> (mục 3.1) là thứ quyết định toàn bộ phần dưới của tab 1.
>
> Giao điểm với khối 4: tab 1 là nơi **duy nhất** sinh dòng `Payment`, nên `fn_invoice_amount_paid`
> và `sp_settle_invoice` phụ thuộc trực tiếp vào mục 3.1. Giao điểm với khối 6: khối 6 sinh seed
> động nên nó sẽ phải tuân đúng hợp đồng tiền mặt ở mục 3.1 (xem mục 2.3).

---

## 1. Bối Cảnh & Vấn Đề

### 1.1. Hiện trạng (đã đọc code + SQL + seed + script chạy, không phỏng đoán)

| Thành phần | Trạng thái hiện tại |
|---|---|
| `src/billing/ui/NewInvoicePanel.java` | **1087 dòng**, một lớp làm 5 việc: thông tin hoá đơn, giỏ hàng, thanh toán, mã QR, hoá đơn in ra. Lớp dài thứ hai của dự án sau `SearchPanel` |
| `NewInvoicePanel:163-319` | Ba hàm dựng giao diện. Khung "4. Thanh toan" (`:235`) là **một `FlowLayout` với 9 thành phần** |
| `NewInvoicePanel:570-633` | `addToCart()` — trái tim của app: hỏi giá theo ngày (`:588`), hỏi khuyến mãi (`:599`), tự tính tiền giảm (`:602-604`), gộp dòng theo mã vạch (`:609-618`) |
| `NewInvoicePanel:638-667` | `editQuantity()` — đổi `quantity`, **không** tính lại `discount` |
| `NewInvoicePanel:677-717` | Hai hàm `addPayment()` nạp chồng. Với tiền mặt: `detail1` = tiền khách đưa, đặt **bằng** `amount` (`:682`) |
| `NewInvoicePanel:719-773` | `handleRefund()` — "Thối tiền": trừ bớt `amount` của dòng tiền mặt cuối, giữ nguyên `detail1` |
| `NewInvoicePanel:900-970` | `save()` — đã được khối 4 sửa phần thông điệp `OPEN` (`:915-933`), phần còn lại nguyên bản |
| `NewInvoicePanel:976-1161` | `showReceiptPopup()` — **186 dòng** dựng HTML cho `JTextPane`, đọc từ giỏ hàng trong bộ nhớ |
| `NewInvoicePanel:337-351` | `loadBankConfig()` — `getClass().getClassLoader().getResourceAsStream("config.properties")` |
| `scripts/ps1/run.ps1:119` | `java -cp "out;lib\*"` — **`config.properties` không nằm trên classpath** (nó ở gốc dự án, không ở `out\`) |
| `config.properties` | 6 khoá: `db.*`, `app.main.class`, `quick.login.*`. **Không có khoá `qr.*` nào** |
| `src/billing/util/VietQRHelper.java:18-19` | `DEFAULT_BIN = "970422"`, `DEFAULT_ACCOUNT = "90902241107"` — **hằng số riêng**, không nhận tham số tài khoản |
| `src/billing/util/AppConfig.java` | Khối 2 đã dựng chỗ đọc cấu hình đúng cách (`FileInputStream("config.properties")`, lỗi không làm app chết) |
| `src/billing/ui/Ui.java:20-31` | `MONEY = DecimalFormat("#,##0")` với `DecimalFormatSymbols(Locale.US)` + `setGroupingSeparator('.')` |
| `InvoiceViewPanel:88-128` | **Bản chép thứ hai** của bộ định dạng: `CURRENCY_FORMAT`, `INTEGER_FORMAT`, `QUANTITY_FORMAT`, `QUANTITY_INPUT_FORMAT` |
| `SearchPanel:35-46` | **Bản chép thứ ba**, cộng thêm `new Locale("vi","VN")` (API deprecated — cảnh báo biên dịch duy nhất còn lại của dự án) |
| `SearchPanel:903-913` | **Bản chép thứ tư**, chỉ cho `Tax_Rate`, và nó là bản **duy nhất** biết định dạng phần trăm — nhưng dùng `double` |
| `InvoiceViewPanel:421` | `fillFormatted(tbLines, lines, new int[]{4, 5, 6, 7}, ...)` — cột 7 là `VAT_pct` |
| `InvoiceViewPanel:1210-1242` | `readQuantity()` — hợp đồng kiểu số của khối 3 (`plan/12` mục 3.2), là `private` của một lớp |
| `InvoiceViewPanel:609-610` | Câu **SQL thô** cuối cùng còn lại trong tầng UI/DAO của app billing |
| `InvoiceViewPanel:486-487, 627-628, 654-655` | Ba chỗ `reload(); onSelect(null);` — khối 3 đã ghi rõ là để lại cho khối này |
| `LookupDao:45-63` | `searchCustomersByPhonePrefix()` — câu **SQL thô** thứ hai, trả về `IdName` với `name` = `"CUS001 - Nguyen Van A - 0901234567"` |
| `LookupDao:32-38` | `customers()` — **không còn chỗ gọi nào** trong cả dự án (đã `grep`) |
| `PaymentEntry:32-37` | `methodLabel()` trả `"Tien mat"` / `"The ngan hang"` / `"Vi dien tu"` |
| `sql/04_routines.sql:415-418` | `sp_get_invoice_payments` — `CASE` trả **đúng ba chuỗi đó**. Hai bản chép, một trong Java một trong SQL |
| `NewInvoicePanel:239-240` | `new JComboBox<>(new String[]{"Tien mat", "The ngan hang", "Vi dien tu"})` — **bản chép thứ ba**, và từ đó sinh ra các chỉ số 0/1/2 rải khắp file (`:254`, `:264`, `:286-289`, `:680-692`, `:707-713`, `:780`, `:878`, `:1244`) |
| `sql/04_routines.sql:420` | `CONCAT('Khach dua: ', ca.Tendered_Amount)` — **định dạng tiền nằm trong SQL**, nên tab 3 hiện `"Khach dua: 500000.00"` |
| `sql/04_routines.sql:113-126` | `fn_next_payment_id()` — và `InvoiceDao:211-218` là **bản chép nguyên văn thân hàm đó bằng SQL thô trong Java** |
| `Product:20` | `taxRate` được nạp từ `sp_get_all_products` rồi **không dùng ở bất kỳ đâu** (đã `grep`) |
| `01_schema.sql:76` | `Tax_Rate DECIMAL(5,2) NOT NULL` — `Invoice_Line` **không có** cột thuế; `Thanh_tien = Quantity*Unit_Price - Discount` ở cả 7 chỗ tính tổng |
| `01_schema.sql:218-224` | `Cash_Payment.Tendered_Amount DECIMAL(12,2) NOT NULL` — "khách đưa bao nhiêu tiền" |
| `01_schema.sql:60-65` | `Customer.Phone` có `UNIQUE KEY uq_customer_phone` — số điện thoại là khoá tự nhiên |
| `MainWindow:102-104` | Khối 2 đã học bài `FlowLayout` xuống dòng ở cửa sổ hẹp và **ghi lại bài học đó bằng comment** |
| `MainWindow:223-229` | *"LƯU Ý CHO KHỐI SAU"*: thêm chỗ nào khoá/mở UI theo vai trò thì phải thêm vào `switchRole()` |

Nói gọn: **tab 1 ghi dữ liệu đúng cấu trúc nhưng sai con số, và nói với người dùng những điều không
đúng.** Giao dịch vẫn nguyên vẹn (khối 4 đã kiểm), thủ tục SQL vẫn đúng — cái sai nằm ở lớp giữa
bàn phím và `BigDecimal`, và ở lớp giữa `BigDecimal` và màn hình.

### 1.2. Hai mươi chín lỗi cụ thể

**Nhóm A — tiền và số lượng sai (ghi dữ liệu sai, không một thông báo nào)**

1. **Ô "So tien" ăn mất dấu phân cách nghìn.** `:253` gọi thẳng
   `new BigDecimal(tfAmount.getText().trim())`. Thu ngân gõ `500.000` (cách viết năm trăm nghìn của
   mọi người Việt) → `BigDecimal("500.000")` = **500**, `setScale(2)` → `500.00` → ghi `Payment`
   **500 đồng**. Không ngoại lệ, không cảnh báo, sai đúng **1000 lần**. Cùng lỗi ở ô số lượng
   (`:579`, `:646`) và ở cả 3 ô số của `EditLineDialog:139, 151, 163`.

2. **"Chỉnh số lượng" không tính lại tiền giảm giá.** `:638-667` chỉ gán `cartLine.quantity = newQty`.
   Lấy số thật: một sản phẩm giá `20.000` có khuyến mãi 10%. Thêm 1 → `discount = 2.000`
   (`:602-604`). Bấm *Chỉnh số lượng* thành 10 → `lineTotal() = 10*20.000 - 2.000 = 198.000`, trong
   khi đúng phải là `180.000`. **Lệch 18.000**, và con số sai đó được `sp_add_invoice_line` ghi thẳng
   vào cột `Invoice_Line.Discount` nên tab 3, báo cáo và tiền hoàn trả hàng đều sai theo.

3. **Gộp dòng theo mã vạch bỏ qua việc giá đã đổi.** `:609-618` cộng `quantity` và `discount` vào
   dòng cũ nhưng **giữ nguyên `unitPrice` của lần thêm đầu**. Đổi ô "Ngày bán" giữa hai lần thêm
   cùng một sản phẩm (chính là thao tác mà `:179-180` dạy người dùng làm) → toàn bộ số lượng bị tính
   theo giá cũ. Đây là lỗi **phá đúng tính năng mà cả app sinh ra để minh hoạ**.

4. **Hoá đơn in ra làm tròn số lượng về số nguyên.** `:1045` gọi `item.quantity.intValue()`.
   `1,5 KG` thịt ba chỉ in thành `1`. Đúng lớp lỗi #7 của khối 3 (`plan/12`), ở một file khối 3
   không được phép chạm vào.

5. **Nhánh tiền mặt của hoá đơn in ra là CODE CHẾT.** `:1076` so `"Tiền mặt".equals(paymentMethod)`
   trong khi `paymentMethod = firstPayment.methodLabel()` trả `"Tien mat"` (`PaymentEntry:33`) —
   **không dấu**. Hai chuỗi không bao giờ bằng nhau, nên **12 dòng `:1076-1087` chưa từng chạy một
   lần nào**: hoá đơn in ra chưa bao giờ hiện "Tiền khách đưa" và "Tiền thừa thối lại".

6. **Hoá đơn in ra chỉ đọc lần thanh toán đầu tiên.** `:1073` lấy `payments.get(0)`. Trả
   `200.000` tiền mặt + `300.000` quẹt thẻ → hoá đơn in ra chỉ nói "Tiền mặt", mất hẳn lần thứ hai.
   Mà "một hoá đơn có NHIỀU dòng thanh toán" là tiêu đề của chính khung số 4 (`:237`).

7. **Cột "Thanh tien" của tab 1 không trừ giảm giá, tab 3 thì trừ.** `:1171-1179` ghi rõ
   *"Thanh tien = Don gia * SL (khong tru giam gia)"*, còn `sp_get_invoice_lines:396` trả
   `Quantity*Unit_Price - Discount`. **Cùng một dòng hàng, hai con số trên hai màn hình** — và tổng
   cột "Thanh tien" của tab 1 không bằng nhãn "Tong:" ngay bên dưới nó (`:1227` dùng `lineTotal()`).

8. **"Thối tiền" thối được xuống dưới mức phải trả.** `:748-751` chỉ kiểm
   `refundAmt <= cashPayment.amount`. Nút sáng khi thu thừa đúng 1 đồng (`:1250`), nhưng rồi cho
   thối **cả dòng tiền mặt** → `paid < due` → sau khối 4 hoá đơn thành `OPEN` và bị loại khỏi báo
   cáo doanh thu, chỉ vì thu ngân thối tiền.

9. **Tiền khách đưa và tiền ghi nhận bị trộn vào một ô nhập.** `:682` đặt
   `detail1 = amount` → `Cash_Payment.Tendered_Amount` **luôn** bằng `Payment.Amount` cho tới khi ai
   đó bấm *Thối tiền*. Nghĩa là cột "khách đưa bao nhiêu tiền" của database chỉ đúng khi thu ngân
   làm thêm một thao tác tay mà **không có gì nhắc**; bỏ qua nó thì `Payment.Amount` ghi nhận cả
   tiền thối ra như thể đó là doanh thu.

**Nhóm B — khách hàng bị gắn sai vào hoá đơn**

10. **`selectedCustomer` không bị xoá khi người dùng sửa lại ô.** `:452-457` thừa nhận ngay trong
    comment: *"we'll keep last selection"*. Chọn khách A → gõ thêm một số → không khớp ai →
    `save():940` **vẫn gắn hoá đơn cho khách A**. Không dấu hiệu nào trên màn hình.

11. **Sau khi lưu, khách hàng không được reset.** `:961-964` xoá `cart` và `payments`, **không** xoá
    `selectedCustomer` lẫn ô text. Hoá đơn **tiếp theo** tự động gắn cho khách của hoá đơn trước.
    Với khách vãng lai (đa số) thì đây là gán sai dữ liệu một cách im lặng, mỗi lần lập hoá đơn.

12. **Ô mang nhãn "Khach hang" nhưng tìm theo số điện thoại, rồi tự điền tên vào.** `:523` đặt
    `tfCustomerPhone.setText(selectedCustomer.name)` với `name` = `"CUS001 - Nguyen Van A -
    0901234567"` (`LookupDao:58`). Sau khi chọn, nội dung ô **không còn là số điện thoại nào cả**,
    nên lần gõ tiếp theo tìm theo một chuỗi vô nghĩa — đây chính là nguyên nhân của lỗi #10.

13. **Cửa sổ popup không có cha, và nó giật con trỏ khỏi ô nhập ngay ký tự đầu tiên.**
    `:365` gọi `new JWindow(SwingUtilities.getWindowAncestor(this))` **trong hàm dựng**, lúc panel
    chưa thuộc cửa sổ nào → owner `null`. Rồi `:482` gọi `customerList.requestFocusInWindow()` mỗi
    lần popup hiện → gõ `0` là con trỏ nhảy sang danh sách, ký tự thứ hai không vào được ô.

14. **Mỗi ký tự gõ vào ô khách hàng là một câu SQL chạy trên EDT**, qua `DocumentListener`
    (`:390-405` → `:441`). Và `selectCustomerFromList():520` **chạy lại đúng câu đó lần nữa** chỉ để
    lấy lại object vừa hiện trên màn hình — comment `:517-518` tự gọi đó là *"not optimal"*.

15. **`LookupDao:50-51` là câu SQL thô, phá quy ước `docs/05` mục 1** (`Swing → DAO → Stored
    Procedure`). Nó cũng ghép chuỗi hiển thị **bên trong DAO** (`:58`), trong khi định dạng hiển thị
    là việc của tầng UI.

**Nhóm C — chạy trên EDT, và bấm hai lần ra hai hoá đơn**

16. **`createInvoice` chạy ngay trên EDT.** `:946`. Đó là một transaction gồm `1 + N + M + 1` lệnh
    gọi thủ tục (`InvoiceDao:81-186`). MySQL chậm hoặc tắt là **cả cửa sổ đứng hình** — đúng vấn đề
    khối 2 đã sửa cho đăng nhập (`plan/11` mục 4.2) và khối 3 đã sửa cho trả hàng.

17. **Nút *XUẤT HÓA ĐƠN* không bị khoá trong lúc lưu.** Bấm hai lần nhanh → hai lần `save()` xếp
    hàng trên EDT → **hai hoá đơn** cho cùng một giỏ hàng.

18. **`nextInvoiceId()` chạy trên một Connection KHÁC, trước transaction.** `:935` →
    `InvoiceDao:192-208` mở kết nối riêng. Hai cửa sổ app (chuyện rất dễ xảy ra khi demo, `run.ps1`
    không cấm) cùng lấy `INV-0017` → cái thứ hai chết vì trùng khoá chính, và thu ngân mất giỏ hàng.

19. **`loadLookups()` chạy trên EDT trong hàm dựng.** `:117` → `:324-332`: ba câu `SELECT` trước khi
    cửa sổ kịp vẽ. Lỗi thì `Ui.error` hiện trên một panel chưa hiển thị.

20. **NPE nếu `loadLookups()` thất bại.** `:936-937` lấy `cbCounter.getSelectedItem()` rồi `:950`
    đọc `counter.id` mà không kiểm `null` → `NullPointerException` trần, hiện qua `Ui.error` kèm
    stack trace thay vì một câu nói được chuyện gì xảy ra.

**Nhóm D — hiển thị và định dạng (chạm cả tab 2 và tab 3)**

21. **`VAT_pct` ở tab 3 hiện là `"8đ"`.** `InvoiceViewPanel:421` xếp cột 7 vào nhóm tiền tệ. Việc
    khối 3 (`plan/12` mục 2.3) và khối 4 (`plan/13` mục 2.3) đều đã giao lại cho khối này.

22. **Bốn bản chép của bộ định dạng số**: `Ui:20-25` (dùng `Locale.US`), `InvoiceViewPanel:88-97`,
    `SearchPanel:39-46`, `SearchPanel:903-913` (phần trăm, dùng `double`). Thêm một kiểu cột là phải
    sửa bốn chỗ, mà **không chỗ nào báo** nếu quên — đúng loại lỗi mà khối 3 trị bằng
    `fn_return_refund` và khối 4 trị bằng `InvoiceStatus`.

23. **`readQuantity` — hợp đồng kiểu số của khối 3 — bị khoá trong một lớp.**
    `InvoiceViewPanel:1210-1242` là `private`. Nên tab 1 và `EditLineDialog` **không dùng được**, và
    cả hai chỗ đó đang nhận `1.2345` để MySQL âm thầm làm tròn thành `1.235` — đúng điều quy tắc 1
    của `plan/12` mục 3.2 cấm.

24. **Khung "4. Thanh toan" là một `FlowLayout` 9 thành phần** (`:235`). Thu nhỏ cửa sổ là hàng nút
    xuống dòng và khung cao gấp đôi, ăn hết chỗ của bảng giỏ hàng — **đúng cái bẫy mà
    `MainWindow:102-104` đã gặp, đã sửa, và đã ghi lại bằng comment**.

25. **Bảng thanh toán cao 90 pixel** (`:294`) với `rowHeight = 24` (`Ui:38`) → xem được **2 dòng**,
    trong khi khung mang tên *"một hoá đơn có thể có NHIỀU dòng thanh toán"*.

26. **Nhãn lẫn có dấu / không dấu trong cùng một hàng nút**: `"Thối tiền"` (`:86`) cạnh
    `"Tra het bang tien mat"` (`:267`); `"Chỉnh số lượng"` (`:83`) cạnh `"Xoa dong dang chon"`
    (`:210`). Các hộp thoại cũng vậy (`:575` có dấu, `:581` không).

**Nhóm E — code chết và những điều app nói không đúng**

27. **Cấu hình mã QR không bao giờ được đọc, và dù đọc được cũng không được dùng.** Ba lỗi xếp lên
    nhau: (a) `getResourceAsStream("config.properties")` với classpath `out;lib\*` (`run.ps1:119`)
    trả `null` → `props.load(null)` ném `NullPointerException` → `catch` in một dòng ra `System.err`
    (`:347-350`); (b) `config.properties` **không có** ba khoá `qr.bank.id`, `qr.account.no`,
    `qr.account.name`; (c) ba field `bankId`/`accountNo`/`accountName` (`:100-102`) **không được
    truyền cho `VietQRHelper`** — lớp đó dùng hằng số riêng (`VietQRHelper:18-19`). Kết quả:
    **mọi mã QR đều trỏ về một số tài khoản ghi cứng trong mã nguồn**, và không có cách nào đổi mà
    không biên dịch lại.

28. **Nút "In hóa đơn" không in gì.** `:1127-1133` chỉ hiện *"Đã gửi lệnh in hóa đơn."*.

29. **Code chết và dữ liệu chết**: field `ignoreTextChanges` khai báo rồi không dùng (`:97`); biến
    `receiptShown` gán rồi không đọc (`:957`); **8 import không dùng** (`ImageIO`,
    `FontRenderContext`, `Rectangle2D`, `IOException`, `URL`, `HashMap`, `Map`,
    `StandardCharsets` — thành 9 sau khi bỏ field chết ở trên, vì `AtomicBoolean` chỉ còn nó dùng);
    `LookupDao.customers()` không còn chỗ gọi;
    `Product.taxRate` nạp rồi không dùng; `InvoiceDao:211-218` chép lại `fn_next_payment_id`;
    `new JDialog((Frame) null, ...)` ở `:836` và `:980` (hộp thoại không có cha); `save()` gọi
    `LocalTime.now()` **hai lần** (`:949`, `:957`) nên giờ trên hoá đơn in ra có thể lệch một giây
    so với giờ đã lưu; tên cửa hàng ghi cứng `"SIÊU THỊ MINI MART"` (`:996`) trong khi
    `sp_get_invoice_header` trả về `Shop` và `Dia_chi` thật.

---

## 2. Mục Tiêu & Phạm Vi

### 2.1. Mục tiêu

1. **Con số gõ vào được hiểu đúng:** `500.000` là năm trăm nghìn, `1,5` là một phẩy năm, và cái gì
   không hiểu được thì **từ chối kèm lý do**, không bao giờ âm thầm đoán (mục 3.3).
2. **Tiền khách đưa, tiền ghi nhận và tiền thối là ba số khác nhau** — mỗi số có đúng một chỗ ở,
   và người dùng thấy cả ba (mục 3.1).
3. **Tiền giảm giá luôn khớp số lượng**: đổi số lượng là tính lại, bằng một công thức duy nhất
   (mục 3.2).
4. **Khách hàng trên hoá đơn đúng là khách đang hiện trên màn hình**, không phải khách của hoá đơn
   trước (mục 3.5).
5. **Hoá đơn in ra đọc từ database**, nên nó không thể nói khác tab 3 — kể cả về trạng thái
   `OPEN`/`PAID` của khối 4.
6. **Mọi định dạng số chỉ còn một bản chép** (`Ui`), và nhờ vậy `VAT_pct` tự đúng là `8%`.
7. **Không thao tác database nào chạy trên EDT**, và không bấm hai lần ra hai hoá đơn.
8. **App không nói điều không đúng**: nút in thì in thật, cấu hình QR thì đọc được và có tác dụng,
   tên cửa hàng lấy từ bảng `Shop`.

### 2.2. Nguyên tắc bất di bất dịch

> **Con số trên màn hình phải là con số trong database. Mỗi phép đọc số và mỗi phép định dạng số
> chỉ tồn tại ở MỘT chỗ.**

Khối 3 có nguyên tắc "công thức tiền hoàn chỉ ở một chỗ, và chỗ đó là SQL". Khối 5 là mặt còn lại
của cùng một đồng xu: **lớp giữa bàn phím và `BigDecimal`, và lớp giữa `BigDecimal` và màn hình
cũng là code, cũng sai được, và cũng phải chỉ có một bản.** Lỗi #1, #5, #7, #21, #22 đều sinh ra
vì có nhiều bản chép.

Hệ quả cụ thể:

- **Không `new BigDecimal(textField.getText())` ở bất kỳ đâu nữa.** Đọc số từ bàn phím đi qua
  `Ui.readMoney` / `Ui.readQuantity` (mục 3.3), hai hàm đó chịu trách nhiệm từ chối và giải thích.
- **Không `DecimalFormat` mới ở bất kỳ file nào nữa.** Hiển thị số đi qua `Ui.money` / `Ui.qty` /
  `Ui.percent` (mục 3.4).
- **Không so sánh bằng nhãn hiển thị.** Lỗi #5 là án lệ: so `"Tiền mặt"` với `"Tien mat"`. Rẽ nhánh
  theo mã (`PayMethod.CASH`), không theo chữ hiện trên màn hình (mục 3.6).
- Giữ nguyên quy ước ba khối trước: **UI kiểm để thân thiện, SQL kiểm để đúng, SQL là chốt cuối**
  (`plan/12` mục 3.4); **khoá ở UI, không khoá ở DAO** (`InvoiceViewPanel:37-40`); **Additive
  First** (`plan/06` mục 1.2) — thêm file mới thay vì phình file cũ, sửa thủ tục cũ thì sửa tối
  thiểu và giữ nguyên thứ tự tham số (`docs/05` Quy tắc 2).

### 2.3. Ngoài phạm vi khối này (và vì sao)

| Việc | Vì sao không làm ở khối này |
|---|---|
| Thêm cột thuế vào `Invoice_Line`, tính VAT vào tổng | Đổi **định nghĩa tổng tiền** ở 10 chỗ trong `04_routines.sql` (`:152, 210, 335, 368-370, 396, 478, 528, 579, 602`) và 4 chỗ trong `03_queries.sql` (`:36-38, 62, 122, 146`), rồi làm lệch mọi hoá đơn đã có. Khối này chỉ **hiển thị đúng** `Tax_Rate` là phần trăm và nói rõ giá đã gồm VAT (mục 3.4) |
| Ghi phiếu hoàn tiền (dòng `Payment` âm) | Khối 3 và khối 4 đều đã đẩy sang "khối thanh toán". Vẫn đúng: nó phá phép kiểm `paid >= due` của `sp_settle_invoice` |
| Quét mã vạch bằng máy quét, ô tìm sản phẩm theo tên | Tính năng mới, không phải lỗi. `cbProduct` hiện nạp cả bảng `Product` vào một `JComboBox` — chấp nhận được với seed 20 sản phẩm, thành vấn đề thật ở quy mô thật → khối riêng |
| Tồn kho | Lược đồ không có bảng kho (`plan/12` mục 2.3 đã kết luận) |
| Lập hoá đơn nhiều bước (tạo `OPEN` trước, thêm hàng sau) | Khối 4 đã dọn đường (`plan/13` rủi ro #10) nhưng đây là **đổi quy trình nghiệp vụ**, không phải sửa giao diện. Khối này giữ đúng một lần `commit` cho một hoá đơn |
| Cổng thanh toán QR thật (đối soát tự động) | Nút *Xác nhận đã nhận tiền* vẫn là người bấm. Khối này chỉ làm cho mã QR **trỏ đúng tài khoản cấu hình** và nói thật rằng việc đối soát là thủ công |
| Làm lại tab 2 (`SearchPanel`) | 931 dòng, là một trình xem bảng tổng quát — SQL thô ở đó là **có chủ ý**. Khối này chỉ lấy đi 2 bản chép định dạng của nó và sửa cảnh báo `new Locale` |
| Khoá quyền cho tab 1 | **Thu ngân PHẢI lập được hoá đơn** — đó là việc của thu ngân. Tab 1 không có nút nào phụ thuộc vai trò, nên không phải thêm gì vào `MainWindow.switchRole()` (`MainWindow:223-229`) |
| Sửa `sp_get_invoice_returns`, báo cáo, trạng thái | Của khối 3 và khối 4. Khối này không chạm |
| Đổi `NewInvoicePanel` sang tiếng Việt có dấu toàn bộ | **Đã xét và loại** — mục 3.6 |

---

## 3. Bốn Hợp Đồng (quan trọng nhất)

Khối 1 có hợp đồng định dạng file, khối 2 hợp đồng cấu hình, khối 3 hợp đồng tính tiền, khối 4 hợp
đồng vòng đời. Khối này có **hợp đồng tiền mặt, hợp đồng giảm giá, hợp đồng đọc/hiện số, và hợp
đồng khách hàng**. Sửa một bên phải sửa tài liệu này.

### 3.1. Hợp đồng tiền mặt: ba con số, ba chỗ ở

```
Gọi  due        = tổng phải trả của hoá đơn        = Σ lineTotal()        (CartLine:34-39)
     recorded   = Payment.Amount                   -- tiền GHI NHẬN, vào fn_invoice_amount_paid
     tendered   = Cash_Payment.Tendered_Amount      -- tiền khách ĐƯA
     change     = tendered - recorded               -- tiền THỐI, KHÔNG lưu ở cột nào

Tiền mặt:  thu ngân gõ TENDERED (tiền khách đưa), app tự tính:
               recorded = min(tendered, due - đã ghi nhận trước đó)
               change   = tendered - recorded
Thẻ / ví:  thu ngân gõ RECORDED, và tendered không tồn tại (NULL)
               recorded <= due - đã ghi nhận trước đó   -- chốt cứng, xem bên dưới
```

| Con số | Ở đâu trong database | Ai nhập | Vào báo cáo doanh thu? |
|---|---|---|---|
| `tendered` | `Cash_Payment.Tendered_Amount` (`NOT NULL`, chỉ bảng tiền mặt) | thu ngân gõ | Không |
| `recorded` | `Payment.Amount` | **app tính**, không ai gõ (với tiền mặt) | Có — `fn_invoice_amount_paid` |
| `change` | **không có cột nào** — là giá trị dẫn xuất | — | Không |

Bốn quyết định trong một công thức ngắn:

1. **`recorded = min(tendered, còn thiếu)`, nên `paid > due` không bao giờ xảy ra từ tab 1.**
   Nút *Thối tiền* (lỗi #8) hết lý do tồn tại: nó chỉ sinh ra vì ô nhập trộn `tendered` với
   `recorded`, buộc người dùng tự sửa lại sau. Bỏ nút, bỏ `handleRefund()`, bỏ
   `updateRefundButtonState()`, bỏ field `selectedPaymentMethodIndex` và `tfAmountRef`.
   Phép kiểm `>=` trong `sp_settle_invoice` (`04_routines.sql:759`) **giữ nguyên** — nó thuộc khối 4
   và vẫn đúng; chỉ là tab 1 không còn sinh ra trường hợp `>`.
2. **Thẻ và ví điện tử bị TỪ CHỐI khi quá số còn thiếu**, không phải hỏi lại. Lý do: quẹt thẻ nhiều
   hơn hoá đơn thì **không có đường trả lại** (không có cột tiền thối cho thẻ, và phiếu hoàn tiền
   nằm ngoài phạm vi — mục 2.3). Tiền mặt thì thừa bao nhiêu cũng được vì thối được.
3. **`change` không được lưu.** Nó suy ra được từ hai cột đã có, đúng nguyên tắc mục 2.2 và đúng kết
   luận của `plan/13` mục 3.3 phương án C ("giá trị dẫn xuất không lưu trong bảng").
4. **Không có câu `SUM(Payment.Amount)` nào trong `03_queries.sql` hay các báo cáo** (đã kiểm: 7
   chỗ tính tổng đều đi từ `Invoice_Line`). Nên đổi ý nghĩa của `Payment.Amount` cho đúng
   **không làm lệch một báo cáo nào** — chỉ `fn_invoice_amount_paid` đọc nó, và đó chính là chỗ
   cần nó đúng.

**Bất biến mà seed hiện có đã thoả** (kiểm bằng kịch bản #4): mọi dòng `Cash_Payment` trong
`02_seed.sql` có `Tendered_Amount >= Payment.Amount`. Sau khối này app cũng chỉ sinh ra dữ liệu như
vậy. **Khối 6 (seed động) phải giữ đúng bất biến này** — ghi ở đây để khối 6 không phải tìm lại.

### 3.2. Hợp đồng giảm giá: `discount` là hàm của (sản phẩm, ngày, số lượng)

```
     pct      = Applies_To.Discount_Percent của khuyến mãi tốt nhất tại NGÀY BÁN  (sp_get_active_promo)
     discount = ROUND( quantity * unitPrice * pct / 100 , 2 )        -- số tiền giảm của CẢ DÒNG
     lineTotal= quantity * unitPrice - discount                      -- CartLine:34-39
```

- **`discount` phụ thuộc `quantity`.** Nên **mọi** chỗ đổi `quantity` phải tính lại `discount`.
  Đây là nội dung của lỗi #2, và cách sửa là: chỉ có **một** hàm dựng/đổi một dòng giỏ hàng
  (`priceLine(product, qty, date)` — mục 5.6b), và `addToCart` lẫn `editQuantity` đều gọi nó.
- **Gộp dòng chỉ khi đơn giá bằng nhau.** Nếu giá tra được tại ngày bán **khác** `unitPrice` của
  dòng đã có (vì người dùng đã đổi ô "Ngày bán") thì **thêm dòng mới**, không gộp. Lược đồ cho phép:
  khoá chính `Invoice_Line` là `(Invoice_ID, Line_Number)`, không cấm trùng `Barcode`
  (`01_schema.sql:168`), và khối 3 đã sửa hộp thoại trả hàng để xử lý đúng trường hợp này
  (`plan/12` lỗi #9). Hai đơn giá khác nhau **là** hai dòng hoá đơn khác nhau.
- **Công thức này nằm trong Java, và đó KHÔNG phải ngoại lệ của nguyên tắc khối 3.** Nguyên tắc là
  *"chỉ một bản chép"*; "và chỗ đó là SQL" đúng cho tiền hoàn vì **SQL cũng tính tiền hoàn** (hai
  thủ tục đọc `sp_get_invoice_returns` và `sp_report_big_returns` đều cần nó). `Invoice_Line.Discount`
  thì khác: nó là **giá trị đầu vào được lưu**, không chỗ nào trong SQL tính lại nó. Đưa công thức
  xuống SQL (`fn_line_discount`) sẽ tạo ra **bản chép thứ hai của quy tắc chọn khuyến mãi tốt nhất**
  (`sp_get_active_promo:302-310` vẫn phải tự chọn để lấy `Rule_Description` cho dòng gợi ý) — tức
  là đổi một bản chép thành hai. **Đã xét và loại.** Bù lại: công thức phải nằm trong đúng một hàm
  `lineDiscount(...)`, có javadoc trỏ về mục này.

### 3.3. Hợp đồng đọc số từ bàn phím: hai quy tắc, và vì sao phải là hai

| | **Tiền** (`Ui.readMoney`) | **Số lượng** (`Ui.readQuantity`) |
|---|---|---|
| Dấu `.` | **tách nghìn**, phải đủ 3 chữ số theo sau | **dấu thập phân** |
| Dấu `,` | dấu thập phân, tối đa 2 chữ số | dấu thập phân |
| Khoảng trắng, chữ `đ` ở cuối | bỏ qua | không nhận |
| Chấp nhận | `500000`, `500.000`, `1.234.567`, `1.234,50`, `0,5`, `35 000 đ` | `2`, `1.5`, `1,5`, `0.25` |
| Từ chối kèm lý do | `500.00` (dấu `.` không đủ 3 số), `1.2.3`, `1,234`, số âm, chuỗi rỗng | quá 3 chữ số thập phân, hai dấu thập phân, `<= 0`, vượt `max` |
| Kiểu đích | `DECIMAL(12,2)` | `DECIMAL(10,3)` |

Vì sao **không** dùng một quy tắc cho cả hai: số lượng nhỏ và có phần lẻ (`1,5 KG` — dữ liệu thật ở
`02_seed.sql:151`), tiền thì lớn và có dấu tách nghìn. Một quy tắc chung buộc phải chọn một nghĩa
duy nhất cho dấu `.`, và nghĩa nào cũng làm sai một nửa các ô nhập. Hai quy tắc là **cố ý**, nên
chúng phải được nói ra thành lời:

- Mỗi ô nhập có **tooltip nêu đúng quy tắc của nó** và một ví dụ.
- Ô tiền có **nhãn vọng lại** ngay bên cạnh, cập nhật theo từng ký tự: gõ `500.000` thì hiện
  `= 500.000đ`, gõ `500.00` thì hiện `= ?` màu đỏ. Người dùng thấy app hiểu gì **trước khi** bấm
  nút. Nhãn này chỉ tính trong bộ nhớ, không gọi database (khác lỗi #14).
- `readMoney` / `readQuantity` trả `null` **sau khi đã hiện thông báo** — đúng khuôn
  `InvoiceViewPanel.readQuantity` của khối 3, để chỗ gọi chỉ cần `if (v == null) return;`.
- **So sánh bằng `compareTo`, không `equals`**: `BigDecimal("1.5").equals(BigDecimal("1.50"))` là
  `false` (khác scale) — cái bẫy mà `plan/12` mục 3.2 đã ghi.

### 3.4. Hợp đồng hiện số: ba hàm, một bản chép

| Hàm | Mẫu | Ví dụ | Dùng cho |
|---|---|---|---|
| `Ui.money(BigDecimal)` | `#,##0` + `đ`, dấu nghìn `.` | `135.100đ` | mọi cột tiền; `null` → `0đ` |
| `Ui.qty(Object)` | `#,##0.###`, dấu thập phân `,` | `1,5` / `2` | `Quantity`, `Return_Quantity` |
| `Ui.percent(Object)` | `#0.##` + `%`, không dấu nghìn | `8%` / `7,5%` | `Tax_Rate` / `VAT_pct` / `Phan_tram_tra` |
| `Ui.qtyInput(BigDecimal)` | `0.###`, **không** dấu nghìn | `1.5` | giá trị điền sẵn vào ô số lượng (`plan/12` mục 9.2 điểm 3) |

- Cả ba dùng **một** `DecimalFormatSymbols` của `vi-VN` (`Locale.forLanguageTag("vi-VN")` —
  không `new Locale("vi","VN")`, API đó đã deprecated và là cảnh báo biên dịch duy nhất của dự án).
  `Ui.money` hiện dùng `Locale.US` rồi tự đổi dấu nghìn (`Ui:22-24`): cho ra cùng kết quả, nhưng
  nói sai ý định.
- **`InvoiceViewPanel` và `SearchPanel` xoá bộ format của mình và gọi `Ui`.** Bốn bản chép còn một.
- **`VAT_pct` thuộc nhóm phần trăm, không thuộc nhóm tiền tệ.** `fillFormatted` nhận thêm tham số
  `percentCols` (mục 5.9a). Đây là lỗi #21 và cách sửa nó.
- **Giá trong `Price_History` đã gồm VAT.** `Tax_Rate` chỉ là thông tin: không chỗ nào trong 7 công
  thức tính tổng dùng tới nó. Nên cột VAT ở tab 1 và tab 3 mang nhãn `VAT %` kèm tooltip
  *"thuế suất của sản phẩm; đơn giá đã gồm VAT"* — một dòng chữ rẻ hơn nhiều so với việc để người
  đọc tự suy ra sai.

### 3.5. Hợp đồng khách hàng: ô đó là SỐ ĐIỆN THOẠI

```
Bất biến:  selectedCustomer != null   ⟺   tfPhone.getText().trim() == selectedCustomer.phone
```

- **Mọi thay đổi nội dung ô làm vỡ đẳng thức này đều xoá `selectedCustomer` về `null`** (= khách
  vãng lai). Đây là câu trả lời cho lỗi #10 và #11, và nó **kiểm được bằng mắt**: cạnh ô luôn có
  một nhãn hiện `(khach vang lai)` hoặc `→ Nguyen Van A (CUS001)`.
- **Chọn xong thì ô chứa SỐ ĐIỆN THOẠI**, không chứa `"CUS001 - Nguyen Van A - 0901234567"`. Tên
  khách đi vào nhãn bên cạnh. Nhờ vậy gõ thêm một ký tự vẫn là một lần tìm hợp lệ (lỗi #12).
- **Chỉ tìm khi nội dung ô toàn chữ số và dài ≥ 3.** `Customer.Phone` là chuỗi số có
  `UNIQUE KEY` (`01_schema.sql:62-64`); lọc ở UI vừa bỏ được các lần gọi vô nghĩa (lỗi #14) vừa
  khiến `p_prefix` không thể chứa `%` hay `_` làm rộng điều kiện `LIKE`.
- **Tìm kiếm chạy ngoài EDT**, có **hoãn 250ms** (`javax.swing.Timer` khởi động lại mỗi lần gõ):
  gõ 10 số là **1 câu SQL**, không phải 10.
- **Sau khi lưu hoá đơn, ô và `selectedCustomer` được xoá** cùng với giỏ hàng.

### 3.6. Hợp đồng tên và nhãn

| Routine mới / sửa | Tham số | Trả về |
|---|---|---|
| `sp_search_customers_by_phone` | `p_prefix` | 3 cột `Ma_KH, Ten, Dien_thoai`, `ORDER BY Phone`, `LIMIT 20` |
| `sp_get_invoice_line_edit_fields` | `p_invoice_id`, `p_line_number` | 4 cột `Barcode, Quantity, Unit_Price, Discount` — **đúng thứ tự** câu SQL thô nó thay (`InvoiceViewPanel:609`) |
| `sp_get_invoice_payments` (sửa) | `p_invoice_id` | **5** cột: `Ma_TT, So_tien, Phuong_thuc, Chi_tiet, Tien_khach_dua` |

**Bộ nhãn phương thức thanh toán chỉ còn một bản chép**, trong `enum PayMethod` mới — đúng khuôn
`InvoiceStatus` của khối 4. Hiện có **ba** bản: `PaymentEntry:33-35`, `NewInvoicePanel:239-240`,
`04_routines.sql:415-418`. Bản trong SQL **giữ nguyên** (nó là cột `Phuong_thuc` mà tab 3 và tab 2
hiển thị); hai bản Java gộp về `PayMethod`, và `PayMethod.label()` phải trả **đúng ba chuỗi mà SQL
trả** để một hoá đơn không hiện hai cách viết cho cùng một phương thức.

**Nhãn tiếng Việt có dấu trong `NewInvoicePanel`: đã xét và loại.**

| | Phương án | Kết luận |
|---|---|---|
| **A** | **Chuẩn hoá tab 1 về KHÔNG dấu**, như tab 3, tab 4 và 4 hộp thoại | **ĐÃ CHỌN.** Nhãn phương thức thanh toán là chuỗi do `CASE` trong SQL sinh ra (`:415-418`) và hiện ở tab 2, tab 3; đổi chúng là đổi một hợp đồng cột. Một màn hình hiện `"Tiền mặt"` trong ô chọn và `"Tien mat"` ở bảng ngay dưới **tệ hơn** là hiện `"Tien mat"` ở cả hai. Diff nhỏ, không chạm SQL |
| B | Có dấu toàn bộ tab 1 + sửa `CASE` trong SQL + `PaymentEntry.methodLabel()` | Loại: kéo theo `sp_get_invoice_payments`, `InvoiceViewPanel`, bộ lọc của `SearchPanel`, và phải kiểm lại cả ba — rủi ro không tương xứng với một thay đổi thuần thẩm mỹ |
| C | Để lẫn như hiện tại | Loại: lỗi #26 là lỗi thật, chỉ là lỗi nhỏ |

Tab 2 (`SearchPanel`) **giữ tiếng Việt có dấu** như `plan/13` mục 5.6 đã chốt. Quy ước thành hình:
**một file dùng một lối viết**, và chuỗi do SQL sinh ra thì giữ nguyên dạng SQL. Ghi vào `docs/05`.

---

## 4. Thiết Kế SQL

### 4.1. Đừng quên khối `DROP` ở đầu `04_routines.sql`

File tự xoá mọi thứ trước khi tạo lại (`:17-58`) để chạy lại được nhiều lần. Thiếu một dòng `DROP`
là lần chạy thứ hai báo *"already exists"*. Thêm vào đúng nhóm:

```sql
DROP PROCEDURE IF EXISTS sp_search_customers_by_phone;      -- vao nhom dong 26-30
DROP PROCEDURE IF EXISTS sp_get_invoice_line_edit_fields;   -- vao nhom dong 32-38
```

### 4.2. Mới: `sp_search_customers_by_phone`

Đặt vào **PHẦN 2**, ngay sau `sp_lookup_customers` (khoảng dòng 280):

```sql
-- ---------------------------------------------------------------------
-- 2.4 sp_search_customers_by_phone: Tim khach hang theo TIEN TO so dien thoai
--     Thay cau SQL tho cuoi cung con lai trong tang DAO (LookupDao:50-51),
--     giu dung quy uoc Swing -> DAO -> Stored Procedure (docs/05 muc 1).
--
--     Tra ve BA COT RIENG, khong CONCAT san chuoi hien thi: ghep chuoi de
--     hien tren man hinh la viec cua tang UI. Ban cu ghep trong DAO nen
--     chinh cai chuoi ghep do bi do tro lai o nhap (plan/14 loi #12).
--
--     LIMIT 20: danh sach goi y khong ai doc het 500 dong, va popup cao
--     toi da 200px (NewInvoicePanel).
--
--     p_prefix duoc chac chan la chuoi so: phia Java chi goi khi noi dung
--     o nhap toan chu so (plan/14 muc 3.5), nen khong co ky tu % hay _
--     lot vao lam rong dieu kien LIKE.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_search_customers_by_phone(
    IN p_prefix VARCHAR(20)
)
BEGIN
    SELECT  Customer_ID AS Ma_KH,
            Name        AS Ten,
            Phone       AS Dien_thoai
    FROM    Customer
    WHERE   Phone LIKE CONCAT(p_prefix, '%')
    ORDER BY Phone
    LIMIT   20;
END$$
```

Các thủ tục sau nó trong PHẦN 2 **dời số thứ tự comment** (`sp_get_all_products` 2.4 → 2.5,
`sp_get_active_promo` 2.5 → 2.6). Chỉ là comment, nhưng `docs/05` mục 6 trích theo nhóm nên giữ
cho khớp.

### 4.3. Mới: `sp_get_invoice_line_edit_fields`

Đặt vào **PHẦN 3**, sau `sp_get_invoice_edit_fields` (khoảng dòng 515):

```sql
-- ---------------------------------------------------------------------
-- 3.8 sp_get_invoice_line_edit_fields: 4 truong THO cua MOT dong hang,
--     dung cho hop thoai sua dong (EditLineDialog).
--     Thay cau SQL tho o InvoiceViewPanel:609 -- cau cuoi cung cua tang UI
--     (khoi 4 da xoa cau o :447, con dung cau nay).
--     Thu tu 4 cot la hop dong voi Java: cho goi doc theo chi so r[0]..r[3].
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_get_invoice_line_edit_fields(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT
)
BEGIN
    SELECT  l.Barcode,
            l.Quantity,
            l.Unit_Price,
            l.Discount
    FROM    Invoice_Line l
    WHERE   l.Invoice_ID  = p_invoice_id
      AND   l.Line_Number = p_line_number;
END$$
```

### 4.4. Sửa `sp_get_invoice_payments` — tách số ra khỏi chuỗi

`:408-430`. Thêm cột thứ 5 và bỏ số tiền khỏi `Chi_tiet`:

```sql
-- ---------------------------------------------------------------------
-- 3.4 sp_get_invoice_payments: Chi tiết các đợt thanh toán của hóa đơn
--     Tien_khach_dua la MOT COT SO RIENG (DECIMAL), khong CONCAT vao
--     Chi_tiet nua: ban cu cho ra "Khach dua: 500000.00" -- dinh dang tien
--     nam trong SQL thi Java khong dinh dang lai duoc (plan/14 loi #22).
--     NULL cho the va vi dien tu: hai phuong thuc do khong co tien khach dua.
--
--     HOP DONG COT: 5 cot, va cho goi phai sua theo (plan/14 muc 5.9b):
--       0 Ma_TT | 1 So_tien | 2 Phuong_thuc | 3 Chi_tiet | 4 Tien_khach_dua
-- ---------------------------------------------------------------------
    SELECT  p.Payment_ID            AS Ma_TT,
            p.Amount                AS So_tien,
            CASE
              WHEN ca.Payment_ID IS NOT NULL THEN 'Tien mat'
              WHEN cd.Payment_ID IS NOT NULL THEN 'The ngan hang'
              WHEN ew.Payment_ID IS NOT NULL THEN 'Vi dien tu'
              ELSE '(khong ro)' END AS Phuong_thuc,
            CASE
              WHEN cd.Payment_ID IS NOT NULL THEN CONCAT(cd.Card_number, ' / ', cd.Auth_code)
              WHEN ew.Payment_ID IS NOT NULL THEN CONCAT(ew.Wallet_provider, ' / ', ew.Transaction_Ref)
              ELSE '' END           AS Chi_tiet,
            ca.Tendered_Amount      AS Tien_khach_dua
    FROM    Payment p
    ...
```

Ba chuỗi trong `CASE` đầu **giữ nguyên từng chữ** — mục 3.6 đã chốt, và `PayMethod.label()` phải
khớp với chúng.

### 4.5. Dùng lại `fn_next_payment_id`, xoá bản chép trong Java

`InvoiceDao:211-218` là thân của `fn_next_payment_id` (`04_routines.sql:118-125`) chép sang Java
bằng SQL thô. Xoá hàm `nextPaymentSeq` và gọi hàm SQL **trên cùng Connection của transaction**,
một lần cho mỗi dòng thanh toán:

```java
            String fnPayId = "{? = call fn_next_payment_id()}";
            try (CallableStatement csId  = conn.prepareCall(fnPayId);
                 CallableStatement cs    = conn.prepareCall(spPayment)) {
                for (PaymentEntry pe : payments) {
                    // Goi TRONG transaction: dong Payment vua INSERT o vong truoc
                    // da nhin thay duoc tren cung Connection, nen ma ke tiep dung.
                    csId.registerOutParameter(1, Types.VARCHAR);
                    csId.execute();
                    String payId = csId.getString(1);
                    ...
```

Vì sao không giữ `nextPaymentSeq` cho nhanh: quy tắc sinh mã là **một quy tắc nghiệp vụ**, và nó
đang có hai bản chép ở hai ngôn ngữ. `docs/05:178` thậm chí đã khai sai là *"SQL & Java"* trong khi
Java **không** gọi nó. Đổi định dạng mã (`PAY-%04d` → 5 chữ số) mà sửa một bên là hỏng ngay.

### 4.6. Không đổi gì khác — và vì sao

| File / routine | Vì sao không đổi |
|---|---|
| `sql/01_schema.sql` | Mục 3.1 không cần cột mới: `change` là giá trị dẫn xuất, `tendered` đã có cột |
| `sql/02_seed.sql` | Bất biến `Tendered_Amount >= Amount` đã thoả (mục 3.1). Seed là việc của khối 6 |
| `sp_add_payment` | **Giữ nguyên 6 tham số**, kể cả `CAST(p_detail1 AS DECIMAL(12,2))` ở `:698`. Java vẫn truyền tiền khách đưa qua `detail1` dạng chuỗi; cái đổi là **Java lấy số đó từ đâu** (mục 5.4b). Đổi chữ ký thủ tục là phá `docs/05` Quy tắc 2 mà không đổi được gì |
| `sp_create_invoice_header`, `sp_settle_invoice`, `sp_void_invoice` | Khối 4 sở hữu. Mục 3.1 điểm 1 đã nói rõ vì sao `>=` vẫn đúng |
| `sp_get_active_promo` | Mục 3.2 đã kết luận: không đưa công thức giảm giá xuống SQL |
| `sp_lookup_customers` | **Giữ thủ tục, xoá chỗ gọi Java.** Thủ tục 6 dòng, vô hại, và còn giá trị minh hoạ; cái gây nhầm là `LookupDao.customers()` vì nó làm người đọc tin rằng có màn hình đang dùng. `docs/05:185` ghi lại là *"không còn chỗ gọi trong Java — thay bằng `sp_search_customers_by_phone`"* |
| `sp_get_invoice_lines` | Cột `VAT_pct` **không sai** — chỗ sai là Java xếp nó vào nhóm tiền tệ (lỗi #21) |

---

## 5. Thiết Kế Java

### 5.1. Bảng tổng hợp

| File | Trạng thái | Việc |
|---|---|---|
| `sql/04_routines.sql` | sửa | 2 `DROP`, 2 thủ tục mới, sửa `sp_get_invoice_payments` (mục 4.1–4.4) |
| `src/billing/ui/Ui.java` | sửa | `money` dùng `vi-VN`; thêm `qty`, `percent`, `qtyInput`, `readMoney`, `readQuantity`, `dec` (mục 5.2, 5.3) |
| `src/billing/model/PayMethod.java` | **mới** | Bản chép duy nhất của bộ phương thức thanh toán trong Java (mục 5.5a) |
| `src/billing/model/CustomerRef.java` | **mới** | `id` + `name` + `phone` — thay chuỗi ghép của `IdName` (mục 5.5b) |
| `src/billing/model/PaymentEntry.java` | sửa | Thêm `tendered` (`BigDecimal`, `null` cho thẻ/ví); `methodLabel()` gọi `PayMethod` (mục 5.5a) |
| `src/billing/dao/LookupDao.java` | sửa | `searchCustomersByPhone()` qua thủ tục, trả `CustomerRef`; xoá `customers()` (mục 5.5b) |
| `src/billing/dao/InvoiceDao.java` | sửa | Xoá `nextPaymentSeq`, dùng `fn_next_payment_id`; `createInvoice` lấy mã hoá đơn **trong** transaction; thêm `invoiceLineEditFields()` (mục 5.4) |
| `src/billing/ui/NewInvoicePanel.java` | **viết lại phần lớn** | 1087 → khoảng 520 dòng: bố cục mới, hợp đồng tiền mặt, tính lại giảm giá, lưu ngoài EDT (mục 5.6) |
| `src/billing/ui/ReceiptDialog.java` | **mới** | Hoá đơn in ra, **đọc từ database**, in thật (mục 5.7) |
| `src/billing/ui/CustomerPicker.java` | **mới** | Ô số điện thoại + popup gợi ý, giữ bất biến mục 3.5 (mục 5.8) |
| `src/billing/ui/QrPaymentDialog.java` | **mới** | Mã QR + xác nhận, nhận tài khoản từ cấu hình (mục 5.6f) |
| `src/billing/util/VietQRHelper.java` | sửa | Nhận `bin`, `accountNo` làm tham số; giữ 2 hằng số làm mặc định (mục 5.6f) |
| `src/billing/ui/InvoiceViewPanel.java` | sửa | `fillFormatted` thêm `percentCols` (sửa lỗi `VAT_pct`); xoá bộ format riêng; 3 chỗ `onSelect(null)`; bỏ SQL thô `:609` (mục 5.9) |
| `src/billing/ui/SearchPanel.java` | sửa | Xoá 2 bộ format riêng, gọi `Ui`; bỏ `new Locale(...)` (mục 5.9c) |
| `src/billing/ui/EditLineDialog.java` | sửa | Dùng `Ui.readQuantity` / `Ui.readMoney`; nhận `BigDecimal` thay `String` (mục 5.10) |
| `config.properties` | sửa | Thêm 4 khoá `qr.*` kèm chú thích (mục 5.6f) |
| `docs/05-SQL-JAVA-CONVENTION.md` | sửa | 2 dòng mới, sửa 3 dòng; thêm quy ước nhãn (mục 5.11) |
| `src/billing/model/Product.java` | **không đổi** | `taxRate` thôi chết: cột `VAT %` của tab 1 dùng nó (mục 5.6c) |
| `src/billing/ui/MainWindow.java` | **không đổi** | Tab 1 không có gì phụ thuộc vai trò (mục 2.3) |

### 5.2. `Ui` — hiện số

```java
    /** Bộ ký hiệu số của tiếng Việt: dấu nghìn '.', dấu thập phân ','. */
    private static final DecimalFormat MONEY;     // #,##0      -> 135.100
    private static final DecimalFormat QTY;       // #,##0.###  -> 1,5
    private static final DecimalFormat QTY_IN;    // 0.###      -> 1.5  (dien vao o nhap)
    private static final DecimalFormat PERCENT;   // #0.##      -> 8
    static {
        // Locale.forLanguageTag chu khong new Locale("vi","VN"): ham dung
        // da deprecated va la canh bao bien dich duy nhat con lai cua du an.
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"));
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        ...
    }

    /** 135100 -> "135.100đ". null -> "0đ" (khong bao gio nem). */
    public static String money(BigDecimal v) { ... }
    /** Cot DECIMAL tu JDBC ve: 1.500 -> "1,5" ; 2.000 -> "2". */
    public static String qty(Object v) { ... }
    /** 8.00 -> "8%" ; 7.50 -> "7,5%". Dung cho Tax_Rate / VAT_pct. */
    public static String percent(Object v) { ... }
    /** Gia tri dien san vao o nhap so luong: 1.5 -> "1.5", KHONG dau nghin. */
    public static String qtyInput(BigDecimal v) { ... }
    /** Doi gia tri tho tu JDBC thanh BigDecimal, khong qua double. */
    public static BigDecimal dec(Object o) { ... }
```

`money(Object)` nạp chồng nhận `Object` để `fillFormatted` dùng chung một đường. `dec` là bản dời
của `InvoiceViewPanel:134-139` — nó đã có javadoc đúng (`docs/05` mục 3: cột `DECIMAL` về Java vốn
là `BigDecimal`, không qua `double`), chỉ cần cho nó ra chỗ dùng chung được.

### 5.3. `Ui` — đọc số (hợp đồng mục 3.3)

```java
    /** Biểu thức của một số tiền người Việt gõ: 500.000 | 1.234,50 | 500000 | 35000,5 */
    private static final Pattern MONEY_IN = Pattern.compile(
            "^(\\d{1,3}(\\.\\d{3})*|\\d+)(,\\d{1,2})?$");

    /**
     * Đọc số tiền người dùng gõ. Trả null (và ĐÃ hiện thông báo) nếu không hợp lệ.
     *
     * Dấu '.' là dấu TÁCH NGHÌN và phải đủ 3 chữ số theo sau; dấu thập phân là
     * ','. Nguoc hoan toan voi readQuantity, va day la co y -- plan/14 muc 3.3
     * giai thich vi sao mot quy tac khong phuc vu duoc ca hai.
     *
     * "500.000" -> 500000 (truoc khoi 5: new BigDecimal("500.000") = 500, ghi
     * vao Payment 500 dong, khong mot canh bao nao -- plan/14 loi #1).
     */
    public static BigDecimal readMoney(Component parent, String input, String fieldLabel) {
        if (input == null) return null;
        String s = input.trim().replace(" ", "").replace(" ", "");
        if (s.endsWith("đ") || s.endsWith("d")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty())              { warn(parent, "Chua nhap " + fieldLabel + "."); return null; }
        if (!MONEY_IN.matcher(s).matches()) {
            warn(parent, fieldLabel + " khong doc duoc: \"" + input + "\"\n\n"
                       + "Dau . la dau tach nghin (phai du 3 chu so), dau , la dau thap phan.\n"
                       + "Dung:  500.000   |   1.234,50   |   500000\n"
                       + "Sai :  500.00    |   1.2.3      |   1,234");
            return null;
        }
        BigDecimal v = new BigDecimal(s.replace(".", "").replace(',', '.'));
        if (v.signum() <= 0)          { warn(parent, fieldLabel + " phai lon hon 0."); return null; }
        return v.setScale(2, RoundingMode.HALF_UP);   // DECIMAL(12,2)
    }

    /**
     * Đọc số lượng. Bản dời của InvoiceViewPanel.readQuantity (khối 3) lên chỗ
     * dùng chung được: trước khối 5 nó là private nên tab 1 và EditLineDialog
     * không dùng được, và cả hai đang để MySQL âm thầm làm tròn (plan/14 lỗi #23).
     *
     * @param max số lượng tối đa, hoặc null khi không có trần (tab 1)
     */
    public static BigDecimal readQuantity(Component parent, String input,
                                          BigDecimal max, String unit) { /* nguyên văn khối 3 */ }
```

**Ô tiền có nhãn vọng lại** (mục 3.3): `Ui` thêm một hàm nhỏ dựng sẵn cặp ô + nhãn, để tab 1 không
tự viết `DocumentListener`:

```java
    /**
     * Gắn một nhãn "= 500.000đ" cạnh ô nhập tiền, cập nhật theo từng ký tự.
     * KHÔNG gọi database (khác lỗi #14): chỉ parse trong bộ nhớ.
     * Không đọc được thì hiện "= ?" màu đỏ -- người dùng thấy app hiểu gì
     * TRƯỚC khi bấm nút.
     */
    public static JLabel attachMoneyEcho(JTextField field) { ... }
```

`warn(...)` là bản dời của `InvoiceViewPanel:1244-1247`.

### 5.4. `InvoiceDao`

**a) Mã hoá đơn sinh TRONG transaction** (lỗi #18). `nextInvoiceId()` công khai vẫn giữ (có thể còn
chỗ khác dùng, và nó là ví dụ dạy gọi `fn_*` trong `docs/02-JDBC.md`), nhưng `createInvoice` **không
gọi nó nữa**: nó nhận `invoiceId = null` và tự sinh trên Connection của mình.

```java
    /**
     * @param invoiceId mã hoá đơn, hoặc null để thủ tục tự sinh TRONG transaction.
     *        Truyền null là cách dùng đúng từ khối 5: bản cũ gọi
     *        nextInvoiceId() trên MOT CONNECTION KHAC trước khi mở transaction,
     *        nên hai cửa sổ app cùng lưu là trùng khoá chính (plan/14 lỗi #18).
     *        Hàm trả về mã đã dùng.
     */
    public String createInvoice(String invoiceId, ...) {
        ...
        if (invoiceId == null) {
            invoiceId = scalarString(conn, "{? = call fn_next_invoice_id()}");
        }
```

Khe hở còn lại — hai transaction chạy song song vẫn đọc được cùng `MAX(...)` trước khi cái nào
`INSERT` — **không bịt ở khối này**, và đây là một ghi chú trung thực chứ không phải bỏ sót: bịt
đúng cách là `INSERT ... SELECT` hoặc một bảng cấp số, tức là đổi `fn_next_invoice_id` và
`sp_create_invoice_header` — hai routine của khối 4. Cái sửa được ở khối này là **thu hẹp cửa sổ từ
"khoảng thời gian người dùng đọc hộp thoại" xuống "vài micro giây trong một transaction"**, và làm
cho lần thất bại là một `ROLLBACK` sạch kèm thông điệp đọc hiểu được thay vì mất giỏ hàng.

**b) Tiền khách đưa lấy từ `PaymentEntry.tendered`:**

```java
                    cs.setBigDecimal(3, pe.amount);                 // Payment.Amount  = tien GHI NHAN
                    cs.setString(4, pe.method);
                    // detail1 van la chuoi (sp_add_payment giu 6 tham so, docs/05
                    // Quy tac 2) nhung nguon cua no gio la mot BigDecimal co kieu,
                    // khong phai mot ban sao cua pe.amount (plan/14 loi #9).
                    cs.setString(5, pe.wireDetail1());
```

`PaymentEntry.wireDetail1()` trả `tendered.toPlainString()` cho `CASH` và `detail1` cho hai phương
thức còn lại — **một chỗ duy nhất** biết rằng `sp_add_payment` nhận tiền khách đưa dưới dạng chuỗi.

**c) Mới:** `invoiceLineEditFields(invoiceId, lineNumber)` →
`QueryResult.call("{call sp_get_invoice_line_edit_fields(?, ?)}", ...)`.

**d) Xoá** `nextPaymentSeq` (mục 4.5).

### 5.5. Model

**a) Mới: `src/billing/model/PayMethod.java`** — đúng khuôn `InvoiceStatus` (`plan/13` mục 5.2):

```java
/**
 * Phuong thuc thanh toan -- BAN CHEP DUY NHAT trong Java.
 *
 * Truoc khoi 5 bo ba gia tri nay bi chep 3 lan: hang so trong PaymentEntry,
 * mang String[] trong NewInvoicePanel (sinh ra cac chi so 0/1/2 rai khap
 * file), va CASE trong sp_get_invoice_payments. Nhan phai GIONG HET chuoi
 * ma SQL tra ve, neu khong mot hoa don se hien hai cach viet cho cung mot
 * phuong thuc (plan/14 muc 3.6).
 */
public enum PayMethod {
    CASH   ("Tien mat",      true),    // co tien khach dua -> co tien thoi
    CARD   ("The ngan hang", false),
    EWALLET("Vi dien tu",    false);

    public String  code()      { return name(); }
    public String  label()     { return label; }
    public boolean hasChange() { return hasChange; }
    /** Doc tu chuoi lay tu database. Tra null khi khong khop -- khong nem. */
    public static PayMethod of(String raw) { ... }
    @Override public String toString() { return label; }   // JComboBox goi ham nay
}
```

`cbMethod` thành `JComboBox<PayMethod>`, nên **mọi chỉ số 0/1/2 biến mất** khỏi
`NewInvoicePanel` (`:254`, `:264`, `:286-289`, `:680-692`, `:707-713`, `:780`, `:878`, `:1244`).
`hasChange()` là chỗ duy nhất trả lời "phương thức này có tiền thối không" — thay cho phép so sánh
`methodIndex == 0`.

**b) Mới: `src/billing/model/CustomerRef.java`** — `id`, `name`, `phone`, với
`display()` = `"Nguyen Van A - 0901234567 (CUS001)"` cho popup. `IdName` **không đổi** (quầy và thu
ngân vẫn dùng đúng nghĩa `id + name`); cái sai là nhét ba thông tin vào field `name` của nó
(`LookupDao:58`).

**c) `PaymentEntry`:** thêm `public final BigDecimal tendered;` (null cho thẻ/ví) và
`wireDetail1()`. Hàm dựng cũ giữ lại (gọi hàm mới với `tendered = null`) để không phá chỗ gọi nào.
`methodLabel()` thành `PayMethod.of(method).label()`, có nhánh dự phòng trả `method` khi không khớp.

### 5.6. `NewInvoicePanel` — bố cục và luồng mới

**a) Bố cục.** Bốn khung giữ nguyên tên và số thứ tự (người dùng cũ không phải học lại), đổi ruột:

```
┌ 1. Thong tin hoa don ───────────────────────────────────────────────────┐
│ Quay [C01 - 123 Le Loi ▾]   Thu ngan [E02 - Le Thi Binh ▾]              │
│ Ngay ban [11/10/2026 ▴▾]    Khach (SDT) [0901234567    ]  → Nguyen Van A│
│ Meo: doi "Ngay ban" ve nam ngoai roi them san pham -> don gia lay GIA CU │
└─────────────────────────────────────────────────────────────────────────┘
┌ 2. Them san pham ───────────────────────────────────────────────────────┐
│ San pham [▾]   So luong [1   ]  [Them vao hoa don]  [Sua so luong]      │
│                                 [Xoa dong dang chon]                     │
│ Gia ngay 11/10/2026 = 33.000d   KM-03 (-10%)                            │
└─────────────────────────────────────────────────────────────────────────┘
┌ 3. Cac dong hang (se thanh bang Invoice_Line) ──────────────────────────┐
│ Dong │ San pham │ SL │ DVT │ Don gia │ Giam gia │ Thanh tien │ VAT %    │
└─────────────────────────────────────────────────────────────────────────┘
┌ 4. Thanh toan (mot hoa don co the co NHIEU dong thanh toan) ────────────┐
│ Phuong thuc [Tien mat ▾]   Khach dua [500.000   ] = 500.000d            │
│ [Them thanh toan] [Khach tra du bang tien mat] [Tao ma QR]              │
│ [Xoa dong dang chon] [Xoa het]                                          │
│ Phuong thuc │ So tien │ Khach dua │ Tien thoi │ Chi tiet                │
└─────────────────────────────────────────────────────────────────────────┘
 Tong: 198.000d    Da thu: 198.000d    Con thieu: 0d    Tien thoi: 302.000d
                                    [XUAT HOA DON  (INSERT + COMMIT)]
```

Bốn thay đổi bố cục, mỗi cái một lý do:

- **Khung 4 dùng `GridBagLayout`, không `FlowLayout`** — lỗi #24, bài học đã ghi ở
  `MainWindow:102-104`. Hàng nút tự xuống dòng **theo đúng chỗ ta đặt**, không theo bề rộng cửa sổ.
- **Nhãn ô tiền đổi theo phương thức**: `"Khach dua:"` cho tiền mặt, `"So tien:"` cho thẻ/ví — hợp
  đồng mục 3.1 nhìn thấy được ngay trên màn hình, không phải đọc tài liệu.
- **Bảng thanh toán 5 cột, cao ~4 dòng** (lỗi #25): `Khach dua` và `Tien thoi` là hai cột mới,
  trống với thẻ/ví.
- **Hàng tổng thêm `Tien thoi`** = `Σ(tendered − recorded)`. Nhãn `Con thieu` không bao giờ âm nữa
  (mục 3.1 điểm 1), nên nhánh *"Số tiền cần thối"* ở `:1217-1220` biến mất — tiền thối có chỗ riêng.
- **Enter trong ô số lượng = *Them vao hoa don*; Enter trong ô tiền = *Them thanh toan*.** Hai
  `addActionListener` trên `JTextField`. Màn hình bán hàng mà phải rời bàn phím để bấm chuột sau mỗi
  sản phẩm là một lỗi dùng được, chỉ là không ai gọi nó là lỗi.

**b) Một chỗ duy nhất dựng/sửa một dòng giỏ hàng** (lỗi #2, #3; hợp đồng mục 3.2):

```java
    /**
     * Dựng một dòng giỏ hàng: hỏi database giá tại NGÀY BÁN và khuyến mãi tại
     * ngày đó, rồi tính tiền giảm. Đây là chỗ DUY NHẤT tính Discount.
     *
     * CHẠY NGOÀI EDT (2 lần gọi database). Chỗ gọi dùng SwingWorker.
     */
    private CartLine priceLine(Product p, BigDecimal qty, LocalDate date) { ... }

    /** discount = ROUND(qty * price * pct / 100, 2) -- plan/14 mục 3.2. */
    private static BigDecimal lineDiscount(BigDecimal qty, BigDecimal price, BigDecimal pct) { ... }
```

`addToCart()` và `editQuantity()` **đều** đi qua `priceLine`. `addToCart` gộp dòng **chỉ khi**
`priceOn(date).compareTo(existing.unitPrice) == 0`; khác giá thì thêm dòng mới và `lbHint` nói rõ:
*"Gia ngay 10/10 (130.000d) khac don gia cua dong da co (158.000d) -> them dong moi."*

**c) Bảng giỏ hàng** (lỗi #7): `Thanh tien` = `c.lineTotal()` — **đúng công thức của
`sp_get_invoice_lines:396`**, thêm cột `Giam gia` và cột `VAT %` (dùng `Product.taxRate`, lỗi #29).
Tám cột của tab 1 giờ khớp từng cột với tám cột của tab 3.

**d) `editQuantity()`** không mượn ô "So luong" của khung 2 nữa (lỗi #2 có một nửa nguyên nhân là
chỗ này): `JOptionPane.showInputDialog` điền sẵn `Ui.qtyInput(line.quantity)`, đọc bằng
`Ui.readQuantity`, rồi `priceLine` lại ngoài EDT. Nút đổi nhãn thành `"Sua so luong"` (nó sửa dòng
đang chọn, không "chỉnh" ô nhập).

**e) `save()` ngoài EDT, nút bị khoá** (lỗi #16, #17, #20):

```java
        // Doc het cac o NHAP tren EDT truoc khi vao SwingWorker: doc Swing
        // component tu luong khac la sai, va la loai sai chay dung 99 lan.
        IdName counter = (IdName) cbCounter.getSelectedItem();
        IdName cashier = (IdName) cbCashier.getSelectedItem();
        if (counter == null || cashier == null) {
            JOptionPane.showMessageDialog(this,
                "Chua nap duoc danh sach quay / thu ngan.\n"
              + "Kiem tra ket noi MySQL roi bam \"Tai lai\" o khung 1.",
                "Thieu du lieu danh muc", JOptionPane.WARNING_MESSAGE);
            return;                                   // thay cho NPE (loi #20)
        }
        LocalTime time = LocalTime.now().withNano(0);  // MOT lan (loi #29)
        setSaving(true);                               // khoa nut + con tro cho
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                return invoiceDao.createInvoice(null, date, time, ...);   // null = SQL tu sinh ma
            }
            @Override protected void done() {
                setSaving(false);
                String id;
                try { id = get(); } catch (Exception ex) { Ui.error(NewInvoicePanel.this, ex); return; }
                ReceiptDialog.show(NewInvoicePanel.this, invoiceDao, id);  // doc lai tu DB
                resetForm();                            // gio hang + thanh toan + KHACH HANG
                if (onSaved != null) onSaved.run();
            }
        }.execute();
```

`resetForm()` xoá **cả khách hàng** (lỗi #11) và xoá dòng gợi ý giá (`lbHint`).
`setSaving(true)` đổi nhãn nút thành `"Dang luu..."` — một nút mờ không giải thích gì thì người dùng
bấm tiếp.

**f) Mã QR nói thật** (lỗi #27). `loadBankConfig()` bị xoá; `QrPaymentDialog` đọc qua `AppConfig`
(khối 2 đã dựng đúng đường, và nó đọc bằng `FileInputStream` nên **không** phụ thuộc classpath):

```java
    // config.properties -- 4 khoa moi, co kem chu thich trong file
    String bin  = AppConfig.get("qr.bank.bin",      "970422");   // MB Bank
    String acc  = AppConfig.get("qr.account.no",    "90902241107");
    String name = AppConfig.get("qr.account.name",  "");
    boolean on  = AppConfig.getBoolean("qr.enabled", true);
```

`VietQRHelper.generateQRCodeImage(bin, accountNo, amount, ref, size)` — thêm 2 tham số ở **đầu**
danh sách (không phải cuối: đây là một API nội bộ 2 chỗ gọi, không phải thủ tục SQL có hợp đồng, nên
đặt chủ thể trước số tiền đọc tự nhiên hơn). Hai hằng số `DEFAULT_BIN` / `DEFAULT_ACCOUNT` giữ lại
làm **giá trị mặc định của `AppConfig.get`**, không còn là đường duy nhất. Thêm:

- `qr.account.name` hiện trên hộp thoại ("Chuyển tới: ..."), để người quét biết tiền đi đâu.
- `amount` dùng `setScale(0, HALF_UP)` **trước** khi `longValue()` (`:809` đang cắt phần lẻ im lặng).
- Một dòng chữ xám trong hộp thoại: *"Ban demo: viec doi soat la thu cong -- bam Xac nhan khi da
  thay tien vao."* Nút *Xác nhận* ghi `EWALLET` với `detail1 = "VietQR"`, `detail2 = ref` như cũ.
- `qr.enabled=false` thì nút *Tao ma QR* **ẩn hẳn** (không mờ), đúng cách khối 2 xử lý nút *Đổi vai
  trò* (`MainWindow:194-202`): một nút bị khoá không giải thích được gì.

### 5.7. Mới: `ReceiptDialog` — đọc từ database

Thay `showReceiptPopup` (186 dòng, `:976-1161`). Lưu xong, **đọc lại** bằng ba thủ tục đã có —
`sp_get_invoice_header`, `sp_get_invoice_lines`, `sp_get_invoice_payments` — ngoài EDT.

| | Phương án | Kết luận |
|---|---|---|
| **A** | **Đọc lại từ database sau khi `commit`** | **ĐÃ CHỌN.** Hoá đơn in ra **không thể** nói khác tab 3 vì nó đọc cùng nguồn. Nó hiện được `Trang_thai` của khối 4 (`OPEN`/`PAID`), tên shop và địa chỉ thật (lỗi #29), tiền khách đưa và tiền thối thật (lỗi #5, #9), số lượng thập phân (lỗi #4), đủ mọi lần thanh toán (lỗi #6). Không cần thủ tục mới |
| B | Dựng từ giỏ hàng trong bộ nhớ (như hiện tại) | Loại: 6 trong 29 lỗi của khối này nằm trong đúng 186 dòng đó, và **mọi** lỗi ấy là một dạng của "màn hình nói khác database" |

Nội dung bắt buộc có, ngoài những gì đang có:

- **Dòng trạng thái**, chữ to: `DA THANH TOAN (PAID)` hoặc `DANG LAP (OPEN) -- con thieu 100.000d`
  màu cam. Khối 4 làm `OPEN` thành trạng thái thật và `NewInvoicePanel:920-932` đã cảnh báo trước
  khi lưu; một tờ hoá đơn in ra trông như đã thu đủ tiền sẽ phá hết công đó.
- **Tên và địa chỉ shop** từ cột `Shop` / `Dia_chi` của `sp_get_invoice_header`, thay chuỗi ghi cứng
  `"SIÊU THỊ MINI MART"` (`:996`). Không đọc được thì để trống, **không** bịa.
- **Bảng thanh toán nhiều dòng**, mỗi dòng: phương thức, số tiền, khách đưa, tiền thối, chi tiết.
- **Mọi con số đi qua `Ui.money` / `Ui.qty` / `Ui.percent`.** Không `String.format("%,.0fđ")`
  (`:855` — bản chép thứ năm của định dạng tiền).
- **Nút "In hoá đơn" in thật** (lỗi #28): `receiptPane.print()` — `JTextComponent.print()` tự mở
  hộp thoại máy in của hệ điều hành và trả `false` khi người dùng bấm Cancel. Không có máy in thì
  chính hộp thoại đó nói ra, chứ không phải app nói dối là đã in.
- **Hộp thoại có cha**: `new JDialog(SwingUtilities.getWindowAncestor(parent), ...)` (lỗi #29).

### 5.8. Mới: `CustomerPicker` — giữ bất biến mục 3.5

Một `JPanel` gói `JTextField` + `JLabel` trạng thái + popup gợi ý, lấy ra khỏi `NewInvoicePanel`
(`:76-80`, `:356-527` — 175 dòng). Những chỗ khác với bản cũ:

```java
    /** Khách đang chọn, hoặc null = khách vãng lai. KHÔNG BAO GIỜ trả về một
     *  khách mà số điện thoại của họ khác nội dung đang hiện trong ô.  */
    public CustomerRef getSelected() {
        if (selected == null) return null;
        return selected.phone.equals(tfPhone.getText().trim()) ? selected : null;   // bat bien muc 3.5
    }
```

- **Bất biến kiểm ở chỗ ĐỌC, không chỉ ở chỗ ghi.** `DocumentListener` vẫn xoá `selected` khi nội
  dung đổi, nhưng `getSelected()` kiểm lại lần nữa. Lỗi #10 là một lỗi "quên xoá ở một nhánh" —
  phép kiểm ở chỗ đọc thì không nhánh nào thoát được.
- **Chỉ tìm khi nội dung toàn chữ số và dài ≥ 3** (mục 3.5).
- **Hoãn 250ms bằng `javax.swing.Timer`, tìm trong `SwingWorker`** (lỗi #14).
- **Không giật con trỏ** (lỗi #13): popup dùng `JPopupMenu` (hoặc `JWindow` lấy owner **tại thời
  điểm hiện**, không trong hàm dựng), và **không gọi `requestFocusInWindow`**. Mũi lên / xuống /
  Enter / Esc được xử lý bằng `InputMap` của chính ô nhập — con trỏ ở lại trong ô, đúng cách mọi ô
  tìm kiếm hoạt động.
- **Chọn xong thì ô chứa số điện thoại**, nhãn hiện `→ Nguyen Van A (CUS001)` (mục 3.5), và
  **không gọi lại database** để lấy object (lỗi #14): popup giữ `List<CustomerRef>` của lần tìm.
- `clear()` để `resetForm()` gọi (lỗi #11).

### 5.9. `InvoiceViewPanel` và `SearchPanel`

**a) `fillFormatted` thêm `percentCols`** (lỗi #21). Chữ ký thành 6 tham số; 4 chỗ gọi phải sửa —
đúng cái nghi thức mà `plan/12` mục 3.5 đã đặt ra ("đổi cột trả về là đổi hợp đồng"):

```java
            // tbLines: Dong, San_pham, SL, DVT, Don_gia, Giam_gia, Thanh_tien, VAT_pct
            // VAT_pct (7) la PHAN TRAM, khong phai tien: ban cu xep no vao nhom
            // tien te nen 8.00 hien ra la "8đ" (plan/14 loi #21).
            fillFormatted(tbLines, lines, new int[]{4, 5, 6}, null, new int[]{2}, new int[]{7});
```

**b) `tbPayments` thêm 2 cột** (mục 4.4): `fillFormatted(tbPayments, payments, new int[]{1, 4}, ...)`.

**c) Xoá bộ format riêng của hai lớp** (lỗi #22): `InvoiceViewPanel:84-139` và `SearchPanel:34-46`
+ `:903-913` gọi `Ui`. `SearchPanel.formatValue` giữ nguyên logic chọn nhóm theo tên cột, chỉ đổi
chỗ định dạng — và cảnh báo `new Locale("vi","VN")` biến mất cùng bộ format.

**d) Ba chỗ `reload(); onSelect(null);`** (lỗi #30) → `reload(); selectInvoiceRow(id); showDetail(id);`
ở `:486-487` (`onEditInvoice`), `:627-628` (`onEditLine`), `:654-655` (`onDeleteLine`). Khối 3 đã
dựng sẵn hai hàm đó và ghi rõ là để lại cho khối này (`plan/12` mục 9.2 điểm 7).

**e) Bỏ SQL thô `:609-610`** → `dao.invoiceLineEditFields(invoiceId, lineNo)`, và truyền
**`BigDecimal`** cho `EditLineDialog` thay vì `String.valueOf(r[i])` (mục 5.10).

**f) `:585-587`** — ba câu `QueryResult.run("SELECT '' WHERE 1=0")` chỉ để xoá trắng bảng: đổi thành
`Ui.clear(table)` (`model.setRowCount(0); model.setColumnCount(0);`). Ba lượt đi database để vẽ một
bảng rỗng là ba lượt không cần thiết.

### 5.10. `EditLineDialog`

Hàm dựng nhận `BigDecimal quantity, unitPrice, discount` thay vì `String` (lỗi #1, #23). Ô nhập
điền sẵn bằng `Ui.qtyInput(...)` / `Ui.money(...)`; `doSave()` đọc bằng `Ui.readQuantity(...)` và
`Ui.readMoney(...)`. Ba khối `try { new BigDecimal(...) } catch` (`:137-171`) biến mất, và dòng hàng
hết đường nhận `1.2345` để `DECIMAL(10,3)` âm thầm làm tròn thành `1.235`.

Nhãn ô thêm quy tắc đọc số, vì hai ô cạnh nhau có hai quy tắc khác nhau (mục 3.3):
`"So luong (Quantity) *:"` tooltip *"dau , hoac . la dau thap phan, toi da 3 chu so"*;
`"Don gia (Unit_Price) *:"` tooltip *"dau . tach nghin: 158.000"*.

Giữ nguyên toàn bộ cơ chế *"Thu xoa (Set NULL)"* và các cảnh báo `NOT NULL` — đó là mục đích dạy học
của hộp thoại này (`plan/06` mục 4.3).

### 5.11. `docs/05-SQL-JAVA-CONVENTION.md`

Thêm 2 dòng vào bảng mục 6, đúng vị trí nhóm:

| Tên Routine | Loại | Tham số | Ý nghĩa nghiệp vụ | Nơi gọi trong Java |
|---|---|---|---|---|
| `sp_search_customers_by_phone` | PROCEDURE | `p_prefix` | Tìm khách theo tiền tố số điện thoại, 3 cột, `LIMIT 20`. Trả **cột riêng**, không ghép chuỗi hiển thị | `LookupDao.searchCustomersByPhone()` |
| `sp_get_invoice_line_edit_fields` | PROCEDURE | `p_invoice_id`, `p_line_number` | 4 trường thô của một dòng hàng cho hộp thoại sửa | `InvoiceDao.invoiceLineEditFields()` |

Sửa 3 dòng đang có:

- `fn_next_payment_id` — *"SQL & Java"* → `InvoiceDao.createInvoice()` (mục 4.5 đã làm cho đúng).
- `sp_lookup_customers` — → *"(không còn chỗ gọi trong Java — thay bằng `sp_search_customers_by_phone`)"*.
- `sp_get_invoice_payments` — ghi rõ **5 cột** và thứ tự (hợp đồng mục 3.6).

Thêm một mục ngắn **"7. Quy ước nhãn giao diện"**: một file dùng một lối viết (có dấu hoặc không);
chuỗi do SQL sinh ra giữ nguyên dạng SQL; định dạng số chỉ qua `Ui`; đọc số chỉ qua
`Ui.readMoney` / `Ui.readQuantity`.

---

## 6. Danh Sách Công Việc (theo thứ tự thực hiện)

### Phần A — SQL (làm trước, một mình cũng kiểm thử được)

1. Thêm 2 dòng `DROP` vào đúng nhóm ở đầu `04_routines.sql` (mục 4.1).
2. Thêm `sp_search_customers_by_phone` vào PHẦN 2, dời số comment 2.4/2.5 (mục 4.2).
3. Thêm `sp_get_invoice_line_edit_fields` vào PHẦN 3 (mục 4.3).
4. Sửa `sp_get_invoice_payments`: thêm cột `Tien_khach_dua`, bỏ số tiền khỏi `Chi_tiet` (mục 4.4).
5. Nạp lại — khối này **không** đổi lược đồ nên nạp riêng phần routine là đủ, **không** cần
   `db-setup.ps1` (sẽ xoá sạch dữ liệu):
   ```powershell
   $env:MYSQL_PWD = 'root'
   Get-Content .\sql\04_routines.sql -Raw -Encoding UTF8 | mysql -u root -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4
   $env:MYSQL_PWD = $null
   ```
6. Chạy hết mục 7.1 bằng `mysql` CLI. **Xong Phần A mới sang Phần B.**

### Phần B — lớp dùng chung (không có nó thì Phần C không biên dịch được)

7. `Ui`: `vi-VN`, `money`/`qty`/`percent`/`qtyInput`/`dec`/`clear` (mục 5.2).
8. `Ui`: `readMoney`, `readQuantity` (dời từ `InvoiceViewPanel`), `warn`, `attachMoneyEcho` (mục 5.3).
9. `PayMethod`, `CustomerRef` mới; `PaymentEntry` thêm `tendered` + `wireDetail1()` (mục 5.5).
10. `LookupDao`: `searchCustomersByPhone()` qua thủ tục, xoá `customers()` (mục 5.5b).
11. `InvoiceDao`: xoá `nextPaymentSeq`, dùng `fn_next_payment_id`, sinh mã hoá đơn trong
    transaction, thêm `invoiceLineEditFields()` (mục 5.4).
12. **Biên dịch ngay tại đây** (`javac`). Phần B không đổi hành vi nhìn thấy được, nên lỗi biên dịch
    ở bước này là lỗi của chính nó, không lẫn với Phần C.

### Phần C — tab 1

13. `CustomerPicker` (mục 5.8).
14. `QrPaymentDialog` + `VietQRHelper` 2 tham số + 4 khoá `qr.*` trong `config.properties` (mục 5.6f).
15. `ReceiptDialog` (mục 5.7).
16. `NewInvoicePanel`: bố cục mới (mục 5.6a), `priceLine`/`lineDiscount` (5.6b), bảng giỏ hàng 8 cột
    (5.6c), `editQuantity` (5.6d), hợp đồng tiền mặt trong `addPayment`/bảng/hàng tổng (mục 3.1),
    `save()` ngoài EDT + `resetForm()` (5.6e). Xoá: `handleRefund`, `updateRefundButtonState`,
    `loadBankConfig`, `showQrDialog`, `showReceiptPopup`, `ignoreTextChanges`, 8 import không dùng.
17. Chạy mục 7.2–7.4.

### Phần D — các tab khác

18. `InvoiceViewPanel`: `fillFormatted` + `percentCols` và 4 chỗ gọi, 2 cột mới của `tbPayments`,
    xoá bộ format riêng, 3 chỗ `onSelect(null)`, bỏ SQL thô `:609`, `Ui.clear` (mục 5.9a–f).
19. `EditLineDialog`: `BigDecimal` + `Ui.read*` (mục 5.10).
20. `SearchPanel`: xoá 2 bộ format, gọi `Ui` (mục 5.9c).
21. Chạy mục 7.5.

### Phần E — tài liệu

22. `docs/05` mục 6: 2 dòng mới, sửa 3 dòng, thêm mục 7 quy ước nhãn (mục 5.11).
23. Chạy lại **toàn bộ** mục 7, rồi viết mục 9 của file này.

---

## 7. Kịch Bản Kiểm Thử

Đánh số liên tục để nhắc nhau khi báo lỗi ("kịch bản #12 sai" ngắn hơn một đoạn văn).

### 7.1. Phần A — chỉ bằng `mysql` CLI, chưa cần Java

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 1 | Nạp `04_routines.sql` **hai lần liên tiếp** | Lần hai cũng in `... da tao thanh cong!`, không có `already exists` (đủ 2 dòng `DROP`) |
| 2 | `CALL sp_search_customers_by_phone('09');` | Các khách có `Phone` bắt đầu `09`, **3 cột** `Ma_KH, Ten, Dien_thoai`, sắp theo `Phone`, tối đa 20 dòng |
| 3 | `CALL sp_search_customers_by_phone('khong-co-so-nay');` | **0 dòng**, không lỗi |
| 4 | `SELECT p.Payment_ID, p.Amount, c.Tendered_Amount FROM Payment p JOIN Cash_Payment c ON c.Payment_ID=p.Payment_ID WHERE c.Tendered_Amount < p.Amount;` | **0 dòng** — bất biến tiền mặt của mục 3.1 trên toàn bộ seed |
| 5 | `CALL sp_get_invoice_payments('INV-0001');` | **5 cột**; dòng tiền mặt có `Tien_khach_dua` là **số** và `Chi_tiet` **rỗng**; dòng thẻ/ví có `Tien_khach_dua` = `NULL` và `Chi_tiet` như cũ |
| 6 | `CALL sp_get_invoice_line_edit_fields('INV-0002', 1);` | 1 dòng, 4 cột đúng thứ tự `Barcode, Quantity, Unit_Price, Discount`; `Quantity` = `1.500` (dữ liệu thật của `seed:151`) |
| 7 | `CALL sp_get_invoice_line_edit_fields('INV-0002', 99);` | **0 dòng**, không lỗi (chỗ gọi tự báo "không tìm thấy dòng") |
| 8 | `SELECT fn_next_payment_id();` rồi `CALL sp_add_payment(<ma do>, 'INV-0001', 1000, 'CASH', '1000', NULL);` rồi `SELECT fn_next_payment_id();` | Mã thứ hai **lớn hơn mã thứ nhất đúng 1** — điều kiện để mục 4.5 chạy đúng trong một transaction. (Nhớ `ROLLBACK` hoặc xoá dòng vừa thêm) |
| 9 | `CALL sp_report_daily_takings();` và `sp_report_big_returns();` trước / sau khi nạp | **Giống nhau từng dòng** — khối này không chạm báo cáo nào |

### 7.2. Tab 1 — đọc số (hợp đồng mục 3.3)

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 10 | Ô "Khach dua": gõ `500.000` | Nhãn vọng lại hiện `= 500.000đ`. Thêm thanh toán → bảng hiện `500.000đ`, **không** phải `500đ` (lỗi #1) |
| 11 | Gõ `500.00` | Nhãn hiện `= ?` đỏ; bấm *Them thanh toan* → thông báo nêu đúng 3 ví dụ đúng và 3 ví dụ sai, **không** thêm dòng nào |
| 12 | Gõ `1.234,50` | `= 1.234,50đ`, lưu xuống `Payment.Amount` = `1234.50` |
| 13 | Gõ `35 000 đ` | `= 35.000đ` (bỏ khoảng trắng và chữ `đ`) |
| 14 | Gõ `-5` rồi `0` | Cả hai: *"... phai lon hon 0"* |
| 15 | Ô "So luong": gõ `1,5` rồi `1.5` | Cả hai cho `1,5`; bảng giỏ hàng hiện `1,5` (không phải `2`) |
| 16 | Ô "So luong": gõ `1.2345` | *"Toi da 3 chu so thap phan (cot DECIMAL(10,3))"*, **không** thêm dòng |
| 17 | Ô "So luong": gõ `1.000` | Hiểu là **một phẩy không** (số lượng dùng `.` làm dấu thập phân) — khác hẳn ô tiền, đúng mục 3.3, và tooltip của ô đã nói trước |

### 7.3. Tab 1 — giỏ hàng và giảm giá (hợp đồng mục 3.2)

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 18 | Thêm 1 sản phẩm **có khuyến mãi**, xem cột `Giam gia` và `Thanh tien` | `Thanh tien = SL*Don gia - Giam gia`; tổng cột `Thanh tien` **bằng** nhãn `Tong:` (lỗi #7) |
| 19 | Chọn dòng đó, *Sua so luong* → `10` | `Giam gia` **tăng gấp 10**; `Thanh tien` đúng; `Tong:` đúng. Ví dụ ở lỗi #2: `180.000`, không phải `198.000` |
| 20 | Lưu hoá đơn ở #19, vào tab 3 xem dòng hàng | `Giam_gia` và `Thanh_tien` **bằng đúng** con số tab 1 vừa hiện |
| 21 | Thêm cùng một sản phẩm **hai lần**, cùng ngày | Gộp thành **1 dòng**, số lượng cộng lại, `Giam gia` tính theo **tổng** số lượng |
| 22 | Thêm sản phẩm → đổi "Ngay ban" về năm ngoái → thêm **lại sản phẩm đó** | **2 dòng** với 2 đơn giá khác nhau; `lbHint` nói rõ vì sao không gộp (lỗi #3) |
| 23 | Lưu hoá đơn ở #22, mở tab 3 → *Tra hang* trên hoá đơn đó | Hộp thoại liệt kê **cả hai** dòng, trả được từng dòng riêng — khối 3 đã sửa đúng trường hợp này (`plan/12` lỗi #9) |
| 24 | Đổi "Ngay ban" sang **ngày mai** | Không chọn được (trần của `SpinnerDateModel` là hôm nay). Bán hàng ở tương lai không phải nghiệp vụ nào |
| 25 | Chọn sản phẩm, bấm *Them vao hoa don* khi ô số lượng trống | Thông báo rõ, không `NumberFormatException` |
| 26 | Nhấn **Enter** trong ô số lượng | Thêm vào hoá đơn (không phải rời bàn phím) |

### 7.4. Tab 1 — tiền mặt, thanh toán, lưu (hợp đồng mục 3.1)

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 27 | Hoá đơn `198.000`. Phương thức **Tiền mặt**, "Khach dua" = `500.000`, *Them thanh toan* | Bảng: `So tien = 198.000đ`, `Khach dua = 500.000đ`, `Tien thoi = 302.000đ`. Hàng tổng: `Da thu 198.000đ`, `Con thieu 0đ`, `Tien thoi 302.000đ` |
| 28 | Lưu, rồi `SELECT p.Amount, c.Tendered_Amount ...` cho hoá đơn đó | `Amount = 198000.00`, `Tendered_Amount = 500000.00` — ba con số của mục 3.1 nằm đúng chỗ (lỗi #9) |
| 29 | Hoá đơn đó ở tab 3: trạng thái và bảng thanh toán | `PAID`; cột `Tien_khach_dua` hiện `500.000đ` đã định dạng, **không** phải `"Khach dua: 500000.00"` (lỗi #22) |
| 30 | Không còn nút *Thối tiền* nào trên tab 1 | Đúng mục 3.1 điểm 1 — và không còn cách nào thối tiền xuống dưới mức phải trả (lỗi #8) |
| 31 | Hoá đơn `198.000`, Tiền mặt `100.000` → rồi Tiền mặt `150.000` | Dòng 1: `So tien 100.000`, `Tien thoi 0`. Dòng 2: `So tien 98.000`, `Khach dua 150.000`, `Tien thoi 52.000`. `Con thieu 0đ` |
| 32 | Hoá đơn `198.000`, **Thẻ ngân hàng**, số tiền `500.000` | **Từ chối**, thông điệp nêu lý do (không có đường trả lại cho thẻ) và nêu số còn thiếu. Nhãn ô là `"So tien:"`, không phải `"Khach dua:"` (mục 3.1 điểm 2) |
| 33 | Hoá đơn `198.000`, Tiền mặt `100.000`, bấm *Lưu* | Hộp thoại `OPEN` của khối 4 hiện đủ 3 hệ quả; bấm Yes → tab 3 hiện `OPEN` (không hồi quy `plan/13` #30) |
| 34 | *Khach tra du bang tien mat* | Một dòng tiền mặt đúng số còn thiếu, `Tien thoi = 0đ` |
| 35 | Chọn một dòng thanh toán, *Xoa dong dang chon* | Chỉ dòng đó mất; các tổng cập nhật |
| 36 | Bấm *XUAT HOA DON* **hai lần thật nhanh** | **Một** hoá đơn. Nút mờ và đổi chữ `Dang luu...` ngay sau lần bấm đầu (lỗi #17) |
| 37 | Tắt MySQL rồi bấm *XUAT HOA DON* | Cửa sổ **không đứng hình**; `Ui.error` hiện lỗi kết nối; giỏ hàng **còn nguyên** để lưu lại (lỗi #16) |
| 38 | Mở hai cửa sổ app, cả hai lưu cùng lúc | Hai mã hoá đơn **khác nhau**, cả hai lưu được — hoặc nếu trùng thì một cái `ROLLBACK` sạch kèm thông điệp đọc hiểu được, **không** mất giỏ hàng âm thầm (lỗi #18, và ghi chú trung thực ở mục 5.4a) |
| 39 | Khởi động app khi MySQL đang tắt | Cửa sổ **vẫn vẽ ra**, khung 1 báo không nạp được danh mục; bấm *XUAT HOA DON* → thông báo thiếu danh mục, **không** `NullPointerException` (lỗi #19, #20) |
| 40 | Tab 5 Setup App (khối 1) sau khi lưu một hoá đơn | Thứ tự: `fn_next_invoice_id` → `sp_create_invoice_header` → `sp_add_invoice_line [batch x N]` → (`fn_next_payment_id` → `sp_add_payment`) × M → `sp_settle_invoice` → `COMMIT`. **Không** còn dòng `SELECT COALESCE(MAX(...)) FROM Payment` (mục 4.5) |

### 7.5. Khách hàng (hợp đồng mục 3.5)

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 41 | Gõ `09` vào ô "Khach (SDT)" | **Chưa** tìm (dưới 3 chữ số). Gõ số thứ ba → sau ~250ms popup hiện. **Con trỏ vẫn ở trong ô**, gõ tiếp được (lỗi #13) |
| 42 | Gõ 10 chữ số liên tục, xem tab 5 Setup App | **1–2 dòng** `sp_search_customers_by_phone`, không phải 8–10 (lỗi #14) |
| 43 | Gõ chữ cái vào ô | Không gọi database lần nào; popup không hiện |
| 44 | Chọn một khách từ popup | Ô chứa **số điện thoại**; nhãn cạnh ô hiện `→ Nguyen Van A (CUS001)` (lỗi #12) |
| 45 | Chọn khách rồi **gõ thêm một số** | Nhãn đổi về `(khach vang lai)` ngay. Lưu → `Invoice.Customer_ID` là `NULL` (lỗi #10) |
| 46 | Chọn khách, lưu hoá đơn, rồi lập hoá đơn thứ hai **không chạm ô khách** | Hoá đơn thứ hai là **khách vãng lai**; ô trống, nhãn `(khach vang lai)` (lỗi #11) |
| 47 | Gõ số điện thoại đầy đủ của một khách **không** chọn từ popup, rồi lưu | `Customer_ID` = **NULL**. Bất biến mục 3.5: chỉ chọn từ danh sách mới gắn khách. Nhãn đã nói rõ trước khi bấm lưu |
| 48 | Chọn khách rồi xoá trắng ô | `(khach vang lai)`, popup đóng |

### 7.6. Hoá đơn in ra

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 49 | Lưu hoá đơn tiền mặt thu thừa (như #27) | Hoá đơn in ra có **"Tien khach dua 500.000đ"** và **"Tien thoi lai 302.000đ"** — 12 dòng code chết ở lỗi #5 giờ chạy thật |
| 50 | Lưu hoá đơn có `1,5 KG` | Cột SL in `1,5`, **không** phải `1` (lỗi #4) |
| 51 | Lưu hoá đơn 2 lần thanh toán (tiền mặt + thẻ) | **Cả hai** dòng hiện trên hoá đơn in ra (lỗi #6) |
| 52 | Lưu hoá đơn trả thiếu (như #33) | Hoá đơn in ra có dòng cam **`DANG LAP (OPEN) -- con thieu 100.000d`** |
| 53 | Đầu hoá đơn in ra | Tên shop + địa chỉ **từ bảng `Shop`** (so với `sp_get_invoice_header`), không phải `"SIÊU THỊ MINI MART"` (lỗi #29) |
| 54 | So từng con số trên hoá đơn in ra với tab 3 của cùng hoá đơn | **Khớp từng đồng** — cả hai đọc cùng ba thủ tục |
| 55 | Bấm *In hoa don* | Hộp thoại máy in của hệ điều hành mở ra. Bấm Cancel → không có thông báo "đã in" (lỗi #28) |
| 56 | Bấm Esc trên hoá đơn in ra | Đóng hộp thoại (giữ hành vi cũ `:1143-1150`) |

### 7.7. Các tab khác (chống hồi quy)

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 57 | Tab 3, bảng "Cac dong hang", cột cuối | **`8%`**, không phải `8đ` (lỗi #21 — việc khối 3 và khối 4 giao lại) |
| 58 | Tab 3: *Sua dong hang* → đổi số lượng → Lưu | Bảng dòng hàng **hiện số mới ngay**, hàng hoá đơn vẫn được chọn (lỗi #30). Trước khối này bảng giữ nội dung cũ |
| 59 | Tab 3: *Sua hoa don* → đổi giờ → Lưu | Khung chi tiết cập nhật ngay |
| 60 | Tab 3: *Xoa dong hang* | Bảng cập nhật ngay; tổng tiền ở khung trên đổi theo |
| 61 | Tab 3: *Sua dong hang*, ô số lượng | Điền sẵn `1.5` (không dấu nghìn); gõ `1.2345` bị **từ chối** thay vì để MySQL làm tròn (lỗi #23) |
| 62 | Tab 3: *Sua dong hang*, ô đơn giá, gõ `158.000` | Hiểu là **158 nghìn** (lỗi #1) |
| 63 | Tab 2, bảng `Product`, cột `Tax_Rate` | Vẫn `8%` như trước — đổi chỗ định dạng không đổi kết quả |
| 64 | Tab 2, các cột tiền và `Quantity` | Giống trước khối này từng chữ |
| 65 | Tab 3 → *Tra hang* (khối 3) | Hoạt động nguyên như cũ, mọi con số không đổi |
| 66 | Tab 4 Báo cáo | Mọi con số không đổi (khối này không chạm báo cáo) |
| 67 | Biên dịch với `-Xlint:all` | **Không còn cảnh báo `deprecation` của `new Locale`** — cảnh báo cuối cùng ngoài nhóm `[serial]`/`[this-escape]` của Swing |

### 7.8. Mã QR

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 68 | Đổi `qr.account.no` trong `config.properties`, **không biên dịch lại**, tạo mã QR | Quét bằng app ngân hàng → **số tài khoản mới**. Trước khối này đổi file không có tác dụng gì (lỗi #27) |
| 69 | Xoá cả 4 khoá `qr.*` khỏi `config.properties` | App chạy bình thường, QR dùng giá trị mặc định, **không** exception (đúng hợp đồng `AppConfig`) |
| 70 | `qr.enabled=false` | Nút *Tao ma QR* **ẩn hẳn**, không mờ |
| 71 | Tạo QR với số tiền `198.000,50` | Payload mang `198001` (`setScale(0, HALF_UP)`), **không** phải `198000` do `longValue()` cắt phần lẻ (`:809`); hộp thoại hiện đúng con số đã nằm trong mã |
| 72 | QR → *Xác nhận đã nhận tiền* | Một dòng `Vi dien tu` với `Chi tiet = VietQR / TXN...`; `Khach dua` và `Tien thoi` trống (mục 3.1: ví không có tiền thối) |

---

## 8. Rủi Ro & Cách Giảm

| # | Rủi ro | Mức | Cách giảm |
|---|---|---|---|
| 1 | **`Ui.readMoney` từ chối một cách gõ hợp lệ mà ta chưa nghĩ tới** (ví dụ `500 000` dùng khoảng trắng, hay dán từ Excel ra `500,000.00` kiểu Mỹ) → thu ngân không nhập được tiền, app trông như hỏng nặng | **Cao** | Kịch bản #10–#14 liệt kê đúng những cách gõ phải nhận. Khoảng trắng đã được bỏ (`:readMoney`). Kiểu Mỹ `500,000.00` **cố ý từ chối** (nó nhập nhằng với `500,00` = 500 đồng 0 xu) nhưng thông điệp lỗi phải nêu ví dụ đúng, chứ không chỉ nói "sai" |
| 2 | **Hợp đồng tiền mặt bị hiểu ngược**: ai đó đọc "Khach dua" thành "số tiền ghi nhận" rồi `recorded = tendered` trở lại | **Cao** | Mục 3.1 là bảng ba dòng, có công thức. Nhãn ô đổi theo phương thức (mục 5.6a) nên hợp đồng nhìn thấy được trên màn hình. Kịch bản #28 kiểm thẳng hai cột trong database |
| 3 | Quên tính lại `discount` ở một đường thứ ba nào đó (ví dụ sau này thêm nút "nhân đôi dòng") | Trung bình | Chỉ có **một** hàm dựng dòng (`priceLine`, mục 5.6b) và nó bắt buộc nhận `qty`. Kịch bản #19–#21 |
| 4 | **Đổi chữ ký `fillFormatted` làm lệch chỉ số cột ở một trong 4 chỗ gọi** → một cột tiền hiện thành ngày, hoặc ngược lại | Trung bình | `plan/12` mục 3.5 đã đặt nghi thức: chỉ số cột là hợp đồng với thứ tự cột của thủ tục. Comment trên mỗi chỗ gọi liệt kê tên cột theo chỉ số (đã có sẵn ở `:417-419`, `:423`, `:427-429`). Kịch bản #57, #63, #64 |
| 5 | **Thêm cột vào `sp_get_invoice_payments` làm vỡ `tbPayments`** | Trung bình | Một chỗ gọi duy nhất (`:425`), sửa cùng lúc (mục 5.9b). Kịch bản #5 kiểm từ CLI **trước** khi Java chạy (thứ tự Phần A → Phần D) |
| 6 | `CustomerPicker` giữ bất biến ở chỗ ghi mà quên một nhánh → lỗi #10 trở lại | Trung bình | Bất biến được kiểm **lần thứ hai ở chỗ đọc** (`getSelected()`, mục 5.8), nên không nhánh nào thoát. Kịch bản #45, #47 |
| 7 | Viết lại `NewInvoicePanel` làm mất một tính năng đang có mà không ai nhớ (ví dụ dòng gợi ý giá theo ngày — thứ cả app sinh ra để minh hoạ) | **Cao** | Mục 5.6a vẽ nguyên bố cục mới, **giữ cả 4 khung, cả dòng mẹo `:179-180`, cả dòng gợi ý giá `:621-622`**. Kịch bản #22 kiểm đúng tính năng lõi đó. Và: không xoá file cũ cho tới khi mục 7.3 chạy xong |
| 8 | `receiptPane.print()` chặn EDT trong lúc hộp thoại máy in mở | Thấp | Đúng là nó chặn — nhưng đó là **hộp thoại của hệ điều hành**, người dùng đang chủ động chờ nó. Khác hẳn việc chặn EDT vì một câu SQL |
| 9 | Khe hở trùng mã hoá đơn vẫn còn (mục 5.4a) | Thấp | Đã thu hẹp từ "thời gian đọc hộp thoại" xuống "vài micro giây trong transaction", và lần thất bại là `ROLLBACK` sạch + thông điệp đọc được. Bịt hẳn là việc của khối sở hữu `fn_next_invoice_id` (khối 4). Kịch bản #38 |
| 10 | Người dùng cũ tìm nút *Thối tiền* không thấy | Thấp | Cột `Tien thoi` trong bảng và nhãn `Tien thoi` ở hàng tổng **tự xuất hiện** khi khách đưa thừa — thông tin họ cần vẫn ở đó, chỉ là không phải bấm gì |
| 11 | Khối 6 (seed động) sinh dữ liệu vi phạm hợp đồng tiền mặt (`Tendered < Amount`) | Thấp | Mục 3.1 đã ghi lại thành yêu cầu cho khối 6. Kịch bản #4 là một câu SQL chạy được bất cứ lúc nào để kiểm toàn bộ database |
| 12 | `javax.swing.Timer` hoãn 250ms bắn sau khi panel đã bị `dispose` (đăng xuất giữa lúc đang gõ) | Thấp | `CustomerPicker` dừng timer trong `clear()`, và `SwingWorker.done()` kiểm `tfPhone.isShowing()` trước khi vẽ popup — đúng cách khối 3 xử lý `showReturnFailure` (`plan/12` mục 9.2 điểm 6) |

---

## 9. Trạng Thái Triển Khai

**ĐÃ TRIỂN KHAI** ngày 2026-10-11. Kế hoạch viết cùng ngày, sau khi khối 4 hoàn tất.

### 9.1. File đã thay đổi

`18 file, +3948 / −1337`.

| File | +/− | Việc |
|---|---|---|
| `sql/04_routines.sql` | +69 / −4 | 2 `DROP`, `sp_search_customers_by_phone`, `sp_get_invoice_line_edit_fields`, `sp_get_invoice_payments` thêm cột `Tien_khach_dua` và bỏ `CONCAT` tiền khỏi `Chi_tiet` |
| `src/billing/ui/Ui.java` | +351 / −8 | Bản chép **duy nhất** của lớp đọc số và lớp hiện số: `money`/`qty`/`percent`/`integer`/`qtyInput`/`moneyInput`/`dec`/`clear`, `readMoney`/`readMoneyAllowZero`/`parseMoney`/`readQuantity`/`warn`/`attachMoneyEcho` |
| `src/billing/ui/NewInvoicePanel.java` | +818 / −991 | Viết lại phần lớn: bố cục `GridBagLayout`, hợp đồng tiền mặt (`splitPayment`), `priceLine`/`lineDiscount`, bảng giỏ hàng 8 cột, bảng thanh toán 5 cột, `save()` ngoài EDT, `resetForm()`. Xoá `handleRefund`, `updateRefundButtonState`, `loadBankConfig`, `showQrDialog`, `showReceiptPopup`, `ignoreTextChanges`, 9 import chết |
| `src/billing/ui/ReceiptDialog.java` | +354 | **Mới.** Hoá đơn in ra, đọc từ database bằng 3 thủ tục đã có, in thật bằng `JTextPane.print()` |
| `src/billing/ui/CustomerPicker.java` | +346 | **Mới.** Ô số điện thoại + popup gợi ý, giữ bất biến mục 3.5 (kiểm cả ở chỗ ghi và chỗ đọc) |
| `src/billing/ui/QrPaymentDialog.java` | +142 | **Mới.** Mã QR nhận tài khoản từ `AppConfig`, `setScale(0)` trước `longValue()`, nói thật rằng đối soát là thủ công |
| `src/billing/ui/InvoiceViewPanel.java` | +105 / −168 | `fillFormatted` thêm `percentCols` (+ 4 chỗ gọi), `tbPayments` 2 cột mới, xoá bộ format riêng, 3 chỗ `onSelect(null)`, bỏ SQL thô, `Ui.clear`, `readQuantity`/`warn` dời sang `Ui` |
| `src/billing/dao/InvoiceDao.java` | +90 / −22 | `createInvoice` trả mã và sinh mã **trong** transaction, `fn_next_payment_id` thay bản chép SQL thô, `pe.wireDetail1()`, `invoiceLineEditFields()`, xoá `nextPaymentSeq` |
| `src/billing/model/PayMethod.java` | +76 | **Mới.** Bản chép duy nhất của bộ phương thức thanh toán trong Java |
| `src/billing/model/PaymentEntry.java` | +64 / −9 | Thêm `tendered`, `wireDetail1()`, `change()`; `methodLabel()` qua `PayMethod` |
| `src/billing/ui/EditLineDialog.java` | +47 / −40 | Nhận `BigDecimal`, đọc qua `Ui.read*`, tooltip nêu quy tắc của từng ô |
| `src/billing/model/CustomerRef.java` | +42 | **Mới.** `id` + `name` + `phone` riêng, thay chuỗi ghép của `IdName` |
| `src/billing/util/VietQRHelper.java` | +37 / −9 | Nhận `bin`/`accountNo` làm tham số; 2 hằng số thành **giá trị mặc định** |
| `src/billing/dao/LookupDao.java` | +34 / −29 | `searchCustomersByPhone()` qua thủ tục, trả `CustomerRef`; xoá `customers()` |
| `src/billing/ui/SearchPanel.java` | +22 / −54 | Xoá 2 bộ format riêng, gọi `Ui`; `new Locale(...)` biến mất |
| `config.properties` | +26 | 4 khoá `qr.*` kèm chú thích |
| `docs/05-SQL-JAVA-CONVENTION.md` | +77 / −3 | 2 dòng mới, sửa 3 dòng, thêm mục 7 "Quy ước nhãn & con số" (4 tiểu mục) |
| `plan/14-...md` | +1248 | File này |

`src/billing/model/Product.java` và `src/billing/ui/MainWindow.java` **không đổi**, đúng mục 5.1.

### 9.2. Những điểm làm khác kế hoạch (và lý do)

**1. `NewInvoicePanel` còn 991 dòng, không phải ~520 như mục 5.1 dự đoán.**
Việc *tách file* đã làm đúng: hoá đơn in ra (186 dòng) sang `ReceiptDialog`, ô khách hàng (175 dòng)
sang `CustomerPicker`, mã QR (90 dòng) sang `QrPaymentDialog`. Nhưng đếm **dòng code thật** (bỏ
comment và dòng trắng) thì file chỉ giảm từ 879 xuống 703 — con số 520 của kế hoạch ngầm giả định mật
độ comment thấp hơn nhiều so với lối viết của dự án này. Tôi chọn giữ mật độ comment: bốn hợp đồng
của mục 3 là thứ dễ bị "dọn dẹp" sai hướng nhất, nên mỗi cái được ghi lại ngay tại chỗ code thực
hiện nó. Đây là một lệch **dự đoán**, không phải lệch thiết kế.

**2. Thêm `Ui.readMoneyAllowZero` — kế hoạch chỉ nói tới `readMoney`.**
Mục 3.3 ghi `readMoney` từ chối số `<= 0`, và kịch bản #14 yêu cầu đúng thế cho ô tiền ở tab 1. Nhưng
mục 5.10 lại bắt `EditLineDialog` đọc ô `Discount` bằng `readMoney` — mà `Invoice_Line.Discount = 0.00`
là giá trị **bình thường nhất** của cột đó (phần lớn dòng hàng không có khuyến mãi), và bản trước
khối 5 nhận `>= 0` cho cả `Unit_Price` lẫn `Discount`. Một quy tắc không phục vụ được cả hai ô, nên
có hai hàm: `readMoney` (dòng thanh toán 0 đồng là vô nghĩa) và `readMoneyAllowZero`. Nếu dùng
`readMoney` cho ô `Discount` thì sẽ không ai xoá được khuyến mãi của một dòng hàng nữa — một hồi quy
mà kế hoạch không thấy.

**3. Thêm `Ui.moneyInput` và `Ui.integer` — không có trong bảng mục 3.4.**
`moneyInput` sinh ra vì điểm 2: `EditLineDialog` cần điền sẵn ô tiền, và `Ui.money` thì kèm chữ `đ`
nên điền vào ô nhập là `readMoney` phải tự gỡ ra. `integer` là bản dời của `formatInteger` — mục 5.9c
nói `SearchPanel.formatValue` "giữ nguyên logic chọn nhóm, chỉ đổi chỗ định dạng", mà logic đó có
nhóm số nguyên; không dời thì vẫn còn một `DecimalFormat` ngoài `Ui`.

**4. `Ui.qtyInput` dùng dấu thập phân `.`, khác bản khối 3 vốn cho ra `1,5`.**
Bảng mục 3.4 ghi `1.5` và kịch bản #61 cũng ghi `1.5`, nhưng `QUANTITY_INPUT_FORMAT` của khối 3 dùng
bộ ký hiệu có dấu thập phân `,` nên thực tế nó cho ra `1,5`. Cả hai đều đọc được (`readQuantity` đổi
`,` thành `.`), nên đây là một khác biệt nhỏ — tôi làm theo kế hoạch vì `1.5` **không thể** bị hiểu
thành dấu nghìn, dù người dùng có sửa thêm vào ô hay không. Hệ quả: hộp thoại trả hàng của khối 3 giờ
điền sẵn `1.5` thay vì `1,5`.

**5. `fillFormatted` để `null` thành `""` cho cột tiền, không phải `"0đ"`.**
Mục 3.4 ghi `Ui.money`: `null -> 0đ`, và `Ui.money` vẫn làm đúng thế. Nhưng cột `Tien_khach_dua` mới
là `NULL` với thẻ và ví điện tử, và `"0đ"` ở đó là **nói rằng khách đưa 0 đồng**, trong khi sự thật
là "phương thức này không có tiền khách đưa" (mục 3.1). Nên `fillFormatted` kiểm `null` trước khi
gọi `Ui.money`. Bảng thanh toán của tab 1 cũng làm vậy (ô trống, không phải `0đ`).

**6. Tách `splitPayment` + lớp `Split` ra khỏi `addPayment`.**
Kế hoạch để công thức tiền mặt nằm thẳng trong `addPayment`, cùng với các phép kiểm giỏ hàng và các
hộp thoại. Rủi ro #2 của mục 8 lại xếp "hợp đồng tiền mặt bị hiểu ngược" ở mức **Cao** — và một hàm
dính Swing, dính `cart`, dính `JOptionPane` thì không kiểm được mà không dựng cả màn hình. `splitPayment`
là hàm **thuần**: `(phương thức, số vừa gõ, còn thiếu) -> (recorded, tendered)`, `null` = từ chối.
Nhờ vậy kịch bản #27, #31, #32, #34, #72 kiểm được bằng code, trên đúng cái hàm mà màn hình gọi
(xem 9.3).

**7. Thêm nút "Tai lai" ở khung 1 — kế hoạch nhắc tới nó nhưng không đưa vào bố cục.**
Thông điệp lỗi ở mục 5.6e nói *"bấm 'Tai lai' o khung 1"*, mà bố cục mục 5.6a không có nút nào tên
vậy. Vì `loadLookups()` giờ chạy ngoài EDT và được phép thất bại mà không làm chết cửa sổ (lỗi #19),
phải có đường nạp lại — nếu không thì MySQL bật lên sau khi app đã mở là người dùng buộc phải khởi
động lại app.

**8. Cột `Khuyen mai` của bảng giỏ hàng biến mất.**
Bố cục mục 5.6a liệt kê đúng 8 cột khớp với 8 cột của tab 3, và `Khuyen mai` không có trong đó. Ghi
chú khuyến mãi vẫn hiện, nhưng ở `lbHint` dưới khung 2 (cho dòng vừa thêm / vừa sửa) chứ không còn
mỗi dòng một ô. Đây là cái **giá** của việc "tám cột khớp từng cột với tab 3" (lỗi #7) — kế hoạch đã
chọn, tôi làm theo, và ghi lại ở đây để khối sau biết nó là một lựa chọn chứ không phải một sơ suất.

**9. Thông điệp của `readQuantity` bớt chữ "tra".**
Bản khối 3 nói *"Chua nhap so luong tra."* và *"So luong tra phai lon hon 0."* vì nó chỉ phục vụ hộp
thoại trả hàng. Giờ nó phục vụ cả tab 1 và `EditLineDialog`, nên hai câu đó bỏ chữ "tra". Câu
*"Chi con X <đơn vị> co the tra."* giữ nguyên — nó chỉ chạy khi có `max`, tức là chỉ ở trả hàng.

**10. `InvoiceDao.nextInvoiceId()` vẫn còn, đúng mục 5.4a, nhưng giờ KHÔNG còn chỗ gọi nào trong
app.** `createInvoice(null, ...)` tự sinh mã. Hàm công khai được giữ vì `docs/02-JDBC.md` dùng nó làm
ví dụ dạy gọi `fn_*`. Khối sau muốn xoá thì phải sửa tài liệu đó trước.

### 9.3. Kết quả kiểm thử đã chạy

**Môi trường:** MySQL 8.0.45 (`mysqld` đang chạy, database `retail_billing` với seed gốc),
JDK 25.0.4, Windows 11.

#### Đã chạy thật, bằng máy — 80 phép kiểm, 80 đạt

**Phần A (`mysql` CLI) — kịch bản #1–#9: đạt hết.** Nạp `04_routines.sql` hai lần liên tiếp đều in
`... da tao thanh cong!` (#1); `sp_search_customers_by_phone('09')` trả 4 khách đúng 3 cột sắp theo
`Phone` (#2), tiền tố vô nghĩa trả 0 dòng không lỗi (#3); bất biến `Tendered_Amount >= Amount` cho
**0 dòng** trên toàn seed (#4); `sp_get_invoice_payments` trả **5 cột**, `INV-0001` (tiền mặt) có
`Tien_khach_dua = 150000.00` và `Chi_tiet` rỗng, `INV-0002` (thẻ) có `Tien_khach_dua = NULL` và
`Chi_tiet` như cũ (#5); `sp_get_invoice_line_edit_fields('INV-0002', 1)` trả `Quantity = 1.500`
(#6), dòng 99 trả 0 dòng không lỗi (#7); trong một transaction, `fn_next_payment_id()` cho
`PAY-0019 → PAY-0020 → PAY-0021` sau mỗi lần `sp_add_payment` (#8); hai thủ tục báo cáo chạy đúng
như trước (#9 — khối này không chạm chúng, nên đây là xác nhận chứ không phải so sánh trước/sau).

**Phần B–E (chạy bằng Java, headless) — một chương trình kiểm tạm ở thư mục scratchpad, KHÔNG commit
vào dự án** (dự án không có bộ khung test; thêm một bộ nằm ngoài phạm vi khối này). Nó gọi thẳng vào
`Ui`, `NewInvoicePanel.splitPayment`, `NewInvoicePanel.lineDiscount` (qua reflection),
`VietQRHelper`, `AppConfig` và `InvoiceDao`:

| Nhóm | Kịch bản | Kết quả |
|---|---|---|
| Đọc số tiền | #10, #11, #12, #13, #14 | đạt. `500.000 → 500000.00`; `500.00`, `1.2.3`, `1,234`, `500,000.00` (kiểu Mỹ), chuỗi rỗng, `-5`, `0` đều bị **từ chối**; `1.234,50 → 1234.50`; `35 000 đ → 35000.00` |
| Đọc số lượng | #15, #16, #17 | đạt. `1,5` và `1.5` đều cho `1.5`; `1.2345` bị từ chối; `1.000` hiểu là **một phẩy không** — khác hẳn ô tiền, đúng mục 3.3. Thêm: bằng đúng `max` thì nhận (`compareTo`, không `equals`) |
| Hiện số | #57, #61, #62, #63 | đạt. `Ui.percent(8.00) = "8%"`, `percent(7.50) = "7,5%"`, `money(135100) = "135.100đ"`, `money(null) = "0đ"`, `qty(1.500) = "1,5"`, `qty(2.000) = "2"`, `qtyInput(1.500) = "1.5"`, `moneyInput(158000.00) = "158.000"` |
| Giảm giá | #18, #19 | đạt. `lineDiscount(1, 20000, 10%) = 2000.00`; đổi lên 10 cho `20000.00` (gấp 10) nên `Thanh tien = 180.000` — đúng con số mục lỗi #2 nói là phải ra, và kiểm luôn rằng con số **sai** của bản cũ là `198.000` |
| Hợp đồng tiền mặt | #27, #31, #32, #34, #72 | đạt, trên đúng hàm `splitPayment` mà màn hình gọi. Hoá đơn `198.000` + khách đưa `500.000` → ghi nhận `198.000`, thối `302.000`; `100.000` rồi `150.000` → dòng 2 ghi nhận `98.000`, thối `52.000`, tổng ghi nhận `198.000`; **thẻ** `500.000` → **từ chối**; thẻ/ví luôn có `tendered = null`; trả đủ bằng tiền mặt → thối `0` |
| Ghi thật xuống DB | #28, #29, #33, #40 | đạt. Lưu 3 hoá đơn thật rồi xoá: `Payment.Amount = 209000.00` và `Cash_Payment.Tendered_Amount = 511000.00` nằm **đúng hai cột** (#28); hoá đơn thành `PAID` (#29); trả thiếu 1.000 → ở lại `OPEN` (#33); hai dòng `Payment` có mã khác nhau và liên tiếp (#40) |
| Mã QR | #68, #69, #71 | đạt. Payload mang đúng tài khoản **truyền vào** và không chứa hằng số cũ (#68); thiếu cấu hình thì dùng mặc định, không exception (#69); `198.000,50` vào payload thành `198001` (tag `5406198001`), chứ không phải `198000` như `longValue()` của bản cũ (#71); 4 khoá `qr.*` trong `config.properties` đọc được qua `AppConfig` |
| Bất biến toàn DB | #4 | đạt, chạy lại sau khi app đã ghi: vẫn 0 dòng vi phạm |

**Nhật ký SQL khi lưu hoá đơn (#40)** — thứ tự quan sát được đúng như kế hoạch yêu cầu:
`fn_next_invoice_id` → `sp_create_invoice_header` → `sp_add_invoice_line [batch x 1]` →
(`fn_next_payment_id` → `sp_add_payment`) × M → `sp_settle_invoice` → `COMMIT`. **Không còn** dòng
`SELECT COALESCE(MAX(...)) FROM Payment`.

**Biên dịch (#67): đạt.** `javac -Xlint:all` trên toàn app: `exit=0`, 46 cảnh báo và **tất cả** thuộc
hai nhóm `[serial]` (37) / `[this-escape]` (9) của Swing. **0 cảnh báo `deprecation`** — cảnh báo
`new Locale("vi","VN")` đã biến mất.

**Khởi động app: đạt.** `java billing.Main --login=cashier` vẽ ra cửa sổ, không một dòng nào trên
`stderr`. Nhật ký cho thấy `sp_get_recent_invoices` chạy trước, rồi **~450ms sau** mới tới
`sp_lookup_counters` / `sp_lookup_cashiers` / `sp_get_all_products` — tức là `loadLookups()` thật sự
chạy ngoài EDT, sau khi cửa sổ đã vẽ (#19).

**Các quy ước của mục 2.2, kiểm bằng `grep` trên dòng code (bỏ dòng comment): đạt hết.**
0 chỗ `new BigDecimal(textField.getText())`; 0 `new DecimalFormat` ngoài `Ui` (cả 6 chỗ đều trong
`Ui.java`); 0 dấu vết của nút *Thối tiền* / `handleRefund` / `updateRefundButtonState` (#30);
0 `SELECT '' WHERE 1=0`; 0 `new JDialog((Frame) null, ...)`; 0 câu SQL thô trong tầng UI;
0 chỗ gọi `LookupDao.customers()` / `searchCustomersByPhonePrefix`; 0 `ignoreTextChanges` /
`receiptShown` / `loadBankConfig` / `showReceiptPopup`; 0 import chết trong `NewInvoicePanel`.
Trong `04_routines.sql`, `CONCAT('Khach dua: ', ...)` chỉ còn trong **comment** giải thích vì sao nó
bị bỏ.

#### CHƯA chạy — cần người bấm chuột trên Swing

Các kịch bản dưới đây kiểm **hành vi tương tác**, và tôi không bấm được chuột trong phiên này. Không
ghi là đạt.

| Kịch bản | Cần kiểm gì |
|---|---|
| #20, #23 | Lưu xong mở tab 3 / hộp thoại trả hàng để so con số (phần dữ liệu đã kiểm ở #28–#33, phần **hiển thị** thì chưa) |
| #21, #22 | Gộp dòng khi cùng giá / **không** gộp khi khác giá, và câu `lbHint` giải thích. Logic đã viết theo mục 3.2 nhưng chỉ chạy được qua `SwingWorker` của `addToCart` |
| #24, #25, #26 | Trần ngày là hôm nay; ô số lượng trống; **Enter** trong ô số lượng |
| #30 | Mắt thường xác nhận không còn nút *Thối tiền* (đã kiểm bằng `grep`, chưa kiểm bằng mắt) |
| #35, #36, #37, #38, #39 | Xoá dòng thanh toán; bấm *XUAT HOA DON* hai lần thật nhanh; tắt MySQL rồi bấm lưu; **hai cửa sổ app** lưu cùng lúc; khởi động khi MySQL đang tắt. (#38 chỉ kiểm được phần "hai lần lưu liên tiếp cho hai mã khác nhau"; **đua thật** giữa hai transaction thì chưa, và mục 5.4a đã ghi rõ khe hở đó còn nguyên) |
| #41–#48 | Toàn bộ `CustomerPicker`: hoãn 250ms, con trỏ ở lại trong ô, chọn từ popup, gõ thêm một số thì về khách vãng lai, reset sau khi lưu |
| #49–#56 | Toàn bộ `ReceiptDialog`: tiền khách đưa / tiền thối hiện ra, `1,5 KG` không bị làm tròn, hai dòng thanh toán, dòng cam `OPEN`, tên shop từ bảng `Shop`, nút in mở hộp thoại máy in, Esc đóng |
| #58, #59, #60 | Tab 3 cập nhật ngay sau *Sua dong hang* / *Sua hoa don* / *Xoa dong hang* (ba chỗ `selectInvoiceRow` + `showDetail` mới) |
| #64, #65, #66 | Chống hồi quy tab 2, trả hàng, tab 4 báo cáo |
| #70 | `qr.enabled=false` thì nút *Tao ma QR* ẩn hẳn |

**Đáng chú ý nhất trong số chưa chạy:** #36 (bấm hai lần ra một hoá đơn) và #37 (tắt MySQL, cửa sổ
không đứng hình) là hai lỗi mà cách sửa *chỉ* kiểm được bằng tay. #49–#56 cũng vậy: `ReceiptDialog`
là 354 dòng mới và chưa có một pixel nào của nó được nhìn thấy.
