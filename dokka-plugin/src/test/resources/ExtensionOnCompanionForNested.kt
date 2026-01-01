package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class ExtensionOnCompanionForNested {
	companion object {
		@ConstructorLike
		fun ExtensionOnCompanionForNested.Companion.NestedClass(): NestedClass = TODO()
	}

	class NestedClass
}
