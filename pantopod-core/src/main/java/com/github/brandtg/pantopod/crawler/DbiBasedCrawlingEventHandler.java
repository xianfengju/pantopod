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
package com.github.brandtg.pantopod.crawler;

import org.apache.http.client.HttpClient;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.util.BooleanMapper;
import org.skife.jdbi.v2.util.IntegerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;

public class DbiBasedCrawlingEventHandler extends CrawlingEventHandler {
  private static final Logger LOG = LoggerFactory.getLogger(DbiBasedCrawlingEventHandler.class);
  private static final String CREATE_DATA_TABLE = "CREATE TABLE IF NOT EXISTS `pantopod_crawler` ("
      + "`url` VARCHAR(255),"
      + "`data` LONGBLOB,"
      + "`error` INT,"
      + "PRIMARY KEY(`url`))";
  private static final String CREATE_EXT_TABLE = "CREATE TABLE IF NOT EXISTS `pantopod_ext` ("
      + "`src` VARCHAR(255),"
      + "`dst` VARCHAR(255),"
      + "PRIMARY KEY(`src`, `dst`))";
  private final DBI dbi;

  public DbiBasedCrawlingEventHandler(HttpClient httpClient, DBI dbi) {
    super(httpClient);
    this.dbi = dbi;
    try (Handle handle = dbi.open()) {
      handle.execute(CREATE_DATA_TABLE);
      handle.execute(CREATE_EXT_TABLE);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  protected void handleExternalDomain(URI srcUrl, URI dstUrl) throws IOException {
    try (Handle handle = dbi.open()) {
      int numRows = handle.update("INSERT IGNORE INTO `pantopod_ext` (`src_url`, `dst_url`) VALUES (?, ?)",
          srcUrl.getAuthority(), dstUrl.getAuthority());
      if (numRows > 0) {
        LOG.info("Inserted {} #=> {}", srcUrl.getAuthority(), dstUrl.getAuthority());
      }
    }
  }

  @Override
  protected boolean handleData(URI url, byte[] data) throws IOException {
    try (Handle handle = dbi.open()) {
      int numRows = handle.update("INSERT IGNORE INTO `pantopod_crawler` (`url`, `data`) VALUES (?, ?)", url.toString(), data);
      if (numRows > 0) {
        LOG.info("Inserted {}", url);
      }
      return true;
    } catch (Exception e) {
      LOG.error("Could not insert {}", url, e);
      return false;
    }
  }

  @Override
  protected boolean shouldExplore(URI url) {
    try (Handle handle = dbi.open()) {
      Integer count = handle.createQuery("SELECT COUNT(*) FROM `pantopod_crawler` WHERE `url` = :url")
          .bind("url", url.toString())
          .map(IntegerMapper.FIRST)
          .first();
      return count == 0;
    }
  }

  @Override
  protected boolean hasError(URI url) {
    try (Handle handle = dbi.open()) {
      Boolean isError = handle.createQuery("SELECT `error` FROM `pantopod_crawler` WHERE `url` = :url")
          .bind("url", url.toString())
          .map(BooleanMapper.FIRST)
          .first();
      return isError != null;
    }
  }

  @Override
  protected void markError(URI url, int errorCode) throws IOException {
    try (Handle handle = dbi.open()) {
      handle.execute("INSERT INTO `pantopod_crawler` (`url`, `error`) VALUES (?, ?)", url.toString(), errorCode);
    } catch (Exception e) {
      LOG.error("Could not mark error {}", url, e);
    }
  }
}
