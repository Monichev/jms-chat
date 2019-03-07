package com.monichev.jmschat.client.service;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.network.NetworkConnector;
import org.springframework.stereotype.Service;

@Service
public class EmbeddedBrokerService {
    private final BrokerService broker;

    public EmbeddedBrokerService() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("Client");
        broker.setPersistent(true);
        NetworkConnector connector = broker.addNetworkConnector("static:(tcp://localhost:61616)");
        connector.setDuplex(true);
        broker.start();
    }
}
