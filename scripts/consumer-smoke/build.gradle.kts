import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

plugins {
    java
}

group = "com.omaarr90.statecraft.smoke"
version = "0.1.0"

val useGitHubPackages =
    providers
        .gradleProperty("statecraft.githubPackages")
        .orElse(providers.environmentVariable("STATECRAFT_GITHUB_PACKAGES"))
        .map { it.toBoolean() }
        .getOrElse(false)

repositories {
    if (useGitHubPackages) {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/omaarr90/StateCraft")
            credentials {
                username =
                    providers
                        .gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                        .orNull
                password =
                    providers
                        .gradleProperty("gpr.key")
                        .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                        .orNull
            }
        }
    } else {
        mavenLocal()
    }
    mavenCentral()
}

dependencies {
    implementation("com.omaarr90.statecraft:statecraft-engines:0.1.0")

    testImplementation(platform("org.junit:junit-bom:5.12.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--enable-preview", "--add-modules", "jdk.incubator.vector"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-preview", "--add-modules", "jdk.incubator.vector")
}
