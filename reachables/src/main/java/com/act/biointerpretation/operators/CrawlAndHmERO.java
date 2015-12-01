package com.act.biointerpretation.operators;

import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.cofactors.SimpleReaction;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by jca20n on 11/21/15.
 */
public class CrawlAndHmERO {
    private OperatorExtractor extractor;
    private File dir;

    public CrawlAndHmERO() {
        extractor = new OperatorExtractor();
    }

    public static void main(String[] args) {
        CrawlAndHmERO crawl = new CrawlAndHmERO();
        crawl.run();
    }

    public void run() {
        dir = new File("output/hmEROs");
        if(!dir.exists()) {
            dir.mkdir();
        }

        //Collate all the indices of pre-mapped reactions
        Set<Integer> rxnIds = new HashSet<>();
        File ssdir = new File("output/simple_reactions");
        for(File range : ssdir.listFiles()) {
            if(!range.getName().startsWith("range")) {
                continue;
            }
            for(File afile : range.listFiles()) {
                String name = afile.getName();
                if(!name.endsWith(".ser")) {
                    continue;
                }
                String sid = name.replaceAll(".ser", "");
                int id = Integer.parseInt(sid);
                rxnIds.add(id);
            }
        }

        //Crawl through mapped rxns and process each
        for(Integer rxnId : rxnIds) {
            try {
                processOne(rxnId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println();
    }

    private void processOne(int rxnId) throws Exception {
        double dround = Math.floor(rxnId / 1000);
        int iround = (int) dround;

        //Deserialize the SimpleReaction
        FileInputStream fis = new FileInputStream("output/simple_reactions/range" + iround + "/" + rxnId + ".ser");
        ObjectInputStream ois = new ObjectInputStream(fis);
        SimpleReaction srxn =  (SimpleReaction) ois.readObject();
        ois.close();
        fis.close();

        //Deserialize the AutoMapper-mapped rxn
        try {
            fis = new FileInputStream("output/mapped_reactions/automap/range" + iround + "/" + rxnId + ".ser");
            ois = new ObjectInputStream(fis);
            RxnMolecule mapped = (RxnMolecule) ois.readObject();
            ois.close();
            fis.close();
            save(rxnId, srxn, mapped);
        } catch(Exception err) {
        }

        //Deserialize the SkeletonMapper-mapped rxn
        try {
            fis = new FileInputStream("output/mapped_reactions/skeleton/range" + iround + "/" + rxnId + ".ser");
            ois = new ObjectInputStream(fis);
            RxnMolecule mapped = (RxnMolecule) ois.readObject();
            ois.close();
            fis.close();
            save(rxnId, srxn, mapped);
        } catch(Exception err) {
        }

        System.out.println();
    }

    private void save(int rxnId, SimpleReaction srxn, RxnMolecule mapped) {
        //Calculate the hmCRO
        RxnMolecule cro = extractor.calc_hmCRO(mapped);

        //Create the cro directory info
        String croSmiles = ChemAxonUtils.toSMARTS(cro);

        List<String> inchis = new ArrayList<>();
        for(Molecule mol : cro.getReactants()) {
            inchis.add(ChemAxonUtils.toInchi(mol));
        }
        for(Molecule mol : cro.getProducts()) {
            inchis.add(ChemAxonUtils.toInchi(mol));
        }

        String croPath = getRxnDir(inchis);

        File croDir = new File(dir.getAbsolutePath() + "/" + croPath);
        if(!croDir.exists()) {
            croDir.mkdir();
            StringBuilder sb = new StringBuilder();
            sb.append("cro\t").append(croSmiles).append("\n");
            for(String inchi : inchis) {
                sb.append(inchi).append("\n");
            }

            FileUtils.writeFile(sb.toString(), croDir.getAbsolutePath() + "/cro.txt");
        }

        //Calculate the hmERO

        //Create the object
        ReactionInterpretation ro = new ReactionInterpretation();
        ro.ro = "";
        ro.mapping = ChemAxonUtils.toSmiles(mapped);
        ro.prodCofactors = srxn.prodCofactors;
        ro.subCofactors = srxn.subCofactors;
        ro.rxnId = rxnId;

        //Save to directory
    }

    public static String getRxnDir(List<String> inchis) {
        try {
            MessageDigest mDigest = MessageDigest.getInstance("SHA1");

            String input = "";
            for(String inchi : inchis) {
                input += inchi;
            }

            byte[] result = mDigest.digest(input.getBytes());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < result.length; i++) {
                sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (Exception ex) {
            System.out.println("Error getting rxnDir");
        }
        return null;
    }
}
