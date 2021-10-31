# Quarkus Javet Extension

The goal is to take care of native builds and [CDI](https://quarkus.io/guides/cdi-reference) aspects so that the [Javet](https://www.caoccao.com/Javet/) library can stay framework agnostic and Quarkus-based applications can do

```java
  @Inject
  V8Host v8host; // V8 mode

  @Inject
  @Named("Node") // See JSRuntimeType
  V8Host v8host;
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

With built-in JNI library loading replaced with build-time Quarkus processing,

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

the test instead fails on:

```
2021-10-31 08:34:32,285 INFO  [JavetLibLoadingSetup] (main) Disabling Javet's built-in lib loader
2021-10-31 08:34:32,299 INFO  [QuarkusJavetRecorder] (main) Loading /tmp/libjavet-v8-linux-x86_64.v.1.0.2.so
2021-10-31 08:34:32,302 INFO  [JavetLibLoadingSetup] (main) Disabling Javet's built-in lib loader
2021-10-31 08:34:32,336 INFO  [QuarkusJavetRecorder] (main) Loading /tmp/libjavet-node-linux-x86_64.v.1.0.2.so
2021-10-31 08:34:32,349 INFO  [io.quarkus] (main) quarkus-javet-integration-tests 1.0.0-SNAPSHOT native (powered by Quarkus 2.4.0.Final) started in 0.068s. Listening on: http://0.0.0.0:8081
2021-10-31 08:34:32,349 INFO  [io.quarkus] (main) Profile prod activated. 
2021-10-31 08:34:32,349 INFO  [io.quarkus] (main) Installed features: [cdi, javet, resteasy, smallrye-context-propagation, vertx]
[ERROR] WARNING: An illegal reflective access operation has occurred
[ERROR] WARNING: Illegal reflective access by org.codehaus.groovy.vmplugin.v9.Java9 (file:/home/solsson/.m2/repository/org/codehaus/groovy/groovy/3.0.8/groovy-3.0.8.jar) to constructor java.lang.AssertionError(java.lang.String)
[ERROR] WARNING: Please consider reporting this to the maintainers of org.codehaus.groovy.vmplugin.v9.Java9
[ERROR] WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
[ERROR] WARNING: All illegal access operations will be denied in a future release
2021-10-31 08:34:33,391 ERROR [io.qua.ver.htt.run.QuarkusErrorHandler] (executor-thread-0) HTTP Request to /quarkus-javet/v8 failed, error id: b5e14f63-5eaf-4f21-aff9-0f25f8a1d485-1: org.jboss.resteasy.spi.UnhandledException: com.caoccao.javet.exceptions.JavetException: Javet library is not loaded because <null>
	at org.jboss.resteasy.core.ExceptionHandler.handleApplicationException(ExceptionHandler.java:106)
	at org.jboss.resteasy.core.ExceptionHandler.handleException(ExceptionHandler.java:372)
	at org.jboss.resteasy.core.SynchronousDispatcher.writeException(SynchronousDispatcher.java:218)
	at org.jboss.resteasy.core.SynchronousDispatcher.invoke(SynchronousDispatcher.java:519)
	at org.jboss.resteasy.core.SynchronousDispatcher.lambda$invoke$4(SynchronousDispatcher.java:261)
	at org.jboss.resteasy.core.SynchronousDispatcher.lambda$preprocess$0(SynchronousDispatcher.java:161)
	at org.jboss.resteasy.core.interception.jaxrs.PreMatchContainerRequestContext.filter(PreMatchContainerRequestContext.java:364)
	at org.jboss.resteasy.core.SynchronousDispatcher.preprocess(SynchronousDispatcher.java:164)
	at org.jboss.resteasy.core.SynchronousDispatcher.invoke(SynchronousDispatcher.java:247)
	at io.quarkus.resteasy.runtime.standalone.RequestDispatcher.service(RequestDispatcher.java:73)
	at io.quarkus.resteasy.runtime.standalone.VertxRequestHandler.dispatch(VertxRequestHandler.java:135)
	at io.quarkus.resteasy.runtime.standalone.VertxRequestHandler$1.run(VertxRequestHandler.java:90)
	at io.quarkus.vertx.core.runtime.VertxCoreRecorder$13.runWith(VertxCoreRecorder.java:543)
	at org.jboss.threads.EnhancedQueueExecutor$Task.run(EnhancedQueueExecutor.java:2449)
	at org.jboss.threads.EnhancedQueueExecutor$ThreadBody.run(EnhancedQueueExecutor.java:1478)
	at org.jboss.threads.DelegatingRunnable.run(DelegatingRunnable.java:29)
	at org.jboss.threads.ThreadLocalResettingRunnable.run(ThreadLocalResettingRunnable.java:29)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.lang.Thread.run(Thread.java:829)
	at com.oracle.svm.core.thread.JavaThreads.threadStartRoutine(JavaThreads.java:596)
	at com.oracle.svm.core.posix.thread.PosixJavaThreads.pthreadStartRoutine(PosixJavaThreads.java:192)
Caused by: com.caoccao.javet.exceptions.JavetException: Javet library is not loaded because <null>
Caused by: java.lang.reflect.InvocationTargetException
Caused by: com.caoccao.javet.exceptions.JavetException: Failed to read /tmp/javet/52/libjavet-v8-linux-x86_64.v.1.0.2.so
Caused by: java.lang.UnsatisfiedLinkError: /tmp/javet/52/libjavet-v8-linux-x86_64.v.1.0.2.so: /lib64/libstdc++.so.6: version `GLIBCXX_3.4.26' not found (required by /tmp/javet/52/libjavet-v8-linux-x86_64.v.1.0.2.so)
```

## Devloop

Without native build (this mode currently doesn't do anything useful);

```
mvn clean install && (cd integration-tests/; mvn clean test)
```

## Native test

Quarkus [docs](https://quarkus.io/guides/writing-extensions#multi-module-maven-projects-and-the-development-mode) mention that the example project can be part of the multi-module project, but we wouldn't want to deploy it to central. Until the matter is settled we build them using the one-liner below:

```
# Opts depend on the build and test environment
NATIVE_BUILD_OPTS="-Dquarkus.native.remote-container-build=true"
NATIVE_BUILD_OPTS="$NATIVE_BUILD_OPTS -Dquarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel:21.3-java11"
NATIVE_BUILD_OPTS="$NATIVE_BUILD_OPTS -Dquarkus.native.enable-reports=true"
# Get stdout from container-build docker run
NATIVE_BUILD_OPTS="$NATIVE_BUILD_OPTS -Dquarkus.native.container-runtime-options=-ti"
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
