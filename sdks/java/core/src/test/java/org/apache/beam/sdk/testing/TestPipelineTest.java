/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.beam.sdk.testing;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.options.ApplicationNameOptions;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.values.PCollection;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Suite;

/** Tests for {@link TestPipeline}. */
@RunWith(Suite.class)
@Suite.SuiteClasses({
  TestPipelineTest.TestPipelineCreationTest.class,
  TestPipelineTest.TestPipelineEnforcementsTest.WithRealPipelineRunner.class,
  TestPipelineTest.TestPipelineEnforcementsTest.WithCrashingPipelineRunner.class
})
public class TestPipelineTest implements Serializable {

  /** Tests related to the creation of a {@link TestPipeline}. */
  @RunWith(JUnit4.class)
  public static class TestPipelineCreationTest {
    @Rule public transient TestRule restoreSystemProperties = new RestoreSystemProperties();
    @Rule public transient ExpectedException thrown = ExpectedException.none();
    @Rule public transient TestPipeline pipeline = TestPipeline.create();

    @Test
    public void testCreationUsingDefaults() {
      assertNotNull(pipeline);
      assertNotNull(TestPipeline.create());
    }

    @Test
    public void testCreationNotAsTestRule() {
      thrown.expect(IllegalStateException.class);
      thrown.expectMessage("@Rule");

      TestPipeline.create().run();
    }

    @Test
    public void testCreationOfPipelineOptions() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      String stringOptions =
          mapper.writeValueAsString(
              new String[] {
                "--runner=org.apache.beam.sdk.testing.CrashingRunner"
              });
      System.getProperties().put("beamTestPipelineOptions", stringOptions);
      PipelineOptions options = TestPipeline.testingPipelineOptions();
      assertEquals(CrashingRunner.class, options.getRunner());
    }

    @Test
    public void testCreationOfPipelineOptionsFromReallyVerboselyNamedTestCase() throws Exception {
      PipelineOptions options = TestPipeline.testingPipelineOptions();
      assertThat(
          options.as(ApplicationNameOptions.class).getAppName(),
          startsWith(
              "TestPipelineTest$TestPipelineCreationTest"
                  + "-testCreationOfPipelineOptionsFromReallyVerboselyNamedTestCase"));
    }

    @Test
    public void testToString() {
      assertEquals(
          "TestPipeline#TestPipelineTest$TestPipelineCreationTest-testToString",
          TestPipeline.create().toString());
    }

    @Test
    public void testToStringNestedMethod() {
      TestPipeline p = nestedMethod();

      assertEquals(
          "TestPipeline#TestPipelineTest$TestPipelineCreationTest-testToStringNestedMethod",
          p.toString());
      assertEquals(
          "TestPipelineTest$TestPipelineCreationTest-testToStringNestedMethod",
          p.getOptions().as(ApplicationNameOptions.class).getAppName());
    }

    private TestPipeline nestedMethod() {
      return TestPipeline.create();
    }

    @Test
    public void testConvertToArgs() {
      String[] args = new String[] {"--tempLocation=Test_Location"};
      PipelineOptions options = PipelineOptionsFactory.fromArgs(args).as(PipelineOptions.class);
      String[] arr = TestPipeline.convertToArgs(options);
      List<String> lst = Arrays.asList(arr);
      assertEquals(lst.size(), 2);
      assertThat(
          lst,
          containsInAnyOrder("--tempLocation=Test_Location", "--appName=TestPipelineCreationTest"));
    }

    @Test
    public void testToStringNestedClassMethod() {
      TestPipeline p = new NestedTester().p();

      assertEquals(
          "TestPipeline#TestPipelineTest$TestPipelineCreationTest-testToStringNestedClassMethod",
          p.toString());
      assertEquals(
          "TestPipelineTest$TestPipelineCreationTest-testToStringNestedClassMethod",
          p.getOptions().as(ApplicationNameOptions.class).getAppName());
    }

    private static class NestedTester {
      public TestPipeline p() {
        return TestPipeline.create();
      }
    }

    @Test
    public void testRunWithDummyEnvironmentVariableFails() {
      System.getProperties()
          .setProperty(TestPipeline.PROPERTY_USE_DEFAULT_DUMMY_RUNNER, Boolean.toString(true));
      pipeline.apply(Create.of(1, 2, 3));

      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("Cannot call #run");
      pipeline.run();
    }

