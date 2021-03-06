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
package com.netflix.rewrite.visitor.refactor

import com.netflix.rewrite.assertRefactored
import com.netflix.rewrite.Parser
import com.netflix.rewrite.tree.Cursor
import com.netflix.rewrite.tree.Tr
import org.junit.Assert.assertNotNull
import org.junit.Test

class TransformVisitorTest : Parser() {
    @Test
    fun multipleTransformations() {
        val a = parse("""
            public class A {
                public void test() {
                }
            }
        """)

        val changeMethodName = { to: String ->
            object : RefactorVisitor() {
                override fun visitMethod(method: Tr.MethodDecl): MutableList<AstTransform> =
                        transform(method) { m: Tr.MethodDecl -> m.withName(m.name.withName(to)) }
            }
        }

        assertRefactored(a.refactor()
                .visit(changeMethodName("test2"))
                .visit(changeMethodName("test3"))
                .fix().fixed,
                """
                    public class A {
                        public void test3() {
                        }
                    }
                """.trimIndent())
    }

    @Test
    fun canAccessCursorFromInsideTransformation() {
        val a = parse("""
            public class A {
                public void test() {
                }
            }
        """)

        val accessCursor = object : RefactorVisitor() {
            override fun visitMethod(method: Tr.MethodDecl): MutableList<AstTransform> =
                    transform(method) { m: Tr.MethodDecl, c: Cursor? ->
                        assertNotNull(c)
                        m
                    }
        }

        a.refactor().visit(accessCursor).fix().fixed
    }
}
