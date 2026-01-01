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
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.withDescendants
import org.jetbrains.dokka.pages.ClasslikePageNode
import org.jetbrains.dokka.pages.ContentDRILink
import org.jetbrains.dokka.pages.ContentGroup
import org.jetbrains.dokka.pages.ContentTable
import org.jetbrains.dokka.pages.ContentText
import org.jetbrains.dokka.pages.RootPageNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class InjectorTest: BaseAbstractTest() {
	@Test
	fun `generic invoke in companion object`() {
		testWithResource("GenericInCompanion.kt") {
			pagesTransformationStage = {
				val constructors = it.getClassPageNode("GenericInCompanion").constructorsTable()
				assertEquals(2, constructors.children.size)
				assertEquals("GenericInCompanion", constructors.children[0].constructorLinkText())
				val pseudoConstructor = constructors.children[1]
				assertEquals("invoke", pseudoConstructor.constructorLinkText())
				val dri = pseudoConstructor.constructorLinkDRI()
				assertEquals("io.github.spacedvoid.constructorlike", dri.packageName)
				assertEquals("GenericInCompanion.Companion", dri.classNames)
				assertTrue(
					pseudoConstructor.constructorSignature().withDescendants().any { it is ContentText && it.text == "<" },
					"Signature does not have generics information"
				)
			}
		}
	}

	@Test
	fun `class with no default constructors`() {
		testWithResource("ConstructorsTableCreation.kt") {
			pagesTransformationStage = {
				val constructors = it.getClassPageNode("ConstructorsTableCreation").constructorsTable()
				assertEquals(1, constructors.children.size)
				assertEquals("invoke", constructors.children[0].constructorLinkText())
				val dri = constructors.children[0].constructorLinkDRI()
				assertEquals("io.github.spacedvoid.constructorlike", dri.packageName)
				assertEquals("ConstructorsTableCreation.Companion", dri.classNames)
			}
		}
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

private fun RootPageNode.getClassPageNode(className: String): ClasslikePageNode =
	withDescendants().filterIsInstance<ClasslikePageNode>()
		.filter { it.name == className }
		.singleOrNull()
		?: fail("Page has none or more than one classes named $className")

private fun ClasslikePageNode.constructorsTable(): ContentTable =
	((((this.content as? ContentGroup)
		?.children[1] as? ContentGroup)
		?.children[0] as? ContentGroup)
		?.children[0] as? ContentGroup)
		?.children[1] as? ContentTable
		?: fail("Class page ${this.name} does not have a constructors table")

private fun ContentGroup.constructorLinkText(): String = (this.children[0].children[0] as ContentText).text

private fun ContentGroup.constructorLinkDRI(): DRI = (this.children[0] as ContentDRILink).address

private fun ContentGroup.constructorSignature(): ContentGroup = this.children[1].children[0].children[0] as ContentGroup
