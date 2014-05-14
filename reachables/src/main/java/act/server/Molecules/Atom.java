package act.server.Molecules;

import act.shared.SMARTSCanonicalizationException;

enum Element { 
	Unknown, // 0
	H,  // 1
	He, // 2
	Li, // 3
	Be, // 4
	B,  // 5
	C,  // 6
	N,  // 7
	O,  // 8
	F,  // 9
	Ne, // 10
	Na, // 11
	Mg, // 12
	Al, // 13
	Si, // 14
	P,  // 15
	S,  // 16
	Cl, // 17
	Ar, // 18
	K,  // 19
	Ca, // 20
	Sc, // 21
	Ti, // 22
	V,  // 23
	Cr, // 24
	Mn, // 25
	Fe, // 26
	Co, // 27
	Ni, // 28
	Cu, // 29
	Zn, // 30
	Ga, // 31
	Ge, // 32
	As, // 33
	Se, // 34
	Br, // 35
	Kr, // 36
	Rb, // 37
	Sr, // 38
	Y,  // 39
	Zr, // 40
	Nb, // 41
	Mo, // 42
	Tc, // 43
	Ru, // 44
	Rh, // 45
	Pd, // 46
	Ag, // 47
	Cd, // 48
	In, // 49
	Sn, // 50
	Sb, // 51
	Te, // 52
	I,  // 53
	Xe, // 54
	Cs, // 55
	Ba, // 56
	La, // 57
	Ce, // 58
	Pr, // 59
	Nd, // 60
	Pm, // 61
	Sm, // 62
	Eu, // 63
	Gd, // 64
	Tb, // 65
	Dy, // 66
	Ho, // 67
	Er, // 68
	Tm, // 69
	Yb, // 70
	Lu, // 71
	Hf, // 72
	Ta, // 73
	W,  // 74
	Re, // 75
	Os, // 76
	Ir, // 77
	Pt, // 78
	Au, // 79
	Hg, // 80
	Tl, // 81
	Pb, // 82
	Bi, // 83
	Po, // 84
	At, // 85
	Rn, // 86
	Fr, // 87
	Ra, // 88
	
	Ac, // 89 == used for denoting the dot on atoms

	// The following are used for canonicalization, the testing based approach that 
	// substitutes heavy atoms and checks if the canonicalization on the other end is correct
	Th, Pa, U, Np, Pu, Am, Cm, Bk, Cf, Es, // 10 elements with atomic numbers 90 - 99
	Fm, Md, No, Lr, Rf, Db, Sg, Bh, Hs, Mt, // 10 elements with atomic numbers 100 - 109
	Ds, Rg, Cn, Uut, Fl, Uup, Lv, Uus,  // 8 elements with atomic numbers 110 - 117
	;

	public int atomicNumber() {
		return this.ordinal(); // numbering starts from 0, and then we have the elements ordered in this enum by atomic number
	}
	public int valueBasedHashCode() {
		return this.name().hashCode();
	}
	public static int expectedAtomicNumsAreUnder = 89; // just so that we can use atomic numbers over this for identifying unknowns
	public static Element getMaxExtraHeavyAtom() {
		return Element.Ac;
	}
	public static Element getExtraHeavyAtoms(int atomicNum) throws SMARTSCanonicalizationException {
		if (atomicNum < 90 || atomicNum > 117) {
			System.err.println("MolGraph.java:NodeType: For star replacement, very heavy #" + atomicNum
					+ "\n Will lead to: " + (atomicNum - Element.expectedAtomicNumsAreUnder) + "!");
			throw new SMARTSCanonicalizationException("Too many stars "+ atomicNum +" being replaced with heavy atoms.");
		}
		switch (atomicNum) {
		case 90: return Th; case 91: return Pa; case 92: return U; 
		case 93: return Np; case 94: return Pu; case 95: return Am; 
		case 96: return Cm; case 97: return Bk; case 98: return Cf; 
		case 99: return Es;
		
		case 100: return Fm; case 101: return Md; case 102: return No; 
		case 103: return Lr; case 104: return Rf; case 105: return Db; 
		case 106: return Sg; case 107: return Bh; case 108: return Hs; 
		case 109: return Mt; 

		case 110: return Ds; case 111: return Rg; case 112: return Cn; 
		case 113: return Uut; case 114: return Fl; case 115: return Uup; 
		case 116: return Lv; case 117: return Uus; 
		case 118: return null; 
		case 119: return null; 
		}
		return Unknown;
	}
};
enum BondType { 
	Unknown, Single, Double, Triple;
	
	public int valueBasedHashCode() {
		return this.name().hashCode();
	}
	@Override
	public String toString() {
		// somewhat strangely, enums in java are just normal classes and so can have methods; overrides etc.
		String bnd;
		switch (this)
		{
		case Single: bnd = "-"; break;
		case Double: bnd = "="; break;
		case Triple: bnd = "#"; break;
		// case EdgeType.Aromatic: bnd = ":"; break;
		default: bnd = ""; break;
		}
		return bnd;
	}
}

public class Atom {
	Element elem;
	Integer charge = null, radicalElectrons = null, explicitValence = null;
	
	public Atom(Element atom) {
		this.elem = atom;
		this.charge = 0; // by default, you can set it to the appropriate value below.
		this.radicalElectrons = 0; // by default, you can set it to the appropriate value below.
	}
	public void setCharge(Integer charge) { this.charge = charge; }
	public Integer getCharge() { return this.charge; }
	public void setRadicalElectrons(Integer re) { this.radicalElectrons = re; }
	public Integer getRadicalElectrons() { return this.radicalElectrons; }
	public void setExplicitValence(Integer ev) { this.explicitValence = ev; }
	public Integer getExplicitValence() { return this.explicitValence; }
	
	@Override
	public int hashCode() {
		int hash = this.elem.valueBasedHashCode();
		if (this.charge != null) 
			hash ^= this.charge;
		if (this.radicalElectrons != null) 
			hash ^= this.radicalElectrons;
		if (this.explicitValence != null) 
			hash ^= this.explicitValence;
		
		return hash;
	}
	
	@Override 
	public boolean equals(Object o) {
		if (!(o instanceof Atom))
			return false;
		Atom a = (Atom)o;
		return this.elem == a.elem 
				&& this.charge == a.charge 
				&& this.radicalElectrons == a.radicalElectrons 
				&& this.explicitValence == a.explicitValence;
	}
	
	@Override
	public String toString() {
		String a = elem.name();
		if (this.charge != null && this.charge != 0)
			a += (this.charge > 0) ? "+" + this.charge : this.charge;
		if (this.radicalElectrons != null && this.radicalElectrons != 0)
			a += "`" + this.radicalElectrons;
		if (this.explicitValence != null)
			a += "(v" + this.explicitValence + ")";  
		return a;
	}
}