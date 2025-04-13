/*
 *  Copyright © 2017-2019 Cask Data, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package io.cdap.directives.aggregates;

import io.cdap.wrangler.TestingRig;
import io.cdap.wrangler.api.Row;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test for AggregateMetrics directive.
 */
public class AggregateMetricsTest {

  @Test
  public void testTotalAggregationInMBAndSeconds() throws Exception {
    List<Row> rows = new ArrayList<>();
    rows.add(new Row("data_transfer_size", "1048576B").add("response_time", "1000ms"));
    rows.add(new Row("data_transfer_size", "2097152B").add("response_time", "2000ms"));
    rows.add(new Row()); // this flushes the result

    String[] recipe = new String[] {
        "aggregate-metrics :data_transfer_size :response_time :total_size_mb :total_time_sec 'total' 'MB' 's'"
    };

    List<Row> output = TestingRig.execute(recipe, rows);

    // ✅ Debug output
    System.out.println("Output size: " + output.size());
    for (Row row : output) {
      for (int i = 0; i < row.length(); i++) {
        System.out.println("Column: " + row.getColumn(i) + ", Value: " + row.getValue(i));
      }
    }

    Assert.assertNotNull("Output is null", output);
    Assert.assertFalse("Output is empty", output.isEmpty());

    Row result = output.get(0);

    Object sizeVal = result.getValue("total_size_mb");
    Object timeVal = result.getValue("total_time_sec");

    System.out.println("total_size_mb = " + sizeVal);
    System.out.println("total_time_sec = " + timeVal);

    Assert.assertNotNull("total_size_mb is null", sizeVal);
    Assert.assertNotNull("total_time_sec is null", timeVal);

    double expectedMB = 3.0;
    double expectedSec = 3.0;

    Assert.assertEquals(expectedMB, (double) sizeVal, 0.001);
    Assert.assertEquals(expectedSec, (double) timeVal, 0.001);
  }

  // @Test
  // public void testAverageAggregationInKBAndMilliseconds() throws Exception {
  // List<Row> rows = new ArrayList<>();
  // rows.add(new Row("data_transfer_size", "512KB").add("response_time",
  // "1500ms"));
  // rows.add(new Row("data_transfer_size", "1024KB").add("response_time",
  // "2500ms"));

  // String[] recipe = new String[] {
  // "aggregate-metrics :data_transfer_size :response_time :avg_size_kb
  // :avg_time_ms 'average' 'KB' 'ms'"
  // };

  // List<Row> output = TestingRig.execute(recipe, rows);

  // Assert.assertEquals(1, output.size());
  // Row result = output.get(0);

  // double expectedKB = (512 + 1024) / 2.0; // 768 KB
  // double expectedMs = (1500 + 2500) / 2.0; // 2000 ms

  // Assert.assertEquals(expectedKB, (double) result.getValue("avg_size_kb"),
  // 0.001);
  // Assert.assertEquals(expectedMs, (double) result.getValue("avg_time_ms"),
  // 0.001);
  // }
}
