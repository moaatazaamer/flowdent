package com.flowdent.backend.service;

import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.Product;
import com.flowdent.backend.model.StockMovement;
import com.flowdent.backend.model.StockMovementType;
import com.flowdent.backend.repository.ProductRepository;
import com.flowdent.backend.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import static com.flowdent.backend.TestData.egp;
import static com.flowdent.backend.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceTest {

    @Mock ProductRepository productRepository;
    @Mock StockMovementRepository movementRepository;

    StockService stockService;

    @BeforeEach
    void setUp() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
        stockService = new StockService(productRepository, movementRepository);
    }

    @Test
    @DisplayName("Adding stock updates the product and writes a ledger line")
    void changeAddsStockAndRecordsMovement() {
        Product p = product(1, "Gloves", "100", 5, "60");

        StockMovement m = stockService.change(p, StockMovementType.SALE_CANCELLED, 3, null, "Order #9", null);

        assertThat(p.getStockQuantity()).isEqualTo(8);
        assertThat(m.getQuantityChange()).isEqualTo(3);
        assertThat(m.getStockAfter()).isEqualTo(8);
        assertThat(m.getType()).isEqualTo(StockMovementType.SALE_CANCELLED);
        assertThat(m.getReference()).isEqualTo("Order #9");
        assertThat(m.getUnitCost()).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("Stock can never go below zero")
    void changeBelowZeroIsRejected() {
        Product p = product(1, "Gloves", "100", 2, "60");

        assertThatThrownBy(() -> stockService.change(p, StockMovementType.SALE, -3, null, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("available: 2");

        assertThat(p.getStockQuantity()).isEqualTo(2);
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("A zero change writes no ledger line")
    void zeroChangeWritesNothing() {
        Product p = product(1, "Gloves", "100", 4, null);

        assertThat(stockService.change(p, StockMovementType.ADJUSTMENT, 0, null, null, null)).isNull();
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("First receipt sets the cost price")
    void firstReceiptSetsCost() {
        Product p = product(1, "Composite", "500", 0, null);

        stockService.receive(p, 10, egp("110"), StockMovementType.PURCHASE, "Purchase #1", null);

        assertThat(p.getStockQuantity()).isEqualTo(10);
        assertThat(p.getCostPrice()).isEqualByComparingTo("110");
    }

    @Test
    @DisplayName("Later receipts use a weighted-average cost")
    void receiptsUseWeightedAverageCost() {
        Product p = product(1, "Composite", "500", 10, "100");

        stockService.receive(p, 10, egp("130"), StockMovementType.PURCHASE, "Purchase #2", null);

        assertThat(p.getStockQuantity()).isEqualTo(20);
        assertThat(p.getCostPrice()).isEqualByComparingTo("115");
    }

    @Test
    @DisplayName("Stock count records only the difference")
    void setToRecordsDifference() {
        Product p = product(1, "Mirror", "50", 7, "20");

        StockMovement m = stockService.setTo(p, 4, StockMovementType.ADJUSTMENT, "Stock count", "shelf check");

        assertThat(p.getStockQuantity()).isEqualTo(4);
        assertThat(m.getQuantityChange()).isEqualTo(-3);
        assertThat(m.getNote()).isEqualTo("shelf check");
    }
}
