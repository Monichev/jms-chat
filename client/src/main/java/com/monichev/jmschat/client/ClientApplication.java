package com.monichev.jmschat.client;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication(exclude = ActiveMQAutoConfiguration.class)
public class ClientApplication {

    public static void main(String[] args) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(ClientApplication.class);
        builder.headless(false).run(args);
    }
}
