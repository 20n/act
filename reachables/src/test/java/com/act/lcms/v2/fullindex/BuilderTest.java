package com.act.lcms.v2.fullindex;

import com.act.lcms.LCMSSpectrum;
import com.act.utils.MockRocksDBAndHandles;
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

public class BuilderTest {
  public static final double FP_TOLERANCE = 0.000001;

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

  public static final List<MZWindow> MZ_WINDOWS = new ArrayList<MZWindow>() {{
    add(new MZWindow(0, 100.000));
    add(new MZWindow(1, 100.010));
    add(new MZWindow(2, 100.020));
  }};
  public Map<Integer, MZWindow> windowIdsToWindows =
      new HashMap<Integer, MZWindow>() {{
        for (MZWindow window : MZ_WINDOWS){
          put(window.getIndex(), window);
        }
      }};

  public static MockRocksDBAndHandles<ColumnFamilies> populateTestDB() throws Exception {
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

    MockRocksDBAndHandles<ColumnFamilies> testDB =
        new MockRocksDBAndHandles<>(ColumnFamilies.values());
    Builder builder = new Builder(testDB);
    builder.extractTriples(spectra.iterator(), MZ_WINDOWS);
    builder.writeWindowsToDB(MZ_WINDOWS);
    return testDB;
  }

  public MockRocksDBAndHandles<ColumnFamilies> fakeDB;

  @Before
  public void setup() throws Exception {
    fakeDB = populateTestDB();
  }

  @Test
  public void testExtractTriples() throws Exception {
    // Verify all TMzI triples are stored correctly.
    Map<Long, TMzI> deserializedTriples = new HashMap<>();
    assertEquals("All triples should have entries in the DB", 9,
        fakeDB.getFakeDB().get(ColumnFamilies.ID_TO_TRIPLE).size());
    for (Map.Entry<List<Byte>, byte[]> entry :
        fakeDB.getFakeDB().get(ColumnFamilies.ID_TO_TRIPLE).entrySet()) {
      Long id = ByteBuffer.wrap(fakeDB.byteListToArray(entry.getKey())).getLong();
      TMzI triple =
          TMzI.readNextFromByteBuffer(ByteBuffer.wrap(entry.getValue()));
      Float expectedTime = Double.valueOf(TIMES[id.intValue() / 3]).floatValue();
      Double expectedMZ = MZS[id.intValue() / 3][id.intValue() % 3];
      Float expectedIntensity = Double.valueOf(INTENSITIES[id.intValue() / 3][id.intValue() % 3]).floatValue();

      assertEquals("Time matches expected", expectedTime, triple.getTime(), FP_TOLERANCE); // No error expected
      assertEquals("M/z matches expected", expectedMZ, triple.getMz(), FP_TOLERANCE);
      assertEquals("Intensity matches expected", expectedIntensity, triple.getIntensity(), FP_TOLERANCE);

      deserializedTriples.put(id, triple);
    }

    for (Map.Entry<List<Byte>, byte[]> entry :
        fakeDB.getFakeDB().get(ColumnFamilies.WINDOW_ID_TO_TRIPLES).entrySet()) {
      int windowId = ByteBuffer.wrap(fakeDB.byteListToArray(entry.getKey())).getInt();
      MZWindow window = windowIdsToWindows.get(windowId);

      List<Long> tmziIds = new ArrayList<>(entry.getValue().length / Long.BYTES);
      ByteBuffer valBuffer = ByteBuffer.wrap(entry.getValue());
      while (valBuffer.hasRemaining()) {
        tmziIds.add(valBuffer.getLong());
      }

      for (Long tripleId : tmziIds) {
        TMzI triple = deserializedTriples.get(tripleId);
        assertTrue("Triple m/z falls within range of containing window",
            triple.getMz() >= window.getMin() && triple.getMz() <= window.getMax()
        );
      }
    }

    for (Map.Entry<List<Byte>, byte[]> entry :
        fakeDB.getFakeDB().get(ColumnFamilies.TIMEPOINT_TO_TRIPLES).entrySet()) {
      float time = ByteBuffer.wrap(fakeDB.byteListToArray(entry.getKey())).getFloat();

      List<Long> tmziIds = new ArrayList<>(entry.getValue().length / Long.BYTES);
      ByteBuffer valBuffer = ByteBuffer.wrap(entry.getValue());
      while (valBuffer.hasRemaining()) {
        tmziIds.add(valBuffer.getLong());
      }

      for (Long tripleId : tmziIds) {
        TMzI triple = deserializedTriples.get(tripleId);
        assertEquals("Triple time matches key time", time, triple.getTime(), FP_TOLERANCE);
      }
    }
  }

  @Test
  public void testAppendOrRealloc() throws Exception {
    ByteBuffer dest = ByteBuffer.allocate(4);
    assertEquals("Initial buffer capacity matches expected", 4, dest.capacity());
    dest = Utils.appendOrRealloc(dest, ByteBuffer.wrap(new byte[] {'a', 'b', 'c', 'd'})); // No need to flip w/ wrap().
    assertEquals("Post-append (fits) buffer capacity matches expected", 4, dest.capacity());
    assertEquals("Post-append (fits) buffer position matches expected", 4, dest.position());
    dest = Utils.appendOrRealloc(dest, ByteBuffer.wrap(new byte[] {'e'}));
    assertEquals("Post-append (too large) buffer capacity has doubled", 8, dest.capacity());
    assertEquals("Post-append (too large) buffer position matches expected", 5, dest.position());
    dest = Utils.appendOrRealloc(dest, ByteBuffer.wrap(new byte[] {'f', 'g', 'h'}));
    assertEquals("Post-append (fits) buffer capacity matches expected", 8, dest.capacity());
    assertEquals("Post-append (fits) buffer position matches expected", 8, dest.position());
    dest = Utils.appendOrRealloc(dest, ByteBuffer.wrap(new byte[] {'i'}));
    assertEquals("Post-append (too large) buffer capacity has doubled", 16, dest.capacity());
    assertEquals("Post-append (too large) buffer position matches expected", 9, dest.position());

  }
}
