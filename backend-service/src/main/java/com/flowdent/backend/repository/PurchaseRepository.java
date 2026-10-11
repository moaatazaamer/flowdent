package com.flowdent.backend.repository;

import com.flowdent.backend.model.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    List<Purchase> findAllByOrderByPurchaseDateDescIdDesc();
    List<Purchase> findBySupplierIdOrderByPurchaseDateDescIdDesc(Long supplierId);
    boolean existsByItemsProductId(Long productId);
    long countBySupplierId(Long supplierId);
}
