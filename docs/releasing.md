# Releasing StateCraft

StateCraft publishes two Maven artifacts:

- `com.omaarr90.statecraft:statecraft-core`
- `com.omaarr90.statecraft:statecraft-engines`

The `engines` artifact depends on `core`, so most consumers only need
`statecraft-engines` unless they are implementing their own simulator backend.

## Maven Central prerequisites

Before the first Maven Central release:

1. Create a Central Portal account and generate a user token.
2. Verify a namespace that matches the Gradle `GROUP` property.
3. Create a GPG signing key and publish the public key to a public keyserver.
4. Add the release secrets to the GitHub repository.

The current default group is `com.omaarr90.statecraft`, configured in
`gradle.properties`. Maven Central requires that this namespace is verified in
the Central Portal. If that namespace is not available, switch `GROUP` before
the first Central release, for example to a verified `io.github.omaarr90`
namespace. Do not change the group after publishing a public release unless you
intend to move consumers to new coordinates.

## GitHub secrets

Configure these repository secrets before running `.github/workflows/release.yml`:

- `MAVEN_CENTRAL_USERNAME`: Central Portal user-token username.
- `MAVEN_CENTRAL_PASSWORD`: Central Portal user-token password.
- `SIGNING_IN_MEMORY_KEY`: ASCII-armored private GPG key.
- `SIGNING_IN_MEMORY_KEY_ID`: GPG key id. This can be empty if Gradle can infer it.
- `SIGNING_IN_MEMORY_KEY_PASSWORD`: GPG key password, or empty for an unprotected key.

You can export an armored private key with:

```sh
gpg --export-secret-keys --armor <KEY_ID>
```

## Local release check

Run the same checks used by release CI before cutting a tag:

```sh
./gradlew spotlessCheck test :core:javadoc :engines:javadoc publishToMavenLocal --stacktrace
./gradlew -p scripts/consumer-smoke test --stacktrace
```

To test a future version locally without editing files:

```sh
./gradlew -PVERSION_NAME=1.0.1 publishToMavenLocal
./gradlew -p scripts/consumer-smoke -Pstatecraft.version=1.0.1 test
```

## Publishing from GitHub Actions

Recommended path:

```sh
git tag v1.0.0
git push origin v1.0.0
```

The release workflow strips the leading `v` and publishes `VERSION_NAME=1.0.0`.
You can also run the workflow manually and provide the version input.

Maven Central versions are immutable. If a release fails after Central accepts a
version, publish the fix with a new version rather than reusing the old one.

## Publishing locally

GitHub Actions should be the normal release path, but a local Central release can
be run with the same Gradle properties:

```sh
ORG_GRADLE_PROJECT_mavenCentralUsername=<central-token-username> \
ORG_GRADLE_PROJECT_mavenCentralPassword=<central-token-password> \
ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --export-secret-keys --armor <KEY_ID>)" \
ORG_GRADLE_PROJECT_signingInMemoryKeyId=<KEY_ID> \
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=<key-password> \
./gradlew -PVERSION_NAME=1.0.0 publishAndReleaseToMavenCentral --stacktrace
```

References:

- [Maven Central publishing requirements](https://central.sonatype.org/publish/requirements/)
- [Central Portal Gradle publishing guide](https://central.sonatype.org/publish/publish-portal-gradle/)
- [Vanniktech Maven Publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/)
