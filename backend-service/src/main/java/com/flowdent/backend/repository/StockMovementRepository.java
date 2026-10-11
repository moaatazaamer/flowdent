package com.flowdent.backend.repository;

import com.flowdent.backend.model.StockMovement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByProductIdOrderByIdDesc(Long productId, Pageable pageable);
    List<StockMovement> findAllByOrderByIdDesc(Pageable pageable);
    void deleteByProductId(Long productId);
}
