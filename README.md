# ConstructorLike

Dokka plugin for documenting pseudo-constructors for Kotlin.

### Pseudo-constructors?

Most object-oriented languages have constructors that can be used to create an instance of a class.
But sometimes, we need some preprocessing with the arguments: validating, transforming, etc.
Java introduced [flexible constructor bodies](https://openjdk.org/jeps/513) for this purpose,
but Kotlin cannot have such feature because we're stuck at this C-style constructor syntax:

```kotlin
constructor(s: String): this(s.toInt())
```

Or sometimes, we might have an interface that can be instantiated by everyone, but giving a helper function would be great:

```kotlin
interface IntWrapper {
	val i: Int
}

fun TryThis(i: Int): IntWrapper = object: IntWrapper {
	override val i: Int = i
}
```

This leeds us to using functions that look like constructors:
1. `operator fun invoke` functions in companion objects or an extension to one
2. Top level functions with the same name([`fun Char()`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-char.html))

These are what we call pseudo-constructors, since they don't look different with normal constructors: `MyClass(<parameters>)`

Unfortunately, these are not directly shown in the class documentation, since they are technically not constructors.
Users cannot notice such functions if they are not explicitly documented at each class,
and by the nature of people about writing documentations, this is a problem.

This plugin aids this by injecting such elements annotated with `@ConstructorLike` to the `Constructors` table of a class documentation:
![example-doc](resources/images/example-doc.png)

Because they are *technically not constructors*, this is purely decorative:
navigating to such constructor leads to the actual definition of the pseudo-constructor,
which at the example above is an `operator fun invoke` in a companion object,
and the function's name is displayed at the first column of the `Constructor` table for when using it as a function reference(`::MyClass`).

### Specification details

To make a function as a pseudo-constructor, it must be annotated with `@ConstructorLike`.
Non-annotated elements will not be included.

The annotated function must return a class type which is not `kotlin.Unit`, `kotlin.Nothing`, an `annotation class`, `enum class`, or `object`.
The class type must be in the same module so that the plugin can inject the constructor to the documentation.
Also, the function must match one of the following:
1. It is an `operator fun invoke` in a companion object, which is not an extension and returns the type that owns the companion object that again owns the function.
2. It is a package-level `operator fun invoke` extension on a companion object, which returns the type that owns the companion object.
3. It is a package-level non-extension function that has the same name as the simple name of its return type.

If the function violates anything from above, the plugin will raise a warning and will not include the function as a pseudo-constructor.

Note that nested classes cannot be the target type, since the outer class usually works as a namespace and the nested class's name should be qualified.

<details>
<summary>Examples:</summary>

```kotlin
class MyClass { // Also applies to abstract classes and interfaces
	companion object {
		// OK
		@ConstructorLike
		operator fun invoke(): MyClass = TODO()
		
		// Bad: it is an extension function
		@ConstructorLike
		operator fun Any.invoke(): MyClass = TODO()
		
		// Bad: it does not return `MyClass`
		@ConstructorLike
		operator fun invoke(): Any = TODO()
		
		// Bad: these are not `operator fun invoke`
		@ConstructorLike
		fun invoke(): MyClass = TODO()
		@ConstructorLike
		fun create(): MyClass = TODO()
	}
	
	// Bad: it is in a class, not a companion object
	@ConstructorLike
	operator fun invoke(): MyClass = TODO()
}

// OK
@ConstructorLike
operator fun MyClass.Companion.invoke(): MyClass = TODO()

// Bad: it is not an extension function on `MyClass.Companion`
@ConstructorLike
operator fun Any.invoke(): MyClass = TODO()

// Bad: it does not return `MyClass`
@ConstructorLike
operator fun MyClass.Companion.invoke(): Any = TODO()

// Bad: these are not `operator fun invoke`
@ConstructorLike
fun MyClass.Companion.invoke(): MyClass = TODO()
@ConstructorLike
fun MyClass.Companion.create(): MyClass = TODO()

// OK
@ConstructorLike
fun MyClass(): MyClass = TODO()

// Bad: it is an extension function
@ConstructorLike
fun Any.MyClass(): MyClass = TODO()

// Bad: its name is not `MyClass`
@ConstructorLike
fun create(): MyClass = TODO()
```

</details>

There are no limitations about which package the function should be in, but it is recommended to place them in the same package as the class.

In case of `expect`/`actual` declarations, only the `expect` matters since `actual` declarations must match the signature of `expect`:
but make sure to annotate `actual` declarations with `@ConstructorLike`, otherwise the behavior of the plugin is undefined.

# License

This project's source code is *mainly* licensed with [MPL 2.0](LICENSE).
Note that some files have different licenses other than MPL:
currently, only [DefaultPageCreatorFunctions.kt](dokka-plugin/src/main/kotlin/DefaultPageCreatorFunctions.kt)
is licensed with [Apache 2.0](third-party-licenses/Kotlin/dokka/LICENSE.txt).

For more third-party license information, see [third-party-licenses/](third-party-licenses).
