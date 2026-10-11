package com.flowdent.backend.service;

import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.Product;
import com.flowdent.backend.model.StockMovement;
import com.flowdent.backend.model.StockMovementType;
import com.flowdent.backend.repository.ProductRepository;
import com.flowdent.backend.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The only place product stock quantities change. Every change writes a {@link StockMovement},
 * so the stock history is always complete.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;

    /** Adds (positive) or removes (negative) stock and logs the movement. */
    @Transactional
    public StockMovement change(Product p, StockMovementType type, int delta,
                                BigDecimal unitCost, String reference, String note) {
        int current = p.getStockQuantity() == null ? 0 : p.getStockQuantity();
        int after = current + delta;
        if (after < 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Not enough stock for \"" + p.getName() + "\" (available: " + current + ")");
        }
        p.setStockQuantity(after);
        productRepository.save(p);
        if (delta == 0) return null;
        return movementRepository.save(StockMovement.builder()
                .product(p)
                .type(type)
                .quantityChange(delta)
                .stockAfter(after)
                .unitCost(unitCost != null ? unitCost : p.getCostPrice())
                .reference(reference)
                .note(note)
                .createdBy(currentUserName())
                .build());
    }

    /** Sets stock to an exact quantity (stock count / product form), logging the difference. */
    @Transactional
    public StockMovement setTo(Product p, int newQuantity, StockMovementType type, String reference, String note) {
        int current = p.getStockQuantity() == null ? 0 : p.getStockQuantity();
        return change(p, type, newQuantity - current, null, reference, note);
    }

    /**
     * Goods coming in at a known cost: updates the product's weighted-average cost price,
     * then adds the stock.
     */
    @Transactional
    public StockMovement receive(Product p, int qty, BigDecimal unitCost, StockMovementType type,
                                 String reference, String note) {
        if (qty <= 0) return null;
        int current = Math.max(0, p.getStockQuantity() == null ? 0 : p.getStockQuantity());
        if (unitCost != null) {
            BigDecimal oldCost = p.getCostPrice();
            BigDecimal newCost;
            if (oldCost == null || current == 0) {
                newCost = unitCost;
            } else {
                newCost = oldCost.multiply(BigDecimal.valueOf(current))
                        .add(unitCost.multiply(BigDecimal.valueOf(qty)))
                        .divide(BigDecimal.valueOf(current + qty), 4, RoundingMode.HALF_UP);
            }
            p.setCostPrice(newCost.setScale(4, RoundingMode.HALF_UP));
        }
        return change(p, type, qty, unitCost, reference, note);
    }

    private static String currentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()) ? null : auth.getName();
    }
}
