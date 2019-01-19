/* Copyright (C) 2013-2019 TU Dortmund
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
package de.learnlib.algorithms.lstar.mealy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.algorithms.lstar.AbstractExtensibleAutomatonLStar;
import de.learnlib.algorithms.lstar.AutomatonLStarState;
import de.learnlib.algorithms.lstar.ce.ObservationTableCEXHandler;
import de.learnlib.algorithms.lstar.closing.ClosingStrategy;
import de.learnlib.api.oracle.StateLocalInputOracle.StateLocalInputMealyOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.datastructure.observationtable.PartialObservationTable;
import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.automata.concepts.SuffixOutput;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.automata.transducers.OutputAndLocalInputs;
import net.automatalib.automata.transducers.StateLocalInputMealyMachine;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.automata.transducers.impl.compact.CompactMealyTransition;
import net.automatalib.exception.GrowingAlphabetNotSupportedException;
import net.automatalib.util.automata.transducers.StateLocalInputMealyUtil;
import net.automatalib.words.GrowingAlphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.Alphabets;
import net.automatalib.words.impl.SimpleAlphabet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An L* implementation that uses a {@link StateLocalInputMealyOracle} to infer potentially partial {@link
 * MealyMachine}s, by analyzing the available input symbols for each state.
 * <p>
 * This may lead to a significant performance improvement compared to classic L* implementations, because transitions
 * that are not defined, will not be further explored, hence saving membership queries.
 *
 * @param <I>
 *         input symbol class
 * @param <O>
 *         output symbol class
 *
 * @author Maren Geske
 * @author frohme
 */
