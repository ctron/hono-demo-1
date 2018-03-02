#!/bin/bash

# openssl pkcs12 -export -inkey target/certs/server-key.pem -in target/certs/server-cert.pem -out target/certs/server-cert.pfx

keytool -importcert -keystore hono.jks -storepass "123456" -file target/certs/server-cert.pem

