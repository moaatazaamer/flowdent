package com.flowdent.backend.controller;

import com.flowdent.backend.dto.OpsDtos.ProductStockView;
import com.flowdent.backend.dto.OpsDtos.StockAdjustRequest;
import com.flowdent.backend.dto.OpsDtos.StockCountRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.ProductRepository;
import com.flowdent.backend.repository.PurchaseRepository;
import com.flowdent.backend.repository.StockMovementRepository;
import com.flowdent.backend.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/stock")
@RequiredArgsConstructor
public class StockController {

    private static final Set<StockMovementType> MANUAL = Set.of(StockMovementType.ADJUSTMENT, StockMovementType.DAMAGE);

    private final ProductRepository productRepository;
    private final PurchaseRepository purchaseRepository;
    private final StockMovementRepository movementRepository;
    private final StockService stockService;

    /** Every product with stock, cost, value and quantity still on order from providers. */
    @GetMapping
    public List<ProductStockView> stock() {
        Map<Long, Integer> onOrder = new HashMap<>();
        purchaseRepository.findAll().stream()
                .filter(p -> p.getStatus() == PurchaseStatus.ORDERED)
                .flatMap(p -> p.getItems().stream())
                .forEach(i -> onOrder.merge(i.getProduct().getId(), i.getQuantity(), Integer::sum));
        return productRepository.findAll(Sort.by("name")).stream()
                .map(p -> ProductStockView.of(p, onOrder.getOrDefault(p.getId(), 0)))
                .toList();
    }

    @GetMapping("/movements")
    public List<StockMovement> movements(@RequestParam(required = false) Long productId,
                                         @RequestParam(defaultValue = "200") int limit) {
        var page = PageRequest.of(0, Math.min(Math.max(limit, 1), 1000));
        return productId != null
                ? movementRepository.findByProductIdOrderByIdDesc(productId, page)
                : movementRepository.findAllByOrderByIdDesc(page);
    }

    @PostMapping("/adjust")
    public ProductStockView adjust(@Valid @RequestBody StockAdjustRequest req) {
        if (!MANUAL.contains(req.type())) {
            throw ApiException.badRequest("Use ADJUSTMENT or DAMAGE for manual changes");
        }
        if (req.quantityChange() == 0) throw ApiException.badRequest("Quantity change cannot be zero");
        if (req.type() == StockMovementType.DAMAGE && req.quantityChange() > 0) {
            throw ApiException.badRequest("Damage / write-off must remove stock");
        }
        Product p = productRepository.findById(req.productId()).orElseThrow(() -> ApiException.notFound("Product"));
        stockService.change(p, req.type(), req.quantityChange(), null, "Manual", req.note());
        return ProductStockView.of(p, 0);
    }

    @PostMapping("/count")
    public ProductStockView count(@Valid @RequestBody StockCountRequest req) {
        Product p = productRepository.findById(req.productId()).orElseThrow(() -> ApiException.notFound("Product"));
        stockService.setTo(p, req.countedQuantity(), StockMovementType.ADJUSTMENT, "Stock count", req.note());
        return ProductStockView.of(p, 0);
    }
}
