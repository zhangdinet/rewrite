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
package com.netflix.rewrite.visitor

import com.netflix.rewrite.Parser
import com.netflix.rewrite.visitor.RetrieveTreeVisitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RetrieveTreeVisitorTest : Parser() {

    @Test
    fun retrieveTreeById() {
        val a = parse("""
            public class A {
                public void test() {
                    String s;
                }
            }
        """)

        val s = a.classes[0].methods[0].body!!.statements[0]

        val sRetrieved = RetrieveTreeVisitor(s.id).visit(a)
        assertNotNull(sRetrieved)
        assertEquals(s, sRetrieved)
    }
}