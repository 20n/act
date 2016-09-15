package com.act.utils.rocksdb;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteBatchWithIndex;
import org.rocksdb.WriteOptions;

import java.util.Map;

public class RocksDBAndHandles<T extends ColumnFamilyEnumeration<T>> {
  RocksDB db;
  Map<T, ColumnFamilyHandle> columnFamilyHandleMap;

  WriteOptions writeOptions = null;

  // Here to hijack the DB interface for easy testing or alternate implementations.
  protected RocksDBAndHandles() {

  }

  public RocksDBAndHandles(RocksDB db, Map<T, ColumnFamilyHandle> columnFamilyHandleMap) {
    this.db = db;
    this.columnFamilyHandleMap = columnFamilyHandleMap;
  }

  public WriteOptions getWriteOptions() {
    return writeOptions;
  }

  public void setWriteOptions(WriteOptions writeOptions) {
    this.writeOptions = writeOptions;
  }

  public RocksDB getDb() {
    return db;
  }

  protected Map<T, ColumnFamilyHandle> getColumnFamilyHandleMap() {
    return columnFamilyHandleMap;
  }

  protected ColumnFamilyHandle getHandle(T columnFamilyLabel) {
    return this.columnFamilyHandleMap.get(columnFamilyLabel);
  }

  public void put(T columnFamily, byte[] key, byte[] val) throws RocksDBException {
    if (writeOptions != null) {
      this.db.put(getHandle(columnFamily), writeOptions, key, val);
    } else {
      this.db.put(getHandle(columnFamily), key, val);
    }
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

  public void flush(boolean waitForFlush) throws RocksDBException {
    FlushOptions options = new FlushOptions();
    options.setWaitForFlush(waitForFlush);
    db.flush(options);
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

  // Wrap write batches for easier CF management and testing.
  public RocksDBWriteBatch<T> makeWriteBatch() {
    return new RocksDBWriteBatch<T>(this, RocksDBWriteBatch.RESERVED_BYTES);
  }

  public RocksDBWriteBatch<T> makeWriteBatch(int reservedBytes) {
    return new RocksDBWriteBatch<T>(this, reservedBytes);
  }

  public static class RocksDBWriteBatch<T extends ColumnFamilyEnumeration<T>> {
    protected static final int RESERVED_BYTES = 1 << 18;
    private static final WriteOptions DEFAULT_WRITE_OPTIONS = new WriteOptions();
    WriteBatch batch;
    RocksDBAndHandles<T> parent;

    protected RocksDBWriteBatch(RocksDBAndHandles<T> parent, int reservedBytes) {
      this.parent = parent;
      this.batch = new WriteBatch(reservedBytes);
    }

    public void put(T columnFamily, byte[] key, byte[] val) throws RocksDBException {
      batch.put(parent.getHandle(columnFamily), key, val);
    }

    public void write() throws RocksDBException {
      WriteOptions writeOptions = parent.getWriteOptions() != null ? parent.getWriteOptions() : DEFAULT_WRITE_OPTIONS;
      parent.getDb().write(writeOptions, batch);
    }
  }
}
