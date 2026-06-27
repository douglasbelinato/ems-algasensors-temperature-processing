# CLAUDE.md — temperature-processing

Microsserviço de **ingestão/processamento de leituras de temperatura** do projeto AlgaSensors.
Recebe o dado bruto enviado pelos sensores, valida e gera o registro da leitura.

## Stack

- Java 25 (toolchain Gradle) · Spring Boot 4.1.0 · Gradle (use o wrapper `./gradlew`)
- Spring Web (MVC) — **sem JPA / sem banco de dados**
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
- **Estado atual**: a leitura processada é apenas **logada** (`@Slf4j`), ainda não há
  persistência nem publicação para outro serviço.

## Convenções

- `sensorId` é **TSID**; o id da leitura é **UUID v7** (time-based, ordenável).
  - Geração centralizada em `common/IdGenerator` (usa `common/UUIDv7Utils`).
  - Conversão web (path variable `String`→TSID): `api/config/web/StringToTSIDWebConverter`.
- Lombok em uso: `@Slf4j`, `@Builder`, `@Getter/@Setter`.
- Erros de domínio via `ResponseStatusException`.

## Testes

- Padrão e2e: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTestClient` apontando para
  `http://localhost:{port}`. Como não há banco, não há limpeza de repositório.

## Pegadinhas

- Migração Spring Boot 4: se for adicionar consumo HTTP via `RestClient`, é preciso a
  dependência `org.springframework.boot:spring-boot-starter-restclient` (o bean
  `RestClient.Builder` não vem mais no starter web). Em testes, `@MockBean` foi removido — use
  `@MockitoBean`. Ao usar Jackson, o pacote agora é `tools.jackson.*`.

## Contexto do repositório

Este módulo é um submódulo Git do meta-repo `ems-algasensors-meta`. Para a ordem de commit/push
entre submódulos e meta-repo, ver o `README.md` da raiz.
