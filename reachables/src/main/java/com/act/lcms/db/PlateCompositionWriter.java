package com.act.lcms.db;

import com.act.lcms.db.model.DeliveredStrainWell;
import com.act.lcms.db.model.InductionWell;
import com.act.lcms.db.model.LCMSWell;
import com.act.lcms.db.model.Plate;
import com.act.lcms.db.model.PregrowthWell;
import com.act.lcms.db.model.StandardWell;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

public class PlateCompositionWriter {
  public static final String PLATE_TYPE_96_WELLS = "plate_96_well";
  public static final String PLATE_TYPE_96_WELLS_BLOCK = "block_96_well";
  public static final String PLATE_TYPE_24_WELLS = "plate_24_well";
  public static final String PLATE_TYPE_24_WELLS_BLOCK = "block_24_well";

  public static final Integer PLATE_DIM_96_WELL_X = 12;
  public static final Integer PLATE_DIM_96_WELL_Y = 8;
  public static final Integer PLATE_DIM_24_WELL_X = 6;
  public static final Integer PLATE_DIM_24_WELL_Y = 4;

  public static final List<String> PLATE_LABELS_SAMPLE_WELLS = Arrays.asList("msid", "composition", "chemical", "note");
  public static final List<String> PLATE_LABELS_STANDARD_WELLS = Arrays.asList("chemical", "media");
  public static final List<String> PLATE_LABELS_INDUCTION_WELLS =
      Arrays.asList("msid", "chemical_source", "composition", "chemical", "strain_source", "note", "growth");
  public static final List<String> PLATE_LABELS_PREGROWTH_WELLS =
      Arrays.asList("source_plate", "msid", "composition", "source_well", "note", "growth");


  public void writeLCMSWellComposition(PrintWriter w, Plate plate, List<LCMSWell> wells) throws IOException {
    switch (plate.getPlateType()) {
      case PLATE_TYPE_96_WELLS:
      case PLATE_TYPE_96_WELLS_BLOCK:
        String[][][] tables = new String[4][PLATE_DIM_96_WELL_Y][PLATE_DIM_96_WELL_X];
        for (LCMSWell well : wells) {
          int y = well.getPlateRow();
          int x = well.getPlateColumn();
          // TODO: should we use a map for this instead to make the table name -> data correspondence clearer?
          tables[0][y][x] = well.getMsid();
          tables[1][y][x] = well.getComposition();
          tables[2][y][x] = well.getChemical();
          tables[3][y][x] = well.getNote();
        }

        for (int i = 0; i < PLATE_LABELS_SAMPLE_WELLS.size(); i++) {
          write96CellTable(w, PLATE_LABELS_SAMPLE_WELLS.get(i), tables[i]);
        }
        break;
      default:
        // TODO: use a better exception type here.
        throw new RuntimeException(String.format("Found unexpected plate type: %s", plate.getPlateType()));
    }
  }

  public void writeStandardWellComposition(PrintWriter w, Plate plate, List<StandardWell> wells)
      throws IOException {
    switch (plate.getPlateType()) {
      case PLATE_TYPE_96_WELLS:
      case PLATE_TYPE_96_WELLS_BLOCK:
        String[][][] tables = new String[2][PLATE_DIM_96_WELL_Y][PLATE_DIM_96_WELL_X];
        for (StandardWell well : wells) {
          int y = well.getPlateRow();
          int x = well.getPlateColumn();
          // TODO: should we use a map for this instead to make the table name -> data correspondence clearer?
          tables[0][y][x] = well.getChemical();
          tables[1][y][x] = well.getMedia();
          // TODO: add notes when we expect them.
        }

        for (int i = 0; i < PLATE_LABELS_STANDARD_WELLS.size(); i++) {
          write96CellTable(w, PLATE_LABELS_STANDARD_WELLS.get(i), tables[i]);
        }
        break;
      default:
        // TODO: use a better exception type here.
        throw new RuntimeException(String.format("Found unexpected plate type: %s", plate.getPlateType()));
    }
  }

  public void writeDeliveredStrainWellComposition(PrintWriter w, Plate plate, List<DeliveredStrainWell> wells)
      throws IOException {
    switch (plate.getPlateType()) {
      case PLATE_TYPE_96_WELLS:
      case PLATE_TYPE_96_WELLS_BLOCK:
        // Strain files don't use normal plate tables.
        w.println(">>wells\tmsid\tcomposition");
        for (DeliveredStrainWell well : wells) {
          w.println(StringUtils.join(new String[] {
              well.getWell(), well.getMsid(), well.getComposition()
          }, "\t"));
        }
        w.println();
        break;
      default:
        // TODO: use a better exception type here.
        throw new RuntimeException(String.format("Found unexpected plate type: %s", plate.getPlateType()));
    }
  }

  public void writeInductionWellComposition(PrintWriter w, Plate plate, List<InductionWell> wells)
      throws IOException {
    switch (plate.getPlateType()) {
      case PLATE_TYPE_24_WELLS:
      case PLATE_TYPE_24_WELLS_BLOCK:
        String[][][] tables = new String[7][PLATE_DIM_24_WELL_Y][PLATE_DIM_24_WELL_X];
        for (InductionWell well : wells) {
          int y = well.getPlateRow();
          int x = well.getPlateColumn();
          tables[0][y][x] = well.getMsid();
          tables[1][y][x] = well.getChemicalSource();
          tables[2][y][x] = well.getComposition();
          tables[3][y][x] = well.getChemical();
          tables[4][y][x] = well.getStrainSource();
          tables[5][y][x] = well.getNote();
          tables[6][y][x] = well.getGrowth() == null ? "" : well.getGrowth().toString();
        }

        for (int i = 0; i < PLATE_LABELS_INDUCTION_WELLS.size(); i++) {
          write24CellTable(w, PLATE_LABELS_INDUCTION_WELLS.get(i), tables[i]);
        }
        break;
      default:
        // TODO: use a better exception type here.
        throw new RuntimeException(String.format("Found unexpected plate type: %s", plate.getPlateType()));
    }
  }

