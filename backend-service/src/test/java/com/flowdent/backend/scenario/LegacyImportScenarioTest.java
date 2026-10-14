package com.flowdent.backend.scenario;

import com.jayway.jsonpath.JsonPath;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Importing old (pre-system) financial records and what they do to reports and balances. */
class LegacyImportScenarioTest extends ScenarioTestBase {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static void sheet(Workbook wb, String name, String[]... rows) {
        Sheet s = wb.createSheet(name);
        for (int i = 0; i < rows.length; i++) {
            Row r = s.createRow(i);
            for (int c = 0; c < rows[i].length; c++) r.createCell(c).setCellValue(rows[i][c]);
        }
    }

    private static byte[] bytes(Workbook wb) throws Exception {
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }

    private MvcResult post(String url, MockMultipartFile file, String token, String... params) throws Exception {
        var b = MockMvcRequestBuilders.multipart(url).file(file);
        for (int i = 0; i < params.length; i += 2) b.param(params[i], params[i + 1]);
        return mvc.perform(auth(b, token)).andReturn();
    }

    private static MockMultipartFile xlsx(byte[] data) {
        return new MockMultipartFile("file", "old-2018.xlsx", XLSX, data);
    }

    private byte[] oldBooks() throws Exception {
        Workbook wb = new XSSFWorkbook();
        sheet(wb, "Read me", new String[]{"Instructions only"});
        sheet(wb, "Sales",
                new String[]{"date", "client", "invoice no", "amount (EGP)", "paid (EGP)", "cost of goods (EGP)", "notes"},
                new String[]{"15/03/2018", "Legacy Alpha Clinic", "A-1", "1,000", "600", "400", ""},
                new String[]{"20/03/2018", "  legacy ALPHA  clinic ", "A-2", "500", "", "", ""},
                new String[]{"Total", "", "", "1500", "", "", ""});
        sheet(wb, "Purchases",
                new String[]{"date", "provider", "invoice no", "amount (EGP)", "paid (EGP)", "notes"},
                new String[]{"2018-04-02", "Legacy Beta Supplies", "P-1", "700", "700", ""});
        sheet(wb, "Expenses",
                new String[]{"date", "category", "description", "amount (EGP)"},
                new String[]{"01/05/2018", "Rent", "May rent", "200"});
        sheet(wb, "Clients owe us",
                new String[]{"client", "amount owed (EGP)", "notes"},
                new String[]{"Legacy Gamma Lab", "300", ""});
        sheet(wb, "We owe providers",
                new String[]{"provider", "amount owed (EGP)", "notes"},
                new String[]{"Legacy Beta Supplies", "150", ""});
        return bytes(wb);
    }

    private static Map<String, Object> year(String yearly, int y) {
        List<Map<String, Object>> rows = JsonPath.read(yearly, "$[?(@.year == " + y + ")]");
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    @Test
    @DisplayName("Template downloads; preview saves nothing; import feeds yearly reports and balances; duplicate refused; delete undoes it")
    void fullFlow() throws Exception {
        String admin = adminToken();

        MvcResult tpl = mvc.perform(auth(MockMvcRequestBuilders.get("/api/admin/legacy/template"), admin)).andReturn();
        assertThat(tpl.getResponse().getStatus()).isEqualTo(200);
        assertThat(tpl.getResponse().getContentAsByteArray().length).isGreaterThan(1000);

        byte[] data = oldBooks();
        long stockBefore = JsonPath.<List<Object>>read(getJson("/api/admin/stock", admin), "$").size();

        // Preview: nothing saved.
        MvcResult pv = post("/api/admin/legacy/preview", xlsx(data), admin);
        assertThat(pv.getResponse().getStatus()).as(pv.getResponse().getContentAsString()).isEqualTo(200);
        String preview = pv.getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(preview, "$.counts.SALE")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(preview, "$.counts.PURCHASE")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(preview, "$.counts.EXPENSE")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(preview, "$.counts.CLIENT_BALANCE")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(preview, "$.counts.PROVIDER_BALANCE")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(preview, "$.errorCount")).isZero();
        assertThat(num(preview, "$.totals.SALE")).isEqualByComparingTo("1500");
        assertThat(JsonPath.<List<String>>read(preview, "$.newClients")).hasSize(2); // Alpha (once) + Gamma
        assertThat(getJson("/api/admin/legacy/batches", admin)).isEqualTo("[]");

        // Import.
        MvcResult imp = post("/api/admin/legacy/import", xlsx(data), admin);
        assertThat(imp.getResponse().getStatus()).as(imp.getResponse().getContentAsString()).isEqualTo(201);
        long batchId = id(imp.getResponse().getContentAsString());
        assertThat(num(imp.getResponse().getContentAsString(), "$.totalSales")).isEqualByComparingTo("1500");

        // Yearly report for 2018: sales 1500, cost 400, expenses 200, purchases 700.
        String yearly = getJson("/api/admin/finance/yearly", admin);
        Map<String, Object> y18 = year(yearly, 2018);
        String y18Json = json.writeValueAsString(y18);
        assertThat(num(y18Json, "$.sales")).isEqualByComparingTo("1500");
        assertThat(num(y18Json, "$.costOfGoods")).isEqualByComparingTo("400");
        assertThat(num(y18Json, "$.expenses")).isEqualByComparingTo("200");
        assertThat(num(y18Json, "$.purchases")).isEqualByComparingTo("700");
        assertThat(num(y18Json, "$.netProfit")).isEqualByComparingTo("900");
        String full = getJson("/api/admin/finance/summary?from=2018-01-01&to=2018-12-31", admin);
        assertThat(num(full, "$.moneyIn")).isEqualByComparingTo("1100"); // 600 + blank paid = paid in full 500
        assertThat(num(full, "$.moneyOut")).isEqualByComparingTo("900"); // 700 purchase + 200 expense
        assertThat(JsonPath.<Integer>read(y18Json, "$.oldRecords")).isEqualTo(4);

        // Stock is untouched by old records.
        assertThat(JsonPath.<List<Object>>read(getJson("/api/admin/stock", admin), "$").size()).isEqualTo((int) stockBefore);

        // Auto-created client has an opening balance of 400 (Alpha: 1000-600 is NOT debt; only balance sheets count)
        String clients = getJson("/api/admin/clients", admin);
        List<Map<String, Object>> gamma = JsonPath.read(clients, "$[?(@.name == 'Legacy Gamma Lab')]");
        assertThat(gamma).hasSize(1);
        assertThat(new java.math.BigDecimal(String.valueOf(gamma.get(0).get("openingBalance")))).isEqualByComparingTo("300");
        assertThat(new java.math.BigDecimal(String.valueOf(gamma.get(0).get("balance")))).isEqualByComparingTo("300");
        List<Map<String, Object>> alpha = JsonPath.read(clients, "$[?(@.name == 'Legacy Alpha Clinic')]");
        assertThat(alpha).hasSize(1);
        assertThat(new java.math.BigDecimal(String.valueOf(alpha.get(0).get("balance")))).isEqualByComparingTo("0");

        String providers = getJson("/api/admin/suppliers", admin);
        List<Map<String, Object>> beta = JsonPath.read(providers, "$[?(@.name == 'Legacy Beta Supplies')]");
        assertThat(beta).hasSize(1);
        assertThat(new java.math.BigDecimal(String.valueOf(beta.get(0).get("balance")))).isEqualByComparingTo("150");

        // Records browser.
        String recs = getJson("/api/admin/legacy/records?year=2018&type=SALE", admin);
        assertThat(JsonPath.<Integer>read(recs, "$.total")).isEqualTo(2);

        // Same file again is refused.
        MvcResult dup = post("/api/admin/legacy/import", xlsx(data), admin);
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);

