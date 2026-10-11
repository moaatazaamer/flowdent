package com.flowdent.backend.repository;

import com.flowdent.backend.model.ProductReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductReturnRepository extends JpaRepository<ProductReturn, Long> {
    List<ProductReturn> findAllByOrderByReturnDateDescIdDesc();
    List<ProductReturn> findBySupplierIdOrderByReturnDateDescIdDesc(Long supplierId);
    List<ProductReturn> findByClientIdOrderByReturnDateDescIdDesc(Long clientId);
    boolean existsByProductId(Long productId);
    boolean existsBySupplierId(Long supplierId);
    boolean existsByClientId(Long clientId);
}
