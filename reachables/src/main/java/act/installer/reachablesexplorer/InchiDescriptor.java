package act.installer.reachablesexplorer;

import com.act.analysis.chemicals.molecules.MoleculeFormat;
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.ChemicalKeywords;
import com.act.workflow.tool_manager.workflow.workflow_mixins.mongo.ChemicalKeywords$;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

class InchiDescriptor implements Serializable {
    @JsonIgnore
    private static final String moleculeName = "molecule_name";
    @JsonIgnore
    private static final String inchiName = "InChI";
    @JsonIgnore
    private static final String inchiKeyName = "inchi_key";

    @JsonProperty(moleculeName)
    private String name;

    @JsonProperty(inchiName)
    private String inchi;

    @JsonProperty(inchiKeyName)
    private String inchiKey;

    @JsonCreator
    public InchiDescriptor(@JsonProperty(moleculeName) String name,
                           @JsonProperty(inchiName) String inchi,
                           @JsonProperty(inchiKeyName) String inchiKey){
        this.name = name;
        this.inchi = inchi;
        this.inchiKey = inchiKey;
    }

    InchiDescriptor(Reachable reachable){
        this.name = reachable.getPageName();
        this.inchi = reachable.getInchi();
        this.inchiKey = reachable.getInchiKey();
    }

    @JsonIgnore
    public String getPageName(){
        return name;
    }

    @JsonIgnore
    public String getInchi(){
        return inchi;
    }

    @JsonIgnore
    public String getInchiKey(){
        return inchiKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InchiDescriptor that = (InchiDescriptor) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (inchi != null ? !inchi.equals(that.inchi) : that.inchi != null) return false;
        return inchiKey != null ? inchiKey.equals(that.inchiKey) : that.inchiKey == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (inchi != null ? inchi.hashCode() : 0);
        result = 31 * result + (inchiKey != null ? inchiKey.hashCode() : 0);
        return result;
    }
}
