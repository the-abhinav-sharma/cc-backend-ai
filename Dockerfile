# Use Eclipse Temurin (Java 21) base image
FROM eclipse-temurin:21-jdk-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy the JAR file (change the name if your JAR is named differently)
COPY target/*.jar cc-backend-ai.jar

# Expose port 8080
EXPOSE 2990

# Command to run the application
ENTRYPOINT ["java", "-jar", "cc-backend-ai.jar"]