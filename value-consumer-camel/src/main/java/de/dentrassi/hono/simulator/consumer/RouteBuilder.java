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

import org.apache.camel.component.amqp.AMQPConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class RouteBuilder extends org.apache.camel.builder.RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("amqp:topic:telemetry/DEFAULT_TENANT").to("stream:out");
    }

    @Bean
    public AMQPConnectionDetails amqpConnection() {
        final AMQPConnectionDetails details = new AMQPConnectionDetails("amqp://192.168.42.43:30671", "consumer@HONO",
                "verysecret");
        return details;
    }

}
