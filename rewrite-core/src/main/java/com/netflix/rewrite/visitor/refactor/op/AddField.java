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
package com.netflix.rewrite.visitor.refactor.op;

import com.netflix.rewrite.internal.lang.Nullable;
import com.netflix.rewrite.tree.Formatting;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.ScopedRefactorVisitor;
import org.slf4j.helpers.MessageFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class AddField extends ScopedRefactorVisitor {
    private final List<Tr.Modifier> modifiers;
    private final String clazz;
    private final String name;

    @Nullable
    private final String init;

    public AddField(UUID scope, List<Tr.Modifier> modifiers, String clazz, String name, @Nullable String init) {
        super(scope);
        this.modifiers = modifiers;
        this.clazz = clazz;
        this.name = name;
        this.init = init;
    }

    @Override
    public String getRuleName() {
        return MessageFormatter.arrayFormat("core.AddField{classType={},name={}}",
                new String[]{clazz, name}).toString();
    }

    @Override
    public List<AstTransform> visitClassDecl(Tr.ClassDecl classDecl) {
        return maybeTransform(classDecl,
                classDecl.getId().equals(scope) && classDecl.getBody().getStatements()
                        .stream()
                        .filter(s -> s instanceof Tr.VariableDecls)
                        .map(Tr.VariableDecls.class::cast)
                        .noneMatch(mv -> mv.getVars().stream().anyMatch(var -> var.getSimpleName().equals(name))),
                super::visitClassDecl,
                Tr.ClassDecl::getBody,
                block -> {
                    var classType = Type.Class.build(clazz);
                    var newField = new Tr.VariableDecls(randomId(),
                            emptyList(),
                            modifiers,
                            Tr.Ident.build(randomId(), classType.getClassName(), classType, Formatting.EMPTY),
                            null,
                            emptyList(),
                            singletonList(new Tr.VariableDecls.NamedVar(randomId(),
                                    Tr.Ident.build(randomId(), name, null, Formatting.format("", init == null ? "" : " ")),
                                    emptyList(),
                                    init == null ? null : new Tr.UnparsedSource(randomId(), init, Formatting.format(" ")),
                                    classType,
                                    Formatting.format(" ")
                            )),
                            formatter().format(block)
                    );

                    List<Tree> statements = new ArrayList<>(block.getStatements().size() + 1);
                    statements.add(newField);
                    statements.addAll(block.getStatements());
                    return block.withStatements(statements);
                }
        );
    }
}
