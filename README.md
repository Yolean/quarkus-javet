

## test

```
mvn clean install; (cd integration-tests/; mvn clean test)
```

TODO [docs](https://quarkus.io/guides/writing-extensions#multi-module-maven-projects-and-the-development-mode) mention that the example project can be part of the multi-module project, but we wouldn't want to deploy it to central

## native test

```
NATIVE_BUILD_OPTS="-Dquarkus.native.remote-container-build=true"
NATIVE_BUILD_OPTS="$NATIVE_BUILD_OPTS -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel:21.3.0.0-Final-java11@sha256:0ad1f11718d7a94e97d368f13c93a6b52eec82fb3d196c0fb2593a9685894ffa"
NATIVE_BUILD_OPTS="$NATIVE_BUILD_OPTS -Dquarkus.native.enable-reports=true"
# Get stdout from container-build docker run
NATIVE_BUILD_OPTS="$NATIVE_BUILD_OPTS -Dquarkus.native.container-runtime-options=-ti"
# How do we avoid custom lib loading in non-native builds and tests?
EXTENSION_BULID_OPTS="-Dmaven.test.skip=true"
mvn clean install $EXTENSION_BULID_OPTS && (cd integration-tests/; mvn clean verify -Pnative $NATIVE_BUILD_OPTS)

# The quarkus-resteasy hello example executable gets size 50M and we expect the library to be included
du -sh integration-tests/target/quarkus-javet-integration-tests-1.0.0-SNAPSHOT-runner
# A libjavet file should probably not be present in native image build input because it's embedded with the javet jar
unzip -lv integration-tests/target/quarkus-javet-integration-tests-1.0.0-SNAPSHOT-native-image-source-jar/quarkus-javet-integration-tests-1.0.0-SNAPSHOT-runner.jar | grep libjavet
ls -l integration-tests/target/quarkus-javet-integration-tests-1.0.0-SNAPSHOT-native-image-source-jar/lib/ | grep com.caoccao.javet.javet
```

## Eclipse

- https://marketplace.eclipse.org/content/editorconfig-eclipse
- https://quarkus.io/guides/writing-extensions#writing-quarkus-extensions-in-eclipse
