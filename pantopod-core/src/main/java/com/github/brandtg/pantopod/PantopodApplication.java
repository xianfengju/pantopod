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
package com.github.brandtg.pantopod;

import com.github.brandtg.pantopod.consumer.PantopodEventHandler;
import io.dropwizard.Application;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import com.github.brandtg.pantopod.tor.TorProxyManager;
import com.github.brandtg.pantopod.cluster.PantopodClusterConnection;
import com.github.brandtg.pantopod.consumer.PantopodKafkaConsumerManager;
import com.github.brandtg.pantopod.crawler.DbiBasedCrawlingEventHandler;
import com.github.brandtg.pantopod.crawler.FileBasedCrawlingEventHandler;
import org.apache.helix.api.id.ClusterId;
import org.apache.helix.api.id.ParticipantId;
import org.apache.http.client.HttpClient;
import org.skife.jdbi.v2.DBI;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

public class PantopodApplication extends Application<PantopodConfiguration> {

  public static void main(final String[] args) throws Exception {
    new PantopodApplication().run(args);
  }

  @Override
  public String getName() {
    return "Pantopod";
  }

  @Override
  public void initialize(final Bootstrap<PantopodConfiguration> bootstrap) {
    // TODO: application initialization
  }

  @Override
  public void run(final PantopodConfiguration config, final Environment environment) {
    // Tor watchdog scheduler
    ScheduledExecutorService torWatchdogScheduler = environment.lifecycle()
        .scheduledExecutorService("tor-watchdog", true).build();

    // Tor
    if (config.isUseTor()) {
      TorProxyManager proxyManager = new TorProxyManager(
          config.getTorRootDir(),
          config.getSocksPort(),
          config.getControlPort(),
          config.getTorExecutable(),
          torWatchdogScheduler,
          config.getWatchdogDelayMillis());
      environment.lifecycle().manage(proxyManager);
    }

    // Kafka executors
    ExecutorService kafkaExecutors = environment.lifecycle().executorService("kafka-executors").build();

    // Kafka event handler
    final HttpClient httpClient = new HttpClientBuilder(environment)
        .using(config.getHttpClient())
        .build("crawler-client");
    PantopodEventHandler eventHandler;
    if ("database".equalsIgnoreCase(config.getHandlerType())) {
      DBIFactory factory = new DBIFactory();
      DBI jdbi = factory.build(environment, config.getDataSourceFactory(), "pantopod-database");
      eventHandler = new DbiBasedCrawlingEventHandler(httpClient, jdbi);
    } else {
      eventHandler = new FileBasedCrawlingEventHandler(httpClient, new File(config.getOutputDir()));
    }

    // Kafka
    String groupId = config.getKafkaGroupId();
    if ("RANDOM".equals(groupId)) {
      groupId = UUID.randomUUID().toString();
    }
    PantopodKafkaConsumerManager consumerManager = new PantopodKafkaConsumerManager(
        config.getZkConnectString(),
        config.getKafkaBrokerList(),
        groupId,
        kafkaExecutors,
        eventHandler);
    environment.lifecycle().manage(consumerManager);

    // Helix
    PantopodClusterConnection clusterConnection = new PantopodClusterConnection(
        config.getZkConnectString(),
        ClusterId.from(config.getHelixClusterName()),
        ParticipantId.from(config.getHelixParticipantName()),
        consumerManager);
    environment.lifecycle().manage(clusterConnection);
  }
}
