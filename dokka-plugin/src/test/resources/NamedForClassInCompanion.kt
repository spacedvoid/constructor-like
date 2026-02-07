package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class NamedForClassInCompanion {
	companion object {
		class InCompanion

		@ConstructorLike
		fun InCompanion(): InCompanion = TODO()
	}
}
