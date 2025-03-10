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

import java.util.Collection;
import java.util.function.Supplier;

import de.learnlib.oracle.MembershipOracle;
import de.learnlib.oracle.parallelism.ThreadPool.PoolPolicy;
import de.learnlib.query.Query;

/**
 * A specialized {@link AbstractStaticBatchProcessorBuilder} for {@link MembershipOracle}s.
 *
 * @param <I>
 *         input symbol type
 * @param <D>
 *         output domain type
 */
public class StaticParallelOracleBuilder<I, D>
        extends AbstractStaticBatchProcessorBuilder<Query<I, D>, MembershipOracle<I, D>, StaticParallelOracle<I, D>> {

    public StaticParallelOracleBuilder(Collection<? extends MembershipOracle<I, D>> oracles) {
        super(oracles);
    }

    public StaticParallelOracleBuilder(Supplier<? extends MembershipOracle<I, D>> oracleSupplier) {
        super(oracleSupplier);
    }

    @Override
    protected StaticParallelOracle<I, D> buildOracle(Collection<? extends MembershipOracle<I, D>> oracleInstances,
                                                     int minBatchSize,
                                                     PoolPolicy poolPolicy) {
        return new StaticParallelOracle<>(oracleInstances, minBatchSize, poolPolicy);
    }
}
