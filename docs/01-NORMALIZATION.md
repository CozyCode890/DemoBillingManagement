# Chuẩn hóa (Normalization) — giải thích bằng chính project này

> Nếu bạn chỉ đọc một mục, hãy đọc mục 1 và mục 8.

---

## 1. Chuẩn hóa là gì, nói cho dễ hiểu

**Chuẩn hóa = tách một bảng to thành nhiều bảng nhỏ, sao cho mỗi sự thật chỉ được ghi ở ĐÚNG MỘT CHỖ.**

Hết. Đó là toàn bộ ý tưởng. Mọi thứ còn lại (1NF, 2NF, 3NF...) chỉ là các bước kiểm tra xem bạn đã tách đủ chưa.

Vì sao cần? Vì khi một sự thật bị chép ra 50 chỗ, sớm muộn 50 chỗ đó sẽ không khớp nhau.

---

## 2. Bắt đầu từ cái sai: một bảng Excel khổng lồ

Giả sử bạn lười, nhét tất cả vào một bảng duy nhất:

| Invoice_ID | Ngày  | Shop_ID | Shop_Address      | Cashier | Barcode | Tên SP   | Đơn giá | SL |
|------------|-------|---------|-------------------|---------|---------|----------|---------|----|
| INV-001    | 01/09 | S01     | 123 Nguyen Hue    | Bình    | 8934001 | Sữa 1L   | 33000   | 2  |
| INV-001    | 01/09 | S01     | 123 Nguyen Hue    | Bình    | 8934010 | Coca     | 21000   | 3  |
| INV-002    | 01/09 | S01     | 123 Nguyen Hue    | Bình    | 8934001 | Sữa 1L   | 33000   | 1  |
| INV-003    | 02/09 | S02     | 45 Nguyen Van Linh| Cường   | 8934001 | Sữa 1L   | 33000   | 5  |

Nhìn có vẻ tiện — một câu SELECT là ra hết. Nhưng nó hỏng theo **ba** kiểu:

### Lỗi khi SỬA (update anomaly)
Cửa hàng S01 chuyển địa chỉ. Bạn phải sửa `Shop_Address` ở **mọi dòng** có S01 — có thể là 200.000 dòng. Sót một dòng là dữ liệu mâu thuẫn: cùng shop S01 mà hai địa chỉ khác nhau. Database không cách nào biết cái nào đúng.

### Lỗi khi THÊM (insert anomaly)
Bạn mở cửa hàng mới S04 nhưng chưa bán được gì. Không có hóa đơn ⇒ **không có dòng nào để ghi S04 vào**. Muốn lưu shop mới thì phải bịa ra một hóa đơn giả.

### Lỗi khi XÓA (delete anomaly)
Bạn xóa hóa đơn INV-003 (hóa đơn duy nhất của S02). Xong rồi bạn mất luôn thông tin **cửa hàng S02 tồn tại và ở đâu**. Xóa một thứ làm mất một thứ chẳng liên quan.

Ba lỗi này là lý do tồn tại của chuẩn hóa. Sửa cả ba bằng một cách duy nhất: **tách bảng**.

---

## 3. Phụ thuộc hàm — khái niệm bạn cần trước khi học 1NF/2NF/3NF

Ký hiệu `A → B` đọc là **"biết A thì suy ra được B"**.

Trong project này:

| Phụ thuộc hàm | Đọc bằng tiếng Việt |
|---|---|
| `Shop_ID → Address` | Biết mã shop là biết địa chỉ |
| `Barcode → Name, Unit, Tax_Rate` | Biết mã vạch là biết tên, đơn vị, thuế suất |
| `(Barcode, Valid_From_Date) → Price` | Biết sản phẩm **và** ngày thì mới biết giá |
| `(Invoice_ID, Line_Number) → Barcode, Quantity, Unit_Price` | Phải biết cả hóa đơn lẫn số dòng mới xác định được một dòng hàng |

**Quy tắc vàng:**

> Nếu `A → B` thì A và B nên nằm chung một bảng, và **A phải là khóa chính của bảng đó**.

Mọi luật chuẩn hóa bên dưới chỉ là cách nói khác của câu này.

---

## 4. 1NF — Mỗi ô chỉ chứa một giá trị

**Luật:** không có ô nào chứa danh sách, và không có nhóm cột lặp lại.

Vi phạm:

| Invoice_ID | Các sản phẩm                  |
|------------|-------------------------------|
| INV-001    | Sữa, Coca, Bánh mì            |

hoặc:

| Invoice_ID | SP_1 | SP_2 | SP_3 |
|------------|------|------|------|

Cả hai đều tệ: không viết nổi `WHERE` để tìm hóa đơn có bán Coca, không SUM được, và nếu khách mua 4 món thì hết chỗ.

**Cách sửa:** mỗi sản phẩm một dòng riêng ⇒ đó chính là bảng `Invoice_Line`.

```
Invoice_Line
  Invoice_ID  Line_Number  Barcode   Quantity
  INV-001     1            8934001   2
  INV-001     2            8934010   3
```

> Trong project này mọi bảng đã ở 1NF ngay từ đầu.

---

## 5. 2NF — Không phụ thuộc vào MỘT PHẦN khóa

**Chỉ áp dụng khi khóa chính gồm nhiều cột.** Nếu khóa chính chỉ một cột thì bảng tự động đạt 2NF.

**Luật:** mọi cột không thuộc khóa phải phụ thuộc vào **toàn bộ** khóa, không phải chỉ một mẩu.

Xét bảng `Invoice_Line` nếu ta nhét thêm tên sản phẩm vào:

| Invoice_ID | Line_Number | Barcode | **Product_Name** | Quantity |
|---|---|---|---|---|

Khóa chính là `(Invoice_ID, Line_Number)`. Nhưng:

- `Quantity` cần biết cả hóa đơn lẫn số dòng ⇒ phụ thuộc **toàn bộ** khóa ⇒ OK
- `Product_Name` chỉ cần biết `Barcode` là ra ⇒ phụ thuộc vào **một phần thông tin khác**, không cần khóa ⇒ **sai**

Hậu quả: tên "Sữa tươi Vinamilk 1L" bị chép lại ở mọi dòng hóa đơn từng bán sữa. Đổi tên sản phẩm ⇒ update anomaly.

**Cách sửa:** đẩy `Name` sang bảng `Product`, chỗ nó thuộc về. `Invoice_Line` chỉ giữ `Barcode`.

Đó chính xác là lý do trong `01_schema.sql`, `Invoice_Line` **không có** cột tên sản phẩm — muốn hiện tên thì `JOIN Product`.

---

## 6. 3NF — Không phụ thuộc bắc cầu

**Luật:** cột không khóa không được suy ra từ một cột không khóa khác.

Xét bảng `Invoice` nếu ta nhét thêm địa chỉ shop:

| Invoice_ID | Counter_ID | **Shop_ID** | **Shop_Address** |
|---|---|---|---|

Chuỗi suy diễn ở đây là:

```
Invoice_ID  →  Counter_ID  →  Shop_ID  →  Shop_Address
```

`Shop_Address` phụ thuộc vào khóa **qua trung gian** — gọi là phụ thuộc bắc cầu. Đây đúng là cái bảng Excel hỏng ở mục 2.

**Cách sửa:** cắt chuỗi ra thành từng đoạn, mỗi đoạn một bảng:

```
Shop     ( Shop_ID     →  Address )
Counter  ( Counter_ID  →  Shop_ID )
Invoice  ( Invoice_ID  →  Counter_ID, Date, Cashier_ID, ... )
```

Giờ địa chỉ shop nằm **đúng một dòng duy nhất** trong cả database. Đổi địa chỉ = sửa 1 dòng. Không thể mâu thuẫn được nữa, kể cả khi bạn cố tình.

Cái giá: muốn biết hóa đơn này thuộc shop nào thì phải đi hai chặng JOIN — chính là dòng này trong báo cáo doanh thu:

```sql
FROM Invoice i
JOIN Counter ct ON ct.Counter_ID = i.Counter_ID
JOIN Shop    s  ON s.Shop_ID     = ct.Shop_ID
```

**Đây là sự đánh đổi trung tâm của mọi CSDL quan hệ: ghi dữ liệu an toàn hơn, đổi lại đọc dữ liệu phải JOIN nhiều hơn.**

---

## 7. BCNF — bản chặt hơn của 3NF

**Luật:** vế trái của mọi phụ thuộc hàm phải là một khóa ứng viên.

Ví dụ vi phạm (giả định một quầy chỉ do một thu ngân trực):

| Invoice_ID | Counter_ID | Cashier_ID |
|---|---|---|

Nếu luật nghiệp vụ là `Counter_ID → Cashier_ID`, thì vế trái `Counter_ID` không phải khóa của bảng `Invoice` ⇒ vi phạm BCNF ⇒ tách ra bảng `Counter_Staffing(Counter_ID, Cashier_ID)`.

