# Plano de Melhorias — Portabilidade Numérica

**Stack-alvo:** Java 21+ · Spring Boot 3.x · Angular 17+ · API REST · Arquitetura Hexagonal · BFF
**Premissa:** o projeto atual é um batch CLI (Java 17, sem framework) com núcleo de domínio bem separado
(`domain/`, `service/` com ports, `infrastructure/` com adapters). O plano evolui isso para uma plataforma
de serviço com API + frontend, preservando o código que já funciona.

---

## 1. Diagnóstico do estado atual

### O que está bom ✅
| Item | Evidência |
|------|-----------|
| Sementes de arquitetura hexagonal | Interfaces `PortabilityRepository` e `PrefixResolver` em `service/resolver/` (ports); implementações DuckDB/JSON em `infrastructure/` (adapters) — DIP aplicado |
| Normalização robusta | `PhoneNormalizer` preserva fixos de 10 dígitos (bug do "9" corrigido — ver `README_MELHORIAS.md`) |
| Estratégia de resolução em camadas | DuckDB oficial → prefixo 11d → prefixo 10d → `nao_identificado`, com métricas por fonte |
| Testes de unidade | JUnit 5 + Mockito cobrindo normalização e resolução (~20 testes) |
| Docker multi-stage | Base DuckDB fora da imagem, montada como volume |

### Lacunas ❌
| Área | Problema |
|------|----------|
| **Entrega ao cliente** | Sem API — só arquivo em pasta → arquivo na saída. Não atende consulta interativa nem integração sistêmica |
| **Frontend** | Não existe Angular no repositório |
| **Versões** | Java 17 (pom.xml), fora do alvo Java 21+; sem Spring Boot |
| **Performance** | Conexão DuckDB aberta a cada execução e consultada com `IN (?×5000)` — ineficiente em lotes grandes; leitura de arquivos inteira em memória (`List<Map<String,String>>`); conexão serial entre arquivos |
| **Escalabilidade** | Acoplado a filesystem local + arquivo `.duckdb` único; sem paralelismo entre lotes/arquivos; sem cache |
| **Arquitetura** | DI manual no `main`; pacotes `application`/`service` sobrepostos; `ProcessResult` usa `Map<String,String>` com chave = número *bruto* (colisões quando linhas repetem telefone formatado diferente) |
| **Qualidade** | Sem CI, sem cobertura de teste medida, sem testes de integração (DuckDB real), sem validação de schema de entrada |
| **Operação** | Logs apenas em arquivo; sem health/metrics; sem versionamento de base de portabilidade |

---

## 2. Visão de destino (target architecture)

```
┌─────────────────────────┐        ┌──────────────────────────────┐
│  Angular 17+ (SPA)      │  HTTPS │  BFF (Spring Boot 3.x/Java 21)│
│  standalone components, │◄──────►│  - orquestra casos de uso    │
│  signals, lazy routes   │  JSON  │  - agrega core + batch       │
└─────────────────────────┘        │  - auth, rate-limit, DTOs    │
                                   └──────────┬───────────────────┘
                                              │ ports (interfaces)
                              ┌───────────────┼────────────────┐
                              ▼               ▼                ▼
                       Core Service    Batch Service    Adapters
                    (resolução single/ (processamento   DuckDB, JSON prefix,
                     bulk via API)     async com fila)  CSV/XLSX, object storage
```

- **Núcleo de domínio puro** (sem Spring): entidades `PhoneNumber`, `OperatorInfo`, políticas de resolução — reusado pelo batch CLI atual **e** pela API.
- **Ports**: `PortabilityRepository`, `PrefixResolver` + novo `PortabilityQueryUseCase`.
- **Adapters**: Spring Web (REST), DuckDB, loaders, mensageria (batch async).
- **BFF**: camada dedicada para o Angular — endpoints enxutos por tela, agregação, contratos próprios (não expõe o core model).

---

## 3. Roadmap por fases

### Fase 0 — Fundações (quick wins, ~1 semana) ✅ CONCLUÍDA
Objetivo: alinhar toolchain e criar rede de segurança antes de refatorar.

