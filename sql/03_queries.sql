-- =====================================================================
--  03_queries.sql  --  Required Queries
--  This is also the SQL script executed by the Java app.
--  Run via:  mysql -u root -p retail_billing < 03_queries.sql
-- =====================================================================

USE retail_billing;

-- =====================================================================
-- QUERY 1: Detailed Invoice -- items, discounts, and grand total
-- ---------------------------------------------------------------------
-- Idea: Invoice_Line JOIN Product to get product names.
-- Line Total = Quantity * Unit_Price - Discount.
-- =====================================================================
SELECT '=== QUERY 1: Detailed Invoice INV-0001 ===' AS ' ';

SELECT  l.Line_Number                              AS Line_Number,
        p.Name                                     AS Product_Name,
        l.Quantity                                 AS Quantity,
        p.Unit                                     AS Unit,
        l.Unit_Price                               AS Unit_Price,
        l.Discount                                 AS Discount,
        (l.Quantity * l.Unit_Price - l.Discount)   AS Line_Total
FROM    Invoice_Line l
JOIN    Product      p ON p.Barcode = l.Barcode
WHERE   l.Invoice_ID = 'INV-0001'
ORDER BY l.Line_Number;

-- Summary section of that invoice
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
LEFT JOIN Customer   cu ON cu.Customer_ID = i.Customer_ID   -- LEFT because customer can be NULL
JOIN    Invoice_Line l  ON l.Invoice_ID  = i.Invoice_ID
WHERE   i.Invoice_ID = 'INV-0001'
GROUP BY i.Invoice_ID, i.`Date`, i.`Time`, s.Shop_ID, c.Name, cu.Name;


-- =====================================================================
-- QUERY 2: Revenue by DATE of each SHOP, last 7 days
-- ---------------------------------------------------------------------
-- Path: Invoice -> Counter -> Shop (invoices do not point directly to Shop!)
-- GROUP BY 2 columns (shop, date) => each cell is 1 shop's revenue on 1 day.
-- Filter Status = 'PAID' to exclude cancelled invoices.
-- =====================================================================
SELECT '=== QUERY 2: Revenue for the last 7 days by shop ===' AS ' ';

SELECT  s.Shop_ID                                     AS Shop,
        s.Address                                     AS Address,
        i.`Date`                                      AS Date,
        COUNT(DISTINCT i.Invoice_ID)                  AS Invoice_Count,
        SUM(l.Quantity * l.Unit_Price - l.Discount)   AS Revenue
FROM    Invoice      i
JOIN    Counter      ct ON ct.Counter_ID = i.Counter_ID
JOIN    Shop         s  ON s.Shop_ID     = ct.Shop_ID
JOIN    Invoice_Line l  ON l.Invoice_ID  = i.Invoice_ID
WHERE   i.Status = 'PAID'
  AND   i.`Date` >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
GROUP BY s.Shop_ID, s.Address, i.`Date`
ORDER BY i.`Date` DESC, s.Shop_ID;


-- =====================================================================
-- QUERY 3: Products with price increase > 10% this year
-- ---------------------------------------------------------------------
-- Technique: SELF JOIN -- join Price_History with ITSELF.
--   new_price = any price row in the current year
--   old_price = the price row IMMEDIATELY PRECEDING it for the same product
-- Subquery MAX(...) finds "the latest effective date that is still earlier than the new date".
-- =====================================================================
SELECT '=== QUERY 3: Products with price increase > 10% this year ===' AS ' ';

SELECT  p.Barcode                                                      AS Barcode,
        p.Name                                                         AS Product,
        old_price.Price                                                AS Old_Price,
        new_price.Price                                                AS New_Price,
        new_price.Valid_From_Date                                      AS Valid_From,
        ROUND((new_price.Price - old_price.Price) / old_price.Price * 100, 2) AS Increase_Pct
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


