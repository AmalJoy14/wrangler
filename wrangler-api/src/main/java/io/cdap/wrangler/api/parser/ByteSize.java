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
 * Represents a byte size token (e.g., "10KB", "1.5MB") parsed from a directive.
 */
public class ByteSize implements Token {
  private final String value;
  private final long bytes;

  public ByteSize(String value) {
    this.value = value;
    this.bytes = parseBytes(value);
  }

  private long parseBytes(String input) {
    input = input.trim().toUpperCase();
    if (input.endsWith("KB")) {
      return (long) (Double.parseDouble(input.replace("KB", "")) * 1024);
    } else if (input.endsWith("MB")) {
      return (long) (Double.parseDouble(input.replace("MB", "")) * 1024 * 1024);
    } else if (input.endsWith("GB")) {
      return (long) (Double.parseDouble(input.replace("GB", "")) * 1024 * 1024 * 1024);
    } else if (input.endsWith("B")) {
      return (long) Double.parseDouble(input.replace("B", ""));
    } else {
      throw new IllegalArgumentException("Invalid byte size: " + input);
    }
  }

  public long getBytes() {
    return bytes;
  }

  @Override
  public String value() {
    return value;
  }

  @Override
  public TokenType type() {
    return TokenType.BYTE_SIZE;
  }

  @Override
  public JsonElement toJson() {
    JsonObject object = new JsonObject();
    object.addProperty("type", type().name());
    object.addProperty("value", value);
    object.addProperty("bytes", bytes);
    return object;
  }
}
