plugins {
    `java-library`
    alias(libs.plugins.vanniktech.maven.publish)
}

val hasSigningCredentials =
    providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.secretKeyRingFile").isPresent

dependencies {
    implementation(libs.jackson.databind)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    // Required in some environments to launch JUnit Platform (Gradle 9/IDE integration)
    testRuntimeOnly(libs.junit.platform.launcher)
}

mavenPublishing {
    coordinates(project.group.toString(), "statecraft-core", project.version.toString())
    publishToMavenCentral()
    if (hasSigningCredentials) {
        signAllPublications()
    }

    pom {
        name.set("StateCraft Core")
        description.set("Core circuit, parser, math, measurement, and noise APIs for StateCraft quantum simulations.")
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
