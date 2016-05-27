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
package com.github.brandtg.pantopod.consumer;

import io.dropwizard.lifecycle.Managed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class PantopodKafkaConsumerManager implements Managed {
  private final String zkConnectionString;
  private final String kafkaGroupId;
  private final String kafkaBrokerList;
  private final ExecutorService executorService;
  private final PantopodEventHandler eventHandler;
  private final Map<String, List<PantopodKafkaConsumer>> consumers;

  public PantopodKafkaConsumerManager(String zkConnectionString,
                                      String kafkaBrokerList,
                                      String kafkaGroupId,
                                      ExecutorService executorService,
                                      PantopodEventHandler eventHandler) {
    this.zkConnectionString = zkConnectionString;
    this.kafkaGroupId = kafkaGroupId;
    this.kafkaBrokerList = kafkaBrokerList;
    this.executorService = executorService;
    this.eventHandler = eventHandler;
    this.consumers = new HashMap<>();
  }

  public void start(String kafkaTopic, String uriChroot, String startPage) throws Exception {
    synchronized (consumers) {
      PantopodKafkaConsumer consumer = new PantopodKafkaConsumer(
          zkConnectionString,
          kafkaBrokerList,
          kafkaGroupId,
          kafkaTopic,
          executorService,
          eventHandler,
          uriChroot,
          startPage);
      List<PantopodKafkaConsumer> consumerList = consumers.get(kafkaTopic);
      if (consumerList == null) {
        consumerList = new ArrayList<>();
        consumers.put(kafkaTopic, consumerList);
      }
      consumerList.add(consumer);
      consumer.start();
    }
  }

  public void stop(String kafkaTopic) throws Exception {
    synchronized (consumers) {
      List<PantopodKafkaConsumer> consumerList = consumers.get(kafkaTopic);
      if (consumerList != null && !consumerList.isEmpty()) {
        PantopodKafkaConsumer consumer = consumerList.remove(consumerList.size() - 1);
        consumer.stop();
      }
    }
  }

  @Override
  public void start() throws Exception {
    // NOP
  }

  @Override
  public void stop() throws Exception {
    synchronized (consumers) {
      for (List<PantopodKafkaConsumer> consumerList : consumers.values()) {
        for (PantopodKafkaConsumer consumer : consumerList) {
          consumer.stop();
        }
      }
    }
  }
}
