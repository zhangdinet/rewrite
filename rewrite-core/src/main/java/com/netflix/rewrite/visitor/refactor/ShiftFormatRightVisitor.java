/*
 * Copyright 2020 the original authors.
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
package com.netflix.rewrite.visitor.refactor;

import com.netflix.rewrite.tree.Statement;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.IntStream.range;

public class ShiftFormatRightVisitor extends ScopedRefactorVisitor {
    private final String shift;

    public ShiftFormatRightVisitor(UUID scope, int shift, boolean isIndentedWithSpaces) {
        super(scope);
        this.shift = range(0, shift)
                .mapToObj(n -> isIndentedWithSpaces ? " " : "\t")
                .collect(Collectors.joining(""));
    }

    @Override
    public List<AstTransform> visitElse(Tr.If.Else elze) {
        return maybeTransform(elze,
                isInScope(elze) && isOnOwnLine(elze),
                super::visitElse,
                new ShiftRightMutation<>(shift));
    }

    @Override
    public List<AstTransform> visitStatement(Statement statement) {
        return maybeTransform(statement,
                isInScope(statement) && isOnOwnLine(statement),
                super::visitStatement,
                new ShiftRightMutation<>(shift)
        );
    }

    @Override
    public List<AstTransform> visitBlock(Tr.Block<Tree> block) {
        return maybeTransform(block,
                isInScope(block),
                super::visitBlock,
                b -> b.withEndOfBlockSuffix(b.getEndOfBlockSuffix() + shift)
        );
    }

    private boolean isOnOwnLine(Tree tree) {
        return tree.getFormatting().getPrefix().chars().takeWhile(c -> c == '\n' || c == '\r').count() > 0;
    }

    /**
     * Exists only for debugging purposes, where it is easier to see what kind of shifts are applying to each tree element
     */
    @RequiredArgsConstructor
    private static class ShiftRightMutation<T extends Tree> implements Function<T, T> {
        private final String shift;

        @Override
        public T apply(T tree) {
            return tree.withPrefix(tree.getFormatting().getPrefix() + shift);
        }

        @Override
        public String toString() {
            return "ShiftRightMutation{shift='" + shift.length() + "',indentChar='" + (shift.contains("\t") ? "tab" : "space") + "'}";
        }
    }
}
