package com.flowdent.backend.config;

import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.CategoryRepository;
import com.flowdent.backend.repository.ClientRepository;
import com.flowdent.backend.repository.ProductRepository;
import com.flowdent.backend.repository.SupplierRepository;
import com.flowdent.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String U = "https://images.unsplash.com/";
    private static final String Q = "?auto=format&fit=crop&w=800&q=70";

    private static final String IMG_CLINIC = U + "photo-1629909613654-28e377c37b09" + Q;
    private static final String IMG_CHAIR = U + "photo-1588776814546-1ffcf47267a5" + Q;
    private static final String IMG_DENTAL = U + "photo-1598256989800-fe5f95da9787" + Q;
    private static final String IMG_SUPPLIES = U + "photo-1559757148-5c350d0d3c56" + Q;

    /** Brand of Flow Dent's own product line. */
    public static final String OWN_BRAND = "Ora";

    /** Exchange rate used to turn the USD import costs of the price list into EGP. */
    static final BigDecimal USD_TO_EGP = new BigDecimal("52");

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final SupplierRepository supplierRepository;
    private final ClientRepository clientRepository;

    private int skuCounter = 0;

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

    /**
     * The current Flow Dent catalogue: the Ora product line. Prices on the list are USD import
     * costs; they become the EGP cost price. The selling price starts equal to the cost and is
     * flagged in Admin → Products until a real selling price is set. Opening stock is 0: enter
     * the counted quantities from Admin → Stock (stock count) or record the purchases.
     */
    private void seedCatalog() {
        log.info("Seeding Flow Dent catalogue (Ora)...");

        Category patient = cat("Patient Protection", "حماية المريض",
                "Dental bibs and bib holders.",
                "مرايل الأسنان وحوامل المرايل.", IMG_CHAIR);
        Category barrier = cat("Barrier Protection", "الحواجز الواقية",
                "Barrier film, film holders and camera cover sleeves for cross-infection control.",
                "غشاء الحماية وحوامله وأغطية الكاميرا للوقاية من العدوى المتبادلة.", IMG_CLINIC);
        Category application = cat("Application & Dispensing", "التطبيق والتوزيع",
                "Micro applicators, brow tips and dispensing guns.",
                "فرش التطبيق الدقيقة وأطراف Brow ومسدسات التوزيع.", IMG_DENTAL);
        Category disposables = cat("Clinical Disposables", "مستهلكات العيادة",
                "Cotton rolls, air/water syringe tips, articulating paper and irrigation needles.",
                "لفائف القطن وأطراف سرنجة الهواء والماء وورق الإطباق وإبر الغسيل.", IMG_SUPPLIES);

        prod(patient, "Dental Bib", "مريلة أسنان",
                "Disposable patient bib, absorbent and waterproof.",
                "مريلة مريض للاستعمال مرة واحدة، ماصة ومقاومة للماء.", "4.5");
        prod(patient, "Bib Holder", "حامل المريلة",
                "Bib holder that keeps the patient bib in place.",
                "حامل يثبت مريلة المريض في مكانها.", "0.35");
        prod(application, "Micro Applicators", "فرش تطبيق دقيقة",
                "Disposable micro applicators for bonding agents, etchants and liners.",
                "فرش دقيقة للاستعمال مرة واحدة لتطبيق مواد الربط والتخريش والبطانات.", "0.30");
        prod(barrier, "Barrier Film", "غشاء حماية",
                "Adhesive barrier film for covering touch surfaces between patients.",
                "غشاء لاصق لتغطية الأسطح الملموسة بين المرضى.", "1.1");
        prod(barrier, "Barrier Film Holder", "حامل غشاء الحماية",
                "Dispenser that holds the barrier film roll.",
                "موزع يحمل لفة غشاء الحماية.", "3.2");
        prod(disposables, "Cotton Rolls", "لفائف قطنية",
                "Absorbent cotton rolls for isolation and moisture control.",
                "لفائف قطنية ماصة للعزل والتحكم في الرطوبة.", "0.90");
        prod(disposables, "Air/Water Syringe Tips", "أطراف سرنجة الهواء والماء",
                "Disposable tips for the three-way air/water syringe.",
                "أطراف للاستعمال مرة واحدة لسرنجة الهواء والماء الثلاثية.", "1.75");
        prod(application, "Brow Tips - Type A", "أطراف Brow - النوع A",
                "Brow tips, type A.",
                "أطراف Brow، النوع A.", "1.2");
        prod(application, "Brow Tips - Type B", "أطراف Brow - النوع B",
                "Brow tips, type B.",
                "أطراف Brow، النوع B.", "2");
        prod(application, "Gun Dispenser", "مسدس توزيع",
                "Dispensing gun for cartridge materials.",
                "مسدس توزيع للمواد في الخراطيش.", "3.5");
        prod(barrier, "Camera Cover Sleeves", "أغطية كاميرا الفم",
                "Disposable protective sleeves for intraoral cameras.",
                "أغطية واقية للاستعمال مرة واحدة لكاميرات الفم.", "2");
        prod(disposables, "Articulating Paper", "ورق إطباق",
                "Articulating paper for checking occlusal contacts.",
                "ورق إطباق لفحص نقاط التلامس الإطباقي.", "1");
        prod(disposables, "Irrigation Needles", "إبر غسيل",
                "Irrigation needles for root canal irrigation.",
                "إبر لغسيل قنوات الجذور.", "1.19");

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

    /** usdCost is the USD import cost from the price list. */
    private void prod(Category c, String name, String nameAr, String desc, String descAr, String usdCost) {
        BigDecimal cost = new BigDecimal(usdCost).multiply(USD_TO_EGP).setScale(2, RoundingMode.HALF_UP);
        productRepository.save(Product.builder()
                .name(name).nameAr(nameAr).description(desc).descriptionAr(descAr)
                .price(cost).stockQuantity(0).category(c)
                .sku(String.format("ORA-%03d", ++skuCounter)).costPrice(cost).reorderLevel(10)
                .brand(OWN_BRAND)
                .build());
    }
}
