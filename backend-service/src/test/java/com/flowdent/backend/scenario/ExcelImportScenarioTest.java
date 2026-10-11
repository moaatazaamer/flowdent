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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Bulk import of products from Excel, including SKU, cost price and stock history. */
class ExcelImportScenarioTest extends ScenarioTestBase {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private String upload(String url, byte[] bytes, String token) throws Exception {
        MvcResult r = mvc.perform(auth(MockMvcRequestBuilders.multipart(url)
                        .file(new MockMultipartFile("file", "products.xlsx", XLSX, bytes)), token))
                .andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return r.getResponse().getContentAsString();
    }

    private byte[] download(String url, String token) throws Exception {
        MvcResult r = mvc.perform(auth(MockMvcRequestBuilders.get(url), token)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentType()).isEqualTo(XLSX);
        return r.getResponse().getContentAsByteArray();
    }

    /** Re-writes the template's example row with our own values. */
    private byte[] sheetWith(byte[] template, String name, String sku, String price, String stock, String cost) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.getSheetAt(0);
            Row row = s.getRow(1);
            row.getCell(0).setCellValue(name);
            row.getCell(4).setCellValue(price);
            row.getCell(5).setCellValue(stock);
            row.getCell(8).setCellValue(sku);
            row.getCell(9).setCellValue(cost);
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("Template import creates the product, re-import by SKU updates it, history is kept")
    void importCreatesThenUpdates() throws Exception {
        String admin = adminToken();
        byte[] template = download("/api/admin/import/template/products", admin);

        String first = upload("/api/admin/import/products",
                sheetWith(template, "Scenario Imported Gloves", "SCN-IMP-1", "450", "100", "300"), admin);
        assertThat(JsonPath.<Integer>read(first, "$.created")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(first, "$.updated")).isZero();
        assertThat(JsonPath.<List<Object>>read(first, "$.errors")).isEmpty();

        List<Map<String, Object>> found = JsonPath.read(getJson("/api/admin/stock", admin), "$[?(@.sku == 'SCN-IMP-1')]");
        assertThat(found).hasSize(1);
        long productId = ((Number) found.get(0).get("id")).longValue();
        String row = findById(getJson("/api/admin/stock", admin), productId);
        assertThat(num(row, "$.stockQuantity")).isEqualByComparingTo("100");
        assertThat(num(row, "$.costPrice")).isEqualByComparingTo("300");
        assertThat(num(row, "$.price")).isEqualByComparingTo("450");

        // Same SKU, new name, price and stock → update (matched by SKU), stock change logged.
        String second = upload("/api/admin/import/products",
                sheetWith(template, "Scenario Imported Gloves v2", "SCN-IMP-1", "480", "90", "300"), admin);
        assertThat(JsonPath.<Integer>read(second, "$.created")).isZero();
        assertThat(JsonPath.<Integer>read(second, "$.updated")).isEqualTo(1);
        row = findById(getJson("/api/admin/stock", admin), productId);
        assertThat(str(row, "$.name")).isEqualTo("Scenario Imported Gloves v2");
        assertThat(num(row, "$.stockQuantity")).isEqualByComparingTo("90");

        List<String> types = JsonPath.read(getJson("/api/admin/stock/movements?productId=" + productId, admin), "$[*].type");
        assertThat(types).containsExactly("ADJUSTMENT", "INITIAL");
    }

    @Test
    @DisplayName("Bad rows are skipped with a reason; good rows still import")
    void badRowsReported() throws Exception {
        String admin = adminToken();
        byte[] template = download("/api/admin/import/template/products", admin);
        byte[] bad = sheetWith(template, "Scenario Bad Price", "SCN-BAD-1", "abc", "1", "1");

        String result = upload("/api/admin/import/products", bad, admin);
        assertThat(JsonPath.<Integer>read(result, "$.created")).isZero();
        List<String> errors = JsonPath.read(result, "$.errors");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("Row 2").contains("price");
    }

    @Test
    @DisplayName("Only .xlsx files are accepted")
    void rejectsOtherFiles() throws Exception {
        String admin = adminToken();
        MvcResult r = mvc.perform(auth(MockMvcRequestBuilders.multipart("/api/admin/import/products")
                .file(new MockMultipartFile("file", "products.csv", "text/csv", "a,b".getBytes())), admin)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }
}
