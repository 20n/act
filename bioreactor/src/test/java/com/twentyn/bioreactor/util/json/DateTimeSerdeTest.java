package com.twentyn.bioreactor.util.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twentyn.bioreactor.sensors.DOSensorData;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DateTimeSerdeTest {
  @Test
  public void testRoundTripOnClassUsingCustomSerializer() throws Exception {
    ObjectMapper om = new ObjectMapper();

    // Note: round-tripping floating point numbers through JSON is dangerous.  These values seem to work, though.
    DOSensorData dosd = new DOSensorData(6.00, 99.9, "test_device", new DateTime().withZone(DateTimeZone.UTC));
    String json = om.writeValueAsString(dosd);
    DOSensorData dosd2 = om.readValue(json, new TypeReference<DOSensorData>() {});
    assertEquals("Original and serialized/deserialized objects match.", dosd, dosd2);
  }
}
