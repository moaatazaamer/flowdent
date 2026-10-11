package com.flowdent.backend.service;

import com.flowdent.backend.dto.OpsDtos.ReturnRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Returns from clients and returns to providers. */
@Service
@RequiredArgsConstructor
public class ReturnService {

    private final ProductReturnRepository returnRepository;
    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;
    private final OrderRepository orderRepository;
    private final StockService stockService;

    @Transactional
    public ProductReturn create(ReturnRequest req) {
        Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> ApiException.notFound("Product"));

        ProductReturn r = ProductReturn.builder()
                .type(req.type())
                .returnDate(req.returnDate())
                .product(product)
                .quantity(req.quantity())
                .amount(req.amount())
                .settlement(req.settlement() != null ? req.settlement() : ReturnSettlement.CREDIT)
                .unitCost(product.getCostPrice())
                .reason(req.reason())
                .build();

        if (req.type() == ReturnType.CUSTOMER_RETURN) {
            Client client = null;
            if (req.orderId() != null) {
                Order order = orderRepository.findById(req.orderId()).orElseThrow(() -> ApiException.notFound("Order"));
                client = order.getClient();
                r.setOrderId(order.getId());
                // Use the cost recorded on the sale, if the product was on that order.
                order.getItems().stream()
                        .filter(i -> i.getProduct() != null && i.getProduct().getId().equals(product.getId())
                                && i.getUnitCost() != null)
                        .findFirst()
                        .ifPresent(i -> r.setUnitCost(i.getUnitCost()));
            }
            if (req.clientId() != null) {
                client = clientRepository.findById(req.clientId()).orElseThrow(() -> ApiException.notFound("Client"));
            }
            if (client == null) throw ApiException.badRequest("Choose the client (or order) the goods came back from");
            r.setClient(client);
            boolean restock = req.backToStock() == null || req.backToStock();
            r.setBackToStock(restock);
            ProductReturn saved = returnRepository.save(r);
            if (restock) {
                stockService.change(product, StockMovementType.CUSTOMER_RETURN, req.quantity(), r.getUnitCost(),
                        "Return #" + saved.getId(), client.getName());
            }
            return saved;
        }

        // Return to a provider: goods leave stock.
        if (req.supplierId() == null) throw ApiException.badRequest("Choose the provider the goods are returned to");
        Supplier supplier = supplierRepository.findById(req.supplierId())
                .orElseThrow(() -> ApiException.notFound("Provider"));
        r.setSupplier(supplier);
        r.setBackToStock(false);
        ProductReturn saved = returnRepository.save(r);
        stockService.change(product, StockMovementType.SUPPLIER_RETURN, -req.quantity(), r.getUnitCost(),
                "Return #" + saved.getId(), supplier.getName());
        return saved;
    }
}
