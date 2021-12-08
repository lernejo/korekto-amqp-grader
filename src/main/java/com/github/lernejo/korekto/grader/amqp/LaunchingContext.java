package com.github.lernejo.korekto.grader.amqp;

import java.util.ArrayList;
import java.util.List;

import com.github.lernejo.korekto.toolkit.misc.SubjectForToolkitInclusion;
import com.rabbitmq.client.ConnectionFactory;

@SubjectForToolkitInclusion
public class LaunchingContext {
    public final Integer rabbitPort;
    public boolean compilationFailed;
    public boolean testFailed;
    public List<String> modules = new ArrayList<>();

    public LaunchingContext(Integer rabbitPort) {
        this.rabbitPort = rabbitPort;
    }

    public ConnectionFactory newConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setPort(rabbitPort);
        return factory;
    }
}
