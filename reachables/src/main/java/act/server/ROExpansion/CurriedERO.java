package act.server.ROExpansion;

import act.server.ActAdminServiceImpl;
import act.server.Molecules.DotNotation;
import act.server.Molecules.ERO;
import act.shared.Chemical;

import java.io.BufferedWriter;
import java.util.*;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoObject;

public class CurriedERO extends ERO {

  public HashMap<String,List<String>> patternToSmiles = new HashMap<String,List<String>>();
  public String[] patterns;

  public CurriedERO(ERO ero){
    this.ro = ero.ro;
    this.patterns = findPatterns(ero);
  }

  public String[] findPatterns(ERO ero){
    String eroStr = ero.rxn();
    String[] splitEroStr = eroStr.split(">>");
    if (splitEroStr.length == 0){ return splitEroStr; }
    String left = eroStr.split(">>")[0];
    return left.split("\\.");
  }

  public void addChemicalForPattern(Chemical c, String pattern){
    List<String> smilesList;
    if (this.patternToSmiles.containsKey(pattern)){
      smilesList = this.patternToSmiles.get(pattern);
    }
    else{
      smilesList = new ArrayList<String>();
    }

    //toDotNotation gives us a canonical dot notation version of the smiles
    String smiles = Expansion.toDotNotation(c.getSmiles(), new Indigo());
    if (!smilesList.contains(smiles)){
      //System.out.println("Not already contained");
      smilesList.add(smiles);
      this.patternToSmiles.put(pattern,smilesList);
    }
    //System.out.println("Added chemical: "+this.patternToSmiles);
  }

  //like applyForPattern below, but it figures out which substrate patterns the dotNotationSmiles can match
  public HashMap<List<String>,List<List<String>>> apply(String dotNotationSmiles){
    HashMap<List<String>,List<List<String>>> result = new HashMap<List<String>,List<List<String>>>();
    for (String p : this.patterns){
      if (Expansion.patternMatchesChem(p, dotNotationSmiles)){
        HashMap<List<String>,List<List<String>>> oneResult = applyForPattern(dotNotationSmiles,p,null);
        result.putAll(oneResult);
      }
    }
    return result;
  }

  public void record(BufferedWriter bw, String s){
    try{
      bw.write(s);
      bw.write(System.getProperty("line.separator"));
      bw.flush();
    }
    catch(Exception e){
      //nothing
    }
  }

  //the result maps a list of substrates to a list of products, from this RO
  //note that we may have multiple sets of products, if a given chemical can be matched to a given pattern in multiple ways
  public HashMap<List<String>,List<List<String>>> applyForPattern(String dotNotationSmiles, String pattern, BufferedWriter bw){
    HashMap<List<String>,List<List<String>>> products = new HashMap<List<String>,List<List<String>>>();
    List<List<String>> substrateOptions = new ArrayList<List<String>>();
    record(bw, "reaction: "+this.rxn());
    record(bw, "chemical to use: "+dotNotationSmiles);
    record(bw, "pattern to use it as: "+pattern);
    for (String p : this.patterns){
      if (p.equals(pattern)){
        List<String> ls = new ArrayList<String>();
        ls.add(dotNotationSmiles);
        substrateOptions.add(ls);
      }
      else{
        List<String> possibleSubstrates = this.patternToSmiles.get(p);
        //if this list is empty, we can't actually apply this RO
        if (possibleSubstrates == null){
          return products; //currently an empty hashmap
        }
        record(bw, "    "+p+" available substrates: "+possibleSubstrates.size());
        substrateOptions.add(this.patternToSmiles.get(p));
      }
    }
    //System.out.println("substrateOptions: "+substrateOptions.toString());
    List<List<String>> substrateLists = product(substrateOptions);
    int substrateListsSize = substrateLists.size();
    record(bw,"Number of substrate sets: "+substrateListsSize);
    long totalApplicationTime = 0;
    for (List<String> substrateList : substrateLists){
      //System.out.println(substrateList);
      long start = System.currentTimeMillis();
      List<List<String>> dotNotationSmilesResults = ActAdminServiceImpl.applyRO_MultipleSubstrates_DOTNotation(substrateList, this);
      long end = System.currentTimeMillis();
      totalApplicationTime+=(end-start);
      //System.out.println(dotNotationSmilesResults);
      if (dotNotationSmilesResults != null){
        products.put(substrateList, dotNotationSmilesResults);
      }
      else {
        //System.out.println("Called applyRO, but result was null: "+ this.rxn()+" --- "+substrateList.toString());
      }
    }
    if (substrateListsSize > 0){
      record(bw,"Average application time for one substrate list: "+(totalApplicationTime/substrateListsSize));
    }
    return products;
  }

  public List<List<String>> applySubstrateList(List<String> substrateList) {
    return ActAdminServiceImpl.applyRO_MultipleSubstrates_DOTNotation(substrateList, this);
  }

    public List<List<String>> enumerateSubstrateLists() {
        List<List<String>> substrateOptions = new ArrayList<List<String>>();
        for (String p : this.patterns){
            List<String> possibleSubstrates = this.patternToSmiles.get(p);
            //if this list is empty, we can't actually apply this RO
            if (possibleSubstrates == null){
                return new ArrayList<List<String>>();
            }
            substrateOptions.add(possibleSubstrates);
        }

        return product(substrateOptions);
    }

    public int countSubstrateLists() {
        int count = 1;
        for (String p : this.patterns){
            List<String> possibleSubstrates = this.patternToSmiles.get(p);
            if(possibleSubstrates == null) {
                return 0;
            }
            count *= possibleSubstrates.size();
        }

        return count;
    }

  //this product from http://stackoverflow.com/questions/714108/cartesian-product-of-arbitrary-sets-in-java
  public static List<List<String>> product(List<List<String>> substrateSets) {
      if (substrateSets.size() < 2){
          //throw new IllegalArgumentException("Can't have a product of fewer than two sets (got " +substrateSets.size() + ")");
        return substrateSets;
      }
       return productRecurse(0, substrateSets);
  }

  private static List<List<String>> productRecurse(int index, List<List<String>> substrateSets) {
    List<List<String>> ret = new ArrayList<List<String>>();
      if (index == substrateSets.size()) {
          ret.add(new ArrayList<String>());
      } else {
          for (String onePatternSubstrates : substrateSets.get(index)) {
              for (List<String> set : productRecurse(index+1, substrateSets)) {
                  set.add(onePatternSubstrates);
                    ret.add(set);
              }
          }
      }
      return ret;
  }

    public static void canonicalizeSet(List<String> set){
        Collections.sort(set);
    }

  public String serialize() {
    return getXStream().toXML(this);
  }

  public static CurriedERO deserialize(String s){
    return (CurriedERO)getXStream().fromXML(s);
  }

  public static void testProduct(){
    List<List<String>> options = new ArrayList<List<String>>();
    options.add(Arrays.asList("a","b","c"));
    options.add(Arrays.asList("aa","bb","cc"));
    options.add(Arrays.asList("aaa","bbb"));

    List<List<String>> result = product(options);
    for (List<String> ls : result){
      System.out.println(ls.toString());
    }
  }

  public static void main(String[] args){
    //testProduct();
  }

  public boolean equals(Object o){
    if (o instanceof CurriedERO){
      return this.rxn().equals(((CurriedERO) o).rxn());
    }
    return false;
  }

}
