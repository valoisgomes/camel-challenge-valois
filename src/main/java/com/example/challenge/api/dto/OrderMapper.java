package com.example.challenge.api.dto;

import com.example.challenge.domain.Order;
import com.example.challenge.domain.OrderItem;

import java.util.stream.Collectors;

public final class OrderMapper {
    private OrderMapper() {}

    public static OrderResponse toResponse(Order o) {
        OrderResponse r = new OrderResponse();
        r.id = o.getId();
        r.customerId = o.getCustomerId();
        r.total = o.getTotal();
        r.status = o.getStatus();
        r.items = o.getItems().stream().map(OrderMapper::toItem).collect(Collectors.toList());
        return r;
    }

    private static OrderResponse.Item toItem(OrderItem i) {
        OrderResponse.Item it = new OrderResponse.Item();
        it.id = i.getId();
        it.sku = i.getSku();
        it.qty = i.getQty();
        it.unitPrice = i.getUnitPrice();
        return it;
    }
}
