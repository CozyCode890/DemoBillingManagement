# Kế Hoạch Chi Tiết — Khối 4: Trạng Thái Hoá Đơn (OPEN / PAID / VOIDED)

> **Dành cho AI / lập trình viên tiếp theo:** đây là khối thứ tư trong đợt cải tiến gồm 6 khối
> (1 SQL log → Setup App ✅ xong, 2 đăng nhập nhanh ✅ xong, 3 sửa trả hàng ✅ xong,
> **4 trạng thái PAID/OPEN/VOIDED ✅ xong ← khối này**, 5 UI tab lập hoá đơn, 6 seed động 90 ngày).
>
> **Đã triển khai 2026-10-11 — xem mục 9** để biết file nào đã đổi, chỗ nào làm khác kế hoạch,
> và kịch bản nào đã chạy thật.
>
> **Khối này SỞ HỮU cột `Invoice.Status`.** Khối 3 đã cố ý chỉ *đọc* cột đó và để lại ghi chú chỉ
> đường ngay trong `sql/04_routines.sql:626-629`. Mọi quy tắc về vòng đời trạng thái hoá đơn — ai
> đặt, đặt lúc nào, đổi được sang đâu — quyết định ở khối này.
>
> **Khối này CHẠM VÀO DATABASE.** Thêm 2 hàm + 3 thủ tục vào `sql/04_routines.sql`, sửa 4 thủ tục
> đang có, **không** đổi `sql/01_schema.sql` (xem mục 3.3 — đây là một kết luận, không phải sự
> bỏ sót). Vì vậy **thứ tự triển khai là bắt buộc: nạp SQL trước, biên dịch Java sau** (mục 6), và
> phải đọc mục 3 trước khi gõ dòng code đầu tiên.
>
> Giao điểm với khối 3: `sp_add_return` đọc `Status` để chặn trả hàng — khối này **thêm** `OPEN` vào
> danh sách bị chặn (mục 3.1). Giao điểm với khối 5: `NewInvoicePanel` đổi 2 thông điệp (mục 5.7).

---

## 1. Bối Cảnh & Vấn Đề

### 1.1. Hiện trạng (đã đọc code + SQL + seed, không phỏng đoán)

| Thành phần | Trạng thái hiện tại |
|---|---|
| `sql/01_schema.sql:138` | `Status ENUM('OPEN','PAID','VOIDED') NOT NULL` — không `DEFAULT`, nên mọi `INSERT` buộc phải nói rõ trạng thái |
| `sql/04_routines.sql:564-565` | `sp_create_invoice_header` **ghi cứng `'PAID'`**. Hoá đơn **sinh ra đã là PAID** |
| `sql/04_routines.sql:782-800` | `sp_update_invoice_header` nhận `p_status ENUM('OPEN','PAID','VOIDED')` và ghi thẳng vào cột. **Đây là chỗ duy nhất trong cả dự án ghi `Status`** sau khi hoá đơn đã tạo |
| `sql/02_seed.sql:118-137` | 17 hoá đơn: **16 `PAID`**, **1 `VOIDED`** (`INV-0015`), **0 `OPEN`** |
| `sql/02_seed.sql:185` | `INV-0015` (`VOIDED`) **có** 1 dòng hàng, nên nó vẫn hiện ở tab 3 |
| `sql/02_seed.sql:208-225` | 17 dòng `Payment`. `INV-0015` (`VOIDED`) **không có dòng thanh toán nào** — seed đã ngầm hiểu "huỷ = không có tiền" |
| `sql/04_routines.sql:440, 445` | `sp_report_daily_takings` — doanh thu `SUM(l.Quantity * l.Unit_Price - l.Discount)`, lọc `WHERE i.Status = 'PAID'` |
| `sql/04_routines.sql:489` | `sp_report_best_cashier` — `JOIN Invoice i ON ... AND i.Status = 'PAID'` |
| `sql/03_queries.sql:54, 67, 120` | Hai câu truy vấn học thuật lặp lại đúng hai bộ lọc trên |
| `sql/04_routines.sql:277-285, 308-316` | `sp_get_recent_invoices` và `sp_get_invoice_header` trả cột `Trang_thai`; **cả hai `JOIN Invoice_Line` (inner join)** |
| `sql/04_routines.sql:654-672` | `sp_add_return` đọc `i.Status` sau `FOR UPDATE`, chặn `VOIDED` bằng `SIGNAL '45000'` — chốt cuối của khối 3 |
| `sql/04_routines.sql:626-629` | Ghi chú khối 3 để lại: *"Neu khoi 4 muon doi Status sang RETURNED / PARTIALLY_RETURNED..."* → mục 3.3 trả lời |
| `src/billing/dao/InvoiceDao.java:80-170` | `createInvoice` — **một transaction nguyên vẹn**: header + N dòng hàng + N thanh toán + `commit`. Lỗi là `rollback` sạch |
| `src/billing/dao/InvoiceDao.java:305-332` | `updateInvoiceHeader(...)` — `cs.setString(4, status)`, **không kiểm giá trị, không kiểm chuyển trạng thái** |
| `src/billing/ui/NewInvoicePanel.java:906-918` | Trả thiếu/thừa tiền chỉ bị **hỏi** *"Van luu chu?"* — bấm Yes là lưu, và lưu ra một hoá đơn `PAID` |
| `src/billing/ui/NewInvoicePanel.java:677-693` | `Payment.Amount` = số tiền **ghi nhận**; tiền khách đưa đi vào `Cash_Payment.Tendered_Amount` (`detail1`) |
| `src/billing/ui/InvoiceViewPanel.java:315-345` | `applyRolePermissions()` — khoá **7** nút với thu ngân |
| `src/billing/ui/InvoiceViewPanel.java:447-448` | Đọc `Status` bằng **SQL thô** `SELECT ... FROM Invoice WHERE Invoice_ID = ?` qua `QueryResult.run` |
| `src/billing/ui/InvoiceViewPanel.java:840-856` | Chặn trả hàng khi `VOIDED`; comment `:850-851` nhắc cột này thuộc khối 4 |
| `src/billing/ui/InvoiceViewPanel.java:1164-1180` | `showReturnFailure()` — phân biệt `SQLSTATE 45000` (lỗi nghiệp vụ) với lỗi thật. Hiện chỉ trả hàng dùng |
| `src/billing/ui/EditInvoiceDialog.java:27` | `new JComboBox<>(new String[]{"OPEN", "PAID", "VOIDED"})` — **ba literal ghi cứng trong Java** |
| `src/billing/ui/EditInvoiceDialog.java:63, 168, 175` | Ô trạng thái gắn nhãn `"Trang thai *:"` + `"(NOT NULL)"`, lưu qua `dao.updateInvoiceHeader(...)` |
| `src/billing/ui/SearchPanel.java:337-366` | Ba chip lọc: `Tất cả` / `Đã hoàn tất` (PAID) / `Đã hủy` (VOIDED) — **không có chip `OPEN`** |
| `src/billing/ui/SearchPanel.java:424-431` | `where.append("Invoice.Status = 'PAID' AND ")` — bản chép thứ ba của các literal |

Nói gọn: **`Status` là một cột tồn tại nhưng không có vòng đời.** Nó được gán một lần lúc tạo (luôn
là `PAID`), không bao giờ được hệ thống đổi, và chỉ đổi được bằng tay qua hộp thoại sửa hoá đơn.
Hai báo cáo và hai câu truy vấn thì lại **tin** rằng `PAID` nghĩa là "đã thu tiền thật".

### 1.2. Chín lỗi cụ thể

**Nhóm A — `Status` không nói đúng sự thật**

1. **Hoá đơn trả thiếu tiền vẫn được lưu là `PAID`.** `NewInvoicePanel.java:906-913`: nếu
   `due != paid` thì app **hỏi** *"Tong hoa don 500.000 d nhung da thanh toan 1.000 d. Van luu
   chu?"*. Bấm **Yes** → `createInvoice` chạy → `sp_create_invoice_header:565` ghi `'PAID'` →
   hoá đơn đó **được cộng đủ 500.000** vào `sp_report_daily_takings`. Không có gì sai trong
   transaction (nó nguyên vẹn, mục 1.1); cái sai là **cột trạng thái không liên quan gì tới số
   tiền thực thu**.

2. **Trạng thái `OPEN` chưa bao giờ được dùng.** Literal `OPEN` xuất hiện đúng 3 chỗ trong cả dự
   án: định nghĩa `ENUM` (`01_schema.sql:138`), tham số `p_status` (`04_routines.sql:786`), và
   mảng `JComboBox` (`EditInvoiceDialog.java:27`). Không chỗ nào **ghi** nó, seed cũng không có
   dòng nào. Đây đúng là giá trị mà lỗi #1 cần: hoá đơn **chưa thu đủ tiền** là `OPEN`.

3. **`VOIDED` không có nút huỷ hoá đơn.** Muốn huỷ, quản lý phải mở hộp thoại *Chinh sua Hoa don*
   và đổi ô trạng thái — cùng hộp thoại dùng để sửa ngày, giờ, quầy, thu ngân. Huỷ một hoá đơn là
   **hành động nghiệp vụ**, không phải một lần sửa trường. Tab 3 có nút *Xoa hoa don* (xoá vĩnh
   viễn, `:477-500`) nhưng **không có** nút *Huy hoa don* — nên cách "huỷ" dễ nghĩ tới nhất lại là
   cách phá dữ liệu nhất.

4. **Sửa dòng hàng xong thì `PAID` thành lời nói dối, không ai báo.** `sp_update_invoice_line`
   (`:806-822`) cho quản lý đổi `Quantity` / `Unit_Price` → tổng phải trả đổi theo, trong khi
   `Payment` giữ nguyên. Hoá đơn 100.000 đã thu đủ, sửa số lượng từ 1 lên 10 → phải trả
   1.000.000, đã thu 100.000, trạng thái vẫn `PAID`.

**Nhóm B — không có quy tắc chuyển trạng thái**

5. **Mọi chuyển đổi đều được phép, kể cả chuyển ngược.** `sp_update_invoice_header:795` ghi bất kỳ
   giá trị nào trong `ENUM` lên bất kỳ giá trị nào đang có: `VOIDED → PAID` (hồi sinh hoá đơn đã
   huỷ), `PAID → OPEN` (mở lại hoá đơn đã thu tiền, tiền trong `Payment` vẫn còn nguyên). Java
   không chặn (`InvoiceDao.java:316` chỉ `setString`), SQL không chặn.

6. **Ba bản chép của bộ giá trị `ENUM`.** `01_schema.sql:138` (bản gốc),
   `EditInvoiceDialog.java:27` (mảng `String[]`), `SearchPanel.java:427-429` (literal trong chuỗi
   `WHERE`). Thêm một trạng thái là phải sửa đúng ba chỗ, mà **không chỗ nào báo** nếu quên — đúng
   loại lỗi mà khối 3 đã trị bằng nguyên tắc "công thức chỉ tồn tại ở một chỗ" (`plan/12` mục 2.2).

7. **`SearchPanel` thiếu chip `OPEN`.** Hiện vô hại vì chưa có hoá đơn `OPEN` nào; **thành lỗi
   ngay** khi #1/#2 được sửa: chip `Tất cả` hiện cả hoá đơn đang lập mà không có cách nào lọc riêng
   chúng ra, và chip `Đã hoàn tất` im lặng ẩn chúng đi.

**Nhóm C — hệ quả kỹ thuật**

8. **`InvoiceViewPanel.java:447` đọc `Status` bằng SQL thô.** Phá quy ước "Swing UI → DAO → Stored
   Procedure" của `docs/05-SQL-JAVA-CONVENTION.md` mục 1. Một câu `SELECT` nằm ngoài tầm với của
   mọi thay đổi trong SQL: đổi tên cột hay thêm cột là nó sai thầm lặng. Đây cũng là câu SQL thô duy
   nhất còn lại trong `InvoiceViewPanel` ngoài `QueryResult.run("SELECT '' WHERE 1=0")` dùng để
   xoá trắng bảng.

