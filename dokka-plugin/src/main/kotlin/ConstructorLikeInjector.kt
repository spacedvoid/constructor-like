/*
 * Copyright 2025-2026 spacedvoid
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
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.transformers.pages.PageTransformer

class ConstructorLikeInjector(private val context: DokkaContext): PageTransformer {
	override fun invoke(input: RootPageNode): RootPageNode = input.injectConstructors(createContentBuilder(this.context))

	private fun createContentBuilder(context: DokkaContext): PageContentBuilder =
		with(context.plugin<DokkaBase>()) {
			PageContentBuilder(
				querySingle { this.commentsToContentConverter },
				querySingle { this.signatureProvider },
				context.logger
			)
		}

	@Suppress("UNCHECKED_CAST")
	private fun <T: PageNode> T.injectConstructors(builder: PageContentBuilder): T = when(this) {
		is ClasslikePageNode -> injectConstructors(builder)
		else -> modified(children = this.children.map { it.injectConstructors(builder) })
	} as T

	private fun ClasslikePageNode.injectConstructors(builder: PageContentBuilder): ClasslikePageNode {
		val constructors = this.documentables.flatMap {
			@Suppress("UNCHECKED_CAST")
			(it as? WithExtraProperties<DClasslike>)?.extra[PseudoConstructors.Key]
				?.constructors
				?.map { it.constructor }
				?: listOf()
		}
		// Insert the pseudo-constructors to the constructors table, creating one if not present
		return modified(content = (this.content as ContentGroup).replacing<ContentGroup>(1) {
			if(it.withDescendants().none { it.extra[TabbedContentTypeExtra]?.value == BasicTabbedContentType.CONSTRUCTOR })
				it.copy(children = listOf(builder.contentForConstructors(this.documentables, constructors)) + it.children)
			else it.replacing<ContentGroup>(0) {
				it.replacing<ContentGroup>(0) {
					it.replacing<ContentTable>(1) {
						it.copy(
							dci = it.dci.copy(dri = it.dci.dri + constructors.map { it.dri }),
							sourceSets = it.sourceSets + constructors.flatMap { it.sourceSets.toDisplaySourceSets() },
							children = it.children + constructors.map {
								builder.contentFor(
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
}

private inline fun <reified T: ContentNode> ContentGroup.replacing(index: Int, replacement: (T) -> ContentNode): ContentGroup =
	copy(children = this.children.toMutableList().also { it[index] = replacement(it[index] as T) })

internal fun PageContentBuilder.DocumentableContentBuilder.addSignature(documentable: Documentable) {
	if(documentable !is DFunction || !documentable.isConstructor) throw AssertionError("Got non-constructor documentable $documentable")
	var signatures = buildSignature(documentable)
	val generics = documentable.generics
	if(generics.isNotEmpty()) signatures = signatures.map { addGenericsToSignature(it, generics) }
	+signatures
}

private fun PageContentBuilder.DocumentableContentBuilder.addGenericsToSignature(
	signature: ContentNode,
	generics: List<DTypeParameter>
): ContentGroup = buildGroup(kind = ContentKind.Symbol, styles = setOf(TextStyle.Monospace)) {
	val constructorTextIndex = signature.children.indexOfFirst { it is ContentText && it.text == "constructor" }
	repeat(constructorTextIndex) { +signature.children[it] }
	operator("<")
	generics.forEachIndexed { index, it ->
		+buildSignature(it)
		if(index < generics.size - 1) punctuation(", ")
	}
	operator("> ")
	for(i in constructorTextIndex..<signature.children.size) +signature.children[i]
}
