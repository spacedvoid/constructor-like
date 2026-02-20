/*
 * Copyright 2026 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.intellij.lang.annotations.Language
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.model.withDescendants
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * - `...invoke...`: `operator fun invoke` functions
 * - `...named...`: other functions with same name as taget
 */
@Suppress("TestMethodWithoutAssertion")
class FinderTest: BaseAbstractTest() {
	//region Basic: top level targets

	//region Companion receiver

	@Test
	fun `allow invoke in companion object`() {
		testWithCode("""
			class InCompanion {
				companion object {
					@ConstructorLike
					operator fun invoke(): InCompanion = TODO()
				}
			}
		""".trimIndent()) {
			allow()
		}
	}

	@Test
	fun `allow invoke extension on companion object`() {
		testWithCode("""
			class CompanionExtension {
				companion object
			}

			@ConstructorLike
			operator fun CompanionExtension.Companion.invoke(): CompanionExtension = TODO()
		""".trimIndent()) {
			allow()
		}
	}

	@Test
	fun `prohibit invoke extension in companion object`() {
		testWithCode("""
			class ExtensionInCompanion {
				companion object {
					@ConstructorLike
					operator fun ExtensionInCompanion.Companion.invoke(): ExtensionInCompanion = TODO()
				}
			}
		""".trimIndent()) {
			prohibit(
				setOf(
					Validation.INVOKE_ON_CLASSLIKE,
					Validation.TARGET_NOT_NESTED,
					Validation.TARGET_NOT_PARENT_OF_COMPANION,
					Validation.EXTENSION_IN_CLASSLIKE
				)
			)
		}
	}

	@Test
	fun `prohibit invoke in companion with bad return type`() {
		testWithCode("""
			class BadReturnType {
				companion object {
					@ConstructorLike
					operator fun invoke(): Any = TODO()
				}
			}
		""".trimIndent()) {
			prohibit(setOf(Validation.TARGET_NOT_PARENT_OF_COMPANION))
		}
	}

	//endregion

	//region No receiver

	@Test
	fun `allow named package-level function`() {
		testWithCode("""
			class TopLevelFunction

			@ConstructorLike
			fun TopLevelFunction(unused: Int): TopLevelFunction = TODO()
		""".trimIndent()) {
			allow()
		}
	}

	@Test
	fun `prohibit named package-level extension`() {
		testWithCode("""
			class TopLevelExtension

			@ConstructorLike
			fun Any.TopLevelExtension(): TopLevelExtension = TODO()
		""".trimIndent()) {
			prohibit(setOf(Validation.TARGET_NOT_INNER, Validation.TARGET_NOT_NESTED, Validation.RECEIVER_NOT_FOUND))
		}
	}

	//endregion

	//region Other

	@Test
	fun `prohibit invoke in self`() {
		testWithCode("""
			class InvokeInSelf {
				@ConstructorLike
				operator fun invoke(): InvokeInSelf = TODO()
			}
		""".trimIndent()) {
			prohibit(setOf(Validation.INVOKE_ON_CLASSLIKE))
		}
	}

	//endregion

	//endregion Basic

	//region Nested: targets in classlikes

	//region Nested class in class

	@Test
	fun `allow named on companion for nested class`() {
		testWithCode(
			"""
			class NestedTarget {
				companion object {
					@ConstructorLike
					fun NestedClass(): NestedClass = TODO()
				}

				class NestedClass
			}
		""".trimIndent()
		) {
			allow()
		}
	}

	@Test
	fun `prohibit invoke on non-companion`() {
		testWithCode("""
			class InvokeOnNonCompanion {
				class NestedClass {
					@ConstructorLike
					operator fun invoke(): InvokeOnNonCompanion = TODO()
				}
			}
		""".trimIndent()) {
			prohibit(setOf(Validation.INVOKE_ON_CLASSLIKE))
		}
	}

	@Test
	fun `prohibit named extension in companion for nested class`() {
		testWithCode("""
			class ExtensionOnCompanionForNested {
				companion object {
					@ConstructorLike
					fun ExtensionOnCompanionForNested.Companion.NestedClass(): NestedClass = TODO()
				}

				class NestedClass
			}
		""".trimIndent()) {
			prohibit(setOf(Validation.EXTENSION_IN_CLASSLIKE))
		}
	}

	@Test
	fun `prohibit invoke on companion for nested class`() {
		testWithCode("""
			class InvokeOnCompanionForNested {
				companion object {
					@ConstructorLike
					operator fun invoke(): NestedClass = TODO()
				}

				class NestedClass
			}
		""".trimIndent()) {
			prohibit(setOf(Validation.TARGET_NOT_PARENT_OF_COMPANION))
		}
	}

