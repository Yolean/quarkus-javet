package se.yolean.javet.quarkus.deployment;

import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import com.caoccao.javet.enums.JSRuntimeType;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.loader.JavetLibLoader;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.nativeimage.JniRuntimeAccessBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeReinitializedClassBuildItem;
import io.quarkus.deployment.pkg.NativeConfig;
import se.yolean.javet.quarkus.runtime.JavetLibLoadingSetup;
import se.yolean.javet.quarkus.runtime.QuarkusJavetRecorder;
import se.yolean.javet.quarkus.runtime.QuarkusJavetStaticInit;

class QuarkusJavetProcessor {

  private static final String FEATURE = "javet";

  private static final Logger LOGGER = Logger.getLogger(QuarkusJavetProcessor.class.getSimpleName());

  @BuildStep
  @Record(ExecutionTime.STATIC_INIT)
  void registerStaticRecorder(QuarkusJavetStaticInit recorder) {
    recorder.disableBuiltInLibLoading();
  }

  @BuildStep
  @Record(ExecutionTime.RUNTIME_INIT)
  void registerLibraryRecorder(QuarkusJavetRecorder recorder) {
    recorder.loadLibraryModeV8();
    recorder.loadLibraryModeNode();
  }

  Collection<JSRuntimeType> getRuntimeTypes() {
    return List.of(JSRuntimeType.V8, JSRuntimeType.Node);
  }

  @BuildStep
  void build(BuildProducer<FeatureBuildItem> feature,
      BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
      BuildProducer<JniRuntimeAccessBuildItem> jniRuntimeAccessibleClasses,
      BuildProducer<RuntimeReinitializedClassBuildItem> reinitialized,
      BuildProducer<NativeImageResourceBuildItem> nativeLibs,
      LaunchModeBuildItem launchMode,
      NativeConfig config) {

    feature.produce(new FeatureBuildItem(FEATURE));

    if (!config.isContainerBuild()) {
      LOGGER.info("Retaining Javet's default lib loading for non-native/non-container build");
      return;
    }

    JavetLibLoadingSetup.disableBuiltInLoader();

    for (JSRuntimeType mode : getRuntimeTypes()) {
      addNativeJavetMode(nativeLibs, config, mode);
    }

    registerClassesThatAreLoadedThroughReflection(reflectiveClasses, launchMode);
    registerClassesThatAreAccessedViaJni(jniRuntimeAccessibleClasses);
    enableLoadOfNativeLibs(reinitialized);
  }

  private void registerClassesThatAreLoadedThroughReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
      LaunchModeBuildItem launchMode) {
    // https://github.com/quarkusio/quarkus/blob/2.4.0.Final/extensions/kafka-streams/deployment/src/main/java/io/quarkus/kafka/streams/deployment/KafkaStreamsProcessor.java#L60
    registerCompulsoryClasses(reflectiveClasses);
    registerClassesThatClientMaySpecify(reflectiveClasses, launchMode);
  }

  private void registerCompulsoryClasses(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
    // TODO any such classes in Javet?
  }

  private void registerClassesThatClientMaySpecify(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
        LaunchModeBuildItem launchMode) {
    // TODO any such classes in Javet?
  }

  private void registerClassesThatAreAccessedViaJni(BuildProducer<JniRuntimeAccessBuildItem> jniRuntimeAccessibleClasses) {
    // Also relying on jni-config.json because unlike regular reflection issues that typically state the missing class
    // native JNI sometimes throws NPE at https://github.com/oracle/graal/blob/vm-ce-21.3.0/substratevm/src/com.oracle.svm.jni/src/com/oracle/svm/jni/functions/JNIFunctions.java#L1095
    jniRuntimeAccessibleClasses.produce(new JniRuntimeAccessBuildItem(true, false, false,
        com.caoccao.javet.values.reference.IV8Module.class
        ));
  }

  private void addNativeJavetMode(BuildProducer<NativeImageResourceBuildItem> nativeLibs, NativeConfig nativeConfig,
      JSRuntimeType mode) {
    JavetLibLoader loader = new JavetLibLoader(mode);
    String libName;
    try {
      libName = loader.getLibFileName();
    } catch (JavetException e) {
      throw new RuntimeException("Failed to get Javet lib name", e);
    }
    addNativeJavetMode(nativeLibs, nativeConfig, libName);
  }

  private void addNativeJavetMode(BuildProducer<NativeImageResourceBuildItem> nativeLibs, NativeConfig nativeConfig,
      String libPath) {
    if (libPath.startsWith("/")) {
      // Guessing, based on https://github.com/quarkusio/quarkus/blob/2.4.0.Final/extensions/kafka-client/deployment/src/main/java/io/quarkus/kafka/client/deployment/KafkaProcessor.java#L255
      throw new IllegalArgumentException("Resource path should be a classpath entry");
    }
    if (libPath.contains("/")) {
      throw new IllegalArgumentException("Javet lib files are in jar root; didn't expect a slash in path; got " + libPath);
    }
    if (!libPath.endsWith(".so")) {
      throw new IllegalArgumentException("Only targeting linux at the moment; expected lib path to end with .so; got " + libPath);
    }
    if (nativeConfig.isContainerBuild()) {
      nativeLibs.produce(new NativeImageResourceBuildItem(libPath));
      LOGGER.info("Added native image resource build item: " + libPath);
    }
    // otherwise the native lib of the platform this build runs on
    else {
      // TODO quarkus-kafka does lib loading here too, is that useful with Javet?
      throw new UnsupportedOperationException("Custom native lib instrumentation assumes in-container (and native-image) build");
    }
  }

  private void enableLoadOfNativeLibs(BuildProducer<RuntimeReinitializedClassBuildItem> reinitialized) {
    // https://github.com/quarkusio/quarkus/blob/2.4.0.Final/extensions/kafka-streams/deployment/src/main/java/io/quarkus/kafka/streams/deployment/KafkaStreamsProcessor.java#L135
    // TODO do we need re-initialization for Javet?
    reinitialized.produce(new RuntimeReinitializedClassBuildItem(JavetLibLoader.class.getName()));
  }

}
