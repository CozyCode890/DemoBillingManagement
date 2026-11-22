# 16 quy tắc nghiệp vụ (BR1–BR16)

Nguồn: báo cáo nhóm *Retail & Cashier System Operating Rules (POS Business Rules)* — `docs/report/Retail Invoicing FINALL.docx`. Tài liệu có đúng **16 quy tắc**, đánh số theo thứ tự xuất hiện. Comment trong `sql/` và `src/` ghi tắt là `BRn`.

Đừng nhầm với **17 bất biến R1–R17** của `sql/05_invariants.sql` (`sp_check_seed`). Bất biến là phép kiểm dữ liệu, một BR có thể cần nhiều bất biến (ví dụ BR3 → R3, R4, R17). Cột *Bất biến* dưới đây là bất biến nào kiểm cho BR đó.

## Bảng tra nhanh

| BR | Quy tắc | Thực thi ở SQL | Ở Java (app) | Bất biến |
|---|---|---|---|---|
| BR1 | Mã hóa đơn **tự sinh** `<Shop>-<Counter>-<YYYYMMDD>-<SEQ>`, không gõ tay | `sp_create_invoice_header` (trả mã qua OUT), `trg_invoice_bi` kiểm tiền tố | `InvoiceDao.createInvoice` nhận mã từ OUT | R11 |
| BR2 | Ngày và giờ hóa đơn lấy từ **đồng hồ server** | `trg_invoice_bi` ghi đè `Date`/`Time` = `NOW()`; `sp_add_return` dùng `CURDATE()` khi ngày trả NULL | Tab 1 hiện ngày server (`LookupDao.serverDate`), không cho chọn; `processReturn` gửi NULL | R8, R10 |
| BR3 | Chỉ hai trạng thái `PAID` / `VOIDED`. `VOIDED` = Manager hủy sau khi đã thanh toán. **Không lưu nháp**: giỏ hàng chỉ nằm trong bộ nhớ app | `ENUM('PAID','VOIDED')`; `sp_void_invoice` cần Manager; `trg_invoice_bu` chỉ cho `PAID → VOIDED`; `sp_settle_invoice` chặn hóa đơn rỗng / thu thiếu | Giỏ hàng trong RAM tới khi bấm CREATE INVOICE (một transaction) | R3, R4, R17 |
| BR4 | Thu ngân gắn cứng vào hóa đơn, **không ai sửa được** | `trg_invoice_bu`, `sp_update_invoice_header` cấm đổi `Cashier_ID` / quầy / ngày giờ | Hộp thoại *Edit customer* để các ô đó chỉ đọc | R15 |
| BR5 | Lập hóa đơn: quét mã → lấy giá hiện hành, trùng sản phẩm thì cộng dồn, áp khuyến mãi, tính tổng | `sp_add_invoice_line` tự lấy giá, khuyến mãi, VAT và cộng dồn | `NewInvoicePanel` xem trước bằng cùng công thức | R1, R2 |
| BR6 | Đổi giá = thêm mốc giá mới có hiệu lực từ một ngày, **không ghi đè** lịch sử | `sp_add_price_history`; `trg_price_history_bi/bu/bd` (chỉ thêm, cấm sửa / xóa) | — | R1 |
| BR7 | Trùng sản phẩm → cộng dồn **một dòng** (`UNIQUE(Invoice_ID, Barcode)`); số lượng > 0, hàng đóng gói là số nguyên, hàng cân/đong được số lẻ | `UQ_line_invoice_barcode`, `CK_line_quantity`, `sp_add_invoice_line`, `trg_invoice_line_bi/bu` | Tab 1 luôn gộp dòng; `CashRules.quantityProblem` | R7, R16 |
| BR8 | Đơn giá lấy từ server **đúng lúc thêm** và đóng băng vào hóa đơn; thu ngân chỉ xem. Giá hiện hành = mốc có `Valid_From_Date` lớn nhất ≤ ngày giao dịch; mốc mới phải **lớn hơn hẳn** mốc cũ | `fn_get_product_price`; `trg_invoice_line_bi` đặt `Unit_Price`; `sp_update_invoice_line` luôn từ chối; `sp_add_price_history` kiểm ngày | Không có ô sửa giá; tab 1 tính lại giỏ theo ngày server trước khi lưu | R1 |
| BR9 | Thành tiền dòng = SL × đơn giá − giảm giá, **không âm**; giá đã gồm VAT, `Tax_Rate` chụp lại trên dòng chỉ để báo cáo | `CK_line_disc_max`, cột `Invoice_Line.Tax_Rate`, `fn_invoice_amount_due` | Phiếu in ghi "unit prices include VAT" | R16 |
| BR10 | Tìm khách theo số điện thoại; khách quen thì gắn vào hóa đơn, khách vãng lai để trống | `UQ_customer_phone`, `sp_search_customers_by_phone`, `Customer_ID` cho NULL | `CustomerPicker` (tab 1); *Edit customer* (tab 2) là trường **duy nhất** sửa được sau khi chốt | R15 |
| BR11 | Nhiều khuyến mãi cùng lúc → chỉ lấy **một** chương trình giảm nhiều nhất, không cộng dồn; bằng nhau thì lấy chương trình **hết hạn sớm nhất**. `Invoice_Line` chỉ lưu số tiền giảm, không lưu `Promo_ID` | `fn_active_promo_pct`, `sp_get_active_promo` (thứ tự `Discount_Percent DESC, Expiry_Date ASC`) | `NewInvoicePanel.lineDiscount` | R2, R14 |
| BR12 | Một hóa đơn trả bằng **nhiều phương thức** | `Payment` N–1 `Invoice`; `sp_add_payment`; ISA total + disjoint bằng `trg_*_payment_bi` | Khung 4 của tab 1 cho nhiều dòng thanh toán | R6 |
| BR13 | Chỉ **tiền mặt** mới có tiền thối; thẻ / ví thu đúng số tiền, không trả dư | `sp_add_payment` (không vượt số còn thiếu, tiền khách đưa ≥ ghi nhận); `trg_cash/card/ewallet_payment_bi` | `NewInvoicePanel.splitPayment`, `CashRules.checkTendered` | R5 |
| BR14 | Tổng đã trả ≥ tổng hóa đơn thì hóa đơn mới thành `PAID` | `sp_settle_invoice` (SIGNAL → Java ROLLBACK) | Tab 1 chặn lưu khi còn thiếu | R3 |
| BR15 | Chỉ **MANAGER** được duyệt trả hàng; hệ thống ghi Manager làm người duyệt | `Cashier.Role`; `sp_add_return` và `trg_return_bi` kiểm Role | Hộp thoại trả hàng cho chọn trong `sp_lookup_managers` | R9 |
| BR16 | Trả hàng trên hóa đơn `PAID`: 0 < SL trả ≤ SL còn lại (≤ 3 số lẻ); mỗi lượt trả gắn với dòng gốc, **hóa đơn gốc giữ nguyên** (không sửa, không xóa). Tiền hoàn = SL trả × (thành tiền dòng / SL mua) | `sp_add_return`, `trg_return_bi/bu/bd`, `fn_return_refund`; `sp_delete_return` luôn từ chối; `sp_void_invoice` chặn hủy hóa đơn đã trả hàng | `ReturnDialog`; trả được nhiều lần cho tới khi hết | R7, R8, R9, R13 |

