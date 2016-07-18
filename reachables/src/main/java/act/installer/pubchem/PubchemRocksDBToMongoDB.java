package act.installer.pubchem;

import act.server.MongoDB;
import act.shared.Chemical;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PubchemRocksDBToMongoDB {

  public static final Charset UTF8 = Charset.forName("utf-8");
  private static final String INSTANCE_NAME = "Pubchem";
  private MongoDB mongoDB;

  public PubchemRocksDBToMongoDB() {
    this.mongoDB = new MongoDB("localhost", 27017, "test2");
  }

  public void convertAndWriteChemicalRepresentation(PubchemParser.PubChemEntry pubchemRocksDBRepresentation) {
    Chemical chemical = new Chemical(-1L);
    chemical.setInchi(pubchemRocksDBRepresentation.getInchi());

    for (Map.Entry<String, Set<String>> entry : pubchemRocksDBRepresentation.getNames().entrySet()) {
      String[] names = new String[entry.getValue().size()];
      entry.getValue().toArray(names);
      chemical.addNames(entry.getKey(), names);
    }

    int numPubchemIds = pubchemRocksDBRepresentation.getIds().size();
    if (numPubchemIds == 0) {
      System.out.println("This is an error...");
      return;
    }

    chemical.setPubchem(pubchemRocksDBRepresentation.getIds().get(0));

    if (numPubchemIds > 1) {
      chemical.insertAlternatePubchemIds(pubchemRocksDBRepresentation.getIds());
    }

    Long id = mongoDB.getNextAvailableChemicalDBid();
    mongoDB.submitToActChemicalDB(chemical, id);
  }

  public static void main(String[] args) throws Exception {
    File pathToIndex = new File(INSTANCE_NAME);

    PubchemRocksDBToMongoDB pubchemRocksDBToMongoDB = new PubchemRocksDBToMongoDB();

    List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>(1);
    columnFamilyDescriptors.add(new ColumnFamilyDescriptor("default".getBytes()));
    columnFamilyDescriptors.add(new ColumnFamilyDescriptor(INSTANCE_NAME.getBytes(UTF8)));
    List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>(columnFamilyDescriptors.size());

    DBOptions dbOptions = new DBOptions();
    dbOptions.setCreateIfMissing(false);

    RocksDB rocksDB = RocksDB.open(dbOptions, pathToIndex.getAbsolutePath(), columnFamilyDescriptors, columnFamilyHandles);
    PubchemRocksDB pubchemRocksDB = null;

    for (int i = 0; i < columnFamilyDescriptors.size(); i++) {
      ColumnFamilyDescriptor cfd = columnFamilyDescriptors.get(i);
      ColumnFamilyHandle cfh = columnFamilyHandles.get(i);

      String name = new String(cfd.columnFamilyName(), UTF8);
      if (name.equals(INSTANCE_NAME)) {
        pubchemRocksDB = new PubchemRocksDB(cfh, rocksDB);
      }
    }

    rocksDB.flush(new FlushOptions());

    if (pubchemRocksDB == null) {
      return;
    }

    RocksIterator iterator = pubchemRocksDB.getIterator();
    for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
      byte[] chemicalByteRep = iterator.value();
      ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(chemicalByteRep));
      PubchemParser.PubChemEntry representation = (PubchemParser.PubChemEntry) oi.readObject();
      pubchemRocksDBToMongoDB.convertAndWriteChemicalRepresentation(representation);
    }

    if (rocksDB != null) {
      rocksDB.close();
    }
  }
}
