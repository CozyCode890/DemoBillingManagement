-- =====================================================================
--  03_queries.sql  --  5 câu truy vấn đề bài yêu cầu
--  Đây cũng chính là các câu SQL mà app Java chạy.
--  Chạy bằng:  mysql -u root -p retail_billing < 03_queries.sql
-- =====================================================================

USE retail_billing;

-- =====================================================================
-- CÂU 1: Một hóa đơn đầy đủ -- các dòng, giảm giá và tổng tiền
-- ---------------------------------------------------------------------
-- Ý tưởng: Invoice_Line JOIN Product để lấy tên sản phẩm.
-- Thành tiền mỗi dòng = Quantity * Unit_Price - Discount.
--
-- Mã hóa đơn có dạng <Shop>-<Counter>-<YYYYMMDD>-<SEQ> (BR1) và ngày trong
-- mã là ngày THẬT của hóa đơn, nên không gõ cứng được một mã cố định.
-- Hóa đơn mẫu của seed là hóa đơn ĐẦU TIÊN được sinh ra: quầy C01 (shop S01),
-- ngày CURDATE() - 90, số thứ tự 0001 -- seed ép nó có 3 dòng, dòng 1 là
-- 8934001 với SL 2 (02_seed.sql, tìm 'v_sample').
-- Muốn xem hóa đơn khác thì đổi mã trong câu SET, ví dụ:
--     SET @inv = (SELECT Invoice_ID FROM Invoice WHERE Invoice_ID = 'S02-C03-20261120-0001');
-- @inv được đọc TỪ BẢNG (không gán thẳng chuỗi) để mang collation của cột
-- Invoice_ID; gán chuỗi thẳng thì MySQL báo "Illegal mix of collations" khi
-- so sánh, vì biến mang collation của kết nối.
-- =====================================================================
SET @inv = (SELECT Invoice_ID FROM Invoice
            WHERE  Invoice_ID = CONCAT('S01-C01-',
                       DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 90 DAY), '%Y%m%d'), '-0001'));

SELECT CONCAT('=== CAU 1: Chi tiet hoa don ', @inv, ' ===') AS ' ';

SELECT  l.Line_Number                              AS Dong,
        p.Name                                     AS San_pham,
        l.Quantity                                 AS So_luong,
        p.Unit                                     AS Don_vi,
        l.Unit_Price                               AS Don_gia,
        l.Discount                                 AS Giam_gia,
        (l.Quantity * l.Unit_Price - l.Discount)   AS Thanh_tien
FROM    Invoice_Line l
JOIN    Product      p ON p.Barcode = l.Barcode
WHERE   l.Invoice_ID = @inv
ORDER BY l.Line_Number;

-- Phần tổng của hóa đơn đó
SELECT  i.Invoice_ID                                        AS Ma_HD,
        i.`Date`                                            AS Ngay,
        i.`Time`                                            AS Gio,
        s.Shop_ID                                           AS Cua_hang,
        c.Name                                              AS Thu_ngan,
        COALESCE(cu.Name, '(khach vang lai)')               AS Khach_hang,
        SUM(l.Quantity * l.Unit_Price)                      AS Tong_truoc_giam,
        SUM(l.Discount)                                     AS Tong_giam_gia,
        SUM(l.Quantity * l.Unit_Price - l.Discount)         AS TONG_PHAI_TRA
FROM    Invoice      i
JOIN    Counter      ct ON ct.Counter_ID = i.Counter_ID
JOIN    Shop         s  ON s.Shop_ID     = ct.Shop_ID
JOIN    Cashier      c  ON c.Cashier_ID  = i.Cashier_ID
LEFT JOIN Customer   cu ON cu.Customer_ID = i.Customer_ID   -- LEFT vi khach co the NULL
JOIN    Invoice_Line l  ON l.Invoice_ID  = i.Invoice_ID
WHERE   i.Invoice_ID = @inv
GROUP BY i.Invoice_ID, i.`Date`, i.`Time`, s.Shop_ID, c.Name, cu.Name;


