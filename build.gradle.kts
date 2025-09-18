plugins { /* per-module */ }

allprojects {
    repositories { mavenCentral() }
}

subprojects {
    group = "com.omaarr90.statecraft"
    version = "0.1.0-SNAPSHOT"

    plugins.withType<JavaPlugin> {
        the<JavaPluginExtension>().toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
    }
}
