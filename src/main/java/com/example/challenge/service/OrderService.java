package com.example.challenge.service;

import com.example.challenge.api.dto.NewOrderRequest;
import com.example.challenge.api.dto.UpdateOrderRequest;
import com.example.challenge.domain.Order;
import com.example.challenge.domain.OrderItem;
import com.example.challenge.domain.OrderStatus;
import com.example.challenge.repo.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository repo;

    public OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    public static double calculateTotal(List<OrderItem> items) {
        return items.stream().mapToDouble(i -> i.getQty() * i.getUnitPrice()).sum();
    }

    @Transactional
    public Order create(NewOrderRequest req) {
        Order order = new Order();
        order.setCustomerId(req.getCustomerId());
        order.setStatus(OrderStatus.NEW);

        List<OrderItem> items = req.getItems().stream().map(i -> {
            OrderItem item = new OrderItem();
            item.setSku(i.getSku());
            item.setQty(i.getQty());
            item.setUnitPrice(i.getUnitPrice());
            item.setOrder(order);
            return item;
        }).toList();

        order.setItems(items);
        order.setTotal(OrderService.calculateTotal(items));

        return repo.save(order);
    }

    @Transactional(readOnly = true)
    public Optional<Order> get(String id) {
        return repo.findByIdWithItems(id);
    }

    @Transactional(readOnly = true)
    public List<Order> list(Optional<OrderStatus> status) {
        return status.map(repo::findByStatusWithItems)
                .orElseGet(repo::findAllWithItems);
    }

    @Transactional
    public Order updateItems(String id, UpdateOrderRequest req) {
        Order order = repo.findByIdWithItems(id).orElseThrow(() ->
                new IllegalArgumentException("Pedido não encontrado: " + id)
        );
        if (order.getStatus() != OrderStatus.NEW) {
            throw new IllegalStateException("Só é possível editar pedidos com status NEW");
        }

        List<OrderItem> newItems = req.getItems().stream().map(i -> {
            OrderItem item = new OrderItem();
            item.setSku(i.getSku());
            item.setQty(i.getQty());
            item.setUnitPrice(i.getUnitPrice());
            item.setOrder(order);
            return item;
        }).toList();

        order.getItems().clear();
        order.getItems().addAll(newItems);
        order.setTotal(calculateTotal(newItems));

        return repo.save(order);
    }

    @Transactional
    public void delete(String id) {
        Order order = repo.findByIdWithItems(id).orElseThrow(() ->
                new IllegalArgumentException("Pedido não encontrado: " + id)
        );
        if (order.getStatus() != OrderStatus.NEW) {
            throw new IllegalStateException("Só é possível excluir pedidos com status NEW");
        }
        repo.delete(order);
    }

    @Transactional
    public void markPaid(String id) {
        repo.findById(id).ifPresent(order -> {
            order.setStatus(OrderStatus.PAID);
            repo.save(order);
        });
    }

    @Transactional
    public void markFailed(String id) {
        repo.findById(id).ifPresent(order -> {
            order.setStatus(OrderStatus.FAILED_PAYMENT);
            repo.save(order);
        });
    }
}
