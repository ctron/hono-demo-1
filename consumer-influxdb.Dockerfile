FROM hono-demo-base:latest

# start building

COPY . /build

RUN . /opt/rh/rh-maven33/enable && cd build && mvn -B clean package -DskipTests

## run shaded jar

ENTRYPOINT ["java", "-Dvertx.cacheDirBase=/tmp", "-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory", "-jar", "/build/value-consumer-influxdb/target/value-consumer-influxdb-app.jar"]
