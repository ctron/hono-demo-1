# OpenShift deployment

## Using the Web UI

Simply drop the content of `demo.yml` into the "Import YAML / JSON" form of OpenShift.
Be sure to add it to the "Eclipse Hono" project.

## Increasing the maximum number of devices

By default Hono limits the number of devices to 100 for its examples device registry.

This limit can be set by executing:

    oc env -n hono dc/hono-service-device-registry HONO_REGISTRY_SVC_MAX_DEVICES_PER_TENANT=10000

**Note:** Please remember that this device registry is held in-memory and flushed to disk using JSON. So
performance might become an issue with too many devices.
