package com.flowdent.backend.repository;

import com.flowdent.backend.model.ClientPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientPaymentRepository extends JpaRepository<ClientPayment, Long> {
    List<ClientPayment> findAllByOrderByPaidAtDescIdDesc();
    List<ClientPayment> findByClientIdOrderByPaidAtDescIdDesc(Long clientId);
    List<ClientPayment> findByOrderId(Long orderId);
    boolean existsByClientId(Long clientId);
}
