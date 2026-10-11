# Kế Hoạch Chi Tiết — Khối 3: Sửa Nghiệp Vụ Trả Hàng

> **Dành cho AI / lập trình viên tiếp theo:** đây là khối thứ ba trong đợt cải tiến gồm 6 khối
> (1 SQL log → Setup App ✅ xong, 2 đăng nhập nhanh ✅ xong, **3 sửa trả hàng ✅ xong ← khối này**,
> 4 trạng thái PAID/OPEN/VOIDED, 5 UI tab lập hoá đơn, 6 seed động 90 ngày).
>
> **Khác hai khối trước: khối này CHẠM VÀO DATABASE.** Thêm 2 hàm + 3 thủ tục vào
> `sql/04_routines.sql`, thêm 1 cột vào bảng `Account` trong `sql/01_schema.sql`, sửa `sql/02_seed.sql`.
> Vì vậy **thứ tự triển khai là bắt buộc: nạp SQL trước, biên dịch Java sau** (mục 6), và phải đọc
> mục 4.1 trước khi gõ dòng code Java đầu tiên.
>
> Khối này độc lập với khối 4, 5, 6. Giao điểm duy nhất với khối 4 là cột `Invoice.Status`, và khối
> này chỉ **đọc** cột đó, không bao giờ ghi (mục 2.3).

---

## 1. Bối Cảnh & Vấn Đề

### 1.1. Hiện trạng (đã đọc code + SQL + seed, không phỏng đoán)

| Thành phần | Trạng thái hiện tại |
|---|---|
| `sql/01_schema.sql:182-195` | Bảng `` `Return` `` — khoá chính **3 cột** `(Invoice_ID, Line_Number, Return_ID)`; `Return_Quantity DECIMAL(10,3)`; `Approved_By` → khoá ngoại tới `Cashier(Cashier_ID)`. **Không có cột tiền hoàn** |
| `sql/04_routines.sql:304-322` | `sp_get_invoice_returns` — đọc, tự tính `Tien_hoan` bằng công thức **theo tỷ lệ** |
| `sql/04_routines.sql:400-421` | `sp_report_big_returns` — báo cáo 5, lặp lại **y nguyên** công thức đó lần thứ hai |
| `sql/04_routines.sql` | **Không có `sp_add_return`.** Đã `grep` toàn bộ dự án: tên này xuất hiện **đúng một lần**, ở `InvoiceDao.java:360` |
| `sql/02_seed.sql:195-201` | 3 lượt trả mẫu, `Return_ID` đánh số **1 cho từng dòng** (không phải số tăng toàn cục), `Approved_By` = `E01` |
| `sql/01_schema.sql:249-255` | Bảng `Account` — 4 cột, **không có bất kỳ liên kết nào** tới `Cashier` |
| `src/billing/dao/InvoiceDao.java:37-65` | `ReturnItem` — `int returnQuantity`, `double amountRefunded` |
| `src/billing/dao/InvoiceDao.java:355-421` | `processReturn()` — transaction đúng, nhưng gọi thủ tục không tồn tại, và nuốt lỗi (`return false` + `printStackTrace`) |
| `src/billing/ui/InvoiceViewPanel.java:650-1082` | `onReturnLine()` — hộp thoại 432 dòng, chạy toàn bộ trên EDT |
| `src/billing/ui/InvoiceViewPanel.java:401-425` | `applyRolePermissions()` — khoá 5 nút, **không có `btReturnLine`** |
| `src/billing/ui/InvoiceViewPanel.java:53-150` | `InvoiceLineData` + `InvoiceLineTableModel` — chỉ hộp thoại trả hàng dùng, không chỗ nào khác |
| `src/billing/ui/InvoiceViewPanel.java:198-244` | `parseVNCurrency()` — 47 dòng, cũng **chỉ** hộp thoại trả hàng dùng |

Nói gọn: phần SQL *đọc* trả hàng đã hoàn chỉnh và đúng. Phần *ghi* chưa bao giờ tồn tại.

### 1.2. Mười hai lỗi cụ thể

**Nhóm A — tính năng chết hẳn (bấm là thất bại 100%)**

1. **`sp_add_return` không tồn tại.** `InvoiceDao.java:360` gọi `{call sp_add_return(?,?,?,?,?,?,?)}`.
   MySQL trả `PROCEDURE retail_billing.sp_add_return does not exist` → `catch (SQLException)` →
   `rollback` → `return false` → người dùng chỉ thấy *"Xu ly tra hang that bai! Vui long thu lai
   sau."*. **Thử lại sau bao lâu cũng vậy.** Nguyên nhân thật chỉ in ra `System.err`, không ai thấy.

2. **Tham số thứ 6 (`amountRefunded`) không có cột nào để ghi vào.** `InvoiceDao.java:379` truyền
   số tiền hoàn, nhưng bảng `` `Return` `` chỉ có 6 cột và không cột nào là tiền. Dù có viết
   `sp_add_return` 7 tham số thì tham số này cũng phải bỏ đi — tiền hoàn là **giá trị dẫn xuất**,
   `sp_get_invoice_returns` đã tính được nó từ `Return_Quantity` + `Invoice_Line`.

3. **`Return_ID` được coi là `AUTO_INCREMENT`, nhưng nó không phải.** `InvoiceDao.java:372-374`:
   comment ghi *"để NULL và để DB xử lý (AUTO_INCREMENT)"*, code gọi
   `cs.setNull(1, Types.VARCHAR)` cho một cột `INT NOT NULL` nằm trong khoá chính 3 cột. Seed cho
   thấy ý định thật của thiết kế: `Return_ID` là **số thứ tự trong phạm vi một dòng hoá đơn**
   (`INV-0003/dòng 2/lần 1`, `INV-0013/dòng 1/lần 1`) — thứ `AUTO_INCREMENT` không làm được.

4. **`Approved_By` nhận sai loại mã.** `InvoiceViewPanel.java:1025-1029` lấy
   `currentUser.getUsername()` → `"manager"` hoặc `"cashier"`. Khoá ngoại `fk_return_cashier` đòi
   một `Cashier_ID` → `'E01'`..`'E05'`. **Hai tập giá trị không giao nhau.** Nên dù có thủ tục,
   mọi lượt trả vẫn chết ở lỗi 1452 (foreign key constraint fails). Hai bảng `Account` và `Cashier`
   hiện **không có đường nào nối với nhau** — đúng chỗ `plan/11` mục 2.3 đã cố ý hoãn lại.

**Nhóm B — sai số tiền / sai số lượng (sẽ ghi dữ liệu sai nếu nhóm A được sửa mà quên nhóm này)**

5. **Công thức tiền hoàn sai, vì `Discount` là mức giảm của CẢ DÒNG.**
   `sp_get_invoice_lines:264` định nghĩa rõ: `Thanh_tien = Quantity * Unit_Price - Discount`.
   Vậy `Discount` là một **số tiền cho cả dòng**, không phải giảm giá mỗi đơn vị. Nhưng
   `InvoiceViewPanel.java:78-80` (`getUnitPriceAfterDiscount`) làm `unitPrice - discount` rồi
   `:870` nhân với số lượng trả.

   Lấy dữ liệu thật — `INV-0001` dòng 1: `Quantity=2`, `Unit_Price=33.000`, `Discount=6.600`,
   `Thanh_tien = 59.400`.

   | Trả | Công thức của DB (`sp_get_invoice_returns`) | Công thức của hộp thoại hiện tại | Lệch |
   |---|---|---|---|
   | 1 đơn vị | `1/2 × 59.400` = **29.700** | `1 × (33.000 − 6.600)` = **26.400** | 3.300 |
   | 2 đơn vị (cả dòng) | `2/2 × 59.400` = **59.400** | `2 × 26.400` = **52.800** | 6.600 |

   Hàng dưới là hàng đáng sợ: trả lại **toàn bộ** một dòng mà khách nhận về ít hơn đúng số tiền đã
   được giảm giá. Và cả hai con số cùng hiện trên màn hình một lúc: hộp thoại báo 52.800, bảng
   "Tra hang (Return)" ở tab 3 sau đó báo 59.400.

6. **Cột `Thanh_tien` của bảng bên trái hộp thoại không nhân số lượng.**
   `InvoiceLineTableModel.getValueAt` case 6 (`:118`) trả `unitPrice - discount`. Cùng một dòng
   `INV-0001/1`: tab 3 hiện `59.400`, hộp thoại trả hàng hiện `26.400`.

7. **Số lượng bị ép về số nguyên ở 4 chỗ** (`:114`, `:842`, `:852`, `:881`) trong khi
   `Invoice_Line.Quantity` và `Return.Return_Quantity` đều là `DECIMAL(10,3)`.
   Dữ liệu thật: `INV-0002` dòng 1 = **1.500 KG** thịt ba chỉ (`sql/02_seed.sql:151`).
   `(int) Math.round(1.5)` = **2** → hộp thoại nói *"So luong mua ban dau: 2"*, và chấp nhận trả
   **2 kg hàng mà khách chỉ mua 1,5 kg**. Không có cách nào gõ `0.5` để trả nửa số đã mua
   (`Integer.parseInt("0.5")` ném `NumberFormatException`).

8. **Không trừ phần đã trả.** `:860` chỉ so số lượng trả với số lượng **mua**, không hề đọc bảng
   `` `Return` ``. `INV-0013` dòng 1 đã bị trả hết (2.000/2.000 — `sql/02_seed.sql:199`), vẫn mời
   trả thêm 2 nữa. Trả 5 lần liên tiếp thì `sp_report_big_returns` sẽ báo một hoá đơn bị trả
   **500% giá trị**.

**Nhóm C — sai dòng hoá đơn (hỏng dữ liệu, không báo lỗi)**

9. **Ánh xạ theo TÊN sản phẩm thay vì theo số dòng.** `:977-982` dựng
   `Map<String,Integer> lineNumberMap` khoá bằng `San_pham`. Một hoá đơn **được phép** có cùng một
   sản phẩm ở hai dòng (khoá chính `Invoice_Line` là `(Invoice_ID, Line_Number)`, không cấm trùng
   `Barcode`; tab 1 gộp lại nhưng nút "Sua dong hang" ở tab 3 thì tạo được). Khi đó:
   `put(tên, 1)` rồi `put(tên, 2)` → **dòng 1 biến mất khỏi map**. Người dùng chọn dòng 1, hệ
   thống ghi lượt trả vào **dòng 2**, giá lấy từ dòng 1. Không một thông báo nào.
   Thêm nữa, `:833-838` dùng cùng cái khoá đó để chặn trùng → **không thể trả cả hai dòng**.

10. **`updateReturnTableSTTAndTotal` ghi đè cột số dòng.** `:1088-1093` ghi `i + 1` vào cột 0 —
    đúng cái cột đang chứa `Line_Number` thật (`:879`). Chạy ngay sau mỗi lần Thêm/Xoá (`:890`,
    `:922`). **Đây chính là lý do tồn tại của lỗi #9**: số dòng thật đã bị xoá nên đoạn lưu buộc
    phải đi tìm lại nó bằng tên sản phẩm. Sửa lỗi này thì lỗi #9 tự hết đường sống.

**Nhóm D — quyền, luồng, và phản hồi**

11. **`btReturnLine` không nằm trong `applyRolePermissions()`.** 5 nút ghi DB khác đều bị khoá với
    thu ngân (`:405-409`); nút "Tra hang" bị bỏ sót → thu ngân bấm được. Mà cột được ghi tên là
    `Approved_By` — *người duyệt*. Một nút ghi vào ô "người duyệt" mà ai cũng bấm được thì ô đó
    không duyệt gì cả.

12. **Chạy trên EDT, và không thấy kết quả sau khi lưu.**
    - `:1038` gọi `dao.processReturn()` — một transaction N lệnh `INSERT` — ngay trên EDT. MySQL
      chậm/tắt là hộp thoại modal đứng hình (đúng vấn đề `plan/11` mục 4.2 đã sửa cho đăng nhập).
    - `:1046-1047` gọi `reload()` rồi `onSelect(null)`. Nhưng `reload()` → `fillFormatted` →
      `model.setRowCount(0)` → **mất selection** → `getSelectedInvoiceId()` trả `null` →
      `onSelect` thoát ở `:447`. Kết quả: bảng "Tra hang (Return)" **giữ nguyên nội dung cũ**.
      Người dùng vừa được báo "thanh cong" nhưng không thấy lượt trả nào xuất hiện.
    - Phụ: 6 `DefaultTableCellRenderer` ở `:715-794` là **code chết** — model chứa chuỗi đã định
      dạng (`:883-885`) nên `value instanceof Number` luôn `false`. Và tổng tiền được tính bằng
      cách **parse lại chuỗi đã format** (`:1104-1107`), một vòng `format → parse` không cần thiết.

---

## 2. Mục Tiêu & Phạm Vi

### 2.1. Mục tiêu

