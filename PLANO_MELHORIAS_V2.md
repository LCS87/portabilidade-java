# Plano de Melhoria — Portabilidade Numérica (v2)

**Data:** 2026-10-31
**Baseline analisado:** `a53489c`
**Substitui:** `PLANO_MELHORIAS.md` (mantido como histórico; Fases 3 e 4 já foram parcialmente implementadas)
**Escopo:** código, build, testes, contratos API/BFF/UI, segurança, operação e dados.

---

## 1. Resumo executivo

O projeto evoluiu de um batch CLI para uma plataforma com **6 módulos Maven**, API REST, BFF reativo e frontend Angular 20. A base hexagonal é boa e as otimizações de performance da Fase 1 são reais (staging + `COPY` + streaming). Porém, **a cadeia de entrega não está confiável**:

| Situação | Evidência |
|---|---|
| ❌ `mvn clean verify` falha | 3 testes falham (1 erro em adapters + 2 falhas na API) |
| ❌ A API não inicia | `MetricsConfig` cria `TimedAspect` sem AspectJ no classpath (`NoClassDefFoundError`) |
| ❌ As imagens Docker não constroem | POMs dos módulos `api`/`bff` não são copiados nos Dockerfiles |
| ❌ O batch da UI não funciona | UI espera `jobId`/`processados`; BFF devolve `id`/`linhasProcessadas` |
| ❌ A feature flag de fila quebra o boot | Dois beans `BatchExecutor`; `NoUniqueBeanDefinitionException` |
| ❌ Não há autenticação efetiva | BFF libera todo `/bff/**`; API não tem Spring Security |
| ⚠️ Deploy K8s incompleto | Sem volume/PVC da base DuckDB, sem BFF/UI, 2 réplicas sobre arquivo local |
| ✅ Frontend compila | `npm run build` OK (269,71 kB inicial, lazy chunks) |

**Conclusão:** antes de novas features, é preciso uma fase curta de “destravar a entrega” (build, boot, Docker, contrato do batch). Depois, segurança/correção e, por fim, escala/operação. O núcleo de domínio e a arquitetura hexagonal devem ser preservados — o problema não é a base, é o acabamento de produção.

---

## 2. O sistema hoje

### 2.1 Módulos e fluxo

```
Angular 20 (portabilidade-ui)
        │ HTTP /bff/v1
        ▼
BFF WebFlux (portability-bff) ──HTTP──► API REST (portability-api)
                                              │ ports
                                              ▼
                         portability-core (domínio + ports + serviços)
                                              │ adapters
                                              ▼
              portability-adapters (DuckDB, CSV/XLSX, JSON, output)
                                              ▲
                                              │
                              portability-batch (CLI / fat jar)
```

- **Core:** `PhoneNormalizer`, `PortabilityResolutionService`, `ProcessResult`, ports `PortabilityRepository`/`PrefixResolver`.
- **Adapters:** DuckDB (staging + `COPY`), leitores CSV/XLSX em streaming, loader de operadoras/prefixos, writer CSV.
- **API:** Spring Boot 3.3 / Java 21, consulta síncrona, batch multipart assíncrono, Caffeine, OpenAPI, ProblemDetails, RabbitMQ opcional, Actuator/Prometheus/OTel.
- **BFF:** WebFlux, endpoints por tela (`consulta`, `batch`, `dashboard`, `operadoras`), SSE/polling, CORS, Security “JWT-ready”.
- **UI:** Angular 20 standalone + signals, rotas lazy, três telas (consulta, lote, dashboard).
- **Infra:** Dockerfiles (batch/API), compose (batch/API), manifests K8s (API/BFF/RabbitMQ).

### 2.2 Evidências levantadas

