package act.installer.pubchem;

import com.act.utils.TSVWriter;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.FlushOptions;
import org.rocksdb.RocksDB;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PubchemFinder {
  public static final Charset UTF8 = Charset.forName("utf-8");
  private static final String INSTANCE_NAME = "Pubchem";

  public static void main(String[] args) throws Exception {
    File pathToIndex = new File(INSTANCE_NAME);

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

    BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("/mnt/shared-data/Gil/L4_expansion/recent_novel_chemicals")));
    List<String> headerFields = new ArrayList<>();
    headerFields.add("Inchi");
    headerFields.add("Canonical Name");

    TSVWriter<String, String> tsvWriter = new TSVWriter<>(headerFields);
    tsvWriter.open(new File("result.tsv"));

    String line;
    while ((line = in.readLine()) != null) {
      line = line.replace("\n", "");
      PubchemParser.PubChemEntry entry = pubchemRocksDB.getValue(line);
      if (entry != null) {
        Map<String, String> row = new HashMap<>();
        row.put("Inchi", entry.getInchi());

        String nameS = "";
        if (entry.getNames() != null) {

          if (entry.getNames().get("Preferred") != null) {
            for (String name : entry.getNames().get("Preferred")) {
              nameS = name;
              break;
            }
          } else if (entry.getNames().get("Systematic") != null) {
            for (String name : entry.getNames().get("Preferred")) {
              nameS = name;
              break;
            }
          }
        }

        row.put("Canonical Name", nameS);
        tsvWriter.append(row);
      }
    }

    if (rocksDB != null) {
      rocksDB.close();
    }
  }
}
