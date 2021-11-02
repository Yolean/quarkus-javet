package se.yolean.javet.quarkus.runtime;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.logging.Logger;

import com.caoccao.javet.enums.JSRuntimeType;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.loader.JavetLibLoader;
import com.caoccao.javet.utils.JavetOSUtils;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class QuarkusJavetRecorder {

  private static final Logger LOGGER = Logger.getLogger(QuarkusJavetRecorder.class.getSimpleName());

  private static JavetLibLoader getJavetLibLoader(JSRuntimeType mode) {
    return new JavetLibLoader(mode);
  }

  public void loadLibraryModeV8() {
    loadLibrary(JSRuntimeType.V8);
  }

  public void loadLibraryModeNode() {
    // loadLibrary(JSRuntimeType.Node);
    LOGGER.warning("Only Javet V8 mode supported at the moment. Skipping Node lib loading.");
  }

  // https://github.com/quarkusio/quarkus/blob/2.4.0.Final/extensions/kafka-client/runtime/src/main/java/io/quarkus/kafka/client/runtime/KafkaRecorder.java#L23
  protected void loadLibrary(JSRuntimeType mode) {
    LOGGER.info("Loading Javet library for mode " + mode);

    JavetLibLoader loader = getJavetLibLoader(mode);
    String resourceFileName;
    try {
      resourceFileName = loader.getResourceFileName();
    } catch (JavetException e) {
      throw new RuntimeException("Failed to get Javet resource file name", e);
    }
    if (!hasResource(resourceFileName)) {
      throw new IllegalStateException("Failed to confirm that classpath has resource: " + resourceFileName);
    }
    URL resource = JavetLibLoader.class.getResource(resourceFileName);
    String libName;
    try {
      libName = loader.getLibFileName();
    } catch (JavetException e) {
      throw new RuntimeException("Failed to get Javet lib name", e);
    }

    File out = extractLibraryFile(resource, libName);

    String path = out.getAbsolutePath();
    LOGGER.info("Loading " + path);
    System.load(path);
  }

  // https://github.com/quarkusio/quarkus/blob/2.4.0.Final/extensions/kafka-client/runtime/src/main/java/io/quarkus/kafka/client/runtime/KafkaRecorder.java#L52
  private static boolean hasResource(String path) {
    return JavetLibLoader.class.getResource(path) != null;
  }

  // https://github.com/quarkusio/quarkus/blob/2.4.0.Final/extensions/kafka-client/runtime/src/main/java/io/quarkus/kafka/client/runtime/KafkaRecorder.java#L56
  private static File extractLibraryFile(URL library, String name) {
    String tmp = System.getProperty("java.io.tmpdir");
    File extractedLibFile = new File(tmp, name);

    try (BufferedInputStream inputStream = new BufferedInputStream(library.openStream());
        FileOutputStream fileOS = new FileOutputStream(extractedLibFile)) {
      byte[] data = new byte[8192];
      int byteContent;
      while ((byteContent = inputStream.read(data, 0, 8192)) != -1) {
          fileOS.write(data, 0, byteContent);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
        "Unable to extract native library " + name + " to " + extractedLibFile.getAbsolutePath(), e);
    }

    extractedLibFile.deleteOnExit();

    return extractedLibFile;
  }


  public static void checkClassPathResource(final String resourceFileName) {
    // https://github.com/caoccao/Javet/blob/1.0.2/src/main/java/com/caoccao/javet/interop/loader/JavetLibLoader.java#L126
    InputStream inputStream = JavetLibLoader.class.getResourceAsStream(resourceFileName);
    if (inputStream == null) {
      throw new RuntimeException("Resource not found in classpath: " + resourceFileName);
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
      LOGGER.info("Resource " + resourceFileName + " size " + size + " found in classpath");
    }
  }

  protected void checkDefaultJavetLibExtractionPath() {
    File extractionParent = new File(JavetOSUtils.TEMP_DIRECTORY);
    if (!extractionParent.exists()) {
      throw new RuntimeException("Default extraction parent not found: " + extractionParent);
    } else {
      LOGGER.info("Found extraction parent: " + extractionParent);
    }
    if (!extractionParent.canWrite()) {
      throw new RuntimeException("Default extraction parent not writable: " + extractionParent);
    } else {
      LOGGER.info("Extraction parent appears writable: " + extractionParent);
    }
    LOGGER.info("Checks ok. Javet's default lib loading should be fine.");
  }

}
