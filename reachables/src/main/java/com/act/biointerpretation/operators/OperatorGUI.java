package com.act.biointerpretation.operators;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.cofactors.CofactorPanel;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileDrop;
import com.act.biointerpretation.utils.FileUtils;
import com.act.biointerpretation.utils.VerticalLayout;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

/**
 * Created by jca20n on 12/1/15.
 */
public class OperatorGUI extends JFrame {
    private OperatorGUIhelper helper;
    private String rxnPath;

    private ReactionInterpretation rxn;

    public OperatorGUI(OperatorGUIhelper helper, String rxnPath) {
        this.helper = helper;
        this.rxnPath = rxnPath;

        try {
            initData();
            initComponents();
            setVisible(true);
        } catch(Exception err) {
            err.printStackTrace();
        }
    }

    private void initData() {
        String data = FileUtils.readFile(rxnPath);
        rxn = ReactionInterpretation.parse(data);
    }

    private void initComponents() throws Exception {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setLayout(new VerticalLayout());

        //Add file drop
        new FileDrop( getContentPane(), new FileDrop.Listener()
        {   public void filesDropped( java.io.File[] files) {
                File afile = files[0];
                new OperatorGUI(helper, afile.getAbsolutePath());
                setVisible(false);
                dispose();
            }
        });

        //Add the control panel
        JPanel controls = new JPanel();
        JLabel label = new JLabel(Integer.toString(rxn.rxnId));
        controls.add(label);
        getContentPane().add(controls);

        //Add next reaction btn
        JButton nextBtn = new JButton("nextRxn");
        nextBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                helper.goNextRxn();
                setVisible(false);
                dispose();
            }
        });
        controls.add(nextBtn);

        //Add next ERO btn
        JButton nextEROBtn = new JButton("nextERO");
        nextEROBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                helper.goNextERO();
                setVisible(false);
                dispose();
            }
        });
        controls.add(nextEROBtn);


        //Add next JSON btn
        JButton jsonBtn = new JButton("addJSON");
        jsonBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                helper.launchJSONEditor();
            }
        });
        controls.add(jsonBtn);

        //Add open folder btn
        JButton openBtn = new JButton("open");
        openBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                helper.openFolder();
            }
        });
        controls.add(openBtn);

        //Add the cofactors
        CofactorPanel cofactorPanel = new CofactorPanel(rxn.subCofactors,rxn.prodCofactors);
        getContentPane().add(cofactorPanel);

        //Add the three panels
        getContentPane().add(createPanel(rxn.mapping));
        getContentPane().add(createPanel(rxn.hcERO));
        getContentPane().add(createPanel(rxn.hmERO));


        validate();
        pack();
    }

    private JPanel createPanel(String rxnSmilesOrSmarts) throws Exception {
        RxnMolecule rxnMolecule = RxnMolecule.getReaction(MolImporter.importMol(rxnSmilesOrSmarts));

        //Create the buffered image
        byte[] bytes = MolExporter.exportToBinFormat(rxnMolecule, "png:w900,h200,amap");
        InputStream in = new ByteArrayInputStream(bytes);
        BufferedImage bImageFromConvert = ImageIO.read(in);
        ReactionPanel panel = new ReactionPanel(bImageFromConvert, rxnSmilesOrSmarts);

        return panel;
    }

    public static void main(String[] args) {
        ChemAxonUtils.license();
        OperatorGUI gui = new OperatorGUI(null, "/Users/jca20n/act/reachables/output/hmEROs/9e8b3e224ff7d9634c8b666e3f4410abe446b200/db492451b640d42c1d1ea8b8686db3695e0f2f82/a3987bab6709cf737a01635963d1b9c345cd1347/de68ecaa69d5fbcc10b3daf08a4a58a922778852.txt");
    }
}
