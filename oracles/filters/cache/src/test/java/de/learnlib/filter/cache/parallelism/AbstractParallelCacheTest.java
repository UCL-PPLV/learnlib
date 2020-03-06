/* Copyright (C) 2013-2020 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.filter.cache.parallelism;

import java.util.ArrayList;
import java.util.List;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.parallelism.ParallelOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.cache.LearningCache;
import net.automatalib.commons.util.collections.CollectionsUtil;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * A test for checking proper synchronization of the different cache implementations.
 * <p>
 * Normally, the oracle chain would look like <i>learner</i> -> <i>cache</i> -> <i>parallel oracles</i> so that you have
 * a single(-threaded) shared cache, whose cache-misses would be answered by oracles in parallel.
 * <p>
 * This test checks that the other way around, i.e. <i>learner</i> -> <i>(parallel querying) cache</i> -> <i>single
 * oracle</i> does at least not throw any synchronization errors.
 *
 * @author frohme
 */
public abstract class AbstractParallelCacheTest<S, C extends LearningCache<A, I, D>, A, I, D> {

    protected static final int MODEL_SIZE = 10;
    private static final int MAXIMUM_LENGTH_OF_QUERIES = 5;

    private Alphabet<I> alphabet;
    private A targetModel;
    private S sul;
    private C cache;
    private ParallelOracle<I, D> parallelOracle;

    protected abstract Alphabet<I> getAlphabet();

    protected abstract A getTargetModel(Alphabet<I> alphabet);

    protected abstract S getSUL(A targetModel);

    protected abstract C getCache(Alphabet<I> alphabet, S sul);

    protected abstract ParallelOracle<I, D> getParallelOracle(C cache);

    protected abstract int getNumberOfQueries(S sul);

    @BeforeClass
    public void setUp() {
        this.alphabet = getAlphabet();
        this.targetModel = getTargetModel(this.alphabet);
        this.sul = getSUL(this.targetModel);
        this.cache = getCache(this.alphabet, this.sul);
        this.parallelOracle = getParallelOracle(this.cache);
    }

    @AfterClass
    public void teardown() {
        this.parallelOracle.shutdownNow();
    }

    @Test(timeOut = 20000)
    public void testConcurrentMembershipQueries() {
        final List<DefaultQuery<I, D>> queries =
                new ArrayList<>(((int) Math.pow(alphabet.size(), MAXIMUM_LENGTH_OF_QUERIES)) + 1);

        for (final List<I> word : CollectionsUtil.allTuples(alphabet, 0, MAXIMUM_LENGTH_OF_QUERIES)) {
            queries.add(new DefaultQuery<>(Word.fromList(word)));
        }

        this.parallelOracle.processQueries(queries);
        final long numOfQueriesBefore = getNumberOfQueries(this.sul);

        queries.forEach(q -> q.answer(null));

        this.parallelOracle.processQueries(queries);
        final long numOfQueriesAfter = getNumberOfQueries(this.sul);

        Assert.assertEquals(numOfQueriesAfter, numOfQueriesBefore);
    }

    @Test(dependsOnMethods = "testConcurrentMembershipQueries", timeOut = 20000)
    public void testConcurrentEquivalenceQueries() {
        final long previousCount = getNumberOfQueries(this.sul);
        final EquivalenceOracle<? super A, I, D> eqOracle = cache.createCacheConsistencyTest();

        final List<DefaultQuery<I, D>> queries = new ArrayList<>(
                (int) Math.pow(alphabet.size(), MAXIMUM_LENGTH_OF_QUERIES + 1) -
                (int) Math.pow(alphabet.size(), MAXIMUM_LENGTH_OF_QUERIES));

        for (final List<I> word : CollectionsUtil.allTuples(alphabet,
                                                            MAXIMUM_LENGTH_OF_QUERIES + 1,
                                                            MAXIMUM_LENGTH_OF_QUERIES + 1)) {
            queries.add(new DefaultQuery<>(Word.fromList(word)));
        }

        Thread task = new Thread(() -> {
            while (true) {
                if (eqOracle.findCounterExample(targetModel, alphabet) != null) {
                    throw new IllegalStateException();
                } else {
                    try {
                        // do not constantly block the cache, otherwise the test takes to long
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        // if we get interrupted, just terminate
                        return;
                    }
                }
            }
        });

        task.start();
        this.parallelOracle.processQueries(queries);
        task.interrupt();

        final long numOfQueries = getNumberOfQueries(this.sul);
        Assert.assertEquals(numOfQueries, queries.size() + previousCount);
    }
}
