/*
 * Copyright 2025-2026 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(ExperimentalDokkaApi::class)

package io.github.spacedvoid.constructorlike.dokkaplugin

import io.github.spacedvoid.constructorlike.dokkaplugin.Resolution.Found
import io.github.spacedvoid.constructorlike.dokkaplugin.Resolution.Invalid
import org.jetbrains.dokka.ExperimentalDokkaApi
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.DriOfUnit
import org.jetbrains.dokka.links.parent
import org.jetbrains.dokka.model.DAnnotation
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DInterface
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DObject
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.GenericTypeConstructor
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
			when(val estimation = resolve(it)) {
				is Found -> constructors.add(PseudoConstructor(it, estimation.helper, estimation.target))
				is Invalid -> constructors.addInvalid(it, estimation.reason)
			}
		}
		val classlikes = this.classlikes.partition { it.dri == (this as? WithCompanion)?.companion?.dri }
		val companion = classlikes.first.singleOrNull()?.recordPseudoConstructors(constructors, isCompanion = true)
		if(!isCompanion) constructors.validateWith(this)
		val otherClassLikes = classlikes.second.map { it.recordPseudoConstructors(constructors) }
		return listOfNotNull(companion) + otherClassLikes
	}

	private fun resolve(function: DFunction): Resolution {
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

private sealed interface Resolution {
	class Found(val helper: DRI?, val target: DRI): Resolution

	class Invalid(val reason: Validation): Resolution
}

private class PseudoConstructorMap {
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