-- =====================================================================
-- CÂU 2: Doanh thu theo NGÀY của từng CỬA HÀNG, 7 ngày gần nhất
-- ---------------------------------------------------------------------
-- Đường đi: Invoice -> Counter -> Shop  (hóa đơn không trỏ thẳng tới Shop!)
-- GROUP BY 2 cột (shop, ngày) => mỗi ô là doanh thu 1 shop trong 1 ngày.
--
-- DOANH THU = HÓA ĐƠN ĐIỀU CHỈNH (Invoice_Line − Return), áp dụng cho MỌI câu
-- tính doanh thu trong file này (câu 2, 4, 5, 6, 7, 8, 10):
--   * Hóa đơn được tính khi fn_invoice_in_revenue(...) = 1, tức là PAID.
--     Trả hàng KHÔNG đổi trạng thái hóa đơn (BR16: hóa đơn gốc giữ PAID), nên
--     hóa đơn có trả hàng vẫn được tính; VOIDED (Manager hủy, BR3) thì bị loại.
--   * Tiền của một dòng = thành tiền − fn_line_refund(dòng đó).
-- Hai hàm nằm ở 04_routines.sql (mục 1.10–1.12) -- bản chép DUY NHẤT của quy
-- tắc, để các câu ở đây và báo cáo trong app luôn ra cùng một con số.
--
-- "7 ngày gần nhất" = 7 NGÀY ĐÃ TRỌN (hôm qua lùi về 7 ngày), KHÔNG tính hôm
-- nay vì ngày hôm nay chưa kết thúc -- giống hệt sp_report_daily_takings.
-- =====================================================================
SELECT '=== CAU 2: Doanh thu 7 ngay gan nhat theo cua hang ===' AS ' ';

SELECT  s.Shop_ID                                     AS Cua_hang,
        s.Address                                     AS Dia_chi,
        i.`Date`                                      AS Ngay,
        COUNT(DISTINCT i.Invoice_ID)                  AS So_hoa_don,
        ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount
                  - fn_line_refund(l.Invoice_ID, l.Line_Number)), 2) AS Doanh_thu
FROM    Invoice      i
JOIN    Counter      ct ON ct.Counter_ID = i.Counter_ID
JOIN    Shop         s  ON s.Shop_ID     = ct.Shop_ID
JOIN    Invoice_Line l  ON l.Invoice_ID  = i.Invoice_ID
WHERE   fn_invoice_in_revenue(i.Invoice_ID) = 1
  AND   i.`Date` BETWEEN DATE_SUB(CURDATE(), INTERVAL 7 DAY)
                     AND DATE_SUB(CURDATE(), INTERVAL 1 DAY)
GROUP BY s.Shop_ID, s.Address, i.`Date`
ORDER BY i.`Date` DESC, s.Shop_ID;


-- =====================================================================
-- CÂU 3: Sản phẩm tăng giá hơn 10% trong năm nay
-- ---------------------------------------------------------------------
-- Kỹ thuật: SELF JOIN -- nối bảng Price_History với CHÍNH NÓ.
--   gia_moi = một dòng giá bất kỳ trong năm nay
--   gia_cu  = dòng giá NGAY TRƯỚC ĐÓ của cùng sản phẩm
-- Subquery MAX(...) tìm "ngày hiệu lực lớn nhất mà vẫn nhỏ hơn ngày mới".
-- =====================================================================
SELECT '=== CAU 3: San pham tang gia > 10% trong nam nay ===' AS ' ';

SELECT  p.Barcode                                                      AS Ma_vach,
        p.Name                                                         AS San_pham,
        gia_cu.Price                                                   AS Gia_cu,
        gia_moi.Price                                                  AS Gia_moi,
        gia_moi.Valid_From_Date                                        AS Ap_dung_tu,
        ROUND((gia_moi.Price - gia_cu.Price) / gia_cu.Price * 100, 2)  AS Phan_tram_tang
FROM       Price_History gia_moi
JOIN       Price_History gia_cu
        ON gia_cu.Barcode = gia_moi.Barcode
       AND gia_cu.Valid_From_Date = (
               SELECT MAX(h.Valid_From_Date)
               FROM   Price_History h
               WHERE  h.Barcode = gia_moi.Barcode
                 AND  h.Valid_From_Date < gia_moi.Valid_From_Date
           )
JOIN       Product p ON p.Barcode = gia_moi.Barcode
WHERE   YEAR(gia_moi.Valid_From_Date) = YEAR(CURDATE())
  AND   gia_moi.Price > gia_cu.Price * 1.10
ORDER BY Phan_tram_tang DESC;


