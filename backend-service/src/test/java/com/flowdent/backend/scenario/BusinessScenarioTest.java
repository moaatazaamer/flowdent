package com.flowdent.backend.scenario;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The full B2B cycle, end to end through the REST API, with EGP amounts:
 * buy from a provider (with shipping + customs) → sell to a clinic on credit → collect payment →
 * client return → return to provider → expense → check stock, balances and profit.
 */
class BusinessScenarioTest extends ScenarioTestBase {

    @Test
    @DisplayName("Buy, sell on credit, collect, return both ways, and the books add up")
    void fullBusinessCycle() throws Exception {
        String admin = adminToken();
        String financeBefore = getJson("/api/admin/finance/summary", admin);

        // 1. New product with no stock and no cost yet.
        String product = postJson("/api/products", map(
                "name", "Scenario Bond 5ml", "nameAr", "مادة ربط تجريبية", "price", 200,
                "stockQuantity", 0, "sku", "SCN-BOND", "reorderLevel", 5), admin);
        long productId = id(product);

        // 2. Provider.
        long supplierId = id(postJson("/api/admin/suppliers", map("name", "Scenario Supplier Co.",
                "phone", "+20 2 0000 0001"), admin));

        // 3. Purchase 10 × 100 EGP + shipping 60 + customs 40, received now, 600 paid on the spot.
        String purchase = postJson("/api/admin/purchases", map(
                "supplierId", supplierId, "purchaseDate", TODAY, "invoiceNumber", "SCN-INV-1",
                "shippingCost", 60, "customsCost", 40, "otherCost", 0,
                "receivedNow", true, "amountPaid", 600, "paymentMethod", "Cash",
                "items", List.of(map("productId", productId, "quantity", 10, "unitCost", 100))), admin);
        assertThat(str(purchase, "$.status")).isEqualTo("RECEIVED");
        assertThat(num(purchase, "$.goodsTotal")).isEqualByComparingTo("1000");
        assertThat(num(purchase, "$.totalCost")).isEqualByComparingTo("1100");
        assertThat(num(purchase, "$.items[0].landedUnitCost")).isEqualByComparingTo("110");

        // 4. Stock 10 at landed cost 110; cost price is never exposed publicly.
        String stockRow = findById(getJson("/api/admin/stock", admin), productId);
        assertThat(num(stockRow, "$.stockQuantity")).isEqualByComparingTo("10");
        assertThat(num(stockRow, "$.costPrice")).isEqualByComparingTo("110");
        assertThat(num(stockRow, "$.stockValue")).isEqualByComparingTo("1100");
        assertThat(num(stockRow, "$.marginPercent")).isEqualByComparingTo("45.0");
        String publicProduct = getJson("/api/products/" + productId, null);
        assertThat(publicProduct).doesNotContain("costPrice").doesNotContain("reorderLevel");

        // 5. We owe the provider 1000 − 600 = 400 (extra costs are not owed to the provider).
        assertThat(num(getJson("/api/admin/suppliers/" + supplierId, admin), "$.summary.balance"))
                .isEqualByComparingTo("400");

        // 6. Client (clinic) added by staff — no online account.
        long clientId = id(postJson("/api/admin/clients", map("name", "Scenario Dental Clinic",
                "type", "CLINIC", "contactName", "Dr. Test", "phone", "+20 100 999 0001", "city", "Cairo"), admin));

        // 7. Phone sale: 3 × 200 on account, 100 received now.
        String sale = postJson("/api/admin/clients/sales", map(
                "clientId", clientId, "paymentMethod", "CREDIT", "deliveryMethod", "SHOP_PICKUP",
                "amountPaidNow", 100, "paymentNote", "deposit",
                "items", List.of(map("productId", productId, "quantity", 3, "unitPrice", 200))), admin);
        long orderId = id(sale);
        assertThat(num(sale, "$.totalAmount")).isEqualByComparingTo("600");
        assertThat(JsonPath.<Boolean>read(sale, "$.paid")).isFalse();
        assertThat(JsonPath.<Boolean>read(sale, "$.createdByAdmin")).isTrue();
        assertThat(clientBalance(clientId, admin)).isEqualByComparingTo("500");

        // 8. Client pays the remaining 500 → balance 0 and the order is paid.
        postJson("/api/admin/clients/payments", map("clientId", clientId, "orderId", orderId,
                "amount", 500, "paidAt", TODAY, "method", "Bank transfer"), admin);
        assertThat(clientBalance(clientId, admin)).isEqualByComparingTo("0");
        String orderRow = findById(getJson("/api/admin/orders", admin), orderId);
        assertThat(JsonPath.<Boolean>read(orderRow, "$.paid")).isTrue();
        assertThat(str(orderRow, "$.client.name")).isEqualTo("Scenario Dental Clinic");

        // 9. Client returns 1 unit (resellable) as credit: stock 7 → 8, client now in credit −200.
        postJson("/api/admin/returns", map("type", "CUSTOMER_RETURN", "returnDate", TODAY,
                "productId", productId, "quantity", 1, "amount", 200, "settlement", "CREDIT",
                "clientId", clientId, "orderId", orderId, "backToStock", true, "reason", "wrong size"), admin);
        assertThat(clientBalance(clientId, admin)).isEqualByComparingTo("-200");

        // 10. Return 2 units to the provider for credit 220: stock 8 → 6, we owe 400 − 220 = 180.
        postJson("/api/admin/returns", map("type", "SUPPLIER_RETURN", "returnDate", TODAY,
                "productId", productId, "quantity", 2, "amount", 220, "settlement", "CREDIT",
                "supplierId", supplierId, "reason", "near expiry"), admin);
        assertThat(num(getJson("/api/admin/suppliers/" + supplierId, admin), "$.summary.balance"))
                .isEqualByComparingTo("180");
        stockRow = findById(getJson("/api/admin/stock", admin), productId);
        assertThat(num(stockRow, "$.stockQuantity")).isEqualByComparingTo("6");

        // 11. Stock history shows every change, newest first.
        String moves = getJson("/api/admin/stock/movements?productId=" + productId, admin);
        List<String> types = JsonPath.read(moves, "$[*].type");
        assertThat(types).containsExactly("SUPPLIER_RETURN", "CUSTOMER_RETURN", "SALE", "PURCHASE");
        List<Integer> after = JsonPath.read(moves, "$[*].stockAfter");
        assertThat(after).containsExactly(6, 8, 7, 10);

        // 12. Expense.
        postJson("/api/admin/expenses", map("date", TODAY, "category", "RENT",
                "description", "Scenario rent", "amount", 300), admin);

        // 13. Finance changed by exactly this scenario.
        String financeAfter = getJson("/api/admin/finance/summary", admin);
        assertDelta(financeBefore, financeAfter, "sales", "600");
        assertDelta(financeBefore, financeAfter, "returns", "200");
        assertDelta(financeBefore, financeAfter, "netSales", "400");
        assertDelta(financeBefore, financeAfter, "costOfGoods", "220");   // 3 × 110 sold − 1 × 110 back
        assertDelta(financeBefore, financeAfter, "grossProfit", "180");
        assertDelta(financeBefore, financeAfter, "expenses", "300");
        assertDelta(financeBefore, financeAfter, "netProfit", "-120");
        assertDelta(financeBefore, financeAfter, "purchases", "1000");
        assertDelta(financeBefore, financeAfter, "extraCosts", "100");
        assertDelta(financeBefore, financeAfter, "moneyIn", "600");        // 100 deposit + 500 transfer
        assertDelta(financeBefore, financeAfter, "moneyOut", "1000");      // 600 provider + 300 rent + 100 extra
        assertDelta(financeBefore, financeAfter, "stockValue", "660");     // 6 × 110
    }

