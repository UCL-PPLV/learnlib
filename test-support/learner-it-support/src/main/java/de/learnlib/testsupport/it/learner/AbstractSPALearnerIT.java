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
package de.learnlib.testsupport.it.learner;

import java.util.ArrayList;
import java.util.List;

import de.learnlib.example.LearningExample.SPALearningExample;
import de.learnlib.example.LearningExamples;
import de.learnlib.oracle.MembershipOracle.DFAMembershipOracle;
import de.learnlib.oracle.equivalence.spa.SimulatorEQOracle;
import de.learnlib.oracle.membership.SPASimulatorOracle;
import de.learnlib.testsupport.it.learner.LearnerVariantList.SPALearnerVariantList;
import de.learnlib.testsupport.it.learner.LearnerVariantListImpl.SPALearnerVariantListImpl;
import net.automatalib.alphabet.ProceduralInputAlphabet;
import net.automatalib.automaton.procedural.SPA;
import org.testng.annotations.Factory;

/**
 * Abstract integration test for {@link SPA} learning algorithms.
 */
public abstract class AbstractSPALearnerIT {

    @Factory
    public Object[] createExampleITCases() {
        final List<SPALearningExample<?>> examples = LearningExamples.createSPAExamples();
        final List<AbstractLearnerVariantITCase<?, ?, ?>> result = new ArrayList<>(examples.size());

        for (SPALearningExample<?> example : examples) {
            result.addAll(createAllVariantsITCase(example));
        }

        return result.toArray();
    }

    private <I> List<SPALearnerITCase<I>> createAllVariantsITCase(SPALearningExample<I> example) {

        final ProceduralInputAlphabet<I> alphabet = example.getAlphabet();
        final DFAMembershipOracle<I> mqOracle = new SPASimulatorOracle<>(example.getReferenceAutomaton());
        final SPALearnerVariantListImpl<I> variants = new SPALearnerVariantListImpl<>();
        addLearnerVariants(alphabet, mqOracle, variants);

        return LearnerITUtil.createExampleITCases(example,
                                                  variants,
                                                  new SimulatorEQOracle<>(example.getReferenceAutomaton()));
    }

    /**
     * Adds, for a given setup, all the variants of the DFA learner to be tested to the specified
     * {@link LearnerVariantList variant list}.
     *
     * @param alphabet
     *         the input alphabet
     * @param mqOracle
     *         the membership oracle
     * @param variants
     *         list to add the learner variants to
     */
    protected abstract <I> void addLearnerVariants(ProceduralInputAlphabet<I> alphabet,
                                                   DFAMembershipOracle<I> mqOracle,
                                                   SPALearnerVariantList<I> variants);
}
