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
