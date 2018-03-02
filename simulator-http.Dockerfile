FROM hono-demo-1-base:latest

## run shaded jar

ENTRYPOINT ["java", "-jar", "/build/simulator-http/target/simulator-http-app.jar"]
