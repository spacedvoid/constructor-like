/*
 * Copyright 2026 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.base.resolvers.local.DokkaBaseLocationProvider
import org.jetbrains.dokka.base.resolvers.local.DokkaLocationProviderFactory
import org.jetbrains.dokka.base.resolvers.local.LocationProvider
import org.jetbrains.dokka.base.resolvers.local.LocationProviderFactory
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.DisplaySourceSet
import org.jetbrains.dokka.pages.PageNode
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext

class ConstructorLikeProviderFactory(private val context: DokkaContext): LocationProviderFactory {
	private val dokkaProvider: LocationProviderFactory = DokkaLocationProviderFactory(this.context)

	override fun getLocationProvider(pageNode: RootPageNode): LocationProvider =
		ConstructorLikeLinkProvider(pageNode, this.context, this.dokkaProvider.getLocationProvider(pageNode))
}

class ConstructorLikeLinkProvider(
	root: RootPageNode,
	context: DokkaContext,
	private val dokka: LocationProvider
// DokkaBaseLocationProvider is required as a superclass because HtmlRenderer casts all [LocationProvider] to this type
): DokkaBaseLocationProvider(root, context), LocationProvider by dokka {
	override fun resolve(dri: DRI, sourceSets: Set<DisplaySourceSet>, context: PageNode?): String? = when {
		dri.packageName == "io.github.spacedvoid.constructorlike" && dri.classNames == "ConstructorLike" ->
			"https://github.com/spacedvoid/constructor-like/tree/main/resources/markdown/tutorial.md"
		else -> this.dokka.resolve(dri, sourceSets, context)
	}
}
