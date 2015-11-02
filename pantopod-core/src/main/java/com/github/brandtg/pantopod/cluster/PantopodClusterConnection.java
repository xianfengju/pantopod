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
package com.github.brandtg.pantopod.cluster;

import io.dropwizard.lifecycle.Managed;
import com.github.brandtg.pantopod.consumer.PantopodKafkaConsumerManager;
import org.apache.helix.HelixConnection;
import org.apache.helix.HelixParticipant;
import org.apache.helix.api.id.ClusterId;
import org.apache.helix.api.id.ParticipantId;
import org.apache.helix.api.id.StateModelDefId;
import org.apache.helix.manager.zk.ZkHelixConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PantopodClusterConnection implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(PantopodClusterConnection.class);

  private final String zkConnectString;
  private final ClusterId clusterId;
  private final ParticipantId participantId;
  private final PantopodKafkaConsumerManager consumerManager;

  private HelixConnection connection;
  private HelixParticipant participant;

  public PantopodClusterConnection(String zkConnectString,
                                   ClusterId clusterId,
                                   ParticipantId participantId,
                                   PantopodKafkaConsumerManager consumerManager) {
    this.zkConnectString = zkConnectString;
    this.clusterId = clusterId;
    this.participantId = participantId;
    this.consumerManager = consumerManager;
  }

  @Override
  public void start() throws Exception {
    LOG.info("Connecting to {}", zkConnectString);
    connection = new ZkHelixConnection(zkConnectString);
    connection.connect();

    participant = connection.createParticipant(clusterId, participantId);
    participant.getStateMachineEngine().registerStateModelFactory(
        StateModelDefId.OnlineOffline, new PantopodStateTransitionHandlerFactory(consumerManager));

    LOG.info("Starting participant {} :: {}", clusterId, participantId);
    participant.start();
  }

  @Override
  public void stop() throws Exception {
    LOG.info("Stopping participant {} :: {}", clusterId, participantId);
    participant.stop();

    LOG.info("Disconnecting from cluster {}", zkConnectString);
    connection.disconnect();
  }
}
