package com.github.lernejo.korekto.grader.amqp;

import com.github.lernejo.korekto.toolkit.*;
import com.github.lernejo.korekto.toolkit.misc.OS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AmqpGraderTest {

    private final Path workspace = Paths.get("target/repositories");

    @BeforeEach
    void setUp() {
        OS.Companion.getCURRENT_OS().deleteDirectoryCommand(workspace);
    }

    @Test
    @Disabled("missing target lernejo private project")
    void nominal_project() {
        Grader grader = Grader.Companion.load();
        String repoUrl = grader.slugToRepoUrl("lernejo");
        GradingConfiguration configuration = new GradingConfiguration(repoUrl, "", "", workspace);

        AtomicReference<GradingContext> contextHolder = new AtomicReference<>();
        new GradingJob()
            .addCloneStep()
            .addStep("grading", grader)
            .addStep("report", (conf, context) -> contextHolder.set(context))
            .run(configuration);

        assertThat(contextHolder)
            .as("Grading context")
            .hasValueMatching(Objects::nonNull, "is present");

        assertThat(contextHolder.get().getGradeDetails().getParts())
            .containsExactly(
                new GradePart("Part 1 - Compilation & Tests", 2.0D, 4.0D, List.of()),
                new GradePart("Part 2 - CI", 1.0D, 1.0D, List.of())
            );
    }
}
