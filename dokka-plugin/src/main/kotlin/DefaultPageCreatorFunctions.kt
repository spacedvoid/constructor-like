/*
 * Copyright 2025 spacedvoid
 * Copyright 2014-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 *
 * Source code copied and modified from [org.jetbrains.dokka.base.translators.documentables.DefaultPageCreator]
 * (https://github.com/Kotlin/dokka/blob/v2.1.0/dokka-subprojects/plugin-base/src/main/kotlin/org/jetbrains/dokka/base/translators/documentables/DefaultPageCreator.kt)
 * from project [Kotlin/dokka](https://github.com/Kotlin/dokka/tree/v2.1.0) version 2.1.0, licensed with the Apache License 2.0.
 *
 * Some extension function calls were expanded and/or optimized out while maintaining the original flow.
 * Project-level formatting was performed without changing the structure.
 *
 * License and notice files for the project can be found at [/third-party-licenses/Kotlin/dokka].
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.Property
import org.jetbrains.dokka.model.firstMemberOfTypeOrNull
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.pages.BasicTabbedContentType
import org.jetbrains.dokka.pages.ContentGroup
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentStyle
import org.jetbrains.dokka.pages.TabbedContentTypeExtra

/*
 * Copied and modified from [org.jetbrains.dokka.base.translators.documentables.DefaultPageCreator.contentForConstructors]
 *
 * Modified to include generics info to the signature([addSignature], original: [buildSignature]).
 */
internal fun PageContentBuilder.contentForConstructors(documentables: List<Documentable>, constructors: List<DFunction>): ContentGroup =
	contentFor(
		documentables.mapTo(mutableSetOf()) { it.dri },
		documentables.flatMapTo(mutableSetOf()) { it.sourceSets }
	) {
		multiBlock(
			"Constructors",
			2,
			ContentKind.Constructors,
			constructors.groupBy { it.name }.map { it.toPair() },
			constructors.flatMapTo(mutableSetOf()) { it.sourceSets },
			extra = PropertyContainer.withAll(TabbedContentTypeExtra(BasicTabbedContentType.CONSTRUCTOR)),
			needsSorting = false,
			needsAnchors = true
		) { key, ds ->
			link(key, ds.first().dri, ContentKind.Main, styles = setOf(ContentStyle.RowTitle))
			sourceSetDependentHint(
				ds.mapTo(mutableSetOf()) { it.dri },
				ds.flatMapTo(mutableSetOf()) { it.sourceSets },
				ContentKind.SourceSetDependentHint,
				emptySet(),
				PropertyContainer.empty()
			) {
				ds.forEach {
					addSignature(it)
					contentForBrief(it)
				}
			}
		}
	}

/*
 * Copied and modified from [org.jetbrains.dokka.base.translators.documentables.DefaultPageCreator.contentForBrief]
 *
 * Call to [createBriefComment] was inlined after removing the branching on language types.
 */
internal fun PageContentBuilder.DocumentableContentBuilder.contentForBrief(d: Documentable) {
	d.sourceSets.forEach { sourceSet ->
		d.documentation[sourceSet]
			?.let {
				it.firstMemberOfTypeOrNull<Description>() ?: it.firstMemberOfTypeOrNull<Property>().takeIf { d is DProperty }
			}
			?.let {
				group(sourceSets = setOf(sourceSet), kind = ContentKind.BriefComment) {
					firstParagraphComment(it.root)
				}
			}
	}
}
