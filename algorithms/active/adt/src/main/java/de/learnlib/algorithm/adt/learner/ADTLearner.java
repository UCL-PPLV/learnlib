/* Copyright (C) 2013-2023 TU Dortmund
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
package de.learnlib.algorithm.adt.learner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.Resumable;
import de.learnlib.algorithm.LearningAlgorithm;
import de.learnlib.algorithm.adt.adt.ADT;
import de.learnlib.algorithm.adt.adt.ADT.LCAInfo;
import de.learnlib.algorithm.adt.adt.ADTLeafNode;
import de.learnlib.algorithm.adt.adt.ADTNode;
import de.learnlib.algorithm.adt.adt.ADTResetNode;
import de.learnlib.algorithm.adt.api.ADTExtender;
import de.learnlib.algorithm.adt.api.LeafSplitter;
import de.learnlib.algorithm.adt.api.PartialTransitionAnalyzer;
import de.learnlib.algorithm.adt.api.SubtreeReplacer;
import de.learnlib.algorithm.adt.automaton.ADTHypothesis;
import de.learnlib.algorithm.adt.automaton.ADTState;
import de.learnlib.algorithm.adt.automaton.ADTTransition;
import de.learnlib.algorithm.adt.config.ADTExtenders;
import de.learnlib.algorithm.adt.config.LeafSplitters;
import de.learnlib.algorithm.adt.config.SubtreeReplacers;
import de.learnlib.algorithm.adt.model.ExtensionResult;
import de.learnlib.algorithm.adt.model.ObservationTree;
import de.learnlib.algorithm.adt.model.ReplacementResult;
import de.learnlib.algorithm.adt.util.ADTUtil;
import de.learnlib.algorithm.adt.util.SQOOTBridge;
import de.learnlib.counterexample.LocalSuffixFinders;
import de.learnlib.logging.Category;
import de.learnlib.oracle.SymbolQueryOracle;
import de.learnlib.query.DefaultQuery;
import de.learnlib.util.MQUtil;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.Alphabets;
import net.automatalib.alphabet.SupportsGrowingAlphabet;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.common.util.Pair;
import net.automatalib.word.Word;
import net.automatalib.word.WordBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main learning algorithm.
 *
 * @param <I>
 *         input alphabet type
 * @param <O>
 *         output alphabet type
 */
