package com.github.lernejo.korekto.grader.amqp;

import com.github.lernejo.korekto.toolkit.GradingConfiguration;
import com.github.lernejo.korekto.toolkit.GradingContext;
import com.github.lernejo.korekto.toolkit.thirdparty.amqp.AmqpCapable;
import com.rabbitmq.client.ConnectionFactory;

import java.util.ArrayList;
import java.util.List;

public class LaunchingContext extends GradingContext {
    public final Integer rabbitPort;
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