9. **Hoá đơn không còn dòng hàng nào thì biến mất khỏi tab 3.** `sp_get_recent_invoices:282` và
   `sp_get_invoice_header:313` dùng `JOIN Invoice_Line` (inner). Quản lý xoá hết dòng hàng bằng
   `sp_delete_invoice_line` → hoá đơn vẫn nằm trong database, vẫn `PAID`, nhưng **không hiện ở
   bảng nào** → không chọn được → không sửa được, không xoá được bằng giao diện. Khi `OPEN` thành
   trạng thái thật (hoá đơn đang lập, chưa có dòng) thì đây là đường đi chính, không còn là ngoại lệ.

---

## 2. Mục Tiêu & Phạm Vi

### 2.1. Mục tiêu

1. **`Status` nói đúng sự thật:** `OPEN` = chưa thu đủ tiền; `PAID` = đã thu đủ; `VOIDED` = đã huỷ.
2. **Hoá đơn sinh ra là `OPEN`**, và **chỉ** thành `PAID` khi tiền đã vào đủ — **do SQL quyết định**,
   không do Java tự tin.
3. **Chỉ những chuyển trạng thái hợp lệ được phép** (mục 3.2), kiểm ở SQL, báo lỗi đọc hiểu được ở UI.
4. **Có nút *Huy hoa don* riêng** cho quản lý, tách hẳn khỏi *Xoa hoa don* và khỏi hộp thoại sửa.
5. **Bộ giá trị trạng thái chỉ còn một bản chép trong Java** (`InvoiceStatus`), hết ba bản rời rạc.
6. **Báo cáo tin được:** `WHERE Status = 'PAID'` ở 4 chỗ **không phải sửa một chữ** mà tự đúng.
7. `SearchPanel` lọc được cả `OPEN`; hoá đơn không có dòng hàng vẫn hiện ở tab 3.

### 2.2. Nguyên tắc bất di bất dịch

> **Trạng thái hoá đơn là hệ quả của dữ liệu, không phải một ô người dùng gõ vào.**

`PAID` phải suy ra được từ `SUM(Payment.Amount)` so với tổng tiền các dòng hàng. Một hoá đơn `PAID`
mà không ai trả tiền thì cột đó đang nói dối, và cả 4 câu `WHERE Status = 'PAID'` nói dối theo.
Hệ quả cụ thể:

- **Java không được tự gán `PAID`.** Java gọi thủ tục; thủ tục đọc `Payment`, tự quyết. Đây là bản
  sao của nguyên tắc khối 3 ("Java không được tự tính tiền hoàn, dù chỉ một phép nhân").
- **Ô trạng thái trên hộp thoại sửa trở thành chỉ đọc.** Không phải vì phân quyền — mà vì không ai,
  kể cả quản lý, *gõ* ra được một trạng thái đúng.
- Giữ nguyên quy ước hai khối trước: **UI kiểm để thân thiện, SQL kiểm để đúng, SQL là chốt cuối**
  (`plan/12` mục 3.4) — và **khoá ở UI, không khoá ở DAO** (`InvoiceViewPanel:37-40`).
- Giữ **Additive First** (`plan/06` mục 1.2): thêm thủ tục mới thay vì viết lại thủ tục cũ; sửa
  thủ tục cũ thì sửa tối thiểu và giữ nguyên thứ tự tham số (`docs/05` Quy tắc 2).

### 2.3. Ngoài phạm vi khối này (và vì sao)

