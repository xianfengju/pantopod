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

import com.github.brandtg.pantopod.consumer.PantopodKafkaConsumerManager;
import org.apache.helix.NotificationContext;
import org.apache.helix.api.TransitionHandler;
import org.apache.helix.model.HelixConfigScope;
import org.apache.helix.model.Message;
import org.apache.helix.model.builder.HelixConfigScopeBuilder;
import org.apache.helix.participant.statemachine.StateModelInfo;
import org.apache.helix.participant.statemachine.Transition;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

@StateModelInfo(states = "{'OFFLINE','ONLINE'}", initialState = "OFFLINE")
public class PantopodStateTransitionHandler extends TransitionHandler {
  private static final String CHROOT = "chroot";
  private static final String START_PAGE = "startPage";

  private final PantopodKafkaConsumerManager consumerManager;

  public PantopodStateTransitionHandler(PantopodKafkaConsumerManager consumerManager) {
    this.consumerManager = consumerManager;
  }

  @Transition(from = "OFFLINE", to = "ONLINE")
  public void onBecomeOnlineFromOffline(Message message, NotificationContext context) throws Exception {
    Map<String, String> resourceConfig = context
        .getManager()
        .getClusterManagmentTool()
        .getConfig(
            new HelixConfigScopeBuilder(HelixConfigScope.ConfigScopeProperty.RESOURCE)
                .forCluster(context.getManager().getClusterName())
                .forResource(message.getResourceName())
                .build(),
            Arrays.asList(CHROOT, START_PAGE));

    consumerManager.start(
        message.getResourceName(),
        resourceConfig.get(CHROOT),
        resourceConfig.get(START_PAGE));
  }

  @Transition(from = "ONLINE", to = "OFFLINE")
  public void onBecomeOfflineFromOnline(Message message, NotificationContext context) throws Exception {
    consumerManager.stop(message.getResourceName());
  }

  @Transition(from = "ERROR", to = "OFFLINE")
  public void onBecomeOfflineFromError(Message message, NotificationContext context) throws Exception {
    // NOP
  }
}
