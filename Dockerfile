# Playwright Java 1.55.0 with browser dependencies
FROM mcr.microsoft.com/playwright/java:v1.55.0

WORKDIR /app

# Copy the Spring Boot JAR
COPY target/*.jar cc-backend-ai.jar

# Java heap for Koyeb's 512 MB RAM instance
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx300m"

# Application port
EXPOSE 2990

ENTRYPOINT ["java", "-jar", "cc-backend-ai.jar"]
