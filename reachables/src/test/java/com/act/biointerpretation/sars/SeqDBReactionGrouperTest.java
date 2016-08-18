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

public class SeqDBReactionGrouperTest {

  final static private Integer SEQ_ID_A = 0;
  final static private Integer SEQ_ID_B = 1;
  final static private Integer SEQ_ID_C = 2;
  final static private String NAME_A = "SEQ_ID_0";
  final static private String NAME_B = "SEQ_ID_1";
  final static private String NAME_C = "SEQ_ID_2";
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

  final static private String DB_NAME = "THE_DB";

  Seq mockSeqA;
  Seq mockSeqB;
  Seq mockSeqC;

  @Before
  public void init() {
    mockSeqA = Mockito.mock(Seq.class);
    Mockito.when(mockSeqA.getUUID()).thenReturn(SEQ_ID_A);
    Mockito.when(mockSeqA.getSequence()).thenReturn(SEQUENCE_AB);
    Mockito.when(mockSeqA.getReactionsCatalyzed()).thenReturn(reactionSetA);

    mockSeqB = Mockito.mock(Seq.class);
    Mockito.when(mockSeqB.getUUID()).thenReturn(SEQ_ID_B);
    Mockito.when(mockSeqB.getSequence()).thenReturn(SEQUENCE_AB);
    Mockito.when(mockSeqB.getReactionsCatalyzed()).thenReturn(reactionSetB);

    mockSeqC = Mockito.mock(Seq.class);
    Mockito.when(mockSeqC.getUUID()).thenReturn(SEQ_ID_C);
    Mockito.when(mockSeqC.getSequence()).thenReturn(SEQUENCE_C);
    Mockito.when(mockSeqC.getReactionsCatalyzed()).thenReturn(reactionSetC);
  }

  @Test
  public void testStrictSeqGrouper_CorrectSeqGroup() {
    Set<Seq> sequences = new HashSet<>();
    sequences.add(mockSeqA);
    sequences.add(mockSeqB);

    SeqDBReactionGrouper seqGrouper = new SeqDBReactionGrouper(sequences.iterator(), DB_NAME);

    int counter = 0;
    for (ReactionGroup group : seqGrouper.getReactionGroupCorpus()) {
      assertTrue("Right sequence.", group.getName().equals(NAME_A) || group.getName().equals(NAME_B));
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

    SeqDBReactionGrouper seqGrouper = new SeqDBReactionGrouper(sequences.iterator(), DB_NAME);

    int counter = 0;
    Set<String> outputSequences = new HashSet<>();
    for (ReactionGroup group : seqGrouper.getReactionGroupCorpus()) {
      outputSequences.add(group.getName());
      counter++;
    }

    assertEquals("Two seq groups.", 2, counter);
    assertTrue("One seq group has first sequence.",
        outputSequences.contains(NAME_A) || outputSequences.contains(NAME_B));
    assertTrue("One seq group has second sequence.", outputSequences.contains(NAME_C));
  }


  @Test
  public void testStrictSeqGrouper_AppliesLimit() {
    Set<Seq> sequences = new HashSet<>();
    sequences.add(mockSeqA);
    sequences.add(mockSeqB);
    sequences.add(mockSeqC);

    SeqDBReactionGrouper seqGrouper = new SeqDBReactionGrouper(sequences.iterator(), DB_NAME, 1);

    int counter = 0;
    for (ReactionGroup group : seqGrouper.getReactionGroupCorpus()) {
      counter++;
    }
    assertEquals("Only one seqGroup.", 1, counter);
  }


}