| Việc | Vì sao không làm ở khối này |
|---|---|
| Thêm `RETURNED` / `PARTIALLY_RETURNED` vào `ENUM` | **Đã xét và loại** — mục 3.3 |
| Trừ tiền hoàn khỏi doanh thu trong `sp_report_daily_takings` | Đây là đổi **định nghĩa doanh thu** (gộp hay không gộp hàng trả lại), không phải đổi trạng thái. Hiện `sp_report_big_returns` đã chỉ ra các hoá đơn bị trả > 50% nên thông tin không bị mất. Để thành khối riêng, cùng với việc ghi phiếu hoàn tiền |
| Ghi phiếu hoàn tiền (dòng `Payment` âm) | `plan/12` mục 2.3 đã đẩy sang "khối trạng thái/thanh toán", nhưng vẫn quá lớn: mọi `SUM(Amount)` trong `03_queries.sql` và các báo cáo đang giả định tiền thu vào. Và nó sẽ phá luôn phép kiểm "đã thu đủ" ở mục 3.4 |
| Trạng thái của `Payment` (chờ / thành công / thất bại) | Bảng `Payment` không có cột trạng thái (`01_schema.sql:202-209`). Thêm là một khối riêng |
| Tự chuyển `PAID → OPEN` khi quản lý sửa dòng hàng làm tổng tiền tăng (lỗi #4) | **Cố ý không làm:** chuyển ngược bị cấm ở mục 3.2, và một nút "sửa dòng hàng" âm thầm huỷ trạng thái thanh toán của hoá đơn là thứ tệ hơn lỗi #4. Thay vào đó mục 5.5 **cảnh báo** cho quản lý thấy sự lệch, rồi để họ quyết |
| Cột lý do huỷ (`Void_Reason`) | Lược đồ không có cột đó; thêm cột để lưu một chuỗi tự do là mở một hướng khác (lịch sử thao tác / audit log), đáng một khối riêng |
| Làm lại UI tab 1, sửa `VAT_pct` hiện sai định dạng ở tab 3 | Khối 5 (`plan/12` mục 2.3 đã giao) |
| Chặn quyền ở tầng DAO/SQL | Giữ quy ước đã ghi trong javadoc `InvoiceViewPanel:37-40` |

---

## 3. Hợp Đồng Trạng Thái (quan trọng nhất)

Khối 1 có hợp đồng định dạng file, khối 2 có hợp đồng cấu hình, khối 3 có hợp đồng tính tiền.
Khối này có **hợp đồng vòng đời**. Sửa một bên phải sửa tài liệu này.

### 3.1. Ba trạng thái nghĩa là gì

| Giá trị | Nghĩa | Ai đặt | Vào báo cáo doanh thu? | Trả hàng được? |
|---|---|---|---|---|
| `OPEN` | Đang lập / **chưa thu đủ tiền** | `sp_create_invoice_header` lúc tạo | **Không** (4 câu `WHERE Status='PAID'` tự loại) | **Không** — chưa trả tiền thì chưa hoàn gì |
| `PAID` | **Đã thu đủ tiền** | **`sp_settle_invoice` tự chuyển**, không ai gõ tay | Có | Có |
| `VOIDED` | Đã huỷ | Quản lý, qua `sp_void_invoice` | **Không** | **Không** (đã là chốt cuối ở `sp_add_return:668`) |

Ba điểm phải đọc kỹ:

- **`OPEN` bị chặn trả hàng là thay đổi hành vi so với khối 3.** `plan/12` mục 2.3 cố ý *không*
  chặn `OPEN` vì *"ý nghĩa thật của OPEN do khối 4 định nghĩa"*. Giờ đã định nghĩa: `OPEN` = chưa
  thu đủ tiền → hoàn tiền cho một hoá đơn chưa thu tiền là vô nghĩa. Chặn ở **cả hai** chỗ: UI
  (`InvoiceViewPanel:852`) và SQL (`sp_add_return`).
- **`VOIDED` giữ nguyên các dòng `Payment` đã ghi.** Huỷ không xoá dấu vết tiền — ai đã trả bao
  nhiêu vẫn đọc được ở tab 3. Việc *trả lại* số tiền đó là nghiệp vụ hoàn tiền, ngoài phạm vi
  (mục 2.3). Seed đã ngầm theo quy ước này: `INV-0015` (`VOIDED`) không có dòng `Payment` nào.
- **`VOIDED` khác `Xoa hoa don`.** Huỷ = dữ liệu còn, không tính vào báo cáo. Xoá = biến mất khỏi 7
  bảng (`sp_delete_invoice:747-776`). Hộp thoại xác nhận ở mục 5.4 phải nói rõ sự khác nhau đó.

### 3.2. Chuyển trạng thái nào được phép

```
            (tạo mới)
                |
                v
             [OPEN] ---- sp_settle_invoice: đã thu đủ ----> [PAID]
                |                                             |
                | sp_void_invoice                             | sp_void_invoice
                v                                             v
            [VOIDED]  <--- không có đường ra --->          [VOIDED]
```

Hai cạnh được phép, bốn cạnh bị cấm, và **chốt cuối nằm ở SQL**:

| Chuyển | Cho phép? | Đường duy nhất | SQL chặn ở đâu |
|---|---|---|---|
| `(mới) → OPEN` | ✔ | `sp_create_invoice_header` | — |
| `OPEN → PAID` | ✔ | `sp_settle_invoice`, **chỉ khi đã thu đủ** | `sp_settle_invoice` tự tính, không nhận tham số trạng thái |
| `OPEN → VOIDED`, `PAID → VOIDED` | ✔ | `sp_void_invoice` | — |
| `PAID → OPEN` | ✘ | — | `sp_update_invoice_header` `SIGNAL '45000'` |
| `VOIDED → OPEN`, `VOIDED → PAID` | ✘ | — | `sp_update_invoice_header` `SIGNAL '45000'` |
| `OPEN → PAID` **bằng tay** | ✘ | — | `sp_update_invoice_header` `SIGNAL '45000'`, kèm câu chỉ đúng chỗ phải làm |
| `* → VOIDED` **qua hộp thoại sửa** | ✘ | — | `sp_update_invoice_header` `SIGNAL '45000'`: *"dung nut Huy hoa don"* |

Hệ quả gọn gàng: **`sp_update_invoice_header` không còn đổi trạng thái được chút nào.** Nó vẫn nhận
`p_status` (giữ 7 tham số, đúng `docs/05` Quy tắc 2) nhưng tham số đó trở thành **phép kiểm**: giá
trị truyền xuống phải **bằng** trạng thái đang có trong database, nếu không thì từ chối. Nhờ vậy
`InvoiceDao.updateInvoiceHeader` không phải đổi chữ ký, và hộp thoại sửa cứ truyền xuống đúng cái
trạng thái nó đọc lên.

Lý do cấm `PAID → OPEN`: tiền trong `Payment` không mất đi khi đổi trạng thái, nên một hoá đơn
`OPEN` mà đã thu đủ tiền là trạng thái **tự mâu thuẫn** — và `sp_settle_invoice` sẽ chuyển nó lại
`PAID` ngay lần chạy sau. Cho phép đổi ngược là mời người dùng tạo ra dữ liệu mà hệ thống không
diễn giải nổi.

### 3.3. Hàng bị trả hết thì trạng thái là gì? — **ĐÃ CHỐT: không đổi `Status`**

Đây là câu hỏi mà `sql/04_routines.sql:626-629` để lại. Ba phương án:

| | Phương án | Kết luận |
|---|---|---|
| **A** | **Không đổi `Status`.** "Hàng đã trả" là thông tin của bảng `` `Return` ``; muốn biết thì `JOIN` — và `sp_get_returnable_lines` (cột `SL_con_lai`) cùng `sp_report_big_returns` đã trả lời sẵn | **ĐÃ CHỌN.** `ENUM` không đổi → `01_schema.sql` không đổi → 4 câu `WHERE Status = 'PAID'` không phải sửa. Trạng thái giữ đúng **một trục nghĩa duy nhất: tiền đã vào hay chưa** |
| B | Thêm `RETURNED` / `PARTIALLY_RETURNED` vào `ENUM` | **Loại.** `WHERE Status = 'PAID'` ở `04_routines.sql:445`, `:489`, `03_queries.sql:67`, `:120` sẽ **im lặng loại bỏ** mọi hoá đơn bị trả một phần → doanh thu tụt mà không ai biết vì sao. Phải sửa 4 câu SQL + 3 bản chép ở lỗi #6, mà đổi được gì? Một thông tin đã đọc được từ bảng `` `Return` `` |
| C | Thêm cột riêng `Return_State` | **Loại.** Thật hơn B (hai trục, hai cột) nhưng là **giá trị dẫn xuất lưu trong bảng**: phải đồng bộ ở mọi chỗ ghi/xoá lượt trả (`sp_add_return`, `sp_delete_return`, `sp_delete_invoice_line`). Trái thẳng nguyên tắc mục 2.2 |

**Việc phải làm theo kết luận này:** sửa comment `04_routines.sql:626-629` thành câu trả lời (khối
4 đã xét và chọn A, lý do một dòng) thay vì để một lời mời đi sai đường. Đúng việc mà khối 3 đã làm
khi xoá đoạn code chết `InvoiceDao:390-397` (`plan/12` rủi ro #9).

### 3.4. "Thu đủ tiền" là đủ bao nhiêu? — `SUM(Payment.Amount) >= tổng phải trả`

```
Gọi  due  = SUM(Invoice_Line.Quantity * Unit_Price - Discount)   -- đúng công thức sp_get_invoice_header:307
     paid = SUM(Payment.Amount)                                   -- của hoá đơn đó

     "đã thu đủ"  ⟺  hoá đơn CÓ ít nhất một dòng hàng  AND  paid >= due
```

Bốn quyết định trong một công thức ngắn:

1. **`>=` chứ không `=`.** `Payment.Amount` là số tiền **ghi nhận**, không phải tiền khách đưa —
   tiền khách đưa nằm ở `Cash_Payment.Tendered_Amount` (`NewInvoicePanel:682` truyền đúng như vậy).
   Nên `paid > due` là chuyện ghi nhận thừa, và nó **không được** làm hoá đơn mắc kẹt ở `OPEN`.
   Giao diện vẫn giữ phép kiểm chặt hơn (`due.compareTo(paid) != 0` → hỏi lại, `:908`) vì ở đó cảnh
   báo sớm là có ích; SQL thì rộng hơn vì SQL là chốt cuối, không phải người gác cửa.
2. **Hoá đơn không có dòng hàng nào thì KHÔNG bao giờ `PAID`**, dù có bao nhiêu tiền. Phải kiểm
   bằng `EXISTS (SELECT 1 FROM Invoice_Line ...)` **chứ không** dựa vào `due`: hàm trả
   `COALESCE(..., 0)` nên hoá đơn rỗng cho `due = 0`, và `0 >= 0` là `TRUE`. Đây là cái bẫy dễ sa
   nhất của cả khối này.
3. **Không làm tròn thêm.** `Invoice_Line` cho `DECIMAL(12,2)`, `Payment.Amount` cũng
   `DECIMAL(12,2)`; so sánh trực tiếp hai `DECIMAL` là chính xác. Không `ROUND`, không `double`.
4. **Phép so sánh chỉ tồn tại ở một chỗ** — trong `sp_settle_invoice`, qua hai hàm
   `fn_invoice_amount_due` / `fn_invoice_amount_paid`. Java không được so sánh hai số này để tự
   đoán trạng thái; muốn hiển thị thì gọi hàm.

**Bất biến mà seed hiện có đã thoả** (kiểm được bằng câu SQL ở kịch bản #3): mọi hoá đơn `PAID`
trong `02_seed.sql` đều có `paid >= due` — ví dụ `INV-0005` = `200.000 + 177.800` = `377.800`
(`seed:213-214`), `INV-0014` = `231.700` đúng bằng tổng dòng hàng (`seed:181, 223`). Nhờ vậy sau
khối này seed **không cần sửa**, và đó là một phép kiểm chéo: nếu có hoá đơn seed vi phạm thì hoặc
công thức sai, hoặc seed sai.

### 3.5. Tên cột và tên thủ tục là hợp đồng

| Routine mới | Tham số | Trả về |
|---|---|---|
| `fn_invoice_amount_due` | `p_invoice_id` | `DECIMAL(12,2)` — tổng phải trả, `0.00` nếu không có dòng |
| `fn_invoice_amount_paid` | `p_invoice_id` | `DECIMAL(12,2)` — tổng đã ghi nhận, `0.00` nếu chưa trả |
| `sp_settle_invoice` | `p_invoice_id` | **không trả ResultSet** (xem mục 4.4) |
| `sp_void_invoice` | `p_invoice_id` | không trả ResultSet |
| `sp_get_invoice_edit_fields` | `p_invoice_id` | 7 cột: `Ma_HD, Ngay, Gio, Trang_thai, Quay, Thu_ngan, Khach` |

`sp_get_invoice_edit_fields` giữ **đúng thứ tự 7 cột** của câu SQL thô nó thay thế
(`InvoiceViewPanel:447`), vì `:454-460` đọc theo chỉ số `r[1]`..`r[6]`.

---

## 4. Thiết Kế SQL

### 4.1. Đừng quên khối `DROP` ở đầu `04_routines.sql`

File tự xoá mọi thứ trước khi tạo lại (`:17-53`) để chạy lại được nhiều lần. Thiếu một dòng `DROP`
là lần chạy thứ hai báo *"already exists"*. Thêm vào đúng nhóm:

```sql
DROP FUNCTION  IF EXISTS fn_invoice_amount_due;      -- vao nhom dong 18-22
DROP FUNCTION  IF EXISTS fn_invoice_amount_paid;

DROP PROCEDURE IF EXISTS sp_get_invoice_edit_fields; -- vao nhom dong 30-36

DROP PROCEDURE IF EXISTS sp_settle_invoice;          -- vao nhom dong 43-47
DROP PROCEDURE IF EXISTS sp_void_invoice;
```

### 4.2. Mới: `fn_invoice_amount_due` + `fn_invoice_amount_paid`

Đặt vào **PHẦN 1**, sau `fn_next_return_id` (khoảng dòng 186), giữ đúng kiểu khai báo của 5 hàm
đang có (`DETERMINISTIC` + `READS SQL DATA`):

```sql
-- ---------------------------------------------------------------------
-- 1.6 fn_invoice_amount_due: Tong so tien hoa don PHAI thu
--     Dung dung cong thuc cua sp_get_invoice_header (Tong_phai_tra).
--     Hoa don khong co dong hang nao -> tra 0.00 (khong phai NULL), nen
--     NGUOI GOI phai tu kiem "co dong hang hay khong" truoc khi so sanh:
--     0 >= 0 la TRUE, va mot hoa don rong KHONG bao gio duoc coi la da thu
--     du (plan/13 muc 3.4 diem 2).
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_invoice_amount_due(
    p_invoice_id VARCHAR(30)
)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_due DECIMAL(12,2) DEFAULT NULL;

    SELECT SUM(l.Quantity * l.Unit_Price - l.Discount)
    INTO   v_due
    FROM   Invoice_Line l
    WHERE  l.Invoice_ID = p_invoice_id;

    RETURN COALESCE(v_due, 0.00);
END$$


-- ---------------------------------------------------------------------
-- 1.7 fn_invoice_amount_paid: Tong so tien DA GHI NHAN cho hoa don
--     Payment.Amount la so tien ghi nhan, KHONG phai tien khach dua
--     (tien khach dua nam o Cash_Payment.Tendered_Amount).
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_invoice_amount_paid(
    p_invoice_id VARCHAR(30)
)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_paid DECIMAL(12,2) DEFAULT NULL;

    SELECT SUM(p.Amount)
    INTO   v_paid
    FROM   Payment p
    WHERE  p.Invoice_ID = p_invoice_id;

    RETURN COALESCE(v_paid, 0.00);
END$$
```

### 4.3. Sửa `sp_create_invoice_header` — một chữ, đổi cả vòng đời

`04_routines.sql:565`:

```sql
    -- TRUOC:  VALUES (p_invoice_id, p_date, p_time, 'PAID', ...)
    -- SAU:
    INSERT INTO Invoice (Invoice_ID, `Date`, `Time`, Status, Counter_ID, Cashier_ID, Customer_ID)
    VALUES (p_invoice_id, p_date, p_time, 'OPEN', p_counter_id, p_cashier_id, p_customer_id);
```

Kèm comment giải thích, vì đây là chỗ dễ bị "sửa lại cho nhanh" nhất:

```sql
-- 5.1 sp_create_invoice_header: Tạo thông tin đầu hóa đơn
--     Hoa don MOI LUON la 'OPEN' -- chua thu du tien thi chua PAID.
--     Chi sp_settle_invoice moi duoc chuyen sang 'PAID', va no tu doc bang
--     Payment de quyet dinh (plan/13 muc 2.2). Dung ghi 'PAID' o day.
```

### 4.4. Mới: `sp_settle_invoice` — chỗ duy nhất đặt `PAID`

Đặt vào **PHẦN 5**, sau `sp_add_payment` (khoảng dòng 618):

```sql
-- ---------------------------------------------------------------------
-- 5.4 sp_settle_invoice: Chuyen hoa don sang 'PAID' NEU da thu du tien
--     Java goi thu tuc nay o cuoi transaction lap hoa don, TRUOC commit,
--     tren cung mot Connection (InvoiceDao.createInvoice) -- dung nhu
--     sp_add_invoice_line va sp_add_payment.
--
--     KHONG tra ResultSet: Java goi bang cs.executeUpdate(), va
--     Connector/J nem loi "Can not issue SELECT via executeUpdate()" neu
--     thu tuc tra ve bang. Muon biet trang thai sau khi chay thi doc lai
--     bang sp_get_invoice_edit_fields.
--
--     Khong thu du tien -> KHONG loi, chi la khong chuyen (hoa don o lai
--     'OPEN'). Tra thieu la mot trang thai hop le, khong phai mot loi.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_settle_invoice(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    DECLARE v_status   VARCHAR(10)   DEFAULT NULL;
    DECLARE v_has_line INT           DEFAULT 0;
    DECLARE v_msg      VARCHAR(255);

    -- Khoa dong hoa don trong suot transaction: hai cua so app khong the
    -- cung di qua phep kiem ben duoi (cung ly do nhu sp_add_return).
    SELECT Status
    INTO   v_status
    FROM   Invoice
    WHERE  Invoice_ID = p_invoice_id
    FOR UPDATE;

    IF v_status IS NULL THEN
        SET v_msg = CONCAT('Khong tim thay hoa don ', p_invoice_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    IF v_status = 'VOIDED' THEN
        SET v_msg = CONCAT('Hoa don ', p_invoice_id,
                           ' da bi huy (VOIDED), khong the chuyen sang PAID');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SELECT EXISTS (SELECT 1 FROM Invoice_Line WHERE Invoice_ID = p_invoice_id)
    INTO   v_has_line;

    -- Hoa don rong KHONG bao gio PAID, du co bao nhieu tien: fn_invoice_amount_due
    -- tra 0.00 cho hoa don rong nen phep so sanh mot minh se noi "da thu du".
    IF v_status = 'OPEN'
       AND v_has_line = 1
       AND fn_invoice_amount_paid(p_invoice_id) >= fn_invoice_amount_due(p_invoice_id) THEN

        UPDATE Invoice
        SET    Status = 'PAID'
        WHERE  Invoice_ID = p_invoice_id;
    END IF;
END$$
```

Thủ tục này **chạy lại bao nhiêu lần cũng được** (idempotent): đã `PAID` thì không vào `IF`, chưa
đủ tiền thì không làm gì. Nhờ vậy gọi nó thêm ở chỗ khác (ví dụ sau khi sửa dòng hàng) là an toàn.

### 4.5. Mới: `sp_void_invoice` — đường duy nhất tới `VOIDED`

```sql
-- ---------------------------------------------------------------------
-- 5.5 sp_void_invoice: Huy mot hoa don
--     HUY KHAC XOA: du lieu van con nguyen (dong hang, thanh toan, tra
--     hang), chi khong duoc tinh vao bao cao nua vi 4 cau truy van bao cao
--     deu loc WHERE Status = 'PAID'. Muon xoa sach thi dung sp_delete_invoice.
--
--     CAC DONG Payment DA GHI KHONG BI XOA: huy khong xoa dau vet tien.
--     Viec tra lai so tien do la nghiep vu hoan tien -- plan/13 muc 2.3.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_void_invoice(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    DECLARE v_status VARCHAR(10) DEFAULT NULL;
    DECLARE v_msg    VARCHAR(255);

    SELECT Status
    INTO   v_status
    FROM   Invoice
    WHERE  Invoice_ID = p_invoice_id
    FOR UPDATE;

    IF v_status IS NULL THEN
        SET v_msg = CONCAT('Khong tim thay hoa don ', p_invoice_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    IF v_status = 'VOIDED' THEN
        SET v_msg = CONCAT('Hoa don ', p_invoice_id, ' da bi huy truoc do');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    UPDATE Invoice
    SET    Status = 'VOIDED'
    WHERE  Invoice_ID = p_invoice_id;
END$$
```

Vì sao huỷ một hoá đơn đã huỷ là **lỗi** chứ không phải không-làm-gì? Người dùng vừa bấm một nút và
cần biết việc họ tưởng mình làm đã xảy ra từ trước. Khác với `sp_settle_invoice` — chỗ đó **không
ai bấm nút**, nó chạy tự động trong transaction nên im lặng là đúng.

### 4.6. Sửa `sp_update_invoice_header` — phép kiểm chuyển trạng thái

Giữ **nguyên 7 tham số và thứ tự** (`docs/05` Quy tắc 2). Thêm phần kiểm vào đầu thân thủ tục,
trước câu `UPDATE` (`:782-800`):

```sql
-- ---------------------------------------------------------------------
-- 5.3 sp_update_invoice_header: Chỉnh sửa thông tin chung của hóa đơn
--     p_status KHONG con la "trang thai muon dat" ma la MOT PHEP KIEM:
--     no phai BANG trang thai dang co trong database. Moi chuyen doi trang
--     thai chi di qua sp_settle_invoice (-> PAID) hoac sp_void_invoice
--     (-> VOIDED) -- plan/13 muc 3.2.
--     Giu nguyen 7 tham so de khong pha hop dong voi Java (docs/05 Quy tac 2).
-- ---------------------------------------------------------------------
BEGIN
    DECLARE v_old VARCHAR(10) DEFAULT NULL;
    DECLARE v_msg VARCHAR(255);

    SELECT Status INTO v_old
    FROM   Invoice
    WHERE  Invoice_ID = p_invoice_id;

    IF v_old IS NULL THEN
        SET v_msg = CONCAT('Khong tim thay hoa don ', p_invoice_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    IF p_status IS NULL OR p_status <> v_old THEN
        IF v_old = 'VOIDED' THEN
            SET v_msg = CONCAT('Hoa don ', p_invoice_id,
                   ' da bi huy, khong doi trang thai duoc nua');
        ELSEIF p_status = 'VOIDED' THEN
            SET v_msg = 'Khong huy hoa don bang hop thoai sua. Dung nut "Huy hoa don" o tab Xem hoa don';
        ELSEIF p_status = 'PAID' THEN
            SET v_msg = 'Khong tu dat PAID. Hoa don tu chuyen sang PAID khi da thu du tien';
        ELSE
            SET v_msg = CONCAT('Khong mo lai hoa don da thu tien (',
                   v_old, ' -> ', COALESCE(p_status, 'NULL'), ')');
        END IF;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    UPDATE Invoice
    SET    `Date`      = p_date,
           `Time`      = p_time,
           Status      = p_status,   -- = v_old, giu lai cho ro rang
           Counter_ID  = p_counter_id,
           Cashier_ID  = p_cashier_id,
           Customer_ID = p_customer_id
    WHERE  Invoice_ID  = p_invoice_id;
END$$
```

**Ghi chú trung thực:** `InvoiceDao.updateInvoiceHeader` chạy ở chế độ `autocommit`, nên phép kiểm
và câu `UPDATE` **không nguyên tử** — về lý thuyết có một khe hở nếu hai cửa sổ app cùng sửa một hoá
đơn trong vài micro-giây. Không bịt khe hở này bằng `FOR UPDATE` (vô ích dưới autocommit) cũng
không bằng `WHERE ... AND Status = p_status` (vì khi quản lý bấm Lưu mà không sửa gì thì
`ROW_COUNT()` trả 0 và ta sẽ báo một lỗi không có thật). Đánh giá: hậu quả xấu nhất của khe hở là
ghi đè ngày/giờ/quầy — không phải tiền, không phải trạng thái. Chấp nhận, và ghi lại ở đây để người
sau không tưởng là bỏ sót.

### 4.7. Mới: `sp_get_invoice_edit_fields` — xoá câu SQL thô

Đặt vào **PHẦN 3**, sau `sp_get_returnable_lines` (khoảng dòng 425):

```sql
-- ---------------------------------------------------------------------
-- 3.7 sp_get_invoice_edit_fields: Cac truong THO cua mot hoa don, dung cho
--     hop thoai sua (EditInvoiceDialog).
--     Khac sp_get_invoice_header: tra ve MA (Counter_ID, Cashier_ID,
--     Customer_ID) chu khong phai TEN, va KHONG join Invoice_Line nen hoa
--     don chua co dong hang nao van doc duoc.
--     Thu tu 7 cot la hop dong voi Java (InvoiceViewPanel doc theo chi so
--     r[1]..r[6]) -- plan/13 muc 3.5.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_get_invoice_edit_fields(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    SELECT  i.Invoice_ID   AS Ma_HD,
            i.`Date`       AS Ngay,
            i.`Time`       AS Gio,
            i.Status       AS Trang_thai,
            i.Counter_ID   AS Quay,
            i.Cashier_ID   AS Thu_ngan,
            i.Customer_ID  AS Khach
    FROM    Invoice i
    WHERE   i.Invoice_ID = p_invoice_id;
END$$
```

### 4.8. Sửa `sp_get_recent_invoices` + `sp_get_invoice_header` — `LEFT JOIN Invoice_Line`

Lỗi #9. Hai chỗ, mỗi chỗ hai dòng:

```sql
-- sp_get_recent_invoices (:276, :282)
            COALESCE(SUM(l.Quantity * l.Unit_Price - l.Discount), 0) AS Tong_tien
    ...
    LEFT JOIN Invoice_Line l ON l.Invoice_ID = i.Invoice_ID

-- sp_get_invoice_header (:305-307, :313)
            COALESCE(SUM(l.Quantity * l.Unit_Price), 0)                AS Tong_truoc_giam,
            COALESCE(SUM(l.Discount), 0)                               AS Tong_giam,
            COALESCE(SUM(l.Quantity * l.Unit_Price - l.Discount), 0)   AS Tong_phai_tra
    ...
    LEFT JOIN Invoice_Line l ON l.Invoice_ID = i.Invoice_ID
```

Đánh đổi có chủ ý: hoá đơn không có dòng hàng giờ hiện ra với tổng tiền `0` thay vì **biến mất**.
Một hoá đơn `0 d` nhìn thấy được thì quản lý sửa hay xoá được; một hoá đơn vô hình thì không.

**Không sửa** `sp_report_daily_takings` và `sp_report_best_cashier`: chúng *nên* dùng inner join —
hoá đơn không có dòng hàng không có doanh thu để cộng, và `WHERE Status = 'PAID'` giờ đã tự loại
hoá đơn `OPEN` (mục 2.1 #6).

### 4.9. Sửa comment `sp_add_return` + thêm `OPEN` vào danh sách chặn

Hai việc trong một thủ tục (`:620-695`). Thay đoạn ghi chú `:626-629` bằng câu trả lời, và thêm
nhánh `OPEN` cạnh nhánh `VOIDED` (`:668-672`):

```sql
--     GHI CHU CUA KHOI 4 (trang thai hoa don): khoi 4 da xet va QUYET DINH
--     KHONG them RETURNED / PARTIALLY_RETURNED vao ENUM (plan/13 muc 3.3):
--     Status chi mang mot truc nghia "tien da vao hay chua". Hang da tra bao
--     nhieu thi doc tu bang `Return` (sp_get_returnable_lines.SL_con_lai).
--     Dung them trang thai moi vao day.

    IF v_status <> 'PAID' THEN
        SET v_msg = CONCAT('Hoa don ', p_invoice_id, ' dang o trang thai ', v_status,
                           ', chi hoa don da thanh toan (PAID) moi tra hang duoc');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;
```

Một `IF` thay cho hai: `<> 'PAID'` bao cả `OPEN` và `VOIDED`, và thông điệp nói đúng trạng thái
đang gặp. Mất đi câu *"da bi huy (VOIDED), khong tra hang duoc"* — đổi lại một quy tắc không có kẽ
hở nếu sau này `ENUM` có thêm giá trị.

---

## 5. Thiết Kế Java

### 5.1. Bảng tổng hợp

| File | Trạng thái | Việc |
|---|---|---|
| `sql/04_routines.sql` | sửa | 5 `DROP` mới, 2 hàm mới, 3 thủ tục mới, sửa 5 thủ tục cũ (mục 4.1–4.9) |
| `sql/01_schema.sql` | **không đổi** | Kết luận của mục 3.3, không phải bỏ sót |
| `sql/02_seed.sql` | **không đổi** (Phần C: tuỳ chọn thêm 1 hoá đơn `OPEN`) | Bất biến `paid >= due` đã thoả — mục 3.4 |
| `src/billing/model/InvoiceStatus.java` | **mới** | Bản chép duy nhất của bộ giá trị + nhãn tiếng Việt (mục 5.2) |
| `src/billing/dao/InvoiceDao.java` | sửa | `createInvoice` gọi thêm `sp_settle_invoice`; thêm `voidInvoice()`, `invoiceEditFields()`, `amountDue()`, `amountPaid()`; javadoc `updateInvoiceHeader` (mục 5.3) |
| `src/billing/ui/InvoiceViewPanel.java` | sửa | Nút `btVoidInvoice` + `applyRolePermissions` (8 nút); `onVoidInvoice()`; thay SQL thô `:447`; chặn trả hàng khi `<> PAID`; `showReturnFailure` → `showSqlFailure` (mục 5.4) |
| `src/billing/ui/EditInvoiceDialog.java` | sửa | Ô trạng thái thành **chỉ đọc**, nạp từ `InvoiceStatus`; lỗi `45000` hiện gọn; cảnh báo lệch tiền (mục 5.5) |
| `src/billing/ui/SearchPanel.java` | sửa | Thêm chip `Đang lập`, dùng `InvoiceStatus` thay literal (mục 5.6) |
| `src/billing/ui/NewInvoicePanel.java` | sửa | Đổi thứ tự 2 phép kiểm + nói đúng hệ quả `OPEN` (mục 5.7) |
| `docs/05-SQL-JAVA-CONVENTION.md` | sửa | Thêm 5 dòng vào bảng mục 6 |
| `src/billing/model/UserSession.java`, `Account.java` | **không đổi** | Khối này không chạm tới tài khoản |

### 5.2. Mới: `src/billing/model/InvoiceStatus.java`

```java
package billing.model;

/**
 * Trang thai hoa don -- BAN CHEP DUY NHAT cua bo gia tri trong Java.
 *
 * Truoc khoi 4, bo ba gia tri nay bi chep 3 lan: ENUM trong 01_schema.sql,
 * mang String[] trong EditInvoiceDialog, va literal 'PAID'/'VOIDED' trong
 * cau WHERE cua SearchPanel. Them mot trang thai la phai sua ba cho, ma
 * khong cho nao bao neu quen (plan/13 loi #6).
 *
 * Vong doi: OPEN -> PAID (sp_settle_invoice, khi da thu du tien)
 *           OPEN/PAID -> VOIDED (sp_void_invoice)
 *           VOIDED khong co duong ra (plan/13 muc 3.2).
 */
public enum InvoiceStatus {
    OPEN  ("Dang lap"),
    PAID  ("Da thanh toan"),
    VOIDED("Da huy");

    private final String label;

    InvoiceStatus(String label) { this.label = label; }

    /** Ma luu trong database, dung bang ten hang so. */
    public String code()  { return name(); }

    /** Nhan tieng Viet khong dau de hien tren giao dien. */
    public String label() { return label; }

    /** Nhan kem ma, vi du "Da thanh toan (PAID)". */
    public String display() { return label + " (" + name() + ")"; }

    /**
     * Doc tu chuoi lay tu database. Tra null khi khong khop -- KHONG nem
     * IllegalArgumentException nhu Enum.valueOf: mot gia tri la trong cot
     * Status khong duoc phep lam sap man hinh xem hoa don.
     */
    public static InvoiceStatus of(String raw) {
        if (raw == null) return null;
        for (InvoiceStatus s : values()) {
            if (s.name().equalsIgnoreCase(raw.trim())) return s;
        }
        return null;
    }
}
```

### 5.3. `InvoiceDao`

**a) `createInvoice` — thêm bước 4, trong cùng transaction** (sau vòng lặp thanh toán `:149`,
trước `conn.commit()` `:151`):

```java
            // ---- 4. Chuyen trang thai neu da thu du tien --------------
            // Java KHONG tu quyet dinh PAID (plan/13 muc 2.2): thu tuc tu doc
            // bang Payment va so voi tong tien cac dong hang. Tra thieu thi hoa
            // don o lai OPEN, va day KHONG phai loi.
            // executeUpdate duoc vi sp_settle_invoice khong tra ResultSet.
            String spSettle = "{call sp_settle_invoice(?)}";
            try (CallableStatement cs = conn.prepareCall(spSettle)) {
                cs.setString(1, invoiceId);
                cs.executeUpdate();
                SqlLog.add(spSettle, new Object[]{invoiceId}, 0, 1);
            }
```

Nằm **trong** transaction là bắt buộc: thủ tục đọc các dòng `Payment` vừa `INSERT` mà chưa `commit`
— chỉ cùng một `Connection` mới thấy chúng.

**b) Ba hàm mới**, viết theo đúng khuôn của `deleteInvoice` / `returnRefund` đang có:

```java
    /**
     * Huy mot hoa don: sp_void_invoice(p_invoice_id).
     *
     * Huy KHAC xoa: du lieu van con, chi khong vao bao cao nua. Muon xoa sach
     * thi dung deleteInvoice().
     *
     * @throws RuntimeException khi SQL tu choi. Exception goc duoc giu lam
     *         cause de UI doc getSQLState() -- "45000" la loi nghiep vu.
     */
    public void voidInvoice(String invoiceId) { /* {call sp_void_invoice(?)} */ }

    /** Cac truong tho cua hoa don cho hop thoai sua: sp_get_invoice_edit_fields(?) */
    public QueryResult invoiceEditFields(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_edit_fields(?)}", invoiceId);
    }

    /** Tong phai tra / tong da thu: fn_invoice_amount_due, fn_invoice_amount_paid */
    public BigDecimal amountDue(String invoiceId)  { /* {? = call fn_invoice_amount_due(?)} */ }
    public BigDecimal amountPaid(String invoiceId) { /* {? = call fn_invoice_amount_paid(?)} */ }
```

`voidInvoice` phải **giữ nguyên exception gốc làm `cause`** (như `processReturn:465`) — nếu bọc
bằng một `RuntimeException` mới mà bỏ cause thì UI mất đường phân biệt lỗi nghiệp vụ với lỗi thật.

**c) `updateInvoiceHeader`** — không đổi chữ ký, chỉ thêm javadoc:

```java
    /**
     * ...
     * @param status trang thai HIEN TAI cua hoa don, KHONG phai trang thai
     *        muon dat. sp_update_invoice_header dung tham so nay lam phep kiem:
     *        khac trang thai trong DB la bi tu choi bang SIGNAL '45000'.
     *        Moi chuyen doi trang thai di qua voidInvoice() hoac
     *        sp_settle_invoice (plan/13 muc 3.2).
     */
```

### 5.4. `InvoiceViewPanel`

**a) Nút mới** `btVoidInvoice` = `new JButton("Huy hoa don")`, đặt **cạnh** `btDeleteInvoice` dưới
bảng hoá đơn, và thêm vào `applyRolePermissions()` (`:315-345`) — thành **8** nút:

