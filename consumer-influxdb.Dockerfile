FROM centos:7

MAINTAINER Jens Reimann <jreimann@redhat.com>
LABEL maintainer "Jens Reimann <jreimann@redhat.com>"

RUN yum update -y
RUN yum install -y maven

# prepare build

RUN mkdir /build

# start building

COPY . /build

RUN cd build && mvn -B clean package -DskipTests

## run shaded jar

ENTRYPOINT ["java", "-Dvertx.cacheDirBase=/tmp", "-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory", "-jar", "/build/value-consumer-influxdb/target/value-consumer-influxdb-app.jar"]
