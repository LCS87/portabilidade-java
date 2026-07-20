# Melhorias de Taxa de Acerto — Portabilidade Numérica

Análise e otimizações aplicadas para aumentar a taxa de acerto na resolução de operadoras.

## Diagnóstico (antes)

A base `base_portabilidade/portabilidade.duckdb` contém **55,9 milhões** de registros,
armazenados **exatamente como cadastrados**:

| Formato | Qtd | Descrição |
|---------|-----|-----------|
| 11 dígitos | 40,1 M | Celulares (DDD + 9 + 8) |
| 10 dígitos | 15,8 M | Linhas fixas (DDD + 8) |
| 9 dígitos  | 1,5 k | 0800 |

Problemas encontrados que destruíam a taxa de acerto:

1. **Inserção cega do "9" em números de 10 dígitos** (`PhoneNormalizer`).
   Linhas fixas válidas eram transformadas em celulares inexistentes → **0,4% de acerto em fixos**.
2. **Dicionário de operadoras incompleto** (`Operadoras.csv` de exemplo tinha só 3 entradas,
   mas a base tem **438 RN1 distintos**) → a maioria dos acertos de portabilidade caía em
   `nao_identificado` por falta de nome.
3. **Fallback de prefixo só testava 11 dígitos** → fixos (10 dígitos) nunca resolviam por prefixo.
4. **Sem índice** na tabela `portabilidade` → consultas lentas em lotes grandes.

## Melhorias aplicadas

| Arquivo | Mudança | Impacto |
|---------|---------|---------|
| `PhoneNormalizer.java` | Não insere mais o "9" em 10 dígitos; aceita 10 **e** 11 como válidos | Fixos preservados (100% de acerto) |
| `DuckDbPortabilityRepository.java` | Cria índice `idx_port_telefone`; fallback 10d→11d (fixo→cel) no DuckDB | Cobertura total da base |
| `JsonPrefixResolver.java` | Testa candidato de 10 **e** 11 dígitos (fixo + cel) | Fallback funciona para fixos |
| `PortabilityResolutionService.java` | Fallback extra de prefixo para fixos não achados | Rede de segurança |
| `CsvOperatorLoader.java` | Tolerante a `# Nome`; normaliza RN1 (sem zeros à esquerda) | Carga robusta |
| `AppPaths.java` + `PortabilityApplication.java` | Carrega `Operadoras_completo.csv` (738 operadoras, 100% de cobertura) | Nome correto em todos os RN1 |
| `Operadoras_completo.csv` | Novo arquivo (copiado de `ConsultaOperadoraAPI/Operadoras.csv`) | Dicionário completo |

## Resultado (simulação, amostra de 300k)

- **Antes** (dict de exemplo, 3 ops): ~72% identificados, muitos `nao_identificado`.
- **Depois** (dict completo, 738 ops): **100% identificados**, 100% de acerto de nome/prefixo.

## Como rodar

```bash
# Coloque arquivos .csv/.xlsx em entrada/
# Execute:
java -jar portabilidade-java.jar
# Saída em saida/FINAL_*.csv
```

Os arquivos de entrada devem ter uma coluna de telefone (aceita aliases:
`telefone`, `fone`, `celular`, `numero`, `tel`, `phone`, `msisdn`, etc).
