package com.flowdent.backend.service;

import com.flowdent.backend.dto.Dtos.ImportResult;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.Category;
import com.flowdent.backend.model.Product;
import com.flowdent.backend.model.StockMovementType;
import com.flowdent.backend.repository.CategoryRepository;
import com.flowdent.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Bulk import / export of catalog data as .xlsx.
 * Rows are matched by English name: existing records are updated, new ones created.
 */
@Service
@RequiredArgsConstructor
public class ExcelService {

    public static final String[] CATEGORY_HEADERS =
            {"name", "nameAr", "description", "descriptionAr", "imageUrl"};
    public static final String[] PRODUCT_HEADERS =
            {"name", "nameAr", "description", "descriptionAr", "price", "stockQuantity", "categoryName", "imageUrl",
             "sku", "costPrice", "reorderLevel", "brand"};

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final DataFormatter formatter = new DataFormatter();

    // ------------------------------------------------------------------ import

    @Transactional
    public ImportResult importCategories(MultipartFile file) {
        int created = 0, updated = 0;
        List<String> errors = new ArrayList<>();
        try (Workbook wb = open(file)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isBlank(row)) continue;
                String name = str(row, 0);
                if (name.isEmpty()) {
                    errors.add("Row " + (r + 1) + ": name is required");
                    continue;
                }
                Category c = categoryRepository.findByNameIgnoreCase(name).orElse(null);
                boolean isNew = c == null;
                if (isNew) c = new Category();
                c.setName(name);
                c.setNameAr(nullIfEmpty(str(row, 1)));
                c.setDescription(nullIfEmpty(str(row, 2)));
                c.setDescriptionAr(nullIfEmpty(str(row, 3)));
                String img = str(row, 4);
                if (!img.isEmpty() || isNew) c.setImageUrl(nullIfEmpty(img));
                categoryRepository.save(c);
                if (isNew) created++; else updated++;
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the Excel file: " + e.getMessage());
        }
        return new ImportResult(created, updated, errors);
    }

    @Transactional
    public ImportResult importProducts(MultipartFile file) {
        int created = 0, updated = 0;
        List<String> errors = new ArrayList<>();
        try (Workbook wb = open(file)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isBlank(row)) continue;
                int line = r + 1;
                String name = str(row, 0);
                if (name.isEmpty()) {
                    errors.add("Row " + line + ": name is required");
                    continue;
                }
                BigDecimal price;
                try {
                    price = new BigDecimal(str(row, 4).replace(",", ""));
                    if (price.signum() < 0) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    errors.add("Row " + line + ": invalid price '" + str(row, 4) + "'");
                    continue;
                }
                int stock;
                try {
                    String s = str(row, 5);
                    stock = s.isEmpty() ? 0 : new BigDecimal(s).intValueExact();
                    if (stock < 0) throw new NumberFormatException();
                } catch (NumberFormatException | ArithmeticException ex) {
                    errors.add("Row " + line + ": invalid stockQuantity '" + str(row, 5) + "'");
                    continue;
                }
                String catName = str(row, 6);
                Category category = null;
                if (!catName.isEmpty()) {
                    category = categoryRepository.findByNameIgnoreCase(catName).orElse(null);
                    if (category == null) {
                        errors.add("Row " + line + ": category '" + catName + "' does not exist");
                        continue;
                    }
                }
                BigDecimal cost = null;
                String costStr = str(row, 9);
                if (!costStr.isEmpty()) {
                    try {
                        cost = new BigDecimal(costStr.replace(",", ""));
                        if (cost.signum() < 0) throw new NumberFormatException();
                    } catch (NumberFormatException ex) {
                        errors.add("Row " + line + ": invalid costPrice '" + costStr + "'");
                        continue;
                    }
                }
                Integer reorder = null;
                String reorderStr = str(row, 10);
                if (!reorderStr.isEmpty()) {
                    try {
                        reorder = new BigDecimal(reorderStr).intValueExact();
                        if (reorder < 0) throw new NumberFormatException();
                    } catch (NumberFormatException | ArithmeticException ex) {
                        errors.add("Row " + line + ": invalid reorderLevel '" + reorderStr + "'");
                        continue;
                    }
                }
                String sku = str(row, 8);
                Product p = sku.isEmpty() ? null : productRepository.findBySkuIgnoreCase(sku).orElse(null);
                if (p == null) p = productRepository.findFirstByNameIgnoreCase(name).orElse(null);
                boolean isNew = p == null;
                if (isNew) {
                    p = new Product();
                    p.setStockQuantity(0);
                }
                if (!sku.isEmpty()) {
                    final Long selfId = p.getId();
                    var clash = productRepository.findBySkuIgnoreCase(sku)
                            .filter(o -> !o.getId().equals(selfId));
                    if (clash.isPresent()) {
                        errors.add("Row " + line + ": SKU '" + sku + "' is already used by another product");
                        continue;
                    }
                    p.setSku(sku);
                }
                if (cost != null) p.setCostPrice(cost);
                String brand = str(row, 11);
                if (!brand.isEmpty()) p.setBrand(brand);
                if (reorder != null) p.setReorderLevel(reorder);
                p.setName(name);
                p.setNameAr(nullIfEmpty(str(row, 1)));
                p.setDescription(nullIfEmpty(str(row, 2)));
                p.setDescriptionAr(nullIfEmpty(str(row, 3)));
                p.setPrice(price);
                p.setCategory(category);
                String img = str(row, 7);
                if (!img.isEmpty() || isNew) p.setImageUrl(nullIfEmpty(img));
                Product saved = productRepository.save(p);
                // An empty stock cell leaves the current quantity unchanged.
                if (isNew || !str(row, 5).isEmpty()) {
                    stockService.setTo(saved, stock, isNew ? StockMovementType.INITIAL : StockMovementType.ADJUSTMENT,
                            "Excel import", null);
                }
                if (isNew) created++; else updated++;
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the Excel file: " + e.getMessage());
        }
        return new ImportResult(created, updated, errors);
    }

    // ------------------------------------------------------------------ templates / export

    public byte[] categoryTemplate() {
        return build("Categories", CATEGORY_HEADERS, List.<Object[]>of(new Object[]{
                "Orthodontics", "تقويم الأسنان", "Brackets, wires and elastics", "حاصرات وأسلاك ومطاطات", ""}));
    }

    public byte[] productTemplate() {
        return build("Products", PRODUCT_HEADERS, List.<Object[]>of(new Object[]{
                "Saliva Ejectors", "شفاطات لعاب",
                "Disposable saliva ejectors", "شفاطات لعاب للاستعمال مرة واحدة",
                120, 50, "Clinical Disposables", "", "ORA-EXAMPLE", 80, 20, "Ora"}));
    }

    public byte[] exportCategories() {
        List<Object[]> rows = categoryRepository.findAll(Sort.by("id")).stream()
                .map(c -> new Object[]{c.getName(), c.getNameAr(), c.getDescription(), c.getDescriptionAr(), c.getImageUrl()})
                .toList();
        return build("Categories", CATEGORY_HEADERS, rows);
    }

    public byte[] exportProducts() {
        List<Object[]> rows = productRepository.findAll(Sort.by("id")).stream()
                .map(p -> new Object[]{p.getName(), p.getNameAr(), p.getDescription(), p.getDescriptionAr(),
                        p.getPrice(), p.getStockQuantity(),
                        p.getCategory() != null ? p.getCategory().getName() : "", p.getImageUrl(),
                        p.getSku(), p.getCostPrice(), p.getReorderLevel(), p.getBrand()})
                .toList();
        return build("Products", PRODUCT_HEADERS, rows);
    }

    // ------------------------------------------------------------------ helpers

    private Workbook open(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("No file uploaded");
        String n = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!n.endsWith(".xlsx")) throw ApiException.badRequest("Please upload an .xlsx file");
        try (InputStream in = file.getInputStream()) {
            return new XSSFWorkbook(in);
        }
    }

    private byte[] build(String sheetName, String[] headers, List<Object[]> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }
            int r = 1;
            for (Object[] values : rows) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < values.length; i++) {
                    Object v = values[i];
                    Cell c = row.createCell(i);
                    if (v instanceof Number num) c.setCellValue(num.doubleValue());
                    else c.setCellValue(v == null ? "" : v.toString());
                }
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 28 * 256);
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build workbook", e);
        }
    }

    private String str(Row row, int col) {
        Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return c == null ? "" : formatter.formatCellValue(c).trim();
    }

    private boolean isBlank(Row row) {
        if (row == null) return true;
        for (int i = 0; i < Math.max(row.getLastCellNum(), 0); i++) {
            if (!str(row, i).isEmpty()) return false;
        }
        return true;
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
