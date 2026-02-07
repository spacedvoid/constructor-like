package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

object InvokeForClassInObject {
	class InObject {
		companion object {
			@ConstructorLike
            operator fun invoke(): InObject = TODO()
		}
	}
}
