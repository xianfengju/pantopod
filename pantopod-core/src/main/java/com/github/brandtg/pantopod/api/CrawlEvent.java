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
package com.github.brandtg.pantopod.api;

import com.google.common.base.MoreObjects;

import java.util.Objects;

public class CrawlEvent {
  private String url;
  private String parentUrl;
  private int depth;
  private String chroot;

  public CrawlEvent() {}

  public CrawlEvent(CrawlEvent other) {
    this.url = other.getUrl();
    this.parentUrl = other.getParentUrl();
    this.depth = other.getDepth();
    this.chroot = other.getChroot();
  }

  public String getChroot() {
    return chroot;
  }

  public void setChroot(String chroot) {
    this.chroot = chroot;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getParentUrl() {
    return parentUrl;
  }

  public void setParentUrl(String parentUrl) {
    this.parentUrl = parentUrl;
  }

  public int getDepth() {
    return depth;
  }

  public void setDepth(int depth) {
    this.depth = depth;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("url", url)
        .add("parentUrl", parentUrl)
        .add("depth", depth)
        .add("chroot", chroot)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CrawlEvent)) {
      return false;
    }
    CrawlEvent e = (CrawlEvent) o;
    return Objects.equals(url, e.getUrl())
        && Objects.equals(parentUrl, e.getParentUrl())
        && Objects.equals(depth, e.getDepth())
        && Objects.equals(chroot, e.getChroot());
  }

  @Override
  public int hashCode() {
    return Objects.hash(url, parentUrl, depth, chroot);
  }
}
