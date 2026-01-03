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
	//region Companion helper

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
				prohibit(it, "ExtensionInCompanion", "ExtensionInCompanion.Companion", Validation.EXTENSION_IN_CLASSLIKE)
			}
		}
	}

	@Test
	fun `prohibit invoke on non-companion`() {
		testWithResource("InvokeOnNonCompanion.kt") {
			documentablesTransformationStage = {
				prohibit(it, "InvokeOnNonCompanion", "InvokeOnNonCompanion.NestedClass", Validation.INVOKE_ON_CLASSLIKE)
			}
		}
	}

	@Test
	fun `prohibit invoke extension in companion for nested class`() {
		testWithResource("ExtensionOnCompanionForNested.kt") {
			documentablesTransformationStage = {
				prohibit(it, "NestedClass", "ExtensionOnCompanionForNested.Companion", Validation.EXTENSION_IN_CLASSLIKE)
			}
		}
	}

	@Test
	fun `prohibit invoke in companion with bad return type`() {
		testWithResource("BadReturnType.kt") {
			documentablesTransformationStage = {
				prohibit(it, "Something", "BadReturnType.Companion", Validation.TARGET_NOT_PARENT_OF_COMPANION)
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
				prohibit(it, "NestedClass", "InvokeOnCompanionForNested.Companion", Validation.TARGET_NOT_PARENT_OF_COMPANION)
			}
		}
	}

	@Test
	fun `prohibit invoke on companion for inner class`() {
		testWithResource("InvokeOnCompanionForInner.kt") {
			documentablesTransformationStage = {
				prohibit(it, "Inner", "InvokeOnCompanionForInner.Companion", Validation.TARGET_NOT_PARENT_OF_COMPANION)
			}
		}
	}

	@Test
	fun `prohibit named on companion for inner class`() {
		testWithResource("NamedOnCompanionForInner.kt") {
			documentablesTransformationStage = {
				prohibit(it, "Inner", "NamedOnCompanionForInner.Companion", Validation.TARGET_IS_INNER)
			}
		}
	}

	//endregion

	//region Object helper

	@Test
	fun `allow named on object`() {
		testWithResource("NamedOnObject.kt") {
			documentablesTransformationStage = {
				allow(it, "NestedClass", "NamedOnObject", "NamedOnObject")
			}
		}
	}

	//endregion

	//region Class instance helper

	@Test
	fun `prohibit invoke on parent for nested class`() {
		testWithResource("InvokeOnParentForNested.kt") {
			documentablesTransformationStage = {
				prohibit(it, "NestedClass", "InvokeInCompanionForInner", Validation.INVOKE_ON_CLASSLIKE)
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
				prohibit(it, "InnerClass", "NamedExtensionOnParentForInner", Validation.EXTENSION_IN_CLASSLIKE)
			}
		}
	}

	//endregion

	//region No helper

	@Test
	fun `allow package-level function with same name as class`() {
		testWithResource("TopLevelFunction.kt") {
			documentablesTransformationStage = {
				allow(it, "TopLevelFunction", null, null)
			}
		}
	}

	@Test
	fun `prohibit package-level extension`() {
		testWithResource("TopLevelExtension.kt") {
			documentablesTransformationStage = {
				prohibit(it, "TopLevelExtension", null, Validation.TARGET_NOT_INNER)
			}
		}
	}

	//endregion

	//region Other

	@Test
	fun `prohibit invoke in self`() {
		testWithResource("InvokeInSelf.kt") {
			documentablesTransformationStage = {
				prohibit(it, "InvokeInSelf", "InvokeInSelf", Validation.INVOKE_ON_CLASSLIKE)
			}
		}
	}

	@Test
	fun `prohibit extension on instance`() {
		testWithResource("InvokeExtensionOnInstance.kt") {
			documentablesTransformationStage = {
				prohibit(it, "InvokeExtensionOnInstance", null, Validation.INVOKE_ON_CLASSLIKE)
			}
		}
	}

	//endregion

	private fun allow(
		module: DModule,
		targetClass: String,
		helperClass: String?,
		functionClass: String?,
		helperPackage: String = "io.github.spacedvoid.constructorlike",
		functionPackage: String = "io.github.spacedvoid.constructorlike"
	) {
		val injected = module.getDClass(targetClass).extra[PseudoConstructors.Key]
		assertNotNull(injected)
		assertEquals(1, injected.constructors.size)
		val constructor = injected.constructors[0]
		assertEquals(functionPackage, constructor.constructor.dri.packageName)
		assertEquals(functionClass, constructor.constructor.dri.classNames)
		if(helperClass != null) {
			val helper = constructor.helper
			assertNotNull(helper)
			assertEquals(helperPackage, helper.packageName)
			assertEquals(helperClass, helper.classNames)
		}
		else assertNull(constructor.helper)
		assertEquals(Validation.VALID, constructor.validation)
	}

	private fun prohibit(
		module: DModule,
		targetClass: String,
		functionClass: String?,
		reason: Validation,
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
		assertEquals(reason, invalid.second)
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
