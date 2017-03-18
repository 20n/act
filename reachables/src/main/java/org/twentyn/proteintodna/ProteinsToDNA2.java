/*************************************************************************
*                                                                        *
*  This file is part of the 20n/act project.                             *
*  20n/act enables DNA prediction for synthetic biology/bioengineering.  *
*  Copyright (C) 2017 20n Labs, Inc.                                     *
*                                                                        *
*  Please direct all queries to act@20n.com.                             *
*                                                                        *
*  This program is free software: you can redistribute it and/or modify  *
*  it under the terms of the GNU General Public License as published by  *
*  the Free Software Foundation, either version 3 of the License, or     *
*  (at your option) any later version.                                   *
*                                                                        *
*  This program is distributed in the hope that it will be useful,       *
*  but WITHOUT ANY WARRANTY; without even the implied warranty of        *
*  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
*  GNU General Public License for more details.                          *
*                                                                        *
*  You should have received a copy of the GNU General Public License     *
*  along with this program.  If not, see <http://www.gnu.org/licenses/>. *
*                                                                        *
*************************************************************************/

package org.twentyn.proteintodna;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProteinsToDNA2 {

    private final String Pbad_Promoter = "ttatgacaacttgacggctacatcattcactttttcttcacaaccggcacggaactcgctcgggctggccccggtgcattttttaaatacccgcgagaaatagagttgatcgtcaaaaccaacattgcgaccgacggtggcgataggcatccgggtggtgctcaaaagcagcttcgcctggctgatacgttggtcctcgcgccagcttaagacgctaatccctaactgctggcggaaaagatgtgacagacgcgacggcgacaagcaaacatgctgtgcgacgctggcgatatcaaaattgctgtctgccaggtgatcgctgatgtactgacaagcctcgcgtacccgattatccatcggtggatggagcgactcgttaatcgcttccatgcgccgcagtaacaattgctcaagcagatttatcgccagcagctccgaatagcgcccttccccttgcccggcgttaatgatttgcccaaacaggtcgctgaaatgcggctggtgcgcttcatccgggcgaaagaaccccgtattggcaaatattgacggccagttaagccattcatgccagtaggcgcgcggacgaaagtaaacccactggtgataccattcgcgagcctccggatgacgaccgtagtgatgaatctctcctggcgggaacagcaaaatatcacccggtcggcaaacaaattctcgtccctgatttttcaccaccccctgaccgcgaatggtgagattgagaatataacctttcattcccagcggtcggtcgataaaaaaatcgagataaccgttggcctcaatcggcgttaaacccgccaccagatgggcattaaacgagtatcccggcagcaggggatcattttgcgcttcagccatacttttcatactcccgccattcagagaagaaaccaattgtccatattgcatcagacattgccgtcactgcgtcttttactggctcttctcgctaaccaaaccggtaaccccgcttattaaaagcattctgtaacaaagcgggaccaaagccatgacaaaaacgcgtaacaaaagtgtctataatcacggcagaaaagtccacattgattatttgcacggcgtcacactttgctatgccatagcatttttatccataagattagcggatcctacctgacgctttttatcgcaactctctactgtttctccatacccgtttttttgggctagc";
    private final String TrrnB_Terminator = "TGCCTGGCGGCAGTAGCGCGGTGGTCCCACCTGACCCCATGCCGAACTCAGAAGTGAAACGCCGTAGCGCCGATGGTAGTGTGGGGTCTCCCCATGCGAGAGTAGGGAACTGCCAGGCATCAAATAAAACGAAAGGCTCAGTCGAAAGACTGGGCCTT";

    private SlidingWindowOptimizer swo;
    
    private ProteinsToDNA2() {}
    
    public static ProteinsToDNA2 initiate() throws Exception {
        ProteinsToDNA2 out = new ProteinsToDNA2();
        out.swo = SlidingWindowOptimizer.initiate();
        return out;
    }
    
    public Construct computeDNA(List<String> proteins, Host organism) throws Exception {
        // This local `SlidingWindowOptimizer` defeats the purpose of the one-time init of 
        // of the optimizer. Currently just commented out until confirmation from jca.
        // @jcaucb please confirm that this overriding of the `swo` with a local is not needed.
        // SlidingWindowOptimizer swo = SlidingWindowOptimizer.initiate();
        
        //Construct an mRNA for each peptide
        List<Mrna> mRNAs = new ArrayList<>();
        Set<RBSOption> ignores = new HashSet<>();
        for(String peptide : proteins) {
            Mrna mrna = swo.mRNAconstruct(peptide, ignores);
            ignores.add(mrna.rbs);
            mRNAs.add(mrna);
        }
        
        //Construct the output dna
        Construct out = new Construct();
        out.mRNAs = mRNAs;
        out.promoter = this.Pbad_Promoter;
        out.terminator = this.TrrnB_Terminator;
        return out;
    }
    
    public static void main(String[] args) throws Exception {
        ArrayList<String> proteins = new ArrayList<>();
        proteins.add("MSLEREEPQHFGAGPAQMPTPVLQQAAKDLINFNDIGLGIGEISHRSKDATKVIEDSKKHLIELLNIPDTHEVFYLQGGGTTGFSSVATNLAAAYVGKHGKIAPAGYLVTGSWSQKSFEEAKRLHVPAEVIFNAKDYNNGKFGKIPDESLWEDKIKGKAFSYVYLCENETVHGVEWPELPKCLVNDPNIEIVADLSSDILSRKIDVSQYGVIMAGAQKNIGLAGLTLYIIKKSILKNISGASDETLHELGVPITPIAFDYPTVVKNNSAYNTIPIFTLHVMDLVFQHILKKGGVEAQQAENEEKAKILYEALDANSDFYNVPVDPKCRSKMNVVFTLKKDGLDDQFLKEAAARHLTGLKGHRSVGGFRASIYNALSVKTVQNLVDFIKEFAEKNA");
        proteins.add("MGRFILKCLKCGREYSQEYRLTCENDDSFLRAEYLEKKLELRKQPGIGRFHSWLPVQEELTTEAGPITYKSEALARELGLSNLYIGFSGYWPEKGAFIKTCSFKELEAHPTMQLLKESGGKAIVLASAGNTGRAFAHVSALTGTDVYIVVPDSGIPKLWLPEEPTDSIHLISMTPGNDYTDAINLAGRIAKLPGMVPEGGARNVARREGMGTVMLDAAVTIGKMPDHYFQAVGSGTGGISAWEASLRLREDGRFGSKLPKLQLTQNLPFVPMYNAWQEGRRDIIPEIDMKDAKKRIEETYATVLTNRAPPYSVTGGLYDALVDTDGIMYAVSKEEALDAKALFESLEGIDILPPSAVAAASLLKAVEAGNVGKDDTILLNIAGGGFKRLKEDFTLFQIEPEITVSNPDVPLEELKL");
        proteins.add("MADSKPLRTLDGDPVAVEALLRDVFGIVVDEAIRKGTNASEKVCEWKEPEELKQLLDLELQSQGESRERILERCRAVIHYSVKTGHPRFFNQLFSGLDPHALAGRIITESLNTSQYTYEIAPVFVLMEEEVLKKLRALVGWNTGDGVFCPGGSISNMYAINLARFQRYPDCKQRGLRALPPLALFTSKECHYSITKGAAFLGLGTDSVRVVKADERGKMIPEDLERQISLAEAEGSVPFLVSATSGTTVLGAFDPLDAIADVCQRHGLWLHVDAAWGGSVLLSRTHRHLLDGIQRADSVAWNPHKLLAAGLQCSALLLRDTSNLLKRCHGSQASYLFQQDKFYNVALDTGDKVVQCGRRVDCLKLWLMWKAQGGQGLEWRIDQAFALTRYLVEEIKKREGFELVMEPEFVNVCFWFVPPSLRGKKESPDYSQRLSQVAPVLKERMVKKGTMMIGYQPHGTRANFFRMVVANPILVQADIDFLLGELERLGQDL");

        ProteinsToDNA2 p2d = ProteinsToDNA2.initiate();
        try {
            Construct dna = p2d.computeDNA(proteins, Host.Ecoli);
            System.out.println(dna.toSeq());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
