package com.flowdent.backend.service;

import com.flowdent.backend.dto.Dtos.CreateOrderRequest;
import com.flowdent.backend.dto.Dtos.OrderItemRequest;
import com.flowdent.backend.dto.OpsDtos.SaleItemRequest;
import com.flowdent.backend.dto.OpsDtos.StaffSaleRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.ClientRepository;
import com.flowdent.backend.repository.OrderRepository;
import com.flowdent.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Set<OrderStatus> PICKUP_ONLY = Set.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.PICKED_UP);
    private static final Set<OrderStatus> DELIVERY_ONLY = Set.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED);
    private static final Set<PaymentMethod> ONLINE_ALLOWED =
            Set.of(PaymentMethod.ONLINE_PAYMENT, PaymentMethod.CASH_ON_DELIVERY, PaymentMethod.POS_ON_DELIVERY);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final StockService stockService;
    private final ClientService clientService;

    /**
     * Online checkout. Prices always come from the DB, never from the browser.
     * Stock is deducted immediately and the order is attached to a client.
     */
    @Transactional
    public Order createOrder(CreateOrderRequest req, User user) {
        if (!ONLINE_ALLOWED.contains(req.paymentMethod())) {
            throw ApiException.badRequest("Payment method not available for online orders");
        }
        if (req.deliveryMethod() == DeliveryMethod.HOME_DELIVERY
                && (req.customerDetails().deliveryAddress() == null
                    || req.customerDetails().deliveryAddress().isBlank())) {
            throw ApiException.badRequest("A delivery address is required for home delivery");
        }
        Map<Long, Integer> qtyByProduct = new LinkedHashMap<>();
        for (OrderItemRequest i : req.items()) {
            qtyByProduct.merge(i.productId(), i.quantity(), Integer::sum);
        }

        var cd = req.customerDetails();
        Client client = clientService.resolveForOrder(user, cd.fullName(), cd.email(), cd.phone(), cd.deliveryAddress());

        Order order = Order.builder()
                .user(user)
                .client(client)
                .createdByAdmin(false)
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
            checkStock(p, qty);
            order.addItem(OrderItem.builder().product(p).quantity(qty)
                    .price(p.getPrice()).unitCost(p.getCostPrice()).build());
            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(qty)));
        }
        order.setTotalAmount(total);
        order.attachCustomerDetails(CustomerDetails.builder()
                .fullName(cd.fullName())
                .email(cd.email())
                .phone(cd.phone())
                .deliveryAddress(req.deliveryMethod() == DeliveryMethod.SHOP_PICKUP ? null : cd.deliveryAddress())
                .build());

        Order saved = orderRepository.save(order);
        deductStock(saved);
        return saved;
    }

    /** A sale entered by staff for a client (phone / WhatsApp order). Can use special prices and credit. */
    @Transactional
    public Order createStaffSale(StaffSaleRequest req) {
        Client client = clientRepository.findById(req.clientId()).orElseThrow(() -> ApiException.notFound("Client"));
        Map<Long, SaleItemRequest> lines = new LinkedHashMap<>();
        for (SaleItemRequest i : req.items()) {
            lines.merge(i.productId(), i, (a, b) -> new SaleItemRequest(a.productId(),
                    a.quantity() + b.quantity(), a.unitPrice() != null ? a.unitPrice() : b.unitPrice()));
        }

        String address = req.deliveryMethod() == DeliveryMethod.SHOP_PICKUP ? null
                : (req.deliveryAddress() != null && !req.deliveryAddress().isBlank()
                    ? req.deliveryAddress() : joinAddress(client));

        Order order = Order.builder()
                .client(client)
                .user(client.getUser())
                .createdByAdmin(true)
                .notes(req.notes())
                .status(OrderStatus.CONFIRMED)
                .paymentMethod(req.paymentMethod())
                .deliveryMethod(req.deliveryMethod())
                .paid(false)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (SaleItemRequest line : lines.values()) {
            Product p = productRepository.findById(line.productId())
                    .orElseThrow(() -> ApiException.notFound("Product " + line.productId()));
            checkStock(p, line.quantity());
            BigDecimal price = line.unitPrice() != null ? line.unitPrice() : p.getPrice();
            order.addItem(OrderItem.builder().product(p).quantity(line.quantity())
                    .price(price).unitCost(p.getCostPrice()).build());
            total = total.add(price.multiply(BigDecimal.valueOf(line.quantity())));
        }
        order.setTotalAmount(total);
        order.attachCustomerDetails(CustomerDetails.builder()
                .fullName(client.getContactName() != null && !client.getContactName().isBlank()
                        ? client.getName() + " — " + client.getContactName() : client.getName())
                .email(client.getEmail())
                .phone(client.getPhone())
                .deliveryAddress(address)
                .build());

        Order saved = orderRepository.save(order);
        deductStock(saved);

        BigDecimal paidNow = req.amountPaidNow();
        if (paidNow != null && paidNow.signum() > 0) {
            clientService.recordPayment(client, saved.getId(), paidNow.min(total), LocalDate.now(),
                    methodLabel(req.paymentMethod()), req.paymentNote());
            saved = orderRepository.findById(saved.getId()).orElse(saved);
        }
        return saved;
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
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null) {
                    stockService.change(item.getProduct(), StockMovementType.SALE_CANCELLED, item.getQuantity(),
                            item.getUnitCost(), "Order #" + order.getId(), null);
                }
            }
        }
        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);

        // Cash / card machine orders are settled when handed over.
        if ((newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.PICKED_UP)
                && (order.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY
                    || order.getPaymentMethod() == PaymentMethod.POS_ON_DELIVERY)) {
            clientService.settleOrder(saved, methodLabel(order.getPaymentMethod()));
            saved = orderRepository.findById(orderId).orElse(saved);
        }
        return saved;
    }

    /** Called after Stripe confirms an online card payment. */
    @Transactional
    public Order markPaid(Order order, String paymentIntentId) {
        order.setPaymentIntentId(paymentIntentId);
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
        Order saved = orderRepository.save(order);
        clientService.settleOrder(saved, "Card (online)");
        return orderRepository.findById(saved.getId()).orElse(saved);
    }

    private static void checkStock(Product p, int qty) {
        if (p.getStockQuantity() < qty) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Insufficient stock for \"" + p.getName() + "\" (available: " + p.getStockQuantity() + ")");
        }
    }

    private void deductStock(Order order) {
        for (OrderItem item : order.getItems()) {
            stockService.change(item.getProduct(), StockMovementType.SALE, -item.getQuantity(),
                    item.getUnitCost(), "Order #" + order.getId(), null);
        }
    }

    private static String joinAddress(Client c) {
        if (c.getAddress() == null || c.getAddress().isBlank()) return c.getCity();
        return c.getCity() == null || c.getCity().isBlank() ? c.getAddress() : c.getAddress() + ", " + c.getCity();
    }

    static String methodLabel(PaymentMethod m) {
        return switch (m) {
            case ONLINE_PAYMENT -> "Card (online)";
            case CASH_ON_DELIVERY -> "Cash";
            case POS_ON_DELIVERY -> "Card machine";
            case BANK_TRANSFER -> "Bank transfer";
            case CREDIT -> "Payment";
        };
    }
}
