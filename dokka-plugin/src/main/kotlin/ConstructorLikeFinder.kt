/*
 * Copyright 2025 spacedvoid
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:OptIn(ExperimentalDokkaApi::class)

package io.github.spacedvoid.constructorlike.dokkaplugin

import org.jetbrains.dokka.ExperimentalDokkaApi
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.DriOfUnit
import org.jetbrains.dokka.links.parent
import org.jetbrains.dokka.model.AdditionalModifiers
import org.jetbrains.dokka.model.Annotations
import org.jetbrains.dokka.model.DAnnotation
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DInterface
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DObject
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.ExtraModifiers
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.WithCompanion
import org.jetbrains.dokka.model.withDescendants
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.documentation.DocumentableTransformer

class ConstructorLikeFinder: DocumentableTransformer {
	override fun invoke(original: DModule, context: DokkaContext): DModule {
		val pseudoConstructors = original.withDescendants()
			.filterIsInstance<DFunction>()
			.fold(PseudoConstructorMap()) { map, it ->
				if(!it.isConstructorLike) return@fold map
				when(val estimation = it.estimateTargetClass()) {
					is Estimation.Found -> map.add(estimation.targetClass, PseudoConstructor(it))
					is Estimation.Invalid -> map.addInvalid(PseudoConstructor(it).also { it.usageResult = estimation.reason })
				}
				return@fold map
			}
		val recorded = original.recordPseudoConstructors(pseudoConstructors)
		val invalidConstructors = pseudoConstructors.invalidConstructors()
		invalidConstructors.forEach { context.logger.warn(it.usageResult.messageForLogging(it.constructor)) }
		return recorded.copy(extra = recorded.extra + InvalidPseudoConstructors(invalidConstructors))
	}

	private class PseudoConstructorMap {
		private val map = mutableMapOf<DRI, MutableList<PseudoConstructor>>()
		private val invalids = mutableListOf<PseudoConstructor>()

		fun add(dri: DRI, constructor: PseudoConstructor) {
			this.map.getOrPut(dri) { mutableListOf() }.add(constructor)
		}

		fun addInvalid(constructor: PseudoConstructor) {
			this.invalids += constructor
		}

		operator fun <T> get(classlike: T): List<PseudoConstructor> where T: Documentable, T: WithCompanion =
			buildList {
				getRaw(classlike).partition {
					// See [estimateTargetClass] for the reason of this check.
					(it.constructor.type as GenericTypeConstructor).dri == classlike.dri
				}.let { (constructors, notMine) ->
					addAll(constructors)
					constructors.forEach { it.usageResult = FindUseResult.USED }
					notMine.forEach { it.usageResult = FindUseResult.NOT_IN_COMPANION_OF_TARGET }
				}
				classlike.companion?.let { getRaw(it) }?.also { addAll(it) }?.forEach {
					it.usageResult = FindUseResult.USED
				}
			}

		fun getRaw(documentable: Documentable): List<PseudoConstructor> =
			this.map[documentable.dri]?.filter { documentable.sourceSets.containsAll(it.constructor.sourceSets) } ?: listOf()

		fun invalidConstructors(): List<PseudoConstructor> =
			this.invalids + this.map.values.asSequence().flatten().filter { it.usageResult != FindUseResult.USED }
	}

	private val DFunction.isConstructorLike: Boolean
		get() = this.extra[Annotations]
			?.directAnnotations
			?.values
			?.asSequence()
			?.flatten()
			?.any { it.dri.packageName == "io.github.spacedvoid.constructorlike" && it.dri.classNames == "ConstructorLike" }
			?: false

	private sealed interface Estimation {
		@JvmInline
		value class Found(val targetClass: DRI): Estimation

		@JvmInline
		value class Invalid(val reason: FindUseResult): Estimation
	}

	private fun DFunction.estimateTargetClass(): Estimation = when {
		this.name == "invoke" -> when {
			!this.isOperator -> Estimation.Invalid(FindUseResult.NOT_OPERATOR)
			this.receiver == null && this.dri.parent.classNames == null ->
				Estimation.Invalid(FindUseResult.NOT_EXTENSION_NOR_IN_COMPANION)
			this.receiver != null && this.dri.parent.classNames != null ->
				Estimation.Invalid(FindUseResult.EXTENSION_IN_CLASSLIKE)
			else -> run {
				val receiver = this.receiver?.type
				val parent = this.dri.parent
				val returnType = this.type
				return@run when {
					returnType !is GenericTypeConstructor -> Estimation.Invalid(FindUseResult.TARGET_NOT_CLASS)
					returnType.dri == DriOfUnit -> Estimation.Invalid(FindUseResult.TARGET_IS_UNIT)
					returnType.dri == driOfNothing -> Estimation.Invalid(FindUseResult.TARGET_IS_NOTHING)
					receiver != null && (receiver !is GenericTypeConstructor || receiver.dri.parent != returnType.dri) ->
						Estimation.Invalid(FindUseResult.RECEIVER_NOT_COMPANION_OF_TARGET)
					receiver == null && parent.parent != returnType.dri ->
						Estimation.Invalid(FindUseResult.NOT_IN_COMPANION_OF_TARGET)
					/*
					 * We return the parent's dri because we cannot know whether the parent is really a companion object.
					 * [recordPseudoConstructors] will handle this by checking the return type of the function:
					 * if the parent is a non-companion classlike, the return type will differ from the classlike.
					 */
					parent.classNames != null -> Estimation.Found(parent)
					else -> Estimation.Found(receiver?.dri ?: returnType.dri)
				}
			}
		}
		this.receiver != null -> Estimation.Invalid(FindUseResult.NOT_INVOKE_BUT_EXTENSION)
		else -> this.type.let {
			when {
				it !is GenericTypeConstructor -> Estimation.Invalid(FindUseResult.TARGET_NOT_CLASS)
				it.dri == DriOfUnit -> Estimation.Invalid(FindUseResult.TARGET_IS_UNIT)
				it.dri == driOfNothing -> Estimation.Invalid(FindUseResult.TARGET_IS_NOTHING)
				this.name != it.dri.classNames -> Estimation.Invalid(FindUseResult.NAME_NOT_TARGET)
				else -> Estimation.Found(it.dri)
			}
		}
	}

	private val DFunction.isOperator: Boolean
		get() = this.extra[AdditionalModifiers]?.content
			?.any { ExtraModifiers.KotlinOnlyModifiers.Operator in it.value }
			?: false

	private val driOfNothing = DRI("kotlin", "Nothing")

	@Suppress("UNCHECKED_CAST")
	private fun <T: Documentable> T.recordPseudoConstructors(
		constructors: PseudoConstructorMap
	): T = when(this) {
		is DModule -> copy(packages = this.packages.map { it.recordPseudoConstructors(constructors) })
		is DPackage -> copy(classlikes = this.classlikes.map { it.recordPseudoConstructors(constructors) })
		is DClass -> copy(extra = this.extra + PseudoConstructors(constructors[this]))
		is DInterface -> copy(extra = this.extra + PseudoConstructors(constructors[this]))
		is DAnnotation -> this.also { constructors.getRaw(this).forEach { it.usageResult = FindUseResult.TARGET_IS_INVALID_CLASSLIKE } }
		is DEnum -> this.also { constructors.getRaw(this).forEach { it.usageResult = FindUseResult.TARGET_IS_INVALID_CLASSLIKE } }
		is DObject -> this.also { constructors.getRaw(this).forEach { it.usageResult = FindUseResult.TARGET_IS_INVALID_CLASSLIKE } }
		else -> this
	} as T
}
