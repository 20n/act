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
import java.nio.file.Paths;
import java.util.Optional;


public class MoleculeRenderer {

  private static final Logger LOGGER = LogManager.getFormatterLogger(MoleculeRenderer.class);
  private static final String PNG_EXTENSION = ".png";

  private static ReactionRenderer renderer = new ReactionRenderer();
  private File assetLocation;

  public MoleculeRenderer(File assetLocation) {
    if (assetLocation.exists()) {
      this.assetLocation = assetLocation;
    } else {
      String msg = String.format("Could not find or create directory at %s for storing assets.", assetLocation.getAbsolutePath());
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
  }

  public File getRenderingFile(String inchi) {
    String md5 = DigestUtils.md5Hex(inchi);
    String postfix = String.format("-%s%s", md5, PNG_EXTENSION);

    String renderingFilename = String.join("", "molecule", postfix);
    return Paths.get(this.assetLocation.getPath(), renderingFilename).toFile();
  }

  public Optional<File> generateRendering(String inchi) {

    File rendering = getRenderingFile(inchi);

    if (!rendering.exists()) {
      try {
        Molecule mol = MoleculeImporter.importMolecule(inchi);
        renderer.drawMolecule(mol, rendering);
        FileChecker.verifyInputFile(rendering);
      } catch (IOException e) {
        LOGGER.error("Unable to generate rendering for %s at location %s", inchi, rendering.toPath().toString());
        return Optional.empty();
      } catch (StackOverflowError e) {
        // Very rarely, a very large molecule will trigger a StackOverFlowError. Catch it and move on!
        LOGGER.error("Unable to generate rendering for %s at location %s", inchi, rendering.toPath().toString());
        return Optional.empty();
      }
    }

    return Optional.of(rendering);
  }
}
