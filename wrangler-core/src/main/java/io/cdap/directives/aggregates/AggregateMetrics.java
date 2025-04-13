/*
 * Copyright © 2025 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.cdap.directives.aggregates;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.TransientStore;
import io.cdap.wrangler.api.TransientVariableScope;
import io.cdap.wrangler.api.annotations.Categories;
import io.cdap.wrangler.api.annotations.PublicEvolving;
import io.cdap.wrangler.api.parser.ByteSize;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.Text;
import io.cdap.wrangler.api.parser.TimeDuration;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * A directive that aggregates metrics such as byte size and time duration
 * across rows, supporting both total and average modes with unit conversions.
 */
@Plugin(type = "directives")
@Name("aggregate-metrics")
@Categories(categories = { "aggregate" })
@Description("Aggregates metrics such as byte size and time duration.")
@PublicEvolving
public class AggregateMetrics implements Directive {
  private String sizeColumn;
  private String timeColumn;
  private String outputSizeColumn;
  private String outputTimeColumn;
  private String aggregationType = "total";
  private String sizeUnit = "B";
  private String timeUnit = "ms";

  private static final String STORE_KEY_BYTES = "AGG_TOTAL_BYTES";
  private static final String STORE_KEY_NANOS = "AGG_TOTAL_NANOS";
  private static final String STORE_KEY_COUNT = "AGG_ROW_COUNT";

  // Instance flag to ensure flush happens only once per recipe.
  private boolean flushed = false;

  @Override
  public UsageDefinition define() {
    UsageDefinition.Builder builder = UsageDefinition.builder("aggregate-metrics");

    // Required parameters
    builder.define("sizeColumn", TokenType.COLUMN_NAME);
    builder.define("timeColumn", TokenType.COLUMN_NAME);
    builder.define("outputSizeColumn", TokenType.COLUMN_NAME);
    builder.define("outputTimeColumn", TokenType.COLUMN_NAME);

    // Optional parameters
    builder.define("aggregationType", TokenType.TEXT);
    builder.define("sizeUnit", TokenType.TEXT);
    builder.define("timeUnit", TokenType.TEXT);

    return builder.build();
  }

  @Override
  public void initialize(Arguments args) {
    // Reset the flush flag on initialization so that each new recipe run starts fresh.
    flushed = false;
    
    sizeColumn = ((ColumnName) args.value("sizeColumn")).value();
    timeColumn = ((ColumnName) args.value("timeColumn")).value();
    outputSizeColumn = ((ColumnName) args.value("outputSizeColumn")).value();
    outputTimeColumn = ((ColumnName) args.value("outputTimeColumn")).value();

    if (args.contains("aggregationType")) {
      aggregationType = ((Text) args.value("aggregationType")).value().toLowerCase();
      if (!"total".equals(aggregationType) && !"average".equals(aggregationType)) {
        throw new IllegalArgumentException("Invalid aggregation type. Must be 'total' or 'average'.");
      }
    }
    if (args.contains("sizeUnit")) {
      sizeUnit = ((Text) args.value("sizeUnit")).value().toUpperCase();
      if (!isValidSizeUnit(sizeUnit)) {
        throw new IllegalArgumentException("Invalid size unit. Supported units are B, KB, MB, GB.");
      }
    }
    if (args.contains("timeUnit")) {
      timeUnit = ((Text) args.value("timeUnit")).value().toLowerCase();
      if (!isValidTimeUnit(timeUnit)) {
        throw new IllegalArgumentException("Invalid time unit. Supported units are ms, s, min.");
      }
    }
    System.out.println("[DEBUG] initialize() called with values:");
    System.out.println("[DEBUG] sizeColumn = " + sizeColumn);
    System.out.println("[DEBUG] timeColumn = " + timeColumn);
    System.out.println("[DEBUG] outputSizeColumn = " + outputSizeColumn);
    System.out.println("[DEBUG] outputTimeColumn = " + outputTimeColumn);
    System.out.println("[DEBUG] aggregationType = " + aggregationType);
    System.out.println("[DEBUG] sizeUnit = " + sizeUnit);
    System.out.println("[DEBUG] timeUnit = " + timeUnit);

  }

  private boolean isValidSizeUnit(String unit) {
    return unit.matches("B|KB|MB|GB");
  }

  private boolean isValidTimeUnit(String unit) {
    return unit.matches("ms|s|sec|min");
  }

  @Override
  public void destroy() {
    // no-op
  }

  @Override
  public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
    TransientStore store = context.getTransientStore();

    // Check if the flush already occurred.
    if (flushed) {
      System.out.println("[DEBUG] Already flushed. Returning empty list.");
      return new ArrayList<>();
    }

