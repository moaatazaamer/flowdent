package com.flowdent.backend.scenario;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Website orders: guest checkout, delivery, cancellation and stock protection. */
class GuestCheckoutScenarioTest extends ScenarioTestBase {

    private Map<String, Object> guestOrder(long productId, int qty, String payment, String phone) {
        return map("items", List.of(map("productId", productId, "quantity", qty)),
                "paymentMethod", payment, "deliveryMethod", "HOME_DELIVERY",
                "customerDetails", map("fullName", "Dr. Guest Buyer", "email", "guest.buyer@example.com",
                        "phone", phone, "deliveryAddress", "15 Tahrir St, Cairo"));
    }

    @Test
    @DisplayName("Guest order → client created, stock deducted, cash collected on delivery")
    void guestCashOnDelivery() throws Exception {
        String admin = adminToken();
        long productId = id(postJson("/api/products", map("name", "Scenario Guest Gloves", "price", 250,
                "stockQuantity", 5, "costPrice", 150), admin));

        String order = postJson("/api/orders/guest", guestOrder(productId, 2, "CASH_ON_DELIVERY", "+20 100 555 0101"), null);
        long orderId = id(order);
        assertThat(num(order, "$.totalAmount")).isEqualByComparingTo("500");
        assertThat(str(order, "$.status")).isEqualTo("PENDING");
        long clientId = ((Number) JsonPath.read(order, "$.client.id")).longValue();
        assertThat(num(findById(getJson("/api/admin/stock", admin), productId), "$.stockQuantity"))
                .isEqualByComparingTo("3");

        // The guest became a client who owes 500 until the order is delivered.
        String client = getJson("/api/admin/clients/" + clientId, admin);
        assertThat(num(client, "$.summary.balance")).isEqualByComparingTo("500");
        assertThat(str(client, "$.summary.registered")).isEqualTo("false");

        call("PUT", "/api/admin/orders/" + orderId + "/status", map("status", "SHIPPED"), admin, 200);
        String delivered = call("PUT", "/api/admin/orders/" + orderId + "/status", map("status", "DELIVERED"), admin, 200);
        assertThat(JsonPath.<Boolean>read(delivered, "$.paid")).isTrue();
        assertThat(num(getJson("/api/admin/clients/" + clientId, admin), "$.summary.balance")).isEqualByComparingTo("0");

        // A second order from the same phone/email lands on the same client.
        String second = postJson("/api/orders/guest", guestOrder(productId, 1, "POS_ON_DELIVERY", "+20 100 555 0101"), null);
        assertThat(((Number) JsonPath.read(second, "$.client.id")).longValue()).isEqualTo(clientId);

        // Cancelling it puts the stock back.
        call("PUT", "/api/admin/orders/" + id(second) + "/status", map("status", "CANCELLED"), admin, 200);
        assertThat(num(findById(getJson("/api/admin/stock", admin), productId), "$.stockQuantity"))
                .isEqualByComparingTo("3");
        call("PUT", "/api/admin/orders/" + id(second) + "/status", map("status", "CONFIRMED"), admin, 400);
    }

    @Test
    @DisplayName("Guests cannot over-order, pay on account, or skip the address")
    void guestValidation() throws Exception {
        String admin = adminToken();
        long productId = id(postJson("/api/products", map("name", "Scenario Limited Item", "price", 100,
                "stockQuantity", 1, "costPrice", 50), admin));

        call("POST", "/api/orders/guest", guestOrder(productId, 2, "CASH_ON_DELIVERY", "+20 100 555 0202"), null, 409);
        call("POST", "/api/orders/guest", guestOrder(productId, 1, "CREDIT", "+20 100 555 0202"), null, 400);

        Map<String, Object> noAddress = guestOrder(productId, 1, "CASH_ON_DELIVERY", "+20 100 555 0202");
        noAddress.put("customerDetails", map("fullName", "Dr. X", "email", "x@example.com", "phone", "+20 1"));
        call("POST", "/api/orders/guest", noAddress, null, 400);

        // Nothing was sold.
        assertThat(num(findById(getJson("/api/admin/stock", admin), productId), "$.stockQuantity"))
                .isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("A product that was sold cannot be deleted")
    void soldProductCannotBeDeleted() throws Exception {
        String admin = adminToken();
        long productId = id(postJson("/api/products", map("name", "Scenario Sold Item", "price", 100,
                "stockQuantity", 3), admin));
        long unsold = id(postJson("/api/products", map("name", "Scenario Unsold Item", "price", 100,
                "stockQuantity", 3), admin));
        postJson("/api/orders/guest", guestOrder(productId, 1, "CASH_ON_DELIVERY", "+20 100 555 0303"), null);

        call("DELETE", "/api/products/" + productId, null, admin, 409);
        call("DELETE", "/api/products/" + unsold, null, admin, 204);
        call("GET", "/api/products/" + unsold, null, null, 404);
    }
}
