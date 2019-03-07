package com.monichev.jmschat.server.service;

import org.apache.activemq.broker.BrokerService;
import org.springframework.stereotype.Service;

@Service
public class EmbeddedBrokerService {
    private final BrokerService broker;

    public EmbeddedBrokerService() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("Server");
        broker.setPersistent(true);
        broker.setTransportConnectorURIs(new String[]{ "tcp://localhost:61616" });
        broker.start();
    }
}
