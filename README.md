# Retail Invoicing — Java Swing + JDBC + MySQL 8

A small point-of-sale (POS) app for a chain of shops. It is the project for
**Topic 19: Retail Invoicing** of the course IT079IU – Principles of Database Management.

- **Java Swing** for the screens
- **Plain JDBC** to talk to the database (no Spring, no Hibernate, no ORM)
- **MySQL 8** holds all the data and most of the business rules (functions, procedures, triggers)

Every SQL statement the app sends to MySQL is written to a log, so you can see exactly
what happens when you click a button.

The full project report and the ER diagram are in [docs/](docs/):

| File | What is inside |
|---|---|
| [docs/PDM_FinalReport_Group1.docx](docs/PDM_FinalReport_Group1.docx) | Final report: business rules, ERD, schema, functional dependencies, normalization (BCNF), queries, app design |
| [docs/Retail_Invoicing_erd_101.pdf](docs/Retail_Invoicing_erd_101.pdf) | The Enhanced ER diagram |

---

## Run the app (one click)

| OS | Double-click |
|---|---|
| Windows | **`Setup.exe`** |
| macOS | **`Setup.app`** |

The **Setup App** window opens and presses **Run** (*CHẠY ỨNG DỤNG*) by itself. You do not need to install
Java or MySQL or type any command. Wait until the billing window appears.

What the Setup App does:

| Step | First time on a computer | Next times |
|---|---|---|
| Java | Downloads a portable JDK 21 (~190 MB) if the computer has none | reuses it |
| 1. Libraries | Downloads the jars listed in `pom.xml` into `lib/` (checks SHA-1) | skipped |
| 2. MySQL | Uses a MySQL already running on port 3306 if it can log in with `config.properties`. Otherwise downloads a portable MySQL 8.0 (~235 MB), sets it up and starts it | starts in ~2 s |
| 3. Database | Loads the files in `sql/` in the order 01 → 04 → 02 → 05 | **skipped if the SQL did not change** |
| 4. Compile | Compiles `src/` into `out/` | skipped if the code did not change |
| 5. Open app | Opens the billing window | |

Everything it downloads goes to `~/.retail-billing` (Windows: `C:\Users\<you>\.retail-billing`),
**outside the repo**. Delete that folder to start fresh.

### Switching git branches

The Setup App remembers a hash ("fingerprint") of the SQL files it loaded. When you press Run:

- same SQL as last time → keeps the database and your data, opens the app;
- different SQL (new branch, new seed, edited routine...) → drops and reloads the database (2–15 s).

> Data you typed in the app is lost when the database is reloaded.

A branch that adds a new SQL file sets the load order in `config.properties`:

```properties
db.build.files=01_schema.sql, 04_routines.sql, 02_seed.sql, 05_invariants.sql
```

`03_queries.sql` is not in this list, so editing the practice queries never reloads the database.

### Database tools in the Setup App

The Setup App is in Vietnamese only; the Vietnamese name of each button is in brackets.

