package com.act.biointerpretation.step3_mechanisminspection;

import act.api.NoSQLAPI;
import act.shared.Reaction;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.cofactors.MolViewer;

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

    private int currrxn = 0;

    public OperatorEditor(String operator) {
        initComponents();
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
                    e1.printStackTrace();
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
                    e1.printStackTrace();
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
        if(api==null) {
            this.api = new NoSQLAPI("synapse", "synapse");
        }
        for(long i=0; i<99999999; i++) {
            Reaction rxn = api.readReactionFromInKnowledgeGraph(i);
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
        OperatorEditor editor = new OperatorEditor("[C:1]O>>[C:1]N");
        editor.setVisible(true);
    }
}
