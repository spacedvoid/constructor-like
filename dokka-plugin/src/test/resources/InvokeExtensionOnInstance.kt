package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class InvokeExtensionOnInstance

@ConstructorLike
operator fun InvokeExtensionOnInstance.invoke(): InvokeExtensionOnInstance = TODO()
