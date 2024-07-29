# Use a multi-stage build to create a smaller final image
FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /app

# Copy the source code and build the application
COPY target /app/target

# Second stage: use a lightweight base image
FROM openjdk:17-jdk-slim
WORKDIR /app

# Copy the built application from the first stage
COPY --from=build /app/target/api-1.0.0-SNAPSHOT.jar /app/api-1.0.0-SNAPSHOT.jar

# Specify the command to run the application
ENTRYPOINT ["java", "-jar", "/app/api-1.0.0-SNAPSHOT.jar"]
