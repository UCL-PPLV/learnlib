/* Copyright (C) 2013-2024 TU Dortmund University
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
package de.learnlib.oracle.equivalence.spmm;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.common.collect.Streams;
import de.learnlib.oracle.membership.SimulatorOracle;
import net.automatalib.alphabet.ProceduralInputAlphabet;
import net.automatalib.alphabet.ProceduralOutputAlphabet;
import net.automatalib.alphabet.impl.Alphabets;
import net.automatalib.alphabet.impl.DefaultProceduralInputAlphabet;
import net.automatalib.alphabet.impl.DefaultProceduralOutputAlphabet;
import net.automatalib.automaton.procedural.SPMM;
import net.automatalib.util.automaton.conformance.SPMMWMethodTestsIterator;
import net.automatalib.util.automaton.random.RandomAutomata;
import net.automatalib.word.Word;
import org.testng.Assert;
import org.testng.annotations.Test;

public class WMethodEQOracleTest {

    @Test
    public void testOracle() {
        final Random random = new Random(42);
        final ProceduralInputAlphabet<Character> inputAlphabet =
                new DefaultProceduralInputAlphabet<>(Alphabets.characters('x', 'z'),
                                                     Alphabets.characters('A', 'C'),
                                                     'R');
        final ProceduralOutputAlphabet<Character> outputAlphabet =
                new DefaultProceduralOutputAlphabet<>(Alphabets.characters('d', 'f'), '-');
        final SPMM<?, Character, ?, Character> spmm =
                RandomAutomata.randomSPMM(random, inputAlphabet, outputAlphabet, 4);
        final int lookahead = 2;

        final WMethodEQOracle<Character, Character> oracle =
                new WMethodEQOracle<>(new SimulatorOracle<>(spmm), lookahead);

        final List<Word<Character>> eqWords =
                oracle.generateTestWords(spmm, inputAlphabet).collect(Collectors.toList());
        final List<Word<Character>> testWords =
                Streams.stream(new SPMMWMethodTestsIterator<>(spmm, inputAlphabet, lookahead))
                       .collect(Collectors.toList());

        Assert.assertEquals(eqWords, testWords);
    }
}
