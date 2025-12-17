/*
 * Copyright 2025 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.resolvers.anchors.SymbolAnchorHint
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DTypeParameter
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.model.toDisplaySourceSets
import org.jetbrains.dokka.model.withDescendants
import org.jetbrains.dokka.pages.BasicTabbedContentType
import org.jetbrains.dokka.pages.ClasslikePageNode
import org.jetbrains.dokka.pages.ContentGroup
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.ContentStyle
import org.jetbrains.dokka.pages.ContentTable
import org.jetbrains.dokka.pages.ContentText
import org.jetbrains.dokka.pages.PageNode
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.pages.TabbedContentTypeExtra
import org.jetbrains.dokka.pages.TextStyle
import org.jetbrains.dokka.pages.TokenStyle
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.transformers.pages.PageTransformer

class ConstructorLikeInjector(private val context: DokkaContext): PageTransformer {
	override fun invoke(input: RootPageNode): RootPageNode = input.injectConstructors(createContentBuilder(this.context))

	@Suppress("UNCHECKED_CAST")
	private fun <T: PageNode> T.injectConstructors(builder: PageContentBuilder): T = when(this) {
		is ClasslikePageNode -> builder.injectConstructors(this)
		else -> modified(children = this.children.map { it.injectConstructors(builder) })
	} as T

	private fun PageContentBuilder.injectConstructors(classPage: ClasslikePageNode): ClasslikePageNode {
		val constructors = classPage.documentables.flatMap {
			@Suppress("UNCHECKED_CAST")
			(it as? WithExtraProperties<DClasslike>)?.extra[InjectedConstructors.Key]?.constructors ?: listOf()
		}
		// Inserts the pseudo-constructors to the constructors table, creating one if not present
		return classPage.modified(content = (classPage.content as ContentGroup).replacing<ContentGroup>(1) {
			if(it.withDescendants().none { it.extra[TabbedContentTypeExtra]?.value == BasicTabbedContentType.CONSTRUCTOR })
				it.copy(children = listOf(contentForConstructors(classPage.documentables, constructors)) + it.children)
			else it.replacing<ContentGroup>(0) {
				it.replacing<ContentGroup>(0) {
					it.replacing<ContentTable>(1) {
						it.copy(
							dci = it.dci.copy(dri = it.dci.dri + constructors.mapTo(mutableSetOf()) { it.dri }),
							sourceSets = it.sourceSets + constructors.flatMapTo(mutableSetOf()) { it.sourceSets.toDisplaySourceSets() },
							children = it.children + constructors.map {
								contentFor(
									it.dri,
									it.sourceSets,
									ContentKind.Constructors,
									extra = PropertyContainer.withAll(SymbolAnchorHint(it.name, ContentKind.Constructors))
								) {
									link(it.name, it.dri, ContentKind.Main, styles = setOf(ContentStyle.RowTitle))
									sourceSetDependentHint(
										setOf(it.dri),
										it.sourceSets,
										ContentKind.SourceSetDependentHint,
										extra = PropertyContainer.empty()
									) {
										addSignature(it)
										contentForBrief(it)
									}
								}
							}
						)
					}
				}
			}
		})
	}

	private fun createContentBuilder(context: DokkaContext): PageContentBuilder =
		with(context.plugin<DokkaBase>()) {
			PageContentBuilder(
				querySingle { this.commentsToContentConverter },
				querySingle { this.signatureProvider },
				context.logger
			)
		}
}

private inline fun <reified T: ContentNode> ContentGroup.replacing(index: Int, crossinline replacement: (T) -> ContentNode): ContentGroup =
	copy(children = this.children.toMutableList().also { it[index] = replacement(it[index] as T) })

internal fun PageContentBuilder.DocumentableContentBuilder.addSignature(documentable: Documentable) {
	if(documentable !is DFunction || !documentable.isConstructor) throw AssertionError("Got non-constructor documentable $documentable")
	var signatures = buildSignature(documentable)
	val generics = documentable.generics
	if(generics.isNotEmpty()) signatures = addGenericsSignature(signatures, generics)
	+signatures
}

private fun PageContentBuilder.DocumentableContentBuilder.addGenericsSignature(signatures: List<ContentNode>, generics: List<DTypeParameter>): List<ContentGroup> =
	signatures.map { signature ->
		// Position of where to put generics info can be rediscussed, it is currently right before the `constructor` keyword
		val constructorTextIndex = signature.children.indexOfFirst { it is ContentText && it.text == "constructor" }
		return@map buildGroup(kind = ContentKind.Symbol, styles = setOf(TextStyle.Monospace)) {
			repeat(constructorTextIndex) { +signature.children[it] }
			text("<", styles = setOf(TextStyle.Monospace, TokenStyle.Operator))
			generics.forEachIndexed { index, it ->
				+buildSignature(it)
				if(index < generics.size - 1) text(", ", styles = setOf(TextStyle.Monospace, TokenStyle.Punctuation))
			}
			text("> ", styles = setOf(TextStyle.Monospace, TokenStyle.Operator))
			for(i in constructorTextIndex..<signature.children.size) +signature.children[i]
		}
	}