public class ADTLearner<I, O> implements LearningAlgorithm.MealyLearner<I, O>,
                                         PartialTransitionAnalyzer<ADTState<I, O>, I>,
                                         SupportsGrowingAlphabet<I>,
                                         Resumable<ADTLearnerState<ADTState<I, O>, I, O>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ADTLearner.class);

    private final Alphabet<I> alphabet;
    private final SQOOTBridge<I, O> oracle;
    private final LeafSplitter leafSplitter;
    private final ADTExtender adtExtender;
    private final SubtreeReplacer subtreeReplacer;
    private final Queue<ADTTransition<I, O>> openTransitions;
    private final Queue<DefaultQuery<I, Word<O>>> openCounterExamples;
    private final Set<DefaultQuery<I, Word<O>>> allCounterExamples;
    private final ObservationTree<ADTState<I, O>, I, O> observationTree;
    private ADTHypothesis<I, O> hypothesis;
    private ADT<ADTState<I, O>, I, O> adt;

    public ADTLearner(Alphabet<I> alphabet,
                      SymbolQueryOracle<I, O> oracle,
                      LeafSplitter leafSplitter,
                      ADTExtender adtExtender,
                      SubtreeReplacer subtreeReplacer) {
        this(alphabet, oracle, leafSplitter, adtExtender, subtreeReplacer, true);
    }

    @GenerateBuilder(defaults = BuilderDefaults.class)
    public ADTLearner(Alphabet<I> alphabet,
                      SymbolQueryOracle<I, O> oracle,
                      LeafSplitter leafSplitter,
                      ADTExtender adtExtender,
                      SubtreeReplacer subtreeReplacer,
                      boolean useObservationTree) {

        this.alphabet = alphabet;
        this.observationTree = new ObservationTree<>(this.alphabet);
        this.oracle = new SQOOTBridge<>(this.observationTree, oracle, useObservationTree);

        this.leafSplitter = leafSplitter;
        this.adtExtender = adtExtender;
        this.subtreeReplacer = subtreeReplacer;

        this.hypothesis = new ADTHypothesis<>(this.alphabet);
        this.openTransitions = new ArrayDeque<>();
        this.openCounterExamples = new ArrayDeque<>();
        this.allCounterExamples = new LinkedHashSet<>();
        this.adt = new ADT<>();
    }

    @Override
    public void startLearning() {

        final ADTState<I, O> initialState = this.hypothesis.addInitialState();
        initialState.setAccessSequence(Word.epsilon());
        this.observationTree.initialize(initialState);
        this.oracle.initialize();
        this.adt.initialize(initialState);

        for (I i : this.alphabet) {
            this.openTransitions.add(this.hypothesis.createOpenTransition(initialState, i, this.adt.getRoot()));
        }

        this.closeTransitions();
    }

    @Override
    public boolean refineHypothesis(DefaultQuery<I, Word<O>> ce) {

        if (!MQUtil.isCounterexample(ce, this.hypothesis)) {
            return false;
        }

        this.evaluateSubtreeReplacement();

        this.openCounterExamples.add(ce);

        while (!this.openCounterExamples.isEmpty()) {

            // normal refinement step
            while (!this.openCounterExamples.isEmpty()) {

                final DefaultQuery<I, Word<O>> currentCE = this.openCounterExamples.poll();
                this.allCounterExamples.add(currentCE);

                while (this.refineHypothesisInternal(currentCE)) {}
            }

            // subtree replacements may reactivate old CEs
            for (DefaultQuery<I, Word<O>> oldCE : this.allCounterExamples) {
                if (!this.hypothesis.computeOutput(oldCE.getInput()).equals(oldCE.getOutput())) {
                    this.openCounterExamples.add(oldCE);
                }
            }

            ADTUtil.collectLeaves(this.adt.getRoot()).forEach(this::ensureConsistency);
        }

        return true;
    }

    public boolean refineHypothesisInternal(DefaultQuery<I, Word<O>> ceQuery) {

        if (!MQUtil.isCounterexample(ceQuery, this.hypothesis)) {
            return false;
        }

        // Determine a counterexample decomposition (u, a, v)
        final int suffixIdx = LocalSuffixFinders.RIVEST_SCHAPIRE.findSuffixIndex(ceQuery,
                                                                                 this.hypothesis,
                                                                                 this.hypothesis,
                                                                                 this.oracle);

        if (suffixIdx == -1) {
            throw new IllegalStateException();
        }

        final Word<I> ceInput = ceQuery.getInput();

        final Word<I> u = ceInput.prefix(suffixIdx - 1);
        final Word<I> ua = ceInput.prefix(suffixIdx);
        final I a = ceInput.getSymbol(suffixIdx - 1);
        final Word<I> v = ceInput.subWord(suffixIdx);

        final ADTState<I, O> uState = this.hypothesis.getState(u);
        final ADTState<I, O> uaState = this.hypothesis.getState(ua);

        assert uState != null && uaState != null;

        final Word<I> uAccessSequence = uState.getAccessSequence();
        final Word<I> uaAccessSequence = uaState.getAccessSequence();
        final Word<I> uAccessSequenceWithA = uAccessSequence.append(a);

        final ADTState<I, O> newState = this.hypothesis.addState();
        newState.setAccessSequence(uAccessSequenceWithA);
        final ADTTransition<I, O> oldTrans = this.hypothesis.getTransition(uState, a);

        assert oldTrans != null;

        oldTrans.setTarget(newState);
        oldTrans.setIsSpanningTreeEdge(true);

        final Set<ADTNode<ADTState<I, O>, I, O>> finalNodes = ADTUtil.collectLeaves(this.adt.getRoot());
        final ADTNode<ADTState<I, O>, I, O> nodeToSplit = finalNodes.stream()
                                                                    .filter(n -> uaState.equals(n.getHypothesisState()))
                                                                    .findFirst()
                                                                    .orElseThrow(IllegalStateException::new);

        final ADTNode<ADTState<I, O>, I, O> newNode;

        // directly insert into observation tree, because we use it for finding a splitter
        this.observationTree.addState(newState, newState.getAccessSequence(), oldTrans.getOutput());
        this.observationTree.addTrace(newState, nodeToSplit);

        final Word<I> previousTrace = ADTUtil.buildTraceForNode(nodeToSplit).getFirst();
        final Optional<Word<I>> extension = this.observationTree.findSeparatingWord(uaState, newState, previousTrace);

        if (extension.isPresent()) {
            final Word<I> completeSplitter = previousTrace.concat(extension.get());
            final Word<O> oldOutput = this.observationTree.trace(uaState, completeSplitter);
            final Word<O> newOutput = this.observationTree.trace(newState, completeSplitter);

            newNode = this.adt.extendLeaf(nodeToSplit, completeSplitter, oldOutput, newOutput, this.leafSplitter);
        } else {
            // directly insert into observation tree, because we use it for finding a splitter
            this.observationTree.addTrace(uaState, v, this.oracle.answerQuery(uaAccessSequence, v));
            this.observationTree.addTrace(newState, v, this.oracle.answerQuery(uAccessSequenceWithA, v));

            // in doubt, we will always find v
            final Word<I> otSepWord = this.observationTree.findSeparatingWord(uaState, newState);
            final Word<I> splitter;

            if (otSepWord.length() < v.length()) {
                splitter = otSepWord;
            } else {
                splitter = v;
            }

            final Word<O> oldOutput = this.observationTree.trace(uaState, splitter);
            final Word<O> newOutput = this.observationTree.trace(newState, splitter);

            newNode = this.adt.splitLeaf(nodeToSplit, splitter, oldOutput, newOutput, this.leafSplitter);
        }
        newNode.setHypothesisState(newState);

        final ADTNode<ADTState<I, O>, I, O> temporarySplitter = ADTUtil.getStartOfADS(nodeToSplit);
        final List<ADTTransition<I, O>> newTransitions = alphabet.stream()
                                                                 .map(i -> this.hypothesis.createOpenTransition(newState,
                                                                                                                i,
                                                                                                                this.adt.getRoot()))
                                                                 .collect(Collectors.toList());
        final List<ADTTransition<I, O>> transitionsToRefine = nodeToSplit.getHypothesisState()
                                                                         .getIncomingTransitions()
                                                                         .stream()
                                                                         .filter(x -> !x.isSpanningTreeEdge())
                                                                         .collect(Collectors.toList());

        transitionsToRefine.forEach(x -> {
            x.setTarget(null);
            x.setSiftNode(temporarySplitter);
        });

        final ADTNode<ADTState<I, O>, I, O> finalizedSplitter = this.evaluateAdtExtension(temporarySplitter);

        transitionsToRefine.stream().filter(ADTTransition::needsSifting).forEach(x -> {
            x.setSiftNode(finalizedSplitter);
            this.openTransitions.add(x);
        });
        newTransitions.stream().filter(ADTTransition::needsSifting).forEach(this.openTransitions::add);

        this.closeTransitions();
        return true;
    }

    @Override
    public MealyMachine<?, I, ?, O> getHypothesisModel() {
        return this.hypothesis;
    }

    /**
     * Close all pending open transitions.
     */
    private void closeTransitions() {
        while (!this.openTransitions.isEmpty()) {
            this.closeTransition(this.openTransitions.poll());
        }
    }

    /**
     * Close the given transitions by means of sifting the associated long prefix through the ADT.
     *
     * @param transition
     *         the transition to close
     */
    private void closeTransition(ADTTransition<I, O> transition) {

        if (!transition.needsSifting()) {
            return;
        }

        final Word<I> accessSequence = transition.getSource().getAccessSequence();
        final I symbol = transition.getInput();

        this.oracle.reset();
        for (I i : accessSequence) {
            this.oracle.query(i);
        }

        transition.setOutput(this.oracle.query(symbol));

        final Word<I> longPrefix = accessSequence.append(symbol);
        final ADTNode<ADTState<I, O>, I, O> finalNode =
                this.adt.sift(this.oracle, longPrefix, transition.getSiftNode());

        assert ADTUtil.isLeafNode(finalNode);

        final ADTState<I, O> targetState;

        // new state discovered while sifting
        if (finalNode.getHypothesisState() == null) {
            targetState = this.hypothesis.addState();
            targetState.setAccessSequence(longPrefix);

            finalNode.setHypothesisState(targetState);
            transition.setIsSpanningTreeEdge(true);

            this.observationTree.addState(targetState, longPrefix, transition.getOutput());

            for (I i : this.alphabet) {
                this.openTransitions.add(this.hypothesis.createOpenTransition(targetState, i, this.adt.getRoot()));
            }
        } else {
            targetState = finalNode.getHypothesisState();
        }

        transition.setTarget(targetState);
    }

    @Override
    public void closeTransition(ADTState<I, O> state, I input) {

        final ADTTransition<I, O> transition = this.hypothesis.getTransition(state, input);
        assert transition != null;

        if (transition.needsSifting()) {
            final ADTNode<ADTState<I, O>, I, O> ads = transition.getSiftNode();
            final int oldNumberOfFinalStates = ADTUtil.collectLeaves(ads).size();

            this.closeTransition(transition);

            final int newNumberOfFinalStates = ADTUtil.collectLeaves(ads).size();

            if (oldNumberOfFinalStates < newNumberOfFinalStates) {
                throw PartialTransitionAnalyzer.HYPOTHESIS_MODIFICATION_EXCEPTION;
            }
        }
    }

    @Override
    public boolean isTransitionDefined(ADTState<I, O> state, I input) {
        final ADTTransition<I, O> transition = this.hypothesis.getTransition(state, input);
        assert transition != null;
        return !transition.needsSifting();
    }

    @Override
    public void addAlphabetSymbol(I symbol) {

        if (!this.alphabet.containsSymbol(symbol)) {
            Alphabets.toGrowingAlphabetOrThrowException(this.alphabet).addSymbol(symbol);
        }

        this.hypothesis.addAlphabetSymbol(symbol);
        this.observationTree.getObservationTree().addAlphabetSymbol(symbol);

        // check if we already have information about the symbol (then the transition is defined) so we don't post
        // redundant queries
        if (this.hypothesis.getInitialState() != null &&
            this.hypothesis.getSuccessor(this.hypothesis.getInitialState(), symbol) == null) {
            for (ADTState<I, O> s : this.hypothesis.getStates()) {
                this.openTransitions.add(this.hypothesis.createOpenTransition(s, symbol, this.adt.getRoot()));
            }

            this.closeTransitions();
        }
    }

    @Override
    public ADTLearnerState<ADTState<I, O>, I, O> suspend() {
        return new ADTLearnerState<>(this.hypothesis, this.adt);
    }

    @Override
    public void resume(ADTLearnerState<ADTState<I, O>, I, O> state) {
        this.hypothesis = state.getHypothesis();
        this.adt = state.getAdt();

        final Alphabet<I> oldAlphabet = this.hypothesis.getInputAlphabet();
        if (!oldAlphabet.equals(this.alphabet)) {
            LOGGER.warn(Category.DATASTRUCTURE,
                        "The current alphabet '{}' differs from the resumed alphabet '{}'. Future behavior may be inconsistent",
                        this.alphabet,
                        oldAlphabet);
        }

        // startLearning has already been invoked
        if (this.hypothesis.size() > 0) {
            this.observationTree.initialize(this.hypothesis.getStates(),
                                            ADTState::getAccessSequence,
                                            this.hypothesis::computeOutput);
            this.oracle.initialize();
        }
    }

    /**
     * Ensure that the output behavior of a hypothesis state matches the observed output behavior recorded in the ADT.
     * Any differences in output behavior yields new counterexamples.
     *
     * @param leaf
     *         the leaf whose hypothesis state should be checked
     */
    private void ensureConsistency(ADTNode<ADTState<I, O>, I, O> leaf) {

        final ADTState<I, O> state = leaf.getHypothesisState();
        final Word<I> as = state.getAccessSequence();
        final Word<O> asOut = this.hypothesis.computeOutput(as);

        ADTNode<ADTState<I, O>, I, O> iter = leaf;

        while (iter != null) {
            final Pair<Word<I>, Word<O>> trace = ADTUtil.buildTraceForNode(iter);

            final Word<I> input = trace.getFirst();
            final Word<O> output = trace.getSecond();

            final Word<O> hypOut = this.hypothesis.computeStateOutput(state, input);

            if (!hypOut.equals(output)) {
                this.openCounterExamples.add(new DefaultQuery<>(as.concat(input), asOut.concat(output)));
            }

            iter = ADTUtil.getStartOfADS(iter).getParent();
        }
    }

    /**
     * Ask the current {@link #adtExtender} for a potential extension.
     *
     * @param ads
     *         the temporary ADS based on the inferred distinguishing suffix
     *
     * @return a validated ADT that can be used to distinguish the states referenced in the given temporary ADS
     */
    private ADTNode<ADTState<I, O>, I, O> evaluateAdtExtension(ADTNode<ADTState<I, O>, I, O> ads) {

        final ExtensionResult<ADTState<I, O>, I, O> potentialExtension =
                this.adtExtender.computeExtension(this.hypothesis, this, ads);

        if (potentialExtension.isCounterExample()) {
            this.openCounterExamples.add(potentialExtension.getCounterExample());
            return ads;
        } else if (!potentialExtension.isReplacement()) {
            return ads;
        }

        final ADTNode<ADTState<I, O>, I, O> extension = potentialExtension.getReplacement();
        final ADTNode<ADTState<I, O>, I, O> nodeToReplace = ads.getParent(); // reset node

        assert this.validateADS(nodeToReplace, extension, Collections.emptySet());

        final ADTNode<ADTState<I, O>, I, O> replacement = this.verifyADS(nodeToReplace,
                                                                         extension,
                                                                         ADTUtil.collectLeaves(this.adt.getRoot()),
                                                                         Collections.emptySet());

        // verification may have introduced reset nodes
        final int oldCosts = ADTUtil.computeEffectiveResets(nodeToReplace);
        final int newCosts = ADTUtil.computeEffectiveResets(replacement);

        if (newCosts >= oldCosts) {
            return ads;
        }

        // replace
        this.adt.replaceNode(nodeToReplace, replacement);

        final ADTNode<ADTState<I, O>, I, O> finalizedADS = ADTUtil.getStartOfADS(replacement);

        // update
        this.resiftAffectedTransitions(ADTUtil.collectLeaves(extension), finalizedADS);

        return finalizedADS;
    }

    /**
     * Ask the {@link #subtreeReplacer} for any replacements.
     */
    private void evaluateSubtreeReplacement() {

        if (this.hypothesis.size() == 1) {
            // skip replacement if only one node is discovered
            return;
        }

        final Set<ReplacementResult<ADTState<I, O>, I, O>> potentialReplacements =
                this.subtreeReplacer.computeReplacements(this.hypothesis, this.alphabet, this.adt);
        final List<ReplacementResult<ADTState<I, O>, I, O>> validReplacements =
                new ArrayList<>(potentialReplacements.size());
        final Set<ADTNode<ADTState<I, O>, I, O>> cachedLeaves =
                potentialReplacements.isEmpty() ? Collections.emptySet() : ADTUtil.collectLeaves(this.adt.getRoot());

        for (ReplacementResult<ADTState<I, O>, I, O> potentialReplacement : potentialReplacements) {
            final ADTNode<ADTState<I, O>, I, O> proposedReplacement = potentialReplacement.getReplacement();
            final ADTNode<ADTState<I, O>, I, O> nodeToReplace = potentialReplacement.getNodeToReplace();

            assert this.validateADS(nodeToReplace, proposedReplacement, potentialReplacement.getCutoutNodes());

            final ADTNode<ADTState<I, O>, I, O> replacement = this.verifyADS(nodeToReplace,
                                                                             proposedReplacement,
                                                                             cachedLeaves,
                                                                             potentialReplacement.getCutoutNodes());

            // verification may have introduced reset nodes
            final int oldCosts = ADTUtil.computeEffectiveResets(nodeToReplace);
            final int newCosts = ADTUtil.computeEffectiveResets(replacement);

            if (newCosts >= oldCosts) {
                continue;
            }

            validReplacements.add(new ReplacementResult<>(nodeToReplace, replacement));
        }

        for (ReplacementResult<ADTState<I, O>, I, O> potentialReplacement : validReplacements) {
            final ADTNode<ADTState<I, O>, I, O> replacement = potentialReplacement.getReplacement();
            final ADTNode<ADTState<I, O>, I, O> nodeToReplace = potentialReplacement.getNodeToReplace();

            this.adt.replaceNode(nodeToReplace, replacement);

            this.resiftAffectedTransitions(ADTUtil.collectLeaves(replacement), ADTUtil.getStartOfADS(replacement));
        }

        this.closeTransitions();
    }

    /**
     * Validate the well-definedness of an ADT replacement, i.e. both ADTs cover the same set of hypothesis states and
     * the output behavior described in the replacement matches the hypothesis output.
     *
     * @param oldADS
     *         the old ADT (subtree) to be replaced
     * @param newADS
     *         the new ADT (subtree)
     * @param cutout
     *         the set of states not covered by the new ADT
     *
     * @return {@code true} if the replacement is valid, {@code false} otherwise.
     */
    private boolean validateADS(ADTNode<ADTState<I, O>, I, O> oldADS,
                                ADTNode<ADTState<I, O>, I, O> newADS,
                                Set<ADTState<I, O>> cutout) {

        final Set<ADTNode<ADTState<I, O>, I, O>> oldNodes;

        if (ADTUtil.isResetNode(oldADS)) {
            oldNodes = ADTUtil.collectResetNodes(this.adt.getRoot());
        } else {
            oldNodes = ADTUtil.collectADSNodes(this.adt.getRoot());
        }

        if (!oldNodes.contains(oldADS)) {
            throw new IllegalArgumentException("Subtree to replace does not exist");
        }

        final Set<ADTNode<ADTState<I, O>, I, O>> newFinalNodes = ADTUtil.collectLeaves(newADS);

        final Set<ADTState<I, O>> oldFinalStates = ADTUtil.collectHypothesisStates(oldADS);
        final Set<ADTState<I, O>> newFinalStates =
                newFinalNodes.stream().map(ADTNode::getHypothesisState).collect(Collectors.toSet());
        newFinalStates.addAll(cutout);

        if (!oldFinalStates.equals(newFinalStates)) {
            throw new IllegalArgumentException("New ADS does not cover all old nodes");
        }

        final Word<I> parentInputTrace = ADTUtil.buildTraceForNode(oldADS).getFirst();
        final Map<ADTState<I, O>, Pair<Word<I>, Word<O>>> traces = newFinalNodes.stream()
                                                                                .collect(Collectors.toMap(ADTNode::getHypothesisState,
                                                                                                          ADTUtil::buildTraceForNode));

        for (Map.Entry<ADTState<I, O>, Pair<Word<I>, Word<O>>> entry : traces.entrySet()) {

            final Word<I> accessSequence = entry.getKey().getAccessSequence();
            final Word<I> prefix = accessSequence.concat(parentInputTrace);
            final Word<I> input = entry.getValue().getFirst();
            final Word<O> output = entry.getValue().getSecond();

            if (!this.hypothesis.computeSuffixOutput(prefix, input).equals(output)) {
                throw new IllegalArgumentException("Output of new ADS does not match hypothesis");
            }
        }

        return true;
    }

    /**
     * Verify the proposed ADT replacement by checking the actual behavior of the system under learning. During the
     * verification process, the system under learning may behave differently from what the ADT replacement suggests:
     * This means a counterexample is witnessed and added to the queue of counterexamples for later investigation.
     * Albeit observing diverging behavior, this method continues to trying to construct a valid ADT using the observed
     * output. If for two states, no distinguishing output can be observed, the states a separated by means of
     * {@link #resolveAmbiguities(ADTNode, ADTNode, ADTState, Set)}.
     *
     * @param nodeToReplace
     *         the old ADT (subtree) to be replaced
     * @param replacement
     *         the new ADT (subtree). Must have the form of an ADS, i.e. no reset nodes
     * @param cachedLeaves
     *         a set containing the leaves of the current tree, so they don't have to be re-fetched for every
     *         replacement verification
     * @param cutout
     *         the set of states not covered by the new ADT
     *
     * @return A verified ADT that correctly distinguishes the states covered by the original ADT
     */
    private ADTNode<ADTState<I, O>, I, O> verifyADS(ADTNode<ADTState<I, O>, I, O> nodeToReplace,
                                                    ADTNode<ADTState<I, O>, I, O> replacement,
                                                    Set<ADTNode<ADTState<I, O>, I, O>> cachedLeaves,
                                                    Set<ADTState<I, O>> cutout) {

        final Map<ADTState<I, O>, Pair<Word<I>, Word<O>>> traces = new LinkedHashMap<>();
        ADTUtil.collectLeaves(replacement)
               .forEach(x -> traces.put(x.getHypothesisState(), ADTUtil.buildTraceForNode(x)));

        final Pair<Word<I>, Word<O>> parentTrace = ADTUtil.buildTraceForNode(nodeToReplace);
        final Word<I> parentInput = parentTrace.getFirst();
        final Word<O> parentOutput = parentTrace.getSecond();

        ADTNode<ADTState<I, O>, I, O> result = null;

        // validate
        for (Map.Entry<ADTState<I, O>, Pair<Word<I>, Word<O>>> entry : traces.entrySet()) {
            final ADTState<I, O> state = entry.getKey();
            final Word<I> accessSequence = state.getAccessSequence();

            this.oracle.reset();
            accessSequence.forEach(this.oracle::query);
            parentInput.forEach(this.oracle::query);

            final Word<I> adsInput = entry.getValue().getFirst();
            final Word<O> adsOutput = entry.getValue().getSecond();

            final WordBuilder<I> inputWb = new WordBuilder<>(adsInput.size());
            final WordBuilder<O> outputWb = new WordBuilder<>(adsInput.size());

            final Iterator<I> inputIter = adsInput.iterator();
            final Iterator<O> outputIter = adsOutput.iterator();

            boolean equal = true;
            while (equal && inputIter.hasNext()) {
                final I in = inputIter.next();
                final O realOut = this.oracle.query(in);
                final O expectedOut = outputIter.next();

                inputWb.append(in);
                outputWb.append(realOut);

                if (!expectedOut.equals(realOut)) {
                    equal = false;
                }
            }

            final Word<I> traceInput = inputWb.toWord();
            final Word<O> traceOutput = outputWb.toWord();

            if (!equal) {
                this.openCounterExamples.add(new DefaultQuery<>(accessSequence.concat(parentInput, traceInput),
                                                                this.hypothesis.computeOutput(state.getAccessSequence())
                                                                               .concat(parentOutput, traceOutput)));
            }

            final ADTNode<ADTState<I, O>, I, O> trace = ADTUtil.buildADSFromObservation(traceInput, traceOutput, state);

            if (result == null) {
                result = trace;
            } else {
                if (!ADTUtil.mergeADS(result, trace)) {
                    this.resolveAmbiguities(nodeToReplace, result, state, cachedLeaves);
                }
            }
        }

        for (ADTState<I, O> s : cutout) {
            this.resolveAmbiguities(nodeToReplace, result, s, cachedLeaves);
        }

        return result;
    }

    /**
     * If two states show the same output behavior resolve this ambiguity by adding a reset node and add a new (sub) ADS
     * based on the lowest common ancestor in the existing ADT.
     *
     * @param nodeToReplace
     *         the old ADT (subtree) to be replaced
     * @param newADS
     *         the new ADT (subtree)
     * @param state
     *         the state which cannot be distinguished using the given replacement
     * @param cachedLeaves
     *         a set containing the leaves of the current tree, so they don't have to be re-fetched for every
     *         replacement verification
     */
    private void resolveAmbiguities(ADTNode<ADTState<I, O>, I, O> nodeToReplace,
                                    ADTNode<ADTState<I, O>, I, O> newADS,
                                    ADTState<I, O> state,
                                    Set<ADTNode<ADTState<I, O>, I, O>> cachedLeaves) {

        final Pair<Word<I>, Word<O>> parentTrace = ADTUtil.buildTraceForNode(nodeToReplace);
        final Word<I> parentInput = parentTrace.getFirst();
        final Word<I> effectiveAccessSequence = state.getAccessSequence().concat(parentInput);

        this.oracle.reset();
        effectiveAccessSequence.forEach(this.oracle::query);

        ADTNode<ADTState<I, O>, I, O> iter = newADS;
        while (!ADTUtil.isLeafNode(iter)) {

            if (ADTUtil.isResetNode(iter)) {
                this.oracle.reset();
                state.getAccessSequence().forEach(this.oracle::query);
                iter = iter.getChildren().values().iterator().next();
            } else {
                final O output = this.oracle.query(iter.getSymbol());
                final ADTNode<ADTState<I, O>, I, O> succ = iter.getChildren().get(output);

                if (succ == null) {
                    final ADTNode<ADTState<I, O>, I, O> newFinal = new ADTLeafNode<>(iter, state);
                    iter.getChildren().put(output, newFinal);
                    return;
                }

                iter = succ;
            }
        }

        ADTNode<ADTState<I, O>, I, O> oldReference = null, newReference = null;
        for (ADTNode<ADTState<I, O>, I, O> leaf : cachedLeaves) {
            final ADTState<I, O> hypState = leaf.getHypothesisState();
            assert hypState != null;

            if (hypState.equals(iter.getHypothesisState())) {
                oldReference = leaf;
            } else if (hypState.equals(state)) {
                newReference = leaf;
            }

            if (oldReference != null && newReference != null) {
                break;
            }
        }

        final LCAInfo<ADTState<I, O>, I, O> lcaResult = this.adt.findLCA(oldReference, newReference);
        final ADTNode<ADTState<I, O>, I, O> lca = lcaResult.adtNode;
        final Pair<Word<I>, Word<O>> lcaTrace = ADTUtil.buildTraceForNode(lca);

        final Word<I> sepWord = lcaTrace.getFirst().append(lca.getSymbol());
        final Word<O> oldOutputTrace = lcaTrace.getSecond().append(lcaResult.firstOutput);
        final Word<O> newOutputTrace = lcaTrace.getSecond().append(lcaResult.secondOutput);

        final ADTNode<ADTState<I, O>, I, O> oldTrace =
                ADTUtil.buildADSFromObservation(sepWord, oldOutputTrace, iter.getHypothesisState());
        final ADTNode<ADTState<I, O>, I, O> newTrace = ADTUtil.buildADSFromObservation(sepWord, newOutputTrace, state);

        if (!ADTUtil.mergeADS(oldTrace, newTrace)) {
            throw new IllegalStateException("Should never happen");
        }

        final ADTNode<ADTState<I, O>, I, O> reset = new ADTResetNode<>(oldTrace);
        final ADTNode<ADTState<I, O>, I, O> parent = iter.getParent();
        assert parent != null;
        final O parentOutput = ADTUtil.getOutputForSuccessor(parent, iter);

        parent.getChildren().put(parentOutput, reset);
        reset.setParent(parent);
        oldTrace.setParent(reset);
    }

    /**
     * Schedule all incoming transitions of the given states to be re-sifted against the given ADT (subtree).
     *
     * @param states
     *         A set of states, whose incoming transitions should be sifted
     * @param finalizedADS
     *         the ADT (subtree) to sift through
     */
    private void resiftAffectedTransitions(Set<ADTNode<ADTState<I, O>, I, O>> states,
                                           ADTNode<ADTState<I, O>, I, O> finalizedADS) {

        for (ADTNode<ADTState<I, O>, I, O> state : states) {

            final List<ADTTransition<I, O>> transitionsToRefine = state.getHypothesisState()
                                                                       .getIncomingTransitions()
                                                                       .stream()
                                                                       .filter(x -> !x.isSpanningTreeEdge())
                                                                       .collect(Collectors.toList());

            for (ADTTransition<I, O> trans : transitionsToRefine) {
                trans.setTarget(null);
                trans.setSiftNode(finalizedADS);
                this.openTransitions.add(trans);
            }
        }
    }

    public ADT<ADTState<I, O>, I, O> getADT() {
        return adt;
    }

    static final class BuilderDefaults {

        private BuilderDefaults() {
            // prevent instantiation
        }

        public static LeafSplitter leafSplitter() {
            return LeafSplitters.DEFAULT_SPLITTER;
        }

        public static ADTExtender adtExtender() {
            return ADTExtenders.EXTEND_BEST_EFFORT;
        }

        public static SubtreeReplacer subtreeReplacer() {
            return SubtreeReplacers.LEVELED_BEST_EFFORT;
        }

        public static boolean useObservationTree() {
            return true;
        }
    }
}
