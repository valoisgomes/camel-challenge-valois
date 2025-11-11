# Camel DummyJSON Challenge (H2)

Desafio técnico: integração Apache Camel + Spring Boot + DummyJSON API.

## 🧩 Funcionalidades

- Criação de pedidos (`/api/orders`)
- Atualização e exclusão apenas com status `NEW`
- Pagamento processado via rota Camel `direct:payOrder`
- Retentativas automáticas em falha de pagamento
- Testes de integração com sucesso e falha de pagamento (`PaymentRouteSuccessTest`, `PaymentRouteFailureTest`)

## ⚙️ Executando localmente

```bash
./mvnw clean package
java -jar target/camel-dummyjson-challenge-h2-0.1.0-SNAPSHOT.jar
