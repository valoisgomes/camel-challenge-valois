package com.example.challenge.repo;

import com.example.challenge.domain.Order;
import com.example.challenge.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {

    // Busca um pedido e seus itens juntos
    @Query("select o from Order o left join fetch o.items where o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") String id);

    // Busca todos os pedidos com os itens
    @Query("select distinct o from Order o left join fetch o.items")
    List<Order> findAllWithItems();

    // Busca por status com os itens
    @Query("select distinct o from Order o left join fetch o.items where o.status = :status")
    List<Order> findByStatusWithItems(@Param("status") OrderStatus status);
}
