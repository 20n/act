package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

class InchiDescriptor implements Serializable {
    @JsonProperty("molecule_name")
    private String name;

    @JsonProperty("InChI")
    private String inchi;

    @JsonProperty("inchi_key")
    private String inchiKey;

    @JsonCreator
    public InchiDescriptor(String name,
                           String inchi,
                           String inchiKey){
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
