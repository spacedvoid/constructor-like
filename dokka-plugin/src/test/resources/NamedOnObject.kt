package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

object NamedOnObject {
	class NestedClass

	@ConstructorLike
	fun NestedClass(): NestedClass = TODO()
}
