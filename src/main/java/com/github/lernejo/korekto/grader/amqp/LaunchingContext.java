package com.github.lernejo.korekto.grader.amqp;

import java.util.ArrayList;
import java.util.List;

import com.github.lernejo.korekto.toolkit.GradingConfiguration;
import com.github.lernejo.korekto.toolkit.GradingContext;
import com.rabbitmq.client.ConnectionFactory;

public class LaunchingContext extends GradingContext {
    public final Integer rabbitPort;
    public final long SERVER_START_TIMEOUT = Long.valueOf(System.getProperty("SERVER_START_TIMEOUT", "40"));
    public boolean compilationFailed;
    public boolean testFailed;
    public List<String> modules = new ArrayList<>();

    public LaunchingContext(GradingConfiguration configuration, Integer rabbitPort) {
        super(configuration);
        this.rabbitPort = rabbitPort;
    }

    public ConnectionFactory newConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setPort(rabbitPort);
        return factory;
    }
}
