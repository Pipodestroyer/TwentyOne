FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY src/Net/RailwayRelayServer.java src/Net/RailwayRelayServer.java
RUN mkdir -p out && javac -d out src/Net/RailwayRelayServer.java
CMD ["java", "-cp", "out", "Net.RailwayRelayServer"]
