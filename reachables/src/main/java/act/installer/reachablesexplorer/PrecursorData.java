package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class PrecursorData implements Serializable {
    @JsonProperty("precursors")
    private Set<Precursor> precursors;

    public PrecursorData() {
        this.precursors = new HashSet<>();
    }

    public PrecursorData(Set<Precursor> precursors) {
        this.precursors = precursors;
    }

    @JsonIgnore
    public void addPrecursor(Precursor precursor) {
        if (this.precursors == null) {
            this.precursors = new HashSet<>();
        }
        this.precursors.add(precursor);
    }

    @JsonIgnore
    public void addPrecursors(Collection<Precursor> precursors) {
        precursors.stream().forEach(this::addPrecursor);
    }

    public Set<Precursor> getPrecursors() {
        return this.precursors;
    }

    public void setPrecursors(Collection<Precursor> precursors){
        this.precursors = new HashSet<>(precursors);
    }
}
