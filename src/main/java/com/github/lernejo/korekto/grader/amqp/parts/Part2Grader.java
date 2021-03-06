package com.github.lernejo.korekto.grader.amqp.parts;

import com.github.lernejo.korekto.grader.amqp.LaunchingContext;
import com.github.lernejo.korekto.toolkit.GradePart;
import com.github.lernejo.korekto.toolkit.PartGrader;
import com.github.lernejo.korekto.toolkit.thirdparty.github.GitHubNature;
import com.github.lernejo.korekto.toolkit.thirdparty.github.WorkflowRun;
import com.github.lernejo.korekto.toolkit.thirdparty.github.WorkflowRunConclusion;
import com.github.lernejo.korekto.toolkit.thirdparty.github.WorkflowRunStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Part2Grader implements PartGrader<LaunchingContext> {
    private static final Set<String> mainBranchNames = Set.of("main", "master");

    @Override
    public @NotNull String name() {
        return "Part 2 - CI";
    }

    @Override
    public @NotNull Double maxGrade() {
        return 1.0D;
    }

    @Override
    public @NotNull GradePart grade(LaunchingContext context) {
        Optional<GitHubNature> gitHubNature = context.getExercise().lookupNature(GitHubNature.class);
        if (gitHubNature.isEmpty()) {
            return result(List.of("Not a GitHub project"), 0D);
        }
        List<WorkflowRun> actionRuns = gitHubNature.get().listActionRuns();

        List<WorkflowRun> mainRuns = actionRuns.stream()
            .filter(wr -> wr.getStatus() == WorkflowRunStatus.completed)
            .filter(wr -> mainBranchNames.contains(wr.getHead_branch()))
            .collect(Collectors.toList());
        if (mainRuns.isEmpty()) {
            return result(List.of("No CI runs for main branch, check https://github.com/" + context.getExercise().getName() + "/actions"), 0D);
        } else {
            WorkflowRun latestRun = mainRuns.get(0);
            if (latestRun.getConclusion() != WorkflowRunConclusion.success) {
                return result(List.of("Latest CI run is not in success state"), maxGrade() / 2);
            } else {
                return result(List.of(), maxGrade());
            }
        }
    }
}
