package com.act.biointerpretation.carbonenumeration;

import act.api.NoSQLAPI;
import act.shared.Chemical;
import act.shared.Reaction;
import chemaxon.calculations.hydrogenize.Hydrogenize;
import chemaxon.formats.MolImporter;
import chemaxon.struc.MolAtom;
import chemaxon.struc.MolBond;
import chemaxon.struc.Molecule;
import com.act.biointerpretation.utils.ChemAxonUtils;
import com.act.biointerpretation.utils.FileUtils;

import java.util.*;

/**
 * Created by jca20n on 12/9/15.
 */
public class ValidateFormulas {
    private NoSQLAPI api;

    enum ValidationError {
        C1,
        C2,
        C3,
        C4part1,
        C4part2
    }

    private class ValidationException extends Exception {
        List<ValidationError> failures;
        public ValidationException(List<ValidationError> failures) {
            this.failures = failures;
        }
    }

    private class Formula {
        int C = 0;
        int H = 0;
        int N = 0;
        int O = 0;
        int P = 0;
        int S = 0;
        int Cl = 0;
        int Br = 0;
        int F = 0;
        int I = 0;

        public String toString() {
            StringBuilder sb = new StringBuilder();
            if(C > 0) {
                sb.append("C").append(C);
            }
            if(H > 0) {
                sb.append("H").append(H);
            }
            if(N > 0) {
                sb.append("N").append(N);
            }
            if(O > 0) {
                sb.append("O").append(O);
            }
            if(P > 0) {
                sb.append("P").append(P);
            }
            if(S > 0) {
                sb.append("S").append(S);
            }
            if(Cl > 0) {
                sb.append("Cl").append(Cl);
            }
            if(Br > 0) {
                sb.append("Br").append(Br);
            }
            if(I > 0) {
                sb.append("I").append(I);
            }
            if(F > 0) {
                sb.append("F").append(F);
            }
            return sb.toString();
        }
    }

    private Map<String, Long> unparsibleFormulaInchisToUUIDs = new HashMap<>();
    private Map<String, List<ValidationError>> invalidInchisToErrors = new HashMap<>();

    public static void main(String[] args) throws Exception {
        ChemAxonUtils.license();

        ValidateFormulas validator = new ValidateFormulas();
        validator.initiate();

        //Option1:  Run one
        //1-O-Phosphono-D-galactitol
//        Molecule mol = MolImporter.importMol("InChI=1S/C6H15O9P/c7-1-3(8)5(10)6(11)4(9)2-15-16(12,13)14/h3-11H,1-2H2,(H2,12,13,14)/t3-,4+,5+,6-/m1/s1");
//        Formula formula = validator.makeFormula(mol);
//        System.out.println(formula.toString());
//        System.out.println(validator.validate(formula));

        //Option2:  Run everything
        try {
            validator.run();
        } catch(Exception err) {
            System.err.println("\n\n!!!!! Failed to complete !!!!! \n\n");
        }
        validator.printout();
    }

    public void printout() throws Exception {
        StringBuilder sb = new StringBuilder();
        for(String inchi : invalidInchisToErrors.keySet()) {
            sb.append(inchi).append("\t");
            List<ValidationError> failures = invalidInchisToErrors.get(inchi);
            for(ValidationError error : failures) {
                sb.append(error.toString()).append(".");
            }
            sb.append("\n");
        }
//        System.out.println(sb.toString());
        FileUtils.writeFile(sb.toString(), "output/validateFormula_dudInchis.txt");

        sb = new StringBuilder();
        for(String inchi : unparsibleFormulaInchisToUUIDs.keySet()) {
            sb.append(unparsibleFormulaInchisToUUIDs.get(inchi) + "\t" + inchi).append("\n");
        }
//        System.out.println(sb.toString());
        FileUtils.writeFile(sb.toString(), "output/validateFormula_unparsibleFormulaInchis.txt");
    }

    public void initiate() {
        api = new NoSQLAPI("synapse", "synapse");  //read only for this method
    }

