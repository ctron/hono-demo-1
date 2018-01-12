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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private static final String TENANT_ID = "DEFAULT_TENANT";

    private static final String REGISTRATION_HOST = getenv("HONO_SERVICE_DEVICE_REGISTRY_SERVICE_HOST");
    private static final String REGISTRATION_PORT = getenv("HONO_SERVICE_DEVICE_REGISTRY_SERVICE_PORT_HTTP");

    private static final HttpUrl REGISTRATION_URL = REGISTRATION_HOST == null ? null
            : HttpUrl
                    .parse(String.format("http://%s:%s", REGISTRATION_HOST, REGISTRATION_PORT))
                    .resolve("/registration/");

    private static final HttpUrl CREDENTIALS_URL = REGISTRATION_HOST == null ? null
            : HttpUrl
                    .parse(String.format("http://%s:%s", REGISTRATION_HOST, REGISTRATION_PORT))
                    .resolve("/credentials/");

    private static boolean shouldRegister() {
        return REGISTRATION_URL != null;
    }

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

            logger.debug("Registration URL - get: {}", getDevice.request().url());

            if (getDevice.isSuccessful()) {

                logger.debug("Device {} already registered", deviceId);

            } else {

                logger.debug("Failed to retrieve registration: {} {}", getDevice.code(), getDevice.message());

                try (final Response newDevice = http.newCall(new Request.Builder()
                        .url(
                                REGISTRATION_URL
                                        .resolve(TENANT_ID))
                        .post(RequestBody.create(MT_JSON, encode(singletonMap("device-id", deviceId))))
                        .build()).execute()) {

                    logger.debug("Registration URL - post: {}", newDevice.request().url());

                    if (!newDevice.isSuccessful()) {
                        throw new RuntimeException(
                                "Unable to register device: " + deviceId + " -> " + newDevice.code() + ": "
                                        + newDevice.message());
                    }
                    logger.info("Registered device: {}", deviceId);
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

            logger.debug("Credentials URL - get: {}", getCredentials.request().url());

            if (getCredentials.isSuccessful()) {

                logger.debug("User {} already registered", username);

            } else {

                final AddCredentials add = new AddCredentials();
                add.setAuthId(username);
                add.setDeviceId(deviceId);
                add.setType("hashed-password");
                add.getSecrets().add(Secret.sha512(password));

                try (final Response newUser = http.newCall(new Request.Builder()
                        .url(
                                CREDENTIALS_URL
                                        .resolve(TENANT_ID))
                        .post(RequestBody.create(MT_JSON, encode(add)))
                        .build()).execute()) {

                    logger.debug("Credentials URL - get: {}", newUser.request().url());

                    if (!newUser.isSuccessful()) {
                        throw new RuntimeException(
                                "Unable to register user: " + username + " -> " + newUser.code() + ": "
                                        + newUser.message());
                    }

                    logger.info("Registered user {}", username);

                }
            }
        }

    }

    public static void main(final String[] args) throws Exception {

        http = new OkHttpClient.Builder().build();

        final String datasetFile = getenv("DATASET_FILE");
        final String host = getenv("HONO_ADAPTER_MQTT_VERTX_SERVICE_HOST");
        final int port = Integer.parseInt(getenv("HONO_ADAPTER_MQTT_VERTX_SERVICE_PORT"));

        final String deviceIdPrefix = getenv().getOrDefault("DEVICE_ID_PREFIX",
                getenv().getOrDefault("HOSTNAME", "device"));
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

            final int flowIdx = i;

            flow.modify(
                    context -> wrap(() -> setup(deviceIdPrefix, flowIdx, numberOfPublishers, context, datasetFile, host,
                            port)));
            flow.start();

            /*
            if (i == 0) {
            
                final ModelListener model = new ModelListener();
                try (ListenerHandle listener = flow.registerListener(true, model)) {
                    listener.initialized().get();
                }
            
                newRenderer()
                        .render(model.getFlow(), Paths.get("model.dot"));
            }
            */
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

            if (shouldRegister()) {
                registerDevice(deviceId, username, "hono-secret");
            }

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

            // when the CSV record is updated --> publish to MQTT if the client is connected

            final ComponentInstance permit = context.createComponent("de.dentrassi.flow.component.trigger.Permit",
                    null);

            // csv]updated --> input[permit]output --> publish[mqttPublish

            context.connectTrigger(csv.port("updated"), permit.port("input"));
            context.connectTrigger(permit.port("output"), mqttPublish.port("publish"));

            // mqttClient]connected >-- permit[permit

            context.connectData(mqttClient.port("connected"), permit.port("permit"));

            // publish JSON payload
            context.connectData(toJson.port("output"), mqttPublish.port("payload"));

        }
    }

}
