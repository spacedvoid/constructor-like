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
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DInterface
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.ExtraModifiers
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.WithCompanion
import org.jetbrains.dokka.model.withDescendants
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.documentation.DocumentableTransformer
import org.jetbrains.dokka.utilities.DokkaLogger

class ConstructorLikeFinder: DocumentableTransformer {
	override fun invoke(original: DModule, context: DokkaContext): DModule {
		val pseudoConstructors = original.withDescendants()
			.filterIsInstance<DFunction>()
			.fold(mutableMapOf<DRI, MutableList<PseudoConstructor>>()) { map, it ->
				if(!it.isConstructorLike) return@fold map
				when(val estimation = it.estimateTargetClass()) {
					is Estimation.Found -> map.getOrPut(estimation.targetClass) { mutableListOf() }.add(PseudoConstructor(it))
					is Estimation.Invalid ->
						context.logger.warn("Annotation @ConstructorLike cannot be applied to ${it.asString()} because ${estimation.reason}")
				}
				return@fold map
			}
		return original.recordPseudoConstructors(pseudoConstructors).also { context.logger.reportUnused(pseudoConstructors) }
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

		/**
		 * @property reason A user-friendly string of why `@ConstructorLike` is invalid on this element.
		 */
		@JvmInline
		value class Invalid(val reason: String): Estimation
	}

	private fun DFunction.estimateTargetClass(): Estimation = when {
		this.name == "invoke" -> when {
			!this.isOperator -> Estimation.Invalid("the function is not marked 'operator'")
			this.receiver == null && this.dri.parent.classNames == null ->
				Estimation.Invalid("the function is not an extension of a companion object or is not in one")
			this.receiver != null && this.dri.parent.classNames != null ->
				Estimation.Invalid("the function is an extension in a companion object or a class")
			else -> run {
				val receiver = this.receiver?.type
				val parent = this.dri.parent
				val returnType = this.type
				return@run when {
					returnType !is GenericTypeConstructor -> Estimation.Invalid("the function does not return a class type")
					returnType.dri == DriOfUnit -> Estimation.Invalid("the function returns 'kotlin.Unit'")
					returnType.dri == driOfNothing -> Estimation.Invalid("the function returns 'kotlin.Nothing'")
					receiver != null && (receiver !is GenericTypeConstructor || receiver.dri.parent != returnType.dri) ->
						Estimation.Invalid("the function's receiver is not a companion object of the return type")
					receiver == null && parent.parent != returnType.dri ->
						Estimation.Invalid("the class owning the function is not a companion object of the return type")
					/*
					 * We return the parent's dri because we cannot know whether the parent is really a companion object.
					 * [recordPseudoConstructors] will handle this by checking the return type of the function.
					 */
					parent.classNames != null -> Estimation.Found(parent)
					else -> Estimation.Found(receiver?.dri ?: returnType.dri)
				}
			}
		}
		this.receiver != null -> Estimation.Invalid("the function is not 'operator fun invoke' but is an extension")
		else -> this.type.let {
			when {
				it !is GenericTypeConstructor -> Estimation.Invalid("the function does not return a class type")
				it.dri == DriOfUnit -> Estimation.Invalid("the function returns 'kotlin.Unit'")
				it.dri == driOfNothing -> Estimation.Invalid("the function returns 'kotlin.Nothing'")
				this.name != it.dri.classNames -> Estimation.Invalid("the name of the function does not match the return type")
				else -> Estimation.Found(it.dri)
			}
		}
	}

	private val DFunction.isOperator: Boolean
		get() = this.extra[AdditionalModifiers]?.content
			?.any { ExtraModifiers.KotlinOnlyModifiers.Operator in it.value }
			?: false

	private val driOfNothing = DRI("kotlin", "Nothing")

	/**
	 * We need to check whether the pseudo-constructors refers to a valid class or interface in the same module,
	 * but we don't want to re-navigate the [Documentable] tree to check it.
	 * So we create an intermediate object and store whether the pseudo-constructors were [used] in [recordPseudoConstructors]
	 * and raise warnings with [reportUnused].
	 */
	private class PseudoConstructor(original: DFunction) {
		@OptIn(ExperimentalDokkaApi::class)
		val constructor = original.copy(
			isConstructor = true,
			receiver = null
		)

		var used: Boolean = false
	}

	@Suppress("UNCHECKED_CAST")
	private fun <T: Documentable> T.recordPseudoConstructors(
		constructors: Map<DRI, List<PseudoConstructor>>
	): T = when(this) {
		is DModule -> copy(packages = this.packages.map { it.recordPseudoConstructors(constructors) })
		is DPackage -> copy(classlikes = this.classlikes.map { it.recordPseudoConstructors(constructors) })
		is DClass -> copy(extra = this.extra + InjectedConstructors(extractConstructorsFrom(constructors)))
		is DInterface -> copy(extra = this.extra + InjectedConstructors(extractConstructorsFrom(constructors)))
		else -> this
	} as T

	private fun <T> T.extractConstructorsFrom(constructors: Map<DRI, List<PseudoConstructor>>): List<DFunction>
	where T: Documentable, T: WithCompanion = buildList {
		constructors[this@extractConstructorsFrom.dri]
			?.asSequence()
			?.filter {
				this@extractConstructorsFrom.sourceSets.containsAll(it.constructor.sourceSets)
				// See [estimateTargetClass] for the reason of this check.
				&& (it.constructor.type as GenericTypeConstructor).dri == this@extractConstructorsFrom.dri
			}
			?.forEach {
				add(it.constructor)
				it.used = true
			}
		this@extractConstructorsFrom.companion?.dri
			?.let { constructors[it] }
			?.asSequence()
			?.filter { this@extractConstructorsFrom.sourceSets.containsAll(it.constructor.sourceSets) }
			?.forEach {
				add(it.constructor)
				it.used = true
			}
	}

	private fun DokkaLogger.reportUnused(constructors: Map<DRI, List<PseudoConstructor>>) {
		constructors.forEach { (dri, l) ->
			l.forEach {
				if(it.used) return@forEach
				this@reportUnused.warn(
					"Annotation @ConstructorLike cannot be applied to function ${it.constructor.asString()} because "
					+ "the function is not in a companion object, "
					+ "the target class $dri cannot be found, is in a different module, or is an annotation class, enum class, or object"
				)
			}
		}
	}

	private fun Documentable.asString(): String =
		this.dri.toString() + this.sourceSets.joinToString(prefix = "[", postfix = "]") { it.displayName }
}
