package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class GenericInCompanion {
	companion object {
		@ConstructorLike
		operator fun <K: Number> invoke(unused: K): GenericInCompanion = TODO()
	}
}
