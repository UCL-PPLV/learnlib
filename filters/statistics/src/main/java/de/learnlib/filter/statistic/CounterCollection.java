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
package de.learnlib.filter.statistic;

import java.util.Collection;
import java.util.StringJoiner;
import java.util.function.Function;

import de.learnlib.statistic.StatisticData;

/**
 * A collection of counters.
 */
public class CounterCollection implements StatisticData {

    private final Collection<Counter> counters;

    public CounterCollection(Collection<Counter> counters) {
        this.counters = counters;
    }

    @Override
    public String getName() {
        return collect(Counter::getName);
    }

    @Override
    public String getUnit() {
        return collect(Counter::getUnit);
    }

    @Override
    public String getSummary() {
        return collect(Counter::getSummary);
    }

    @Override
    public String getDetails() {
        return collect(Counter::getDetails);
    }

    private String collect(Function<Counter, String> extractor) {
        final StringJoiner sj = new StringJoiner("\n");

        for (Counter c : counters) {
            sj.add(extractor.apply(c));
        }

        return sj.toString();
    }
}