	@Test
	fun `prohibit invoke on parent for nested class`() {
		testWithCode("""
			class InvokeInCompanionForInner {
				class NestedClass

				@ConstructorLike
				operator fun invoke(): NestedClass = TODO()
			}
		""".trimIndent()) {
			prohibit(setOf(Validation.INVOKE_ON_CLASSLIKE))
		}
	}

	//endregion

	//region Inner class in class

	@Test
	fun `allow named on parent for inner class`() {
		testWithCode(
			"""
			class NamedOnParentForInner {
				inner class InnerClass

				@ConstructorLike
				fun InnerClass(): InnerClass = TODO()
			}
		""".trimIndent()
		) {
			allow()
		}
	}

	@Test
	fun `prohibit invoke on companion for inner class`() {
		testWithCode("""
			class InvokeOnCompanionForInner {
				companion object {
					@ConstructorLike
					operator fun invoke(): Inner = TODO()
				}

				inner class Inner
			}
		""".trimIndent()) {
			prohibit(setOf(Validation.TARGET_NOT_PARENT_OF_COMPANION))
		}
	}

	@Test
	fun `prohibit named on companion for inner class`() {
		testWithCode("""
			class NamedOnCompanionForInner {
				companion object {
					@ConstructorLike
					fun Inner(): Inner = TODO()
				}

				inner class Inner
			}
		""".trimIndent()) {
			prohibit(setOf(Validation.TARGET_IS_INNER))
		}
	}

	@Test
	fun `prohibit named extension on parent for inner class`() {
		testWithCode("""
			class NamedExtensionOnParentForInner {
				inner class InnerClass

				@ConstructorLike
				fun Any.InnerClass(): InnerClass = TODO()
			}
		""".trimIndent()) {
			prohibit(setOf(Validation.EXTENSION_IN_CLASSLIKE))
		}
	}

	//endregion

	//region Class in object

	@Test
	fun `allow named for nested on object`() {
		testWithCode("""
			object NamedOnObject {
				class NestedClass

				@ConstructorLike
				fun NestedClass(): NestedClass = TODO()
			}
		""".trimIndent()) {
			allow()
		}
	}

	@Test
	fun `allow invoke for class in object`() {
		testWithCode("""
			object InvokeForClassInObject {
				class InObject {
					companion object {
						@ConstructorLike
			            operator fun invoke(): InObject = TODO()
					}
				}
			}
		""".trimIndent()) {
			allow()
		}
	}

	//endregion

	//region Class in companion

	/*
	 * Not really used in practice, but these are still valid Kotlin.
	 * We'll only check for valid cases.
	 */

	@Test
	fun `named for class in companion`() {
		testWithCode("""
			class NamedForClassInCompanion {
				companion object {
					class InCompanion

					@ConstructorLike
					fun InCompanion(): InCompanion = TODO()
				}
			}
		""".trimIndent()) {
			allow()
		}
	}

	@Test
	fun `invoke for class in companion`() {
		testWithCode("""
			class InvokeForClassInCompanion {
				companion object {
					class InCompanion {
						companion object {
							@ConstructorLike
							operator fun invoke(): InCompanion = TODO()
						}
					}
				}
			}
		""".trimIndent()) {
			allow()
		}
	}

	//endregion

	//endregion

	private inline fun testWithCode(@Language("kotlin") code: String, crossinline assertions: DModule.() -> Unit) {
		val configuration = dokkaConfiguration {
			sourceSets {
				sourceSet {
					sourceRoots = listOf("src/main/kotlin")
				}
			}
		}
		testInline(codePrefix + code, configuration, pluginOverrides = listOf(ConstructorLikePlugin())) {
			documentablesTransformationStage = {
				it.assertions()
			}
		}
	}

	private val codePrefix = """
		/src/main/kotlin/__TestFile__.kt
		package io.github.spacedvoid.constructorlike
		
		@MustBeDocumented
		annotation class ConstructorLike
		
		
	""".trimIndent()

	private fun DModule.allow() {
		val constructors = withDescendants().filterIsInstance<DClasslike>()
			.mapNotNull {
				@Suppress("UNCHECKED_CAST")
				(it as WithExtraProperties<DClasslike>).extra[PseudoConstructors.Key]
			}
			.flatMap { it.constructors }
			.toList()
		assertEquals(1, constructors.size)
		assertEquals(Validation.VALID, constructors[0].validation)
	}

	private fun DModule.prohibit(possibleReasons: Set<Validation>) {
		val constructors = withDescendants().filterIsInstance<DClasslike>()
			.mapNotNull {
				@Suppress("UNCHECKED_CAST")
				(it as WithExtraProperties<DClasslike>).extra[PseudoConstructors.Key]
			}
			.flatMap { it.constructors }
		assertTrue(constructors.none())
		val invalids = extra[InvalidPseudoConstructors.Key]?.constructors
		assertNotNull(invalids)
		assertEquals(1, invalids.size)
		assertContains(possibleReasons, invalids[0].second)
	}
}
