

## test

```
mvn clean install; (cd integration-tests/; mvn clean test)
```

TODO [docs](https://quarkus.io/guides/writing-extensions#multi-module-maven-projects-and-the-development-mode) mention that the example project can be part of the multi-module project, but we wouldn't want to deploy it to central

## native test

```
NATIVE_BUILD_CONF="-Dquarkus.native.remote-container-build=true"
NATIVE_BUILD_CONF="$NATIVE_BUILD_CONF -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel:21.3.0.0-Final-java11"
mvn install; (cd integration-tests/; mvn verify -Pnative $NATIVE_BUILD_CONF)
```

## Eclipse

- https://marketplace.eclipse.org/content/editorconfig-eclipse
- https://quarkus.io/guides/writing-extensions#writing-quarkus-extensions-in-eclipse
