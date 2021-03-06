/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.rewrite.tree

import com.netflix.rewrite.fields
import com.netflix.rewrite.Parser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

open class PrimitiveTest : Parser() {

    @Test
    fun primitiveField() {
        val a = parse("""
            public class A {
                int n = 0;
                char c = 'a';
            }
        """)

        assertThat(a.fields(0..1).map { it.typeExpr?.type })
                .containsExactlyInAnyOrder(Type.Primitive.Int, Type.Primitive.Char)
    }
}