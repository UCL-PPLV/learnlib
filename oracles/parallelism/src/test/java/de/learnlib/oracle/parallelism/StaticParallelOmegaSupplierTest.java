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
package de.learnlib.oracle.parallelism;

import de.learnlib.oracle.parallelism.AbstractDynamicBatchProcessorBuilder.StaticOracleProvider;
import de.learnlib.oracle.parallelism.AbstractStaticParallelOmegaOracleTest.TestOutput;

public class StaticParallelOmegaSupplierTest extends AbstractStaticParallelOmegaOracleTest<TestOutput> {

    @Override
    protected StaticParallelOmegaOracleBuilder<?, Integer, TestOutput> getBuilder() {
        TestMembershipOracle[] oracles = getOracles();
        return ParallelOracleBuilders.newStaticParallelOmegaOracle(new StaticOracleProvider<>(oracles));
    }

    @Override
    protected TestOutput extractTestOutput(TestOutput output) {
        return output;
    }
}
