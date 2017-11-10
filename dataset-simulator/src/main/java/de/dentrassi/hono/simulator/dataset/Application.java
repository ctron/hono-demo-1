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

import static io.glutamate.util.Collections.map;
import static java.lang.System.getenv;

import de.dentrassi.flow.ComponentInstance;
import de.dentrassi.flow.Flow;
import de.dentrassi.flow.FlowContext;
import de.dentrassi.flow.spi.type.ClassLoaderComponentFactory;

public class Application {
    public static void main(final String[] args) throws Exception {

        final String datasetFile = getenv("DATASET_FILE");
        final String host = getenv("HONO_ADAPTER_MQTT_VERTX_SERVICE_HOST");
        final int port = Integer.parseInt(getenv("HONO_ADAPTER_MQTT_VERTX_SERVICE_PORT"));

        try (final Flow flow = new Flow(new ClassLoaderComponentFactory(Application.class.getClassLoader()))) {
            flow.modify(context -> setup(context, datasetFile, host, port));
            flow.start();
            try {
                Thread.sleep(Long.MAX_VALUE);
            } finally {
                flow.stop();
            }
        }

    }

    public static void setup(final FlowContext context, final String file, final String host, final int port) {

        // csv time series

        final ComponentInstance csv = context.createComponent("de.dentrassi.flow.component.csv.CsvTimeSeriesReader",
                map(map -> {
                    map.put("file", file);
                    map.put("timestampColumn", "UNIX_TS");
                    map.put("timestampUnit", "seconds");
                    map.put("durationDividedBy", "60");
                    map.put("durationMultipliedBy", "1"); // FIXME: remove with flow-core:0.0.2
                }));

        // mqtt

        final ComponentInstance mqttClient = context.createComponent("de.dentrassi.flow.component.mqtt.MqttClient",
                map(map -> {
                    /*
                    map.put("host", "iot.eclipse.org");
                    map.put("port", "1883");
                    */
                    map.put("host", host);
                    map.put("port", /* "31883"*/ Integer.toString(port));
                    map.put("username", "sensor1@DEFAULT_TENANT");
                    map.put("password", "hono-secret");
                }));

        context.connectTrigger(context.triggerOutInit(), mqttClient.port("connect"));

        // open CSV when mqtt is connected
        context.connectTrigger(mqttClient.port("connected"), csv.port("open"));

        final ComponentInstance mapBuilder = context.createComponent("de.dentrassi.flow.component.MapBuilder", null);
        final ComponentInstance toJson = context.createComponent("de.dentrassi.flow.component.json.AnyToJson", null);

        context.connectData(mapBuilder.port("map"), toJson.port("input"));

        final ComponentInstance mqttPublish = context.createComponent("de.dentrassi.flow.component.mqtt.MqttPublish",
                map(map -> {
                    map.put("topic", "telemetry");
                    map.put("qos", "0");
                }));

        context.connectData(mqttClient.port("client"), mqttPublish.port("client"));

        context.connectTrigger(csv.port("updated"), mqttPublish.port("publish"));
        context.connectData(toJson.port("output"), mqttPublish.port("payload"));

        for (final String tag : new String[] { "WHE", "RSE", "GRE", "MHE", "B1E", "BME", "CWE", "DWE", "EQE", "FRE",
                "HPE", "OFE", "UTE", "WOE", "B2E", "CDE", "DNE", "EBE", "FGE", "HTE", "OUE", "TVE", "UNE" }) {

            context.connectData(csv.port("record/" + tag), mapBuilder.port(tag));

        }
    }

}