1. **Trả hàng chạy được** — bấm xong có dòng mới trong bảng `` `Return` `` và thấy nó trên màn hình.
2. **Tiền hoàn đúng một công thức duy nhất**, và công thức đó nằm trong SQL (mục 3.1).
3. **Số lượng thập phân đúng tới 3 chữ số** ở mọi chỗ: nhập, hiển thị, lưu (mục 3.2).
4. **Không bao giờ trả vượt số còn lại** — kiểm ở UI để thân thiện, kiểm lại ở SQL để chắc (mục 3.4).
5. **Người duyệt là một nhân viên thật**, suy ra từ tài khoản đang đăng nhập (mục 3.3).
6. **Chỉ quản lý được trả hàng**, nhất quán với 5 nút ghi DB còn lại của tab 3.
7. Lỗi nghiệp vụ hiện ra **đọc hiểu được**, không còn câu "that bai, thu lai sau".

### 2.2. Nguyên tắc bất di bất dịch

> **Công thức tiền hoàn chỉ tồn tại ở MỘT chỗ trong cả dự án, và chỗ đó là SQL.**

Hiện công thức bị chép 3 lần: `sp_get_invoice_returns:312-313`, `sp_report_big_returns:413-414`,
và `InvoiceViewPanel:869-870` (bản chép sai). Khối này gom về một hàm `fn_return_refund` và sửa
**cả hai** thủ tục cũ gọi vào nó. Lý do:

- Lỗi #5 ở trên sinh ra **chính vì** có nhiều bản chép. Giảm về 1 bản là biện pháp phòng ngừa, không
  phải thú vui kiến trúc.
- Đúng vai trò mà `docs/05-SQL-JAVA-CONVENTION.md` mục 1 đã phân: `fn_*` trả giá trị đơn, và "tiền
  hoàn của n đơn vị" là một giá trị đơn.
- Hộp thoại hỏi đúng cái hàm mà báo cáo và tab 3 dùng → con số trên hộp thoại xác nhận **bằng đúng**
  con số hiện ra sau khi lưu. Không còn chuyện hai màn hình nói hai giá trị.

Hệ quả: **Java không được tự tính tiền hoàn.** Dù chỉ là một phép nhân.

### 2.3. Ngoài phạm vi khối này (và vì sao)

| Việc | Vì sao không làm ở khối này |
|---|---|
| Nhập hàng trả về lại kho | Lược đồ **không có bảng kho** (16 bảng, không có `Stock`/`Inventory` — `sql/01_schema.sql`). Thêm bảng tồn kho là một khối riêng, lớn hơn khối này |
| Ghi phiếu hoàn tiền (dòng `Payment` âm) | `Payment.Amount DECIMAL(12,2)` không cấm số âm, nhưng mọi câu `SUM(Amount)` trong `03_queries.sql` và các báo cáo đang giả định tiền thu vào. Đổi giả định đó là việc của khối trạng thái/thanh toán |
| Đổi `Invoice.Status` sang `RETURNED` / `PARTIALLY_RETURNED` | **Khối 4 sở hữu cột `Status`.** Khối này chỉ đọc `Status` để chặn trả hàng trên hoá đơn `VOIDED`. Đoạn code chết `InvoiceDao.java:390-397` (comment về `sp_update_invoice_return_status`) sẽ **xoá**, không hiện thực hoá |
| Chặn trả hàng trên hoá đơn `OPEN` | Nghe hợp lý (chưa trả tiền thì hoàn gì), nhưng ý nghĩa thật của `OPEN` do khối 4 định nghĩa. Khối này chỉ chặn `VOIDED` — trường hợp duy nhất không cần tranh luận |
| Hộp thoại sửa một lượt trả đã ghi | Sửa số lượng của một lượt trả = xoá + thêm. Phần C (tuỳ chọn) làm nút **huỷ** một lượt trả; sửa thì không |
| `VAT_pct` ở tab 3 đang hiện là "8đ" (`:473` xếp cột 7 vào nhóm tiền tệ) | Lỗi hiển thị thật, nhưng thuộc cột thuế, không thuộc trả hàng. Để khối 5 (UI) gom |
| Chặn quyền ở tầng DAO/SQL | Giữ nguyên quan điểm đã ghi trong javadoc `InvoiceViewPanel:37-40`: khoá ở UI, DAO để mở. Khối này không đổi quy ước đó |

---

## 3. Hợp Đồng Dữ Liệu (quan trọng nhất)

Khối 1 có hợp đồng định dạng file, khối 2 có hợp đồng cấu hình. Khối này có **hợp đồng tính tiền và
hợp đồng kiểu số**. Sửa một bên phải sửa tài liệu này.

### 3.1. Công thức tiền hoàn

```
Gọi  Q = Invoice_Line.Quantity        (số lượng đã mua của dòng)
     T = Q * Unit_Price - Discount    (thành tiền của dòng, đúng như sp_get_invoice_lines:264)
     n = số lượng trả lại

     Tien_hoan(n) = ROUND( n / Q * T , 2 )
```

Đọc bằng lời: **trả bao nhiêu phần của dòng thì hoàn lại đúng bấy nhiêu phần tiền của dòng đó**,
giảm giá của dòng cũng được chia theo đúng tỷ lệ ấy.

- Đây **không phải công thức mới** — nó là bản sao chính xác của `sp_get_invoice_returns:312-313`
  đang chạy. Khối này chỉ đặt nó vào một hàm và xoá các bản chép.
- `ROUND(..., 2)` ở từng dòng, rồi **cộng các dòng đã làm tròn** để ra tổng. Không làm tròn tổng.
  Nhờ vậy tổng trên hộp thoại xác nhận trùng khít với tổng cột `Tien_hoan` ở tab 3 sau khi lưu.
- Hàm giữ tên cố định: `fn_return_refund(p_invoice_id, p_line_number, p_quantity)`.

### 3.2. Số lượng là `DECIMAL(10,3)` — từ ô nhập tới cột DB

| Chặng | Kiểu bắt buộc |
|---|---|
| Ô nhập của người dùng | chuỗi, nhận chữ số + **một** dấu thập phân (`,` hoặc `.`) |
| Java trong bộ nhớ | `java.math.BigDecimal` — **không `double`, không `int`** |
| `cs.setBigDecimal(...)` | đúng ánh xạ `DECIMAL ↔ BigDecimal` của `docs/05` mục 3 |
| Cột DB | `DECIMAL(10,3)` |
| Hiển thị | `DecimalFormat("#,##0.###")` → `1.500` ra `1,5`; `2.000` ra `2` |

Ba quy tắc cho ô nhập:

1. **Quá 3 chữ số thập phân thì từ chối**, không âm thầm làm tròn. `DECIMAL(10,3)` sẽ lặng lẽ biến
   `1.2345` thành `1.235` — người dùng gõ một số rồi DB lưu một số khác là thứ không nên dạy.
2. **Không nhận dấu phân cách nghìn.** Vì màn hình hiển thị dùng `.` làm dấu nghìn và `,` làm dấu
   thập phân, nếu ô nhập cũng nhận `.` làm dấu nghìn thì `"1.5"` trở thành nhập nhằng giữa `1,5` và
   `1500`. Số lượng trong app demo không bao giờ tới 1000 nên bỏ hẳn dấu nghìn ở ô nhập là lựa chọn
   không mất gì. `,` và `.` đều được hiểu là **dấu thập phân**.
3. **Điền sẵn số lượng còn lại** vào ô nhập → trường hợp phổ biến nhất (trả hết phần còn lại) chỉ
   còn một lần Enter.

### 3.3. "Người duyệt" là ai? — bốn phương án

