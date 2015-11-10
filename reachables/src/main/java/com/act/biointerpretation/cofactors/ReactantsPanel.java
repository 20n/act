package com.act.biointerpretation.cofactors;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by jca20n on 11/8/15.
 */
public class ReactantsPanel extends JPanel {
    SimpleReaction rxn;
    CofactorGUIHelper helper;

    public ReactantsPanel(SimpleReaction rxn, CofactorGUIHelper helper) {
        this.rxn = rxn;
        this.helper = helper;
        initComponents();
    }

    private void initComponents() {
        setLayout(new VerticalLayout());

        JLabel subLabel = new JLabel("Substrates:");
        add(subLabel);
        for(int i=0; i<rxn.substrateInfo.size(); i++) {
            JPanel itemPanel = createItemPanel(rxn.substrateInfo.get(i));
            add(itemPanel);
        }

        JLabel prodLabel = new JLabel("Products:");
        add(prodLabel);
        for(int i=0; i<rxn.productInfo.size(); i++) {
            JPanel itemPanel = createItemPanel(rxn.productInfo.get(i));
            add(itemPanel);
        }
    }

    private JPanel createItemPanel(ChemicalInfo chemicalInfo) {
        int height = 25;

        JPanel out = new JPanel();
        out.setLayout(new FlowLayout(FlowLayout.LEFT));

        //Put in ID
        JTextField idField = new JTextField();
        idField.setText(chemicalInfo.id);
        idField.setPreferredSize(new Dimension(50, height));
        out.add(idField);

        //Put in name
        JTextField nameField = new JTextField();
        nameField.setText(chemicalInfo.name);
        nameField.setPreferredSize(new Dimension(200, height));
        out.add(nameField);

        //Put in inchi
        JTextField inchiField = new JTextField();
        inchiField.setText(chemicalInfo.inchi);
        inchiField.setPreferredSize(new Dimension(500, height));
        out.add(inchiField);

        //Put in smiles
        JTextField smilesField = new JTextField();
        smilesField.setText(chemicalInfo.smiles);
        smilesField.setPreferredSize(new Dimension(200, height));
        out.add(smilesField);

        //Put in 'add' button
        JButton btn = new JButton("add");
        btn.setPreferredSize(new Dimension(50, height));
        btn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String inchi = inchiField.getText();
                String name = nameField.getText();
                helper.addCofactorToList(name, inchi);
            }
        });
        out.add(btn);

        return out;
    }
}
