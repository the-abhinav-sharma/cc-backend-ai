# Use a lightweight Java 21 JRE image instead of the heavy Playwright image (saves ~300MB RAM)
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the Spring Boot JAR
COPY target/*.jar cc-backend-ai.jar

# Optimized Java heap for a 512 MB RAM Koyeb instance
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx300m"

# Application port (updated to match EXPOSE 2990 or your target port)
EXPOSE 2990

ENTRYPOINT ["java", "-jar", "cc-backend-ai.jar"]