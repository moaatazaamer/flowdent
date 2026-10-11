package com.flowdent.backend;

import com.flowdent.backend.model.*;

import java.math.BigDecimal;
import java.util.ArrayList;

/** Small factory for entities used across unit tests. */
public final class TestData {

    private TestData() {}

    public static BigDecimal egp(String value) {
        return new BigDecimal(value);
    }

    public static Product product(long id, String name, String price, int stock, String cost) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(egp(price));
        p.setStockQuantity(stock);
        p.setCostPrice(cost == null ? null : egp(cost));
        p.setReorderLevel(10);
        return p;
    }

    public static Client client(long id, String name) {
        Client c = new Client();
        c.setId(id);
        c.setName(name);
        c.setType(ClientType.CLINIC);
        c.setPhone("+20 100 000 000" + id);
        return c;
    }

    public static Supplier supplier(long id, String name) {
        Supplier s = new Supplier();
        s.setId(id);
        s.setName(name);
        s.setActive(true);
        return s;
    }

    /** A non-cancelled order with one line, owned by a client. */
    public static Order order(long id, Client client, Product product, int qty, String unitPrice,
                              String unitCost, OrderStatus status) {
        Order o = new Order();
        o.setId(id);
        o.setClient(client);
        o.setStatus(status);
        o.setItems(new ArrayList<>());
        o.setPaymentMethod(PaymentMethod.CREDIT);
        o.setDeliveryMethod(DeliveryMethod.HOME_DELIVERY);
        o.setCreatedAt(java.time.LocalDateTime.now());
        o.addItem(OrderItem.builder()
                .product(product)
                .quantity(qty)
                .price(egp(unitPrice))
                .unitCost(unitCost == null ? null : egp(unitCost))
                .build());
        o.setTotalAmount(egp(unitPrice).multiply(BigDecimal.valueOf(qty)));
        return o;
    }
}
