# OpenShift deployment

First you need to a create a new project in OpenShift. You can do this using the web UI or from
the command line using:

~~~sh
oc new-project iot-simulator --display-name='IoT workload simulator'
~~~

## Using the Web UI

Simply drop the content of `demo.yml` into the "Import YAML / JSON" form of OpenShift.
Be sure to add it to the "Eclipse Hono" project.

## Using the command line

You can also import the example using the command line tools:

~~~sh
oc process -f demo.yml | oc create -f - 
~~~

## Increasing the maximum number of devices

By default Hono limits the number of devices to 100 for its examples device registry.

This limit can be set by executing:

~~~sh
oc env -n hono dc/hono-service-device-registry HONO_REGISTRY_SVC_MAX_DEVICES_PER_TENANT=10000
~~~

**Note:** Please remember that this device registry is held in-memory and flushed to disk using JSON. So
performance might become an issue with too many devices.
