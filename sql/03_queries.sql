USE retail_billing;

-- Query 1: Full details of one invoice (its lines and its totals)
SET @inv = (SELECT Invoice_ID FROM Invoice
            WHERE  Invoice_ID = CONCAT('S01-C01-',
                       DATE_FORMAT((SELECT MIN(`Date`) FROM Invoice), '%Y%m%d'), '-0001'));

SELECT CONCAT('=== QUERY 1: Invoice details ', @inv, ' ===') AS ' ';

SELECT  l.Line_Number                              AS Line_Number,
        p.Name                                     AS Product_Name,
        l.Quantity                                 AS Quantity,
        p.Unit                                     AS Unit,
        l.Unit_Price                               AS Unit_Price,
        l.Discount                                 AS Discount,
        (l.Quantity * l.Unit_Price - l.Discount)   AS Line_Total
FROM    Invoice_Line l
JOIN    Product      p ON p.Barcode = l.Barcode
WHERE   l.Invoice_ID = @inv
ORDER BY l.Line_Number;

SELECT  i.Invoice_ID                                        AS Invoice_ID,
        i.`Date`                                            AS Date,
        i.`Time`                                            AS Time,
        s.Shop_ID                                           AS Shop,
        c.Name                                              AS Cashier,
        COALESCE(cu.Name, '(walk-in customer)')             AS Customer,
        SUM(l.Quantity * l.Unit_Price)                      AS Subtotal_Before_Discount,
        SUM(l.Discount)                                     AS Total_Discount,
        SUM(l.Quantity * l.Unit_Price - l.Discount)         AS TOTAL_DUE
FROM    Invoice      i
JOIN    Counter      ct ON ct.Counter_ID = i.Counter_ID
JOIN    Shop         s  ON s.Shop_ID     = ct.Shop_ID
JOIN    Cashier      c  ON c.Cashier_ID  = i.Cashier_ID
LEFT JOIN Customer   cu ON cu.Customer_ID = i.Customer_ID
JOIN    Invoice_Line l  ON l.Invoice_ID  = i.Invoice_ID
WHERE   i.Invoice_ID = @inv
GROUP BY i.Invoice_ID, i.`Date`, i.`Time`, s.Shop_ID, c.Name, cu.Name;


-- Query 2: Daily revenue of each shop in the last 7 days
SELECT '=== QUERY 2: Revenue for the last 7 days by shop ===' AS ' ';

SELECT  s.Shop_ID                                     AS Shop,
        s.Address                                     AS Address,
        i.`Date`                                      AS Date,
        COUNT(DISTINCT i.Invoice_ID)                  AS Invoice_Count,
        ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount
                  - fn_line_refund(l.Invoice_ID, l.Line_Number)), 2) AS Revenue
FROM    Invoice      i
JOIN    Counter      ct ON ct.Counter_ID = i.Counter_ID
JOIN    Shop         s  ON s.Shop_ID     = ct.Shop_ID
JOIN    Invoice_Line l  ON l.Invoice_ID  = i.Invoice_ID
WHERE   fn_invoice_in_revenue(i.Invoice_ID) = 1
  AND   i.`Date` BETWEEN DATE_SUB(CURDATE(), INTERVAL 7 DAY)
                     AND DATE_SUB(CURDATE(), INTERVAL 1 DAY)
GROUP BY s.Shop_ID, s.Address, i.`Date`
ORDER BY i.`Date` DESC, s.Shop_ID;


-- Query 3: Products whose price went up more than 10% this year
SELECT '=== QUERY 3: Products with price increase > 10% this year ===' AS ' ';

SELECT  p.Barcode                                                              AS Barcode,
        p.Name                                                                 AS Product,
        old_price.Price                                                        AS Old_Price,
        new_price.Price                                                        AS New_Price,
        new_price.Valid_From_Date                                              AS Valid_From,
        ROUND((new_price.Price - old_price.Price) / old_price.Price * 100, 2)  AS Increase_Pct