public class PartialLStarMealy<I, O>
        extends AbstractExtensibleAutomatonLStar<StateLocalInputMealyMachine<?, I, ?, O>, I, Word<OutputAndLocalInputs<I, O>>, Integer, CompactMealyTransition<O>, Void, O, CompactMealy<I, O>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartialLStarMealy.class);

    private final List<OutputAndLocalInputs<I, O>> outputTable = new ArrayList<>();
    private final GrowingAlphabet<I> alphabetAsGrowing;
    private final StateLocalInputMealyOracle<I, OutputAndLocalInputs<I, O>> oracle;

    public PartialLStarMealy(StateLocalInputMealyOracle<I, OutputAndLocalInputs<I, O>> oracle,
                             List<Word<I>> initialSuffixes,
                             ObservationTableCEXHandler<? super I, ? super Word<OutputAndLocalInputs<I, O>>> cexHandler,
                             ClosingStrategy<? super I, ? super Word<OutputAndLocalInputs<I, O>>> closingStrategy) {
        this(oracle, Collections.singletonList(Word.epsilon()), initialSuffixes, cexHandler, closingStrategy);
    }

    @GenerateBuilder(defaults = AbstractExtensibleAutomatonLStar.BuilderDefaults.class)
    public PartialLStarMealy(StateLocalInputMealyOracle<I, OutputAndLocalInputs<I, O>> oracle,
                             List<Word<I>> initialPrefixes,
                             List<Word<I>> initialSuffixes,
                             ObservationTableCEXHandler<? super I, ? super Word<OutputAndLocalInputs<I, O>>> cexHandler,
                             ClosingStrategy<? super I, ? super Word<OutputAndLocalInputs<I, O>>> closingStrategy) {
        this(new SimpleAlphabet<>(),
             oracle,
             new CompactMealy<>(new SimpleAlphabet<>()),
             initialPrefixes,
             initialSuffixes,
             cexHandler,
             closingStrategy);
    }

    private PartialLStarMealy(GrowingAlphabet<I> alphabet,
                              StateLocalInputMealyOracle<I, OutputAndLocalInputs<I, O>> oracle,
                              CompactMealy<I, O> internalHypothesis,
                              List<Word<I>> initialPrefixes,
                              List<Word<I>> initialSuffixes,
                              ObservationTableCEXHandler<? super I, ? super Word<OutputAndLocalInputs<I, O>>> cexHandler,
                              ClosingStrategy<? super I, ? super Word<OutputAndLocalInputs<I, O>>> closingStrategy) {
        super(alphabet,
              oracle,
              internalHypothesis,
              initialPrefixes,
              new ArrayList<>(initialSuffixes),
              cexHandler,
              closingStrategy);
        this.table = new PartialObservationTable<>(this::propagateNewAlphabetSymbol, oracle::definedInputs);
        this.oracle = oracle;
        this.alphabetAsGrowing = alphabet;
    }

    private void propagateNewAlphabetSymbol(I i) {
        this.alphabetAsGrowing.addSymbol(i);
        this.internalHyp.addAlphabetSymbol(i);
    }

    @Override
    public void startLearning() {
        final Collection<I> initialInputs = this.oracle.definedInputs(Word.epsilon());
        final List<Word<I>> newSuffixes = LStarMealyUtil.ensureSuffixCompliancy(initialSuffixes,
                                                                                Alphabets.fromCollection(initialInputs),
                                                                                cexHandler.needsConsistencyCheck());

        initialSuffixes.clear();
        initialSuffixes.addAll(newSuffixes);

        super.startLearning();
    }

    @Override
    protected StateLocalInputMealyMachine<?, I, ?, O> exposeInternalHypothesis() {
        return internalHyp;
    }

    @Override
    protected void updateInternalHypothesis() {
        updateOutputs();
        super.updateInternalHypothesis();
    }

    @Override
    protected Void stateProperty(ObservationTable<I, Word<OutputAndLocalInputs<I, O>>> table, Row<I> stateRow) {
        return null;
    }

    @Override
    protected O transitionProperty(ObservationTable<I, Word<OutputAndLocalInputs<I, O>>> table,
                                   Row<I> stateRow,
                                   int inputIdx) {
        Row<I> transRow = stateRow.getSuccessor(inputIdx);
        return outputTable.get(transRow.getRowId() - 1).getOutput();
    }

    protected void updateOutputs() {
        int numOutputs = outputTable.size();
        int numTransRows = table.numberOfRows() - 1;

        int newOutputs = numTransRows - numOutputs;
        if (newOutputs == 0) {
            return;
        }

        List<DefaultQuery<I, Word<OutputAndLocalInputs<I, O>>>> outputQueries = new ArrayList<>(numOutputs);

        for (int i = numOutputs + 1; i <= numTransRows; i++) {
            Row<I> row = table.getRow(i);
            Word<I> rowPrefix = row.getLabel();
            int prefixLen = rowPrefix.size();
            outputQueries.add(new DefaultQuery<>(rowPrefix.prefix(prefixLen - 1), rowPrefix.suffix(1)));
        }

        oracle.processQueries(outputQueries);

        for (int i = 0; i < newOutputs; i++) {
            DefaultQuery<I, Word<OutputAndLocalInputs<I, O>>> query = outputQueries.get(i);
            OutputAndLocalInputs<I, O> outSym = query.getOutput().getSymbol(0);

            outputTable.add(outSym);
        }
    }

    @Override
    protected SuffixOutput<I, Word<OutputAndLocalInputs<I, O>>> hypothesisOutput() {
        return StateLocalInputMealyUtil.partialToObservableOutput(internalHyp);
    }

    @Override
    public void addAlphabetSymbol(I symbol) throws GrowingAlphabetNotSupportedException {
        LOGGER.info("Adding new symbols to a system, which already exposes its available actions has no effect");
        LOGGER.info("Skipping ...");
    }

    @Override
    public void resume(AutomatonLStarState<I, Word<OutputAndLocalInputs<I, O>>, CompactMealy<I, O>, Integer> state) {
        super.resume(state);
        final PartialObservationTable<I, O> observationTable = (PartialObservationTable<I, O>) super.table;

        final Set<I> inputSymbols = new HashSet<>();

        for (Row<I> r : observationTable.getAllRows()) {
            inputSymbols.addAll(observationTable.cellContents(r, 0).getSymbol(0).getLocalInputs());
        }

        observationTable.setInputAlphabet(new SimpleAlphabet<>(inputSymbols));
        observationTable.setNewAlphabetNotifier(this::propagateNewAlphabetSymbol);
    }
}
