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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.brandtg.pantopod.api.CrawlEvent;
import io.dropwizard.lifecycle.Managed;
import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.javaapi.producer.Producer;
import kafka.message.MessageAndMetadata;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class PantopodKafkaConsumer implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(PantopodKafkaConsumer.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final String zkConnectionString;
  private final String kafkaGroupId;
  private final String kafkaTopic;
  private final String kafkaBrokerList;
  private final ExecutorService executorService;
  private final PantopodEventHandler eventHandler;
  private final String uriChroot;
  private final String startPage;
  private final AtomicBoolean isRunning;

  private Producer<byte[], byte[]> producer;
  private ConsumerConnector consumer;

  public PantopodKafkaConsumer(String zkConnectionString,
                               String kafkaBrokerList,
                               String kafkaGroupId,
                               String kafkaTopic,
                               ExecutorService executorService,
                               PantopodEventHandler eventHandler,
                               String uriChroot,
                               String startPage) {
    this.zkConnectionString = zkConnectionString;
    this.kafkaGroupId = kafkaGroupId;
    this.kafkaTopic = kafkaTopic;
    this.uriChroot = uriChroot;
    this.startPage = startPage;
    this.kafkaBrokerList = kafkaBrokerList;
    this.executorService = executorService;
    this.eventHandler = eventHandler;
    this.isRunning = new AtomicBoolean(false);
  }

  @Override
  public void start() throws Exception {
    if (!isRunning.getAndSet(true)) {
      // Producer config
      Properties producerProps = new Properties();
      producerProps.put("metadata.broker.list", kafkaBrokerList);
      producerProps.put("request.required.acks", "1");
      ProducerConfig producerConfig = new ProducerConfig(producerProps);

      // Producer
      producer = new Producer<byte[], byte[]>(producerConfig);

      // Consumer config
      Properties consumerProps = new Properties();
      consumerProps.put("zookeeper.connect", zkConnectionString);
      consumerProps.put("group.id", kafkaGroupId);
      consumerProps.put("zookeeper.session.timeout.ms", "400");
      consumerProps.put("zookeeper.sync.time.ms", "200");
      consumerProps.put("auto.commit.interval.ms", "1000");
      consumerProps.put("auto.offset.reset", "smallest");
      ConsumerConfig consumerConfig = new ConsumerConfig(consumerProps);

      // Consumer
      consumer = Consumer.createJavaConsumerConnector(consumerConfig);

      // Topic config
      Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
      topicCountMap.put(kafkaTopic, 1 /* one thread per topic */);
      Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = consumer.createMessageStreams(topicCountMap);
      List<KafkaStream<byte[], byte[]>> streams = consumerMap.get(kafkaTopic);

      // Consume streams
      for (final KafkaStream<byte[], byte[]> stream : streams) {
        LOG.info("Starting Kafka consumer for {}", kafkaTopic);
        executorService.submit(new Runnable() {
          @Override
          public void run() {
            ConsumerIterator<byte[], byte[]> it = stream.iterator();
            while (isRunning.get() && it.hasNext()) {
              MessageAndMetadata<byte[], byte[]> messageAndMetadata = it.next();
              try {
                CrawlEvent event = OBJECT_MAPPER.readValue(messageAndMetadata.message(), CrawlEvent.class);
                if (event.getUrl() != null && !URI.create(event.getUrl()).getPath().startsWith(uriChroot)) {
                  LOG.warn("Got event that does not match chroot={}: {}", uriChroot, event);
                }

                Set<CrawlEvent> nextEvents = eventHandler.handle(event);
                if (nextEvents != null) {
                  for (CrawlEvent nextEvent : nextEvents) {
                    byte[] encodedNextEvent = OBJECT_MAPPER.writeValueAsBytes(nextEvent);
                    producer.send(new KeyedMessage<byte[], byte[]>(kafkaTopic, encodedNextEvent));
                  }
                }
              } catch (Exception e) {
                LOG.error("Exception handling message!", e);
              }
            }
          }
        });
      }

      // Produce the topic name as a URL (this kick-starts the process)
      String url = "http://"
          + kafkaTopic
          + (uriChroot == null ? "" : uriChroot)
          + (startPage == null ? "" : startPage);
      CrawlEvent firstEvent = new CrawlEvent();
      firstEvent.setUrl(url);
      firstEvent.setChroot(uriChroot);
      byte[] encodedFirstEvent = OBJECT_MAPPER.writeValueAsBytes(firstEvent);
      producer.send(new KeyedMessage<byte[], byte[]>(kafkaTopic, encodedFirstEvent));
      LOG.info("Sent first event {}", kafkaTopic);
    }
  }

  @Override
  public void stop() throws Exception {
    if (isRunning.getAndSet(false)) {
      LOG.info("Stopping Kafka consumer for {}", kafkaTopic);
      consumer.shutdown();
      producer.close();
    }
  }
}
