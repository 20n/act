package com.act.biointerpretation.cofactors;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import chemaxon.formats.MolExporter;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.FileUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by jca20n on 11/8/15.
 */
public class CofactorGUIHelper {

    private Reaction currReaction;
    private RxnMolecule currRxnMolecule;
    private ReactionSimplifier.SimpleReaction currSRxn;
    private BufferedImage bf;

    private Iterator<Reaction> iterator;
    private ReactionSimplifier simplifier;
    private NoSQLAPI api;

    private Set<Long> ignoreReactions;

    public void initiate() {
        api = new NoSQLAPI("synapse", "synapse");  //read only for this method
        iterator = api.readRxnsFromInKnowledgeGraph();
        simplifier = ReactionSimplifier.generate(api);

        ignoreReactions = new HashSet<>();
        String rxnids = FileUtils.readFile("data/ignore_reactions.txt").trim();
        String[] lines = rxnids.split("\\r|\\r?\\n");
        for(String line : lines) {
            if(line.isEmpty()) {
                continue;
            }
            Long id = Long.parseLong(line);
            ignoreReactions.add(id);
        }

        goNext();
    }

    public BufferedImage getReactionImage() {
        return bf;
    }

    public ReactionSimplifier.SimpleReaction getSimpleReaction() {
        return currSRxn;
    }

    public void goNext() {
        while(true) {
            try {
                //Get the next parsible reaction
                double rand = Math.random()* 928855;
                long irand = (long) Math.floor(rand);

                Reaction rxn = api.readReactionFromInKnowledgeGraph(irand);

                long longId = Long.valueOf(rxn.getUUID());
                if(ignoreReactions.contains(longId)) {
                    continue;
                }

                update(rxn);

//                //Restrict to search for nucleotide sugars (temporary)
//                if(this.currSRxn.prodCofactors.contains("GDP")
//                        || this.currSRxn.prodCofactors.contains("CDP")
//                        || this.currSRxn.prodCofactors.contains("TDP")
//                        || this.currSRxn.prodCofactors.contains("ADP")
//                        || this.currSRxn.prodCofactors.contains("UDP")
//                        || this.currSRxn.prodCofactors.contains("dGDP")
//                        || this.currSRxn.prodCofactors.contains("dCDP")
//                        || this.currSRxn.prodCofactors.contains("dTDP")
//                        || this.currSRxn.prodCofactors.contains("dCDP")
//                        || this.currSRxn.prodCofactors.contains("dUDP")) {
//
//                } else {continue;}
//
//                if(this.currSRxn.subCofactors.size() > 0) {
//                    continue;
//                }

                //Show the GUI
                new CofactorGUI(this).setVisible(true);
                break;
            }
            catch(Exception err) {
            }
        }
    }

    private void update(Reaction rxn) throws Exception {
        ReactionSimplifier.SimpleReaction srxn = simplifier.simplify(rxn);
        RxnMolecule rxnMolecule = srxn.getRxnMolecule();

        //Create the buffered image
        byte[] bytes = MolExporter.exportToBinFormat(rxnMolecule, "png:w900,h450,amap");
        InputStream in = new ByteArrayInputStream(bytes);
        BufferedImage bImageFromConvert = ImageIO.read(in);

        this.currReaction = rxn;
        this.currSRxn = srxn;
        this.currRxnMolecule = rxnMolecule;
        this.bf = bImageFromConvert;
    }

    public static void main(String[] args) {
        CofactorGUIHelper helper = new CofactorGUIHelper();
        helper.initiate();
    }

    public void addCofactorToList(String name, String inchi) {
        System.out.println("Work in progress " + name + "  " + inchi);
        String data = FileUtils.readFile("data/cofactor_data.txt");
        data = data.replace("\"", "");
        data += "\n";
        data += inchi;
        data += "\t";
        data += name;
        FileUtils.writeFile(data, "data/cofactor_data.txt");

        simplifier = ReactionSimplifier.generate(api);
    }

    public void redraw() {
        try {
            update(this.currReaction);
            new CofactorGUI(this).setVisible(true);
        } catch (Exception e) {
        }

    }

    public void ignore() {
        long id = this.currReaction.getUUID();
        ignoreReactions.add(id);

        String rxnids = FileUtils.readFile("data/ignore_reactions.txt").trim();
        rxnids += "\n" + id;
        FileUtils.writeFile(rxnids, "data/ignore_reactions.txt");
    }
}
