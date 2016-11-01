package com.netflix.java.refactor.ast

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.java.refactor.ast.visitor.AstVisitor
import com.netflix.java.refactor.ast.visitor.FormatVisitor
import com.netflix.java.refactor.ast.visitor.PrintVisitor
import com.netflix.java.refactor.ast.visitor.TransformVisitor
import com.netflix.java.refactor.diff.JavaSourceDiff
import com.netflix.java.refactor.parse.SourceFile
import com.netflix.java.refactor.refactor.RefactorVisitor
import com.netflix.java.refactor.refactor.op.AddField
import com.netflix.java.refactor.refactor.op.AddImport
import com.netflix.java.refactor.search.*
import java.io.Serializable
import java.lang.IllegalStateException
import java.util.*
import java.util.regex.Pattern

interface Tree {
    var formatting: Formatting

    fun <R> accept(v: AstVisitor<R>): R = v.default(null)
    fun format(): Tree = throw NotImplementedError()
    fun printTrimmed() = print().trimIndent().trim()
    fun print() = PrintVisitor().visit(this)
}

interface Statement : Tree

interface Expression : Tree {
    val type: Type?
}

/**
 * A tree representing a simple or fully qualified name
 */
interface NameTree : Tree {
    val type: Type?
}

/**
 * A tree identifying a type (e.g. a simple or fully qualified class name, a primitive, array, or parameterized type)
 */
interface TypeTree: NameTree

/**
 * The stylistic surroundings of a tree element
 */
sealed class Formatting {

    /**
     * Formatting should be inferred and reified from surrounding context
     */
    object Infer : Formatting()

    data class Reified(var prefix: String, var suffix: String = "") : Formatting() {
        companion object {
            val Empty = Reified("")
        }
    }

    object None : Formatting()
}

sealed class Tr : Serializable, Tree {

    data class Annotation(var annotationType: NameTree,
                          var args: Arguments?,
                          override val type: Type?,
                          override var formatting: Formatting) : Expression, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitAnnotation(this)

