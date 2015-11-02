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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;

public class FileBasedCrawlingEventHandler extends CrawlingEventHandler {
  private static final Logger LOG = LoggerFactory.getLogger(FileBasedCrawlingEventHandler.class);
  private static final String DAT_FILE = ".dat";
  private static final String ERR_FILE = ".err";
  private static final String EXT_FILE = ".ext";
  private final File outputDir;

  public FileBasedCrawlingEventHandler(HttpClient httpClient, File outputDir) {
    super(httpClient);
    this.outputDir = outputDir;
  }

  @Override
  protected void handleExternalDomain(URI srcUrl, URI dstUrl) throws IOException {
    File srcRoot = new File(outputDir, srcUrl.getHost());
    File extRoot = new File(srcRoot, EXT_FILE);
    File outputDir = new File(extRoot, dstUrl.getHost());
    if (!outputDir.exists()) {
      FileUtils.forceMkdir(outputDir);
    }
  }

  @Override
  protected boolean handleData(URI url, byte[] data) throws IOException {
    File outputRoot = new File(outputDir, url.getHost() + File.separator + url.getPath());
    FileUtils.forceMkdir(outputRoot);
    File outputData = new File(outputRoot, DAT_FILE);
    if (!outputData.exists()) {
      try (OutputStream os = new FileOutputStream(outputData)) {
        IOUtils.write(data, os);
        LOG.info("Wrote {}", outputRoot);
      }
      return true;
    }
    return false;
  }

  @Override
  protected boolean shouldExplore(URI url) {
    File outputRoot = new File(outputDir, url.getHost() + File.separator + url.getPath());
    File errFile = new File(outputRoot, ERR_FILE);
    return !outputRoot.exists() && !errFile.exists();
  }

  @Override
  protected void markError(URI url, int errorCode) throws IOException {
    File outputRoot = new File(outputDir, url.getHost() + File.separator + url.getPath());
    FileUtils.forceMkdir(outputRoot);
    File errFile = new File(outputRoot, ERR_FILE);
    if (!errFile.exists()) {
      try (OutputStream os = new FileOutputStream(errFile)) {
        IOUtils.write(String.valueOf(errorCode).getBytes(), os);
      }
    }
  }

  @Override
  protected boolean hasError(URI url) {
    File outputRoot = new File(outputDir, url.getHost() + File.separator + url.getPath());
    File errFile = new File(outputRoot, ERR_FILE);
    return errFile.exists();
  }
}
