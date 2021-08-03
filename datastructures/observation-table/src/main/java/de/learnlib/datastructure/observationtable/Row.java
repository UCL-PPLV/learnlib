/* Copyright (C) 2013-2021 TU Dortmund
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
package de.learnlib.datastructure.observationtable;

import net.automatalib.words.Word;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A row in an observation table. Minimally, a row consists of a prefix (the row label) and a unique identifier in its
 * observation table which remains constant throughout the whole process.
 * <p>
 * Apart from that, a row is also associated with contents (via an integer id). The prefix of a row may be either a
 * short or long prefix. In the former case, the row will also have successor rows (one-step futures) associated with
 * it.
 *
 * @param <I>
 *         input symbol type
 *
 * @param <D>
 *         domain symbol type
 *
 * @author Malte Isberner
 */
public interface Row<I, D> {

    /**
     * Retrieves the unique row identifier associated with this row.
     *
     * @return the row identifier
     *
     * @see ObservationTable#numberOfRows()
     */
    int getRowId();

    /**
     * Retrieves the RowContent associated with this row (may be {@code null} if this row has not
     * yet been initialized).
     *
     * @return the row content identifier
     */
    @Nullable RowContent<I, D> getRowContent();

    /**
     * Retrieves the label of this row.
     *
     * @return the label of this row
     */
    Word<I> getLabel();

    /**
     * Retrieves whether this row is a short or a long prefix row.
     *
     * @return {@code true} if this row is a short prefix row, {@code false} otherwise.
     */
    boolean isShortPrefixRow();

    /**
     * Retrieves the successor row for this short label row and the given alphabet symbol (by index). If this is no
     * short label row, an exception might occur.
     *
     * @param pos
     *         the index of the alphabet symbol.
     *
     * @return the successor row (may be <code>null</code>)
     */
    Row<I, D> getSuccessor(int pos);
}
