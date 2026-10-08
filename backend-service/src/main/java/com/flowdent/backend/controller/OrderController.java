package com.flowdent.backend.controller;

import com.flowdent.backend.dto.Dtos.CreateOrderRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.Order;
import com.flowdent.backend.model.Role;
import com.flowdent.backend.model.User;
import com.flowdent.backend.repository.OrderRepository;
import com.flowdent.backend.security.CurrentUser;
import com.flowdent.backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final CurrentUser currentUser;

    /** Checkout for a logged-in customer. */
    @PostMapping
    public ResponseEntity<Order> create(@Valid @RequestBody CreateOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(req, currentUser.require()));
    }

    /** Guest checkout: no account; contact details are stored on the order. */
    @PostMapping("/guest")
    public ResponseEntity<Order> createGuest(@Valid @RequestBody CreateOrderRequest req) {
        // If a valid token was sent anyway, attach the order to that user.
        User user = currentUser.find().orElse(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(req, user));
    }

    @GetMapping("/my")
    public List<Order> mine() {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(currentUser.require().getId());
    }

    @GetMapping("/{id}")
    public Order one(@PathVariable Long id) {
        User me = currentUser.require();
        Order o = orderRepository.findById(id).orElseThrow(() -> ApiException.notFound("Order"));
        boolean staff = me.getRole() == Role.ADMIN || me.getRole() == Role.ORDER_ADMIN;
        boolean owner = o.getUser() != null && o.getUser().getId().equals(me.getId());
        if (!staff && !owner) throw ApiException.notFound("Order");
        return o;
    }
}