  public void writePregrowthWellComposition(PrintWriter w, Plate plate, List<PregrowthWell> wells)
      throws IOException {
    switch (plate.getPlateType()) {
      case PLATE_TYPE_24_WELLS:
      case PLATE_TYPE_24_WELLS_BLOCK:
        String[][][] tables = new String[6][PLATE_DIM_24_WELL_Y][PLATE_DIM_24_WELL_X];
        for (PregrowthWell well : wells) {
          int y = well.getPlateRow();
          int x = well.getPlateColumn();
          tables[0][y][x] = well.getSourcePlate();
          tables[1][y][x] = well.getMsid();
          tables[2][y][x] = well.getComposition();
          tables[3][y][x] = well.getSourceWell();
          tables[4][y][x] = well.getNote();
          tables[5][y][x] = well.getGrowth() == null ? "" : well.getGrowth().toString();
        }

        for (int i = 0; i < PLATE_LABELS_PREGROWTH_WELLS.size(); i++) {
          write24CellTable(w, PLATE_LABELS_PREGROWTH_WELLS.get(i), tables[i]);
        }
        break;
      default:
        // TODO: use a better exception type here.
        throw new RuntimeException(String.format("Found unexpected plate type: %s", plate.getPlateType()));
    }
  }

  protected void writePlateAttributes(PrintWriter w, Plate plate) throws IOException {
    w.format(">%s\t%s\n", "name", plate.getName());
    w.format(">%s\t%s\n", "description", plate.getDescription());
    w.format(">%s\t%s\n", "barcode", plate.getBarcode());
    w.println(">schema\tplate\n");
    w.format(">%s\t%s\n", "location", plate.getLocation());
    w.format(">%s\t%s\n", "plate_type", plate.getPlateType());
    if (plate.getSolvent() != null) {
      w.format(">%s\t%s\n", "solvent", plate.getSolvent());
    }
    w.format(">%s\t%d\n", "temperature", plate.getTemperature());
    w.println();
  }

  protected void write96CellTable(PrintWriter w, String tableName, String[][] values) {
    writeCellTable(w, tableName, values, PLATE_DIM_96_WELL_X, PLATE_DIM_96_WELL_Y);
  }

  protected void write24CellTable(PrintWriter w, String tableName, String[][] values) {
    writeCellTable(w, tableName, values, PLATE_DIM_24_WELL_X, PLATE_DIM_24_WELL_Y);
  }

  protected void writeCellTable(PrintWriter w, String tableName, String[][] values,
                                int expectedXDim, int expectedYDim) {
    StringBuilder headerBuilder = new StringBuilder(String.format(">>%s", tableName));
    for (int i = 0; i < expectedXDim; i++) {
      // Columns are one-indexed in composition tables.
      headerBuilder.append(String.format("\t%d", i + 1));
    }
    w.println(headerBuilder.toString());

    if (values.length != expectedYDim) {
      throw new RuntimeException(String.format(
          "ERROR: found incorrect row dimension for 96 well plate.  Expected %d, but got %d.\n",
          expectedYDim, values.length));
    }

    char rowName = 'A';
    for (int i = 0; i < values.length; i++) {
      if (values[i].length != expectedXDim) {
        throw new RuntimeException(String.format(
            "ERROR: found incorrect column dimension for 96 well plate.  Expected %d, but got %d.\n",
            expectedXDim, values.length));
      }
      w.format("%c\t%s\n", rowName, StringUtils.join(values[i], '\t'));
      rowName++;
    }
    w.println();
  }

  public void writePlateCompositionByBarcode(DB db, String plateBarcode, Writer dest) throws Exception {
    Plate p = Plate.getPlateByBarcode(db, plateBarcode);
    writePlateComposition(db, p, dest);
  }

  public void writePlateCompositionByName(DB db, String name, Writer dest) throws Exception {
    Plate p = Plate.getPlateByName(db, name);
    writePlateComposition(db, p, dest);
  }

  public void writePlateComposition(DB db, Plate p, Writer dest) throws Exception {
    PlateCompositionWriter writer = new PlateCompositionWriter();
    PrintWriter w = new PrintWriter(dest, true);
    writePlateAttributes(w, p);

    switch (p.getContentType()) {
      case LCMS:
        List<LCMSWell> LCMSWells = LCMSWell.getInstance().getByPlateId(db, p.getId());
        writer.writeLCMSWellComposition(w, p, LCMSWells);
        break;
      case STANDARD:
        List<StandardWell> stdWells = StandardWell.getInstance().getByPlateId(db, p.getId());
        writer.writeStandardWellComposition(w, p, stdWells);
        break;
      case DELIVERED_STRAIN:
        List<DeliveredStrainWell> strainWells = DeliveredStrainWell.getInstance().getByPlateId(db, p.getId());
        writer.writeDeliveredStrainWellComposition(w, p, strainWells);
        break;
      case INDUCTION:
        List<InductionWell> inductionWells = InductionWell.getInstance().getByPlateId(db, p.getId());
        writer.writeInductionWellComposition(w, p, inductionWells);
        break;
      case PREGROWTH:
        List<PregrowthWell> pregrowthWells = PregrowthWell.getInstance().getByPlateId(db, p.getId());
        writer.writePregrowthWellComposition(w, p, pregrowthWells);
        break;
      default:
        throw new RuntimeException(String.format("Unrecognized table type: %s", p.getContentType()));
    }
  }
}
