package act.render;

import act.shared.Chemical;

import com.ggasoftware.indigo.Indigo;
import com.ggasoftware.indigo.IndigoObject;
import com.ggasoftware.indigo.IndigoRenderer;

public class RenderChemical {
	
	public static void renderToFile(String filename, String smiles) {
		Indigo indigo = new Indigo();	
		IndigoObject molecule = indigo.loadMolecule(smiles);
		IndigoRenderer renderer = new IndigoRenderer(indigo);
		indigo.setOption("render-output-format", "png");
		indigo.setOption("render-image-size", 200,200);
		renderer.renderToFile(molecule, filename);
	}
	
	public static String getSvg(Chemical chemical) {
		byte[] svg = null;
		Indigo indigo = new Indigo();
		IndigoRenderer renderer = new IndigoRenderer(indigo);
		try {
			indigo.setOption("render-output-format", "svg");
			indigo.setOption("render-image-size", 200,200);
			svg = renderer.renderToBuffer(indigo.loadMolecule(chemical.getSmiles()));
			return new String(svg, 0, svg.length);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
		
	}
}