```java
        btVoidInvoice.setEnabled(isManager);
        ...
        // nhanh !isManager:
        btVoidInvoice.setToolTipText(lockHint);
        // nhanh isManager:
        btVoidInvoice.setToolTipText("Huy hoa don: du lieu van con, chi khong tinh vao bao cao");
```

**b) `onVoidInvoice()`** — theo đúng khuôn `onDeleteInvoice` (`:477-500`) nhưng hộp thoại xác nhận
phải dạy được sự khác nhau giữa *huỷ* và *xoá*:

```java
        "Huy hoa don " + invoiceId + "?\n\n"
      + "Huy KHAC Xoa:\n"
      + "  - Du lieu van con nguyen: dong hang, thanh toan, tra hang\n"
      + "  - Hoa don khong con duoc tinh vao bao cao doanh thu\n"
      + "  - Khong tra hang duoc nua\n"
      + "  - KHONG mo lai duoc: VOIDED la trang thai cuoi\n\n"
      + "Muon xoa sach khoi database thi dung nut \"Xoa hoa don\"."
```

Chạy **ngoài EDT** (`SwingWorker`, đúng cách khối 3 đã làm cho `processReturn` và khối 2 cho đăng
nhập): `voidInvoice` là một `UPDATE` có `FOR UPDATE`, MySQL chậm là cả cửa sổ đứng hình. Sau khi
xong: `reload()` → `selectInvoiceRow(invoiceId)` → `showDetail(invoiceId)` — đúng bộ ba mà khối 3
đã dựng để tránh lỗi "lưu xong mà bảng giữ nội dung cũ" (`plan/12` lỗi #12).

**c) Thay câu SQL thô** `:447-448`:

```java
            // TRUOC: QueryResult.run("SELECT Invoice_ID, `Date`, ... FROM Invoice WHERE ...")
            QueryResult qr = dao.invoiceEditFields(invoiceId);
```

7 cột giữ đúng thứ tự cũ nên `:454-460` (`r[1]`..`r[6]`) không phải sửa.

**d) Chặn trả hàng khi trạng thái `<> PAID`** (`:852`):

```java
            // Khoi 4 so huu cot Status va da dinh nghia: OPEN = chua thu du tien
            // -> hoan tien cho mot hoa don chua thu tien la vo nghia. SQL cung
            // chan (sp_add_return), day chi la lop thong bao som.
            InvoiceStatus st = InvoiceStatus.of(status);
            if (st != InvoiceStatus.PAID) {
                JOptionPane.showMessageDialog(this,
                        "Hoa don " + invoiceId + " dang o trang thai "
                      + (st == null ? status : st.display())
                      + ".\nChi hoa don da thanh toan (PAID) moi tra hang duoc.",
                        "Khong tra hang duoc", JOptionPane.WARNING_MESSAGE);
                return;
            }
```

**e) `showReturnFailure` → `showSqlFailure(Component parent, Throwable ex, String title)`**
(`:1164-1180`). Thân hàm không đổi (vòng lặp `getCause()` tìm `SQLSTATE 45000`), chỉ thêm tham số
tiêu đề. Một chỗ gọi hiện có (trả hàng, `"Khong tra hang duoc"`), hai chỗ gọi mới (huỷ hoá đơn,
sửa header). Giữ javadoc cũ và bổ sung: *"ba nut dung chung mot cach hien loi"*.

### 5.5. `EditInvoiceDialog`

**a) Ô trạng thái thành chỉ đọc** (`:27`, `:63`):

```java
    // Bo gia tri nap tu InvoiceStatus -- khong con mang String[] ghi cung.
    private final JComboBox<InvoiceStatus> cbStatus =
            new JComboBox<>(InvoiceStatus.values());
```

```java
        // Trang thai KHONG sua bang tay: khong phai vi phan quyen, ma vi
        // khong ai -- ke ca quan ly -- go ra duoc mot trang thai dung
        // (plan/13 muc 2.2). PAID do sp_settle_invoice quyet dinh, VOIDED do
        // nut "Huy hoa don".
        cbStatus.setEnabled(false);
        cbStatus.setToolTipText("Trang thai doi tu dong khi thu du tien, "
                + "hoac qua nut \"Huy hoa don\" o tab Xem hoa don");
        addFormRow(form, gbc, row++, "Trang thai:", cbStatus, "(chi doc)");
```

Nhãn đổi từ `"Trang thai *:"` + `"(NOT NULL)"` thành `"Trang thai:"` + `"(chi doc)"`: dấu `*` và
chú thích đỏ ở `:87` đang nói về các trường **bị chặn khi để trống**, không còn đúng cho ô này.

`doSave():168` giữ nguyên tinh thần, chỉ đổi kiểu:

```java
        String status = ((InvoiceStatus) cbStatus.getSelectedItem()).code();
```

**b) Lỗi `45000` hiện gọn.** `doSave():181-183` đang gọi `Ui.error(this, ex)` cho mọi lỗi — nghĩa
là thông điệp *"Khong huy hoa don bang hop thoai sua. Dung nut Huy hoa don"* (mục 4.6) sẽ hiện ra
kèm stack trace. Dùng `showSqlFailure(this, ex, "Khong cap nhat duoc hoa don")` thay cho
`Ui.error` — hàm đó tự rơi về `Ui.error` khi không phải lỗi `45000`.