| Verificação | Resultado |
|---|---|
| `mvn -B -ntp clean verify` | **FAILURE** em `portability-adapters` (1 erro) |
| `mvn -B -ntp -pl portability-api test` | **23 testes, 2 falhas** |
| `mvn -B -ntp -pl portability-batch test` | 6 testes, sucesso |
| `mvn -B -ntp -pl portability-bff -am test` | 12 testes, sucesso |
| `java -jar portability-api-*-boot.jar` | **APPLICATION FAILED TO START** (`org/aspectj/lang/ProceedingJoinPoint`) |
| `java -jar ... --app.batch.queue.enabled=true` | **APPLICATION FAILED TO START** (2 beans `BatchExecutor`) |
| Simulação do `Dockerfile`/`Dockerfile-api` | Maven: “Child module ... portability-api/portability-bff does not exist” |
| `npm ci && npm run build` (UI) | **SUCCESS** |
| `git status` | `base_portabilidade/` (~4,7 GB) e `portabilidade-java.jar` (96 MB) não rastreados |

> Contagem atual: **86 execuções de teste** (34 core + 11 adapters + 6 batch + 23 API + 12 BFF). O README ainda fala em 20 testes.

---

## 3. Diagnóstico priorizado

### P0 — Bloqueadores de entrega

| ID | Achado | Evidência | Impacto |
|---|---|---|---|
| **P0-1** | Build/testes vermelhos | `AdaptersIntegrationTest` cria `DuckDbPortabilityRepository` sem `close()` (linhas 55–57); `BatchExecutionServiceTest` usa dicionário invertido (linhas 57–58, 90) e testa header `numero` como se não fosse alias (linhas 98–107) | CI vermelho, release bloqueado |
| **P0-2** | API não sobe | `MetricsConfig` (linhas 15–17) instancia `TimedAspect`; `portability-api/pom.xml` não tem `spring-boot-starter-aop`/`aspectjweaver` | Nenhum endpoint funciona; imagem API inútil |
| **P0-3** | Dockerfiles quebrados | `pom.xml` lista 5 módulos; `Dockerfile` copia 3 POMs (linhas 6–9) e `Dockerfile-api` copia 4 (linhas 7–11) | `docker build` falha para batch e API |
| **P0-4** | Fila quebra o boot | `InMemoryBatchJobStore` sempre implementa `BatchExecutor` (linhas 26–28); `QueuedBatchExecutor` também (linhas 18–20); `SubmitBatchUseCase` injeta um único bean (linhas 32–35) | `FEATURE_BATCH_QUEUE=true` derruba a API |
| **P0-5** | Batch da UI quebrado | UI lê `r.jobId` (`lote.ts` 61–65; `bff.service.ts` 54–58), mas a API devolve `id` (`PortabilityBatchController` 71–77) e o BFF repassa o mapa cru (`BatchController` 33–37). UI lê `processados/total/erros` (`lote.ts` 49–51), API devolve `linhasProcessadas/totalLinhas/erro` (`BatchStatusResponse` 6–14) | Upload, polling e download não funcionam |
| **P0-6** | Repositório em risco | `base_portabilidade/` (~4,7 GB, inclui `portabilidade.db` e `portabilidade.duckdb`) e jar de 96 MB aparecem como untracked; `.gitignore` só ignora `*.duckdb` | Risco de commit/push acidental de gigabytes |
| **P0-7** | Deploy K8s incompleto | `k8s/api-deployment.yaml` não monta a base DuckDB (linhas 20–33); 2 réplicas usam arquivo local; `FEATURE_BATCH_QUEUE=false` (linhas 27–28); não há Deployment da UI; não há Ingress/Secrets/NetworkPolicy | Pods sobem sem dados ou não escalam |

### P1 — Antes de produção

