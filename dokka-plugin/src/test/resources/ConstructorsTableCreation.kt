package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class ConstructorsTableCreation private constructor() {
	companion object {
		@ConstructorLike
		operator fun invoke(): ConstructorsTableCreation = TODO()
	}
}
