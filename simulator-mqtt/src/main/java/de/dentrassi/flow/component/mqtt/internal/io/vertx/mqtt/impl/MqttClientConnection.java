/*
 * Copyright 2016 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dentrassi.flow.component.mqtt.internal.io.vertx.mqtt.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.vertx.core.impl.NetSocketInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttConnAckMessage;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubAckMessage;

/*
 * FIXME: This is required in order to properly pull in impl.MqttClientImpl
 */

/**
 * Represents an MQTT connection with a remote server
 */
public class MqttClientConnection {

    private static final Logger log = LoggerFactory.getLogger(MqttClientConnection.class);

    private final NetSocketInternal so;
    private final ChannelHandlerContext chctx;
    private final MqttClientOptions options;
    private final MqttClientImpl client;

    MqttClientConnection(final MqttClientImpl client, final NetSocketInternal so, final MqttClientOptions options) {
        this.so = so;
        this.chctx = so.channelHandlerContext();
        this.client = client;
        this.options = options;
    }

    /**
     * Handle the MQTT message received from the remote MQTT server
     *
     * @param msg
     *            Incoming Packet
     */
    synchronized void handleMessage(final Object msg) {

        // handling directly native Netty MQTT messages, some of them are translated
        // to the related Vert.x ones for polyglotization
        if (msg instanceof MqttMessage) {

            final MqttMessage mqttMessage = (MqttMessage) msg;

            final DecoderResult result = mqttMessage.decoderResult();
            if (result.isFailure()) {
                this.chctx.pipeline().fireExceptionCaught(result.cause());
                return;
            }
            if (!result.isFinished()) {
                this.chctx.pipeline().fireExceptionCaught(new Exception("Unfinished message"));
                return;
            }

            log.debug(String.format("Incoming packet %s", msg));
            switch (mqttMessage.fixedHeader().messageType()) {

            case CONNACK:

                final io.netty.handler.codec.mqtt.MqttConnAckMessage connack = (io.netty.handler.codec.mqtt.MqttConnAckMessage) mqttMessage;

                final MqttConnAckMessage mqttConnAckMessage = MqttConnAckMessage.create(
                        connack.variableHeader().connectReturnCode(),
                        connack.variableHeader().isSessionPresent());
                handleConnack(mqttConnAckMessage);
                break;

            case PUBLISH:

                final io.netty.handler.codec.mqtt.MqttPublishMessage publish = (io.netty.handler.codec.mqtt.MqttPublishMessage) mqttMessage;
                final ByteBuf newBuf = VertxHandler.safeBuffer(publish.payload(), this.chctx.alloc());

                final MqttPublishMessage mqttPublishMessage = MqttPublishMessage.create(
                        publish.variableHeader().messageId(),
                        publish.fixedHeader().qosLevel(),
                        publish.fixedHeader().isDup(),
                        publish.fixedHeader().isRetain(),
                        publish.variableHeader().topicName(),
                        newBuf);
                handlePublish(mqttPublishMessage);
                break;

            case PUBACK:
                handlePuback(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
                break;

            case PUBREC:
                handlePubrec(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
                break;

            case PUBREL:
                handlePubrel(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
                break;

            case PUBCOMP:
                handlePubcomp(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
                break;

            case SUBACK:

                final io.netty.handler.codec.mqtt.MqttSubAckMessage unsuback = (io.netty.handler.codec.mqtt.MqttSubAckMessage) mqttMessage;

                final MqttSubAckMessage mqttSubAckMessage = MqttSubAckMessage.create(
                        unsuback.variableHeader().messageId(),
                        unsuback.payload().grantedQoSLevels());
                handleSuback(mqttSubAckMessage);
                break;

            case UNSUBACK:
                handleUnsuback(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
                break;

            case PINGRESP:
                handlePingresp();
                break;

            default:

                this.chctx.pipeline()
                        .fireExceptionCaught(new Exception("Wrong message type " + msg.getClass().getName()));
                break;
            }

        } else {

            this.chctx.pipeline().fireExceptionCaught(new Exception("Wrong message type"));
        }
    }

    /**
     * Used for calling the pingresp handler when the server replies to the ping
     */
    synchronized private void handlePingresp() {
        this.client.handlePingresp();
    }

    /**
     * Used for calling the unsuback handler when the server acks an unsubscribe
     *
     * @param unsubackMessageId
     *            identifier of the subscribe acknowledged by the server
     */
    synchronized private void handleUnsuback(final int unsubackMessageId) {
        this.client.handleUnsuback(unsubackMessageId);
    }

    /**
     * Used for calling the suback handler when the server acknoweldge subscribe to
     * topics
     *
     * @param msg
     *            message with suback information
     */
    synchronized private void handleSuback(final MqttSubAckMessage msg) {
        this.client.handleSuback(msg);
    }

    /**
     * Used for calling the pubcomp handler when the server client acknowledge a QoS
     * 2 message with pubcomp
     *
     * @param pubcompMessageId
     *            identifier of the message acknowledged by the server
     */
    synchronized private void handlePubcomp(final int pubcompMessageId) {
        this.client.handlePubcomp(pubcompMessageId);
    }

    /**
     * Used for calling the puback handler when the server acknowledge a QoS 1
     * message with puback
     *
     * @param pubackMessageId
     *            identifier of the message acknowledged by the server
     */
    synchronized private void handlePuback(final int pubackMessageId) {
        this.client.handlePuback(pubackMessageId);
    }

    /**
     * Used for calling the pubrel handler when the server acknowledge a QoS 2
     * message with pubrel
     *
     * @param pubrelMessageId
     *            identifier of the message acknowledged by the server
     */
    synchronized private void handlePubrel(final int pubrelMessageId) {
        this.client.handlePubrel(pubrelMessageId);
    }

    /**
     * Used for calling the publish handler when the server publishes a message
     *
     * @param msg
     *            published message
     */
    synchronized private void handlePublish(final MqttPublishMessage msg) {
        this.client.handlePublish(msg);
    }

    /**
     * Used for sending the pubrel when a pubrec is received from the server
     *
     * @param pubrecMessageId
     *            identifier of the message acknowledged by server
     */
    synchronized private void handlePubrec(final int pubrecMessageId) {
        this.client.handlePubrec(pubrecMessageId);
    }

    /**
     * Used for calling the connect handler when the server replies to the request
     *
     * @param msg
     *            connection response message
     */
    synchronized private void handleConnack(final MqttConnAckMessage msg) {
        this.client.handleConnack(msg);
    }

    /**
     * Close the NetSocket
     */
    void close() {
        this.so.close();
    }

    /**
     * Write the message to socket
     * 
     * @param mqttMessage
     *            message
     */
    void writeMessage(final MqttMessage mqttMessage) {
        this.so.writeMessage(mqttMessage);
    }
}