-- =====================================================================
-- CÂU 4: Thu ngân có giá trị hóa đơn trung bình cao nhất
-- ---------------------------------------------------------------------
-- Không thể AVG() thẳng trên Invoice_Line được, vì như vậy là trung bình
-- của từng DÒNG chứ không phải từng HÓA ĐƠN.
-- => Bước 1: subquery tính tổng tiền MỖI hóa đơn.
--    Bước 2: AVG() trên kết quả đó, gom theo thu ngân.
-- =====================================================================
SELECT '=== CAU 4: Thu ngan co gio hang trung binh cao nhat ===' AS ' ';

SELECT  c.Cashier_ID                      AS Ma_TN,
        c.Name                            AS Ten_thu_ngan,
        COUNT(*)                          AS So_hoa_don,
        ROUND(SUM(t.Tong), 2)             AS Tong_doanh_thu,
        ROUND(AVG(t.Tong), 2)             AS Gio_hang_TB
FROM    Cashier c
JOIN    Invoice i ON i.Cashier_ID = c.Cashier_ID
                 AND fn_invoice_in_revenue(i.Invoice_ID) = 1
JOIN   (SELECT Invoice_ID,
               SUM(Quantity * Unit_Price - Discount
                   - fn_line_refund(Invoice_ID, Line_Number)) AS Tong
        FROM   Invoice_Line
        GROUP BY Invoice_ID) t  ON t.Invoice_ID = i.Invoice_ID
GROUP BY c.Cashier_ID, c.Name
ORDER BY Gio_hang_TB DESC;
-- Muốn CHỈ lấy người cao nhất thì thêm:  LIMIT 1


-- =====================================================================
-- CÂU 5: Hóa đơn bị trả hàng quá NỬA giá trị
-- ---------------------------------------------------------------------
-- Giá trị trả lại của 1 lượt trả được tính theo TỶ LỆ (BR16):
--     Effective_Price = (Quantity * Unit_Price - Discount) / Quantity
--     Amount_Refunded = Return_Quantity * Effective_Price
-- Nhờ vậy phần giảm giá cũng được trừ đúng tỷ lệ. Công thức nằm DUY NHẤT ở
-- fn_return_refund (04_routines.sql mục 1.5) -- làm tròn theo từng lượt trả,
-- đúng con số đã hiện cho khách lúc trả, và đúng con số của
-- sp_report_big_returns trong app.
-- =====================================================================
SELECT '=== CAU 5: Hoa don co tien tra hang > 50% ===' AS ' ';

SELECT  i.Invoice_ID                                                AS Ma_HD,
        i.`Date`                                                    AS Ngay,
        tong.Tong_HD                                                AS Tong_hoa_don,
        ROUND(tra.Tien_tra, 2)                                      AS Tien_tra_lai,
        ROUND(tra.Tien_tra / tong.Tong_HD * 100, 2)                 AS Phan_tram_tra
FROM    Invoice i
JOIN   (SELECT Invoice_ID,
               SUM(Quantity * Unit_Price - Discount) AS Tong_HD
        FROM   Invoice_Line
        GROUP BY Invoice_ID) tong  ON tong.Invoice_ID = i.Invoice_ID
JOIN   (SELECT r.Invoice_ID,
               SUM(fn_return_refund(r.Invoice_ID, r.Line_Number, r.Return_Quantity)) AS Tien_tra
        FROM   `Return` r
        GROUP BY r.Invoice_ID) tra  ON tra.Invoice_ID = i.Invoice_ID
WHERE   fn_invoice_in_revenue(i.Invoice_ID) = 1
  AND   tra.Tien_tra > tong.Tong_HD / 2
ORDER BY Phan_tram_tra DESC;


