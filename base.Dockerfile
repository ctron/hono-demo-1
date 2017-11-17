FROM centos:7

MAINTAINER Jens Reimann <jreimann@redhat.com>
LABEL maintainer "Jens Reimann <jreimann@redhat.com>"

RUN yum update -y
RUN yum install -y centos-release-scl
RUN yum install -y rh-maven33 iproute git

# build hono M11 before

RUN git clone https://github.com/ctron/hono -b feature/fix_settlement_1
RUN scl enable rh-maven33 "cd hono && mvn -B clean install -DskipTests"

# build vertx mqtt 3.5.1-SNAPSHOT

RUN scl enable rh-maven33 "git clone https://github.com/ctron/vertx-mqtt -b feature/fix_missing_callback_1 && cd vertx-mqtt && mvn clean install -B -DskipTests"

# prepare build

RUN mkdir /build

# start building

COPY . /build

RUN xz -fd /build/src/dataset/Electricity_P.csv.xz
RUN scl enable rh-maven33 "cd build && mvn -B clean package -DskipTests"

