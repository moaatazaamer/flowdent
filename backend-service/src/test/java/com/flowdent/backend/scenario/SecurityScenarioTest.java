package com.flowdent.backend.scenario;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Who can see what: public shop, customers, order staff and admins. */
class SecurityScenarioTest extends ScenarioTestBase {

    @Test
    @DisplayName("The catalogue is public; admin APIs need a token")
    void publicCatalogueAndProtectedAdmin() throws Exception {
        String products = getJson("/api/products", null);
        assertThat(JsonPath.<Integer>read(products, "$.length()")).isGreaterThanOrEqualTo(13);  // seeded Ora catalogue
        getJson("/api/categories", null);
        getJson("/api/payment/config", null);

        call("GET", "/api/admin/clients", null, null, 401);
        call("GET", "/api/admin/dashboard", null, null, 401);
        call("GET", "/api/orders/my", null, null, 401);
        call("POST", "/api/products", map("name", "Hack", "price", 1), null, 401);
    }

    @Test
    @DisplayName("Wrong password is refused")
    void wrongPassword() throws Exception {
        call("POST", "/api/auth/login", map("email", "admin", "password", "nope"), null, 401);
    }

    @Test
    @DisplayName("A registered customer can shop but not open the admin")
    void customerCannotOpenAdmin() throws Exception {
        String reg = call("POST", "/api/auth/register", map("email", "security.doctor@example.com",
                "password", "secret123", "fullName", "Dr. Security"), null, 201);
        String token = JsonPath.read(reg, "$.token");
        assertThat(str(reg, "$.user.role")).isEqualTo("CUSTOMER");

        getJson("/api/orders/my", token);
        getJson("/api/auth/me", token);
        call("GET", "/api/admin/clients", null, token, 403);
        call("GET", "/api/admin/orders", null, token, 403);
        call("GET", "/api/admin/finance/summary", null, token, 403);
        call("POST", "/api/products", map("name", "Hack", "price", 1), token, 403);

        call("POST", "/api/auth/register", map("email", "security.doctor@example.com",
                "password", "secret123", "fullName", "Again"), null, 409);
    }

    @Test
    @DisplayName("Order staff manage orders only; admins see everything")
    void roles() throws Exception {
        String orderStaff = login("orderadmin", "orderadmin");
        getJson("/api/admin/orders", orderStaff);
        getJson("/api/admin/dashboard", orderStaff);
        call("GET", "/api/admin/clients", null, orderStaff, 403);
        call("GET", "/api/admin/suppliers", null, orderStaff, 403);
        call("GET", "/api/admin/finance/summary", null, orderStaff, 403);
        call("GET", "/api/admin/stock", null, orderStaff, 403);

        String admin = adminToken();
        for (String url : new String[]{"/api/admin/clients", "/api/admin/suppliers", "/api/admin/purchases",
                "/api/admin/returns", "/api/admin/stock", "/api/admin/expenses", "/api/admin/finance/summary",
                "/api/admin/finance/monthly", "/api/admin/dashboard", "/api/admin/orders"}) {
            getJson(url, admin);
        }
    }

    @Test
    @DisplayName("Seeded data: providers, clients, EGP prices and cost prices")
    void seededData() throws Exception {
        String admin = adminToken();
        assertThat(JsonPath.<Integer>read(getJson("/api/admin/suppliers", admin), "$.length()")).isGreaterThanOrEqualTo(3);
        assertThat(JsonPath.<Integer>read(getJson("/api/admin/clients", admin), "$.length()")).isGreaterThanOrEqualTo(4);
        String stock = getJson("/api/admin/stock", admin);
        java.util.List<Object> withoutCost = JsonPath.read(stock, "$[?(@.sku =~ /[A-Z]{3}-\\d{3}/ && @.costPrice == null)]");
        assertThat(withoutCost).isEmpty();

        // The price list: USD import cost × 52 = EGP cost; selling price starts at cost, stock at 0.
        java.util.List<java.util.Map<String, Object>> bib = JsonPath.read(stock, "$[?(@.sku == 'ORA-001')]");
        assertThat(bib).hasSize(1);
        assertThat(bib.get(0).get("name")).isEqualTo("Dental Bib");
        assertThat(bib.get(0).get("brand")).isEqualTo("Ora");
        assertThat(new java.math.BigDecimal(bib.get(0).get("costPrice").toString())).isEqualByComparingTo("234.00");
        assertThat(new java.math.BigDecimal(bib.get(0).get("price").toString())).isEqualByComparingTo("234.00");
        assertThat(((Number) bib.get(0).get("stockQuantity")).intValue()).isZero();
        java.util.List<Object> needles = JsonPath.read(stock, "$[?(@.sku == 'ORA-013' && @.name == 'Irrigation Needles')]");
        assertThat(needles).hasSize(1);
        assertThat(str(getJson("/api/payment/config", null), "$.currency")).isEqualToIgnoringCase("egp");
    }
}
