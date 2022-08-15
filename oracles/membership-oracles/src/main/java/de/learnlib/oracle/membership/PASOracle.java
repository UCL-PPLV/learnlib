/* Copyright (C) 2013-2021 TU Dortmund
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
package de.learnlib.oracle.membership;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.checkerframework.checker.nullness.qual.Nullable;

import de.learnlib.api.oracle.EquivalenceOracle;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.api.query.Query;
import de.learnlib.filter.statistic.Counter;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.incremental.ConflictException;
import net.automatalib.incremental.mealy.tree.AdaptiveMealyTreeBuilder;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class PASOracle<S, I, T, O>
        implements MembershipOracle.MealyMembershipOracle<I, O>, EquivalenceOracle.MealyEquivalenceOracle<I, O> {
    private final AdaptiveMealyTreeBuilder<I, O> cache;
    private MealyMachine<S, I, T, O> hypothesis;
    private final Set<Word<I>> conflicts;
    private final MembershipOracle.MealyMembershipOracle<I, O> sulOracle;
    private Counter counter;
    private Random random;
    private Integer limit;
    private Double revisionRatio;
    private Double lengthFactor;

    public PASOracle(Alphabet<I> alphabet, MembershipOracle.MealyMembershipOracle<I, O> sulOracle, Counter counter,
            Integer cexSearchLimit, Double revisionRatio, Double lengthFactor, Random random) {
        this.cache = new AdaptiveMealyTreeBuilder<>(alphabet);
        this.conflicts = new HashSet<>();
        this.sulOracle = sulOracle;
        this.counter = counter;
        this.random = random;
        this.limit = cexSearchLimit;
        this.revisionRatio = revisionRatio;
        this.lengthFactor = lengthFactor;
    }

    public MealyMachine<S, I, T, O> getHypothesis() {
        return hypothesis;
    }

    public void setHypothesis(MealyMachine<S, I, T, O> hypothesis) {
        this.hypothesis = hypothesis;
        conflicts.clear();
    }

    private List<T> getTransitions(Word<I> word) {
        List<T> transitions = new LinkedList<>();
        S state = hypothesis.getInitialState();
        for (I sym : word) {
            transitions.add(hypothesis.getTransition(state, sym));
            state = hypothesis.getSuccessor(state, sym);
        }
        return transitions;
    }

    private void addConflict(Query<I, Word<O>> query) {
        conflicts.add(query.getInput());
    }

    private Word<O> internalProcessQuery(Query<I, Word<O>> query, Boolean saveInCache) throws ConflictException {
        Word<O> answer = sulOracle.answerQuery(query.getInput());
        query.answer(answer.suffix(query.getSuffix().length()));
        counter.increment();

        if (saveInCache) {
            cache.insert(query.getInput(), answer, (int) (long) counter.getCount());
        }

        // Conflict detected
        if (cache.conflicts(query.getInput(), answer)) {
            addConflict(query);
            Word<O> cachedAnswer = cache.lookup(query.getInput());
            cache.insert(query.getInput(), answer, (int) (long) counter.getCount());
            throw new ConflictException(
                    "Input: " + query.getInput() + ", Cache: " + cachedAnswer + ", Output: " + answer + ".");
        }

        return answer;
    }

    @Override
    public void processQueries(Collection<? extends Query<I, Word<O>>> queries) throws ConflictException {
        for (Query<I, Word<O>> query : queries) {
            // A: Check against cache.
            Word<O> cacheOutput = cache.lookup(query.getInput());
            if (query.getInput().length() == cacheOutput.length()) {
                query.answer(cacheOutput.suffix(query.getSuffix().length()));
                continue;
            }

            // B: Ask the SUL Oracle.
            internalProcessQuery(query, true);
        }
    }

    private Word<I> sampleWord() {
        if (random.nextFloat() < lengthFactor) {
            List<I> alphas = new LinkedList<>(cache.getInputAlphabet());
            Collections.shuffle(alphas, random);
            return Word.fromSymbols(alphas.get(0)).concat(sampleWord());
        }
        return Word.epsilon();
    }

    @Override
    public @Nullable DefaultQuery<I, Word<O>> findCounterExample(MealyMachine<?, I, ?, O> hypothesis,
            Collection<? extends I> inputs) throws ConflictException {

        Word<I> sepInput = cache.findSeparatingWord(hypothesis, inputs, true);
        while (sepInput != null) {
            DefaultQuery<I, Word<O>> query = new DefaultQuery<>(sepInput);
            Word<O> out = internalProcessQuery(query, true);
            if (!hypothesis.computeOutput(query.getInput()).equals(out)) {
                return new DefaultQuery<>(query.getInput(), out);
            }
            sepInput = cache.findSeparatingWord(hypothesis, inputs, true);
        }

        while (counter.getCount() < limit) {
            DefaultQuery<I, Word<O>> query = new DefaultQuery<>(Word.epsilon());
            Word<O> out = Word.epsilon();

            // FIXME: Finding a right ratio for this will be tricky.
            // Can be made easier by exploiting longer test strings.
            if (random.nextFloat() < revisionRatio) {
                query = new DefaultQuery<I, Word<O>>((Word<I>) cache.getOldestQuery());
                out = internalProcessQuery(query, true);
            } else {
                query = new DefaultQuery<>(sampleWord());
                out = internalProcessQuery(query, true);
            }

            if (!hypothesis.computeOutput(query.getInput()).equals(out)) {
                return new DefaultQuery<>(query.getInput(), out);
            }
        }

        return null;
    }
}
