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

import io.dropwizard.Configuration;
import io.dropwizard.client.HttpClientConfiguration;
import io.dropwizard.db.DataSourceFactory;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.File;

public class PantopodConfiguration extends Configuration {
  // Tor
  private boolean useTor;
  private String torRootDir = System.getProperty("java.io.tmpdir") + File.separator + "pantopod-tor";
  private int socksPort = 8050;
  private int controlPort = 8118;
  private long watchdogDelayMillis = -1; // disabled
  private String torExecutable = "tor";

  // Common
  private String zkConnectString;

  // Helix
  private String helixClusterName;
  private String helixParticipantName;

  // Kafka
  private String kafkaBrokerList;
  private String kafkaGroupId;

  // Output directory
  private String outputDir;

  // File, Dbi
  private String handlerType;

  private DataSourceFactory database = new DataSourceFactory();

  @Valid
  @NotNull
  private HttpClientConfiguration httpClient = new HttpClientConfiguration();

  @JsonProperty("httpClient")
  public HttpClientConfiguration getHttpClient() {
    return httpClient;
  }

  @JsonProperty("database")
  public DataSourceFactory getDataSourceFactory() {
    return database;
  }

  public DataSourceFactory getDatabase() {
    return database;
  }

  public void setDatabase(DataSourceFactory database) {
    this.database = database;
  }

  public long getWatchdogDelayMillis() {
    return watchdogDelayMillis;
  }

  public void setWatchdogDelayMillis(long watchdogDelayMillis) {
    this.watchdogDelayMillis = watchdogDelayMillis;
  }

  public String getHandlerType() {
    return handlerType;
  }

  public void setHandlerType(String handlerType) {
    this.handlerType = handlerType;
  }

  public String getOutputDir() {
    return outputDir;
  }

  public void setOutputDir(String outputDir) {
    this.outputDir = outputDir;
  }

  public String getKafkaBrokerList() {
    return kafkaBrokerList;
  }

  public void setKafkaBrokerList(String kafkaBrokerList) {
    this.kafkaBrokerList = kafkaBrokerList;
  }

  public String getKafkaGroupId() {
    return kafkaGroupId;
  }

  public void setKafkaGroupId(String kafkaGroupId) {
    this.kafkaGroupId = kafkaGroupId;
  }

  public boolean isUseTor() {
    return useTor;
  }

  public void setUseTor(boolean useTor) {
    this.useTor = useTor;
  }

  public String getZkConnectString() {
    return zkConnectString;
  }

  public void setZkConnectString(String zkConnectString) {
    this.zkConnectString = zkConnectString;
  }

  public String getHelixClusterName() {
    return helixClusterName;
  }

  public void setHelixClusterName(String helixClusterName) {
    this.helixClusterName = helixClusterName;
  }

  public String getHelixParticipantName() {
    return helixParticipantName;
  }

  public void setHelixParticipantName(String helixParticipantName) {
    this.helixParticipantName = helixParticipantName;
  }

  public String getTorRootDir() {
    return torRootDir;
  }

  public void setTorRootDir(String torRootDir) {
    this.torRootDir = torRootDir;
  }

  public String getTorExecutable() {
    return torExecutable;
  }

  public void setTorExecutable(String torExecutable) {
    this.torExecutable = torExecutable;
  }

  public int getControlPort() {
    return controlPort;
  }

  public void setControlPort(int controlPort) {
    this.controlPort = controlPort;
  }

  public int getSocksPort() {
    return socksPort;
  }

  public void setSocksPort(int socksPort) {
    this.socksPort = socksPort;
  }
}