-- =====================================================================
-- QUERY 4: Cashier with the highest average invoice value
-- ---------------------------------------------------------------------
-- Cannot AVG() directly on Invoice_Line, because that averages each LINE
-- rather than each INVOICE.
-- => Step 1: subquery calculates total amount per invoice.
--    Step 2: AVG() on that result, grouped by cashier.
-- =====================================================================
SELECT '=== QUERY 4: Cashier with highest average basket value ===' AS ' ';

SELECT  c.Cashier_ID                      AS Cashier_ID,
        c.Name                            AS Cashier_Name,
        COUNT(*)                          AS Invoice_Count,
        ROUND(SUM(t.Total), 2)            AS Total_Revenue,
        ROUND(AVG(t.Total), 2)            AS Avg_Basket_Value
FROM    Cashier c
JOIN    Invoice i ON i.Cashier_ID = c.Cashier_ID AND i.Status = 'PAID'
JOIN   (SELECT Invoice_ID,
               SUM(Quantity * Unit_Price - Discount) AS Total
        FROM   Invoice_Line
        GROUP BY Invoice_ID) t  ON t.Invoice_ID = i.Invoice_ID
GROUP BY c.Cashier_ID, c.Name
ORDER BY Avg_Basket_Value DESC;
-- To get ONLY the top cashier, add:  LIMIT 1


-- =====================================================================
-- QUERY 5: Invoices with returned items exceeding HALF their value
-- ---------------------------------------------------------------------
-- Return value of 1 line is calculated PROPORTIONALLY:
--     (return quantity / purchased quantity) * line total
-- This ensures discounts are also deducted in exact proportion.
-- =====================================================================
SELECT '=== QUERY 5: Invoices with return amount > 50% ===' AS ' ';

SELECT  i.Invoice_ID                                                AS Invoice_ID,
        i.`Date`                                                    AS Date,
        inv_tot.Inv_Total                                           AS Invoice_Total,
        ret.Return_Amt                                              AS Return_Amount,
        ROUND(ret.Return_Amt / inv_tot.Inv_Total * 100, 2)          AS Return_Pct
FROM    Invoice i
JOIN   (SELECT Invoice_ID,
               SUM(Quantity * Unit_Price - Discount) AS Inv_Total
        FROM   Invoice_Line
        GROUP BY Invoice_ID) inv_tot  ON inv_tot.Invoice_ID = i.Invoice_ID
JOIN   (SELECT r.Invoice_ID,
               SUM( r.Return_Quantity / l.Quantity
                    * (l.Quantity * l.Unit_Price - l.Discount) ) AS Return_Amt
        FROM   `Return` r
        JOIN   Invoice_Line l
               ON l.Invoice_ID  = r.Invoice_ID
              AND l.Line_Number = r.Line_Number
        GROUP BY r.Invoice_ID) ret  ON ret.Invoice_ID = i.Invoice_ID
WHERE   ret.Return_Amt > inv_tot.Inv_Total / 2
ORDER BY Return_Pct DESC;


-- =====================================================================
-- BONUS: prove that "old invoices retain old prices"
-- Compares the unit price recorded on the invoice with the CURRENT product price.
--
-- GROUP BY (product, frozen unit price) instead of listing every single line:
-- with a 91-day seed, the price change milestone falls IN THE MIDDLE of the invoice window,
-- so "old price on old invoice" happens across hundreds of lines. Each result row directly
-- answers the prompt's question: "how many line items record this price, and what is
-- today's price".
--
-- The old version filtered `LIKE 'INV-OLD%'` while `sp_report_old_price_proof`
-- had NO `WHERE` clause -- two copies of the same report had diverged.
-- Now both filter by "unit price differs from today's price", i.e., by the DEFINITION
-- of "retaining old prices" rather than an invoice ID prefix (plan/15 section 5.3).
-- The two INV-OLD-* invoices remain in the result, just no longer as the sole proof.
-- =====================================================================
SELECT '=== BONUS: Old invoices still retain old prices ===' AS ' ';

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


-- =====================================================================
-- BONUS 2: FIND VALID PRICE query -- the Java app runs this exact query
-- whenever you add a product to an invoice.
--   "Fetch the price row with the latest effective date NOT exceeding the sale date"
-- =====================================================================
SELECT '=== BONUS 2: Find valid price on a given date ===' AS ' ';

