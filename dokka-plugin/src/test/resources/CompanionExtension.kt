package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class CompanionExtension {
	companion object
}

@ConstructorLike
operator fun CompanionExtension.Companion.invoke(): CompanionExtension = TODO()
