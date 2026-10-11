# Kế Hoạch Chi Tiết — Khối 6: Seed Động 90 Ngày

> **Dành cho AI / lập trình viên tiếp theo:** đây là khối **cuối cùng** trong đợt cải tiến gồm 6 khối
> (1 SQL log → Setup App ✅ xong, 2 đăng nhập nhanh ✅ xong, 3 sửa trả hàng ✅ xong,
> 4 trạng thái PAID/OPEN/VOIDED ✅ xong, 5 UI tab lập hoá đơn ✅ xong, **6 seed động 90 ngày ← khối này**).
>
> **Khối này SỞ HỮU `sql/02_seed.sql`** và toàn bộ dữ liệu mẫu của dự án. Nó là khối duy nhất
> **không thêm một tính năng nào cho người dùng** — nó làm cho năm khối trước có dữ liệu đủ để
> *nhìn thấy* chúng hoạt động.
>
> **Khối này CHẠM VÀO DATABASE nhiều nhất trong cả sáu khối, nhưng KHÔNG đổi lược đồ:**
> `sql/01_schema.sql` **không đổi một dòng** (mục 2.3 — đây là kết luận, không phải bỏ sót).
> Nó viết lại `02_seed.sql`, thêm 1 hàm và 1 file kiểm vào `sql/`, sửa 2 thủ tục vì **hậu quả của
> chính nó** (mục 5.2, 5.3), và **đổi thứ tự nạp SQL trong 2 script** (mục 4.1 — bắt buộc, không
> phải tuỳ chọn).
>
> **Phải đọc mục 3 trước khi gõ dòng code đầu tiên.** Khối này có năm hợp đồng, và hợp đồng nguồn
> sinh (mục 3.1) là thứ quyết định toàn bộ phần dưới: **seed đi qua chính các thủ tục mà app đi
> qua**, nên dữ liệu nó sinh ra là dữ liệu mà app *có thể* đã tự tạo.
>
> Giao điểm với năm khối trước: khối 3 (`fn_return_refund`, `fn_next_return_id`), khối 4
> (`sp_settle_invoice`, `sp_void_invoice` — ba trạng thái), khối 5 (hợp đồng tiền mặt `plan/14`
> mục 3.1, hợp đồng giảm giá mục 3.2). Cả bốn hợp đồng đó, khối này **kiểm bằng SQL trên toàn bộ
> database** thay vì tin vào lời hứa (mục 4.6).

---

## 1. Bối Cảnh & Vấn Đề

### 1.1. Hiện trạng (đã đọc SQL + script + mã Java + chạy đếm, không phỏng đoán)