    /** TestMatcher is a matcher designed for testing matcher serialization/deserialization. */
    public static class TestMatcher extends BaseMatcher<PipelineResult>
        implements SerializableMatcher<PipelineResult> {
      private final UUID uuid = UUID.randomUUID();

      @Override
      public boolean matches(Object o) {
        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(String.format("%tL", new Date()));
      }

      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof TestMatcher)) {
          return false;
        }
        TestMatcher other = (TestMatcher) obj;
        return other.uuid.equals(uuid);
      }

      @Override
      public int hashCode() {
        return uuid.hashCode();
      }
    }
  }

  /**
   * Tests for {@link TestPipeline}'s detection of missing {@link Pipeline#run()}, or abandoned
   * (dangling) {@link PAssert} or {@link org.apache.beam.sdk.transforms.PTransform} nodes.
   */
  public static class TestPipelineEnforcementsTest implements Serializable {

    private static final List<String> WORDS = Collections.singletonList("hi there");
    private static final String WHATEVER = "expected";
    private static final String P_TRANSFORM = "PTransform";
    private static final String P_ASSERT = "PAssert";

    @SuppressWarnings("UnusedReturnValue")
    private static PCollection<String> addTransform(final PCollection<String> pCollection) {
      return pCollection.apply(
          "Map2",
          MapElements.via(
              new SimpleFunction<String, String>() {

                @Override
                public String apply(final String input) {
                  return WHATEVER;
                }
              }));
    }

    private static PCollection<String> pCollection(final Pipeline pipeline) {
      return pipeline
          .apply("Create", Create.of(WORDS).withCoder(StringUtf8Coder.of()))
          .apply(
              "Map1",
              MapElements.via(
                  new SimpleFunction<String, String>() {

                    @Override
                    public String apply(final String input) {
                      return WHATEVER;
                    }
                  }));
    }

    /** Tests for {@link TestPipeline}s with a non {@link CrashingRunner}. */
    @RunWith(JUnit4.class)
    public static class WithRealPipelineRunner {

      private final transient ExpectedException exception = ExpectedException.none();

      private final transient TestPipeline pipeline = TestPipeline.create();

      @Rule
      public final transient RuleChain chain = RuleChain.outerRule(exception).around(pipeline);

      @Category(ValidatesRunner.class)
      @Test
      public void testNormalFlow() throws Exception {
        addTransform(pCollection(pipeline));
        pipeline.run();
      }

      @Category(ValidatesRunner.class)
      @Test
      public void testMissingRun() throws Exception {
        exception.expect(TestPipeline.PipelineRunMissingException.class);
        addTransform(pCollection(pipeline));
      }

      @Category(ValidatesRunner.class)
      @Test
      public void testMissingRunWithDisabledEnforcement() throws Exception {
        pipeline.enableAbandonedNodeEnforcement(false);
        addTransform(pCollection(pipeline));

        // disable abandoned node detection
      }

      @Category(ValidatesRunner.class)
      @Test
      public void testMissingRunAutoAdd() throws Exception {
        pipeline.enableAutoRunIfMissing(true);
        addTransform(pCollection(pipeline));

        // have the pipeline.run() auto-added
      }

      @Category(ValidatesRunner.class)
      @Test
      public void testDanglingPTransformValidatesRunner() throws Exception {
        final PCollection<String> pCollection = pCollection(pipeline);
        PAssert.that(pCollection).containsInAnyOrder(WHATEVER);
        pipeline.run().waitUntilFinish();

        exception.expect(TestPipeline.AbandonedNodeException.class);
        exception.expectMessage(P_TRANSFORM);
        // dangling PTransform
        addTransform(pCollection);
      }

      @Category(NeedsRunner.class)
      @Test
      public void testDanglingPTransformNeedsRunner() throws Exception {
        final PCollection<String> pCollection = pCollection(pipeline);
        PAssert.that(pCollection).containsInAnyOrder(WHATEVER);
        pipeline.run().waitUntilFinish();

        exception.expect(TestPipeline.AbandonedNodeException.class);
        exception.expectMessage(P_TRANSFORM);
        // dangling PTransform
        addTransform(pCollection);
      }

      @Category(ValidatesRunner.class)
      @Test
      public void testDanglingPAssertValidatesRunner() throws Exception {
        final PCollection<String> pCollection = pCollection(pipeline);
        PAssert.that(pCollection).containsInAnyOrder(WHATEVER);
        pipeline.run().waitUntilFinish();

        exception.expect(TestPipeline.AbandonedNodeException.class);
        exception.expectMessage(P_ASSERT);
        // dangling PAssert
        PAssert.that(pCollection).containsInAnyOrder(WHATEVER);
      }

      /**
       * Tests that a {@link TestPipeline} rule behaves as expected when there is no pipeline usage
       * within a test that has a {@link ValidatesRunner} annotation.
       */
      @Category(ValidatesRunner.class)
      @Test
      public void testNoTestPipelineUsedValidatesRunner() {}

      /**
       * Tests that a {@link TestPipeline} rule behaves as expected when there is no pipeline usage
       * present in a test.
       */
      @Test
      public void testNoTestPipelineUsedNoAnnotation() {}
    }

    /** Tests for {@link TestPipeline}s with a {@link CrashingRunner}. */
    @RunWith(JUnit4.class)
    public static class WithCrashingPipelineRunner {

      static {
        System.setProperty(TestPipeline.PROPERTY_USE_DEFAULT_DUMMY_RUNNER, Boolean.TRUE.toString());
      }

      private final transient ExpectedException exception = ExpectedException.none();

      private final transient TestPipeline pipeline = TestPipeline.create();

      @Rule
      public final transient RuleChain chain = RuleChain.outerRule(exception).around(pipeline);

      @Test
      public void testNoTestPipelineUsed() {}

      @Test
      public void testMissingRun() throws Exception {
        addTransform(pCollection(pipeline));

        // pipeline.run() is missing, BUT:
        // 1. Neither @ValidatesRunner nor @NeedsRunner are present, AND
        // 2. The runner class is CrashingRunner.class
        // (1) + (2) => we assume this pipeline was never meant to be run, so no exception is
        // thrown on account of the missing run / dangling nodes.
      }
    }
  }
}
