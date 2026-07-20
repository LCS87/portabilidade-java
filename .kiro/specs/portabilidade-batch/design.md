# Design Técnico — portabilidade-java

## Visão Geral

Aplicação batch Java 17 que lê arquivos CSV/Excel de telefones, consulta um banco DuckDB de portabilidade numérica e um JSON de prefixos de operadora, e gera arquivos de saída enriquecidos com a operadora de cada número.

---

## High-Level Design

### Arquitetura Hexagonal + Camadas Clássicas

```
┌──────────────────────────────────────────────────────┐
│                  APPLICATION LAYER                   │
│         PortabilityBatchProcessor (main)             │
│                  AppPaths (config)                   │
└────────────────────┬─────────────────────────────────┘
                     │ orquestra
┌────────────────────▼─────────────────────────────────┐
│                   SERVICE LAYER                      │
│        PortabilityResolutionService                  │
│        PhoneNormalizer                               │
│  ┌─────────────────────────────────────────────┐     │
│  │  Ports (interfaces)                         │     │
│  │  PortabilityRepository  PrefixResolver      │     │
│  └─────────────────────────────────────────────┘     │
└────────────────────┬─────────────────────────────────┘
                     │ implementado por
┌────────────────────▼─────────────────────────────────┐
│               INFRASTRUCTURE LAYER                   │
│  DuckDbPortabilityRepository  JsonPrefixResolver     │
│  CsvOperatorLoader            JsonPrefixMapLoader    │
│  FileScanner  CsvInputReader  ExcelInputReader       │
│  CsvOutputWriter                                     │
└──────────────────────────────────────────────────────┘
                     │ opera sobre
┌────────────────────▼─────────────────────────────────┐
│               DOMAIN LAYER (Core)                    │
│  PhoneNumber  OperatorInfo  PrefixMapping            │
│  PhoneNormalizationException  DataLoadingException   │
└──────────────────────────────────────────────────────┘
```

### Fluxo de Processamento por Arquivo

```
entrada/*.csv|xlsx
      │
      ▼
FileScanner → lista arquivos
      │
      ▼ para cada arquivo
InputFileReader (CSV ou Excel)
      │ List<Map<String,String>>
      ▼
extrair coluna "telefone"
      │ List<String> rawNumbers
      ▼
PortabilityResolutionService.processNumbers()
      │
      ├─► PhoneNormalizer → Long ou null (mapeamento original→normalizado)
      │
      ├─► DuckDbPortabilityRepository.findOperatorsByPhoneNumbers()
      │      └─ lotes de 5000, IN clause → Map<Long, String> rn1
      │      └─ CsvOperatorLoader → Map<rn1, nome> → resolve nome
      │
      └─► JsonPrefixResolver (fallback p/ não encontrados)
             └─ testa prefixo 7, 6, 5 dígitos → nome operadora
      │
      ▼
Map<String,String> original → operadora
Map<String,String> original → fonte_consulta
      │
      ▼
CsvOutputWriter → saida/FINAL_yyyyMMdd_HHmm_{nome_original}.csv
```

---

## Low-Level Design

### Domain Models

**PhoneNumber.java**
```java
public record PhoneNumber(String raw, Long normalized) {
    public boolean isValid() { return normalized != null; }
}
```

**OperatorInfo.java**
```java
public record OperatorInfo(String rn1, String name) {}
```

**PrefixMapping.java** (deserialização do JSON)
```java
public class PrefixMapping {
    private String operadora;
    private String rn1;
    private int qtd;
    // getters/setters
}
```

### Interfaces (Ports)

**PortabilityRepository**
```java
public interface PortabilityRepository {
    Map<Long, String> findOperatorsByPhoneNumbers(List<Long> numbers);
}
```

**PrefixResolver**
```java
public interface PrefixResolver {
    String resolve(String cleanDigits); // retorna nome ou null
}
```

**InputFileReader**
```java
public interface InputFileReader {
    List<Map<String, String>> read(String filePath);
    boolean supports(String filePath);
}
```

### PhoneNormalizer — Regras de Negócio

```
rawInput → strip não-dígitos
  → se começa com "55" e len >= 12 → remove "55"
  → se len == 10 (DDD+8 dígitos) → insere "9" após DDD → 11 dígitos
  → se len == 11 → válido
  → caso contrário → null (inválido)
  → parseLong → Long
```

