package com.flowdent.backend.controller;

import com.flowdent.backend.dto.Dtos.ProductRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.Product;
import com.flowdent.backend.repository.CategoryRepository;
import com.flowdent.backend.repository.ProductRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

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
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(productRepository.save(p));
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        Product p = one(id);
        apply(p, req);
        return productRepository.save(p);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Product p = one(id);
        try {
            productRepository.delete(p);
            productRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Product appears in existing orders and cannot be deleted. Set its stock to 0 instead.");
        }
        return ResponseEntity.noContent().build();
    }

    private void apply(Product p, ProductRequest req) {
        p.setName(req.name().trim());
        p.setNameAr(req.nameAr());
        p.setDescription(req.description());
        p.setDescriptionAr(req.descriptionAr());
        p.setPrice(req.price());
        p.setImageUrl(req.imageUrl());
        p.setStockQuantity(req.stockQuantity() == null ? 0 : req.stockQuantity());
        p.setCategory(req.categoryId() == null ? null
                : categoryRepository.findById(req.categoryId()).orElseThrow(() -> ApiException.notFound("Category")));
    }
}
