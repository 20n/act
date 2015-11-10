package com.act.biointerpretation.cofactors;

import com.act.biointerpretation.utils.VerticalLayout;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

/**
 * Created by jca20n on 11/8/15.
 */
public class CofactorPanel extends JPanel {
    Set<String> substrateCofactors;
    Set<String> productCofactors;

    public CofactorPanel(Set<String> substrateCofactors, Set<String> productCofactors) {
        this.substrateCofactors = substrateCofactors;
        this.productCofactors = productCofactors;
        initComponents();
    }

    private void initComponents() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        JPanel substratePanel = createListPanel(substrateCofactors, "Substrate Cofactors");
        add(substratePanel);
        JPanel productPanel = createListPanel(productCofactors, "Product Cofactors");
        add(productPanel);
    }

    private JPanel createListPanel(Set<String> cofactors, String label) {
        JPanel out = new JPanel();
        out.setPreferredSize(new Dimension(300, 100));
        out.setLayout(new VerticalLayout());

        JLabel jlabel = new JLabel(label);
        out.add(jlabel);

        for(String cofactor : cofactors) {
            JTextField field = new JTextField(cofactor);
            field.setPreferredSize(new Dimension(300, 25));
            out.add(field);
        }
        return out;
    }
}
