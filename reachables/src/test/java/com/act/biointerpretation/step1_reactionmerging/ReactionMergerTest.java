package com.act.biointerpretation.step1_reactionmerging;

import act.api.NoSQLAPI;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.specs2.runner.JUnitRunner;

//@RunWith(JUnitRunner.class)
public class ReactionMergerTest {

  @Before
  public void setUp() throws Exception {

  }

  @After
  public void tearDown() throws Exception {

  }

  @Test
  public void testSomething() throws Exception {
    System.out.format("Hello world!\n");

    Assert.assertTrue("foo", true);

    NoSQLAPI mockNoSQLAPI = Mockito.mock(NoSQLAPI.class);

  }
}