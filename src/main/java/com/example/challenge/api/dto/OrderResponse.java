package com.example.challenge.api.dto;

import com.example.challenge.domain.OrderStatus;
import java.util.List;

public class OrderResponse {
    public String id;
    public String customerId;
    public double total;
    public OrderStatus status;
    public List<Item> items;

    public static class Item {
        public Long id;
        public String sku;
        public int qty;
        public double unitPrice;
    }
}
