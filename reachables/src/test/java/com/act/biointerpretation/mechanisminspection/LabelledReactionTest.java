package com.act.biointerpretation.mechanisminspection;

import act.server.NoSQLAPI;
import com.act.biointerpretation.desalting.ReactionDesalter;
import com.act.biointerpretation.test.util.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LabelledReactionTest {

  @Test
  public void testThatLabelledReactionsAreGettingDetected() throws Exception {
    LabelledReactionsCorpus reactionsCorpus = new LabelledReactionsCorpus(new NoSQLAPI("marvin_v2", "marvin_v2"));
    reactionsCorpus.loadCorpus();

    // We know that reaction ids 39101, 190803, 38551, 763721 and 763413 in marvin_v2 are labelled reactions.
    assertTrue(reactionsCorpus.checkIfReactionIsALabelledReaction(39101L));
    assertTrue(reactionsCorpus.checkIfReactionIsALabelledReaction(190803L));
    assertTrue(reactionsCorpus.checkIfReactionIsALabelledReaction(38551L));
    assertTrue(reactionsCorpus.checkIfReactionIsALabelledReaction(763721L));
    assertTrue(reactionsCorpus.checkIfReactionIsALabelledReaction(763413L));

    // We know that 7633, 75633, 5347, 41 and 46372 in marvin_v2 are not labelled reactions.
    assertFalse(reactionsCorpus.checkIfReactionIsALabelledReaction(7633L));
    assertFalse(reactionsCorpus.checkIfReactionIsALabelledReaction(75633L));
    assertFalse(reactionsCorpus.checkIfReactionIsALabelledReaction(5347L));
    assertFalse(reactionsCorpus.checkIfReactionIsALabelledReaction(41L));
    assertFalse(reactionsCorpus.checkIfReactionIsALabelledReaction(46372L));
  }
}
