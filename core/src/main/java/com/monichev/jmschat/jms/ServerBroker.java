package com.monichev.jmschat.jms;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monichev.jmschat.entity.MessageEntity;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerBroker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerBroker.class);
    private final BrokerService broker;
    private final ObjectMapper mapper = new ObjectMapper();
    private Session session;
    private MessageProducer replyProducer;
    private final Destination serverQueue;
    private final MessageProducer producer;
    private final Map<String, Destination> clientQueues = new HashMap<>();

    public ServerBroker(int port, Consumer<MessageEntity> messageReceived, Consumer<String> messageDelivered) throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("Server");
        broker.setPersistent(true);
        broker.setTransportConnectorURIs(new String[]{ "http://0.0.0.0:" + port });
        broker.start();

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(broker.getVmConnectorURI());
        Connection connection;
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            serverQueue = this.session.createQueue("server");

            producer = session.createProducer(null);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            //Setup a message producer to respond to messages from clients, we will get the destination
            //to send to from the JMSReplyTo header field from a Message
            replyProducer = session.createProducer(null);
            replyProducer.setDeliveryMode(DeliveryMode.PERSISTENT);

            //Set up a consumer to consume messages off of the admin queue
            MessageConsumer consumer = session.createConsumer(serverQueue);
            consumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    try {
                        if (message instanceof TextMessage) {
                            TextMessage incomingMessage = (TextMessage) message;
                            String text = incomingMessage.getText();
                            try {
                                MessageEntity value = mapper.readValue(text, MessageEntity.class);
                                messageReceived.accept(value);

                                Message response = session.createMessage();
                                response.setJMSCorrelationID(message.getJMSCorrelationID());
                                producer.send(message.getJMSReplyTo(), response);
                                clientQueues.put(value.getFrom(), message.getJMSReplyTo());
                            } catch (IOException e) {
                                LOGGER.error("Can't parse message: " + text, e);
                            }
                        } else {
                            messageDelivered.accept(message.getJMSCorrelationID());
                        }
                    } catch (JMSException e) {
                        throw new IllegalStateException("Can't handle message", e);
                    }
                }
            });
        } catch (JMSException e) {
            throw new IllegalStateException("Can't setup message queue consumer");
        }
    }

    public void sendMessage(MessageEntity message) {
        String to = message.getTo();
        Destination destination = clientQueues.get(to);
        if (destination == null) {
            LOGGER.error("Can't find destination for client: " + to);
        }
        String correlationId = UUID.randomUUID().toString();
        message.setId(correlationId);
        if (message.getTimestamp() == null) {
            message.setTimestamp(Timestamp.valueOf(LocalDateTime.now()));
        }
        String json;
        try {
            json = mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            LOGGER.error("Can't create JSON from" + message);
            return;
        }
        try {
            TextMessage textMessage = session.createTextMessage();
            textMessage.setText(json);

            textMessage.setJMSReplyTo(serverQueue);

            textMessage.setJMSCorrelationID(correlationId);
            producer.send(destination, textMessage);
        } catch (JMSException e) {
            LOGGER.error("Can't send message", e);
        }
    }
}
