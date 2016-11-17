package act.installer.reachablesexplorer;

import chemaxon.struc.Molecule;
import com.act.analysis.chemicals.molecules.MoleculeImporter;
import com.act.biointerpretation.mechanisminspection.ReactionRenderer;
import com.act.jobs.FileChecker;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


public class MoleculeRenderer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(MoleculeRenderer.class);

  private static final String ASSETS_LOCATION = "/Volumes/data-level1/data/reachables-explorer-rendering-cache";
  private static final String PNG_EXTENSION = ".png";

  private static ReactionRenderer renderer = new ReactionRenderer();

  public static String generateRendering(String inchi) {
    String md5 = DigestUtils.md5Hex(inchi);
    String postfix = new StringBuilder("-").append(md5).append(PNG_EXTENSION).toString();

    String renderingFilename = String.join("", "molecule", postfix);
    File rendering = Paths.get(ASSETS_LOCATION, renderingFilename).toFile();

    if (!Files.exists(rendering.toPath())) {
      try {
        Molecule mol = MoleculeImporter.importMolecule(inchi);
        renderer.drawMolecule(mol, rendering);
        FileChecker.verifyInputFile(rendering);
      } catch (IOException e) {
        LOGGER.error("Unable to generate rendering for %s at location %s", inchi, rendering.toPath().toString());
        return null;
      }
    }

    return renderingFilename;
  }
}
