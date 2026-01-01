package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class NamedOnCompanionForInner {
	companion object {
		@ConstructorLike
		fun Inner(): Inner = TODO()
	}

	inner class Inner
}
