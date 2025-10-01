plugins { `java-library` }

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    // Required in some environments to launch JUnit Platform (Gradle 9/IDE integration)
    testRuntimeOnly(libs.junit.platform.launcher)
}
