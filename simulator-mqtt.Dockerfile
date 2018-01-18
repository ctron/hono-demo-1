FROM hono-demo-1-base:latest

## run shaded jar

ENTRYPOINT ["java", "-jar", "/build/simulator-mqtt/target/simulator-mqtt-app.jar"]
