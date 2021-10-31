

## The problem

Javet's built-in lib loading uses varying paths under `/tmp/javet`.
While using /tmp for a JNI lib is fine (so does [quarkus-kafka](https://github.com/quarkusio/quarkus/blob/2.4.0.Final/extensions/kafka-client/runtime/src/main/java/io/quarkus/kafka/client/runtime/KafkaRecorder.java#L56))
the difference in path is difficult to manage because native builds
initialize classes at build time.
The native-image binary is later copied to a container image,
without an accompanying file system.

## Current status

Current ambition is to produce a linux x64 native-image that embeds libjavet*.so,
and could run on a distroless base image.

With built-in JNI library loading replaced with build-time Quarkus processing, 
and [jni-config.json] generated through [native tracing](#nativetracing),
we get a native-image that loads Javet but fails to run V8 at:


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

Without jni-config.json we get an NPE from [JNIFunctions.java](https://github.com/oracle/graal/blob/vm-ce-21.3.0/substratevm/src/com.oracle.svm.jni/src/com/oracle/svm/jni/functions/JNIFunctions.java#L1095) that doesn't say which class is missing.

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
