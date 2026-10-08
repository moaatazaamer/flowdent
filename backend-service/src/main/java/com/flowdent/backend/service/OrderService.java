package com.flowdent.backend.service;

import com.flowdent.backend.dto.Dtos.CreateOrderRequest;
import com.flowdent.backend.dto.Dtos.OrderItemRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.OrderRepository;
import com.flowdent.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<OrderStatus> PICKUP_ONLY = Set.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.PICKED_UP);
    private static final Set<OrderStatus> DELIVERY_ONLY = Set.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    /**
     * Creates an order from product ids + quantities. Prices always come from the DB,
     * never from the client. Stock is reserved (decremented) immediately.
     */
    @Transactional
    public Order createOrder(CreateOrderRequest req, User user) {
        if (req.deliveryMethod() == DeliveryMethod.HOME_DELIVERY
                && (req.customerDetails().deliveryAddress() == null
                    || req.customerDetails().deliveryAddress().isBlank())) {
            throw ApiException.badRequest("A delivery address is required for home delivery");
        }
        // Merge duplicate lines for the same product.
        Map<Long, Integer> qtyByProduct = new LinkedHashMap<>();
        for (OrderItemRequest i : req.items()) {
            qtyByProduct.merge(i.productId(), i.quantity(), Integer::sum);
        }

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .paymentMethod(req.paymentMethod())
                .deliveryMethod(req.deliveryMethod())
                .paid(false)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : qtyByProduct.entrySet()) {
            Product p = productRepository.findById(e.getKey())
                    .orElseThrow(() -> ApiException.notFound("Product " + e.getKey()));
            int qty = e.getValue();
            if (p.getStockQuantity() < qty) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Insufficient stock for \"" + p.getName() + "\" (available: " + p.getStockQuantity() + ")");
            }
            p.setStockQuantity(p.getStockQuantity() - qty);
            order.addItem(OrderItem.builder().product(p).quantity(qty).price(p.getPrice()).build());
            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(qty)));
        }
        order.setTotalAmount(total);

        var cd = req.customerDetails();
        order.attachCustomerDetails(CustomerDetails.builder()
                .fullName(cd.fullName())
                .email(cd.email())
                .phone(cd.phone())
                .deliveryAddress(req.deliveryMethod() == DeliveryMethod.SHOP_PICKUP ? null : cd.deliveryAddress())
                .build());

        return orderRepository.save(order);
    }

    @Transactional
    public Order updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        OrderStatus old = order.getStatus();
        if (old == newStatus) return order;

        if (old == OrderStatus.CANCELLED) {
            throw ApiException.badRequest("Cancelled orders cannot be reopened");
        }
        if (order.getDeliveryMethod() == DeliveryMethod.HOME_DELIVERY && PICKUP_ONLY.contains(newStatus)) {
            throw ApiException.badRequest("Pickup statuses do not apply to home-delivery orders");
        }
        if (order.getDeliveryMethod() == DeliveryMethod.SHOP_PICKUP && DELIVERY_ONLY.contains(newStatus)) {
            throw ApiException.badRequest("Shipping statuses do not apply to shop-pickup orders");
        }

        if (newStatus == OrderStatus.CANCELLED) {
            restoreStock(order);
        }
        // Pay-on-receipt orders are settled when handed over.
        if ((newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.PICKED_UP)
                && order.getPaymentMethod() != PaymentMethod.ONLINE_PAYMENT) {
            order.setPaid(true);
        }
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    @Transactional
    public Order markPaid(Order order, String paymentIntentId) {
        order.setPaid(true);
        order.setPaymentIntentId(paymentIntentId);
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
        return orderRepository.save(order);
    }

    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product p = item.getProduct();
            if (p != null) {
                p.setStockQuantity(p.getStockQuantity() + item.getQuantity());
                productRepository.save(p);
            }
        }
    }
}
