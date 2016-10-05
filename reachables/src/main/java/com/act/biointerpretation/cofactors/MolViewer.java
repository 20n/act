package com.act.biointerpretation.cofactors;

import chemaxon.formats.MolExporter;
import chemaxon.formats.MolFormatException;
import chemaxon.formats.MolImporter;
import chemaxon.struc.Molecule;
import chemaxon.struc.RxnMolecule;
import com.act.biointerpretation.utils.ChemAxonUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by jca20n on 11/7/15.
 */
public class MolViewer extends JPanel {

    //C10H16N5O13P3
    static String molecule = "" +
    "C(=N)C(=N)C(=N)C(N)C(N)C(=O)C(=O)C(=O)C=COP(=O)(O)OP(=O)(O)OP(=O)(O)O";

    private BufferedImage bf;
    private JPanel resultPanel;
    private JTextArea inputArea;
    private JLabel imageLabel;


    public static boolean show(String molecule) {
        String text = molecule;
        Molecule mol = null;
        if (text.contains(">>")) {
            System.out.println("Is reaction");
            try {
                mol = RxnMolecule.getReaction(MolImporter.importMol(text));
            } catch (MolFormatException e) {
                return false;
            }
        } else {
            System.out.println("Is molecule");
            try {
                mol = MolImporter.importMol(text);
                System.out.println(ChemAxonUtils.SmilesToInchi(molecule));
            } catch (MolFormatException e) {
                return false;
            }
        }

        if(mol==null) {
            return false;
        }

        return show(mol, null);
    }

    public static boolean show(Molecule mol, SimpleReaction srxn) {
        try {
            byte[] bytes = MolExporter.exportToBinFormat(mol, "png:w900,h450,amap");
            InputStream in = new ByteArrayInputStream(bytes);
            BufferedImage bImageFromConvert = ImageIO.read(in);

            MolViewer panel = new MolViewer(bImageFromConvert);

            JFrame frame = new JFrame();

            if(srxn!=null) {

            }

            frame.setPreferredSize(new Dimension(900,450));
            frame.getContentPane().add(panel, BorderLayout.CENTER);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setVisible(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public MolViewer(BufferedImage bf) {
        this.bf = bf;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(bf, 0, 0, null); // see javadoc for more info on the parameters
    }

    public static void main(String[] args) throws Exception {
        show(molecule);
    }

}
