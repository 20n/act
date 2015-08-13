package act.installer.brenda;

import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BrendaSupportingEntries {

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
    public static final String QUERY = StringUtils.join(new String[] {
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
     * @param conn A connection on which to prepare the statement.
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
  public static boolean findIdInList(String idList, String idToFind) {
    String[] ids = idList.split(", *");
    for (int i = 0; i < ids.length; i++) {
      if (idToFind.equals(ids[i])) {
        return true;
      }
    }
    return false;
  }

  // Classes representing data linked to the Substrates_Products and Natural_Substrates_Products tables.
  public static class KMValue implements FromBrendaDB<KMValue> {
    public static final String QUERY = "select KM_Value, Commentary, Literature from KM_Value " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    protected static final KMValue INSTANCE = new KMValue();

    protected Double kmValue;
    protected String commentary;

    private KMValue() { }

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
  }

  public static class SpecificActivity implements FromBrendaDB<SpecificActivity> {
    public static final String QUERY = "select Specific_Activity, Commentary, Literature from Specific_Activity " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    protected static final SpecificActivity INSTANCE = new SpecificActivity();

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
  }

  public static class OrganismCommentary implements FromBrendaDB<OrganismCommentary> {
    public static final String QUERY = "select Commentary, Literature from Organism " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    protected static final OrganismCommentary INSTANCE = new OrganismCommentary();

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
  }

  public static class GeneralInformation implements FromBrendaDB<GeneralInformation> {
    public static final String QUERY =
        "select General_Information, Commentary, Literature from General_Information " +
            "where EC_Number = ? and Literature like ? and Organism = ?";
    public static final GeneralInformation INSTANCE = new GeneralInformation();

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
  }

  public static class Cofactor implements FromBrendaDB<Cofactor> {
    public static final String QUERY = "select Cofactor, Commentary, Literature from Cofactor " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    protected static final Cofactor INSTANCE = new Cofactor();

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
  }

  public static class Inhibitors implements FromBrendaDB<Inhibitors> {
    public static final String QUERY = "select Inhibitors, Commentary, Literature from Inhibitors " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    protected static final Inhibitors INSTANCE = new Inhibitors();

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
  }

  public static class ActivatingCompound implements FromBrendaDB<ActivatingCompound> {
    public static final String QUERY ="select Activating_Compound, Commentary, Literature from Activating_Compound " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    protected static final ActivatingCompound INSTANCE = new ActivatingCompound();

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
  }

  public static class KCatKMValue implements FromBrendaDB<KCatKMValue> {
    public static final String QUERY = "select KCat_KM_Value, Substrate, Commentary, Literature from KCat_KM_Value " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    protected static final KCatKMValue INSTANCE = new KCatKMValue();

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
  }

  public static class Expression implements FromBrendaDB<Expression> {
    public static final String QUERY = "select Expression, Commentary, Literature from Expression " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    protected static final Expression INSTANCE = new Expression();

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
  }

  public static class Subunits implements FromBrendaDB<Subunits> {
    public static final String QUERY = "select Subunits, Commentary, Literature from Subunits " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    protected static final Subunits INSTANCE = new Subunits();

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
  }

  public static class Localization implements FromBrendaDB<Localization> {
    public static final String QUERY = "select Localization, Commentary, Literature from Localization " +
        "where EC_Number = ? and Literature like ? and Organism = ?";
    protected static final Localization INSTANCE = new Localization();

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
  }
}
