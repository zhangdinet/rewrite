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

import com.netflix.rewrite.firstMethodStatement
import com.netflix.rewrite.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

open class SynchronizedTest : Parser() {

    val a: Tr.CompilationUnit by lazy {
        parse("""
            public class A {
                Integer n = 0;
                public void test() {
                    synchronized(n) {
                    }
                }
            }
        """)
    }

    private val sync by lazy { a.firstMethodStatement() as Tr.Synchronized }

    @Test
    fun synchronized() {
        assertTrue(sync.lock.tree is Tr.Ident)
    }

    @Test
    fun format() {
        assertEquals("synchronized(n) {\n}", sync.printTrimmed())
    }
}