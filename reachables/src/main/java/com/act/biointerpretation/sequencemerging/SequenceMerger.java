package com.act.biointerpretation.sequencemerging;

import act.server.NoSQLAPI;
import act.shared.Seq;
import com.act.biointerpretation.BiointerpretationProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;

public class SequenceMerger extends BiointerpretationProcessor {
  private static final Logger LOGGER = LogManager.getFormatterLogger(SequenceMerger.class);
  private static final String PROCESSOR_NAME = "Sequence Merger";

  public SequenceMerger(NoSQLAPI noSQLAPI) {
    super(noSQLAPI);
  }

  @Override
  public String getName() {
    return PROCESSOR_NAME;
  }

  @Override
  public void init() {
    // Do nothing for this class, as there's no initialization necessary.
    markInitialized();
  }

  @Override
  public void run() {
    LOGGER.info("processing sequences");
    processSequences();
  }

  @Override
  public void processSequences() {
    Iterator<Seq> sequences = getNoSQLAPI().readSeqsFromInKnowledgeGraph();
  }

}
