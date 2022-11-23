package com.github.lernejo.korekto.grader.amqp;

import com.github.lernejo.korekto.toolkit.GradingConfiguration;
import com.github.lernejo.korekto.toolkit.GradingContext;
import com.github.lernejo.korekto.toolkit.partgrader.MavenContext;
import com.rabbitmq.client.ConnectionFactory;

import java.util.ArrayList;
import java.util.List;

public class LaunchingContext extends GradingContext implements MavenContext {
    public final Integer rabbitPort;
    public final long SERVER_START_TIMEOUT = Long.valueOf(System.getProperty("SERVER_START_TIMEOUT", "40"));
    private boolean compilationFailed;
    private boolean testFailed;
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

    @Override
    public boolean hasCompilationFailed() {
        return compilationFailed;
    }

    @Override
    public boolean hasTestFailed() {
        return testFailed;
    }

    @Override
    public void markAsCompilationFailed() {
        compilationFailed = true;
    }

    @Override
    public void markAsTestFailed() {
        testFailed = true;
    }
}
