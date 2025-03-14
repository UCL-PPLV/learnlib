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

/**
 * A queries job that maintains a fixed reference to a {@link BatchProcessor}, executes queries using this oracle
 * regardless of the executing thread.
 *
 * @param <Q>
 *         query type
 */
final class StaticQueriesJob<Q> extends AbstractQueriesJob<Q> {

    private final BatchProcessor<Q> oracle;

    StaticQueriesJob(Collection<? extends Q> queries, BatchProcessor<Q> oracle) {
        super(queries);
        this.oracle = oracle;
    }

    @Override
    protected BatchProcessor<Q> getOracle() {
        return oracle;
    }

}
