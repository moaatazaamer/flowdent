package com.flowdent.backend.service;

import com.flowdent.backend.dto.OpsDtos.ClientSummary;
import com.flowdent.backend.dto.OpsDtos.SupplierSummary;
import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.flowdent.backend.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerServiceTest {

    @Mock ClientRepository clientRepository;
    @Mock ClientPaymentRepository clientPaymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock SupplierPaymentRepository supplierPaymentRepository;
    @Mock PurchaseRepository purchaseRepository;
    @Mock ProductReturnRepository returnRepository;

    @Mock LegacyRecordRepository legacyRepository;

    @InjectMocks LedgerService ledgerService;

    private ProductReturn clientReturn(Client c, String amount, ReturnSettlement s) {
        return ProductReturn.builder().type(ReturnType.CUSTOMER_RETURN).client(c).amount(egp(amount))
                .settlement(s).quantity(1).returnDate(LocalDate.now()).build();
    }

    private ProductReturn supplierReturn(Supplier sp, String amount, ReturnSettlement s) {
        return ProductReturn.builder().type(ReturnType.SUPPLIER_RETURN).supplier(sp).amount(egp(amount))
                .settlement(s).quantity(1).returnDate(LocalDate.now()).build();
    }

    private Purchase purchase(Supplier sp, PurchaseStatus status, String unitCost, int qty, String shipping) {
        Purchase p = new Purchase();
        p.setSupplier(sp);
        p.setStatus(status);
        p.setPurchaseDate(LocalDate.of(2026, 9, 1));
        p.setItems(new ArrayList<>());
        p.setShippingCost(egp(shipping));
        p.setCustomsCost(egp("0"));
        p.setOtherCost(egp("0"));
        p.addItem(PurchaseItem.builder().product(product(1, "X", "1", 0, null)).quantity(qty).unitCost(egp(unitCost)).build());
        return p;
    }

    @Test
    @DisplayName("Client balance = sales − payments − returns settled as credit")
    void clientBalance() {
        Client c = client(5, "Smile");
        Product p = product(1, "Gloves", "100", 50, "60");
        Order active = order(1, c, p, 10, "100", "60", OrderStatus.CONFIRMED);   // 1000
        Order cancelled = order(2, c, p, 5, "100", "60", OrderStatus.CANCELLED); // ignored
        when(orderRepository.findByClientIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(active, cancelled));
        when(clientPaymentRepository.findByClientIdOrderByPaidAtDescIdDesc(5L)).thenReturn(List.of(
                ClientPayment.builder().client(c).amount(egp("300")).paidAt(LocalDate.now()).build()));
        when(returnRepository.findByClientIdOrderByReturnDateDescIdDesc(5L)).thenReturn(List.of(
                clientReturn(c, "100", ReturnSettlement.CREDIT),
                clientReturn(c, "50", ReturnSettlement.CASH)));

        ClientSummary s = ledgerService.clientSummary(c);

        assertThat(s.orderCount()).isEqualTo(1);
        assertThat(s.totalSales()).isEqualByComparingTo("1000");
        assertThat(s.totalPaid()).isEqualByComparingTo("300");
        assertThat(s.totalReturned()).isEqualByComparingTo("150");
        assertThat(s.balance()).isEqualByComparingTo("600");
    }

    @Test
    @DisplayName("Provider balance counts only received goods, not extra costs")
    void supplierBalance() {
        Supplier sp = supplier(1, "Nile");
        when(purchaseRepository.findBySupplierIdOrderByPurchaseDateDescIdDesc(1L)).thenReturn(List.of(
                purchase(sp, PurchaseStatus.RECEIVED, "100", 20, "200"),  // goods 2000, extra 200 not owed to provider
                purchase(sp, PurchaseStatus.ORDERED, "999", 1, "0"),      // not received yet
                purchase(sp, PurchaseStatus.CANCELLED, "500", 1, "0")));
        when(supplierPaymentRepository.findBySupplierIdOrderByPaidAtDescIdDesc(1L)).thenReturn(List.of(
                SupplierPayment.builder().supplier(sp).amount(egp("1500")).paidAt(LocalDate.now()).build()));
        when(returnRepository.findBySupplierIdOrderByReturnDateDescIdDesc(1L)).thenReturn(List.of(
                supplierReturn(sp, "100", ReturnSettlement.CREDIT),
                supplierReturn(sp, "40", ReturnSettlement.CASH)));

        SupplierSummary s = ledgerService.supplierSummary(sp);

        assertThat(s.purchaseCount()).isEqualTo(2);
        assertThat(s.totalBought()).isEqualByComparingTo("2000");
        assertThat(s.totalPaid()).isEqualByComparingTo("1500");
        assertThat(s.totalReturned()).isEqualByComparingTo("140");
        assertThat(s.balance()).isEqualByComparingTo("400");
        assertThat(s.lastPurchase()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("Total receivables only add clients who owe money")
    void totalReceivablesIgnoresCredit() {
        Client owes = client(1, "Owes");
        Client inCredit = client(2, "Credit");
        Product p = product(1, "Gloves", "100", 50, "60");
        when(clientRepository.findAllByOrderByNameAsc()).thenReturn(List.of(owes, inCredit));
        when(orderRepository.findAll()).thenReturn(List.of(order(1, owes, p, 5, "100", "60", OrderStatus.CONFIRMED)));
        when(clientPaymentRepository.findAll()).thenReturn(List.of(
                ClientPayment.builder().client(inCredit).amount(egp("200")).paidAt(LocalDate.now()).build()));
        when(returnRepository.findAll()).thenReturn(List.of());

        assertThat(ledgerService.totalReceivables()).isEqualByComparingTo("500");
        assertThat(ledgerService.clientSummaries()).extracting(ClientSummary::name).containsExactly("Owes", "Credit");
    }
}
