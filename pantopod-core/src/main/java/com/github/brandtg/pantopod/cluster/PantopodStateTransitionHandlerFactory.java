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
import org.apache.helix.api.StateTransitionHandlerFactory;
import org.apache.helix.api.id.PartitionId;
import org.apache.helix.api.id.ResourceId;

public class PantopodStateTransitionHandlerFactory extends StateTransitionHandlerFactory<PantopodStateTransitionHandler> {
  private final PantopodKafkaConsumerManager consumerManager;

  public PantopodStateTransitionHandlerFactory(PantopodKafkaConsumerManager consumerManager) {
    this.consumerManager = consumerManager;
  }

  @Override
  public PantopodStateTransitionHandler createStateTransitionHandler(ResourceId resourceId, PartitionId partitionId) {
    return new PantopodStateTransitionHandler(consumerManager);
  }
}
