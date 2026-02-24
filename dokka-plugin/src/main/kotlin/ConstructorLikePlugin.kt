/*
 * Copyright 2025-2026 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.resolvers.local.LocationProviderFactory
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.Extension
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement
import org.jetbrains.dokka.transformers.documentation.DocumentableTransformer
import org.jetbrains.dokka.transformers.pages.PageTransformer

class ConstructorLikePlugin: DokkaPlugin() {
	@OptIn(DokkaPluginApiPreview::class)
	override fun pluginApiPreviewAcknowledgement(): PluginApiPreviewAcknowledgement = PluginApiPreviewAcknowledgement

	@Suppress("unused")
	val constructorLikeFinder: Extension<DocumentableTransformer, *, *> by extending {
		CoreExtensions.documentableTransformer with ConstructorLikeFinder()
	}

	@Suppress("unused")
	val constructorLikeInjector: Extension<PageTransformer, *, *> by extending {
		CoreExtensions.pageTransformer providing ::ConstructorLikeInjector
	}

	@Suppress("unused")
	val constructorLikeProviderFactory: Extension<LocationProviderFactory, *, *> by extending {
		(plugin<DokkaBase>().locationProviderFactory
			providing ::ConstructorLikeProviderFactory
			override plugin<DokkaBase>().locationProvider)
	}
}
