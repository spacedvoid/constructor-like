package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class InvokeOnCompanionForInner {
	companion object {
		@ConstructorLike
		operator fun invoke(): Inner = TODO()
	}

	inner class Inner
}
