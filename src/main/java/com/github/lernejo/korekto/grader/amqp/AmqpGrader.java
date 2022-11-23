package com.github.lernejo.korekto.grader.amqp;

import com.github.lernejo.korekto.grader.amqp.parts.Part3Grader;
import com.github.lernejo.korekto.grader.amqp.parts.Part4Grader;
import com.github.lernejo.korekto.toolkit.GradePart;
import com.github.lernejo.korekto.toolkit.Grader;
import com.github.lernejo.korekto.toolkit.GradingConfiguration;
import com.github.lernejo.korekto.toolkit.PartGrader;
import com.github.lernejo.korekto.toolkit.misc.HumanReadableDuration;
import com.github.lernejo.korekto.toolkit.misc.Ports;
import com.github.lernejo.korekto.toolkit.misc.SubjectForToolkitInclusion;
import com.github.lernejo.korekto.toolkit.partgrader.GitHubActionsPartGrader;
import com.github.lernejo.korekto.toolkit.partgrader.MavenCompileAndTestPartGrader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SubjectForToolkitInclusion
public class AmqpGrader implements Grader<LaunchingContext> {

    private final Logger logger = LoggerFactory.getLogger(AmqpGrader.class);

    private final Retrofit retrofit = new Retrofit.Builder()
        .baseUrl("http://localhost:8085/")
        .addConverterFactory(JacksonConverterFactory.create())
        .build();

    public final ChatApiClient client = retrofit.create(ChatApiClient.class);

    private final GenericContainer genericContainer;

    public AmqpGrader() {
        this.genericContainer = new GenericContainer("rabbitmq:3.9.7-management-alpine");
        genericContainer.addExposedPorts(5672, 15672);
        try {
            genericContainer.start();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Unable to use Docker, make sure the Docker engine is started", e);
        }
        logger.info("Waiting for RabbitMQ to boot");
        Ports.waitForPortToBeListenedTo(genericContainer.getMappedPort(5672), TimeUnit.SECONDS, 20L);
        logger.info("RabbitMQ up (management on http://localhost:" + genericContainer.getMappedPort(15672) + " )");
    }

    @Override
    public void close() {
        genericContainer.stop();
    }

    @Override
    public String slugToRepoUrl(String slug) {
        return "https://github.com/" + slug + "/amqp_training";
    }

    @Override
    public boolean needsWorkspaceReset() {
        return true;
    }

    @NotNull
    @Override
    public LaunchingContext gradingContext(@NotNull GradingConfiguration configuration) {
        return new LaunchingContext(configuration, genericContainer.getMappedPort(5672));
    }

    @Override
    public void run(LaunchingContext context) {
        context.getGradeDetails().getParts().addAll(grade(context));
    }

    private Collection<? extends GradePart> grade(LaunchingContext context) {
        return graders().stream()
            .map(g -> applyPartGrader(context, g))
            .collect(Collectors.toList());
    }

    private GradePart applyPartGrader(LaunchingContext context, PartGrader<LaunchingContext> g) {
        long startTime = System.currentTimeMillis();
        try {
            return g.grade(context);
        } finally {
            logger.debug(g.name() + " in " + HumanReadableDuration.toString(System.currentTimeMillis() - startTime));
        }
    }

    private Collection<? extends PartGrader<LaunchingContext>> graders() {
        return List.of(
            new MavenCompileAndTestPartGrader<>("Part 1 - Compilation & Tests", 2.0D),
            new GitHubActionsPartGrader<>("Part 2 - CI", 1.0D),
            new Part3Grader(client),
            new Part4Grader(client)
        );
    }
}
