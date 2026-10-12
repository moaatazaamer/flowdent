package com.flowdent.backend.controller;

import com.flowdent.backend.dto.Dtos.ProductRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.Product;
import com.flowdent.backend.model.StockMovementType;
import com.flowdent.backend.repository.*;
import com.flowdent.backend.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.RoundingMode;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductReturnRepository returnRepository;
    private final StockMovementRepository movementRepository;
    private final StockService stockService;

    @GetMapping
    public List<Product> all() {
        return productRepository.findAll(Sort.by("id"));
    }

    @GetMapping("/{id}")
    public Product one(@PathVariable Long id) {
        return productRepository.findById(id).orElseThrow(() -> ApiException.notFound("Product"));
    }

    @GetMapping("/category/{categoryId}")
    public List<Product> byCategory(@PathVariable Long categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    @GetMapping("/search")
    public List<Product> search(@RequestParam(name = "q", defaultValue = "") String q) {
        String term = q.trim();
        return term.isEmpty() ? List.of() : productRepository.search(term);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest req) {
        Product p = new Product();
        p.setStockQuantity(0);
        apply(p, req);
        Product saved = productRepository.save(p);
        int opening = req.stockQuantity() == null ? 0 : req.stockQuantity();
        if (opening > 0) {
            stockService.change(saved, StockMovementType.INITIAL, opening, saved.getCostPrice(), "New product", null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        Product p = one(id);
        apply(p, req);
        Product saved = productRepository.save(p);
        if (req.stockQuantity() != null) {
            stockService.setTo(saved, req.stockQuantity(), StockMovementType.ADJUSTMENT, "Product edit", null);
        }
        return saved;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Product p = one(id);
        if (orderRepository.existsByItemsProductId(id) || purchaseRepository.existsByItemsProductId(id)
                || returnRepository.existsByProductId(id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Product has sales, purchases or returns and cannot be deleted. Set its stock to 0 instead.");
        }
        movementRepository.deleteByProductId(id);
        productRepository.delete(p);
        return ResponseEntity.noContent().build();
    }

    private void apply(Product p, ProductRequest req) {
        p.setName(req.name().trim());
        p.setNameAr(req.nameAr());
        p.setDescription(req.description());
        p.setDescriptionAr(req.descriptionAr());
        p.setPrice(req.price());
        p.setImageUrl(req.imageUrl());
        p.setCategory(req.categoryId() == null ? null
                : categoryRepository.findById(req.categoryId()).orElseThrow(() -> ApiException.notFound("Category")));
        if (req.sku() != null) {
            String sku = req.sku().trim();
            if (sku.isEmpty()) {
                p.setSku(null);
            } else {
                productRepository.findBySkuIgnoreCase(sku)
                        .filter(other -> !other.getId().equals(p.getId()))
                        .ifPresent(o -> { throw new ApiException(HttpStatus.CONFLICT, "SKU " + sku + " is already used"); });
                p.setSku(sku);
            }
        }
        if (req.brand() != null) p.setBrand(req.brand().isBlank() ? null : req.brand().trim());
        if (req.costPrice() != null) p.setCostPrice(req.costPrice().setScale(4, RoundingMode.HALF_UP));
        if (req.reorderLevel() != null) p.setReorderLevel(req.reorderLevel());
    }
}
