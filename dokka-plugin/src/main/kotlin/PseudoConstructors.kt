/*
 * Copyright 2025 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.properties.ExtraProperty

class PseudoConstructors(val constructors: List<DFunction>): ExtraProperty<DClasslike> {
	object Key: ExtraProperty.Key<DClasslike, PseudoConstructors>

	override val key: ExtraProperty.Key<DClasslike, PseudoConstructors> = Key
}
