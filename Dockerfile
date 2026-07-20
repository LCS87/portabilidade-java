# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copia apenas os arquivos de dependência primeiro para aproveitar o cache do Docker
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

# Copia o código-fonte e compila o fat-jar
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copia o jar gerado
COPY --from=build /build/target/portabilidade-java.jar /app/portabilidade-java.jar

# Copia os dados auxiliares (dicionários, prefixos).
# A base DuckDB (base_portabilidade/, ~1,6 GB) NÃO é copiada para a imagem:
# ela é montada como volume (veja docker-compose.yml ou -v abaixo).
COPY Operadoras.csv /app/Operadoras.csv
COPY Operadoras_completo.csv /app/Operadoras_completo.csv
COPY prefixos_operadora.json /app/prefixos_operadora.json

# Volumes para entrada/saída/logs
VOLUME ["/app/entrada", "/app/saida", "/app/logs"]

# Comando padrão: roda o batch
CMD ["java", "-jar", "/app/portabilidade-java.jar"]
