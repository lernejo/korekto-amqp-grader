package com.github.lernejo.korekto.grader.amqp.parts;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.github.lernejo.korekto.grader.amqp.LaunchingContext;
import com.github.lernejo.korekto.toolkit.Exercise;
import com.github.lernejo.korekto.toolkit.GradePart;
import com.github.lernejo.korekto.toolkit.GradingConfiguration;
import com.github.lernejo.korekto.toolkit.misc.SubjectForToolkitInclusion;
import com.github.lernejo.korekto.toolkit.thirdparty.git.GitContext;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@SubjectForToolkitInclusion
public interface PartGrader {

    String name();

    default Double maxGrade() {
        return null;
    }

    default double minGrade() {
        return 0.0D;
    }

    GradePart grade(GradingConfiguration configuration, Exercise exercise, LaunchingContext context, GitContext gitContext);

    default GradePart result(List<String> explanations, double grade) {
        return new GradePart(name(), Math.min(Math.max(minGrade(), grade), maxGrade()), maxGrade(), explanations);
    }

    default void deleteQueue(ConnectionFactory factory, String queueName) {
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDelete(queueName);
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException("Could not connect to the dockerized RabbitMQ", e);
        }
    }
}
