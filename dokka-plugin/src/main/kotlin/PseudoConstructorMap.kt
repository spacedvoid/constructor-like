/*
 * Copyright 2026 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.Documentable

internal class PseudoConstructorMap {
	private val byHelper = mutableMapOf<DRI, MutableList<PseudoConstructor>>()
	private val byTarget = mutableMapOf<DRI, MutableList<PseudoConstructor>>()
	private val invalids = mutableListOf<Pair<DFunction, Validation>>()

	fun add(constructor: PseudoConstructor) {
		this.byHelper.getOrPut(constructor.helper ?: constructor.target) { mutableListOf() }.add(constructor)
		this.byTarget.getOrPut(constructor.target) { mutableListOf() }.add(constructor)
	}

	fun addInvalid(function: DFunction, reason: Validation) {
		this.invalids += function to reason
	}

	fun getByHelper(helper: Documentable): List<PseudoConstructor> =
		this.byHelper[helper.dri]?.filter { helper.sourceSets.containsAll(it.constructor.sourceSets) } ?: listOf()

	fun getByTarget(target: Documentable): List<PseudoConstructor> =
		this.byTarget[target.dri]?.filter { target.sourceSets.containsAll(it.constructor.sourceSets) && it.validation == Validation.VALID } ?: listOf()

	fun invalidConstructors(): List<Pair<DFunction, Validation>> =
		this.invalids + this.byHelper.values.asSequence().flatten().filter { it.validation != Validation.VALID }.map { it.constructor to it.validation }
}
