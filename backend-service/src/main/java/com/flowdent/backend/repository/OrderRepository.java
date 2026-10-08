package com.flowdent.backend.repository;

import com.flowdent.backend.model.Order;
import com.flowdent.backend.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findTop5ByOrderByCreatedAtDesc();
    long countByStatus(OrderStatus status);
    Optional<Order> findByPaymentIntentId(String paymentIntentId);
}
