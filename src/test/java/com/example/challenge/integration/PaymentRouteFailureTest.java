package com.example.challenge.integration;

import com.example.challenge.integration.PaymentRoute;
import com.example.challenge.service.OrderService;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

@CamelSpringBootTest
@UseAdviceWith
@SpringBootTest
@TestPropertySource(properties = {
        // Deixa os retries pequenos para o teste rodar rápido
        "payment.retry.max-redeliveries=2",
        "payment.retry.redelivery-delay-ms=10",
        "payment.retry.backoff-multiplier=1.5"
})
class PaymentRouteFailureTest {

    @MockBean
    private OrderService orderService;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate template;

    private MockEndpoint httpMock;

    @BeforeEach
    void setup() throws Exception {
        // Intercepta QUALQUER chamada https e joga para mock:http (que lançará 500)
        AdviceWith.adviceWith(camelContext, "pay-order", a -> {
            a.interceptSendToEndpoint("https://*")
                    .skipSendToOriginalEndpoint()
                    .to("mock:http");
        });

        httpMock = camelContext.getEndpoint("mock:http", MockEndpoint.class);

        // Simula falha HTTP 500 para acionar onException(HttpOperationFailedException)
        httpMock.whenAnyExchangeReceived(ex -> {
            throw new HttpOperationFailedException(
                    "https://dummyjson.com/http/500",
                    500,
                    "Server Error",
                    null,
                    null,
                    null
            );
        });

        camelContext.start();
    }

    @Test
    void shouldMarkFailed_whenAmountGreaterThan1000_afterRetries() throws Exception {
        String orderId = "order-failed-1";

        template.requestBodyAndHeaders(
                PaymentRoute.DIRECT_PAY,
                null,
                Map.of("orderId", orderId, "amount", 1500d)
        );

        // Após esgotar os retries, a rota deve invocar markFailed
        Mockito.verify(orderService, Mockito.times(1)).markFailed(orderId);
        Mockito.verify(orderService, Mockito.never()).markPaid(Mockito.anyString());
    }
}
