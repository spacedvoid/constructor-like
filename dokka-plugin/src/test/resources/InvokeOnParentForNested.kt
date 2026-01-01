package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class InvokeInCompanionForInner {
	class NestedClass

	@ConstructorLike
	operator fun invoke(): NestedClass = TODO()
}