| ID | Achado | Evidência | Impacto |
|---|---|---|---|
| **P1-1** | Autenticação inexistente na prática | BFF permite `/bff/**` sem JWT (`SecurityConfig` 23–26); API não tem Spring Security; BFF não propaga token para a API | Qualquer cliente consulta telefones (PII) |
| **P1-2** | CSV de saída malformado | `CsvOutputWriter.escapeField` usa a constante `;` (linhas 186–190), mas a API escreve com `,` (`BatchExecutionService` 73–74) | Campos com vírgula quebram o CSV; sem proteção contra CSV injection (`=`, `+`, `-`, `@`) |
| **P1-3** | Parser CSV não é RFC 4180 | `CsvInputReader.splitLine` faz `split` simples (154–157); `normalizeHeaders` usa `contains(alias)` (143–147); headers duplicados colidem no `LinkedHashMap` | Dados com aspas/`;`/quebra de linha ou colunas tipo `numero_pedido` são lidos errado/perdidos |
| **P1-4** | Charset detectado só no header | `CsvInputReader.stream` escolhe charset lendo apenas a primeira linha (57–74); se o header é ASCII e o corpo é Latin-1, falha no meio sem fallback | Arquivos ISO-8859-1 podem falhar ou corromper acentos |
| **P1-5** | Normalização/domínio incompletos | Sem validação de DDD; `0800` perde o zero ao virar `Long` (`PhoneNormalizer` 33–41); fallback 11d↔10d (`DuckDbPortabilityRepository` 102–122; `JsonPrefixResolver` 40–43) pode gerar falso positivo fixo↔celular | Taxa de acerto “100%” pode incluir erros; números de serviço não resolvem |
| **P1-6** | `null` na lista gera 500 | `ConsultRequest` só valida lista (12–15); `ProcessResult` faz `List.copyOf` (29–37), que lança NPE com elemento nulo | `{"numeros":[null]}` → 500 em vez de 400 |
| **P1-7** | Dicionário ausente degrada silenciosamente | `CsvOperatorLoader` retorna vazio (51–52); `PortabilityResolutionService` usa o RN1 como nome (76) | Saída mostra código numérico como “operadora” |
| **P1-8** | LGPD invertida | `ConsultaService.mask` mascara o nome da operadora (70–75), mas devolve o telefone completo (50–58); UI exibe `numero` cru e grava em `localStorage` (`consulta.ts` 95–102) | Exposição de PII; mascaramento no campo errado |
| **P1-9** | Vazamento de recursos | `ExcelInputReader` inicia virtual thread que pode ficar bloqueada se o consumidor abandonar o iterator (94–111, 176–185); `BatchExecutionService` retorna cedo sem fechar o reader (82–96); jobs/CSVs nunca expiram (`InMemoryBatchJobStore` 30) | Threads, file handles, memória e disco crescendo |
| **P1-10** | BFF carrega tudo em memória | `BatchController.readAll` concatena `byte[]` em O(n²) (60–75); `WebApiClient.batchResultCsv` devolve `byte[]` (67–72), com limite de 64 MB | Upload/download de lote grande causa cópia excessiva/OOM |
| **P1-11** | Status do batch lento | `BatchService.monitor` aplica `delayElement(2s)` antes da primeira emissão (33–39); o endpoint de status usa `take(1)` (`BatchController` 46–49) | Todo `GET /batch/{id}` leva ≥ 2 s |
| **P1-12** | Cache com negativo de 6 h | `CachedPortabilityRepository` grava “miss” com TTL de 360 min (19–21, 44–57); sem versão da base/invalidação; métricas de cache não registradas | Atualização da base não aparece; batch pode expulsar cache online |
| **P1-13** | Erros vazam detalhe interno | `server.error.include-message: always` (`application.yml` 7–8); handlers devolvem mensagens de exceção | Caminho do DuckDB, detalhes internos e stack em produção |
| **P1-14** | Observabilidade insuficiente | Só há `portability_consult_seconds` e contador de linhas; sem hit ratio, latência DuckDB, duração/falha de job, fila; sem logs JSON/correlation ID; health não valida a base | Difícil operar/SLA |
| **P1-15** | Documentação desatualizada | README diz máximo 1000 números (linha 366) mas o código limita a 100 (`ConsultPortabilityUseCase` 22); diz 20 testes (323–325); `PLANO_MELHORIAS` marca Fase 3/4 como pendentes; `AppPaths` tem caminho fixo `D:\ConsultaOperadoraAPI` (69–72) | Onboarding e operação induzidos a erro |

