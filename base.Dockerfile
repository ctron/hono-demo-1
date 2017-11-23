FROM centos:7

MAINTAINER Jens Reimann <jreimann@redhat.com>
LABEL maintainer "Jens Reimann <jreimann@redhat.com>"

RUN yum update -y
RUN yum install -y centos-release-scl
RUN yum install -y java-1.8.0-openjdk java-1.8.0-openjdk-devel rh-maven33 iproute git
RUN alternatives --auto java --verbose && java -version

# build hono M11 before

RUN git clone https://github.com/ctron/hono -b feature/fix_settlement_1
RUN scl enable rh-maven33 "cd hono && git log -1 && mvn -B clean install -DskipTests"

# build vertx mqtt 3.5.1-SNAPSHOT

RUN scl enable rh-maven33 "git clone https://github.com/eclipse/vertx-mqtt && cd vertx-mqtt && git log -1 && mvn clean install -B -DskipTests"

# prepare build

RUN mkdir /build

# start building

COPY . /build

RUN scl enable rh-maven33 "cd build && mvn -B clean package -DskipTests"
