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
package de.learnlib.algorithm.ttt;

import de.learnlib.acex.analyzer.AcexAnalyzers;
import de.learnlib.algorithm.ttt.dfa.TTTLearnerDFA;
import de.learnlib.api.oracle.MembershipOracle;
import de.learnlib.testsupport.AbstractGrowingAlphabetDFATest;
import net.automatalib.words.Alphabet;

public class TTTDFAGrowingAlphabetTest extends AbstractGrowingAlphabetDFATest<TTTLearnerDFA<Character>> {

    @Override
    protected TTTLearnerDFA<Character> getLearner(MembershipOracle<Character, Boolean> oracle,
                                                  Alphabet<Character> alphabet) {
        return new TTTLearnerDFA<>(alphabet, oracle, AcexAnalyzers.LINEAR_FWD);
    }

}
