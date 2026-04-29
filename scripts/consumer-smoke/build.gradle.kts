import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import java.util.Properties

plugins {
    java
}

group = "com.omaarr90.statecraft.smoke"
version = "0.1.0"

val statecraftRootProperties =
    Properties().apply {
        val file = rootProject.projectDir.resolve("../../gradle.properties").normalize()
        if (file.isFile) {
            file.inputStream().use(::load)
        }
    }
val useGitHubPackages =
    providers
        .gradleProperty("statecraft.githubPackages")
        .orElse(providers.environmentVariable("STATECRAFT_GITHUB_PACKAGES"))
        .map { it.toBoolean() }
        .getOrElse(false)
val statecraftGroup =
    providers
        .gradleProperty("statecraft.group")
        .orElse(providers.environmentVariable("STATECRAFT_GROUP"))
        .orElse(statecraftRootProperties.getProperty("GROUP", "com.omaarr90.statecraft"))
val statecraftVersion =
    providers
        .gradleProperty("statecraft.version")
        .orElse(providers.environmentVariable("STATECRAFT_VERSION"))
        .orElse(statecraftRootProperties.getProperty("VERSION_NAME", "0.1.0"))

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
    implementation("${statecraftGroup.get()}:statecraft-engines:${statecraftVersion.get()}")

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
