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

  // Don't expose merge: it appears to be broken in RocksDB JNI.


}
