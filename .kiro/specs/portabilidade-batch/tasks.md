# Tasks — portabilidade-java

## Task 1: pom.xml e estrutura Maven
- [ ] Criar pom.xml com Java 17, dependências DuckDB, POI, OpenCSV, Jackson, Logback, JUnit 5
- [ ] Configurar maven-assembly-plugin para fat JAR

## Task 2: Domain layer
- [ ] PhoneNumber.java (record)
- [ ] OperatorInfo.java (record)
- [ ] PrefixMapping.java (POJO Jackson)
- [ ] PhoneNormalizationException.java
- [ ] DataLoadingException.java

## Task 3: Service layer — PhoneNormalizer
- [ ] Método normalize(String) → Long ou null
- [ ] Método getCleanDigits(String) → String 11 dígitos

## Task 4: Infrastructure — loaders
- [ ] CsvOperatorLoader: lê Operadoras.csv → Map<rn1, nome>
- [ ] JsonPrefixMapLoader: lê prefixos_operadora.json → Map<prefixo, nome>

## Task 5: Infrastructure — repository e resolver
- [ ] Interface PortabilityRepository
- [ ] DuckDbPortabilityRepository: conexão read-only, lote 5000, IN clause
- [ ] Interface PrefixResolver
- [ ] JsonPrefixResolver: fallback prefixo 7, 6, 5 dígitos

## Task 6: Infrastructure — file I/O
- [ ] Interface InputFileReader
- [ ] FileScanner: lista .csv e .xlsx em entrada/
- [ ] CsvInputReader: detecta separador, normaliza headers
- [ ] ExcelInputReader: lê .xlsx via POI, normaliza headers
- [ ] CsvOutputWriter: UTF-8 BOM, separador ;, nome com timestamp

## Task 7: Service — PortabilityResolutionService
- [ ] Método processNumbers(List<String>) → ProcessResult
- [ ] Integra PhoneNormalizer, PortabilityRepository, PrefixResolver

## Task 8: Application — AppPaths e PortabilityBatchProcessor
- [ ] AppPaths: resolve BASE_DIR (dir do JAR ou working dir)
- [ ] PortabilityBatchProcessor: cria pastas, orquestra processamento por arquivo

## Task 9: PortabilityApplication (main)
- [ ] Instanciação manual de todos os componentes (DI manual)
- [ ] Chamada ao PortabilityBatchProcessor.run()

## Task 10: application.properties e pastas raiz
- [ ] src/main/resources/application.properties (opcional)
- [ ] Garantir criação automática de entrada/, saida/