SELECT  Price AS Applicable_Price
FROM    Price_History
WHERE   Barcode = '8934008'
  AND   Valid_From_Date <= CURDATE()
ORDER BY Valid_From_Date DESC
LIMIT 1;


-- =====================================================================  
-- QUERY 6: Top loyal customers with the highest total spending  
-- ---------------------------------------------------------------------  
-- Idea: Customer JOIN Invoice (paid) JOIN Invoice_Line.  
-- Calculate net amount paid (Quantity * Unit_Price - Discount) per customer.  
-- =====================================================================  
SELECT '=== QUERY 6: Top customers with highest total spending ===' AS ' ';  

SELECT cu.Customer_ID AS Customer_ID,  
       cu.Name AS Customer_Name,  
       cu.Phone AS Phone_Number,  
       COUNT(DISTINCT i.Invoice_ID) AS Total_Invoices,  
       ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount), 0) AS Total_Spending  
FROM Customer cu  
JOIN Invoice i ON i.Customer_ID = cu.Customer_ID AND i.Status = 'PAID'  
JOIN Invoice_Line l ON l.Invoice_ID = i.Invoice_ID  
GROUP BY cu.Customer_ID, cu.Name, cu.Phone  
ORDER BY Total_Spending DESC  
LIMIT 5;


-- =====================================================================  
-- QUERY 7: Revenue statistics by Checkout Counter  
-- ---------------------------------------------------------------------  
-- Path: Shop -> Counter -> Invoice -> Invoice_Line.  
-- Unlike Query 2 which groups by Shop and Date, this groups by each specific counter.  
-- =====================================================================  
SELECT '=== QUERY 7: Revenue statistics by checkout counter ===' AS ' ';  

SELECT s.Shop_ID AS Shop_ID,  
       ct.Counter_ID AS Counter_ID,  
       s.Address AS Shop_Address,  
       COUNT(DISTINCT i.Invoice_ID) AS Total_Invoices,  
       ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount), 0) AS Total_Counter_Revenue  
FROM Shop s  
JOIN Counter ct ON ct.Shop_ID = s.Shop_ID  
JOIN Invoice i ON i.Counter_ID = ct.Counter_ID AND i.Status = 'PAID'  
JOIN Invoice_Line l ON l.Invoice_ID = i.Invoice_ID  
GROUP BY s.Shop_ID, ct.Counter_ID, s.Address  
ORDER BY Total_Counter_Revenue DESC;


-- =====================================================================  
-- QUERY 8: Cashier performance (Invoices processed & Total revenue)  
-- ---------------------------------------------------------------------  
-- Idea: Cashier JOIN Invoice (Status = 'PAID') JOIN Invoice_Line.  
-- Unlike Query 4 which calculates average basket value, this ranks by total workload.  
-- =====================================================================  
SELECT '=== QUERY 8: Invoice processing performance by cashier ===' AS ' ';  

SELECT c.Cashier_ID AS Cashier_ID,  
       c.Name AS Cashier_Name,  
       COUNT(DISTINCT i.Invoice_ID) AS Total_Invoices_Processed,  
       ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount), 0) AS Total_Cashier_Revenue  
FROM Cashier c  
JOIN Invoice i ON i.Cashier_ID = c.Cashier_ID AND i.Status = 'PAID'  
JOIN Invoice_Line l ON l.Invoice_ID = i.Invoice_ID  
GROUP BY c.Cashier_ID, c.Name  
ORDER BY Total_Invoices_Processed DESC;


-- =====================================================================  
-- QUERY 9: Value and transaction count statistics by payment method  
-- ---------------------------------------------------------------------  
-- Technique: Use CASE WHEN combined with LEFT JOINs on subtypes (Card, EWallet, Cash)  
-- to accurately classify the payment method used by customers.  
-- =====================================================================  
SELECT '=== QUERY 9: Value and count statistics by payment method ===' AS ' ';  

