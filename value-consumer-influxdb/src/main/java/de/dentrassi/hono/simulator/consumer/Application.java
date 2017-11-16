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

import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.connection.ConnectionFactoryImpl.ConnectionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private final Vertx vertx;
    private final HonoClientImpl honoClient;
    private final CountDownLatch latch;
    private final String tenant;
    private final InfluxDbConsumer consumer;

    public static void main(final String[] args) throws Exception {
        final Application app = new Application(
                getenv("HONO_TENANT"),
                getenv("MESSAGING_SERVICE_HOST"), // HONO_DISPATCH_ROUTER_EXT_SERVICE_HOST 
                Integer.parseInt(getenv("MESSAGING_SERVICE_PORT_AMQP")), // HONO_DISPATCH_ROUTER_EXT_SERVICE_PORT
                getenv("HONO_USER"),
                getenv("HONO_PASSWORD"),
                ofNullable(getenv("HONO_TRUSTED_CERTS")));
        app.consumeTelemetryData();

    }

    public Application(final String tenant, final String host, final int port, final String user, final String password,
            final Optional<String> trustedCerts) {

        this.consumer = new InfluxDbConsumer(makeInfluxDbUrl(), getenv("INFLUXDB_USER"), getenv("INFLUXDB_PASSWORD"),
                getenv("INFLUXDB_NAME"));

        this.tenant = tenant;

        this.vertx = Vertx.vertx();

        final ConnectionFactoryBuilder builder = ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                .vertx(this.vertx).host(host).port(port)
                .user(user)
                .password(password)
                .disableHostnameVerification();

        trustedCerts.ifPresent(builder::trustStorePath);

        this.honoClient = new HonoClientImpl(this.vertx, builder.build());

        this.latch = new CountDownLatch(1);

    }

    private String makeInfluxDbUrl() {
        final String url = getenv("INFLUXDB_URL");
        if (url != null && !url.isEmpty()) {
            return url;
        }

        return String.format("http://%s:%s", getenv("INFLUXDB_SERVICE_HOST"), getenv("INFLUXDB_SERVICE_PORT_API"));
    }

    private void consumeTelemetryData() throws Exception {
        final Future<MessageConsumer> consumerFuture = Future.future();

        consumerFuture.setHandler(result -> {
            if (!result.succeeded()) {
                System.err.println("honoClient could not create telemetry consumer : " + result.cause());
            }
            this.latch.countDown();
        });

        final Future<HonoClient> connectionTracker = Future.future();

        this.honoClient.connect(new ProtonClientOptions(), connectionTracker.completer());

        connectionTracker.compose(honoClient -> {
            honoClient.createTelemetryConsumer(this.tenant, msg -> handleTelemetryMessage(msg),
                    consumerFuture.completer());
        }, consumerFuture);

        this.latch.await();

        if (consumerFuture.succeeded()) {
            while (true) {
                Thread.sleep(Long.MAX_VALUE);
            }
        }
        this.vertx.close();

    }

    private void handleTelemetryMessage(final Message msg) {
        // System.out.println(msg);

        final Section body = msg.getBody();

        if (body instanceof AmqpValue) {

            final Object value = ((AmqpValue) body).getValue();

            if (value == null) {
                logger.info("Missing body value");
                return;
            }

            if (value instanceof String) {
                this.consumer.consume(msg, (String) value);
            } else if (value instanceof byte[]) {
                this.consumer.consume(msg, new String((byte[]) value, StandardCharsets.UTF_8));
            } else {
                logger.info("Unsupported body type: {}", value.getClass());
            }
        } else if (body instanceof Data) {
            final String s = StandardCharsets.UTF_8.decode(((Data) body).getValue().asByteBuffer()).toString();
            this.consumer.consume(msg, s);
        } else {
            logger.info("Unsupported body type: {}", body.getClass());
        }

    }

}
