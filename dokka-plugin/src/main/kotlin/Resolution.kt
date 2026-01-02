/*
 * Copyright 2026 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.DriOfUnit
import org.jetbrains.dokka.links.parent
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.GenericTypeConstructor

internal sealed interface Resolution {
	class Found(val helper: DRI?, val target: DRI): Resolution

	class Invalid(val reason: Validation): Resolution

	companion object {
		fun resolve(function: DFunction): Resolution {
			val receiver = function.receiver?.type
			val target = function.type
			return when {
				target !is GenericTypeConstructor -> Invalid(Validation.TARGET_NOT_CLASS)
				target.dri == DriOfUnit -> Invalid(Validation.TARGET_IS_UNIT)
				target.dri == driOfNothing -> Invalid(Validation.TARGET_IS_NOTHING)
				receiver !is GenericTypeConstructor? -> Invalid(Validation.RECEIVER_NOT_CLASSLIKE)
				function.name == "invoke" -> when {
					!function.isOperator -> Invalid(Validation.NOT_OPERATOR)
					function.receiver == null && function.dri.parent.classNames == null ->
						Invalid(Validation.INVOKE_NEITHER_EXTENSION_NOR_IN_CLASSLIKE)
					function.receiver != null && function.dri.parent.classNames != null ->
						Invalid(Validation.EXTENSION_IN_CLASSLIKE)
					else -> Found(receiver?.dri ?: function.dri.parent, target.dri)
				}
				function.name != target.dri.classNames?.substringAfterLast(".") -> Invalid(Validation.NAME_NOT_TARGET)
				function.dri.parent.classNames == null && function.receiver == null && target.dri.parent.classNames != null ->
					Invalid(Validation.TARGET_NOT_TOP_LEVEL)
				function.dri.parent.classNames != null && function.receiver != null ->
					Invalid(Validation.EXTENSION_IN_CLASSLIKE)
				else -> Found(receiver?.dri ?: function.dri.parent.takeIf { it.classNames != null }, target.dri)
			}
		}
	}
}