    @Test
    @DisplayName("Ordered purchase adds stock only when received; cancelled purchase never does")
    void purchaseLifecycle() throws Exception {
        String admin = adminToken();
        long productId = id(postJson("/api/products", map("name", "Scenario Files 25mm", "price", 300,
                "stockQuantity", 2, "costPrice", 150), admin));
        long supplierId = id(postJson("/api/admin/suppliers", map("name", "Scenario Endo Supplier"), admin));

        long ordered = id(postJson("/api/admin/purchases", map("supplierId", supplierId, "purchaseDate", TODAY,
                "receivedNow", false, "items", List.of(map("productId", productId, "quantity", 2, "unitCost", 210))), admin));
        String row = findById(getJson("/api/admin/stock", admin), productId);
        assertThat(num(row, "$.stockQuantity")).isEqualByComparingTo("2");
        assertThat(num(row, "$.onOrder")).isEqualByComparingTo("2");

        String received = call("POST", "/api/admin/purchases/" + ordered + "/receive", map("receivedDate", TODAY), admin, 200);
        assertThat(str(received, "$.status")).isEqualTo("RECEIVED");
        row = findById(getJson("/api/admin/stock", admin), productId);
        assertThat(num(row, "$.stockQuantity")).isEqualByComparingTo("4");
        assertThat(num(row, "$.costPrice")).isEqualByComparingTo("180");   // (2 × 150 + 2 × 210) / 4
        assertThat(num(row, "$.onOrder")).isEqualByComparingTo("0");

        call("POST", "/api/admin/purchases/" + ordered + "/cancel", null, admin, 400);
        call("POST", "/api/admin/purchases/" + ordered + "/receive", null, admin, 400);

        long another = id(postJson("/api/admin/purchases", map("supplierId", supplierId, "purchaseDate", TODAY,
                "items", List.of(map("productId", productId, "quantity", 5, "unitCost", 200))), admin));
        String cancelled = call("POST", "/api/admin/purchases/" + another + "/cancel", null, admin, 200);
        assertThat(str(cancelled, "$.status")).isEqualTo("CANCELLED");
        assertThat(num(findById(getJson("/api/admin/stock", admin), productId), "$.stockQuantity"))
                .isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("Stock adjustments, write-offs and counts are validated and logged")
    void stockAdjustments() throws Exception {
        String admin = adminToken();
        long productId = id(postJson("/api/products", map("name", "Scenario Mirrors", "price", 80,
                "stockQuantity", 10, "costPrice", 40), admin));

        call("POST", "/api/admin/stock/adjust", map("productId", productId, "quantityChange", -2,
                "type", "DAMAGE", "note", "broken"), admin, 200);
        call("POST", "/api/admin/stock/adjust", map("productId", productId, "quantityChange", 3,
                "type", "DAMAGE"), admin, 400);                       // write-off must remove stock
        call("POST", "/api/admin/stock/adjust", map("productId", productId, "quantityChange", 1,
                "type", "SALE"), admin, 400);                         // not a manual type
        call("POST", "/api/admin/stock/adjust", map("productId", productId, "quantityChange", -50,
                "type", "ADJUSTMENT"), admin, 409);                   // below zero
        String counted = call("POST", "/api/admin/stock/count", map("productId", productId,
                "countedQuantity", 5, "note", "monthly count"), admin, 200);
        assertThat(num(counted, "$.stockQuantity")).isEqualByComparingTo("5");

        List<String> types = JsonPath.read(getJson("/api/admin/stock/movements?productId=" + productId, admin), "$[*].type");
        assertThat(types).containsExactly("ADJUSTMENT", "DAMAGE", "INITIAL");
    }

    @Test
    @DisplayName("Providers and clients with history cannot be deleted")
    void deleteRules() throws Exception {
        String admin = adminToken();
        long supplierId = id(postJson("/api/admin/suppliers", map("name", "Scenario Delete Supplier"), admin));
        long clientId = id(postJson("/api/admin/clients", map("name", "Scenario Delete Clinic"), admin));
        long emptyClient = id(postJson("/api/admin/clients", map("name", "Scenario Empty Clinic"), admin));

        postJson("/api/admin/suppliers/payments", map("supplierId", supplierId, "amount", 50, "paidAt", TODAY), admin);
        postJson("/api/admin/clients/payments", map("clientId", clientId, "amount", 50, "paidAt", TODAY), admin);

        call("DELETE", "/api/admin/suppliers/" + supplierId, null, admin, 409);
        call("DELETE", "/api/admin/clients/" + clientId, null, admin, 409);
        call("DELETE", "/api/admin/clients/" + emptyClient, null, admin, 204);
        call("POST", "/api/admin/suppliers", map("name", "Scenario Delete Supplier"), admin, 409);  // duplicate name
    }

    @Test
    @DisplayName("Brand (e.g. Ora) is public, searchable and editable")
    void brandIsPublicAndSearchable() throws Exception {
        String admin = adminToken();
        long productId = id(postJson("/api/products", map("name", "Scenario Brand Composite", "price", 500,
                "stockQuantity", 1, "brand", "Ora"), admin));

        assertThat(str(getJson("/api/products/" + productId, null), "$.brand")).isEqualTo("Ora");
        List<Object> found = JsonPath.read(getJson("/api/products/search?q=ora", null), "$[?(@.id == " + productId + ")]");
        assertThat(found).hasSize(1);
        assertThat(str(findById(getJson("/api/admin/stock", admin), productId), "$.brand")).isEqualTo("Ora");

        // Seeded sample catalogue has Ora products.
        List<Object> ora = JsonPath.read(getJson("/api/products", null), "$[?(@.brand == 'Ora')]");
        assertThat(ora.size()).isGreaterThanOrEqualTo(8);

        // Blank brand clears it; omitting brand keeps it.
        call("PUT", "/api/products/" + productId, map("name", "Scenario Brand Composite", "price", 500), admin, 200);
        assertThat(str(getJson("/api/products/" + productId, null), "$.brand")).isEqualTo("Ora");
        call("PUT", "/api/products/" + productId, map("name", "Scenario Brand Composite", "price", 500, "brand", " "), admin, 200);
        assertThat(JsonPath.<Object>read(getJson("/api/products/" + productId, null), "$.brand")).isNull();
    }

    private BigDecimal clientBalance(long clientId, String token) throws Exception {
        return num(getJson("/api/admin/clients/" + clientId, token), "$.summary.balance");
    }

    private static void assertDelta(String before, String after, String key, String expected) {
        BigDecimal delta = num(after, "$." + key).subtract(num(before, "$." + key));
        assertThat(delta).as(key).isEqualByComparingTo(expected);
    }
}
