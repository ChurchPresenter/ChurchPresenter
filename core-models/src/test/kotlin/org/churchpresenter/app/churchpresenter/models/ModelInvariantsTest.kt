package org.churchpresenter.app.churchpresenter.models

import java.io.File
import java.util.zip.ZipFile
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.churchpresenter.app.churchpresenter.models.scene.SceneSource
import org.churchpresenter.app.churchpresenter.models.shortcuts.KeyChord

/**
 * Every model in this module, checked for the properties a data class is relied on to have.
 *
 * The models are wide and repetitive — `ShapeSource` alone takes fifteen parameters, eight of them
 * `String` — and a mis-ordered constructor argument between two same-typed parameters compiles,
 * serializes and round-trips without complaint. Nothing else in the suite would notice. So each
 * class is built with a value **derived from its own parameter name**, and every property is read
 * back: swap two arguments and the values arrive in the wrong properties.
 *
 * Classes are discovered from the compiled output rather than listed, so a model added later is
 * covered without editing this file — and [every model is discovered] fails if the walk ever stops
 * finding them.
 */
class ModelInvariantsTest {

    /**
     * Every model class on the classpath: the data classes and enums of this package.
     *
     * The test classes share the package, so the filter is what a model *is* — a data class or an
     * enum — rather than where its file sits.
     */
    private fun modelClasses(): List<KClass<*>> =
        System.getProperty("java.class.path").split(File.pathSeparator)
            .flatMap { classNamesIn(File(it)) }
            .toSet()
            .mapNotNull { runCatching { Class.forName(it).kotlin }.getOrNull() }
            .filter { it.isData || it.java.isEnum }
            .filterNot { it.isAbstract || it.isSealed }
            .sortedBy { it.qualifiedName }

    private fun classNamesIn(root: File): List<String> = when {
        root.isDirectory -> directoryClassNames(root)
        root.isFile && root.extension == "jar" -> jarClassNames(root)
        else -> emptyList()
    }

