import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    alias(libs.plugins.vanniktech.maven.publish)
}

val useGpgCmdForSigning =
    providers.gradleProperty("signing.useGpgCmd").map(String::toBoolean).orElse(false)
val hasSigningCredentials =
    providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.secretKeyRingFile").isPresent ||
        useGpgCmdForSigning.get()

dependencies {
    api(project(":core"))
    implementation(libs.ejml.ddense)
    implementation(libs.ejml.zdense)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--enable-preview", "--add-modules", "jdk.incubator.vector"))
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview", "--add-modules", "jdk.incubator.vector")
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("-add-modules", "jdk.incubator.vector")
}

mavenPublishing {
    coordinates(project.group.toString(), "statecraft-engines", project.version.toString())
    publishToMavenCentral()
    if (hasSigningCredentials) {
        signAllPublications()
    }

    pom {
        name.set("StateCraft Engines")
        description.set("StateCraft quantum simulator engines exposed through Java ServiceLoader.")
        inceptionYear.set("2025")
        url.set("https://github.com/omaarr90/StateCraft")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("omaarr90")
                name.set("omaarr90")
                url.set("https://github.com/omaarr90")
            }
        }

        scm {
            url.set("https://github.com/omaarr90/StateCraft")
            connection.set("scm:git:https://github.com/omaarr90/StateCraft.git")
            developerConnection.set("scm:git:ssh://git@github.com/omaarr90/StateCraft.git")
        }
    }
}

plugins.withId("signing") {
    configure<org.gradle.plugins.signing.SigningExtension> {
        if (useGpgCmdForSigning.get()) {
            useGpgCmd()
        }
    }
}

publishing {
    repositories {
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
    }
}
