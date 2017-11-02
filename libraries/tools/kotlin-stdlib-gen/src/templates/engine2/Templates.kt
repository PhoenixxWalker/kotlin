package templates2

import templates.*
import kotlin.coroutines.experimental.*
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf

@DslMarker
annotation class TemplateDsl

enum class Keyword(val value: String) {
    Function("fun"),
    Value("val"),
    Variable("var");
}

typealias MemberBuildAction = MemberBuilder.() -> Unit
typealias MemberBuildActionP<TParam> = MemberBuilder.(TParam) -> Unit

private fun def(signature: String, memberKind: Keyword): MemberBuildAction = {
    this.signature = signature
    this.keyword = memberKind
}

fun fn(defaultSignature: String): MemberBuildAction = def(defaultSignature, Keyword.Function)

fun fn(defaultSignature: String, setup: FamilyPrimitiveMemberDefinition.() -> Unit): FamilyPrimitiveMemberDefinition =
        FamilyPrimitiveMemberDefinition().apply {
            builder(fn(defaultSignature))
            setup()
        }

fun MemberBuildAction.byTwoPrimitives(setup: PairPrimitiveMemberDefinition.() -> Unit): PairPrimitiveMemberDefinition =
        PairPrimitiveMemberDefinition().apply {
            builder(this@byTwoPrimitives)
            setup()
        }

fun pval(name: String, setup: FamilyPrimitiveMemberDefinition.() -> Unit): FamilyPrimitiveMemberDefinition =
        FamilyPrimitiveMemberDefinition().apply {
            builder(def(name, Keyword.Value))
            setup()
        }

fun pvar(name: String, setup: FamilyPrimitiveMemberDefinition.() -> Unit): FamilyPrimitiveMemberDefinition =
        FamilyPrimitiveMemberDefinition().apply {
            builder(def(name, Keyword.Variable))
            setup()
        }


interface MemberTemplate {
    /** Specifies which platforms this member template should be generated for */
    fun platforms(vararg platforms: Platform)

    fun instantiate(platforms: List<Platform> = Platform.values): Sequence<MemberBuilder>

    /** Registers parameterless member builder function */
    fun builder(b: MemberBuildAction)
}

infix fun <MT: MemberTemplate> MT.builder(b: MemberBuildAction): MT = apply { builder(b) }
infix fun <TParam, MT : MemberTemplateDefinition<TParam>> MT.builderWith(b: MemberBuildActionP<TParam>): MT = apply { builderWith(b) }

abstract class MemberTemplateDefinition<TParam> : MemberTemplate {

