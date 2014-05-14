package act.render;

import act.server.Molecules.CRO;
import act.server.SQLInterface.MongoDB;
import act.shared.helpers.P;

public class RenderRO {
	static MongoDB mongo = new MongoDB();
	public static void renderRO(int id) {
		P<CRO,Integer> cro = mongo.getCRO(id);
		cro.fst().render("renderro_cro"+id, "");
	}
	
	public static void main(String[] args) {
		if(args.length == 1) {
			renderRO(Integer.parseInt(args[0]));
		}
	}
}
