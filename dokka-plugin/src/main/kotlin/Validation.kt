/*
 * Copyright 2026 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.model.DFunction

enum class Validation(private val message: String) {
	VALID(""),
	// Used by [ConstructorLikeFinder.resolve]
	TARGET_NOT_CLASS("the function does not return a class type"),
	TARGET_IS_UNIT("the function returns 'kotlin.Unit'"),
	TARGET_IS_NOTHING("the function returns 'kotlin.Nothing'"),
	RECEIVER_NOT_CLASSLIKE("the receiver is not a class type"),
	EXTENSION_IN_CLASSLIKE("the function is an extension in a class type"),
	NOT_OPERATOR("the function is not marked 'operator'"),
	INVOKE_NEITHER_EXTENSION_NOR_IN_CLASSLIKE("the function is not an extension of a companion object or is not a member"),
	NAME_NOT_TARGET("the name of the function does not match the return type"),
	TARGET_NOT_TOP_LEVEL("the target type is not package-level"),
	// Used by [ConstructorLikeFinder.recordPseudoConstructors]
	TARGET_IS_INVALID_CLASSLIKE("the target type is an annotation class, enum class, or object"),
	// Used by [PseudoConstructorMap.validateWith]
	INVOKE_ON_CLASSLIKE("the function is a member or an extension"),
	TARGET_IS_INNER("the target type is an inner class"),
	TARGET_NOT_INNER("the target type is not an inner class"),
	TARGET_NOT_NESTED("the target type is not a nested class"),
	TARGET_NOT_PARENT_OF_COMPANION("the target type is not the parent of the companion object"),
	// Default
	TARGET_NOT_FOUND("the target type cannot be found or is in a different module");

	fun messageForLogging(function: DFunction): String {
		require(this != VALID) { "Should not be invoked when function is valid" }
		val asString = function.dri.toString() + function.sourceSets.joinToString(prefix = "[", postfix = "]") { it.displayName }
		return "Annotation @ConstructorLike cannot be applied to function $asString because ${this.message}"
	}
}
