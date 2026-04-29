# API Reference

The API reference is generated from Javadocs during the GitHub Pages build.

- [Core Javadocs](api/core/)
- [Engines Javadocs](api/engines/)

Build the same reference locally with:

```sh
./gradlew :core:javadoc :engines:javadoc
rm -rf docs/api/core docs/api/engines
mkdir -p docs/api/core docs/api/engines
cp -R core/build/docs/javadoc/. docs/api/core/
cp -R engines/build/docs/javadoc/. docs/api/engines/
mkdocs build --strict --site-dir site
```
