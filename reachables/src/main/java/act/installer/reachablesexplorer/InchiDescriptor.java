package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

class InchiDescriptor implements Serializable {
    @JsonProperty("molecule_name")
    private String name;

    @JsonProperty("inchi")
    private String inchi;

    @JsonProperty("inchi_key")
    private String inchiKey;

    @JsonCreator
    public InchiDescriptor(@JsonProperty("molecule_name") String name,
                           @JsonProperty("inchi") String inchi,
                           @JsonProperty("inchi_key") String inchiKey){
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

}
