package com.flowdent.backend.dto;

import com.flowdent.backend.model.DeliveryMethod;
import com.flowdent.backend.model.OrderStatus;
import com.flowdent.backend.model.PaymentMethod;
import com.flowdent.backend.model.Role;
import com.flowdent.backend.model.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Request / response payloads, grouped for brevity. */
public final class Dtos {

    private Dtos() {}

    // ---------- Auth ----------
    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 100) String password,
            @NotBlank String fullName,
            String phone,
            String address) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record UserResponse(Long id, String email, String fullName, String phone,
                               String address, Role role, LocalDateTime createdAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                    u.getAddress(), u.getRole(), u.getCreatedAt());
        }
    }

    public record AuthResponse(String token, UserResponse user) {}

    // ---------- Profile ----------
    public record ProfileUpdateRequest(String fullName, String phone, String address,
                                       String currentPassword,
                                       @Size(min = 6, max = 100) String newPassword) {}

    public record AddressRequest(String label, @NotBlank String fullAddress, String city,
                                 String postalCode, Boolean isDefault) {}

    // ---------- Catalog ----------
    public record CategoryRequest(@NotBlank String name, String nameAr, String description,
                                  String descriptionAr, String imageUrl) {}

    /** sku / costPrice / reorderLevel are optional: when null the stored value is kept. */
    public record ProductRequest(@NotBlank String name, String nameAr, String description,
                                 String descriptionAr,
                                 @NotNull @DecimalMin("0.00") BigDecimal price,
                                 String imageUrl,
                                 @Min(0) Integer stockQuantity,
                                 Long categoryId,
                                 String sku,
                                 @DecimalMin("0.00") BigDecimal costPrice,
                                 @Min(0) Integer reorderLevel) {}

    // ---------- Orders ----------
    public record OrderItemRequest(@NotNull Long productId, @NotNull @Min(1) Integer quantity) {}

    public record CustomerDetailsRequest(@NotBlank String fullName, @NotBlank @Email String email,
                                         @NotBlank String phone, String deliveryAddress) {}

    public record CreateOrderRequest(
            @NotEmpty List<@Valid OrderItemRequest> items,
            @NotNull PaymentMethod paymentMethod,
            @NotNull DeliveryMethod deliveryMethod,
            @NotNull @Valid CustomerDetailsRequest customerDetails) {}

    public record UpdateStatusRequest(@NotNull OrderStatus status) {}

    // ---------- Payment ----------
    public record CreateIntentRequest(@NotNull Long orderId) {}

    public record ConfirmPaymentRequest(@NotNull Long orderId, @NotBlank String paymentIntentId) {}

    // ---------- Admin ----------
    public record CustomerSummary(Long id, String email, String fullName, String phone,
                                  String address, LocalDateTime createdAt,
                                  long orderCount, BigDecimal totalSpent) {}

    public record DailyStat(String date, BigDecimal revenue, long orders) {}

    public record ImportResult(int created, int updated, List<String> errors) {}

    public record MessageResponse(String message) {}
}
