package com.act.biointerpretation.step3_stereochemistry;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoInchi;
import com.ggasoftware.indigo.IndigoObject;

/**
 * Created by jca20n on 10/27/15.
 */
public class SplitProjector {

    public static void main(String[] args) {
        //Tartrate >> monomethyl tartrate
//        String reactantInchi = "InChI=1S/C4H6O6/c5-1(3(7)8)2(6)4(9)10/h1-2,5-6H,(H,7,8)(H,9,10)/t1?,2?";
//        String productInchi = "InChI=1/C5H8O6/c1-11-5(10)3(7)2(6)4(8)9/h2-3,6-7H,1H3,(H,8,9)/t2?,3?";
//
//        String testSubstrate = "InChI=1S/C4H6O6/c5-1(3(7)8)2(6)4(9)10/h1-2,5-6H,(H,7,8)(H,9,10)/t1-,2+";

        //menthol >> methyl menthol
        String reactantInchi = "InChI=1S/C10H20O/c1-7(2)9-5-4-8(3)6-10(9)11/h7-11H,4-6H2,1-3H3/t8?,9?,10?";
        String productInchi = "InChI=1S/C17H24O2/c1-12(2)15-10-9-13(3)11-16(15)19-17(18)14-7-5-4-6-8-14/h4-8,12-13,15-16H,9-11H2,1-3H3/t13?,15?,16?";

        String testSubstrate = "InChI=1S/C10H20O/c1-7(2)9-5-4-8(3)6-10(9)11/h7-11H,4-6H2,1-3H3/t8-,9+,10-/m1/s1";

        SplitReaction rxn = new SplitReaction(reactantInchi, productInchi);
            System.out.println(rxn.toString());

        SplitChem chem = new SplitChem(testSubstrate);
            System.out.println("test substrate:");
            System.out.println(chem.toString());

        SplitProjector projector = new SplitProjector();
        SplitChem result = projector.project(rxn, chem);

            System.out.println("projected product:");
            System.out.println(chem.toString());

        String resInchi = result.getInchi();
            System.out.println("result: " + resInchi);

        Indigo indigo = new Indigo();
        IndigoInchi iinchi = new IndigoInchi(indigo);
        IndigoObject resMol = iinchi.loadMolecule(resInchi);
            System.out.println("smiles: " + resMol.canonicalSmiles());
    }

    /**
     * Applies the Split Reaction to a substrate, and transfers the stereochemistry
     * @param reaction
     * @param substrate
     * @return
     */
    public SplitChem project(SplitReaction reaction, SplitChem substrate) {
        SplitChem out = new SplitChem(reaction.product);
        for(int i=0; i<reaction.transforms.length; i++) {
            int index = reaction.transforms[i];
            boolean stereoValue = substrate.stereos[i];

            //If there are stereochemical constraints in the substrate SplitChem for the Reaction, then this reaction does not apply
            Boolean constraintValue = reaction.substrate.stereos[i];
            if(constraintValue!=null) {
                if(constraintValue != stereoValue) {
                    return null;
                }
            }

            out.stereos[index] = stereoValue;

            if(reaction.inversions[i]) {
                out.stereos[index] = !out.stereos[index];
            }
        }
        return out;
    }
}