`Return.Approved_By` là `NOT NULL` và trỏ tới `Cashier(Cashier_ID)`. Phải có một nhân viên thật.
Mà `Account` hiện không nối với `Cashier` (lỗi #4).

| | Phương án | Vì sao chọn / loại |
|---|---|---|
| **A** | **Thêm cột `Account.Cashier_ID` (NULL được, khoá ngoại tới `Cashier`); `sp_authenticate_account` trả thêm cột đó; `Account` (Java) có thêm `cashierId`** | **ĐÃ CHỌN.** Người duyệt chính là người đang đăng nhập — đúng nghĩa của cột. Ánh xạ nằm trong **dữ liệu**, không nằm trong `if` của Java. Và nó lấp đúng khoảng trống mà `plan/11` mục 2.3 đã cố ý để lại ("gắn `Account` với `Cashier_ID` → khối riêng") |
| B | Ánh xạ cứng trong Java: `"manager" → "E01"` | Loại: nhét dữ liệu nghiệp vụ vào code. Thêm một tài khoản vào bảng `Account` là app sai ngay, mà không chỗ nào báo |
| C | Hộp thoại có ô chọn "Người duyệt" liệt kê toàn bộ thu ngân (`sp_lookup_cashiers` đã có sẵn) | Loại ở vai trò chính: cho phép ghi công duyệt cho **bất kỳ ai**. Một ô ghi nhận trách nhiệm mà người thao tác tự chọn tên người khác thì tệ hơn là không có ô đó |
| D | Dùng luôn `Invoice.Cashier_ID` (thu ngân đã bán hoá đơn) | Loại: người bán tự duyệt hoàn tiền cho chính phiếu mình bán — đúng cái mà cột `Approved_By` sinh ra để ngăn |

Giá trị seed cho phương án A:

| Username | Role | `Cashier_ID` | Vì sao |
|---|---|---|---|
| `manager` | manager | `E01` | `E01` là cấp trên của E02–E05 (`Cashier.Supervisor_ID`), và cả 3 lượt trả trong seed đều do `E01` duyệt |
| `cashier` | cashier | `E02` | Một thu ngân thường, có `Supervisor_ID = 'E01'` |

**Khi `Account.Cashier_ID` là `NULL`** (database chưa nạp lại, hoặc ai đó thêm tài khoản mới mà bỏ
trống): **từ chối trả hàng kèm thông báo chỉ rõ câu lệnh cần chạy**, không âm thầm chọn một
`Cashier_ID` nào khác. Đây là lựa chọn có chủ ý: thà hỏng rõ ràng còn hơn ghi một tên người duyệt
bịa ra. App **không** chết, không exception — chỉ một hộp thoại cảnh báo.

### 3.4. Quy tắc nghiệp vụ được kiểm ở ĐÂU

> **UI kiểm để thân thiện. SQL kiểm để đúng. SQL là chốt cuối.**

| Quy tắc | UI (hộp thoại) | SQL (`sp_add_return`) |
|---|---|---|
| Số lượng trả > 0 | chặn lúc gõ, báo ngay | `SIGNAL '45000'` |
| Số lượng trả ≤ số còn lại | chặn lúc gõ (số còn lại đọc từ `sp_get_returnable_lines`) | `SIGNAL '45000'`, sau khi đã khoá dòng bằng `FOR UPDATE` |
| Tối đa 3 chữ số thập phân | chặn lúc gõ (mục 3.2) | `DECIMAL(10,3)` tự làm tròn → phải chặn ở UI |
| Dòng phải thuộc hoá đơn đang chọn | chỉ liệt kê dòng của hoá đơn đó | `SIGNAL '45000'` nếu không tìm thấy dòng |
| Hoá đơn không ở trạng thái `VOIDED` | thông báo và không mở hộp thoại | `SIGNAL '45000'` |
| Người duyệt là `Cashier_ID` hợp lệ | lấy từ `Account.Cashier_ID` (mục 3.3) | khoá ngoại `fk_return_cashier` |
| Chỉ `manager` được trả hàng | `applyRolePermissions()` | **không kiểm** — giữ quy ước ở mục 2.3 |

Vì sao kiểm hai lần cái quy tắc "không vượt số còn lại"? Vì UI đọc số còn lại **lúc mở hộp thoại**.
Nếu mở hai cửa sổ app (chuyện rất dễ xảy ra khi demo, và `run.ps1` không cấm) thì cả hai đều thấy
"còn 2", cả hai trả 2. Chỉ bản kiểm trong SQL — chạy **sau** `SELECT ... FOR UPDATE` trên dòng hoá
đơn, trong cùng transaction — mới chặn được. Lượt thứ hai nhận thông báo "chỉ còn 0".

### 3.5. Thay đổi cột trả về của `sp_get_invoice_returns` → Java phải đổi chỉ số cột

`docs/05` mục 5 "Quy tắc 1: khoá cứng tên cột trả về" — đổi cột là đổi hợp đồng, nên ghi rõ ở đây.
Thêm `Lan_tra` (`Return_ID`) để phân biệt các lượt trả của cùng một dòng (hiện hai lượt trả cùng
dòng hiện ra như hai hàng giống nhau, không có gì phân biệt):

| Cột | Chỉ số cũ | Chỉ số mới |
|---|---|---|
| `Dong` | 0 | 0 |
| **`Lan_tra`** | — | **1** |
| `San_pham` | 1 | 2 |
| `Ngay_tra` | 2 | 3 |
| `SL_tra` | 3 | 4 |
| `Tien_hoan` | 4 | 5 |
| `Nguoi_duyet` | 5 | 6 |

→ `InvoiceViewPanel:482-483` phải đổi từ `fillFormatted(tbReturns, returns, {4}, {3})` thành
`fillFormatted(tbReturns, returns, {5}, null, {4})` (xem mục 5.8 về tham số thứ 5 mới).

---

## 4. Thiết Kế SQL

### 4.1. Bảng `Account`: thêm cột `Cashier_ID`

**Sửa `sql/01_schema.sql:249-255`:**

```sql
CREATE TABLE Account (
    Username   VARCHAR(50)  NOT NULL,
    Password   VARCHAR(100) NOT NULL,
    Role       ENUM('cashier', 'manager') NOT NULL,
    Full_Name  VARCHAR(100) NULL,
    -- Nhan vien tuong ung trong bang Cashier. NULL = tai khoan khong gan voi
    -- nhan vien nao (khi do khong duyet duoc tra hang -- xem plan/12 muc 3.3).
    Cashier_ID VARCHAR(10)  NULL,
    PRIMARY KEY (Username),
    CONSTRAINT fk_account_cashier
        FOREIGN KEY (Cashier_ID) REFERENCES Cashier (Cashier_ID)
) ENGINE=InnoDB;
```

**Sửa `sql/02_seed.sql:254-256`:**

```sql
INSERT INTO Account (Username, Password, Role, Full_Name, Cashier_ID) VALUES
('cashier', 'cashier', 'cashier', 'Nhan Vien Thu Ngan', 'E02'),
('manager', 'manager', 'manager', 'Quan Ly He Thong',   'E01');
```

**Hai đường để đưa thay đổi này vào database đang chạy:**

| | Cách | Khi nào dùng |
|---|---|---|
| 1 | Chạy lại `.\scripts\ps1\db-setup.ps1` | Khi không cần giữ dữ liệu. **Lưu ý: `01_schema.sql` mở đầu bằng `DROP DATABASE retail_billing` — mọi hoá đơn đã tạo trong lúc học sẽ mất sạch** (cảnh báo này có sẵn ở đầu `db-setup.ps1`) |
| 2 | Chạy đoạn `ALTER` dưới đây rồi nạp riêng `04_routines.sql` | Khi muốn giữ dữ liệu đang có |

```sql
-- Migration cho database da co du lieu
USE retail_billing;

ALTER TABLE Account
    ADD COLUMN Cashier_ID VARCHAR(10) NULL AFTER Full_Name,
    ADD CONSTRAINT fk_account_cashier
        FOREIGN KEY (Cashier_ID) REFERENCES Cashier (Cashier_ID);

UPDATE Account SET Cashier_ID = 'E01' WHERE Username = 'manager';
UPDATE Account SET Cashier_ID = 'E02' WHERE Username = 'cashier';
```

Nạp riêng phần thủ tục (không xoá database), theo đúng cách `db-setup.ps1:46-55` đang làm:

```powershell
$env:MYSQL_PWD = 'root'
Get-Content .\sql\04_routines.sql -Raw -Encoding UTF8 | mysql -u root -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4
$env:MYSQL_PWD = $null
```

### 4.2. Đừng quên khối `DROP` ở đầu `04_routines.sql`

File tự xoá mọi thứ trước khi tạo lại (`:17-48`) để chạy lại được nhiều lần. Thiếu một dòng `DROP`
là lần chạy thứ hai báo lỗi "already exists". Thêm vào đúng nhóm:

```sql
DROP FUNCTION  IF EXISTS fn_return_refund;        -- vao nhom dong 18-20
DROP FUNCTION  IF EXISTS fn_next_return_id;

DROP PROCEDURE IF EXISTS sp_get_returnable_lines; -- vao nhom dong 28-32
DROP PROCEDURE IF EXISTS sp_add_return;           -- vao nhom dong 40-42
DROP PROCEDURE IF EXISTS sp_delete_return;        -- chi khi lam phan C
```

### 4.3. Mới: `fn_return_refund` — bản duy nhất của công thức tiền

Đặt vào **PHẦN 1** của `04_routines.sql` (sau `fn_next_payment_id`, khoảng dòng 117), giữ đúng kiểu
khai báo của 3 hàm đang có (`DETERMINISTIC` + `READS SQL DATA`):

```sql
-- ---------------------------------------------------------------------
-- 1.4 fn_return_refund: Tien hoan lai cho p_quantity don vi cua MOT dong hoa don
--     Tra bao nhieu phan cua dong thi hoan lai dung bay nhieu phan tien cua dong
--     (giam gia cua dong cung duoc chia theo ty le do).
--     DAY LA BAN DUY NHAT CUA CONG THUC NAY -- xem plan/12 muc 3.1.
--     sp_get_invoice_returns va sp_report_big_returns deu goi vao day.
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_return_refund(
    p_invoice_id  VARCHAR(30),
    p_line_number INT,
    p_quantity    DECIMAL(10,3)
)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_refund DECIMAL(12,2) DEFAULT NULL;

    SELECT ROUND(p_quantity / l.Quantity
                 * (l.Quantity * l.Unit_Price - l.Discount), 2)
    INTO   v_refund
    FROM   Invoice_Line l
    WHERE  l.Invoice_ID  = p_invoice_id
      AND  l.Line_Number = p_line_number;

    RETURN COALESCE(v_refund, 0.00);
END$$
```

`COALESCE` lo trường hợp không tìm thấy dòng: trả `0` thay vì `NULL`, để `SUM()` ở báo cáo không
bị `NULL` hoá cả nhóm.

### 4.4. Mới: `fn_next_return_id` — số thứ tự lượt trả TRONG MỘT DÒNG

```sql
-- ---------------------------------------------------------------------
-- 1.5 fn_next_return_id: So thu tu lan tra ke tiep CUA MOT DONG hoa don
--     Khoa chinh bang `Return` la (Invoice_ID, Line_Number, Return_ID) nen
--     Return_ID chi can duy nhat trong pham vi mot dong -- dung nhu seed dang
--     danh so (moi dong deu bat dau tu 1). AUTO_INCREMENT khong lam duoc viec nay.
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_next_return_id(
    p_invoice_id  VARCHAR(30),
    p_line_number INT
)
RETURNS INT
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_max INT DEFAULT 0;

    SELECT COALESCE(MAX(Return_ID), 0)
    INTO   v_max
    FROM   `Return`
    WHERE  Invoice_ID  = p_invoice_id
      AND  Line_Number = p_line_number;

    RETURN v_max + 1;
END$$
```

`MAX + 1` an toàn **vì** `sp_add_return` chỉ gọi hàm này **sau** khi đã khoá dòng `Invoice_Line`
bằng `FOR UPDATE` (mục 4.6): mọi lượt trả khác cho cùng dòng đó phải chờ ở cùng cái khoá ấy. Đây là
lý do thứ hai để khoá dòng, cạnh lý do kiểm số lượng ở mục 3.4.

### 4.5. Mới: `sp_get_returnable_lines` — các dòng còn trả được

Đặt vào **PHẦN 3** (cạnh `sp_get_invoice_returns`, khoảng dòng 323). Đây là câu đọc mà hộp thoại
cần: nó trả về **số còn lại**, thứ mà code hiện tại không hề biết (lỗi #8).

```sql
-- ---------------------------------------------------------------------
-- 3.6 sp_get_returnable_lines: Cac dong cua hoa don kem SO LUONG CON TRA DUOC
--     Ten cot la hop dong voi Java (plan/12 muc 5.6) -- doi ten phai sua ca hai ben.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_get_returnable_lines(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    SELECT  l.Line_Number                                      AS Dong,
            p.Name                                             AS San_pham,
            p.Unit                                             AS DVT,
            l.Quantity                                         AS SL_mua,
            COALESCE(SUM(r.Return_Quantity), 0)                AS SL_da_tra,
            l.Quantity - COALESCE(SUM(r.Return_Quantity), 0)   AS SL_con_lai,
            l.Unit_Price                                       AS Don_gia,
            l.Discount                                         AS Giam_gia,
            (l.Quantity * l.Unit_Price - l.Discount)           AS Thanh_tien
    FROM    Invoice_Line l
    JOIN    Product      p ON p.Barcode = l.Barcode
    LEFT JOIN `Return`   r ON r.Invoice_ID  = l.Invoice_ID
                          AND r.Line_Number = l.Line_Number
    WHERE   l.Invoice_ID = p_invoice_id
    GROUP BY l.Line_Number, p.Name, p.Unit,
             l.Quantity, l.Unit_Price, l.Discount
    ORDER BY l.Line_Number;
END$$
```

`LEFT JOIN` (không phải `JOIN`) để dòng chưa bị trả lần nào vẫn hiện ra với `SL_da_tra = 0`.

### 4.6. Mới: `sp_add_return` — thủ tục còn thiếu

Đặt vào **PHẦN 5** (sau `sp_add_payment`, khoảng dòng 516):

```sql
-- ---------------------------------------------------------------------
-- 5.4 sp_add_return: Ghi MOT luot tra hang cho mot dong hoa don
--     Java quan ly ranh gioi transaction (InvoiceDao.processReturn goi thu tuc
--     nay N lan tren cung mot Connection) -- dung nhu sp_add_invoice_line.
--     Thu tuc nay la CHOT CUOI cua cac quy tac nghiep vu: plan/12 muc 3.4.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_add_return(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT,
    IN p_return_date DATE,
    IN p_quantity    DECIMAL(10,3),
    IN p_approved_by VARCHAR(10)
)
BEGIN
    DECLARE v_line_qty  DECIMAL(10,3) DEFAULT NULL;
    DECLARE v_status    VARCHAR(10)   DEFAULT NULL;
    DECLARE v_returned  DECIMAL(10,3) DEFAULT 0;
    DECLARE v_remaining DECIMAL(10,3) DEFAULT 0;
    DECLARE v_return_id INT           DEFAULT 1;
    DECLARE v_msg       VARCHAR(255);

    IF p_quantity IS NULL OR p_quantity <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'So luong tra phai lon hon 0';
    END IF;

    -- Khoa dong hoa don goc trong suot transaction. Hai muc dich:
    --   1. hai cua so app khong the cung di qua phep kiem "con lai" ben duoi
    --   2. fn_next_return_id (MAX + 1) nho the moi an toan
    SELECT l.Quantity, i.Status
    INTO   v_line_qty, v_status
    FROM   Invoice_Line l
    JOIN   Invoice      i ON i.Invoice_ID = l.Invoice_ID
    WHERE  l.Invoice_ID  = p_invoice_id
      AND  l.Line_Number = p_line_number
    FOR UPDATE;

    IF v_line_qty IS NULL THEN
        SET v_msg = CONCAT('Khong tim thay dong ', p_line_number,
                           ' cua hoa don ', p_invoice_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    IF v_status = 'VOIDED' THEN
        SET v_msg = CONCAT('Hoa don ', p_invoice_id,
                           ' da bi huy (VOIDED), khong tra hang duoc');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SELECT COALESCE(SUM(Return_Quantity), 0)
    INTO   v_returned
    FROM   `Return`
    WHERE  Invoice_ID  = p_invoice_id
      AND  Line_Number = p_line_number;

    SET v_remaining = v_line_qty - v_returned;

    IF p_quantity > v_remaining THEN
        SET v_msg = CONCAT('Dong ', p_line_number, ': chi con ', v_remaining,
                           ' co the tra (da mua ', v_line_qty,
                           ', da tra ', v_returned, '), khong tra duoc ', p_quantity);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_msg;
    END IF;

    SET v_return_id = fn_next_return_id(p_invoice_id, p_line_number);

    INSERT INTO `Return` (Invoice_ID, Line_Number, Return_ID,
                          Return_Date, Return_Quantity, Approved_By)
    VALUES (p_invoice_id, p_line_number, v_return_id,
            p_return_date, p_quantity, p_approved_by);
END$$
```

Ghi chú:

- **5 tham số, không 7.** Mất `Return_ID` (SQL tự sinh) và mất tiền hoàn (giá trị dẫn xuất) so với
  câu gọi hiện tại ở `InvoiceDao.java:360`.
- `p_return_date` là tham số chứ không phải `CURDATE()` bên trong: giữ nguyên quy ước "Java sở hữu
  cái đồng hồ" như `createInvoice` đang truyền `date`/`time`.
- `SQLSTATE '45000'` là mã chuẩn cho lỗi do người viết chủ động phát. JDBC biến nó thành
  `SQLException` có `getSQLState() = "45000"` → Java phân biệt được **lỗi nghiệp vụ** (hiện cảnh
  báo gọn) với **lỗi thật** (hiện `Ui.error`) — mục 5.7.
- Thông điệp viết **không dấu**, đúng kiểu mọi chuỗi hiện ra trên giao diện của app này.

### 4.7. Sửa 3 thủ tục đang có

**a) `sp_get_invoice_returns` (`:304-322`)** — thêm cột `Lan_tra`, công thức gọi vào hàm:

```sql
    SELECT  r.Line_Number   AS Dong,
            r.Return_ID     AS Lan_tra,
            pr.Name         AS San_pham,
            r.Return_Date   AS Ngay_tra,
            r.Return_Quantity AS SL_tra,
            fn_return_refund(r.Invoice_ID, r.Line_Number, r.Return_Quantity) AS Tien_hoan,
            c.Name          AS Nguoi_duyet
    FROM    `Return` r
    JOIN    Invoice_Line l ON l.Invoice_ID  = r.Invoice_ID
                          AND l.Line_Number = r.Line_Number
    JOIN    Product pr ON pr.Barcode   = l.Barcode
    JOIN    Cashier c  ON c.Cashier_ID = r.Approved_By
    WHERE   r.Invoice_ID = p_invoice_id
    ORDER BY r.Line_Number, r.Return_ID;
```

Giá trị `Tien_hoan` **không đổi một đồng** — chỉ đổi chỗ chứa công thức. Đây là phép kiểm chứng
được: so cột `Tien_hoan` trước và sau khi sửa trên 3 lượt trả của seed (kịch bản #24).

**b) `sp_report_big_returns` (`:400-421`)** — bản chép thứ hai của công thức, ở dòng 413-414:

```sql
    JOIN   (SELECT r.Invoice_ID,
                   SUM(fn_return_refund(r.Invoice_ID, r.Line_Number, r.Return_Quantity)) AS Tien_tra
            FROM   `Return` r
            GROUP BY r.Invoice_ID) tra ON tra.Invoice_ID = i.Invoice_ID
```

`JOIN Invoice_Line` trong bảng dẫn xuất đó **bỏ được** vì hàm đã tự tra `Invoice_Line`.

**c) `sp_authenticate_account` (`:525-534`)** — trả thêm `Cashier_ID`:

```sql
    SELECT Username, Role, Full_Name, Cashier_ID
    FROM   Account
    WHERE  Username = p_username
      AND  Password = p_password;
```

### 4.8. Phần C (tuỳ chọn): `sp_delete_return`

```sql
-- ---------------------------------------------------------------------
-- 5.6 sp_delete_return: Huy MOT luot tra hang da ghi
--     Hien tai mot luot tra ghi sai chi co the sua bang cach xoa ca hoa don.
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_delete_return(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT,
    IN p_return_id   INT
)
BEGIN
    DELETE FROM `Return`
    WHERE Invoice_ID  = p_invoice_id
      AND Line_Number = p_line_number
      AND Return_ID   = p_return_id;
END$$
```

Không cần `SIGNAL`: xoá không có gì thì `DELETE` trả 0 dòng, Java báo lại. Và **không** đánh lại số
`Return_ID` của các lượt còn lại — số thứ tự là danh tính, không phải vị trí (đúng bài học mà lỗi
#10 dạy cho ta).

---

## 5. Thiết Kế Java

### 5.1. Bảng tổng hợp

| File | Trạng thái | Việc |
|---|---|---|
| `sql/01_schema.sql` | sửa | Thêm cột + khoá ngoại cho `Account` (mục 4.1) |
| `sql/02_seed.sql` | sửa | Thêm `Cashier_ID` vào 2 dòng `Account` (mục 4.1) |
| `sql/04_routines.sql` | sửa | 5 `DROP` mới, 2 hàm mới, 2 thủ tục mới, sửa 3 thủ tục cũ (mục 4.2–4.7) |
| `src/billing/model/Account.java` | sửa | Thêm `cashierId` + `getCashierId()`; hàm dựng thành 4 tham số |
| `src/billing/dao/AccountDao.java` | sửa | Đọc `Cashier_ID` **có kiểm tra cột tồn tại** (mục 5.2) |
| `src/billing/dao/InvoiceDao.java` | sửa | `ReturnItem` đổi kiểu; `processReturn` viết lại; thêm `returnableLines()`, `returnRefund()`; xoá đoạn comment chết `:390-397` |
| `src/billing/ui/InvoiceViewPanel.java` | sửa | Hộp thoại trả hàng dựng lại; `applyRolePermissions` + `btReturnLine`; làm mới sau khi lưu; `formatQuantity`; **xoá** `parseVNCurrency`, `InvoiceLineData`, `InvoiceLineTableModel`, `updateReturnTableSTTAndTotal`, `updateTotalRefundLabel` |
| `src/billing/ui/SearchPanel.java` | sửa | `QUANTITY` / `RETURN_QUANTITY` hiện đúng phần thập phân (mục 5.8) |
| `docs/05-SQL-JAVA-CONVENTION.md` | sửa | Thêm 4 dòng mới vào bảng mục 6 |
| `src/billing/model/UserSession.java` | **không đổi** | Đã chứa `Account`, tự có `Cashier_ID` theo |

### 5.2. `Account` + `AccountDao`

`Account` thêm một trường (hàm dựng chỉ được gọi ở **đúng một chỗ** — `AccountDao:45`, đã `grep`):

```java
private final String cashierId;   // co the null -- xem plan/12 muc 3.3

public Account(String username, String role, String fullName, String cashierId) { ... }

/** Ma nhan vien trong bang Cashier, hoac null neu tai khoan khong gan voi nhan vien nao. */
public String getCashierId() { return cashierId; }
```

`AccountDao.authenticate()` **phải đọc phòng thủ**. Lý do rất cụ thể: nếu ai đó biên dịch Java mới
mà chưa nạp `04_routines.sql`, thì `rs.getString("Cashier_ID")` ném `SQLException: Column
'Cashier_ID' not found` → **không ai đăng nhập được nữa**, kể cả để làm việc khác. Hỏng đăng nhập
vì một tính năng trả hàng là cái giá quá đắt:

```java
String cashierId = hasColumn(rs, "Cashier_ID") ? rs.getString("Cashier_ID") : null;
...
/** Kiem tra ket qua tra ve co cot nay khong -- de app con dang nhap duoc khi
 *  database chua nap lai 04_routines.sql (xem plan/12 muc 5.2). */
private static boolean hasColumn(ResultSet rs, String name) throws SQLException {
    ResultSetMetaData md = rs.getMetaData();
    for (int i = 1; i <= md.getColumnCount(); i++) {
        if (name.equalsIgnoreCase(md.getColumnLabel(i))) return true;
    }
    return false;
}
```

Khi đó `cashierId == null` → đăng nhập bình thường, chỉ nút trả hàng báo "chưa nạp lại database"
(mục 5.7). Dòng `SqlLog` giữ nguyên, mật khẩu vẫn che `******`.

### 5.3. `InvoiceDao.ReturnItem`

```java
/** Mot muc trong danh sach tra hang: dong nao, tra bao nhieu.
 *  KHONG chua tien hoan -- tien hoan do SQL tinh (plan/12 muc 2.2). */
public static class ReturnItem {
    private final int        lineNumber;
    private final String     productName;   // chi de hien thi / thong bao loi
    private final BigDecimal quantity;      // DECIMAL(10,3), khong dung double
    ...
}
```

Mất `double amountRefunded`, `int returnQuantity` thành `BigDecimal quantity`. Hai thay đổi này là
nghiệp vụ, không phải thẩm mỹ: cái thứ nhất xoá bản chép sai của công thức, cái thứ hai cho phép
trả 1,5 kg.

### 5.4. `InvoiceDao.processReturn` — ném lỗi thay vì trả `false`

```java
/**
 * Ghi mot lan tra hang gom nhieu dong, TRONG MOT TRANSACTION:
 * hoac tat ca cac dong duoc ghi, hoac khong dong nao.
 *
 * @param approvedByCashierId ma nhan vien duyet -- Cashier_ID, KHONG phai username
 * @throws RuntimeException khi SQL tu choi. Thong diep goc cua MySQL duoc giu lai,
 *         ke ca thong diep cua SIGNAL '45000' trong sp_add_return.
 */
public void processReturn(String invoiceId, List<ReturnItem> items, String approvedByCashierId) {
    String spReturn = "{call sp_add_return(?, ?, ?, ?, ?)}";
    ...
    conn.setAutoCommit(false);
    LocalDate today = LocalDate.now();            // mot moc ngay cho ca lan tra
    try (CallableStatement cs = conn.prepareCall(spReturn)) {
        for (ReturnItem it : items) {
            cs.setString(1, invoiceId);
            cs.setInt(2, it.getLineNumber());
            cs.setDate(3, Date.valueOf(today));
            cs.setBigDecimal(4, it.getQuantity());
            cs.setString(5, approvedByCashierId);
            cs.executeUpdate();
            SqlLog.add(spReturn, new Object[]{invoiceId, it.getLineNumber(),
                       today, it.getQuantity(), approvedByCashierId}, 0, 1);
        }
    }
    conn.commit();
    SqlLog.add("COMMIT;  -- luu thanh cong tra hang hoa don " + invoiceId, ...);
}
```

Bốn thay đổi so với bản hiện tại, kèm lý do:

| Thay đổi | Vì sao |
|---|---|
| `boolean` → `void`, lỗi thì **ném** | 5 hàm ghi DB khác của `InvoiceDao` (`createInvoice`, `deleteInvoice`, `updateInvoiceHeader`, `updateInvoiceLine`, `deleteInvoiceLine`) đều ném `RuntimeException`. `processReturn` là ngoại lệ duy nhất, và chính vì nó trả `false` mà nguyên nhân thật (lỗi #1) đã nằm im trong `System.err` |
| Bỏ `e.printStackTrace()` | `Ui.error` đã in stack trace (`Ui.java:72`). In hai lần không thêm thông tin |
| `prepareCall` **ngoài** vòng lặp | Chuẩn bị lại câu gọi cho từng dòng là vô ích; `sp_add_payment` ở `:137-151` đã làm đúng cách này |
| Một `LocalDate.now()` cho cả lượt trả | Bản hiện tại gọi `LocalDate.now()` trong vòng lặp (`:377`, `:385`) — bấm lúc 23:59:59.9 thì các dòng của **cùng một lượt trả** rơi vào hai ngày khác nhau |

Và **xoá** `:390-397` — khối comment về `sp_update_invoice_return_status` / `"PARTIALLY_RETURNED"`.
Trạng thái hoá đơn là của khối 4 (mục 2.3); để lại một gợi ý sai chỗ là mời khối sau làm sai.

Thêm hai hàm đọc:

```java
/** Cac dong con tra duoc cua hoa don: sp_get_returnable_lines(p_invoice_id) */
public QueryResult returnableLines(String invoiceId) {
    return QueryResult.call("{call sp_get_returnable_lines(?)}", invoiceId);
}

/** Tien hoan cho mot so luong cua mot dong: fn_return_refund(...).
 *  Java KHONG tu tinh so nay -- plan/12 muc 2.2. */
public BigDecimal returnRefund(String invoiceId, int lineNumber, BigDecimal quantity) {
    String sql = "{? = call fn_return_refund(?, ?, ?)}";          // docs/05 muc 4.1
    try (Connection c = Db.getConnection();
         CallableStatement cs = c.prepareCall(sql)) {
        cs.registerOutParameter(1, Types.DECIMAL);
        cs.setString(2, invoiceId);
        cs.setInt(3, lineNumber);
        cs.setBigDecimal(4, quantity);
        cs.execute();
        ...
    }
}
```

### 5.5. Hộp thoại trả hàng — cấu trúc mới

Giữ **bố cục** hiện tại (hai bảng cạnh nhau, `Them >>` / `<< Xoa`, tổng tiền ở dưới) vì nó dễ hiểu.
Đổi **dữ liệu nằm sau bố cục đó**.

```
┌─ Tra hang -- hoa don INV-0002 | Ngay: 2026-10-09 | Trang thai: PAID ──────────────┐
│ ┌─ Cac dong cua hoa don ────────────────┐  ┌─ Se tra lai ──────────────────────┐ │
│ │ Dong San_pham    DVT SL_mua SL_da_tra │  │ Dong San_pham     SL_tra DVT     │ │
│ │                      SL_con_lai ...   │  │                   Tien_hoan      │ │
│ │  1   Thit ba chi  KG   1,5     0   1,5│  │  1   Thit ba chi   0,5   KG      │ │
│ │  2   Trung ga hop BOX  2       0   2  │  │                       79.000đ    │ │
│ └──────────── [ Them >> ] ──────────────┘  └────────── [ << Xoa ] ─────────────┘ │
│ Tong tien phai tra lai: 79.000đ          Nguoi duyet: Tran Van An (E01)          │
│                                            [ Xac nhan tra hang ]  [ Huy ]        │
└───────────────────────────────────────────────────────────────────────────────────┘
```

**Hai lớp dữ liệu mới** (thay cho `InvoiceLineData` + `InvoiceLineTableModel`), theo đúng khuôn
`AbstractTableModel` mà file này đã dùng:

```java
/** Mot dong hoa don kem so luong con tra duoc -- doc tu sp_get_returnable_lines.
 *  Ten truong tuong ung 1-1 voi ten cot cua thu tuc (plan/12 muc 4.5). */
private static class ReturnableLine {
    final int        lineNumber;
    final String     productName, unit;
    final BigDecimal bought, returned, remaining, unitPrice, discount, lineTotal;
}

/** Mot dong trong bang "se tra lai". */
private static class ReturnRow {
    final int        lineNumber;
    final String     productName, unit;
    final BigDecimal quantity;
    final BigDecimal refund;      // do fn_return_refund tra ve, Java khong tinh
}
```

**Quy tắc đã đổi:**

| | Hiện tại | Sau khối này |
|---|---|---|
| Khoá định danh một mục | tên sản phẩm (`:834`, `:951`, `:981`) | **`lineNumber`** |
| Cột 0 của bảng phải | bị ghi đè bằng `i+1` (`:1091`) | là `lineNumber`, không ai ghi đè |
| Số lượng | `int` | `BigDecimal` |
| Giá trị trong model | chuỗi đã `formatCurrency` | **số**, renderer lo việc định dạng (6 renderer ở `:715-794` sống lại) |
| Tổng tiền | parse lại chuỗi đã format (`:1107`) | cộng `BigDecimal` từ `List<ReturnRow>` |
| Số còn trả được | không biết | cột `SL_con_lai`, trừ tiếp phần đang có trong bảng phải |
| Ngưỡng chặn khi gõ | `> SL_mua` | `> SL_con_lai − (đã chọn trong bảng phải)` |
| Cùng một dòng thêm 2 lần | chặn (nhầm sang "cùng tên sản phẩm") | chặn theo `lineNumber`, kèm gợi ý "xoá mục cũ rồi thêm lại với số lượng khác" |

Đọc số lượng từ ô nhập — đúng 3 quy tắc ở mục 3.2:

```java
/** Doc so luong nguoi dung go. Tra null (va da hien thong bao) neu khong hop le. */
private BigDecimal readQuantity(Component parent, String input, BigDecimal max, String unit) {
    String s = input.trim().replace(',', '.');          // ca , va . deu la dau thap phan
    if (s.indexOf('.') != s.lastIndexOf('.')) { warn(parent, "Chi duoc mot dau thap phan..."); return null; }
    BigDecimal q;
    try { q = new BigDecimal(s); }
    catch (NumberFormatException ex) { warn(parent, "So luong khong hop le: " + input); return null; }
    if (q.scale() > 3)              { warn(parent, "Toi da 3 chu so thap phan (cot DECIMAL(10,3))."); return null; }
    if (q.signum() <= 0)            { warn(parent, "So luong tra phai lon hon 0."); return null; }
    if (q.compareTo(max) > 0)       { warn(parent, "Chi con " + formatQuantity(max) + " " + unit + " co the tra."); return null; }
    return q;
}
```

So sánh bằng `compareTo`, **không** `equals` — `BigDecimal("1.5").equals(new BigDecimal("1.50"))` là
`false` (khác `scale`), một cái bẫy kinh điển sẽ làm phép kiểm "còn lại" sai ngay ở biên.

**Dữ liệu từ DB không đi qua `double`.** Thay `parseVNCurrency` (xoá hẳn, 47 dòng) bằng:

```java
/** Doi gia tri tho tu JDBC thanh BigDecimal. Cot DECIMAL cua MySQL ve Java
 *  von la BigDecimal roi (docs/05 muc 3) -- khong qua double, khong parse chuoi. */
private static BigDecimal dec(Object o) {
    if (o == null)                 return BigDecimal.ZERO;
    if (o instanceof BigDecimal)   return (BigDecimal) o;
    if (o instanceof Number)       return new BigDecimal(o.toString());
    return new BigDecimal(String.valueOf(o).trim());
}
```

### 5.6. Luồng bấm nút, và chỗ nào chạy ngoài EDT

| Bước | Chạy ở đâu | Vì sao |
|---|---|---|
| Mở hộp thoại: `invoiceHeader` + `returnableLines` | **EDT** | Hai câu `SELECT` nhỏ. Cả app đang đọc DB trên EDT (`onSelect:449-483`); đổi riêng chỗ này là tạo ra sự không nhất quán mà không giải quyết vấn đề gì |
| `Them >>`: một lần gọi `fn_return_refund` | **EDT** | Một hàm vô hướng, vài ms |
| `Xac nhan`: `processReturn` (transaction N `INSERT`) | **`SwingWorker`** | Đây là thao tác **ghi**, lâu nhất, và là thao tác mà nếu MySQL treo thì hộp thoại **modal** sẽ đứng hình không vẽ lại được — đúng vấn đề `plan/11` mục 4.2 |
| Làm mới tab 3 sau khi lưu | EDT, trong `done()` | Chỉ là `SELECT` |

Ghi đúng nguyên tắc này vào javadoc của hàm, để khối sau không "dọn dẹp" theo một trong hai hướng
ngược nhau.

Trong lúc `SwingWorker` chạy: khoá `btnConfirm` + `btnAdd` + `btnRemove` (cùng một lý do như
`setBusy` của `plan/11` mục 4.2 — bấm 5 lần không được xếp hàng 5 transaction).

Sau khi `done()` thành công, **phải thấy kết quả** (lỗi #12). Tách phần thân của `onSelect` ra:

```java
/** Ve lai 4 khung chi tiet cho mot hoa don. Tach ra tu onSelect de goi duoc
 *  tu cho khac ma khong phu thuoc vao dong dang duoc chon trong tbInvoices. */
private void showDetail(String invoiceId) { ... }   // phan than cu cua onSelect:449-487

private void onSelect(ListSelectionEvent e) {
    if (e != null && e.getValueIsAdjusting()) return;
    String id = getSelectedInvoiceId();
    if (id == null) return;
    showDetail(id);
}
```

→ sau khi lưu: `reload(); showDetail(invoiceId);` và chọn lại đúng hàng hoá đơn đó trong
`tbInvoices` nếu tìm thấy. Không còn phụ thuộc vào cái selection mà `reload()` vừa xoá.

### 5.7. Phân quyền và người duyệt

**a) `applyRolePermissions()` (`:401-425`)** — thêm `btReturnLine` vào cả ba danh sách
(`setEnabled`, tooltip khoá, tooltip mở). `plan/11` mục 4.4 đã kiểm: `MainWindow.switchRole()`
gọi lại đúng hàm này (`MainWindow:260`), nên **đổi vai trò tại chỗ cũng tự đúng**, không phải sửa gì
ở khối 2.

Lưu ý khi ghi vào mục 9: thay đổi này **lấy đi** một khả năng của thu ngân. Nhưng khả năng đó chưa
bao giờ hoạt động (lỗi #1), nên không ai mất gì đang dùng được.

**b) Lấy `Cashier_ID` của người duyệt** — thay cho `:1025-1029`:

```java
Account user = UserSession.getCurrentUser();
if (user == null || user.getCashierId() == null || user.getCashierId().isBlank()) {
    JOptionPane.showMessageDialog(this,
        "Tai khoan '" + (user == null ? "?" : user.getUsername()) + "' chua duoc gan voi nhan vien\n"
      + "nao trong bang Cashier, nen khong the dung lam nguoi duyet tra hang.\n\n"
      + "Cach sua: chay lai .\\scripts\\ps1\\db-setup.ps1 (xoa sach du lieu),\n"
      + "hoac chay doan ALTER/UPDATE o plan/12 muc 4.1 de giu du lieu dang co.",
        "Chua xac dinh duoc nguoi duyet", JOptionPane.WARNING_MESSAGE);
    return;                                  // khong mo hop thoai
}
```

Tên người duyệt hiện ngay trên hộp thoại (`Nguoi duyet: Tran Van An (E01)`) — ai duyệt phải nhìn
thấy được trước khi bấm, không chỉ sau khi lưu.

**c) Lỗi nghiệp vụ ≠ lỗi thật.** `plan/11` mục 4.1 đã chốt: `Ui.error` dành cho ngoại lệ thật.
`SIGNAL '45000'` là quyết định có chủ ý của SQL, không phải sự cố:

```java
/** Loi do SQL chu dong SIGNAL (SQLSTATE 45000) -> canh bao gon, doc duoc.
 *  Moi loi khac (mat ket noi, thieu thu tuc, sai khoa ngoai) -> Ui.error. */
private void showReturnFailure(Component parent, Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
        if (t instanceof SQLException && "45000".equals(((SQLException) t).getSQLState())) {
            JOptionPane.showMessageDialog(parent, t.getMessage(),
                "Khong tra hang duoc", JOptionPane.WARNING_MESSAGE);
            return;
        }
    }
    Ui.error(parent, ex);
}
```

Nhờ vậy câu *"Dong 1: chi con 0.000 co the tra (da mua 2.000, da tra 2.000)"* hiện ra như một lời
giải thích, chứ không như một tai nạn. Và lỗi thật vẫn giữ nguyên đường cũ với thông điệp gốc của
MySQL.

### 5.8. Hiện số lượng thập phân cho đúng

Bug này có sẵn từ trước khối 3 (`INV-0002` dòng 1 mua 1,5 kg đang hiện là "2" ở tab 3 và tab 2),
nhưng khối này **làm nó xuất hiện nhiều hơn** nên phải sửa cùng lúc.

**`InvoiceViewPanel`:**

```java
/** 1.500 -> "1,5" ; 2.000 -> "2" ; 0.250 -> "0,25"  (cot DECIMAL(10,3)) */
private static final DecimalFormat QUANTITY_FORMAT = new DecimalFormat("#,##0.###", sym);
```

`fillFormatted` thêm tham số thứ 5 `int[] quantityCols` (hàm private, 4 chỗ gọi):

| Bảng | Hiện tại | Sau khi sửa |
|---|---|---|
| `tbInvoices` (`:431`) | `({cuối}, null)` | `({cuối}, null, null)` |
| `tbLines` (`:473`) | `({4,5,6,7}, {2})` | `({4,5,6,7}, null, {2})` — `SL` sang nhóm số lượng |
| `tbPayments` (`:478`) | `({1}, null)` | `({1}, null, null)` |
| `tbReturns` (`:483`) | `({4}, {3})` | `({5}, null, {4})` — chỉ số đã dịch, xem mục 3.5 |

**`SearchPanel`:** `isQuantityColumn` (`:962-980`) đang gộp số lượng chung với các mã `_ID` rồi
format tất cả bằng `formatInteger` (`:896-898`). Bỏ `"QUANTITY"` và `"RETURN_QUANTITY"` khỏi hàm
đó, và chèn trước nó:

```java
// So luong la DECIMAL(10,3) -- khong phai so nguyen (plan/12 muc 3.2)
if (upper.equals("QUANTITY") || upper.equals("RETURN_QUANTITY")) {
    return formatQuantity(value);
}
```

---

## 6. Danh Sách Công Việc (theo thứ tự thực hiện)

### Phần A — SQL (làm trước, một mình cũng kiểm thử được)

1. `sql/01_schema.sql`: thêm `Cashier_ID` + `fk_account_cashier` vào bảng `Account` (mục 4.1).
2. `sql/02_seed.sql`: thêm cột vào `INSERT INTO Account` (mục 4.1).
3. `sql/04_routines.sql`: thêm 5 dòng `DROP` vào đúng nhóm (mục 4.2). **Làm bước này trước**, vì
   quên nó thì lần nạp lại thứ hai báo lỗi và rất dễ tưởng là lỗi của thủ tục mới.
4. `sql/04_routines.sql`: `fn_return_refund` (mục 4.3), `fn_next_return_id` (mục 4.4).
5. `sql/04_routines.sql`: `sp_get_returnable_lines` (mục 4.5), `sp_add_return` (mục 4.6).
6. `sql/04_routines.sql`: sửa `sp_get_invoice_returns`, `sp_report_big_returns`,
   `sp_authenticate_account` (mục 4.7).
7. Nạp vào database: `db-setup.ps1` (mất dữ liệu) **hoặc** `ALTER` + nạp riêng `04_routines.sql`
   (mục 4.1). Mã thoát phải là 0, không `Warning` nào.
8. Chạy kịch bản mục 7.1 **bằng `mysql` CLI trước khi chạm vào Java.** Trả hàng chạy được bằng
   `CALL sp_add_return(...)` là xong một nửa khối này, và nửa đó độc lập hoàn toàn với Swing.

### Phần B — Java

9. `src/billing/model/Account.java`: thêm `cashierId` (mục 5.2).
10. `src/billing/dao/AccountDao.java`: `hasColumn` + đọc `Cashier_ID` (mục 5.2).
11. `src/billing/dao/InvoiceDao.java`: `ReturnItem` (mục 5.3); `processReturn` viết lại và **xoá**
    `:390-397`; thêm `returnableLines()`, `returnRefund()` (mục 5.4).
12. `src/billing/ui/InvoiceViewPanel.java`: `QUANTITY_FORMAT`, `formatQuantity`, `dec()`;
    `fillFormatted` thêm tham số và sửa 4 chỗ gọi; **xoá** `parseVNCurrency` (mục 5.8, 5.5).
13. `src/billing/ui/InvoiceViewPanel.java`: `ReturnableLine`, `ReturnRow` và 2 table model mới;
    **xoá** `InvoiceLineData`, `InvoiceLineTableModel` (mục 5.5).
14. `src/billing/ui/InvoiceViewPanel.java`: `onReturnLine()` viết lại — chặn `VOIDED`, kiểm người
    duyệt, `readQuantity`, `SwingWorker`, `showReturnFailure`; **xoá**
    `updateReturnTableSTTAndTotal`, `updateTotalRefundLabel` (mục 5.5–5.7).
15. `src/billing/ui/InvoiceViewPanel.java`: tách `showDetail(invoiceId)` khỏi `onSelect`; thêm
    `btReturnLine` vào `applyRolePermissions()` (mục 5.6, 5.7).
16. `src/billing/ui/SearchPanel.java`: số lượng hiện phần thập phân (mục 5.8).
17. Kiểm kích thước hộp thoại: đặt `setMinimumSize` và so `getSize()` với `getPreferredSize()`
    **bằng số**, ở 100% và 125% DPI. Bảng bên trái giờ có 9 cột — `plan/09` mục 7.1 ghi 6/8 lỗi của
    tab Git là lỗi kích thước, và `plan/11` rủi ro #4 nhắc lại: "nhìn thấy ổn" đã sai một lần rồi.
18. Biên dịch: `javac -encoding UTF-8 -d out -cp "lib\*"` trên `src/**/*.java` trừ `src/envsetup`
    (giống `run.ps1` mục 4). Phải sạch, **không thêm cảnh báo mới nào** — chú ý `BigDecimal` dễ
    sinh cảnh báo kiểu nếu còn sót `double` ở đâu đó.
19. `docs/05-SQL-JAVA-CONVENTION.md` mục 6: thêm 4 dòng (`fn_return_refund`, `fn_next_return_id`,
    `sp_get_returnable_lines`, `sp_add_return`). *(Bảng đó đang thiếu cả 5 thủ tục mà `plan/06` đã
    thêm — bổ sung luôn thì tốt, nhưng đó là việc tuỳ chọn, không thuộc khối này.)*
20. Chạy hết kịch bản mục 7, ghi kết quả vào mục 9.

### Phần C — tuỳ chọn: huỷ một lượt trả

Làm sau khi A + B đã xong và kiểm thử sạch. Giá trị: một lượt trả ghi sai hiện chỉ sửa được bằng
cách xoá cả hoá đơn.

21. `sql/04_routines.sql`: `sp_delete_return` + dòng `DROP` của nó (mục 4.8).
22. `src/billing/dao/InvoiceDao.java`: `deleteReturn(invoiceId, lineNumber, returnId)`.
23. `src/billing/ui/InvoiceViewPanel.java`: nút `Huy luot tra` dưới bảng "Tra hang (Return)", chỉ
    `manager`, có hộp thoại xác nhận nêu rõ dòng / lần trả / số lượng / tiền hoàn sẽ bị huỷ. Lấy
    `Dong` và `Lan_tra` từ cột 0 và cột 1 của `tbReturns` (hai cột này tồn tại được là nhờ mục 3.5).

---

## 7. Kịch Bản Kiểm Thử

Dùng dữ liệu seed thật. Các con số dưới đây đã tính sẵn để so trực tiếp.

### 7.1. Phần A — chỉ bằng `mysql` CLI, chưa cần Java

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | `CALL sp_get_returnable_lines('INV-0003');` | 2 dòng. Dòng 2 (Ca phe G7): `SL_mua=2.000`, `SL_da_tra=1.000`, `SL_con_lai=1.000`. Dòng 1: `SL_da_tra=0.000` |
| 2 | `CALL sp_get_returnable_lines('INV-0013');` | Dòng 1: `SL_mua=2.000`, `SL_da_tra=2.000`, **`SL_con_lai=0.000`** |
| 3 | `SELECT fn_return_refund('INV-0001', 1, 1);` | **29700.00** (= `1/2 × 59.400`). Nếu ra `26400` là ai đó lại chép công thức sai của lỗi #5 |
| 4 | `SELECT fn_return_refund('INV-0001', 1, 2);` | **59400.00** — trả cả dòng thì hoàn đúng `Thanh_tien` của dòng |
| 5 | `SELECT fn_return_refund('INV-0002', 1, 0.5);` | **79000.00** (= `0,5/1,5 × 237.000`) |
| 6 | `SELECT fn_next_return_id('INV-0003', 2);` → `CALL sp_add_return('INV-0003', 2, CURDATE(), 1.000, 'E01');` → gọi lại hàm | `2` trước, `3` sau. Số thứ tự đi theo **từng dòng**, không toàn cục |
| 7 | `CALL sp_add_return('INV-0013', 1, CURDATE(), 1.000, 'E01');` | Lỗi `45000`: *"Dong 1: chi con 0.000 co the tra (da mua 2.000, da tra 2.000)..."*. **Không** dòng nào được ghi |
| 8 | `CALL sp_add_return('INV-0002', 1, CURDATE(), 1.600, 'E01');` | Lỗi `45000` — 1,6 > 1,5 |
| 9 | `CALL sp_add_return('INV-0002', 1, CURDATE(), 0, 'E01');` và `-1` | Lỗi `45000`: *"So luong tra phai lon hon 0"* |
| 10 | `CALL sp_add_return('INV-0002', 99, CURDATE(), 1, 'E01');` | Lỗi `45000`: không tìm thấy dòng 99 |
| 11 | `CALL sp_add_return('INV-0015', 1, CURDATE(), 1.000, 'E01');` (`INV-0015` là hoá đơn `VOIDED`) | Lỗi `45000`: *"...da bi huy (VOIDED), khong tra hang duoc"* |
| 12 | `CALL sp_add_return('INV-0002', 1, CURDATE(), 1.000, 'manager');` | Lỗi **1452** khoá ngoại — chứng minh `Approved_By` chỉ nhận `Cashier_ID`, tức lỗi #4 là thật |
| 13 | Chạy lại toàn bộ `04_routines.sql` lần thứ hai | Thành công, không lỗi "already exists" (phép kiểm cho bước 3 của mục 6) |
| 14 | `SELECT Username, Role, Cashier_ID FROM Account;` | `manager`→`E01`, `cashier`→`E02` |

### 7.2. Đường cơ bản trên giao diện

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 15 | Đăng nhập `manager`, tab 3, chọn `INV-0001`, bấm `Tra hang` | Hộp thoại mở, tiêu đề có mã hoá đơn + trạng thái, dòng `Nguoi duyet: Tran Van An (E01)` |
| 16 | Bảng bên trái, dòng 1 (Sua tuoi Vinamilk) | `SL_mua=2`, `SL_da_tra=0`, `SL_con_lai=2`, `Don_gia=33.000đ`, `Giam_gia=6.600đ`, **`Thanh_tien=59.400đ`** (không phải `26.400đ` của lỗi #6) |
| 17 | Chọn dòng 1, `Them >>`, ô nhập | Điền sẵn `2` (= số còn lại). Gõ `1`, Enter |
| 18 | Bảng bên phải và nhãn tổng | `Tien_hoan = 29.700đ`; tổng `29.700đ`. **Không phải `26.400đ`** |
| 19 | `Xac nhan tra hang` → `Yes` | Hộp thoại xác nhận nêu `29.700đ`; lưu xong, hộp thoại đóng |
| 20 | Nhìn tab 3 **ngay**, không bấm gì thêm | Bảng "Tra hang (Return)" có hàng mới: `Dong=1`, `Lan_tra=1`, `SL_tra=1`, `Tien_hoan=29.700đ`, `Nguoi_duyet=Tran Van An`. **Đây là phép kiểm cho lỗi #12** |
| 21 | Con số ở #19 và ở #20 | **Bằng nhau.** Đây là lý do tồn tại của nguyên tắc mục 2.2 |
| 22 | Trả hàng lần nữa trên `INV-0001` dòng 1 | `SL_con_lai` giờ là `1`; ô nhập điền sẵn `1`; gõ `2` thì bị chặn ngay khi gõ |
| 23 | Sau khi trả nốt 1 nữa, mở lại hộp thoại | Dòng 1 có `SL_con_lai=0`, không thêm được vào bảng phải, thông báo nói rõ đã trả hết |
| 24 | Trước và sau khi sửa `sp_get_invoice_returns`: so cột `Tien_hoan` của `INV-0003`, `INV-0013`, `INV-0014` | **Giống nhau từng đồng.** Chuyển công thức vào `fn_return_refund` không được làm đổi số nào đang có |

### 7.3. Số lượng thập phân

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 25 | `INV-0002`, bảng bên trái, dòng 1 (Thit ba chi, DVT = KG) | `SL_mua` hiện **`1,5`** — không phải `2` (lỗi #7) |
| 26 | Them >>, gõ `0,5` | Nhận. `Tien_hoan = 79.000đ` |
| 27 | Gõ `0.5` (dấu chấm) | Nhận, cùng kết quả — cả hai dấu đều là dấu thập phân (mục 3.2) |
| 28 | Gõ `1,6` | Chặn: *"Chi con 1,5 KG co the tra."* |
| 29 | Gõ `0,1234` | Chặn: *"Toi da 3 chu so thap phan."* — không âm thầm làm tròn |
| 30 | Gõ `1.2.3` | Chặn: *"Chi duoc mot dau thap phan."* |
| 31 | Gõ `abc`, để trống, bấm Cancel | Không exception, không thêm hàng nào |
| 32 | Lưu 0,5 kg rồi xem bảng "Tra hang (Return)" ở tab 3 | `SL_tra` hiện `0,5` — không phải `0` hay `1` |
| 33 | Tab 2 (Tra cứu dữ liệu), bảng `Return` và `Invoice_Line` | `Return_Quantity` và `Quantity` hiện `0,5` / `1,5` — không phải `1` / `2` (mục 5.8) |

### 7.4. Sai dòng hoá đơn (lỗi #9 và #10)

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 34 | Dựng hoá đơn có trùng sản phẩm: tab 3 → `INV-0001` → chọn dòng 2 → `Sua dong hang` → đổi `Barcode` thành `8934001` → Lưu | `INV-0001` giờ có "Sua tuoi Vinamilk 1L" ở **cả dòng 1 và dòng 2** |
| 35 | Mở `Tra hang`, thêm **dòng 1**, rồi thêm **dòng 2** | **Cả hai vào được** bảng bên phải, hai hàng riêng với `Dong=1` và `Dong=2`. (Code cũ chặn ở câu *"San pham da co trong danh sach tra hang!"*) |
| 36 | Số lượng và tiền hoàn của hai hàng đó | Tính theo **đúng dòng của mình** — dòng 1 có `Discount=6.600`, dòng 2 có `Discount=0` nên hai số tiền khác nhau |
| 37 | Lưu, rồi xem bảng "Tra hang (Return)" | Hai hàng, `Dong=1` và `Dong=2`. **Lượt trả nằm đúng dòng.** Code cũ ghi cả hai vào dòng 2 (lỗi #9) |
| 38 | Thêm 3 mục, xoá mục giữa, xem cột `Dong` của bảng phải | Vẫn là số dòng thật, **không** bị đánh lại thành 1, 2 (lỗi #10) |

### 7.5. Phân quyền & người duyệt

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 39 | Đăng nhập `cashier`, tab 3 | Nút `Tra hang` **mờ**, tooltip giải thích — giống 5 nút ghi DB còn lại |
| 40 | Bấm `Doi vai tro` → `manager` (khối 2) | Nút `Tra hang` **sáng lên ngay**, không phải mở lại cửa sổ |
| 41 | Đổi về `cashier` | Mờ lại. Không phải sửa `switchRole()` — `plan/11` mục 4.4 |
| 42 | `UPDATE Account SET Cashier_ID = NULL WHERE Username='manager';` rồi bấm `Tra hang` | Hộp thoại cảnh báo nêu rõ cách sửa (mục 5.7b), **hộp thoại trả hàng không mở**, app không chết |
| 43 | Trả lại `Cashier_ID='E01'`, đăng nhập lại, lưu một lượt trả, xem cột `Nguoi_duyet` | `Tran Van An` — tên người đang đăng nhập, không phải chuỗi `manager` |
| 44 | Hoá đơn `VOIDED` (`INV-0015`) → `Tra hang` | Thông báo hoá đơn đã huỷ, không mở hộp thoại. (Phép kiểm cho chốt SQL là #11) |

### 7.6. Kiểm thử âm (bắt buộc — tất cả phải "app vẫn chạy")

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 45 | Biên dịch Java mới nhưng **chưa** nạp `04_routines.sql`, rồi đăng nhập | **Vẫn đăng nhập được** (mục 5.2). Bấm `Tra hang` → cảnh báo chưa gán nhân viên. Không có `Column 'Cashier_ID' not found` làm chết màn hình đăng nhập |
| 46 | Nạp `01_schema` + `02_seed` nhưng **không** nạp `04_routines`, bấm `Tra hang` rồi xác nhận | Hộp thoại lỗi hiện **thông điệp gốc của MySQL** (`sp_add_return does not exist`) qua `Ui.error`, không phải câu "that bai, thu lai sau" |
| 47 | Tắt MySQL rồi bấm `Xac nhan tra hang` | Hộp thoại **vẫn vẽ lại được** (không đứng hình), các nút bị khoá trong lúc chờ, hết chờ thì `Ui.error` hiện lỗi kết nối và mở lại nút |
| 48 | Bấm `Xac nhan` 5 lần thật nhanh | Chỉ **một** transaction chạy; bảng `` `Return` `` chỉ có một bộ hàng mới |
| 49 | Hộp thoại không có mục nào, bấm `Xac nhan` | Thông báo "khong co san pham nao de tra", không gọi DB |
| 50 | Hai cửa sổ app cùng mở, cùng hoá đơn, cùng dòng còn 2, cả hai trả 2 | Cửa sổ thứ nhất thành công; cửa sổ thứ hai nhận cảnh báo `45000` "chỉ còn 0". **Tổng số đã trả không vượt số đã mua** (mục 3.4) |
| 51 | `SELECT SUM(Return_Quantity) ... GROUP BY Invoice_ID, Line_Number` so với `Invoice_Line.Quantity`, sau khi chạy hết các kịch bản trên | **Không dòng nào** có tổng trả > số mua. Đây là bất biến của cả khối này |
| 52 | Xoá một hoá đơn đã có lượt trả (`Xoa hoa don`) | Vẫn xoá sạch như cũ — `sp_delete_invoice:551` xoá `` `Return` `` trước. Khối này không chạm vào đường đó |
| 53 | Xoá một **dòng hàng** đã có lượt trả | `sp_delete_invoice_line:633` xoá lượt trả của dòng đó trước. Không lỗi khoá ngoại |

### 7.7. Giao điểm với các khối khác

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 54 | Lưu một lượt trả 2 dòng, mở tab 5 của Setup App (khối 1) | **2** dòng `{call sp_add_return(?,?,?,?,?)}` kèm tham số, rồi **1** dòng `COMMIT; -- luu thanh cong tra hang hoa don ...`. Mỗi câu đúng một dòng, lọc theo `sp_add_return` ra đủ |
| 55 | Thử một lượt trả bị `45000` chặn, xem tab 5 | Có dòng `sp_add_return` của lần thử, rồi dòng `ROLLBACK;`. Không dòng `COMMIT` |
| 56 | Tab 4 (Báo cáo) → `5. Hoa don tra hang > 50%` | Vẫn chạy. `INV-0013` (100%) và `INV-0014` (~75%) vẫn có mặt với **đúng số tiền như trước** khi sửa `sp_report_big_returns` |
| 57 | Trả thêm 1 Coca của `INV-0014` dòng 2 | `Tien_tra_lai` tăng đúng `18.900` (= `1/3 × 56.700`), `Phan_tram_tra` lên ~83,7% |
| 58 | Trả hàng, rồi đổi vai trò (khối 2), rồi trả hàng tiếp | Lượt thứ hai ghi `Nguoi_duyet` của **tài khoản mới** — `UserSession` được đọc tại thời điểm bấm, đúng như `plan/11` mục 4.4 đã ghi |
| 59 | Giỏ hàng ở tab 1 có 3 dòng → sang tab 3 trả hàng → về tab 1 | Giỏ hàng còn nguyên. Hộp thoại trả hàng không dựng lại `MainWindow` |

---

## 8. Rủi Ro & Cách Giảm

| # | Rủi ro | Mức | Cách giảm |
|---|---|---|---|
| 1 | **Biên dịch Java mới mà quên nạp SQL** → không ai đăng nhập được, cả app vô dụng | Cao nếu không xử lý | `hasColumn()` ở mục 5.2: thiếu cột thì `cashierId = null`, đăng nhập vẫn chạy, chỉ nút trả hàng báo lỗi kèm cách sửa. Kịch bản #45 là phép kiểm bắt buộc |
| 2 | `db-setup.ps1` **xoá sạch database** — mất hoá đơn đã tạo trong lúc học | Trung bình | Mục 4.1 cho hai đường, đường `ALTER` giữ nguyên dữ liệu. Cảnh báo đã có ở đầu `db-setup.ps1:4-6`, mục 6 bước 7 nhắc lại |
| 3 | Sửa `sp_get_invoice_returns` / `sp_report_big_returns` làm **đổi số tiền** đang có | Trung bình | Kịch bản #24 và #56 so trực tiếp trước/sau trên 3 lượt trả của seed. `fn_return_refund` là bản sao từng ký tự của biểu thức cũ, không phải công thức "cải tiến" |
| 4 | Đổi chỉ số cột của `sp_get_invoice_returns` làm **lệch cột định dạng** ở tab 3 | Trung bình | Bảng ánh xạ chỉ số ở mục 3.5 + mục 5.8. Đây đúng là cái mà `docs/05` "Quy tắc 1" cảnh báo; triệu chứng là `Tien_hoan` mất chữ `đ` hoặc `Ngay_tra` bị format thành tiền — nhìn một lần là thấy |
| 5 | Nút `Tra hang` bị khoá với thu ngân → bị coi là "mất tính năng" | Thấp | Tính năng đó **chưa bao giờ hoạt động** (lỗi #1), nên không ai mất gì đang dùng được. Ghi rõ câu này vào mục 9 và vào commit message |
| 6 | `BigDecimal` so sánh bằng `equals` thay vì `compareTo` → phép kiểm "còn lại" sai ở biên | Trung bình | Ghi rõ ở mục 5.5. Kịch bản #22, #23, #28 chạy đúng vào biên (`1.0` so với `1.000`) |
| 7 | Hộp thoại 9 cột bên trái → **tràn box** ở cửa sổ nhỏ hoặc 125% DPI | Trung bình | `plan/09` mục 7.1: 6/8 lỗi của tab Git là lỗi kích thước; `plan/11` rủi ro #4 nhắc lại rằng "nhìn thấy ổn" đã sai một lần. Mục 6 bước 17: đo bằng số, không bằng mắt |
| 8 | `SELECT ... FOR UPDATE` giữ khoá lâu nếu ai đó để hộp thoại mở | Thấp | Khoá chỉ sống trong transaction của `processReturn` — mở **sau** khi bấm Xác nhận và đóng ở `commit`, tính bằng milli giây. Hộp thoại mở bao lâu cũng không giữ khoá nào, vì lúc đó chỉ đang `SELECT` thường |
| 9 | Khối 4 (trạng thái) sẽ muốn đổi `Status` khi hàng bị trả hết | Thấp | Khối này **không** ghi `Status`, chỉ đọc, và đã xoá đoạn comment `PARTIALLY_RETURNED` ở `InvoiceDao:390-397` để không mời khối 4 đi sai đường. Chỗ duy nhất khối 4 phải sửa là thêm quy tắc vào `sp_add_return` — ghi chú này cần nằm ngay trong comment của thủ tục, không chỉ trong tài liệu |
| 10 | Người học tưởng tiền hoàn "sai" vì nó không bằng `SL_tra × Don_gia` | Thấp | Đúng là không bằng, khi dòng có giảm giá. Comment của `fn_return_refund` giải thích bằng một câu tiếng Việt, và mục 3.1 có ví dụ số thật. Đây là nội dung **đáng dạy**, không phải lỗi cần che |

---

## 9. Trạng Thái Triển Khai (đã hoàn thành — Phần A + B + C)

### 9.1. File đã thay đổi

**SQL (nạp vào database đang chạy, không mất dữ liệu):**

| File | Thay đổi |
|---|---|
| `sql/01_schema.sql` | Bảng `Account`: thêm cột `Cashier_ID VARCHAR(10) NULL` + khoá ngoại `fk_account_cashier` |
| `sql/02_seed.sql` | `INSERT INTO Account` thêm cột: `manager`→`E01`, `cashier`→`E02` |
| `sql/04_routines.sql` | **5 dòng `DROP` mới** (2 hàm + 3 thủ tục). **2 hàm mới:** `fn_return_refund` (1.4), `fn_next_return_id` (1.5). **3 thủ tục mới:** `sp_get_returnable_lines` (3.6), `sp_add_return` (5.4), `sp_delete_return` (5.5). **3 thủ tục sửa:** `sp_get_invoice_returns` (thêm cột `Lan_tra`, gọi hàm), `sp_report_big_returns` (gọi hàm, bỏ `JOIN Invoice_Line`), `sp_authenticate_account` (trả thêm `Cashier_ID`) |

**Java:**

| File | Thay đổi |
|---|---|
| `src/billing/model/Account.java` | `cashierId` + `getCashierId()`; hàm dựng thành 4 tham số (chỉ 1 chỗ gọi — `AccountDao`) |
| `src/billing/dao/AccountDao.java` | `hasColumn()` + đọc `Cashier_ID` phòng thủ |
| `src/billing/dao/InvoiceDao.java` | `ReturnItem` đổi kiểu (`BigDecimal quantity`, mất `amountRefunded`); `processReturn()` viết lại (`void`, ném lỗi, `prepareCall` ngoài vòng lặp, một `LocalDate.now()`); **xoá** khối comment `sp_update_invoice_return_status`; thêm `returnableLines()`, `returnRefund()`, `deleteReturn()` |
| `src/billing/ui/InvoiceViewPanel.java` | Viết lại đáng kể — xem bảng dưới |
| `src/billing/ui/SearchPanel.java` | `QUANTITY_FORMAT` + `formatQuantity()`; bỏ `QUANTITY`/`RETURN_QUANTITY` khỏi `isQuantityColumn()` |
| `docs/05-SQL-JAVA-CONVENTION.md` | Mục 6: thêm 6 dòng (`fn_return_refund`, `fn_next_return_id`, `sp_get_returnable_lines`, `sp_add_return`, `sp_delete_return`, ghi chú 7 cột cho `sp_get_invoice_returns`) |
| `src/billing/model/UserSession.java` | **không đổi** — đúng như kế hoạch dự đoán |

`InvoiceViewPanel` chi tiết:

| Thêm | Xoá |
|---|---|
| `QUANTITY_FORMAT`, `QUANTITY_INPUT_FORMAT`, `formatQuantity()`, `dec()`, `intOf()` | `parseVNCurrency()` (47 dòng) |
| `ReturnableLine`, `ReturnableLinesModel`, `ReturnRow`, `ReturnRowsModel` | `InvoiceLineData`, `InvoiceLineTableModel` |
| `CurrencyRenderer`, `QuantityRenderer`, `applyRenderers()`, `sizeColumns()` | 6 `DefaultTableCellRenderer` nội tuyến (code chết) |
| `showDetail()`, `selectInvoiceRow()` | — |
| `readQuantity()`, `warn()`, `showReturnFailure()`, `approverLabel()` | `updateReturnTableSTTAndTotal()`, `updateTotalRefundLabel()` |
| `btDeleteReturn` + `onDeleteReturn()` (phần C) | — |
| `btReturnLine` + `btDeleteReturn` vào `applyRolePermissions()` | — |
| `fillFormatted()` thêm tham số thứ 5 `quantityCols`, sửa cả 4 chỗ gọi | — |

### 9.2. Những điểm làm khác kế hoạch (và lý do)

1. **Làm luôn phần C (huỷ một lượt trả), dù kế hoạch ghi là tuỳ chọn.** `sp_delete_return` đã được
   đặc tả đầy đủ ở mục 4.8, và nó là cách duy nhất sửa một lượt trả ghi sai mà không phải xoá cả
   hoá đơn. Nút `Huy luot tra` nằm dưới bảng "Tra hang (Return)", chỉ `manager`, có hộp thoại xác
   nhận nêu rõ dòng / lần trả / số lượng / tiền hoàn.

2. **`processReturn` ghi nhật ký cả lần thử THẤT BẠI.** Kế hoạch mục 5.4 đặt `SqlLog.add` ngay sau
   `executeUpdate`, nhưng khi `sp_add_return` ném `SIGNAL`, `executeUpdate` thoát trước khi tới dòng
   log → tab 5 chỉ còn dòng `ROLLBACK` trơ trọi, trái với kịch bản #55 ("có dòng `sp_add_return` của
   lần thử, rồi dòng `ROLLBACK`"). Đã thêm một biến `current` theo dõi mục đang ghi; khối `catch`
   ghi lại câu gọi bị từ chối với `0 dong` rồi mới `ROLLBACK`. Lọc theo `sp_add_return` vẫn ra đủ.

3. **Thêm `QUANTITY_INPUT_FORMAT` (không có dấu phân cách nghìn) cho giá trị điền sẵn.**
   `QUANTITY_FORMAT` dùng `.` làm dấu nghìn, mà ô nhập hiểu `.` là dấu thập phân (mục 3.2 quy tắc 2)
   — điền `1.500` vào ô đó thì chính app tự tạo ra sự nhập nhằng mà quy tắc ấy muốn tránh. Số lượng
   trong app demo không tới 1000 nên đây là phòng xa, nhưng phòng xa đúng chỗ.

4. **Hai bảng trong hộp thoại dùng `AUTO_RESIZE_ALL_COLUMNS`, không phải bề rộng cứng.** Rủi ro #7
   là tràn box ở cửa sổ nhỏ / 125% DPI. Với chế độ này các con số bề rộng trở thành **tỷ lệ**: bảng
   luôn vừa khít khung nhìn nên không bao giờ tràn, và nới rộng hộp thoại thì mọi cột rộng ra theo.

5. **Tên người duyệt tra qua `sp_lookup_cashiers`, không qua `Account.Full_Name`.**
   `Account.Full_Name` của `manager` là *"Quan Ly He Thong"*, còn `Cashier.Name` của `E01` là
   *"Tran Van An"* — cột `Nguoi_duyet` ở tab 3 hiện cái thứ hai, nên hộp thoại phải hiện cùng cái đó
   (kịch bản #15 và #43 đòi đúng chuỗi này). Dùng `LookupDao.cashiers()` có sẵn, không thêm thủ tục.
   Tra tên thất bại thì hiện mã `E01` chứ không chặn trả hàng.

6. **`showReturnFailure` chọn cha hộp thoại theo `isShowing()`.** Kế hoạch khoá 3 nút
   (`btnAdd`/`btnRemove`/`btnConfirm`) trong lúc `SwingWorker` chạy, để `Huy` vẫn bấm được. Nếu
   người dùng bấm `Huy` giữa lúc chờ thì `done()` sẽ hiện hộp thoại lỗi với cha đã bị `dispose` —
   nên `done()` lấy `returnDialog.isShowing() ? returnDialog : InvoiceViewPanel.this`.

7. **`onEditLine` / `onDeleteLine` vẫn giữ `reload(); onSelect(null);` như cũ.** Hai nút này cũng
   mắc đúng lỗi #12 (mất selection → khung chi tiết giữ nội dung cũ) và giờ đã có sẵn
   `showDetail()` + `selectInvoiceRow()` để sửa trong một dòng. **Cố ý không sửa:** mục 2.3 giao UI
   tab 3 cho khối 5, và khối này chỉ nhận phần trả hàng. Ghi lại ở đây để khối 5 không phải tìm lại.

8. **Nạp SQL bằng đường `ALTER` (mục 4.1 cách 2), không chạy `db-setup.ps1`.** Để không xoá dữ liệu
   đang có trên máy. Mã thoát 0, không `Warning` nào.

### 9.3. Kết quả kiểm thử đã chạy

**Biên dịch** (`javac -encoding UTF-8 -d out -cp "lib\*"` trên 30 file, trừ `src/envsetup` — đúng
lệnh của `run.ps1` mục 4): **sạch, exit 0, không thêm cảnh báo mới nào.** Dòng `Note:` về API
deprecated của `SearchPanel` là `new Locale("vi","VN")` ở dòng 39, có từ trước khối này. Chạy thêm
với `-Xlint:all`: 43 cảnh báo, toàn bộ thuộc ba nhóm `[serial]` / `[this-escape]` / `[deprecation]`
mà mọi lớp Swing của dự án đã sinh ra từ trước; code mới không sinh cảnh báo `unchecked` hay `cast`
nào (đây là điều cần canh khi đổi `double` → `BigDecimal`).

**Phần A — `mysql` CLI (kịch bản 7.1), 14/14 đạt:**

| # | Kết quả thực tế |
|---|---|
| 1 | `INV-0003`: dòng 2 `SL_mua=2.000 SL_da_tra=1.000 SL_con_lai=1.000`; dòng 1 `SL_da_tra=0.000` ✔ |
| 2 | `INV-0013` dòng 1 `SL_con_lai=0.000` ✔ |
| 3,4,5 | `29700.00`, `59400.00`, `79000.00` ✔ (đúng từng xu, không phải `26400`) |
| 6 | `2` → `CALL` → `3`; bảng `Return` có `(INV-0003, 2, 1)` và `(INV-0003, 2, 2)` ✔ |
| 7 | `ERROR 1644 (45000)`: *"Dong 1: chi con 0.000 co the tra (da mua 2.000, da tra 2.000), khong tra duoc 1.000"* ✔ |
| 8 | `45000`: *"...chi con 1.500... khong tra duoc 1.600"* ✔ |
| 9 | `0` và `-1` đều: *"So luong tra phai lon hon 0"* ✔ |
| 10 | `45000`: *"Khong tim thay dong 99 cua hoa don INV-0002"* ✔ |
| 11 | `45000`: *"Hoa don INV-0015 da bi huy (VOIDED), khong tra hang duoc"* ✔ |
| 12 | `ERROR 1452 (23000)` khoá ngoại `fk_return_cashier` ✔ |
| 13 | Nạp lại `04_routines.sql` lần thứ hai: exit 0, không lỗi "already exists" ✔ |
| 14 | `manager`→`E01`, `cashier`→`E02` ✔ |

**#24 và #56 (phép kiểm chống hồi quy — quan trọng nhất của phần A):** đã lưu lại cột `Tien_hoan`
của `INV-0003`/`INV-0013`/`INV-0014` **trước** khi sửa, so lại **sau** khi sửa:
`78000.00 / 316000.00 / 175000.00` — **giống nhau từng đồng**. `sp_report_big_returns` cũng y nguyên:
`INV-0013` `316000.00` (100.00%), `INV-0014` `175000.00` (75.53%). #57: `fn_return_refund('INV-0014',
2, 1)` = `18900.00`, đúng con số kế hoạch đã tính sẵn.

**Phần B — tầng Java, 58/58 đạt** (3 chương trình kiểm thử tạm trong thư mục scratchpad, gọi thẳng
DAO + dựng `InvoiceViewPanel` thật rồi gọi `onReturnLine()`/`readQuantity()` qua reflection):

- **38/38** — `AccountDao` đọc `Cashier_ID` (`E01`/`E02`); `sp_get_returnable_lines` trả đúng 9 cột
  đúng tên; `SL_mua` về Java là `BigDecimal("1.500")` không bị làm tròn; `fn_return_refund` qua
  `{? = call ...}` ra `29700.00` / `79000.00`; `processReturn` 2 dòng thành công →
  `sp_get_invoice_returns` trả **7 cột đúng thứ tự mục 3.5**, tổng `Tien_hoan` = `48700.00` **bằng
  đúng** tổng hộp thoại xác nhận (#21), `Nguoi_duyet` = *"Tran Van An"* chứ không phải `manager`
  (#43); nhật ký có **đúng 2 dòng** `sp_add_return` + 1 dòng `COMMIT`, không `ROLLBACK` (#54);
  lỗi `45000` → `SQLState` đọc được qua chuỗi `getCause()` (đúng cơ chế `showReturnFailure`), thông
  điệp đọc hiểu được, không dòng nào được ghi, nhật ký có `sp_add_return` + `ROLLBACK` không `COMMIT`
  (#55); `VOIDED`/dòng 99/số lượng 0 đều `45000`, còn `Approved_By='manager'` ra `23000` nên **đi
  đường `Ui.error`** chứ không thành cảnh báo gọn; `deleteReturn` trả 1 rồi 0 dòng; **bất biến #51:
  không dòng nào có tổng trả > số mua**; hộp thoại dựng được, tiêu đề có mã hoá đơn, 2 bảng 9+5=14
  cột; `Cashier_ID = NULL` → hiện *"Chua xac dinh duoc nguoi duyet"* và **không mở** hộp thoại (#42).
- **14/14 — ô nhập số lượng (#26–#31):** `0,5` và `0.5` cho cùng `0.5`; `1,5` (đúng biên, phép so
  `compareTo`) nhận; `1,6` → *"Chi con 1,5 KG co the tra."*; `0,1234` → *"Toi da 3 chu so thap
  phan"*; `1.2.3` → *"Chi duoc mot dau thap phan"*; `abc`, rỗng, chỉ khoảng trắng, `0`, `-1` đều bị
  chặn với thông điệp riêng; Cancel → `null`, **không** hiện cảnh báo nào.
- **6/6 — kiểm thử âm #45 và #46:** hạ `sp_authenticate_account` về bản cũ (3 cột) → **vẫn đăng nhập
  được**, `getCashierId()` trả `null`, không `SQLException`, `role` vẫn đọc được nên phân quyền UI
  vẫn đúng. Xoá `sp_add_return` → `processReturn` ném lỗi `SQLState 42000` (**không** phải `45000`,
  nên đi `Ui.error`) kèm **thông điệp gốc của MySQL**: *"PROCEDURE retail_billing.sp_add_return does
  not exist"* — không còn câu "that bai, thu lai sau". Cả hai đã khôi phục sau khi kiểm.

**#52, #53 — xoá hoá đơn / dòng hàng đã có lượt trả:** chạy trên hoá đơn dùng một lần
`INV-TEST-K3` (2 dòng, 2 lượt trả). `sp_delete_invoice_line` xoá dòng 2: lượt trả còn 1, dòng còn 1,
không lỗi khoá ngoại. `sp_delete_invoice` xoá sạch: `0 / 0 / 0`. Dữ liệu seed không bị ảnh hưởng.

**Bước 17 — kích thước hộp thoại, đo BẰNG SỐ:** `getSize` = **1350x404**, `preferredSize` =
**1350x404** (bằng nhau → không bị co), `minimumSize` = **720x404**, vùng làm việc thật của máy =
**1536x816** (màn hình 1920x1080 ở **125% DPI**) → còn thừa 186px chiều rộng. Nếu màn hình nhỏ hơn,
`onReturnLine` in một dòng ra console nêu rõ kích thước thực / kích thước cần, và hai bảng đều có
thanh cuộn nên không mất nội dung.

**Khởi động app thật:** `java -cp "out;lib\*" billing.Main --login=manager` chạy lên, không exception,
nhật ký SQL có `sp_authenticate_account` → `sp_get_recent_invoices` → ... như bình thường.

**Trạng thái database sau khi kiểm thử xong:** đúng y trạng thái seed — 3 lượt trả
(`INV-0003/2/1`, `INV-0013/1/1`, `INV-0014/1/1`), không hoá đơn test nào còn lại.

#### Các kịch bản CHƯA chạy (và vì sao)

Những kịch bản dưới đây cần **bấm chuột thật trên giao diện Swing**, phiên làm việc này không có
công cụ điều khiển chuột/bàn phím trên desktop nên không tự chạy được. Phần *logic* bên dưới mỗi
kịch bản đều đã được kiểm qua DAO hoặc qua reflection như ghi ở trên; phần chưa kiểm là **thao tác
và những gì hiện ra trên màn hình**:

| # | Kịch bản | Phần đã kiểm thay thế |
|---|---|---|
| 16–19, 22, 23 | Đường cơ bản: nhìn `Thanh_tien=59.400đ`, `Tien_hoan=29.700đ`, ô nhập điền sẵn, trả tiếp lần hai | `fn_return_refund` (#3,#4), `sp_get_returnable_lines` (#1,#2), `readQuantity` (14/14), `ReturnableLinesModel` trả `lineTotal` của cả dòng |
| 20, 21 | Bảng "Tra hang (Return)" hiện hàng mới **ngay** sau khi lưu | `showDetail()` + `selectInvoiceRow()` chạy trong `done()`; tổng `48700.00` đã khớp hai đầu |
| 25, 32, 33 | `SL_mua` hiện `1,5`; `SL_tra` hiện `0,5`; tab 2 hiện `0,5`/`1,5` | `formatQuantity` của cả hai lớp, và `SL_mua` về Java là `BigDecimal("1.500")` |
| 34–38 | Hoá đơn có **trùng sản phẩm** ở hai dòng | Khoá định danh đã đổi sang `lineNumber` (`ReturnRowsModel.indexOfLine`), cột 0 không còn ai ghi đè — `updateReturnTableSTTAndTotal` đã bị xoá hẳn |
| 39–41, 44 | Nút mờ với thu ngân, sáng lại khi đổi vai trò | `btReturnLine`/`btDeleteReturn` đã vào cả ba danh sách của `applyRolePermissions()`; `MainWindow:260` gọi lại hàm này |
| 47, 48 | Tắt MySQL rồi bấm Xác nhận; bấm 5 lần thật nhanh | `SwingWorker` + `setBusy` đã dựng; hộp thoại xác nhận modal chặn các cú bấm sau |
| 49 | Bấm Xác nhận khi danh sách trống | Nhánh `getRowCount() == 0` thoát trước mọi lời gọi DB |
| 50 | Hai cửa sổ app cùng trả một dòng | Chốt SQL đã kiểm bằng CLI (#7, #8) — `FOR UPDATE` + kiểm "còn lại" trong cùng transaction |
| 58, 59 | Đổi vai trò giữa hai lượt trả; giỏ hàng tab 1 còn nguyên | `UserSession` được đọc tại thời điểm bấm `Tra hang`; hộp thoại không dựng lại `MainWindow` |

Chạy nốt bằng tay: `.\scripts\ps1\run.ps1 -Login manager`