- [x] Migrar `pom.xml` para **Java 21** (`maven.compiler.release=21`, `<java.version>21</java.version>`); usar virtual threads desde já onde fizer sentido
- [x] Introduzir **Spring Boot 3.3+** como parent/BOM mantendo o jar batch funcionando (dois artefatos: `portability-core` e `portability-app`) *(BOM gerenciado no parent; aplicação Spring Boot efetiva na Fase 2)*
- [x] Criar **CI** (GitHub Actions/GitLab CI): build + testes + `jacoco` (gate inicial de cobertura ≥70% no core) + `spotbugs`/Checkstyle
- [x] Extrair módulos Maven multi-módulo:
  - `core` (domain + service + ports — zero dependências de infra) → `portability-core`
  - `adapters` (DuckDB, JSON, CSV/XLSX) → `portability-adapters`
  - `batch` (CLI atual, thin) → `portability-batch`
  - `api` / `bff` (novos, Fase 2–3)
- [x] Cobrir com **testes de integração** (Testcontainers ou DuckDB em tmpfs): `DuckDbPortabilityRepository`, `CsvInputReader`, `ExcelInputReader`, `CsvOutputWriter` (hoje só unit tests com mocks) → `AdaptersIntegrationTest` (9 testes)
- [x] Extra: corrigir chave do `ProcessResult` (colisão de telefones formatados — antecipado da Fase 1) e atualizar Dockerfile/README para a estrutura multi-módulo

**Resultado:** `mvn clean verify` verde — 48 testes (34 core + 9 adapters + 5 batch), Checkstyle + JaCoCo executando no quality gate, fat-jar em `portability-batch/target/portabilidade-java.jar`.

### Fase 1 — Performance e correções do núcleo (~1–2 semanas) ✅ CONCLUÍDA
- [x] **Batch query eficiente no DuckDB**: `IN (?×N)` substituído por staging table + `COPY` CSV + `JOIN` em lotes de 50k → **21.324 ms → 1.195 ms (~17,8x)** para 100k números em base de 200k (`DuckDbBenchmarkTest`)
- [x] **Reuso de conexão**: `Connection` única por execução; repositório agora `AutoCloseable` (try-with-resources no batch); sem criação de índice no query path
- [x] **Paralelismo**: leitura Excel com virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) no SAX reader; isolamento de falha por arquivo no `run()`
- [x] **Streaming de I/O**: `InputFileReader` com iteração por chunks, `CsvOutputWriter` incremental — batch end-to-end de 50.137 linhas em ~1,5 s sem materializar o arquivo inteiro
- [x] **Chave do `ProcessResult`**: API posicional (`operatorAt(i)/sourceAt(i)`) sem colisão (concluído na Fase 0)
- [x] Benchmark antes/depois documentado em `docs/performance.md`

**Resultado:** `mvn clean verify` verde — 55 testes (34 core + 11 adapters/benchmark + ... incluindo batch), Checkstyle + JaCoCo ok, benchmark automatizado como gate de regressão de performance (<30s; atual ~1,2s).

### Fase 2 — API REST com arquitetura hexagonal (~2–3 semanas)
- [ ] Módulo `api` (Spring Boot 3.x, Java 21):
  - `POST /api/v1/portabilidade/consultar` — 1..N números (sync, limitado)
  - `POST /api/v1/portabilidade/batch` — upload multipart CSV/XLSX → retorna job ID
  - `GET  /api/v1/portabilidade/batch/{id}` — status/progresso
  - `GET  /api/v1/portabilidade/batch/{id}/resultado` — download
- [ ] Respeitar a hexagonal na prática:
  - `application/` = use cases (`ConsultPortabilityUseCase`, `SubmitBatchUseCase`) dependendo só de ports
  - `infrastructure/web` = controllers + DTOs/mappers; `infrastructure/persistence` = adapter DuckDB existente
  - Composição via `@Configuration` (elimina DI manual do `main`)
- [ ] Contratos: **OpenAPI 3** (`springdoc-openapi`) + versões de API (`/v1`), erros padronizados (**RFC 7807 ProblemDetails**)
- [ ] Validação de entrada (`jakarta.validation`), limites de payload, deduplicação de números na request
- [ ] **Cache**: resultados de portabilidade são relativamente estáveis → Caffeine (L1, hot numbers) com TTL configurável; avaliar Redis (L2) para múltiplas réplicas
- [ ] Testes: contrato (`@WebMvcTest`), integração com DuckDB embarcado, carga básica (k6/Gatling) — meta p95 < 50 ms single lookup com cache

### Fase 3 — BFF + Frontend Angular 17+ (~2–4 semanas)
- [ ] Módulo **`bff`** (Spring Boot dedicado ao frontend):
  - Endpoints orientados a tela: `GET /bff/v1/consulta` (resolve + formata p/ UI), `GET /bff/v1/dashboard/resumo` (agrega métricas das fontes), `GET /bff/v1/operadoras` (dicionário)
  - Adaptação de contratos, paginação, compressão, auth de usuário final (JWT/OAuth2 resource server)
  - BFF consome a API core — nunca o DuckDB diretamente