### P2 — Qualidade, escala e produto

| ID | Achado | Evidência | Impacto |
|---|---|---|---|
| **P2-1** | DuckDB serializa todas as consultas | `DuckDbPortabilityRepository` usa `synchronized` na conexão e no método (56–71, 87–88); flag `writable` não abre `read_only` | Virtual threads não ajudam; p95 baixo com concorrência |
| **P2-2** | Modelo de serving do DuckDB não decidido | 2 réplicas + arquivo local (K8s) não é suportado; sem snapshot/versão/checksum da base | Escala horizontal e atualização da base indefinidas |
| **P2-3** | Fronteira hexagonal furada | `SubmitBatchUseCase` importa `BatchJob` de `infrastructure.batch` (linha 3) | Caso de uso depende de detalhe de infraestrutura |
| **P2-4** | Contratos como `Map<String,Object>` | `ApiClientPort` (14–23), `BatchController`, `PortabilityBatchController` devolvem mapas | Drift de contrato (origem do bug P0-5); sem cliente gerado |
| **P2-5** | Qualidade não bloqueia | Checkstyle com `failOnViolation=false` (`pom.xml` 190); JaCoCo `check` só no core (70%); sem SpotBugs/PMD/OWASP/Trivy/CodeQL | Regressões entram verdes |
| **P2-6** | CI cobre só Maven | `.github/workflows/ci.yml` roda `mvn verify` e publica artefatos (22–37); não compila UI, não testa UI, não constrói Docker, sem E2E/carga/scan | Falhas como P0-2/P0-3 passam no CI |
| **P2-7** | Frontend sem entrega | Sem `Dockerfile`/nginx, sem `environment.prod.ts`, sem interceptor de auth/erro, sem lint/E2E; só 2 specs; ainda usa `zone.js` | Não há caminho de deploy do frontend; pouca cobertura |
| **P2-8** | Dashboard não é operacional | BFF agrega apenas o array enviado pelo browser (`DashboardService` 19–51); UI usa `localStorage` (`consulta.ts` 95–102) | Métricas manipuláveis e sem visão real de produção |
| **P2-9** | Benchmark não representa produção | `docs/performance.md` usa base sintética de 200k; meta de 100k em <30 s é frouxa (local ~250 ms); sem p95/p99/concorrência | Não prevê comportamento com 55,9 M e múltiplos usuários |
| **P2-10** | Sem versionamento/provisionamento da base | `DuckDbDatabaseProvisioner` existe, mas não há pipeline, checksum, data da ANS, rollback | Atualização da base é manual e arriscada |
| **P2-11** | Sem idempotência/retry/DLQ no batch | Mensagem carrega caminho local (`BatchJobMessage`); job store em memória por instância; sem retry/DLQ | Réplicas não compartilham estado; falha de worker perde job |
| **P2-12** | Segurança de planilhas | `SAXParserFactory` sem features contra XXE/entity expansion (`ExcelInputReader` 97–103); sem limite de zip bomb | Upload malicioso pode exaurir CPU/memória ou ler arquivos locais |

---

## 4. Plano de ação

### Fase 0 — “Verde e deployável” (4–6 dias-dev)

**Objetivo:** `mvn clean verify` verde, API/BFF sobem, imagens constroem, batch da UI funciona de ponta a ponta.

