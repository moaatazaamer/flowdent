package com.flowdent.backend.controller;

import com.flowdent.backend.dto.Dtos.*;
import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.OrderRepository;
import com.flowdent.backend.repository.ProductRepository;
import com.flowdent.backend.repository.UserRepository;
import com.flowdent.backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderService orderService;

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        List<Order> orders = orderRepository.findAll();
        List<Order> active = orders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList();

        BigDecimal revenue = active.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidRevenue = active.stream().filter(Order::isPaid)
                .map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) byStatus.put(s.name(), 0L);
        orders.forEach(o -> byStatus.merge(o.getStatus().name(), 1L, Long::sum));

        Map<String, Long> byPayment = new LinkedHashMap<>();
        for (PaymentMethod p : PaymentMethod.values()) byPayment.put(p.name(), 0L);
        active.forEach(o -> { if (o.getPaymentMethod() != null) byPayment.merge(o.getPaymentMethod().name(), 1L, Long::sum); });

        // Last 14 days revenue / order count.
        LocalDate today = LocalDate.now();
        List<DailyStat> daily = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            List<Order> dayOrders = active.stream().filter(o -> o.getCreatedAt().toLocalDate().equals(d)).toList();
            daily.add(new DailyStat(d.toString(),
                    dayOrders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                    dayOrders.size()));
        }

        // Top products by units sold.
        Map<Long, long[]> units = new HashMap<>();
        Map<Long, Product> productById = new HashMap<>();
        for (Order o : active) {
            for (OrderItem it : o.getItems()) {
                if (it.getProduct() == null) continue;
                productById.put(it.getProduct().getId(), it.getProduct());
                units.computeIfAbsent(it.getProduct().getId(), k -> new long[1])[0] += it.getQuantity();
            }
        }
        List<Map<String, Object>> topProducts = units.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5)
                .map(e -> {
                    Product p = productById.get(e.getKey());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("nameAr", p.getNameAr());
                    m.put("unitsSold", e.getValue()[0]);
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("totalRevenue", revenue);
        res.put("paidRevenue", paidRevenue);
        res.put("totalOrders", orders.size());
        res.put("pendingOrders", byStatus.get(OrderStatus.PENDING.name()));
        res.put("totalCustomers", userRepository.countByRole(Role.CUSTOMER));
        res.put("totalProducts", productRepository.count());
        res.put("ordersByStatus", byStatus);
        res.put("ordersByPaymentMethod", byPayment);
        res.put("dailyStats", daily);
        res.put("topProducts", topProducts);
        res.put("lowStockProducts", productRepository.findAll().stream()
                .filter(p -> p.getStockQuantity() <= p.reorderLevelOrDefault())
                .sorted(java.util.Comparator.comparing(Product::getStockQuantity))
                .limit(8).toList());
        res.put("recentOrders", orderRepository.findTop5ByOrderByCreatedAtDesc());
        return res;
    }

    @GetMapping("/customers")
    public List<CustomerSummary> customers() {
        Map<Long, List<Order>> byUser = orderRepository.findAll().stream()
                .filter(o -> o.getUser() != null)
                .collect(Collectors.groupingBy(o -> o.getUser().getId()));
        return userRepository.findByRoleOrderByCreatedAtDesc(Role.CUSTOMER).stream().map(u -> {
            List<Order> os = byUser.getOrDefault(u.getId(), List.of());
            BigDecimal spent = os.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                    .map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new CustomerSummary(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                    u.getAddress(), u.getCreatedAt(), os.size(), spent);
        }).toList();
    }

    @GetMapping("/orders")
    public List<Order> orders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    @PutMapping("/orders/{id}/status")
    public Order updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest req) {
        return orderService.updateStatus(id, req.status());
    }
}
