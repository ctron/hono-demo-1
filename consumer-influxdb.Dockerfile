FROM hono-demo-1-base:latest

# start building

COPY . /build

RUN scl enable rh-maven33 "cd build && mvn -B clean package -DskipTests"

## run shaded jar

ENTRYPOINT ["java", "-Dvertx.cacheDirBase=/tmp", "-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory", "-jar", "/build/value-consumer-influxdb/target/value-consumer-influxdb-app.jar"]
