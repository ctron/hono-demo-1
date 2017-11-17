FROM hono-demo-base:latest

# start building

COPY . /build

RUN xz -fd /build/src/dataset/Electricity_P.csv.xz

## run shaded jar

ENTRYPOINT ["java", "-Dvertx.cacheDirBase=/tmp", "-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory", "-jar", "/build/dataset-simulator/target/dataset-simulator-app.jar"]
