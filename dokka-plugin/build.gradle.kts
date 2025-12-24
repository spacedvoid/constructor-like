plugins {
	kotlin("jvm")
}

repositories {
	mavenCentral()
}

dependencies {
	compileOnly("org.jetbrains.dokka:dokka-core:2.1.0")
	implementation("org.jetbrains.dokka:dokka-base:2.1.0")
}

kotlin {
	jvmToolchain(21)
}
