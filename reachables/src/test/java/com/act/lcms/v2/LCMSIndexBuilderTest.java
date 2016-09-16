package com.act.lcms.v2;

import com.act.lcms.LCMSSpectrum;
import com.act.utils.rocksdb.ColumnFamilyEnumeration;
import com.act.utils.rocksdb.RocksDBAndHandles;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.spark.sql.catalyst.plans.logical.Window;
import org.junit.Before;
import org.junit.Test;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LCMSIndexBuilderTest {
  public class MockRocksDBAndHandles<T extends ColumnFamilyEnumeration<T>> extends RocksDBAndHandles<T> {
    T columnFamilies;
    Map<T, HashMap<List<Byte>, byte[]>> fakeDB = new HashMap<>();

    public MockRocksDBAndHandles(T[] columnFamilies) {
      super();
      for (T family : columnFamilies) {
        fakeDB.put(family, new HashMap<>());
      }
    }

    public List<Byte> byteArrayToList(byte[] array) {
      // Must wrap byte[] for keys, as byte arrays hash code is address (and not content) based.
      List<Byte> keyList = new ArrayList<>(array.length);
      for (byte b : array) {
        keyList.add(b); // Streams and Arrays aren't friendly to byte[], so do it the old fashioned way.
      }
      return keyList;
    }

    public byte[] byteListToArray(List<Byte> list) {
      byte[] array = new byte[list.size()];
      for (int i = 0; i < array.length; i++) {
        array[i] = list.get(i); // Sigh, still the old way with byte[].
      }
      return array;
    }


    @Override
    public void put(T columnFamily, byte[] key, byte[] val) throws RocksDBException {
      fakeDB.get(columnFamily).put(byteArrayToList(key), Arrays.copyOf(val, val.length));
    }

    @Override
    public boolean keyMayExist(T columnFamily, byte[] key) throws RocksDBException {
      return fakeDB.get(columnFamily).containsKey(byteArrayToList(key));
    }

    @Override
    public byte[] get(T columnFamily, byte[] key) throws RocksDBException {
      return fakeDB.get(columnFamily).get(byteArrayToList(key));
    }

    @Override
    public RocksIterator newIterator(T columnFamily) throws RocksDBException {
      throw new RuntimeException("newIterator not supported in testing");
    }

    @Override
    public void flush(boolean waitForFlush) throws RocksDBException {

    }

    @Override
    public RocksDBWriteBatch<T> makeWriteBatch() {
      return new MockRocksDBWriteBatch<T>(this, 0);
    }
  }

  public static class MockRocksDBWriteBatch<T extends ColumnFamilyEnumeration<T>>
      extends RocksDBAndHandles.RocksDBWriteBatch<T> {
    RocksDBAndHandles<T> parent;
    protected MockRocksDBWriteBatch(RocksDBAndHandles<T> parent, int reservedBytes) {
      super();
      this.parent = parent;
    }

    @Override
    public void put(T columnFamily, byte[] key, byte[] val) throws RocksDBException {
      // This is not what a write batch is supposed to do, but it should get us close enough to test.
      parent.put(columnFamily, key, val);
    }

    @Override
    public void write() throws RocksDBException {
      // Do nothing, we're just writing as we go anyway.
    }
  }

  public double[] times = { 1.0, 2.0, 3.0 };
  public double[][] mzs = {
      {100.000, 100.005, 100.010},
      {100.005, 100.010, 100.015},
      {100.010, 100.015, 100.020},
  };
  public double[][] intensities =  {
      {  1.0,   2.0,   3.0},
      { 10.0,  20.0,  30.0},
      {100.0, 200.0, 300.0},
  };

  public int[][] expectedMZTripleIDs = {
      {0, 1, 2, 3, 4, 6},
      {0, 1, 2, 3, 4, 5, 6, 7, 8},
      {2, 4, 5, 6, 7, 8},
  };
  public int[][] expectedTimeTripleIds = {
      {0, 1, 2},
      {3, 4, 5},
      {6, 7, 8},
  };

  public MockRocksDBAndHandles<LCMSIndexBuilder.COLUMN_FAMILIES> fakeDB;
  public Map<Integer, LCMSIndexBuilder.MZWindow> windowIdsToWindows;

  public MockRocksDBAndHandles<LCMSIndexBuilder.COLUMN_FAMILIES> populateTestDB() throws Exception {

    List<LCMSSpectrum> spectra = new ArrayList<> (times.length);
    for (int i = 0; i < times.length; i++) {
      List<Pair<Double, Double>> mzIntensities = new ArrayList<>();
      double totalIntensity = 0.0;
      for (int j = 0; j < mzs[i].length; j++) {
        mzIntensities.add(Pair.of(mzs[i][j], intensities[i][j]));
        totalIntensity += intensities[i][j];
      }
      spectra.add(new LCMSSpectrum(i, times[i], "s", mzIntensities, null, null, null, i, totalIntensity));
    }

    List<LCMSIndexBuilder.MZWindow> windows = new ArrayList<LCMSIndexBuilder.MZWindow>() {{
      add(new LCMSIndexBuilder.MZWindow(0, 100.000));
      add(new LCMSIndexBuilder.MZWindow(1, 100.010));
      add(new LCMSIndexBuilder.MZWindow(2, 100.020));
    }};
    windowIdsToWindows = new HashMap<Integer, LCMSIndexBuilder.MZWindow>() {{
      for (LCMSIndexBuilder.MZWindow window : windows){
        put(window.getIndex(), window);
      }
    }};

    LCMSIndexBuilder extractor = new LCMSIndexBuilder();
    MockRocksDBAndHandles<LCMSIndexBuilder.COLUMN_FAMILIES> fakeDB =
        new MockRocksDBAndHandles<>(LCMSIndexBuilder.COLUMN_FAMILIES.values());
    extractor.extractTriples(fakeDB, spectra.iterator(), windows);
    return fakeDB;
  }

  @Before
  public void setup() throws Exception {
    fakeDB = populateTestDB();
  }

  @Test
  public void extractTriples() throws Exception {


    // Verify all TMzI triples are stored correctly.
    Map<Long, LCMSIndexBuilder.TMzI> deserializedTriples = new HashMap<>();
    assertEquals("All triples should have entries in the DB", 9,
        fakeDB.fakeDB.get(LCMSIndexBuilder.COLUMN_FAMILIES.ID_TO_TRIPLE).size());
    for (Map.Entry<List<Byte>, byte[]> entry :
        fakeDB.fakeDB.get(LCMSIndexBuilder.COLUMN_FAMILIES.ID_TO_TRIPLE).entrySet()) {
      Long id = ByteBuffer.wrap(fakeDB.byteListToArray(entry.getKey())).getLong();
      LCMSIndexBuilder.TMzI triple =
          LCMSIndexBuilder.TMzI.readNextFromByteBuffer(ByteBuffer.wrap(entry.getValue()));
      Float expectedTime = Double.valueOf(times[id.intValue() / 3]).floatValue();
      Double expectedMZ = mzs[id.intValue() / 3][id.intValue() % 3];
      Float expectedIntensity = Double.valueOf(intensities[id.intValue() / 3][id.intValue() % 3]).floatValue();

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
