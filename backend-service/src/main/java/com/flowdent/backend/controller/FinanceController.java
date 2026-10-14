package com.flowdent.backend.controller;

import com.flowdent.backend.dto.OpsDtos.ExpenseRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.Expense;
import com.flowdent.backend.repository.ExpenseRepository;
import com.flowdent.backend.repository.SupplierRepository;
import com.flowdent.backend.service.FinanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Expenses and the finance summary. All amounts EGP. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final ExpenseRepository expenseRepository;
    private final SupplierRepository supplierRepository;

    @GetMapping("/finance/summary")
    public Map<String, Object> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        if (start.isAfter(end)) throw ApiException.badRequest("'from' must be before 'to'");
        return financeService.summary(start, end);
    }

    @GetMapping("/finance/monthly")
    public List<Map<String, Object>> monthly(@RequestParam(defaultValue = "12") int months) {
        return financeService.monthly(Math.min(Math.max(months, 1), 36));
    }

    /** One row per year, from the earliest old or new record up to this year. */
    @GetMapping("/finance/yearly")
    public List<Map<String, Object>> yearly() {
        return financeService.yearly();
    }

    // ---------- expenses ----------

    @GetMapping("/expenses")
    public List<Expense> expenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null && to == null) return expenseRepository.findAllByOrderByDateDescIdDesc();
        return expenseRepository.findByDateBetweenOrderByDateDescIdDesc(
                from != null ? from : LocalDate.of(2000, 1, 1), to != null ? to : LocalDate.now().plusYears(1));
    }

    @PostMapping("/expenses")
    public ResponseEntity<Expense> create(@Valid @RequestBody ExpenseRequest req) {
        Expense e = new Expense();
        apply(e, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseRepository.save(e));
    }

    @PutMapping("/expenses/{id}")
    public Expense update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest req) {
        Expense e = expenseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Expense"));
        apply(e, req);
        return expenseRepository.save(e);
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        expenseRepository.delete(expenseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Expense")));
        return ResponseEntity.noContent().build();
    }

    private void apply(Expense e, ExpenseRequest req) {
        e.setDate(req.date());
        e.setCategory(req.category());
        e.setDescription(req.description().trim());
        e.setAmount(req.amount());
        e.setSupplier(req.supplierId() == null ? null
                : supplierRepository.findById(req.supplierId()).orElseThrow(() -> ApiException.notFound("Provider")));
    }
}
