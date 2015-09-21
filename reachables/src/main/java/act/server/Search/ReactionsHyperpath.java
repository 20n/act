package act.server.Search;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReactionsHyperpath<N, E> extends ReactionsHypergraph<N, E>{
  private List<E> ordering;
  public ReactionsHyperpath() {
    super();
    ordering = new ArrayList<E>();
  }

  public List<E> getOrdering() { return ordering; }
  public void appendReaction(E reaction) {
    ordering.add(reaction);
    reactionLabels.put(reaction, "Step " + ordering.size());
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ReactionsHyperpath<?, ?>)) return false;
    ReactionsHyperpath<N, E> other = (ReactionsHyperpath<N, E>) o;
    return other.reactions.equals(this.reactions);
  }

  @Override
  public int hashCode() {
    return this.reactions.hashCode();
  }

  @Override
  public String toString() {
    String result = "";
    for (E reaction : ordering) {
      result += reaction + ": " +
          this.getReactants(reaction) + " -> " +
          this.getProducts(reaction) + "\n";
    }
    return result;
  }

  /**
   * HTML table representation
   */
  private Map<E, Map<String, Object>> reactionProperties = new HashMap();
  private List<String> propertyList = null;

  /**
   * Sets the ordering of fields to display (columns of HTML table)
   * @param fields
   */
  public void setFields(List<String> fields) {
    propertyList = new ArrayList(fields);
  }

  /**
   * Add a field's value for a reaction.
   * @param node
   * @param field
   * @param value
   */
  public void addField(E reaction, String field, Object value) {
    if (!reactionProperties.containsKey(reaction)) {
      reactionProperties.put(reaction, new HashMap());
    }
    Map properties = reactionProperties.get(reaction);
    properties.put(field, value);
  }

  /**
   * Writes html table to filename.
   * @param filename
   * @throws IOException
   */
  public void writeHTMLTable(String filename, String svgFilename) throws IOException {
    BufferedWriter htmlFile = new BufferedWriter(new FileWriter(filename, false));
    htmlFile.write("<html><head></head>\n<body>\n");

    htmlFile.write("<table border=\"1\">");
    htmlFile.write("<tr>");
    for (String field : propertyList) {
      htmlFile.write("<th>" + field + "</th>");
    }
    htmlFile.write("</tr>\n");

    int step = 1;
    for (E reaction : ordering) {
      htmlFile.write("<tr>");
      for (String field : propertyList) {
        Object value = null;
        Map<String, Object> properties = reactionProperties.get(reaction);
        if (properties != null) value = properties.get(field);
        if (value == null && field.equals("Step")) value = "" + step;
        htmlFile.write("<td>" + value + "</td>");
      }
      htmlFile.write("</tr>\n");
      step++;
    }
    htmlFile.write("</table>\n");

    if (svgFilename != null) {
      htmlFile.write("<embed src=\""+ svgFilename +"\" type=\"image/svg+xml\" />");
    }

    htmlFile.write("</body></html>");
    htmlFile.close();
  }

  /**
   * End of table representation
   */
}
