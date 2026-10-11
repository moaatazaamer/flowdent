package com.flowdent.backend.controller;

import com.flowdent.backend.dto.OpsDtos.PurchaseRequest;
import com.flowdent.backend.dto.OpsDtos.ReceiveRequest;
import com.flowdent.backend.model.Purchase;
import com.flowdent.backend.repository.PurchaseRepository;
import com.flowdent.backend.service.PurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseService purchaseService;

    @GetMapping
    public List<Purchase> all(@RequestParam(required = false) Long supplierId) {
        return supplierId != null
                ? purchaseRepository.findBySupplierIdOrderByPurchaseDateDescIdDesc(supplierId)
                : purchaseRepository.findAllByOrderByPurchaseDateDescIdDesc();
    }

    @GetMapping("/{id}")
    public Purchase one(@PathVariable Long id) {
        return purchaseService.get(id);
    }

    @PostMapping
    public ResponseEntity<Purchase> create(@Valid @RequestBody PurchaseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseService.create(req));
    }

    @PutMapping("/{id}")
    public Purchase update(@PathVariable Long id, @Valid @RequestBody PurchaseRequest req) {
        return purchaseService.update(id, req);
    }

    @PostMapping("/{id}/receive")
    public Purchase receive(@PathVariable Long id, @RequestBody(required = false) ReceiveRequest req) {
        return purchaseService.receive(id, req != null ? req.receivedDate() : null);
    }

    @PostMapping("/{id}/cancel")
    public Purchase cancel(@PathVariable Long id) {
        return purchaseService.cancel(id);
    }
}
