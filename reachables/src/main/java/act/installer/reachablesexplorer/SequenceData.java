package act.installer.reachablesexplorer;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.mongojack.ObjectId;

public class SequenceData implements Comparable<SequenceData> {

  @ObjectId
  @JsonProperty("_id")
  private String id;

  @JsonProperty("organism_name")
  private String organismName;

  @JsonProperty("sequence")
  private String sequence;

  // No serialization errors, please.
  private SequenceData() {

  }

  public SequenceData(String organismName, String sequence) {
    this.organismName = organismName;
    this.sequence = sequence;
  }

  public String getId() {
    return id;
  }

  private void setId(String id) {
    this.id = id;
  }

  public String getOrganismName() {
    return organismName;
  }

  private void setOrganismName(String organismName) {
    this.organismName = organismName;
  }

  public String getSequence() {
    return sequence;
  }

  private void setSequence(String sequence) {
    this.sequence = sequence;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SequenceData that = (SequenceData) o;

    if (!organismName.equals(that.organismName)) return false;
    return sequence.equals(that.sequence);

  }

  @Override
  public int hashCode() {
    int result = organismName.hashCode();
    result = 31 * result + sequence.hashCode();
    return result;
  }

  @Override
  public int compareTo(SequenceData o) {
    int result = getOrganismName().compareTo(o.getOrganismName());
    return result != 0 ? result : getSequence().compareTo(o.getSequence());
  }
}
