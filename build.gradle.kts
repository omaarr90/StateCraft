plugins {
    alias(libs.plugins.spotless)
}

apply(from = "gradle/benchmark-validation.gradle.kts")

val statecraftGroup = providers.gradleProperty("GROUP").orElse("com.omaarr90.statecraft")
val statecraftVersion = providers.gradleProperty("VERSION_NAME").orElse("1.0.0")

spotless {
    java {
        target("**/src/*/java/**/*.java")
        eclipse()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        ktlint()
    }
    format("misc") {
        target("**/*.md", ".gitignore")
        targetExclude("**/build/**", "**/bin/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

allprojects {
    repositories { mavenCentral() }
}

subprojects {
    group = statecraftGroup.get()
    version = statecraftVersion.get()

    plugins.withType<JavaPlugin> {
        the<JavaPluginExtension>().toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
    }
}
