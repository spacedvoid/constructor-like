package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class NestedTarget {
	companion object {
		@ConstructorLike
		fun NestedClass(): NestedClass = TODO()
	}

	class NestedClass
}
