package act.server.Molecules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import act.shared.helpers.P;

class MorS {
  private String smile;
  private MolGraph graph;
  private MorS(MolGraph g) {
    if (g == null) try { throw new Exception(); } catch (Exception e) { System.out.println("MorS: Received null MolGraph: " + e.getMessage()); e.printStackTrace(); System.exit(-1); }
    this.smile = null; this.graph = g;
  }
  private MorS(String s) {
    if (s == null) try { throw new Exception(); } catch (Exception e) { System.out.println("MorS: Received null String: " + e.getMessage()); e.printStackTrace(); System.exit(-1); }
    this.smile = s; this.graph = null;
  }

  public boolean isMolGraph() { return this.smile == null; }
  public boolean isSmile() { return this.graph == null; }
  public String getSmile() { return this.smile; }
  public MolGraph getMolGraph() { return this.graph; }

  public static List<MorS> convertFromSmiles(List<String> ll) {
    List<MorS> l = new ArrayList<MorS>();
    for (String s : ll)
      l.add(new MorS(s));
    return l;
  }
  public static List<MorS> convertFromGraph(List<MolGraph> ll) {
    List<MorS> l = new ArrayList<MorS>();
    for (MolGraph s : ll)
      l.add(new MorS(s));
    return l;
  }
  public static HashMap<P<String, String>, Double> convertToSmiles(HashMap<P<MorS, MorS>, Double> setin) {
    HashMap<P<String, String>, Double> h = new HashMap<P<String, String>, Double>();
    for (P<MorS, MorS> sin : setin.keySet()) {
      h.put(new P<String, String>(
          sin.fst() == null ? null : sin.fst().smile,
          sin.snd() == null ? null : sin.snd().smile
      ), setin.get(sin));
    }
    return h;
  }
  public static Set<P<String, String>> convertToSmiles(Set<P<MorS, MorS>> setin) {
    Set<P<String, String>> s = new HashSet<P<String, String>>();
    for (P<MorS, MorS> sin : setin)
      s.add(new P<String, String>(
          sin.fst() == null ? null : sin.fst().smile,
          sin.snd() == null ? null : sin.snd().smile));
    return s;
  }
  public static Set<P<MolGraph, MolGraph>> convertToGraph(Set<P<MorS, MorS>> setin) {
    Set<P<MolGraph, MolGraph>> s = new HashSet<P<MolGraph, MolGraph>>();
    for (P<MorS, MorS> sin : setin)
      s.add(new P<MolGraph, MolGraph>(
          sin.fst() == null ? null : sin.fst().graph,
          sin.snd() == null ? null : sin.snd().graph));
    return s;
  }
}

public class MolSimilarity {
  enum Type { DeltaC, CorrHeavyAtomsPearson, CorrHeavyAtomsCount }

  // returns a similarity measure between [0.0, 1.0]
  public static Double similarity(Type type, MorS smile1, MorS smile2) {
    switch (type) {
      case DeltaC: return deltaCarbonsSimilarity(smile1, smile2);
      case CorrHeavyAtomsPearson: return corrHeavyAtomsLinearPearson(smile1, smile2);
      case CorrHeavyAtomsCount: return corrHeavyAtomsCount(smile1, smile2);
    }
    System.err.println("Unknown similarity type." + type);
    System.exit(-1);
    return null;
  }

  private static Double corrHeavyAtomsLinearPearson(MorS smile1, MorS smile2) {
    // computes the correlation between the vectors of heavy atoms counts between the two strings.
    Integer[] atoms1 = getHeavyVector(smile1);
    Integer[] atoms2 = getHeavyVector(smile2);

    return Math.abs( // the correlation will be [-1,1], so we abs for the similarity [0,1]
        pearsonCorrelation(atoms1, atoms2)
    );
  }

