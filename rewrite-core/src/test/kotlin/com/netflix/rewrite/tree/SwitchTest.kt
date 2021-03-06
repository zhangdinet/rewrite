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

open class SwitchTest : Parser() {
    
    @Test
    fun switch() {
        val a = parse("""
            public class A {
                int n;
                public void test() {
                    switch(n) {
                    case 0: break;
                    }
                }
            }
        """)
        
        val switch = a.firstMethodStatement() as Tr.Switch
        assertTrue(switch.selector.tree is Tr.Ident)
        assertEquals(1, switch.cases.statements.size)
        
        val case0 = switch.cases.statements[0]
        assertTrue(case0.pattern is Tr.Literal)
        assertTrue(case0.statements[0] is Tr.Break)
    }
    
    @Test
    fun switchWithDefault() {
        val a = parse("""
            public class A {
                int n;
                public void test() {
                    switch(n) {
                    default: System.out.println("default!");
                    }
                }
            }
        """)

        val switch = a.firstMethodStatement() as Tr.Switch
        assertTrue(switch.selector.tree is Tr.Ident)
        assertEquals(1, switch.cases.statements.size)

        val default = switch.cases.statements[0]
        assertEquals("default", (default.pattern as Tr.Ident).simpleName)
        assertTrue(default.statements[0] is Tr.MethodInvocation)
    }

    @Test
    fun format() {
        val a = parse("""
            public class A {
                int n;
                public void test() {
                    switch(n) {
                    default: break;
                    }
                }
            }
        """)

        val switch = a.firstMethodStatement() as Tr.Switch
        assertEquals("""
            switch(n) {
            default: break;
            }
        """.trimIndent(), switch.printTrimmed())
    }
    
    @Test
    fun switchWithNoCases() {
        val a = parse("""
            public class A {
                int n;
                public void test() {
                    switch(n) {}
                }
            }
        """)

        val switch = a.firstMethodStatement() as Tr.Switch
        assertTrue(switch.selector.tree is Tr.Ident)
        assertEquals(0, switch.cases.statements.size)
    }

    @Test
    fun multipleCases() {
        val aSrc = """
            public class A {
                int n;
                public void test() {
                    switch(n) {
                    case 0: {
                       break;
                    }
                    case 1: {
                       break;
                    }
                    }
                }
            }
        """.trimIndent()

        assertEquals(aSrc, parse(aSrc).printTrimmed())
    }
}