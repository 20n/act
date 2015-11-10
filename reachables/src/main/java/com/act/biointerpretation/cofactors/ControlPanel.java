package com.act.biointerpretation.cofactors;

import com.act.biointerpretation.utils.VerticalLayout;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by jca20n on 11/8/15.
 */
public class ControlPanel extends JPanel {
    private CofactorGUIHelper helper;
    private JFrame window;

    public ControlPanel(CofactorGUIHelper helper, JFrame window) {
        this.helper = helper;
        this.window = window;
        initComponents();
    }

    private void initComponents() {
        setLayout(new VerticalLayout());

        JButton nextBtn = new JButton("next");
        nextBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                helper.goNext();
                window.setVisible(false);
                window.dispose();
            }
        });
        add(nextBtn);

        JButton refreshBtn = new JButton("refresh");
        refreshBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                helper.redraw();
                window.setVisible(false);
                window.dispose();
            }
        });
        add(refreshBtn);

        JButton ignoreBtn = new JButton("ignore");
        ignoreBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                helper.ignore();
            }
        });
        add(ignoreBtn);
    }
}
