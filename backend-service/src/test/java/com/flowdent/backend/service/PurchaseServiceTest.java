package com.flowdent.backend.service;

import com.flowdent.backend.dto.OpsDtos.PurchaseItemRequest;
import com.flowdent.backend.dto.OpsDtos.PurchaseRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.flowdent.backend.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseServiceTest {

    @Mock PurchaseRepository purchaseRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock ProductRepository productRepository;
    @Mock SupplierPaymentRepository supplierPaymentRepository;
    @Mock StockMovementRepository movementRepository;

    PurchaseService purchaseService;
    Supplier nile;
    Product gloves;
    Product composite;
    /** Last purchase handed to save(), returned by findById(). */
    Purchase stored;

    @BeforeEach
    void setUp() {
        nile = supplier(1, "Nile Dental");
        gloves = product(10, "Gloves", "180", 0, null);
        composite = product(11, "Composite", "400", 0, null);

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(nile));
        when(productRepository.findById(10L)).thenReturn(Optional.of(gloves));
        when(productRepository.findById(11L)).thenReturn(Optional.of(composite));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supplierPaymentRepository.save(any(SupplierPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> {
            Purchase p = inv.getArgument(0);
            if (p.getId() == null) p.setId(7L);
            stored = p;
            return p;
        });
        when(purchaseRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(stored));

        StockService stockService = new StockService(productRepository, movementRepository);
        purchaseService = new PurchaseService(purchaseRepository, supplierRepository, productRepository,
                supplierPaymentRepository, stockService);
    }

    private PurchaseRequest request(boolean receivedNow, String paid, String shipping, String customs, String other,
                                    List<PurchaseItemRequest> items) {
        return new PurchaseRequest(1L, LocalDate.of(2026, 10, 1), "INV-55",
                shipping == null ? null : egp(shipping), customs == null ? null : egp(customs),
                other == null ? null : egp(other), "first order", receivedNow,
                paid == null ? null : egp(paid), "Bank transfer", items);
    }

    @Test
    @DisplayName("Ordered purchase does not touch stock")
    void orderedPurchaseKeepsStock() {
        Purchase p = purchaseService.create(request(false, null, null, null, null,
                List.of(new PurchaseItemRequest(10L, 10, egp("100")))));

        assertThat(p.getStatus()).isEqualTo(PurchaseStatus.ORDERED);
        assertThat(p.getGoodsTotal()).isEqualByComparingTo("1000");
        assertThat(gloves.getStockQuantity()).isZero();
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Receiving spreads extra costs over items by value (landed cost)")
    void receiveSpreadsExtraCostsByValue() {
        // Goods: 10 × 100 + 5 × 200 = 2000. Extra costs: 300 + 100 = 400 (20% of goods value).
        Purchase p = purchaseService.create(request(true, null, "300", "100", null, List.of(
                new PurchaseItemRequest(10L, 10, egp("100")),
                new PurchaseItemRequest(11L, 5, egp("200")))));

        assertThat(p.getStatus()).isEqualTo(PurchaseStatus.RECEIVED);
        assertThat(p.getReceivedDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(p.getExtraCosts()).isEqualByComparingTo("400");
        assertThat(p.getTotalCost()).isEqualByComparingTo("2400");

        assertThat(p.getItems().get(0).getLandedUnitCost()).isEqualByComparingTo("120");
        assertThat(p.getItems().get(1).getLandedUnitCost()).isEqualByComparingTo("240");

        assertThat(gloves.getStockQuantity()).isEqualTo(10);
        assertThat(gloves.getCostPrice()).isEqualByComparingTo("120");
        assertThat(composite.getStockQuantity()).isEqualTo(5);
        assertThat(composite.getCostPrice()).isEqualByComparingTo("240");

        // Landed value of stock equals goods + extra costs.
        BigDecimal stockValue = gloves.getCostPrice().multiply(BigDecimal.TEN)
                .add(composite.getCostPrice().multiply(BigDecimal.valueOf(5)));
        assertThat(stockValue).isEqualByComparingTo("2400");

        ArgumentCaptor<StockMovement> moves = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository, times(2)).save(moves.capture());
        assertThat(moves.getAllValues()).allSatisfy(m -> {
            assertThat(m.getType()).isEqualTo(StockMovementType.PURCHASE);
            assertThat(m.getReference()).isEqualTo("Purchase #7");
        });
    }

    @Test
    @DisplayName("Free items split extra costs evenly per unit")
    void freeItemsSplitExtraEvenly() {
        Purchase p = purchaseService.create(request(true, null, "40", null, null, List.of(
                new PurchaseItemRequest(10L, 2, egp("0")),
                new PurchaseItemRequest(11L, 2, egp("0")))));

        assertThat(p.getItems()).allSatisfy(i -> assertThat(i.getLandedUnitCost()).isEqualByComparingTo("10"));
    }

    @Test
    @DisplayName("Amount paid on creation is saved as a provider payment")
    void paymentOnCreationIsRecorded() {
        purchaseService.create(request(false, "600", null, null, null,
                List.of(new PurchaseItemRequest(10L, 10, egp("100")))));

        ArgumentCaptor<SupplierPayment> pay = ArgumentCaptor.forClass(SupplierPayment.class);
        verify(supplierPaymentRepository).save(pay.capture());
        assertThat(pay.getValue().getAmount()).isEqualByComparingTo("600");
        assertThat(pay.getValue().getPurchaseId()).isEqualTo(7L);
        assertThat(pay.getValue().getSupplier()).isSameAs(nile);
        assertThat(pay.getValue().getMethod()).isEqualTo("Bank transfer");
    }

    @Test
    @DisplayName("A purchase cannot be received twice")
    void cannotReceiveTwice() {
        purchaseService.create(request(true, null, null, null, null,
                List.of(new PurchaseItemRequest(10L, 3, egp("100")))));

        assertThatThrownBy(() -> purchaseService.receive(7L, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already received");
        assertThat(gloves.getStockQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Received purchases cannot be cancelled; ordered ones can")
    void cancelRules() {
        purchaseService.create(request(false, null, null, null, null,
                List.of(new PurchaseItemRequest(10L, 3, egp("100")))));
        assertThat(purchaseService.cancel(7L).getStatus()).isEqualTo(PurchaseStatus.CANCELLED);

        stored = null;
        purchaseService.create(request(true, null, null, null, null,
                List.of(new PurchaseItemRequest(10L, 3, egp("100")))));
        assertThatThrownBy(() -> purchaseService.cancel(7L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("return to the provider");
    }

    @Test
    @DisplayName("Only ordered purchases can be edited")
    void onlyOrderedCanBeEdited() {
        purchaseService.create(request(true, null, null, null, null,
                List.of(new PurchaseItemRequest(10L, 3, egp("100")))));

        assertThatThrownBy(() -> purchaseService.update(7L, request(false, null, null, null, null,
                new ArrayList<>(List.of(new PurchaseItemRequest(10L, 1, egp("100")))))))
                .isInstanceOf(ApiException.class);
    }
}
