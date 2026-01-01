package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class ExtensionInCompanion {
	companion object {
		@ConstructorLike
		operator fun ExtensionInCompanion.Companion.invoke(): ExtensionInCompanion = TODO()
	}
}
