FROM centos:7

MAINTAINER Jens Reimann <jreimann@redhat.com>
LABEL maintainer "Jens Reimann <jreimann@redhat.com>"

RUN yum update -y
RUN yum install -y maven iproute git

# prepare build

RUN mkdir /build

RUN git clone https://github.com/ctron/hono -b feature/fix_settlement_1
RUN cd hono && mvn -B clean install -DskipTests

# start building

COPY . /build

RUN cd build && mvn -B clean package -DskipTests

## run shaded jar

ENTRYPOINT ["java", "-Dvertx.cacheDirBase=/tmp", "-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory", "-jar", "/build/value-consumer-influxdb/target/value-consumer-influxdb-app.jar"]
