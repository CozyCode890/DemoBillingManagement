# Hướng dẫn học và đọc hiểu Codebase — Dành cho Fullstack Java Developer

> **Lời mở đầu:** Tài liệu này được biên soạn lại với tiêu chí: **Cực kỳ dễ hiểu — Trực quan — Giàu ví dụ thực tế**. Chúng tôi loại bỏ tối đa các thuật ngữ kỹ thuật khô khan, trừu tượng và thay bằng các hình ảnh ví von đời thường (như nhà hàng, quầy thu ngân siêu thị, vòi nước công cộng).
> 
> Dù bạn là người mới làm quen với Java hay đã từng làm Web với Spring Boot / React, tài liệu này sẽ giúp bạn hiểu tường tận bản chất của ứng dụng **Demo Billing Management** mà không bị "ngợp" hay lãng phí thời gian.

---

## MỤC LỤC

1. [Giải thích các khái niệm cốt lõi bằng ví dụ đời thường](#1-giải-thích-các-khái-niệm-cốt-lõi-bằng-ví-dụ-đời-thường)
   - [1.1. JDBC và Driver là gì? (Ví dụ: Ổ cắm điện và phích chuyển đổi)](#11-jdbc-và-driver-là-gì-ví-dụ-ổ-cắm-điện-và-phích-chuyển-đổi)
   - [1.2. Vòng đời kết nối và `try-with-resources` (Ví dụ: Vòi nước công cộng tự ngắt)](#12-vòng-đời-kết-nối-và-try-with-resources-ví-dụ-vòi-nước-công-cộng-tự-ngắt)
   - [1.3. Transaction và ACID là gì? (Ví dụ: Chuyển tiền ngân hàng)](#13-transaction-và-acid-là-gì-ví-dụ-chuyển-tiền-ngân-hàng)
   - [1.4. Giao diện Swing và Luồng EDT (Ví dụ: Bác thu ngân duy nhất ở siêu thị)](#14-giao-diện-swing-và-luồng-edt-ví-dụ-bác-thu-ngân-duy-nhất-ở-siêu-thị)
   - [1.5. JTable và TableModel (Ví dụ: Màn hình Tivi và Chiếc USB)](#15-jtable-và-tablemodel-ví-dụ-màn-hình-tivi-và-chiếc-usb)
   - [1.6. Mô hình 3 tầng (Ví dụ: Quy trình gọi món tại Nhà hàng)](#16-mô-hình-3-tầng-ví-dụ-quy-trình-gọi-món-tại-nhà-hàng)
2. [Lộ trình đọc code 6 bước (Đi từ dễ đến khó)](#2-lộ-trình-đọc-code-6-bước-đi-từ-dễ-đến-khó)
   - [Bước 0: Bật ứng dụng và nhìn tận mắt](#bước-0-bật-ứng-dụng-và-nhìn-tận-mắt)
   - [Bước 1: Đọc bảng dữ liệu (sql/) — Ví dụ vì sao giá đổi theo ngày](#bước-1-đọc-bảng-dữ-liệu-sql--ví-dụ-vì-sao-giá-đổi-theo-ngày)
   - [Bước 2: Tầng kết nối (billing.db) — Cầu nối Java với MySQL](#bước-2-tầng-kết-nối-billingdb--cầu-nối-java-với-mysql)
   - [Bước 3: Tầng khuôn mẫu (billing.model) — Những "chiếc hộp" đựng dữ liệu](#bước-3-tầng-khuôn-mẫu-billingmodel--những-chiếc-hộp-đựng-dữ-liệu)
   - [Bước 4: Tầng xử lý nghiệp vụ (billing.dao) — Trái tim của hệ thống](#bước-4-tầng-xử-lý-nghiệp-vụ-billingdao--trái-tim-của-hệ-thống)
   - [Bước 5: Tầng giao diện (billing.ui) — Nơi người dùng bấm chuột](#bước-5-tầng-giao-diện-billingui--nơi-người-dùng-bấm-chuột)
3. [Những cạm bẫy kinh điển (Kèm ví dụ thực tế Sai vs Đúng)](#3-những-cạm-bẫy-kinh-điển-kèm-ví-dụ-thực-tế-sai-vs-đúng)
   - [Cạm bẫy 1: Nối chuỗi SQL dẫn đến bị Hack mất dữ liệu (SQL Injection)](#cạm-bẫy-1-nối-chuỗi-sql-dẫn-đến-bị-hack-mất-dữ-liệu-sql-injection)
   - [Cạm bẫy 2: Quên Transaction làm dữ liệu bị "nửa nạc nửa mỡ"](#cạm-bẫy-2-quên-transaction-làm-dữ-liệu-bị-nửa-nạc-nửa-mỡ)
   - [Cạm bẫy 3: Tính tiền bằng số thực làm mất tiền của khách](#cạm-bẫy-3-tính-tiền-bằng-số-thực-làm-mất-tiền-của-khách)
   - [Cạm bẫy 4: Bắt hệ thống chạy đi chạy lại 50 lần thay vì gom vào 1 xe đẩy (Batching)](#cạm-bẫy-4-bắt-hệ-thống-chạy-đi-chạy-lại-50-lần-thay-vì-gom-vào-1-xe-đẩy-batching)
   - [Cạm bẫy 5: Bắt giao diện làm việc nặng khiến ứng dụng bị đơ (Freeze)](#cạm-bẫy-5-bắt-giao-diện-làm-việc-nặng-khiến-ứng-dụng-bị-đơ-freeze)
4. [Những "đường vòng" gây tốn thời gian cần tránh](#4-những-đường-vòng-gây-tốn-thời-gian-cần-tránh)
5. [Bảng dịch thuật: Đối chiếu từ Java thuần sang Spring Boot & React](#5-bảng-dịch-thuật-đối-chiếu-từ-java-thuần-sang-spring-boot--react)
6. [Thực hành: Theo chân một ổ bánh mì từ lúc bấm chuột đến khi lưu vào ổ cứng](#6-thực-hành-theo-chân-một-ổ-bánh-mì-từ-lúc-bấm-chuột-đến-khi-lưu-vào-ổ-cứng)

---

## 1. Giải thích các khái niệm cốt lõi bằng ví dụ đời thường

Trước khi nhìn vào code, hãy dành 5 phút đọc các hình ảnh ví von dưới đây. Khi đã hiểu bản chất đời thường, bạn sẽ thấy code Java viết ra chỉ là đang mô phỏng lại đúng những điều này.

---

### 1.1. JDBC và Driver là gì? (Ví dụ: Ổ cắm điện và phích chuyển đổi)

- **Giải thích đơn giản:**
  - Bạn mang một chiếc laptop mua ở Mỹ (chân cắm 3 chấu dẹt) sang Việt Nam (ổ cắm 2 chấu tròn). Để cắm được điện, bạn cần một **đầu chuyển đổi (adapter)**.
  - Trong lập trình cũng y hệt như vậy:
    - **Java (JDK)** là chiếc laptop của bạn. Java chỉ đưa ra một bộ quy tắc chung: *"Tôi muốn một cổng kết nối (`Connection`), tôi muốn gửi câu lệnh (`PreparedStatement`), và tôi muốn nhận kết quả (`ResultSet`)"*.
    - **MySQL** là ổ cắm trên tường với cấu trúc riêng biệt của nó.
    - **JDBC Driver (file `lib/mysql-connector-j-8.4.0.jar`)** chính là cái đầu chuyển đổi. Nó biến các lệnh chung chung của Java thành đúng thứ ngôn ngữ mạng mà máy chủ MySQL có thể hiểu được.

```
+---------------+           +--------------------------+           +---------------+
|   Mã Java     |  gọi qua  |     MySQL Driver         | gửi mạng  |   Cơ sở       |
| (Connection,  | --------> | (mysql-connector-j.jar)  | --------> | dữ liệu MySQL |
|  PreparedStatement)       |   "Người thông dịch"     |           |               |
+---------------+           +--------------------------+           +---------------+
```

> **Bài học rút ra:** Nếu trong dự án thiếu file `.jar` này, Java sẽ báo lỗi: *"Tôi không biết cách nói chuyện với MySQL như thế nào cả!"*.

---

### 1.2. Vòng đời kết nối và `try-with-resources` (Ví dụ: Vòi nước công cộng tự ngắt)

- **Vấn đề thực tế:** 
  - Hãy tưởng tượng ở công viên có một vòi nước công cộng. Nếu mỗi người đến rửa tay xong lại **quên khóa vòi**, nước sẽ chảy lênh láng suốt ngày đêm. Chẳng mấy chốc bể chứa sẽ cạn sạch nước và những người đến sau sẽ không còn giọt nước nào để dùng!
  - Trong máy tính, mỗi lần Java kết nối tới MySQL, nó mở ra một đường ống liên lạc (gọi là `Connection`). Máy chủ MySQL chỉ cho phép một số lượng đường ống nhất định (ví dụ tối đa 100 người cùng lúc). Nếu bạn mở kết nối mà quên đóng lại, hệ thống sẽ bị **"rò rỉ kết nối" (Connection Leak)** và sập hoàn toàn.

- **Giải pháp cũ (Dễ quên, dễ lỗi):**
  - Giống như việc bạn phải tự tay vặn khóa vòi nước. Nhưng lỡ bạn đang rửa tay mà bị trượt chân ngã ngất đi (gặp lỗi / `Exception`), vòi nước vẫn chảy mãi mà không ai khóa!

- **Giải pháp hiện đại: `try-with-resources` (Vòi nước cảm ứng tự ngắt):**
  - Bạn chỉ cần đưa tay vào rửa, khi rút tay ra hoặc có bất cứ sự cố gì xảy ra, vòi nước **luôn luôn tự động ngắt ngay lập tức**.
  - Trong Java, chỉ cần bạn đặt lệnh kết nối trong dấu ngoặc tròn `try (...)`, Java cam kết 100% sẽ tự đóng kết nối cho bạn, không bao giờ lo bị quên.

```java
// VÍ DỤ CỰC KỲ DỄ HIỂU:
// Mọi thứ khai báo trong ngoặc tròn () của try sẽ TỰ ĐỘNG ĐÓNG khi kết thúc khối {}
try (Connection conn = Db.getConnection();
     PreparedStatement ps = conn.prepareStatement("SELECT * FROM Product");
     ResultSet rs = ps.executeQuery()) {

    while (rs.next()) {
        System.out.println("Tên hàng: " + rs.getString("Product_Name"));
    }

} // <--- ĐẾN ĐÂY: Dù code chạy êm ru hay bị lỗi sập giữa chừng, 
  // Java cũng TỰ ĐỘNG ĐÓNG rs -> ps -> conn. Bạn không cần làm gì thêm!
```

---

### 1.3. Transaction và ACID là gì? (Ví dụ: Chuyển tiền ngân hàng)

- **Ví dụ đời thường:**
  - Bạn dùng app ngân hàng chuyển 500.000đ cho bạn của mình. Quá trình này bắt buộc gồm 2 bước:
    - **Bước 1:** Trừ 500.000đ trong tài khoản của bạn.
    - **Bước 2:** Cộng 500.000đ vào tài khoản của bạn bạn.
  - Điều gì xảy ra nếu Bước 1 vừa xong thì máy chủ ngân hàng bị sét đánh mất điện, Bước 2 chưa kịp chạy?
    - **Nếu KHÔNG có Transaction:** Bạn bị mất 500.000đ, còn bạn của bạn thì không nhận được đồng nào. Tiền bốc hơi vào hư không! Khách hàng sẽ kiện ngân hàng ngay lập tức.
    - **Nếu CÓ Transaction (Nguyên tắc "Tất cả hoặc Không có gì"):** Ngân hàng coi 2 bước này là **một khối thống nhất**. Nếu Bước 2 thất bại vì bất kỳ lý do gì, hệ thống sẽ lập tức **hoàn tác (Rollback)** lại Bước 1, trả lại 500.000đ cho bạn như thể chưa từng có lệnh chuyển tiền nào.

- **Áp dụng vào hệ thống Lập Hóa Đơn (Billing):**
  Khi bấm nút "Lưu hóa đơn", phần mềm phải làm 3 việc:
  1. Ghi thông tin hóa đơn vào bảng `Invoice`.
  2. Ghi 5 món hàng khách mua vào bảng `Invoice_Line`.
  3. Ghi số tiền khách trả vào bảng `Payment`.

  > **Nếu không có Transaction:** Lỡ ghi xong hóa đơn mà máy in kẹt giấy hay mạng chập chờn không ghi được tiền thanh toán, cơ sở dữ liệu sẽ lưu lại một đơn hàng rác (hàng đã xuất kho mà tiền thì chưa thu). Doanh số kế toán sẽ bị sai lệch hoàn toàn!

---

### 1.4. Giao diện Swing và Luồng EDT (Ví dụ: Bác thu ngân duy nhất ở siêu thị)

- **Giải thích bằng hình ảnh Bác thu ngân:**
  - Trong siêu thị mini chỉ có **đúng một bác thu ngân** đứng ở quầy. Bác ấy vừa phải làm nhiệm vụ quét mã tính tiền, vừa phải mỉm cười chào khách, trả lời khi khách hỏi giá (đây chính là **EDT — Luồng giao diện** trong Java).
  - Giả sử có một khách hàng đến thanh toán một túi gạo. Thay vì trả tiền ngay, người khách này nói: *"Bác đợi cháu 10 phút, cháu chạy về nhà lấy tiền nhé!"*.
  - Nếu bác thu ngân đứng im đợi người đó 10 phút:
    - Cả hàng dài 30 người xếp hàng phía sau sẽ phải **đứng chôn chân, không ai được phục vụ, cả siêu thị rơi vào tê liệt**.
    - Trên màn hình máy tính, đây chính là hiện tượng: **Ứng dụng bị đơ (Freeze), chuột quay tròn, cửa sổ xám xịt và báo "(Not Responding)"**.

- **Cách giải quyết đúng:**
  - Bác thu ngân bảo khách: *"Cháu chạy sang quầy dịch vụ khách hàng gặp cô nhân viên phụ đằng kia (đây là Luồng nền / Worker Thread) để gửi đồ và xử lý. Khi nào có tiền thì cầm phiếu quay lại bác quét 1 giây là xong!"*.
  - Bác thu ngân vẫn tiếp tục bấm máy, phục vụ những người khác bình thường, không ai bị tắc nghẽn cả.

> **Quy tắc vàng:** Những việc tốn thời gian (như tải báo cáo hàng triệu dòng dữ liệu từ database) không bao giờ được bắt "bác thu ngân" (EDT) làm trực tiếp!

---

### 1.5. JTable và TableModel (Ví dụ: Màn hình Tivi và Chiếc USB)

Nhiều người mới học nhầm tưởng rằng cái bảng trên màn hình (`JTable`) chính là nơi lưu trữ dữ liệu. Thực ra không phải:
- **`JTable` giống như chiếc Màn hình Tivi:** Nó chỉ là một tấm kính để phát hình ảnh cho mắt người nhìn thấy. Bản thân cái tivi không lưu giữ bất kỳ bộ phim nào.
- **`TableModel` giống như Chiếc USB cắm vào Tivi:** Nó là nơi thực sự chứa dữ liệu (danh sách các dòng, các cột).
- **Cách hoạt động:** Khi bạn muốn đổi nội dung chiếu trên màn hình:
  - Bạn không cần đập vỡ màn hình tivi để vẽ lại.
  - Bạn chỉ cần chép dữ liệu mới vào USB (`model.setDataVector(dữ_liệu_mới, tên_cột)`). Chiếc Tivi (`JTable`) thấy USB có nội dung mới sẽ tự động phát hình ảnh mới lên màn hình cho bạn xem!

---

### 1.6. Mô hình 3 tầng (Ví dụ: Quy trình gọi món tại Nhà hàng)

Để không bị nhầm lẫn khi đọc code, hãy hình dung kiến trúc 3 tầng của dự án này giống hệt như cách vận hành của một nhà hàng ẩm thực:

```
[ Khách hàng ] (Người dùng bấm chuột)
       │
       ▼
[ TẦNG 1: GIAO DIỆN (UI) ] === NHÂN VIÊN BỒI BÀN
  - Đưa Menu cho khách xem, nở nụ cười chào đón.
  - Bắt các sự kiện: Khách chọn món gì, số lượng bao nhiêu.
  - Ghi phiếu Order rồi chuyển xuống nhà bếp.
  * LƯU Ý: Bồi bàn không được tự ý nhảy vào bếp nấu nướng (UI không được viết lệnh SQL)!
       │
       ▼
[ TẦNG 2: NGHIỆP VỤ & TRUY CẬP (DAO) ] === ĐẦU BẾP TRƯỞNG
  - Nhận phiếu Order từ bồi bàn.
  - Biết rõ công thức nấu nướng (Tính giá tiền, áp mã khuyến mãi).
  - Điều phối nấu nướng: Món khai vị, món chính, tráng miệng phải xong cùng lúc (Transaction).
  * LƯU Ý: Đầu bếp không bao giờ ra bàn khách đứng cãi nhau hay tiếp khách (DAO không chứa mã đồ họa Swing)!
       │
       ▼
[ TẦNG 3: CƠ SỞ DỮ LIỆU (Database / MySQL) ] === NHÀ KHO & TỦ LẠNH
  - Nơi cất giữ thịt, cá, rau củ theo từng ngăn kệ có đánh số (Bảng và Chỉ mục Index).
  - Đầu bếp cần nguyên liệu gì thì mở kho lấy đúng món đó ra chế biến.
```

---

## 2. Lộ trình đọc code 6 bước (Đi từ dễ đến khó)

Sai lầm lớn nhất của người mới học là vừa mở dự án ra đã bấm ngay vào file giao diện dài 400-500 dòng như `NewInvoicePanel.java`. Bạn sẽ bị "hoa mắt" bởi hàng trăm dòng code căn chỉnh lề, nút bấm và màu sắc.

Hãy đi theo lộ trình chuẩn 6 bước từ ngoài vào trong:

```
[Bước 0: Chạy app] -> [Bước 1: Đọc DB] -> [Bước 2: Kết nối] -> [Bước 3: Dữ liệu] -> [Bước 4: DAO] -> [Bước 5: UI]
```

---

### Bước 0: Bật ứng dụng và nhìn tận mắt

- **Mục đích:** Khi bạn thấy tận mắt màn hình hoạt động như thế nào, não bộ sẽ tự động hiểu *"À, code này viết ra để vẽ cái nút này, bấm nút này thì chạy cái bảng kia"*.
- **Cách làm:**
  1. Chạy file khởi động database: chuột phải chạy `mysql-start.ps1`.
  2. Chạy ứng dụng: chuột phải chạy `run.ps1`.
  3. Mở tab **4. Nhat ky SQL** -> bấm nút *Xóa nhật ký*.
  4. Quay lại tab **1. Lap hoa don** -> chọn một món hàng -> bấm *Thêm vào hóa đơn*.
  5. Quay lại tab **4. Nhat ky SQL** -> bạn sẽ thấy ngay câu lệnh SQL vừa chạy hiện ra trên màn hình. Rất trực quan!

---

### Bước 1: Đọc bảng dữ liệu (sql/) — Ví dụ vì sao giá đổi theo ngày

Mở file [sql/01_schema.sql](file:///d:/DemoBillingManagement/sql/01_schema.sql). Bạn chỉ cần tập trung vào một câu hỏi nghiệp vụ thú vị sau:

> **Câu hỏi:** Tại sao trong bảng Sản phẩm (`Product`) lại **KHÔNG CÓ cột Giá (`Price`)**?
> 
> **Ví dụ thực tế (Cây xăng):**
> - Sáng hôm nay giá xăng là **23.000đ/lít**. Bạn đổ xăng và nhận được một hóa đơn.
> - Đến 15h chiều, nhà nước thông báo giá xăng tăng lên **24.000đ/lít**.
> - Nếu giá được lưu cố định ngay trong bảng Sản phẩm, thì khi kế toán in lại hóa đơn buổi sáng của bạn, hệ thống sẽ lấy nhầm mức giá 24.000đ!
> - Vì vậy, giá không thuộc về riêng sản phẩm, mà thuộc về **Lịch sử thay đổi giá theo ngày**: Bảng `Price_History` lưu cặp `(Mã_sản_phẩm, Ngày_bắt_đầu_áp_dụng, Giá)`. Khi bán hàng vào ngày nào, ta tra giá có hiệu lực của đúng ngày đó!

---

### Bước 2: Tầng kết nối (billing.db) — Cầu nối Java với MySQL

Tầng này chỉ có đúng 3 file, đọc rất nhanh:
1. [Db.java](file:///d:/DemoBillingManagement/src/billing/db/Db.java): Đọc file cấu hình mật khẩu database từ `config.properties` và cấp phát kết nối qua hàm `Db.getConnection()`.
2. [QueryResult.java](file:///d:/DemoBillingManagement/src/billing/db/QueryResult.java): 
   - Khi chạy một câu SQL tra cứu (ví dụ báo cáo doanh thu), kết quả trả về gồm nhiều cột và nhiều dòng.
   - File này là một chiếc hộp đa năng chứa: danh sách tên cột (`List<String> columns`) và ma trận các dòng dữ liệu (`List<Object[]> rows`). Cực kỳ tiện lợi để chuyển thẳng sang cho bảng giao diện hiển thị.
3. [SqlLog.java](file:///d:/DemoBillingManagement/src/billing/db/SqlLog.java): Cuốn sổ ghi chép, mỗi khi chạy câu SQL nào thì lưu lại câu lệnh đó cùng thời gian chạy (mili-giây) để hiển thị lên tab Nhật ký SQL.

---

### Bước 3: Tầng khuôn mẫu (billing.model) — Những "chiếc hộp" đựng dữ liệu

Các file ở đây rất đơn giản, chỉ gồm các biến và hàm `get/set` (như những chiếc hộp đóng gói đồ đạc):
- [Product.java](file:///d:/DemoBillingManagement/src/billing/model/Product.java): Chứa mã vạch, tên sản phẩm, đơn vị tính.
- [CartLine.java](file:///d:/DemoBillingManagement/src/billing/model/CartLine.java): Chứa thông tin một dòng hàng trong giỏ (mua sản phẩm gì, số lượng mấy, đơn giá bao nhiêu, được giảm giá bao nhiêu).
- [PaymentEntry.java](file:///d:/DemoBillingManagement/src/billing/model/PaymentEntry.java): Chứa thông tin trả tiền (khách trả bằng tiền mặt, quẹt thẻ hay quét ví Momo).

---

### Bước 4: Tầng xử lý nghiệp vụ (billing.dao) — Trái tim của hệ thống

Đây là nơi chứa toàn bộ tri thức của dự án. Hãy đọc theo thứ tự:
1. [LookupDao.java](file:///d:/DemoBillingManagement/src/billing/dao/LookupDao.java): Dễ nhất. Chỉ chứa các hàm lấy danh sách quầy thu ngân, danh sách nhân viên để đổ vào ô chọn thả xuống (combobox).
2. [ProductDao.java](file:///d:/DemoBillingManagement/src/billing/dao/ProductDao.java): Xem hàm `priceOn(barcode, date)` — tìm xem vào ngày bán đó thì món hàng có giá bao nhiêu.
3. [InvoiceDao.java](file:///d:/DemoBillingManagement/src/billing/dao/InvoiceDao.java): Xem hàm `createInvoice(...)`. Đây là nơi thực hiện Transaction mẫu mực: vừa lưu hóa đơn, vừa lưu chi tiết từng món hàng, vừa lưu thanh toán.
4. [ReportDao.java](file:///d:/DemoBillingManagement/src/billing/dao/ReportDao.java): Chứa các câu SQL phân tích doanh thu nâng cao.

---

### Bước 5: Tầng giao diện (billing.ui) — Nơi người dùng bấm chuột

Bây giờ bạn đã biết dữ liệu lấy từ đâu và lưu vào đâu, hãy đọc giao diện:
1. [Ui.java](file:///d:/DemoBillingManagement/src/billing/ui/Ui.java): Chứa hàm thần kỳ `Ui.fill(table, queryResult)` — chỉ với 3 dòng code, nó đổ toàn bộ dữ liệu lấy từ database lên mặt bảng `JTable`.
2. [MainWindow.java](file:///d:/DemoBillingManagement/src/billing/ui/MainWindow.java): Tạo cửa sổ chính và 4 tab làm việc.
3. [InvoiceViewPanel.java](file:///d:/DemoBillingManagement/src/billing/ui/InvoiceViewPanel.java): Bấm chọn một hóa đơn bên trái $\rightarrow$ hiển thị chi tiết tiền bạc bên phải.
4. [NewInvoicePanel.java](file:///d:/DemoBillingManagement/src/billing/ui/NewInvoicePanel.java): Màn hình lập hóa đơn với giỏ hàng và các nút thanh toán.

---

## 3. Những cạm bẫy kinh điển (Kèm ví dụ thực tế Sai vs Đúng)

Dưới đây là 5 sai lầm phổ biến nhất mà lập trình viên rất hay mắc phải. Chúng tôi đưa ra ví dụ trực quan để bạn thấy hậu quả thực tế:

---

### Cạm bẫy 1: Nối chuỗi SQL dẫn đến bị Hack mất dữ liệu (SQL Injection)

#### 📖 Câu chuyện thực tế:
Bạn làm một ô nhập mã vạch để tìm sản phẩm. 
- Người dùng bình thường sẽ gõ: `8934001`.
- Kẻ xấu (Hacker) không gõ mã vạch mà cố tình gõ vào ô nhập chuỗi ký tự ma quái: `' OR '1'='1`.

#### ❌ Code SAI (Nối chuỗi ngây thơ):
```java
String barcode = txtBarcode.getText(); // Người dùng nhập: ' OR '1'='1
String sql = "SELECT * FROM Product WHERE Barcode = '" + barcode + "'";
Statement st = conn.createStatement();
ResultSet rs = st.executeQuery(sql);
```
- **Hậu quả kinh hoàng:** Câu SQL gửi tới MySQL sẽ bị biến thành:
  ```sql
  SELECT * FROM Product WHERE Barcode = '' OR '1'='1'
  ```
  Vì `'1'='1'` luôn luôn đúng, MySQL sẽ **trả về toàn bộ sạch sẽ dữ liệu bí mật của công ty** cho hacker! Thậm chí hacker có thể gõ `; DROP TABLE Product; --` để xóa sạch toàn bộ danh mục hàng hóa trong 1 giây!

#### ✅ Code ĐÚNG (Dùng dấu hỏi chấm `?` với PreparedStatement):
```java
String sql = "SELECT * FROM Product WHERE Barcode = ?";
try (PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, txtBarcode.getText());
    try (ResultSet rs = ps.executeQuery()) {
        // Xử lý dữ liệu an toàn 100%
    }
}
```
- **Giải thích dễ hiểu:** Dấu `?` giống như một **chiếc hộp đựng đồ có dán tem niêm phong**. Dù hacker có nhét chữ gì vào ô nhập, MySQL cũng hiểu đó chỉ là một chuỗi văn bản bình thường để tìm kiếm, không bao giờ đem ra chạy như mã lệnh.

---

### Cạm bẫy 2: Quên Transaction làm dữ liệu bị "nửa nạc nửa mỡ"

#### ❌ Code SAI (Để tự động Commit):
```java
// Mặc định mỗi lệnh chạy xong là lưu chết vào database ngay
insertInvoice(conn, "HD001");         // Bước 1: Lưu hóa đơn -> THÀNH CÔNG!
insertLines(conn, danhSachMonHang);   // Bước 2: Bị mất điện đột ngột hoặc lỗi mạng -> BỊ VĂNG LỖI!
insertPayment(conn, 150000);          // Bước 3: Chưa kịp chạy
```
- **Hậu quả:** Trong database tồn tại hóa đơn `HD001`, nhưng mở ra thì không có món hàng nào và chưa thu đồng tiền nào. Kế toán cuối tháng không thể nào cân đối sổ sách!

#### ✅ Code ĐÚNG (Quản lý Transaction chặt chẽ):
Xem trong [InvoiceDao.java dòng 76-179](file:///d:/DemoBillingManagement/src/billing/dao/InvoiceDao.java#L76-L179):
```java
try {
    conn.setAutoCommit(false); // BẬT CHẾ ĐỘ AN TOÀN: Chưa cho phép lưu vội!

    insertInvoice(conn, "HD001");
    insertLines(conn, danhSachMonHang);
    insertPayment(conn, 150000);

    conn.commit(); // TẤT CẢ ĐỀU XONG: Bây giờ mới chốt lưu vĩnh viễn!
} catch (Exception e) {
    if (conn != null) {
        conn.rollback(); // CÓ LỖI XẢY RA: Xóa sạch các bước dang dở, trở về như cũ!
    }
    throw e;
} finally {
    if (conn != null) {
        conn.setAutoCommit(true); // Trả lại trạng thái mặc định
        conn.close();
    }
}
```

---

### Cạm bẫy 3: Tính tiền bằng số thực làm mất tiền của khách

#### 📖 Câu chuyện thực tế:
Tại sao máy tính siêu thông minh đôi khi lại tính toán số học ngô nghê như học sinh lớp 1?
Hãy thử mở Java lên và gõ lệnh in sau:
```java
System.out.println(0.1 + 0.2);
// Kết quả in ra màn hình: 0.30000000000000004  (KHÔNG PHẢI LÀ 0.3!)
```
- **Nguyên nhân đời thường:** Trong hệ thập phân của con người, phép tính $1 / 3 = 0.333333...$ không thể viết hết. Tương tự như vậy, trong bộ nhớ nhị phân của máy tính (chỉ gồm 0 và 1), số $0.1$ và $0.2$ là các số thập phân vô hạn tuần hoàn. Kiểu dữ liệu `double` hay `float` bị xén bớt phần đuôi, gây ra sai số li ti.
- **Hậu quả kế toán:** Nếu một siêu thị bán 100.000 đơn hàng mỗi ngày, sai số vài cent lẻ tích lũy qua năm tháng sẽ làm lệch sổ sách kế toán hàng chục triệu đồng!

#### ❌ Code SAI:
```java
double donGia = 19.99;
int soLuong = 3;
double tongTien = donGia * soLuong; // Sai số lẻ thập phân âm thầm xuất hiện!
```

#### ✅ Code ĐÚNG:
```java
// Tiền nong trong Java BẮT BUỘC dùng BigDecimal
BigDecimal donGia = new BigDecimal("19.99"); // Luôn truyền dạng chuỗi ""
BigDecimal soLuong = new BigDecimal("3");

// Tính nhân và làm tròn đúng 2 chữ số thập phân (chuẩn kế toán)
BigDecimal tongTien = donGia.multiply(soLuong).setScale(2, RoundingMode.HALF_UP);
```

---

### Cạm bẫy 4: Bắt hệ thống chạy đi chạy lại 50 lần thay vì gom vào 1 xe đẩy (Batching)

#### 📖 Ví dụ đời thường (Đi siêu thị):
Bạn cần mua 50 gói mì tôm ở siêu thị:
- **Cách ngô nghê:** Bạn đi đến kệ mì lấy 1 gói $\rightarrow$ chạy ra quầy thu ngân tính tiền $\rightarrow$ chạy quay lại kệ lấy gói thứ 2 $\rightarrow$ chạy ra tính tiền... Cứ thế làm đủ 50 chuyến! Bạn sẽ kiệt sức và quầy thu ngân sẽ bị tắc nghẽn.
- **Cách thông minh:** Bạn lấy một **chiếc xe đẩy**, gom đủ 50 gói mì vào xe, rồi đẩy một lần duy nhất ra quầy tính tiền!

#### ❌ Code SAI (Chạy 50 chuyến qua mạng internet):
```java
for (CartLine hang : danhSach50Mon) {
    ps.setString(1, hang.getBarcode());
    ps.setBigDecimal(2, hang.getQuantity());
    ps.executeUpdate(); // Gửi mạng đi 1 dòng... Chờ MySQL trả lời... Lặp lại 50 lần!
}
```

#### ✅ Code ĐÚNG (Dùng JDBC Batching — Xe đẩy hàng):
Xem trong [InvoiceDao.java dòng 108-111](file:///d:/DemoBillingManagement/src/billing/dao/InvoiceDao.java#L108-L111):
```java
for (CartLine hang : danhSach50Mon) {
    ps.setString(1, hang.getBarcode());
    ps.setBigDecimal(2, hang.getQuantity());
    ps.addBatch(); // XẾP VÀO XE ĐẨY: Gom vào bộ nhớ tạm, chưa gửi vội
}
ps.executeBatch(); // ĐẨY XE ĐI: Gửi toàn bộ 50 dòng xuống MySQL trong 1 lượt duy nhất!
```

---

### Cạm bẫy 5: Bắt giao diện làm việc nặng khiến ứng dụng bị đơ (Freeze)

#### ❌ Code SAI:
```java
btnXemBaoCao.addActionListener(e -> {
    // Bác thu ngân (EDT) phải tự mình ngồi tính toán báo cáo mất 5 giây!
    QueryResult ketQua = ReportDao.tinhDoanhThuTrieuDongHang(); 
    Ui.fill(table, ketQua);
});
// HẬU QUẢ: Màn hình app chết đứng trong 5 giây, bấm vào đâu cũng không có phản hồi!
```

#### ✅ Hướng dẫn tư duy ĐÚNG:
Với các tác vụ tính toán lâu, ta dùng một luồng phụ (luồng làm việc ngầm bên dưới — Background Thread) để tính toán, trong lúc đó màn hình vẫn hiển thị biểu tượng quay tròn hoặc cho phép người dùng bấm nút "Hủy". Khi luồng phụ tính xong, nó mới nhẹ nhàng gửi kết quả cho giao diện hiển thị.

---

## 4. Những "đường vòng" gây tốn thời gian cần tránh

Khi học codebase này, có 3 cái "bẫy tư duy" khiến bạn mất rất nhiều thời gian mà không học thêm được gì bổ ích:

1. **Đừng ngồi tỉ mẩn đọc từng dòng chỉnh lề `GridBagLayout` trong UI:**
   - Các đoạn code như `c.gridx = 0; c.gridy = 1; c.insets = new Insets(...)` chỉ đơn thuần là bảo nút này nằm bên trái, ô kia nằm bên phải. Nó không chứa chút tư duy lập trình nào cả. Hãy lướt qua thật nhanh!
2. **Đừng thắc mắc: "Sao không dùng Spring Boot cho nhàn?":**
   - Mục đích của dự án này là cho bạn thấy **"bên dưới nắp ca-pô của chiếc xe ô tô có những gì"**. 
   - Framework như Spring Boot rất tiện, nhưng nó giấu hết mọi thứ bên dưới. Khi bạn tự tay nhìn thấy `conn.setAutoCommit(false)` và `try-with-resources`, sau này khi dùng `@Transactional` của Spring bị lỗi, bạn sẽ biết chính xác nguyên nhân gốc rễ nằm ở đâu để sửa.
3. **Đừng kéo cả triệu dòng dữ liệu về Java rồi mới dùng code để tính tổng:**
   - Đừng viết lệnh lấy hết 1 triệu dòng hóa đơn về bộ nhớ RAM rồi dùng vòng lặp `for` để cộng tiền.
   - MySQL được viết bằng ngôn ngữ C++ siêu nhanh và tối ưu hóa tận ổ cứng. Hãy để MySQL làm phép tính `SUM(Total_Amount)` và chỉ trả về cho Java đúng 1 con số kết quả duy nhất!

---

## 5. Bảng dịch thuật: Đối chiếu từ Java thuần sang Spring Boot & React

Nếu bạn đã từng làm Web với Spring Boot và React, bảng dịch thuật dưới đây sẽ giúp bạn "kết nối nơ-ron thần kinh" ngay lập tức:

| Trong dự án này (Java Swing + JDBC thuần) | Tương đương trong Web hiện đại (Spring Boot + React) | Giải thích bản chất bằng đời thường |
|---|---|---|
| `Db.getConnection()` | `HikariCP DataSource` (Connection Pool) | Dự án này mỗi lần cần thì xin 1 kết nối mới rồi đóng. Spring thì nuôi sẵn 1 đàn 10 kết nối túc trực sẵn trong bể để dùng ngay. |
| `QueryResult.java` | `JdbcTemplate` trong Spring | Chiếc hộp bọc ngoài giúp việc gửi SQL và lấy kết quả trở nên gọn gàng, không phải viết đi viết lại vòng lặp. |
| `InvoiceDao`, `ProductDao` | Tầng `@Repository` trong Spring | Nơi chuyên lo việc giao tiếp với cơ sở dữ liệu, tách biệt hoàn toàn khỏi màn hình hiển thị. |
| `conn.setAutoCommit(false)` + `conn.commit()` | `@Transactional` trong Spring | Cơ chế đảm bảo tính toàn vẹn: Tất cả cùng thành công hoặc hủy hết nếu có lỗi. |
| Dấu hỏi chấm `?` trong `PreparedStatement` | Tham số `:param` trong Spring Data JPA | Chiếc hộp niêm phong dữ liệu để hacker không thể chèn mã độc phá hoại. |
| `JTable` + `DefaultTableModel` | Thẻ `<table />` + React `useState` | Tách biệt giữa **Màn hình hiển thị (View)** và **Dữ liệu thực tế (State/Model)**. |
| `btn.addActionListener(e -> ...)` | Sự kiện `onClick={() => ...}` trong React | Lắng nghe khi nào người dùng bấm chuột thì chạy đoạn code tương ứng. |
| `Ui.fill(table, result)` | `setTableData(response.data)` trong React | Đổ dữ liệu vừa lấy được vào bảng để giao diện tự động vẽ lại. |

---

## 6. Thực hành: Theo chân một ổ bánh mì từ lúc bấm chuột đến khi lưu vào ổ cứng

Để kiểm tra xem bạn đã thực sự hiểu toàn bộ dự án hay chưa, hãy nhắm mắt lại và tưởng tượng luồng đi của hành động: **Người dùng chọn món "Bánh mì pate" và bấm nút "Thêm vào hóa đơn"**:

```
[1] Người dùng bấm nút "Thêm vào hóa đơn" trên màn hình
     │
     ▼ (Sự kiện click được gửi đến Bác thu ngân EDT)
[2] Hàm NewInvoicePanel.addToCart() được gọi:
     ├── Đọc mã vạch món hàng từ ô chọn: ví dụ "8934001"
     ├── Đọc ngày lập hóa đơn từ ô ngày: ví dụ hôm nay 2026-09-22
     │
     ▼ (Nhờ Đầu bếp DAO đi tra cứu giá tiền)
[3] Gọi Backend: ProductDao.priceOn("8934001", 2026-09-22)
     ├── Mở đường ống kết nối Db.getConnection() tới MySQL
     ├── Gửi câu lệnh: "Tìm giá của bánh mì có ngày áp dụng gần nhất trước ngày hôm nay"
     ├── MySQL kiểm tra sổ sách và báo lại: "Giá là 25.000đ"
     └── Tự động ngắt kết nối an toàn (try-with-resources)
     │
     ▼ (Đầu bếp kiểm tra tiếp xem có giảm giá không)
[4] Gọi Backend: ProductDao.activePromoOn("8934001", 2026-09-22)
     └── MySQL báo lại: "Hôm nay khai trương, giảm giá 10% (còn 22.500đ)"
     │
     ▼ (Đóng gói hàng vào Giỏ)
[5] Tạo ra một dòng hàng CartLine(Bánh mì, Số lượng: 1, Đơn giá: 25.000, Giảm: 2.500)
     └── Nhét món này vào danh sách giỏ hàng tạm thời trên bộ nhớ
     │
     ▼ (Chiếu kết quả lên Tivi cho khách xem)
[6] Cập nhật giao diện:
     ├── Chép danh sách giỏ hàng mới vào USB TableModel
     ├── Màn hình JTable tự động vẽ thêm dòng "Bánh mì pate"
     └── Ô tổng tiền nhảy số: "Cần thanh toán: 22.500 VNĐ"
```

Khi bạn có thể tự mình giải thích được 6 bước nhịp nhàng này cho một người bạn khác nghe, bạn đã chính thức **làm chủ hoàn toàn 100% codebase** của dự án này!