    private fun directoryClassNames(root: File): List<String> {
        val dir = File(root, PACKAGE_PATH)
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map { it.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.') }
            .toList()
    }

    private fun jarClassNames(jar: File): List<String> =
        ZipFile(jar).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("$PACKAGE_PATH/") && it.endsWith(".class") }
                .map { it.removeSuffix(".class").replace('/', '.') }
                .toList()
        }

    /**
     * A value for [param] that is distinctive to its own name, so a swapped constructor argument
     * lands somewhere it can be seen. [seed] shifts every value so two instances differ in every
     * property at once.
     */
    private fun valueFor(param: KParameter, seed: Int): Any? {
        val name = param.name.orEmpty()
        val n = name.hashCode().toLong() and 0xFFFF
        return valueOfType(param.type, name, n, seed)
    }

    @Suppress("ReturnCount")
    private fun valueOfType(type: KType, name: String, n: Long, seed: Int): Any? {
        val cls = type.classifier as? KClass<*> ?: fail("no classifier for $name")
        return when {
            cls == String::class -> "$name-$seed"
            cls == Int::class -> (n + seed).toInt()
            cls == Long::class -> n + seed
            cls == Float::class -> (n + seed).toFloat()
            cls == Double::class -> (n + seed).toDouble()
            cls == Boolean::class -> (n + seed) % 2 == 0L
            cls == List::class -> {
                val arg = type.arguments.firstOrNull()?.type ?: return emptyList<Any>()
                listOf(valueOfType(arg, name, n, seed))
            }
            cls.java.isEnum -> {
                val constants = cls.java.enumConstants
                constants[((n + seed) % constants.size).toInt()]
            }
            else -> build(cls, seed)
        }
    }

    /**
     * An instance of [cls] with every constructor parameter given a value of its own.
     *
     * A sealed or abstract type stands in for one of its own subclasses — `Scene.sources` is a
     * `List<SceneSource>`, and `SceneSource` itself cannot be built.
     */
    private fun build(cls: KClass<*>, seed: Int): Any {
        val concrete = concreteOf(cls)
        val ctor = concrete.primaryConstructor ?: fail("${concrete.simpleName} has no primary constructor")
        ctor.isAccessible = true
        return runCatching {
            ctor.call(*ctor.parameters.map { valueFor(it, seed) }.toTypedArray())
        }.getOrElse { fail("could not build ${concrete.qualifiedName}: $it") }
    }

    private fun concreteOf(cls: KClass<*>): KClass<*> =
        if (!cls.isSealed && !cls.isAbstract) cls
        else cls.sealedSubclasses.firstOrNull()?.let { concreteOf(it) }
            ?: fail("${cls.simpleName} is abstract with no usable subclass")

    /** Reads every readable property, which is what covers the generated getters. */
    private fun readAll(instance: Any): Map<String, Any?> =
        instance::class.memberProperties.associate { prop ->
            prop.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            prop.name to (prop as kotlin.reflect.KProperty1<Any, *>).get(instance)
        }

    @Test
    fun `every constructor argument lands in the property of the same name`() {
        var checked = 0
        modelClasses().filter { it.primaryConstructor != null && !it.java.isEnum }.forEach { cls ->
            val ctor = cls.primaryConstructor ?: return@forEach
            if (ctor.parameters.isEmpty()) return@forEach
            val given = ctor.parameters.associate { it.name to valueFor(it, seed = 1) }
            val instance = ctor.also { it.isAccessible = true }
                .call(*ctor.parameters.map { given[it.name] }.toTypedArray())
            val read = readAll(instance)

            given.forEach { (name, expected) ->
                if (read.containsKey(name)) {
                    assertEquals(expected, read[name], "${cls.simpleName}.$name")
                    checked++
                }
            }
        }
        assertTrue(checked > 100, "only $checked properties checked — discovery is not finding the models")
    }

    @Test
    fun `every model compares and hashes by value`() {
        modelClasses().filter { it.primaryConstructor?.parameters?.isNotEmpty() == true && !it.java.isEnum }
            .forEach { cls ->
                val a = build(cls, seed = 1)
                val b = build(cls, seed = 1)
                val c = build(cls, seed = 2)

                assertEquals(a, b, "${cls.simpleName} does not compare by value")
                assertEquals(a.hashCode(), b.hashCode(), "${cls.simpleName} hashes two equal values apart")
                assertNotEquals(a, c, "${cls.simpleName} compares two different values equal")
                assertNotEquals(a, Any(), "${cls.simpleName} equals an unrelated type")
                assertTrue(a.toString().startsWith(cls.simpleName!!), "${cls.simpleName} toString is not its own")
            }
    }

    @Test
    fun `copy carries every property that was not replaced`() {
        modelClasses().filter { it.primaryConstructor?.parameters?.isNotEmpty() == true && !it.java.isEnum }
            .forEach { cls ->
                val original = build(cls, seed = 1)
                val copyFn = cls.members.firstOrNull { it.name == "copy" } ?: return@forEach
                copyFn.isAccessible = true

                // copy() with nothing replaced — the arguments come from the instance, not from the
                // constructor defaults, which is the property callers depend on.
                val copied = copyFn.callBy(mapOf(copyFn.parameters.first() to original))

                assertEquals(original, copied, "${cls.simpleName}.copy() did not reproduce the value")
                assertEquals(original.hashCode(), copied.hashCode(), "${cls.simpleName}.copy() hashed apart")
            }
    }

    @Test
    fun `every model destructures in constructor order`() {
        modelClasses().filter { it.primaryConstructor?.parameters?.isNotEmpty() == true && !it.java.isEnum }
            .forEach { cls ->
                val ctor = cls.primaryConstructor ?: return@forEach
                val instance = build(cls, seed = 1)
                val read = readAll(instance)

                ctor.parameters.forEachIndexed { index, param ->
                    val component = cls.members.firstOrNull { it.name == "component${index + 1}" }
                        ?: return@forEachIndexed
                    component.isAccessible = true
                    assertEquals(
                        read[param.name],
                        component.call(instance),
                        "${cls.simpleName}.component${index + 1} is not ${param.name}",
                    )
                }
            }
    }

    @Test
    fun `every model can be built from its own defaults`() {
        // Kotlin compiles a second, synthetic constructor for defaulted parameters, and it is the
        // one every `LyricSection()` call site in the app actually uses. Passing every argument —
        // which the tests above do — never reaches it.
        var built = 0
        modelClasses().filter { !it.java.isEnum }.forEach { cls ->
            val ctor = cls.primaryConstructor ?: return@forEach
            val required = ctor.parameters.filterNot { it.isOptional }
            if (required.size == ctor.parameters.size) return@forEach

            ctor.isAccessible = true
            val instance = ctor.callBy(required.associateWith { valueFor(it, seed = 1) })
            built++

            // Read them back: a default that throws or is wired to the wrong property is exactly
            // what this is here to catch.
            val read = readAll(instance)
            required.forEach { param ->
                if (read.containsKey(param.name)) {
                    assertEquals(valueFor(param, seed = 1), read[param.name], "${cls.simpleName}.${param.name}")
                }
            }
            assertEquals(instance, instance.let { it }, "${cls.simpleName} is not equal to itself")
        }
        assertTrue(built > 5, "only $built defaulted models built")
    }

    @Test
    fun `every enum resolves each of its own names`() {
        val enums = modelClasses().filter { it.java.isEnum }
        assertTrue(enums.size >= 3, "expected the model enums to be discovered, found ${enums.size}")

        enums.forEach { cls ->
            val constants = cls.java.enumConstants
            assertTrue(constants.isNotEmpty(), "${cls.simpleName} has no entries")
            constants.forEach { constant ->
                val name = (constant as Enum<*>).name
                val method = cls.java.getMethod("valueOf", String::class.java)
                assertEquals(constant, method.invoke(null, name), "${cls.simpleName}.$name did not resolve")
            }
        }
    }

    @Test
    fun `every model is discovered`() {
        val names = modelClasses().mapNotNull { it.simpleName }.toSet()

        // A spread of the shapes this walk has to keep finding: a top-level data class, a nested
        // one, an enum, and a serializable wire type. If the class-file walk breaks, the tests above
        // silently check nothing — this is what says so.
        listOf("KeyChord", "LyricSection", "SelectedVerse", "SongTuning", "Scene", "PathPoint")
            .forEach { assertTrue(it in names, "$it was not discovered") }
        assertTrue(names.size > 25, "only ${names.size} model classes discovered")
    }

    @Test
    fun `the sealed scene sources are all reachable as subtypes`() {
        val sources = modelClasses().filter {
            it.createType().isSubtypeOf(SceneSource::class.createType())
        }

        assertTrue(sources.size >= 10, "expected every SceneSource variant, found ${sources.size}")
        sources.forEach { cls ->
            val instance = build(cls, seed = 1) as SceneSource
            assertTrue(instance.id.isNotEmpty(), "${cls.simpleName}.id did not come through")
            assertTrue(instance.name.isNotEmpty(), "${cls.simpleName}.name did not come through")
        }
    }

    private companion object {
        /**
         * The models root, as a class-file path — the walk below is recursive, so every
         * subpackage (`schedule/`, `songs/`, `scene/`, …) is covered by it.
         *
         * Anchored on THIS class rather than on a model: this test sits at the models root by
         * design, while any given model belongs to a feature subpackage and can be moved between
         * them. It used to read `KeyChord::class.java.packageName`, which silently became
         * `…models.shortcuts` the day the models were grouped — the walk then started one level
         * too deep and found exactly one class. `every model is discovered` is what caught it.
         */
        val PACKAGE_PATH: String = ModelInvariantsTest::class.java.packageName.replace('.', '/')
    }
}
