package com.example.challenge.integration;

import com.example.challenge.integration.PaymentRoute;
import com.example.challenge.service.OrderService;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;

@CamelSpringBootTest
@UseAdviceWith
@SpringBootTest
class PaymentRouteSuccessTest {

    @MockBean
    private OrderService orderService;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate template;

    private MockEndpoint httpMock;

    @BeforeEach
    void setup() throws Exception {
        // Intercepta QUALQUER chamada https feita na rota "pay-order" e redireciona para mock:http
        AdviceWith.adviceWith(camelContext, "pay-order", a -> {
            a.interceptSendToEndpoint("https://*")
                    .skipSendToOriginalEndpoint()
                    .to("mock:http");
        });

        // Configura o mock para responder 200 OK
        httpMock = camelContext.getEndpoint("mock:http", MockEndpoint.class);
        httpMock.whenAnyExchangeReceived(ex -> {
            ex.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
            ex.getMessage().setBody("OK");
        });

        camelContext.start();
    }

    @Test
    void shouldMarkPaid_whenAmountIsLessOrEqualTo1000() throws Exception {
        String orderId = "order-paid-1";

        httpMock.expectedMessageCount(1);

        template.requestBodyAndHeaders(
                PaymentRoute.DIRECT_PAY,          // "direct:payOrder"
                null,
                Map.of("orderId", orderId, "amount", 950d)
        );

        httpMock.assertIsSatisfied();

        Mockito.verify(orderService, Mockito.times(1)).markPaid(orderId);
        Mockito.verify(orderService, Mockito.never()).markFailed(Mockito.anyString());
    }
}
