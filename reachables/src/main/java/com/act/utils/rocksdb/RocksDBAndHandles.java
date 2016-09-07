package com.act.utils.rocksdb;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.util.Map;

public class RocksDBAndHandles<T extends ColumnFamilyEnumeration> {
  RocksDB db;
  Map<T, ColumnFamilyHandle> columnFamilyHandleMap;

  public RocksDBAndHandles(RocksDB db, Map<T, ColumnFamilyHandle> columnFamilyHandleMap) {
    this.db = db;
    this.columnFamilyHandleMap = columnFamilyHandleMap;
  }

  public RocksDB getDb() {
    return db;
  }

  public Map<T, ColumnFamilyHandle> getColumnFamilyHandleMap() {
    return columnFamilyHandleMap;
  }

  public ColumnFamilyHandle getHandle(T columnFamilyLabel) {
    return this.columnFamilyHandleMap.get(columnFamilyLabel);
  }

  public void put(T columnFamily, byte[] key, byte[] val) throws RocksDBException {
    this.db.put(getHandle(columnFamily), key, val);
  }

  public boolean keyMayExist(T columnFamily, byte[] key) throws RocksDBException {
    StringBuffer buffer = new StringBuffer();
    return this.db.keyMayExist(getHandle(columnFamily), key, buffer);
  }

  public byte[] get(T columnFamily, byte[] key) throws RocksDBException {
    return this.db.get(getHandle(columnFamily), key);
  }

  public RocksIterator newIterator(T columnFamily) throws RocksDBException {
    return this.db.newIterator(getHandle(columnFamily));
  }

  /* Important: don't expose merge(), as it appears to be broken in RocksDB JNI.
   *
   * RocksDB supports "merge" functionality, where a new value can be merged at the DB level (as opposed to the client
   * level) into an existing value for a given key using a predefined function.
   * See https://github.com/facebook/rocksdb/wiki/Merge-Operator.
   *
   * This ought to be super fast, as it's being done in the same stroke as the DB lookup--the old and new values should
   * be in the CPU cache (assuming they fit) and the merge is done in a layer of the library that should have some
   * notion of what operations will do right by the storage system.  So I tried it!
   *
   * Alas, calling merge() from Java causes the native layer to throw an exception that complains about a merge
   * function not being defined.  When you specify a built-in merge function in the DB constructor, the exception
   * *still* gets thrown.  Sad face!
   *
   * The workable alternative is to bubble the value all the way up to Java land, merge there, and then send the
   * resulting value back to the DB.  This tends to be incredibly slow, however, so just don't expose it at all.
   */
}