**c) Cảnh báo lệch tiền (lỗi #4).** Hộp thoại này không sửa dòng hàng, nhưng nó là chỗ duy nhất
hiện trạng thái. Thêm một dòng nhãn dưới ô trạng thái, đọc từ `dao.amountDue` / `dao.amountPaid`:

```
Trang thai: Da thanh toan (PAID)   (chi doc)
Da thu 100.000 d / 1.000.000 d  --  LECH 900.000 d
```

Dòng này chỉ hiện khi `paid < due` trên hoá đơn `PAID` (đúng dấu vết của lỗi #4, sau khi ai đó sửa
dòng hàng), màu đỏ, kèm một câu: *"Sua dong hang xong lam lech so tien. Trang thai KHONG tu doi
nguoc -- xem plan/13 muc 2.3."* Đọc hai số này ngoài EDT khi dialog mở, đừng chặn lúc dựng form.

### 5.6. `SearchPanel`

Thêm chip thứ tư. File này dùng tiếng Việt **có dấu** cho nhãn (`:357-359`) — giữ đúng:

```java
        private final JToggleButton rbOpen;     // them vao :339-341
        ...
        rbOpen = createToggleButton("Đang lập", false);
        statusGroup.add(rbOpen);
        statusRow.add(rbOpen);                  // dat giua rbAllStatus va rbPaid
        ...
        rbOpen.addItemListener(e -> performSearch());   // :392-394
        rbOpen.setSelected(false);                      // resetToDefault, :404-412
```

`getWhereFragment():424-431` — bỏ literal, dùng `InvoiceStatus`:

```java
            // Status -- ma trang thai lay tu InvoiceStatus, khong go literal
            if (!rbAllStatus.isSelected()) {
                InvoiceStatus want = rbOpen.isSelected()   ? InvoiceStatus.OPEN
                                   : rbPaid.isSelected()   ? InvoiceStatus.PAID
                                   : rbVoided.isSelected() ? InvoiceStatus.VOIDED
                                   : null;
                if (want != null) {
                    where.append("Invoice.Status = ? AND ");
                    params.add(want.code());
                }
            }
```

Đổi luôn sang tham số `?` + `params.add(...)` thay vì nối chuỗi: hàm này **đã** có danh sách
`params` cho các bộ lọc khác (`:422`), nên đây là dùng đúng cơ chế đang có, không phải thêm cơ chế.

Nhãn chip `Đã hoàn tất` giữ nguyên để không làm người dùng cũ bối rối, dù `PAID` giờ có nghĩa hẹp
hơn (đã thu đủ tiền).

### 5.7. `NewInvoicePanel` — nói thật về hệ quả

`save():900-918` hiện kiểm **lệch tiền trước**, **có thanh toán nào chưa** sau. Đổi thứ tự và đổi
thông điệp:

```java
            if (cart.isEmpty())    { ... return; }          // giu nguyen :902-905

            if (payments.isEmpty()) {                        // CHUYEN LEN TRUOC
                JOptionPane.showMessageDialog(this, "Chua co dong thanh toan nao.");
                return;
            }

            BigDecimal due  = totalDue();
            BigDecimal paid = totalPaid();
            if (paid.compareTo(due) < 0) {
                int ok = JOptionPane.showConfirmDialog(this,
                        "Tong hoa don " + Ui.money(due) + " nhung chi thanh toan "
                      + Ui.money(paid) + " (thieu " + Ui.money(due.subtract(paid)) + ").\n\n"
                      + "Van luu, nhung hoa don se o trang thai DANG LAP (OPEN):\n"
                      + "  - Khong duoc tinh vao bao cao doanh thu\n"
                      + "  - Khong tra hang duoc\n"
                      + "  - Tu chuyen sang DA THANH TOAN khi thu du tien\n\n"
                      + "Luu chu?",
                        "Thieu tien -- hoa don se la OPEN", JOptionPane.YES_NO_OPTION);
                if (ok != JOptionPane.YES_OPTION) return;
            }
```

Ba thay đổi, mỗi cái một lý do:

- **Thứ tự:** hiện tại giỏ có hàng mà chưa thanh toán gì thì app hỏi *"lệch tiền, vẫn lưu chứ?"*,
  người dùng bấm Yes, rồi mới bị từ chối vì *"Chua co dong thanh toan nao"*. Hỏi một câu vô ích.
- **`!= 0` → `< 0`:** ghi nhận thừa không còn là chuyện phải hỏi, vì mục 3.4 đã quyết `paid >= due`
  là đủ. Trả thừa mà bị hỏi *"vẫn lưu chứ?"* là báo động giả.
- **Nội dung:** đây là chỗ duy nhất người dùng gặp trạng thái `OPEN` lần đầu. Nếu không nói rõ hậu
  quả, họ sẽ tưởng app vừa đánh mất một hoá đơn.

### 5.8. `docs/05-SQL-JAVA-CONVENTION.md` mục 6

Thêm 5 dòng vào bảng (`:176-201`), đúng vị trí nhóm:

| Tên Routine | Loại | Tham số | Ý nghĩa nghiệp vụ | Nơi gọi trong Java |
|---|---|---|---|---|
| `fn_invoice_amount_due` | FUNCTION | `p_invoice_id` | Tổng tiền hoá đơn phải thu. `0.00` nếu chưa có dòng hàng → **người gọi phải tự kiểm có dòng hàng hay không** | `InvoiceDao.amountDue()` |
| `fn_invoice_amount_paid` | FUNCTION | `p_invoice_id` | Tổng tiền đã ghi nhận (`Payment.Amount`, không phải tiền khách đưa) | `InvoiceDao.amountPaid()` |
| `sp_get_invoice_edit_fields` | PROCEDURE | `p_invoice_id` | 7 trường thô của hoá đơn cho hộp thoại sửa (trả **mã**, không join `Invoice_Line`) | `InvoiceDao.invoiceEditFields()` |
| `sp_settle_invoice` | PROCEDURE | `p_invoice_id` | **Chỗ duy nhất đặt `PAID`**: chuyển khi đã thu đủ. Không trả ResultSet, idempotent | `InvoiceDao.createInvoice()` |
| `sp_void_invoice` | PROCEDURE | `p_invoice_id` | **Đường duy nhất tới `VOIDED`**. Huỷ ≠ xoá: dữ liệu còn nguyên | `InvoiceDao.voidInvoice()` |

Và sửa 2 dòng đang có: `sp_create_invoice_header` (*"Ghi đầu hóa đơn mới, luôn là `OPEN`"*) và
`sp_update_invoice_header` (*"`p_status` là phép kiểm, không đổi trạng thái được"*).

---

## 6. Danh Sách Công Việc (theo thứ tự thực hiện)

### Phần A — SQL (làm trước, một mình cũng kiểm thử được)

1. Thêm 5 dòng `DROP` vào đúng nhóm ở đầu `04_routines.sql` (mục 4.1).
2. Thêm `fn_invoice_amount_due`, `fn_invoice_amount_paid` vào PHẦN 1 (mục 4.2).
3. Sửa `sp_create_invoice_header`: `'PAID'` → `'OPEN'` + comment (mục 4.3).
4. Thêm `sp_settle_invoice` vào PHẦN 5 (mục 4.4).
5. Thêm `sp_void_invoice` vào PHẦN 5 (mục 4.5).
6. Sửa `sp_update_invoice_header`: phép kiểm chuyển trạng thái (mục 4.6).
7. Thêm `sp_get_invoice_edit_fields` vào PHẦN 3 (mục 4.7).
8. Sửa `sp_get_recent_invoices` + `sp_get_invoice_header`: `LEFT JOIN` + `COALESCE` (mục 4.8).
9. Sửa `sp_add_return`: comment trả lời khối 3 + `IF v_status <> 'PAID'` (mục 4.9).
10. Nạp lại: `.\scripts\ps1\db-setup.ps1` (xoá sạch dữ liệu) **hoặc** nạp riêng phần thủ tục nếu
    muốn giữ dữ liệu — khối này **không** đổi lược đồ nên cách 2 là đủ:
    ```powershell
    $env:MYSQL_PWD = 'root'
    Get-Content .\sql\04_routines.sql -Raw -Encoding UTF8 | mysql -u root -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4
    $env:MYSQL_PWD = $null
    ```
11. Chạy hết mục 7.1 bằng `mysql` CLI. **Xong Phần A mới sang Phần B.**

### Phần B — Java

12. Tạo `src/billing/model/InvoiceStatus.java` (mục 5.2).
13. `InvoiceDao`: bước 4 trong `createInvoice`, `voidInvoice`, `invoiceEditFields`, `amountDue`,
    `amountPaid`, javadoc `updateInvoiceHeader` (mục 5.3).
14. `InvoiceViewPanel`: `showReturnFailure` → `showSqlFailure` **trước** (hai mục sau dùng tới nó),
    rồi nút `btVoidInvoice` + `applyRolePermissions` + `onVoidInvoice`, thay SQL thô `:447`, chặn
    trả hàng `<> PAID` (mục 5.4).
15. `EditInvoiceDialog`: ô trạng thái chỉ đọc, `InvoiceStatus`, `showSqlFailure`, dòng cảnh báo
    lệch tiền (mục 5.5).
16. `SearchPanel`: chip `Đang lập` + tham số `?` (mục 5.6).
17. `NewInvoicePanel`: đổi thứ tự 2 phép kiểm + thông điệp `OPEN` (mục 5.7).
18. `docs/05` mục 6: thêm 5 dòng, sửa 2 dòng (mục 5.8).
19. Biên dịch + chạy: `powershell .\scripts\ps1\run.ps1`, rồi chạy hết mục 7.2–7.7.

### Phần C — tuỳ chọn: seed có hoá đơn `OPEN` để xem

20. Thêm vào `02_seed.sql` một hoá đơn `OPEN` có dòng hàng và **thanh toán thiếu** (ví dụ
    `INV-0016`, tổng 200.000, `Payment` 50.000) → tab 3 có cái để xem, chip `Đang lập` có kết quả,
    và báo cáo doanh thu chứng minh được là nó bị loại. Nhớ: thêm hoá đơn mới làm lệch
    `fn_next_invoice_id`, nên đặt mã **liền ngay sau** `INV-0015`.

---

## 7. Kịch Bản Kiểm Thử

Đánh số liên tục để nhắc nhau khi báo lỗi ("kịch bản #12 sai" ngắn hơn một đoạn văn).

### 7.1. Phần A — chỉ bằng `mysql` CLI, chưa cần Java

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 1 | Nạp `04_routines.sql` **hai lần liên tiếp** | Lần hai cũng in `Cac ham va thu tuc SQL ... da tao thanh cong!`, không có `already exists` (đủ 5 dòng `DROP`) |
| 2 | `SELECT fn_invoice_amount_due('INV-0005'), fn_invoice_amount_paid('INV-0005');` | `377800.00` và `377800.00` (`seed:213-214`) |
| 3 | `SELECT i.Invoice_ID, fn_invoice_amount_due(i.Invoice_ID) due, fn_invoice_amount_paid(i.Invoice_ID) paid FROM Invoice i WHERE i.Status='PAID' AND fn_invoice_amount_paid(i.Invoice_ID) < fn_invoice_amount_due(i.Invoice_ID);` | **0 dòng** — bất biến của mục 3.4 trên toàn bộ seed |
| 4 | `SELECT fn_invoice_amount_due('KHONG-CO'), fn_invoice_amount_paid('KHONG-CO');` | `0.00`, `0.00` — không `NULL`, không lỗi |
| 5 | `CALL sp_settle_invoice('INV-0015');` (hoá đơn `VOIDED`) | `SQLSTATE 45000`: *"...da bi huy (VOIDED), khong the chuyen sang PAID"* |
| 6 | `CALL sp_settle_invoice('KHONG-CO');` | `45000`: *"Khong tim thay hoa don KHONG-CO"* |
| 7 | `CALL sp_settle_invoice('INV-0001');` (đang `PAID`) | Chạy êm, **0 dòng bị sửa**, `Status` vẫn `PAID` (idempotent) |
| 8 | Tạo tay một hoá đơn `OPEN` không dòng hàng, không thanh toán, rồi `sp_settle_invoice` | Chạy êm, **vẫn `OPEN`** — cái bẫy `0 >= 0` ở mục 3.4 điểm 2 |
| 9 | Thêm 1 dòng `Payment` cho hoá đơn ở #8 nhưng **không** thêm dòng hàng, rồi `sp_settle_invoice` | **Vẫn `OPEN`**: không có dòng hàng thì không bao giờ `PAID` |
| 10 | Thêm dòng hàng 100.000 + `Payment` 99.999 → `sp_settle_invoice` | Vẫn `OPEN` (thiếu 1 đồng), **không báo lỗi** |
| 11 | Thêm `Payment` 1 đồng nữa (tổng 100.000) → `sp_settle_invoice` | Thành `PAID` |
| 12 | Thêm `Payment` nữa (tổng > due) → `sp_settle_invoice` trên hoá đơn khác đang `OPEN` thu thừa | Thành `PAID` — `>=` chứ không `=` (mục 3.4 điểm 1) |
| 13 | `CALL sp_void_invoice('INV-0002');` | Thành `VOIDED`. `SELECT COUNT(*) FROM Payment WHERE Invoice_ID='INV-0002'` vẫn **1** — huỷ không xoá dấu vết tiền |
| 14 | `CALL sp_void_invoice('INV-0002');` lần hai | `45000`: *"...da bi huy truoc do"* |
| 15 | `CALL sp_void_invoice('KHONG-CO');` | `45000`: *"Khong tim thay hoa don..."* |
| 16 | `CALL sp_update_invoice_header('INV-0001', ..., 'VOIDED', ...)` | `45000`: *"Khong huy hoa don bang hop thoai sua. Dung nut..."* |
| 17 | `CALL sp_update_invoice_header('INV-0001', ..., 'OPEN', ...)` (đang `PAID`) | `45000`: *"Khong mo lai hoa don da thu tien (PAID -> OPEN)"* |
| 18 | `CALL sp_update_invoice_header('INV-0002', ..., 'PAID', ...)` (đang `VOIDED` sau #13) | `45000`: *"...da bi huy, khong doi trang thai duoc nua"* |
| 19 | `CALL sp_update_invoice_header('INV-0001', '2026-01-01', '08:00:00', 'PAID', 'C01','E02', NULL)` — trạng thái **bằng** cái đang có | Thành công, ngày/giờ đổi, `Status` vẫn `PAID` |
| 20 | `CALL sp_update_invoice_header('INV-0001', ..., NULL, ...)` | `45000` (nhánh `p_status IS NULL`), không phải lỗi `ENUM` của MySQL |
| 21 | `CALL sp_add_return('INV-0002', 1, CURDATE(), 1, 'E01');` (`INV-0002` đang `VOIDED`) | `45000`: *"...dang o trang thai VOIDED, chi hoa don da thanh toan (PAID) moi tra hang duoc"* |
| 22 | `CALL sp_add_return` trên hoá đơn `OPEN` ở #11 → rồi đổi nó về `OPEN` bằng tay (`UPDATE` trực tiếp) và thử lại | `45000` nêu đúng chữ `OPEN` — nhánh mới của mục 4.9 |
| 23 | `CALL sp_get_invoice_edit_fields('INV-0001');` | 7 cột đúng tên và đúng thứ tự: `Ma_HD, Ngay, Gio, Trang_thai, Quay, Thu_ngan, Khach`; `Quay`/`Thu_ngan` là **mã** (`C01`, `E02`) |
| 24 | `CALL sp_get_invoice_edit_fields('INV-0015');` | Trả về được dù `INV-0015` có 1 dòng hàng — và vẫn trả về nếu xoá hết dòng hàng của nó (không join `Invoice_Line`) |
| 25 | Xoá hết dòng hàng của một hoá đơn, rồi `CALL sp_get_recent_invoices();` | Hoá đơn đó **vẫn có mặt**, `Tong_tien = 0` (lỗi #9 đã sửa) |
| 26 | `CALL sp_get_invoice_header('<hoá đơn rỗng đó>');` | Trả 1 dòng, ba cột tổng đều `0` thay vì không có dòng nào |
| 27 | `CALL sp_report_daily_takings();` sau khi #13 huỷ `INV-0002` | `INV-0002` **không** còn được cộng (nó của hôm nay, `seed:120`) — số hoá đơn hôm nay giảm 1 |
| 28 | `CALL sp_report_big_returns();` | Kết quả **không đổi** so với trước khối 4 — thủ tục này không lọc theo `Status` |

### 7.2. Đường cơ bản trên giao diện

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 29 | Lập một hoá đơn **thu đủ tiền** ở tab 1 | Lưu xong, tab 3 hiện trạng thái `PAID`. Tab 5 của Setup App (khối 1) có dòng `{call sp_settle_invoice(?)}` **trước** dòng `COMMIT` |
| 30 | Lập hoá đơn, thêm thanh toán **thiếu**, bấm Lưu | Hộp thoại *"Thieu tien -- hoa don se la OPEN"* nói rõ 3 hệ quả. Bấm Yes → lưu → tab 3 hiện `OPEN` |
| 31 | Hoá đơn `OPEN` đó trong `sp_report_daily_takings` (tab Báo cáo) | **Không xuất hiện** |
| 32 | Lập hoá đơn, giỏ có hàng, **chưa thêm thanh toán**, bấm Lưu | Thông báo *"Chua co dong thanh toan nao."* **ngay**, không hỏi câu lệch tiền trước (mục 5.7) |
| 33 | Lập hoá đơn trả **thừa** (ghi nhận > tổng) | **Không** còn hỏi *"Van luu chu?"*, lưu luôn, trạng thái `PAID` |
| 34 | Mở lại app, vào tab 3, chọn hoá đơn `OPEN`, bấm *Tra hang* | Cảnh báo *"...dang o trang thai Dang lap (OPEN). Chi hoa don da thanh toan (PAID) moi tra hang duoc."*, hộp thoại trả hàng **không** mở |
| 35 | Chọn hoá đơn `PAID`, bấm *Tra hang* | Hoạt động đúng như khối 3 — không hồi quy |

### 7.3. Huỷ hoá đơn

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 36 | Chọn hoá đơn `PAID`, bấm *Huy hoa don*, đọc hộp thoại | Nêu đủ 4 điểm khác biệt huỷ/xoá và câu *"Muon xoa sach... dung nut Xoa hoa don"* |
| 37 | Bấm Yes | Bảng hoá đơn tự reload, **dòng đó vẫn được chọn**, khung chi tiết hiện `VOIDED`, các bảng dòng hàng/thanh toán/trả hàng **vẫn còn dữ liệu** |
| 38 | Bấm *Huy hoa don* lần nữa trên cùng hoá đơn | Cảnh báo gọn *"...da bi huy truoc do"*, **không** stack trace (`showSqlFailure` chạy đúng) |
| 39 | Bấm *Tra hang* trên hoá đơn vừa huỷ | Bị chặn ở UI, với chữ `VOIDED` trong thông báo |
| 40 | Mở *Chinh sua Hoa don* trên hoá đơn vừa huỷ, sửa giờ lập, bấm Lưu | **Thành công** — xem lưu ý ngay dưới bảng |
| 41 | Tab Báo cáo sau khi huỷ | Doanh thu giảm đúng số tiền của hoá đơn đó; *"thu ngân xuất sắc"* cũng giảm theo |
| 42 | Bấm *Huy hoa don* trên một hoá đơn **`OPEN`** (hoá đơn trả thiếu ở #30) | Thành `VOIDED` — cạnh `OPEN → VOIDED` của sơ đồ mục 3.2, đường duy nhất ra khỏi `OPEN` ngoài việc thu đủ tiền |

> **Lưu ý kịch bản #40:** đây là chỗ dễ đọc nhầm nhất của mục 4.6. `p_status <> v_old` mới bị chặn;
> sửa ngày/giờ trên một hoá đơn `VOIDED` là **được phép** vì ô trạng thái chỉ đọc nên nó truyền
> xuống đúng `VOIDED`. Nếu lúc chạy thật mà #40 báo lỗi thì ô trạng thái chưa thực sự chỉ đọc, hoặc
> `EditInvoiceDialog` đang gửi trạng thái khác cái nó đọc lên.

### 7.4. Phân quyền & ô trạng thái chỉ đọc

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 43 | Đăng nhập `cashier`, vào tab 3 | **8** nút ghi DB đều mờ, kể cả *Huy hoa don*, tooltip nêu lý do |
| 44 | Bấm *Doi vai tro* → `manager` (khối 2) | *Huy hoa don* **sáng lên ngay**, không phải mở lại cửa sổ (`applyRolePermissions` được gọi lại) |
| 45 | Mở *Chinh sua Hoa don* | Ô *Trang thai* **mờ (không chọn được)**, nhãn `(chi doc)`, tooltip chỉ đúng hai đường đổi trạng thái |
| 46 | Sửa giờ lập một hoá đơn `PAID`, bấm Lưu | Thành công, trạng thái không đổi |
| 47 | Sửa dòng hàng của hoá đơn `PAID` cho tổng tiền tăng gấp 10, rồi mở lại *Chinh sua Hoa don* | Dòng đỏ *"Da thu 100.000 d / 1.000.000 d -- LECH 900.000 d"* hiện ra (mục 5.5c) |
| 48 | Chip *Đang lập* ở tab Tìm kiếm | Ra đúng các hoá đơn `OPEN`; chip *Đã hoàn tất* **không** hiện chúng; chip *Tất cả* hiện đủ |

### 7.5. Kiểm thử âm (bắt buộc — tất cả phải "app vẫn chạy")

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 49 | `UPDATE Invoice SET Status='PAID' WHERE Invoice_ID='INV-0015';` rồi thử trả hàng qua UI | Cho trả (trạng thái đã là `PAID` thật) — chứng tỏ UI đọc DB chứ không nhớ trạng thái cũ |
| 50 | Chèn tay một giá trị lạ vào cột `Status` là **không thể** (`ENUM`) — thay vào đó cho `InvoiceStatus.of("abc")` | Trả `null`, UI hiện nguyên chuỗi thô, **không** `IllegalArgumentException`, màn hình xem hoá đơn vẫn mở |
| 51 | Tắt MySQL rồi bấm *Huy hoa don* | Cửa sổ **không đứng hình** (SwingWorker), hiện `Ui.error` với lỗi kết nối, không phải cảnh báo nghiệp vụ |
| 52 | Nạp `04_routines.sql` **thiếu** `sp_settle_invoice` (xoá thủ tục bằng tay) rồi lập hoá đơn | `ROLLBACK` sạch, thông báo lỗi nêu đúng tên thủ tục thiếu; **không** có hoá đơn nửa vời trong DB |
| 53 | Hai cửa sổ app cùng mở, cả hai bấm *Huy hoa don* trên cùng hoá đơn | Một cái thành công, cái còn lại nhận *"da bi huy truoc do"* — không cái nào văng exception |

### 7.6. Giao điểm với các khối khác

| # | Việc | Kết quả mong đợi |
|---|---|---|
| 54 | Lập một hoá đơn, mở tab 5 Setup App (khối 1) | Thứ tự dòng: `sp_create_invoice_header` → `sp_add_invoice_line [batch x N]` → `sp_add_payment` (N lần) → `sp_settle_invoice` → `COMMIT`. Lọc theo `sp_settle_invoice` ra đúng 1 dòng mỗi hoá đơn |
| 55 | Huỷ một hoá đơn, xem tab 5 | Một dòng `{call sp_void_invoice(?)}` kèm tham số |
| 56 | Huỷ thất bại (#38), xem tab 5 | Có dòng `sp_void_invoice` với **0 dòng ảnh hưởng** — đúng quy ước khối 3 dùng để kể cả lần thử thất bại |
| 57 | Trả hàng (khối 3) trên hoá đơn `PAID` rồi xem lại trạng thái | Vẫn `PAID` — khối 4 đã chốt không thêm `RETURNED` (mục 3.3) |
| 58 | Trả hết 100% hàng của một hoá đơn, chạy `sp_report_big_returns` | Hoá đơn hiện ra với `Phan_tram_tra = 100`, trạng thái vẫn `PAID`: thông tin không mất, chỉ không nằm ở cột `Status` |
| 59 | `quick.login.enabled=false` (khối 2) rồi chạy toàn bộ mục 7.2 | Không khối nào ảnh hưởng tới khối nào |

---

## 8. Rủi Ro & Cách Giảm

| # | Rủi ro | Mức | Cách giảm |
|---|---|---|---|
| 1 | **Quên bước 4 trong `createInvoice`** → mọi hoá đơn mới mắc kẹt ở `OPEN`, báo cáo doanh thu rỗng và trông như app hỏng nặng | **Cao** | Kịch bản #29 là bài test đầu tiên của Phần B. Và thứ tự Phần A → Phần B khiến lỗi này lộ ra ngay lần lập hoá đơn đầu tiên, không âm thầm |
| 2 | Gọi `sp_settle_invoice` bằng `executeQuery` hoặc để thủ tục trả `SELECT` | Trung bình | Comment ngay trong thủ tục (mục 4.4) và trong `InvoiceDao` (mục 5.3a) nói rõ `executeUpdate` + "không trả ResultSet" |
| 3 | Gọi `sp_settle_invoice` **ngoài** transaction (connection khác) → không thấy `Payment` chưa commit → hoá đơn thu đủ vẫn `OPEN` | Trung bình | Mục 5.3a nêu lý do; kịch bản #29 bắt được ngay |
| 4 | Cái bẫy `0 >= 0`: bỏ phép kiểm `EXISTS` dòng hàng → hoá đơn rỗng thành `PAID` | **Cao** | Kịch bản #8 và #9 tồn tại chỉ để bắt lỗi này. Comment cảnh báo nằm trong cả hàm (mục 4.2) và thủ tục (mục 4.4) |
| 5 | Sửa `sp_update_invoice_header` làm hỏng luôn việc sửa ngày/giờ (chặn quá tay) | Trung bình | Kịch bản #19, #40, #46 — ba bài test đều về "sửa trường khác, trạng thái không đổi" |
| 6 | `LEFT JOIN` ở `sp_get_invoice_header` làm `Tong_phai_tra` thành `NULL` → `InvoiceViewPanel.showDetail:389` gọi `formatCurrency(null)` | Trung bình | `COALESCE(..., 0)` trên cả **ba** cột tổng (mục 4.8), không chỉ cột cuối. Kịch bản #26 |
| 7 | Người dùng tưởng *Huy hoa don* và *Xoa hoa don* là một | Trung bình | Hộp thoại ở mục 5.4b nêu 4 điểm khác biệt; hai nút đặt cạnh nhau nhưng nhãn và tooltip khác rõ |
| 8 | Hoá đơn `OPEN` tích tụ: khách trả thiếu, không ai quay lại thu nốt, tab 3 đầy hoá đơn dở | Thấp | Chip *Đang lập* (mục 5.6) là công cụ để tìm chúng. Việc "nhắc thu nốt" là nghiệp vụ, không phải khối này |
| 9 | Lỗi #4 (sửa dòng hàng làm lệch tiền) vẫn còn nguyên sau khối này | Thấp | **Có chủ ý** (mục 2.3) — và giờ ít nhất có cảnh báo nhìn thấy được (mục 5.5c, kịch bản #47). Nếu khối sau muốn tự chuyển trạng thái thì `sp_settle_invoice` đã idempotent nên gọi thêm ở cuối `sp_update_invoice_line` là đủ — nhưng **chỉ** theo chiều `OPEN → PAID`, chiều ngược vẫn bị cấm |
| 10 | Khối 5 (UI tab lập hoá đơn) muốn lập hoá đơn nhiều bước (tạo header trước, thêm hàng sau) | Thấp | Khối này đã dọn sẵn đường: `OPEN` là trạng thái thật, `LEFT JOIN` cho hoá đơn chưa có dòng hàng, `sp_settle_invoice` gọi lại bao nhiêu lần cũng được. Ghi ở đây để khối 5 không phải tìm lại |

---

## 9. Trạng Thái Triển Khai

**ĐÃ TRIỂN KHAI** — 2026-10-11. Phần A (SQL) và Phần B (Java) xong, Phần C (seed `OPEN`) cũng làm.

### 9.1. File đã thay đổi

| File | +/- | Việc |
|---|---|---|
| `sql/04_routines.sql` | +255 / -17 | 5 `DROP`; 2 hàm `fn_invoice_amount_due/paid`; 3 thủ tục `sp_settle_invoice`, `sp_void_invoice`, `sp_get_invoice_edit_fields`; sửa 5 thủ tục cũ |
| `sql/02_seed.sql` | +17 / -2 | Phần C: `INV-0016` trạng thái `OPEN` (tổng 178.000, `PAY-0018` 78.000, thiếu 100.000) |
| `src/billing/model/InvoiceStatus.java` | **mới, 41 dòng** | Bản chép duy nhất của bộ giá trị + nhãn tiếng Việt |
| `src/billing/dao/InvoiceDao.java` | +114 / -0 | Bước 4 trong `createInvoice`; `voidInvoice`, `invoiceEditFields`, `amountDue`, `amountPaid`, `scalarMoney`; javadoc `updateInvoiceHeader` |
| `src/billing/ui/InvoiceViewPanel.java` | +101 / -12 | `btVoidInvoice` + `onVoidInvoice()`; `applyRolePermissions` 8 nút; bỏ SQL thô; chặn trả hàng `<> PAID`; `showReturnFailure` → `showSqlFailure` |
| `src/billing/ui/EditInvoiceDialog.java` | +112 / -6 | Ô trạng thái chỉ đọc, `JComboBox<InvoiceStatus>`, `showSqlFailure`, dòng cảnh báo lệch tiền (`loadMoneyGap`) |
| `src/billing/ui/SearchPanel.java` | +23 / -6 | Chip `Đang lập` + tham số `?` thay literal |
| `src/billing/ui/NewInvoicePanel.java` | +23 / -8 | Đổi thứ tự 2 phép kiểm, `!= 0` → `< 0`, thông điệp `OPEN` |
| `docs/05-SQL-JAVA-CONVENTION.md` | +8 / -2 | 5 dòng mới + sửa `sp_create_invoice_header`, `sp_add_return` |
| `sql/01_schema.sql` | **không đổi** | Đúng kết luận mục 3.3 |

Tổng: **653 dòng thêm, 53 dòng xoá** trên 8 file + 1 file mới.

### 9.2. Những điểm làm khác kế hoạch (và lý do)

1. **`sp_settle_invoice` / `sp_void_invoice` đánh số 5.4 / 5.5, nên `sp_add_return` và
   `sp_delete_return` dời thành 5.6 / 5.7.** Kế hoạch mục 4.4 nói đặt sau `sp_add_payment` (đang là
   5.3) nhưng hai số đó đã có chủ. Chọn giữ đúng **vị trí** kế hoạch yêu cầu — thứ tự trong file
   khớp với thứ tự Java gọi (header → line → payment → settle) — và đổi hai dòng comment tiêu đề.

2. **`docs/05` mục 6 không có dòng `sp_update_invoice_header` để sửa.** Mục 5.8 nói "sửa 2 dòng đang
   có" nhưng bảng đó chưa bao giờ liệt kê các thủ tục sửa/xoá trực tiếp
   (`sp_update_invoice_header`, `sp_update_invoice_line`, `sp_delete_invoice`,
   `sp_delete_invoice_line`, `sp_authenticate_account`). Đã **thêm mới** dòng
   `sp_update_invoice_header` (vì hợp đồng `p_status` là phép kiểm cần được ghi lại ở đó), và sửa
   `sp_create_invoice_header` + `sp_add_return` như kế hoạch. Bốn thủ tục còn lại vẫn thiếu — việc
   của một khối khác, không phải khối này.

3. **`EditInvoiceDialog.doSave()` truyền `originalStatus` (chuỗi thô đọc từ DB), không phải
   `((InvoiceStatus) cbStatus.getSelectedItem()).code()` như mục 5.5a.** Hai cách cho kết quả giống
   nhau ở mọi trường hợp thường, nhưng khác nhau đúng ở trường hợp mục 5.1 kịch bản #50 quan tâm:
   nếu cột `Status` chứa giá trị mà `InvoiceStatus.of()` không nhận ra thì combo box không chọn được
   mục nào, và `code()` sẽ gửi xuống `OPEN` (mục đầu danh sách) → `sp_update_invoice_header` từ chối
   một việc hợp lệ. Gửi lại đúng chuỗi đã đọc lên là cách duy nhất chắc chắn khớp. Vẫn giữ nhánh
   `code()` làm dự phòng khi `originalStatus` là `null`.

4. **Dòng cảnh báo lệch tiền chỉ hiện trên hoá đơn `PAID`.** Mục 5.5c nói vậy nhưng dễ bị đọc thành
   "hiện khi `paid < due`". Trên hoá đơn `OPEN` thì `paid < due` là trạng thái **bình thường** (đó
   chính là định nghĩa của `OPEN`), báo động đỏ ở đó là báo động giả. `loadMoneyGap()` thoát ngay nếu
   trạng thái không phải `PAID`.

5. **`InvoiceStatus` có thêm `toString()` trả về `display()`.** Không có trong mục 5.2, nhưng
   `JComboBox<InvoiceStatus>` gọi `toString()` để vẽ từng mục — thiếu nó thì ô trạng thái hiện
   `OPEN`/`PAID`/`VOIDED` trần thay vì `Dang lap (OPEN)`.

6. **`InvoiceDao.voidInvoice` ghi nhật ký cả lần thử thất bại** (một dòng `0 dong` trước khi ném).
   Mục 5.3b không nêu, nhưng kịch bản **#56** đòi đúng điều đó.

7. **`amountDue` / `amountPaid` dùng chung một hàm riêng `scalarMoney(...)`** thay vì hai thân hàm
   giống nhau — cùng một câu gọi, chỉ khác tên hàm SQL và câu thông báo lỗi.

8. **`showSqlFailure` là `static` (package-private)** để `EditInvoiceDialog` gọi được
   (`InvoiceViewPanel.showSqlFailure(this, ex, ...)`). Mục 5.4e không nói về tầm vực.

### 9.3. Kết quả kiểm thử đã chạy

**Mục 7.1 (Phần A, `mysql` CLI) — chạy thật đủ 28/28 kịch bản, tất cả ĐÚNG như mong đợi.**
Đáng chú ý:

- **#1** nạp `04_routines.sql` hai lần liên tiếp, lần hai vẫn in `... da tao thanh cong!`
- **#3** bất biến `paid >= due` trên toàn bộ seed: **0 dòng** vi phạm (kể cả sau khi thêm `INV-0016`)
- **#8, #9** cái bẫy `0 >= 0`: hoá đơn rỗng (kể cả khi có `Payment` 500.000) vẫn `OPEN`
- **#10, #11** thiếu 1 đồng → `OPEN` không báo lỗi; thêm 1 đồng → `PAID`
- **#20** `p_status = NULL` cho `45000` *"Khong mo lai hoa don da thu tien (PAID -> NULL)"*, **không
  phải** lỗi `ENUM` của MySQL
- **#25** `sp_get_recent_invoices` trả **20/20** hoá đơn có trong DB, hoá đơn 0 dòng hàng có
  `Tong_tien = 0`
- **#27** đối chiếu cột `So_hoa_don` của báo cáo với câu đếm `Status='PAID'` độc lập: trùng khớp

**Mục 7.2–7.6 — chạy ở tầng DAO bằng hai chương trình kiểm thử tạm (không qua Swing): 31/31 + 2/2.**
Các kịch bản mà giao diện chỉ là lớp mỏng bên trên DAO:

| Kịch bản | Kết quả |
|---|---|
| **#29** thu đủ tiền → `PAID`; thứ tự nhật ký SQL đúng: `sp_create_invoice_header` → `sp_add_invoice_line [batch x N]` → `sp_add_payment` → `sp_settle_invoice` → `COMMIT` (**= #54**) | ✔ |
| **#30** trả thiếu → `OPEN` | ✔ |
| **#33** trả thừa (50.000/35.000) → `PAID` | ✔ |
| **#34** `processReturn` trên hoá đơn `OPEN` → `45000` nêu đúng chữ `OPEN` | ✔ |
| **#37** `voidInvoice` → `VOIDED`, dòng hàng và thanh toán **vẫn còn** | ✔ |
| **#38** huỷ lần hai → exception có cause `SQLSTATE 45000` (đúng đường `showSqlFailure` đi) | ✔ |
| **#40** sửa giờ hoá đơn `VOIDED` (truyền đúng `VOIDED`) → **thành công** | ✔ |
| **#42** huỷ hoá đơn `OPEN` → `VOIDED` | ✔ |
| **#46** sửa giờ hoá đơn `PAID` → thành công, trạng thái không đổi | ✔ |
| **#16, #17** đổi trạng thái qua `updateInvoiceHeader` → `45000` với đúng thông điệp | ✔ |
| **#24, #25, #26** hoá đơn 0 dòng hàng: đọc được, có mặt ở danh sách, **ba** cột tổng = `0` (không `NULL`) | ✔ |
| **#47** `amountDue`/`amountPaid` trả đúng số; hoá đơn không tồn tại → `0.00` | ✔ |
| **#50** `InvoiceStatus.of("abc")` → `null`, không `IllegalArgumentException` | ✔ |
| **#52** xoá `sp_settle_invoice` rồi lập hoá đơn → `ROLLBACK` sạch (`Invoice`/`Invoice_Line`/`Payment` đều 0 dòng), thông điệp nêu đúng tên thủ tục thiếu | ✔ |
| **#53** hai luồng cùng `voidInvoice` một hoá đơn → 1 thành công, 1 nhận *"da bi huy truoc do"*, **0** exception lạ (`FOR UPDATE` làm đúng việc) | ✔ |
| **#56** lần huỷ thất bại có dòng nhật ký `sp_void_invoice` với **0 dòng ảnh hưởng** | ✔ |

**Biên dịch:** 31 file `.java`, 0 lỗi, 0 cảnh báo mới (chỉ còn cảnh báo `Locale(String,String)` có
từ trước ở `SearchPanel.java:40`). **App khởi động** bằng `.\scripts\ps1\run.ps1 -Login manager`:
kết nối DB, tab 3 nạp 18 hoá đơn, không exception.

**CHƯA chạy — cần người bấm chuột trên giao diện Swing:**

| Kịch bản | Vì sao |
|---|---|
| **#31, #32, #35** báo cáo / thông báo / trả hàng trên tab 1 và tab 3 | Cần bấm nút; logic bên dưới đã kiểm ở DAO (#29/#30/#34) |
| **#36, #39, #41** nội dung hộp thoại xác nhận huỷ, chặn trả hàng ở UI, số liệu tab Báo cáo | Chỉ đọc được bằng mắt trên màn hình |
| **#43, #44, #45, #48** 8 nút mờ với thu ngân, *Đổi vai trò* bật nút ngay, ô trạng thái mờ, chip *Đang lập* | Phân quyền và trạng thái widget |
| **#47 (phần hiển thị)** dòng đỏ *"Da thu ... LECH ..."* | Hai số đã kiểm đúng ở DAO; còn lại là việc vẽ nhãn |
| **#49** đổi `Status` bằng tay rồi thử trả hàng qua UI | Cần giao diện |
| **#51** tắt MySQL rồi bấm *Huy hoa don* | Không tự tắt MySQL của người dùng. `onVoidInvoice` chạy trong `SwingWorker` và lỗi không phải `45000` rơi về `Ui.error` — cùng đường code đã chạy ở #38 |
| **#54, #55, #56 (xem trên tab 5 Setup App)** | Nội dung nhật ký đã kiểm trực tiếp từ `logs/sql.log`; còn lại là việc tab 5 đọc file |
| **#57, #58, #59** giao điểm với khối 2 và 3 | #57/#58 đã đúng theo thiết kế (không thủ tục nào ghi `Status` khi trả hàng — kiểm bằng #28); #59 cần đổi `config.properties` rồi chạy lại |

`INV-0016` trong seed tồn tại để các kịch bản `OPEN` có dữ liệu sẵn mà không phải lập hoá đơn trước:
mở app là thấy ngay một hoá đơn *Đang lập* ở tab 3.