## Chỗ code chưa làm đúng tài liệu

Nhóm đã biết các điểm dưới đây. Ghi lại để báo cáo không mô tả một hành vi mà app không có:

1. **BR4, BR15 — "đang đăng nhập".** Tài liệu viết thu ngân *đang đăng nhập* được tự gán vào hóa đơn, và Manager *đang đăng nhập* tự là người duyệt. App không có đăng nhập: ERD chỉ có 15 bảng, không có bảng tài khoản. Nên thu ngân được **chọn** ở tab 1, Manager duyệt được **chọn** trong danh sách. SQL vẫn kiểm `Role = 'MANAGER'`, nhưng không kiểm được *ai đang ngồi trước máy*.
2. **BR10 — tạo hồ sơ khách mới tại quầy.** Chưa có. Khách chưa có hồ sơ thì để trống (khách vãng lai).
3. **BR13 — mã QR động cho thẻ / ví.** App gọn không có nút tạo QR (đã bỏ cùng giao diện cũ). Thu ngân ghi số tiền đúng bằng số còn thiếu, SQL chặn trả dư.
4. **Cờ phiên `@retail_*` của trigger.** Trigger chặn ghi thẳng vào bảng trừ khi thủ tục tương ứng đã bật cờ. Cờ là biến phiên, client nào cũng tự đặt được, nên đây là chốt chống **nhầm lẫn**, không phải cơ chế bảo mật.
