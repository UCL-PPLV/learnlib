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
package de.learnlib.algorithm.procedural.adapter.dfa;

import java.util.List;

import de.learnlib.AccessSequenceTransformer;
import de.learnlib.algorithm.oml.ttt.dfa.OptimalTTTDFA;
import de.learnlib.algorithm.oml.ttt.pt.PTNode;
import de.learnlib.oracle.MembershipOracle;
import net.automatalib.alphabet.Alphabet;
import net.automatalib.word.Word;

/**
 * Adapter for using {@link OptimalTTTDFA} as a procedural learner.
 *
 * @param <I>
 *         input symbol type
 */
public class OptimalTTTAdapterDFA<I> extends OptimalTTTDFA<I> implements AccessSequenceTransformer<I> {

    public OptimalTTTAdapterDFA(Alphabet<I> alphabet, MembershipOracle<I, Boolean> oracle) {
        super(alphabet, oracle);
    }

    @Override
    public Word<I> transformAccessSequence(Word<I> word) {
        final List<PTNode<I, Boolean>> shortPrefixes = super.getState(word).getShortPrefixes();

        assert shortPrefixes.size() == 1;

        return shortPrefixes.get(0).word();
    }
}
