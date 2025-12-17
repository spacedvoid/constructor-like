plugins {
	kotlin("jvm")
	kotlin("plugin.serialization") version "2.2.20"
}

repositories {
	mavenCentral()
}

dependencies {
	compileOnly("org.jetbrains.dokka:dokka-core:2.1.0")
	compileOnly("org.jetbrains.dokka:dokka-base:2.1.0")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
	implementation(kotlin("reflect"))
}

kotlin {
	jvmToolchain(21)
}
