package billing.dao;

import billing.db.Db;
import billing.db.QueryResult;
import billing.db.SqlLog;
import billing.model.CartLine;
import billing.model.PaymentEntry;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class InvoiceDao {
    public static class ReturnItem {
        private final String     productName;
        private final int        lineNumber;
        private final BigDecimal quantity;

        public ReturnItem(String productName, int lineNumber, BigDecimal quantity) {
            this.productName = productName;
            this.lineNumber = lineNumber;
            this.quantity = quantity;
        }

        public String getProductName() {
            return productName;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }
    }

    public String createInvoice(String counterId, String cashierId, String customerId,
                                List<CartLine> lines,
                                List<PaymentEntry> payments) {
        String fnPayId   = "{? = call fn_next_payment_id()}";
        String spHeader  = "{call sp_create_invoice_header(?, ?, ?, ?, ?, ?)}";
        String spLine    = "{call sp_add_invoice_line(?, ?, ?, ?, ?, ?)}";
        String spPayment = "{call sp_add_payment(?, ?, ?, ?, ?, ?)}";
        String spSettle  = "{call sp_settle_invoice(?)}";

        long t0 = System.currentTimeMillis();
        Connection conn = null;

        try {
            conn = Db.getConnection();
            conn.setAutoCommit(false);

            String invoiceId;
            try (CallableStatement cs = conn.prepareCall(spHeader)) {
                cs.setNull(1, Types.DATE);
                cs.setNull(2, Types.TIME);
                cs.setString(3, counterId);
                cs.setString(4, cashierId);
                if (customerId == null) {
                    cs.setNull(5, Types.VARCHAR);
                } else {
                    cs.setString(5, customerId);
                }
                cs.registerOutParameter(6, Types.VARCHAR);
                cs.execute();
                invoiceId = cs.getString(6);
                SqlLog.add(spHeader, new Object[]{null, null,
                        counterId, cashierId, customerId, "OUT=" + invoiceId}, 0, 1);
            }

            try (CallableStatement cs = conn.prepareCall(spLine)) {
                int lineNo = 1;
                for (CartLine cl : lines) {
                    cs.setString(1, invoiceId);
                    cs.setInt(2, lineNo++);
                    cs.setString(3, cl.product.barcode);
                    cs.setBigDecimal(4, cl.quantity);
                    cs.setBigDecimal(5, cl.unitPrice);
                    cs.setBigDecimal(6, cl.discount);
                    cs.addBatch();
                }
                cs.executeBatch();
                SqlLog.add(spLine + "   [batch x " + lines.size() + "]",
                           null, 0, lines.size());
            }

            try (CallableStatement csId = conn.prepareCall(fnPayId);
                 CallableStatement cs   = conn.prepareCall(spPayment)) {
                for (PaymentEntry pe : payments) {
                    csId.registerOutParameter(1, Types.VARCHAR);
                    csId.execute();
                    String payId = csId.getString(1);
                    SqlLog.add(fnPayId, null, 0, 1);

                    String d1 = pe.wireDetail1();

                    cs.setString(1, payId);
                    cs.setString(2, invoiceId);
                    cs.setBigDecimal(3, pe.amount);
                    cs.setString(4, pe.method);
                    cs.setString(5, d1);
                    cs.setString(6, pe.detail2);
                    cs.executeUpdate();

                    SqlLog.add(spPayment, new Object[]{payId, invoiceId, pe.amount,
                            pe.method, d1, pe.detail2}, 0, 1);
                }
            }

            try (CallableStatement cs = conn.prepareCall(spSettle)) {
                cs.setString(1, invoiceId);
                cs.executeUpdate();
                SqlLog.add(spSettle, new Object[]{invoiceId}, 0, 1);
            }

            conn.commit();
            SqlLog.add("COMMIT;  -- save invoice successfully " + invoiceId,
                       null, System.currentTimeMillis() - t0, 0);
            return invoiceId;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                    SqlLog.add("ROLLBACK;  -- rolled back everything due to an error", null, 0, 0);
                } catch (SQLException ignore) { }
            }
            throw new RuntimeException("Failed to save invoice: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignore) { }
                try { conn.close(); }            catch (SQLException ignore) { }
            }
        }
    }

    public QueryResult recentInvoices(int days) {
        return QueryResult.call("{call sp_get_recent_invoices(?)}", days);
    }

    public QueryResult invoiceLines(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_lines(?)}", invoiceId);
    }

    public QueryResult invoiceHeader(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_header(?)}", invoiceId);
    }

    public QueryResult invoiceEditFields(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_edit_fields(?)}", invoiceId);
    }

    public QueryResult invoicePayments(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_payments(?)}", invoiceId);
    }

    public QueryResult invoiceAdjusted(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_adjusted(?)}", invoiceId);
    }

    public QueryResult returnableLines(String invoiceId) {
        return QueryResult.call("{call sp_get_returnable_lines(?)}", invoiceId);
    }

    public BigDecimal returnRefund(String invoiceId, int lineNumber, BigDecimal quantity) {
        String sql = "{? = call fn_return_refund(?, ?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {
            cs.registerOutParameter(1, Types.DECIMAL);
            cs.setString(2, invoiceId);
            cs.setInt(3, lineNumber);
            cs.setBigDecimal(4, quantity);
            cs.execute();
            BigDecimal refund = cs.getBigDecimal(1);

            SqlLog.add(sql, new Object[]{invoiceId, lineNumber, quantity},
                       System.currentTimeMillis() - t0, 1);
            return refund == null ? BigDecimal.ZERO : refund;
        } catch (SQLException e) {
            throw new RuntimeException("Cannot calculate refund amount for line "
                                       + lineNumber + ": " + e.getMessage(), e);
        }
    }

    public BigDecimal amountDue(String invoiceId) {
        return scalarMoney("{? = call fn_invoice_amount_due(?)}", invoiceId,
                           "Cannot read total amount due for invoice ");
    }

    public BigDecimal amountPaid(String invoiceId) {
        return scalarMoney("{? = call fn_invoice_amount_paid(?)}", invoiceId,
                           "Cannot read total amount paid for invoice ");
    }

    private BigDecimal scalarMoney(String sql, String invoiceId, String errorPrefix) {
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {
            cs.registerOutParameter(1, Types.DECIMAL);
            cs.setString(2, invoiceId);
            cs.execute();
            BigDecimal amount = cs.getBigDecimal(1);

            SqlLog.add(sql, new Object[]{invoiceId}, System.currentTimeMillis() - t0, 1);
            return amount == null ? BigDecimal.ZERO : amount;
        } catch (SQLException e) {
            throw new RuntimeException(errorPrefix + invoiceId + ": " + e.getMessage(), e);
        }
    }

    public void voidInvoice(String invoiceId, String supervisorId) {
        String sql = "{call sp_void_invoice(?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {
            cs.setString(1, invoiceId);
            cs.setString(2, supervisorId);
            cs.executeUpdate();

            SqlLog.add(sql, new Object[]{invoiceId, supervisorId}, System.currentTimeMillis() - t0, 1);
        } catch (SQLException e) {
            SqlLog.add(sql, new Object[]{invoiceId, supervisorId}, System.currentTimeMillis() - t0, 0);
            throw new RuntimeException("Cannot void invoice " + invoiceId + ": " + e.getMessage(), e);
        }
    }

    public void updateInvoiceCustomer(String invoiceId, String customerId) {
        QueryResult f = invoiceEditFields(invoiceId);
        if (f.isEmpty()) {
            throw new IllegalArgumentException("Invoice " + invoiceId + " not found");
        }
        Object[] r = f.rows.get(0);
        String cus = (customerId == null || customerId.isBlank()) ? null : customerId.trim();

        String sql = "{call sp_update_invoice_header(?, ?, ?, ?, ?, ?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {
            cs.setString(1, invoiceId);
            cs.setObject(2, r[1]);
            cs.setObject(3, r[2]);
            cs.setObject(4, r[3]);
            cs.setObject(5, r[4]);
            cs.setObject(6, r[5]);
            if (cus == null) cs.setNull(7, Types.VARCHAR); else cs.setString(7, cus);
            cs.executeUpdate();

            SqlLog.add(sql, new Object[]{invoiceId, r[1], r[2], r[3], r[4], r[5], cus},
                       System.currentTimeMillis() - t0, 1);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update invoice " + invoiceId + ": " + e.getMessage(), e);
        }
    }

    public void processReturn(String invoiceId, List<ReturnItem> items, String approvedByManagerId) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("There are no lines to return");
        }

        String spReturn = "{call sp_add_return(?, ?, ?, ?, ?)}";

        long t0 = System.currentTimeMillis();
        Connection conn = null;

        ReturnItem current = null;

        try {
            conn = Db.getConnection();
            conn.setAutoCommit(false);

            try (CallableStatement cs = conn.prepareCall(spReturn)) {
                for (ReturnItem it : items) {
                    current = it;
                    cs.setString(1, invoiceId);
                    cs.setInt(2, it.getLineNumber());
                    cs.setNull(3, Types.DATE);
                    cs.setBigDecimal(4, it.getQuantity());
                    cs.setString(5, approvedByManagerId);
                    cs.executeUpdate();

                    SqlLog.add(spReturn, new Object[]{invoiceId, it.getLineNumber(),
                            null, it.getQuantity(), approvedByManagerId}, 0, 1);
                    current = null;
                }
            }

            conn.commit();
            SqlLog.add("COMMIT;  -- save return successfully for invoice " + invoiceId,
                       null, System.currentTimeMillis() - t0, 0);
        } catch (SQLException e) {
            if (current != null) {
                SqlLog.add(spReturn, new Object[]{invoiceId, current.getLineNumber(),
                        null, current.getQuantity(), approvedByManagerId}, 0, 0);
            }
            if (conn != null) {
                try {
                    conn.rollback();
                    SqlLog.add("ROLLBACK;  -- rolled back everything due to an error", null, 0, 0);
                } catch (SQLException ignore) { }
            }
            throw new RuntimeException("Failed to process return: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignore) { }
                try { conn.close(); }            catch (SQLException ignore) { }
            }
        }
    }
}
