# Quarkus Javet Extension

The goal is to take care of native builds and [CDI](https://quarkus.io/guides/cdi-reference) aspects so that the [Javet](https://www.caoccao.com/Javet/) library can stay framework agnostic and Quarkus-based applications can do

```java
  @Inject
  V8Host v8host; // V8 mode
```

Also the extension should support [native image builds](https://quarkus.io/guides/building-native-image).
The resulting binary should be compatible with [distroless](https://quarkus.io/guides/building-native-image#using-a-distroless-base-image]) images.

## Current status

First goal is to tweak/override JNI lib loading so that it works in Linux x86 native images. We're not there.

[Javet's way to customize lib loading](https://www.caoccao.com/Javet/reference/resource_management/load_and_unload.html#can-javet-native-library-be-deployed-to-a-custom-location) gets complex when combined with how GraalVM, and to some degree Quarkus, configures and instantiates classes at build time.

### Library loading

The Quarkus extension works insofar that it propagates the Javet dependency
and that native-builds contain the two Linx x86 .so library resources.

### Native compile

Despite attempts to [disable built-in library loading](https://www.caoccao.com/Javet/reference/resource_management/load_and_unload.html#can-javet-native-library-deployment-be-skipped) native compile prints errors such as `java.lang.UnsatisfiedLinkError: /tmp/javet/52/libjavet-node-linux-x86_64.v.1.0.2.so: /lib64/libm.so.6: version `GLIBC_2.29' not found (required by /tmp/javet/52/libjavet-node-linux-x86_64.v.1.0.2.so)`. However the libraries get included anyway,
so this is probably not a blocker.

### Runtime library loading

Without jni-config.json (OR the processor adding JniRuntimeAccessBuildItem?) we get an NPE from [JNIFunctions.java](https://github.com/oracle/graal/blob/vm-ce-21.3.0/substratevm/src/com.oracle.svm.jni/src/com/oracle/svm/jni/functions/JNIFunctions.java#L1095) that doesn't say which class is missing.

With [jni-config.json](runtime/src/main/resources/META-INF/native-image/jni-config.json) generated through [native tracing](#nativetracing) native compile bails with:

```
Error: Error parsing JNI configuration in jar:file:/project/lib/se.yolean.javet.quarkus-javet-1.0.0-SNAPSHOT.jar!/META-INF/native-image/jni-config.json:
Method com.caoccao.javet.values.reference.IV8Module.getHandle() not found. To allow unresolvable reflection configuration, use option --allow-incomplete-classpath
Verify that the configuration matches the schema described in the -H:PrintFlags=+ output for option JNIConfigurationResources.
com.oracle.svm.core.util.UserError$UserException: Error parsing JNI configuration in jar:file:/project/lib/se.yolean.javet.quarkus-javet-1.0.0-SNAPSHOT.jar!/META-INF/native-image/jni-config.json:
Method com.caoccao.javet.values.reference.IV8Module.getHandle() not found. To allow unresolvable reflection configuration, use option --allow-incomplete-classpath
Verify that the configuration matches the schema described in the -H:PrintFlags=+ output for option JNIConfigurationResources.
	at com.oracle.svm.core.util.UserError.abort(UserError.java:73)
	at com.oracle.svm.hosted.config.ConfigurationParserUtils.doParseAndRegister(ConfigurationParserUtils.java:135)
```

BUT with the following patch:

```
diff --git a/runtime/src/main/resources/META-INF/native-image/jni-config.json b/runtime/src/main/resources/META-INF/native-image/jni-config.json
index 924061a..b803448 100644
--- a/runtime/src/main/resources/META-INF/native-image/jni-config.json
+++ b/runtime/src/main/resources/META-INF/native-image/jni-config.json
@@ -131,7 +131,7 @@
 ,
 {
   "name":"com.caoccao.javet.values.reference.IV8Module",
-  "methods":[{"name":"getHandle","parameterTypes":[] }]}
+  "allPublicMethods":true}
 ,
 {
   "name":"com.caoccao.javet.values.reference.IV8ValueReference",
```

## Devloop

Without native build (this mode currently doesn't do anything useful);

```
mvn clean install && (cd integration-tests/; mvn clean test)
```

## Builder image

For builds to be platform independent we must use Quarkus' "container build" mode
instead of the default local build.
Howerver that mode doesn't seem to support our dependence on lib files
extracted to /tmp during build "recording".

```
BUILDER_BASE=quay.io/quarkus/ubi-quarkus-mandrel:21.3-java11
mvn dependency:unpack-dependencies -DincludeGroupIds=com.caoccao.javet
cat <<EOF >>runtime/target/dependency/.dockerignore
*
!*.so
EOF
cat <<EOF >runtime/target/dependency/Dockerfile
FROM $BUILDER_BASE as mandrel
FROM ubuntu:20.04 as builder
RUN set -ex; \
  export DEBIAN_FRONTEND=noninteractive; \
  apt-get update && apt-get install -y \
    gcc \
    g++ \
    libc-dev \
    zlib1g-dev \
    --no-install-recommends; \
  \
  rm -rf /var/lib/apt/lists; \
  rm -rf /var/log/dpkg.log /var/log/alternatives.log /var/log/apt /root/.gnupg
COPY --from=mandrel /opt/mandrel /opt/mandrel
ENV \
  CI=true \
  GRAALVM_HOME=/opt/mandrel \
  JAVA_HOME=/opt/mandrel \
  MAVEN_CONFIG=/home/nonroot/.m2 \
  PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/opt/mandrel/bin
# https://github.com/caoccao/Javet/discussions/26
#RUN strings /lib64/libstdc++.so.6 | grep GLIBCXX_3.4.26
# docker inspect quay.io/quarkus/ubi-quarkus-mandrel:21.2.0.1-Final-java11
RUN grep 'quarkus:x:1001' /etc/passwd || \
  echo 'quarkus:x:1001:65534:Quarkus:/home/quarkus:/usr/sbin/nologin' >> /etc/passwd && \
  mkdir -p /home/quarkus && touch /home/quarkus/.bash_history && chown -R 1001:65534 /home/quarkus
USER 1001:nogroup
ENTRYPOINT ["native-image"]
#VOLUME /project
WORKDIR /project
COPY *.so /tmp/
RUN ls -l /tmp/
EOF
docker build -t quarkus-javet-builder:local runtime/target/dependency/
```

TODO maybe it's dependency libs that are missing at build time:

```
The bundle named: messages, has not been found. If the bundle is part of a module, verify the bundle name is a fully qualified class name. Otherwise verify the bundle path is accessible in the classpath.
04:26:25,635 SEVERE [com.cao.jav.int.loa.JavetLibLoader] /tmp/libjavet-node-linux-x86_64.v.1.0.3.so: /lib64/libm.so.6: version `GLIBC_2.29 not found (required by /tmp/libjavet-node-linux-x86_64.v.1.0.3.so)
04:26:25,641 SEVERE [com.cao.jav.int.loa.JavetLibLoader] /tmp/libjavet-v8-linux-x86_64.v.1.0.3.so: /lib64/libstdc++.so.6: version `GLIBCXX_3.4.26 not found (required by /tmp/libjavet-v8-linux-x86_64.v.1.0.3.so)
04:26:25,646 SEVERE [com.cao.jav.int.loa.JavetLibLoader] java.lang.UnsatisfiedLinkError: /tmp/libjavet-node-linux-x86_64.v.1.0.3.so: /lib64/libm.so.6: version `GLIBC_2.29' not found (required by /tmp/libjavet-node-linux-x86_64.v.1.0.3.so)
```

## Native test

Quarkus [docs](https://quarkus.io/guides/writing-extensions#multi-module-maven-projects-and-the-development-mode) mention that the example project can be part of the multi-module project, but we wouldn't want to deploy it to central. Until the matter is settled we build them using the one-liner below:

```
# Opts depend on the build and test environment
NATIVE_BUILD_OPTS="-Dquarkus.native.remote-container-build=true"
NATIVE_BUILD_OPTS="$NATIVE_BUILD_OPTS -Dquarkus.native.builder-image=quarkus-javet-builder:local"
NATIVE_BUILD_OPTS="$NATIVE_BUILD_OPTS -Dquarkus.native.enable-reports=true"
# Get stdout from container-build docker run; workaround for remote build volume not using builder's user
NATIVE_BUILD_OPTS="$NATIVE_BUILD_OPTS -Dquarkus.native.container-runtime-options=-ti,--user=0:0"
# https://github.com/caoccao/Javet/commit/a7dc048b665166d77c532f066281282fb7cdb1de
#NATIVE_BUILD_OPTS="$NATIVE_BUILD_OPTS -Djavet.lib.loading.type=system"
NATIVE_BUILD_OPTS="$NATIVE_BUILD_OPTS -Dquarkus.native.additional-build-args=-J-Djavet.lib.loading.type=custom,-J-Djavet.lib.loading.path=/tmp"
# How do we avoid custom lib loading in non-native builds and tests?
EXTENSION_BULID_OPTS="-Dmaven.test.skip=true"
# Run the test
mvn clean install $EXTENSION_BULID_OPTS && (cd integration-tests/; mvn clean verify -Pnative $NATIVE_BUILD_OPTS)

# The quarkus-resteasy hello example executable gets size 50M and we expect the library to be included
# With both linux lib files included it's at 120M
du -sh integration-tests/target/quarkus-javet-integration-tests-1.0.0-SNAPSHOT-runner
```

## TODO option for not embedding libs

For runtime images that can embed the two libjavet .so files
it saves aboud 70MB of native-image binary size if the base image
contains libjavet files. They must match the Javet version.

## Eclipse

- https://marketplace.eclipse.org/content/editorconfig-eclipse
- https://quarkus.io/guides/writing-extensions#writing-quarkus-extensions-in-eclipse

## Nativetracing

With GraalVM/Mandrel in path:

```
cd integration-tests
mvn clean package
JAVA_ARGS="-agentlib:native-image-agent=config-merge-dir=../runtime/src/main/resources/META-INF/native-image"
java $JAVA_ARGS -jar target/quarkus-app/quarkus-run.jar
curl http://localhost:8080/quarkus-javet/v8
curl http://localhost:8080/quarkus-javet/node
# stop the Quarkus process, then
cd ..
# only use jni-config.json
rm runtime/src/main/resources/META-INF/native-image/proxy-config.json
rm runtime/src/main/resources/META-INF/native-image/resource-config.json
rm runtime/src/main/resources/META-INF/native-image/predefined-classes-config.json
rm runtime/src/main/resources/META-INF/native-image/reflect-config.json
rm runtime/src/main/resources/META-INF/native-image/serialization-config.json

git diff src/main/resources/*.json
```
