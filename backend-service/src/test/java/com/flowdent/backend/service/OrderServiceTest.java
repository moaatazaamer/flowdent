package com.flowdent.backend.service;

import com.flowdent.backend.dto.Dtos.CreateOrderRequest;
import com.flowdent.backend.dto.Dtos.CustomerDetailsRequest;
import com.flowdent.backend.dto.Dtos.OrderItemRequest;
import com.flowdent.backend.dto.OpsDtos.SaleItemRequest;
import com.flowdent.backend.dto.OpsDtos.StaffSaleRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.ClientRepository;
import com.flowdent.backend.repository.OrderRepository;
import com.flowdent.backend.repository.ProductRepository;
import com.flowdent.backend.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.flowdent.backend.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock ProductRepository productRepository;
    @Mock ClientRepository clientRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock ClientService clientService;

    OrderService orderService;
    Product gloves;
    Client smile;
    Order stored;

    @BeforeEach
    void setUp() {
        gloves = product(10, "Gloves", "150", 10, "90");
        smile = client(5, "Smile Dental Clinic");

        when(productRepository.findById(10L)).thenReturn(Optional.of(gloves));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(clientRepository.findById(5L)).thenReturn(Optional.of(smile));
        when(clientService.resolveForOrder(any(), anyString(), anyString(), anyString(), any())).thenReturn(smile);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) o.setId(1L);
            stored = o;
            return o;
        });
        when(orderRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(stored));

        StockService stockService = new StockService(productRepository, movementRepository);
        orderService = new OrderService(orderRepository, productRepository, clientRepository, stockService, clientService);
    }

    private CreateOrderRequest online(PaymentMethod pm, DeliveryMethod dm, String address, OrderItemRequest... items) {
        return new CreateOrderRequest(List.of(items), pm, dm,
                new CustomerDetailsRequest("Dr. Sara", "sara@example.com", "+20 100 111 2233", address));
    }

    @Test
    @DisplayName("Online order uses database prices, merges lines and deducts stock")
    void onlineOrderUsesDbPricesAndDeductsStock() {
        Order o = orderService.createOrder(online(PaymentMethod.CASH_ON_DELIVERY, DeliveryMethod.HOME_DELIVERY,
                "Nasr City", new OrderItemRequest(10L, 2), new OrderItemRequest(10L, 1)), null);

        assertThat(o.getItems()).hasSize(1);
        assertThat(o.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(o.getItems().get(0).getPrice()).isEqualByComparingTo("150");
        assertThat(o.getItems().get(0).getUnitCost()).isEqualByComparingTo("90");
        assertThat(o.getTotalAmount()).isEqualByComparingTo("450");
        assertThat(o.getClient()).isSameAs(smile);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(gloves.getStockQuantity()).isEqualTo(7);

        ArgumentCaptor<StockMovement> m = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(m.capture());
        assertThat(m.getValue().getType()).isEqualTo(StockMovementType.SALE);
        assertThat(m.getValue().getQuantityChange()).isEqualTo(-3);
        assertThat(m.getValue().getReference()).isEqualTo("Order #1");
    }

    @Test
    @DisplayName("Ordering more than in stock is refused and nothing changes")
    void insufficientStockIsRefused() {
        assertThatThrownBy(() -> orderService.createOrder(online(PaymentMethod.CASH_ON_DELIVERY,
                DeliveryMethod.SHOP_PICKUP, null, new OrderItemRequest(10L, 11)), null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(gloves.getStockQuantity()).isEqualTo(10);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Pay-later (credit) is not offered to online shoppers")
    void creditNotAllowedOnline() {
        assertThatThrownBy(() -> orderService.createOrder(online(PaymentMethod.CREDIT,
                DeliveryMethod.SHOP_PICKUP, null, new OrderItemRequest(10L, 1)), null))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Home delivery needs an address")
    void homeDeliveryNeedsAddress() {
        assertThatThrownBy(() -> orderService.createOrder(online(PaymentMethod.CASH_ON_DELIVERY,
                DeliveryMethod.HOME_DELIVERY, " ", new OrderItemRequest(10L, 1)), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("address");
    }

    @Test
    @DisplayName("Staff sale honours special prices and records the amount received now")
    void staffSaleWithSpecialPriceAndPartialPayment() {
        StaffSaleRequest req = new StaffSaleRequest(5L,
                List.of(new SaleItemRequest(10L, 2, egp("120"))),
                PaymentMethod.CREDIT, DeliveryMethod.HOME_DELIVERY, null, "phone order", egp("100"), "deposit");

        Order o = orderService.createStaffSale(req);

        assertThat(o.getTotalAmount()).isEqualByComparingTo("240");
        assertThat(o.getItems().get(0).getPrice()).isEqualByComparingTo("120");
        assertThat(o.getCreatedByAdmin()).isTrue();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(o.getClient()).isSameAs(smile);
        assertThat(gloves.getStockQuantity()).isEqualTo(8);
        verify(clientService).recordPayment(eq(smile), eq(1L), argThat(a -> a.compareTo(egp("100")) == 0),
                any(), eq("Payment"), eq("deposit"));
    }

    @Test
    @DisplayName("Staff sale without a special price uses the list price; payment is capped at the total")
    void staffSaleListPriceAndPaymentCap() {
        StaffSaleRequest req = new StaffSaleRequest(5L,
                List.of(new SaleItemRequest(10L, 1, null)),
                PaymentMethod.CASH_ON_DELIVERY, DeliveryMethod.SHOP_PICKUP, null, null, egp("999"), null);

        Order o = orderService.createStaffSale(req);

        assertThat(o.getTotalAmount()).isEqualByComparingTo("150");
        assertThat(o.getCustomerDetails().getDeliveryAddress()).isNull();
        verify(clientService).recordPayment(eq(smile), eq(1L), argThat(a -> a.compareTo(egp("150")) == 0),
                any(), eq("Cash"), isNull());
    }

    @Test
    @DisplayName("Staff sale with nothing paid records no payment")
    void staffSaleNothingPaid() {
        orderService.createStaffSale(new StaffSaleRequest(5L, List.of(new SaleItemRequest(10L, 1, null)),
                PaymentMethod.CREDIT, DeliveryMethod.SHOP_PICKUP, null, null, BigDecimal.ZERO, null));
        verify(clientService, never()).recordPayment(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Cancelling an order puts the stock back")
    void cancelRestoresStock() {
        gloves.setStockQuantity(7);
        stored = order(1, smile, gloves, 3, "150", "90", OrderStatus.CONFIRMED);

        Order o = orderService.updateStatus(1L, OrderStatus.CANCELLED);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(gloves.getStockQuantity()).isEqualTo(10);
        ArgumentCaptor<StockMovement> m = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(m.capture());
        assertThat(m.getValue().getType()).isEqualTo(StockMovementType.SALE_CANCELLED);
    }

    @Test
    @DisplayName("Cancelled orders cannot be reopened")
    void cancelledCannotReopen() {
        stored = order(1, smile, gloves, 1, "150", "90", OrderStatus.CANCELLED);
        assertThatThrownBy(() -> orderService.updateStatus(1L, OrderStatus.CONFIRMED))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Delivering a cash-on-delivery order settles it")
    void deliveringCodSettlesOrder() {
        stored = order(1, smile, gloves, 1, "150", "90", OrderStatus.SHIPPED);
        stored.setPaymentMethod(PaymentMethod.CASH_ON_DELIVERY);

        orderService.updateStatus(1L, OrderStatus.DELIVERED);

        verify(clientService).settleOrder(stored, "Cash");
    }

    @Test
    @DisplayName("Delivering an on-account order does not mark it paid")
    void deliveringCreditOrderDoesNotSettle() {
        stored = order(1, smile, gloves, 1, "150", "90", OrderStatus.SHIPPED);

        orderService.updateStatus(1L, OrderStatus.DELIVERED);

        verify(clientService, never()).settleOrder(any(), any());
    }

    @Test
    @DisplayName("Pickup statuses are rejected for home-delivery orders")
    void pickupStatusRejectedForDelivery() {
        stored = order(1, smile, gloves, 1, "150", "90", OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> orderService.updateStatus(1L, OrderStatus.READY_FOR_PICKUP))
                .isInstanceOf(ApiException.class);
    }
}
