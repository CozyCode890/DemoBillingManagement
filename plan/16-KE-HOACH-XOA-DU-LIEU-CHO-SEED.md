# Kế Hoạch Chi Tiết — Xoá Dữ Liệu Database (không gian thực hành cho người viết seed)

> **Dành cho AI / lập trình viên tiếp theo:** đây là tính năng **đứng riêng**, làm sau đợt 6 khối
> (`plan/10` → `plan/15`). Nó thêm **một nút** vào tab SQL của Setup App: *Xoá dữ liệu*. Nút này
> xoá các dòng nhưng **giữ nguyên 16 bảng và mọi hàm/thủ tục**, để người viết seed có một database
> trống đúng nghĩa và chạy thử seed của mình bao nhiêu lần cũng được.
>
> **Khối này KHÔNG đổi lược đồ** (`01_schema.sql` không đổi một dòng), **không đổi thủ tục nào mà
> app gọi**, và **không đổi dữ liệu mẫu** (`02_seed.sql` sinh ra đúng bộ dữ liệu cũ, từng đồng —
> kịch bản #120). Nó thêm 2 thủ tục vào `04_routines.sql`, thay khối DỌN SẠCH của `02_seed.sql`
> bằng một lệnh `CALL`, thêm 2 script, sửa `SqlTab.java` và tài liệu.
>
> **Phải đọc mục 3 trước khi gõ dòng code đầu tiên.** Hợp đồng *một bản chép* (mục 3.2) quyết định
> thứ tự xoá nằm ở đâu, và đó là quyết định duy nhất trong khối này có hậu quả lâu dài.

---

## 1. Bối Cảnh & Vấn Đề

### 1.1. Hiện trạng (đã đọc code, không phỏng đoán)

| Thành phần | Trạng thái hiện tại |
|---|---|
| `src/envsetup/ui/SqlTab.java:16-35` | 4 nút: *Tạo database*, *Tạo dữ liệu mẫu*, *Chạy bài tập 03_queries.sql*, *Xoá database* |
| `SqlTab.java:41-54` + `sql-drop-db.ps1:33-34` | *Xoá database* chạy `DROP DATABASE IF EXISTS retail_billing` → mất **cả 16 bảng lẫn 40 routine** (9 hàm + 31 thủ tục — đếm từ khối `DROP` ở `04_routines.sql:18-62`) |
| `sql-create-db.ps1` | Nạp `01_schema.sql` (mở đầu bằng `DROP DATABASE` — `01_schema.sql:12`) rồi `04_routines.sql`. **Không seed** ⟶ kết quả là một database trống có routine. Không chỗ nào nói ra điều này: nút tên *Tạo* nhưng thực chất là *xoá sạch rồi tạo lại* |
| `sql/02_seed.sql:50-84` | Khối `0. DON SACH`: 15 lệnh `DELETE` theo thứ tự ngược khoá ngoại + `UPDATE Cashier SET Supervisor_ID = NULL` + `DELETE FROM Cashier`. Đây là **chỗ duy nhất** trong repo biết thứ tự xoá toàn bộ dữ liệu — và nó dính liền với việc nạp lại ~550 hoá đơn |
| `02_seed.sql:63-69` / `:70-84` | Khối đó **đã tự chia đôi**: 7 lệnh đầu là 7 bảng giao dịch, 9 bảng sau là danh mục — đúng ranh giới của `docs/05` mục 8.1 |
| `04_routines.sql:1131-1160` | `sp_delete_invoice` xoá **một** hoá đơn, cùng thứ tự con-trước-cha cho 6 bảng, có `WHERE` |
| `01_schema.sql` | **Không bảng nào dùng `AUTO_INCREMENT`.** `fn_next_invoice_id` / `fn_next_payment_id` (`04_routines.sql:171-203`) lấy `MAX(...) + 1` ⟶ xoá xong là mã tự quay về `INV-0001` / `PAY-0001`, không cần reset bộ đếm nào |
| `02_seed.sql:771-774` | `sp_seed_demo_data` bị `DROP` ngay sau khi gọi vì *"để lại một thủ tục SINH DỮ LIỆU trong database là một cái bẫy (ai gọi hai lần là dữ liệu nhân đôi)"* |
| `docs/05` mục 6, dòng `sp_check_seed` | Tiền lệ: một thủ tục **dụng cụ** mà app không gọi, được ghi rõ *"app không gọi nó"* |
| `PlatformUtil.java:84-98` | Tham số cho script đi qua **biến môi trường**, không ghép vào dòng lệnh (tiền lệ `BILLING_LOGIN` của khối 2) |
| `ScriptRunner.java:139-141` | stdin của script bị đóng ⟶ script trong app **không được hỏi xác nhận**. Xác nhận là việc của GUI |
| `05_invariants.sql:229-235` | **R12** = *"91 ngày gần nhất đều có hoá đơn"*. Trên database trống, R12 báo **91** vi phạm. R12 là **cam kết phủ ngày của seed chính thức**, không phải quy tắc nghiệp vụ |
| `Icons.java:22`, `UiHelper.java:87` | `Icons.Shape.ERASER` và `UiHelper.BTN_ORANGE` đã có sẵn — không phải vẽ icon hay thêm màu |

### 1.2. Sáu vấn đề cụ thể

1. **Muốn có database trống thì chỉ có cách xoá luôn cấu trúc.** *Xoá database* mất cả bảng lẫn
   routine; *Tạo database* cho ra database trống nhưng tên của nó không nói vậy. Người viết seed
   phải *biết* mẹo đó.
2. **Không có cách xoá dữ liệu giao dịch mà giữ lại danh mục.** Đây chính là thứ cần nhất khi tập
   viết phần khó của seed: vòng lặp sinh hoá đơn qua `sp_create_invoice_header` →
   `sp_add_invoice_line` → `sp_add_payment` → `sp_settle_invoice` (`plan/15` mục 4.5). Hôm nay
   phải xoá sạch rồi tự gõ lại 9 bảng danh mục, hoặc chạy cả `02_seed.sql` để ~550 hoá đơn quay về.
3. **`DROP DATABASE` xoá cả những gì người viết seed tự tạo trong database**: thủ tục phụ, bảng
   nháp, và cả `sp_check_seed` (`check-seed.ps1:60-67` phải tự nạp lại vì lý do này).
4. **Thứ tự xoá nằm trong `02_seed.sql`.** Ai viết seed riêng mà muốn nó chạy lại được thì phải
   chép 17 câu lệnh đó vào file của mình — một bản chép thứ hai, và hôm nay chưa có gì chốt rằng nó
   đủ bảng.
5. **Không thấy được mỗi bảng còn bao nhiêu dòng** sau khi xoá hay nạp. Script hiện có chỉ in
   `[OK]`.
6. **Người viết seed sẽ chạy `check-seed` trên seed của mình và thấy R12 đỏ** mà không hiểu vì
   sao. Hiện chưa có tài liệu nào nói rằng R12 là cam kết riêng của seed chính thức.

---

## 2. Mục Tiêu & Phạm Vi

### 2.1. Mục tiêu

1. Nút **Xoá dữ liệu** trong tab SQL, có hộp thoại cho chọn **hai mức**: chỉ dữ liệu giao dịch,
   hoặc toàn bộ dữ liệu.
2. **Giữ nguyên** 16 bảng, mọi hàm/thủ tục, và **mọi thứ khác người dùng đã tạo** trong database.
3. **Thứ tự xoá nằm ở đúng một chỗ.** `02_seed.sql`, Setup App và seed của người học đều gọi lại
   chỗ đó (mục 3.2).
4. Log in một **bảng Trước/Sau** cho cả 16 bảng.
5. Người viết seed chỉ cần **một dòng** `CALL sp_wipe_transactions();` ở đầu file là seed của họ
   chạy lại được.
6. Chạy được cả bằng dòng lệnh, trên cả Windows lẫn macOS/Linux, giống mọi nút khác của Setup App.

### 2.2. Nguyên tắc bất di bất dịch

- **Không `SET FOREIGN_KEY_CHECKS = 0`, không `TRUNCATE`.** Kế thừa nguyên văn lý do của `plan/15`
  mục 4.2: tắt khoá ngoại thì sai thứ tự cũng không báo gì, còn `TRUNCATE` thì MySQL từ chối trên
  bảng đang được khoá ngoại tham chiếu.
- **Không có mức mặc định ngầm.** Script không nhận được phạm vi thì **từ chối và không xoá gì**
  (exit 2). Một script xoá dữ liệu mà tự đoán *"chắc là xoá hết"* là một cái bẫy.
- **Hộp thoại mặc định là lựa chọn an toàn hơn:** radio chọn sẵn mức 1, và nút mặc định
  (phím Enter) là **Huỷ**.
- **Additive First** (`plan/06` mục 1.2): không đổi `01_schema.sql`, không đổi thủ tục nào app
  gọi, không đổi cột trả về nào.

### 2.3. Ngoài phạm vi khối này (và vì sao)

| Việc | Vì sao không làm ở khối này |
|---|---|
| Nút *Kiểm bất biến* (`check-seed`) trong tab SQL | Đi cặp rất tự nhiên với nút này (*xoá → seed → kiểm*), nhưng là một tính năng riêng, và phải giải quyết R12 trước (dòng dưới). Đề xuất cho khối sau |
| Tách R12 thành quy tắc "của seed chính thức" / cho `v_days` thành tham số | Đổi `05_invariants.sql` là đổi hợp đồng của khối 6. Khối này chỉ **ghi rõ** trong tài liệu rằng R12 khác 0 trên seed riêng là bình thường (mục 6) |
| Nút *Nạp file seed tự chọn* (chọn một file `.sql` bất kỳ để chạy) | Tính năng riêng. Hôm nay người học sửa thẳng `02_seed.sql` hoặc chạy `mysql < file.sql` |
| Sao lưu trước khi xoá (`mysqldump`) | Dữ liệu mẫu **tái tạo được bằng một nút** và xác định từng đồng (`plan/15` mục 3.5). Cái mất thật sự chỉ là hoá đơn người dùng tự lập trong app, và hộp thoại nói thẳng điều đó |
| Tự tắt app billing trước khi xoá | Setup App không tự tắt một tiến trình người dùng đang mở. Hộp thoại nhắc đóng app |
| Mức thứ ba: chọn từng bảng | Rất nhiều tổ hợp sẽ bị khoá ngoại chặn (ví dụ xoá `Product` mà giữ `Invoice_Line`). Hai mức hiện có trùng đúng ranh giới mà khoá ngoại cho phép |
| Đổi *Tạo database* / *Xoá database* | Không cần. Nút mới đứng cạnh, không thay thế |

---

## 3. Ba Hợp Đồng (quan trọng nhất)

### 3.1. Hợp đồng hai mức xoá

```
Muc 1 -- sp_wipe_transactions()            WIPE_SCOPE=transactions
    XOA  7 bang GIAO DICH : `Return`, Cash_Payment, Card_Payment,
                            EWallet_Payment, Payment, Invoice_Line, Invoice
    GIU  9 bang DANH MUC  : Shop, Counter, Cashier, Customer, Product,
                            Price_History, Promotion, Applies_To, Account

Muc 2 -- sp_wipe_all_data()                WIPE_SCOPE=all
    = CALL sp_wipe_transactions()  +  xoa 9 bang danh muc

Ca hai muc: GIU 16 bang (cau truc), moi ham / thu tuc, va moi thu khac
            nguoi dung tu tao trong database.
```

**Ranh giới 7 / 9 không phải chia mới.** Nó chính là ranh giới của `docs/05` mục 8.1 (bảng nào đi
qua thủ tục, bảng nào `INSERT` thẳng), và cũng là chỗ `02_seed.sql:63-84` đã tự chia đôi. Nó đúng
vì **khoá ngoại chỉ đi một chiều**: bảng giao dịch trỏ vào danh mục (`Invoice` → `Counter`,
`Cashier`, `Customer`; `Invoice_Line` → `Product`; `Return.Approved_By` → `Cashier`), còn
**không bảng danh mục nào trỏ vào bảng giao dịch**. Vì vậy mức 1 **không thể** vi phạm một khoá
ngoại nào, bất kể danh mục đang có gì.

| | Mức 1 — giao dịch | Mức 2 — toàn bộ |
|---|---|---|
| App billing đăng nhập được? | **Được** (`Account` còn nguyên, kể cả đăng nhập nhanh của khối 2) | **Không**, cho tới khi seed thêm lại tài khoản |
| Tab 1 lập hoá đơn ngay được? | Được, mã bắt đầu từ `INV-0001` | Không (chưa có quầy, sản phẩm) |
| Dùng để tập | Vòng lặp sinh hoá đơn qua thủ tục | Viết seed từ con số 0, kể cả danh mục |
| Khác *Tạo database* ở chỗ | Giữ danh mục | Giữ mọi thứ không có trong `01` + `04` (thủ tục phụ, bảng nháp, `sp_check_seed`) |

Ghi thẳng ra: **mức 2 gần trùng với *Tạo database*.** Khác biệt duy nhất là nó không đụng tới
những gì nằm ngoài hai file đó. Vẫn làm mức 2 vì *"xoá dữ liệu, giữ cấu trúc"* là một ý nghĩa rõ
ràng, trong khi *"Tạo database cũng xoá"* là kiến thức ngầm (vấn đề #1).

### 3.2. Hợp đồng một bản chép: thứ tự xoá nằm ở `04_routines.sql`

```
Thu tu xoa (nguoc chieu khoa ngoai) -> CHI o 04_routines.sql phan 6:
    sp_wipe_transactions()   7 bang
    sp_wipe_all_data()       goi cai tren + 9 bang

Nguoi goi:
    02_seed.sql khoi 0       CALL sp_wipe_all_data();      (thay 17 cau lenh)
    sql-wipe-data.ps1 / .sh  CALL sp_wipe_transactions() hoac sp_wipe_all_data()
    seed cua nguoi hoc       CALL sp_wipe_transactions();  (tuy chon)
```

Ba phương án:

| | Phương án | Kết luận |
|---|---|---|
| **A** | **Hai thủ tục trong `04_routines.sql`, `02_seed.sql` gọi lại** | **ĐÃ CHỌN.** Một bản duy nhất. `02_seed.sql` vốn đã bắt buộc nạp **sau** `04` (`02_seed.sql:5-11`), nên không thêm phụ thuộc mới. Người viết seed gọi được bằng một dòng |
| B | File riêng `sql/06_wipe_data.sql`, script tự nạp như `check-seed` tự nạp `05` | Loại: `02_seed.sql` được nạp bằng cách pipe qua stdin, nên nó không `source` được một file khác một cách tin cậy (đường dẫn tính theo thư mục hiện tại của client, và README dạy chạy `mysql ... < 02_seed.sql` từ bất cứ đâu). Kết cục là **hai bản**: một trong `02`, một trong `06` |
| C | Mỗi script tự chứa 16 lệnh `DELETE` | Loại: thành **ba bản** (`02_seed.sql`, `.ps1`, `.sh`) |

**Vì sao lý lẽ *"cái bẫy"* của `02_seed.sql:771-774` không áp dụng ở đây.** `sp_seed_demo_data`
bị `DROP` vì gọi hai lần là dữ liệu **nhân đôi**. Còn `sp_wipe_*` gọi hai lần thì **y như gọi một
lần** (idempotent): lần hai không còn gì để xoá. Cái giá thật thì phải nói ra: từ khối này, database
có **một thủ tục phá huỷ nằm sẵn**. Ai có quyền `EXECUTE` cũng gọi được nó. Trong dự án này, người
đó chỉ là `root@localhost`, và **app billing không gọi nó** (không có DAO nào). `docs/05` mục 6 sẽ
ghi đúng như dòng `sp_check_seed`: *app không gọi*.

`sp_delete_invoice` **không** được gộp vào đây. Nó xoá **một** hoá đơn theo `WHERE`, là thủ tục
app gọi (`InvoiceDao.java:437`), và đổi nó là đổi hợp đồng với Java. Hai thủ tục có cùng thứ tự
con-trước-cha nhưng phục vụ hai việc khác nhau.

### 3.3. Hợp đồng script

```
scripts/ps1/sql-wipe-data.ps1  [-Scope transactions|all]   mac dinh: $env:WIPE_SCOPE
scripts/bash/sql-wipe-data.sh  [transactions|all]          mac dinh: $WIPE_SCOPE

Exit 0  da xoa xong (in bang Truoc/Sau)
Exit 1  loi: MySQL chua chay / chua co database / mot lenh DELETE that bai
Exit 2  thieu hoac sai pham vi -- KHONG xoa gi, in cach dung
```

- **Tham số dòng lệnh thắng biến môi trường.** GUI dùng biến môi trường (`PlatformUtil.java:84-98`),
  người chạy tay dùng tham số. Không có giá trị mặc định nào khác.
- **Script không hỏi xác nhận** (stdin bị đóng — `ScriptRunner.java:139-141`). Chạy tay thì việc
  phải gõ `-Scope all` đã là một lần xác nhận.
- Tên giá trị viết thường, tiếng Anh, giống `BILLING_LOGIN=manager`. So sánh **không phân biệt
  hoa thường** (`-eq` của PowerShell vốn vậy; bash chuẩn hoá bằng `tr`).

---

## 4. Thiết Kế SQL

### 4.1. `04_routines.sql` — Phần 6 mới

Thêm 2 dòng vào khối `DROP ... IF EXISTS` ở đầu file (`:17-62`), theo một nhóm riêng:

```sql
DROP PROCEDURE IF EXISTS sp_wipe_transactions;
DROP PROCEDURE IF EXISTS sp_wipe_all_data;
```

Thêm **Phần 6** ở cuối file, trước `DELIMITER ;` (`:1269`). Comment được **chuyển nguyên văn** từ
`02_seed.sql:51-62` và `:79-82`, vì đó là phần người học đọc:

```sql
-- =====================================================================
-- PHAN 6: DUNG CU XOA DU LIEU -- app billing KHONG goi.
--         Nguoi goi: 02_seed.sql (khoi 0), nut "Xoa du lieu" cua Setup App
--         (sql-wipe-data.*), va seed tu viet cua nguoi hoc. plan/16.
--
--   Thu tu la NGUOC CHIEU KHOA NGOAI: bang con truoc, bang cha sau.
--   Doc tu duoi len la doc dung thu tu CREATE TABLE cua 01_schema.sql.
--   (giu nguyen comment SET FOREIGN_KEY_CHECKS / TRUNCATE cua 02_seed cu)
--
--   THEM BANG MOI vao 01_schema.sql thi them no vao DAY, va vao danh sach
--   dem cua sql-wipe-data.ps1 / .sh. Quen buoc nay: bang con co khoa ngoai
--   se lam DELETE bang cha BAO LOI (on, dung cho); bang khong khoa ngoai thi
--   sot du lieu IM LANG -- plan/16 muc 9.
-- =====================================================================

-- 6.1 sp_wipe_transactions: xoa 7 bang GIAO DICH, giu 9 bang danh muc.
--     Khong the vi pham khoa ngoai: khong bang danh muc nao tro vao day.
CREATE PROCEDURE sp_wipe_transactions()
BEGIN
    DELETE FROM `Return`;             -- con cua Invoice_Line
    DELETE FROM Cash_Payment;         -- 3 bang con cua Payment
    DELETE FROM Card_Payment;
    DELETE FROM EWallet_Payment;
    DELETE FROM Payment;              -- con cua Invoice
    DELETE FROM Invoice_Line;         -- con cua Invoice (va cua Product)
    DELETE FROM Invoice;
END$$

-- 6.2 sp_wipe_all_data: xoa ca 16 bang.
CREATE PROCEDURE sp_wipe_all_data()
BEGIN
    CALL sp_wipe_transactions();      -- 7 bang con phai di truoc

    DELETE FROM Account;              -- con cua Cashier
    DELETE FROM Applies_To;           -- con cua Product va Promotion
    DELETE FROM Promotion;
    DELETE FROM Price_History;        -- con cua Product
    DELETE FROM Product;
    DELETE FROM Customer;
    DELETE FROM Counter;              -- con cua Shop
    DELETE FROM Shop;

    -- Cashier TU THAM CHIEU chinh no (Supervisor_ID -> Cashier_ID) ...
    -- (giu nguyen comment cua 02_seed.sql:79-82)
    UPDATE Cashier SET Supervisor_ID = NULL;
    DELETE FROM Cashier;
END$$
```

Hai quyết định:

- **Không bọc transaction.** Lý do của `plan/15` mục 4.2 vẫn đúng ở đây: thủ tục idempotent, nên
  nếu chết giữa đường thì lần chạy sau làm nốt. Thêm một lý do riêng: `START TRANSACTION` bên trong
  thủ tục **tự commit transaction đang mở của người gọi**. Người viết seed bọc seed của mình trong
  transaction rồi gọi `sp_wipe_transactions()` ở đầu sẽ bị commit ngầm mà không hề biết.
- **Thủ tục không `SELECT` gì.** `02_seed.sql` gọi nó, nên nếu nó có `SELECT` thì log nạp seed sẽ
  có thêm một bảng thừa. Báo cáo số dòng là việc của script (mục 4.3).

### 4.2. `02_seed.sql` — khối 0 thành một lệnh

`02_seed.sql:50-84` (17 câu lệnh + comment) thay bằng:

```sql
-- ---------------------------------------------------------------------
-- 0. DON SACH -- de file nay chay lai duoc bao nhieu lan cung xong.
--    Thu tu xoa (nguoc chieu khoa ngoai) nam o MOT cho duy nhat:
--    sp_wipe_all_data, 04_routines.sql phan 6. Do cung la mot ly do nua
--    khien file nay phai nap SAU 04_routines.sql (xem dau file).
--    Seed tu viet cua ban cung goi duoc: CALL sp_wipe_transactions();
-- ---------------------------------------------------------------------
CALL sp_wipe_all_data();
```

Dữ liệu sinh ra **không đổi**. Kịch bản #120 chứng minh điều đó bằng `CHECKSUM TABLE`, không phải
bằng lời hứa.

### 4.3. Bảng Trước/Sau (trong script)

Lô SQL mà script gửi vào `retail_billing`, chạy với cờ `-t` (in dạng bảng):

```sql
CREATE TEMPORARY TABLE tmp_truoc AS <DEM>;
CALL sp_wipe_transactions();                 -- hoac sp_wipe_all_data()
SELECT t.Nhom, t.Bang, t.So_dong AS Truoc, s.So_dong AS Sau
FROM   tmp_truoc t
JOIN   (<DEM>) s ON s.Bang = t.Bang
ORDER  BY t.Thu_tu;
```

`<DEM>` gồm 16 nhánh `UNION ALL`, viết **một lần** trong mỗi script (biến `$dem` / `DEM`), dùng ở
cả hai chỗ:

```sql
SELECT 1 AS Thu_tu, 'GIAO DICH' AS Nhom, 'Invoice' AS Bang, COUNT(*) AS So_dong FROM Invoice
UNION ALL ...
UNION ALL SELECT 16, 'DANH MUC', 'Account', COUNT(*) FROM Account
```

Log mức 1 trông như sau (số liệu chỉ để minh hoạ, đúng quy ước *"khoảng"* của `plan/15` mục 3.5):

```
+-----------+-----------------+-------+-----+
| Nhom      | Bang            | Truoc | Sau |
+-----------+-----------------+-------+-----+
| GIAO DICH | Invoice         |  ~550 |   0 |
| GIAO DICH | Invoice_Line    | ~1100 |   0 |
| ...       | ...             |       |     |
| DANH MUC  | Product         |    20 |  20 |
| DANH MUC  | Account         |     2 |   2 |
+-----------+-----------------+-------+-----+
```

- **Đếm bằng `COUNT(*)`, không dùng `information_schema.TABLES.TABLE_ROWS`.** Với InnoDB, cột đó
  chỉ là **ước lượng**, và MySQL 8 còn **cache nó 24 giờ** (`information_schema_stats_expiry =
  86400`). Nếu dùng nó, bảng "Sau" ngay sau khi xoá vẫn hiện số cũ.
- Danh sách đếm là **bản chép thứ ba của danh sách bảng**, nhưng chỉ phục vụ hiển thị. Nếu nó lệch,
  báo cáo thiếu một dòng chứ dữ liệu không sai. Việc xoá vẫn được khoá ngoại bảo vệ.

### 4.4. Kiểm tra trước khi xoá (trong script)

1. **Phạm vi hợp lệ?** Không thì exit 2 ngay, chưa kết nối tới MySQL.
2. **MySQL đang chạy?** Kiểm giống `check-seed.ps1:49-53`.
3. **Database có tồn tại?** Kiểm `SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE
   SCHEMA_NAME = 'retail_billing'`. Nếu bằng 0 → exit 1 với gợi ý *"Bấm Tạo database — database
   mới tạo vốn đã trống (01 + 04, không seed)"*.
4. **Hai thủ tục đã có chưa?** Kiểm `information_schema.ROUTINES`. Nếu thiếu (database được tạo
   trước bản này) → **tự nạp lại `sql/04_routines.sql`**, in một dòng giải thích, rồi xoá tiếp.
   Tiền lệ là `check-seed` tự nạp `05_invariants.sql` (`check-seed.ps1:59-67`): file đó chỉ
   `DROP`/`CREATE` routine, không chạm một dòng dữ liệu nào. Nếu thay vào đó bắt người dùng bấm
   *Tạo database* thì thực chất là bắt họ xoá mức 2 cộng thêm routine.

Phần dò `mysql`, đọc `config.properties` và `MYSQL_PWD` trong `try/finally` **chép theo khuôn
`sql-seed-data.ps1:6-37`** (bash: `sql-seed-data.sh`), như mọi script `sql-*` khác.

---

## 5. Thiết Kế Setup App

### 5.1. `SqlTab.java` — nút mới

Thứ tự nút từ trái sang (mức độ phá huỷ tăng dần về bên phải):

```
[Tao database] [Tao du lieu mau] [Chay bai tap 03_queries.sql] [Xoa du lieu] [Xoa database]
     BLUE            GREEN                 PURPLE                  ORANGE         RED
```

- `addButton("Xoá dữ liệu", UiHelper.BTN_ORANGE, Icons.of(Icons.Shape.ERASER, Color.WHITE),
  "Xoá các dòng, giữ 16 bảng và mọi hàm/thủ tục (chọn mức trong hộp thoại)")`.
- **Cam** vì *phá huỷ nhưng phục hồi được bằng một nút*; **đỏ** để dành cho thao tác mất cả cấu
  trúc.
- Là nút *managed* (bị khoá khi có script đang chạy), như 4 nút còn lại.

### 5.2. Hộp thoại chọn mức

```
+------------------------------------------------------------------+
|  /!\  Xoá dữ liệu trong 'retail_billing'                          |
|                                                                   |
|  Giữ nguyên 16 bảng và toàn bộ hàm/thủ tục SQL — chỉ xoá các dòng.|
|                                                                   |
|  (•) Chỉ dữ liệu giao dịch                                        |
|      Xoá hoá đơn, dòng hàng, thanh toán, trả hàng (7 bảng).       |
|      Giữ cửa hàng, sản phẩm, giá, khuyến mãi, tài khoản —         |
|      app billing vẫn đăng nhập được.                              |
|                                                                   |
|  ( ) Toàn bộ dữ liệu                                              |
|      Xoá cả 16 bảng, kể cả tài khoản: app billing KHÔNG đăng      |
|      nhập được cho tới khi seed của bạn thêm lại tài khoản.       |
|                                                                   |
|  Muốn có lại dữ liệu mẫu: bấm "Tạo dữ liệu mẫu".                  |
|  Hoá đơn bạn tự lập trong app sẽ mất. Nên đóng app billing trước. |
|                                                                   |
|                                    [ Xoá dữ liệu ]   [ Huỷ ]      |
+------------------------------------------------------------------+
```

- `JOptionPane.showOptionDialog(..., WARNING_MESSAGE, options = {"Xoá dữ liệu", "Huỷ"},
  initialValue = "Huỷ")`. Nhấn **Enter** hoặc đóng cửa sổ đều là **Huỷ**.
- Hai `JRadioButton` trong một `ButtonGroup`, **chọn sẵn mức 1**. Mỗi lần mở hộp thoại đều quay về
  mức 1, không nhớ lần chọn trước: lần trước chọn *toàn bộ* thì lần sau không được mặc định là
  *toàn bộ*.
- Xác nhận xong thì gọi
  `runScript("sql-wipe-data", label, null, Map.of("WIPE_SCOPE", scope))`, với `label` là
  `"Xoá dữ liệu giao dịch"` hoặc `"Xoá toàn bộ dữ liệu"`. Dòng trạng thái sẽ hiện
  *"Xoá dữ liệu giao dịch: hoàn thành sau 1s."* (`BaseTab.java:208-217`).

### 5.3. Chữ trên tab

- Subtitle (`SqlTab.java:13-14`): thêm *"… xoá dữ liệu để thử seed riêng …"*.
- `printWelcome` (`:37-38`): thêm một câu *"Viết seed riêng: Xoá dữ liệu → chạy seed của bạn →
  check-seed."*

### 5.4. Đóng gói

`src/envsetup/` nằm trong `.gitignore`, nên trên git chỉ thấy **`SetupApp.jar`** thay đổi. Build
bằng `scripts/ps1/build-setup.ps1` (đã khoá `--release 21` — `build-setup.ps1:15-20`) rồi commit
jar.

---

## 6. Tài Liệu

| File | Sửa gì |
|---|---|
| `docs/05` mục 6 | Thêm 2 dòng `sp_wipe_transactions`, `sp_wipe_all_data`, cột *Nơi gọi*: *(app không gọi — `02_seed.sql`, `sql-wipe-data.*`)*, giống dòng `sp_check_seed` |
| `docs/05` mục 8 | Mục mới **8.5 — Seed của bạn chạy lại được bằng một dòng**: `CALL sp_wipe_transactions();` (hoặc `sp_wipe_all_data()`) ở đầu file. Kèm theo: **R12 khác 0 trên seed tự viết là bình thường**, vì R12 là cam kết *"phủ kín 91 ngày"* của seed chính thức. 13 quy tắc còn lại là quy tắc nghiệp vụ, seed nào cũng phải đạt |
| `README.md` mục *Nạp lại dữ liệu mẫu* (`:71-79`) | Thêm đoạn *"Chỉ muốn xoá dữ liệu (giữ bảng và thủ tục)"* với nút GUI và lệnh dòng lệnh. Sửa luôn đường dẫn sai `.\db-setup.ps1` → `.\scripts\ps1\db-setup.ps1` (file đã chuyển vào `scripts/ps1/` từ trước) |
| `README.md` *Cách 2* (`:42-55`) | Thêm dòng *Xoá dữ liệu* cho cả PowerShell và bash |

---

## 7. Danh Sách Công Việc (theo thứ tự thực hiện)

**Phần A — SQL** (làm trước; kiểm được bằng `mysql` CLI, chưa cần GUI)
1. `04_routines.sql`: 2 dòng `DROP`, Phần 6.
2. **Ghi `CHECKSUM TABLE` của 16 bảng ngay sau khi nạp seed CŨ** (để có số đối chiếu cho #120).
3. `02_seed.sql`: khối 0 thành `CALL sp_wipe_all_data();`.
4. Nạp `01 → 04 → 02`, chạy `check-seed`, rồi so checksum (#119, #120).

**Phần B — script**
5. `scripts/ps1/sql-wipe-data.ps1`.
6. `scripts/bash/sql-wipe-data.sh`, kèm `git update-index --chmod=+x` (bài học của commit
   `0351f30`).

**Phần C — Setup App**
7. `SqlTab.java`: nút, hộp thoại, chữ.
8. `build-setup.ps1` → `SetupApp.jar`.

**Phần D — tài liệu**
9. `docs/05` mục 6 và 8.5, `README.md`.
10. Điền mục 10 của tài liệu này.

---

## 8. Kịch Bản Kiểm Thử

Số kịch bản nối tiếp `plan/15` (kết thúc ở #118).

### 8.1. Phần A — SQL và `02_seed.sql`

| # | Kịch bản | Kết quả mong đợi |
|---|---|---|
| 119 | Nạp `01 → 04 → 02` từ database trắng, rồi `check-seed` | Xong sạch, **`@v = 0`** — `02_seed.sql` mới gọi `sp_wipe_all_data` mà không lỗi |
| 120 | `CHECKSUM TABLE` 16 bảng: seed cũ và seed mới, **cùng ngày** | **Khớp từng bảng.** Dữ liệu mẫu không đổi một đồng nào |
| 121 | Chạy `sql-seed-data` **hai lần** | Lần hai cũng xong (giữ kịch bản #118 của `plan/15`) |
| 122 | `CALL sp_wipe_transactions();` trong một phiên `mysql` | 7 bảng = 0, 9 bảng không đổi |
| 123 | Ngay sau #122: `SELECT fn_next_invoice_id(), fn_next_payment_id();` | `INV-0001`, `PAY-0001` |
| 124 | Ngay sau #122: chạy `02_seed.sql` | Xong, checksum khớp #120 — khoá ngoại không chặn khi danh mục còn mà giao dịch đã trống |

### 8.2. Phần B — script

| # | Kịch bản | Kết quả mong đợi |
|---|---|---|
| 125 | `sql-wipe-data.ps1` **không tham số, không biến môi trường** | **Exit 2**, in cách dùng; `COUNT(*) FROM Invoice` không đổi |
| 126 | `-Scope abc` | Exit 2, không xoá gì |
| 127 | `-Scope transactions` | Exit 0; bảng Trước/Sau đủ 16 dòng; cột *Sau* = 0 ở đúng 7 dòng GIAO DICH |
| 128 | `-Scope ALL` (viết hoa) | Exit 0; 16 dòng *Sau* = 0; `SHOW PROCEDURE STATUS WHERE Db='retail_billing'` vẫn đủ như trước |
| 129 | `-Scope all` **hai lần** | Lần hai exit 0, *Trước* = *Sau* = 0 |
| 130 | Biến `WIPE_SCOPE=all` nhưng tham số `-Scope transactions` | Tham số thắng: chỉ xoá giao dịch |
| 131 | MySQL tắt | Exit 1, nhắc bật MySQL ở tab Chung |
| 132 | `DROP DATABASE retail_billing`, rồi chạy script | Exit 1, gợi ý *Tạo database* |
| 133 | `DROP PROCEDURE sp_wipe_transactions; DROP PROCEDURE sp_wipe_all_data;` rồi chạy | Script tự nạp lại `04_routines.sql`, in dòng giải thích, xoá xong, exit 0 |
| 134 | Tạo một bảng nháp và một thủ tục nháp, rồi chạy `-Scope all` | **Cả hai còn nguyên.** Đây là điểm khác *Tạo database* (mục 3.1) |
| 135 | Sau #127: chạy `check-seed` | **R12 = 91**, 13 quy tắc còn lại = 0, exit 1. Đúng với mục 6. Nếu R12 = 0 thì nghĩa là còn hoá đơn sót lại |
| 136 | macOS/Linux: `sql-wipe-data.sh` lặp lại #125, #127, #128 | Kết quả giống hệt |
| 137 | MySQL Workbench (mặc định bật *Safe Updates*): `CALL sp_wipe_transactions();` | **Ghi lại hành vi thật.** Dự kiến lỗi 1175 (`DELETE` không có `WHERE` theo khoá). Nếu đúng vậy thì ghi vào `docs/05` mục 8.5: *chạy `SET SQL_SAFE_UPDATES = 0;` trước*. `02_seed.sql` cũ cũng gặp đúng lỗi này trong Workbench, không phải lỗi mới |

### 8.3. Phần C — Setup App

| # | Kịch bản | Kết quả mong đợi |
|---|---|---|
| 138 | Bấm *Xoá dữ liệu* rồi Huỷ / đóng cửa sổ / nhấn Enter | **Không chạy script**: log không có dòng `[THỰC THI]` |
| 139 | Mở hộp thoại, chọn *Toàn bộ*, Huỷ, mở lại | Radio đã quay về *Chỉ dữ liệu giao dịch* |
| 140 | Mức 1 → Xoá dữ liệu | Log có bảng Trước/Sau; dòng trạng thái *"Xoá dữ liệu giao dịch: hoàn thành sau …"*; 5 nút bị khoá trong lúc chạy |
| 141 | Sau #140: chạy billing, dùng đăng nhập nhanh | Vào được. Tab 3 trống, tab 1 lập hoá đơn ra `INV-0001` |
| 142 | Sau mức 2: chạy billing, đăng nhập tay và đăng nhập nhanh | Cả hai **báo đăng nhập thất bại** bằng thông báo, app không văng lỗi |
| 143 | Vòng thực hành đầy đủ: mức 1 → *Tạo dữ liệu mẫu* → `check-seed` | Seed xong, `@v = 0` |
| 144 | `SetupApp.jar`: `javap -v` một class bất kỳ | `major version: 65` (Java 21) |

### 8.4. Chống hồi quy

| # | Kịch bản | Kết quả mong đợi |
|---|---|---|
| 145 | *Tạo database*, *Tạo dữ liệu mẫu*, *Chạy bài tập*, *Xoá database* | Không đổi hành vi |
| 146 | `check-contracts.ps1` | Toàn bộ đạt, như trước khối này |
| 147 | `db-setup.ps1` / `db-setup.sh` từ database trắng | Ba bước `01 → 04 → 02` xong sạch |

---

## 9. Rủi Ro & Cách Giảm

| # | Rủi ro | Mức | Cách giảm |
|---|---|---|---|
| 1 | **Bấm nhầm mức 2**, mất tài khoản, không đăng nhập được app | Trung bình | Mặc định mức 1, nút mặc định Huỷ, hộp thoại nói rõ hậu quả. Phục hồi bằng một nút *Tạo dữ liệu mẫu* |
| 2 | Mất hoá đơn người dùng tự lập trong app | Trung bình | Hộp thoại nói thẳng. Sao lưu để ngoài phạm vi (mục 2.3) |
| 3 | App billing đang mở trong lúc xoá | Thấp | Màn hình vẫn giữ dữ liệu cũ cho tới khi tải lại; thao tác trên hoá đơn đã xoá thì thủ tục `SIGNAL` hoặc báo lỗi khoá ngoại, app hiện lỗi như mọi lỗi SQL khác. Hộp thoại nhắc đóng app |
| 4 | **Khối sau thêm bảng mà quên sửa `sp_wipe_*`** | Trung bình | Nếu bảng mới có khoá ngoại tới một bảng bị xoá thì `DELETE` bảng cha **báo lỗi 1451**: ồn ào, và báo đúng chỗ. Nếu bảng không có khoá ngoại thì **sót dữ liệu mà không báo gì**. Giảm bằng comment ở đầu Phần 6, và bảng Trước/Sau sẽ thiếu đúng dòng đó |
| 5 | Một thủ tục phá huỷ nằm sẵn trong database | Thấp | Chỉ `root@localhost` gọi được, app không gọi, `docs/05` mục 6 ghi rõ. Gọi hai lần không gây hại thêm (mục 3.2) |
| 6 | Workbench *Safe Updates* chặn `DELETE` | Thấp | Kịch bản #137 đo hành vi thật, rồi ghi vào tài liệu. Script và Setup App không bị ảnh hưởng |

---

## 10. Trạng Thái Triển Khai

**Đã triển khai** (2026-10-22). Chưa commit.

### 10.1. File đã thay đổi

| File | Thay đổi |
|---|---|
| `sql/04_routines.sql` | +2 dòng `DROP`, **Phần 6** mới: `sp_wipe_transactions`, `sp_wipe_all_data` (comment chuyển từ `02_seed.sql`) |
| `sql/02_seed.sql` | Khối 0: 17 câu lệnh → `CALL sp_wipe_all_data();` |
| `scripts/ps1/sql-wipe-data.ps1` | Mới |
| `scripts/bash/sql-wipe-data.sh` | Mới, mode `100755` trong index |
| `src/envsetup/ui/SqlTab.java` | Nút *Xoá dữ liệu*, hộp thoại 2 mức, subtitle, gợi ý (gitignored — chỉ thấy qua jar) |
| `src/envsetup/ui/BaseTab.java` | `printWelcome(String hint)` → `printWelcome(String... hints)` (mục 10.2 điểm 2; gitignored) |
| `SetupApp.jar` | Build lại, `--release 21` |
| `docs/05-SQL-JAVA-CONVENTION.md` | Mục 6: +2 dòng; mục **8.5** mới |
| `README.md` | *Cách 2*: +2 dòng; mục mới *Chỉ muốn xoá dữ liệu*; sửa đường dẫn `.\db-setup.ps1` → `.\scripts\ps1\db-setup.ps1` |

### 10.2. Những điểm làm khác kế hoạch (và lý do)

1. **`sql-wipe-data.ps1` pipe sang `mysql` bằng UTF-8 KHÔNG BOM** (`New-Object
   System.Text.UTF8Encoding $false`) thay vì dòng chuẩn
   `$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8` của các script
   khác. Lý do: Windows PowerShell **5.1** (chính là `powershell.exe` mà `PlatformUtil` gọi) chèn
   BOM của `[Text.Encoding]::UTF8` vào đầu chuỗi pipe sang lệnh ngoài, nên `mysql` báo
   `ERROR 1064` ngay dòng 1. pwsh 7 không chèn, nên chạy tay bằng pwsh không thấy lỗi.
   **Phát hiện kèm theo — lỗi có từ trước, KHÔNG sửa ở khối này:** `sql-seed-data.ps1` hiện tại
   chạy qua `powershell.exe` 5.1 cũng **exit 1 với `ERROR 1064` ở dòng 1** (đã chạy lại đúng lệnh
   mà `ScriptRunner` dựng). Cùng nguyên nhân thì `sql-create-db.ps1` và bước nạp `05` của
   `check-seed.ps1` cũng dính. Dòng mã hoá đó có từ commit `fda90f7` (2026-09-24). Hệ quả: trên
   Windows, nút *Tạo database* / *Tạo dữ liệu mẫu* của Setup App nhiều khả năng đang hỏng. Cách
   sửa đã chứng minh được ở chính script mới — đề xuất cho khối sau, một dòng mỗi script.
2. **`BaseTab.printWelcome` nhận nhiều dòng.** Gợi ý *"Viết seed riêng: …"* nối vào câu cũ thì dài
   quá bề rộng bảng log (log không tự xuống dòng) và bị cắt — thấy được trên ảnh chụp. Đổi sang
   varargs, tương thích ngược với `GeneralTab` / `JavaTab`; `SqlLogTab` / `GitTab` có
   `printWelcome()` riêng (private, không tham số) — overload khác chữ ký, không đè nhau.
3. **Dòng giải thích của mức 1 tách làm hai** (*"…khuyến mãi và tài khoản,"* / *"nên app billing
   vẫn đăng nhập được."*) cho cân với mức 2.
4. **Gợi ý cuối của script nhắc luôn R12** — người viết seed gặp R12 đỏ ngay ở bước kế tiếp
   (`check-seed`), nên nói ở chỗ họ đang nhìn thay vì chỉ trong `docs/05`.

### 10.3. Kết quả kiểm thử đã chạy

Môi trường: Windows 11, MySQL 8.0.45, `powershell.exe` 5.1 (đường của Setup App) và pwsh 7.6.

| # | Kết quả thật |
|---|---|
| 119 | `01 → 04 → 02` từ database trắng: xong; `check-seed` **TONG_VI_PHAM=0** |
| 120 | `CHECKSUM TABLE` 16 bảng, seed cũ vs seed mới cùng ngày: **16/16 khớp** |
| 121 | `sql-seed-data` lần hai: xong (548 hoá đơn / 1.205 dòng / 645 thanh toán / 49 lượt trả) |
| 122 | `CALL sp_wipe_transactions()`: 7 bảng 548/1205/49/645/299/174/172 → 0; 9 bảng danh mục 3/6/5/30/20/28/6/9/2 không đổi |
| 123 | `INV-0001`, `PAY-0001` |
| 124 | Seed lại sau #122: checksum **16/16 khớp** #120 |
| 125 | Không phạm vi: **exit 2**, không xoá |
| 126 | `abc`: **exit 2** |
| 127 | `WIPE_SCOPE=transactions` qua `powershell.exe` 5.1: exit 0, bảng Trước/Sau đúng (lần đầu **exit 1 vì BOM** — mục 10.2 điểm 1) |
| 128 | `-Scope ALL`: 16 bảng → 0; số routine **44 trước = 44 sau** |
| 129 | Lần hai: exit 0, Trước = Sau = 0 |
| 130 | `WIPE_SCOPE=all` + `-Scope transactions`: chỉ xoá giao dịch (Invoice=0, Product=20, Account=2) |
| 131 | MySQL tắt: **exit 1**, nhắc bật MySQL |
| 132 | Không có database: **exit 1**, gợi ý *Tạo database* |
| 133 | Thiếu `sp_wipe_*`: in dòng `[!!]`, tự nạp `04`, xoá xong, exit 0 |
| 134 | Bảng nháp (2 dòng) + thủ tục nháp: **còn nguyên** sau `-Scope all` |
| 135 | `check-seed` sau mức 1: **R12 = 91**, 13 quy tắc còn lại = 0, exit 1 — đúng dự đoán |
| 136 | Bản `.sh` chạy bằng **Git Bash 5.3 trên Windows** (không có `pgrep` → nhánh `mysqladmin ping`): không phạm vi exit 2; `TRANSACTIONS` thắng `WIPE_SCOPE=all`; `WIPE_SCOPE=all` xoá 16 bảng. **Chưa chạy trên macOS thật** |
| 137 | `SET SQL_SAFE_UPDATES = 1; CALL sp_wipe_transactions();` bằng `mysql` CLI (thay cho Workbench): **ERROR 1175, không xoá dòng nào** (vẫn 548 hoá đơn). Đã ghi vào `docs/05` mục 8.5 |
| 138 | Harness Swing: Huỷ / đóng cửa sổ / nút mặc định → log **không có** `[THỰC THI]` |
| 139 | Chọn *Toàn bộ*, Huỷ, mở lại → radio về *Chỉ dữ liệu giao dịch*; nút mặc định của root pane là **Huỷ** |
| 140 | Harness bấm thật *Xoá dữ liệu* → mức 1: nút bị khoá khi chạy, mở lại khi xong; trạng thái *"Xoá dữ liệu giao dịch: hoàn thành sau 1s."*; log có đủ bảng Trước/Sau |
| 141, 142 | **Chưa chạy app billing.** Chỉ kiểm ở mức dữ liệu: sau mức 1 `Account` còn 2 dòng, sau mức 2 còn 0 |
| 143 | Mức 1 → seed → `check-seed` = 0 (chạy bằng pwsh; qua Setup App trên Windows sẽ vướng lỗi BOM có từ trước — mục 10.2 điểm 1) |
| 144 | `SqlTab.class` trong jar: `major version: 65` |
| 145 | Bốn nút cũ không đổi một dòng code (`SqlTab` chỉ thêm) |
| 146 | `check-contracts.ps1`: **89 đạt, 0 sai** |
| 147 | **Không chạy được** ở môi trường không tương tác (`db-setup.ps1:37` gọi `Read-Host`). Thứ tự nạp `01 → 04 → 02` của nó đã kiểm ở #119 |

Hình dạng hộp thoại đã kiểm bằng ảnh chụp màn hình thật (`java.awt.Robot`): đủ chữ, không cắt dòng.
