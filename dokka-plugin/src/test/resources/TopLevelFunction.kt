package io.github.spacedvoid.constructorlike

annotation class ConstructorLike

class TopLevelFunction

@ConstructorLike
fun TopLevelFunction(): TopLevelFunction = TODO()
