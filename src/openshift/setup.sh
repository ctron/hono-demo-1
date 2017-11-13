#!/bin/bash

oc env -n hono dc/hono-service-device-registry HONO_REGISTRY_SVC_MAX_DEVICES_PER_TENANT=10000000 # 10 million

echo In addition to running this file you will need to import the template 'demo.yml'
