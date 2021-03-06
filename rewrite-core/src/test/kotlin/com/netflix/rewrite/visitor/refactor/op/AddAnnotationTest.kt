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
package com.netflix.rewrite.visitor.refactor.op

import com.netflix.rewrite.Parser
import com.netflix.rewrite.assertRefactored
import org.junit.Test

class AddAnnotationTest : Parser() {
    @Test
    fun addAnnotationToClass() {
        val a = parse("""
            package a;
            
            public class UsersController {
                class Inner {
                }
            }
        """.trimIndent())

        val fixed = a.refactor()
                .visit(AddAnnotation(a.classes[0].id, "lombok.RequiredArgsConstructor"))
                .visit(AddAnnotation(a.classes[0].body.statements[0].id, "lombok.RequiredArgsConstructor"))
                .fix().fixed

        assertRefactored(fixed, """
            package a;
            
            import lombok.RequiredArgsConstructor;
            
            @RequiredArgsConstructor
            public class UsersController {
                @RequiredArgsConstructor
                class Inner {
                }
            }
        """)
    }

    @Test
    fun addAnnotationToField() {
        val a = parse("""
            package a;
            
            public class UsersController {
                private final UserService userService;
                NameService nameService;
            }
        """.trimIndent())

        val fixed = a.refactor()
                .visit(AddAnnotation(a.classes[0].fields[0].id, "javax.inject.Inject"))
                .visit(AddAnnotation(a.classes[0].fields[1].id, "javax.inject.Inject"))
                .fix().fixed

        assertRefactored(fixed, """
            package a;
            
            import javax.inject.Inject;
            
            public class UsersController {
                @Inject
                private final UserService userService;
            
                @Inject
                NameService nameService;
            }
        """)
    }

    @Test
    fun addAnnotationToMethod() {
        val a = parse("""
            package a;
            
            public class UsersController {
                UsersController() {
                }
            
                public void onInit() {
                }
    
                void onInit2() {
                }
                
                <T> T onInit3() {
                    return null;
                }
            }
        """.trimIndent())


        val fixed = a.classes[0].methods
                .fold(a.refactor()) { refactor, method -> refactor.visit(AddAnnotation(method.id, "javax.annotation.PostConstruct")) }
                .fix().fixed

        assertRefactored(fixed, """
            package a;
            
            import javax.annotation.PostConstruct;
            
            public class UsersController {
                @PostConstruct
                UsersController() {
                }
            
                @PostConstruct
                public void onInit() {
                }
            
                @PostConstruct
                void onInit2() {
                }
                
                @PostConstruct
                <T> T onInit3() {
                    return null;
                }
            }
        """)
    }
}
