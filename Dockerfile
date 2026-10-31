# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# O parent declara 5 módulos: todos os POMs precisam existir para o reactor
# resolver o build, mesmo que só o batch seja compilado (-pl ... -am).
COPY pom.xml .
COPY portability-core/pom.xml portability-core/
COPY portability-adapters/pom.xml portability-adapters/
COPY portability-batch/pom.xml portability-batch/
COPY portability-api/pom.xml portability-api/
COPY portability-bff/pom.xml portability-bff/
COPY config config
RUN mvn -B -q -ntp -pl portability-batch -am dependency:go-offline || true

# Copia apenas o código necessário e compila o fat-jar do batch
COPY portability-core/src portability-core/src
COPY portability-adapters/src portability-adapters/src
COPY portability-batch/src portability-batch/src
RUN mvn -B -q -ntp -pl portability-batch -am clean package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/portability-batch/target/portabilidade-java.jar /app/portabilidade-java.jar

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
