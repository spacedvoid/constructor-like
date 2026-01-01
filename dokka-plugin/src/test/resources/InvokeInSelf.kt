package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class InvokeInSelf {
	@ConstructorLike
	operator fun invoke(): InvokeInSelf = TODO()
}
