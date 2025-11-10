package com.example.challenge.api;

import com.example.challenge.api.dto.NewOrderRequest;
import com.example.challenge.api.dto.OrderMapper;
import com.example.challenge.api.dto.OrderResponse;
import com.example.challenge.api.dto.UpdateOrderRequest;
import com.example.challenge.domain.OrderStatus;
import com.example.challenge.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
@Tag(name="Orders")
public class OrderController {

    private final OrderService service;
    private final ProducerTemplate template;

    public OrderController(OrderService service, ProducerTemplate template) {
        this.service = service;
        this.template = template;
    }

    @Operation(summary = "Cria um novo pedido")
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody NewOrderRequest req) {
        var created = service.create(req);
        var body = OrderMapper.toResponse(created);
        return ResponseEntity.created(URI.create("/api/orders/" + created.getId())).body(body);
    }

    @Operation(summary = "Busca um pedido por ID")
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable("id") String id) {
        return service.get(id)
                .map(o -> ResponseEntity.ok(OrderMapper.toResponse(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Lista pedidos (opcional filtrar por status)")
    @GetMapping
    public ResponseEntity<List<OrderResponse>> list(
            @RequestParam(name = "status", required = false) OrderStatus status) { // <-- nome explícito
        var list = service.list(Optional.ofNullable(status))
                .stream().map(OrderMapper::toResponse).toList();
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "Atualiza itens de um pedido (apenas se NEW)")
    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> update(@PathVariable("id") String id,
                                                @Valid @RequestBody UpdateOrderRequest req) {
        try {
            var updated = service.updateItems(id, req);
            return ResponseEntity.ok(OrderMapper.toResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().build();
        }
    }

    @Operation(summary = "Exclui um pedido (apenas se NEW)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.unprocessableEntity().build();
        }
    }

    @Operation(summary = "Processa pagamento do pedido via Camel chamando DummyJSON")
    @PostMapping("/{id}/pay")
    public ResponseEntity<OrderResponse> pay(@PathVariable("id") String id) {
        var orderOpt = service.get(id);
        if (orderOpt.isEmpty()) return ResponseEntity.notFound().build();
        var order = orderOpt.get();
        if (order.getStatus() != OrderStatus.NEW) return ResponseEntity.unprocessableEntity().build();

        // chamada síncrona pra garantir que o status esteja atualizado ao retornar
        template.requestBodyAndHeaders(
                "direct:payOrder",
                null,
                java.util.Map.of("orderId", order.getId(), "amount", order.getTotal())
        );

        return service.get(id)
                .map(o -> ResponseEntity.ok(OrderMapper.toResponse(o)))
                .orElse(ResponseEntity.notFound().build());
    }
}
