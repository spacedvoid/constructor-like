package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class InvokeForClassInCompanion {
	companion object {
		class InCompanion {
			companion object {
				@ConstructorLike
				operator fun invoke(): InCompanion = TODO()
			}
		}
	}
}