| Thành phần | Trạng thái hiện tại |
|---|---|
| `sql/02_seed.sql` | **281 dòng**, toàn bộ là `INSERT` gõ tay. Đếm thật: **18** hoá đơn, **30** dòng hàng, **18** thanh toán, **3** lượt trả, **10** sản phẩm, **4** khách, 5 thu ngân, 3 shop, 4 quầy, 3 khuyến mãi, 4 `Applies_To`, 18 `Price_History`, 2 tài khoản |
| `02_seed.sql:14` | `@old_day = MAKEDATE(YEAR(CURDATE()),1) − 400 ngày` |
| `02_seed.sql:15-16` | `@new_day = GREATEST(1 tháng 1 năm nay, CURDATE() − 60 ngày)` — **một phép kẹp theo mốc NĂM** |
| `02_seed.sql:118-145` | Ngày hoá đơn: `CURDATE()` tới `CURDATE() − 6` (16 hoá đơn) **+ 2 hoá đơn ở −400 ngày**. Khoảng `−7 .. −399` **rỗng hoàn toàn** |
| `02_seed.sql:118-145` | `Status` được **gõ thẳng** vào câu `INSERT INTO Invoice`: 14 `PAID`, 1 `VOIDED`, 1 `OPEN`, 2 `PAID` cũ |
| `02_seed.sql:154-200` | `Unit_Price` và `Discount` là **số gõ tay**; tổng từng hoá đơn ghi trong comment (`:154`, `:158`, ...) |
| `02_seed.sql:206-212` | 3 lượt trả hàng, **cả 3 đều `Return_ID = 1`** |
| `02_seed.sql` | **Không có một câu `DELETE` / `TRUNCATE` nào.** Chạy lần hai là `Duplicate entry 'S01'` |
| `02_seed.sql:49-52` | 4 khách, số điện thoại `0901000001` … `0901000004` |
| `scripts/ps1/db-setup.ps1:57-59` | Nạp theo thứ tự **01 → 02 → 04** (routine **sau** seed) |
| `scripts/bash/db-setup.sh:46-53` | Cùng thứ tự **01 → 02 → 04** |
| `scripts/ps1/sql-create-db.ps1:30-37` | Nạp **01 → 04**; rồi `sql-seed-data.ps1:30` nạp **02** ⟶ routine **trước** seed |
| | **Hai đường nạp của cùng một dự án có thứ tự ngược nhau.** Hôm nay vô hại vì seed không gọi routine nào |
| `src/envsetup/ui/SqlTab.java:20-22, 33` | Nút **"Tạo dữ liệu mẫu"** chạy `sql-seed-data` → nạp `02_seed.sql` |
| `db-setup.ps1:57`, `db-setup.sh:46` | Nhãn *"tao 15 bang"* — lược đồ có **16** bảng từ `plan/06` (bảng `Account`) |
| `04_routines.sql:17-60` | Khối `DROP ... IF EXISTS` ở đầu file: `04_routines.sql` **chạy lại được bao nhiêu lần cũng xong**. `02_seed.sql` không có khối tương ứng |
| `04_routines.sql:358-377` | `sp_get_recent_invoices` — **không `LIMIT`, không lọc ngày**: trả mọi hoá đơn từng có |
| `04_routines.sql:682-698` | `sp_report_old_price_proof` — **không có `WHERE`**: trả **mọi dòng hàng của mọi hoá đơn** |
| `03_queries.sql:180` | Bản cùng tên trong file truy vấn **có** `WHERE l.Invoice_ID LIKE 'INV-OLD%'` ⟶ **hai bản chép đã lệch nhau** |
| `04_routines.sql:587-602` | `sp_report_daily_takings` lọc `i.Date >= CURDATE() − 7 DAY` |
| `04_routines.sql:608-628` | `sp_report_price_rise` lọc `YEAR(gia_moi.Valid_From_Date) = YEAR(CURDATE())` |
| `04_routines.sql:329-343` | `sp_get_active_promo`: lọc `a.Promo_Start_date ≤ p_date ≤ a.Promo_End_date` **và** `p.Status = 'ACTIVE'` **và** `p.Expiry_Date >= p_date`, rồi `ORDER BY Discount_Percent DESC LIMIT 1` |
| `01_schema.sql:116-127` | `Applies_To` khoá chính `(Barcode, Promo_ID)` ⟶ một sản phẩm vào **một** khuyến mãi đúng **một** lần, không có hai cửa sổ ngày |
| `04_routines.sql:711-722` | `sp_create_invoice_header` **luôn** ghi `'OPEN'`. `sp_settle_invoice` (`:790`) là **chỗ duy nhất** đặt `'PAID'`; `sp_void_invoice` (`:846`) là **đường duy nhất** tới `'VOIDED'` |
| `04_routines.sql:891-958` | `sp_add_return`: `SIGNAL` khi số lượng ≤ 0, vượt số còn lại, hoặc hoá đơn `<> 'PAID'`; `Return_ID` do `fn_next_return_id` sinh |
| `src/billing/ui/ReportPanel.java:88-103` | Báo cáo chạy **trên EDT** (`action.get()` ngay trong `ActionListener`) |
| `ReportPanel.java:70` | Chữ giải thích: *"Hai hoa don INV-OLD-* co gia thap hon han"* |
| `src/billing/ui/SearchPanel.java:703, 809` | `SELECT * FROM <bảng>` — **không `LIMIT`** |
| `03_queries.sql:26`, `:193` | Ghim `INV-0001` và mã vạch `8934008` |
| `docs/03-SCHEMA.md:82-83` | Ghim hai mốc giá của `8934008`: `130.000` và `158.000` |
| `src/billing/db/SqlLog.java:38` | Nhật ký SQL **xoay vòng ở 2 MB** |
| `tools/check/ContractCheck.java:203-345` | `e2e()` lập **3 hoá đơn thật qua `InvoiceDao.createInvoice`** rồi xoá; `:341-345` kiểm bất biến `Tendered_Amount >= Amount` trên **toàn bộ** database (kịch bản #4 của khối 5) |
| `fn_next_invoice_id` / `fn_next_payment_id` | `LPAD(..., 4, '0')` ⟶ `INV-0001` … `INV-9999`, `PAY-0001` … `PAY-9999` |

Nói gọn: **dữ liệu mẫu đúng hôm nay vì có người tính tay đúng, không vì có gì giữ cho nó đúng.**
Năm khối trước đã dựng xong các thủ tục *ép* mọi quy tắc nghiệp vụ — và seed là chỗ duy nhất trong
cả dự án **đi vòng qua tất cả chúng** bằng `INSERT` trực tiếp. Cộng thêm: 7 ngày dữ liệu là vừa đủ
cho đúng một báo cáo, và không đủ cho bất cứ thứ gì khác.

### 1.2. Hai mươi ba vấn đề cụ thể

**Nhóm A — seed đi vòng qua chính các quy tắc mà app ép**

1. **`Status` là số gõ tay, không phải kết luận từ số tiền.** `:118-145` ghi thẳng
   `'PAID'` / `'OPEN'` / `'VOIDED'` vào `INSERT INTO Invoice`. Tôi đã kiểm bằng tay cả 18 hoá đơn:
   hôm nay chúng **khớp** (INV-0001 tổng `135.100` = `PAY-0001`; INV-0005 tổng `377.800` =
   `200.000 + 177.800`; INV-0016 thu `78.000` / phải thu `178.000` nên `OPEN` là đúng). Nghĩa là
   đây **chưa phải một con số sai** — nó là **một con số không có gì giữ**. Sửa một `Quantity` trong
   seed là 18 trạng thái có thể sai mà không một thông báo nào, trong khi `sp_settle_invoice` của
   khối 4 sinh ra để trị đúng việc đó (`plan/13` mục 2.2: *"Đừng ghi 'PAID' ở đây"*).

2. **`Unit_Price` không được bảo đảm khớp `fn_get_product_price(Barcode, Date)`.** Lấy số thật:
   chạy seed ngày **2027-01-03** thì `@new_day` bị kẹp về `2027-01-01` (`:15-16`), nhưng `INV-0012`
   có ngày `CURDATE() − 6 = 2026-12-28`, tức là **trước** mốc giá mới. Dòng hàng của nó ghi
   `8934007 @ 128.000` (giá mới) trong khi `fn_get_product_price('8934007','2026-12-28')` trả
   **120.000** (giá cũ). Cùng lỗi với `8934002` (`19.000` vs `18.000`). Vậy: **chạy seed trong 6
   ngày đầu tháng 1 là hoá đơn mang một đơn giá không tồn tại ở ngày đó** — và "hoá đơn cũ giữ giá
   cũ" là tính năng *ngôi sao* của đề bài (`docs/03-SCHEMA.md:82`).

3. **`Discount` là số gõ tay, không phải hàm của khuyến mãi tại ngày bán.** `plan/14` mục 3.2 đã
   chốt công thức `discount = ROUND(qty × price × pct / 100, 2)` với `pct` là cái
   `sp_get_active_promo` trả tại **ngày bán**. Seed không gọi gì — nó ghi `6600`, `8750`, `17500`,
   `12600`, `13200`, `6300`. Hôm nay đúng; không có gì giữ.

4. **`Return_ID` ghi tay bằng `1` ở cả 3 lượt** (`:206-212`), nên `fn_next_return_id` — hàm khối 3
   dựng riêng cho việc này (`04_routines.sql:175`) — chưa từng được seed dùng, và cột `Lan_tra` mà
   khối 3 thêm vào `sp_get_invoice_returns` (`plan/12` mục 3.5) **chưa bao giờ hiện một giá trị nào
   khác 1**.

5. **Tổng tiền từng hoá đơn nằm trong comment** (`:154` *"INV-0001 : tong 135,100"*, và 17 dòng
   nữa). Comment không chạy, nên nó là bản chép duy nhất **không ai kiểm**.

6. **Seed chạy lần hai là lỗi** — và Setup App có đúng một nút mời làm thế (`SqlTab.java:20-22`).
   `04_routines.sql` thì chạy lại bao nhiêu lần cũng xong nhờ khối `DROP` ở `:17-60`. Hai file cùng
   thư mục, hai hành vi, không ghi chú ở đâu.

7. **Hai đường nạp SQL có thứ tự ngược nhau** (`db-setup.*` là 01→02→04, Setup App là 01+04→02).
   Hôm nay không ai thấy vì seed không gọi routine. Khối này làm seed **gọi** routine, nên một
   trong hai đường sẽ chết nếu không sửa — xem mục 4.1.

**Nhóm B — 90 ngày: cái gì đang thiếu**

8. **Chỉ 7 ngày có dữ liệu.** `sp_report_daily_takings` lọc đúng 7 ngày, nên báo cáo câu 2 **vừa
   khít** phạm vi dữ liệu: không có một ngày nào để so sánh "tuần này với tuần trước", và lùi cửa
   sổ một ngày là báo cáo mất dòng ở biên.

9. **Khoảng trống `−7 .. −399` ngày.** Mọi câu hỏi theo tháng, theo quý, theo mùa đều rỗng. Hai hoá
   đơn `INV-OLD-*` ở `−400` ngày là **chỗ duy nhất** chứng minh giá lịch sử, và chúng nằm ngoài mọi
   khung thời gian mà giao diện hiện ra: tab 3 sắp theo ngày giảm nên chúng ở cuối bảng, cách hoá
   đơn gần nhất **400 ngày trống**.

10. **4 khách hàng, cả 4 cùng tiền tố `09010000`.** `sp_search_customers_by_phone` có `LIMIT 20`
    (`04_routines.sql:310`) — **không bao giờ chạm tới**; gõ `090` ra cả 4; và hợp đồng khách hàng
    của khối 5 (`plan/14` mục 3.5, 346 dòng `CustomerPicker`) chưa từng được thử ở quy mô nào.

11. **10 sản phẩm.** `plan/14` mục 2.3 viết *"chấp nhận được với seed 20 sản phẩm"* — con số đó
    không đúng với file đang có. `cbProduct` của tab 1 nạp cả bảng `Product`, nên 10 hay 20 đều
    chưa phải vấn đề, nhưng **tài liệu và dữ liệu phải nói cùng một con số**.

12. **Một hoá đơn `OPEN` duy nhất, một `VOIDED` duy nhất.** Chip lọc trạng thái mà khối 4 thêm vào
    tab 2 (`plan/13` mục 5.6) ra đúng **1 dòng** cho mỗi trạng thái — đủ để biết nó chạy, không đủ
    để thấy nó có ích.

13. **Hợp đồng tiền mặt của khối 5 gần như không xuất hiện trong seed.** 8 dòng `Cash_Payment`, chỉ
    6 dòng có `Tendered > Amount`, và chênh lớn nhất là `14.900`. Cột `Tien thoi` mà khối 5 thêm
    vào tab 3 và hoá đơn in ra (`plan/14` mục 3.1) gần như luôn nhỏ hoặc bằng 0.

14. **Chỉ `INV-0005` có 2 lượt thanh toán.** Khung số 4 của tab 1 mang tên *"một hoá đơn có thể có
    NHIỀU dòng thanh toán"*, và seed có đúng một ví dụ.

**Nhóm C — khuyến mãi không phủ được 90 ngày**

15. **Cửa sổ khuyến mãi hiện là `±15` / `−10..+20` ngày** (`:108-112`). Mở hoá đơn ra 90 ngày thì
    **khoảng 70 ngày đầu không có khuyến mãi nào**, nên cột `Giam gia` của tab 3 sẽ rỗng ở gần hết
    bảng — và hợp đồng giảm giá của khối 5 không có dữ liệu để kiểm.

16. **`Applies_To` khoá chính `(Barcode, Promo_ID)`** (`01_schema.sql:122`): một sản phẩm **không
    thể** vào cùng một khuyến mãi ở hai cửa sổ ngày khác nhau. Muốn hai đợt khuyến mãi cho cùng một
    sản phẩm thì phải **hai `Promo_ID`** — một ràng buộc lược đồ mà seed hiện tại chưa bao giờ phải
    đối mặt.

17. **`sp_get_active_promo` trộn hai trục thời gian.** `a.Promo_Start_date` / `a.Promo_End_date` là
    sự thật **lịch sử**; `p.Status` là sự thật **hôm nay**. Nên một khuyến mãi đã chạy xong và được
    đánh `'EXPIRED'` thì **tra cứu ở đúng ngày nó đang chạy cũng trả về rỗng**. Với seed 7 ngày
    không ai gặp; với seed 90 ngày đây là câu hỏi đầu tiên phải trả lời — mục 3.3 trả lời nó
    **không cần sửa thủ tục**.

**Nhóm D — những chỗ 90 ngày dữ liệu làm vỡ (hậu quả của chính khối này)**

18. **`sp_get_recent_invoices` không `LIMIT`** (`04_routines.sql:358`): tab 3 nạp **mọi** hoá đơn
    từng có. 18 dòng thành ~550 dòng, trong một bảng không có ô lọc nào (`InvoiceViewPanel:222` chỉ
    có `RowSorter` — sắp xếp, không lọc).

19. **`sp_report_old_price_proof` không có `WHERE`** (`04_routines.sql:682-698`): trả **mọi dòng
    hàng của mọi hoá đơn**. 30 dòng thành ~1.100 dòng, trong một báo cáo mang tên *"chứng minh"*.
    Và bản trong `03_queries.sql:180` **có** `WHERE ... LIKE 'INV-OLD%'` — hai bản chép của cùng
    một báo cáo đã lệch nhau, cái lệch đó chỉ lộ ra khi dữ liệu đủ lớn.

20. **`ReportPanel:88-103` chạy báo cáo trên EDT.** Đúng vấn đề khối 2 đã sửa cho đăng nhập
    (`plan/11` mục 4.2), khối 3 cho trả hàng, khối 5 cho lưu hoá đơn — và là panel duy nhất còn
    lại chưa sửa. Với 7 ngày dữ liệu không ai thấy; với 90 ngày thì thấy.

21. **`SearchPanel:703, 809` `SELECT *` không `LIMIT`.** Chọn bảng `Invoice_Line` là nạp ~1.100
    dòng; chọn `Payment` là ~700 dòng.

22. **Mã `INV-nnnn` / `PAY-nnnn` dùng `LPAD(..., 4, '0')`.** 90 ngày × 6 hoá đơn = 548 hoá đơn và
    ~700 thanh toán — còn xa `9999`. Nhưng người sau tăng tham số lên 365 ngày × 6 là **2.190 hoá
    đơn**, và 365 × 20 là chạm trần. Vượt trần thì mã vẫn sinh ra được (`INV-10000`) và
    `fn_next_invoice_id` vẫn đúng (nó `CAST ... AS UNSIGNED`), nhưng `ORDER BY p.Payment_ID` ở
    `sp_get_invoice_payments:472` là **sắp theo chuỗi**, nên `PAY-10000` đứng **trước** `PAY-9999`.
    Ghi ra đây kèm con số để khối sau không phải tìm lại.

23. **Nếu seed sinh dữ liệu qua Java thì nhật ký SQL của khối 1 bị xoá trôi.** `SqlLog` xoay vòng ở
    **2 MB** (`SqlLog.java:38`); ~550 hoá đơn qua `InvoiceDao` là ~8.000 dòng nhật ký ≈ 800 KB, đẩy
    trôi gần hết nhật ký thật và làm tab 5 của khối 1 vô dụng ngay sau khi nạp. Đây là một trong
    bốn lý do mục 3.1 chọn SQL thay vì Java.

---

## 2. Mục Tiêu & Phạm Vi

### 2.1. Mục tiêu

1. **91 ngày liên tục** (`CURDATE() − 90` đến hôm nay) đều có hoá đơn, **mỗi shop mỗi ngày**, dựng
   bằng **một vòng lặp có tham số** chứ không phải 300 dòng `INSERT` gõ tay.
2. **Mọi con số trong seed thoả đúng những hợp đồng mà thủ tục của app ép — và thoả vì nó *đi qua*
   chính các thủ tục đó**, không vì ai đó tính tay đúng (mục 3.1).
3. **Seed chạy lại được** bao nhiêu lần cũng xong, như `04_routines.sql` (mục 4.2).
4. **Tính đúng đắn của seed không phụ thuộc ngày chạy.** Chỗ duy nhất còn dùng mốc năm là *ngày đổi
   giá*, và nó **không thể làm sai một dòng hàng nào** vì đơn giá được tra bằng
   `fn_get_product_price` tại ngày bán (mục 3.2) — đây là cách sửa lỗi #2.
5. **Năm báo cáo đều có dữ liệu đáng xem**, và mỗi báo cáo có ít nhất một dòng nổi bật **do chủ ý**,
   không do may mắn (mục 4.5).
6. **Dữ liệu xác định (deterministic):** cùng một ngày chạy → cùng một bộ dữ liệu, **từng đồng**.
   Không `RAND()` (mục 3.5).
7. **Mỗi bất biến kiểm được bằng một câu SQL**, và cả bộ kiểm được bằng một lệnh (mục 4.6).
8. **Giao diện vẫn dùng được ở quy mô mới**: tab 3, báo cáo bonus, và `ReportPanel` không còn chặn
   EDT (mục 5.2–5.4).

### 2.2. Nguyên tắc bất di bất dịch

> **Seed là dữ liệu mà app có thể tự tạo ra. Nếu một thủ tục của app từ chối một dòng, seed không
> được có dòng đó.**

Năm khối trước, mỗi khối có một nguyên tắc về **một bản chép**: khối 3 (công thức tiền hoàn chỉ ở
SQL), khối 4 (`InvoiceStatus` một bản), khối 5 (định dạng số một bản). Khối này là mặt thứ ba của
cùng một đồng xu: **dữ liệu mẫu cũng là một bản chép của các quy tắc nghiệp vụ** — nó chép chúng
dưới dạng những con số đã tính sẵn. Cách để nó không thành bản chép thứ hai là **không cho nó tự
tính**: nó gọi hàm, gọi thủ tục, và nhận lại đúng những gì app nhận.

Hệ quả cụ thể:

- **Không `INSERT` thẳng vào `Invoice`, `Invoice_Line`, `Payment`, `Cash_Payment`, `Card_Payment`,
  `EWallet_Payment`, `Return`.** Bảy bảng đó đi qua `sp_create_invoice_header`,
  `sp_add_invoice_line`, `sp_add_payment`, `sp_add_return`, `sp_settle_invoice`, `sp_void_invoice`.
- **Chín bảng danh mục vẫn `INSERT` thẳng** (`Shop`, `Counter`, `Cashier`, `Customer`, `Product`,
  `Price_History`, `Promotion`, `Applies_To`, `Account`): **không có thủ tục nào ghi chúng**, nên
  không có hợp đồng nào để đi qua. Và đây là phần người học *đọc*, nên nó giữ nguyên lối viết
  `INSERT ... VALUES` kèm comment như hiện tại.
- **Không `Status` gõ tay, không `Unit_Price` gõ tay, không `Discount` gõ tay, không `Return_ID` gõ
  tay.** Bốn con số đó có chủ, và chủ của chúng là SQL.
- **Mỗi bất biến phải kiểm được bằng một câu SQL trả về số vi phạm.** Không có bất biến nào chỉ tồn
  tại trong một câu văn.
- Giữ nguyên quy ước năm khối trước: **Additive First** (`plan/06` mục 1.2) — không đổi
  `01_schema.sql`, sửa thủ tục cũ thì sửa tối thiểu; **UI kiểm để thân thiện, SQL kiểm để đúng, SQL
  là chốt cuối** (`plan/12` mục 3.4); **đổi cột trả về là đổi hợp đồng** (`plan/12` mục 3.5).
- **Giữ mọi khoá mà tài liệu đã ghim:** 10 mã vạch `8934001`–`8934010` với đúng tên/đơn vị/thuế
  suất hiện tại, `INV-0001` (có ≥ 3 dòng và ≥ 1 dòng có giảm giá — `03_queries.sql:15-46`),
  `INV-OLD-01` / `INV-OLD-02`, hai mốc giá `130.000` / `158.000` của `8934008`
  (`docs/03-SCHEMA.md:82-83`), `8934008` trong `03_queries.sql:193` và `docs/02-JDBC.md:46`.

### 2.3. Ngoài phạm vi khối này (và vì sao)

| Việc | Vì sao không làm ở khối này |
|---|---|
| Đổi `sql/01_schema.sql` | Không cần một cột nào. Cả năm hợp đồng của mục 3 đều dựng được trên 16 bảng đang có — tôi đã kiểm từng cột. Đây là **kết luận**, không phải bỏ sót |
| Sửa `sp_get_active_promo` thành tra cứu điểm-thời-gian thật (bỏ `p.Status = 'ACTIVE'`) | Mục 3.3 giải quyết được **bằng dữ liệu**, không cần sửa thủ tục. Sửa nó là **đổi hành vi tab 1** (dòng gợi ý khuyến mãi khi đổi "Ngày bán" về quá khứ) — việc của một khối khuyến mãi về sau. Diff một dòng đã ghi sẵn ở mục 3.3 để khối đó không phải tìm lại |
| Thêm `INDEX` cho `Invoice.Date`, `Invoice.Status`, … | Đã kiểm: **mọi** đường truy cập mà seed và app dùng đều đi qua khoá có sẵn — `Invoice_Line` PK `(Invoice_ID, Line_Number)`, `Payment` có index từ `fk_payment_invoice`, `Return` PK `(Invoice_ID, Line_Number, Return_ID)`, `Price_History` PK `(Barcode, Valid_From_Date)`, `Applies_To` PK `(Barcode, Promo_ID)`. Ở ~550 hoá đơn thì thêm index là **tối ưu một thứ chưa đo**. Kịch bản #88 đo thời gian; vượt ngưỡng thì mới là việc của khối sau |
| Phân trang thật cho tab 3 / tab 2 | `LIMIT` + nhãn nói rõ (mục 5.2) là đủ ở quy mô này. Phân trang có nút *Trang sau* là **tính năng mới**, và nó kéo theo `OFFSET` trong hợp đồng thủ tục |
| Đổi `sp_report_daily_takings` từ 7 ngày sang khoảng chọn được | Báo cáo đó **là đề bài câu 2** (`03_queries.sql:50`: *"7 ngày gần nhất"*). Đổi là đổi đề. Khối này làm cho 7 ngày đó có dữ liệu dày, và để 83 ngày còn lại cho người muốn viết câu truy vấn của mình |
| Dùng thư viện sinh dữ liệu (Faker…) | Thêm một phụ thuộc vào một dự án cố ý chỉ có 3 jar (`mysql-connector`, `core`, `javase`) |
| Dữ liệu tiếng Việt **có dấu** trong seed | Giữ không dấu như hiện tại. `docs/05` mục 7.4 đã chốt *"một file dùng một lối viết"*, và `02_seed.sql` đang là không dấu |
| Sinh `Account` thứ ba, thứ tư (nhiều vai trò) | Khối 2 sở hữu đăng nhập; 2 tài khoản đang đủ cho cả hai vai trò |
| Xoá `INV-OLD-01` / `INV-OLD-02` | `03_queries.sql:180`, `ReportPanel:70` và `sp_report_old_price_proof` đều trỏ vào chúng. Giữ — nhưng từ khối này chúng **không còn là bằng chứng duy nhất** về giá lịch sử (mục 3.2) |
| Sửa `plan/14` mục 2.3 (chỗ ghi *"seed 20 sản phẩm"*) | Kế hoạch đã triển khai là **biên bản**, không sửa lại. Khối này làm cho con số đó thành **đúng** (mục 4.3), đó là cách xử lý tử tế hơn |

---

## 3. Năm Hợp Đồng (quan trọng nhất)

Khối 1 có hợp đồng định dạng file, khối 2 hợp đồng cấu hình, khối 3 hợp đồng tính tiền, khối 4 hợp
đồng vòng đời, khối 5 bốn hợp đồng về con số. Khối này có **hợp đồng nguồn sinh, hợp đồng giá, hợp
đồng khuyến mãi lịch sử, hợp đồng vòng đời & tiền, và hợp đồng xác định**. Sửa một bên phải sửa tài
liệu này.

### 3.1. Hợp đồng nguồn sinh: seed đi qua thủ tục của app

```
Bay bang GIAO DICH  (Invoice, Invoice_Line, Payment + 3 bang con, Return)
    -> CHI duoc ghi qua:  sp_create_invoice_header, sp_add_invoice_line,
                          sp_add_payment, sp_settle_invoice,
                          sp_void_invoice, sp_add_return
    -> va cac con so dua vao chung lay tu:  fn_get_product_price,
                          fn_active_promo_pct, fn_invoice_amount_due,
                          fn_next_invoice_id, fn_next_return_id

Chin bang DANH MUC  (Shop, Counter, Cashier, Customer, Product,
                     Price_History, Promotion, Applies_To, Account)
    -> INSERT ... VALUES truc tiep, giu nguyen loi viet + comment hien tai
```

Ba phương án sinh dữ liệu, và vì sao chọn phương án A:

| | Phương án | Kết luận |
|---|---|---|
| **A** | **Thủ tục SQL `sp_seed_demo_data(p_days, p_inv_per_day)`**, định nghĩa → `CALL` → `DROP` ngay trong `02_seed.sql` | **ĐÃ CHỌN.** (1) Không cần JDK để nạp dữ liệu, nên `db-setup.*` và nút *Tạo dữ liệu mẫu* của Setup App vẫn là "pipe một file `.sql`" — **không script nào phải viết lại**, không nút nào phải đổi. (2) `02_seed.sql` vẫn là **một artifact duy nhất** của dữ liệu mẫu, đúng vai trò *"Dev SQL sở hữu `sql/`"* của `docs/05` mục 1. (3) Nhật ký SQL của khối 1 chỉ thấy **một** dòng `CALL sp_seed_demo_data(...)` thay vì ~8.000 dòng (lỗi #23). (4) Đọc được: vòng lặp + các lệnh `CALL` nằm cạnh nhau, người học thấy đúng những thủ tục mà tab 1 gọi |
| B | **Chương trình Java `tools/seed/SeedGen.java`** gọi `InvoiceDao.createInvoice` (chính là `ContractCheck.e2e()` nhân lên) | Loại, dù rất gần. Ưu điểm thật: **không sinh thêm một bản chép nào của bất cứ quy tắc nào** — nó đi đúng đường tab 1 đi. Nhược điểm quyết định: nạp dữ liệu mẫu bỗng cần JDK + biên dịch cả app (hôm nay chỉ cần `mysql.exe`), nút *Tạo dữ liệu mẫu* của Setup App phải đổi sang chạy Java, `02_seed.sql` thành một file rỗng hoặc nửa vời, và nhật ký SQL bị xoá trôi (lỗi #23). Ba cái giá đó không tương xứng với một bản chép của **một** công thức |
| C | **Java sinh ra một `02_seed.sql` toàn `INSERT` literal**, commit vào repo | Loại: một file `.sql` ~2.000 dòng sinh tự động, không comment, là **bước lùi** cho một repo mà cả tính cách là "file dày comment cho người mới đọc". Thêm nữa, việc sinh lại là một bước tay dễ quên, và các literal bị đóng băng theo offset ngày của lần sinh |

**Cái giá của phương án A, nói thẳng:** vòng lặp SQL cần `pct` khuyến mãi dưới dạng **một giá trị**,
mà `sp_get_active_promo` trả về **một bảng** (không dùng được trong SQL thuần). Nên khối này thêm
`fn_active_promo_pct` — và đó là **bản chép thứ hai của điều kiện chọn khuyến mãi**. `plan/14` mục
3.2 đã **loại** `fn_line_discount` đúng vì lý do này. Vì sao kết luận bây giờ khác:

- `plan/14` loại nó vì **khi đó không có code SQL nào cần tính `Discount`** — thêm hàm là đổi *một*
  bản chép thành *hai* mà không được gì. Tiền đề đó **đã đổi**: từ khối này, SQL là nơi sinh ra
  `Invoice_Line.Discount`.
- Hai bản chép không tự xấu; **hai bản chép không có cái chốt** mới xấu. Nên `fn_active_promo_pct`
  đi kèm hai phép kiểm bằng số: kịch bản **#76** (hàm trả đúng cái `sp_get_active_promo` trả, trên
  một lưới mã vạch × ngày) và kịch bản **#77** (`fn_line_discount` của SQL khớp
  `NewInvoicePanel.lineDiscount` của Java, từng đồng, trên một lưới số lượng × giá × phần trăm).
  Đây đúng khuôn *"Chốt bằng số cho khối sau"* của `plan/14` mục 9.4.

### 3.2. Hợp đồng giá: đơn giá của mọi dòng hàng là giá tại ngày bán

```
Bat bien G1:  voi MOI dong hang l va hoa don i cua no:
                  l.Unit_Price = fn_get_product_price(l.Barcode, i.Date)

Cach dat duoc:  vong lap KHONG BAO GIO gan don gia bang hang so.
                No goi fn_get_product_price(barcode, v_date) va dung ket qua.
                Gia tra ve NULL (san pham chua co gia tai ngay do) -> BO QUA
                dong hang nay, khong ghi 0.
```

- **Mốc đổi giá nằm TRONG cửa sổ 90 ngày.** Hôm nay mốc giá mới là `CURDATE() − 60` (bị kẹp theo
  năm) còn hoá đơn chỉ lùi tới `CURDATE() − 6`, nên **không một hoá đơn nào nằm trước mốc đổi giá**:
  "hoá đơn cũ giữ giá cũ" chỉ nhìn thấy được ở hai hoá đơn `−400` ngày. Từ khối này, mốc đổi giá là
  `CURDATE() − 45`, nằm **giữa** cửa sổ hoá đơn, nên **cùng một sản phẩm xuất hiện ở hai đơn giá
  trên hai hoá đơn mà người dùng thấy cạnh nhau trong tab 3**. Đó là tính năng ngôi sao của đề bài,
  lần đầu tiên hiển thị được mà không phải lùi 400 ngày.
- **Phép kẹp theo năm còn lại, và nó vô hại — ghi rõ ở đây để không ai "dọn dẹp" nó.**
  `sp_report_price_rise` lọc `YEAR(Valid_From_Date) = YEAR(CURDATE())` (`04_routines.sql:625`), nên
  mốc đổi giá **phải** nằm trong năm dương lịch hiện tại, nếu không báo cáo câu 3 rỗng. Vậy mốc giá
  giữ công thức `GREATEST(CURDATE() − 45, <1 tháng 1 năm nay>)`. Trong 45 ngày đầu năm nó bị kẹp về
  1 tháng 1 — và **điều đó không làm sai một dòng hàng nào**, vì bất biến G1 tra giá *tại ngày bán*
  thay vì giả định sản phẩm nào mang giá nào. Cái nó đổi chỉ là **tỷ lệ** hoá đơn mang giá cũ. So
  với lỗi #2: ở đó cùng một phép kẹp làm sai dữ liệu, vì đơn giá là hằng số gõ tay.
- **Mọi mốc ngày khác là `CURDATE() − n`**, không `MAKEDATE`, không `YEAR()`. Một chỗ dùng mốc năm,
  có comment trỏ về mục này.
- Giữ `8934008` ở đúng hai mốc `130.000` → `158.000` (`docs/03-SCHEMA.md:82-83` ghim cặp số đó).

### 3.3. Hợp đồng khuyến mãi lịch sử: `Discount` là hàm của (sản phẩm, ngày, số lượng)

```
Bat bien G2:  voi MOI dong hang l va hoa don i cua no:
                  pct = fn_active_promo_pct(l.Barcode, i.Date)
                  l.Discount = ROUND(l.Quantity * l.Unit_Price * pct / 100, 2)
              (pct = 0 khi khong co khuyen mai -> Discount = 0)

fn_active_promo_pct tra ve DUNG phan tram ma sp_get_active_promo tra ve:
    MAX(a.Discount_Percent)  tren cung dieu kien WHERE
    (ORDER BY ... DESC LIMIT 1 == MAX khi chi lay mot cot)
```

Đây là cùng một công thức với `plan/14` mục 3.2 — **cố ý**, vì tab 1 và seed phải sinh ra cùng một
loại dữ liệu. Kịch bản #77 chốt hai bản bằng số.

**Vấn đề thật: một khuyến mãi đã kết thúc thì tra cứu ở quá khứ cũng rỗng** (lỗi #17). Ba phương án:

| | Phương án | Kết luận |
|---|---|---|
| **A** | **`Promotion.Expiry_Date` ở TƯƠNG LAI, cửa sổ `Applies_To` ở QUÁ KHỨ.** Một "chương trình" còn hiệu lực đăng ký, nhưng *đợt áp dụng cho sản phẩm này* đã xong | **ĐÃ CHỌN.** Kiểm lại từng điều kiện của `sp_get_active_promo` với promo `Status='ACTIVE'`, `Expiry_Date = CURDATE()+30`, cửa sổ `−70..−40`: tra ở ngày `−60` → cửa sổ ✓, `Status='ACTIVE'` ✓, `Expiry_Date ≥ p_date` ✓ → **trả về phần trăm**. Tra ở hôm nay → cửa sổ ✗ → **rỗng**. Đúng y nghĩa muốn có, **không sửa một dòng thủ tục nào**, và nó đọc đúng theo lược đồ: mức giảm và cửa sổ ngày nằm ở `Applies_To` vì *"mức giảm phụ thuộc vào CẢ CẶP"* (`01_schema.sql:111-115`), còn `Expiry_Date` là hạn của **chương trình** |
| B | `Status='ACTIVE'` + `Expiry_Date` trong quá khứ | Loại: cũng chạy (thủ tục so `Expiry_Date >= p_date`, không so với `CURDATE()`), nhưng một dòng `Promotion` ghi `ACTIVE` với hạn đã qua là **nói sai với người đọc bảng** — và tab 2 có bộ lọc trạng thái khuyến mãi |
| C | Sửa `sp_get_active_promo` bỏ `p.Status = 'ACTIVE'`, chỉ dựa `Expiry_Date >= p_date` | Loại **ở khối này** (mục 2.3): nó đổi hành vi tab 1. Diff để sẵn cho khối khuyến mãi về sau: bỏ dòng `AND p.Status = 'ACTIVE'` và thay bằng `AND (p.Status <> 'PAUSED' OR a.Promo_End_date < CURDATE())` — *tạm dừng là sự thật hôm nay, đã chạy xong là sự thật lịch sử*. Kèm theo: bất biến G2 phải chạy lại sau khi đổi, vì nó sẽ đổi `pct` của các ngày quá khứ |

Thiết kế bảng khuyến mãi theo phương án A, **sáu chương trình phủ 91 ngày** (chi tiết ở mục 4.4):

- **hai cửa sổ gối nhau trên cùng một sản phẩm** với hai phần trăm khác nhau → `ORDER BY
  Discount_Percent DESC LIMIT 1` (*"khuyến mãi tốt nhất"*) lần đầu tiên **thật sự phải chọn**. Hôm
  nay không có dữ liệu nào bắt nó chọn.
- **một chương trình `PAUSED` với cửa sổ nằm GIỮA vùng hoá đơn** → có khuyến mãi đúng ngày đó, phủ
  đúng sản phẩm đó, mà `Discount` vẫn bằng 0 vì `Status` loại nó. Bất biến G2 **vẫn đúng**, vì cả
  seed và phép kiểm đều hỏi cùng một hàm. Đây là dữ liệu dạy học tốt nhất trong cả bảng.
- **một chương trình `EXPIRED`** (giữ `PR03` hiện tại) với cửa sổ **ngoài** vùng hoá đơn → không
  dòng hàng nào phụ thuộc nó, nên nó minh hoạ bộ lọc `Status` mà không làm bất biến nào lung lay.
- `Applies_To` khoá chính `(Barcode, Promo_ID)` (lỗi #16) ⟶ hai đợt cho cùng một sản phẩm **phải**
  là hai `Promo_ID`. Đã tính vào thiết kế.

### 3.4. Hợp đồng vòng đời & tiền: bốn con số không ai được gõ tay

```
Bat bien G3 (vong doi -- khoi 4):
    Status='PAID'   => co dong hang  AND  fn_invoice_amount_paid >= fn_invoice_amount_due
    Status='OPEN'   => fn_invoice_amount_paid <  fn_invoice_amount_due   (hoac chua co dong hang)
    Status='VOIDED' => chi den tu sp_void_invoice

Bat bien G4 (tien mat -- khoi 5, plan/14 muc 3.1):
    moi Cash_Payment:  Tendered_Amount >= Payment.Amount
    the / vi dien tu:  khong co dong Cash_Payment nao

Bat bien G5 (thua ke Payment -- 01_schema.sql:213-216):
    moi dong Payment nam o DUNG MOT trong ba bang con

Bat bien G6 (tra hang -- khoi 3):
    SUM(Return_Quantity) theo tung dong  <=  Invoice_Line.Quantity
    Invoice.Date  <=  Return_Date  <=  CURDATE()
    chi hoa don Status='PAID' moi co luot tra
    Return_ID cua moi dong lien tuc tu 1
```

Cách đạt được — không phải bằng cách tính cẩn thận, mà bằng cách **không tự tính**:

- `Status`: vòng lặp **không bao giờ** `UPDATE Invoice SET Status`. Nó gọi `sp_settle_invoice` sau
  khi ghi xong thanh toán; hoá đơn nào trả thiếu thì **tự** ở lại `OPEN`. `VOIDED` chỉ qua
  `sp_void_invoice`. Ba trạng thái trở thành **kết luận**, không phải dữ liệu vào.
- `recorded` (`Payment.Amount`): vòng lặp đọc `fn_invoice_amount_due(id)` **sau** khi đã ghi hết
  dòng hàng, rồi chia theo đúng công thức `recorded = min(tendered, còn thiếu)` của `plan/14` mục
  3.1. Không bao giờ `paid > due`.
- `tendered` (`Cash_Payment.Tendered_Amount`): **làm tròn LÊN tới tờ 10.000 gần nhất** —
  `CEILING(recorded / 10000) * 10000`. Hai cái được cùng lúc: G4 đúng **bởi cấu tạo** (một phép làm
  tròn lên không thể cho ra số nhỏ hơn), và tiền thối ra là con số thật mà một người đưa tiền mặt
  sẽ tạo ra. Mỗi hoá đơn tiền mặt từ đây có `Tien thoi` khác 0 (lỗi #13).
- `Return_ID`: do `sp_add_return` gọi `fn_next_return_id` (lỗi #4). Vòng lặp **không truyền**
  `Return_ID`. Nhờ vậy có thể gọi `sp_add_return` **hai lần cho cùng một dòng** và được `1` rồi
  `2` — cột `Lan_tra` của khối 3 lần đầu hiện số khác 1.
- Trả hàng chỉ nhắm vào hoá đơn đã `PAID` và ngày trả nằm trong `[Date, CURDATE()]`; `sp_add_return`
  là **chốt cuối** và nó `SIGNAL` nếu sai — tức là **seed sai thì seed chết ngay**, không âm thầm
  sinh ra dữ liệu bẩn (mục 4.2 chọn autocommit để thấy nó chết ở hoá đơn nào).

### 3.5. Hợp đồng xác định: cùng ngày chạy, cùng từng đồng

```
Moi lua chon "ngau nhien" la mot bieu thuc MOD tren chi so vong lap:
    gio      = 7 + MOD(d * 7 + k * 13, 11)          -> 07h..17h
    phut     = MOD(d * 29 + k * 17, 60)
    quay     = MOD(k - 1, so_quay) + 1              -> moi shop moi ngay
    thu ngan = MOD(d + k, 5) + 1
    khach    = NULL khi MOD(d * 3 + k, 3) = 0, nguoc lai MOD(d * 11 + k * 7, 30) + 1
    so dong  = 1 + MOD(d + k, 3)                    -> 1..3
    san pham = MOD(d * 3 + k * 7 + j * 11, 20) + 1
```

- **Không `RAND()`, kể cả `RAND(hằng số)`.** Hai lý do: tính xác định của `RAND(seed)` **không**
  được MySQL bảo đảm giữa các phiên bản (nó bảo đảm trong một phiên bản), và một biểu thức `MOD`
  thì **đọc được** — người học thấy ngay vì sao hoá đơn này ở quầy C03.
- **Hệ quả kiểm thử:** mọi con số trong mục 7 là con số *chốt được*. Chạy lại seed hai lần trong
  cùng một ngày phải cho **hai database giống nhau từng dòng** (kịch bản #85 kiểm bằng
  `CHECKSUM TABLE`).
- **Hệ quả tài liệu:** các con số trích trong `README.md` / `docs/` chỉ được trích ở dạng *"khoảng
  550 hoá đơn"*, không phải *"548"* — vì `p_days` và `p_inv_per_day` là tham số, và ngày chạy đổi
  thì số ngày trong năm nay cũng đổi.
- `MOD` trên chỉ số làm cho các lựa chọn **trải đều một cách có chu kỳ**. Chu kỳ là cái giá: cộng
  dồn 91 ngày thì mỗi thu ngân nhận gần như bằng nhau, nên `sp_report_best_cashier` sẽ cho 5 dòng
  gần bằng nhau. Mục 4.5 xử lý bằng một phép lệch **có chủ ý**, không bằng cách thêm `RAND`.

---

## 4. Thiết Kế SQL

### 4.1. Thứ tự nạp: 01 → 04 → 02 (bắt buộc)

Từ khối này, `02_seed.sql` **gọi** `fn_get_product_price`, `fn_active_promo_pct`,
`fn_invoice_amount_due`, `fn_next_invoice_id`, `sp_create_invoice_header`, `sp_add_invoice_line`,
`sp_add_payment`, `sp_settle_invoice`, `sp_void_invoice`, `sp_add_return`. Nên routine **phải** có
trước seed. Hai chỗ phải sửa:

```powershell
# scripts/ps1/db-setup.ps1:57-59  -- TRUOC
Invoke-SqlFile '.\sql\01_schema.sql'   'Buoc 1/3: tao 15 bang'
Invoke-SqlFile '.\sql\02_seed.sql'     'Buoc 2/3: nap du lieu mau'
Invoke-SqlFile '.\sql\04_routines.sql' 'Buoc 3/3: tao cac ham va thu tuc SQL'

# SAU  -- doi cho buoc 2 va 3, va sua nhan "15 bang" -> "16 bang"
Invoke-SqlFile '.\sql\01_schema.sql'   'Buoc 1/3: tao 16 bang'
Invoke-SqlFile '.\sql\04_routines.sql' 'Buoc 2/3: tao cac ham va thu tuc SQL'
Invoke-SqlFile '.\sql\02_seed.sql'     'Buoc 3/3: nap du lieu mau (90 ngay)'
```

Y hệt cho `scripts/bash/db-setup.sh:46-53`.

Hai điểm đáng ghi:

1. **`sql-create-db.*` + `sql-seed-data.*` (đường của Setup App) KHÔNG phải sửa** — chúng đã nạp
   `01 + 04` rồi mới `02` (lỗi #7). Nên việc sửa `db-setup.*` không chỉ là một điều kiện kỹ thuật:
   nó làm **hai đường nạp của dự án thống nhất thứ tự**, và đó là một lỗi có từ trước khối này.
2. **Nút *Tạo dữ liệu mẫu* của Setup App không phải đổi một dòng nào** (`SqlTab.java:20-22`). Đây
   là lý do (1) của mục 3.1: giữ seed ở dạng "một file `.sql` nạp bằng `mysql`".

### 4.2. Khối dọn dẹp ở đầu `02_seed.sql` (seed chạy lại được)

`04_routines.sql` có khối `DROP ... IF EXISTS` ở `:17-60`; `02_seed.sql` cần khối tương ứng để sửa
lỗi #6. Thứ tự **ngược chiều khoá ngoại** — con trước, cha sau:

```sql
-- ---------------------------------------------------------------------
-- 0. DON SACH -- de file nay chay lai duoc bao nhieu lan cung xong,
--    dung nhu khoi DROP o dau 04_routines.sql.
--    Thu tu la NGUOC CHIEU KHOA NGOAI: bang con truoc, bang cha sau.
--    Doc tu duoi len la doc dung thu tu CREATE TABLE cua 01_schema.sql.
-- ---------------------------------------------------------------------
DELETE FROM `Return`;             -- con cua Invoice_Line
DELETE FROM Cash_Payment;         -- 3 bang con cua Payment
DELETE FROM Card_Payment;
DELETE FROM EWallet_Payment;
DELETE FROM Payment;              -- con cua Invoice
DELETE FROM Invoice_Line;         -- con cua Invoice (va cua Product)
DELETE FROM Invoice;
DELETE FROM Account;              -- con cua Cashier
DELETE FROM Applies_To;           -- con cua Product va Promotion
DELETE FROM Promotion;
DELETE FROM Price_History;        -- con cua Product
DELETE FROM Product;
DELETE FROM Customer;
DELETE FROM Counter;              -- con cua Shop
DELETE FROM Shop;

-- Cashier TU THAM CHIEU chinh no (Supervisor_ID -> Cashier_ID), nen mot
-- cau DELETE duy nhat se that bai: InnoDB kiem khoa ngoai TUNG DONG, va
-- xoa E01 trong khi E02..E05 con tro vao no la vi pham.
-- Thao lien ket truoc, roi moi xoa.
UPDATE Cashier SET Supervisor_ID = NULL;
DELETE FROM Cashier;
```

| | Phương án dọn dẹp | Kết luận |
|---|---|---|
| **A** | **`DELETE` theo thứ tự ngược khoá ngoại + `UPDATE ... SET Supervisor_ID = NULL`** | **ĐÃ CHỌN.** `01_schema.sql:5-8` mở đầu bằng đúng bài học này (*"bảng cha phải có trước bảng con"*); khối dọn dẹp đọc từ dưới lên **là** thứ tự `CREATE TABLE`, nên nó dạy lại bài đó miễn phí. Và nó **không tắt** một ràng buộc nào, nên nếu lược đồ thêm khoá ngoại mà ai đó quên sửa khối này thì nó **báo lỗi** |
| B | `SET FOREIGN_KEY_CHECKS = 0; ... = 1;` | Loại: ngắn hơn 14 dòng, nhưng dạy người học tắt toàn vẹn dữ liệu để đỡ phải nghĩ về thứ tự — ngược hẳn mục đích của repo. Và nó **im lặng** khi thứ tự sai |
| C | `TRUNCATE TABLE` | **Không dùng được:** MySQL từ chối `TRUNCATE` trên bảng đang được một khoá ngoại tham chiếu |

**Không bọc transaction cho cả lần sinh.** `sp_add_return` và `sp_settle_invoice` đều `SIGNAL` khi
dữ liệu sai, nên một lần nạp thất bại giữa đường là chuyện có thật và **đáng xảy ra**. Để
autocommit thì thông điệp lỗi đi kèm một database dừng lại **đúng ở hoá đơn gây lỗi** — đếm
`SELECT COUNT(*) FROM Invoice` là biết nó chết ở vòng nào. Bọc một transaction 8.000 lệnh thì lỗi
nào cũng cho ra một database rỗng và không một dấu vết nào về chỗ sai. Khối dọn dẹp ở trên làm cho
trạng thái nửa vời đó vô hại: lần chạy sau xoá sạch rồi làm lại.

### 4.3. Danh mục: từ 10 sản phẩm / 4 khách lên 20 / 30

Phần này vẫn là `INSERT ... VALUES` gõ tay, giữ nguyên lối viết và comment hiện tại (mục 2.2).

| Bảng | Hiện tại | Sau khối này | Vì sao |
|---|---|---|---|
| `Shop` | 3 | **3** (`S01`–`S03`) | Đủ cho bảng chéo (shop × ngày) của báo cáo câu 2 |
| `Counter` | 4 | **6** (`C01`–`C06`, **2 quầy mỗi shop**) | Để `MOD(k-1, 6)` cho **mỗi shop 2 hoá đơn mỗi ngày** → bảng chéo của báo cáo câu 2 **không có ô rỗng ngày nào** (mục 3.5) |
| `Cashier` | 5 | **5** (`E01`–`E05`, `E01` là sếp) | `Account` ghim `E01` / `E02` (`02_seed.sql:271-273`); giữ nguyên |
| `Customer` | 4 | **30**, trong đó **22 số bắt đầu `09`** | `sp_search_customers_by_phone` có `LIMIT 20`: gõ `09` phải trả **đúng 20 dòng** (chạm trần — lỗi #10), gõ `091` trả ít hơn. Bốn khách `KH001`–`KH004` giữ nguyên tên + số. Tám số còn lại dùng tiền tố `03`, `07`, `08` để ô tìm kiếm có nhiều nhánh |
| `Product` | 10 | **20** (`8934001`–`8934020`) | `plan/14` mục 2.3 đã giả định 20 (lỗi #11); 10 mã cũ giữ **y nguyên** tên / `Unit` / `Tax_Rate`. 10 mã mới trải đủ 5 giá trị `Unit` và 3 mức `Tax_Rate` (5 / 8 / 10) |
| `Price_History` | 18 | **28** | 20 mốc "giá cũ" ở `CURDATE() − 400`; **8 mốc "giá mới"** ở ngày đổi giá (mục 3.2) cho đúng 8 sản phẩm cũ, giữ nguyên phân bố **5 sản phẩm tăng > 10%** (`001`, `003`, `006`, `008`, `010`), **3 tăng < 10%** (`002`, `004`, `007`), **2 chưa đổi giá** (`005`, `009`). 10 sản phẩm mới mỗi cái **một** mốc giá → cũng thuộc nhóm "chưa đổi giá" |
| `Promotion` | 3 | **6** | Mục 4.4 |
| `Applies_To` | 4 | **9** | Mục 4.4 |
| `Account` | 2 | **2** | Khối 2 sở hữu; không đổi |

Hai mốc giá của `8934008` giữ đúng `130.000` → `158.000` (`docs/03-SCHEMA.md:82-83`).

### 4.4. Sáu chương trình khuyến mãi phủ 91 ngày

Theo phương án A của mục 3.3: chương trình nào có cửa sổ đã qua thì **`Expiry_Date` vẫn ở tương
lai**, và comment phải nói ra vì sao.

| Promo | Status | `Expiry_Date` | Cửa sổ `Applies_To` (ngày, so với hôm nay) | Sản phẩm | % | Mục đích |
|---|---|---|---|---|---|---|
| `PR01` | `ACTIVE` | `+30` | `−15 .. +15` | `8934001`, `8934010` | 10 | **Giữ nguyên hiện tại.** Khuyến mãi đang chạy hôm nay |
| `PR02` | `ACTIVE` | `+60` | `−10 .. +20` | `8934003` | 5 | **Giữ nguyên hiện tại** |
| `PR03` | `EXPIRED` | `−100` | `−130 .. −100` | `8934007` | 15 | **Giữ nguyên hiện tại.** Cửa sổ **ngoài** vùng 91 ngày → không dòng hàng nào phụ thuộc nó (mục 3.3) |
| `PR04` | `ACTIVE` | `+45` | `−90 .. −46` | `8934001`, `8934004` | 8 | **Đợt đã xong nhưng tra cứu lịch sử vẫn đúng** — trái tim của phương án A. Phủ nửa đầu cửa sổ, nên `INV-0001` (hoá đơn cũ nhất) có khuyến mãi (`03_queries.sql:15-46` cần nó) |
| `PR05` | `ACTIVE` | `+45` | `−60 .. −30` | `8934001` | 12 | **Gối lên `PR04` 15 ngày trên cùng `8934001`.** Trong 15 ngày đó `sp_get_active_promo` phải **chọn** 12% chứ không 8% — lần đầu tiên `ORDER BY Discount_Percent DESC LIMIT 1` thật sự làm việc gì. Phải là `Promo_ID` khác vì `Applies_To` khoá chính `(Barcode, Promo_ID)` (lỗi #16) |
| `PR06` | `PAUSED` | `+90` | `−45 .. −20` | `8934006`, `8934012` | 20 | **Có khuyến mãi, đúng ngày, đúng sản phẩm, mức cao nhất bảng — mà `Discount` vẫn 0** vì `Status` loại nó. Dữ liệu dạy học: "bảng có dòng" ≠ "quy tắc áp dụng". Bất biến G2 vẫn đúng vì seed và phép kiểm hỏi **cùng một hàm** |

Mỗi dòng `Applies_To` nhận một comment nêu mục đích; `PR04`/`PR05` thêm một dòng giải thích vì sao
`Expiry_Date` ở tương lai trong khi cửa sổ đã qua (trỏ về mục 3.3).

### 4.5. `sp_seed_demo_data` — vòng lặp sinh hoá đơn

Định nghĩa → `CALL` → `DROP` **ngay trong `02_seed.sql`**:

```sql
DELIMITER $$

-- ---------------------------------------------------------------------
-- sp_seed_demo_data: sinh hoa don cho p_days ngay gan nhat.
--
--   KHONG phai mot thu tuc cua app: no duoc tao, goi, roi XOA ngay trong
--   file nay. Lam vay vi (a) app khong bao gio goi no nen no khong thuoc
--   hop dong nao trong docs/05 muc 6, va (b) de lai mot thu tuc SINH DU
--   LIEU trong database la mot cai bay: ai goi hai lan la du lieu nhan doi.
--
--   No KHONG INSERT truc tiep vao bay bang giao dich. Moi dong di qua dung
--   nhung thu tuc ma tab 1 di qua, va moi con so lay tu dung nhung ham ma
--   tab 1 goi -- plan/15 muc 3.1.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_seed_demo_data(
    IN p_days        INT,        -- so ngay lui ve qua khu (90)
    IN p_inv_per_day INT         -- so hoa don moi ngay   (6 = 2 moi shop)
)
BEGIN
    DECLARE v_d, v_k, v_j, v_lines  INT;
    DECLARE v_id       VARCHAR(30);
    DECLARE v_date     DATE;
    DECLARE v_time     TIME;
    DECLARE v_barcode  VARCHAR(20);
    DECLARE v_price    DECIMAL(12,2);
    DECLARE v_pct      DECIMAL(5,2);
    DECLARE v_qty      DECIMAL(10,3);
    DECLARE v_due, v_recorded, v_tendered DECIMAL(12,2);
    ...
END$$

DELIMITER ;

-- 91 ngay (hom nay va 90 ngay truoc) x 6 hoa don = khoang 550 hoa don.
CALL sp_seed_demo_data(90, 6);

DROP PROCEDURE sp_seed_demo_data;
```

**Thân vòng lặp, theo đúng thứ tự mà `InvoiceDao.createInvoice` làm** (`plan/14` kịch bản #40):

1. `v_id = fn_next_invoice_id()` — dùng hàm của app, không tự `CONCAT('INV-', ...)`.
2. `CALL sp_create_invoice_header(v_id, v_date, v_time, quầy, thu ngân, khách)` → hoá đơn ra đời ở
   `'OPEN'`, đúng `04_routines.sql:707-709`.
3. Với mỗi dòng `v_j` trong `1 .. v_lines`:
   - chọn `v_barcode` theo công thức `MOD` của mục 3.5;
   - `v_price = fn_get_product_price(v_barcode, v_date)` — **bất biến G1**. `NULL` → bỏ dòng này
     (sản phẩm chưa có giá tại ngày đó), **không** ghi 0;
   - `v_qty`: số nguyên `1..3` cho `PIECE` / `PACK` / `BOX`; thêm hai giá trị `0.500` và `1.500`
     cho `KG` / `LITER` — đọc `Product.Unit` bằng một `SELECT ... INTO`. Nhờ vậy `Ui.qty` và cột
     `SL` của khối 5 có dữ liệu thập phân **thật** trải khắp 91 ngày, không chỉ ở `INV-0002`;
   - `v_pct = fn_active_promo_pct(v_barcode, v_date)`;
   - `v_discount = ROUND(v_qty * v_price * v_pct / 100, 2)` — **bất biến G2**;
   - `CALL sp_add_invoice_line(v_id, v_j, v_barcode, v_qty, v_price, v_discount)`.
   - **Gộp dòng:** không gộp. Hợp đồng giảm giá của khối 5 (`plan/14` mục 3.2) cho phép hai dòng
     cùng mã vạch khi đơn giá khác nhau, và ở đây mỗi dòng là một `Line_Number` riêng — đúng lược
     đồ (`01_schema.sql:168`). Công thức `MOD` có thể cho cùng một mã vạch hai lần trong một hoá
     đơn, và **đó là dữ liệu có ích**: nó bắt hộp thoại trả hàng của khối 3 (`plan/12` lỗi #9) gặp
     đúng trường hợp nó được sửa để xử lý.
4. `v_due = fn_invoice_amount_due(v_id)` — **sau** khi đã ghi hết dòng hàng.
5. Thanh toán, theo `plan/14` mục 3.1:
   - `MOD(v_d + v_k, 7) = 0` → **trả thiếu có chủ ý**: ghi một dòng tiền mặt bằng ~60% `v_due`
     → hoá đơn **tự** ở lại `'OPEN'`. ~78 hoá đơn `OPEN` trải đều 91 ngày (lỗi #12);
   - `MOD(v_d + v_k, 5) = 0` → **hai lượt thanh toán** (tiền mặt một phần, rồi thẻ hoặc ví cho phần
     còn thiếu) — lỗi #14;
   - còn lại → một lượt, phương thức theo `MOD(v_d * 3 + v_k, 3)`: `CASH` / `CARD` / `EWALLET`;
   - **tiền mặt:** `v_recorded = LEAST(phần muốn trả, v_due − đã ghi nhận)`;
     `v_tendered = CEILING(v_recorded / 10000) * 10000` → **bất biến G4 đúng bởi cấu tạo**
     (mục 3.4). `CALL sp_add_payment(fn_next_payment_id(), v_id, v_recorded, 'CASH',
     CAST(v_tendered AS CHAR), NULL)` — `detail1` là chuỗi, đúng hợp đồng 6 tham số của
     `sp_add_payment` mà `plan/14` mục 4.6 đã chốt giữ nguyên;
   - **thẻ:** số thẻ và `Auth_code` sinh từ chỉ số (`CONCAT('4111-****-****-', LPAD(...))`);
     **ví:** `Wallet_provider` lấy theo `MOD` từ `MoMo` / `ZaloPay` / `VNPay`, `Transaction_Ref`
     **phải duy nhất** (`01_schema.sql:240` có `uq_wallet_txn`) → ghép chỉ số vào.
6. `CALL sp_settle_invoice(v_id)` → `'PAID'` **nếu** đã thu đủ. **Bất biến G3 đúng bởi cấu tạo**:
   không một dòng nào trong seed `UPDATE Invoice SET Status`.
7. `MOD(v_d * v_k, 37) = 0` **và** hoá đơn đã `PAID` → `CALL sp_void_invoice(v_id)`. ~15 hoá đơn
   `VOIDED` (lỗi #12).
8. `MOD(v_d + v_k * 3, 12) = 0` **và** hoá đơn đã `PAID` → **trả hàng**:
   - trả một phần dòng 1 (`sp_add_return` tự sinh `Return_ID = 1`);
   - `MOD(v_d, 48) = 0` → gọi **lần thứ hai** cho cùng dòng đó → `Return_ID = 2`, cột `Lan_tra` của
     khối 3 lần đầu hiện `2` (lỗi #4);
   - `MOD(v_d, 29) = 0` → trả **toàn bộ** mọi dòng → hoá đơn vào `sp_report_big_returns` (> 50%);
   - `Return_Date = v_date + MOD(v_k, 3)` ngày, **kẹp không vượt `CURDATE()`** → bất biến G6.
9. **Phép lệch có chủ ý cho báo cáo câu 4** (mục 3.5 đã nêu chu kỳ `MOD` làm 5 thu ngân bằng nhau):
   thu ngân `E03` nhận `v_lines + 1` dòng hàng. Một dòng code, và `sp_report_best_cashier` có một
   dòng đứng đầu rõ ràng **do chủ ý**, không do may mắn.

**Hai hoá đơn `INV-OLD-01` / `INV-OLD-02`** ở `CURDATE() − 400`: tạo **trước** vòng lặp, cũng **qua
các thủ tục**, truyền mã tường minh cho `sp_create_invoice_header`. `fn_next_invoice_id` bỏ qua
chúng (`REGEXP '^INV-[0-9]+$'` — `04_routines.sql:106`) nên `INV-0001` vẫn là hoá đơn đầu của vòng
lặp. Giữ `8934001` và `8934008` ở giá cũ, đúng như `ReportPanel:70` và `03_queries.sql:180` đang
nói.

**`INV-0001` phải có ≥ 3 dòng và ≥ 1 dòng có giảm giá** (`03_queries.sql:15-46` lấy nó làm ví dụ
mẫu, `docs/03-SCHEMA.md:28` trích nó). Vòng lặp bắt đầu từ ngày cũ nhất (`v_d = p_days`), nên
`INV-0001` là hoá đơn của ngày `−90`: `PR04` phủ `−90..−46` cho `8934001` và `8934004` (mục 4.4),
và `v_lines` của vòng đầu phải là 3 — **ép tường minh cho `v_d = p_days AND v_k = 1`** kèm comment
trỏ về hai chỗ ghim ở trên. Kịch bản #78 kiểm.

### 4.6. Mới: `fn_active_promo_pct` (vào `04_routines.sql`)

Thêm một dòng `DROP` vào đúng nhóm (`04_routines.sql:18-24`):

```sql
DROP FUNCTION IF EXISTS fn_active_promo_pct;    -- vao nhom dong 18-24
```

Và hàm, đặt ngay sau `fn_get_product_price` (PHẦN 1, khoảng dòng 91) vì nó là cặp song sinh của
hàm đó — *giá tại ngày bán* và *phần trăm giảm tại ngày bán*:

```sql
-- ---------------------------------------------------------------------
-- 1.2 fn_active_promo_pct: Phan tram giam cua khuyen mai TOT NHAT dang ap
--     dung cho san pham tai mot ngay. Khong co khuyen mai -> 0.00.
--
--     DAY LA BAN CHEP THU HAI cua dieu kien chon khuyen mai: ban thu nhat
--     la sp_get_active_promo (muc 2.6). plan/14 muc 3.2 da tu choi tao ban
--     thu hai nay -- va luc do la dung, vi khi do KHONG CO code SQL nao can
--     tinh Discount. Tien de do doi o khoi 6: seed sinh Invoice_Line.Discount
--     bang SQL (plan/15 muc 3.1).
--
--     Hai ban chep nay co CHOT BANG SO, khong phai co loi hua:
--       - kich ban #76: ham tra dung cai sp_get_active_promo tra, tren mot
--         luoi ma vach x ngay
--       - kich ban #77: cong thuc giam gia cua SQL khop
--         NewInvoicePanel.lineDiscount cua Java, tung dong
--     Sua mot ban thi hai kich ban do sai ngay. DUNG sua mot ben.
--
--     Vi sao MAX(...) chu khong ORDER BY ... LIMIT 1: khi chi lay MOT cot
--     thi hai cach cho cung ket qua, va MAX() doc duoc trong mot cau SELECT
--     INTO. sp_get_active_promo phai dung ORDER BY vi no con tra Promo_ID
--     va Rule_Description.
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_active_promo_pct(
    p_barcode VARCHAR(20),
    p_date    DATE
)
RETURNS DECIMAL(5,2)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_pct DECIMAL(5,2) DEFAULT NULL;

    SELECT MAX(a.Discount_Percent)
    INTO   v_pct
    FROM   Applies_To a
    JOIN   Promotion  p ON p.Promo_ID = a.Promo_ID
    WHERE  a.Barcode = p_barcode
      AND  p_date BETWEEN a.Promo_Start_date AND a.Promo_End_date
      AND  p.Status = 'ACTIVE'
      AND  p.Expiry_Date >= p_date;

    RETURN COALESCE(v_pct, 0.00);
END$$
```

Các mục comment sau nó trong PHẦN 1 **dời số thứ tự** (`fn_next_invoice_id` 1.2 → 1.3, …,
`fn_invoice_amount_paid` 1.7 → 1.8). Chỉ là comment, nhưng `docs/05` mục 6 trích theo nhóm.

**Tuỳ chọn, cùng lúc:** `fn_line_discount(p_barcode, p_date, p_quantity, p_unit_price)` gói trọn
công thức `ROUND(qty × price × pct / 100, 2)`. Nên làm: nó biến bất biến G2 từ một biểu thức viết ở
hai chỗ (vòng lặp và phép kiểm) thành **một lời gọi hàm ở cả hai chỗ**, và kịch bản #77 so thẳng nó
với `NewInvoicePanel.lineDiscount`.

### 4.7. Mới: `sql/05_invariants.sql` — chốt bằng số cho dữ liệu

Khối 5 chốt bốn hợp đồng giao diện bằng `tools/check/ContractCheck.java`. Khối này chốt năm hợp
đồng **dữ liệu** bằng một file SQL — vì bất biến về dữ liệu thì kiểm bằng SQL là ngắn nhất, và chạy
được **bất cứ lúc nào**, kể cả sau khi người dùng đã bấm chuột làm bẩn database.

```sql
-- =====================================================================
--  05_invariants.sql -- 14 bat bien cua du lieu, va mot thu tuc de kiem
--  Chay bang:  mysql -u root -p retail_billing < 05_invariants.sql
--              hoac  .\scripts\ps1\check-seed.ps1
--  Dac ta: plan/15 muc 3. Moi quy tac tra ve SO DONG VI PHAM -- tat ca
--  phai bang 0.
-- =====================================================================
DROP PROCEDURE IF EXISTS sp_check_seed;

DELIMITER $$
CREATE PROCEDURE sp_check_seed(OUT p_violations INT)
BEGIN
    -- Mot bang ket qua: moi quy tac mot dong, kem so vi pham.
    -- OUT p_violations = tong, de script biet exit code nao.
    ...
END$$
DELIMITER ;
```

Mười bốn quy tắc:

| # | Quy tắc | Câu kiểm (rút gọn) | Hợp đồng |
|---|---|---|---|
| R1 | Đơn giá = giá tại ngày bán | `Invoice_Line l JOIN Invoice i ... WHERE l.Unit_Price <> fn_get_product_price(l.Barcode, i.Date)` | 3.2 (G1) |
| R2 | Giảm giá = công thức tại ngày bán | `... WHERE l.Discount <> ROUND(l.Quantity * l.Unit_Price * fn_active_promo_pct(l.Barcode, i.Date) / 100, 2)` | 3.3 (G2) |
| R3 | `PAID` thì đã thu đủ **và** có dòng hàng | `Status='PAID' AND (paid < due OR NOT EXISTS(line))` | 3.4 (G3) |
| R4 | `OPEN` thì chưa thu đủ | `Status='OPEN' AND paid >= due AND EXISTS(line)` — tức là `sp_settle_invoice` đáng ra đã chuyển nó | 3.4 (G3) |
| R5 | Tiền khách đưa ≥ tiền ghi nhận | `Cash_Payment c JOIN Payment p ... WHERE c.Tendered_Amount < p.Amount` | 3.4 (G4) — **chính là kịch bản #4 của khối 5** |
| R6 | Mỗi `Payment` ở đúng một bảng con | đếm `LEFT JOIN` ba bảng con, số bảng khớp `<> 1` | 3.4 (G5) |
| R7 | Trả không quá số đã mua | `SUM(Return_Quantity) > Invoice_Line.Quantity` theo từng dòng | 3.4 (G6) |
| R8 | Ngày trả trong khoảng hợp lệ | `Return_Date < Invoice.Date OR Return_Date > CURDATE()` | 3.4 (G6) |
| R9 | Chỉ hoá đơn `PAID` có lượt trả | `Return r JOIN Invoice i ... WHERE i.Status <> 'PAID'` | 3.4 (G6) |
| R10 | Không bán ở tương lai | `Invoice.Date > CURDATE()` | 3.5 |
| R11 | Mã `INV-nnnn` liên tục | `COUNT(*) <> MAX(CAST(SUBSTRING(Invoice_ID,5) AS UNSIGNED))` trên các mã khớp `^INV-[0-9]+$` | 3.1 (`fn_next_invoice_id`) |
| R12 | **91 ngày gần nhất đều có hoá đơn** | đếm số ngày trong `[CURDATE()−p_days, CURDATE()]` **không** có hoá đơn nào | mục 2.1 điểm 1 — **mục tiêu của khối này, dưới dạng một phép kiểm** |
| R13 | `Return_ID` của mỗi dòng liên tục từ 1 | `COUNT(*) <> MAX(Return_ID)` theo từng `(Invoice_ID, Line_Number)` | 3.4 (`fn_next_return_id`) |
| R14 | Khuyến mãi không hết hiệu lực trước khi cửa sổ đóng | `Promotion p JOIN Applies_To a ... WHERE p.Expiry_Date < a.Promo_End_date` — một đợt áp dụng mà tra cứu lịch sử sẽ hụt ở cuối | 3.3 phương án A |

Hai điểm thiết kế:

- **`OUT p_violations` chứ không `SIGNAL`.** Một thủ tục vừa trả bảng vừa `SIGNAL` thì phía Java
  nhận exception sau khi đã đọc xong bảng — xử lý rối. Với `OUT`, script đọc **một số nguyên** và
  quyết định exit code; `ContractCheck` đọc **cả hai**: bảng để in ra, số để `fail++`.
- **R1 và R2 gọi hàm trên từng dòng** (~1.100 dòng × 2 hàm, mỗi hàm một lần tìm theo khoá chính).
  Đó là cách duy nhất để kiểm *đúng cái app sẽ tính*; và đó cũng là phép kiểm đắt nhất trong file.
  Kịch bản #88 đo: ngưỡng là **dưới 10 giây**.

### 4.8. Không đổi gì khác — và vì sao

| File / routine | Vì sao không đổi |
|---|---|
| `sql/01_schema.sql` | Mục 2.3. Năm hợp đồng dựng được trên 16 bảng đang có |
| `sp_get_active_promo` | Mục 3.3 phương án A giải quyết bằng dữ liệu. Diff của phương án C đã ghi sẵn |
| `sp_settle_invoice`, `sp_void_invoice`, `sp_create_invoice_header` | Khối 4 sở hữu. Khối này **gọi** chúng — đó chính là điều cần làm |
| `sp_add_return`, `fn_next_return_id`, `fn_return_refund` | Khối 3 sở hữu. Khối này gọi `sp_add_return` và **để nó sinh `Return_ID`** |
| `sp_add_payment` | **Giữ nguyên 6 tham số**, kể cả `CAST(p_detail1 AS DECIMAL(12,2))` ở `:763`. Seed truyền tiền khách đưa qua `detail1` dạng chuỗi, đúng như Java (`plan/14` mục 4.6) |
| `sp_report_daily_takings`, `sp_report_price_rise`, `sp_report_best_cashier`, `sp_report_big_returns` | Bốn báo cáo của đề bài. Khối này làm cho chúng **có dữ liệu**, không đổi câu truy vấn |
| `03_queries.sql` | Năm câu của đề bài, có `INV-0001` và `8934008` ghim cứng — mục 2.2 giữ cả hai. **Một ngoại lệ:** nếu bản `sp_report_old_price_proof` sửa ở mục 5.3 làm hai bản lệch tiếp, thì sửa luôn `:165-181` cho khớp (lỗi #19) |
| `fn_get_product_price` | Bất biến G1 **dùng** nó làm định nghĩa. Đổi nó là đổi định nghĩa |

---

## 5. Thiết Kế Java & Hậu Quả Của Khối Này

Khối này có rất ít Java — nhưng bốn chỗ dưới đây **vỡ vì chính nó**, nên chúng thuộc nó. Mỗi chỗ là
một lỗi có từ trước mà chỉ dữ liệu đủ lớn mới làm lộ ra.

### 5.1. Bảng tổng hợp

| File | Trạng thái | Việc |
|---|---|---|
| `sql/02_seed.sql` | **viết lại** | Khối dọn dẹp (4.2), danh mục 20 sản phẩm / 30 khách / 6 quầy / 6 khuyến mãi (4.3, 4.4), `sp_seed_demo_data` (4.5) |
| `sql/04_routines.sql` | sửa | 1 `DROP`, `fn_active_promo_pct` (+ `fn_line_discount`), dời số comment PHẦN 1; `sp_get_recent_invoices` thêm `p_days` (5.2); `sp_report_old_price_proof` thêm `WHERE` (5.3) |
| `sql/05_invariants.sql` | **mới** | `sp_check_seed(OUT p_violations)` + 14 quy tắc (4.7) |
| `scripts/ps1/db-setup.ps1` | sửa | Đổi chỗ bước 2 và 3; nhãn `15 bang` → `16 bang` (4.1) |
| `scripts/bash/db-setup.sh` | sửa | Y hệt |
| `scripts/ps1/check-seed.ps1` | **mới** | `CALL sp_check_seed(@v)`, in bảng, `exit 1` nếu `@v <> 0` |
| `scripts/bash/check-seed.sh` | **mới** | Y hệt |
| `src/billing/dao/InvoiceDao.java` | sửa | `recentInvoices(int days)` — 1 tham số mới (5.2) |
| `src/billing/ui/InvoiceViewPanel.java` | sửa | Ô chọn khoảng ngày + nhãn số dòng (5.2) |
| `src/billing/ui/ReportPanel.java` | sửa | Báo cáo chạy ngoài EDT; sửa chữ giải thích của báo cáo bonus (5.4) |
| `src/billing/ui/SearchPanel.java` | sửa | `LIMIT` + nhãn "đang hiện N dòng" (5.5) |
| `tools/check/ContractCheck.java` | sửa | Thêm nhóm *Seed*: gọi `sp_check_seed`, kịch bản #76, #77 (5.6) |
| `docs/05-SQL-JAVA-CONVENTION.md` | sửa | 3 dòng mới ở mục 6, sửa 2 dòng, thêm mục 8 "Quy ước dữ liệu mẫu" (5.7) |
| `README.md` | sửa | 3 chỗ nói về seed (5.7) |
| `src/envsetup/ui/SqlTab.java` | **không đổi** | Mục 4.1 điểm 2 |
| `sql/01_schema.sql` | **không đổi** | Mục 2.3 |

### 5.2. `sp_get_recent_invoices` nhận khoảng ngày (lỗi #18)

Hôm nay thủ tục trả **mọi** hoá đơn từng có và tab 3 nạp hết. 18 dòng thì không ai thấy; ~550 dòng
trong một bảng không có ô lọc thì thấy.

| | Phương án | Kết luận |
|---|---|---|
| **A** | **`sp_get_recent_invoices(IN p_days INT)`**, `0` hoặc `NULL` = tất cả; tab 3 có ô chọn `7 / 30 / 90 / Tất cả`, mặc định `30` | **ĐÃ CHỌN.** "Gần đây" của một danh sách hoá đơn là một **khoảng thời gian**, không phải một số dòng — và thủ tục đã tên là `recent`. Mọi hoá đơn vẫn **đến được** (chọn *Tất cả*), nên `INV-0001` và `INV-OLD-*` không bị khoá ngoài tầm tay (mục 2.2 ghim chúng). Đổi chữ ký là đổi hợp đồng, nhưng chỉ **một** chỗ gọi (`InvoiceDao:279`) |
| B | `LIMIT 200` cố định trong thủ tục, không đổi chữ ký | Loại: với ~550 hoá đơn thì **250 hoá đơn cũ nhất không đến được bằng giao diện**, trong đó có `INV-0001` mà `03_queries.sql` lấy làm ví dụ mẫu và hai hoá đơn `INV-OLD-*` mà báo cáo bonus nói về |
| C | Phân trang thật (`LIMIT` + `OFFSET` + nút *Trang sau*) | Loại ở khối này (mục 2.3): tính năng mới, và `OFFSET` vào hợp đồng thủ tục |

Kèm theo: **nhãn số dòng** dưới bảng — *"Dang hien 148 hoa don (30 ngay gan nhat)"*. Một bảng bị
cắt mà không nói là đang nói sai; `plan/14` mục 5.6f đã đặt quy ước đó cho nút *Tạo mã QR* (*"một
nút bị khoá không giải thích được gì"*), và nó áp dụng y nguyên cho một bảng bị cắt.

### 5.3. `sp_report_old_price_proof` có `WHERE` (lỗi #19)

Thủ tục trả **mọi dòng hàng của mọi hoá đơn** — ~1.100 dòng cho một báo cáo tên là *"chứng minh"*.
Và bản trong `03_queries.sql:180` **có** `WHERE ... LIKE 'INV-OLD%'`: hai bản chép của cùng một báo
cáo đã lệch nhau từ trước khối này.

Hôm nay `LIKE 'INV-OLD%'` chỉ ra 3 dòng. Từ khối này, giá đổi **giữa** cửa sổ 90 ngày (mục 3.2) nên
"hoá đơn cũ giữ giá cũ" xảy ra ở hàng trăm dòng — và đó là một báo cáo hay hơn nhiều, nếu nó **gộp
lại**:

```sql
--     Gom theo (san pham, don gia da dong bang) thay vi liet ke tung dong:
--     ban cu khong co WHERE nen tu khoi 6 no tra ~1.100 dong cho mot bao cao
--     mang ten "chung minh" (plan/15 loi #19). Moi dong ket qua tra loi dung
--     cau hoi cua de bai: "co bao nhieu dong hang dang ghi gia nay, va gia
--     hom nay la bao nhieu".
    SELECT  p.Name                        AS San_pham,
            l.Unit_Price                  AS Gia_ghi_tren_HD,
            fn_get_product_price(l.Barcode, CURDATE()) AS Gia_hom_nay,
            COUNT(*)                      AS So_dong_hang,
            MIN(i.`Date`)                 AS Ban_tu_ngay,
            MAX(i.`Date`)                 AS Ban_den_ngay
    FROM    Invoice_Line l
    JOIN    Invoice      i ON i.Invoice_ID = l.Invoice_ID
    JOIN    Product      p ON p.Barcode    = l.Barcode
    WHERE   l.Unit_Price <> fn_get_product_price(l.Barcode, CURDATE())
    GROUP BY p.Name, l.Unit_Price, l.Barcode
    ORDER BY p.Name, l.Unit_Price;
```

Hai cái được: báo cáo trả ~8 dòng (đúng 8 sản phẩm đã đổi giá) thay vì 1.100, và nó dùng
`fn_get_product_price` thay cho subquery chép tay ở `:688-693` — **một bản chép nữa biến mất**. Đổi
cột là đổi hợp đồng, nên sửa luôn chữ giải thích ở `ReportPanel:66-71` (nó đang nói *"Hai hoa don
INV-OLD-*..."*) và cân nhắc đồng bộ `03_queries.sql:165-181` (mục 4.8).

### 5.4. `ReportPanel` chạy báo cáo ngoài EDT (lỗi #20)

`ReportPanel:88-103` gọi `action.get()` ngay trong `ActionListener`. Đây là panel **cuối cùng** còn
chặn EDT vì một câu SQL: khối 2 đã sửa cho đăng nhập (`plan/11` mục 4.2), khối 3 cho trả hàng, khối
5 cho lưu hoá đơn và cho danh mục. Với 7 ngày dữ liệu không ai thấy; với 91 ngày và một báo cáo gọi
hàm trên từng dòng thì thấy.

Sửa theo đúng khuôn `plan/14` mục 5.6e: khoá nút + đổi nhãn → `SwingWorker` → `done()` đổ bảng,
`Ui.error` khi lỗi. Năm nút dùng chung một `makeButton`, nên là **một** chỗ sửa cho cả năm.

### 5.5. `SearchPanel` nói ra số dòng (lỗi #21)

`:703` và `:809` là `SELECT *` không `LIMIT`. Tab 2 là **trình xem bảng tổng quát** và `plan/14`
mục 2.3 đã chốt rằng SQL thô ở đó là **có chủ ý** — nên khối này không viết lại nó. Việc tối thiểu
và đủ: `LIMIT 1000` + nhãn *"Dang hien 1.000 / 1.100 dong"*. Một bảng bị cắt phải nói là đang bị
cắt (mục 5.2). Chọn bảng `Invoice_Line` không còn là nạp mù ~1.100 dòng.

### 5.6. `ContractCheck` thêm nhóm *Seed*

`tools/check/ContractCheck.java` đã có đúng chỗ cho việc này: `:341-345` **đã** kiểm bất biến tiền
mặt trên toàn database (kịch bản #4). Thêm vào cuối, trước phần `TONG:`:

- **gọi `sp_check_seed`**, in bảng 14 quy tắc, `check("#73", p_violations, 0)`;
- **kịch bản #76**: `fn_active_promo_pct(barcode, date)` trả **đúng** `Discount_Percent` mà
  `sp_get_active_promo(barcode, date)` trả, trên lưới 20 mã vạch × 7 ngày mốc
  (`−90, −60, −45, −30, −15, 0, +10`) — gồm cả các ngày mà hai cửa sổ `PR04`/`PR05` gối nhau
  (phải ra **12**, không phải 8) và ngày mà `PR06` `PAUSED` phủ (phải ra **0**);
- **kịch bản #77**: `fn_line_discount(...)` của SQL khớp `NewInvoicePanel.lineDiscount(...)` của
  Java **từng đồng**, trên lưới số lượng `{1, 2, 1.5, 0.5}` × giá `{20000, 33000, 158000}` ×
  phần trăm `{0, 5, 8, 10, 12}`. Đây là cái chốt mà mục 3.1 hứa khi chấp nhận bản chép thứ hai.

Hai kịch bản này **phải** nằm trong `ContractCheck` chứ không trong `05_invariants.sql`: chúng so
**SQL với Java**, nên chỉ chạy được ở chỗ gọi được cả hai.

### 5.7. Tài liệu

**`docs/05` mục 6** — 3 dòng mới, đúng vị trí nhóm:

| Tên Routine | Loại | Tham số | Ý nghĩa nghiệp vụ | Nơi gọi trong Java |
|---|---|---|---|---|
| `fn_active_promo_pct` | FUNCTION | `p_barcode`, `p_date` | Phần trăm giảm của khuyến mãi **tốt nhất** tại ngày bán; `0.00` nếu không có. **Bản chép thứ hai** của điều kiện chọn khuyến mãi — có chốt bằng số (kịch bản #76) | *(chỉ SQL — `sp_seed_demo_data` và `05_invariants.sql` gọi)* |
| `fn_line_discount` | FUNCTION | `p_barcode`, `p_date`, `p_quantity`, `p_unit_price` | Tiền giảm của cả dòng: `ROUND(qty × price × pct / 100, 2)`. Khớp `NewInvoicePanel.lineDiscount` — chốt bằng kịch bản #77 | *(chỉ SQL)* |
| `sp_check_seed` | PROCEDURE | `OUT p_violations` | 14 bất biến dữ liệu, mỗi quy tắc một dòng kèm số vi phạm; `OUT` là tổng | `ContractCheck`, `check-seed.ps1` |

Sửa 2 dòng đang có:

- `sp_get_recent_invoices` — thêm tham số `p_days` (`0` = tất cả) và ghi rõ nó là một **đổi hợp
  đồng** của khối 6.
- `sp_report_old_price_proof` — đổi sang bản gộp theo (sản phẩm, đơn giá), 6 cột.

Thêm **mục 8 "Quy ước dữ liệu mẫu"**, bốn điểm, mỗi điểm một câu:
seed đi qua thủ tục của app, không `INSERT` thẳng vào bảng giao dịch (mục 3.1);
`Status`, `Unit_Price`, `Discount`, `Return_ID` **không ai được gõ tay** (mục 3.4);
mọi mốc ngày là `CURDATE() − n`, một ngoại lệ có comment (mục 3.2);
mỗi bất biến có một dòng trong `05_invariants.sql` (mục 4.7).

**`README.md`** — ba chỗ: `:220` (*"dữ liệu mẫu (ngày tháng tự tính theo hôm nay)"* → nói thêm "90
ngày, sinh bằng vòng lặp"), `:246` (*"báo cáo 7 ngày gần nhất cũng có dữ liệu"* → 90 ngày), `:261`
(bảng gỡ lỗi — thêm một dòng cho `check-seed`). Thêm `check-seed.ps1` vào danh sách script.

---

## 6. Danh Sách Công Việc (theo thứ tự thực hiện)

### Phần A — hàm và thứ tự nạp (làm trước, chưa cần đổi seed)

1. `fn_active_promo_pct` + `fn_line_discount` vào `04_routines.sql`, kèm 2 dòng `DROP`, dời số
   comment PHẦN 1 (mục 4.6).
2. Đổi thứ tự nạp trong `db-setup.ps1` và `db-setup.sh`, sửa nhãn `15 bang` → `16 bang` (mục 4.1).
3. Nạp lại routine, chạy **kịch bản #73–#77** bằng `mysql` CLI trên **seed hiện tại**. Hai hàm mới
   phải đúng *trước khi* có gì phụ thuộc chúng. **Xong Phần A mới sang Phần B.**

### Phần B — bộ kiểm (viết trước khi viết seed)

4. `sql/05_invariants.sql`: `sp_check_seed(OUT p_violations)` + 14 quy tắc (mục 4.7).
5. `scripts/ps1/check-seed.ps1` + `scripts/bash/check-seed.sh`.
6. Chạy nó trên **seed hiện tại** (18 hoá đơn). Kết quả mong đợi: **R1–R11, R13, R14 đạt**
   (dữ liệu hiện tại đúng — lỗi #1, #2, #3 là *"không có gì giữ"*, không phải *"đang sai"*), và
   **R12 báo ~84 ngày trống**. Đó là ảnh "trước" của khối này, và là phép kiểm rằng bộ kiểm **chạy
   đúng** chứ không chỉ luôn trả 0.

> **Thứ tự này là cố ý.** Viết phép kiểm trước thì khi seed mới sai, ta biết ngay là seed sai. Viết
> seed trước thì một bộ kiểm toàn số 0 không phân biệt được "dữ liệu đúng" với "phép kiểm hỏng".

### Phần C — seed

7. `02_seed.sql`: khối dọn dẹp (mục 4.2). Nạp **hai lần liên tiếp** trên seed cũ — kịch bản #79.
8. Danh mục: 6 quầy, 30 khách, 20 sản phẩm, 28 mốc giá (mục 4.3).
9. 6 khuyến mãi + 9 dòng `Applies_To` (mục 4.4). Chạy lại #76 — giờ lưới có `PR04`/`PR05` gối nhau
   và `PR06` `PAUSED`.
10. Hai hoá đơn `INV-OLD-*` qua thủ tục (mục 4.5).
11. `sp_seed_demo_data`: vòng lặp, dòng hàng, thanh toán, `sp_settle_invoice` (mục 4.5 bước 1–6).
12. Chạy `check-seed`. **R1–R14 phải đạt hết, R12 phải về 0.**
13. Thêm `sp_void_invoice`, trả hàng, phép lệch cho báo cáo câu 4 (mục 4.5 bước 7–9).
14. Chạy `check-seed` lại, rồi mục 7.2 và 7.3.

### Phần D — hậu quả của volume

15. `sp_get_recent_invoices(p_days)` + `InvoiceDao.recentInvoices(int)` + ô chọn và nhãn ở tab 3
    (mục 5.2).
16. `sp_report_old_price_proof` bản gộp + chữ giải thích ở `ReportPanel` (mục 5.3).
17. `ReportPanel` ngoài EDT (mục 5.4).
18. `SearchPanel` `LIMIT` + nhãn (mục 5.5).
19. Chạy mục 7.4 và 7.5.

### Phần E — chốt và tài liệu

20. `ContractCheck`: nhóm *Seed*, kịch bản #73, #76, #77 (mục 5.6).
21. `docs/05` mục 6 (3 dòng mới, 2 dòng sửa) + mục 8 mới; `README.md` 3 chỗ (mục 5.7).
22. Chạy lại **toàn bộ** mục 7 (và `check-contracts` của khối 5 — nó không được hồi quy), rồi viết
    mục 9 của file này.

---

## 7. Kịch Bản Kiểm Thử

Đánh số **tiếp** từ khối 5 (file này bắt đầu ở #73, `plan/14` kết thúc ở #72) để nhắc nhau khi báo
lỗi: *"kịch bản #81 sai"* ngắn hơn một đoạn văn.

### 7.1. Phần A & B — chỉ bằng `mysql` CLI

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 73 | Nạp `04_routines.sql` rồi `05_invariants.sql`, mỗi file **hai lần liên tiếp** | Lần hai cũng xong, không `already exists` (đủ dòng `DROP` cho 2 hàm mới và `sp_check_seed`) |
| 74 | `SELECT fn_active_promo_pct('8934001', CURDATE());` | `10.00` (PR01 đang chạy) |
| 75 | `SELECT fn_active_promo_pct('8934009', CURDATE());` | `0.00` — không khuyến mãi, **không `NULL`** |
| 76 | Với mỗi mã vạch × mỗi ngày trong `{−90, −60, −45, −30, −15, 0, +10}`: so `fn_active_promo_pct` với `Discount_Percent` của `sp_get_active_promo` | **Khớp từng ô.** Sau Phần C, lưới này phải cho **12.00** ở các ngày `PR04`/`PR05` gối nhau trên `8934001`, và **0.00** ở ngày `PR06` (`PAUSED`) phủ `8934006` |
| 77 | `fn_line_discount` so với `NewInvoicePanel.lineDiscount` trên lưới SL × giá × % (mục 5.6) | Khớp **từng đồng**, kể cả `pct = 0` và SL thập phân `0.5` |
| 78 | `CALL sp_check_seed(@v); SELECT @v;` **trên seed cũ** (18 hoá đơn) | R1–R11, R13, R14 = **0**; **R12 ≈ 84** (số ngày trống). `@v` khác 0. Đây là ảnh "trước", và là phép kiểm rằng bộ kiểm không chỉ luôn trả 0 |

### 7.2. Phần C — seed mới

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 79 | Nạp `02_seed.sql` **ba lần liên tiếp** | Cả ba lần xong, không `Duplicate entry` (lỗi #6). Số dòng mỗi bảng **giống nhau** sau mỗi lần |
| 80 | `CALL sp_check_seed(@v); SELECT @v;` | **`@v = 0`.** Cả 14 quy tắc đều 0 vi phạm — kể cả R1, R2 trên ~1.100 dòng hàng |
| 81 | Đếm dòng: `Invoice`, `Invoice_Line`, `Payment`, `Return`, `Customer`, `Product` | Khoảng `550 / 1.100 / 700 / 45 / 30 / 20`. Con số chính xác phụ thuộc `p_days`, `p_inv_per_day` và ngày chạy — chốt là **khoảng**, không phải số (mục 3.5) |
| 82 | `SELECT Status, COUNT(*) FROM Invoice GROUP BY Status;` | Cả **ba** trạng thái đều có hàng chục dòng, không phải 1 (lỗi #12). `OPEN` ≈ 78, `VOIDED` ≈ 15 |
| 83 | `SELECT COUNT(DISTINCT Date) FROM Invoice WHERE Date >= CURDATE() − INTERVAL 90 DAY;` | **91** — mục tiêu 2.1 điểm 1 (cùng nội dung R12, nhưng chạy được một mình) |
| 84 | `CALL sp_get_invoice_lines('INV-0001');` | **≥ 3 dòng**, **≥ 1 dòng có `Giam_gia` > 0** — `03_queries.sql:15-46` và `docs/03-SCHEMA.md:28` ghim hoá đơn này (mục 4.5) |
| 85 | `CHECKSUM TABLE Invoice, Invoice_Line, Payment, Cash_Payment, `Return`;` → nạp lại seed → `CHECKSUM` lần hai | **Giống nhau từng bảng** — hợp đồng xác định (mục 3.5). Khác nhau nghĩa là đã có `RAND` lọt vào |
| 86 | `SELECT Return_ID, COUNT(*) FROM `Return` GROUP BY Return_ID;` | Có ít nhất một dòng `Return_ID = 2` — cột `Lan_tra` của khối 3 lần đầu có dữ liệu (lỗi #4) |
| 87 | `SELECT COUNT(*) FROM Cash_Payment c JOIN Payment p USING (Payment_ID) WHERE c.Tendered_Amount > p.Amount;` | **Hàng trăm dòng** — mọi hoá đơn tiền mặt có tiền thối thật (lỗi #13). Và R5 vẫn 0 |
| 88 | Đo thời gian: nạp `02_seed.sql`, rồi `CALL sp_check_seed(@v)` | Nạp **dưới 60 giây**; kiểm **dưới 10 giây** (mục 4.7). Vượt ngưỡng thì `INDEX` thành việc của khối sau (mục 2.3), kèm số đo |
| 89 | `CALL sp_search_customers_by_phone('09');` rồi `('0901');` | `'09'` trả **đúng 20 dòng** (chạm `LIMIT 20` — lỗi #10); `'0901'` trả ít hơn 20 |
| 90 | `SELECT * FROM Promotion;` | 6 dòng; `PR04`/`PR05` có `Expiry_Date` **tương lai** trong khi cửa sổ `Applies_To` đã qua — kèm comment giải thích (mục 3.3) |

### 7.3. Năm báo cáo

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 91 | `CALL sp_report_daily_takings();` | **7 ngày × 3 shop = 21 dòng, không ô rỗng** (mục 4.3: 2 quầy mỗi shop, 6 hoá đơn mỗi ngày). Trước khối này: 7 ngày nhưng nhiều ô trống |
| 92 | `CALL sp_report_price_rise();` | **Đúng 5 sản phẩm** (`001`, `003`, `006`, `008`, `010`), y như trước khối này — phân bố giá được giữ nguyên (mục 4.3) |
| 93 | Chạy #92 **trong 45 ngày đầu năm** (đổi ngày hệ thống, hoặc đọc kỹ mục 3.2) | **Vẫn 5 dòng** — mốc giá bị kẹp về 1 tháng 1 nhưng báo cáo không rỗng, **và** R1 vẫn 0 vì đơn giá tra bằng `fn_get_product_price` (đây là cách sửa lỗi #2) |
| 94 | `CALL sp_report_best_cashier();` | 5 dòng; `E03` **đứng đầu rõ ràng**, không sát nút — phép lệch có chủ ý ở mục 4.5 bước 9 |
| 95 | `CALL sp_report_big_returns();` | **Vài dòng** với `Phan_tram_tra` > 50, trải trên nhiều tháng (hôm nay: 1 dòng) |
| 96 | `CALL sp_report_old_price_proof();` | **~8 dòng gộp** theo (sản phẩm, đơn giá), mỗi dòng có `So_dong_hang` và khoảng ngày — **không** phải ~1.100 dòng liệt kê (lỗi #19) |
| 97 | So #96 với `03_queries.sql` phần BONUS | Hoặc khớp nhau, hoặc file truy vấn đã được sửa cho khớp (mục 4.8) — **không để hai bản lệch tiếp** |

### 7.4. Tab 3 và tab 2 ở quy mô mới

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 98 | Mở tab 3 | Mặc định **30 ngày**, nhãn dưới bảng nói đúng số dòng đang hiện (mục 5.2). Không nạp cả 550 dòng |
| 99 | Tab 3 → chọn *Tất cả* | Mọi hoá đơn hiện ra, trong đó có `INV-0001` và `INV-OLD-01/02` — chúng **đến được** (vì sao phương án B bị loại ở mục 5.2) |
| 100 | Tab 3 → chọn một hoá đơn **cũ** (trước mốc đổi giá) và một hoá đơn **mới**, cùng sản phẩm | Hai `Don_gia` **khác nhau** trên hai hoá đơn người dùng thấy cạnh nhau — "hoá đơn cũ giữ giá cũ", lần đầu tiên không phải lùi 400 ngày (mục 3.2) |
| 101 | Tab 3 → một hoá đơn có khuyến mãi của `PR04` (ngày `−90..−46`) | Cột `Giam_gia` khác 0, và `Thanh_tien = SL × Don_gia − Giam_gia` (hợp đồng khối 5) |
| 102 | Tab 3 → một hoá đơn trong cửa sổ `PR06` (`PAUSED`, ngày `−45..−20`, sản phẩm `8934006`) | `Giam_gia` = **0**. Bảng `Applies_To` **có** dòng phủ đúng ngày đó — "có dòng" ≠ "được áp dụng" (mục 4.4) |
| 103 | Tab 3 → *Trả hàng* trên một hoá đơn có 2 lượt trả cùng một dòng | Bảng trả hàng hiện `Lan_tra` = 1 và 2; `SL_con_lai` đúng (khối 3 không hồi quy) |
| 104 | Tab 3 → một hoá đơn `OPEN` | Nhãn trạng thái của khối 4 đúng; nút *Trả hàng* bị chặn với lý do (`plan/13`) |
| 105 | Tab 2 → chọn bảng `Invoice_Line` | Nhãn nói *"đang hiện 1.000 / 1.1xx dòng"*; cửa sổ không treo (mục 5.5) |
| 106 | Tab 2 → chip lọc `Status = OPEN` trên bảng `Invoice` | Hàng chục dòng, không phải 1 — chip của khối 4 lần đầu có việc làm (lỗi #12) |
| 107 | Tab 4 Báo cáo → bấm cả 5 nút liên tiếp, thật nhanh | Cửa sổ **không đứng hình** ở nút nào; nút bị khoá và đổi nhãn trong lúc chạy (mục 5.4) |
| 108 | Tắt MySQL rồi bấm một nút báo cáo | `Ui.error` hiện lỗi kết nối, cửa sổ còn sống (mục 5.4) |

### 7.5. Chống hồi quy năm khối trước

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 109 | `.\scripts\ps1\check-contracts.ps1` (khối 5) | **Đạt hết**, kể cả phần `e2e()` lập 3 hoá đơn thật rồi xoá — nó phải chạy được **trên seed mới** |
| 110 | Lập một hoá đơn mới ở tab 1 rồi chạy `check-seed` | **`@v = 0`** — dữ liệu mà app sinh ra và dữ liệu mà seed sinh ra thoả **cùng một** bộ bất biến. Đây là nội dung của mục 3.1, dưới dạng một phép kiểm chạy được |
| 111 | Lập một hoá đơn ở tab 1 với **ngày bán trong quá khứ** (trước mốc đổi giá) | Đơn giá là **giá cũ**, khuyến mãi là khuyến mãi của ngày đó, và `check-seed` vẫn 0 |
| 112 | Tab 5 Setup App (khối 1), sau khi nạp seed | **Một** dòng `CALL sp_seed_demo_data(...)`, không phải ~8.000 dòng (lỗi #23). Nhật ký trước đó **còn nguyên** |
| 113 | Tab 1 → ô khách hàng, gõ `09` rồi gõ tiếp | Popup hiện tối đa 20 dòng; con trỏ ở lại trong ô; `CustomerPicker` của khối 5 lần đầu chạy ở quy mô thật (lỗi #10) |
| 114 | Đăng nhập nhanh (khối 2), đổi vai trò, khoá/mở nút (khối 4) | Không đổi |
| 115 | `javac -Xlint:all` toàn app | Vẫn **0 cảnh báo `deprecation`**; chỉ còn nhóm `[serial]` / `[this-escape]` của Swing (`plan/14` kịch bản #67) |
| 116 | `.\scripts\ps1\db-setup.ps1` từ database trắng | Ba bước theo thứ tự **01 → 04 → 02**, xong sạch, rồi `check-seed` = 0 (mục 4.1) |
| 117 | Setup App: *Tạo database* → *Tạo dữ liệu mẫu* → *Chạy bài tập* | Cả ba nút xong, **không đổi một dòng nào trong `SqlTab.java`** (mục 4.1 điểm 2) |
| 118 | Setup App: bấm *Tạo dữ liệu mẫu* **hai lần** | Lần hai cũng xong (lỗi #6) — trước khối này nó báo `Duplicate entry` |

---

## 8. Rủi Ro & Cách Giảm

| # | Rủi ro | Mức | Cách giảm |
|---|---|---|---|
| 1 | **`fn_active_promo_pct` và `sp_get_active_promo` lệch nhau** sau khi ai đó sửa một bên — và lệch im lặng, vì cả seed và phép kiểm R2 đều hỏi **cùng** hàm nên R2 vẫn trả 0 | **Cao** | Đây là cái giá đã nhận ở mục 3.1, và nó có **chốt bằng số**: kịch bản #76 so hai bên trên lưới 20 × 7. Javadoc của hàm (mục 4.6) nói thẳng *"ĐỪNG sửa một bên"* và trỏ về kịch bản. `docs/05` mục 6 ghi nó là *"bản chép thứ hai"* chứ không giả vờ là duy nhất |
| 2 | **R2 trả 0 vì phép kiểm và dữ liệu cùng sai một cách.** Cả hai dùng `fn_active_promo_pct`, nên nếu hàm sai thì cả hai sai khớp nhau | **Cao** | Chính là rủi ro #1 nhìn từ phía khác, và nó là lý do Phần B đứng **trước** Phần C (mục 6): bộ kiểm chạy trên seed cũ trước, nơi `Discount` do **người** tính. R1–R11 đạt trên dữ liệu gõ tay = phép kiểm đồng ý với con người, không chỉ đồng ý với chính nó. Thêm #74, #75 chốt hai giá trị cụ thể bằng tay |
| 3 | **Seed chạy quá lâu** và Setup App trông như treo khi bấm *Tạo dữ liệu mẫu* | Trung bình | Kịch bản #88 đặt ngưỡng 60 giây và đo thật. `BaseTab.runScript` của khối 1 đổ log theo dòng nên có dấu hiệu sống. Vượt ngưỡng thì giảm `p_inv_per_day` xuống 4 (còn ~370 hoá đơn) **trước khi** nghĩ tới index — một tham số đổi được ở một chỗ |
| 4 | **Một `SIGNAL` từ `sp_add_return` / `sp_settle_invoice` làm seed chết giữa đường**, để lại database nửa vời | Trung bình | **Có chủ ý** (mục 4.2): autocommit nên `SELECT COUNT(*) FROM Invoice` chỉ ra ngay nó chết ở vòng nào, và khối dọn dẹp làm lần chạy sau sạch. Thay vì bọc transaction rồi mất dấu vết |
| 5 | **Đổi chữ ký `sp_get_recent_invoices` làm tab 3 trắng bảng** | Trung bình | Một chỗ gọi duy nhất (`InvoiceDao:279`), sửa cùng lúc. `docs/05` Quy tắc 2 là nghi thức cho việc này. Kịch bản #98, #99 |
| 6 | **Đổi cột `sp_report_old_price_proof` làm `Ui.fill` đổ sai bảng** | Thấp | `Ui.fill` đọc tên cột từ `ResultSetMetaData`, không ghim chỉ số (khác `fillFormatted` của tab 3). Kịch bản #96. Nhưng chữ giải thích ở `ReportPanel:66-71` thì **ghim bằng tay** — mục 5.3 sửa nó cùng lúc |
| 7 | **~550 hoá đơn làm một kịch bản tay nào của khối 5 trở nên khó bấm** (ví dụ #38: mở hai cửa sổ app cùng lưu) | Thấp | Kịch bản #109 chạy lại toàn bộ `check-contracts`; các kịch bản tay của `plan/14` mục 9.3 vẫn còn nguyên giá trị và seed lớn hơn **giúp** chúng (có nhiều hoá đơn `OPEN` / `VOIDED` / có trả hàng để thử) |
| 8 | **`Transaction_Ref` của `EWallet_Payment` trùng** → `uq_wallet_txn` chặn, seed chết | Thấp | Ghép chỉ số vòng lặp vào mã (mục 4.5 bước 5). Nếu chết thì chết **ngay ở hoá đơn đầu tiên dùng ví**, không phải sau 500 hoá đơn — rủi ro #4 làm nó dễ tìm |
| 9 | **Vượt trần `LPAD(..., 4)`** khi người sau tăng `p_days` / `p_inv_per_day` | Thấp | Lỗi #22 ghi sẵn **con số**: trần là `9999`, và cấu hình 90 × 6 dùng ~550 / ~700. Comment trên tham số của `sp_seed_demo_data` nhắc lại. Ngưỡng thật đáng lo là `ORDER BY p.Payment_ID` sắp theo chuỗi ở `sp_get_invoice_payments:472` |
| 10 | **Phép kẹp theo năm của mốc đổi giá bị ai đó "dọn dẹp"** vì mục 2.1 điểm 4 nói *"mọi mốc là `CURDATE() − n`"* | Thấp | Mục 3.2 ghi đúng một đoạn về việc này, và comment tại chỗ trỏ về đoạn đó. Kịch bản #93 kiểm đúng trường hợp đó bằng cách đổi ngày hệ thống |
| 11 | **`sp_check_seed` trở thành một thủ tục không ai chạy** sau khi khối này xong | Thấp | `ContractCheck` gọi nó (mục 5.6), nên nó chạy cùng `check-contracts.ps1` của khối 5 — một lệnh mà mọi khối sau đều phải chạy trước khi báo xong. Và `docs/05` mục 6 có một dòng cho nó |
| 12 | **Hoá đơn `VOIDED` có dòng `Payment`** làm người đọc tưởng dữ liệu sai | Thấp | Đúng thiết kế của khối 4: *"huỷ không xoá dấu vết tiền"* (`04_routines.sql:839-840`). Vòng lặp gọi `sp_void_invoice` **sau** `sp_settle_invoice`, nên hoá đơn bị huỷ là hoá đơn đã thu tiền — đúng tình huống nghiệp vụ mà khối 4 mô tả. Bất biến G3 không nói gì về `VOIDED` có chủ ý |

---

## 9. Trạng Thái Triển Khai

**ĐÃ TRIỂN KHAI** ngày 2026-10-11 (kế hoạch viết cùng ngày, ngay sau khi khối 5 hoàn tất).

### 9.1. File đã thay đổi

| File | +/− | Việc |
|---|---|---|
| `sql/02_seed.sql` | +696 / −190 | **Viết lại.** Khối dọn dẹp 15 câu `DELETE` + `UPDATE Cashier SET Supervisor_ID = NULL` (4.2); danh mục 6 quầy / 30 khách / 20 sản phẩm / 28 mốc giá (4.3); 6 khuyến mãi + 9 `Applies_To` (4.4); hai hoá đơn `INV-OLD-*` qua thủ tục; `sp_seed_demo_data(90, 6)` → `CALL` → `DROP` (4.5) |
| `sql/04_routines.sql` | +144 / −23 | 2 dòng `DROP`; `fn_active_promo_pct` (1.2) và `fn_line_discount` (1.3); dời số comment PHẦN 1 (1.2→1.4 … 1.7→1.9); `sp_get_recent_invoices` thêm `p_days` (5.2); `sp_report_old_price_proof` bản gộp 6 cột (5.3); sửa ví dụ trong javadoc `fn_return_refund` |
| `sql/05_invariants.sql` | **+287 / −0** | **Mới.** `sp_check_seed(OUT p_violations)` + 14 quy tắc (4.7) |
| `sql/03_queries.sql` | +24 / −14 | Câu BONUS gộp theo (sản phẩm, đơn giá) cho khớp `sp_report_old_price_proof` — hai bản chép thôi lệch nhau (4.8 ngoại lệ) |
| `scripts/ps1/check-seed.ps1` | **+116 / −0** | **Mới.** Tự nạp `05_invariants.sql`, `CALL sp_check_seed(@v)`, in bảng, `exit 1` nếu `@v <> 0` |
| `scripts/bash/check-seed.sh` | **+119 / −0** | **Mới.** Y hệt (mode `100755`) |
| `scripts/ps1/db-setup.ps1` | +9 / −3 | Đổi chỗ bước 2 và 3 → **01 → 04 → 02**; nhãn `15 bang` → `16 bang` (4.1) |
| `scripts/bash/db-setup.sh` | +11 / −5 | Y hệt |
| `scripts/ps1/check-contracts.ps1` | +27 / −0 | Nạp `05_invariants.sql` trước khi chạy (xem 9.2 điểm 6) |
| `scripts/bash/check-contracts.sh` | +17 / −0 | Y hệt |
| `src/billing/dao/InvoiceDao.java` | +15 / −3 | `recentInvoices(int days)` (5.2) |
| `src/billing/ui/InvoiceViewPanel.java` | +51 / −3 | Ô chọn `7 / 30 / 90 / Tất cả` (mặc định 30) + nhãn số dòng (5.2) |
| `src/billing/ui/ReportPanel.java` | +68 / −11 | Báo cáo chạy ngoài EDT bằng `SwingWorker`, khoá cả 5 nút + đổi nhãn (5.4); sửa chữ giải thích báo cáo bonus (5.3) |
| `src/billing/ui/SearchPanel.java` | +45 / −3 | `LIMIT 1000` (lấy `+1` để biết "còn nữa") + nhãn nói ra khi bảng bị cắt (5.5) |
| `src/billing/ui/Ui.java` | +3 / −1 | Sửa ví dụ ghim `INV-0002` trong javadoc `Ui.qty` (xem 9.2 điểm 7) |
| `tools/check/ContractCheck.java` | **+250 / −0** | Nhóm *Seed*: `sp_check_seed` (#80), lưới #76, lưới #77, và **#110/#111 tự động hoá** (xem 9.2 điểm 5) |
| `docs/05-SQL-JAVA-CONVENTION.md` | +81 / −2 | 4 dòng mới ở mục 6, sửa 2 dòng; **mục 8 "Quy Ước Dữ Liệu Mẫu"** mới, 4 tiểu mục (5.7) |
| `docs/03-SCHEMA.md` | +16 / −6 | Sửa 2 ví dụ đã thành sai (xem 9.2 điểm 7) |
| `README.md` | +31 / −10 | Mục *Dữ liệu mẫu có sẵn* viết lại; cây thư mục thêm `05_invariants.sql` + `check-seed.*`; 3 dòng mới ở bảng gỡ lỗi (5.7) |
| `sql/01_schema.sql` | — | **Không đổi** (mục 2.3) |
| `src/envsetup/ui/SqlTab.java` | — | **Không đổi** (mục 4.1 điểm 2) |

**Tổng: 19 file (3 file mới), +2.010 / −274** (không tính chính file kế hoạch này).

### 9.2. Những điểm làm khác kế hoạch (và lý do)

1. **R2 **không** đạt trên seed cũ — đây là lệch *dự đoán*, và là phát hiện đáng giá nhất của Phần B.**
   Mục 6 bước 6 dự đoán *"R1–R11, R13, R14 đạt"* trên seed cũ, vì lỗi #1/#2/#3 là *"không có gì
   giữ"* chứ không phải *"đang sai"*. Thực tế `R2 = 1`: `INV-0014` dòng 1 bán `8934003` ở ngày
   `CURDATE() − 4`, **nằm trong** cửa sổ `PR02` (`−10..+20`, 5%), mà `Discount` gõ tay là `0.00`
   trong khi công thức cho `8.750,00`. Cùng sản phẩm, cùng khuyến mãi, `INV-0003` thì **có** giảm
   `8.750` — tức là seed cũ đã tự mâu thuẫn, chỉ chưa ai biết. Bộ kiểm **đồng ý với con người ở 13
   quy tắc và bắt được một chỗ người tính sai ở quy tắc thứ 14**; đó là bằng chứng mạnh hơn nhiều so
   với "tất cả đều 0" mà rủi ro #2 lo ngại. `R12 = 84` đúng y dự đoán.

2. **Hoá đơn `VOIDED` chọn bằng `MOD(v_d * 7 + v_k * 11, 37)` thay vì `MOD(v_d * v_k, 37)` — lệch
   *thiết kế*, bắt buộc.** Công thức của mục 4.5 bước 7 có `v_d = 0` là `v_d * v_k = 0`, nên `MOD`
   bằng 0 với **mọi** `v_k`: cả 6 hoá đơn của **hôm nay** bị huỷ, cộng toàn bộ ngày `−37` và `−74`.
   Hậu quả: doanh thu hôm nay bằng 0 và báo cáo câu 2 mất dòng của ngày gần nhất. Công thức tuyến
   tính cho 12 hoá đơn `VOIDED` **trải đều** 91 ngày (kế hoạch dự kiến ~15).

3. **Hai điều kiện trả hàng đổi ước số, và nhánh "trả toàn bộ" là *loại trừ* — lệch *thiết kế*.**
   Ba gạch đầu dòng của mục 4.5 bước 8 đọc theo nghĩa cộng dồn thì **xung đột**: trả toàn bộ mọi
   dòng *rồi* trả thêm một phần dòng 1 là `sp_add_return` `SIGNAL` *"chi con 0 co the tra"*. Nên
   nhánh `MOD(v_d, …) = 0` → trả toàn bộ là `IF/ELSE` với nhánh trả một phần.
   Ngoài ra `29` và `48` cho **quá ít** ví dụ, vì hai điều kiện đó *giao* với điều kiện của chính
   nhánh trả hàng (`MOD(v_d + 3·v_k, 12) = 0`, chỉ đúng khi `v_d` chia hết 3): `29` cho **2** hoá
   đơn trong `sp_report_big_returns` và `48` cho **2** dòng `Return_ID = 2`. Đổi sang `7` và `9`
   (ước số phải chọn theo *cái giao*, không theo tần số của riêng nó — có comment tại chỗ): nay
   **10 dòng** báo cáo câu 5 trải trên 4 tháng với cả ba mức `100% / 75% / 64%`, và **7 dòng**
   `Return_ID = 2`. Đúng tinh thần *"vài dòng, trải trên nhiều tháng"* của kịch bản #95.

4. **`INV-0001` có một biến `v_sample` riêng, và điều kiện thứ ba là `'PAID'` — mở rộng *thiết kế*.**
   Mục 4.5 chỉ yêu cầu ép `v_lines = 3`. Nhưng công thức `MOD` chọn sản phẩm cho vòng đầu ra
   `8934009 / 8934020 / 8934011` — **không** sản phẩm nào được `PR04` phủ, nên hoá đơn mẫu **không
   có dòng giảm giá**, trái yêu cầu của chính mục 4.5. Phải ép thêm mã vạch dòng 1 (`8934001`) và
   `SL = 2`. Và khi chạy thì `MOD(90 + 1, 7) = 0` đưa `INV-0001` vào nhánh **trả thiếu**, nên hoá
   đơn mẫu của cả dự án ở `'OPEN'` — làm **ví dụ trả hàng trong javadoc `fn_return_refund` không
   chạy được**, vì `sp_add_return` từ chối hoá đơn `<> 'PAID'`. Nên gom cả ba luật rẽ riêng vào một
   biến `v_sample` (tìm `v_sample` là thấy hết, 4 chỗ) và loại hoá đơn mẫu khỏi cả nhánh trả thiếu
   lẫn nhánh huỷ — để điều kiện `'PAID'` đúng **bởi cấu tạo** chứ không đúng tình cờ theo `p_days`.

5. **Kịch bản #110 và #111 **đã tự động hoá**, không còn là kịch bản tay — mở rộng *thiết kế*.**
   Mục 9.3 của kế hoạch xếp chúng vào nhóm chạy được bằng máy nhưng mục 5.6 không mô tả cách làm.
   `ContractCheck.appInvoiceKeepsInvariants` lập **một hoá đơn thật** qua `InvoiceDao.createInvoice`
   (đường tab 1 đi), tra đơn giá bằng `ProductDao.priceOn` và tiền giảm bằng
   `NewInvoicePanel.lineDiscount` tại ngày bán, chạy lại 14 bất biến, rồi **xoá** hoá đơn đó. Đây
   chính là nội dung mục 3.1 dưới dạng một phép kiểm chạy được. #111 là bằng chứng mạnh nhất của cả
   khối: hoá đơn bán ngày `−60` nhận **giá cũ `28.000`** (mốc đổi giá ở `−45`) và **giảm 12%** của
   `PR05` — khuyến mãi của *ngày đó*, không phải `PR01` 10% của hôm nay.

6. **`check-seed.*` và `check-contracts.*` **tự nạp** `05_invariants.sql` — lệch *thiết kế*, do một
   lỗ hổng quy trình.** Mục 5.1 không xếp `05_invariants.sql` vào đường nạp nào. Nhưng
   `01_schema.sql` mở đầu bằng `DROP DATABASE`, nên **mỗi** lần chạy `db-setup` hoặc bấm *Tạo
   database* trong Setup App là `sp_check_seed` biến mất, và cả hai bộ kiểm chết với
   `PROCEDURE ... does not exist` — gặp thật khi chạy kịch bản #117 rồi #109. Hai cách sửa: thêm
   bước 4 vào `db-setup.*`, hoặc để bộ kiểm tự nạp. Chọn cách sau vì nó chạy được sau **bất kỳ**
   đường dựng database nào (kể cả đường Setup App mà mục 4.1 điểm 2 **cấm sửa**), giữ `db-setup` ở
   đúng *ba* bước như kịch bản #116 mô tả, và file đó chỉ `CREATE` một thủ tục nên nạp lại bao nhiêu
   lần cũng xong.

7. **Sửa thêm 3 ví dụ trong tài liệu và javadoc đã thành sai — ngoài danh sách của mục 5.7.**
   Mục 5.7 chỉ kể `docs/05` và `README.md`. Nhưng ba chỗ khác **ghim dữ liệu seed bằng tay** và trở
   thành sai sau khối này: `04_routines.sql` javadoc `fn_return_refund` (*"INV-0001 dòng 1: mua 2,
   đơn giá 33.000, giảm 6.600"* → nay `28.000 / 4.480`), `docs/03-SCHEMA.md` ví dụ `INV-0005` với
   `PAY-0005 = 200.000` / `PAY-0006 = 177.800` (không còn tồn tại với các số đó), và `Ui.java`
   javadoc `Ui.qty` (*"INV-0002 dòng 1 bán 1,5 KG thịt ba chỉ"*). Hai chỗ đầu nay dùng số thật của
   seed mới kèm **cảnh báo đừng ghim lại**; chỗ thứ ba chuyển sang nói về *nhóm* `KG`/`LITER` thay
   vì một hoá đơn. Đây đúng loại lỗi mà mục 1.1 nói về comment: *"bản chép duy nhất không ai kiểm"*.

8. **Chưa dùng tới nhánh `v_price IS NULL`.** Bảng `Price_History` có mốc giá cũ cho **cả 20** sản
   phẩm ở `CURDATE() − 400` (đúng mục 4.3), nên `fn_get_product_price` không bao giờ trả `NULL`
   trong cửa sổ 91 ngày. Nhánh "bỏ qua dòng, không ghi 0" của mục 3.2 vẫn **giữ nguyên** trong vòng
   lặp kèm comment: nó là cái giữ bất biến G1 đúng nếu người sau thêm một sản phẩm không có giá ở
   `@old_day`. Ghi ra đây để khối sau biết nhánh đó **chưa** được một kịch bản nào chạy qua.

9. **`sp_report_daily_takings` trả 24 dòng, không phải 21.** Kịch bản #91 dự đoán *"7 ngày × 3 shop
   = 21 dòng"*. Thủ tục lọc `i.Date >= CURDATE() − INTERVAL 7 DAY`, tức là **8 ngày** tính cả hôm
   nay, nên `8 × 3 = 24`. Đây là hành vi **có từ trước** khối này và mục 4.8 đã chốt không đổi câu
   truy vấn đó (nó là đề bài câu 2). Điều kiện thật sự cần kiểm — **không ô rỗng** — đã kiểm bằng số
   riêng: `24 ô cần có / 24 ô thực có`.

### 9.3. Kết quả kiểm thử đã chạy

**Đã chạy thật bằng máy (MySQL 8.0.45, JDK có `javac`), tất cả đạt:**

| # | Việc | Kết quả |
|---|---|---|
| 73 | Nạp `04_routines.sql` và `05_invariants.sql`, mỗi file 2 lần | Cả hai lần xong, không `already exists` |
| 74 / 75 | `fn_active_promo_pct('8934001'/'8934009', CURDATE())` | `10.00` / `0.00` (**không** `NULL`) |
| 76 | Lưới 20 mã vạch × 7 ngày, hàm so thủ tục | **140 ô, 0 lệch.** Ba ô chốt tay: `−50` → `12.00` (`PR04` 8% + `PR05` 12% → *phải chọn*), `−70` → `8.00`, `8934006 @ −30` → `0.00` (`PR06` `PAUSED` phủ mà vẫn 0) |
| 77 | `fn_line_discount` so `NewInvoicePanel.lineDiscount` | **60 ô, 0 lệch**, kể cả `pct = 0` và SL `0.5` / `1.5`. `#77b` qua hàm thật: `4.480,00` |
| 78 | `sp_check_seed` trên **seed cũ** | R1, R3–R11, R13, R14 = 0; **R12 = 84**; **R2 = 1** (xem 9.2 điểm 1). `@v = 85` — bộ kiểm không chỉ luôn trả 0 |
| 79 | Nạp `02_seed.sql` **3 lần liên tiếp** | Cả 3 lần xong, không `Duplicate entry`; số dòng mỗi bảng **giống nhau** sau mỗi lần |
| 80 | `sp_check_seed` trên seed mới | **`@v = 0`** — cả 14 quy tắc, kể cả R1/R2 trên 1.205 dòng hàng |
| 81 | Đếm dòng | `Invoice 548 / Invoice_Line 1.205 / Payment 645 / Return 49 / Customer 30 / Product 20`; ba bảng con: `Cash 299 / Card 174 / EWallet 172` |
| 82 | `Status, COUNT(*)` | `PAID 459 / OPEN 77 / VOIDED 12` — cả ba hàng chục dòng |
| 83 | `COUNT(DISTINCT Date)` trong 90 ngày | **91** |
| 84 | `sp_get_invoice_lines('INV-0001')` | **3 dòng**, dòng 1 `Giam_gia = 4.480` > 0, hoá đơn ở `'PAID'` |
| 85 | `CHECKSUM TABLE` trước/sau khi nạp lại | **Giống nhau từng bảng** (7 bảng) — xác định, không `RAND` |
| 86 | `Return_ID, COUNT(*)` | `1 → 42`, **`2 → 7`** — cột `Lan_tra` lần đầu có dữ liệu |
| 87 | `Tendered_Amount > Amount` | **277 / 299** dòng tiền mặt có tiền thối thật; R5 vẫn 0 |
| 88 | Đo thời gian | Nạp `02_seed.sql` **9,5 s** (ngưỡng 60 s); `sp_check_seed` **0,4 s** (ngưỡng 10 s). Toàn bộ `01+04+02+05` 10,6 s. **Không cần thêm `INDEX`** |
| 89 | `sp_search_customers_by_phone` | `'09'` → **đúng 20 dòng** (chạm `LIMIT 20`); `'0901'` → 4; `'07'` → 3 |
| 90 | `Promotion` + `Applies_To` | 6 chương trình, 9 dòng; `PR04`/`PR05` `Expiry_Date` **tương lai** trong khi cửa sổ **đã qua** |
| 91 | `sp_report_daily_takings` | 24 dòng (8 ngày × 3 shop — xem 9.2 điểm 9), **24 ô cần có / 24 ô thực có: không ô rỗng** |
| 92 | `sp_report_price_rise` | **Đúng 5 sản phẩm** (`001, 003, 006, 008, 010`), y như trước khối này |
| 94 | `sp_report_best_cashier` | 5 dòng; **`E03` đứng đầu rõ ràng** (`488.213` so với `337.353` — hơn 45%) |
| 95 | `sp_report_big_returns` | **10 dòng** trải tháng 7 → tháng 10, mức `100% / 75% / 64,49%` |
| 96 | `sp_report_old_price_proof` | **Đúng 8 dòng gộp** (8 sản phẩm đã đổi giá) kèm `So_dong_hang` và khoảng ngày — không phải ~1.200 dòng |
| 97 | So #96 với `03_queries.sql` BONUS | **Khớp từng dòng** — hai bản chép thôi lệch nhau |
| 109 | `check-contracts.ps1` | **89 đạt, 0 sai** (87 của khối 5 + nhóm *Seed*); `e2e()` lập 3 hoá đơn thật rồi xoá, chạy được trên seed mới |
| 110 | Hoá đơn do **app** lập hôm nay → `check-seed` | **`@v = 0`** (`8934003`, giá `175.000`, giảm `17.500`) |
| 111 | Hoá đơn do **app** lập ở `−60` ngày | Đơn giá **`28.000`** (giá *cũ*) và giảm **`6.720`** = 12% của `PR05` — khuyến mãi *của ngày đó*. `@v = 0` |
| 112 | `logs/sql.log` trước/sau khi nạp seed | **240.051 byte / 2.310 dòng, không đổi một byte.** Seed đi qua `mysql` CLI nên nhật ký của khối 1 **không bị ghi gì**, còn tốt hơn "một dòng" mà mục 4.5 hứa |
| 115 | `javac -Xlint:all` toàn app | **0 cảnh báo `deprecation`, 0 lỗi**; còn `[serial] 38` + `[this-escape] 9` của Swing — đúng nhóm mà `plan/14` kịch bản #67 đã chốt |
| 116 | Nạp `01 → 04 → 02` từ database trắng | Ba bước xong sạch, rồi `check-seed` = **0** |
| 117 | Đường Setup App: `sql-create-db` → `sql-seed-data` → `sql-run-queries` | Cả ba xong, **`SqlTab.java` không đổi một dòng nào** |
| 118 | `sql-seed-data` **hai lần** | Lần hai cũng xong — trước khối này là `Duplicate entry 'S01'` |

**Chưa chạy — cần người bấm chuột (kịch bản #93, #98–#108, #113, #114):**

- **#93** (chạy #92 **trong 45 ngày đầu năm**): cần đổi ngày hệ thống. Suy luận đã ghi ở mục 3.2 và
  cơ chế bảo vệ đã kiểm **gián tiếp** bằng R1: đơn giá được tra bằng `fn_get_product_price` tại ngày
  bán nên phép kẹp không thể làm sai một dòng hàng nào. Vẫn nên chạy thật một lần.
- **#98–#108** (tab 3 ô chọn khoảng ngày và nhãn, hai đơn giá cạnh nhau, `PR04`/`PR06` trên giao
  diện, hộp thoại trả hàng với `Lan_tra` = 1 và 2, nhãn trạng thái `OPEN`, tab 2 với `Invoice_Line`,
  5 nút báo cáo bấm liên tiếp, tắt MySQL rồi bấm báo cáo).
  Phần SQL đứng sau chúng **đã** kiểm: `sp_get_recent_invoices` trả `48 / 186 / 546 / 548` dòng cho
  `7 / 30 / 90 / 0`, và `INV-0001` + `INV-OLD-01/02` **có** trong kết quả khi chọn *Tất cả* (#99).
- **#113** (popup khách hàng ở tab 1): phần SQL đã kiểm ở #89 — `'09'` trả đúng 20 dòng.
- **#114** (đăng nhập nhanh, đổi vai trò, khoá/mở nút): khối này không chạm một dòng nào của khối 2
  và khối 4.

### 9.4. Chốt bằng số cho khối sau

```powershell
.\scripts\ps1\check-seed.ps1            # macOS/Linux: ./scripts/bash/check-seed.sh
.\scripts\ps1\check-contracts.ps1       # macOS/Linux: ./scripts/bash/check-contracts.sh
```

**Một lệnh, 14 quy tắc, exit code khác 0 là có vi phạm.** Cả hai script **tự nạp**
`05_invariants.sql`, nên chúng chạy được ngay sau bất kỳ đường dựng database nào — không có bước
tay nào để quên (9.2 điểm 6). `check-seed` **không ghi một dòng dữ liệu nào**, nên chạy được bất cứ
lúc nào, kể cả sau khi người dùng đã bấm chuột làm bẩn database.

Khối nào thêm một quy tắc nghiệp vụ thì **thêm một dòng vào `sp_check_seed`**, đúng như khối 5 thêm
phép kiểm vào `ContractCheck`. Cột `Hop_dong` của bảng kết quả trỏ về mục đặc tả, nên một dòng `SAI`
tự nói nó vi phạm cái gì.

**Ba con số để khối sau không phải đo lại:**

| Việc | Đo được | Ngưỡng của kế hoạch |
|---|---|---|
| Nạp `02_seed.sql` (548 hoá đơn, 1.205 dòng hàng) | **9,5 s** | < 60 s |
| `CALL sp_check_seed(@v)` (R1/R2 gọi hàm trên 1.205 dòng) | **0,4 s** | < 10 s |
| Nạp cả `01 + 04 + 02 + 05` từ database trắng | **10,6 s** | — |

Nên **`INDEX` vẫn là tối ưu một thứ chưa cần tối ưu** (mục 2.3): không ngưỡng nào bị vượt, và còn
xa. Trần thật đáng lo vẫn là `LPAD(..., 4, '0')` của `fn_next_invoice_id` / `fn_next_payment_id`:
cấu hình `90 × 6` dùng `548` / `645` trên trần `9999`, nhưng `365 × 20` là **chạm trần**, và lúc đó
`ORDER BY p.Payment_ID` ở `sp_get_invoice_payments` sắp theo **chuỗi** nên `PAY-10000` đứng **trước**
`PAY-9999` (lỗi #22). Comment trên tham số của `sp_seed_demo_data` nhắc lại con số này.

**Hai bản chép đã nhận, và cái chốt của chúng.** `fn_active_promo_pct` là bản chép thứ hai của điều
kiện chọn khuyến mãi (mục 3.1 đã nhận). Vì cả seed **và** quy tắc R2 đều gọi `fn_line_discount`, nếu
hàm đó sai thì R2 **vẫn trả 0** — hai bên sai khớp nhau (rủi ro #1 và #2). Cái phá vòng tròn đó là
kịch bản **#76** (so hàm với *thủ tục*) và **#77** (so hàm với *Java*), cộng ba ô chốt tay
(`12.00` / `8.00` / `0.00`) để phép kiểm không chỉ đồng ý với chính nó. **Đừng sửa một bên.**
