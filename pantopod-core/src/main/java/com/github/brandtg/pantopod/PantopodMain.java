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

import org.apache.helix.controller.HelixControllerMain;
import org.apache.helix.tools.ClusterSetup;

import java.util.Arrays;

public class PantopodMain {
  private enum Mode {
    CONTROLLER,
    PARTICIPANT,
    ADMIN
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("usage: <mode> args...");
      System.exit(1);
    }

    Mode mode = Mode.valueOf(args[0].toUpperCase());
    String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

    switch (mode) {
      case PARTICIPANT:
        PantopodApplication.main(subArgs);
        break;
      case CONTROLLER:
        HelixControllerMain.main(subArgs);
        break;
      case ADMIN:
        ClusterSetup.main(subArgs);
        break;
      default:
        throw new IllegalStateException("Unsupported mode " + mode);
    }
  }
}
