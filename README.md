# Installation

This setup requires an existing [installation of Minishift](https://docs.openshift.org/latest/minishift/getting-started/installing.html) and follows the installation instructions for Hono on EnMasse using S2I: https://github.com/ctron/hono/tree/feature/support_s2i_05x/openshift

This tutorial will assume that you have a Unix-ish operating system and at least the following command line tools installed:

* minishift, oc
* curl, wget, jq, bash, tar

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
cd ..
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

## Deploy the demo

Create a new project for the simulator and deploy it:

~~~sh
oc new-project iot-simulator --display-name='IoT workload simulator'
oc process -f src/openshift/demo.yml \
  -p "GIT_BRANCH=develop" | oc create -f -
~~~

## Install Grafana

Grafana can be deployed in order to watch the metrics of Hono and also see the simulated payload. It can
be installed by executing the following commands:

~~~sh
oc new-project grafana --display-name='Grafana Dashboard'
oc process -f https://raw.githubusercontent.com/ctron/hono/feature/support_s2i_05x/openshift/grafana.yml \
   -p ADMIN_PASSWORD=admin | oc create -f -
~~~

## Configure Grafana

The following command line requests require the use of the Grafana URL and will use the
environment variable `GRAFANA_URL` for that. You can set the URL in your local shell using:

~~~sh
GRAFANA_URL="$(oc -n grafana get route grafana --template='{{ .spec.host }}')"
echo "http://$GRAFANA_URL"
~~~

It is also possible to open the URL with a web browser in order to view dashboards and configurations.
The credentials for this instance are `admin` / `admin`.

Create two new datasources by executing the following commands:

~~~sh
curl -X POST -T src/grafana/ds_hono.json -H "content-type: application/json" "http://admin:admin@$GRAFANA_URL/api/datasources"
curl -X POST -T src/grafana/ds_payload.json -H "content-type: application/json" "http://admin:admin@$GRAFANA_URL/api/datasources"

curl -X POST -T src/grafana/dashboard_hono.json -H "content-type: application/json" "http://admin:admin@$GRAFANA_URL/api/dashboards/db"
curl -X POST -T src/grafana/dashboard_payload.json -H "content-type: application/json" "http://admin:admin@$GRAFANA_URL/api/dashboards/db"
~~~

# What now?

Now you have an IoT simulator running, which will stream a data set to the Eclipse Hono instance. The IoT consumer
will consume the simulated payload and store it in the metrics database of Hono.

## Some dashboards

You can check the following URLs:

<dl>
<dt>$GRAFANA_URL/dashboard/db/ampds2</dt><dd>This shows the payload as it is stored in the InfluxDB payload instance.</dd>
<dt>$GRAFANA_URL/dashboard/db/hono</dt><dd>Shows the metrics of the Eclipse Hono instances. Initially this should show a "stunning" 1 message/second throughput.</dd>
</dl>

## Installing Eclipse Che

You can install Eclipse Che to the setup by executing the following commands:

~~~sh
DEPLOY_ROOT_URL=https://raw.githubusercontent.com/eclipse/che/6.1.x/dockerfiles/init/modules/openshift/files/scripts/
curl -fL "$DEPLOY_ROOT_URL/deploy_che.sh" -o get-che.sh
curl -fL "$DEPLOY_ROOT_URL/che-openshift.yml" -o che-openshift.yml
curl -fL "$DEPLOY_ROOT_URL/che-config" -o che-config
export CHE_IMAGE_TAG=6.1.1
bash ./get-che.sh
~~~

After Che is initialized you can import the example project and start working on it:

~~~sh
CHE_URL="http://$(oc -n eclipse-che get route che --template='{{ .spec.host }}')"
echo "Open browser at: $CHE_URL/f?url=https://github.com/ctron/hono-demo-1/tree/develop"
~~~
