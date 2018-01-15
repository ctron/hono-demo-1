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
package de.dentrassi.hono.simulator.http;

import static de.dentrassi.hono.demo.common.Register.shouldRegister;
import static java.lang.System.getenv;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.dentrassi.hono.demo.common.InfluxDbMetrics;
import de.dentrassi.hono.demo.common.Register;
import okhttp3.OkHttpClient;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static final String DEFAULT_TENANT = "DEFAULT_TENANT";

    private static InfluxDbMetrics metrics;

    private static final boolean METRICS_ENABLED = Optional
            .ofNullable(System.getenv("ENABLE_METRICS"))
            .map(Boolean::parseBoolean)
            .orElse(true);

    private static String makeInfluxDbUrl() {
        final String url = getenv("INFLUXDB_URL");
        if (url != null && !url.isEmpty()) {
            return url;
        }

        return String.format("http://%s:%s", getenv("INFLUXDB_SERVICE_HOST"), getenv("INFLUXDB_SERVICE_PORT_API"));
    }

    public static void main(final String[] args) throws Exception {

        if (METRICS_ENABLED) {
            logger.info("Recording metrics");
            metrics = new InfluxDbMetrics(makeInfluxDbUrl(),
                    getenv("INFLUXDB_USER"),
                    getenv("INFLUXDB_PASSWORD"),
                    getenv("INFLUXDB_NAME"));
        } else {
            metrics = null;
        }

        final int numberOfDevices = envOrElse("NUM_DEVICES", Integer::parseInt, 10);
        final int numberOfThreads = envOrElse("NUM_THREADS", Integer::parseInt, 10);

        final OkHttpClient http = new OkHttpClient.Builder()
                .build();

        final String deviceIdPrefix = System.getenv("HOSTNAME");

        final Register register = new Register(http, DEFAULT_TENANT);

        final ScheduledExecutorService executor = Executors.newScheduledThreadPool(numberOfThreads);

        try {

            executor.scheduleAtFixedRate(Application::dumpStats, 1, 1, TimeUnit.SECONDS);

            for (int i = 0; i < numberOfDevices; i++) {

                final String username = String.format("user-%s-%s", deviceIdPrefix, i);
                final String deviceId = String.format("%s-%s", deviceIdPrefix, i);

                if (shouldRegister()) {
                    register.device(deviceId, username, "hono-secret");
                }

                final Device device = new Device(username + "@" + DEFAULT_TENANT, "hono-secret", http);
                executor.scheduleAtFixedRate(device::tick, 1, 1, TimeUnit.SECONDS);
            }

            Thread.sleep(Long.MAX_VALUE);
        } finally {
            executor.shutdown();
        }

    }

    private static void dumpStats() {
        final long sent = Device.SENT.getAndSet(0);
        final long success = Device.SUCCESS.getAndSet(0);
        final long failure = Device.FAILURE.getAndSet(0);

        final Map<String, Number> values = new HashMap<>(3);
        values.put("sent", sent);
        values.put("success", success);
        values.put("failure", failure);

        metrics.updateStats(Instant.now(), "http-publish", values);

        System.out.format("Sent: %10s, Success: %10s, Failure: %10s%n", sent, success, failure);
        System.out.flush();
    }

    private static <T> T envOrElse(final String name, final Function<String, T> converter, final T defaultValue) {
        final String value = System.getenv(name);

        if (value == null) {
            return defaultValue;
        }

        return converter.apply(value);
    }
}
