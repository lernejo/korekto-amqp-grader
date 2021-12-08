package com.github.lernejo.korekto.grader.amqp.parts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.github.lernejo.korekto.grader.amqp.ChatApiClient;
import com.github.lernejo.korekto.grader.amqp.LaunchingContext;
import com.github.lernejo.korekto.toolkit.Exercise;
import com.github.lernejo.korekto.toolkit.GradePart;
import com.github.lernejo.korekto.toolkit.GradingConfiguration;
import com.github.lernejo.korekto.toolkit.misc.Ports;
import com.github.lernejo.korekto.toolkit.thirdparty.git.GitContext;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenExecutionHandle;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenExecutor;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenReader;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import retrofit2.Response;

public class Part3Grader implements PartGrader {

    public static final String QUEUE_NAME = "chat_messages";
    private final ChatApiClient client;
    private final Random random = new Random();

    public Part3Grader(ChatApiClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "Part 3 - Listener AMQP & Server HTTP";
    }

    @Override
    public Double maxGrade() {
        return 4.0D;
    }

    @Override
    public GradePart grade(GradingConfiguration configuration, Exercise exercise, LaunchingContext context, GitContext gitContext) {
        if (context.compilationFailed) {
            return result(List.of("Not trying to start server as compilation failed"), 0.0D);
        }

        ConnectionFactory factory = context.newConnectionFactory();
        deleteQueue(factory, QUEUE_NAME);
        context.modules = MavenReader.readModel(exercise).getModules();
        String serverModuleSpec = context.modules.size() > 0 ? "-pl :server " : "";
        try
            (MavenExecutionHandle handle = MavenExecutor.executeGoalAsync(exercise, configuration.getWorkspace(),
                "org.springframework.boot:spring-boot-maven-plugin:2.5.5:run " + serverModuleSpec + " -Dspring-boot.run.jvmArguments='-Dserver.port=8085 -Dspring.rabbitmq.port=" + context.rabbitPort + "'")) {

            Ports.waitForPortToBeListenedTo(8085, TimeUnit.SECONDS, 40L);

            double grade = maxGrade();
            List<String> errors = new ArrayList<>();

            try {
                Response<List<String>> messagesResponse = client.getMessages().execute();
                if (!messagesResponse.isSuccessful()) {
                    grade -= maxGrade() / 2;
                    errors.add("Unsuccessful response of GET /api/todo: " + messagesResponse.code());
                } else {
                    if (!messagesResponse.body().isEmpty()) {
                        grade -= maxGrade() / 2;
                        errors.add("GET /api/todo should respond with an empty list when no message was sent, but got: *" + messagesResponse.body().size() + " messages");
                    }
                }

                try (Connection connection = factory.newConnection();
                     Channel channel = connection.createChannel()) {

                    boolean queueExists = doesQueueExists(connection, QUEUE_NAME);
                    if (!queueExists) {
                        grade -= maxGrade() / 2;
                        errors.add("No queue named `" + QUEUE_NAME + "` was created by the server when starting");
                    } else {
                        int callNbr = random.nextInt(6) + 3;

                        AMQP.BasicProperties basicProperties = new AMQP.BasicProperties().builder().contentType("text/plain").deliveryMode(2).build();
                        for (int i = 0; i < callNbr; i++) {
                            channel.basicPublish("", QUEUE_NAME, true, false, basicProperties, ("hello-" + i).getBytes(StandardCharsets.UTF_8));
                        }

                        try {
                            TimeUnit.MILLISECONDS.sleep(500L);
                        } catch (InterruptedException e) {
                            throw new IllegalStateException("Sleep have been interrupted!");
                        }

                        Response<List<String>> secMessagesResponse = client.getMessages().execute();
                        if (!secMessagesResponse.isSuccessful()) {
                            grade -= maxGrade() / 2;
                            errors.add("Unsuccessful response of GET /api/todo: " + secMessagesResponse.code());
                        } else {
                            if (secMessagesResponse.body().size() != callNbr) {
                                grade -= maxGrade() / 2;
                                errors.add("GET /api/message should respond a list of " + callNbr + " messages (messages sent), but was: " + secMessagesResponse.body().size());
                            }
                        }
                    }

                } catch (IOException | TimeoutException e) {
                    throw new IllegalStateException("Could not connect to the dockerized RabbitMQ", e);
                }

            } catch (RuntimeException e) {
                grade -= maxGrade() / 2;
                errors.add("Unsuccessful response of POST /api/message: " + e.getMessage());
            }

            return result(errors, grade);
        } catch (CancellationException e) {
            return result(List.of("Server failed to start within 20 sec."), 0.0D);
        } catch (RuntimeException e) {
            return result(List.of("Unwanted error during API invocation: " + e.getMessage()), 0.0D);
        } catch (IOException e) {
            return result(List.of("Fail to call server: " + e.getMessage()), 0.0D);
        } finally {
            Ports.waitForPortToBeFreed(8085, TimeUnit.SECONDS, 5L);
        }
    }

    private boolean doesQueueExists(Connection connection, String queueName) {
        try (Channel channel = connection.createChannel()) {
            AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(queueName);
            return declareOk != null;
        } catch (IOException | TimeoutException e) {
            return false;
        }
    }
}