| # | Ação | Critério de aceite | Esforço |
|---|---|---|---|
| 0.1 | Corrigir os 3 testes e adicionar regressões | `mvn clean verify` verde em Windows e Linux | 0,5 d |
| 0.2 | Adicionar `spring-boot-starter-aop` **ou** trocar `@Timed` por `Observation`/`Timer` manual; criar `@SpringBootTest` de smoke que sobe o contexto | `java -jar ...-boot.jar` inicia e `/actuator/health` responde 200 | 0,5 d |
| 0.3 | Corrigir Dockerfiles (copiar todos os POMs) e criar `Dockerfile-bff` + `Dockerfile-ui` (nginx); completar `docker-compose` com bff/ui/rabbitmq | `docker compose build` verde para todos os serviços | 1 d |
| 0.4 | Tipar o contrato do batch no BFF e alinhar a UI (`jobId`, `status`, `processados`, `total`, `erros`, `progressoPct`) | Teste de contrato BFF↔API + E2E de upload/polling/download | 1 d |
| 0.5 | Resolver o conflito de `BatchExecutor` (qualificador/`@Primary`/`@ConditionalOnMissingBean`) e testar com a flag ligada | API sobe com `FEATURE_BATCH_QUEUE=true` e `false` | 0,5 d |
| 0.6 | Higiene do repositório: ignorar `base_portabilidade/`, `*.db`, `*.db-wal`, `*.db-shm`, `/portabilidade-java.jar`; remover caminho `D:\...`; atualizar README/plano | `git status` limpo; nenhum arquivo > 100 MB untracked | 0,5 d |
| 0.7 | CI mínimo: `mvn verify`, build da UI, `docker build` (sem push), smoke de boot | Pipeline verde e < 10 min | 1 d |

**Definição de pronto:** um novo dev clona, roda `mvn clean verify`, `docker compose build`, sobe API/BFF/UI e processa um lote pela tela sem intervenção manual.

---

### Fase 1 — “Confiabilidade e segurança” (10–15 dias-dev)

| # | Ação | Critério de aceite | Esforço |
|---|---|---|---|
| 1.1 | **AuthN/AuthZ**: BFF como OAuth2 resource server (JWT), API com autenticação de serviço (mTLS/API key), remover `permitAll`, papéis, headers de segurança, rate limit (Bucket4j), Swagger/Actuator protegidos em prod | Sem token → 401; token sem papel → 403; teste de segurança no CI | 3 d |
| 1.2 | **Validação/erros**: `List<@NotBlank String>`, limite de payload/body, handler genérico 500, correlation ID, ProblemDetails também no BFF, mensagens sanitizadas | `null`, vazio, >100 e payload abusivo retornam 400/413 sem stack | 1,5 d |
| 1.3 | **CSV correto**: escaping com separador de instância, parser RFC 4180 (OpenCSV), aliases exatos, headers duplicados, charset robusto, proteção contra CSV injection | Testes com aspas, `;`, `,`, quebra de linha, acentos e fórmulas | 2 d |
| 1.4 | **Ciclo de vida**: readers `AutoCloseable`/`Stream` com `onClose`, cancelamento do parser Excel, `try-with-resources` em todos os caminhos; TTL/limpeza de jobs e CSVs; `DELETE /batch/{id}`; shutdown gracioso | Teste de arquivo abandonado não deixa thread/handle; job expira e arquivo é removido | 2 d |
| 1.5 | **Domínio de telefone**: validação de DDD, `0800`/serviços, representação canônica em `String`, tratamento de `null`, revisão do fallback 11↔10 com confiança/tipo | Testes de 0800, DDD inválido, fixo/celular ambíguo; taxa de falso positivo medida | 2 d |
| 1.6 | **LGPD**: mascarar telefone (não operadora), remover PII do `localStorage`, retenção de uploads/resultados, auditoria de acesso, logs sem PII | Revisão de logs/UI; nenhum telefone completo em localStorage/log | 1 d |
| 1.7 | **Observabilidade base**: health da base DuckDB, métricas por fonte, cache hit ratio, duração/falha de job, fila; logs JSON com correlation ID | Dashboard Prometheus com os painéis mínimos; health falha se base ausente | 2 d |

---

### Fase 2 — “Escala e operação” (15–20 dias-dev)

