/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann - initial API and implementation
 *******************************************************************************/
package de.dentrassi.hono.simulator.dataset;

import static io.glutamate.lang.Exceptions.wrap;
import static io.glutamate.util.Collections.map;
import static io.vertx.core.json.Json.encode;
import static java.lang.System.getenv;
import static java.util.Collections.singletonMap;
import static java.util.Optional.ofNullable;

import de.dentrassi.flow.ComponentInstance;
import de.dentrassi.flow.Flow;
import de.dentrassi.flow.FlowContext;
import de.dentrassi.flow.spi.type.ClassLoaderComponentFactory;
import de.dentrassi.hono.simulator.dataset.AddCredentials.Secret;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Application {

    private static final String TENANT_ID = "DEFAULT_TENANT";

    private static final String REGISTRATION_HOST = getenv("HONO_SERVICE_DEVICE_REGISTRY_SERVICE_HOST");
    private static final String REGISTRATION_PORT = getenv("HONO_SERVICE_DEVICE_REGISTRY_SERVICE_PORT_HTTP");

    private static final HttpUrl REGISTRATION_URL = HttpUrl
            .parse(String.format("http://%s:%s", REGISTRATION_HOST, REGISTRATION_PORT))
            .resolve("/registration/");

    private static final HttpUrl CREDENTIALS_URL = HttpUrl
            .parse(String.format("http://%s:%s", REGISTRATION_HOST, REGISTRATION_PORT))
            .resolve("/credentials/");

    private static final MediaType MT_JSON = MediaType.parse("application/json");

    private static OkHttpClient http;

    private static void registerDevice(final String deviceId, final String username, final String password)
            throws Exception {

        try (final Response getDevice = http.newCall(new Request.Builder()
                .url(
                        REGISTRATION_URL
                                .resolve(TENANT_ID + "/")
                                .resolve(deviceId))
                .get()
                .build()).execute()) {

            System.out.println(getDevice.request().url());

            if (getDevice.isSuccessful()) {

                System.out.format("Device %s already registered%n", deviceId);

            } else {

                try (final Response newDevice = http.newCall(new Request.Builder()
                        .url(
                                REGISTRATION_URL
                                        .resolve(TENANT_ID))
                        .post(RequestBody.create(MT_JSON, encode(singletonMap("device-id", deviceId))))
                        .build()).execute()) {

                    System.out.println(newDevice.request().url());

                    if (!newDevice.isSuccessful()) {
                        throw new RuntimeException(
                                "Unable to register device: " + deviceId + " -> " + newDevice.code() + ": "
                                        + newDevice.message());
                    }
                    System.out.format("Registered device: %s%n", deviceId);
                }
            }
        }

        try (Response getCredentials = http.newCall(new Request.Builder()
                .url(
                        CREDENTIALS_URL
                                .resolve(TENANT_ID + "/")
                                .resolve(username + "/")
                                .resolve("hashed-password"))
                .get()
                .build())
                .execute()) {

            System.out.println(getCredentials.request().url());

            if (getCredentials.isSuccessful()) {
                System.out.format("User %s already registered%n", username);
            } else {

                final AddCredentials add = new AddCredentials();
                add.setAuthId(username);
                add.setDeviceId(deviceId);
                add.setType("hashed-password");
                add.getSecrets().add(Secret.sha512(password));

                System.out.println(encode(add));

                try (final Response newUser = http.newCall(new Request.Builder()
                        .url(
                                CREDENTIALS_URL
                                        .resolve(TENANT_ID))
                        .post(RequestBody.create(MT_JSON, encode(add)))
                        .build()).execute()) {

                    System.out.println(newUser.request().url());

                    if (!newUser.isSuccessful()) {
                        throw new RuntimeException(
                                "Unable to register user: " + username + " -> " + newUser.code() + ": "
                                        + newUser.message());
                    }

                    System.out.format("Registered user: %s%n", deviceId);

                }
            }
        }

    }

    public static void main(final String[] args) throws Exception {

        http = new OkHttpClient.Builder().build();

        final String datasetFile = getenv("DATASET_FILE");
        final String host = getenv("HONO_ADAPTER_MQTT_VERTX_SERVICE_HOST");
        final int port = Integer.parseInt(getenv("HONO_ADAPTER_MQTT_VERTX_SERVICE_PORT"));

        final String deviceIdPrefix = getenv().getOrDefault("DEVICE_ID_PREFIX", "device");
        final int numberOfPublishers = ofNullable(getenv("NUMBER_OF_PUBLISHERS")).map(Integer::parseInt).orElse(1);
        final int numberOfFlows = ofNullable(getenv("NUMBER_OF_FLOWS")).map(Integer::parseInt).orElse(1);

        System.out.format("Dataset: %s%n", datasetFile);
        System.out.format("MQTT Host: %s%n", host);
        System.out.format("MQTT Port: %s%n", port);

        System.out.format("Number of instances (flows × producers) = total - %s × %s = %s%n", numberOfFlows,
                numberOfPublishers, numberOfFlows * numberOfPublishers);

        for (int i = 0; i < numberOfFlows; i++) {

            System.out.format("Starting flow #%s …%n", i);

            @SuppressWarnings("resource")
            final Flow flow = new Flow(new ClassLoaderComponentFactory(Application.class.getClassLoader()));

            Thread.sleep(1_000); // FIXME: remove with flow-core:0.0.2

            final int flowIdx = i;

            flow.modify(
                    context -> wrap(() -> setup(deviceIdPrefix, flowIdx, numberOfPublishers, context, datasetFile, host,
                            port)));
            flow.start();
        }

        System.out.println("Flows are running…");

        try {
            Thread.sleep(Long.MAX_VALUE);
        } finally {
        }

        // yes I know I didn't close the resources after coming out of a 292 million year long sleep 
    }

    public static void setup(final String deviceIdPrefix, final int flowIdx, final int numberOfPublishers,
            final FlowContext context,
            final String file, final String host, final int port) throws Exception {

        System.out.format("Setting up flow #%s - prefix: %s, publishers#: %s%n", flowIdx, deviceIdPrefix,
                numberOfPublishers);

        // csv time series

        final ComponentInstance csv = context.createComponent("de.dentrassi.flow.component.csv.CsvTimeSeriesReader",
                map(map -> {
                    map.put("file", file);
                    map.put("timestampColumn", "UNIX_TS");
                    map.put("timestampUnit", "seconds");
                    map.put("durationDividedBy", "60");
                    map.put("durationMultipliedBy", "1"); // FIXME: remove with flow-core:0.0.2
                }));

        // open CSV when mqtt is connected
        // context.connectTrigger(mqttClient.port("connected"), csv.port("open"));

        // open CSV on flow start - we may loose a few events, but we all run with the same CSV data set
        context.connectTrigger(context.triggerOutInit(), csv.port("open"));

        // map records to JSON

        final ComponentInstance mapBuilder = context.createComponent("de.dentrassi.flow.component.MapBuilder", null);
        final ComponentInstance toJson = context.createComponent("de.dentrassi.flow.component.json.AnyToJson", null);

        context.connectData(mapBuilder.port("map"), toJson.port("input"));

        for (final String tag : new String[] { "WHE", "RSE", "GRE", "MHE", "B1E", "BME", "CWE", "DWE", "EQE", "FRE",
                "HPE", "OFE", "UTE", "WOE", "B2E", "CDE", "DNE", "EBE", "FGE", "HTE", "OUE", "TVE", "UNE" }) {

            // connect fields of map builder, creating the payload
            context.connectData(csv.port("record/" + tag), mapBuilder.port(tag));

        }

        for (int i = 0; i < numberOfPublishers; i++) {

            // mqtt

            final String username = String.format("user-%s-%s-%s", deviceIdPrefix, flowIdx, i);
            final String deviceId = String.format("%s-%s-%s", deviceIdPrefix, flowIdx, i);

            registerDevice(deviceId, username, "hono-secret");

            final ComponentInstance mqttClient = context.createComponent("de.dentrassi.flow.component.mqtt.MqttClient",
                    map(map -> {
                        map.put("host", host);
                        map.put("port", /* "31883"*/ Integer.toString(port));
                        map.put("username", /* "sensor1@DEFAULT_TENANT" */ username + "@" + TENANT_ID);
                        map.put("password", "hono-secret");
                    }));

            context.connectTrigger(context.triggerOutInit(), mqttClient.port("connect"));

            final ComponentInstance mqttPublish = context
                    .createComponent("de.dentrassi.flow.component.mqtt.MqttPublish", map(map -> {
                        map.put("topic", "telemetry");
                        map.put("qos", "0");
                    }));

            // map MQTT client

            context.connectData(mqttClient.port("client"), mqttPublish.port("client"));

            // when the CSV record is updated --> publish to MQTT
            context.connectTrigger(csv.port("updated"), mqttPublish.port("publish"));
            context.connectData(toJson.port("output"), mqttPublish.port("payload"));

        }
    }

}
