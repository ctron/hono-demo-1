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
import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

import de.dentrassi.flow.ComponentInstance;
import de.dentrassi.flow.Flow;
import de.dentrassi.flow.FlowContext;
import de.dentrassi.flow.spi.type.ClassLoaderComponentFactory;

public class KapuaApplication {

    public static void main(final String[] args) throws Exception {

        final String datasetFile = getenv("DATASET_FILE");
        final String uri = getenv("KAPUA_BROKER_PORT");

        final String deviceIdPrefix = getenv().getOrDefault("DEVICE_ID_PREFIX", "device");
        final int numberOfPublishers = ofNullable(getenv("NUMBER_OF_PUBLISHERS")).map(Integer::parseInt).orElse(1);
        final int numberOfFlows = ofNullable(getenv("NUMBER_OF_FLOWS")).map(Integer::parseInt).orElse(1);

        System.out.format("Dataset: %s%n", datasetFile);
        System.out.format("Broker URI: %s%n", uri);

        System.out.format("Number of instances (flows × producers) = total - %s × %s = %s%n", numberOfFlows,
                numberOfPublishers, numberOfFlows * numberOfPublishers);

        for (int i = 0; i < numberOfFlows; i++) {

            System.out.format("Starting flow #%s …%n", i);

            @SuppressWarnings("resource")
            final Flow flow = new Flow(new ClassLoaderComponentFactory(KapuaApplication.class.getClassLoader()));

            final int flowIdx = i;

            flow.modify(
                    context -> wrap(
                            () -> setup(deviceIdPrefix, flowIdx, numberOfPublishers, context, datasetFile, uri)));
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
            final String file, final String uri) throws Exception {

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

        // open CSV on flow start - we may loose a few events, but we all run with the same CSV data set
        context.connectTrigger(context.triggerOutInit(), csv.port("open"));

        // map records to JSON

        final ComponentInstance mapBuilder = context.createComponent("de.dentrassi.flow.component.MapBuilder");

        for (final String tag : new String[] { "WHE", "RSE", "GRE", "MHE", "B1E", "BME", "CWE", "DWE", "EQE", "FRE",
                "HPE", "OFE", "UTE", "WOE", "B2E", "CDE", "DNE", "EBE", "FGE", "HTE", "OUE", "TVE", "UNE" }) {

            // connect fields of map builder, creating the payload
            final ComponentInstance s2n = context.createComponent("de.dentrassi.flow.component.StringToNumber");
            context.connectData(csv.port("record/" + tag), s2n.port("input"));
            context.connectData(s2n.port("output"), mapBuilder.port(tag));
        }

        // set up transmission to Kapua

        for (int i = 0; i < numberOfPublishers; i++) {

            final String deviceId = String.format("%s-%s-%s", deviceIdPrefix, flowIdx, i);

            // create client

            final ComponentInstance kapuaClient = context.createComponent(
                    "de.dentrassi.flow.component.kapua.KapuaClient",
                    map(map -> {
                        map.put("brokerUrl", uri);
                        map.put("username", "kapua-broker");
                        map.put("password", "kapua-password");
                        map.put("accountName", "kapua-sys");
                        map.put("clientId", deviceId);
                    }));

            // connect client when context starts

            context.connectTrigger(context.triggerOutInit(), kapuaClient.port("connect"));

            final ComponentInstance kapuaApplication = context.createComponent(
                    "de.dentrassi.flow.component.kapua.KapuaApplication",
                    map(map -> {
                        map.put("applicationId", "flow");
                    }));

            // connect client to application

            context.connectData(kapuaClient.port("client"), kapuaApplication.port("client"));

            // trigger app creation on startup

            context.connectTrigger(kapuaClient.port("clientCreated"), kapuaApplication.port("create"));

            final ComponentInstance kapuaTopic = context.createComponent(
                    "de.dentrassi.flow.component.kapua.KapuaTopic",
                    map(map -> {
                        map.put("topic", "telemetry");
                    }));

            context.connectData(kapuaApplication.port("application"), kapuaTopic.port("application"));

            final ComponentInstance permit = context.createComponent("de.dentrassi.flow.component.trigger.Permit",
                    null);

            // context.connectTrigger(csv.port("updated"), kapuaTopic.port("publish"));
            context.connectTrigger(csv.port("updated"), permit.port("input"));
            context.connectTrigger(permit.port("output"), kapuaTopic.port("publish"));

            // map "application ready"

            final ComponentInstance isNull = context.createComponent("de.dentrassi.flow.component.IsNull",
                    null);
            final ComponentInstance not = context.createComponent("de.dentrassi.flow.component.Not",
                    null);

            context.connectData(kapuaApplication.port("application"), isNull.port("input"));
            context.connectData(isNull.port("output"), not.port("input"));
            context.connectData(not.port("output"), permit.port("permit"));

            // map payload data

            context.connectData(mapBuilder.port("map"), kapuaTopic.port("payload"));

        }
    }

}
