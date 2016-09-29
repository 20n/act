package com.act.utils;

import com.act.utils.rocksdb.ColumnFamilyEnumeration;
import com.act.utils.rocksdb.RocksDBAndHandles;
import org.rocksdb.RocksDBException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockRocksDBAndHandles<T extends ColumnFamilyEnumeration<T>> extends RocksDBAndHandles<T> {
  T[] columnFamilies;
  Map<T, Map<List<Byte>, byte[]>> fakeDB = new HashMap<>();

  public MockRocksDBAndHandles(T[] columnFamilies) {
    super();
    this.columnFamilies = columnFamilies;
    for (T family : columnFamilies) {
      fakeDB.put(family, new HashMap<>());
    }
  }

  public static List<Byte> byteArrayToList(byte[] array) {
    // Must wrap byte[] for keys, as byte arrays hash code is address (and not content) based.
    List<Byte> keyList = new ArrayList<>(array.length);
    for (byte b : array) {
      keyList.add(b); // Streams and Arrays aren't friendly to byte[], so do it the old fashioned way.
    }
    return keyList;
  }

  public static byte[] byteListToArray(List<Byte> list) {
    byte[] array = new byte[list.size()];
    for (int i = 0; i < array.length; i++) {
      array[i] = list.get(i); // Sigh, still the old way with byte[].
    }
    return array;
  }


  public Map<T, Map<List<Byte>, byte[]>> getFakeDB() {
    return this.fakeDB;
  }

  @Override
  public void put(T columnFamily, byte[] key, byte[] val) throws RocksDBException {
    // Copy the value bytes since they'll likely be modified in place.
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
  public RocksDBIterator newIterator(T columnFamily) throws RocksDBException {
    return new MockRocksDBIterator(fakeDB.get(columnFamily));
  }

  @Override
  public void flush(boolean waitForFlush) throws RocksDBException {

  }

  @Override
  public RocksDBWriteBatch<T> makeWriteBatch() {
    return new MockRocksDBWriteBatch<T>(this, 0);
  }

  public static class MockRocksDBWriteBatch<T extends ColumnFamilyEnumeration<T>>
      extends RocksDBWriteBatch<T> {
    RocksDBAndHandles<T> parent;
    Map<T, Map<List<Byte>, byte[]>> batch = new HashMap<>();

    protected MockRocksDBWriteBatch(RocksDBAndHandles<T> parent, int reservedBytes) {
      super();
      this.parent = parent;
    }

    @Override
    public void put(T columnFamily, byte[] key, byte[] val) throws RocksDBException {
      // This is not what a write batch is supposed to do, but it should get us close enough to test.
      Map<List<Byte>, byte[]> columnFamilyMap = batch.get(columnFamily);
      if (columnFamilyMap == null) {
        columnFamilyMap = new HashMap<>();
        batch.put(columnFamily, columnFamilyMap);
      }

      List<Byte> keyObject = byteArrayToList(key);
      // Copy the value bytes since they'll likely be modified in place.
      columnFamilyMap.put(keyObject, Arrays.copyOf(val, val.length));
    }

    @Override
    public void write() throws RocksDBException {
      /* Write everything accumulated in the batch in case the client depends on the writes no showing up in the DB
       * until batch.write is called.  Though is that even true when using a real RocksDB write batch? */
      for (Map.Entry<T, Map<List<Byte>, byte[]>> cfEntry : batch.entrySet()) {
        T cf = cfEntry.getKey();
        for (Map.Entry<List<Byte>, byte[]> kvEntry : cfEntry.getValue().entrySet()) {
          parent.put(cf, byteListToArray(kvEntry.getKey()), kvEntry.getValue());
        }
      }
    }
  }

  public static class MockRocksDBIterator extends RocksDBIterator {
    Map<List<Byte>, byte[]> index;
    List<List<Byte>> keys;
    int cursor = 0;

    public MockRocksDBIterator(Map<List<Byte>, byte[]> index) {
      super();
      this.index = index;
    }

    @Override
    public void reset() {
      List<List<Byte>> keys = new ArrayList<>(index.keySet());
      Collections.sort(keys, (a, b) -> {
        for (int i = 0; i < a.size() && i < b.size(); i++) {
          int cmp = a.get(i).compareTo(b.get(i));
          if (cmp != 0) {
            return cmp; // Return the comparison results for the first mismatch.
          }
        }
        return Integer.valueOf(a.size()).compareTo(b.size()); // If the values are all the same, prefer the shorter.
      });
      this.keys = keys;
      this.cursor = 0;
    }

    @Override
    public void seekToFirst() {
      reset();
    }

    @Override
    public void seekToLast() {
      this.cursor = this.keys.size() - 1;
    }

    @Override
    public void next() {
      this.cursor++;
    }

    @Override
    public void prev() {
      this.cursor--;
    }

    @Override
    public boolean isValid() {
      return this.cursor >= 0 && this.cursor < this.keys.size();
    }

    @Override
    public byte[] value() {
      return index.get(keys.get(cursor));
    }

    @Override
    public byte[] key() {
      return byteListToArray(this.keys.get(cursor));
    }
  }
}