    // If input rows are empty, then it's the signal to flush the aggregation result.
    if (rows.isEmpty()) {
      System.out.println("[DEBUG] Rows are empty, triggering emitAggregateResult().");
      List<Row> flushResult = emitAggregateResult(context);
      flushed = true;
      return flushResult;
    }

    // Otherwise, accumulate values from each row.
    for (Row row : rows) {
      Object sizeObj = row.getValue(sizeColumn);
      Object timeObj = row.getValue(timeColumn);

      if (sizeObj == null || timeObj == null) {
        throw new DirectiveExecutionException(
            String.format("Columns '%s' or '%s' contain null values.", sizeColumn, timeColumn));
      }

      if (!(sizeObj instanceof String) || !(timeObj instanceof String)) {
        throw new DirectiveExecutionException(
            String.format("Columns '%s' and '%s' must be string values.", sizeColumn, timeColumn));
      }

      try {
        System.out.println("[DEBUG] Raw row values: size = " + sizeObj + ", time = " + timeObj);
        ByteSize size = new ByteSize((String) sizeObj);
        TimeDuration time = new TimeDuration((String) timeObj);

        System.out.println("[DEBUG] Parsed ByteSize = " + size.getBytes() + " bytes");
        System.out.println("[DEBUG] Parsed TimeDuration = " + time.getMilliseconds() + " ms");

        Long totalBytes = store.get(STORE_KEY_BYTES);
        Long totalNanos = store.get(STORE_KEY_NANOS);
        Long count = store.get(STORE_KEY_COUNT);

        totalBytes = (totalBytes == null ? 0L : totalBytes) + size.getBytes();
        totalNanos = (totalNanos == null ? 0L : totalNanos) + time.getMilliseconds() * 1_000_000L;
        count = (count == null ? 0L : count) + 1;

        store.set(TransientVariableScope.GLOBAL, STORE_KEY_BYTES, totalBytes);
        store.set(TransientVariableScope.GLOBAL, STORE_KEY_NANOS, totalNanos);
        store.set(TransientVariableScope.GLOBAL, STORE_KEY_COUNT, count);

        System.out.println("[DEBUG] Updated store: Bytes = " + totalBytes + ", Nanos = " + totalNanos);
      } catch (Exception e) {
        throw new DirectiveExecutionException(
            String.format("Error parsing size or time value: %s", e.getMessage()), e);
      }
    }

    // While accumulating, return an empty list.
    return new ArrayList<>();
  }

  private List<Row> emitAggregateResult(ExecutorContext context) {
    System.out.println("[DEBUG] emitAggregateResult() called.");

    TransientStore store = context.getTransientStore();

    Long totalBytes = store.get(STORE_KEY_BYTES);
    Long totalNanos = store.get(STORE_KEY_NANOS);
    Long count = store.get(STORE_KEY_COUNT);

    totalBytes = totalBytes == null ? 0L : totalBytes;
    totalNanos = totalNanos == null ? 0L : totalNanos;
    count = count == null ? 0L : count;

    System.out.println("[DEBUG] Retrieved from store - Bytes: " + totalBytes + ", Nanos: " + totalNanos);

    if (count == 0L) {
      System.out.println("[DEBUG] Count is 0. Returning empty list.");
      return new ArrayList<>();
    }

    double finalSize = "average".equals(aggregationType) ? (double) totalBytes / count : totalBytes;
    double finalTime = "average".equals(aggregationType) ? (double) totalNanos / count : totalNanos;

    finalSize = convertBytes(finalSize, sizeUnit);
    finalTime = convertNanos(finalTime, timeUnit);

    System.out.println("[DEBUG] Converted Final Size: " + finalSize + " " + sizeUnit);
    System.out.println("[DEBUG] Converted Final Time: " + finalTime + " " + timeUnit);

    Row result = new Row();
    result.add(outputSizeColumn, finalSize);
    result.add(outputTimeColumn, finalTime);

    // Reset aggregation counts after emitting the result.
    store.set(TransientVariableScope.GLOBAL, STORE_KEY_BYTES, 0L);
    store.set(TransientVariableScope.GLOBAL, STORE_KEY_NANOS, 0L);
    store.set(TransientVariableScope.GLOBAL, STORE_KEY_COUNT, 0L);

    List<Row> out = new ArrayList<>();
    out.add(result);
    return out;
  }

  private double convertBytes(double bytes, String unit) {
    switch (unit) {
      case "KB":
        return bytes / 1024.0;
      case "MB":
        return bytes / (1024.0 * 1024);
      case "GB":
        return bytes / (1024.0 * 1024 * 1024);
      default:
        return bytes;
    }
  }

  private double convertNanos(double nanos, String unit) {
    switch (unit) {
      case "s":
      case "sec":
        return nanos / 1_000_000_000.0;
      case "min":
        return nanos / 60_000_000_000.0;
      default:
        return nanos / 1_000_000.0; // default to milliseconds
    }
  }
}
