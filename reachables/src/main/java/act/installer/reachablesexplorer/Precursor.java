package act.installer.reachablesexplorer;

import com.act.biointerpretation.l2expansion.ProjectionResult;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import scala.collection.JavaConversions;

import java.io.Serializable;
import java.util.List;


public class Precursor implements Serializable {
    @JsonProperty("precursor_inchis")
    private List<InchiDescriptor> precursorMolecules;

    @JsonProperty("source")
    private List<String> sources;

    @JsonCreator
    public Precursor(@JsonProperty("precursor_inchis") List<InchiDescriptor> precursorMolecules,
                     @JsonProperty("source") List<String> sources) {
        this.precursorMolecules = precursorMolecules;
        this.sources = sources;
    }

    @JsonIgnore
    public List<InchiDescriptor> getMolecules(){
        return precursorMolecules;
    }

    @JsonIgnore
    public List<String> getSources(){
        return sources;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Precursor) &&
                sources.equals(((Precursor) o).getSources()) &&
                precursorMolecules.equals(((Precursor) o).getMolecules());
    }

    @Override
    public int hashCode() {
        int start = 31;
        start = 31 * start + sources.hashCode();
        for (InchiDescriptor m: this.precursorMolecules){
            start = 31*start + m.getInchi().hashCode();
        }

        return start;
    }
}
