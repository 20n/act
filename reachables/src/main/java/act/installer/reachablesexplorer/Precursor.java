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

    @JsonProperty("sequences")
    private List<String> sequences;

    @JsonCreator
    public Precursor(@JsonProperty("precursor_inchis") List<InchiDescriptor> precursorMolecules,
<<<<<<< f6427738bebfbb72b456d603cf4eac8daa475ca3
                     @JsonProperty("source") String source,
                     @JsonProperty("sequences") List<String> sequences) {
        this.precursorMolecules = precursorMolecules;
        this.source = source;
        this.sequences = sequences;
=======
                     @JsonProperty("source") List<String> sources) {
        this.precursorMolecules = precursorMolecules;
        this.sources = sources;
>>>>>>> Create Precursor from ProjectionResult
    }

    @JsonIgnore
    public List<InchiDescriptor> getMolecules(){
        return precursorMolecules;
    }

    @JsonIgnore
    public List<String> getSources(){
        return sources;
    }

    @JsonIgnore
    public List<String> getSequences() {
        return sequences;
    }

    @Override
    public boolean equals(Object o) {
<<<<<<< f6427738bebfbb72b456d603cf4eac8daa475ca3
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Precursor precursor = (Precursor) o;

        if (!precursorMolecules.equals(precursor.precursorMolecules)) return false;
        if (!source.equals(precursor.source)) return false;
        return sequences != null ? sequences.equals(precursor.sequences) : precursor.sequences == null;
=======
        return (o instanceof Precursor) &&
                sources.equals(((Precursor) o).getSources()) &&
                precursorMolecules.equals(((Precursor) o).getMolecules());
>>>>>>> Create Precursor from ProjectionResult
    }

    @Override
    public int hashCode() {
<<<<<<< f6427738bebfbb72b456d603cf4eac8daa475ca3
        int result = precursorMolecules.hashCode();
        result = 31 * result + source.hashCode();
        result = 31 * result + (sequences != null ? sequences.hashCode() : 0);
        return result;
=======
        int start = 31;
        start = 31 * start + sources.hashCode();
        for (InchiDescriptor m: this.precursorMolecules){
            start = 31*start + m.getInchi().hashCode();
        }

        return start;
>>>>>>> Create Precursor from ProjectionResult
    }
}
