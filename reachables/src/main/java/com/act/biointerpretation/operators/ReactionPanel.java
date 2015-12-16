package com.act.biointerpretation.operators;

import com.act.biointerpretation.cofactors.MolViewer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Created by jca20n on 12/1/15.
 */
public class ReactionPanel extends JPanel {

    private JTextField smartsField;

    public ReactionPanel(BufferedImage bf, String rxnSmiles) {
        initComponents(bf, rxnSmiles);
    }

    private void initComponents(BufferedImage bf, String rxnSmiles) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(900, 200));

        MolViewer viewer = new MolViewer(bf);
        add(viewer, BorderLayout.CENTER);

//        smartsField = new JTextField(rxnSmiles);
//        add(smartsField, BorderLayout.SOUTH);
    }
}