FROM       Price_History new_price
JOIN       Price_History old_price
        ON old_price.Barcode = new_price.Barcode
       AND old_price.Valid_From_Date = (
               SELECT MAX(h.Valid_From_Date)
               FROM   Price_History h
               WHERE  h.Barcode = new_price.Barcode
                 AND  h.Valid_From_Date < new_price.Valid_From_Date
           )
JOIN       Product p ON p.Barcode = new_price.Barcode
WHERE   YEAR(new_price.Valid_From_Date) = YEAR(CURDATE())
  AND   new_price.Price > old_price.Price * 1.10
ORDER BY Increase_Pct DESC;


-- Query 4: Cashier with the highest average invoice value
SELECT '=== QUERY 4: Cashier with highest average basket value ===' AS ' ';

SELECT  c.Cashier_ID                      AS Cashier_ID,
        c.Name                            AS Cashier_Name,
        COUNT(*)                          AS Invoice_Count,
        ROUND(SUM(t.Total), 2)            AS Total_Revenue,
        ROUND(AVG(t.Total), 2)            AS Avg_Basket_Value
FROM    Cashier c
JOIN    Invoice i ON i.Cashier_ID = c.Cashier_ID
                 AND fn_invoice_in_revenue(i.Invoice_ID) = 1
JOIN   (SELECT Invoice_ID,
               SUM(Quantity * Unit_Price - Discount
                   - fn_line_refund(Invoice_ID, Line_Number)) AS Total
        FROM   Invoice_Line
        GROUP BY Invoice_ID) t  ON t.Invoice_ID = i.Invoice_ID
GROUP BY c.Cashier_ID, c.Name
ORDER BY Avg_Basket_Value DESC;


-- Query 5: Invoices where more than half of the value was returned
SELECT '=== QUERY 5: Invoices with return amount > 50% ===' AS ' ';

SELECT  i.Invoice_ID                                         AS Invoice_ID,
        i.`Date`                                             AS Date,
        inv_tot.Inv_Total                                    AS Invoice_Total,
        ROUND(ret.Return_Amt, 2)                             AS Return_Amount,
        ROUND(ret.Return_Amt / inv_tot.Inv_Total * 100, 2)   AS Return_Pct
FROM    Invoice i
JOIN   (SELECT Invoice_ID,
               SUM(Quantity * Unit_Price - Discount) AS Inv_Total
        FROM   Invoice_Line
        GROUP BY Invoice_ID) inv_tot  ON inv_tot.Invoice_ID = i.Invoice_ID
JOIN   (SELECT r.Invoice_ID,
               SUM(fn_return_refund(r.Invoice_ID, r.Line_Number, r.Return_Quantity)) AS Return_Amt
        FROM   `Return` r
        GROUP BY r.Invoice_ID) ret  ON ret.Invoice_ID = i.Invoice_ID
WHERE   fn_invoice_in_revenue(i.Invoice_ID) = 1
  AND   ret.Return_Amt > inv_tot.Inv_Total / 2
ORDER BY Return_Pct DESC;


-- Query 6: Customers who buy often and buy many different products (HAVING)
SELECT '=== QUERY 6: Frequent customers with varied purchases (HAVING) ===' AS ' ';

SELECT cu.Customer_ID                          AS Customer_ID,
       cu.Name                                 AS Customer_Name,
       COUNT(DISTINCT i.Invoice_ID)            AS Invoice_Count,
       COUNT(DISTINCT l.Barcode)               AS Distinct_Product_Count,
       ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount
                 - fn_line_refund(l.Invoice_ID, l.Line_Number)), 2) AS Total_Spending
FROM   Customer cu
JOIN   Invoice i      ON i.Customer_ID = cu.Customer_ID
                     AND fn_invoice_in_revenue(i.Invoice_ID) = 1
JOIN   Invoice_Line l ON l.Invoice_ID  = i.Invoice_ID
GROUP BY cu.Customer_ID, cu.Name
HAVING COUNT(DISTINCT i.Invoice_ID) >= 12
   AND COUNT(DISTINCT l.Barcode)    >= 15
ORDER BY Total_Spending DESC;


