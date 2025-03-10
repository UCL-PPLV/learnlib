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
package de.learnlib.statistic;

import de.learnlib.algorithm.LearningAlgorithm;
import net.automatalib.automaton.fsa.DFA;
import net.automatalib.automaton.transducer.MealyMachine;
import net.automatalib.automaton.transducer.MooreMachine;
import net.automatalib.word.Word;

/**
 * Common interface for learners keeping statistics.
 *
 * @param <M>
 *         the automaton type
 * @param <I>
 *         input symbol class
 * @param <D>
 *         output symbol class
 */
public interface StatisticLearner<M, I, D> extends LearningAlgorithm<M, I, D>, StatisticCollector {

    interface DFAStatisticLearner<I> extends StatisticLearner<DFA<?, I>, I, Boolean> {}

    interface MealyStatisticLearner<I, O> extends StatisticLearner<MealyMachine<?, I, ?, O>, I, Word<O>> {}

    interface MooreStatisticLearner<I, O> extends StatisticLearner<MooreMachine<?, I, ?, O>, I, Word<O>> {}
}
