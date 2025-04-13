/*
 * Copyright © 2017-2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.wrangler.api.parser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Represents a time duration token (e.g., "150ms", "2s") parsed from a directive.
 */
public class TimeDuration implements Token {
  private final String value;
  private final long milliseconds;

  public TimeDuration(String value) {
    this.value = value;
    this.milliseconds = parseDuration(value);
  }

  private long parseDuration(String input) {
    input = input.trim().toLowerCase();
    if (input.endsWith("ms")) {
      return (long) Double.parseDouble(input.replace("ms", ""));
    } else if (input.endsWith("s")) {
      return (long) (Double.parseDouble(input.replace("s", "")) * 1000);
    } else {
      throw new IllegalArgumentException("Invalid time duration: " + input);
    }
  }

  public long getMilliseconds() {
    return milliseconds;
  }


  @Override
  public String value() {
    return value;
  }

  @Override
  public TokenType type() {
    return TokenType.TIME_DURATION;
  }

  @Override
  public JsonElement toJson() {
    JsonObject object = new JsonObject();
    object.addProperty("type", type().name());
    object.addProperty("value", value);
    object.addProperty("milliseconds", milliseconds);
    return object;
  }
}
