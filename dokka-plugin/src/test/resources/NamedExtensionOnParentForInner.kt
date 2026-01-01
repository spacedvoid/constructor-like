package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class NamedExtensionOnParentForInner {
	inner class InnerClass

	@ConstructorLike
	fun Any.InnerClass(): InnerClass = TODO()
}
