# Multi-stage build for ISMA FileCompressor
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
COPY public ./public
RUN mvn clean package -DskipTests -B && \
    find target -name "*.jar" ! -name "original-*.jar" -exec cp {} target/app.jar \;

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install wget for health checks
RUN apk add --no-cache wget

# Create necessary directories
RUN mkdir -p uploads output config

# Copy the built JAR from build stage
# The JAR was prepared as app.jar in the build stage
COPY --from=build /app/target/app.jar /app/app.jar

# Expose port (Railway will set PORT environment variable automatically)
EXPOSE 8080

# Set environment variables
# Railway will override PORT, but JAVA_OPTS can be set in Railway dashboard
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV PORT=8080

# Health check endpoint (Railway will use /api/info from railway.json)
# Using wget which is available in alpine, or fallback to sh
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/info 2>/dev/null || exit 1

# Run the application
# Use shell form to properly expand environment variables
# This ensures JAVA_OPTS is expanded correctly
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]

