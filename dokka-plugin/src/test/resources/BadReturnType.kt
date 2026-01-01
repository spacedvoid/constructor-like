package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class Something

class BadReturnType {
	companion object {
		@ConstructorLike
		operator fun invoke(): Something = TODO()
	}
}
