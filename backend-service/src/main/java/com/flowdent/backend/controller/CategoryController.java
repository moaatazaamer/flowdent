package com.flowdent.backend.controller;

import com.flowdent.backend.dto.Dtos.CategoryRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.Category;
import com.flowdent.backend.repository.CategoryRepository;
import com.flowdent.backend.repository.ProductRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @GetMapping
    public List<Category> all() {
        return categoryRepository.findAll(Sort.by("id"));
    }

    @GetMapping("/{id}")
    public Category one(@PathVariable Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
    }

    @PostMapping
    public ResponseEntity<Category> create(@Valid @RequestBody CategoryRequest req) {
        if (categoryRepository.existsByNameIgnoreCase(req.name().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "A category with this name already exists");
        }
        Category c = new Category();
        apply(c, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryRepository.save(c));
    }

    @PutMapping("/{id}")
    public Category update(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        Category c = one(id);
        categoryRepository.findByNameIgnoreCase(req.name().trim())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new ApiException(HttpStatus.CONFLICT, "A category with this name already exists"); });
        apply(c, req);
        return categoryRepository.save(c);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Category c = one(id);
        if (productRepository.countByCategoryId(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Category still has products. Move or delete them first.");
        }
        categoryRepository.delete(c);
        return ResponseEntity.noContent().build();
    }

    private static void apply(Category c, CategoryRequest req) {
        c.setName(req.name().trim());
        c.setNameAr(req.nameAr());
        c.setDescription(req.description());
        c.setDescriptionAr(req.descriptionAr());
        c.setImageUrl(req.imageUrl());
    }
}
