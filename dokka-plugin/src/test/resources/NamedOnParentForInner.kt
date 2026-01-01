package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class NamedOnParentForInner {
	inner class InnerClass

	@ConstructorLike
	fun InnerClass(): InnerClass = TODO()
}
