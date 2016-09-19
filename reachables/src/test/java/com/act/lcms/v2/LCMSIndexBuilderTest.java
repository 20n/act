package com.act.lcms.v2;

import com.act.lcms.LCMSSpectrum;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LCMSIndexBuilderTest {

  public static final double[] TIMES = { 1.0, 2.0, 3.0 };
  public static final double[][] MZS = {
      {100.000, 100.005, 100.010},
      {100.005, 100.010, 100.015},
      {100.010, 100.015, 100.020},
  };
  public static final double[][] INTENSITIES =  {
      {  1.0,   2.0,   3.0},
      { 10.0,  20.0,  30.0},
      {100.0, 200.0, 300.0},
  };

  public static final List<LCMSIndexBuilder.MZWindow> MZ_WINDOWS = new ArrayList<LCMSIndexBuilder.MZWindow>() {{
    add(new LCMSIndexBuilder.MZWindow(0, 100.000));
    add(new LCMSIndexBuilder.MZWindow(1, 100.010));
    add(new LCMSIndexBuilder.MZWindow(2, 100.020));
  }};
  public Map<Integer, LCMSIndexBuilder.MZWindow> windowIdsToWindows =
      new HashMap<Integer, LCMSIndexBuilder.MZWindow>() {{
        for (LCMSIndexBuilder.MZWindow window : MZ_WINDOWS){
          put(window.getIndex(), window);
        }
      }};

  public static MockRocksDBAndHandles<LCMSIndexBuilder.COLUMN_FAMILIES> populateTestDB() throws Exception {
    List<LCMSSpectrum> spectra = new ArrayList<> (TIMES.length);
    for (int i = 0; i < TIMES.length; i++) {
      List<Pair<Double, Double>> mzIntensities = new ArrayList<>();
      double totalIntensity = 0.0;
      for (int j = 0; j < MZS[i].length; j++) {
        mzIntensities.add(Pair.of(MZS[i][j], INTENSITIES[i][j]));
        totalIntensity += INTENSITIES[i][j];
      }
      spectra.add(new LCMSSpectrum(i, TIMES[i], "s", mzIntensities, null, null, null, i, totalIntensity));
    }

    LCMSIndexBuilder builder = new LCMSIndexBuilder();
    MockRocksDBAndHandles<LCMSIndexBuilder.COLUMN_FAMILIES> fakeDB =
        new MockRocksDBAndHandles<>(LCMSIndexBuilder.COLUMN_FAMILIES.values());
    builder.extractTriples(fakeDB, spectra.iterator(), MZ_WINDOWS);
    builder.writeWindowsToDB(fakeDB, MZ_WINDOWS);
    return fakeDB;
  }

  public MockRocksDBAndHandles<LCMSIndexBuilder.COLUMN_FAMILIES> fakeDB;

  @Before
  public void setup() throws Exception {
    fakeDB = populateTestDB();
  }

  @Test
  public void testExtractTriples() throws Exception {
    // Verify all TMzI triples are stored correctly.
    Map<Long, LCMSIndexBuilder.TMzI> deserializedTriples = new HashMap<>();
    assertEquals("All triples should have entries in the DB", 9,
        fakeDB.fakeDB.get(LCMSIndexBuilder.COLUMN_FAMILIES.ID_TO_TRIPLE).size());
    for (Map.Entry<List<Byte>, byte[]> entry :
        fakeDB.fakeDB.get(LCMSIndexBuilder.COLUMN_FAMILIES.ID_TO_TRIPLE).entrySet()) {
      Long id = ByteBuffer.wrap(fakeDB.byteListToArray(entry.getKey())).getLong();
      LCMSIndexBuilder.TMzI triple =
          LCMSIndexBuilder.TMzI.readNextFromByteBuffer(ByteBuffer.wrap(entry.getValue()));
      Float expectedTime = Double.valueOf(TIMES[id.intValue() / 3]).floatValue();
      Double expectedMZ = MZS[id.intValue() / 3][id.intValue() % 3];
      Float expectedIntensity = Double.valueOf(INTENSITIES[id.intValue() / 3][id.intValue() % 3]).floatValue();

      assertEquals("Time matches expected", expectedTime, triple.getTime(), 0.000001); // No error expected
      assertEquals("M/z matches expected", expectedMZ, triple.getMz(), 0.000001);
      assertEquals("Intensity matches expected", expectedIntensity, triple.getIntensity(), 0.000001);

      deserializedTriples.put(id, triple);
    }

    for (Map.Entry<List<Byte>, byte[]> entry :
        fakeDB.fakeDB.get(LCMSIndexBuilder.COLUMN_FAMILIES.WINDOW_ID_TO_TRIPLES).entrySet()) {
      int windowId = ByteBuffer.wrap(fakeDB.byteListToArray(entry.getKey())).getInt();
      LCMSIndexBuilder.MZWindow window = windowIdsToWindows.get(windowId);

      List<Long> tmziIds = new ArrayList<>(entry.getValue().length / Long.BYTES);
      ByteBuffer valBuffer = ByteBuffer.wrap(entry.getValue());
      while (valBuffer.hasRemaining()) {
        tmziIds.add(valBuffer.getLong());
      }

      for (Long tripleId : tmziIds) {
        LCMSIndexBuilder.TMzI triple = deserializedTriples.get(tripleId);
        assertTrue("Triple m/z falls within range of containing window",
            triple.getMz() >= window.getMin() && triple.getMz() <= window.getMax()
        );
      }
    }

    for (Map.Entry<List<Byte>, byte[]> entry :
        fakeDB.fakeDB.get(LCMSIndexBuilder.COLUMN_FAMILIES.TIMEPOINT_TO_TRIPLES).entrySet()) {
      float time = ByteBuffer.wrap(fakeDB.byteListToArray(entry.getKey())).getFloat();

      List<Long> tmziIds = new ArrayList<>(entry.getValue().length / Long.BYTES);
      ByteBuffer valBuffer = ByteBuffer.wrap(entry.getValue());
      while (valBuffer.hasRemaining()) {
        tmziIds.add(valBuffer.getLong());
      }

      for (Long tripleId : tmziIds) {
        LCMSIndexBuilder.TMzI triple = deserializedTriples.get(tripleId);
        assertEquals("Triple time matches key time", time, triple.getTime(), 0.000001);
      }
    }
  }
}
