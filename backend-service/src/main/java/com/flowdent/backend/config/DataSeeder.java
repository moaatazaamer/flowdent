package com.flowdent.backend.config;

import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.CategoryRepository;
import com.flowdent.backend.repository.ClientRepository;
import com.flowdent.backend.repository.ProductRepository;
import com.flowdent.backend.repository.SupplierRepository;
import com.flowdent.backend.repository.UserRepository;
import com.flowdent.backend.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String U = "https://images.unsplash.com/";
    private static final String Q = "?auto=format&fit=crop&w=800&q=70";

    private static final String IMG_GLOVES = U + "photo-1584515933487-779824d29309" + Q;
    private static final String IMG_GLOVES_2 = U + "photo-1583947215259-38e31be8751f" + Q;
    private static final String IMG_CLINIC = U + "photo-1629909613654-28e377c37b09" + Q;
    private static final String IMG_TOOLS = U + "photo-1606811971618-4486d14f3f99" + Q;
    private static final String IMG_CHAIR = U + "photo-1588776814546-1ffcf47267a5" + Q;
    private static final String IMG_DENTAL = U + "photo-1598256989800-fe5f95da9787" + Q;
    private static final String IMG_TEETH = U + "photo-1609840114035-3c981b782dfe" + Q;
    private static final String IMG_MEDICAL = U + "photo-1576091160550-2173dba999ef" + Q;
    private static final String IMG_LAB = U + "photo-1579684385127-1ef15d508118" + Q;
    private static final String IMG_SUPPLIES = U + "photo-1559757148-5c350d0d3c56" + Q;

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final SupplierRepository supplierRepository;
    private final ClientRepository clientRepository;
    private final StockService stockService;

    /**
     * Sample products tagged with the Ora brand (Flow Dent is Ora's exclusive agent).
     * Example data only: replace with the real Ora catalogue from the admin panel or Excel import.
     */
    private static final java.util.Set<String> ORA_SAMPLE = java.util.Set.of(
            "Universal Nano-Hybrid Composite A2 (4g)",
            "Universal Bonding Agent 5ml",
            "Glass Ionomer Restorative Kit",
            "Phosphoric Acid Etchant Gel 37% (3x5ml)",
            "Dust-Free Alginate 453g",
            "A-Silicone Putty Impression Material",
            "NiTi Rotary Files Assorted (6 pcs)",
            "Absorbent Paper Points #30 (200 pcs)");

    /** Running number per category prefix, for generated SKUs. */
    private final Map<String, Integer> skuCounters = new HashMap<>();

    @Override
    @Transactional
    public void run(String... args) {
        seedUser("admin", "admin", "Store Administrator", Role.ADMIN);
        seedUser("orderadmin", "orderadmin", "Order Administrator", Role.ORDER_ADMIN);

        if (categoryRepository.count() == 0) seedCatalog();
        if (supplierRepository.count() == 0 && clientRepository.count() == 0) seedContacts();
    }

    /** Example providers and clients so the Operations screens are not empty on first run. */
    private void seedContacts() {
        log.info("Seeding example providers and clients...");
        supplierRepository.save(Supplier.builder().name("Nile Dental Supplies").contactName("Ahmed Fathy")
                .phone("+20 2 2345 6789").email("sales@niledental.example").address("Abbas El Akkad St, Nasr City, Cairo")
                .notes("Gloves and disposables. 30 days credit.").build());
        supplierRepository.save(Supplier.builder().name("Delta Medical Import").contactName("Mona Adel")
                .phone("+20 3 4567 8901").email("orders@deltamedical.example").address("Smouha, Alexandria")
                .notes("Endodontics and restoratives, imported.").build());
        supplierRepository.save(Supplier.builder().name("Cairo Instruments Co.").contactName("Karim Hassan")
                .phone("+20 10 1234 5678").address("Downtown, Cairo").notes("Hand instruments.").build());

        clientRepository.save(Client.builder().name("Smile Dental Clinic").type(ClientType.CLINIC)
                .contactName("Dr. Sara Mahmoud").phone("+20 100 111 2233").city("Cairo")
                .address("12 Mostafa El Nahas St, Nasr City").build());
        clientRepository.save(Client.builder().name("Dr. Omar Khaled").type(ClientType.DOCTOR)
                .phone("+20 122 333 4455").city("Giza").address("Mohandessin").build());
        clientRepository.save(Client.builder().name("Alex Ortho Center").type(ClientType.CLINIC)
                .contactName("Dr. Hany Samir").phone("+20 111 555 6677").city("Alexandria")
                .address("Fouad St, Alexandria").build());
        clientRepository.save(Client.builder().name("Precision Dental Lab").type(ClientType.LAB)
                .contactName("Eng. Youssef Ali").phone("+20 155 777 8899").city("Cairo").build());
    }

    private void seedCatalog() {
        log.info("Seeding Flow Dent catalog...");

        Category gloves = cat("Gloves", "القفازات",
                "Examination and surgical gloves in nitrile, latex and vinyl.",
                "قفازات فحص وجراحة من النتريل واللاتكس والفينيل.", IMG_GLOVES);
        Category suction = cat("Suction & Aspiration", "الشفط والسحب",
                "Saliva ejectors, surgical aspirator tips and suction accessories.",
                "شفاطات اللعاب وأطراف الشفط الجراحية وملحقات الشفط.", IMG_CHAIR);
        Category endo = cat("Endodontics", "علاج الجذور",
                "Files, paper points, gutta-percha and irrigation for root canal therapy.",
                "مبارد وأقماع ورقية وجوتابيركا ومحاليل غسيل لعلاج قنوات الجذور.", IMG_TEETH);
        Category steril = cat("Sterilization & Infection Control", "التعقيم ومكافحة العدوى",
                "Sterilization pouches, indicators, disinfectants and protective wear.",
                "أكياس التعقيم والمؤشرات والمطهرات والملابس الواقية.", IMG_LAB);
        Category restorative = cat("Restorative Materials", "مواد الترميم",
                "Composites, bonding agents, glass ionomers and etchants.",
                "الكمبوزيت ومواد الربط والزجاج الأيونومري ومواد التخريش.", IMG_DENTAL);
        Category instruments = cat("Hand Instruments", "الأدوات اليدوية",
                "Mirrors, probes, explorers, scalers and forceps in stainless steel.",
                "مرايا ومسابر ومستكشفات ومكاشط وملاقط من الفولاذ المقاوم للصدأ.", IMG_TOOLS);
        Category impression = cat("Impression Materials", "مواد الطبعات",
                "Alginate, silicone impression materials and impression trays.",
                "الألجينات ومواد الطبعات السيليكونية وصواني الطبعات.", IMG_CLINIC);
        Category disposables = cat("General Disposables", "المستهلكات العامة",
                "Masks, bibs, cotton rolls, cups and everyday clinic consumables.",
                "كمامات ومرايل وقطن ولفائف وأكواب ومستهلكات العيادة اليومية.", IMG_SUPPLIES);

        // Gloves
        prod(gloves, "Nitrile Exam Gloves - Medium (100 pcs)", "قفازات فحص نتريل - مقاس متوسط (100 قطعة)",
                "Powder-free, latex-free blue nitrile gloves with textured fingertips.",
                "قفازات نتريل زرقاء خالية من البودرة واللاتكس مع أطراف أصابع محببة.", "9.90", 250, IMG_GLOVES);
        prod(gloves, "Latex Exam Gloves - Small (100 pcs)", "قفازات فحص لاتكس - مقاس صغير (100 قطعة)",
                "Lightly powdered latex gloves offering excellent tactile sensitivity.",
                "قفازات لاتكس ببودرة خفيفة توفر حساسية لمس ممتازة.", "7.50", 180, IMG_GLOVES_2);
        prod(gloves, "Sterile Surgical Gloves Size 7 (50 pairs)", "قفازات جراحية معقمة مقاس 7 (50 زوج)",
                "Individually packed sterile latex surgical gloves for oral surgery.",
                "قفازات جراحية لاتكس معقمة مغلفة بشكل فردي لجراحة الفم.", "34.00", 60, IMG_GLOVES);
        prod(gloves, "Vinyl Exam Gloves - Large (100 pcs)", "قفازات فحص فينيل - مقاس كبير (100 قطعة)",
                "Economical clear vinyl gloves for short, low-risk procedures.",
                "قفازات فينيل شفافة اقتصادية للإجراءات القصيرة منخفضة الخطورة.", "5.90", 300, IMG_GLOVES_2);

        // Suction
        prod(suction, "Saliva Ejectors (100 pcs)", "شفاطات اللعاب (100 قطعة)",
                "Flexible PVC saliva ejectors with fixed tip, assorted colours.",
                "شفاطات لعاب مرنة من PVC بطرف ثابت وألوان متنوعة.", "4.80", 400, IMG_CHAIR);
        prod(suction, "Surgical Aspirator Tips (25 pcs)", "أطراف شفط جراحية (25 قطعة)",
                "Disposable sterile surgical aspirator tips, 2.5 mm.",
                "أطراف شفط جراحية معقمة للاستعمال مرة واحدة، 2.5 مم.", "11.20", 150, IMG_CHAIR);
        prod(suction, "High-Volume Evacuator Tips (50 pcs)", "أطراف شفط عالي القدرة (50 قطعة)",
                "Vented HVE tips that reduce aerosol during ultrasonic scaling.",
                "أطراف شفط عالي القدرة مهواة تقلل الرذاذ أثناء التنظيف بالموجات فوق الصوتية.", "13.50", 120, IMG_CHAIR);
        prod(suction, "Suction Line Cleaner 1L", "منظف خطوط الشفط 1 لتر",
                "Non-foaming enzymatic concentrate for daily suction-line cleaning.",
                "مركز إنزيمي غير رغوي لتنظيف خطوط الشفط يوميًا.", "18.90", 70, IMG_LAB);

        // Endodontics
        prod(endo, "K-Files 25mm Assorted #15-40 (6 pcs)", "مبارد K بطول 25 مم متنوعة #15-40 (6 قطع)",
                "Stainless steel hand K-files for canal negotiation and shaping.",
                "مبارد K يدوية من الفولاذ المقاوم للصدأ لاستكشاف القنوات وتشكيلها.", "6.40", 200, IMG_TEETH);
        prod(endo, "NiTi Rotary Files Assorted (6 pcs)", "مبارد دوارة نيكل تيتانيوم متنوعة (6 قطع)",
                "Heat-treated NiTi rotary files with high flexibility and fatigue resistance.",
                "مبارد دوارة من النيكل تيتانيوم معالجة حراريًا بمرونة عالية ومقاومة للتعب.", "29.00", 90, IMG_TEETH);
        prod(endo, "Absorbent Paper Points #30 (200 pcs)", "أقماع ورقية ماصة #30 (200 قطعة)",
                "Colour-coded, highly absorbent paper points for canal drying.",
                "أقماع ورقية عالية الامتصاص بترميز لوني لتجفيف القنوات.", "5.20", 160, IMG_TEETH);
        prod(endo, "Gutta-Percha Points #25 (120 pcs)", "أقماع جوتابيركا #25 (120 قطعة)",
                "ISO-standard gutta-percha points for lateral condensation.",
                "أقماع جوتابيركا وفق معيار ISO للتكثيف الجانبي.", "6.90", 140, IMG_TEETH);

        // Sterilization
        prod(steril, "Self-Seal Sterilization Pouches 90x230mm (200 pcs)", "أكياس تعقيم ذاتية اللصق 90×230 مم (200 قطعة)",
                "Medical-grade paper/film pouches with steam and EO indicators.",
                "أكياس من الورق والبلاستيك الطبي مع مؤشرات للبخار وأكسيد الإيثيلين.", "12.40", 220, IMG_LAB);
        prod(steril, "Class 4 Chemical Indicator Strips (250 pcs)", "شرائط مؤشر كيميائي فئة 4 (250 قطعة)",
                "Multi-parameter indicators to verify autoclave cycle conditions.",
                "مؤشرات متعددة المعايير للتحقق من ظروف دورة الأوتوكلاف.", "15.80", 110, IMG_LAB);
        prod(steril, "Surface Disinfectant Wipes (100 pcs)", "مناديل تطهير الأسطح (100 قطعة)",
                "Alcohol-based wipes, fast-acting against bacteria and viruses.",
                "مناديل كحولية سريعة المفعول ضد البكتيريا والفيروسات.", "8.70", 260, IMG_MEDICAL);
        prod(steril, "Instrument Disinfectant Concentrate 1L", "مركز تطهير الأدوات 1 لتر",
                "Aldehyde-free concentrate for pre-cleaning and disinfecting instruments.",
                "مركز خالٍ من الألدهيد للتنظيف المسبق وتطهير الأدوات.", "21.50", 80, IMG_LAB);

        // Restorative
        prod(restorative, "Universal Nano-Hybrid Composite A2 (4g)", "كمبوزيت نانو هجين عالمي A2 (4 غ)",
                "Light-cure composite for anterior and posterior restorations.",
                "كمبوزيت يتصلب بالضوء للترميمات الأمامية والخلفية.", "16.90", 130, IMG_DENTAL);
        prod(restorative, "Universal Bonding Agent 5ml", "مادة ربط عالمية 5 مل",
                "Single-bottle adhesive compatible with self-etch and total-etch.",
                "لاصق بزجاجة واحدة متوافق مع التخريش الذاتي والكلي.", "24.50", 95, IMG_DENTAL);
        prod(restorative, "Glass Ionomer Restorative Kit", "طقم ترميم زجاج أيونومري",
                "Fluoride-releasing glass ionomer powder and liquid set.",
                "طقم مسحوق وسائل زجاج أيونومري يطلق الفلورايد.", "27.00", 70, IMG_DENTAL);
        prod(restorative, "Phosphoric Acid Etchant Gel 37% (3x5ml)", "جل تخريش حمض الفوسفوريك 37% (3×5 مل)",
                "Blue thixotropic etching gel with disposable tips.",
                "جل تخريش أزرق متغير القوام مع أطراف للاستعمال مرة واحدة.", "9.80", 150, IMG_DENTAL);

        // Hand instruments
        prod(instruments, "Mouth Mirror Heads #4 (12 pcs)", "رؤوس مرايا الفم #4 (12 قطعة)",
                "Front-surface mirror heads for distortion-free viewing.",
                "رؤوس مرايا بسطح أمامي لرؤية خالية من التشوه.", "14.00", 100, IMG_TOOLS);
        prod(instruments, "Double-Ended Explorer Probe", "مسبار مستكشف مزدوج الطرف",
                "Stainless steel explorer #23 / periodontal probe combination.",
                "مستكشف #23 من الفولاذ المقاوم للصدأ مع مسبار لثوي.", "7.90", 120, IMG_TOOLS);
        prod(instruments, "Gracey Curette 11/12", "مكشطة جريسي 11/12",
                "Area-specific curette for mesial surfaces of posterior teeth.",
                "مكشطة مخصصة للأسطح الأنسية للأسنان الخلفية.", "19.50", 60, IMG_TOOLS);
        prod(instruments, "Extraction Forceps Upper Molars", "ملقط خلع الأضراس العلوية",
                "Anatomically shaped forceps for maxillary molar extraction.",
                "ملقط بتصميم تشريحي لخلع الأضراس الفكية العلوية.", "39.00", 40, IMG_TOOLS);

        // Impression
        prod(impression, "Dust-Free Alginate 453g", "ألجينات خالية من الغبار 453 غ",
                "Fast-set, chromatic alginate with mint flavour.",
                "ألجينات سريعة التصلب متغيرة اللون بنكهة النعناع.", "8.60", 140, IMG_CLINIC);
        prod(impression, "A-Silicone Putty Impression Material", "مادة طبعات سيليكون إضافي (معجون)",
                "Addition silicone putty base and catalyst, 2 x 300ml.",
                "قاعدة ومحفز معجون سيليكون إضافي، 2 × 300 مل.", "32.00", 55, IMG_CLINIC);
        prod(impression, "Perforated Impression Trays Set (12 pcs)", "طقم صواني طبعات مثقبة (12 قطعة)",
                "Disposable plastic trays in assorted upper/lower sizes.",
                "صواني بلاستيكية للاستعمال مرة واحدة بمقاسات علوية وسفلية متنوعة.", "10.90", 90, IMG_CLINIC);

        // Disposables
        prod(disposables, "Surgical Face Masks Type IIR (50 pcs)", "كمامات جراحية نوع IIR (50 قطعة)",
                "3-ply fluid-resistant masks with ear loops.",
                "كمامات ثلاثية الطبقات مقاومة للسوائل بأربطة أذن.", "6.50", 500, IMG_MEDICAL);
        prod(disposables, "Patient Bibs 2-Ply (500 pcs)", "مرايل مرضى طبقتين (500 قطعة)",
                "Paper/poly laminated bibs, absorbent and waterproof.",
                "مرايل ورقية مغلفة ماصة ومقاومة للماء.", "19.90", 120, IMG_SUPPLIES);
        prod(disposables, "Cotton Rolls #2 (1000 pcs)", "لفائف قطنية #2 (1000 قطعة)",
                "Highly absorbent, non-sterile cotton rolls.",
                "لفائف قطنية غير معقمة عالية الامتصاص.", "7.20", 200, IMG_SUPPLIES);

        log.info("Seeded {} categories and {} products", categoryRepository.count(), productRepository.count());
    }

    private void seedUser(String email, String password, String fullName, Role role) {
        if (userRepository.existsByEmailIgnoreCase(email)) return;
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName(fullName)
                .role(role)
                .build());
        log.info("Created {} user '{}'", role, email);
    }

    private Category cat(String name, String nameAr, String desc, String descAr, String img) {
        return categoryRepository.save(Category.builder()
                .name(name).nameAr(nameAr).description(desc).descriptionAr(descAr).imageUrl(img).build());
    }

    /**
     * Seed prices are written in USD for readability and converted to EGP here
     * (×50, rounded to 5 EGP). Cost price is set to ~60% of the selling price.
     */
    private void prod(Category c, String name, String nameAr, String desc, String descAr,
                      String usdPrice, int stock, String img) {
        BigDecimal egp = new BigDecimal(usdPrice).multiply(BigDecimal.valueOf(50))
                .divide(BigDecimal.valueOf(5), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(5))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = egp.multiply(new BigDecimal("0.60")).setScale(2, RoundingMode.HALF_UP);
        String prefix = c.getName().replaceAll("[^A-Za-z]", "").substring(0, 3).toUpperCase();
        int n = skuCounters.merge(prefix, 1, Integer::sum);
        Product p = productRepository.save(Product.builder()
                .name(name).nameAr(nameAr).description(desc).descriptionAr(descAr)
                .price(egp).stockQuantity(0).imageUrl(img).category(c)
                .sku(String.format("%s-%03d", prefix, n)).costPrice(cost).reorderLevel(10)
                .brand(ORA_SAMPLE.contains(name) ? "Ora" : null)
                .build());
        stockService.change(p, StockMovementType.INITIAL, stock, cost, "Opening stock", null);
    }
}
