/*
 * Copyright 2025-2026 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(ExperimentalDokkaApi::class)

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.ExperimentalDokkaApi
import org.jetbrains.dokka.model.DAnnotation
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DInterface
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DObject
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.WithCompanion
import org.jetbrains.dokka.model.WithScope
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.documentation.DocumentableTransformer

class ConstructorLikeFinder: DocumentableTransformer {
	override fun invoke(original: DModule, context: DokkaContext): DModule {
		val map = PseudoConstructorMap()
		return original.recordPseudoConstructors(map).also {
			map.invalidConstructors().forEach { context.logger.warn(it.second.messageForLogging(it.first)) }
		}
	}

	/**
	 * This implementation follows the procedure below:
	 * 1. Enter the scope([processScope])
	 * 2. Find pseudo-constructors in this scope(shallow)
	 * 3. If this scope has a [companion][WithCompanion], find pseudo-constructors in the companion(shallow)
	 * 4. Validate pseudo-constructors with the helper as this scope
	 * 5. If this scope has a companion, validate pseudo-constructors with the helper as the companion
	 * 6. Recurse into the child classlikes
	 * 7. Add pseudo-constructors with the target as this scope
	 *
	 * This works because a helper(or its parent if it is a companion) only refers to its child classes when validating,
	 * and the target type can only be created by functions in the parent or child scopes of itself.
	 * When the target type finds its pseudo-constructors, all relevant functions would already have been validated.
	 *
	 * @param isCompanion Because companion objects are not standalone objects, it cannot be used as a helper like other classlikes.
	 *                    Instead, its parent will resolve its companion with itself.
	 */
	@Suppress("UNCHECKED_CAST")
	private fun <T: Documentable> T.recordPseudoConstructors(
		constructors: PseudoConstructorMap,
		isCompanion: Boolean = false
	): T = when(this) {
		is DModule -> copy(
			packages = this.packages.map { it.recordPseudoConstructors(constructors) },
			extra = this.extra + InvalidPseudoConstructors(constructors.invalidConstructors())
		)
		is DPackage -> copy(classlikes = processScope(constructors))
		is DClass -> copy(
			classlikes = processScope(constructors),
			extra = this.extra + PseudoConstructors(constructors.getByTarget(this))
		)
		is DInterface -> copy(
			classlikes = processScope(constructors),
			extra = this.extra + PseudoConstructors(constructors.getByTarget(this))
		)
		is DAnnotation -> run {
			val processed = processScope(constructors)
			constructors.getByTarget(this).forEach { it.validation = Validation.TARGET_IS_INVALID_CLASSLIKE }
			return@run copy(classlikes = processed)
		}
		is DEnum -> run {
			val processed = processScope(constructors)
			constructors.getByTarget(this).forEach { it.validation = Validation.TARGET_IS_INVALID_CLASSLIKE }
			return@run copy(classlikes = processed)
		}
		is DObject -> run {
			val processed = processScope(constructors, isCompanion)
			constructors.getByTarget(this).forEach { it.validation = Validation.TARGET_IS_INVALID_CLASSLIKE }
			return@run copy(classlikes = processed)
		}
		else -> this
	} as T

	private fun <T> T.processScope(constructors: PseudoConstructorMap, isCompanion: Boolean = false): List<DClasslike> where T: Documentable, T: WithScope {
		this.functions.forEach {
			if(!it.isConstructorLike) return@forEach
			when(val estimation = Resolution.resolve(it)) {
				is Resolution.Found -> constructors.add(PseudoConstructor(it, estimation.helper, estimation.target))
				is Resolution.Invalid -> constructors.addInvalid(it, estimation.reason)
			}
		}
		val classlikes = this.classlikes.partition { it.dri == (this as? WithCompanion)?.companion?.dri }
		val companion = classlikes.first.singleOrNull()?.recordPseudoConstructors(constructors, isCompanion = true)
		if(!isCompanion) constructors.validateWith(this)
		val otherClassLikes = classlikes.second.map { it.recordPseudoConstructors(constructors) }
		return listOfNotNull(companion) + otherClassLikes
	}

	private fun <T> PseudoConstructorMap.validateWith(helper: T) where T: Documentable, T: WithScope {
		val childClasslikesToIsInner = helper.classlikes.associate { it.dri to it.isInner }
		val (topLevel, instanceHelper) = getByHelper(helper).partition { it.helper == null }
		topLevel.forEach { it.validation = Validation.VALID }
		instanceHelper.forEach {
			val isInner = childClasslikesToIsInner[it.target]
			it.validation = when {
				it.constructor.name == "invoke" -> Validation.INVOKE_ON_CLASSLIKE
				isInner == null || helper !is DObject && !isInner -> Validation.TARGET_NOT_INNER
				else -> Validation.VALID
			}
		}
		val companion = (helper as? WithCompanion)?.companion ?: return
		getByHelper(companion).forEach {
			it.validation = when {
				it.constructor.name == "invoke" -> if(it.target == helper.dri) Validation.VALID else Validation.TARGET_NOT_PARENT_OF_COMPANION
				it.target !in childClasslikesToIsInner -> Validation.TARGET_NOT_NESTED
				childClasslikesToIsInner.getValue(it.target) -> Validation.TARGET_IS_INNER
				else -> Validation.VALID
			}
		}
	}
}