  private static Double corrHeavyAtomsCount(MorS smile1, MorS smile2) {
    // computes the correlation between the vectors of heavy atoms counts between the two strings.
    Integer[] atoms1 = getHeavyVector(smile1);
    Integer[] atoms2 = getHeavyVector(smile2);

    // average the similarity on the heavy atoms...
    Double sim = 0.0;
    for (int a = 0; a<atoms1.length; a++)
      sim += 1.0 / ( 1.0 + Math.abs(atoms1[a] - atoms2[a]) );
    sim /= atoms1.length;

    return sim;
  }

  private static Integer[] getHeavyVector(String smile) {
    List<Integer> atoms = new ArrayList<Integer>();
    char[] heavyAtoms = new char[] { 'C', 'O', 'P', 'N' };
    for (char atom : heavyAtoms) {
      atoms.add(countAtoms(smile, atom));
    }
    return atoms.toArray(new Integer[]{});
  }

  private static Integer[] getHeavyVector(MorS m) {
    return m.isSmile() ? getHeavyVector(m.getSmile()) : getHeavyVector(m.getMolGraph());
  }

  private static Integer[] getHeavyVector(MolGraph smile) {
    List<Integer> atoms = new ArrayList<Integer>();
    Atom[] heavyAtoms = new Atom[] { new Atom(Element.C), new Atom(Element.O), new Atom(Element.P), new Atom(Element.N) };
    for (Atom atom : heavyAtoms) {
      atoms.add(countAtoms(smile, atom));
    }
    return atoms.toArray(new Integer[]{});
  }

  private static Double pearsonCorrelation(Integer[] X, Integer[] Y) {
    // http://en.wikipedia.org/wiki/Correlation#Pearson.27s_product-moment_coefficient
    if (X.length != Y.length) { System.err.println("Correlation between unequal vectors?"); System.exit(-1); }
    int len = X.length;
    Double avgX = 0.0, avgY = 0.0;
    for (int i = 0; i <len; i++) { avgX += X[i]; avgY += Y[i]; }
    avgX /= len; avgY /= len;
    Double[] diffX = new Double[len], diffY = new Double[len];
    for (int i = 0; i < len; i++) { diffX[i] = X[i] - avgX; diffY[i] = Y[i] - avgY; }
    Double sum_MomentXTimesMomentY = 0.0, stddevX = 0.0, stddevY = 0.0;
    for (int i = 0; i < len; i++) sum_MomentXTimesMomentY += diffX[i] * diffY[i];
    for (int i = 0; i < len; i++) stddevX += diffX[i] * diffX[i];
    for (int i = 0; i < len; i++) stddevY += diffY[i] * diffY[i];
    if (stddevX == 0.0 || stddevY == 0.0) { System.err.println("Pearson only defined when stddev of X,Y finite and non-zero."); System.exit(-1); }
    Double corr = sum_MomentXTimesMomentY / Math.sqrt(stddevX * stddevY);
    return corr;
  }

  private static Double deltaCarbonsSimilarity(MorS n1, MorS n2) {
    int c1, c2;
    if (n1.isSmile()) {
      c1 = countAtoms(n1.getSmile(),'C'); c2 = countAtoms(n2.getSmile(),'C');
    } else {
      c1 = countAtoms(n1.getMolGraph(), new Atom(Element.C)); c2 = countAtoms(n2.getMolGraph(),new Atom(Element.C));
    }
    return 1.0 / ( 1.0 + Math.abs(c1 - c2) );
  }

  public static int countAtoms(MolGraph mol, Atom atom) {
    int count = 0;
    for (int a : mol.GetNodeIDs())
      count += mol.GetNodeType(a).equals(atom) ? 1 : 0;
    return count;
  }

  private static int countAtoms(String smile, char atom) {
    char atomUpper = Character.toUpperCase(atom);
    char atomLower = Character.toLowerCase(atom);
    int count = 0;
    for (int i = 0; i<smile.length(); i++) {
      char c = smile.charAt(i);
      count += (c == atomUpper || c == atomLower) ? 1 : 0;
    }
    return count;
  }
}
