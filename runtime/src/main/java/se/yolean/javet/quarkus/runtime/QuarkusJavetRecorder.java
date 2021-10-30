package se.yolean.javet.quarkus.runtime;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import com.caoccao.javet.enums.JSRuntimeType;
import com.caoccao.javet.interop.loader.IJavetLibLoadingListener;
import com.caoccao.javet.interop.loader.JavetLibLoader;
import com.caoccao.javet.utils.JavetOSUtils;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class QuarkusJavetRecorder {

  private static final Logger LOGGER = Logger.getLogger(QuarkusJavetRecorder.class.getSimpleName());

  public void loadLibrary() {
//    LOGGER.info("Disabling Javet's default lib loading");
//    JavetLibLoader.setLibLoadingListener(new IJavetLibLoadingListener() {
//      @Override
//      public boolean isLibInSystemPath(JSRuntimeType jsRuntimeType) {
//        return true;
//      }
//    });
//
//    LOGGER.info("Loading library");
    final String resourceFileName = "/libjavet-v8-linux-x86_64.v.1.0.2.so"; // TODO get from arg or config
//
//    InputStream in = this.getClass().getClassLoader().getResourceAsStream(RESOURCE_NAME);
//
//    // https://github.com/caoccao/Javet/blob/1.0.2/src/main/java/com/caoccao/javet/interop/loader/JavetLibLoader.java#L347
//    // https://github.com/quarkusio/quarkus/blob/2.4.0.Final/extensions/kafka-client/runtime/src/main/java/io/quarkus/kafka/client/runtime/KafkaRecorder.java#L49
//    System.loadLibrary(RESOURCE_NAME);

    // https://github.com/caoccao/Javet/blob/1.0.2/src/main/java/com/caoccao/javet/interop/loader/JavetLibLoader.java#L126
    InputStream inputStream = JavetLibLoader.class.getResourceAsStream(resourceFileName);
    if (inputStream == null) {
      throw new RuntimeException("Javet JNI library not found in classpath: " + resourceFileName);
    } else {
      byte[] buffer = new byte[4096];
      int size = 0;
      while (true) {
        int length;
        try {
          length = inputStream.read(buffer);
        } catch (IOException e) {
          throw new RuntimeException("TODO handle error", e);
        }
        if (length == -1) {
            break;
        }
        size += length;
      }
      LOGGER.info("Resource " + resourceFileName + " size in bytes: " + size);
    }
    File extractionParent = new File(JavetOSUtils.TEMP_DIRECTORY);
    if (!extractionParent.exists()) {
      throw new RuntimeException("Default extraction parent not found: " + extractionParent);
    } else {
      LOGGER.info("Found extraction parent: " + extractionParent);
    }
    if (!extractionParent.canWrite()) {
      throw new RuntimeException("Default extraction parent not writable: " + extractionParent);
    } else {
      LOGGER.info("Extraction parent apperas writable: " + extractionParent);
    }
    LOGGER.info("Checks ok. Javet's default lib loading should be fine.");
  }

}