    sealed class BuildAction {
        class Generic(val action: MemberBuildAction) : BuildAction() {
            operator fun invoke(builder: MemberBuilder) { action(builder) }
        }
        class Parametrized(val action: MemberBuildActionP<*>) : BuildAction() {
            @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "UNCHECKED_CAST")
            operator fun <TParam> invoke(builder: MemberBuilder, p: @kotlin.internal.NoInfer TParam) {
                (action as MemberBuildActionP<TParam>).invoke(builder, p)
            }
        }
    }

    private val buildActions = mutableListOf<BuildAction>()

    private var targetPlatforms = setOf(*Platform.values())
    override fun platforms(vararg platforms: Platform) {
        targetPlatforms = setOf(*platforms)
    }


    private var filterPredicate: ((Family, TParam) -> Boolean)? = null
    /** Sets the filter predicate that is applied to a produced sequence of variations. */
    fun filter(predicate: (Family, TParam) -> Boolean) {
        this.filterPredicate = predicate
    }

    override fun builder(b: MemberBuildAction) { buildActions += BuildAction.Generic(b) }
    /** Registers member builder function with the parameter(s) of this DSL */
    fun builderWith(b: MemberBuildActionP<TParam>) { buildActions += BuildAction.Parametrized(b) }



    /** Provides the sequence of member variation parameters */
    protected abstract fun parametrize(): Sequence<Pair<Family, TParam>>

    private fun Sequence<Pair<Family, TParam>>.applyFilter() =
            filterPredicate?.let { predicate ->
                filter { (family, p) -> predicate(family, p) }
            } ?: this


    override fun instantiate(platforms: List<Platform>): Sequence<MemberBuilder> {
        val resultingPlatforms = platforms.intersect(targetPlatforms)
        val specificPlatforms by lazy { resultingPlatforms - Platform.Common }

        fun platformMemberBuilders(family: Family, p: TParam) =
                if (Platform.Common in targetPlatforms) {
                    val commonMemberBuilder = createMemberBuilder(Platform.Common, family, p)
                    mutableListOf(commonMemberBuilder).also { builders ->
                        if (commonMemberBuilder.hasPlatformSpecializations) {
                            specificPlatforms.mapTo(builders) {
                                createMemberBuilder(it, family, p)
                            }
                        }
                    }
                } else {
                    resultingPlatforms.map { createMemberBuilder(it, family, p) }
                }

        return parametrize()
                .applyFilter()
                .map { (family, p) -> platformMemberBuilders(family, p) }
                .flatten()
    }

    private fun createMemberBuilder(platform: Platform, family: Family, p: TParam): MemberBuilder {
        return MemberBuilder(targetPlatforms, platform, family).also { builder ->
            for (action in buildActions) {
                when (action) {
                    is BuildAction.Generic -> action(builder)
                    is BuildAction.Parametrized -> action<TParam>(builder, p)
                }
            }
        }
    }

}


private fun defaultPrimitives(f: Family): Set<PrimitiveType> =
        if (f.isPrimitiveSpecialization) PrimitiveType.defaultPrimitives else emptySet()

@TemplateDsl
class FamilyPrimitiveMemberDefinition : MemberTemplateDefinition<PrimitiveType?>() {

    private val familyPrimitives = mutableMapOf<Family, Set<PrimitiveType?>>()

    fun include(vararg fs: Family) {
        for (f in fs) familyPrimitives[f] = defaultPrimitives(f)
    }
    @Deprecated("Use include()", ReplaceWith("include(*fs)"))
    fun only(vararg fs: Family) = include(*fs)

    fun include(fs: Collection<Family>) {
        for (f in fs) familyPrimitives[f] = defaultPrimitives(f)
    }

    fun includeDefault() {
        include(Family.defaultFamilies)
    }

    fun include(f: Family, primitives: Set<PrimitiveType?>) {
        familyPrimitives[f] = primitives
    }

    fun exclude(vararg ps: PrimitiveType) {
        val toExclude = ps.toSet()
        for (e in familyPrimitives) {
            e.setValue(e.value - toExclude)
        }
    }

    override fun parametrize(): Sequence<Pair<Family, PrimitiveType?>> = buildSequence {
        for ((family, primitives) in familyPrimitives) {
            if (primitives.isEmpty())
                yield(family to null)
            else
                yieldAll(primitives.map { family to it })
        }
    }

    init {
        builderWith { p -> primitive = p }
    }
}

@TemplateDsl
class PairPrimitiveMemberDefinition : MemberTemplateDefinition<Pair<PrimitiveType, PrimitiveType>>() {

    private val familyPrimitives = mutableMapOf<Family, Set<Pair<PrimitiveType, PrimitiveType>>>()

    fun include(f: Family, primitives: Collection<Pair<PrimitiveType, PrimitiveType>>) {
        familyPrimitives[f] = primitives.toSet()
    }

    override fun parametrize(): Sequence<Pair<Family, Pair<PrimitiveType, PrimitiveType>>> {
        return familyPrimitives
                .flatMap { e -> e.value.map { e.key to it } }
                .asSequence()
    }

    init {
        builderWith { (p1, p2) -> primitive = p1 }
    }
}

typealias TemplateGroup = () -> Sequence<MemberTemplate>

