package com.flowdent.backend.service;

import com.flowdent.backend.model.*;
import com.flowdent.backend.repository.ClientPaymentRepository;
import com.flowdent.backend.repository.ClientRepository;
import com.flowdent.backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Client (B2B buyer) lookups and client payments. */
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientPaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /** Creates the client record for a newly registered web account. */
    @Transactional
    public Client createForUser(User user, String clinicName) {
        return clientRepository.findByUserId(user.getId()).orElseGet(() -> {
            boolean hasClinic = clinicName != null && !clinicName.isBlank();
            return clientRepository.save(Client.builder()
                    .name(hasClinic ? clinicName.trim() : user.getFullName())
                    .type(hasClinic ? ClientType.CLINIC : ClientType.DOCTOR)
                    .contactName(user.getFullName())
                    .email(user.getEmail().contains("@") ? user.getEmail() : null)
                    .phone(user.getPhone())
                    .address(user.getAddress())
                    .user(user)
                    .build());
        });
    }

    /**
     * Finds the client for an online order: the account's client, else an existing client with
     * the same email or phone, else a new one.
     */
    @Transactional
    public Client resolveForOrder(User user, String fullName, String email, String phone, String address) {
        if (user != null) {
            var existing = clientRepository.findByUserId(user.getId());
            if (existing.isPresent()) return existing.get();
        }
        Client match = null;
        if (email != null && !email.isBlank()) match = clientRepository.findFirstByEmailIgnoreCase(email.trim()).orElse(null);
        if (match == null && phone != null && !phone.isBlank()) match = clientRepository.findFirstByPhone(phone.trim()).orElse(null);
        if (match != null) {
            if (user != null && match.getUser() == null) {
                match.setUser(user);
                clientRepository.save(match);
            }
            return match;
        }
        return clientRepository.save(Client.builder()
                .name(fullName)
                .type(user != null ? ClientType.DOCTOR : ClientType.OTHER)
                .contactName(fullName)
                .email(email)
                .phone(phone)
                .address(address)
                .user(user)
                .build());
    }

    /** Records money received from a client and marks the linked order paid once fully covered. */
    @Transactional
    public ClientPayment recordPayment(Client client, Long orderId, BigDecimal amount, LocalDate paidAt,
                                       String method, String notes) {
        ClientPayment payment = paymentRepository.save(ClientPayment.builder()
                .client(client)
                .orderId(orderId)
                .amount(amount)
                .paidAt(paidAt != null ? paidAt : LocalDate.now())
                .method(method)
                .notes(notes)
                .build());
        if (orderId != null) refreshOrderPaid(orderId);
        return payment;
    }

    /** Sets order.paid from the payments linked to it. */
    @Transactional
    public void refreshOrderPaid(Long orderId) {
        orderRepository.findById(orderId).ifPresent(o -> {
            BigDecimal paid = paidForOrder(orderId);
            boolean fully = o.getTotalAmount() != null && paid.compareTo(o.getTotalAmount()) >= 0;
            if (o.isPaid() != fully) {
                o.setPaid(fully);
                orderRepository.save(o);
            }
        });
    }

    public BigDecimal paidForOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId).stream()
                .map(ClientPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Pays whatever is still open on an order (used for card payments and cash/POS at delivery). */
    @Transactional
    public void settleOrder(Order order, String method) {
        if (order.getClient() == null) {
            order.setPaid(true);
            orderRepository.save(order);
            return;
        }
        BigDecimal open = order.getTotalAmount().subtract(paidForOrder(order.getId()));
        if (open.signum() > 0) {
            recordPayment(order.getClient(), order.getId(), open, LocalDate.now(), method, "Order #" + order.getId());
        } else {
            refreshOrderPaid(order.getId());
        }
    }
}
