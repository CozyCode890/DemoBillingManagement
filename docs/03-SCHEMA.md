# Giải thích schema — 15 bảng

Bản đồ quan hệ (đọc mũi tên là "trỏ tới"):

```
Shop ──< Counter ──< Invoice >── Cashier ──┐
                        │          ▲        │ (Supervisor_ID tự trỏ về Cashier)
                        │          └────────┘
                        ├──> Customer  (có thể NULL)
                        │
                        ├──< Invoice_Line ──> Product ──< Price_History
                        │         │                 └──< Applies_To >── Promotion
                        │         └──< Return ──> Cashier
                        │
                        └──< Payment ──┬── Cash_Payment
                                       ├── Card_Payment
                                       └── EWallet_Payment
```

`──<` nghĩa là một-nhiều. Đầu `<` nằm ở phía "nhiều".

---

## Ba điểm đề bài yêu cầu phải làm đúng

### 1. "An invoice line is identified only inside its invoice"

`Invoice_Line` là **weak entity**: `Line_Number` chỉ có nghĩa bên trong một hóa đơn cụ thể. Dòng số 1 của `INV-0001` và dòng số 1 của `INV-0002` là hai thứ hoàn toàn khác nhau.

```sql
PRIMARY KEY (Invoice_ID, Line_Number)
```

Hệ quả: bảng `Return` muốn trỏ tới một dòng hàng thì phải mang theo **cả cặp**:

```sql
FOREIGN KEY (Invoice_ID, Line_Number)
    REFERENCES Invoice_Line (Invoice_ID, Line_Number)
```

Đây là **composite foreign key** — khóa ngoại gồm nhiều cột. Thứ tự cột phải khớp đúng với khóa chính bên bảng cha.

### 2. "One invoice may be paid by more than one method"

Nếu để `payment_method` thành một cột trên `Invoice` thì mỗi hóa đơn chỉ có một phương thức. Đề bài cấm điều đó.

Cách làm: tách `Payment` thành bảng riêng, quan hệ một-nhiều với `Invoice`.

```
INV-0005  (tổng 377.800)
  ├─ PAY-0005  200.000  → Cash_Payment   (khách đưa 200.000)
  └─ PAY-0006  177.800  → Card_Payment   (thẻ ****9911)
```

Mỗi phương thức lại cần thông tin riêng: tiền mặt cần "khách đưa bao nhiêu", thẻ cần số thẻ và mã chuẩn chi, ví điện tử cần nhà cung cấp và mã giao dịch. Nhét hết vào `Payment` thì mỗi dòng sẽ có 4-5 cột `NULL` — vừa rối vừa không ràng buộc được gì.

Cách làm: **table-per-subclass** (mô phỏng kế thừa trong CSDL quan hệ).

```sql
CREATE TABLE Cash_Payment (
    Payment_ID VARCHAR(20) NOT NULL,
    Tendered_Amount DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (Payment_ID),                      -- vừa PK
    FOREIGN KEY (Payment_ID) REFERENCES Payment(Payment_ID)   -- vừa FK
);
```

`Payment_ID` **vừa là khóa chính vừa là khóa ngoại** — đó chính là cách tạo quan hệ 1-1. Vì là PK nên không thể có hai dòng `Cash_Payment` cho cùng một `Payment`.

Muốn biết một payment thuộc loại nào thì LEFT JOIN cả ba bảng con rồi dùng `CASE` — xem `InvoiceDao.invoicePayments()`.

> Hạn chế: SQL thuần không ép được "mỗi Payment phải có mặt ở **đúng một** bảng con". Về lý thuyết bạn có thể chèn cùng một `Payment_ID` vào cả `Cash_Payment` lẫn `Card_Payment`. Ràng buộc đó phải do tầng ứng dụng (hoặc một trigger) đảm bảo.

### 3. "The price on the invoice line must be the price valid on the invoice date"

Đây là điểm khó nhất, và là lý do tồn tại của bảng `Price_History`.

`Product` **không có cột giá**. Giá nằm ở `Price_History` dưới dạng các mốc "từ ngày này, giá là X":

| Barcode | Valid_From_Date | Price |
|---|---|---|
| 8934008 | 2024-11-27 | 130.000 |
| 8934008 | 2026-07-22 | 158.000 |

Muốn biết giá ngày 2025-08-16, lấy mốc gần nhất mà **không vượt quá** ngày đó:

```sql
SELECT Price
FROM   Price_History
WHERE  Barcode = ?
  AND  Valid_From_Date <= ?        -- <== ngày của HÓA ĐƠN, không phải CURDATE()
ORDER BY Valid_From_Date DESC
LIMIT 1;
```

Giá trả về là **130.000**, đúng như hóa đơn giấy khách đang giữ.

