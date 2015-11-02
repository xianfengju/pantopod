/**
 * Copyright (C) 2015 Greg Brandt (brandt.greg@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.brandtg.pantopod.tor;

import io.dropwizard.lifecycle.Managed;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TorProxyManager implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(TorProxyManager.class);
  private static final String PID_FILE_NAME = "tor.pid";

  private final String torRootDir;
  private final int socksPort;
  private final int controlPort;
  private final String torExecutable;
  private final ScheduledExecutorService scheduler;
  private final long watchdogDelayMillis;
  private final Object sync = new Object();

  private File pidFile;

  public TorProxyManager(String torRootDir,
                         int socksPort,
                         int controlPort,
                         String torExecutable,
                         ScheduledExecutorService scheduler,
                         long watchdogDelayMillis) {
    this.torRootDir = torRootDir;
    this.socksPort = socksPort;
    this.controlPort = controlPort;
    this.torExecutable = torExecutable;
    this.scheduler = scheduler;
    this.watchdogDelayMillis = watchdogDelayMillis;
  }

  @Override
  public void start() throws Exception {
    synchronized (sync) {
      File dataDir = new File(torRootDir, String.format("tor_%d", socksPort));
      pidFile = new File(dataDir, PID_FILE_NAME);

      if (pidFile.exists()) {
        LOG.info("Stopping existing process {}", pidFile);
        stopProcess(pidFile, 2 /* SIGINT */);
      }

      LOG.info("Creating {}", dataDir);
      FileUtils.forceMkdir(dataDir);

      String command = torExecutable
          + " --RunAsDaemon 1"
          + " --CookieAuthentication 0"
          + " --HashedControlPassword \"\""
          + " --ControlPort " + controlPort
          + " --PidFile " + pidFile.getAbsolutePath()
          + " --SocksPort " + socksPort
          + " --DataDirectory " + dataDir.getAbsolutePath();

      LOG.info("Executing {}", command);
      DefaultExecutor executor = new DefaultExecutor();
      int retCode = executor.execute(CommandLine.parse(command));
      LOG.info("Executed {} #=> {}", command, retCode);

      // Set the system socket proxy
      System.setProperty("socksProxyHost", "localhost");
      System.setProperty("socksProxyPort", String.valueOf(socksPort));

      // Schedule a watchdog to periodically send SIGHUP, which resets the tor circuit
      if (watchdogDelayMillis > 0) {
        scheduler.schedule(new SigHupWatchdog(pidFile), watchdogDelayMillis, TimeUnit.MILLISECONDS);
      }
    }
  }

  @Override
  public void stop() throws Exception {
    synchronized (sync) {
      if (pidFile.exists()) {
        stopProcess(pidFile, 2 /* SIGINT */);
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
      }
    }
  }

  public void reset() throws Exception {
    synchronized (sync) {
      stop();
      start();
    }
  }

  private static void stopProcess(File pidFile, int signalNumber) throws Exception {
    try (InputStream inputStream = new FileInputStream(pidFile)) {
      Integer pid = Integer.valueOf(IOUtils.toString(inputStream).trim());
      String command = String.format("KILL -%d %d", signalNumber, pid);
      LOG.info("Executing {}", command);
      DefaultExecutor executor = new DefaultExecutor();
      int retCode = executor.execute(CommandLine.parse(command));
      LOG.info("{} exited with {}", command, retCode);
    } catch (Exception e) {
      LOG.error("Could not kill {}", pidFile, e);
    }
  }

  private class SigHupWatchdog implements Runnable {
    private final File pidFile;

    SigHupWatchdog(File pidFile) {
      this.pidFile = pidFile;
    }

    @Override
    public void run() {
      if (pidFile.exists()) {
        try {
          stopProcess(pidFile, 1);

          // Keep going as long as exists
          scheduler.schedule(new SigHupWatchdog(pidFile), watchdogDelayMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
          LOG.warn("Could not send SIGHUP to {}", pidFile);
        }
      }
    }
  }
}
