package com.flowdent.backend.dto;

import com.flowdent.backend.model.ClientType;
import com.flowdent.backend.model.DeliveryMethod;
import com.flowdent.backend.model.ExpenseCategory;
import com.flowdent.backend.model.PaymentMethod;
import com.flowdent.backend.model.Product;
import com.flowdent.backend.model.ReturnSettlement;
import com.flowdent.backend.model.ReturnType;
import com.flowdent.backend.model.StockMovementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Payloads for the Operations admin APIs (stock, purchases, returns, providers, buyers, expenses). All money is EGP. */
public final class OpsDtos {

    private OpsDtos() {}

    // ---------- Stock ----------

    /** Admin view of a product including internal cost and stock fields. */
    public record ProductStockView(Long id, String name, String nameAr, String sku, String imageUrl,
                                   Long categoryId, String categoryName, String categoryNameAr,
                                   BigDecimal price, BigDecimal costPrice, BigDecimal marginPercent,
                                   int stockQuantity, int reorderLevel, int onOrder,
                                   BigDecimal stockValue, String stockStatus) {

        public static ProductStockView of(Product p, int onOrder) {
            BigDecimal margin = null;
            if (p.getPrice() != null && p.getPrice().signum() > 0 && p.getCostPrice() != null) {
                margin = p.getPrice().subtract(p.getCostPrice())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(p.getPrice(), 1, RoundingMode.HALF_UP);
            }
            int stock = p.getStockQuantity() == null ? 0 : p.getStockQuantity();
            String status = stock <= 0 ? "OUT" : stock <= p.reorderLevelOrDefault() ? "LOW" : "OK";
            return new ProductStockView(p.getId(), p.getName(), p.getNameAr(), p.getSku(), p.getImageUrl(),
                    p.getCategory() != null ? p.getCategory().getId() : null,
                    p.getCategory() != null ? p.getCategory().getName() : null,
                    p.getCategory() != null ? p.getCategory().getNameAr() : null,
                    p.getPrice(),
                    p.getCostPrice() == null ? null : p.getCostPrice().setScale(2, RoundingMode.HALF_UP),
                    margin, stock, p.reorderLevelOrDefault(), onOrder,
                    p.costOrZero().multiply(BigDecimal.valueOf(Math.max(stock, 0))).setScale(2, RoundingMode.HALF_UP),
                    status);
        }
    }

    /** Add or remove stock by hand. type must be ADJUSTMENT or DAMAGE. */
    public record StockAdjustRequest(@NotNull Long productId,
                                     @NotNull Integer quantityChange,
                                     @NotNull StockMovementType type,
                                     @Size(max = 1000) String note) {}

    /** Enter the counted quantity; the difference is recorded as an adjustment. */
    public record StockCountRequest(@NotNull Long productId, @NotNull @Min(0) Integer countedQuantity,
                                    @Size(max = 1000) String note) {}

    // ---------- Providers (sellers) ----------

    public record SupplierRequest(@NotBlank String name, String contactName, String phone, String email,
                                  String address, String notes, Boolean active) {}

    public record SupplierSummary(Long id, String name, String contactName, String phone, String email,
                                  String address, String notes, boolean active, LocalDateTime createdAt,
                                  long purchaseCount, BigDecimal totalBought, BigDecimal totalPaid,
                                  BigDecimal totalReturned, BigDecimal balance, LocalDate lastPurchase) {}

    public record SupplierPaymentRequest(@NotNull Long supplierId, Long purchaseId,
                                         @NotNull @DecimalMin("0.01") BigDecimal amount,
                                         @NotNull LocalDate paidAt, String method, String notes) {}

    // ---------- Purchases ----------

    public record PurchaseItemRequest(@NotNull Long productId, @NotNull @Min(1) Integer quantity,
                                      @NotNull @DecimalMin("0.00") BigDecimal unitCost) {}

    public record PurchaseRequest(@NotNull Long supplierId,
                                  @NotNull LocalDate purchaseDate,
                                  String invoiceNumber,
                                  @DecimalMin("0.00") BigDecimal shippingCost,
                                  @DecimalMin("0.00") BigDecimal customsCost,
                                  @DecimalMin("0.00") BigDecimal otherCost,
                                  String notes,
                                  /** true = goods already arrived: add to stock immediately. */
                                  Boolean receivedNow,
                                  /** optional: amount paid to the provider right away. */
                                  @DecimalMin("0.00") BigDecimal amountPaid,
                                  String paymentMethod,
                                  @NotEmpty List<@Valid PurchaseItemRequest> items) {}

    public record ReceiveRequest(LocalDate receivedDate) {}

    // ---------- Returns ----------

    public record ReturnRequest(@NotNull ReturnType type,
                                @NotNull LocalDate returnDate,
                                @NotNull Long productId,
                                @NotNull @Min(1) Integer quantity,
                                @NotNull @DecimalMin("0.00") BigDecimal amount,
                                ReturnSettlement settlement,
                                Long clientId, Long orderId,
                                Long supplierId,
                                Boolean backToStock,
                                @Size(max = 1000) String reason) {}

    // ---------- Clients (buyers) ----------

    public record ClientRequest(@NotBlank String name, ClientType type, String contactName, String phone,
                                String email, String address, String city, String notes) {}

    public record ClientSummary(Long id, String name, ClientType type, String contactName, String phone,
                                String email, String address, String city, String notes,
                                boolean registered, LocalDateTime createdAt,
                                long orderCount, BigDecimal totalSales, BigDecimal totalPaid,
                                BigDecimal totalReturned, BigDecimal balance, LocalDateTime lastOrder) {}

    public record ClientPaymentRequest(@NotNull Long clientId, Long orderId,
                                       @NotNull @DecimalMin("0.01") BigDecimal amount,
                                       @NotNull LocalDate paidAt, String method, String notes) {}

    /** A sale entered by staff (phone / WhatsApp order). unitPrice overrides the list price. */
    public record SaleItemRequest(@NotNull Long productId, @NotNull @Min(1) Integer quantity,
                                  @DecimalMin("0.00") BigDecimal unitPrice) {}

    public record StaffSaleRequest(@NotNull Long clientId,
                                   @NotEmpty List<@Valid SaleItemRequest> items,
                                   @NotNull PaymentMethod paymentMethod,
                                   @NotNull DeliveryMethod deliveryMethod,
                                   String deliveryAddress,
                                   String notes,
                                   /** Amount received right now (0 / null = nothing yet). */
                                   @DecimalMin("0.00") BigDecimal amountPaidNow,
                                   String paymentNote) {}

    // ---------- Expenses ----------

    public record ExpenseRequest(@NotNull LocalDate date, @NotNull ExpenseCategory category,
                                 @NotBlank String description,
                                 @NotNull @DecimalMin("0.01") BigDecimal amount,
                                 Long supplierId) {}
}
