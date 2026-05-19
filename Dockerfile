FROM eclipse-temurin:17-jre
WORKDIR /app

ARG JAR_FILE=target/enterprise-rag-demo-0.0.1-SNAPSHOT.jar
ENV JAVA_OPTS=""
COPY ${JAR_FILE} /app/app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
