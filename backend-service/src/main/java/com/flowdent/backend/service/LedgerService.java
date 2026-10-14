package com.flowdent.backend.service;

import com.flowdent.backend.dto.OpsDtos.ClientSummary;
import com.flowdent.backend.dto.OpsDtos.SupplierSummary;
import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Account balances.
 * <ul>
 *   <li>Client owes = sales (not cancelled) − payments received − returns settled as credit.</li>
 *   <li>Shop owes provider = goods received − payments made − returns settled as credit.</li>
 *   <li>Both also include the opening balance imported from old records (what was still owed when the system started).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerService {

    private final ClientRepository clientRepository;
    private final ClientPaymentRepository clientPaymentRepository;
    private final OrderRepository orderRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductReturnRepository returnRepository;
    private final LegacyRecordRepository legacyRepository;

    private static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------ opening balances

    private Map<Long, BigDecimal> openingBalances(LegacyType type) {
        Map<Long, BigDecimal> out = new HashMap<>();
        for (LegacyRecord r : legacyRepository.findByTypeIn(List.of(type))) {
            Long id = type == LegacyType.CLIENT_BALANCE
                    ? (r.getClient() == null ? null : r.getClient().getId())
                    : (r.getSupplier() == null ? null : r.getSupplier().getId());
            if (id != null) out.merge(id, r.getAmount(), BigDecimal::add);
        }
        return out;
    }

    private BigDecimal openingFor(LegacyType type, Long id) {
        return openingBalances(type).getOrDefault(id, BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------ clients

    public List<ClientSummary> clientSummaries() {
        Map<Long, List<Order>> orders = orderRepository.findAll().stream()
                .filter(o -> o.getClient() != null && o.getStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.groupingBy(o -> o.getClient().getId()));
        Map<Long, List<ClientPayment>> payments = clientPaymentRepository.findAll().stream()
                .collect(Collectors.groupingBy(p -> p.getClient().getId()));
        Map<Long, List<ProductReturn>> returns = returnRepository.findAll().stream()
                .filter(r -> r.getType() == ReturnType.CUSTOMER_RETURN && r.getClient() != null)
                .collect(Collectors.groupingBy(r -> r.getClient().getId()));

        Map<Long, BigDecimal> opening = openingBalances(LegacyType.CLIENT_BALANCE);
        return clientRepository.findAllByOrderByNameAsc().stream()
                .map(c -> clientSummary(c,
                        orders.getOrDefault(c.getId(), List.of()),
                        payments.getOrDefault(c.getId(), List.of()),
                        returns.getOrDefault(c.getId(), List.of()),
                        opening.getOrDefault(c.getId(), BigDecimal.ZERO)))
                .toList();
    }

    public ClientSummary clientSummary(Client c) {
        List<Order> orders = orderRepository.findByClientIdOrderByCreatedAtDesc(c.getId()).stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList();
        return clientSummary(c, orders,
                clientPaymentRepository.findByClientIdOrderByPaidAtDescIdDesc(c.getId()),
                returnRepository.findByClientIdOrderByReturnDateDescIdDesc(c.getId()),
                openingFor(LegacyType.CLIENT_BALANCE, c.getId()));
    }

    private ClientSummary clientSummary(Client c, List<Order> orders, List<ClientPayment> payments,
                                        List<ProductReturn> returns, BigDecimal opening) {
        BigDecimal sales = sum(orders.stream().map(Order::getTotalAmount).toList());
        BigDecimal paid = sum(payments.stream().map(ClientPayment::getAmount).toList());
        BigDecimal returnedCredit = sum(returns.stream()
                .filter(r -> r.getSettlement() != ReturnSettlement.CASH)
                .map(ProductReturn::getAmount).toList());
        BigDecimal returnedAll = sum(returns.stream().map(ProductReturn::getAmount).toList());
        LocalDateTime last = orders.stream().map(Order::getCreatedAt).max(Comparator.naturalOrder()).orElse(null);
        return new ClientSummary(c.getId(), c.getName(), c.getType(), c.getContactName(), c.getPhone(),
                c.getEmail(), c.getAddress(), c.getCity(), c.getNotes(), c.isRegistered(), c.getCreatedAt(),
                orders.size(), sales, paid, returnedAll,
                sales.subtract(paid).subtract(returnedCredit).add(opening), last, opening);
    }

    public BigDecimal totalReceivables() {
        return sum(clientSummaries().stream().map(ClientSummary::balance).filter(b -> b.signum() > 0).toList());
    }

    // ------------------------------------------------------------------ providers

    public List<SupplierSummary> supplierSummaries() {
        Map<Long, List<Purchase>> purchases = purchaseRepository.findAll().stream()
                .collect(Collectors.groupingBy(p -> p.getSupplier().getId()));
        Map<Long, List<SupplierPayment>> payments = supplierPaymentRepository.findAll().stream()
                .collect(Collectors.groupingBy(p -> p.getSupplier().getId()));
        Map<Long, List<ProductReturn>> returns = returnRepository.findAll().stream()
                .filter(r -> r.getType() == ReturnType.SUPPLIER_RETURN && r.getSupplier() != null)
                .collect(Collectors.groupingBy(r -> r.getSupplier().getId()));

        Map<Long, BigDecimal> opening = openingBalances(LegacyType.PROVIDER_BALANCE);
        return supplierRepository.findAll().stream()
                .sorted(Comparator.comparing(s -> s.getName().toLowerCase()))
                .map(s -> supplierSummary(s,
                        purchases.getOrDefault(s.getId(), List.of()),
                        payments.getOrDefault(s.getId(), List.of()),
                        returns.getOrDefault(s.getId(), List.of()),
                        opening.getOrDefault(s.getId(), BigDecimal.ZERO)))
                .toList();
    }

    public SupplierSummary supplierSummary(Supplier s) {
        return supplierSummary(s,
                purchaseRepository.findBySupplierIdOrderByPurchaseDateDescIdDesc(s.getId()),
                supplierPaymentRepository.findBySupplierIdOrderByPaidAtDescIdDesc(s.getId()),
                returnRepository.findBySupplierIdOrderByReturnDateDescIdDesc(s.getId()),
                openingFor(LegacyType.PROVIDER_BALANCE, s.getId()));
    }

    private SupplierSummary supplierSummary(Supplier s, List<Purchase> purchases, List<SupplierPayment> payments,
                                            List<ProductReturn> returns, BigDecimal opening) {
        List<Purchase> received = purchases.stream().filter(p -> p.getStatus() == PurchaseStatus.RECEIVED).toList();
        BigDecimal bought = sum(received.stream().map(Purchase::getGoodsTotal).toList());
        BigDecimal paid = sum(payments.stream().map(SupplierPayment::getAmount).toList());
        BigDecimal returnedCredit = sum(returns.stream()
                .filter(r -> r.getSettlement() != ReturnSettlement.CASH)
                .map(ProductReturn::getAmount).toList());
        BigDecimal returnedAll = sum(returns.stream().map(ProductReturn::getAmount).toList());
        LocalDate last = purchases.stream().filter(p -> p.getStatus() != PurchaseStatus.CANCELLED)
                .map(Purchase::getPurchaseDate).max(Comparator.naturalOrder()).orElse(null);
        long count = purchases.stream().filter(p -> p.getStatus() != PurchaseStatus.CANCELLED).count();
        return new SupplierSummary(s.getId(), s.getName(), s.getContactName(), s.getPhone(), s.getEmail(),
                s.getAddress(), s.getNotes(), Boolean.TRUE.equals(s.getActive()), s.getCreatedAt(),
                count, bought, paid, returnedAll,
                bought.subtract(paid).subtract(returnedCredit).add(opening), last, opening);
    }

    public BigDecimal totalPayables() {
        return sum(supplierSummaries().stream().map(SupplierSummary::balance).filter(b -> b.signum() > 0).toList());
    }
}
