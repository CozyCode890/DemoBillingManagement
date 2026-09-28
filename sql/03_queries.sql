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
-- =====================================================================
SELECT '=== CAU 1: Chi tiet hoa don INV-0001 ===' AS ' ';

SELECT  l.Line_Number                              AS Dong,
        p.Name                                     AS San_pham,
        l.Quantity                                 AS So_luong,
        p.Unit                                     AS Don_vi,
        l.Unit_Price                               AS Don_gia,
        l.Discount                                 AS Giam_gia,
        (l.Quantity * l.Unit_Price - l.Discount)   AS Thanh_tien
FROM    Invoice_Line l
JOIN    Product      p ON p.Barcode = l.Barcode
WHERE   l.Invoice_ID = 'INV-0001'
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
WHERE   i.Invoice_ID = 'INV-0001'
GROUP BY i.Invoice_ID, i.`Date`, i.`Time`, s.Shop_ID, c.Name, cu.Name;


-- =====================================================================
-- CÂU 2: Doanh thu theo NGÀY của từng CỬA HÀNG, 7 ngày gần nhất
-- ---------------------------------------------------------------------
-- Đường đi: Invoice -> Counter -> Shop  (hóa đơn không trỏ thẳng tới Shop!)
-- GROUP BY 2 cột (shop, ngày) => mỗi ô là doanh thu 1 shop trong 1 ngày.
-- Lọc Status = 'PAID' để bỏ hóa đơn bị hủy.
-- =====================================================================
SELECT '=== CAU 2: Doanh thu 7 ngay gan nhat theo cua hang ===' AS ' ';

SELECT  s.Shop_ID                                     AS Cua_hang,
        s.Address                                     AS Dia_chi,
        i.`Date`                                      AS Ngay,
        COUNT(DISTINCT i.Invoice_ID)                  AS So_hoa_don,
        SUM(l.Quantity * l.Unit_Price - l.Discount)   AS Doanh_thu
FROM    Invoice      i
JOIN    Counter      ct ON ct.Counter_ID = i.Counter_ID
JOIN    Shop         s  ON s.Shop_ID     = ct.Shop_ID
JOIN    Invoice_Line l  ON l.Invoice_ID  = i.Invoice_ID
WHERE   i.Status = 'PAID'
  AND   i.`Date` >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
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
JOIN    Invoice i ON i.Cashier_ID = c.Cashier_ID AND i.Status = 'PAID'
JOIN   (SELECT Invoice_ID,
               SUM(Quantity * Unit_Price - Discount) AS Tong
        FROM   Invoice_Line
        GROUP BY Invoice_ID) t  ON t.Invoice_ID = i.Invoice_ID
GROUP BY c.Cashier_ID, c.Name
ORDER BY Gio_hang_TB DESC;
-- Muốn CHỈ lấy người cao nhất thì thêm:  LIMIT 1


-- =====================================================================
-- CÂU 5: Hóa đơn bị trả hàng quá NỬA giá trị
-- ---------------------------------------------------------------------
-- Giá trị trả lại của 1 dòng được tính theo TỶ LỆ:
--     (số lượng trả / số lượng mua) * thành tiền của dòng
-- Nhờ vậy phần giảm giá cũng được trừ đúng tỷ lệ.
-- =====================================================================
SELECT '=== CAU 5: Hoa don co tien tra hang > 50% ===' AS ' ';

SELECT  i.Invoice_ID                                                AS Ma_HD,
        i.`Date`                                                    AS Ngay,
        tong.Tong_HD                                                AS Tong_hoa_don,
        tra.Tien_tra                                                AS Tien_tra_lai,
        ROUND(tra.Tien_tra / tong.Tong_HD * 100, 2)                 AS Phan_tram_tra
FROM    Invoice i
JOIN   (SELECT Invoice_ID,
               SUM(Quantity * Unit_Price - Discount) AS Tong_HD
        FROM   Invoice_Line
        GROUP BY Invoice_ID) tong  ON tong.Invoice_ID = i.Invoice_ID
JOIN   (SELECT r.Invoice_ID,
               SUM( r.Return_Quantity / l.Quantity
                    * (l.Quantity * l.Unit_Price - l.Discount) ) AS Tien_tra
        FROM   `Return` r
        JOIN   Invoice_Line l
               ON l.Invoice_ID  = r.Invoice_ID
              AND l.Line_Number = r.Line_Number
        GROUP BY r.Invoice_ID) tra  ON tra.Invoice_ID = i.Invoice_ID
WHERE   tra.Tien_tra > tong.Tong_HD / 2
ORDER BY Phan_tram_tra DESC;


-- =====================================================================
-- BONUS: chứng minh "hóa đơn cũ giữ giá cũ"
-- So sánh đơn giá đã ghi trên hóa đơn với giá HIỆN TẠI của sản phẩm.
-- =====================================================================
SELECT '=== BONUS: Hoa don cu van giu gia cu ===' AS ' ';

SELECT  l.Invoice_ID                AS Ma_HD,
        i.`Date`                    AS Ngay_ban,
        p.Name                      AS San_pham,
        l.Unit_Price                AS Gia_tren_hoa_don,
        (SELECT h.Price
         FROM   Price_History h
         WHERE  h.Barcode = l.Barcode
           AND  h.Valid_From_Date <= CURDATE()
         ORDER BY h.Valid_From_Date DESC
         LIMIT 1)                   AS Gia_hom_nay
FROM    Invoice_Line l
JOIN    Invoice i ON i.Invoice_ID = l.Invoice_ID
JOIN    Product p ON p.Barcode    = l.Barcode
WHERE   l.Invoice_ID LIKE 'INV-OLD%'
ORDER BY l.Invoice_ID, l.Line_Number;


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
