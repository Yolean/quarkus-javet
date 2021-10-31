

## The problem

Javet's built-in lib loading uses varying paths under `/tmp/javet`.
While using /tmp for a JNI lib is fine (quarkus-kafka does that too)
the difference in path is difficult to manage because native builds
initialize classes at build time.
The native-image binary is later copied to a container image,
without an accompanying file system.

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
# With both linux lib files included it's at 120M
du -sh integration-tests/target/quarkus-javet-integration-tests-1.0.0-SNAPSHOT-runner
# A libjavet file should probably not be present in native image build input because it's embedded with the javet jar
unzip -lv integration-tests/target/quarkus-javet-integration-tests-1.0.0-SNAPSHOT-native-image-source-jar/quarkus-javet-integration-tests-1.0.0-SNAPSHOT-runner.jar | grep libjavet
ls -l integration-tests/target/quarkus-javet-integration-tests-1.0.0-SNAPSHOT-native-image-source-jar/lib/ | grep com.caoccao.javet.javet
```

## Current status

Runtime crashes at:
https://github.com/oracle/graal/blob/vm-ce-21.3.0/substratevm/src/com.oracle.svm.jni/src/com/oracle/svm/jni/functions/JNIFunctions.java#L1095

Attempts to disable lib loading fail, so native compile prints this error (but continues):

```
05:32:39,507 INFO  [QuarkusJavetStaticInit] Disabling built-in lib loading
05:32:39,529 INFO  [JavetLibLoadingSetup] Disabling Javet's built-in lib loader
05:33:01,386 SEVERE [com.cao.jav.int.loa.JavetLibLoader] /tmp/javet/52/libjavet-v8-linux-x86_64.v.1.0.2.so: /lib64/libstdc++.so.6: version `GLIBCXX_3.4.26 not found (required by /tmp/javet/52/libjavet-v8-linux-x86_64.v.1.0.2.so)
05:33:01,391 SEVERE [com.cao.jav.int.loa.JavetLibLoader] java.lang.UnsatisfiedLinkError: /tmp/javet/52/libjavet-v8-linux-x86_64.v.1.0.2.so: /lib64/libstdc++.so.6: version `GLIBCXX_3.4.26' not found (required by /tmp/javet/52/libjavet-v8-linux-x86_64.v.1.0.2.so)
	at java.base/java.lang.ClassLoader$NativeLibrary.load0(Native Method)
	at java.base/java.lang.ClassLoader$NativeLibrary.load(ClassLoader.java:2442)
	at java.base/java.lang.ClassLoader$NativeLibrary.loadLibrary(ClassLoader.java:2498)
	at java.base/java.lang.ClassLoader.loadLibrary0(ClassLoader.java:2694)
	at java.base/java.lang.ClassLoader.loadLibrary(ClassLoader.java:2627)
	at java.base/java.lang.Runtime.load0(Runtime.java:768)
	at java.base/java.lang.System.load(System.java:1837)
	at com.caoccao.javet.interop.loader.JavetLibLoader.load(JavetLibLoader.java:349)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.base/java.lang.reflect.Method.invoke(Method.java:566)
	at com.caoccao.javet.interop.JavetClassLoader.load(JavetClassLoader.java:85)
	at com.caoccao.javet.interop.V8Host.loadLibrary(V8Host.java:417)
	at com.caoccao.javet.interop.V8Host.<init>(V8Host.java:67)
	at com.caoccao.javet.interop.V8Host.<init>(V8Host.java:41)
	at com.caoccao.javet.interop.V8Host$V8InstanceHolder.<clinit>(V8Host.java:478)
```

## TODO option for not embedding libs

For runtime images that can embed the two libjavet .so files
it saves aboud 70MB of native-image binary size if the base image
contains libjavet files. They must match the Javet version.

## Eclipse

- https://marketplace.eclipse.org/content/editorconfig-eclipse
- https://quarkus.io/guides/writing-extensions#writing-quarkus-extensions-in-eclipse
