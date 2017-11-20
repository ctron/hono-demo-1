## Installation

### Create a Minishift instance

~~~sh
minishift start --cpus 4 --memory 16GB --metrics --disk-size 40GB
~~~

### Install EnMasse

Download https://github.com/EnMasseProject/enmasse/releases/download/0.13.2/enmasse-0.13.2.tgz

~~~sh
tar xzf enmasse-0.13.2.tgz
cd enmasse-0.13.2
./deploy-openshift.sh -n hono -m https://$(minishift ip):8443
~~~

### Install Eclipse Hono

~~~sh
git clone https://github.com/eclipse/hono
eval $(minishift docker-env)
cd hono
mvn clean install -Pbuild-docker-image -DskipTests
cd example/target/deploy/openshift
chmod a+x *.sh
./enmasse_deploy.sh
~~~

### Deploy the demo

Deploy the OpenShift part: [src/openshift/README.md](src/openshift/README.md).

### Deploy Grafana setup

Apply the Grafana configuration: [src/grafana/README.md](src/grafana/README.md).

### Run locally

Extract the certificate:

~~~sh
mkdir target/certs/
oc extract -n hono secret/external-certs-messaging --to=target/certs/
./convert-to-jks.sh
~~~
