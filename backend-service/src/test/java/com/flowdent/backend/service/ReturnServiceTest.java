package com.flowdent.backend.service;

import com.flowdent.backend.dto.OpsDtos.ReturnRequest;
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

import java.time.LocalDate;
import java.util.Optional;

import static com.flowdent.backend.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnServiceTest {

    @Mock ProductReturnRepository returnRepository;
    @Mock ProductRepository productRepository;
    @Mock ClientRepository clientRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock OrderRepository orderRepository;
    @Mock StockMovementRepository movementRepository;

    ReturnService returnService;
    Product gloves;
    Client smile;
    Supplier nile;

    @BeforeEach
    void setUp() {
        gloves = product(10, "Gloves", "150", 5, "95");
        smile = client(5, "Smile Dental Clinic");
        nile = supplier(1, "Nile Dental");

        when(productRepository.findById(10L)).thenReturn(Optional.of(gloves));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(clientRepository.findById(5L)).thenReturn(Optional.of(smile));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(nile));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(returnRepository.save(any(ProductReturn.class))).thenAnswer(inv -> {
            ProductReturn r = inv.getArgument(0);
            r.setId(3L);
            return r;
        });

        returnService = new ReturnService(returnRepository, productRepository, clientRepository,
                supplierRepository, orderRepository, new StockService(productRepository, movementRepository));
    }

    private ReturnRequest req(ReturnType type, int qty, String amount, ReturnSettlement s, Long clientId,
                              Long orderId, Long supplierId, Boolean backToStock) {
        return new ReturnRequest(type, LocalDate.of(2026, 10, 5), 10L, qty, egp(amount), s,
                clientId, orderId, supplierId, backToStock, "test");
    }

    @Test
    @DisplayName("Resellable client return goes back into stock")
    void clientReturnBackToStock() {
        ProductReturn r = returnService.create(req(ReturnType.CUSTOMER_RETURN, 2, "300", ReturnSettlement.CREDIT,
                5L, null, null, true));

        assertThat(r.getClient()).isSameAs(smile);
        assertThat(r.getSettlement()).isEqualTo(ReturnSettlement.CREDIT);
        assertThat(gloves.getStockQuantity()).isEqualTo(7);
        ArgumentCaptor<StockMovement> m = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(m.capture());
        assertThat(m.getValue().getType()).isEqualTo(StockMovementType.CUSTOMER_RETURN);
        assertThat(m.getValue().getReference()).isEqualTo("Return #3");
    }

    @Test
    @DisplayName("Damaged client return is recorded but stock stays the same")
    void clientReturnNotResellable() {
        ProductReturn r = returnService.create(req(ReturnType.CUSTOMER_RETURN, 2, "300", ReturnSettlement.CASH,
                5L, null, null, false));

        assertThat(r.getBackToStock()).isFalse();
        assertThat(gloves.getStockQuantity()).isEqualTo(5);
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Return linked to an order takes the client and cost from that sale")
    void clientReturnUsesOrderCost() {
        Order o = order(44, smile, gloves, 3, "150", "80", OrderStatus.DELIVERED);
        when(orderRepository.findById(44L)).thenReturn(Optional.of(o));

        ProductReturn r = returnService.create(req(ReturnType.CUSTOMER_RETURN, 1, "150", null,
                null, 44L, null, true));

        assertThat(r.getClient()).isSameAs(smile);
        assertThat(r.getOrderId()).isEqualTo(44L);
        assertThat(r.getUnitCost()).isEqualByComparingTo("80");
        assertThat(r.getSettlement()).isEqualTo(ReturnSettlement.CREDIT);
    }

    @Test
    @DisplayName("A client return needs a client or an order")
    void clientReturnNeedsClient() {
        assertThatThrownBy(() -> returnService.create(req(ReturnType.CUSTOMER_RETURN, 1, "150",
                ReturnSettlement.CREDIT, null, null, null, true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("client");
    }

    @Test
    @DisplayName("Return to a provider removes stock")
    void supplierReturnRemovesStock() {
        ProductReturn r = returnService.create(req(ReturnType.SUPPLIER_RETURN, 2, "190", ReturnSettlement.CREDIT,
                null, null, 1L, null));

        assertThat(r.getSupplier()).isSameAs(nile);
        assertThat(r.getBackToStock()).isFalse();
        assertThat(gloves.getStockQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Cannot return more to a provider than is in stock")
    void supplierReturnLimitedByStock() {
        assertThatThrownBy(() -> returnService.create(req(ReturnType.SUPPLIER_RETURN, 6, "100",
                ReturnSettlement.CREDIT, null, null, 1L, null)))
                .isInstanceOf(ApiException.class);
        assertThat(gloves.getStockQuantity()).isEqualTo(5);
    }
}