| # | Ação | Critério de aceite | Esforço |
|---|---|---|---|
| 2.1 | **Decisão do serving DuckDB**: (a) snapshot read-only por pod via initContainer/object storage; (b) Postgres como serving layer e DuckDB só no batch. Recomendo (a) primeiro | ADR aprovado; teste com 2+ réplicas sem lock | 2 d |
| 2.2 | **Concorrência**: remover `synchronized` global, abrir `read_only`, estratégia de conexão/pool, staging sem CSV temporário quando possível | p95 de consulta concorrente dentro da meta; benchmark antes/depois | 3 d |
| 2.3 | **Fila durável**: job store compartilhado (Redis/Postgres), arquivos em S3/MinIO, idempotência, retry/backoff, DLQ, cancelamento, workers escaláveis | Matar worker no meio → job reprocessa/retoma; status visível em qualquer réplica | 4 d |
| 2.4 | **K8s completo**: PVC/initContainer da base, Secrets/ConfigMaps, Deployments BFF/UI, Ingress, HPA/PDB, securityContext, probes, NetworkPolicy, imagens por digest | `kubectl apply` sobe o stack; rollout e rollback testados | 3 d |
| 2.5 | **Versionamento da base**: metadados (`base_version`, data ANS, checksum), pipeline de atualização, invalidação de cache, runbook e rollback | Atualização sem downtime; `/info` mostra versão; rollback < 15 min | 2 d |
| 2.6 | **Performance realista**: k6/JMH com base de 55,9 M, p95/p99, memória, throughput de batch; gate de regressão no CI | Relatório com SLOs e comparativo antes/depois | 3 d |

---

### Fase 3 — “Qualidade contínua e produto” (10–15 dias-dev)

| # | Ação | Critério de aceite | Esforço |
|---|---|---|---|
| 3.1 | **Contract-first**: OpenAPI como fonte, geração de cliente TS e DTOs Java, testes de contrato (Pact/Spring Cloud Contract), eliminar `Map<String,Object>` | UI compila contra cliente gerado; contrato quebra no CI | 3 d |
| 3.2 | **Pirâmide de testes**: `@SpringBootTest`, WireMock BFF↔API, Testcontainers/DuckDB real, Playwright E2E, PIT no core; gates de cobertura (core 85%, adapters 75%, api/bff 70%, UI 60%) | Cobertura medida e bloqueante; E2E dos 3 fluxos no CI | 4 d |
| 3.3 | **Frontend de produto**: paginação/virtual scroll, cancelar/retry, progresso/erro, a11y, i18n, `environment.prod`, interceptor de auth/erro, validação de arquivo, dashboard server-side | Lighthouse/a11y mínimos; fluxo completo sem localStorage de PII | 3 d |
| 3.4 | **Segurança/qualidade contínua**: SpotBugs/PMD, OWASP dependency-check, Trivy, CodeQL, SBOM, Dependabot, licenças | Pipeline bloqueia CVE crítica e violação de estilo | 2 d |
| 3.5 | **Documentação operacional**: ADRs, guia da API, runbook, LGPD/DPIA, disaster recovery, plano de capacidade | Runbook validado em simulação de incidente | 2 d |

---

## 5. Backlog consolidado (ordem sugerida)

| Ordem | Item | Fase | Prioridade | Depende de |
|---|---|---|---|---|
| 1 | Corrigir testes e build | 0 | P0 | — |
| 2 | Corrigir boot da API (AspectJ/`@Timed`) | 0 | P0 | — |
| 3 | Corrigir Dockerfiles + BFF/UI | 0 | P0 | — |
| 4 | Contrato do batch BFF↔UI | 0 | P0 | — |
| 5 | Conflito de beans da fila | 0 | P0 | — |
| 6 | Higiene do repositório/.gitignore | 0 | P0 | — |
| 7 | CI com UI + Docker + smoke | 0 | P0 | 1–6 |
| 8 | AuthN/AuthZ + rate limit | 1 | P1 | 7 |
| 9 | CSV correto (entrada/saída) | 1 | P1 | 7 |
| 10 | Validação/erros/correlation ID | 1 | P1 | 8 |
| 11 | Ciclo de vida de readers/jobs | 1 | P1 | 7 |
| 12 | Domínio de telefone (DDD/0800/fallback) | 1 | P1 | 9 |
| 13 | LGPD (mascaramento/retenção) | 1 | P1 | 10 |
| 14 | Observabilidade base | 1 | P1 | 10 |
| 15 | Serving DuckDB (snapshot/Postgres) | 2 | P2 | 14 |
| 16 | Concorrência DuckDB | 2 | P2 | 15 |
| 17 | Fila durável + object storage | 2 | P2 | 15 |
| 18 | K8s completo | 2 | P2 | 15, 17 |
| 19 | Versionamento da base | 2 | P2 | 15 |
| 20 | Performance realista | 2 | P2 | 16 |
| 21 | Contract-first/geração de clientes | 3 | P2 | 8, 9 |
| 22 | Testes/E2E/mutation/cobertura | 3 | P2 | 21 |
| 23 | Frontend de produto | 3 | P2 | 21 |
| 24 | Scans de segurança/SBOM | 3 | P2 | 7 |
| 25 | Runbooks/ADRs/LGPD/DR | 3 | P2 | 18, 19 |

