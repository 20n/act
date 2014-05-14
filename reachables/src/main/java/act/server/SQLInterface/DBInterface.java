/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package act.server.SQLInterface;


import java.util.HashMap;
import java.util.List;

/**
 *
 * @author paul291
 */
public interface DBInterface {
	public List<Long> getRxnsWith(Long reactant);
    public List<Long> getRxnsWith(Long compound, Boolean product);
    public List<Long> getReactants(Long rxn);
    public List<Long> getProducts(Long rxn);
    public HashMap<Long,Double> getRarity(Long compound, Boolean product);
    public List<String> getCanonNames(Iterable<Long> compounds);
    public List<String> convertIDsToSmiles(List<Long> ids);
    public String getEC5Num(Long rxn);
    public String getDescription(Long rxn);
   
}
