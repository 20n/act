package com.act.biointerpretation.step3_mechanisminspection;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.cofactors.CofactorPanel;
import com.act.biointerpretation.cofactors.MolViewer;
import com.act.biointerpretation.utils.ChemAxonUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by jca20n on 1/7/16.
 */
public class ReactionDashboard extends JFrame {
    private Set<String> subs;
    private Set<String> prods;
    private int rxnId;
    private RxnLog rxnlog;

    public ReactionDashboard(Set<String> subs, Set<String> prods, int id, RxnLog dudlog) {
        this.subs = subs;
        this.prods = prods;
        this.rxnId = id;
        this.rxnlog = dudlog;

        try {
            initComponenets();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initComponenets() throws Exception {
        //Process cofactors
        MechanisticValidator_old validator = new MechanisticValidator_old();
        validator.initiate();

        Set<String> subs2 = new HashSet<>();
        subs2.addAll(subs);
        Set<String> prods2 = new HashSet<>();
        prods2.addAll(prods);

        Set<String> subCos = validator.pullCofactors(subs2);
        Set<String> prodCos = validator.pullCofactors(prods2);

        //Construct the reaction
        String srxn = "";
        for(String inchi : subs2) {
            srxn += ChemAxonUtils.InchiToSmiles(inchi);
            srxn+=".";
            System.out.println(inchi);
        }
        srxn = srxn.substring(0,srxn.length()-1);
        srxn += ">>";
        for(String inchi : prods2) {
            srxn += ChemAxonUtils.InchiToSmiles(inchi);
            srxn+=".";
            System.out.println(inchi);
        }
        srxn = srxn.substring(0,srxn.length()-1);
        RxnMolecule rxnMolecule = RxnMolecule.getReaction(MolImporter.importMol(srxn));

        //Create the buffered image and MolPanel
        byte[] bytes = MolExporter.exportToBinFormat(rxnMolecule, "png:w900,h450,amap");
        InputStream in = new ByteArrayInputStream(bytes);
        BufferedImage bImageFromConvert = ImageIO.read(in);

        MolViewer molpanel = new MolViewer(bImageFromConvert);
        getContentPane().add(molpanel, BorderLayout.CENTER);

        //Put in the cofactor header
        CofactorPanel copanel = new CofactorPanel(subCos, prodCos);
        getContentPane().add(copanel, BorderLayout.NORTH);

        //Put in the buttons
        JPanel controls = createControlPanel();
        getContentPane().add(controls, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(900, 800));
        pack();
    }

    private JPanel createControlPanel() {
        JPanel out = new JPanel();

        //Create the impossible button
        JButton impossible = new JButton("impossible");
        impossible.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rxnlog.log(subs, prods, "impossible");
            }
        });
        out.add(impossible);

        //Create okrare button for rare but valid reactions
        JButton okrare = new JButton("OK, rare");
        okrare.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rxnlog.log(subs, prods, "ok_rare");
            }
        });
        out.add(okrare);

        //Create almost button for rare but valid reactions
        JButton almost = new JButton("almost");
        almost.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rxnlog.log(subs, prods, "almost");
            }
        });
        out.add(almost);


        //Create unsure button for reactions that look right, but I am not confident about
        JButton unsure = new JButton("unsure");
        unsure.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rxnlog.log(subs, prods, "unsure");
            }
        });
        out.add(unsure);

        //Create the new RO button
        JButton newro = new JButton("New RO");
        newro.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                OperatorEditor editor = new OperatorEditor("");
                editor.setVisible(true);
            }
        });
        out.add(newro);

        return out;
    }

    public static void main(String[] args) {
        //NAD+ + propanol >> NADPH + propanal
        Set<String> subs = new HashSet<>();
        subs.add("InChI=1S/C3H8O/c1-2-3-4/h4H,2-3H2,1H3");
        subs.add("InChI=1S/C21H27N7O14P2/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(32)14(30)11(41-21)6-39-44(36,37)42-43(34,35)38-5-10-13(29)15(31)20(40-10)27-3-1-2-9(4-27)18(23)33/h1-4,7-8,10-11,13-16,20-21,29-32H,5-6H2,(H5-,22,23,24,25,33,34,35,36,37)/p+1/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1");

        Set<String> prods = new HashSet<>();
        prods.add("InChI=1S/C21H29N7O14P2/c22-17-12-19(25-7-24-17)28(8-26-12)21-16(32)14(30)11(41-21)6-39-44(36,37)42-43(34,35)38-5-10-13(29)15(31)20(40-10)27-3-1-2-9(4-27)18(23)33/h1,3-4,7-8,10-11,13-16,20-21,29-32H,2,5-6H2,(H2,23,33)(H,34,35)(H,36,37)(H2,22,24,25)/t10-,11-,13-,14-,15-,16-,20-,21-/m1/s1");
        prods.add("InChI=1S/C3H6O/c1-2-3-4/h3H,2H2,1H3");


        ReactionDashboard dash = new ReactionDashboard(subs, prods, 23, new RxnLog());
        dash.setVisible(true);
    }
}
