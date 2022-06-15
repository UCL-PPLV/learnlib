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
package de.learnlib.algorithms.continuous.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

import net.automatalib.SupportsGrowingAlphabet;
import net.automatalib.automata.DeterministicAutomaton;
import net.automatalib.automata.FiniteAlphabetAutomaton;
import net.automatalib.commons.util.Pair;
import net.automatalib.graphs.Graph;
import net.automatalib.visualization.DefaultVisualizationHelper;
import net.automatalib.visualization.VisualizationHelper;
import net.automatalib.words.Alphabet;
import net.automatalib.words.GrowingAlphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;

/**
 * Hypothesis DFA for the
 * {@link de.learnlib.algorithms.continuous.dfa.ContinuousDFA algorithm}.
 *
 * @param <I> input symbol type
 *
 * @author Tiago Ferreira
 */
public class AbstractICHypothesis<I> implements DeterministicAutomaton<Word<I>, I, Pair<Word<I>, I>>,
        FiniteAlphabetAutomaton<Word<I>, I, Pair<Word<I>, I>>, SupportsGrowingAlphabet<I> {

    private final Alphabet<I> alphabet;
    public Word<I> initialState;
    public final Map<Pair<Word<I>, I>, Word<I>> transitions = new HashMap<>();

    /**
     * Constructor.
     *
     * @param alphabet the input alphabet
     */
    public AbstractICHypothesis(Alphabet<I> alphabet) {
        this.alphabet = alphabet;
    }

    @Override
    public Word<I> getInitialState() {
        return initialState;
    }

    @Override
    public Pair<Word<I>, I> getTransition(Word<I> state, I input) {
        return Pair.of(state, input);
    }

    @Override
    public Alphabet<I> getInputAlphabet() {
        return alphabet;
    }

    @Override
    public GraphView graphView() {
        return new GraphView();
    }

    @Override
    public void addAlphabetSymbol(I symbol) {
        final GrowingAlphabet<I> growingAlphabet = Alphabets.toGrowingAlphabetOrThrowException(this.alphabet);

        if (!growingAlphabet.containsSymbol(symbol)) {
            growingAlphabet.addSymbol(symbol);
        }
    }

    @Override
    public Collection<Word<I>> getStates() {
        return transitions.keySet().stream().map(p -> p.getFirst()).collect(Collectors.toSet());
    }

    @Override
    public int size() {
        return getStates().size();
    }

    public void setInitial(Word<I> initial) {
        initialState = initial;
    }

    @Override
    public Collection<Pair<Word<I>, I>> getTransitions(Word<I> state, I input) {
        return Collections.singleton(Pair.of(state, input));
    }

    @Override
    public Word<I> getSuccessor(Pair<Word<I>, I> transition) {
        return transitions.get(transition);
    }

    @Override
    public Set<Word<I>> getInitialStates() {
        return Collections.singleton(initialState);
    }

    @Override
    public @Nullable Word<I> getSuccessor(Word<I> state, I input) {
        return transitions.get(Pair.of(state, input));
    }

    public static final class ICEdge<I> {

        public final Pair<Word<I>, I> transition;
        public final Word<I> target;

        public ICEdge(Pair<Word<I>, I> transition, Word<I> target) {
            this.transition = transition;
            this.target = target;
        }
    }

    public class GraphView implements Graph<Word<I>, ICEdge<I>> {

        @Override
        public Collection<Word<I>> getNodes() {
            return getStates();
        }

        @Override
        public Collection<ICEdge<I>> getOutgoingEdges(Word<I> node) {
            List<ICEdge<I>> result = new ArrayList<>();
            transitions.entrySet().stream().filter(e -> e.getKey().getFirst().equals(node))
                    .forEach(e -> result.add(new ICEdge<>(e.getKey(), e.getValue())));
            return result;
        }

        @Override
        public Word<I> getTarget(ICEdge<I> edge) {
            return edge.target;
        }

        @Override
        public VisualizationHelper<Word<I>, ICEdge<I>> getVisualizationHelper() {
            return new DefaultVisualizationHelper<Word<I>, ICEdge<I>>() {

                @Override
                public boolean getEdgeProperties(Word<I> src, ICEdge<I> edge, Word<I> tgt,
                        Map<String, String> properties) {
                    properties.put(EdgeAttrs.LABEL, String.valueOf(edge.transition.getSecond()));
                    return true;
                }

            };
        }

    }
}
