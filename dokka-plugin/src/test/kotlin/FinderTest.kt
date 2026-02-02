/*
 * Copyright 2026 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.base.testApi.testRunner.BaseTestBuilder
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.withDescendants
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * - `...invoke...`: `operator fun invoke` functions
 * - `...named...`: other functions with same name as taget
 */
@Suppress("TestMethodWithoutAssertion")
class FinderTest: BaseAbstractTest() {
	//region Companion receiver

	@Test
	fun `allow invoke in companion object`() {
		testWithResource("InCompanion.kt") {
			documentablesTransformationStage = {
				allow(it, "InCompanion", "InCompanion.Companion", "InCompanion.Companion")
			}
		}
	}

	@Test
	fun `allow invoke extension on companion object`() {
		testWithResource("CompanionExtension.kt") {
			documentablesTransformationStage = {
				allow(it, "CompanionExtension", "CompanionExtension.Companion", null)
			}
		}
	}

	@Test
	fun `prohibit invoke extension in companion object`() {
		testWithResource("ExtensionInCompanion.kt") {
			documentablesTransformationStage = {
				prohibit(
					it,
					"ExtensionInCompanion",
					"ExtensionInCompanion.Companion",
					setOf(
						Validation.INVOKE_ON_CLASSLIKE,
						Validation.TARGET_NOT_NESTED,
						Validation.TARGET_NOT_PARENT_OF_COMPANION
					)
				)
			}
		}
	}

	@Test
	fun `prohibit invoke on non-companion`() {
		testWithResource("InvokeOnNonCompanion.kt") {
			documentablesTransformationStage = {
				prohibit(
					it,
					"InvokeOnNonCompanion",
					"InvokeOnNonCompanion.NestedClass",
					setOf(Validation.INVOKE_ON_CLASSLIKE)
				)
			}
		}
	}

	@Test
	fun `prohibit named extension in companion for nested class`() {
		testWithResource("ExtensionOnCompanionForNested.kt") {
			documentablesTransformationStage = {
				prohibit(
					it,
					"NestedClass",
					"ExtensionOnCompanionForNested.Companion",
					setOf(Validation.EXTENSION_IN_CLASSLIKE)
				)
			}
		}
	}

	@Test
	fun `prohibit invoke in companion with bad return type`() {
		testWithResource("BadReturnType.kt") {
			documentablesTransformationStage = {
				prohibit(it, "Something", "BadReturnType.Companion", setOf(Validation.TARGET_NOT_PARENT_OF_COMPANION))
			}
		}
	}

	@Test
	fun `allow named on companion for nested class`() {
		testWithResource("NestedTarget.kt") {
			documentablesTransformationStage = {
				allow(it, "NestedClass", "NestedTarget.Companion", "NestedTarget.Companion")
			}
		}
	}

	@Test
	fun `prohibit invoke on companion for nested class`() {
		testWithResource("InvokeOnCompanionForNested.kt") {
			documentablesTransformationStage = {
				prohibit(it, "NestedClass", "InvokeOnCompanionForNested.Companion", setOf(Validation.TARGET_NOT_PARENT_OF_COMPANION))
			}
		}
	}

	@Test
	fun `prohibit invoke on companion for inner class`() {
		testWithResource("InvokeOnCompanionForInner.kt") {
			documentablesTransformationStage = {
				prohibit(it, "Inner", "InvokeOnCompanionForInner.Companion",setOf(Validation.TARGET_NOT_PARENT_OF_COMPANION))
			}
		}
	}

	@Test
	fun `prohibit named on companion for inner class`() {
		testWithResource("NamedOnCompanionForInner.kt") {
			documentablesTransformationStage = {
				prohibit(it, "Inner", "NamedOnCompanionForInner.Companion", setOf(Validation.TARGET_IS_INNER))
			}
		}
	}

	//endregion

	//region Object receiver

	@Test
	fun `allow named for nested on object`() {
		testWithResource("NamedOnObject.kt") {
			documentablesTransformationStage = {
				allow(it, "NestedClass", "NamedOnObject", "NamedOnObject")
			}
		}
	}

	//endregion

	//region Class instance receiver

