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
package de.learnlib.algorithm.rivestschapire;

import de.learnlib.oracle.MembershipOracle.MooreMembershipOracle;
import de.learnlib.testsupport.AbstractGrowingAlphabetMooreTest;
import net.automatalib.alphabet.Alphabet;

public class RivestSchapireMooreGrowingAlphabetTest
        extends AbstractGrowingAlphabetMooreTest<RivestSchapireMoore<Character, Character>> {

    @Override
    protected RivestSchapireMoore<Character, Character> getLearner(MooreMembershipOracle<Character, Character> oracle,
                                                                   Alphabet<Character> alphabet) {
        return new RivestSchapireMoore<>(alphabet, oracle);
    }
}
