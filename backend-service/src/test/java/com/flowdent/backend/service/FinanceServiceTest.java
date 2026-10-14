package com.flowdent.backend.service;

import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.flowdent.backend.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinanceServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductReturnRepository returnRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock PurchaseRepository purchaseRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock ClientPaymentRepository clientPaymentRepository;
    @Mock SupplierPaymentRepository supplierPaymentRepository;
    @Mock ProductRepository productRepository;
    @Mock LedgerService ledgerService;

    @Mock LegacyRecordRepository legacyRepository;

    @InjectMocks FinanceService financeService;

    final LocalDate today = LocalDate.now();
    Client smile;
    Supplier nile;
    Product gloves;

    @BeforeEach
    void setUp() {
        smile = client(5, "Smile");
        nile = supplier(1, "Nile");
        gloves = product(10, "Gloves", "200", 6, "110");

        // Sales: 3 × 200 = 600 (cost 3 × 110 = 330). A cancelled order must be ignored.
        Order sale = order(1, smile, gloves, 3, "200", "110", OrderStatus.CONFIRMED);
        Order cancelled = order(2, smile, gloves, 10, "200", "110", OrderStatus.CANCELLED);
        Order lastYear = order(3, smile, gloves, 1, "200", "110", OrderStatus.DELIVERED);
        lastYear.setCreatedAt(LocalDateTime.now().minusYears(2));
        when(orderRepository.findAll()).thenReturn(List.of(sale, cancelled, lastYear));

        // Client returns 1 unit (resellable, credit 200) → refunds 200, cost back 110.
        ProductReturn back = ProductReturn.builder().type(ReturnType.CUSTOMER_RETURN).client(smile)
                .product(gloves).quantity(1).amount(egp("200")).unitCost(egp("110")).backToStock(true)
                .settlement(ReturnSettlement.CREDIT).returnDate(today).build();
        // Return to provider settled in cash: money in 220, not part of profit.
        ProductReturn toProvider = ProductReturn.builder().type(ReturnType.SUPPLIER_RETURN).supplier(nile)
                .product(gloves).quantity(2).amount(egp("220")).unitCost(egp("110"))
                .settlement(ReturnSettlement.CASH).returnDate(today).build();
        when(returnRepository.findAll()).thenReturn(List.of(back, toProvider));

        when(expenseRepository.findAll()).thenReturn(List.of(
                Expense.builder().date(today).category(ExpenseCategory.RENT).description("Rent").amount(egp("250")).build(),
                Expense.builder().date(today).category(ExpenseCategory.TRANSPORT).description("Taxi").amount(egp("50")).build()));

        Purchase p = new Purchase();
        p.setSupplier(nile);
        p.setStatus(PurchaseStatus.RECEIVED);
        p.setPurchaseDate(today);
        p.setItems(new ArrayList<>());
        p.setShippingCost(egp("60"));
        p.setCustomsCost(egp("40"));
        p.setOtherCost(egp("0"));
        p.addItem(PurchaseItem.builder().product(gloves).quantity(10).unitCost(egp("100")).build());
        when(purchaseRepository.findAll()).thenReturn(List.of(p));

        // One damaged unit written off at cost 110.
        when(movementRepository.findAll()).thenReturn(List.of(StockMovement.builder().product(gloves)
                .type(StockMovementType.DAMAGE).quantityChange(-1).stockAfter(5).unitCost(egp("110"))
                .createdAt(LocalDateTime.now()).build()));

        when(clientPaymentRepository.findAll()).thenReturn(List.of(
                ClientPayment.builder().client(smile).amount(egp("300")).paidAt(today).build()));
        when(supplierPaymentRepository.findAll()).thenReturn(List.of(
                SupplierPayment.builder().supplier(nile).amount(egp("500")).paidAt(today).build()));
        when(productRepository.findAll()).thenReturn(List.of(gloves));
        when(ledgerService.totalReceivables()).thenReturn(egp("400"));
        when(ledgerService.totalPayables()).thenReturn(egp("500"));
    }

    private static BigDecimal num(Map<String, Object> m, String key) {
        return (BigDecimal) m.get(key);
    }

    @Test
    @DisplayName("Profit = sales − returns − cost of goods − expenses − write-offs")
    void periodProfit() {
        Map<String, Object> m = financeService.summary(today.withDayOfMonth(1), today);

        assertThat(m.get("orderCount")).isEqualTo(1);
        assertThat(num(m, "sales")).isEqualByComparingTo("600");
        assertThat(num(m, "returns")).isEqualByComparingTo("200");
        assertThat(num(m, "netSales")).isEqualByComparingTo("400");
        assertThat(num(m, "costOfGoods")).isEqualByComparingTo("220");     // 330 − 110 returned to stock
        assertThat(num(m, "grossProfit")).isEqualByComparingTo("180");
        assertThat(num(m, "grossMargin")).isEqualByComparingTo("45.0");
        assertThat(num(m, "expenses")).isEqualByComparingTo("300");
        assertThat(num(m, "writeOffs")).isEqualByComparingTo("110");
        assertThat(num(m, "netProfit")).isEqualByComparingTo("-230");
    }

    @Test
    @DisplayName("Extra purchase costs, money in/out and position are reported")
    void extraCostsAndCashFlow() {
        Map<String, Object> m = financeService.summary(today.withDayOfMonth(1), today);

        assertThat(num(m, "purchases")).isEqualByComparingTo("1000");
        assertThat(num(m, "extraCosts")).isEqualByComparingTo("100");
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> extra = (Map<String, BigDecimal>) m.get("extraCostsByType");
        assertThat(extra.get("SHIPPING")).isEqualByComparingTo("60");
        assertThat(extra.get("CUSTOMS")).isEqualByComparingTo("40");

        // In: 300 from clients + 220 cash back from provider.
        assertThat(num(m, "moneyIn")).isEqualByComparingTo("520");
        // Out: 500 to provider + 300 expenses + 100 extra costs (client return was credit, not cash).
        assertThat(num(m, "moneyOut")).isEqualByComparingTo("900");

        assertThat(num(m, "stockValue")).isEqualByComparingTo("660");
        assertThat(num(m, "clientsOwe")).isEqualByComparingTo("400");
        assertThat(num(m, "weOweProviders")).isEqualByComparingTo("500");

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> byCat = (Map<String, BigDecimal>) m.get("expensesByCategory");
        assertThat(byCat.get("RENT")).isEqualByComparingTo("250");
        assertThat(byCat.get("TRANSPORT")).isEqualByComparingTo("50");
        assertThat(byCat.get("SALARIES")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Monthly view returns one row per month, oldest first, current month last")
    void monthlyRows() {
        List<Map<String, Object>> rows = financeService.monthly(12);

        assertThat(rows).hasSize(12);
        Map<String, Object> current = rows.get(11);
        assertThat(current.get("month")).isEqualTo(java.time.YearMonth.now().toString());
        assertThat(num(current, "sales")).isEqualByComparingTo("600");
        assertThat(num(rows.get(0), "sales")).isEqualByComparingTo("0");
    }
}
