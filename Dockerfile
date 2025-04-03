# Importing JDK and copying required files
FROM openjdk:21-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src src

# Copy Maven wrapper
COPY mvnw .
COPY .mvn .mvn

# Set execution permission for the Maven wrapper
RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

# Stage 2: Create the final Docker image using OpenJDK 19
FROM openjdk:21-jdk
VOLUME /tmp

# Copy the JAR from the build stage
COPY --from=build /app/target/*.jar cc-backend-ai.jar
ENTRYPOINT ["java", "-jar", "cc-backend-ai.jar", "--spring.datasource.url=${MYSQL_DB_URL}", "--spring.datasource.username=${MYSQL_DB_USER}", "--spring.datasource.password=${MYSQL_DB_PASS}"]
EXPOSE 2991