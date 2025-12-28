/*
 * Copyright 2025 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.model.DFunction

enum class FindUseResult(private val message: String) {
	USED(""),
	NOT_OPERATOR("the function is not marked 'operator'"),
	NOT_EXTENSION_NOR_IN_COMPANION("the function is not an extension of a companion object or is not in one"),
	EXTENSION_IN_CLASSLIKE("the function is an extension in a companion object or a classlike"),
	TARGET_NOT_CLASS("the function does not return a class type"),
	TARGET_IS_UNIT("the function returns 'kotlin.Unit'"),
	TARGET_IS_NOTHING("the function returns 'kotlin.Nothing'"),
	RECEIVER_NOT_COMPANION_OF_TARGET("the function's receiver is not a companion object of the return type"),
	NOT_IN_COMPANION_OF_TARGET("the class owning the function is not a companion object of the return type"),
	NOT_INVOKE_BUT_EXTENSION("the function is not 'operator fun invoke' but is an extension"),
	NAME_NOT_TARGET("the name of the function does not match the return type"),
	TARGET_IS_INVALID_CLASSLIKE("the target class is an annotation class, enum class, or object"),
	TARGET_NOT_FOUND("the target class cannot be found or is in a different module");

	fun messageForLogging(function: DFunction): String {
		require(this != USED) { "Should not invoke when function is valid" }
		val asString = function.dri.toString() + function.sourceSets.joinToString(prefix = "[", postfix = "]") { it.displayName }
		return "Annotation @ConstructorLike cannot be applied to function $asString because ${this.message}"
	}
}
