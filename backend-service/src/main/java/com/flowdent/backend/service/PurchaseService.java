package com.flowdent.backend.service;

import com.flowdent.backend.dto.OpsDtos.PurchaseItemRequest;
import com.flowdent.backend.dto.OpsDtos.PurchaseRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.ProductRepository;
import com.flowdent.backend.repository.PurchaseRepository;
import com.flowdent.backend.repository.SupplierPaymentRepository;
import com.flowdent.backend.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Buying from providers. All amounts EGP. */
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final StockService stockService;

    @Transactional
    public Purchase create(PurchaseRequest req) {
        Purchase p = new Purchase();
        p.setStatus(PurchaseStatus.ORDERED);
        apply(p, req);
        Purchase saved = purchaseRepository.save(p);

        if (req.amountPaid() != null && req.amountPaid().signum() > 0) {
            supplierPaymentRepository.save(SupplierPayment.builder()
                    .supplier(saved.getSupplier())
                    .purchaseId(saved.getId())
                    .amount(req.amountPaid())
                    .paidAt(saved.getPurchaseDate())
                    .method(req.paymentMethod())
                    .notes("Purchase #" + saved.getId())
                    .build());
        }
        if (Boolean.TRUE.equals(req.receivedNow())) {
            saved = receive(saved.getId(), saved.getPurchaseDate());
        }
        return saved;
    }

    /** Only purchases still waiting for goods can be edited. */
    @Transactional
    public Purchase update(Long id, PurchaseRequest req) {
        Purchase p = get(id);
        if (p.getStatus() != PurchaseStatus.ORDERED) {
            throw ApiException.badRequest("Only purchases that are not yet received can be edited");
        }
        apply(p, req);
        Purchase saved = purchaseRepository.save(p);
        if (Boolean.TRUE.equals(req.receivedNow())) {
            saved = receive(saved.getId(), saved.getPurchaseDate());
        }
        return saved;
    }

    /**
     * Goods arrived: add stock. Each unit's landed cost = provider price + its share of the
     * extra costs (spread by value), and product cost prices are updated (weighted average).
     */
    @Transactional
    public Purchase receive(Long id, LocalDate receivedDate) {
        Purchase p = get(id);
        if (p.getStatus() != PurchaseStatus.ORDERED) {
            throw ApiException.badRequest("This purchase is already " + p.getStatus().name().toLowerCase());
        }
        BigDecimal goods = p.getGoodsTotal();
        BigDecimal extra = p.getExtraCosts();
        int totalUnits = p.getItems().stream().mapToInt(PurchaseItem::getQuantity).sum();

        for (PurchaseItem item : p.getItems()) {
            BigDecimal unit = item.getUnitCost();
            BigDecimal share;
            if (extra.signum() == 0) {
                share = BigDecimal.ZERO;
            } else if (goods.signum() > 0) {
                // Extra cost per unit proportional to the unit's value.
                share = extra.multiply(unit).divide(goods, 4, RoundingMode.HALF_UP);
            } else {
                // All items free (samples): split extra costs evenly per unit.
                share = extra.divide(BigDecimal.valueOf(Math.max(totalUnits, 1)), 4, RoundingMode.HALF_UP);
            }
            BigDecimal landed = unit.add(share).setScale(4, RoundingMode.HALF_UP);
            item.setLandedUnitCost(landed);
            stockService.receive(item.getProduct(), item.getQuantity(), landed, StockMovementType.PURCHASE,
                    "Purchase #" + p.getId(), p.getSupplier().getName());
        }
        p.setStatus(PurchaseStatus.RECEIVED);
        p.setReceivedDate(receivedDate != null ? receivedDate : LocalDate.now());
        return purchaseRepository.save(p);
    }

    @Transactional
    public Purchase cancel(Long id) {
        Purchase p = get(id);
        if (p.getStatus() == PurchaseStatus.RECEIVED) {
            throw ApiException.badRequest("Received purchases cannot be cancelled. Record a return to the provider instead.");
        }
        p.setStatus(PurchaseStatus.CANCELLED);
        return purchaseRepository.save(p);
    }

    public Purchase get(Long id) {
        return purchaseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Purchase"));
    }

    private void apply(Purchase p, PurchaseRequest req) {
        Supplier s = supplierRepository.findById(req.supplierId()).orElseThrow(() -> ApiException.notFound("Provider"));
        p.setSupplier(s);
        p.setPurchaseDate(req.purchaseDate());
        p.setInvoiceNumber(blankToNull(req.invoiceNumber()));
        p.setShippingCost(nz(req.shippingCost()));
        p.setCustomsCost(nz(req.customsCost()));
        p.setOtherCost(nz(req.otherCost()));
        p.setNotes(req.notes());
        if (p.getItems() == null) p.setItems(new java.util.ArrayList<>());
        p.getItems().clear();
        for (PurchaseItemRequest i : req.items()) {
            Product prod = productRepository.findById(i.productId())
                    .orElseThrow(() -> ApiException.notFound("Product " + i.productId()));
            p.addItem(PurchaseItem.builder().product(prod).quantity(i.quantity()).unitCost(i.unitCost()).build());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