---

## 6. Métricas de sucesso (Definition of Done)

| Métrica | Hoje | Alvo |
|---|---|---|
| `mvn clean verify` | vermelho (3 falhas) | verde em Windows/Linux |
| Boot da API | falha | smoke test no CI |
| Build Docker (batch/API/BFF/UI) | falha/inexistente | todos verdes no CI |
| Fluxo de lote na UI | quebrado | E2E upload → progresso → download |
| Latência consulta (API) | não medida | p95 < 50 ms (cache) / < 200 ms (DuckDB) |
| Batch 100k | ~250 ms local (sintético) | < 2 s em base realista, com p95 |
| Disponibilidade | não medida | 99,5% |
| Jobs de batch com sucesso | não medido | > 99% |
| Cobertura | só core ≥ 70% | core 85%, adapters 75%, api/bff 70%, UI 60% |
| Segurança | sem auth | 401/403 testados; 0 CVE crítica |
| LGPD | telefone completo em UI/localStorage | PII mascarada, retenção e auditoria |
| Atualização da base | manual | < 1 h, com rollback < 15 min |

---

## 7. Decisões pendentes e riscos

1. **Serving do DuckDB (maior decisão):** arquivo local não escala para 2+ réplicas. Recomendo começar com **snapshot read-only por pod** (initContainer/object storage) e reavaliar **Postgres** quando houver tráfego online relevante. Não migrar os dois ao mesmo tempo.
2. **Provedor de identidade:** Keycloak interno, Azure AD, Auth0 ou JWT próprio? Isso define o esforço da Fase 1.1.
3. **Fila vs polling:** RabbitMQ já existe, mas o job store é em memória. Sem estado compartilhado + object storage, a fila não entrega durabilidade real.
4. **Base de 55,9 M / ~1,6–2,8 GB:** cold start, memória e lock do DuckDB impactam o desenho de pods. Medir antes de definir requests/limits.
5. **LGPD:** telefone é dado pessoal; definir base legal, retenção, mascaramento em logs, auditoria e DPIA antes de expor a API externamente.
6. **Capacidade do time:** Fases 0–1 entregam a maior parte do valor. Fases 2–3 podem ser paralelizadas com frontend/ops, mas a Fase 0 é pré-requisito para tudo.

---

## 8. Próximo passo recomendado

**Começar pela Fase 0 (4–6 dias-dev)**, nesta ordem:

1. Corrigir os 3 testes e o boot da API (P0-1/P0-2) — devolve um build confiável.
2. Corrigir Dockerfiles e adicionar BFF/UI (P0-3) — devolve deployabilidade.
3. Alinhar contrato do batch e resolver o bean da fila (P0-4/P0-5) — devolve o fluxo principal.
4. Higiene do repositório + CI mínimo (P0-6/P0-7) — evita regressão.

Ao final da Fase 0, o sistema estará **verde, buildável, executável e demonstrável de ponta a ponta**. Só então vale investir em auth, CSV/domínio e escala.
