/*
 * Copyright 2026 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.AdditionalModifiers
import org.jetbrains.dokka.model.Annotations
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.ExtraModifiers
import org.jetbrains.dokka.model.properties.WithExtraProperties

val driOfNothing = DRI("kotlin", "Nothing")

val DFunction.isOperator: Boolean
	get() = this.extra[AdditionalModifiers]?.content
		?.any { ExtraModifiers.KotlinOnlyModifiers.Operator in it.value }
		?: false

@Suppress("UNCHECKED_CAST")
val DClasslike.isInner: Boolean
	get() = (this as WithExtraProperties<Documentable>).extra[AdditionalModifiers]
		?.content
		?.any { ExtraModifiers.KotlinOnlyModifiers.Inner in it.value }
		?: false

val DFunction.isConstructorLike: Boolean
	get() = this.extra[Annotations]
		?.directAnnotations
		?.values
		?.asSequence()
		?.flatten()
		?.any { it.dri.packageName == "io.github.spacedvoid.constructorlike" && it.dri.classNames == "ConstructorLike" }
		?: false
