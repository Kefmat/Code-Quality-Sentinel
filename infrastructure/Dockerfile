# STAGE 1: Build
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build
COPY ./app/src ./src
RUN javac src/CodeAnalyzer.java

# STAGE 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Installer Node.js
RUN apk add --no-cache nodejs

# Kopier kompilerte filer og verktøy
COPY --from=builder /build/src/*.class ./src/
COPY ./app/scripts ./scripts
COPY ./app/templates ./templates

# Opprett mapper for skanning og resultat.
# Vi oppretter både /app/output og /app/app/output for å være 100% sikre på 
# at Node-skriptets relative baner (../output) treffer en eksisterende mappe.
RUN mkdir -p /source /app/output /app/app/output

# Kjør mot /source mappen
CMD ["sh", "-c", "java -cp src CodeAnalyzer /source && node scripts/report_generator.js"]