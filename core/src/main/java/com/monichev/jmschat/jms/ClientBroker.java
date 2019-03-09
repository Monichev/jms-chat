package com.monichev.jmschat.jms;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monichev.jmschat.entity.MessageEntity;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.network.NetworkConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientBroker implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientBroker.class);
    private final BrokerService brokerService;
    private final MessageProducer producer;
    private final Queue clientQueue;
    private final Connection connection;
    private final ObjectMapper mapper = new ObjectMapper();
    private Session session;

    public ClientBroker(String name, String remoteAddress, Consumer<MessageEntity> messageReceived, Consumer<String> messageDelivered) throws Exception {
        brokerService = new BrokerService();
        brokerService.setBrokerName(name);
        brokerService.setPersistent(true);
        NetworkConnector connector = brokerService.addNetworkConnector("static:(" + remoteAddress + ")");
        connector.setDuplex(true);
        brokerService.start();

        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(brokerService.getVmConnectorURI());
        connection = activeMQConnectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue serverQueue = session.createQueue("server");
        producer = session.createProducer(serverQueue);
        producer.setDeliveryMode(DeliveryMode.PERSISTENT);

        MessageProducer producer = session.createProducer(null);
        producer.setDeliveryMode(DeliveryMode.PERSISTENT);

        clientQueue = session.createQueue(name);
        MessageConsumer consumer = session.createConsumer(clientQueue);
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                try {
                    if (message instanceof TextMessage) {
                        TextMessage incomingMessage = (TextMessage) message;
                        String json = incomingMessage.getText();
                        try {
                            MessageEntity value = mapper.readValue(json, MessageEntity.class);
                            messageReceived.accept(value);
                        } catch (IOException e) {
                            LOGGER.error("Can't parse message: " + json, e);
                        }

                        Message response = session.createMessage();
                        response.setJMSCorrelationID(message.getJMSCorrelationID());
                        producer.send(message.getJMSReplyTo(), response);
                    } else {
                        messageDelivered.accept(message.getJMSCorrelationID());
                    }
                } catch (JMSException e) {
                    throw new IllegalStateException("Can't handle message", e);
                }
            }
        });
    }

    @Override
    public void close() throws Exception {
        producer.close();
        connection.stop();
        session.close();
        brokerService.stop();
    }

    public void sendMessage(MessageEntity message) throws JMSException {
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
        TextMessage textMessage = session.createTextMessage();
        textMessage.setText(json);

        textMessage.setJMSReplyTo(clientQueue);

        textMessage.setJMSCorrelationID(correlationId);
        producer.send(textMessage);
    }
}
