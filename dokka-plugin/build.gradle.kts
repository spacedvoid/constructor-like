plugins {
	kotlin("jvm")
}

repositories {
	mavenCentral()
}

dependencies {
	compileOnly("org.jetbrains.dokka:dokka-core:2.1.0")
	implementation("org.jetbrains.dokka:dokka-base:2.1.0")
	testImplementation(kotlin("test"))
	testImplementation("org.jetbrains.dokka:dokka-test-api:2.1.0")
	testRuntimeOnly("org.jetbrains.dokka:analysis-kotlin-symbols:2.1.0")
	testImplementation("org.jetbrains.dokka:dokka-base-test-utils:2.1.0")
}

tasks.test {
	useJUnitPlatform()
}

kotlin {
	jvmToolchain(21)
}
