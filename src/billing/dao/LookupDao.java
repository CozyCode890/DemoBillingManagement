package billing.dao;

import billing.db.QueryResult;
import billing.model.CustomerRef;
import billing.model.IdName;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class LookupDao {
    public List<IdName> counters() {
        QueryResult qr = QueryResult.call("{call sp_lookup_counters()}");
        return toIdNames(qr);
    }

    public List<IdName> cashiers() {
        QueryResult qr = QueryResult.call("{call sp_lookup_cashiers()}");
        return toIdNames(qr);
    }

    public List<IdName> managers() {
        QueryResult qr = QueryResult.call("{call sp_lookup_managers()}");
        return toIdNames(qr);
    }

    public LocalDate serverDate() {
        Object v = QueryResult.run("SELECT CURDATE()").rows.get(0)[0];
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof LocalDate d)     return d;
        return LocalDate.parse(String.valueOf(v));
    }

    public List<CustomerRef> searchCustomersByPhone(String phonePrefix) {
        List<CustomerRef> list = new ArrayList<>();
        if (phonePrefix == null || phonePrefix.isEmpty()) {
            return list;
        }

        QueryResult qr = QueryResult.call("{call sp_search_customers_by_phone(?)}", phonePrefix);
        for (Object[] row : qr.rows) {
            list.add(new CustomerRef(
                    String.valueOf(row[0]),
                    String.valueOf(row[1]),
                    String.valueOf(row[2])));
        }
        return list;
    }

    private List<IdName> toIdNames(QueryResult qr) {
        List<IdName> list = new ArrayList<>();
        for (Object[] row : qr.rows) {
            list.add(new IdName(String.valueOf(row[0]), String.valueOf(row[1])));
        }
        return list;
    }
}
