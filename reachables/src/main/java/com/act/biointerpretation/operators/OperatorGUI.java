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
        OperatorGUI gui = new OperatorGUI(null, "/Users/jca20n/act/reachables/output/hmEROs/906e1df4e37dbadb62fb90bb08b77fdd661dcd43/b1da84b711a4c428c453b27770dc9eb7bd61552b/b1da84b711a4c428c453b27770dc9eb7bd61552b/0a0531d62599fe7a98df4868f4ef3ea6aa20a1cc.txt");
    }
}
