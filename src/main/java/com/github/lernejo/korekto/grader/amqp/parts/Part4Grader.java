package com.github.lernejo.korekto.grader.amqp.parts;

import com.github.lernejo.korekto.grader.amqp.ChatApiClient;
import com.github.lernejo.korekto.grader.amqp.LaunchingContext;
import com.github.lernejo.korekto.toolkit.GradePart;
import com.github.lernejo.korekto.toolkit.PartGrader;
import com.github.lernejo.korekto.toolkit.misc.Ports;
import com.github.lernejo.korekto.toolkit.misc.SubjectForToolkitInclusion;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenExecutionHandle;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenExecutor;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenInvocationResult;
import org.jetbrains.annotations.NotNull;
import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Response;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

public class Part4Grader implements PartGrader<LaunchingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Part4Grader.class);

    private final ChatApiClient client;
    private final Random random = new Random();

    private final long processReadTimeout = Long.parseLong(System.getProperty("PROCESS_READ_TIMEOUT", "400"));
    private final long processReadRetryDelay = Long.parseLong(System.getProperty("PROCESS_READ_RETRY_DELAY", "50"));

    public Part4Grader(ChatApiClient client) {
        this.client = client;
    }

    @Override
    public @NotNull String name() {
        return "Part 4 - Client AMQP & message limit";
    }

    @Override
    public @NotNull Double maxGrade() {
        return 4.0D;
    }

    @Override
    public @NotNull GradePart grade(LaunchingContext context) {
        if (context.compilationFailed) {
            return result(List.of("Not trying to start server as compilation failed"), 0.0D);
        }
        if (!context.modules.contains("client")) {
            return result(List.of("No *client* module defined in the root *pom.xml*"), 0.0D);
        }

        MavenInvocationResult result = MavenExecutor.executeGoal(context.getExercise(), context.getConfiguration().getWorkspace(),
            "dependency:build-classpath -DincludeScope=compile -Dmdep.outputFile=cp.txt -pl :client");
        Path cpFilePath = context.getExercise().getRoot().resolve("client").resolve("cp.txt");
        if (result.getStatus() != MavenInvocationResult.Status.OK) {
            return result(List.of("Unable to determine *client* module classpath: \n```" + result.getOutput() + "\n```"), 0.0D);
        } else {
            try {
                String cp = Files.readString(cpFilePath, StandardCharsets.UTF_8);
                String fullCp = "-cp " + context.getExercise().getRoot().resolve("client").resolve("target").resolve("classes") + File.pathSeparator + cp;
                Files.writeString(cpFilePath, fullCp);
            } catch (IOException e) {
                return result(List.of("Unable to generate CP file " + e.getMessage()), 0.0D);
            }
        }

        String mainClass = "fr.lernejo.chat.Launcher";
        ProcessBuilder processBuilder = new ProcessBuilder()
            .directory(context.getExercise().getRoot().toFile())
            .command(
                Paths.get(System.getProperty("java.home")).resolve("bin").resolve("java").toString(),
                "@client/cp.txt",
                "-Dspring.rabbitmq.port=" + context.rabbitPort,
                mainClass);

        try (CloseableProcess process = new CloseableProcess(processBuilder.start());
             MavenExecutionHandle ignored = MavenExecutor.executeGoalAsync(context.getExercise(), context.getConfiguration().getWorkspace(),
                 "org.springframework.boot:spring-boot-maven-plugin:2.5.5:run -pl :server -Dspring-boot.run.jvmArguments='-Dserver.port=8085 -Dspring.rabbitmq.port=" + context.rabbitPort + "'")) {

            Ports.waitForPortToBeListenedTo(8085, TimeUnit.SECONDS, 40L);

            if (!process.process().isAlive()) {
                String error = readStream(process.process().getErrorStream());
                return result(List.of("client crashed at launch: " + error), 0.0D);
            }

            double grade = maxGrade();
            List<String> errors = new ArrayList<>();

            int callNbr = random.nextInt(6) + 1;

            readAllOutputLogs(process);
            // Wait fot the client app to boot

            for (int i = 0; i < callNbr; i++) {
                writeInput(process.process(), "message " + i + "\n");
            }

            readAllOutputLogs(process);

            try {
                TimeUnit.MILLISECONDS.sleep(500L);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Sleep have been interrupted!");
            }

            Response<List<String>> response = client.getMessages().execute();
            if (!response.isSuccessful()) {
                grade = 0;
                errors.add("Unsuccessful response of GET /api/message: " + response.code());
            } else {
                if (response.body().size() != callNbr) {
                    grade = 0;
                    errors.add("GET /api/message should respond a list of " + callNbr + " messages (messages sent), but was: " + response.body().size());
                }
            }

            int messagesToSend = 15 - callNbr;

            for (int i = callNbr; i < messagesToSend; i++) {
                writeInput(process.process(), "message " + i + "\n");
            }

            writeInput(process.process(), "q\n");
            readAllOutputLogs(process);
            int counter = 0;
            do {
                counter++;
                try {
                    TimeUnit.MILLISECONDS.sleep(50L);
                } catch (InterruptedException e) {
                    throw new IllegalStateException("Sleep have been interrupted!");
                }
            } while (counter < 20 && process.process().isAlive());


            // TODO replace with a queueDeclarePassive check on ready ?
            try {
                TimeUnit.MILLISECONDS.sleep(1000L);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Sleep have been interrupted!");
            }

            response = client.getMessages().execute();

            int maxMessages = 10;
            if (!response.isSuccessful()) {
                grade = 0;
                errors.add("Unsuccessful response of GET /api/message: " + response.code());
            } else {
                if (response.body().size() != maxMessages) {
                    grade -= maxGrade() / 2;
                    errors.add("GET /api/message should respond a list of the last *" + maxMessages + "* messages received but returned *" + response.body().size() + "*");
                }
            }
            return result(errors, grade);
        } catch (CancellationException e) {
            return result(List.of("Server failed to start within 20 sec."), 0.0D);
        } catch (RuntimeException e) {
            return result(List.of("Unwanted error during API invocation: " + e.getMessage()), 0.0D);
        } catch (IOException e) {
            return result(List.of("Cannot start " + mainClass + ": " + e.getMessage()), 0.0D);
        } finally {
            Ports.waitForPortToBeFreed(8085, TimeUnit.SECONDS, 5L);
        }
    }

    private void readAllOutputLogs(CloseableProcess process) {
        String clientLog;
        do {
            clientLog = readOutput(process.process());
        } while (clientLog != null && clientLog.length() > 0);
    }

    @SubjectForToolkitInclusion
    private String readOutput(Process process) {
        return readStream(process.getInputStream());
    }

    private String readStream(InputStream inputStream) {
        long start = System.currentTimeMillis();
        do {
            try {
                TimeUnit.MILLISECONDS.sleep(processReadRetryDelay);
                StringBuilder sb = new StringBuilder();
                while (inputStream.available() > 0) {
                    byte[] bytes = inputStream.readNBytes(inputStream.available());
                    UniversalDetector detector = new UniversalDetector();
                    detector.handleData(bytes);
                    detector.dataEnd();
                    String detectedCharset = detector.getDetectedCharset();
                    sb.append(new String(bytes, detectedCharset != null ? Charset.forName(detectedCharset) : StandardCharsets.UTF_8));
                }
                String lineOutput = sb.toString().trim();
                if (lineOutput.length() == 0) {
                    continue;
                }
                return lineOutput;
            } catch (IOException | InterruptedException e) {
                LOGGER.warn("Unable to read process output: " + e.getMessage());
                return null;
            }
        } while (System.currentTimeMillis() - start < processReadTimeout);
        LOGGER.warn("No process output to read in " + processReadTimeout + " ms");
        return null;
    }

    @SubjectForToolkitInclusion
    private void writeInput(Process process, String s) {
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            writer.write(s);
            writer.flush();
            TimeUnit.MILLISECONDS.sleep(100L);
        } catch (IOException | InterruptedException e) {
            LOGGER.warn("Unable to write to process input: " + e.getMessage());
        }
    }
}
