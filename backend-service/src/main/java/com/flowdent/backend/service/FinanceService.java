package com.flowdent.backend.service;

import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Simple operational finance in EGP.
 * <pre>
 * Net sales     = sales − client returns
 * Cost of goods = cost of items sold − cost of returned items put back in stock
 * Gross profit  = net sales − cost of goods
 * Net profit    = gross profit − expenses − written-off stock (damage)
 * </pre>
 * Extra purchase costs (shipping, customs…) are part of product cost, so they reach profit
 * through cost of goods; they are also shown separately for tracking.
 * <p>
 * Imported old records are included by their own date: old sales add to sales (and their cost of goods,
 * when it was given), old expenses to expenses, old purchases to purchases. Old purchases are not profit
 * costs, exactly like new ones. A row with no "paid" amount counts as paid in full.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinanceService {

    private final OrderRepository orderRepository;
    private final ProductReturnRepository returnRepository;
    private final ExpenseRepository expenseRepository;
    private final PurchaseRepository purchaseRepository;
    private final StockMovementRepository movementRepository;
    private final ClientPaymentRepository clientPaymentRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final ProductRepository productRepository;
    private final LegacyRecordRepository legacyRepository;
    private final LedgerService ledgerService;

    /** Label of the profit-by-category row that groups imported old sales. */
    static final String OLD_RECORDS = "Old records";

    private static boolean in(LocalDate d, LocalDate from, LocalDate to) {
        return d != null && !d.isBefore(from) && !d.isAfter(to);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal r2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Raw data loaded once and reused for the period and monthly figures. */
    private record Data(List<Order> orders, List<ProductReturn> returns, List<Expense> expenses,
                        List<Purchase> purchases, List<StockMovement> damages,
                        List<ClientPayment> clientPayments, List<SupplierPayment> supplierPayments,
                        List<LegacyRecord> legacy) {}

    private Data load() {
        return new Data(
                orderRepository.findAll().stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList(),
                returnRepository.findAll(),
                expenseRepository.findAll(),
                purchaseRepository.findAll().stream().filter(p -> p.getStatus() != PurchaseStatus.CANCELLED).toList(),
                movementRepository.findAll().stream().filter(m -> m.getType() == StockMovementType.DAMAGE).toList(),
                clientPaymentRepository.findAll(),
                supplierPaymentRepository.findAll(),
                legacyRepository.findByTypeIn(List.of(LegacyType.SALE, LegacyType.PURCHASE, LegacyType.EXPENSE)));
    }

    private Map<String, Object> period(Data d, LocalDate from, LocalDate to, boolean detailed) {
        List<Order> orders = d.orders().stream().filter(o -> in(o.getCreatedAt().toLocalDate(), from, to)).toList();
        BigDecimal sales = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        for (Order o : orders) {
            sales = sales.add(nz(o.getTotalAmount()));
            for (OrderItem i : o.getItems()) {
                BigDecimal unitCost = i.getUnitCost() != null ? i.getUnitCost()
                        : (i.getProduct() != null ? i.getProduct().costOrZero() : BigDecimal.ZERO);
                cogs = cogs.add(unitCost.multiply(BigDecimal.valueOf(i.getQuantity())));
            }
        }

        List<ProductReturn> clientReturns = d.returns().stream()
                .filter(r -> r.getType() == ReturnType.CUSTOMER_RETURN && in(r.getReturnDate(), from, to)).toList();
        BigDecimal refunds = clientReturns.stream().map(ProductReturn::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnedCost = clientReturns.stream()
                .filter(r -> Boolean.TRUE.equals(r.getBackToStock()))
                .map(r -> nz(r.getUnitCost()).multiply(BigDecimal.valueOf(r.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LegacyRecord> old = d.legacy().stream().filter(r -> in(r.getRecordDate(), from, to)).toList();
        List<LegacyRecord> oldSales = old.stream().filter(r -> r.getType() == LegacyType.SALE).toList();
        List<LegacyRecord> oldPurchases = old.stream().filter(r -> r.getType() == LegacyType.PURCHASE).toList();
        List<LegacyRecord> oldExpenses = old.stream().filter(r -> r.getType() == LegacyType.EXPENSE).toList();
        BigDecimal oldSalesTotal = oldSales.stream().map(LegacyRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal oldCost = oldSales.stream().map(r -> nz(r.getCostAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal oldExpenseTotal = oldExpenses.stream().map(LegacyRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal oldPurchaseTotal = oldPurchases.stream().map(LegacyRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        sales = sales.add(oldSalesTotal);
        cogs = cogs.add(oldCost);

        List<Expense> expenses = d.expenses().stream().filter(e -> in(e.getDate(), from, to)).toList();
        BigDecimal expenseTotal = expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(oldExpenseTotal);

        BigDecimal writeOffs = d.damages().stream()
                .filter(m -> in(m.getCreatedAt().toLocalDate(), from, to))
                .map(m -> nz(m.getUnitCost()).multiply(BigDecimal.valueOf(-m.getQuantityChange())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Purchase> purchases = d.purchases().stream().filter(p -> in(p.getPurchaseDate(), from, to)).toList();
        BigDecimal purchasesGoods = purchases.stream().map(Purchase::getGoodsTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(oldPurchaseTotal);
        BigDecimal shipping = purchases.stream().map(p -> nz(p.getShippingCost())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal customs = purchases.stream().map(p -> nz(p.getCustomsCost())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal other = purchases.stream().map(p -> nz(p.getOtherCost())).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netSales = sales.subtract(refunds);
        BigDecimal netCogs = cogs.subtract(returnedCost);
        BigDecimal gross = netSales.subtract(netCogs);
        BigDecimal net = gross.subtract(expenseTotal).subtract(writeOffs);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", to);
        m.put("sales", r2(sales));
        m.put("orderCount", orders.size() + oldSales.size());
        m.put("oldRecords", old.size());
        m.put("returns", r2(refunds));
        m.put("netSales", r2(netSales));
        m.put("costOfGoods", r2(netCogs));
        m.put("grossProfit", r2(gross));
        m.put("grossMargin", netSales.signum() > 0
                ? gross.multiply(BigDecimal.valueOf(100)).divide(netSales, 1, RoundingMode.HALF_UP) : null);
        m.put("expenses", r2(expenseTotal));
        m.put("writeOffs", r2(writeOffs));
        m.put("netProfit", r2(net));
        m.put("purchases", r2(purchasesGoods));
        m.put("extraCosts", r2(shipping.add(customs).add(other)));

        if (detailed) {
            Map<String, BigDecimal> extra = new LinkedHashMap<>();
            extra.put("SHIPPING", r2(shipping));
            extra.put("CUSTOMS", r2(customs));
            extra.put("OTHER", r2(other));
            m.put("extraCostsByType", extra);

            Map<String, BigDecimal> byCat = new LinkedHashMap<>();
            for (ExpenseCategory c : ExpenseCategory.values()) byCat.put(c.name(), BigDecimal.ZERO);
            expenses.forEach(e -> byCat.merge(e.getCategory().name(), e.getAmount(), BigDecimal::add));
            oldExpenses.forEach(e -> byCat.merge(e.getCategory() != null ? e.getCategory().name() : ExpenseCategory.OTHER.name(),
                    e.getAmount(), BigDecimal::add));
            byCat.replaceAll((k, v) -> r2(v));
            m.put("expensesByCategory", byCat);

            BigDecimal moneyIn = d.clientPayments().stream().filter(p -> in(p.getPaidAt(), from, to))
                    .map(ClientPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .add(oldSales.stream().map(LegacyRecord::paidOrFull).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .add(d.returns().stream()
                            .filter(r -> r.getType() == ReturnType.SUPPLIER_RETURN
                                    && r.getSettlement() == ReturnSettlement.CASH && in(r.getReturnDate(), from, to))
                            .map(ProductReturn::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            BigDecimal moneyOut = d.supplierPayments().stream().filter(p -> in(p.getPaidAt(), from, to))
                    .map(SupplierPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .add(oldPurchases.stream().map(LegacyRecord::paidOrFull).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .add(expenseTotal)
                    .add(shipping).add(customs).add(other)
                    .add(clientReturns.stream().filter(r -> r.getSettlement() == ReturnSettlement.CASH)
                            .map(ProductReturn::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            m.put("moneyIn", r2(moneyIn));
            m.put("moneyOut", r2(moneyOut));

            // Profit by product category.
            Map<String, BigDecimal[]> cat = new TreeMap<>();
            for (Order o : orders) {
                for (OrderItem i : o.getItems()) {
                    String key = i.getProduct() != null && i.getProduct().getCategory() != null
                            ? i.getProduct().getCategory().getName() : "—";
                    BigDecimal rev = i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity()));
                    BigDecimal cost = (i.getUnitCost() != null ? i.getUnitCost()
                            : i.getProduct() != null ? i.getProduct().costOrZero() : BigDecimal.ZERO)
                            .multiply(BigDecimal.valueOf(i.getQuantity()));
                    BigDecimal[] acc = cat.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    acc[0] = acc[0].add(rev);
                    acc[1] = acc[1].add(cost);
                }
            }
            if (!oldSales.isEmpty()) {
                BigDecimal[] acc = cat.computeIfAbsent(OLD_RECORDS, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                acc[0] = acc[0].add(oldSalesTotal);
                acc[1] = acc[1].add(oldCost);
            }
            List<Map<String, Object>> byCategory = new ArrayList<>();
            cat.forEach((k, v) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("category", k);
                row.put("sales", r2(v[0]));
                row.put("cost", r2(v[1]));
                row.put("profit", r2(v[0].subtract(v[1])));
                byCategory.add(row);
            });
            byCategory.sort((a, b) -> ((BigDecimal) b.get("profit")).compareTo((BigDecimal) a.get("profit")));
            m.put("profitByCategory", byCategory);
        }
        return m;
    }

    public Map<String, Object> summary(LocalDate from, LocalDate to) {
        Data d = load();
        Map<String, Object> m = period(d, from, to, true);
        // Position right now (not period based).
        BigDecimal stockValue = productRepository.findAll().stream()
                .map(p -> p.costOrZero().multiply(BigDecimal.valueOf(Math.max(0, p.getStockQuantity()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("stockValue", r2(stockValue));
        m.put("clientsOwe", r2(ledgerService.totalReceivables()));
        m.put("weOweProviders", r2(ledgerService.totalPayables()));
        return m;
    }

    /** Year-by-year figures from the earliest record (old or new) up to this year, oldest first. */
    public List<Map<String, Object>> yearly() {
        Data d = load();
        int now = LocalDate.now().getYear();
        int first = now;
        for (Order o : d.orders()) first = Math.min(first, o.getCreatedAt().getYear());
        for (Expense e : d.expenses()) first = Math.min(first, e.getDate().getYear());
        for (Purchase p : d.purchases()) first = Math.min(first, p.getPurchaseDate().getYear());
        for (LegacyRecord r : d.legacy()) first = Math.min(first, r.getRecordDate().getYear());
        first = Math.max(first, now - 30);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int y = first; y <= now; y++) {
            Map<String, Object> m = period(d, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31), false);
            m.put("year", y);
            rows.add(m);
        }
        return rows;
    }

    /** Month-by-month figures for the last {@code months} months (oldest first). */
    public List<Map<String, Object>> monthly(int months) {
        Data d = load();
        YearMonth now = YearMonth.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            Map<String, Object> m = period(d, ym.atDay(1), ym.atEndOfMonth(), false);
            m.put("month", ym.toString());
            rows.add(m);
        }
        return rows;
    }
}
