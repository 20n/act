package com.act.biointerpretation.sandbox;

import act.shared.Chemical;
import com.act.biointerpretation.moieties.CrawlMoieties;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by jca20n on 12/9/15.
 */
public class FindMonosaccharides {
    public static void main(String[] args) {
        ChemAxonUtils.license();

        CrawlMoieties abstractor = new CrawlMoieties();
        abstractor.initiate();
        abstractor.run();
        Set<Chemical> chems = abstractor.getExactMoietyChems();

        //Hash out all those chems that are sugars
        Set<Chemical> monosacs = new HashSet<>();
        FindSugars finder = new FindSugars();
        for(Chemical achem : chems) {
            try {
                double score = finder.score(achem.getInChI());
                if(score > 0.7) {
                    monosacs.add(achem);
                }
            } catch(Exception err) {

            }
        }

        //Print out all the monosaccharides
        StringBuilder sb = new StringBuilder();
        for(Chemical achem : monosacs) {
            String inchi = achem.getInChI();
            String smiles = ChemAxonUtils.InchiToSmiles(inchi);
            String name = achem.getFirstName();

            sb.append(inchi).append("\t").append(name).append("\t").append(smiles).append("\n");
        }
        FileUtils.writeFile(sb.toString(), "output/moieties/monosaccharides.txt");
    }
}