| Button | What it does |
|---|---|
| **Reload database** (*Nạp lại database*) | Drops and reloads from `sql/`, even if nothing changed — back to the sample data |
| **Clear data...** (*Xoá dữ liệu...*) | Deletes rows but keeps all tables and routines. Two levels: only transactions (keeps shops, staff, products...) or all 15 tables |
| **Run 03_queries.sql** (*Chạy 03_queries.sql*) | Runs the practice queries and prints the results as tables |
| **Check seed** (*Kiểm tra seed*) | Calls `sp_check_seed`: checks the 17 data rules (see [Data checks](#data-checks)) |
| **Stop MySQL** (*Tắt MySQL*) | Stops the Setup App's own MySQL. It never touches a MySQL you installed yourself |

The **SQL Log** tab (*Nhật ký SQL*) of the Setup App shows every SQL statement the billing app sends.

<details>
<summary>If the OS blocks the launcher (only when you download the repo as a ZIP)</summary>

With `git clone` this does not happen.

- **Windows** shows *"Windows protected your PC"* → click `More info` → `Run anyway`.
- **macOS** says the app is from an unknown developer → open Terminal and run once:

  ```bash
  xattr -dr com.apple.quarantine /path/to/DemoBillingManagement
  ```

- **macOS** cannot read the project folder: the repo is inside Desktop / Documents / Downloads.
  Move it to your home folder and double-click again.
</details>

<details>
<summary>Your computer already has its own MySQL with a different password</summary>

Put that account in `config.properties`:

```properties
db.user=root
db.password=your_password
```

Then press Run again.
</details>

---

## The four tabs of the app

There is **no login screen**. The cashier picks the counter and the cashier name on tab 1.
When an action needs a manager (void, return), the app asks you to pick the manager who approves it.

The app is **bilingual**: Vietnamese by default. The *English / Tiếng Việt* button at the
bottom (or the *Language* menu) switches every open window.

### 1. Create Billing
1. **Invoice details** — choose counter, cashier, and the customer by phone number
   (or leave it empty for a walk-in customer). The date and time come from the **server clock**;
   you cannot change them.
2. **Add products** — pick a product and a quantity. The app shows today's price and the best
   active promotion. Adding the same product again adds to the same line.
3. **Items** — edit the quantity or remove a line.
4. **Payments** — add one or more payments: cash, card or e-wallet. For e-wallet, **Create QR code**
   shows a VietQR code for the amount still owed (bank account from the `qr.*` keys in `config.properties`).

The bar at the bottom shows **Total, Paid, Remaining and Change**. **CREATE INVOICE** works only
when the cart is not empty and nothing is left to pay. It saves the header, lines and payments in
**one transaction**, then shows the receipt. The invoice ID `<Shop>-<Counter>-<YYYYMMDD>-<SEQ>`
is made by SQL.

### 2. Search
Pick a table (products, invoices, invoice lines, price history, promotions, returns, customers,
cashiers, shops, counters, payments...) and filter it by keyword, date range, amount, status and more.

### 3. Invoices
Invoice list on the left (filter: last 7 / 30 / 90 days or all), details on the right:
header and totals, lines, payments, and the **adjusted invoice** after returns.
A saved invoice allows only three actions:

- **Edit customer** — change the customer; every other field is fixed (BR10).
- **Void invoice** — a manager cancels a paid invoice that has **no returns**. The invoice stays
  in the database but no longer counts as revenue (BR3).
- **Return items** — a manager approves a return. The invoice stays *Paid*, and you can return
  more later until everything is returned (BR15, BR16).

### 4. Reports
Each report has a short "About this report" text.

| Button | Question |
|---|---|
| 1. 7-day revenue by shop | Q2 — revenue of each shop for each of the last 7 full days |
| 2. Price rises > 10% | Q3 — products whose price went up more than 10% this year |
| 3. Cashier with the highest average basket | Q4 — average invoice value per cashier |
| 4. Invoices with > 50% returned | Q5 — paid invoices where more than half the value was returned |
| 5. 30-day price (qty 1) | The price of one product on each of the last 30 days, after promotions |
| Bonus: old invoices keep old prices | Compares the price saved on old invoices with today's price |

Q1 (one full invoice with its lines and total) is the **receipt** shown after you save an invoice.

> **Try this:** open *Bonus: old invoices keep old prices*. Lines sold before a price change still
> show the old price, next to today's price. Nothing is hard-coded: the app asks the database
> "what was the price on that day?". This is the hardest requirement of the topic.

---

## Business rules

These are the 17 rules from the report (Table 3.1). The rules live **in the database** whenever
possible, so they hold even if another program writes to it. The Java code checks input early
only to show a friendly message before MySQL says no.

| ID | Rule (simple words) | Where it is enforced |
|---|---|---|
| BR1 | Each checkout makes one invoice. Its ID is never typed by hand: `<Shop>-<Counter>-<YYYYMMDD>-<SEQ>` | `sp_create_invoice_header`, `trg_invoice_bi` |
| BR2 | The invoice date and time come from the server clock, so nobody can back-date a sale | `trg_invoice_bi` |
| BR3 | An invoice is saved only as **PAID** or **VOIDED**. Only a manager can void it, and only if it has no returns | `Status ENUM('PAID','VOIDED')`, `sp_void_invoice`, `trg_invoice_bu` |
| BR4 | The cashier is saved on the invoice and cannot be changed later | NOT NULL foreign key, `trg_invoice_bu` |
| BR5 | The cashier scans a barcode; the system finds the price, merges repeated products, applies discounts and computes the total | `sp_add_invoice_line`, `fn_invoice_amount_due`, `trg_invoice_line_bi` |
| BR6 | A new price gets a later "valid from" date; old prices are never overwritten. The price used is the latest one not after the sale date | PK `(Barcode, Valid_From_Date)`, `fn_get_product_price`, `trg_price_history_*` |
| BR7 | The same product again adds to the existing line. Quantity > 0: whole numbers for packed goods, up to 3 decimals for KG / LITER | `UNIQUE(Invoice_ID, Barcode)`, `CHECK (Quantity > 0)`, `CashRules.quantityProblem` |
| BR8 | The unit price is copied onto the line at sale time and cannot be edited, so a later price change never alters a past invoice | snapshot columns, `trg_invoice_line_bu` / `_bd` |
| BR9 | Line total = Quantity × Unit_Price − Discount, never negative. Prices include VAT; the tax rate is copied for the VAT breakdown only | `CHECK (0 <= Discount <= Quantity * Unit_Price)` |
| BR10 | The cashier may find a customer by phone. Walk-in customers can stay empty | `UNIQUE(Phone)`, nullable `Customer_ID`, *Edit customer* |
| BR11 | If several promotions match, only the biggest discount applies (tie → earliest expiry). Promotions do not stack | `fn_active_promo_pct`, `fn_line_discount` |
| BR12 | One invoice can be paid with several methods | one Invoice → many Payment, `sp_add_payment` |
| BR13 | Only cash gives change. Card and e-wallet are always the exact amount | `trg_cash_payment_bi`, `vw_cash_change_due`, `CashRules.checkTendered` |
| BR14 | When the payments reach the total, the invoice becomes PAID | `sp_settle_invoice`, `trg_invoice_bu` |
| BR15 | Only a manager can approve a return; the manager is saved on it | `sp_add_return`, `trg_return_bi` |
| BR16 | A return takes lines of a PAID invoice, 0 < quantity ≤ quantity not yet returned. The original invoice is never changed. Refund = Return_Quantity × (line total ÷ Quantity) | `trg_return_bi`, `fn_return_refund` |
| BR17 | While items are being scanned, the cart lives only in the app's memory; no draft invoice is ever saved | Java cart (`CartLine`), saved in one transaction by `InvoiceDao.createInvoice`, `sp_check_seed` (R17) |

One more rule comes from the ER diagram: **each payment is exactly one type** (cash, card or
e-wallet, never two). It is enforced by `trg_cash_payment_bi`, `trg_card_payment_bi` and
`trg_ewallet_payment_bi`.

---

## Data checks

`sql/05_invariants.sql` creates `sp_check_seed`. It counts rows that break a rule. 0 everywhere
means the data is correct. You can run it any time, also after you changed data in the app.

| Check | What must be true |
|---|---|
| R1 | Line price = product price on the sale date |
| R2 | Line discount = promotion formula on the sale date |
| R3 | A PAID invoice is fully paid and has at least one line |
| R4 | A VOIDED invoice was also fully paid (payments are never deleted) |
| R5 | Cash given ≥ cash amount |
| R6 | Each payment is in exactly one of Cash / Card / E-wallet |
| R7 | Total returned ≤ quantity bought, per line |
| R8 | Sale date ≤ return date ≤ today |
| R9 | Returns are only on PAID invoices and approved by a MANAGER |
| R10 | No invoice in the future |
| R11 | Invoice ID matches `<Shop>-<Counter>-<YYYYMMDD>-<SEQ>` |
| R12 | Each of the last 91 days has at least one invoice |
| R13 | Return numbers of each line go 1, 2, 3... with no gap |
| R14 | Promotion expiry date ≥ its end date |
| R15 | Cashier exists, and the customer (if any) exists |
| R16 | Whole quantities for PIECE / PACK / BOX, and line total ≥ 0 |
| R17 | Status is only PAID or VOIDED, no draft in DB (BR17) |

Run it with the **Check seed** button, or from a terminal:

```powershell
.\scripts\ps1\check-seed.ps1
```

```bash
./scripts/bash/check-seed.sh
```

---

## Sample data

`sql/02_seed.sql` creates:

- 15 shops, 30 counters (2 per shop), 30 staff (15 managers, 15 cashiers), 30 customers
- 20 products, 15 promotions
- **91 days in a row with invoices** — about 550 invoices, 1,100 lines and 640 payments
- about 100 invoices paid with **two methods**, a few voided invoices, about 40 returns
- 5 products with a **price rise > 10%**, and 3 products that change price **every day**

Dates are relative to `CURDATE()`, so the reports always have data whenever you run it.

The invoices are made by **a SQL loop that calls the same procedures as the Create Billing tab**.
So every number in the database is a number the app itself could have made: no `Status`,
`Unit_Price`, `Discount` or `Return_ID` is typed by hand.

---

## Project structure

```
DemoBillingManagement/
├── README.md
├── config.properties     <- DB connection, main class, QR bank account, SQL load order
├── pom.xml               <- list of libraries (the Setup App downloads them; IDEs read it)
├── Setup.exe             <- DOUBLE-CLICK on Windows
├── Setup.app/            <- DOUBLE-CLICK on macOS
├── SetupApp.jar          <- the Setup App itself
│
├── docs/                 <- final report (.docx) and ER diagram (.pdf)
│
├── sql/
│   ├── 01_schema.sql     <- 15 tables, keys, CHECK, foreign keys, one index
│   ├── 02_seed.sql       <- sample data for 91 days
│   ├── 03_queries.sql    <- 13 queries of the topic + the "old price" proof
│   ├── 04_routines.sql   <- functions (fn_), procedures (sp_), triggers (trg_), views (vw_)
│   └── 05_invariants.sql <- sp_check_seed: the 17 data checks
│
├── src/billing/          <- the billing app
│   ├── Main.java         <- start: tests the DB connection, opens the window
│   ├── db/               <- Db (connection), QueryResult (run a call into a table), SqlLog
│   ├── dao/              <- InvoiceDao, ProductDao, LookupDao, ReportDao: all SQL lives here
│   ├── model/            <- simple data classes: CartLine, PaymentEntry, Product, ...
│   ├── util/             <- CashRules, VietQRHelper, AppConfig, Lang
│   └── ui/               <- Swing screens and dialogs (no SQL here); I18n = language switch
│
├── scripts/              <- optional tools you run by hand
│   ├── ps1/              <- Windows: check-seed, check-contracts, build-setup, build-launcher
│   └── bash/             <- macOS / Linux: the same four
│
└── tools/
    ├── check/ContractCheck.java  <- automatic tests for the UI rules (money, cash, QR, returns...)
    └── launcher/                 <- source of Setup.exe and the icons
```

The app has three layers: **ui → dao → db → MySQL**. The UI never writes SQL, and the DAO
never opens a window. Every SQL call uses `PreparedStatement` or `CallableStatement`.

### How a checkout is saved

`InvoiceDao.createInvoice` runs all steps on one connection with auto-commit off:

1. `sp_create_invoice_header` → makes the invoice ID
2. `sp_add_invoice_line` for each cart line → copies price, tax and discount
3. `sp_add_payment` for each payment → writes `Payment` and its child table
4. `sp_settle_invoice` → checks that payments = total, sets PAID
5. `commit()`

If any step fails, `rollback()` undoes everything and the cart stays on screen with the error message.

---

## Scripts (optional)

| Script | When to use it |
|---|---|
| `check-seed` | Check the 17 data rules from a terminal (same as the **Check seed** button) |
| `check-contracts` | Run `tools/check/ContractCheck.java`: about 145 automatic tests of the UI rules. Needs MySQL running and the sample data. It creates 3 test invoices and deletes them again |
| `build-setup` | Rebuild `SetupApp.jar` (maintainer only — needs the Setup App source, which is not in git) |
| `build-launcher` | Rebuild `Setup.exe` / the icons after editing `tools/launcher/` (maintainer only) |

Windows: `powershell -ExecutionPolicy Bypass -File scripts\ps1\<name>.ps1`
macOS / Linux: `bash scripts/bash/<name>.sh`

---

## Problems?

Most problems go away if you **press Run again**: the Setup App checks every step again and
writes the exact reason in its log. Otherwise:

| Message | What to do |
|---|---|
| *"Cổng 3306 đang có một MySQL khác chạy sẵn..."* (port 3306 already has another MySQL) | Your computer has its own MySQL with another password. Put it in `config.properties` (see above) |
| *"Không tải được ..."* (cannot download) | The network dropped. Press Run again; it continues the download |
| Error in `sql/xx.sql line N` | The SQL file of this branch has an error (the log shows the statement and the MySQL error). Fix the file and press Run |
| *"Biên dịch thất bại"* (compile failed) | Java error in `src/`; the log shows `file:line: error`. Fix it and press Run |
| `Communications link failure` when you run `billing.Main` from an IDE | MySQL is not running. Open the Setup App and press Run once |
| `UnsupportedClassVersionError ... class file version 69` | `SetupApp.jar` was built with a newer JDK. Rebuild it with `build-setup` (it uses `--release 21`) |
| `Setup.exe` says `side-by-side configuration is incorrect` | `tools/launcher/app.manifest` has broken XML. Run `scripts\ps1\build-launcher.ps1`; it shows the error |
| `check-seed` shows a rule as `SAI` (wrong) | The `Hop_dong` column says which business rule is broken. **Reload database** brings it back to 0. If only R12 fails, the database was usually loaded several days ago |
