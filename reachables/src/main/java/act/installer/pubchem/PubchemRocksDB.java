package act.installer.pubchem;

import act.shared.Chemical;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.FlushOptions;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PubchemRocksDB {

  public static final Charset UTF8 = Charset.forName("utf-8");
  protected ColumnFamilyHandle columnFamilyHandle;
  protected RocksDB db;

  public PubchemRocksDB() {}

  public PubchemRocksDB(ColumnFamilyHandle handle, RocksDB db) {
    this.columnFamilyHandle = handle;
    this.db = db;
  }

  public RocksIterator getIterator() {
    return db.newIterator(columnFamilyHandle, new ReadOptions());
  }

  /**
   * Write a K/V pair to the index.
   * @param key The key to write.
   * @param chemical The object to add to a Serializable-serialized list of values.
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws RocksDBException
   */
  private void addObjectToIndex(byte[] key, Chemical chemical) throws IOException, ClassNotFoundException, RocksDBException {
    StringBuffer buffer = new StringBuffer();
    PubchemRocksDBRepresentation updateChemical = null;

    if (db.keyMayExist(columnFamilyHandle, key, buffer)) {
      byte[] existingVal = db.get(columnFamilyHandle, key);
      if (existingVal != null) {
        ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(existingVal));
        updateChemical = (PubchemRocksDBRepresentation) oi.readObject();
        updateChemical.addPubchemId(chemical.getPubchemID());
        updateChemical.populateNames(chemical.getPubchemNames());
      } else {
        updateChemical = new PubchemRocksDBRepresentation(chemical);
      }
    } else {
      updateChemical = new PubchemRocksDBRepresentation(chemical);
    }

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream oo = new ObjectOutputStream(bos);
    oo.writeObject(updateChemical);
    oo.flush();

    try {
      db.put(columnFamilyHandle, key, bos.toByteArray());
    } catch (RocksDBException e) {
      // TODO: do better;
      throw new IOException(e);
    }
    oo.close();
    bos.close();
  }

  public void initializeRocksDB(String fileName) throws RocksDBException {
    File pathToIndex = new File(fileName);
    RocksDB rocksDB = null; // Not auto-closable.
    org.rocksdb.Options options = new org.rocksdb.Options().setCreateIfMissing(true);
    System.out.println("Opening index at " + pathToIndex.getAbsolutePath());
    rocksDB = RocksDB.open(options, pathToIndex.getAbsolutePath());
    ColumnFamilyHandle cfh = rocksDB.createColumnFamily(new ColumnFamilyDescriptor(fileName.getBytes(UTF8)));
    this.db = rocksDB;
    this.columnFamilyHandle = cfh;
    rocksDB.flush(new FlushOptions());
  }

  public void closeRocksDB() {
    if (db != null) {
      db.close();
    }
  }

  /**
   * Make a list of keys + an object for the next result set row.  Multiple keys can exist for a given object
   * thanks to BRENDA's nested literature references.
   * @param chemical The chemical object to be written to the rocksdb.
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws RocksDBException
   * @throws SQLException
   */
  public void createKeysAndWrite(Chemical chemical)
      throws IOException, ClassNotFoundException, RocksDBException {
    byte[] key = chemical.getInChI().getBytes(UTF8);
    addObjectToIndex(key, chemical);
  }
}
