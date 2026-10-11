package com.flowdent.backend.controller;

import com.flowdent.backend.dto.OpsDtos.ClientPaymentRequest;
import com.flowdent.backend.dto.OpsDtos.ClientRequest;
import com.flowdent.backend.dto.OpsDtos.ClientSummary;
import com.flowdent.backend.dto.OpsDtos.StaffSaleRequest;
import com.flowdent.backend.exception.ApiException;
import com.flowdent.backend.model.Client;
import com.flowdent.backend.model.ClientPayment;
import com.flowdent.backend.model.ClientType;
import com.flowdent.backend.model.Order;
import com.flowdent.backend.repository.ClientPaymentRepository;
import com.flowdent.backend.repository.ClientRepository;
import com.flowdent.backend.repository.OrderRepository;
import com.flowdent.backend.repository.ProductReturnRepository;
import com.flowdent.backend.service.ClientService;
import com.flowdent.backend.service.LedgerService;
import com.flowdent.backend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Clients (buyers): clinics and doctors, their sales, payments and balances. */
@RestController
@RequestMapping("/api/admin/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientRepository clientRepository;
    private final ClientPaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProductReturnRepository returnRepository;
    private final ClientService clientService;
    private final LedgerService ledgerService;
    private final OrderService orderService;

    @GetMapping
    public List<ClientSummary> all() {
        return ledgerService.clientSummaries();
    }

    /** Client account: summary plus orders, payments and returns. */
    @GetMapping("/{id}")
    public Map<String, Object> one(@PathVariable Long id) {
        Client c = get(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", ledgerService.clientSummary(c));
        m.put("orders", orderRepository.findByClientIdOrderByCreatedAtDesc(id));
        m.put("payments", paymentRepository.findByClientIdOrderByPaidAtDescIdDesc(id));
        m.put("returns", returnRepository.findByClientIdOrderByReturnDateDescIdDesc(id));
        return m;
    }

    @PostMapping
    public ResponseEntity<ClientSummary> create(@Valid @RequestBody ClientRequest req) {
        Client c = new Client();
        apply(c, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.clientSummary(clientRepository.save(c)));
    }

    @PutMapping("/{id}")
    public ClientSummary update(@PathVariable Long id, @Valid @RequestBody ClientRequest req) {
        Client c = get(id);
        apply(c, req);
        return ledgerService.clientSummary(clientRepository.save(c));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Client c = get(id);
        if (orderRepository.existsByClientId(id) || paymentRepository.existsByClientId(id)
                || returnRepository.existsByClientId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "This client has orders or payments and cannot be deleted.");
        }
        clientRepository.delete(c);
        return ResponseEntity.noContent().build();
    }

    // ---------- sales entered by staff ----------

    @PostMapping("/sales")
    public ResponseEntity<Order> sale(@Valid @RequestBody StaffSaleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createStaffSale(req));
    }

    // ---------- payments from clients ----------

    @GetMapping("/payments")
    public List<ClientPayment> payments() {
        return paymentRepository.findAllByOrderByPaidAtDescIdDesc();
    }

    @PostMapping("/payments")
    public ResponseEntity<ClientPayment> pay(@Valid @RequestBody ClientPaymentRequest req) {
        Client c = get(req.clientId());
        if (req.orderId() != null) {
            Order o = orderRepository.findById(req.orderId()).orElseThrow(() -> ApiException.notFound("Order"));
            if (o.getClient() == null || !o.getClient().getId().equals(c.getId())) {
                throw ApiException.badRequest("That order belongs to another client");
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
                clientService.recordPayment(c, req.orderId(), req.amount(), req.paidAt(), req.method(), req.notes()));
    }

    @DeleteMapping("/payments/{paymentId}")
    @Transactional
    public ResponseEntity<Void> deletePayment(@PathVariable Long paymentId) {
        ClientPayment p = paymentRepository.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment"));
        Long orderId = p.getOrderId();
        paymentRepository.delete(p);
        paymentRepository.flush();
        if (orderId != null) clientService.refreshOrderPaid(orderId);
        return ResponseEntity.noContent().build();
    }

    private Client get(Long id) {
        return clientRepository.findById(id).orElseThrow(() -> ApiException.notFound("Client"));
    }

    private static void apply(Client c, ClientRequest req) {
        c.setName(req.name().trim());
        c.setType(req.type() != null ? req.type() : ClientType.CLINIC);
        c.setContactName(req.contactName());
        c.setPhone(req.phone());
        c.setEmail(req.email());
        c.setAddress(req.address());
        c.setCity(req.city());
        c.setNotes(req.notes());
    }
}
