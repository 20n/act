package com.act.biointerpretation.step4_mechanisminspection;

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
    private MechanisticValidator.Report report;
    private int rxnId;
    private RxnLog rxnlog;

    public ReactionDashboard(MechanisticValidator.Report report, int id, RxnLog dudlog) {
        this.report = report;
        this.rxnId = id;
        this.rxnlog = dudlog;

        try {
            initComponenets();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initComponenets() throws Exception {
        Set<String> subs2 = new HashSet<>();
        subs2.addAll(report.subInchis);
        Set<String> prods2 = new HashSet<>();
        prods2.addAll(report.prodInchis);

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
        CofactorPanel copanel = new CofactorPanel(report.subCofactors, report.prodCofactors);
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
                rxnlog.log(report.subInchis, report.prodInchis, "impossible");
            }
        });
        out.add(impossible);

        //Create okrare button for rare but valid reactions
        JButton okrare = new JButton("OK, rare");
        okrare.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rxnlog.log(report.subInchis, report.prodInchis, "ok_rare");
            }
        });
        out.add(okrare);

        //Create almost button for rare but valid reactions
        JButton almost = new JButton("almost");
        almost.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rxnlog.log(report.subInchis, report.prodInchis, "almost");
            }
        });
        out.add(almost);


        //Create unsure button for reactions that look right, but I am not confident about
        JButton unsure = new JButton("unsure");
        unsure.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rxnlog.log(report.subInchis, report.prodInchis, "unsure");
            }
        });
        out.add(unsure);

        //Create the new RO button
        JButton newro = new JButton("New RO");
        newro.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                OperatorEditor editor = new OperatorEditor();
                editor.initiate("");
                editor.setVisible(true);
            }
        });
        out.add(newro);

        return out;
    }

}
