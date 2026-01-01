package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class InvokeOnNonCompanion {
	class NestedClass {
		@ConstructorLike
		operator fun invoke(): InvokeOnNonCompanion = TODO()
	}
}
