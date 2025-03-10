
/* Copyright (C) 2018
 * This file is part of the PhD research project entitled
 * 'Inferring models from Evolving Systems and Product Families'
 * developed by Carlos Diego Nascimento Damasceno at the
 * University of Sao Paulo (ICMC-USP).
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
package de.learnlib.algorithm.dlstar;

import de.learnlib.Resumable;
import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.datastructure.observationtable.Row;
import de.learnlib.oracle.MembershipOracle;
import de.learnlib.query.DefaultQuery;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.alphabet.SupportsGrowingAlphabet;
import net.automatalib.automaton.MutableDeterministic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for algorithms that produce (subclasses of)
 * {@link MutableDeterministic} automata.
 * <p>
 * This class provides an extended version of the L*-style hypothesis
 * construction named Dynamic L*. Implementing classes solely have to specify
 * how state and transition properties should be derived. The main difference of
 * it stands on the way that the Observation Table is handled at the
 * initialization.
 *
 * @param <A>  automaton type, must be a subclass of
 *             {@link MutableDeterministic}
 * @param <I>  input symbol type
 * @param <D>  output domain type
 * @param <SP> state property type
 * @param <TP> transition property type
 *
 * @author Carlos Diego Nascimento Damasceno (damascenodiego@usp.br)
 */
public abstract class AbstractAutomatonDLStar<A, I, D, S, T, SP, TP, AI extends MutableDeterministic<S, I, T, SP, TP> & SupportsGrowingAlphabet<I>>
        extends AbstractDLStar<A, I, D> implements Resumable<AutomatonDLStarState<I, D, AI, S>> {

    protected AI internalHyp;
    protected List<StateInfo<S, I>> stateInfos = new ArrayList<>();

    protected AbstractAutomatonDLStar(Alphabet<I> alphabet, MembershipOracle<I, D> oracle, AI internalHyp) {
        super(alphabet, oracle);
        this.internalHyp = internalHyp;
        internalHyp.clear();
    }

    @Override
    public A getHypothesisModel() {
        return exposeInternalHypothesis();
    }

    protected abstract A exposeInternalHypothesis();

    @Override
    public final void startLearning() {
        super.startLearning();
        updateInternalHypothesis();
    }

    protected void updateInternalHypothesis() {
        if (!table.isInitialized()) {
            throw new IllegalStateException("Cannot update internal hypothesis: not initialized");
        }

        int oldStates = internalHyp.size();
        int numDistinct = table.numberOfDistinctRows();

        int newStates = numDistinct - oldStates;

        stateInfos.addAll(Collections.nCopies(newStates, null));

        // TODO: Is there a quicker way than iterating over *all* rows?
        // FIRST PASS: Create new hypothesis states
        for (Row<I> sp : table.getShortPrefixRows()) {
            int id = sp.getRowContentId();
            StateInfo<S, I> info = stateInfos.get(id);
            if (info != null) {
                // State from previous hypothesis, property might have changed
                if (info.getRow() == sp) {
                    internalHyp.setStateProperty(info.getState(), stateProperty(table, sp));
                }
                continue;
            }

            S state = createState((id == 0), sp);

            stateInfos.set(id, new StateInfo<>(sp, state));
        }

        // SECOND PASS: Create hypothesis transitions
        for (StateInfo<S, I> info : stateInfos) {
            Row<I> sp = info.getRow();
            S state = info.getState();

            for (int i = 0; i < alphabet.size(); i++) {
                I input = alphabet.getSymbol(i);

                Row<I> succ = sp.getSuccessor(i);
                int succId = succ.getRowContentId();

                S succState = stateInfos.get(succId).getState();

                setTransition(state, input, succState, sp, i);
            }
        }
    }

    protected abstract SP stateProperty(ObservationTable<I, D> table, Row<I> stateRow);

    protected S createState(boolean initial, Row<I> row) {
        SP prop = stateProperty(table, row);
        if (initial) {
            return internalHyp.addInitialState(prop);
        }
        return internalHyp.addState(prop);
    }

    protected void setTransition(S from, I input, S to, Row<I> fromRow, int inputIdx) {
        TP prop = transitionProperty(table, fromRow, inputIdx);
        internalHyp.setTransition(from, input, to, prop);
    }

    protected abstract TP transitionProperty(ObservationTable<I, D> table, Row<I> stateRow, int inputIdx);

    @Override
    protected final void doRefineHypothesis(DefaultQuery<I, D> ceQuery) {
        refineHypothesisInternal(ceQuery);
        updateInternalHypothesis();
    }

    protected void refineHypothesisInternal(DefaultQuery<I, D> ceQuery) {
        super.doRefineHypothesis(ceQuery);
    }

    @Override
    public void addAlphabetSymbol(I symbol) {
        super.addAlphabetSymbol(symbol);

        this.internalHyp.addAlphabetSymbol(symbol);

        if (this.table.isInitialized()) {
            this.updateInternalHypothesis();
        }
    }

    @Override
    public AutomatonDLStarState<I, D, AI, S> suspend() {
        return new AutomatonDLStarState<>(table, internalHyp, stateInfos);
    }

    @Override
    public void resume(final AutomatonDLStarState<I, D, AI, S> state) {
        this.table = state.getObservationTable();
        this.table.setInputAlphabet(alphabet);
        this.internalHyp = state.getHypothesis();
        this.stateInfos = state.getStateInfos();
    }

    static final class StateInfo<S, I> implements Serializable {

        private final Row<I> row;
        private final S state;

        StateInfo(Row<I> row, S state) {
            this.row = row;
            this.state = state;
        }

        public Row<I> getRow() {
            return row;
        }

        public S getState() {
            return state;
        }

        // IDENTITY SEMANTICS!
    }
}
