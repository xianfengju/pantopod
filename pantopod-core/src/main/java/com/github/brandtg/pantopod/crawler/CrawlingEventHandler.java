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

import com.github.brandtg.pantopod.api.CrawlEvent;
import com.github.brandtg.pantopod.consumer.PantopodEventHandler;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * Follows all links on a page.
 */
public abstract class CrawlingEventHandler implements PantopodEventHandler {
  private static Logger LOG = LoggerFactory.getLogger(CrawlingEventHandler.class);

  private final HttpClient httpClient;
  private final boolean checkErrors;
  private final boolean traverseDuplicates;

  public CrawlingEventHandler(HttpClient httpClient) {
    this(httpClient, true, false);
  }

  public CrawlingEventHandler(HttpClient httpClient, boolean checkErrors, boolean traverseDuplicates) {
    this.httpClient = httpClient;
    this.checkErrors = checkErrors;
    this.traverseDuplicates = traverseDuplicates;
  }

  @Override
  public Set<CrawlEvent> handle(CrawlEvent event) throws Exception {
    Set<CrawlEvent> nextEvents = new HashSet<>();

    // Get url
    URI url = URI.create(event.getUrl());
    Document dom = null;
    boolean created = false;
    if (!checkErrors || !hasError(url)) {
      HttpGet req = new HttpGet(url);
      HttpResponse res = httpClient.execute(req);

      try {
        if (res.getStatusLine().getStatusCode() == 200) {
          byte[] domBytes = IOUtils.toByteArray(res.getEntity().getContent());
          created = handleData(url, domBytes);
          dom = Jsoup.parse(new String(domBytes));
        } else {
          LOG.error("Error for {} #=> {}", url, res.getStatusLine().getStatusCode());
          markError(url, res.getStatusLine().getStatusCode());
        }
      } finally {
        if (res.getEntity() != null) {
          EntityUtils.consumeQuietly(res.getEntity());
        }
      }
    }

    // Extract links
    if ((created || traverseDuplicates) && dom != null) {
      for (Element element : dom.select("a")) {
        String href = element.attr("href");
        if (href != null) {
          URI nextUri = getNextUri(url, href);
          if (shouldExplore(nextUri)
              && isSameDomain(url, nextUri)
              && isDifferentPage(url, nextUri)) {
            CrawlEvent nextEvent = new CrawlEvent(event);
            nextEvent.setUrl(nextUri.toString());
            nextEvent.setParentUrl(event.getUrl());
            nextEvent.setDepth(event.getDepth() + 1);
            nextEvents.add(nextEvent);
            LOG.debug("Exploring {}", nextUri);
          } else {
            LOG.debug("Skipping {}", nextUri);
          }
        }
      }
    }

    return nextEvents;
  }

  private URI getNextUri(URI url, String href) throws Exception {
    if (href.contains("..")) {
      throw new IllegalArgumentException("Relative URI not allowed: " + href);
    }

    URI hrefUri = URI.create(href.trim().replaceAll(" ", "+"));

    URIBuilder builder = new URIBuilder();

    builder.setScheme(hrefUri.getScheme() == null ? url.getScheme() : hrefUri.getScheme());
    builder.setHost(hrefUri.getHost() == null ? url.getHost() : hrefUri.getHost());
    builder.setPort(hrefUri.getPort() == -1 ? url.getPort() : hrefUri.getPort());

    if (hrefUri.getPath() != null) {
      builder.setPath(hrefUri.getPath());
    }
    if (hrefUri.getQuery() != null) {
      builder.setCustomQuery(hrefUri.getQuery());
    }
    if (hrefUri.getFragment() != null) {
      builder.setFragment(hrefUri.getFragment());
    }

    return builder.build();
  }

  private boolean isSameDomain(URI url, URI nextUrl) throws IOException {
    if (nextUrl.getAuthority() == null || nextUrl.getAuthority().equals(url.getAuthority())) {
      return true;
    }
    handleExternalDomain(url, nextUrl);
    return false;
  }

  private boolean isDifferentPage(URI url, URI nextUrl) throws IOException {
    return nextUrl.getPath() != null && !nextUrl.getPath().equals(url.getPath());
  }

  protected abstract void handleExternalDomain(URI srcUrl, URI dstUrl) throws IOException;

  protected abstract boolean handleData(URI url, byte[] data) throws IOException;

  protected abstract boolean shouldExplore(URI url);

  protected abstract boolean hasError(URI url);

  protected abstract void markError(URI url, int errorCode) throws IOException;
}
