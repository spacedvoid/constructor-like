package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

object NamedForClassInObject {
	class InObject

	@ConstructorLike
	fun InObject(): InObject = TODO()
}
