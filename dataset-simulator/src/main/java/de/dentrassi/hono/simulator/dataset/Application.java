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

import static de.dentrassi.hono.demo.common.Register.shouldRegister;
import static io.glutamate.lang.Exceptions.wrap;
import static io.glutamate.util.Collections.map;
import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

import de.dentrassi.flow.ComponentInstance;
import de.dentrassi.flow.Flow;
import de.dentrassi.flow.FlowContext;
import de.dentrassi.flow.spi.type.ClassLoaderComponentFactory;
import de.dentrassi.hono.demo.common.Register;
import okhttp3.OkHttpClient;

public class Application {

    private static final String TENANT_ID = "DEFAULT_TENANT";

    private static OkHttpClient http;

    private static Register register;

    public static void main(final String[] args) throws Exception {

        http = new OkHttpClient.Builder().build();
        register = new Register(http, TENANT_ID);

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
                register.device(deviceId, username, "hono-secret");
            }

            final ComponentInstance mqttClient = context.createComponent("de.dentrassi.flow.component.mqtt.MqttClient",
                    map(map -> {
                        map.put("host", host);
                        map.put("port", /* "31883"*/ Integer.toString(port));
                        map.put("username", /* "sensor1@DEFAULT_TENANT" */ username + "@" + TENANT_ID);
                        map.put("password", "hono-secret");
                        map.put("trustAll", "true"); // WARNING: DO NOT USE IN PRODUCTION
                        map.put("ssl", "true");
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
