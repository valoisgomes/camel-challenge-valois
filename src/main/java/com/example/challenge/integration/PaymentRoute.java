package com.example.challenge.integration;

import com.example.challenge.service.OrderService;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentRoute extends RouteBuilder {
    public static final String DIRECT_PAY = "direct:payOrder";

    private static final Logger log = LoggerFactory.getLogger(PaymentRoute.class);

    private final PaymentProperties props;
    private final OrderService orderService;

    public PaymentRoute(PaymentProperties props, OrderService orderService) {
        this.props = props;
        this.orderService = orderService;
    }

    @Override
    public void configure() {

        // Retry/backoff para falhas HTTP (status >= 300 gera HttpOperationFailedException)
        onException(HttpOperationFailedException.class)
                .maximumRedeliveries(props.getRetry().getMaxRedeliveries())
                .redeliveryDelay(props.getRetry().getRedeliveryDelayMs())
                .useExponentialBackOff()
                .backOffMultiplier(props.getRetry().getBackoffMultiplier())
                .handled(true) // após esgotar tentativas, tratamos como falha de pagamento
                .process(exchange -> {
                    String orderId = exchange.getIn().getHeader("orderId", String.class);
                    String exId = exchange.getExchangeId();
                    log.warn("Payment FAILED after retries - orderId={}, exchangeId={}, status={}",
                            orderId, exId,
                            exchange.getProperty(Exchange.HTTP_RESPONSE_CODE, Object.class));
                    orderService.markFailed(orderId);
                });

        from(DIRECT_PAY)
                .routeId("pay-order")
                .validate(header("orderId").isNotNull())
                .validate(header("amount").isNotNull())
                .process(e -> {
                    String orderId = e.getIn().getHeader("orderId", String.class);
                    Object amount  = e.getIn().getHeader("amount");
                    log.info("Starting payment - orderId={}, amount={}, exchangeId={}",
                            orderId, amount, e.getExchangeId());
                })
                // Seleciona URL pelo valor do pedido
                .choice()
                .when(simple("${header.amount} > 1000"))
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .toD(props.getFailureUrl()) // ex.: https://dummyjson.com/http/500
                .otherwise()
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .toD(props.getSuccessUrl()) // ex.: https://dummyjson.com/http/200
                .end()
                // Se chegamos aqui, não houve exceção => pagamento OK
                .process(exchange -> {
                    String orderId = exchange.getIn().getHeader("orderId", String.class);
                    log.info("Payment SUCCESS - orderId={}, exchangeId={}", orderId, exchange.getExchangeId());
                    orderService.markPaid(orderId);
                });
    }
}