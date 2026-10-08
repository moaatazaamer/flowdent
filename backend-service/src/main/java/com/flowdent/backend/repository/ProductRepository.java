package com.flowdent.backend.repository;

import com.flowdent.backend.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategoryId(Long categoryId);

    Optional<Product> findFirstByNameIgnoreCase(String name);

    long countByCategoryId(Long categoryId);

    List<Product> findByStockQuantityLessThanOrderByStockQuantityAsc(Integer threshold);

    @Query("""
            SELECT p FROM Product p LEFT JOIN p.category c
            WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))
               OR p.nameAr LIKE CONCAT('%', :q, '%')
               OR p.descriptionAr LIKE CONCAT('%', :q, '%')
               OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR c.nameAr LIKE CONCAT('%', :q, '%')
            """)
    List<Product> search(@Param("q") String q);
}
