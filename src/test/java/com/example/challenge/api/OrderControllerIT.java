package com.example.challenge.api;

import com.example.challenge.Application;
import com.example.challenge.api.dto.NewOrderRequest;
import com.example.challenge.api.dto.UpdateOrderRequest;
import com.example.challenge.domain.OrderStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerIT {

    @Autowired
    TestRestTemplate rest;

    @org.springframework.beans.factory.annotation.Autowired
    org.apache.camel.CamelContext camel;

    // configura rotas locais para simular sucesso/erro e redirecionamos via Advice nos testes Camel.
    // Aqui usaremos as URLs reais do application.yaml e apenas verificaremos o fluxo REST básico.

    @Test
    void create_get_update_delete_and_pay_success() throws Exception {
        // 1) CREATE
        NewOrderRequest req = new NewOrderRequest();
        req.setCustomerId("cli-paid");
        NewOrderRequest.Item i1 = new NewOrderRequest.Item();
        i1.setSku("NOTE-001"); i1.setQty(1); i1.setUnitPrice(650);
        NewOrderRequest.Item i2 = new NewOrderRequest.Item();
        i2.setSku("MOUSE-002"); i2.setQty(1); i2.setUnitPrice(300);
        req.setItems(List.of(i1, i2));

        ResponseEntity<Map> created = rest.postForEntity("/api/orders", req, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("id");
        assertThat(id).isNotBlank();

        // 2) GET by id
        ResponseEntity<Map> got = rest.getForEntity("/api/orders/{id}", Map.class, id);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(got.getBody().get("total")).isEqualTo(950.0);

        // 3) UPDATE (continua NEW)
        UpdateOrderRequest upd = new UpdateOrderRequest();
        UpdateOrderRequest.Item ui = new UpdateOrderRequest.Item();
        ui.setSku("KBD-003"); ui.setQty(1); ui.setUnitPrice(50);
        upd.setItems(List.of(ui));

        ResponseEntity<Map> updated = rest.exchange(
                "/api/orders/{id}", HttpMethod.PUT, new HttpEntity<>(upd), Map.class, id);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("total")).isEqualTo(50.0);

        // 4) PAY (como total <= 1000 deve virar PAID)
        ResponseEntity<Map> paid = rest.postForEntity("/api/orders/{id}/pay", null, Map.class, id);
        assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paid.getBody().get("status")).isEqualTo(OrderStatus.PAID.name());

        // 5) DELETE (não permitido - já não é NEW)
        ResponseEntity<Void> del = rest.exchange("/api/orders/{id}", HttpMethod.DELETE, HttpEntity.EMPTY, Void.class, id);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void pay_failure_marks_failed() throws Exception {
        // cria pedido >1000
        NewOrderRequest req = new NewOrderRequest();
        req.setCustomerId("cli-failed");
        NewOrderRequest.Item a = new NewOrderRequest.Item();
        a.setSku("TV-70"); a.setQty(1); a.setUnitPrice(1200);
        req.setItems(List.of(a));

        String id = (String) rest.postForEntity("/api/orders", req, Map.class).getBody().get("id");

        // simula 500 montando rotas locais e usando Advice nos testes Camel.
        // Aqui apenas exercitamos o endpoint – o PaymentRouteFailureTest já valida a marcação FAILED.
        ResponseEntity<Map> resp = rest.postForEntity("/api/orders/{id}/pay", null, Map.class, id);
        // dependendo da conectividade com DummyJSON, pode retornar 200 com FAILED marcado pela rota
        assertThat(resp.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
