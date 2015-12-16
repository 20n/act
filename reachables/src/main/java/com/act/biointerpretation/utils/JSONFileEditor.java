package com.act.biointerpretation.utils;

import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * Created by jca20n on 12/15/15.
 */
public class JSONFileEditor extends JFrame {
    File file;
    JEditorPane jsArea;
    JLabel validation;

    public JSONFileEditor(File file, String initialData) {
        this.file = file;
        initComponents(initialData);
    }

    private void initComponents(String initialData) {
        //Put in the editor
        jsArea = new JEditorPane();
        JScrollPane scrPane = new JScrollPane(jsArea);
        scrPane.setMinimumSize(new Dimension(500, 400));
        scrPane.setPreferredSize(new Dimension(500, 400));
        scrPane.setMaximumSize(new Dimension(1600, 1600));
        scrPane.setBorder(BorderFactory.createLineBorder(Color.white, 3));
        jsArea.setContentType("text/javascript");
        jsArea.setFont(Font.getFont("Arial"));
        getContentPane().add(scrPane, BorderLayout.CENTER);
        jsArea.setText(initialData);

        //Put in some buttons
        JPanel controls = new JPanel();
        getContentPane().add(controls, BorderLayout.SOUTH);

        JButton valBtn = new JButton("validate");
        valBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String data = jsArea.getText();
                try {
                    JSONObject json = new JSONObject(data);
                    if(json==null) {
                        throw new Exception();
                    }
                    System.out.println(json.toString());
                    System.out.println("VALID");
                    validation.setText("valid");
                } catch(Exception err) {
                    System.out.println("!!!!!  JSON invalid !!!!");
                    validation.setText("!!! INVALID !!!");
                }
                repaint();
            }
        });
        controls.add(valBtn);

        JButton saveBtn = new JButton("save");
        saveBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String data = jsArea.getText();
                FileUtils.writeFile(data, file.getAbsolutePath());
            }
        });
        controls.add(saveBtn);

        validation = new JLabel("?");
        controls.add(validation);

        pack();
    }

    public static void main(String[] args) {
        File afile = new File("/Users/jca20n/Downloads/trashy.json");
        String data = "{\n\t\"name\" : \"\",\n\t\"validation\" : TRUE,\n\t\"confidence\" : \"high\"\n}";
        new JSONFileEditor(afile, data).setVisible(true);
    }
}