Sau khi tra ra, giá đó được **chép vào** `Invoice_Line.Unit_Price` và đóng băng vĩnh viễn ở đó. Lần tăng giá sau không động được tới hóa đơn cũ.

> Trong app: `ProductDao.priceOn(barcode, date)`. Bạn có thể tự kiểm chứng ở tab **"1. Lap hoa don"** — đổi ô "Ngay ban" về năm ngoái rồi thêm "Thit ba chi", đơn giá sẽ hiện 130.000 thay vì 158.000.

---

## Bảng chi tiết

| Bảng | Khóa chính | Vai trò | Ghi chú thiết kế |
|---|---|---|---|
| `Shop` | `Shop_ID` | Cửa hàng | |
| `Counter` | `Counter_ID` | Quầy tính tiền | Thuộc 1 shop. Hóa đơn gắn với quầy, suy ra shop qua JOIN |
| `Cashier` | `Cashier_ID` | Thu ngân | `Supervisor_ID` tự trỏ về `Cashier` (quan hệ đệ quy). NULL = cấp cao nhất |
| `Customer` | `Customer_ID` | Khách thành viên | `Phone` UNIQUE |
| `Product` | `Barcode` | Sản phẩm | **Không có cột giá** |
| `Price_History` | `(Barcode, Valid_From_Date)` | Lịch sử giá | Khóa ghép: mỗi SP một giá bắt đầu từ mỗi ngày |
| `Promotion` | `Promo_ID` | Chương trình KM | |
| `Applies_To` | `(Barcode, Promo_ID)` | KM áp cho SP nào | Bảng nối N-N. `Discount_Percent` ở đây vì phụ thuộc cả cặp |
| `Invoice` | `Invoice_ID` | Đầu hóa đơn | `Customer_ID` cho phép NULL = khách vãng lai |
| `Invoice_Line` | `(Invoice_ID, Line_Number)` | Dòng hàng | Weak entity. `Unit_Price` là ảnh chụp tại thời điểm bán |
| `Return` | `(Invoice_ID, Line_Number, Return_ID)` | Trả hàng | FK ghép trỏ về dòng gốc. Tên bảng phải bọc backtick |
| `Payment` | `Payment_ID` | Một lần thanh toán | Nhiều dòng cho một hóa đơn ⇒ hỗ trợ trả góp nhiều phương thức |
| `Cash_Payment` | `Payment_ID` | Chi tiết tiền mặt | PK = FK ⇒ quan hệ 1-1 |
| `Card_Payment` | `Payment_ID` | Chi tiết thẻ | |
| `EWallet_Payment` | `Payment_ID` | Chi tiết ví điện tử | `Transaction_Ref` UNIQUE |

---

## Vài chi tiết kỹ thuật đáng chú ý

**`ENGINE=InnoDB`** — bắt buộc. Engine cũ MyISAM bỏ qua `FOREIGN KEY` (khai báo vẫn chạy nhưng không ràng buộc gì) và không hỗ trợ transaction. MySQL 8 mặc định là InnoDB nên thực ra không cần ghi, nhưng ghi ra cho rõ ràng.

**`ON DELETE CASCADE` trên `Invoice_Line`** — xóa một hóa đơn thì các dòng hàng của nó tự bị xóa theo. Không có nó thì MySQL từ chối xóa vì còn dòng con tham chiếu. Chú ý: `Return` **không** để cascade, vì mất bằng chứng trả hàng là chuyện nghiêm trọng hơn.

**`ENUM` vs bảng tra cứu** — `Unit ENUM('PIECE','KG',...)` gọn và nhanh, nhưng muốn thêm giá trị mới phải `ALTER TABLE`. Nếu danh sách hay thay đổi thì nên tách thành bảng riêng. Ở đây đơn vị tính gần như cố định nên ENUM là hợp lý.

**`DECIMAL(12,2)` cho tiền** — `12` là tổng số chữ số, `2` là số chữ số sau dấu phẩy ⇒ tối đa 9.999.999.999,99. Tuyệt đối không dùng `FLOAT`/`DOUBLE` cho tiền (xem [02-JDBC.md](02-JDBC.md) mục 6).

**`DECIMAL(10,3)` cho số lượng** — 3 chữ số thập phân để bán được hàng cân ký: 1,250 kg thịt.

**Bảng `Return` phải bọc backtick** — `RETURN` là từ khóa dành riêng của MySQL. Mọi câu lệnh chạm tới nó đều phải viết `` `Return` ``. Trong dự án thật nên đặt tên khác (`Product_Return`) để đỡ phiền, nhưng ở đây tôi giữ đúng tên trong ERD của đề bài.

---

## Liên quan

- [01-NORMALIZATION.md](01-NORMALIZATION.md) — vì sao lại tách nhiều bảng như vậy
- [02-JDBC.md](02-JDBC.md) — Java gọi các bảng này như thế nào