-- =====================================================================
-- BONUS: chứng minh "hóa đơn cũ giữ giá cũ"
-- So sánh đơn giá đã ghi trên hóa đơn với giá HIỆN TẠI của sản phẩm.
--
-- GOM theo (sản phẩm, đơn giá đã đóng băng) thay vì liệt kê từng dòng:
-- với seed 91 ngày, mốc đổi giá nằm GIỮA cửa sổ hóa đơn nên "giá cũ trên
-- hóa đơn cũ" xảy ra ở hàng trăm dòng. Mỗi dòng kết quả trả lời đúng câu
-- hỏi của đề bài: "có bao nhiêu dòng hàng đang ghi giá này, và giá hôm nay
-- là bao nhiêu".
--
-- Lọc theo "đơn giá khác giá hôm nay", tức là theo ĐỊNH NGHĨA của "giữ giá
-- cũ" (BR8), giống hệt sp_report_old_price_proof trong app. Với seed hiện
-- tại, các mốc đổi giá nằm giữa cửa sổ 91 ngày nên mọi dòng hàng bán trước
-- một mốc đổi giá đều hiện ở đây.
-- =====================================================================
SELECT '=== BONUS: Hoa don cu van giu gia cu ===' AS ' ';

SELECT  p.Name                                     AS San_pham,
        l.Unit_Price                               AS Gia_ghi_tren_HD,
        fn_get_product_price(l.Barcode, CURDATE()) AS Gia_hom_nay,
        COUNT(*)                                   AS So_dong_hang,
        MIN(i.`Date`)                              AS Ban_tu_ngay,
        MAX(i.`Date`)                              AS Ban_den_ngay
FROM    Invoice_Line l
JOIN    Invoice      i ON i.Invoice_ID = l.Invoice_ID
JOIN    Product      p ON p.Barcode    = l.Barcode
WHERE   l.Unit_Price <> fn_get_product_price(l.Barcode, CURDATE())
GROUP BY p.Name, l.Unit_Price, l.Barcode
ORDER BY p.Name, l.Unit_Price;


-- =====================================================================
-- BONUS 2: truy vấn TÌM GIÁ ĐÚNG -- app Java dùng đúng câu này mỗi khi
-- bạn thêm một sản phẩm vào hóa đơn.
--   "Lấy dòng giá có ngày hiệu lực gần nhất nhưng KHÔNG vượt quá ngày bán"
-- =====================================================================
SELECT '=== BONUS 2: Tim gia hop le tai mot ngay ===' AS ' ';

SELECT  Price AS Gia_ap_dung
FROM    Price_History
WHERE   Barcode = '8934008'
  AND   Valid_From_Date <= CURDATE()
ORDER BY Valid_From_Date DESC
LIMIT 1;

-- =====================================================================  
-- CÂU 6: Top khách hàng thân thiết có tổng chi tiêu cao nhất  
-- ---------------------------------------------------------------------  
-- Ý tưởng: Customer JOIN Invoice (đã thanh toán) JOIN Invoice_Line.  
-- Tính tổng tiền thực trả (Quantity * Unit_Price - Discount) theo từng khách hàng.  
-- =====================================================================  
SELECT '=== CAU 6: Top khach hang co tong chi tieu cao nhat ===' AS ' ';  

SELECT cu.Customer_ID AS Ma_KH,  
       cu.Name AS Ten_Khach_Hang,  
       cu.Phone AS So_Dien_Thoai,  
       COUNT(DISTINCT i.Invoice_ID) AS So_Luong_Hoa_Don,  
       ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount - fn_line_refund(l.Invoice_ID, l.Line_Number)), 2) AS Tong_Tien_Chi_Tieu  
FROM Customer cu  
JOIN Invoice i ON i.Customer_ID = cu.Customer_ID AND fn_invoice_in_revenue(i.Invoice_ID) = 1  
JOIN Invoice_Line l ON l.Invoice_ID = i.Invoice_ID  
GROUP BY cu.Customer_ID, cu.Name, cu.Phone  
ORDER BY Tong_Tien_Chi_Tieu DESC  
LIMIT 5;


-- =====================================================================  
-- CÂU 7: Thống kê doanh thu theo từng Quầy tính tiền (Counter)  
-- ---------------------------------------------------------------------  
-- Đường đi: Shop -> Counter -> Invoice -> Invoice_Line.  
-- Khác với Câu 2 gom theo Shop và Ngày, câu này gom theo từng quầy bán cụ thể.  
-- =====================================================================  
SELECT '=== CAU 7: Thong ke doanh thu theo tung Quay tinh tien ===' AS ' ';  

SELECT s.Shop_ID AS Ma_Cua_Hang,  
       ct.Counter_ID AS Ma_Quay,  
       s.Address AS Dia_Chi_Shop,  
       COUNT(i.Invoice_ID) AS Tong_So_Hoa_Don,  
       ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount - fn_line_refund(l.Invoice_ID, l.Line_Number)), 2) AS Tong_Doanh_Thu_Quay  
