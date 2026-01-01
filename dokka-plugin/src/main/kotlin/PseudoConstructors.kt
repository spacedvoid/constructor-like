/*
 * Copyright 2025-2026 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.ExperimentalDokkaApi
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.properties.ExtraProperty

/**
 * @property helper `null` if and only if the function has no receiver and is top-level.
 */
class PseudoConstructor(original: DFunction, val helper: DRI?, val target: DRI) {
	@OptIn(ExperimentalDokkaApi::class)
	val constructor: DFunction = original.copy(isConstructor = true)

	var validation: Validation = Validation.TARGET_NOT_FOUND
}

class PseudoConstructors(val constructors: List<PseudoConstructor>): ExtraProperty<DClasslike> {
	object Key: ExtraProperty.Key<DClasslike, PseudoConstructors>

	override val key: ExtraProperty.Key<DClasslike, PseudoConstructors> = Key
}

/**
 * Collection of invalid pseudo-constructors in this module.
 * Mostly for testing purposes.
 */
class InvalidPseudoConstructors(val constructors: List<Pair<DFunction, Validation>>): ExtraProperty<DModule> {
	object Key: ExtraProperty.Key<DModule, InvalidPseudoConstructors>

	override val key: ExtraProperty.Key<DModule, InvalidPseudoConstructors> = Key
}
