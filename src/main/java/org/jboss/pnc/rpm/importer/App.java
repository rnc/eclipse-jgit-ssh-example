/*
 * Copyright 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.rpm.importer;

import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.jboss.pnc.rpm.importer.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.logging.Log;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@TopCommand
@CommandLine.Command(
        name = "jgit-cli",
        description = "",
        mixinStandardHelpOptions = true)
public class App implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    @Option(names = "--profile", description = "PNC Configuration profile")
    private String profile = "default";

    @Option(names = "--url", description = "External URL to distgit repository", required = true)
    private String url;

    @Option(names = "--branch", description = "Branch in distgit repository")
    private String branch;

    @Override
    public void run() {
        // This is not ideal - while there should be native java transports to use
        // the ssh agent I couldn't get them to work. According to
        // https://gerrit.googlesource.com/jgit/+/refs/heads/servlet-4/org.eclipse.jgit.ssh.apache/README.md
        // setting GIT_SSH means it will use native git to communicate.
        // Entered https://github.com/eclipse-jgit/jgit/issues/216 and https://github.com/eclipse-jgit/jgit/issues/215
        if (System.getenv("GIT_SSH") == null) {
            log.error("Define GIT_SSH=/bin/ssh in the environment.");
            //            return;
        }

        cloneRepository(url, branch);

    }

    Path cloneRepository(String url, String branch) {
        Path path = Utils.createTempDirForCloning();
        log.info("Using {} for repository", path);
        StringWriter writer = new StringWriter();
        TextProgressMonitor monitor = new TextProgressMonitor(writer) {
            // Don't want percent updates, just final summaries.
            protected void onUpdate(String taskName, int workCurr, Duration duration) {
            }

            protected void onUpdate(String taskName, int cmp, int totalWork, int pcnt, Duration duration) {
            }
        };
        monitor.showDuration(true);

        var repoClone = Git.cloneRepository()
                .setURI(url)
                .setProgressMonitor(monitor)
                .setBranch(branch)
                .setDirectory(path.toFile());
        try (var ignored = repoClone.call()) {
            Log.infof("Clone summary:\n%s", writer.toString().replaceAll("(?m)^\\s+", ""));
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        }
        return path;
    }

}
