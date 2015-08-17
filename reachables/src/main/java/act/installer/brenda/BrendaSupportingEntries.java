package act.installer.brenda;

import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.StringUtils;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BrendaSupportingEntries {
  public static final Charset UTF8 = Charset.forName("utf-8");

  public static class Ligand {
    public static final String QUERY = "select LigandID, Ligand, inchi, molfile, groupID from ligand_molfiles";

    protected Integer ligandId; // The BRENDA identifier for a particular ligand (references one row in the table).
    protected String ligand; // The textual name of the ligand.
    protected String inchi; // The InChI for this ligand.
    protected byte[] molfile; // The contents of the associated MOL file.  Not sure what this means yet.
    protected Integer groupId; // Links this ligand to synonyms (i.e. different ligand name, same InChI).

    public Ligand(Integer ligandId, String ligand, String inchi, byte[] molfile, Integer groupId) {
      this.ligandId = ligandId;
      this.ligand = ligand;
      this.inchi = inchi;
      this.molfile = molfile;
      this.groupId = groupId;
    }

    public Integer getLigandId() {
      return ligandId;
    }

    public String getLigand() {
      return ligand;
    }

    public String getInchi() {
      return inchi;
    }

    public byte[] getMolfile() {
      return molfile;
    }

    public Integer getGroupId() {
      return groupId;
    }

    public static Ligand fromResultSet(ResultSet resultSet) throws SQLException {
      Integer groupId = resultSet.getInt(5);
      if (groupId.equals(0)) {
        groupId = null; // A zero group id means no group.
      }

      return new Ligand(
          resultSet.getInt(1),
          resultSet.getString(2),
          resultSet.getString(3),
          resultSet.getBytes(4),
          groupId
      );
    }
  }

  public static class Organism {
    public static final String QUERY = StringUtils.join(new String[]{
        "select", // Equivalent of `select distinct` but w/ multiple columns.
        "  Organism,",
        "  Organism_no,",
        "  Organism_ID",
        "from Organism",
        "group by Organism, Organism_no, Organism_ID"
    }, " ");

    protected String organism;
    protected String organismNo;
    protected Integer organismId;

    public Organism(String organism, String organismNo, Integer organismId) {
      this.organism = organism;
      this.organismNo = organismNo;
      this.organismId = organismId;
    }

    public String getOrganism() {
      return organism;
    }

    public String getOrganismNo() {
      return organismNo;
    }

    public Integer getOrganismId() {
      return organismId;
    }

    public static Organism fromResultSet(ResultSet resultSet) throws SQLException {
      return new Organism(
          resultSet.getString(1),
          resultSet.getString(2),
          resultSet.getInt(3)
      );
    }
  }

  public static class Sequence {
    public static final String QUERY = StringUtils.join(new String[]{
        "select",
        "  s.ID,",
        "  s.First_Accession_Code,",
        "  s.Entry_Name,",
        "  s.Source,",
        "  s.Sequence",
        "from Sequence s",
        "join ProtID2SwissProtID p2s on p2s.UniProt_acc = s.First_Accession_Code",
        "join Substrates_Products_ps spps on spps.protein_id = p2s.protein_id",
        "where spps.EC_Number = ?",
        "  and spps.Substrates = ?",
        "  and spps.Products = ?",
        "  and spps.Literature_Substrates = ?",
        "  and spps.Organism_Substrates = ?",
        "  and p2s.first_no = 1" // Necessary to ensure lookup in sequence DB.
    }, " ");

    /**
     * Prepare a query for the sequence based on a reaction, with all required arguments in the query bound to values.
     * This method lives here to keep the binding next to the statement, which makes sense given the complexity of the
     * query.
     *
     * @param conn     A connection on which to prepare the statement.
     * @param rxnEntry The reaction entry whose sequences to search for.
     * @return A prepared statement that will fetch results of query.
     * @throws SQLException
     */
    public static PreparedStatement prepareStatement(Connection conn, BrendaRxnEntry rxnEntry) throws SQLException {
      PreparedStatement stmt = conn.prepareStatement(QUERY);
      stmt.setString(1, rxnEntry.ecNumber);
      stmt.setString(2, rxnEntry.substrates);
      stmt.setString(3, rxnEntry.products);
      stmt.setString(4, rxnEntry.literatureSubstrates);
      stmt.setString(5, rxnEntry.organismSubstrates);
      return stmt;
    }

    public static Sequence sequenceFromResultSet(ResultSet resultSet) throws SQLException {
      return new Sequence(resultSet.getInt(1), resultSet.getString(2), resultSet.getString(3),
          resultSet.getString(4), resultSet.getString(5));
    }

    protected Integer brendaId;
    protected String firstAccessionCode;
    protected String entryName;
    protected String source;
    protected String sequence;

    public Sequence(Integer brendaId, String firstAccessionCode, String entryName, String source, String sequence) {
      this.brendaId = brendaId;
      this.firstAccessionCode = firstAccessionCode;
      this.entryName = entryName;
      this.source = source;
      this.sequence = sequence;
    }

    public Integer getBrendaId() {
      return brendaId;
    }

    public String getFirstAccessionCode() {
      return firstAccessionCode;
    }

    public String getEntryName() {
      return entryName;
    }

    public String getSource() {
      return source;
    }

    public String getSequence() {
      return sequence;
    }
  }

  /* ******************************
   * Result classes for BRENDA data types linked to Substrates_Products entries.
   * TODO: move these to their own class files if they seem unwieldy. */

  /* BRENDA likes to pack lists of literature ids into comma-delimited lists within a single DB field.  We need to
   * ensure that a given row actually references a literature id, so we split the list and search for an exact match
   * on one of the fields.  Crude but effective. */
  public static String[] idsFieldToArray(String ids) {
    return ids.split(", *");
  }

  public static boolean findIdInList(String idList, String idToFind) {
    String[] ids = idsFieldToArray(idList);
    for (int i = 0; i < ids.length; i++) {
      if (idToFind.equals(ids[i])) {
        return true;
      }
    }
    return false;
  }

  // Classes representing data linked to the Substrates_Products and Natural_Substrates_Products tables.
  public static class KMValue implements FromBrendaDB<KMValue>, Serializable {
    public static final String QUERY = "select KM_Value, Commentary, Literature from KM_Value " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    public static final String ALL_QUERY =
        "select KM_Value, Commentary, Literature, EC_Number, Organism from KM_Value";
    public static final String COLUMN_FAMILY_NAME = "KM_Value";
    protected static final KMValue INSTANCE = new KMValue();
    private static final long serialVersionUID = 4014251635935240023L;

    protected Double kmValue;
    protected String commentary;

    private KMValue() {
    }

    public KMValue(Double kmValue, String commentary) {
      this.kmValue = kmValue;
      this.commentary = commentary;
    }

    public Double getKmValue() {
      return kmValue;
    }

    public String getCommentary() {
      return commentary;
    }

    @Override
    public KMValue fromResultSet(ResultSet resultSet) throws SQLException {
      return new KMValue(resultSet.getDouble(1), resultSet.getString(2));
    }

    @Override
    public int getLiteratureField() {
      return 3;
    }

    @Override
    public String getAllQuery() {
      return ALL_QUERY;
    }

    @Override
    public int getLiteratureFieldForAllQuery() {
      return 3;
    }

    @Override
    public int getECNumberFieldForAllQuery() {
      return 4;
    }

    @Override
    public int getOrganismFieldForAllQuery() {
      return 5;
    }

    @Override
    public String getColumnFamilyName() {
      return COLUMN_FAMILY_NAME;
    }
  }

  public static class SpecificActivity implements FromBrendaDB<SpecificActivity>, Serializable {
    public static final String QUERY = "select Specific_Activity, Commentary, Literature from Specific_Activity " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    public static final String ALL_QUERY =
        "select Specific_Activity, Commentary, Literature, EC_Number, Organism from Specific_Activity";
    public static final String COLUMN_FAMILY_NAME = "Specific_Activity";
    protected static final SpecificActivity INSTANCE = new SpecificActivity();
    private static final long serialVersionUID = 4017492820129501771L;

    protected Double specificActivity;
    protected String commentary;

    protected SpecificActivity() {
    }

    public SpecificActivity(Double specificActivity, String commentary) {
      this.specificActivity = specificActivity;
      this.commentary = commentary;
    }

    public Double getSpecificActivity() {
      return specificActivity;
    }

    public String getCommentary() {
      return commentary;
    }

    @Override
    public SpecificActivity fromResultSet(ResultSet resultSet) throws SQLException {
      return new SpecificActivity(resultSet.getDouble(1), resultSet.getString(2));
    }

    @Override
    public int getLiteratureField() {
      return 3;
    }

    @Override
    public String getAllQuery() {
      return ALL_QUERY;
    }

    @Override
    public int getLiteratureFieldForAllQuery() {
      return 3;
    }

    @Override
    public int getECNumberFieldForAllQuery() {
      return 4;
    }

    @Override
    public int getOrganismFieldForAllQuery() {
      return 5;
    }

    @Override
    public String getColumnFamilyName() {
      return COLUMN_FAMILY_NAME;
    }
  }

  public static class OrganismCommentary implements FromBrendaDB<OrganismCommentary>, Serializable {
    public static final String QUERY = "select Commentary, Literature from Organism " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    public static final String ALL_QUERY =
        "select Commentary, Literature, EC_Number, Organism from Organism";
    public static final String COLUMN_FAMILY_NAME = "Organism_Commentary";
    protected static final OrganismCommentary INSTANCE = new OrganismCommentary();
    private static final long serialVersionUID = -2085699584700496115L;

    protected String commentary;

    protected OrganismCommentary() {
    }

    public OrganismCommentary(String commentary) {
      this.commentary = commentary;
    }

    public String getCommentary() {
      return commentary;
    }

    @Override
    public OrganismCommentary fromResultSet(ResultSet resultSet) throws SQLException {
      return new OrganismCommentary(resultSet.getString(1));
    }

    @Override
    public int getLiteratureField() {
      return 2;
    }

    @Override
    public String getAllQuery() {
      return ALL_QUERY;
    }

    @Override
    public int getLiteratureFieldForAllQuery() {
      return 2;
    }

    @Override
    public int getECNumberFieldForAllQuery() {
      return 3;
    }

    @Override
    public int getOrganismFieldForAllQuery() {
      return 4;
    }

    @Override
    public String getColumnFamilyName() {
      return COLUMN_FAMILY_NAME;
    }
  }

  public static class GeneralInformation implements FromBrendaDB<GeneralInformation>, Serializable {
    public static final String QUERY =
        "select General_Information, Commentary, Literature from General_Information " +
            "where EC_Number = ? and Literature like ? and Organism = ?";
    public static final String ALL_QUERY =
        "select General_Information, Commentary, Literature, EC_Number, Organism from General_Information";
    public static final String COLUMN_FAMILY_NAME = "General_Information";
    public static final GeneralInformation INSTANCE = new GeneralInformation();
    private static final long serialVersionUID = 1157007471920187876L;


    protected String generalInformation;
    protected String commentary;

    protected GeneralInformation() {
    }

    public GeneralInformation(String generalInformation, String commentary) {
      this.generalInformation = generalInformation;
      this.commentary = commentary;
    }

    public String getGeneralInformation() {
      return generalInformation;
    }

    public String getCommentary() {
      return commentary;
    }

    @Override
    public GeneralInformation fromResultSet(ResultSet resultSet) throws SQLException {
      return new GeneralInformation(resultSet.getString(1), resultSet.getString(2));
    }

    @Override
    public int getLiteratureField() {
      return 3;
    }

    @Override
    public String getAllQuery() {
      return ALL_QUERY;
    }

    @Override
    public int getLiteratureFieldForAllQuery() {
      return 3;
    }

    @Override
    public int getECNumberFieldForAllQuery() {
      return 4;
    }

    @Override
    public int getOrganismFieldForAllQuery() {
      return 5;
    }

    @Override
    public String getColumnFamilyName() {
      return COLUMN_FAMILY_NAME;
    }
  }

  public static class Cofactor implements FromBrendaDB<Cofactor>, Serializable {
    public static final String QUERY = "select Cofactor, Commentary, Literature from Cofactor " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    public static final String ALL_QUERY =
        "select Cofactor, Commentary, Literature, EC_Number, Organism from Cofactor";
    public static final String COLUMN_FAMILY_NAME = "Cofactor";
    protected static final Cofactor INSTANCE = new Cofactor();
    private static final long serialVersionUID = -520309053864923030L;

    protected String cofactor;
    protected String commentary;

    protected Cofactor() {
    }

    public Cofactor(String cofactor, String commentary) {
      this.cofactor = cofactor;
      this.commentary = commentary;
    }

    public String getCofactor() {
      return cofactor;
    }

    public String getCommentary() {
      return commentary;
    }

    @Override
    public Cofactor fromResultSet(ResultSet resultSet) throws SQLException {
      return new Cofactor(resultSet.getString(1), resultSet.getString(2));
    }

    @Override
    public int getLiteratureField() {
      return 3;
    }

    @Override
    public String getAllQuery() {
      return ALL_QUERY;
    }

    @Override
    public int getLiteratureFieldForAllQuery() {
      return 3;
    }

    @Override
    public int getECNumberFieldForAllQuery() {
      return 4;
    }

    @Override
    public int getOrganismFieldForAllQuery() {
      return 5;
    }

    @Override
    public String getColumnFamilyName() {
      return COLUMN_FAMILY_NAME;
    }
  }

  public static class Inhibitors implements FromBrendaDB<Inhibitors>, Serializable {
    public static final String QUERY = "select Inhibitors, Commentary, Literature from Inhibitors " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    public static final String ALL_QUERY =
        "select Inhibitors, Commentary, Literature, EC_Number, Organism from Inhibitors";
    public static final String COLUMN_FAMILY_NAME = "Inhibitors";
    protected static final Inhibitors INSTANCE = new Inhibitors();
    private static final long serialVersionUID = -6978439225811251470L;

    protected String inhibitors;
    protected String commentary;

    protected Inhibitors() {
    }

    public Inhibitors(String inhibitors, String commentary) {
      this.inhibitors = inhibitors;
      this.commentary = commentary;
    }

    public String getInhibitors() {
      return inhibitors;
    }

    public String getCommentary() {
      return commentary;
    }

    @Override
    public Inhibitors fromResultSet(ResultSet resultSet) throws SQLException {
      return new Inhibitors(resultSet.getString(1), resultSet.getString(2));
    }

    @Override
    public int getLiteratureField() {
      return 3;
    }

    @Override
    public String getAllQuery() {
      return ALL_QUERY;
    }

    @Override
    public int getLiteratureFieldForAllQuery() {
      return 3;
    }

    @Override
    public int getECNumberFieldForAllQuery() {
      return 4;
    }

    @Override
    public int getOrganismFieldForAllQuery() {
      return 5;
    }

    @Override
    public String getColumnFamilyName() {
      return COLUMN_FAMILY_NAME;
    }
  }

  public static class ActivatingCompound implements FromBrendaDB<ActivatingCompound>, Serializable {
    public static final String QUERY = "select Activating_Compound, Commentary, Literature from Activating_Compound " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    public static final String ALL_QUERY =
        "select Activating_Compound, Commentary, Literature, EC_Number, Organism from Activating_Compound";
    public static final String COLUMN_FAMILY_NAME = "Activating_Compound";
    protected static final ActivatingCompound INSTANCE = new ActivatingCompound();
    private static final long serialVersionUID = -3326349402641253159L;

    protected String activatingCompound;
    protected String commentary;

    protected ActivatingCompound() {
    }

    public ActivatingCompound(String activatingCompound, String commentary) {
      this.activatingCompound = activatingCompound;
      this.commentary = commentary;
    }

    public String getActivatingCompound() {
      return activatingCompound;
    }

    public String getCommentary() {
      return commentary;
    }

    @Override
    public ActivatingCompound fromResultSet(ResultSet resultSet) throws SQLException {
      return new ActivatingCompound(resultSet.getString(1), resultSet.getString(2));
    }

    @Override
    public int getLiteratureField() {
      return 3;
    }

    @Override
    public String getAllQuery() {
      return ALL_QUERY;
    }

    @Override
    public int getLiteratureFieldForAllQuery() {
      return 3;
    }

    @Override
    public int getECNumberFieldForAllQuery() {
      return 4;
    }

    @Override
    public int getOrganismFieldForAllQuery() {
      return 5;
    }

    @Override
    public String getColumnFamilyName() {
      return COLUMN_FAMILY_NAME;
    }
  }

  public static class KCatKMValue implements FromBrendaDB<KCatKMValue>, Serializable {
    public static final String QUERY = "select KCat_KM_Value, Substrate, Commentary, Literature from KCat_KM_Value " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    public static final String ALL_QUERY =
        "select KCat_KM_Value, Substrate, Commentary, Literature, EC_Number, Organism from KCat_KM_Value";
    public static final String COLUMN_FAMILY_NAME = "KCat_KM_Value";
    protected static final KCatKMValue INSTANCE = new KCatKMValue();
    private static final long serialVersionUID = -9166694433469659408L;

    protected Double kcatKMValue;
    protected String substrate;
    protected String commentary;

    protected KCatKMValue() {
    }

    public KCatKMValue(Double kcatKMValue, String substrate, String commentary) {
      this.kcatKMValue = kcatKMValue;
      this.substrate = substrate;
      this.commentary = commentary;
    }

    public Double getKcatKMValue() {
      return kcatKMValue;
    }

    public String getSubstrate() {
      return substrate;
    }

    public String getCommentary() {
      return commentary;
    }

    @Override
    public KCatKMValue fromResultSet(ResultSet resultSet) throws SQLException {
      return new KCatKMValue(resultSet.getDouble(1), resultSet.getString(2), resultSet.getString(3));
    }

    @Override
    public int getLiteratureField() {
      return 4;
    }

    @Override
    public String getAllQuery() {
      return ALL_QUERY;
    }

    @Override
    public int getLiteratureFieldForAllQuery() {
      return 4;
    }

    @Override
    public int getECNumberFieldForAllQuery() {
      return 5;
    }

    @Override
    public int getOrganismFieldForAllQuery() {
      return 6;
    }

    @Override
    public String getColumnFamilyName() {
      return COLUMN_FAMILY_NAME;
    }
  }

  public static class Expression implements FromBrendaDB<Expression>, Serializable {
    public static final String QUERY = "select Expression, Commentary, Literature from Expression " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    public static final String ALL_QUERY =
        "select Expression, Commentary, Literature, EC_Number, Organism from Expression";
    public static final String COLUMN_FAMILY_NAME = "Expression";
    protected static final Expression INSTANCE = new Expression();
    private static final long serialVersionUID = 49938329620615767L;

    protected String expression; // TODO: would an enum be better?
    protected String commentary;

    protected Expression() {
    }

    public Expression(String expression, String commentary) {
      this.expression = expression;
      this.commentary = commentary;
    }

    public String getExpression() {
      return expression;
    }

    public String getCommentary() {
      return commentary;
    }

    @Override
    public BrendaSupportingEntries.Expression fromResultSet(ResultSet resultSet) throws SQLException {
      return new Expression(resultSet.getString(1), resultSet.getString(2));
    }

    @Override
    public int getLiteratureField() {
      return 3;
    }

    @Override
    public String getAllQuery() {
      return ALL_QUERY;
    }

    @Override
    public int getLiteratureFieldForAllQuery() {
      return 3;
    }

    @Override
    public int getECNumberFieldForAllQuery() {
      return 4;
    }

    @Override
    public int getOrganismFieldForAllQuery() {
      return 5;
    }

    @Override
    public String getColumnFamilyName() {
      return COLUMN_FAMILY_NAME;
    }
  }

  public static class Subunits implements FromBrendaDB<Subunits>, Serializable {
    public static final String QUERY = "select Subunits, Commentary, Literature from Subunits " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    public static final String ALL_QUERY =
        "select Subunits, Commentary, Literature, EC_Number, Organism from Subunits";
    public static final String COLUMN_FAMILY_NAME = "Subunits";
    protected static final Subunits INSTANCE = new Subunits();
    private static final long serialVersionUID = -3048744632876384569L;

    protected String subunits;
    protected String commentary;

    protected Subunits() {
    }

    public Subunits(String subunits, String commentary) {
      this.subunits = subunits;
      this.commentary = commentary;
    }

    public String getSubunits() {
      return subunits;
    }

    public String getCommentary() {
      return commentary;
    }

    @Override
    public Subunits fromResultSet(ResultSet resultSet) throws SQLException {
      return new Subunits(resultSet.getString(1), resultSet.getString(2));
    }

    @Override
    public int getLiteratureField() {
      return 3;
    }

    @Override
    public String getAllQuery() {
      return ALL_QUERY;
    }

    @Override
    public int getLiteratureFieldForAllQuery() {
      return 3;
    }

    @Override
    public int getECNumberFieldForAllQuery() {
      return 4;
    }

    @Override
    public int getOrganismFieldForAllQuery() {
      return 5;
    }

    @Override
    public String getColumnFamilyName() {
      return COLUMN_FAMILY_NAME;
    }
  }

  public static class Localization implements FromBrendaDB<Localization>, Serializable {
    public static final String QUERY = "select Localization, Commentary, Literature from Localization " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    public static final String ALL_QUERY =
        "select Localization, Commentary, Literature, EC_Number, Organism from Localization";
    public static final String COLUMN_FAMILY_NAME = "Localization";
    protected static final Localization INSTANCE = new Localization();
    private static final long serialVersionUID = -7765943450126973420L;

    protected String localization;
    protected String commentary;

    protected Localization() {
    }

    public Localization(String localization, String commentary) {
      this.localization = localization;
      this.commentary = commentary;
    }

    public String getLocalization() {
      return localization;
    }

    public String getCommentary() {
      return commentary;
    }

    @Override
    public Localization fromResultSet(ResultSet resultSet) throws SQLException {
      return new Localization(resultSet.getString(1), resultSet.getString(2));
    }

    @Override
    public int getLiteratureField() {
      return 3;
    }

    @Override
    public String getAllQuery() {
      return ALL_QUERY;
    }

    @Override
    public int getLiteratureFieldForAllQuery() {
      return 3;
    }

    @Override
    public int getECNumberFieldForAllQuery() {
      return 4;
    }

    @Override
    public int getOrganismFieldForAllQuery() {
      return 5;
    }

    @Override
    public String getColumnFamilyName() {
      return COLUMN_FAMILY_NAME;
    }
  }


  /* ****************************************
   * Build an uber-index of all supporting BRENDA tables for faster integration of data into documents.
   */

  public static class IndexWriter<T extends Serializable & FromBrendaDB<T>> {
    protected ColumnFamilyHandle columnFamilyHandle;
    protected RocksDB db;
    protected Connection conn;
    protected T instance;


    public IndexWriter(ColumnFamilyHandle columnFamilyHandle, RocksDB db, Connection conn, T instance) {
      this.columnFamilyHandle = columnFamilyHandle;
      this.db = db;
      this.conn = conn;
      this.instance = instance;
    }

    public void addObjectToIndex(byte[] key, T val) throws IOException, ClassNotFoundException, RocksDBException {
      StringBuffer buffer = new StringBuffer();
      List<T> storedObjects = null;
      if (db.keyMayExist(columnFamilyHandle, key, buffer)) {
        byte[] existingVal = db.get(columnFamilyHandle, key);
        if (existingVal != null) {
          //System.out.println("Found existing key for " + new String(key, UTF8) + " with length " + existingVal.length);
          //System.out.println("Existing value: " + buffer);
          ObjectInputStream oi = new ObjectInputStream(new ByteArrayInputStream(existingVal));
          storedObjects = (ArrayList<T>) oi.readObject(); // Note: assumes all values are lists.
          //System.out.println("Number of existing objects for key: " + storedObjects.size());
        } else {
          storedObjects = new ArrayList<>(1);
        }
      } else {
        storedObjects = new ArrayList<>(1);
      }
      storedObjects.add(val);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream oo = new ObjectOutputStream(bos);
      oo.writeObject(storedObjects);
      //System.out.println("Inserting byte val with length " + bos.toByteArray().length);
      //System.out.flush();
      try {
        db.put(columnFamilyHandle, key, bos.toByteArray());
      } catch (RocksDBException e) {
        // TODO: do better;
        throw new IOException(e);
      }
    }

    public static byte[] makeKey(String ecNumber, String literature, String organism) {
      // Given that ecNumber is always \d.\d.\d.\d and literature is always a number, this should be safe.
      return StringUtils.join(new String[]{ecNumber, literature, organism}, "::").getBytes(UTF8);
    }

    public void createKeysAndWrite(ResultSet resultSet)
        throws IOException, ClassNotFoundException, RocksDBException, SQLException {
      String ecNumber = resultSet.getString(instance.getECNumberFieldForAllQuery());
      String literatureList = resultSet.getString(instance.getLiteratureFieldForAllQuery());
      String organism = resultSet.getString(instance.getOrganismFieldForAllQuery());

      T val = instance.fromResultSet(resultSet);
      List<String> literatureIds = Arrays.asList(idsFieldToArray(literatureList));
      for (String literatureId : literatureIds) {
        byte[] key = makeKey(ecNumber, literatureId, organism);
        addObjectToIndex(key, val);
      }
    }

    public void run(Connection conn)
        throws IOException, ClassNotFoundException, RocksDBException, SQLException {
      PreparedStatement stmt = conn.prepareStatement(this.instance.getAllQuery());
      // "All" query should have no arguments.
      ResultSet results = stmt.executeQuery();

      int processed = 0;
      while (results.next()) {
        createKeysAndWrite(results);
        processed++;
        if (processed % 1000 == 0) {
          System.out.println("  processed " + processed + " entries");
        }
      }
    }
  }

  // TODO: this seems suspicious.  Is there a better way to organize the various supporting classes?
  public static List<FromBrendaDB> allFromBrendaDBInstances() {
    List<FromBrendaDB> instances = new ArrayList<>();
    instances.add(KMValue.INSTANCE);
    instances.add(SpecificActivity.INSTANCE);
    instances.add(OrganismCommentary.INSTANCE);
    instances.add(GeneralInformation.INSTANCE);
    instances.add(Cofactor.INSTANCE);
    instances.add(Inhibitors.INSTANCE);
    instances.add(ActivatingCompound.INSTANCE);
    instances.add(KCatKMValue.INSTANCE);
    instances.add(Expression.INSTANCE);
    instances.add(Subunits.INSTANCE);
    instances.add(Localization.INSTANCE);
    return instances;
  }

  public void constructOnDiskBRENDAIndex(File pathToIndex, Connection conn)
      throws IOException, ClassNotFoundException, RocksDBException, SQLException {
    if (pathToIndex.exists()) {
      System.out.println("Index already exists, not recreating.");
      return;
    }

    // TODO: handle the case where the DB already exists.
    RocksDB db = null;

    List<FromBrendaDB> instances = allFromBrendaDBInstances();

    try {
      Options options = new Options().setCreateIfMissing(true);
      // TODO: choke if DB already exists.
      System.out.println("Opening index at " + pathToIndex.getAbsolutePath());
      db = RocksDB.open(options, pathToIndex.getAbsolutePath());

      for (FromBrendaDB instance : instances) {
        System.out.println("Writing index for " + instance.getColumnFamilyName());
        ColumnFamilyHandle cfh =
            db.createColumnFamily(new ColumnFamilyDescriptor(instance.getColumnFamilyName().getBytes(UTF8)));
        // TODO: is there a clean way to avoid this cast?
        IndexWriter writer = new IndexWriter(cfh, db, conn, (Serializable) instance);
        writer.run(conn);
        db.flush(new FlushOptions());
      }
    } finally {
      if (db != null) {
        db.close();
      }
    }
  }
}