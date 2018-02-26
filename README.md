# Installation

This setup requires an existing [installation of Minishift](https://docs.openshift.org/latest/minishift/getting-started/installing.html) and follows the installation instructions for Hono on EnMasse using S2I: https://github.com/ctron/hono/tree/feature/support_s2i_05x/openshift

This tutorial will assume that you have a Unix-ish operating system and know how to use `wget`, `tar`, …

## Create a Minishift instance

First you will need to start up minishift:

~~~sh
minishift start --cpus 4 --memory 16GB --metrics --disk-size 40GB
~~~

## Install EnMasse

Create a new EnMasse project:

~~~sh
oc new-project enmasse --display-name='EnMasse Instance'
~~~

Download, unpack and deploy EnMasse:

~~~sh
wget https://github.com/EnMasseProject/enmasse/releases/download/0.17.0/enmasse-0.17.0.tgz
tar xzf enmasse-0.17.0.tgz
cd enmasse-0.17.0
./deploy-openshift.sh -n enmasse -m "$(minishift console --url)"
~~~

**Note:** After the deployment of EnMasse the system might need a while to download and install container
images. Please wait for all pods to start before continuing. You can track the progress
using the OpenShift Web UI.

## Configure EnMasse

The following step will create the addresses for `DEFAULT_TENANT`:

~~~sh
curl -X PUT --insecure -T src/openshift/addresses.json -H "content-type: application/json" https://$(oc -n enmasse get route restapi -o jsonpath='{.spec.host}')/apis/enmasse.io/v1/addresses/default
~~~

## Install Eclipse Hono

~~~sh
oc new-project hono --display-name='Eclipse Hono™'
oc -n hono create configmap influxdb-config --from-file=src/openshift/influxdb.conf
oc process -f https://raw.githubusercontent.com/ctron/hono/feature/support_s2i_05x/openshift/hono.yml \
   -p "ENMASSE_NAMESPACE=enmasse" \
   -p "GIT_REPOSITORY=https://github.com/ctron/hono" \
   -p "GIT_BRANCH=feature/support_s2i_05x"| oc create -f -
~~~

**Note:** After executing the template container images will be downloaded and builds will be triggered in
order to start up Hono. Please wait for all pods to start before continuing. You can track the progress
using the OpenShift Web UI.

### Increase the number of devices

By default Hono limits the number of devices to 100 for its examples device registry.

This limit can be set by executing:

~~~sh
oc env -n hono dc/hono-service-device-registry HONO_REGISTRY_SVC_MAX_DEVICES_PER_TENANT=10000
~~~

**Note:** Please remember that this device registry is held in-memory and flushed to disk using JSON. So
performance might become an issue with too many devices.

## Install Grafana Dashboard

Grafana can be deployed in order to watch the metrics of Hono and also see the simulated payload. It can
be installed by executing the following commands:

~~~sh
oc new-project grafana --display-name='Grafana Dashboard'
oc process -f https://raw.githubusercontent.com/ctron/hono/feature/support_s2i_05x/openshift/grafana.yml \
   -p ADMIN_PASSWORD=admin | oc create -f -
~~~

## Deploy the demo

Create a new project for the simulator and deploy it:

~~~sh
oc new-project iot-simulator --display-name='IoT workload simulator'
oc process -f src/openshift/demo.yml | oc create -f - 
~~~

