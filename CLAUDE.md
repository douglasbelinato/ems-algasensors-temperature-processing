# CLAUDE.md — temperature-processing

Microsserviço de **ingestão/processamento de leituras de temperatura** do projeto AlgaSensors.
Recebe o dado bruto enviado pelos sensores, valida e gera o registro da leitura.

## Stack

- Java 25 (toolchain Gradle) · Spring Boot 4.1.0 · Gradle (use o wrapper `./gradlew`)
- Spring Web (MVC) — **sem JPA / sem banco de dados**
- **Spring AMQP / RabbitMQ** (`spring-boot-starter-amqp`) — publica as leituras processadas
- Lombok · hypersistence-tsid (TSID para `sensorId`) · java-uuid-generator + commons-lang3
  (UUID v7 para o id da leitura)

## Comandos

```bash
./gradlew bootRun   # sobe a aplicação na porta 8081
./gradlew test      # roda os testes
./gradlew build     # compila + testa + empacota
```

## Arquitetura

- Porta **8081**. Pacote base: `com.algaworks.algasensors.temperature.processing`.
- Camadas: `api` (controller/model/config) · `common`.
- `TemperatureProcessingController`: `POST /api/sensors/{sensorId}/temperatures/data`,
  consome **`text/plain`** (o corpo é o valor numérico da temperatura). Valida corpo
  vazio/não-numérico (→ 400) e monta um `TemperatureLogOutput`.
- **Fluxo da leitura**: o controller loga o `TemperatureLogOutput` (`@Slf4j`) e o **publica**
  no broker (ver Mensageria). Não há persistência neste serviço.

## Mensageria (RabbitMQ)

- Config em `infrastructure/rabbitmq`:
  - `RabbitMQConfig` declara o **fanout exchange** `temperature-processing.temperature-received.v1.e`
    (constante `FANOUT_EXCHANGE_NAME`), o `RabbitAdmin` e o `JacksonJsonMessageConverter`
    (usa o `JsonMapper` do Jackson 3 — pacote `tools.jackson.*`).
  - `RabbitMQInitializer` chama `rabbitAdmin.initialize()` num `@PostConstruct` para declarar
    as topologias no startup (**isso força conexão com o broker ao subir o contexto**).
- O controller publica via `rabbitTemplate.convertAndSend(FANOUT_EXCHANGE_NAME, "", payload, mpp)`,
  onde o `MessagePostProcessor` carimba o header **`sensorId`** na mensagem. Quem liga as filas a
  este exchange é o `temperature-monitoring`.

## Convenções

- `sensorId` é **TSID**; o id da leitura é **UUID v7** (time-based, ordenável).
  - Geração centralizada em `common/IdGenerator` (usa `common/UUIDv7Utils`).
  - Conversão web (path variable `String`→TSID): `api/config/web/StringToTSIDWebConverter`.
- Lombok em uso: `@Slf4j`, `@Builder`, `@Getter/@Setter`.
- Erros de domínio via `ResponseStatusException`.

## Testes

- Padrão e2e: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTestClient` apontando para
  `http://localhost:{port}`. Como não há banco, não há limpeza de repositório.
- **Isolamento do RabbitMQ (sem broker nos testes)**: os `@SpringBootTest` mockam os beans AMQP
  com `@MockitoBean RabbitTemplate` e `@MockitoBean RabbitAdmin`. Mockar o `RabbitAdmin` torna
  `RabbitMQInitializer#init()` um no-op (não tenta conectar); mockar o `RabbitTemplate` evita
  publicação real **e** permite verificar a publicação com `ArgumentCaptor`
  (`TemperatureProcessingControllerIT#shouldPublishTemperatureReading` confere exchange,
  routing key, payload e header `sensorId`). Sem isso os testes dependem de um broker no ar.
- `UUIDv7Test`: teste unitário do gerador de id (sem Spring).

## Pegadinhas

- Migração Spring Boot 4: se for adicionar consumo HTTP via `RestClient`, é preciso a
  dependência `org.springframework.boot:spring-boot-starter-restclient` (o bean
  `RestClient.Builder` não vem mais no starter web). Em testes, `@MockBean` foi removido — use
  `@MockitoBean`. Ao usar Jackson, o pacote agora é `tools.jackson.*`.

## Contexto do repositório

Este módulo é um submódulo Git do meta-repo `ems-algasensors-meta`. Para a ordem de commit/push
entre submódulos e meta-repo, ver o `README.md` da raiz.