Trong project này **chúng ta không có luật đó** (quầy nào thu ngân nào cũng trực được), nên `Invoice` đã đạt BCNF. Thực tế 3NF là đủ cho 95% hệ thống; BCNF hay được hỏi khi thi.

---

## 8. Hai chỗ trong project "trông như" vi phạm nhưng thực ra đúng

Đây là phần quan trọng nhất, và cũng là phần mà đề bài đang kiểm tra bạn.

### 8.1. `Invoice_Line.Unit_Price` — chép giá lại là CỐ Ý

Nhìn qua thì: giá đã có trong `Price_History` rồi, chép sang `Invoice_Line` là dư thừa, là vi phạm chuẩn hóa. **Sai.**

Chuẩn hóa loại bỏ việc **cùng một sự thật** bị ghi hai chỗ. Nhưng ở đây là **hai sự thật khác nhau**:

| Cột | Ý nghĩa | Có được phép đổi không? |
|---|---|---|
| `Price_History.Price` | Giá niêm yết hiện hành của sản phẩm | Có, mỗi lần tăng giá |
| `Invoice_Line.Unit_Price` | **Giá mà khách đã thực trả hôm đó** | Không bao giờ |

Nếu không chép lại, thì hôm nay sữa tăng từ 28.000 lên 33.000, mọi hóa đơn từ năm ngoái sẽ tự động "tăng giá" theo. Hóa đơn giấy khách giữ trong tay không khớp với database. Kế toán và thuế sẽ không vui.

Thuật ngữ cho việc này: **point-in-time snapshot** (ảnh chụp tại thời điểm). Nó là thiết kế đúng, không phải denormalization ẩu.

> App demo minh họa điều này ở tab **"3. Bao cao" → nút "Bonus: hoa don cu giu gia cu"**.

### 8.2. `Applies_To.Discount_Percent` — vì sao không để ở `Promotion`?

Vì mức giảm phụ thuộc vào **cặp** (sản phẩm, khuyến mãi), không phải riêng khuyến mãi. Cùng chương trình "Khuyến mãi tháng 9" có thể giảm 10% cho sữa nhưng chỉ 5% cho gạo.

Phụ thuộc hàm là `(Barcode, Promo_ID) → Discount_Percent`, mà `(Barcode, Promo_ID)` chính là khóa chính của `Applies_To` ⇒ đặt ở đây là đúng chỗ.

---

## 9. Bảng tra nhanh

| Dạng chuẩn | Câu hỏi tự kiểm tra | Bảng ví dụ trong project |
|---|---|---|
| **1NF** | Có ô nào chứa danh sách không? | `Invoice_Line` tách mỗi SP một dòng |
| **2NF** | Cột nào chỉ cần *một phần* khóa là suy ra được? | Tên SP bị đẩy khỏi `Invoice_Line` sang `Product` |
| **3NF** | Cột nào suy ra từ cột không khóa khác? | Địa chỉ shop bị đẩy khỏi `Invoice` sang `Shop` |
| **BCNF** | Vế trái mọi FD có phải khóa không? | `Invoice` đã thỏa |

**Mẹo kiểm tra nhanh một bảng bất kỳ:** che khóa chính đi, nhìn các cột còn lại và tự hỏi *"cột này có thể suy ra từ cột nào khác trong bảng mà không cần khóa không?"* Nếu có ⇒ cột đó phải chuyển sang bảng khác.

---

## 10. Khi nào KHÔNG nên chuẩn hóa

Chuẩn hóa tối ưu cho việc **ghi**. Một số trường hợp việc **đọc** quan trọng hơn:

- **Dữ liệu lịch sử phải đóng băng** → chép lại, như `Unit_Price`. Đây là trường hợp của project này.
- **Báo cáo chạy trên hàng chục triệu dòng** → tạo bảng tổng hợp sẵn (`daily_shop_summary`) thay vì JOIN 4 bảng mỗi lần mở dashboard.
- **Dữ liệu chỉ đọc, không bao giờ sửa** → log, sự kiện. Không có update anomaly thì không có gì để lo.

Nguyên tắc thực dụng: **chuẩn hóa tới 3NF trước, chỉ phá vỡ khi đo được là chậm, và phải ghi rõ lý do trong comment.** Phá vỡ trước rồi mới đi đo là con đường dẫn tới dữ liệu rác.

---

## Liên quan

- [02-JDBC.md](02-JDBC.md) — Java nói chuyện với MySQL như thế nào
- [03-SCHEMA.md](03-SCHEMA.md) — giải thích từng bảng và quan hệ
