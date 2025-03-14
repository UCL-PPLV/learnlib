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
package de.learnlib.datastructure.discriminationtree;

import java.util.HashMap;
import java.util.Map;

import de.learnlib.datastructure.discriminationtree.model.AbstractWordBasedDTNode;
import de.learnlib.datastructure.discriminationtree.model.BooleanMap;

/**
 * Binary discrimination tree node specialization.
 *
 * @param <I>
 *         input symbol type
 * @param <D>
 *         node data type
 */
public class BinaryDTNode<I, D> extends AbstractWordBasedDTNode<I, Boolean, D> {

    public BinaryDTNode(AbstractWordBasedDTNode<I, Boolean, D> node) {
        super(node.getParent(), node.getParentOutcome(), node.getData());
        this.depth = node.getDepth();
        this.discriminator = node.getDiscriminator();
        this.children = null;
        if (node.getChildEntries() != null) {
            this.children = new HashMap<>();
            for (Map.Entry<Boolean, AbstractWordBasedDTNode<I, Boolean, D>> pair : node.getChildEntries()) {
                children.put(pair.getKey(), new BinaryDTNode<>(pair.getValue()));
                children.get(pair.getKey()).setParent(this);
            }
        }
    }

    public BinaryDTNode(D data) {
        super(data);
    }

    public BinaryDTNode(BinaryDTNode<I, D> parent, Boolean parentOutcome, D data) {
        super(parent, parentOutcome, data);
    }

    @Override
    protected Map<Boolean, AbstractWordBasedDTNode<I, Boolean, D>> createChildMap() {
        return new BooleanMap<>();
    }

    @Override
    protected BinaryDTNode<I, D> createChild(Boolean outcome, D data) {
        return new BinaryDTNode<>(this, outcome, data);
    }
}
