# Desempenho — Fase 1 (Otimização do núcleo)

Data: 2026-10-31 · Ambiente: container Linux, JDK 21 (Temurin), DuckDB JDBC embutido (tmpfs/local SSD).

## Problema original
A consulta de portabilidade usava `SELECT ... WHERE telefone IN (?, ?, ... ×N)` com até centenas de
milhares de parâmetros via JDBC, gerando parsing/plan explosivo e round-trips por parâmetro.
Medição baseline (legado): **21.324 ms** para resolver 100k números em base de 200k linhas.

## Solução implementada
1. **Staging table + COPY**: os números do lote são gravados em CSV temporário e carregados com
   `COPY ... INTO tmp_staging`; a resolução é feita com um único `JOIN` no DuckDB
   (`DuckDbPortabilityRepository.findOperatorsByPhoneNumbers`).
2. **Conexão única por execução**: o repositório abre uma conexão e reutiliza entre lotes
   (agora `AutoCloseable`, fechado via try-with-resources pelo batch).
3. **Lotes de até 50.000 números** (`QUERY_BATCH_SIZE`) para limitar memória da staging table.
4. **Streaming ponta a ponta**:
   - `CsvInputReader` / `ExcelInputReader` (SAX + POI event mode) produzem linhas sob demanda;
   - `CsvOutputWriter` escreve em chunks incrementais — nada é materializado integralmente;
   - `PortabilityBatchProcessor.processFile` consome em chunks de 50k linhas.
5. **Isolamento de falhas por arquivo**: um `.xlsx` corrompido não derruba o restante do lote.

## Resultados medidos (mvn verify — testes automatizados)

| Cenário | Antes (IN ?×N) | Depois (staging+COPY) | Ganho |
|---|---|---|---|
| 100k números vs base 200k linhas | 21.324 ms | **1.195 ms** | **~17,8x** |
| Batch end-to-end 50.137 linhas (CSV, leitura+consulta+escrita) | OOM/lentidão em heap padrão | **1.515 ms** | streaming ok |

Benchmark reproduzível: `portability-adapters/src/test/java/.../DuckDbBenchmarkTest.java`
(gate de CI: < 30s; valor atual: ~1,2s).

## Observações
- Os 1.195 ms incluem abertura da conexão DuckDB + COPY do CSV de staging + JOIN + fetch dos 100k resultados.
- PRAGMA `threads=4` limita paralelismo do DuckDB em CI; em produção pode ser ajustado via config.
- Próximo passo (Fase 2): expor essa capacidade via API REST com paginação/cursor para grandes volumes.
