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
package com.netflix.rewrite.visitor.refactor.op

import com.netflix.rewrite.assertRefactored
import com.netflix.rewrite.Parser
import org.junit.Test

open class DeleteStatementTest : Parser() {

    @Test
    fun deleteField() {
        val a = parse("""
            import java.util.List;
            public class A {
               List collection = null;
            }
        """.trimIndent())

        val fixed = a.refactor()
            .deleteStatement(a.classes[0].findFields("java.util.List"))
            .fix().fixed

        assertRefactored(fixed, """
            public class A {
            }
        """)
    }
}
