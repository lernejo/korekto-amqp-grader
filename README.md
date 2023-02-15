# korekto-amqp-grader
Grader for AMQP exercise

[![Build](https://github.com/lernejo/korekto-amqp-grader/actions/workflows/build.yml/badge.svg)](https://github.com/lernejo/korekto-amqp-grader/actions)

## Launch locally

This **grader** uses [Testcontainers](https://www.testcontainers.org/) which needs Docker.  
On Windows this means that the Docker engine must be running.

To launch the tool locally, run `com.github.lernejo.korekto.toolkit.launcher.GradingJobLauncher` with the
argument `-s=mySlug`

### With Maven

```bash
mvn compile exec:java -Dexec.args="-s=yourGitHubLogin"
```

### With intelliJ

![Demo Run Configuration](https://raw.githubusercontent.com/lernejo/korekto-toolkit/main/docs/demo_run_configuration.png)

## Timeouts

Timeouts are overridable using the following system properties:
* `-Dserver_start_timeout=40` to wait at most 50 sec for the server to start
* `-Dqueue_read_timeout=4` to await at most 4 sec for reading messages from the queue

## GitHub API rate limiting

When using the grader a lot, GitHub may block API calls for a certain amount of time (criterias change regularly).
This is because by default GitHub API are accessed anonymously.

To increase the API usage quota, use a [Personal Access Token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) in order to access GitHub APIs authenticated.

Such a token can be supplied to the grader tool through the system property : `-Dgithub_token=<your token>`

Like so:

```bash
mvn compile exec:java -Dexec.args="-s=yourGitHubLogin" -Dgithub_token=<your token>
```
