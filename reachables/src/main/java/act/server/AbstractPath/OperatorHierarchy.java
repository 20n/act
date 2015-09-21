package act.server.AbstractPath;

import java.util.List;

import act.server.SQLInterface.MongoDB;
import act.shared.Chemical;

/*
 * Encapsulates the hierarchy of transforms
 * Provides the basic function of a single step (maybe aggregate) transformation
 * chem' := Transform(chem, fwd?, nth)
 * Given the output chem' from the input chem in either the fwd? or !fwd? direction
 * Also skips the first nth possible chem' outputs.
 *
 * For efficiency, implementers of this interface, will cache the last returned (chem',chem,fwd?,nth)
 * tuples and keep pointers in the hierarchy so that they can efficiently output the n+1th chem'
 * in the subsequent call.
 *
 * The call can also return NULL if there is no nth transforms possible for this chem.
 */
public interface OperatorHierarchy {
  // static OperatorHierarchy getInstance(MongoDB db);

  /*
   * Note: Only one chemical queried; vs Operators in general are over multiple chems on either side
   * We consider a match successful if at least one of reactants/products of the operator matches
   * the chemical queried.
   */
  public Applier Transform(Chemical start, boolean fwd, int nth);
}

class Applier {
  int nextIndex;
  List<Chemical> srcChems, dstChems;
  Chemical onChem;
}