fun templateGroupOf(vararg templates: MemberTemplate): TemplateGroup = { templates.asSequence() }

abstract class TemplateGroupBase : TemplateGroup {

    override fun invoke(): Sequence<MemberTemplate> = buildSequence {
        with(this@TemplateGroupBase) {
            this::class.members.filter { it.name.startsWith("f_") }.forEach {
                require(it.parameters.size == 1) { "Member $it violates naming convention" }
                when {
                    it.returnType.isSubtypeOf(typeMemberTemplate) ->
                        yield(it.call(this) as MemberTemplate)
                    it.returnType.isSubtypeOf(typeIterableOfMemberTemplates) ->
                        @Suppress("UNCHECKED_CAST")
                        yieldAll(it.call(this) as Iterable<MemberTemplate>)
                    else ->
                        error("Member $it violates naming convention")
                }
            }
        }
    }.run {
        if (defaultActions.isEmpty()) this else onEach { t -> defaultActions.forEach(t::builder) }
    }

    private val defaultActions = mutableListOf<MemberBuildAction>()

    fun defaultBuilder(builderAction: MemberBuildAction) {
        defaultActions += builderAction
    }

    companion object {
        private val typeMemberTemplate = MemberTemplate::class.createType()
        private val typeIterableOfMemberTemplates = Iterable::class.createType(arguments = listOf(KTypeProjection.invariant(typeMemberTemplate)))
    }

}

/*
Replacement pattern:
    templates add f\(\"(\w+)(\(.*)
    val f_$1 = fn("$1$2
*/


/*
val t_copyOfResized = MemberTemplatePar<DefaultParametrization>().apply {
    parametrization = buildSequence<DefaultParametrization> {
        val allPlatforms = setOf(*Platform.values())
        yield(DefaultParametrization(InvariantArraysOfObjects, platforms = allPlatforms))
        yieldAll(PrimitiveType.defaultPrimitives.map { DefaultParametrization(ArraysOfPrimitives, it, platforms = allPlatforms) })
    }
    builder = { p, platform, builder ->
        builder.family = if (p.family == InvariantArraysOfObjects && platform == Platform.JS)
            ArraysOfObjects else p.family

        if (platform == Platform.JVM)
            builder.inline = Inline.Only

        builder.doc = "Returns new array which is a copy of the original array."
        builder.returns = "SELF"
        if (platform == Platform.JS && p.family == ArraysOfObjects)
            builder.returns = "Array<T>"

        if (platform == Platform.JVM) {
            builder.body = "return java.util.Arrays.copyOf(this, size)"
        } else if (platform == Platform.JS) {
            when (p.primitive) {
                null ->
                    builder.body = "return this.asDynamic().slice()"
                PrimitiveType.Char, PrimitiveType.Boolean, PrimitiveType.Long ->
                    builder.body = "return withType(\"${p.primitive}Array\", this.asDynamic().slice())"
                else -> {
                    builder.annotations += """@Suppress("NOTHING_TO_INLINE")"""
                    builder.inline = Inline.Yes
                    builder.body = "return this.asDynamic().slice()"
                }
            }
        }


    }
}
interface MemberTemplate {

    fun instantiate(): Sequence<MemberInstance>
}

class MemberTemplatePar<TParametrization : Parametrization> : MemberTemplate {

    val keyword: String = "fun"

    lateinit var parametrization: Sequence<TParametrization>
    lateinit var builder: (TParametrization, Platform, MemberBuilder) -> Unit

    override fun instantiate(): Sequence<MemberInstance> =
            parametrization.flatMap {
                it.platforms.asSequence().map { p ->
                    val memberBuilder = MemberBuilder().apply {
                        builder(it, p, this)
                    }
                    MemberInstance(memberBuilder::build, PlatformSourceFile(p, memberBuilder.sourceFile))
                }
            }

}
*/



/*

class MemberInstance(
        val textBuilder: (Appendable) -> Unit,
        val platformSourceFile: PlatformSourceFile)
*/

