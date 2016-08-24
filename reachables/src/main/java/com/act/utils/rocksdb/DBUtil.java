package com.act.utils.rocksdb;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.CompactionStyle;
import org.rocksdb.CompressionType;
import org.rocksdb.DBOptions;
import org.rocksdb.MergeOperator;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.StringAppendOperator;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DBUtil {
  private static final Logger LOGGER = LogManager.getFormatterLogger(DBUtil.class);
  private static final Charset UTF8 = StandardCharsets.UTF_8;

  private static final String DEFAULT_ROCKSDB_COLUMN_FAMILY = "default";

  // Dunno why RocksDB needs two different types for these...
  private static final Options ROCKS_DB_CREATE_OPTIONS = new Options()
      .setCreateIfMissing(true)
      .setDisableDataSync(true)
      .setAllowMmapReads(true) // Trying all sorts of performance tweaking knobs, which are not well documented. :(
      .setAllowMmapWrites(true)
      .setWriteBufferSize(1 << 30)
      .setArenaBlockSize(1 << 20)
      .setAllowOsBuffer(true)
      .setCompressionType(CompressionType.SNAPPY_COMPRESSION) // Will hopefully trade CPU for I/O.
      ;

  public static final DBOptions ROCKS_DB_OPEN_OPTIONS = new DBOptions()
      .setCreateIfMissing(false)
      .setDisableDataSync(true)
      .setAllowMmapReads(true)
      .setAllowMmapWrites(true)
      ;

  /**
   * Create a new rocks DB at a particular location on disk.
   * @param pathToIndex A path to the directory where the index will be created.
   * @param columnFamilies Column families to create in the DB.
   * @param <T> A type (probably an enum) that represents a set of column families.
   * @return A DB and map of column family labels (as T) to enums.
   * @throws RocksDBException
   */
  public static <T extends ColumnFamilyEnumeration<T>> RocksDBAndHandles<T> createNewRocksDB(
      File pathToIndex, T[] columnFamilies) throws RocksDBException {
    RocksDB db = null; // Not auto-closable.
    Map<T, ColumnFamilyHandle> columnFamilyHandles = new HashMap<>();

    db = RocksDB.open(ROCKS_DB_CREATE_OPTIONS, pathToIndex.getAbsolutePath());

    for (T cf : columnFamilies) {
      LOGGER.info("Creating column family %s", cf.getName());
      ColumnFamilyHandle cfh =
          db.createColumnFamily(new ColumnFamilyDescriptor(cf.getName().getBytes(UTF8)));
      columnFamilyHandles.put(cf, cfh);
    }

    return new RocksDBAndHandles<T>(db, columnFamilyHandles);
  }


  /**
   * Open an existing RocksDB index.
   * @param pathToIndex A path to the RocksDB index directory to use.
   * @param columnFamilies A list of column familities to open.  Must be exhaustive, non-empty, and non-null.
   * @return A DB and map of column family labels (as T) to enums.
   * @throws RocksDBException
   */
  public static <T extends ColumnFamilyEnumeration<T>> RocksDBAndHandles<T> openExistingRocksDB(
      File pathToIndex, T[] columnFamilies) throws RocksDBException {
    if (columnFamilies == null || columnFamilies.length == 0) {
      throw new RuntimeException("Cannot open a RocksDB with an empty list of column families.");
    }

    List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>(columnFamilies.length + 1);
    // Must also open the "default" family or RocksDB will probably choke.
    columnFamilyDescriptors.add(new ColumnFamilyDescriptor(DEFAULT_ROCKSDB_COLUMN_FAMILY.getBytes()));
    for (T family : columnFamilies) {
      columnFamilyDescriptors.add(new ColumnFamilyDescriptor(family.getName().getBytes()));
    }
    List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>(columnFamilyDescriptors.size());

    DBOptions dbOptions = ROCKS_DB_OPEN_OPTIONS;
    dbOptions.setCreateIfMissing(false);
    RocksDB db = RocksDB.open(dbOptions, pathToIndex.getAbsolutePath(),
        columnFamilyDescriptors, columnFamilyHandles);
    Map<T, ColumnFamilyHandle> columnFamilyHandleMap = new HashMap<>(columnFamilies.length);
    // TODO: can we zip these together more easily w/ Java 8?

    for (int i = 0; i < columnFamilyDescriptors.size(); i++) {
      ColumnFamilyDescriptor cfd = columnFamilyDescriptors.get(i);
      ColumnFamilyHandle cfh = columnFamilyHandles.get(i);
      String familyName = new String(cfd.columnFamilyName(), UTF8);
      T descriptorFamily = columnFamilies[0].getFamilyByName(familyName); // Use any instance to get the next family.
      if (descriptorFamily == null) {
        if (!DEFAULT_ROCKSDB_COLUMN_FAMILY.equals(familyName)) {
          String msg = String.format("Found unexpected family name '%s' when trying to open RocksDB at %s",
              familyName, pathToIndex.getAbsolutePath());
          LOGGER.error(msg);
          // Crash if we don't recognize the contents of this DB.
          throw new RuntimeException(msg);
        }
        // Just skip this column family if it doesn't map to something we know but is expected.
        continue;
      }

      columnFamilyHandleMap.put(descriptorFamily, cfh);
    }

    return new RocksDBAndHandles<T>(db, columnFamilyHandleMap);
  }

}