-- Query 7: Customers who bought products on promotion and also returned something (IN + EXISTS)
SELECT '=== QUERY 7: Customers who bought promoted products and made a return (IN + EXISTS) ===' AS ' ';

SELECT cu.Customer_ID AS Customer_ID,
       cu.Name        AS Customer_Name,
       cu.Phone       AS Phone_Number
FROM   Customer cu
WHERE  cu.Customer_ID IN (
           SELECT i.Customer_ID
           FROM   Invoice      i
           JOIN   Invoice_Line l ON l.Invoice_ID = i.Invoice_ID
           WHERE  i.Customer_ID IS NOT NULL
             AND  fn_invoice_in_revenue(i.Invoice_ID) = 1
             AND  l.Barcode IN (
                      SELECT a.Barcode
                      FROM   Applies_To a
                      JOIN   Promotion  pr ON pr.Promo_ID = a.Promo_ID
                      WHERE  pr.Status = 'ACTIVE'
                        AND  pr.Expiry_Date >= CURDATE()
                        AND  CURDATE() BETWEEN a.Promo_Start_date AND a.Promo_End_date
                  )
       )
  AND  EXISTS (
           SELECT 1
           FROM   Invoice i2
           JOIN   `Return` r ON r.Invoice_ID = i2.Invoice_ID
           WHERE  i2.Customer_ID = cu.Customer_ID
       )
ORDER BY cu.Customer_ID;


-- Query 8: Customers who bought ALL products of promotion PR01 (division, NOT EXISTS)
SELECT '=== QUERY 8: Customers who bought ALL products of PR01 (NOT EXISTS) ===' AS ' ';

SELECT cu.Customer_ID AS Customer_ID,
       cu.Name        AS Customer_Name
FROM   Customer cu
WHERE  NOT EXISTS (
           SELECT 1
           FROM   Applies_To a
           WHERE  a.Promo_ID = 'PR01'
             AND  NOT EXISTS (
                      SELECT 1
                      FROM   Invoice      i
                      JOIN   Invoice_Line l ON l.Invoice_ID = i.Invoice_ID
                      WHERE  i.Customer_ID = cu.Customer_ID
                        AND  l.Barcode     = a.Barcode
                        AND  fn_invoice_in_revenue(i.Invoice_ID) = 1
                  )
       )
ORDER BY cu.Customer_ID;


-- Query 9: Number of payments and total amount by payment method
SELECT '=== QUERY 9: Value and count statistics by payment method ===' AS ' ';

SELECT
    CASE
        WHEN cp.Payment_ID  IS NOT NULL THEN 'Card'
        WHEN ep.Payment_ID  IS NOT NULL THEN 'E-Wallet'
        WHEN csh.Payment_ID IS NOT NULL THEN 'Cash'
        ELSE 'Other'
    END                      AS Payment_Method,
    COUNT(p.Payment_ID)      AS Transaction_Count,
    ROUND(SUM(p.Amount), 2)  AS Total_Payment_Amount
FROM Payment p
JOIN Invoice i ON i.Invoice_ID = p.Invoice_ID AND fn_invoice_in_revenue(i.Invoice_ID) = 1
LEFT JOIN Card_Payment    cp  ON cp.Payment_ID  = p.Payment_ID
LEFT JOIN EWallet_Payment ep  ON ep.Payment_ID  = p.Payment_ID
LEFT JOIN Cash_Payment    csh ON csh.Payment_ID = p.Payment_ID
GROUP BY Payment_Method
ORDER BY Total_Payment_Amount DESC;


-- Query 10: Top 5 best-selling products by quantity
SELECT '=== QUERY 10: Top best-selling products by quantity ===' AS ' ';

SELECT p.Barcode AS Barcode,
       p.Name    AS Product_Name,
       p.Unit    AS Unit,
       SUM(l.Quantity - fn_line_returned_qty(l.Invoice_ID, l.Line_Number)) AS Total_Quantity_Sold,
       ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount
                 - fn_line_refund(l.Invoice_ID, l.Line_Number)), 2)        AS Total_Product_Revenue
