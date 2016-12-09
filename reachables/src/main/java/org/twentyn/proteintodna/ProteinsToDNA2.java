/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.twentyn.proteintodna;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author jca20n
 */
public class ProteinsToDNA2 {

  private final String Pbad_Promoter = "ttatgacaacttgacggctacatcattcactttttcttcacaaccggcacggaactcgctcgggctggccccggtgcattttttaaatacccgcgagaaatagagttgatcgtcaaaaccaacattgcgaccgacggtggcgataggcatccgggtggtgctcaaaagcagcttcgcctggctgatacgttggtcctcgcgccagcttaagacgctaatccctaactgctggcggaaaagatgtgacagacgcgacggcgacaagcaaacatgctgtgcgacgctggcgatatcaaaattgctgtctgccaggtgatcgctgatgtactgacaagcctcgcgtacccgattatccatcggtggatggagcgactcgttaatcgcttccatgcgccgcagtaacaattgctcaagcagatttatcgccagcagctccgaatagcgcccttccccttgcccggcgttaatgatttgcccaaacaggtcgctgaaatgcggctggtgcgcttcatccgggcgaaagaaccccgtattggcaaatattgacggccagttaagccattcatgccagtaggcgcgcggacgaaagtaaacccactggtgataccattcgcgagcctccggatgacgaccgtagtgatgaatctctcctggcgggaacagcaaaatatcacccggtcggcaaacaaattctcgtccctgatttttcaccaccccctgaccgcgaatggtgagattgagaatataacctttcattcccagcggtcggtcgataaaaaaatcgagataaccgttggcctcaatcggcgttaaacccgccaccagatgggcattaaacgagtatcccggcagcaggggatcattttgcgcttcagccatacttttcatactcccgccattcagagaagaaaccaattgtccatattgcatcagacattgccgtcactgcgtcttttactggctcttctcgctaaccaaaccggtaaccccgcttattaaaagcattctgtaacaaagcgggaccaaagccatgacaaaaacgcgtaacaaaagtgtctataatcacggcagaaaagtccacattgattatttgcacggcgtcacactttgctatgccatagcatttttatccataagattagcggatcctacctgacgctttttatcgcaactctctactgtttctccatacccgtttttttgggctagc";
  private final String TrrnB_Terminator = "TGCCTGGCGGCAGTAGCGCGGTGGTCCCACCTGACCCCATGCCGAACTCAGAAGTGAAACGCCGTAGCGCCGATGGTAGTGTGGGGTCTCCCCATGCGAGAGTAGGGAACTGCCAGGCATCAAATAAAACGAAAGGCTCAGTCGAAAGACTGGGCCTT";

  private SlidingWindowOptimizer swo;

  private ProteinsToDNA2() {
  }

  public static ProteinsToDNA2 initiate() throws Exception {
    ProteinsToDNA2 out = new ProteinsToDNA2();
    out.swo = SlidingWindowOptimizer.initiate();
    return out;
  }

  public Construct computeDNA(List<String> proteins, Host organism) throws Exception {
    SlidingWindowOptimizer swo = SlidingWindowOptimizer.initiate();

    //Construct an mRNA for each peptide
    List<Mrna> mRNAs = new ArrayList<>();
    Set<RBSOption> ignores = new HashSet<>();
    for (String peptide : proteins) {
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