    public void run() throws Exception {
        int limit = 100000;
        int count = 0;

        Iterator<Chemical> iterator = api.readChemsFromInKnowledgeGraph();
        outer: while(iterator.hasNext()) {
            Chemical achem = iterator.next();
            count++;
            if(count > limit) {
                break outer;
            }

            String inchi = null;
            Formula formula = null;
            Molecule mol = null;
            try {
                inchi = achem.getInChI();
                mol = MolImporter.importMol(inchi);
                formula = makeFormula(mol);
            } catch(Exception err) {
                if(inchi!=null && inchi.contains("FAKE")) {
//                    System.out.println(" - FAKE inchi for " + achem.getUuid());
                    continue;
                }

                if(inchi == null) {
//                    System.out.println(" - inchi is null for " + achem.getUuid());
                    continue;
                }

                if(mol == null) {
//                    System.out.println(" - mol is null for " + inchi);
                    continue;
                }
                if(formula == null) {
                    System.out.println(" - formula is null for " + inchi);
                    unparsibleFormulaInchisToUUIDs.put(inchi, achem.getUuid());
                }
                continue;
            }

            try {
                validate(formula);
//                System.out.println(inchi);
            } catch(ValidationException err) {
                invalidInchisToErrors.put(inchi, err.failures);
                System.err.println("Error validating " + achem.getUuid());
            }
        }
    }
    
    public Formula makeFormula(Molecule mol) throws Exception {
        Hydrogenize.addHAtoms(mol);
        Formula out = new Formula();
        for(int i=0; i<mol.getAtomCount(); i++) {
            MolAtom atom = mol.getAtom(i);
            int atomnum = atom.getAtno();
            if(atomnum == 6) {
                out.C++;
            } else if(atomnum == 1) {
                out.H++;
            } else if(atomnum == 7) {
                out.N++;
            } else if(atomnum == 8) {
                out.O++;
            } else if(atomnum == 15) {
                out.P++;
            } else if(atomnum == 16) {
                out.S++;

            } else if(atomnum == 17) {
                out.Cl++;
            } else if(atomnum == 35) {
                out.Br++;
            } else if(atomnum == 53) {
                out.I++;
            } else if(atomnum == 9) {
                out.F++;
            } else {
                System.err.println("Error converting to formula, atypical atom");
                throw new Exception();
            }
        }
        return out;
    }

    public void validate(Formula formula) throws ValidationException {
        // constraint c1 above
        int cBonds = 2 * formula.C + 2;
        int numHetero = formula.N + formula.S + formula.O - 3*formula.P;
        boolean c1 = numHetero <= cBonds;

        // constraint c2 above
        int hMax = formula.N + cBonds  + formula.P;
        boolean c2 = formula.H >= 0 && formula.H <= hMax + formula.N;

        // constraint c3 above
        boolean c3 = formula.P < 5 && ( !(formula.P > 0) || (formula.O > 3 * formula.P));

        // constraint c4 above
        int halogensAndH = formula.H + formula.Cl + formula.Br + formula.I + formula.F;
        boolean c4part1 = halogensAndH <= hMax + formula.N;
        boolean c4part2 = formula.N > 0 || halogensAndH % 2 == hMax % 2;
        boolean c4 = c4part1 && c4part2;

        boolean constraint = c1 && c2 && c3 && c4;

        if(constraint==false) {
            List<ValidationError> failures = new ArrayList<>();
            ValidationException exception = new ValidationException(failures);

            if(c1==false) {
                System.out.println("Does not pass C1");
                failures.add(ValidationError.C1);
            }
            if(c2==false) {
                System.out.println("Does not pass C2");
                failures.add(ValidationError.C2);
            }
            if(c3==false) {
                System.out.println("Does not pass C3");
                failures.add(ValidationError.C3);
            }
            if(c4==false) {
                System.out.println("Does not pass C4");
                if(c4part1 == false) {
                    System.out.println("   Does not pass c4part1");
                    failures.add(ValidationError.C4part1);
                }
                if(c4part2 == false) {
                    System.out.println("   Does not pass c4part2");
                    failures.add(ValidationError.C4part2);
                }
            }
            throw exception;
        }
    }

}
