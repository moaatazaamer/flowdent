package com.flowdent.backend.repository;

import com.flowdent.backend.model.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {
    List<SupplierPayment> findAllByOrderByPaidAtDescIdDesc();
    List<SupplierPayment> findBySupplierIdOrderByPaidAtDescIdDesc(Long supplierId);
    boolean existsBySupplierId(Long supplierId);
}