FROM Shop s  
JOIN Counter ct ON ct.Shop_ID = s.Shop_ID  
JOIN Invoice i ON i.Counter_ID = ct.Counter_ID AND fn_invoice_in_revenue(i.Invoice_ID) = 1  
JOIN Invoice_Line l ON l.Invoice_ID = i.Invoice_ID  
GROUP BY s.Shop_ID, ct.Counter_ID, s.Address  
ORDER BY Tong_Doanh_Thu_Quay DESC;


-- =====================================================================  
-- CÂU 8: Hiệu suất làm việc của Thu ngân (Số hóa đơn xử lý & Tổng doanh thu)  
-- ---------------------------------------------------------------------  
-- Ý tưởng: Cashier JOIN Invoice (tính vào doanh thu) JOIN Invoice_Line.  
-- Khác với Câu 4 là tính trung bình giỏ hàng, câu này xếp hạng theo tổng lượng việc xử lý.  
-- =====================================================================  
SELECT '=== CAU 8: Hieu suat xu ly hoa don cua tung thu ngan ===' AS ' ';  

SELECT c.Cashier_ID AS Ma_Thu_Ngan,  
       c.Name AS Ten_Thu_Ngan,  
       COUNT(i.Invoice_ID) AS Tong_So_Hoa_Don_Xu_Ly,  
       ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount - fn_line_refund(l.Invoice_ID, l.Line_Number)), 2) AS Tong_Doanh_Thu_Thu_Ngan  
FROM Cashier c  
JOIN Invoice i ON i.Cashier_ID = c.Cashier_ID AND fn_invoice_in_revenue(i.Invoice_ID) = 1  
JOIN Invoice_Line l ON l.Invoice_ID = i.Invoice_ID  
GROUP BY c.Cashier_ID, c.Name  
ORDER BY Tong_So_Hoa_Don_Xu_Ly DESC;


-- =====================================================================  
-- CÂU 9: Thống kê giá trị và số lượng theo phương thức thanh toán  
-- ---------------------------------------------------------------------  
-- Kỹ thuật: Dùng CASE WHEN kết hợp LEFT JOIN các bảng con (Card, EWallet, Cash)  
-- để phân loại chính xác hình thức khách hàng dùng để trả tiền.  
-- =====================================================================  
SELECT '=== CAU 9: Thong ke gia tri va so luong theo phuong thuc thanh toan ===' AS ' ';  

SELECT   
    CASE   
        WHEN cp.Payment_ID IS NOT NULL THEN 'The (Card)'  
        WHEN ep.Payment_ID IS NOT NULL THEN 'Vi dien tu (E-Wallet)'  
        WHEN csh.Payment_ID IS NOT NULL THEN 'Tien mat (Cash)'  
        ELSE 'Khac'  
    END AS Phuong_Thuc_Thanh_Toan,  
    COUNT(p.Payment_ID) AS So_Luot_Giao_Dich,  
    ROUND(SUM(p.Amount), 2) AS Tong_Tien_Thanh_Toan  
FROM Payment p  
LEFT JOIN Card_Payment cp ON cp.Payment_ID = p.Payment_ID  
LEFT JOIN EWallet_Payment ep ON ep.Payment_ID = p.Payment_ID  
LEFT JOIN Cash_Payment csh ON csh.Payment_ID = p.Payment_ID  
GROUP BY Phuong_Thuc_Thanh_Toan  
ORDER BY Tong_Tien_Thanh_Toan DESC;


-- =====================================================================  
-- CÂU 10: Top sản phẩm bán chạy nhất theo tổng số lượng tiêu thụ  
-- ---------------------------------------------------------------------  
-- Ý tưởng: Product JOIN Invoice_Line JOIN Invoice (hóa đơn tính vào doanh thu).
-- Số lượng bán = số lượng mua − số lượng đã trả (fn_line_returned_qty).  
-- Gom nhóm theo sản phẩm để tìm ra mặt hàng tiêu thụ mạnh nhất.  
-- =====================================================================  
SELECT '=== CAU 10: Top san pham ban chay nhat theo so luong ===' AS ' ';  