- [ ] Projeto **Angular 17+** (`frontend/`):
  - Standalone components, **signals**, new control flow (`@if/@for`), lazy loading por rota
  - Tela 1: consulta individual/lote (input colado + resultado com badge `fonte_consulta`)
  - Tela 2: upload de arquivo + progresso do batch (polling/SSE) + download do resultado
  - Tela 3: dashboard (taxa de acerto por fonte, top operadoras)
  - `HttpClient` tipado via client gerado do OpenAPI; interceptors (auth, erro); zoneless se possível
- [ ] E2E: Playwright nos fluxos críticos; unit: Vitest/Jest + testing-library

### Fase 4 — Escalabilidade e operação (~2 semanas)
- [ ] **Batch assíncrono durável**: fila (RabbitMQ/Kafka ou `JdbcClientPolling` simples) entre BFF/API e workers; horizontalizar workers de batch
- [ ] Base de portabilidade: versionar snapshots; pipeline de atualização periódica (ANS); considerar particionamento por DDD ou espelhar em Postgres com índices para servir online, mantendo DuckDB para analytics
- [ ] **Observabilidade**: Micrometer + Prometheus (histogramas de query, contadores por `fonte_consulta`), Loki/ELK para logs estruturados (JSON), tracing OpenTelemetry API→BFF→core
- [ ] Docker: imagens separadas (api, bff, worker, frontend nginx), compose com healthchecks; Kubernetes-ready (probes, HPA por CPU/QPS)
- [ ] Rate limiting (sentinel/Bucket4j) na API pública

### Fase 5 — Qualidade contínua
- [ ] Mutation testing (PIT) no módulo core
- [ ] Trunk-based + feature flags para rollout da API
- [ ] Runbook de operação + testes de carga agendados no CI (smoke k6)

---

## 4. Métricas de sucesso (Definition of Done do plano)

| Métrica | Hoje | Alvo |
|---------|------|------|
| Latência consulta 1 número (API) | n/a (só batch) | p95 < 50 ms (cache) / < 200 ms (DuckDB) |
| Throughput batch | serial, IN clause | ≥ 5× atual com JOIN + virtual threads |
| Arquivo de entrada máximo | limitado à RAM | streaming, 1 GB+ |
| Cobertura de testes core | não medida | ≥ 80% linha + integração |
| Disponibilidade da API | n/a | 99,5% com healthcheck/restart |
| Time-to-answer do cliente | horas (rodar jar) | segundos (UI/API) |

---

## 5. Riscos e decisões pendentes

1. **DuckDB em ambiente multi-réplica**: arquivo local não compartilha — decidir entre (a) réplica read-only da base por pod via init-container/object storage, (b) migrar serving layer para Postgres e manter DuckDB só no batch. Recomendo (a) primeiro (menor mudança), (b) quando houver tráfego online relevante.
2. **Tamanho da base (~1,6 GB)**: impacta cold start de containers — avaliar `PRAGMA` de memory limit e preload.
3. **Dados pessoais (LGPD)**: telefones são dados pessoais → a API deve registrar acesso, minimizar logs (mascarar dígitos) e definir retenção de arquivos de saída.
4. **Ordem de grandeza do time**: Fases 0–2 entregam a maior parte do valor (API + performance); Fase 3 depende de disponibilidade de front-end.

---

## 6. Backlog resumido (para tracker)

| # | Épico | Histórias principais | Fase |
|---|-------|----------------------|------|
| E1 | Toolchain Java 21 + Spring Boot | Multi-módulo Maven; CI com cobertura | 0 |
| E2 | Núcleo performático | Staging-table JOIN; virtual threads; streaming I/O; ProcessResult por linha | 1 |
| E3 | API REST hexagonal | Endpoints sync/batch; OpenAPI; RFC7807; cache Caffeine | 2 |
| E4 | BFF | Endpoints por tela; JWT; agregação de métricas | 3 |
| E5 | Angular 17+ | Consulta, Upload/batch progress, Dashboard; Playwright | 3 |
| E6 | Escala & Ops | Fila p/ batch; Prometheus/OTel; K8s; rate limit | 4 |
| E7 | Governança LGPD | Mascaramento em logs; retenção; auditoria | 4 |

Quer que eu comece por alguma fase? Sugiro executar a **Fase 0 + itens críticos da Fase 1** (migração Java 21, reestruturação multi-módulo e otimização das consultas DuckDB) como primeiro incremento, pois destrava todo o resto sem quebrar o batch existente.
