package act.installer;

import act.shared.Chemical;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

public class PubchemDB {

  public static final Charset UTF8 = Charset.forName("utf-8");

  public static class PubchemRocksDBRepresentation implements Serializable {

    private static final long serialVersionUID = 4017492820129501773L;

    private String inchi;
    private String inchiKey;
    private String smiles;
    private List<Long> pubchemIds;
    private Map<String, Set<String>> categoryToSetOfNames;

    public String getInchi() {
      return inchi;
    }

    public String getInchiKey() {
      return inchiKey;
    }

    public String getSmiles() {
      return smiles;
    }

    public List<Long> getPubchemIds() {
      return pubchemIds;
    }

    public Map<String, Set<String>> getCategoryToSetOfNames() {
      return categoryToSetOfNames;
    }

    public void populateNames(Map<String, String[]> pubchemNames) {
      for (Map.Entry<String, String[]> entry : pubchemNames.entrySet()) {
        String category = entry.getKey();
        Set<String> names = categoryToSetOfNames.get(category);
        if (names == null) {
          names = new HashSet<>();
          categoryToSetOfNames.put(category, names);
        }

        if (!names.contains(entry.getValue()[0])) {
          //TODO: change this or explain this...
          names.add(entry.getValue()[0]);
        }
      }
    }

    public PubchemRocksDBRepresentation(Chemical chemical) {
      inchi = chemical.getInChI();
      inchiKey = chemical.getInChIKey();
      smiles = chemical.getSmiles();
      pubchemIds = new ArrayList<>();
      pubchemIds.add(chemical.getPubchemID());
      categoryToSetOfNames = new HashMap<>();
      populateNames(chemical.getPubchemNames());
    }

    public void addPubchemId(Long pubchemId) {
      this.pubchemIds.add(pubchemId);
    }
  }

  protected ColumnFamilyHandle columnFamilyHandle;
  protected RocksDB db;

  /**
   * Construct an index writer for a particular FromBrendaDB class.
   * @param columnFamilyHandle A handle to the column family where this class's data should be written.
   * @param db A db to which to write data.
   */
  public PubchemDB(ColumnFamilyHandle columnFamilyHandle, RocksDB db) {
    this.columnFamilyHandle = columnFamilyHandle;
    this.db = db;
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