	@Test
	fun `prohibit invoke on parent for nested class`() {
		testWithResource("InvokeOnParentForNested.kt") {
			documentablesTransformationStage = {
				prohibit(it, "NestedClass", "InvokeInCompanionForInner", setOf(Validation.INVOKE_ON_CLASSLIKE))
			}
		}
	}

	@Test
	fun `allow named on parent for inner class`() {
		testWithResource("NamedOnParentForInner.kt") {
			documentablesTransformationStage = {
				allow(it, "InnerClass", "NamedOnParentForInner", "NamedOnParentForInner")
			}
		}
	}

	@Test
	fun `prohibit named extension on parent for inner class`() {
		testWithResource("NamedExtensionOnParentForInner.kt") {
			documentablesTransformationStage = {
				prohibit(
					it,
					"InnerClass",
					"NamedExtensionOnParentForInner",
					setOf(Validation.EXTENSION_IN_CLASSLIKE)
				)
			}
		}
	}

	//endregion

	//region No receiver

	@Test
	fun `allow named package-level function`() {
		testWithResource("TopLevelFunction.kt") {
			documentablesTransformationStage = {
				allow(it, "TopLevelFunction", null, null)
			}
		}
	}

	@Test
	fun `prohibit named package-level extension`() {
		testWithResource("TopLevelExtension.kt") {
			documentablesTransformationStage = {
				prohibit(it, "TopLevelExtension", null, setOf(Validation.TARGET_NOT_INNER, Validation.TARGET_NOT_NESTED))
			}
		}
	}

	//endregion

	//region Other

	@Test
	fun `prohibit invoke in self`() {
		testWithResource("InvokeInSelf.kt") {
			documentablesTransformationStage = {
				prohibit(it, "InvokeInSelf", "InvokeInSelf", setOf(Validation.INVOKE_ON_CLASSLIKE))
			}
		}
	}

	//endregion

	private fun allow(
		module: DModule,
		targetClass: String,
		receiverClass: String?,
		functionClass: String?,
		receiverPackage: String = "io.github.spacedvoid.constructorlike",
		functionPackage: String = "io.github.spacedvoid.constructorlike"
	) {
		val injected = module.getDClass(targetClass).extra[PseudoConstructors.Key]
		assertNotNull(injected)
		assertEquals(1, injected.constructors.size)
		val constructor = injected.constructors[0]
		assertEquals(functionPackage, constructor.constructor.dri.packageName)
		assertEquals(functionClass, constructor.constructor.dri.classNames)
		if(receiverClass != null) {
			val receiver = constructor.receiver
			assertNotNull(receiver)
			assertEquals(receiverPackage, receiver.packageName)
			assertEquals(receiverClass, receiver.classNames)
		}
		else assertNull(constructor.receiver)
		assertEquals(Validation.VALID, constructor.validation)
	}

	private fun prohibit(
		module: DModule,
		targetClass: String,
		functionClass: String?,
		possibleReasons: Set<Validation>,
		functionPackage: String = "io.github.spacedvoid.constructorlike"
	) {
		val constructors = module.getDClass(targetClass).extra[PseudoConstructors.Key]
		assertNotNull(constructors)
		assertTrue(constructors.constructors.isEmpty())
		val invalids = module.extra[InvalidPseudoConstructors.Key]
		assertNotNull(invalids)
		assertEquals(1, invalids.constructors.size)
		val invalid = invalids.constructors.single()
		assertEquals(functionPackage, invalid.first.dri.packageName)
		assertEquals(functionClass, invalid.first.dri.classNames)
		assertContains(possibleReasons, invalid.second)
	}

	private inline fun testWithResource(path: String, crossinline assertion: BaseTestBuilder.() -> Unit) {
		val configuration = dokkaConfiguration {
			sourceSets {
				sourceSet {
					sourceRoots = listOf(getTestDataDir(path).toString())
				}
			}
		}
		val plugin = ConstructorLikePlugin()
		testFromData(configuration, pluginOverrides = listOf(plugin)) {
			assertion()
		}
	}
}

private fun DModule.getDClass(className: String): DClass =
	withDescendants().filterIsInstance<DClass>()
		.singleOrNull { it.name == className }
		?: fail("Documentable has none or more than one classes named $className")
