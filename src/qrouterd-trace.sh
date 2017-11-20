#!/bin/bash

set -e

while true; do
	sleep 5
	oc exec "$1" -c router -it -- qdmanage QUERY --type link > "qrouterd-stats.$(date +%Y%m%d-%H%M%S).txt"
done