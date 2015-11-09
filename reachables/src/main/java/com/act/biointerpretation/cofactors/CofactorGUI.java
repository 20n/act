package com.act.biointerpretation.cofactors;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Created by jca20n on 11/8/15.
 */
public class CofactorGUI extends JFrame {
    private CofactorGUIHelper helper;

    public CofactorGUI(CofactorGUIHelper helper) {
        this.helper = helper;
        initComponents();
    }

    private void initComponents() {
        getContentPane().setLayout(new BorderLayout());
        setPreferredSize(new Dimension(1100, 800));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Put in the ReactantsPanel with the chemical information
        ReactantsPanel rpanel = new ReactantsPanel(helper.getSimpleReaction(), helper);
        getContentPane().add(rpanel, BorderLayout.NORTH);

        //Put in the image of the reaction
        BufferedImage bf = helper.getReactionImage();
        MolViewer viewer = new MolViewer(bf);
        getContentPane().add(viewer, BorderLayout.CENTER);

        //Put in the cofactors
        CofactorPanel cpanel = new CofactorPanel(helper.getSimpleReaction().subCofactors, helper.getSimpleReaction().prodCofactors);
        getContentPane().add(cpanel, BorderLayout.SOUTH);

        //Put inthe control panel
        ControlPanel controls = new ControlPanel(helper, this);
        getContentPane().add(controls, BorderLayout.EAST);

        pack();
    }

}
