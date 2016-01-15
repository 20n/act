package com.act.biointerpretation.step3_mechanisminspection;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.RxnMolecule;
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

/**
 * Created by jca20n on 1/6/16.
 */
public class OperatorEditor extends JFrame {
    JTextField rofield;
    MolViewer molpanel;
    NoSQLAPI api;
    MechanisticValidator validator;

    private long currrxn = 0;

    public void initiate(String operator) {
        initComponents();
        this.api = new NoSQLAPI("synapse", "synapse");
        validator = new MechanisticValidator(api);
        validator.initiate();
        rofield.setText(operator);
    }

    private void initComponents() {
        //Create the input panel
        JPanel inputArea = new JPanel();
        getContentPane().add(inputArea, BorderLayout.NORTH);

        //Put in the ro entry field
        rofield = new JTextField();
        rofield.setPreferredSize(new Dimension(800, 40));
        inputArea.add(rofield);

        //Put in the show button
        JButton show = new JButton("show");
        show.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String input = rofield.getText().trim();
                try {
                    addMolPanel(input);
                    currrxn = 0;
                } catch (Exception e1) {
//                    e1.printStackTrace();
                }
            }
        });
        inputArea.add(show);

        //Put in the scan button
        JButton scan = new JButton("scan");
        scan.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String input = rofield.getText().trim();
                try {
                    goNext(input);
                } catch (Exception e1) {
                }
            }
        });
        inputArea.add(scan);

        setPreferredSize(new Dimension(1000, 600));
        pack();
    }

    private void goNext(String input) throws Exception {
        //Pull the RO
        RxnMolecule ro = RxnMolecule.getReaction(MolImporter.importMol(input));
        for(long i=this.currrxn+1; i<99999999; i++) {
            currrxn = i;
            Reaction rxn = api.readReactionFromInKnowledgeGraph(i);
            MechanisticValidator.Report report = validator.validateOne(rxn, ro);
            if(report.score > 0) {
                String smiles = "";
                for(String inchi : report.subInchis) {
                    String asmile = ChemAxonUtils.InchiToSmiles(inchi);
                    smiles+=asmile;
                    smiles+=".";
                }
                smiles = smiles.substring(0, smiles.length()-1);
                smiles+=">>";
                for(String inchi : report.prodInchis) {
                    String asmile = ChemAxonUtils.InchiToSmiles(inchi);
                    smiles+=asmile;
                    smiles+=".";
                }
                smiles = smiles.substring(0, smiles.length()-1);
                addMolPanel(smiles);
                break;
            }

        }
    }

    private void addMolPanel(String input) throws Exception {
        RxnMolecule rxn = RxnMolecule.getReaction(MolImporter.importMol(input));
        //Create the buffered image and MolPanel
        byte[] bytes = MolExporter.exportToBinFormat(rxn, "png:w900,h450,amap");
        InputStream in = new ByteArrayInputStream(bytes);
        BufferedImage bImageFromConvert = ImageIO.read(in);

        if(molpanel != null) {
            getContentPane().remove(molpanel);
        }

        molpanel = new MolViewer(bImageFromConvert);
        getContentPane().add(molpanel, BorderLayout.CENTER);
        validate();
        repaint();
    }

    public static void main(String[] args) {
        ChemAxonUtils.license();
        OperatorEditor editor = new OperatorEditor();
        editor.initiate("[C:1]O>>[C:1]=O");
        editor.setVisible(true);
    }
}
