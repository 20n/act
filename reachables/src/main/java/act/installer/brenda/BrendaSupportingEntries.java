package act.installer.brenda;

import org.apache.commons.lang3.StringUtils;

import javax.xml.transform.Result;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BrendaSupportingEntries {

    public static class Ligand {
        public static final String QUERY = "select LigandID, Ligand, inchi, molfile, groupID from ligand_molfiles";

        protected Integer ligandId;
        protected String ligand;
        protected String inchi;
        protected byte[] molfile;
        protected Integer groupId;

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
        public static final String QUERY =
                "select KM_Value, Commentary, Literature from KM_Value where Literature like ? and Organism = ?";
        protected static final KMValue INSTANCE = new KMValue();

        protected Double kmValue;
        protected String commentary;

        private KMValue() {
        }

        ;

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
                "where Literature like ? and Organism = ?";
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
        public static final String QUERY =
                "select Commentary, Literature from Organism where Literature like ? and Organism = ?";
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
                        "where Literature like ? and Organism = ?";
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
        public static final String QUERY =
                "select Cofactor, Commentary, Literature from Cofactor where Literature like ? and Organism = ?";
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
        public static final String QUERY =
                "select Inhibitors, Commentary, Literature from Inhibitors where Literature like ? and Organism = ?";
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
        public static final String QUERY =
                "select Activating_Compound, Commentary, Literature from Activating_Compound " +
                        "where Literature like ? and Organism = ?";
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
}