### DuckDbPortabilityRepository — Consulta em Lote

```java
// Conexão read-only
Connection conn = DriverManager.getConnection("jdbc:duckdb:" + dbPath + "?access_mode=read_only");

// Paginação interna por lote de 5000
for (List<Long> batch : partition(numbers, 5000)) {
    String placeholders = batch.stream().map(n -> "?").collect(joining(","));
    String sql = "SELECT telefone, rn1 FROM portabilidade WHERE telefone IN (" + placeholders + ")";
    PreparedStatement ps = conn.prepareStatement(sql);
    // bind params, executar, popular mapa resultado
}
```

### JsonPrefixResolver — Fallback

```java
// cleanDigits = 11 dígitos (ex: "11987654321")
// testa prefixos de comprimento 7, 6, 5
for (int len : new int[]{7, 6, 5}) {
    String prefix = cleanDigits.substring(0, len);
    if (prefixMap.containsKey(prefix)) return prefixMap.get(prefix); // nome operadora
}
return null; // nao_identificado
```

### PortabilityResolutionService.processNumbers()

```
Input:  List<String> rawNumbers
Output: ProcessResult { Map<String,String> operatorMap, Map<String,String> sourceMap }

1. normalizar cada número → Map<String, Long> originalToNormalized
2. coletar Long válidos → List<Long>
3. consultar DuckDB em lote → Map<Long, String> rn1Map
4. resolver nomes via operatorsDict (rn1 → nome) → Map<Long, String> phoneToName
5. para cada número original:
   a. se normalizado == null  → operadora="nao_identificado", fonte="invalido"
   b. se phoneToName contém   → operadora=nome, fonte="portabilidade"
   c. else PrefixResolver     → se achou: operadora=nome, fonte="prefixo"
   d. else                    → operadora="nao_identificado", fonte="nao_identificado"
```

### CsvOutputWriter — Saída

- Encoding: UTF-8 com BOM (`\uFEFF`)
- Separador: `;`
- Colunas: todas as originais + `operadora` + `fonte_consulta`
- Filename: `FINAL_yyyyMMdd_HHmm_{nomeArquivoOriginalSemExtensao}.csv`

---

## Estrutura de Pacotes

```
com.portabilidade/
├── PortabilityApplication.java
├── domain/
│   ├── model/
│   │   ├── PhoneNumber.java
│   │   ├── OperatorInfo.java
│   │   └── PrefixMapping.java
│   └── exception/
│       ├── PhoneNormalizationException.java
│       └── DataLoadingException.java
├── application/
│   ├── PortabilityBatchProcessor.java
│   └── config/
│       └── AppPaths.java
├── service/
│   ├── PortabilityResolutionService.java
│   ├── PhoneNormalizer.java
│   └── resolver/
│       ├── PortabilityRepository.java  (interface/port)
│       └── PrefixResolver.java         (interface/port)
└── infrastructure/
    ├── repository/
    │   └── DuckDbPortabilityRepository.java
    ├── resolver/
    │   └── JsonPrefixResolver.java
    ├── loader/
    │   ├── CsvOperatorLoader.java
    │   └── JsonPrefixMapLoader.java
    ├── file/
    │   ├── FileScanner.java
    │   ├── InputFileReader.java        (interface)
    │   ├── CsvInputReader.java
    │   └── ExcelInputReader.java
    └── output/
        └── CsvOutputWriter.java
```

## Stack e Dependências Maven

| Biblioteca | Versão | Finalidade |
|---|---|---|
| DuckDB JDBC | 1.1.3 | Consulta SQL no .duckdb |
| Apache POI + OOXML | 5.3.0 | Leitura .xlsx |
| OpenCSV | 5.9 | CSV com delimitador `;` |
| Jackson Databind | 2.18.0 | Parse JSON prefixos |
| SLF4J + Logback | 2.0.13 | Logging |
| JUnit 5 | 5.11.0 | Testes unitários |

## Empacotamento

Fat JAR via `maven-assembly-plugin` com `<mainClass>com.portabilidade.PortabilityApplication</mainClass>`.  
Execução: `java -jar portabilidade-java.jar` a partir da pasta raiz onde estão `entrada/`, `saida/`, `Operadoras.csv`, `prefixos_operadora.json` e `base_portabilidade/portabilidade.duckdb`.
