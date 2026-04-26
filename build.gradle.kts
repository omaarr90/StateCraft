plugins {
    alias(libs.plugins.spotless)
}

apply(from = "gradle/benchmark-validation.gradle.kts")

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
    group = "com.omaarr90.statecraft"
    version = "0.1.0"

    plugins.withType<JavaPlugin> {
        the<JavaPluginExtension>().toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
    }
}
