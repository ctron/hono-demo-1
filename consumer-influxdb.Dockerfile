FROM hono-demo-1-base:latest

## run shaded jar

ENTRYPOINT ["java", "-Dvertx.cacheDirBase=/tmp", "-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory", "-jar", "/build/value-consumer-influxdb/target/value-consumer-influxdb-app.jar"]
