# Build stage — Maven ile derle
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage — sadece JRE, küçük image
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/batak-game.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]