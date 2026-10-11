package com.flowdent.backend.controller;

import com.flowdent.backend.dto.OpsDtos.ReturnRequest;
import com.flowdent.backend.model.ProductReturn;
import com.flowdent.backend.repository.ProductReturnRepository;
import com.flowdent.backend.service.ReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/returns")
@RequiredArgsConstructor
public class ReturnController {

    private final ProductReturnRepository returnRepository;
    private final ReturnService returnService;

    @GetMapping
    public List<ProductReturn> all() {
        return returnRepository.findAllByOrderByReturnDateDescIdDesc();
    }

    @PostMapping
    public ResponseEntity<ProductReturn> create(@Valid @RequestBody ReturnRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(returnService.create(req));
    }
}
