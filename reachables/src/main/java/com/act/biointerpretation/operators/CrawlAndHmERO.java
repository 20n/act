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
        int count = 0;
        for(Integer rxnId : rxnIds) {
            try {
                count++;
                System.out.println(count);
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
//        try {
//            fis = new FileInputStream("output/mapped_reactions/automap/range" + iround + "/" + rxnId + ".ser");
//            ois = new ObjectInputStream(fis);
//            RxnMolecule mapped = (RxnMolecule) ois.readObject();
//            ois.close();
//            fis.close();
//            save(rxnId, srxn, mapped);
//        } catch(Exception err) {
//        }

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
        //Calculate the ros
        RxnMolecule hmCRO = extractor.calc_hmCRO(mapped);
        RxnMolecule hmERO = extractor.calc_hmERO(mapped);
        RxnMolecule hcERO = extractor.calc_hcERO(mapped);

        //Create the directory paths
        String croPath = SHA1(ChemAxonUtils.getReactionHash(hmCRO));
        String hmEROPath = SHA1(ChemAxonUtils.getReactionHash(hmERO));
        String hcEROPath = SHA1(ChemAxonUtils.getReactionHash(hcERO));

        //Create the cro directory
        File croDir = new File(dir.getAbsolutePath() + "/" + croPath);
        if(!croDir.exists()) {
            croDir.mkdir();
            String croSmarts = ChemAxonUtils.toSMARTS(hmCRO);
            FileUtils.writeFile(croSmarts, croDir.getAbsolutePath() + "/cro.txt");
        }

        //Create the hmERO directory
        File hmERODir = new File(croDir.getAbsolutePath() + "/" + hmEROPath);
        if(!hmERODir.exists()) {
            hmERODir.mkdir();
            String eroSmarts = ChemAxonUtils.toSMARTS(hmERO);
            FileUtils.writeFile(eroSmarts, hmERODir.getAbsolutePath() + "/hmERO.txt");
        }

        //Create the hcERO directory
        File hcERODir = new File(hmERODir.getAbsolutePath() + "/" + hcEROPath);
        if(!hcERODir.exists()) {
            hcERODir.mkdir();
            String eroSmarts = ChemAxonUtils.toSMARTS(hcERO);
            FileUtils.writeFile(eroSmarts, hcERODir.getAbsolutePath() + "/hcERO.txt");
        }

        //Create the object
        ReactionInterpretation ro = new ReactionInterpretation();
        ro.hmERO = ChemAxonUtils.toSMARTS(hmERO);
        ro.hcERO = ChemAxonUtils.toSMARTS(hcERO);
        ro.mapping = ChemAxonUtils.toSmiles(mapped);
        ro.prodCofactors = srxn.prodCofactors;
        ro.subCofactors = srxn.subCofactors;
        ro.rxnId = rxnId;

        //Save to directory
        String data = ro.toString();
        String rxnHash = SHA1(ChemAxonUtils.getReactionHash(mapped));
        FileUtils.writeFile(data, hcERODir.getAbsolutePath() + "/" + rxnHash + ".txt");
    }

    private static String SHA1(String hash) {
        try {
            MessageDigest mDigest = MessageDigest.getInstance("SHA1");
            byte[] result = mDigest.digest(hash.getBytes());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < result.length; i++) {
                sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (Exception ex) {
            System.out.println("Error getting SHA1");
        }
        return null;
    }
}