FROM Product p
JOIN Invoice_Line l ON l.Barcode = p.Barcode
JOIN Invoice i ON i.Invoice_ID = l.Invoice_ID AND fn_invoice_in_revenue(i.Invoice_ID) = 1
GROUP BY p.Barcode, p.Name, p.Unit
ORDER BY Total_Quantity_Sold DESC
LIMIT 5;


-- Query 11: Total discount given by each promotion
SELECT '=== QUERY 11: Total discount statistics by promotion program ===' AS ' ';

SELECT pr.Promo_ID               AS Promo_ID,
       pr.Rule_Description       AS Program_Description,
       pr.Status                 AS Status,
       COUNT(*)                  AS Times_Applied,
       ROUND(SUM(l.Discount), 2) AS Total_Discount_Given
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
  AND  fn_invoice_in_revenue(i.Invoice_ID) = 1
GROUP BY pr.Promo_ID, pr.Rule_Description, pr.Status
ORDER BY Total_Discount_Given DESC;


-- Query 12: Products on promotion now OR returned before (UNION)
SELECT '=== QUERY 12: Products on promotion OR returned before (UNION) ===' AS ' ';

SELECT p.Barcode AS Barcode, p.Name AS Product_Name, 'On promotion' AS Reason
FROM   Product p
JOIN   Applies_To a  ON a.Barcode   = p.Barcode
JOIN   Promotion  pr ON pr.Promo_ID = a.Promo_ID
WHERE  pr.Status = 'ACTIVE'
  AND  pr.Expiry_Date >= CURDATE()
  AND  CURDATE() BETWEEN a.Promo_Start_date AND a.Promo_End_date
UNION
SELECT p.Barcode, p.Name, 'Returned before'
FROM   Product p
JOIN   Invoice_Line l ON l.Barcode = p.Barcode
JOIN   `Return`     r ON r.Invoice_ID  = l.Invoice_ID
                     AND r.Line_Number = l.Line_Number
ORDER BY Barcode, Reason;


-- Query 13: Cash invoices with the most change given back (uses 2 views)
SELECT '=== QUERY 13: Cash invoices with the most change given (VIEW) ===' AS ' ';

SELECT inv_tot.Invoice_ID              AS Invoice_ID,
       i.`Date`                        AS Date,
       ROUND(inv_tot.Inv_Total, 2)     AS Invoice_Total,
       ROUND(cash.Tendered, 2)         AS Cash_Tendered,
       ROUND(cash.Change_Due, 2)       AS Change_Given
FROM  (SELECT Invoice_ID, SUM(Total) AS Inv_Total
       FROM   vw_invoice_line_total
       GROUP BY Invoice_ID) inv_tot
JOIN  (SELECT Invoice_ID,
              SUM(Tendered_Amount) AS Tendered,
              SUM(Change_Due)      AS Change_Due
       FROM   vw_cash_change_due
       GROUP BY Invoice_ID) cash ON cash.Invoice_ID = inv_tot.Invoice_ID
JOIN   Invoice i ON i.Invoice_ID = inv_tot.Invoice_ID
WHERE  fn_invoice_in_revenue(i.Invoice_ID) = 1
  AND  cash.Change_Due > 0
ORDER BY cash.Change_Due DESC
LIMIT 10;


-- Proof: old invoices keep their old prices
SELECT '=== BONUS: Old invoices still keep old prices ===' AS ' ';

SELECT  p.Name                                     AS Product,
        l.Unit_Price                               AS Price_On_Invoice,
        fn_get_product_price(l.Barcode, CURDATE()) AS Today_Price,
        COUNT(*)                                   AS Line_Item_Count,
        MIN(i.`Date`)                              AS Sold_From_Date,
        MAX(i.`Date`)                              AS Sold_To_Date
FROM    Invoice_Line l
JOIN    Invoice      i ON i.Invoice_ID = l.Invoice_ID
JOIN    Product      p ON p.Barcode    = l.Barcode
WHERE   l.Unit_Price <> fn_get_product_price(l.Barcode, CURDATE())
GROUP BY p.Name, l.Unit_Price, l.Barcode
ORDER BY p.Name, l.Unit_Price;
