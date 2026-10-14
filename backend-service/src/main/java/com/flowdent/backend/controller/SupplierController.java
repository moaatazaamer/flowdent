package com.flowdent.backend.controller;

import com.flowdent.backend.dto.OpsDtos.SupplierPaymentRequest;
import com.flowdent.backend.dto.OpsDtos.SupplierRequest;
import com.flowdent.backend.dto.OpsDtos.SupplierSummary;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.Supplier;
import com.flowdent.backend.model.SupplierPayment;
import com.flowdent.backend.repository.*;
import com.flowdent.backend.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Providers (sellers): who the shop buys from, and what is owed to them. */
@RestController
@RequestMapping("/api/admin/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierRepository supplierRepository;
    private final SupplierPaymentRepository paymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductReturnRepository returnRepository;
    private final ExpenseRepository expenseRepository;
    private final LegacyRecordRepository legacyRepository;
    private final LedgerService ledgerService;

    @GetMapping
    public List<SupplierSummary> all() {
        return ledgerService.supplierSummaries();
    }

    /** Provider account: summary plus purchases, payments and returns. */
    @GetMapping("/{id}")
    public Map<String, Object> one(@PathVariable Long id) {
        Supplier s = get(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", ledgerService.supplierSummary(s));
        m.put("purchases", purchaseRepository.findBySupplierIdOrderByPurchaseDateDescIdDesc(id));
        m.put("payments", paymentRepository.findBySupplierIdOrderByPaidAtDescIdDesc(id));
        m.put("returns", returnRepository.findBySupplierIdOrderByReturnDateDescIdDesc(id));
        return m;
    }

    @PostMapping
    public ResponseEntity<SupplierSummary> create(@Valid @RequestBody SupplierRequest req) {
        if (supplierRepository.findByNameIgnoreCase(req.name().trim()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "A provider with this name already exists");
        }
        Supplier s = new Supplier();
        apply(s, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.supplierSummary(supplierRepository.save(s)));
    }

    @PutMapping("/{id}")
    public SupplierSummary update(@PathVariable Long id, @Valid @RequestBody SupplierRequest req) {
        Supplier s = get(id);
        supplierRepository.findByNameIgnoreCase(req.name().trim())
                .filter(o -> !o.getId().equals(id))
                .ifPresent(o -> { throw new ApiException(HttpStatus.CONFLICT, "A provider with this name already exists"); });
        apply(s, req);
        return ledgerService.supplierSummary(supplierRepository.save(s));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Supplier s = get(id);
        if (purchaseRepository.countBySupplierId(id) > 0 || paymentRepository.existsBySupplierId(id)
                || returnRepository.existsBySupplierId(id) || expenseRepository.existsBySupplierId(id)
                || legacyRepository.existsBySupplierId(id)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This provider has history and cannot be deleted. Mark it inactive instead.");
        }
        supplierRepository.delete(s);
        return ResponseEntity.noContent().build();
    }

    // ---------- payments to providers ----------

    @GetMapping("/payments")
    public List<SupplierPayment> payments() {
        return paymentRepository.findAllByOrderByPaidAtDescIdDesc();
    }

    @PostMapping("/payments")
    public ResponseEntity<SupplierPayment> pay(@Valid @RequestBody SupplierPaymentRequest req) {
        Supplier s = get(req.supplierId());
        if (req.purchaseId() != null) {
            var purchase = purchaseRepository.findById(req.purchaseId()).orElseThrow(() -> ApiException.notFound("Purchase"));
            if (!purchase.getSupplier().getId().equals(s.getId())) {
                throw ApiException.badRequest("That purchase belongs to another provider");
            }
        }
        SupplierPayment p = paymentRepository.save(SupplierPayment.builder()
                .supplier(s)
                .purchaseId(req.purchaseId())
                .amount(req.amount())
                .paidAt(req.paidAt())
                .method(req.method())
                .notes(req.notes())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(p);
    }

    @DeleteMapping("/payments/{paymentId}")
    public ResponseEntity<Void> deletePayment(@PathVariable Long paymentId) {
        paymentRepository.delete(paymentRepository.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment")));
        return ResponseEntity.noContent().build();
    }

    private Supplier get(Long id) {
        return supplierRepository.findById(id).orElseThrow(() -> ApiException.notFound("Provider"));
    }

    private static void apply(Supplier s, SupplierRequest req) {
        s.setName(req.name().trim());
        s.setContactName(req.contactName());
        s.setPhone(req.phone());
        s.setEmail(req.email());
        s.setAddress(req.address());
        s.setNotes(req.notes());
        s.setActive(req.active() == null || req.active());
    }
}
