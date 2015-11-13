package com.act.analysis;


import chemaxon.calculations.clean.Cleaner;
import chemaxon.formats.MolExporter;
import chemaxon.formats.MolImporter;
import chemaxon.license.LicenseManager;
import chemaxon.marvin.calculations.LogPMethod;
import chemaxon.marvin.calculations.logPPlugin;
import chemaxon.marvin.space.BoundingBox;
import chemaxon.marvin.space.MSpaceEasy;
import chemaxon.marvin.space.MolecularSurfaceComponent;
import chemaxon.marvin.space.MoleculeComponent;
import chemaxon.marvin.space.SurfaceColoring;
import chemaxon.marvin.space.SurfaceComponent;
import chemaxon.struc.DPoint3;
import chemaxon.struc.MolAtom;
import chemaxon.struc.MolBond;
import chemaxon.struc.Molecule;
import com.dreizak.miniball.highdim.Miniball;
import com.dreizak.miniball.model.ArrayPointSet;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class LogPAnalysis {
  public static void main(String[] args) throws Exception {
    LicenseManager.setLicenseFile(args[0]);
    System.out.format("plugin list: %s\n", StringUtils.join(LicenseManager.getPluginList(), ", "));
    Molecule mol = MolImporter.importMol(args[1]);
    System.out.format("Output: %s\n", MolExporter.exportToFormat(mol, "smiles"));
    System.out.format("LogPPlugin class key: %s\n", logPPlugin.PLUGIN_CLASS_KEY);
    logPPlugin plugin = new logPPlugin();
    plugin.setlogPMethod(LogPMethod.CONSENSUS);

    //plugin.setCloridIonConcentration(0.2);
    //plugin.setNaKIonConcentration(0.2);
    plugin.setUserTypes("logPTrue,logPMicro,logPNonionic");

    System.out.format("Is licensed: %s\n", LicenseManager.isLicensed(plugin.getProductName()));

    plugin.standardize(mol);
    plugin.setMolecule(mol);
    plugin.run();
    System.out.format("True logp: %f\n", plugin.getlogPTrue());
    System.out.format("Micro logp: %f\n", plugin.getlogPMicro());
    Molecule mol2 = plugin.getResultMolecule();
    Cleaner.clean(mol2, 3);
    /*
    for (int i = 0; i < molAtoms.length; i++) {
      MolAtom molAtom = molAtoms[i];
      DPoint3 coords = molAtom.getLocation();
      System.out.format("%d: %s %s %f (%f, %f, %f)\n", i, molAtom.getSymbol(), molAtom.getExtraLabel(),
          plugin.getAtomlogPIncrement(i), coords.x, coords.y, coords.z);
      logPVals.add(plugin.getAtomlogPIncrement(i));
    }
    */
    ArrayList<Double> logPVals = new ArrayList<>();
    ArrayList<Double> hValues = new ArrayList<>();
    ArrayList<Integer> ids = new ArrayList<>();
    for (int i = 0; i < mol.getAtomCount(); i++) {
      ids.add(i);
      Double logP = plugin.getAtomlogPIncrement(i);
      logPVals.add(logP);
      MolAtom molAtom = mol2.getAtom(i);
      DPoint3 coords = molAtom.getLocation();
      System.out.format("%d: %s %s %f (%f, %f, %f)\n", i, molAtom.getSymbol(), molAtom.getExtraLabel(),
          plugin.getAtomlogPIncrement(i), coords.x, coords.y, coords.z);
      for (int j = 0; j < molAtom.getImplicitHcount(); j++) {
        hValues.add(logP);
      }

      MolBond[] bonds = molAtom.getBondArray();
      for (int j = 0; j < bonds.length; j++) {
        MolBond bond = bonds[j];
        System.out.format("  %d -> %d %s %s %s\n", bond.getAtom1().getAtomMap(), bond.getAtom2().getAtomMap(), bond.getBondType(),
            molAtom.equals(bond.getAtom1()), molAtom.equals(bond.getAtom2()));
      }

    }
    //for (int i = 0; i < mol.getImplicitHcount(); i++) {
//      logPVals.add(0d);
  //  }

    ArrayPointSet aps = new ArrayPointSet(3, mol2.getAtomCount());

    Double maxDist = 0.0d;
    Integer di1 = null, di2 = null; // Endpoint atoms of the diameter of the structure.
    for (int i = 0; i < mol2.getAtomCount(); i++) {
      for (int j = 0; j < mol2.getAtomCount(); j++) {
        if (i == j) {
          continue;
        }
        if (Double.isNaN(plugin.getAtomlogPIncrement(i))) {
          continue;
        }
        if (Double.isNaN(plugin.getAtomlogPIncrement(j))) {
          continue;
        }

        MolAtom m1 = mol2.getAtom(i);
        MolAtom m2 = mol2.getAtom(j);

        DPoint3 c1 = m1.getLocation();
        DPoint3 c2 = m2.getLocation();

        Double dist = c1.distance(c2);
        Double angle = c1.angle3D(c2);

        if (dist > maxDist) {
          maxDist = dist;
          di1 = i;
          di2 = j;
        }

        System.out.format("%d -> %d: %s %s, %f %f, %f %f\n", i, j, m1.getSymbol(), m2.getSymbol(), dist, angle,
            plugin.getAtomlogPIncrement(i), plugin.getAtomlogPIncrement(j));
      }
      if (!Double.isNaN(plugin.getAtomlogPIncrement(i))) {
        DPoint3 c1 = mol2.getAtom(i).getLocation();
        aps.set(i, 0, c1.x);
        aps.set(i, 1, c1.y);
        aps.set(i, 2, c1.z);
      } else {
        System.err.format("Atom %d has a logP increment of NaN\n", i);
      }
    }

    DPoint3 newOrigin = mol2.getAtom(di1).getLocation();
    List<DPoint3> coords = new ArrayList<>();
    for (int i = 0; i < mol2.getAtomCount(); i++) {
      DPoint3 c = mol2.getAtom(i).getLocation();
      c.subtract(newOrigin);
      coords.add(c);
    }

    System.out.format("Diameter (%d -> %d) length is %f\n", di1, di2, Math.sqrt(coords.get(di2).lengthSquare()));

    for (int i = 0; i < mol2.getAtomCount(); i++) {
      DPoint3 origin = coords.get(di1);
      DPoint3 diameter = coords.get(di2);
      DPoint3 exp = coords.get(i);

      Double dotProduct = diameter.x * exp.x + diameter.y * exp.y + diameter.z * exp.z;
      Double lengthProduct = Math.sqrt(diameter.lengthSquare()) * Math.sqrt(exp.lengthSquare());
      Double cosine = dotProduct / lengthProduct;
      Double sine = Math.sqrt(1 - cosine * cosine);
      Double dist = sine * Math.sqrt(exp.lengthSquare());

      Double angle = Math.acos(cosine) * 180.0d / (Math.PI) ;

      System.out.format(
          "Dist %d: (%f, %f, %f) -> (%f, %f, %f); point (%f, %f, %f) has dist %f, angle %f, cos %f, sin %f (%f/%f), dist %f\n",
          i, origin.x, origin.y, origin.z, diameter.x, diameter.y, diameter.z, exp.x, exp.y, exp.z, Math.sqrt(exp.lengthSquare()),
          angle, cosine, sine, dotProduct, lengthProduct, dist);
    }

    Miniball mb = new Miniball(aps);
    double[] c = mb.center();
    System.out.format("Minimum bounding ball: %f %s\n", mb.radius(), mb);
    for (int i = 0; i < c.length; i++) {
      System.out.format("  %d %f\n", i, c[i]);
    }
    DPoint3 center = new DPoint3(c[0], c[1], c[2]);

    for (int i = 0; i < mol2.getAtomCount(); i++) {
      MolAtom m = mol2.getAtom(i);
      DPoint3 p = m.getLocation();
      System.out.format("%d: %f %f %f\n", i, center.distance(p), center.angle3D(p),  center.distance(p) - mb.radius());

    }

    MSpaceEasy mspace = new MSpaceEasy(1, 2, true);

    JFrame jframe = new JFrame();
    jframe.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

    mspace.addCanvas(jframe.getContentPane());
    mspace.setSize(1200, 600);

    MoleculeComponent mc1 = mspace.addMoleculeTo(mol2, 0);
          mspace.getEventHandler().createAtomLabels(mc1, ids);

    //msc.setDrawProperty("MacroMolecule.Hydrogens", "false");
    mspace.setProperty("MacroMolecule.Hydrogens", "false");
    MoleculeComponent mc2 = mspace.addMoleculeTo(mol2, 1);
    MolecularSurfaceComponent msc = mspace.computeSurface(mc2);
    SurfaceComponent sc = msc.getSurface();
    System.out.format("ColorF length: %d\n", sc.getColorF().length);
    System.out.format("Vertex count: %d\n", sc.getVertexCount());

    msc.setPalette(SurfaceColoring.COLOR_MAPPER_BLUE_TO_RED);
    //mspace.getEventHandler().createAtomLabels(mc2, logPVals);
    msc.showVolume(true);
    msc.setSurfacePrecision("High");
    msc.setSurfaceType("van der Waals");
    msc.setDrawProperty("Surface.DrawType", "Dot");
    msc.setDrawProperty("Surface.Quality", "High");
    logPVals.addAll(hValues);
    msc.setAtomPropertyList(logPVals);
    msc.setDrawProperty("Surface.ColorType", "AtomProperty");

    jframe.pack();
    jframe.setVisible(true);

    BoundingBox bb = mc2.getBoundingBox();
    System.out.format("Bounding box: ([%f - %f], [%f - %f], [%f - %f])\n",
        bb.getMinX(), bb.getMaxX(), bb.getMinY(), bb.getMaxY(), bb.getMinZ(), bb.getMaxZ());
  }
}