        data class Arguments(val args: List<Expression>, override var formatting: Formatting): Tr()
    }

    data class ArrayAccess(val indexed: Expression,
                           val dimension: Dimension,
                           override val type: Type?,
                           override var formatting: Formatting) : Expression, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitArrayAccess(this)

        data class Dimension(val index: Expression, override var formatting: Formatting): Tr()
    }

    data class ArrayType(val elementType: TypeTree,
                         val dimensions: List<Dimension>,
                         override var formatting: Formatting): TypeTree, Tr() {

        @JsonIgnore
        override val type = elementType.type

        override fun <R> accept(v: AstVisitor<R>): R = v.visitArrayType(this)

        data class Dimension(val inner: Empty, override var formatting: Formatting): Tr()
    }

    data class Assign(val variable: NameTree,
                      val assignment: Expression,
                      override val type: Type?,
                      override var formatting: Formatting) : Expression, Statement, Tr() {
        override fun <R> accept(v: AstVisitor<R>): R = v.visitAssign(this)
    }

    data class AssignOp(val variable: Expression,
                        val operator: Operator,
                        val assignment: Expression,
                        override val type: Type?,
                        override var formatting: Formatting) : Expression, Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitAssignOp(this)

        sealed class Operator: Tr() {
            // Arithmetic
            data class Addition(override var formatting: Formatting) : Operator()
            data class Subtraction(override var formatting: Formatting) : Operator()
            data class Multiplication(override var formatting: Formatting) : Operator()
            data class Division(override var formatting: Formatting) : Operator()
            data class Modulo(override var formatting: Formatting) : Operator()

            // Bitwise
            data class BitAnd(override var formatting: Formatting) : Operator()
            data class BitOr(override var formatting: Formatting) : Operator()
            data class BitXor(override var formatting: Formatting) : Operator()
            data class LeftShift(override var formatting: Formatting) : Operator()
            data class RightShift(override var formatting: Formatting) : Operator()
            data class UnsignedRightShift(override var formatting: Formatting) : Operator()
        }
    }

    data class Binary(val left: Expression,
                      val operator: Operator,
                      val right: Expression,
                      override val type: Type?,
                      override var formatting: Formatting) : Expression, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitBinary(this)

        sealed class Operator: Tr() {
            // Arithmetic
            data class Addition(override var formatting: Formatting) : Operator()
            data class Subtraction(override var formatting: Formatting) : Operator()
            data class Multiplication(override var formatting: Formatting) : Operator()
            data class Division(override var formatting: Formatting) : Operator()
            data class Modulo(override var formatting: Formatting) : Operator()

            // Relational
            data class LessThan(override var formatting: Formatting) : Operator()
            data class GreaterThan(override var formatting: Formatting) : Operator()
            data class LessThanOrEqual(override var formatting: Formatting) : Operator()
            data class GreaterThanOrEqual(override var formatting: Formatting) : Operator()
            data class Equal(override var formatting: Formatting) : Operator()
            data class NotEqual(override var formatting: Formatting) : Operator()

            // Bitwise
            data class BitAnd(override var formatting: Formatting) : Operator()
            data class BitOr(override var formatting: Formatting) : Operator()
            data class BitXor(override var formatting: Formatting) : Operator()
            data class LeftShift(override var formatting: Formatting) : Operator()
            data class RightShift(override var formatting: Formatting) : Operator()
            data class UnsignedRightShift(override var formatting: Formatting) : Operator()

            // Boolean
            data class Or(override var formatting: Formatting) : Operator()
            data class And(override var formatting: Formatting) : Operator()
        }
    }

    data class Block<out T: Tree>(val static: Tr.Empty?,
                                  val statements: List<T>,
                                  override var formatting: Formatting,
                                  val endOfBlockSuffix: String) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitBlock(this)
    }

    data class Break(val label: Ident?,
                     override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitBreak(this)
    }

    data class Case(val pattern: Expression?, // null for the default case
                    val statements: List<Statement>,
                    override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitCase(this)
    }

    data class Catch(val param: Parentheses<VariableDecls>,
                     val body: Block<Statement>,
                     override var formatting: Formatting) : Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitCatch(this)
    }

    data class ClassDecl(val annotations: List<Annotation>,
                         val modifiers: List<Modifier>,
                         val kind: Kind,
                         val name: Ident,
                         val typeParams: TypeParameters?,
                         val extends: Tree?,
                         val implements: List<Tree>,
                         val body: Block<Tree>,
                         val type: Type?,
                         override var formatting: Formatting) : Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitClassDecl(this)

        /**
         * Values will always occur before any fields, constructors, or methods
         */
        fun enumValues(): List<EnumValue> = body.statements.filterIsInstance<EnumValue>()

        fun fields(): List<VariableDecls> = body.statements.filterIsInstance<VariableDecls>()
        fun methods(): List<MethodDecl> = body.statements.filterIsInstance<MethodDecl>()

        sealed class Modifier : Tr() {
            data class Public(override var formatting: Formatting): Modifier()
            data class Protected(override var formatting: Formatting): Modifier()
            data class Private(override var formatting: Formatting): Modifier()
            data class Abstract(override var formatting: Formatting): Modifier()
            data class Static(override var formatting: Formatting): Modifier()
            data class Final(override var formatting: Formatting): Modifier()
        }

        sealed class Kind: Tr() {
            data class Class(override var formatting: Formatting): Kind()
            data class Enum(override var formatting: Formatting): Kind()
            data class Interface(override var formatting: Formatting): Kind()
            data class Annotation(override var formatting: Formatting): Kind()
        }

        /**
         * Find fields defined on this class, but do not include inherited fields up the type hierarchy
         */
        fun findFields(clazz: Class<*>): List<Tr.VariableDecls> = FindFields(clazz.name).visit(this)

        fun findFields(clazz: String): List<Tr.VariableDecls> = FindFields(clazz).visit(this)

        /**
         * Find fields defined up the type hierarchy
         */
        fun findInheritedFields(clazz: Class<*>): List<Type.Var> = FindInheritedFields(clazz.name).visit(this)

        fun findInheritedFields(clazz: String): List<Type.Var> = FindInheritedFields(clazz).visit(this)

        fun findMethodCalls(signature: String): List<Tr.MethodInvocation> = FindMethods(signature).visit(this)

        fun hasType(clazz: Class<*>): Boolean = HasType(clazz.name).visit(this)
        fun hasType(clazz: String): Boolean = HasType(clazz).visit(this)

        fun refactor(): RefactorTransaction = RefactorTransaction(this)

        class RefactorTransaction(val clazz: Tr.ClassDecl) {
            private val ops = ArrayList<RefactorVisitor>()

            fun addField(clazz: Class<*>, name: String, init: String?) = addField(clazz.name, name, init)

            fun addField(clazz: Class<*>, name: String) = addField(clazz.name, name, null)

            fun addField(clazz: String, name: String) = addField(clazz, name, null)

            fun addField(clazz: String, name: String, init: String?): RefactorTransaction {
                ops.add(AddField(clazz, name, init))
                return this
            }

            fun fix() {
                TODO()
            }
        }
    }

    data class CompilationUnit(val source: SourceFile,
                               val packageDecl: Package?,
                               val imports: List<Import>,
                               val typeDecls: List<ClassDecl>,
                               val cacheId: UUID,
                               override var formatting: Formatting) : Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitCompilationUnit(this)

        fun hasImport(clazz: Class<*>): Boolean = HasImport(clazz.name).visit(this)
        fun hasImport(clazz: String): Boolean = HasImport(clazz).visit(this)

        fun refactor() = RefactorTransaction(this)

        class RefactorTransaction(val clazz: Tr.CompilationUnit) {
            private val ops = ArrayList<RefactorVisitor>()

            fun addImport(clazz: Class<*>, staticMethod: String? = null) = addImport(clazz.name, staticMethod)

            fun addImport(clazz: String, staticMethod: String? = null): RefactorTransaction {
                ops.add(AddImport(clazz, staticMethod))
                return this
            }

            fun fix(): CompilationUnit {
                val fixed = TransformVisitor(ops.flatMap { it.visit(clazz) }).visit(clazz)
                FormatVisitor().visit(fixed)
                return fixed as CompilationUnit
            }
        }

        fun diff(body: Tr.CompilationUnit.() -> Unit): String {
            val diff = JavaSourceDiff(this)
            this.body()
            return diff.gitStylePatch()
        }

        fun beginDiff() = JavaSourceDiff(this)
    }

    data class Continue(val label: Ident?,
                        override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitContinue(this)
    }

    data class DoWhileLoop(val body: Statement,
                           val condition: Parentheses<Expression>,
                           override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitDoWhileLoop(this)
    }

    data class Empty(override var formatting: Formatting) : Statement, Expression, TypeTree, NameTree, Tr() {
        override val type: Type? = null
        override fun <R> accept(v: AstVisitor<R>): R = v.visitEmpty(this)
    }

    data class EnumValue(val name: Ident,
                         val initializer: Arguments?,
                         override var formatting: Formatting): Statement, Tr() {
        override fun <R> accept(v: AstVisitor<R>): R = v.visitEnumValue(this)

        data class Arguments(val args: List<Expression>, override var formatting: Formatting): Tr()
    }

    data class FieldAccess(val target: Expression,
                           val name: Ident,
                           override val type: Type?,
                           override var formatting: Formatting) : Expression, NameTree, TypeTree, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitFieldAccess(this)
    }

    data class ForEachLoop(val control: Control,
                           val body: Statement,
                           override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitForEachLoop(this)

        data class Control(val variable: VariableDecls,
                           val iterable: Expression,
                           override var formatting: Formatting): Tr()
    }

    data class ForLoop(val control: Control,
                       val body: Statement,
                       override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitForLoop(this)

        data class Control(val init: Statement, // either Tr.Empty or Tr.VariableDecls
                           val condition: Expression,
                           val update: List<Statement>,
                           override var formatting: Formatting): Tr()
    }

    data class Ident(val name: String,
                     override val type: Type?,
                     override var formatting: Formatting) : Expression, NameTree, TypeTree, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitIdentifier(this)
    }

    data class If(val ifCondition: Parentheses<Expression>,
                  val thenPart: Statement,
                  val elsePart: Else?,
                  override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitIf(this)

        data class Else(val statement: Statement, override var formatting: Formatting): Tr()
    }

    data class Import(val qualid: FieldAccess,
                      val static: Boolean,
                      override var formatting: Formatting) : Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitImport(this)

        fun matches(clazz: String): Boolean = when (qualid.name.name) {
            "*" -> qualid.target.printTrimmed() == clazz.split('.').takeWhile { it[0].isLowerCase() }.joinToString(".")
            else -> qualid.printTrimmed() == clazz
        }
    }

    data class InstanceOf(val expr: Expression,
                          val clazz: Tree,
                          override val type: Type?,
                          override var formatting: Formatting) : Expression, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitInstanceOf(this)
    }

    data class Label(val label: Ident,
                     val statement: Statement,
                     override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitLabel(this)
    }

    data class Lambda(val params: List<VariableDecls>,
                      val arrow: Arrow,
                      val body: Tree,
                      override val type: Type?,
                      override var formatting: Formatting) : Expression, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitLambda(this)

        data class Arrow(override var formatting: Formatting): Tr()
    }

    data class Literal(val typeTag: Type.Tag,
                       val value: Any?,
                       // see casing specification at http://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.10
                       val upperCaseSuffix: Boolean,
                       override val type: Type?,
                       override var formatting: Formatting) : Expression, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitLiteral(this)

        /**
         * Primitive values sometimes contain a prefix and suffix that hold the special characters,
         * e.g. the "" around String, the L at the end of a long, etc.
         */
        fun <T> transformValue(transform: (T) -> Any): String {
            val valueMatcher = "(.*)${Pattern.quote(value.toString())}(.*)".toRegex().find(this.printTrimmed().replace("\\", ""))
            @Suppress("UNREACHABLE_CODE")
            return when (valueMatcher) {
                is MatchResult -> {
                    val (prefix, suffix) = valueMatcher.groupValues.drop(1)
                    @Suppress("UNCHECKED_CAST")
                    return "$prefix${transform(value as T)}$suffix"
                }
                else -> {
                    throw IllegalStateException("Encountered a literal `$this` that could not be transformed")
                }
            }
        }
    }

    data class MethodDecl(val annotations: List<Annotation>,
                          val modifiers: List<Modifier>,
                          val typeParameters: TypeParameters?,
                          val returnTypeExpr: TypeTree?, // null for constructors
                          val name: Ident,
                          val params: Parameters,
                          val throws: Throws?,
                          val body: Block<Statement>?,
                          val defaultValue: Expression?,
                          override var formatting: Formatting) : Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitMethod(this)

        sealed class Modifier: Tr() {
            data class Default(override var formatting: Formatting): Modifier()
            data class Public(override var formatting: Formatting) : Modifier()
            data class Protected(override var formatting: Formatting) : Modifier()
            data class Private(override var formatting: Formatting) : Modifier()
            data class Abstract(override var formatting: Formatting) : Modifier()
            data class Static(override var formatting: Formatting) : Modifier()
            data class Final(override var formatting: Formatting) : Modifier()
        }

        data class Parameters(val params: List<Statement>, override var formatting: Formatting): Tr()
        data class Throws(val exceptions: List<NameTree>, override var formatting: Formatting): Tr()
    }

    data class MethodInvocation(val select: Expression?,
                                val typeParameters: TypeParameters?,
                                val name: Ident,
                                val args: Arguments,
                                val genericSignature: Type.Method?,
                                // in the case of generic signature parts, this concretizes
                                // them relative to the call site
                                val resolvedSignature: Type.Method?,
                                val declaringType: Type.Class?,
                                override var formatting: Formatting) : Expression, Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitMethodInvocation(this)

        override val type = resolvedSignature?.returnType

        fun returnType(): Type? = resolvedSignature?.returnType

        fun firstMethodInChain(): MethodInvocation =
            if(select is MethodInvocation)
                select.firstMethodInChain()
            else this

        fun argExpressions() = args.args.filter { it !is Tr.Empty }

        data class Arguments(val args: List<Expression>, override var formatting: Formatting): Tr()
        data class TypeParameters(val params: List<NameTree>, override var formatting: Formatting): Tr()
    }

    data class MultiCatch(val alternatives: List<NameTree>, override var formatting: Formatting): TypeTree, Tr() {
        override val type: Type by lazy { throw IllegalArgumentException("Multi-catch does not represent a single type") }

        override fun <R> accept(v: AstVisitor<R>): R = v.visitMultiCatch(this)
    }

    data class NewArray(val typeExpr: TypeTree?, // null in the case of an array as an annotation parameter
                        val dimensions: List<Dimension>,
                        val initializer: Initializer?,
                        override val type: Type?,
                        override var formatting: Formatting) : Expression, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitNewArray(this)

        data class Dimension(val size: Expression, override var formatting: Formatting): Tr()
        data class Initializer(val elements: List<Expression>, override var formatting: Formatting): Tr()
    }

    data class NewClass(val clazz: TypeTree,
                        val args: Arguments,
                        val classBody: Block<Tree>?, // non-null for anonymous classes
                        override val type: Type?,
                        override var formatting: Formatting) : Expression, Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitNewClass(this)

        data class Arguments(val args: List<Expression>, override var formatting: Formatting): Tr()
    }

    data class Package(val expr: Expression,
                       override var formatting: Formatting) : Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitPackage(this)
    }

    data class ParameterizedType(val clazz: NameTree,
                                 val typeArguments: TypeArguments?,
                                 override var formatting: Formatting): TypeTree, Tr() {

        @JsonIgnore
        override val type = clazz.type

        override fun <R> accept(v: AstVisitor<R>): R = v.visitParameterizedType(this)

        data class TypeArguments(val args: List<NameTree>,
                                 override var formatting: Formatting): Tr()
    }

    data class Parentheses<out T: Tree>(val tree: T,
                                        override var formatting: Formatting) : Expression, Tr() {

        override val type = when(tree) {
            is Expression -> tree.type
            else -> null
        }

        override fun <R> accept(v: AstVisitor<R>): R = v.visitParentheses(this)
    }

    data class Primitive(val typeTag: Type.Tag,
                         override val type: Type?,
                         override var formatting: Formatting) : Expression, TypeTree, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitPrimitive(this)
    }

    data class Return(val expr: Expression?,
                      override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitReturn(this)
    }

    data class Switch(val selector: Parentheses<Expression>,
                      val cases: Block<Case>,
                      override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitSwitch(this)
    }

    data class Synchronized(val lock: Parentheses<Expression>,
                            val body: Block<Statement>,
                            override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitSynchronized(this)
    }

    data class Ternary(val condition: Expression,
                       val truePart: Expression,
                       val falsePart: Expression,
                       override val type: Type?,
                       override var formatting: Formatting) : Expression, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitTernary(this)
    }

    data class Throw(val exception: Expression,
                     override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitThrow(this)
    }

    data class Try(val resources: Resources?,
                   val body: Block<Statement>,
                   val catches: List<Catch>,
                   val finally: Finally?,
                   override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitTry(this)

        data class Resources(val decls: List<VariableDecls>, override var formatting: Formatting): Tr()
        data class Finally(val block: Block<Statement>, override var formatting: Formatting): Tr()
    }

    data class TypeCast(val clazz: Parentheses<TypeTree>,
                        val expr: Expression,
                        override var formatting: Formatting): Expression, Tr() {

        override val type = clazz.type

        override fun <R> accept(v: AstVisitor<R>): R = v.visitTypeCast(this)
    }

    data class TypeParameter(val annotations: List<Annotation>,
                             val name: NameTree,
                             val bounds: List<Expression>,
                             override var formatting: Formatting) : Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitTypeParameter(this)
    }

    data class TypeParameters(val params: List<TypeParameter>, override var formatting: Formatting): Tr() {
        override fun <R> accept(v: AstVisitor<R>): R = v.visitTypeParameters(this)
    }

    /**
     * Increment and decrement operations are valid statements, other operations are not
     */
    data class Unary(val operator: Operator,
                     val expr: Expression,
                     override val type: Type?,
                     override var formatting: Formatting) : Expression, Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitUnary(this)

        sealed class Operator: Tr() {
            // Arithmetic
            data class PreIncrement(override var formatting: Formatting): Operator()
            data class PreDecrement(override var formatting: Formatting): Operator()
            data class PostIncrement(override var formatting: Formatting): Operator()
            data class PostDecrement(override var formatting: Formatting): Operator()
            data class Positive(override var formatting: Formatting): Operator()
            data class Negative(override var formatting: Formatting): Operator()

            // Bitwise
            data class Complement(override var formatting: Formatting): Operator()

            // Boolean
            data class Not(override var formatting: Formatting): Operator()
        }
    }

    data class UnparsedSource(val source: String, override var formatting: Formatting): Expression, Statement, Tr() {
        override val type: Type? = null
        override fun <R> accept(v: AstVisitor<R>): R = v.visitUnparsedSource(this)
    }

    data class VariableDecls(
            val annotations: List<Annotation>,
            val modifiers: List<Modifier>,
            val typeExpr: TypeTree,
            val varArgs: Varargs?,
            val dimensionsBeforeName: List<Dimension>,
            val vars: List<NamedVar>,
            override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitMultiVariable(this)

        sealed class Modifier: Tr() {
            data class Public(override var formatting: Formatting): Modifier()
            data class Protected(override var formatting: Formatting): Modifier()
            data class Private(override var formatting: Formatting): Modifier()
            data class Abstract(override var formatting: Formatting): Modifier()
            data class Static(override var formatting: Formatting): Modifier()
            data class Final(override var formatting: Formatting): Modifier()
            data class Transient(override var formatting: Formatting): Modifier()
            data class Volatile(override var formatting: Formatting): Modifier()
        }

        data class Varargs(override var formatting: Formatting): Tr()
        data class Dimension(val whitespace: Tr.Empty, override var formatting: Formatting): Tr()

        data class NamedVar(val name: Ident,
                            val dimensionsAfterName: List<Dimension>, // thanks for making it hard, Java
                            val initializer: Expression?,
                            val type: Type?,
                            override var formatting: Formatting): Tr() {
            override fun <R> accept(v: AstVisitor<R>): R = v.visitVariable(this)
        }
    }

    data class WhileLoop(val condition: Parentheses<Expression>,
                         val body: Statement,
                         override var formatting: Formatting) : Statement, Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitWhileLoop(this)
    }

    data class Wildcard(val bound: Bound?,
                        val boundedType: NameTree?,
                        override var formatting: Formatting): Tr() {

        override fun <R> accept(v: AstVisitor<R>): R = v.visitWildcard(this)

        sealed class Bound: Tr() {
            data class Super(override var formatting: Formatting): Bound()
            data class Extends(override var formatting: Formatting): Bound()
        }
    }
}
