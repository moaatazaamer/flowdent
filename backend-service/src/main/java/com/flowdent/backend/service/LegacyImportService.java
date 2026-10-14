package com.flowdent.backend.service;

import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.ClientRepository;
import com.flowdent.backend.repository.LegacyBatchRepository;
import com.flowdent.backend.repository.LegacyRecordRepository;
import com.flowdent.backend.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports old (pre-system) financial records from an Excel workbook or a CSV file.
 * <p>
 * Old records are history only: they show up in the finance reports and in the records list,
 * and the "what is still owed" sheets become opening balances for clients and providers.
 * They never touch stock. Importing is two steps: {@link #preview} reads the file and reports
 * what it found without saving anything, then {@link #commit} saves it as one batch that can
 * be deleted again as a whole.
 */
@Service
@RequiredArgsConstructor
public class LegacyImportService {

    private static final int MAX_MESSAGES = 100;
    private static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000000");

    private final LegacyBatchRepository batchRepository;
    private final LegacyRecordRepository recordRepository;
    private final ClientRepository clientRepository;
    private final SupplierRepository supplierRepository;

    // ------------------------------------------------------------------ result types

    /** One valid row read from the file. */
    public record Line(LegacyType type, int line, String sheet, LocalDate date, String party,
                       ExpenseCategory category, String reference, String description,
                       BigDecimal amount, BigDecimal paid, BigDecimal cost) {}

    public record Preview(String fileName, LocalDateTime alreadyImportedAt,
                          Map<String, Integer> counts, Map<String, BigDecimal> totals,
                          List<Map<String, Object>> years,
                          List<String> newClients, List<String> newProviders,
                          int validRows, int problemRows, int errorCount,
                          List<String> errors, List<String> warnings, List<Line> sample) {}

    public record BatchView(Long id, String fileName, LocalDateTime importedAt,
                            int sales, int purchases, int expenses, int clientBalances, int providerBalances,
                            BigDecimal totalSales, BigDecimal totalPurchases, BigDecimal totalExpenses) {
        static BatchView of(LegacyBatch b) {
            return new BatchView(b.getId(), b.getFileName(), b.getImportedAt(), b.getSales(), b.getPurchases(),
                    b.getExpenses(), b.getClientBalances(), b.getProviderBalances(),
                    b.getTotalSales(), b.getTotalPurchases(), b.getTotalExpenses());
        }
    }

    public record RecordView(Long id, LegacyType type, LocalDate date, String party, ExpenseCategory category,
                             String reference, String description, BigDecimal amount, BigDecimal paid,
                             BigDecimal cost, Long batchId) {}

    // ------------------------------------------------------------------ parsing model

    private enum F { DATE, PARTY, REFERENCE, AMOUNT, PAID, COST, NOTES, CATEGORY }

    private record RawRow(int line, List<String> cells) {}

    private record Table(String sheet, LegacyType type, List<String> headers, List<RawRow> rows) {}

    private static final class Parsed {
        String fileName;
        String hash;
        final List<Line> lines = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        int problemRows;
        int errorCount;
    }

    // Header names (normalised) that are understood for each field. English and Arabic.
    private static final Map<F, List<String>> ALIASES = new EnumMap<>(F.class);

    static {
        ALIASES.put(F.DATE, norm("date", "invoice date", "transaction date", "day", "التاريخ", "تاريخ", "تاريخ الفاتورة"));
        ALIASES.put(F.PARTY, norm("client", "client name", "customer", "customer name", "buyer", "clinic", "doctor",
                "provider", "provider name", "supplier", "supplier name", "vendor", "seller", "name",
                "العميل", "اسم العميل", "الطبيب", "العيادة", "المورد", "اسم المورد", "الاسم", "الجهة"));
        ALIASES.put(F.REFERENCE, norm("invoice", "invoice no", "invoice number", "invoice #", "reference", "ref", "no", "number",
                "رقم الفاتورة", "الفاتورة", "رقم", "مرجع", "المرجع"));
        ALIASES.put(F.AMOUNT, norm("amount", "total", "value", "sum", "price", "balance", "owed", "amount owed",
                "المبلغ", "الاجمالي", "إجمالي", "الإجمالي", "اجمالي", "القيمة", "قيمة", "الرصيد", "المتبقي", "المستحق"));
        ALIASES.put(F.PAID, norm("paid", "paid amount", "amount paid", "received", "payment", "payments",
                "المدفوع", "مدفوع", "المسدد", "المحصل", "المستلم", "الواصل"));
        ALIASES.put(F.COST, norm("cost", "cost of goods", "cogs", "goods cost",
                "التكلفة", "تكلفة", "تكلفة البضاعة"));
        ALIASES.put(F.NOTES, norm("notes", "note", "description", "details", "memo", "remarks", "item",
                "ملاحظات", "ملاحظة", "بيان", "البيان", "الوصف", "وصف", "تفاصيل"));
        ALIASES.put(F.CATEGORY, norm("category", "type", "kind", "expense type", "expense category",
                "الفئة", "النوع", "البند", "نوع المصروف", "التصنيف"));
    }

    private static final Set<String> TOTAL_WORDS = new HashSet<>(norm("total", "grand total", "sum", "subtotal",
            "الاجمالي", "إجمالي", "الإجمالي", "المجموع", "اجمالي"));

    private static final DataFormatter FORMATTER = new DataFormatter();

    // ------------------------------------------------------------------ public API

    @Transactional(readOnly = true)
    public Preview preview(MultipartFile file, String type) {
        Parsed p = parse(file, type);
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (LegacyType t : LegacyType.values()) {
            counts.put(t.name(), 0);
            totals.put(t.name(), BigDecimal.ZERO);
        }
        TreeMap<Integer, BigDecimal[]> byYear = new TreeMap<>();
        for (Line l : p.lines) {
            counts.merge(l.type().name(), 1, Integer::sum);
            totals.merge(l.type().name(), l.amount(), BigDecimal::add);
            int slot = switch (l.type()) {
                case SALE -> 0;
                case PURCHASE -> 1;
                case EXPENSE -> 2;
                default -> -1;
            };
            if (slot >= 0) {
                BigDecimal[] acc = byYear.computeIfAbsent(l.date().getYear(),
                        y -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                acc[slot] = acc[slot].add(l.amount());
            }
        }
        List<Map<String, Object>> years = new ArrayList<>();
        byYear.forEach((y, a) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("year", y);
            row.put("sales", a[0]);
            row.put("purchases", a[1]);
            row.put("expenses", a[2]);
            years.add(row);
        });

        Set<String> knownClients = new HashSet<>();
        clientRepository.findAll().forEach(c -> knownClients.add(key(c.getName())));
        Set<String> knownProviders = new HashSet<>();
        supplierRepository.findAll().forEach(s -> knownProviders.add(key(s.getName())));
        Map<String, String> newClients = new LinkedHashMap<>();
        Map<String, String> newProviders = new LinkedHashMap<>();
        for (Line l : p.lines) {
            boolean client = l.type() == LegacyType.SALE || l.type() == LegacyType.CLIENT_BALANCE;
            boolean provider = l.type() == LegacyType.PURCHASE || l.type() == LegacyType.PROVIDER_BALANCE;
            if (client && !knownClients.contains(key(l.party()))) newClients.putIfAbsent(key(l.party()), l.party());
            if (provider && !knownProviders.contains(key(l.party()))) newProviders.putIfAbsent(key(l.party()), l.party());
        }

        LocalDateTime already = batchRepository.findFirstByFileHash(p.hash).map(LegacyBatch::getImportedAt).orElse(null);
        return new Preview(p.fileName, already, counts, totals, years,
                capped(newClients.values()), capped(newProviders.values()),
                p.lines.size(), p.problemRows, p.errorCount, p.errors, p.warnings,
                p.lines.subList(0, Math.min(8, p.lines.size())));
    }

    @Transactional
    public BatchView commit(MultipartFile file, String type, boolean skipInvalid) {
        Parsed p = parse(file, type);
        batchRepository.findFirstByFileHash(p.hash).ifPresent(b -> {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,
                    "This file was already imported on " + b.getImportedAt().toLocalDate()
                            + ". Delete that import first if you want to import it again.");
        });
        if (p.errorCount > 0 && !skipInvalid) {
            throw ApiException.badRequest("The file has " + p.errorCount
                    + " problem(s). Fix them, or choose to skip the rows with problems.");
        }
        if (p.lines.isEmpty()) {
            throw ApiException.badRequest("There are no valid rows to import.");
        }

        Map<String, Client> clients = new HashMap<>();
        clientRepository.findAll().forEach(c -> clients.putIfAbsent(key(c.getName()), c));
        Map<String, Supplier> suppliers = new HashMap<>();
        supplierRepository.findAll().forEach(s -> suppliers.putIfAbsent(key(s.getName()), s));

        LegacyBatch batch = batchRepository.save(LegacyBatch.builder()
                .fileName(p.fileName).fileHash(p.hash).build());
        int sales = 0, purchases = 0, expenses = 0, clientBalances = 0, providerBalances = 0;
        BigDecimal totalSales = BigDecimal.ZERO, totalPurchases = BigDecimal.ZERO, totalExpenses = BigDecimal.ZERO;
        List<LegacyRecord> records = new ArrayList<>();

        for (Line l : p.lines) {
            Client client = null;
            Supplier supplier = null;
            if (l.type() == LegacyType.SALE || l.type() == LegacyType.CLIENT_BALANCE) {
                client = clients.computeIfAbsent(key(l.party()), k -> clientRepository.save(Client.builder()
                        .name(l.party()).type(ClientType.CLINIC).notes("Created by the old-records import").build()));
            } else if (l.type() == LegacyType.PURCHASE || l.type() == LegacyType.PROVIDER_BALANCE) {
                supplier = suppliers.computeIfAbsent(key(l.party()), k -> supplierRepository.save(Supplier.builder()
                        .name(l.party()).notes("Created by the old-records import").build()));
            }
            switch (l.type()) {
                case SALE -> { sales++; totalSales = totalSales.add(l.amount()); }
                case PURCHASE -> { purchases++; totalPurchases = totalPurchases.add(l.amount()); }
                case EXPENSE -> { expenses++; totalExpenses = totalExpenses.add(l.amount()); }
                case CLIENT_BALANCE -> clientBalances++;
                case PROVIDER_BALANCE -> providerBalances++;
            }
            records.add(LegacyRecord.builder()
                    .batch(batch).type(l.type()).recordDate(l.date())
                    .partyName(l.party()).client(client).supplier(supplier)
                    .category(l.category()).reference(l.reference()).description(l.description())
                    .amount(l.amount()).paidAmount(l.paid()).costAmount(l.cost())
                    .build());
        }
        recordRepository.saveAll(records);

        batch.setSales(sales);
        batch.setPurchases(purchases);
        batch.setExpenses(expenses);
        batch.setClientBalances(clientBalances);
        batch.setProviderBalances(providerBalances);
        batch.setTotalSales(totalSales);
        batch.setTotalPurchases(totalPurchases);
        batch.setTotalExpenses(totalExpenses);
        return BatchView.of(batchRepository.save(batch));
    }

    @Transactional(readOnly = true)
    public List<BatchView> batches() {
        return batchRepository.findAllByOrderByImportedAtDescIdDesc().stream().map(BatchView::of).toList();
    }

    @Transactional
    public void deleteBatch(Long id) {
        LegacyBatch b = batchRepository.findById(id).orElseThrow(() -> ApiException.notFound("Import"));
        recordRepository.deleteByBatchId(b.getId());
        batchRepository.delete(b);
    }

    /** Browse imported records, newest first. At most {@code limit} rows are returned; {@code total} is the full match count. */
    @Transactional(readOnly = true)
    public Map<String, Object> records(LegacyType type, Integer year, String q, int limit) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<LegacyRecord> all = recordRepository.findAllByOrderByRecordDateDescIdDesc().stream()
                .filter(r -> type == null || r.getType() == type)
                .filter(r -> year == null || r.getRecordDate().getYear() == year)
                .filter(r -> needle.isEmpty()
                        || (r.getPartyName() != null && r.getPartyName().toLowerCase(Locale.ROOT).contains(needle))
                        || (r.getDescription() != null && r.getDescription().toLowerCase(Locale.ROOT).contains(needle))
                        || (r.getReference() != null && r.getReference().toLowerCase(Locale.ROOT).contains(needle)))
                .toList();
        List<RecordView> shown = all.stream().limit(limit)
                .map(r -> new RecordView(r.getId(), r.getType(), r.getRecordDate(), r.getPartyName(), r.getCategory(),
                        r.getReference(), r.getDescription(), r.getAmount(), r.getPaidAmount(), r.getCostAmount(),
                        r.getBatch().getId()))
                .toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("records", shown);
        return m;
    }

    // ------------------------------------------------------------------ template

    public byte[] template() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet help = wb.createSheet("Read me");
            String[] lines = {
                    "Flow Dent: old records",
                    "",
                    "1. Fill in the sheets you need and leave the others empty. Delete nothing, rename nothing.",
                    "2. Amounts are in Egyptian pounds (EGP). Dates are day/month/year, for example 25/03/2024.",
                    "3. Sales: one row per old invoice to a client. 'paid' empty = paid in full. 'cost of goods' is optional.",
                    "    Example:  25/03/2024 | Smile Dental Clinic | INV-1042 | 3500 | 2000 | 2100 | notes",
                    "4. Purchases: one row per old purchase from a provider. 'paid' empty = paid in full.",
                    "    Example:  10/01/2023 | Nile Dental Supplies | P-77 | 12000 | 12000 | notes",
                    "5. Expenses: category is one of rent, salaries, utilities, transport, marketing, maintenance, bank fees, taxes, other.",
                    "    Example:  01/02/2023 | rent | Shop rent February | 6000",
                    "6. 'Clients owe us' and 'We owe providers': what is STILL unpaid today, one row per client or provider.",
                    "    These amounts are added to the balances in the system.",
                    "7. Old invoices on the Sales and Purchases sheets are history only and do NOT change balances,",
                    "    so do not list the same unpaid amount on both. Old records never change your stock.",
                    "8. Clients and providers that do not exist yet are created automatically (you see the list before saving).",
                    "9. Your own sheet layout also works if the column names are similar (English or Arabic). You also get a preview before anything is saved."
            };
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            help.setColumnWidth(0, 130 * 256);

            addSheet(wb, head, "Sales", "date", "client", "invoice no", "amount (EGP)", "paid (EGP)", "cost of goods (EGP)", "notes");
            addSheet(wb, head, "Purchases", "date", "provider", "invoice no", "amount (EGP)", "paid (EGP)", "notes");
            addSheet(wb, head, "Expenses", "date", "category", "description", "amount (EGP)");
            addSheet(wb, head, "Clients owe us", "client", "amount owed (EGP)", "notes");
            addSheet(wb, head, "We owe providers", "provider", "amount owed (EGP)", "notes");
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build the template", e);
        }
    }

    private static void addSheet(Workbook wb, CellStyle head, String name, String... headers) {
        Sheet s = wb.createSheet(name);
        Row r = s.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = r.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(head);
            s.setColumnWidth(i, 24 * 256);
        }
        s.createFreezePane(0, 1);
    }

    // ------------------------------------------------------------------ reading files

    private Parsed parse(MultipartFile file, String typeParam) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("No file uploaded");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the file: " + e.getMessage());
        }
        Parsed p = new Parsed();
        p.fileName = file.getOriginalFilename() == null ? "records" : file.getOriginalFilename();
        p.hash = sha256(bytes);
        LegacyType forced = typeParam == null || typeParam.isBlank() ? null : parseType(typeParam);

        String lower = p.fileName.toLowerCase(Locale.ROOT);
        List<Table> tables;
        if (lower.endsWith(".xlsx")) {
            tables = readWorkbook(bytes, forced, p);
        } else if (lower.endsWith(".csv")) {
            if (forced == null) {
                throw ApiException.badRequest("For a CSV file, choose what it contains (sales, purchases, expenses, or balances).");
            }
            tables = List.of(readCsv(bytes, forced));
        } else {
            throw ApiException.badRequest("Please upload an Excel (.xlsx) or CSV (.csv) file.");
        }
        if (tables.isEmpty()) {
            throw ApiException.badRequest("No sheet named Sales, Purchases, Expenses, Clients owe us or We owe providers "
                    + "was found. Download the template to see the layout, or choose what the file contains.");
        }
        for (Table t : tables) readTable(t, p);
        return p;
    }

    private static LegacyType parseType(String raw) {
        String n = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (n) {
            case "sale", "sales" -> LegacyType.SALE;
            case "purchase", "purchases" -> LegacyType.PURCHASE;
            case "expense", "expenses" -> LegacyType.EXPENSE;
            case "client-balance", "client-balances", "clients-owe-us" -> LegacyType.CLIENT_BALANCE;
            case "provider-balance", "provider-balances", "we-owe-providers" -> LegacyType.PROVIDER_BALANCE;
            default -> throw ApiException.badRequest("Unknown record type '" + raw + "'");
        };
    }

    private List<Table> readWorkbook(byte[] bytes, LegacyType forced, Parsed p) {
        List<Table> tables = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            List<Sheet> usable = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet s = wb.getSheetAt(i);
                if (isHelpSheet(s.getSheetName())) continue;
                usable.add(s);
            }
            for (Sheet s : usable) {
                LegacyType type = typeFromSheetName(s.getSheetName());
                if (type == null && forced != null && usable.size() == 1) type = forced;
                if (type == null) {
                    if (s.getPhysicalNumberOfRows() > 0) {
                        p.warnings.add("The sheet \"" + s.getSheetName() + "\" was ignored (its name is not Sales, Purchases, Expenses, "
                                + "Clients owe us or We owe providers).");
                    }
                    continue;
                }
                List<RawRow> rows = new ArrayList<>();
                for (int r = 0; r <= s.getLastRowNum(); r++) {
                    Row row = s.getRow(r);
                    if (row == null) continue;
                    List<String> cells = new ArrayList<>();
                    for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) cells.add(cellText(row.getCell(c), ev));
                    rows.add(new RawRow(r + 1, cells));
                }
                tables.add(buildTable(s.getSheetName(), type, rows));
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException ae) throw ae;
            throw ApiException.badRequest("Could not read the Excel file: " + e.getMessage());
        }
        return tables;
    }

    private Table readCsv(byte[] bytes, LegacyType type) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.indexOf('�') >= 0) text = new String(bytes, Charset.forName("windows-1256"));
        if (text.startsWith("﻿")) text = text.substring(1);
        String firstLine = text.lines().findFirst().orElse("");
        char delim = ',';
        int best = count(firstLine, ',');
        if (count(firstLine, ';') > best) { delim = ';'; best = count(firstLine, ';'); }
        if (count(firstLine, '\t') > best) delim = '\t';
        List<List<String>> parsed = parseCsv(text, delim);
        List<RawRow> rows = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) rows.add(new RawRow(i + 1, parsed.get(i)));
        return buildTable("CSV", type, rows);
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    /** Minimal RFC-4180 reader: quoted fields, doubled quotes, embedded delimiters and newlines. */
    static List<List<String>> parseCsv(String text, char delim) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else quoted = false;
                } else cur.append(ch);
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == delim) {
                row.add(cur.toString().trim());
                cur.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cur.toString().trim());
                cur.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else cur.append(ch);
        }
        if (cur.length() > 0 || !row.isEmpty()) {
            row.add(cur.toString().trim());
            rows.add(row);
        }
        return rows;
    }

    private static boolean isHelpSheet(String name) {
        String n = norm1(name);
        return n.equals(norm1("read me")) || n.equals("readme") || n.equals("instructions") || n.equals("help")
                || n.equals(norm1("تعليمات"));
    }

    private static LegacyType typeFromSheetName(String name) {
        String n = norm1(name);
        if (n.contains("clientsowe") || n.contains("clientbalance") || n.contains("customersowe") || n.contains(norm1("أرصدة العملاء"))
                || n.contains(norm1("مديونية العملاء"))) return LegacyType.CLIENT_BALANCE;
        if (n.contains("oweproviders") || n.contains("weowe") || n.contains("providerbalance") || n.contains("supplierbalance")
                || n.contains(norm1("أرصدة الموردين")) || n.contains(norm1("مستحقات الموردين"))) return LegacyType.PROVIDER_BALANCE;
        if (n.startsWith("sale") || n.startsWith("invoice") || n.contains(norm1("مبيعات"))) return LegacyType.SALE;
        if (n.startsWith("purchase") || n.startsWith("buying") || n.contains(norm1("مشتريات"))) return LegacyType.PURCHASE;
        if (n.startsWith("expense") || n.contains(norm1("مصروفات")) || n.contains(norm1("مصاريف"))) return LegacyType.EXPENSE;
        return null;
    }

    private String cellText(Cell c, FormulaEvaluator ev) {
        if (c == null) return "";
        try {
            CellType t = c.getCellType();
            if (t == CellType.FORMULA) {
                CellValue v = ev.evaluate(c);
                if (v == null) return "";
                return switch (v.getCellType()) {
                    case NUMERIC -> numericText(c, v.getNumberValue());
                    case STRING -> v.getStringValue().trim();
                    case BOOLEAN -> String.valueOf(v.getBooleanValue());
                    default -> "";
                };
            }
            return switch (t) {
                case NUMERIC -> numericText(c, c.getNumericCellValue());
                case STRING -> c.getStringCellValue().trim();
                case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
                default -> "";
            };
        } catch (RuntimeException e) {
            return FORMATTER.formatCellValue(c).trim();
        }
    }

    private static String numericText(Cell c, double d) {
        if (DateUtil.isCellDateFormatted(c)) {
            return DateUtil.getJavaDate(d).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
        }
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    // ------------------------------------------------------------------ tables and rows

    private static List<F> fieldsFor(LegacyType t) {
        return switch (t) {
            case SALE -> List.of(F.DATE, F.PARTY, F.AMOUNT, F.PAID, F.COST, F.REFERENCE, F.NOTES);
            case PURCHASE -> List.of(F.DATE, F.PARTY, F.AMOUNT, F.PAID, F.REFERENCE, F.NOTES);
            case EXPENSE -> List.of(F.DATE, F.AMOUNT, F.CATEGORY, F.NOTES);
            case CLIENT_BALANCE, PROVIDER_BALANCE -> List.of(F.DATE, F.PARTY, F.AMOUNT, F.NOTES);
        };
    }

    private static List<F> requiredFor(LegacyType t) {
        return switch (t) {
            case SALE, PURCHASE -> List.of(F.DATE, F.PARTY, F.AMOUNT);
            case EXPENSE -> List.of(F.DATE, F.AMOUNT);
            case CLIENT_BALANCE, PROVIDER_BALANCE -> List.of(F.PARTY, F.AMOUNT);
        };
    }

    /** The header row is the first of the first ten non-empty rows that has at least two known column names. */
    private Table buildTable(String sheet, LegacyType type, List<RawRow> all) {
        List<RawRow> nonEmpty = all.stream().filter(r -> r.cells().stream().anyMatch(s -> !s.isEmpty())).toList();
        int headerIdx = 0;
        for (int i = 0; i < Math.min(10, nonEmpty.size()); i++) {
            if (resolve(nonEmpty.get(i).cells(), type).size() >= 2) { headerIdx = i; break; }
        }
        if (nonEmpty.isEmpty()) return new Table(sheet, type, List.of(), List.of());
        List<String> headers = nonEmpty.get(headerIdx).cells();
        return new Table(sheet, type, headers, nonEmpty.subList(headerIdx + 1, nonEmpty.size()));
    }

    private static Map<F, Integer> resolve(List<String> headers, LegacyType type) {
        List<String> normHeaders = headers.stream().map(LegacyImportService::headerKey).toList();
        Map<F, Integer> out = new EnumMap<>(F.class);
        Set<Integer> used = new HashSet<>();
        List<F> order = List.of(F.DATE, F.PARTY, F.AMOUNT, F.PAID, F.COST, F.REFERENCE, F.CATEGORY, F.NOTES);
        List<F> wanted = fieldsFor(type);
        for (F f : order) {
            if (!wanted.contains(f)) continue;
            for (String alias : ALIASES.get(f)) {
                int idx = -1;
                for (int i = 0; i < normHeaders.size(); i++) {
                    if (!used.contains(i) && normHeaders.get(i).equals(alias)) { idx = i; break; }
                }
                if (idx >= 0) { out.put(f, idx); used.add(idx); break; }
            }
        }
        return out;
    }

    private static String headerKey(String header) {
        String n = norm1(header);
        n = n.replaceAll("egp$", "").replaceAll("optional$", "");
        return n;
    }

    private static String friendly(F f) {
        return switch (f) {
            case DATE -> "date";
            case PARTY -> "client / provider name";
            case AMOUNT -> "amount";
            default -> f.name().toLowerCase(Locale.ROOT);
        };
    }

    private static String label(LegacyType t) {
        return switch (t) {
            case SALE -> "Sales";
            case PURCHASE -> "Purchases";
            case EXPENSE -> "Expenses";
            case CLIENT_BALANCE -> "Clients owe us";
            case PROVIDER_BALANCE -> "We owe providers";
        };
    }

    private void addError(Parsed p, String message) {
        p.errorCount++;
        if (p.errors.size() < MAX_MESSAGES) p.errors.add(message);
    }

    private void readTable(Table t, Parsed p) {
        String label = t.type() == null ? t.sheet() : label(t.type());
        if (t.headers().isEmpty()) return;   // an empty sheet
        Map<F, Integer> col = resolve(t.headers(), t.type());
        boolean missing = false;
        for (F f : requiredFor(t.type())) {
            if (!col.containsKey(f)) {
                addError(p, label + ": no \"" + friendly(f) + "\" column was found. Columns seen: "
                        + String.join(", ", t.headers().stream().filter(h -> !h.isEmpty()).toList()));
                missing = true;
            }
        }
        if (t.type() == LegacyType.EXPENSE && !col.containsKey(F.NOTES) && !col.containsKey(F.CATEGORY)) {
            addError(p, label + ": no \"description\" or \"category\" column was found.");
            missing = true;
        }
        if (missing) return;

        boolean balance = t.type() == LegacyType.CLIENT_BALANCE || t.type() == LegacyType.PROVIDER_BALANCE;
        boolean needsParty = t.type() != LegacyType.EXPENSE;
        for (RawRow r : t.rows()) {
            String dateRaw = cell(r, col, F.DATE);
            String party = clean(cell(r, col, F.PARTY));
            String reference = cell(r, col, F.REFERENCE);
            String amountRaw = cell(r, col, F.AMOUNT);
            String paidRaw = cell(r, col, F.PAID);
            String costRaw = cell(r, col, F.COST);
            String notes = cell(r, col, F.NOTES);
            String catRaw = cell(r, col, F.CATEGORY);

            String subject = party.isEmpty() ? notes : party;
            if (dateRaw.isEmpty() && TOTAL_WORDS.contains(norm1(subject))) {
                p.warnings.add(label + ", row " + r.line() + " looks like a total line and was skipped.");
                continue;
            }

            List<String> problems = new ArrayList<>();
            LocalDate date = null;
            if (dateRaw.isEmpty()) {
                if (balance) date = LocalDate.now();
                else problems.add("the date is missing");
            } else {
                try {
                    date = parseDate(dateRaw);
                    if (date.isBefore(EARLIEST) || date.isAfter(LocalDate.now().plusDays(1))) {
                        problems.add("the date " + date + " looks wrong");
                    }
                } catch (DateTimeException | NumberFormatException e) {
                    problems.add("\"" + dateRaw + "\" is not a valid date (use day/month/year, for example 25/03/2024)");
                }
            }
            if (needsParty && party.isEmpty()) {
                problems.add(t.type() == LegacyType.SALE || t.type() == LegacyType.CLIENT_BALANCE
                        ? "the client name is missing" : "the provider name is missing");
            }

            BigDecimal amount = null;
            if (amountRaw.isEmpty()) {
                problems.add("the amount is missing");
            } else {
                try {
                    amount = parseMoney(amountRaw);
                    if (!balance && amount.signum() < 0) problems.add("the amount cannot be negative");
                    if (amount.abs().compareTo(MAX_AMOUNT) >= 0) problems.add("the amount is too large");
                } catch (NumberFormatException e) {
                    problems.add("the amount \"" + amountRaw + "\" is not a number");
                }
            }
            BigDecimal paid = null;
            if (!paidRaw.isEmpty() && t.type() != LegacyType.EXPENSE && !balance) {
                try {
                    paid = parseMoney(paidRaw);
                    if (paid.signum() < 0) problems.add("the paid amount cannot be negative");
                } catch (NumberFormatException e) {
                    problems.add("the paid amount \"" + paidRaw + "\" is not a number");
                }
            }
            BigDecimal cost = null;
            if (!costRaw.isEmpty() && t.type() == LegacyType.SALE) {
                try {
                    cost = parseMoney(costRaw);
                    if (cost.signum() < 0) problems.add("the cost cannot be negative");
                } catch (NumberFormatException e) {
                    problems.add("the cost \"" + costRaw + "\" is not a number");
                }
            }

            ExpenseCategory category = null;
            String description = notes;
            if (t.type() == LegacyType.EXPENSE) {
                category = category(catRaw);
                if (description.isEmpty()) description = catRaw;
                if (description.isEmpty()) problems.add("the description is missing");
            }

            if (balance && amount != null && amount.signum() == 0 && problems.isEmpty()) continue;   // nothing owed

            if (!problems.isEmpty()) {
                p.problemRows++;
                addError(p, label + ", row " + r.line() + ": " + String.join("; ", problems));
                continue;
            }
            if (paid != null && paid.compareTo(amount) > 0) {
                p.warnings.add(label + ", row " + r.line() + ": the paid amount is more than the invoice amount.");
            }
            p.lines.add(new Line(t.type(), r.line(), t.sheet(), date, party.isEmpty() ? null : party, category,
                    reference.isEmpty() ? null : reference, description.isEmpty() ? null : description,
                    amount, paid, cost));
        }
    }

    private static String cell(RawRow r, Map<F, Integer> col, F f) {
        Integer i = col.get(f);
        if (i == null || i >= r.cells().size()) return "";
        return r.cells().get(i) == null ? "" : r.cells().get(i).trim();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String key(String name) {
        return clean(name).toLowerCase(Locale.ROOT);
    }

    private static List<String> capped(Collection<String> names) {
        return names.stream().limit(200).toList();
    }

    // ------------------------------------------------------------------ value parsing

    private static final Pattern ISO = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[ T].*)?$");
    private static final Pattern YMD = Pattern.compile("^(\\d{4})[/.](\\d{1,2})[/.](\\d{1,2})(?:\\s.*)?$");
    private static final Pattern DMY = Pattern.compile("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2}|\\d{4})(?:\\s.*)?$");

    /** Day-first by default (Egypt); month-first only when the day-first reading is impossible. */
    static LocalDate parseDate(String raw) {
        String s = latinDigits(raw).trim();
        Matcher m = ISO.matcher(s);
        if (m.matches()) return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        m = YMD.matcher(s);
        if (m.matches()) return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        m = DMY.matcher(s);
        if (m.matches()) {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            int y = Integer.parseInt(m.group(3));
            if (m.group(3).length() == 2) y += 2000;
            int day = a, month = b;
            if (month > 12 && day <= 12) { day = b; month = a; }
            return LocalDate.of(y, month, day);
        }
        if (s.matches("^\\d{5}$")) return LocalDate.of(1899, 12, 30).plusDays(Long.parseLong(s));   // Excel serial in a CSV
        throw new DateTimeException("Unrecognised date: " + raw);
    }

    /** Accepts 1,234.50  1 234,5?  EGP 1,200  1200 ج.م  (1,200) and Arabic-Indic digits. */
    static BigDecimal parseMoney(String raw) {
        String s = latinDigits(raw).trim();
        boolean negative = s.startsWith("(") && s.endsWith(")");
        s = s.replace('٫', '.').replace("٬", "")
                .replaceAll("(?i)egp|l\\.e\\.?|le\\b|ج\\.?\\s?م\\.?|جنيه|£", "")
                .replaceAll("[\\s\\u00A0,()]", "");
        if (s.startsWith("+")) s = s.substring(1);
        if (s.isEmpty() || s.equals("-")) throw new NumberFormatException("empty");
        BigDecimal v = new BigDecimal(s);
        if (negative) v = v.negate();
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    static String latinDigits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '٠' && c <= '٩') b.append((char) ('0' + (c - '٠')));
            else if (c >= '۰' && c <= '۹') b.append((char) ('0' + (c - '۰')));
            else b.append(c);
        }
        return b.toString();
    }

    static ExpenseCategory category(String raw) {
        String n = norm1(raw);
        if (n.isEmpty()) return ExpenseCategory.OTHER;
        if (hasAny(n, "rent", "ايجار")) return ExpenseCategory.RENT;
        if (hasAny(n, "salar", "wage", "payroll", "staff", "رواتب", "راتب", "مرتب", "اجور", "اجر")) return ExpenseCategory.SALARIES;
        if (hasAny(n, "utilit", "electric", "water", "gas", "internet", "phone", "مرافق", "كهرب", "مياه", "انترنت", "تليفون", "غاز")) return ExpenseCategory.UTILITIES;
        if (hasAny(n, "transport", "shipping", "delivery", "fuel", "petrol", "نقل", "شحن", "مواصلات", "بنزين", "توصيل")) return ExpenseCategory.TRANSPORT;
        if (hasAny(n, "market", "advert", "ads", "promo", "تسويق", "اعلان", "دعايه")) return ExpenseCategory.MARKETING;
        if (hasAny(n, "maint", "repair", "صيان", "اصلاح")) return ExpenseCategory.MAINTENANCE;
        if (hasAny(n, "bank", "fee", "commission", "بنك", "عمول")) return ExpenseCategory.BANK_FEES;
        if (hasAny(n, "tax", "vat", "ضريب", "ضرائب")) return ExpenseCategory.TAXES;
        return ExpenseCategory.OTHER;
    }

    private static boolean hasAny(String n, String... stems) {
        for (String s : stems) if (n.contains(norm1(s))) return true;
        return false;
    }

    // ------------------------------------------------------------------ text helpers

    /** Lower-case, Arabic letters folded (alef forms, ya, ta marbuta, diacritics), everything but letters and digits removed. */
    static String norm1(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        n = n.replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "")
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replace('ة', 'ه');
        n = latinDigits(n);
        return n.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static List<String> norm(String... names) {
        return Arrays.stream(names).map(LegacyImportService::norm1).distinct().toList();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
