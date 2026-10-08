package com.flowdent.backend.controller;

import com.flowdent.backend.dto.Dtos.ConfirmPaymentRequest;
import com.flowdent.backend.dto.Dtos.CreateIntentRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.Order;
import com.flowdent.backend.model.OrderStatus;
import com.flowdent.backend.model.PaymentMethod;
import com.flowdent.backend.repository.OrderRepository;
import com.flowdent.backend.service.OrderService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Stripe flow: the client first creates the order (ONLINE_PAYMENT, unpaid), then calls
 * /create-intent with the order id; the amount is taken from the stored order total.
 * After Stripe.js confirms the card, the client calls /confirm, which re-checks the
 * PaymentIntent status with Stripe before marking the order paid.
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.publishable-key}")
    private String publishableKey;

    @Value("${stripe.currency}")
    private String currency;

    @PostConstruct
    void init() {
        Stripe.apiKey = secretKey;
    }

    private boolean configured() {
        return secretKey != null && secretKey.startsWith("sk_") && !secretKey.contains("placeholder");
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of(
                "publishableKey", publishableKey,
                "currency", currency,
                "enabled", configured() && !publishableKey.contains("placeholder"));
    }

    @PostMapping("/create-intent")
    public Map<String, Object> createIntent(@Valid @RequestBody CreateIntentRequest req) {
        if (!configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Online payment is not configured. Set STRIPE_SECRET_KEY and STRIPE_PUBLISHABLE_KEY.");
        }
        Order order = orderRepository.findById(req.orderId()).orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getPaymentMethod() != PaymentMethod.ONLINE_PAYMENT) {
            throw ApiException.badRequest("Order is not set for online payment");
        }
        if (order.isPaid()) throw ApiException.badRequest("Order is already paid");
        if (order.getStatus() == OrderStatus.CANCELLED) throw ApiException.badRequest("Order is cancelled");

        long amountMinor = order.getTotalAmount().multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        try {
            PaymentIntent intent;
            if (order.getPaymentIntentId() != null) {
                // Reuse an existing intent if the customer retries.
                intent = PaymentIntent.retrieve(order.getPaymentIntentId());
            } else {
                PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                        .setAmount(amountMinor)
                        .setCurrency(currency)
                        .putMetadata("orderId", String.valueOf(order.getId()))
                        .addPaymentMethodType("card")
                        .build();
                intent = PaymentIntent.create(params);
                order.setPaymentIntentId(intent.getId());
                orderRepository.save(order);
            }
            return Map.of("clientSecret", intent.getClientSecret(),
                    "paymentIntentId", intent.getId(),
                    "amount", order.getTotalAmount(),
                    "currency", currency);
        } catch (StripeException e) {
            log.warn("Stripe create-intent failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Payment provider error: " + e.getMessage());
        }
    }

    @PostMapping("/confirm")
    public Order confirm(@Valid @RequestBody ConfirmPaymentRequest req) {
        if (!configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Online payment is not configured");
        }
        Order order = orderRepository.findById(req.orderId()).orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getPaymentIntentId() == null || !order.getPaymentIntentId().equals(req.paymentIntentId())) {
            throw ApiException.badRequest("Payment does not match this order");
        }
        if (order.isPaid()) return order;
        try {
            PaymentIntent intent = PaymentIntent.retrieve(req.paymentIntentId());
            if (!"succeeded".equals(intent.getStatus())) {
                throw ApiException.badRequest("Payment not completed (status: " + intent.getStatus() + ")");
            }
            return orderService.markPaid(order, intent.getId());
        } catch (StripeException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Payment provider error: " + e.getMessage());
        }
    }
}
