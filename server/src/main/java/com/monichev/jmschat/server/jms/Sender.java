package com.monichev.jmschat.server.jms;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class Sender {
    private final JmsTemplate jmsTemplate;

    @Autowired
    public Sender(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void sendMessage(String queue, String message) {
        jmsTemplate.send(queue, session -> {
            System.out.println(queue + " << " + message);
            return session.createTextMessage(message);
        });
    }
}
