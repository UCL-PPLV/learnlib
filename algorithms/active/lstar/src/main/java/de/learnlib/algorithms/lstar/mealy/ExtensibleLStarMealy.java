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
package de.learnlib.algorithms.lstar.mealy;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.misberner.buildergen.annotations.GenerateBuilder;
import de.learnlib.algorithms.lstar.AbstractExtensibleAutomatonLStar;
import de.learnlib.algorithms.lstar.ce.ObservationTableCEXHandler;
import de.learnlib.algorithms.lstar.closing.ClosingStrategy;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.datastructure.observationtable.OTLearner.OTLearnerMealy;
import de.learnlib.datastructure.observationtable.ObservationTable;
import de.learnlib.datastructure.observationtable.Row;
import net.automatalib.automata.base.compact.CompactTransition;
import net.automatalib.automata.concepts.SuffixOutput;
import net.automatalib.automata.transducers.MealyMachine;
import net.automatalib.automata.transducers.impl.compact.CompactMealy;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;

public class ExtensibleLStarMealy<I, O>
        extends AbstractExtensibleAutomatonLStar<MealyMachine<?, I, ?, O>, I, Word<O>, Integer, CompactTransition<O>, Void, O, CompactMealy<I, O>>
        implements OTLearnerMealy<I, O> {

    private final Map<Word<I>, O> outputTable = new HashMap<>();

    public ExtensibleLStarMealy(Alphabet<I> alphabet,
                                MembershipOracle<I, Word<O>> oracle,
                                List<Word<I>> initialSuffixes,
                                ObservationTableCEXHandler<? super I, ? super Word<O>> cexHandler,
                                ClosingStrategy<? super I, ? super Word<O>> closingStrategy) {
        this(alphabet, oracle, Collections.singletonList(Word.epsilon()), initialSuffixes, cexHandler, closingStrategy);
    }

    @GenerateBuilder(defaults = AbstractExtensibleAutomatonLStar.BuilderDefaults.class)
    public ExtensibleLStarMealy(Alphabet<I> alphabet,
                                MembershipOracle<I, Word<O>> oracle,
                                List<Word<I>> initialPrefixes,
                                List<Word<I>> initialSuffixes,
                                ObservationTableCEXHandler<? super I, ? super Word<O>> cexHandler,
                                ClosingStrategy<? super I, ? super Word<O>> closingStrategy) {
        super(alphabet,
              oracle,
              new CompactMealy<>(alphabet),
              initialPrefixes,
              LStarMealyUtil.ensureSuffixCompliancy(initialSuffixes, alphabet, cexHandler.needsConsistencyCheck()),
              cexHandler,
              closingStrategy);
    }

    @Override
    public CompactMealy<I, O> getHypothesisModel() {
        return internalHyp;
    }

    @Override
    protected MealyMachine<?, I, ?, O> exposeInternalHypothesis() {
        return internalHyp;
    }

    @Override
    protected void updateInternalHypothesis() {
        updateOutputs();
        super.updateInternalHypothesis();
    }

    @Override
    protected Void stateProperty(ObservationTable<I, Word<O>> table, Row<I, Word<O>> stateRow) {
        return null;
    }

    @Override
    protected O transitionProperty(ObservationTable<I, Word<O>> table, Row<I, Word<O>> stateRow, int inputIdx) {
        Row<I, Word<O>> transRow = stateRow.getSuccessor(inputIdx);
        return outputTable.get(transRow.getLabel());
    }

    protected void updateOutputs() {
        List<DefaultQuery<I, Word<O>>> outputQueries = table.getAllRows().stream()
            .filter(row -> !outputTable.containsKey(row.getLabel()) && row.getLabel().getClass() != Word.epsilon().getClass())
            .map(row -> new DefaultQuery<I, Word<O>>(row.getLabel().prefix(row.getLabel().size() - 1), row.getLabel().suffix(1)))
            .collect(Collectors.toList());

        if (outputQueries.isEmpty()) {
            return;
        }

        oracle.processQueries(outputQueries);

        outputQueries.forEach(q -> outputTable.put(table.getRow(q.getInput()).getLabel(), q.getOutput().getSymbol(0)));
    }

    @Override
    protected SuffixOutput<I, Word<O>> hypothesisOutput() {
        return internalHyp;
    }

}
