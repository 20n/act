package com.act.biointerpretation.sars;

import act.shared.Seq;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StrictSeqGrouperTest {

  final static private Integer SEQ_ID_A = 0;
  final static private Integer SEQ_ID_B = 1;
  final static private Integer SEQ_ID_C = 2;
  final static private String SEQUENCE_AB = "AAA";
  final static private String SEQUENCE_C = "BBB";
  final static private Long REACTION_1 = 77L;
  final static private Long REACTION_2 = 83L;
  final static private Long REACTION_3 = 90L;
  final static private Set<Long> reactionSetA = new HashSet<>();
  final static private Set<Long> reactionSetB = new HashSet<>();
  final static private Set<Long> reactionSetC = new HashSet<>();

  static {
    reactionSetA.add(REACTION_1);
    reactionSetA.add(REACTION_2);
    reactionSetB.add(REACTION_2);
    reactionSetB.add(REACTION_3);
    reactionSetC.add(REACTION_3);
  }

  Seq mockSeqA;
  Seq mockSeqB;
  Seq mockSeqC;

  @Before
  public void init() {
    mockSeqA = Mockito.mock(Seq.class);
    Mockito.when(mockSeqA.getUUID()).thenReturn(SEQ_ID_A);
    Mockito.when(mockSeqA.get_sequence()).thenReturn(SEQUENCE_AB);
    Mockito.when(mockSeqA.getReactionsCatalyzed()).thenReturn(reactionSetA);

    mockSeqB = Mockito.mock(Seq.class);
    Mockito.when(mockSeqB.getUUID()).thenReturn(SEQ_ID_B);
    Mockito.when(mockSeqB.get_sequence()).thenReturn(SEQUENCE_AB);
    Mockito.when(mockSeqB.getReactionsCatalyzed()).thenReturn(reactionSetB);

    mockSeqC = Mockito.mock(Seq.class);
    Mockito.when(mockSeqC.getUUID()).thenReturn(SEQ_ID_C);
    Mockito.when(mockSeqC.get_sequence()).thenReturn(SEQUENCE_C);
    Mockito.when(mockSeqC.getReactionsCatalyzed()).thenReturn(reactionSetC);
  }

  @Test
  public void testStrictSeqGrouper_CorrectSeqGroup() {
    Set<Seq> sequences = new HashSet<>();
    sequences.add(mockSeqA);
    sequences.add(mockSeqB);

    StrictSeqGrouper seqGrouper = new StrictSeqGrouper(sequences.iterator());

    int counter = 0;
    for (SeqGroup group : seqGrouper.getSeqGroups()) {
      assertEquals("Right sequence.", SEQUENCE_AB, group.getSequence());
      Collection<Integer> seqIds = group.getSeqIds();
      assertEquals("Two seq ids", 2, seqIds.size());
      assertTrue("Contains first seq ID", seqIds.contains(SEQ_ID_A));
      assertTrue("Contains second seq ID", seqIds.contains(SEQ_ID_B));
      Collection<Long> reactionIds = group.getReactionIds();
      assertEquals("Three reaction ids", 3, reactionIds.size());
      assertTrue("Contains first reaction ID", reactionIds.contains(REACTION_1));
      assertTrue("Contains second reaction ID", reactionIds.contains(REACTION_2));
      assertTrue("Contains third reaction ID", reactionIds.contains(REACTION_3));
      counter++;
    }
    assertEquals("Only one seqGroup.", 1, counter);
  }

  @Test
  public void testStrictSeqGrouper_twoSeqGroups_rightSequences() {
    Set<Seq> sequences = new HashSet<>();
    sequences.add(mockSeqA);
    sequences.add(mockSeqB);
    sequences.add(mockSeqC);

    StrictSeqGrouper seqGrouper = new StrictSeqGrouper(sequences.iterator());

    int counter = 0;
    Set<String> outputSequences = new HashSet<>();
    for (SeqGroup group : seqGrouper.getSeqGroups()) {
      outputSequences.add(group.getSequence());
      counter++;
    }

    assertEquals("Two seq groups.", 2, counter);
    assertTrue("One seq group has first sequence.", outputSequences.contains(SEQUENCE_AB));
    assertTrue("One seq group has second sequence.", outputSequences.contains(SEQUENCE_C));
  }


  @Test
  public void testStrictSeqGrouper_AppliesLimit() {
    Set<Seq> sequences = new HashSet<>();
    sequences.add(mockSeqA);
    sequences.add(mockSeqB);
    sequences.add(mockSeqC);

    StrictSeqGrouper seqGrouper = new StrictSeqGrouper(sequences.iterator(), 1);

    int counter = 0;
    for (SeqGroup group : seqGrouper.getSeqGroups()) {
      assertEquals("Only one seq id", 1, group.getSeqIds().size());
      counter++;
    }
    assertEquals("Only one seqGroup.", 1, counter);
  }


}
