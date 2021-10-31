package se.yolean.javet.quarkus.runtime;

import java.nio.file.Path;
import java.util.logging.Logger;

import com.caoccao.javet.enums.JSRuntimeType;
import com.caoccao.javet.interop.loader.IJavetLibLoadingListener;
import com.caoccao.javet.interop.loader.JavetLibLoader;

public class JavetLibLoadingSetup {

  private static final Logger LOGGER = Logger.getLogger(JavetLibLoadingSetup.class.getSimpleName());

  public static String getSystemTemp() {
    return System.getProperty("java.io.tmpdir");
  }

  static final IJavetLibLoadingListener DISABLED = new Disabled();
  static final StaticPath PREDEFINED = new StaticPath(getSystemTemp());

  public static void disableBuiltInLoader__() {
    LOGGER.info("Disabling Javet's built-in lib loader");
    changeTo(DISABLED);
  }

  public static void forNative() {
    LOGGER.info("Setting Javet's lib loading path to predefined: " + PREDEFINED.path);
    changeTo(PREDEFINED);
  }

  private static void changeTo(IJavetLibLoadingListener to) {
    JavetLibLoader.setLibLoadingListener(to);
  }

  private static class Disabled implements IJavetLibLoadingListener {
    @Override
    public boolean isLibInSystemPath(JSRuntimeType jsRuntimeType) {
      return true;
    }
  }

  private static class StaticPath implements IJavetLibLoadingListener {

    private final Path path;

    public StaticPath(String systemTemp) {
      this.path = Path.of(systemTemp);
    }

    @Override
    public Path getLibPath(JSRuntimeType jsRuntimeType) {
        return path;
    }
  }

}