SELECT p.Barcode AS Ma_Vach,  
       p.Name AS Ten_SanPham,  
       p.Unit AS Don_Vi,  
       SUM(l.Quantity - fn_line_returned_qty(l.Invoice_ID, l.Line_Number)) AS Tong_So_Luong_Ban,  
       ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount - fn_line_refund(l.Invoice_ID, l.Line_Number)), 2) AS Tong_Doanh_Thu_SanPham  
FROM Product p  
JOIN Invoice_Line l ON l.Barcode = p.Barcode  
JOIN Invoice i ON i.Invoice_ID = l.Invoice_ID AND fn_invoice_in_revenue(i.Invoice_ID) = 1  
GROUP BY p.Barcode, p.Name, p.Unit  
ORDER BY Tong_So_Luong_Ban DESC  
LIMIT 5;


-- =====================================================================  
-- CÂU 11: Thống kê hiệu quả giảm giá theo các chương trình khuyến mãi  
-- ---------------------------------------------------------------------  
-- Đường đi: Invoice_Line -> Invoice (ngày bán) -> chương trình ĐÃ ÁP DỤNG.
-- Đo lường tổng số tiền thực tế hệ thống đã giảm cho khách qua từng chương trình.
--
-- Invoice_Line chỉ lưu SỐ TIỀN giảm, không lưu Promo_ID (BR11, giữ 3NF), nên
-- chương trình của một dòng được suy lại đúng như lúc bán: chương trình tốt
-- nhất còn hiệu lực TẠI NGÀY BÁN, bằng nhau thì hết hạn sớm hơn -- cùng điều
-- kiện và thứ tự với sp_get_active_promo. Mỗi dòng có giảm giá thuộc ĐÚNG
-- MỘT chương trình: nối thẳng Invoice_Line với Applies_To theo Barcode (bản
-- cũ) sẽ đếm một dòng cho MỌI chương trình từng áp lên sản phẩm đó, kể cả
-- chương trình chưa bắt đầu hoặc đã hết hạn vào ngày bán.
-- =====================================================================
SELECT '=== CAU 11: Thong ke tong tien giam gia theo chuong trinh khuyen mai ===' AS ' ';

SELECT pr.Promo_ID              AS Ma_Khuyen_Mai,
       pr.Rule_Description      AS Mo_Ta_Chuong_Trinh,
       pr.Status                AS Trang_Thai,
       COUNT(*)                 AS So_Luot_Ap_Dung,
       ROUND(SUM(l.Discount), 2) AS Tong_Tien_Da_Giam
FROM   Invoice_Line l
JOIN   Invoice      i  ON i.Invoice_ID = l.Invoice_ID
JOIN   Promotion    pr ON pr.Promo_ID = (
           SELECT a2.Promo_ID
           FROM   Applies_To a2
           JOIN   Promotion  p2 ON p2.Promo_ID = a2.Promo_ID
           WHERE  a2.Barcode = l.Barcode
             AND  i.`Date` BETWEEN a2.Promo_Start_date AND a2.Promo_End_date
             AND  p2.Status = 'ACTIVE'
             AND  p2.Expiry_Date >= i.`Date`
           ORDER BY a2.Discount_Percent DESC, p2.Expiry_Date ASC, a2.Promo_ID ASC
           LIMIT 1)
WHERE  l.Discount > 0
GROUP BY pr.Promo_ID, pr.Rule_Description, pr.Status
ORDER BY Tong_Tien_Da_Giam DESC;


-- =====================================================================  
-- CÂU 12: Thống kê số lượng nhân viên cấp dưới trực thuộc của quản lý  
-- ---------------------------------------------------------------------  
-- Kỹ thuật: SELF JOIN bảng Cashier (sếp lớn nối với nhân viên cấp dưới qua Supervisor_ID).  
-- =====================================================================  
SELECT '=== CAU 12: Thong ke so luong nhan vien cap duoi cua moi quan ly ===' AS ' ';  

SELECT s.Cashier_ID AS Ma_Quan_Ly,  
       s.Name AS Ten_Quan_Ly,  
       COUNT(e.Cashier_ID) AS So_Nhan_Vien_Cap_Duoi  
FROM Cashier s  
LEFT JOIN Cashier e ON e.Supervisor_ID = s.Cashier_ID  
WHERE s.Supervisor_ID IS NULL  
GROUP BY s.Cashier_ID, s.Name;