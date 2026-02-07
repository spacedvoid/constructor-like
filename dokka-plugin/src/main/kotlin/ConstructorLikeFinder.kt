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
			it.extra[InvalidPseudoConstructors.Key]?.constructors?.forEach {
				context.logger.warn(it.second.messageForLogging(it.first))
			}
		}
	}

	/**
	 * An in-order DFS that shallowly collects the functions in the scope and validates the cumulated functions,
	 * then recurse into the child scopes, and finishes by recording valid pseudo-constructors.
	 * Companion objects are traversed before validating the functions.
	 *
	 * This works because a receiver only refers to its direct child classes when validating,
	 * and the target type can only be created by functions in the parent or child scopes of itself.
	 * When the target type looks for its pseudo-constructors, all relevant functions would already have been validated.
	 *
	 * @param siblings Information of sibling classlikes mapped to whether it is an inner class.
	 *                 Only used when [T] is a companion object, `null` otherwise.
	 */
	@Suppress("UNCHECKED_CAST")
	private fun <T: Documentable> T.recordPseudoConstructors(
		constructors: PseudoConstructorMap,
		siblings: Map<DRI, Boolean>? = null
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
		is DAnnotation -> copy(classlikes = processScope(constructors)).also {
			constructors.getByTarget(this).forEach { it.validation = Validation.TARGET_IS_INVALID_CLASSLIKE }
		}
		is DEnum -> copy(classlikes = processScope(constructors)).also {
			constructors.getByTarget(this).forEach { it.validation = Validation.TARGET_IS_INVALID_CLASSLIKE }
		}
		is DObject -> copy(classlikes = processScope(constructors, siblings)).also {
			constructors.getByTarget(this).forEach { it.validation = Validation.TARGET_IS_INVALID_CLASSLIKE }
		}
		else -> this
	} as T

	private fun <T> T.processScope(
		constructors: PseudoConstructorMap,
		siblings: Map<DRI, Boolean>? = null
	): List<DClasslike> where T: Documentable, T: WithScope {
		this.functions.forEach {
			if(!it.isConstructorLike) return@forEach
			when(val estimation = resolve(it)) {
				is Found -> constructors.add(PseudoConstructor(it, estimation.receiver, estimation.target))
				is Invalid -> constructors.addInvalid(it, estimation.reason)
			}
		}
		/*
		 * We can't trust [WithCompanion.companion] since Dokka itself transforms a documentable
		 * by only mapping its [WithScope.classlikes] and ignoring the `companion` property,
		 * which causes the `companion` instances to be different objects.
		 * And we need to separate the companion from other classlikes anyway.
		 */
		val companionDri = (this as? WithCompanion)?.companion?.dri
		val (companion, classlikes) = this.classlikes.partition { it.dri == companionDri }
		val children = classlikes.associate { it.dri to it.isInner }
		val newCompanion = companion.singleOrNull()?.recordPseudoConstructors(constructors, children)
		constructors.validateWith(this, children, siblings)
		val otherClassLikes = classlikes.map { it.recordPseudoConstructors(constructors) }
		return listOfNotNull(newCompanion) + otherClassLikes
	}

	/**
	 * Also performs basic validations that only uses the function;
	 * for example, whether the target type is a top-level class can be inferred by its [DRI]
	 * without needing to look for the class definition.
	 */
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

	/**
	 * @param children Similar structure with [siblings]:
	 *                 child classlikes of the [receiver] except the companion object
	 *                 mapped to whether it is an inner class.
	 */
	private fun PseudoConstructorMap.validateWith(
		receiver: Documentable,
		children: Map<DRI, Boolean>,
		siblings: Map<DRI, Boolean>?
	) {
		val (topLevel, instanceReceiver) = getByReceiver(receiver).partition { it.receiver == null }
		topLevel.forEach { it.validation = Validation.VALID } // All cases checked from [resolve]
		instanceReceiver.forEach {
			val targetIsInnerChild = children[it.target]
			val targetIsInnerSibling = siblings?.get(it.target)
			it.validation = when {
				it.constructor.name == "invoke" -> when {
					siblings == null -> Validation.INVOKE_ON_CLASSLIKE
					it.target != receiver.dri.parent -> Validation.TARGET_NOT_PARENT_OF_COMPANION
					else -> Validation.VALID
				}
				targetIsInnerSibling == null && targetIsInnerChild == null -> Validation.TARGET_NOT_NESTED
				targetIsInnerSibling == true -> Validation.TARGET_IS_INNER
				targetIsInnerChild == false && receiver !is DObject -> Validation.TARGET_NOT_INNER
				else -> Validation.VALID
			}
		}
	}
}

private sealed interface Resolution {
	class Found(val receiver: DRI?, val target: DRI): Resolution

	class Invalid(val reason: Validation): Resolution
}

private class PseudoConstructorMap {
	private val byReceiver = mutableMapOf<DRI, MutableList<PseudoConstructor>>()
	private val byTarget = mutableMapOf<DRI, MutableList<PseudoConstructor>>()
	private val invalids = mutableListOf<Pair<DFunction, Validation>>()

	fun add(constructor: PseudoConstructor) {
		this.byReceiver.getOrPut(constructor.receiver ?: constructor.target) { mutableListOf() }.add(constructor)
		this.byTarget.getOrPut(constructor.target) { mutableListOf() }.add(constructor)
	}

	fun addInvalid(function: DFunction, reason: Validation) {
		this.invalids += function to reason
	}

	/**
	 * Includes functions that have a `null` [receiver][PseudoConstructor.receiver],
	 * which uses its [target][PseudoConstructor.target] as the receiver instead.
	 */
	fun getByReceiver(receiver: Documentable): List<PseudoConstructor> =
		this.byReceiver[receiver.dri]?.filter { receiver.sourceSets.containsAll(it.constructor.sourceSets) } ?: listOf()

	fun getByTarget(target: Documentable): List<PseudoConstructor> =
		this.byTarget[target.dri]
			?.filter { target.sourceSets.containsAll(it.constructor.sourceSets) && it.validation == Validation.VALID }
			?: listOf()

	fun invalidConstructors(): List<Pair<DFunction, Validation>> =
		this.invalids.plus(
			this.byReceiver.values.asSequence()
				.flatten()
				.filter { it.validation != Validation.VALID }
				.map { it.constructor to it.validation }
		)
}
