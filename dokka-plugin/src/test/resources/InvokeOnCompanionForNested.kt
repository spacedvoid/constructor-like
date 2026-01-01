package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class InvokeOnCompanionForNested {
	companion object {
		@ConstructorLike
		operator fun invoke(): NestedClass = TODO()
	}

	class NestedClass
}
