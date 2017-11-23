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
package de.dentrassi.hono.simulator.consumer;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfluxDbMetrics {

    private static final Logger logger = LoggerFactory.getLogger(InfluxDbMetrics.class);

    private final InfluxDB db;

    public InfluxDbMetrics(final String uri, final String username, final String password,
            final String databaseName) {

        logger.info("InfluxDB - metrics - URL: {}", uri);

        this.db = InfluxDBFactory.connect(uri, username, password);

        if (!this.db.databaseExists(databaseName)) {
            this.db.createDatabase(databaseName);
        }

        this.db.setDatabase(databaseName);
    }

    public void updateStats(final Instant timestamp, final long messageCount) {

        final Point.Builder p = Point.measurement("consumer-metrics")
                .time(timestamp.toEpochMilli(), TimeUnit.MILLISECONDS);

        p.addField("messageCount", messageCount);

        this.db.write(p.build());
    }

}