SELECT   
    CASE   
        WHEN cp.Payment_ID IS NOT NULL THEN 'Card'  
        WHEN ep.Payment_ID IS NOT NULL THEN 'E-Wallet'  
        WHEN csh.Payment_ID IS NOT NULL THEN 'Cash'  
        ELSE 'Other'  
    END AS Payment_Method,  
    COUNT(p.Payment_ID) AS Transaction_Count,  
    ROUND(SUM(p.Amount), 0) AS Total_Payment_Amount  
FROM Payment p  
LEFT JOIN Card_Payment cp ON cp.Payment_ID = p.Payment_ID  
LEFT JOIN EWallet_Payment ep ON ep.Payment_ID = p.Payment_ID  
LEFT JOIN Cash_Payment csh ON csh.Payment_ID = p.Payment_ID  
GROUP BY Payment_Method  
ORDER BY Total_Payment_Amount DESC;


-- =====================================================================  
-- QUERY 10: Top best-selling products by total quantity sold  
-- ---------------------------------------------------------------------  
-- Idea: Product JOIN Invoice_Line JOIN Invoice (only count PAID invoices).  
-- Group by product to find the highest-selling items.  
-- =====================================================================  
SELECT '=== QUERY 10: Top best-selling products by quantity ===' AS ' ';  

SELECT p.Barcode AS Barcode,  
       p.Name AS Product_Name,  
       p.Unit AS Unit,  
       SUM(l.Quantity) AS Total_Quantity_Sold,  
       ROUND(SUM(l.Quantity * l.Unit_Price - l.Discount), 0) AS Total_Product_Revenue  
FROM Product p  
JOIN Invoice_Line l ON l.Barcode = p.Barcode  
JOIN Invoice i ON i.Invoice_ID = l.Invoice_ID AND i.Status = 'PAID'  
GROUP BY p.Barcode, p.Name, p.Unit  
ORDER BY Total_Quantity_Sold DESC  
LIMIT 5;


-- =====================================================================  
-- QUERY 11: Discount effectiveness statistics by promotion program  
-- ---------------------------------------------------------------------  
-- Path: Promotion -> Applies_To -> Invoice_Line -> Invoice.  
-- Measures actual total discount granted on PAID invoices during the promotion window.  
-- =====================================================================  
SELECT '=== QUERY 11: Total discount statistics by promotion program ===' AS ' ';  

SELECT pr.Promo_ID AS Promo_ID,  
       pr.Rule_Description AS Program_Description,  
       pr.Status AS Status,  
       COUNT(DISTINCT i.Invoice_ID) AS Times_Applied,  
       ROUND(SUM(l.Discount), 0) AS Total_Discount_Given  
FROM Promotion pr  
JOIN Applies_To a ON a.Promo_ID = pr.Promo_ID  
JOIN Invoice_Line l ON l.Barcode = a.Barcode  
JOIN Invoice i ON i.Invoice_ID = l.Invoice_ID  
              AND i.Status = 'PAID'  
              AND i.`Date` BETWEEN a.Promo_Start_date AND a.Promo_End_date  
WHERE l.Discount > 0  
GROUP BY pr.Promo_ID, pr.Rule_Description, pr.Status  
ORDER BY Total_Discount_Given DESC;


-- =====================================================================  
-- QUERY 12: Subordinate employee count per manager  
-- ---------------------------------------------------------------------  
-- Technique: SELF JOIN on Cashier table (managers joined with subordinates via Supervisor_ID).  
-- =====================================================================  
SELECT '=== QUERY 12: Subordinate count per manager ===' AS ' ';  

SELECT s.Cashier_ID AS Manager_ID,  
       s.Name AS Manager_Name,  
       COUNT(e.Cashier_ID) AS Subordinate_Count  
FROM Cashier s  
LEFT JOIN Cashier e ON e.Supervisor_ID = s.Cashier_ID  
WHERE s.Supervisor_ID IS NULL  
GROUP BY s.Cashier_ID, s.Name;