        // A client with old records cannot be deleted.
        long alphaId = ((Number) alpha.get(0).get("id")).longValue();
        mvc.perform(auth(MockMvcRequestBuilders.delete("/api/admin/clients/" + alphaId), admin))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isGreaterThanOrEqualTo(400));

        // Delete the import: reports return to zero for 2018 and balances disappear.
        call("DELETE", "/api/admin/legacy/batches/" + batchId, null, admin, 204);
        assertThat(getJson("/api/admin/legacy/batches", admin)).isEqualTo("[]");
        Map<String, Object> after = year(getJson("/api/admin/finance/yearly", admin), 2018);
        assertThat(JsonPath.<Integer>read(json.writeValueAsString(after), "$.oldRecords")).isZero();
        String clientsAfter = getJson("/api/admin/clients", admin);
        List<Map<String, Object>> gammaAfter = JsonPath.read(clientsAfter, "$[?(@.name == 'Legacy Gamma Lab')]");
        assertThat(new java.math.BigDecimal(String.valueOf(gammaAfter.get(0).get("balance")))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Rows with problems block the import unless skipInvalid; CSV needs a type")
    void problemsAndCsv() throws Exception {
        String admin = adminToken();
        Workbook wb = new XSSFWorkbook();
        sheet(wb, "Expenses",
                new String[]{"date", "category", "description", "amount (EGP)"},
                new String[]{"03/06/2017", "Transport", "Delivery van", "75"},
                new String[]{"not a date", "Rent", "Broken row", "10"},
                new String[]{"05/06/2017", "Rent", "Bad amount", "abc"});
        byte[] data = bytes(wb);

        MvcResult pv = post("/api/admin/legacy/preview", xlsx(data), admin);
        String preview = pv.getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(preview, "$.validRows")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(preview, "$.errorCount")).isEqualTo(2);

        assertThat(post("/api/admin/legacy/import", xlsx(data), admin).getResponse().getStatus()).isEqualTo(400);
        MvcResult ok = post("/api/admin/legacy/import", xlsx(data), admin, "skipInvalid", "true");
        assertThat(ok.getResponse().getStatus()).as(ok.getResponse().getContentAsString()).isEqualTo(201);
        long batch = id(ok.getResponse().getContentAsString());
        assertThat(num(ok.getResponse().getContentAsString(), "$.totalExpenses")).isEqualByComparingTo("75");
        call("DELETE", "/api/admin/legacy/batches/" + batch, null, admin, 204);

        // CSV with a type.
        String csv = "date,category,description,amount\n01/02/2016,Utilities,Electricity,120\n";
        MockMultipartFile f = new MockMultipartFile("file", "exp.csv", "text/csv", csv.getBytes());
        MvcResult c = post("/api/admin/legacy/import", f, admin, "type", "expenses");
        assertThat(c.getResponse().getStatus()).as(c.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(num(c.getResponse().getContentAsString(), "$.totalExpenses")).isEqualByComparingTo("120");
        call("DELETE", "/api/admin/legacy/batches/" + id(c.getResponse().getContentAsString()), null, admin, 204);
    }

    @Test
    @DisplayName("Only admins can reach the old-records endpoints")
    void adminOnly() throws Exception {
        call("GET", "/api/admin/legacy/batches", null, null, 401);
        String orderAdmin = login("orderadmin", "orderadmin");
        call("GET", "/api/admin/legacy/batches", null, orderAdmin, 403);
    }
}
