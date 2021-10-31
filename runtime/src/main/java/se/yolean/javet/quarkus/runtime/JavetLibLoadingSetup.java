package se.yolean.javet.quarkus.runtime;

import java.util.logging.Logger;

import com.caoccao.javet.enums.JSRuntimeType;
import com.caoccao.javet.interop.loader.IJavetLibLoadingListener;
import com.caoccao.javet.interop.loader.JavetLibLoader;

public class JavetLibLoadingSetup {

  private static final Logger LOGGER = Logger.getLogger(JavetLibLoadingSetup.class.getSimpleName());

  static final IJavetLibLoadingListener DISABLED = new Disabled();

  public static void disableBuiltInLoader() {
    LOGGER.info("Disabling Javet's built-in lib loader");
    changeTo(DISABLED);
  }

  private static void changeTo(IJavetLibLoadingListener to) {
    JavetLibLoader.setLibLoadingListener(DISABLED);
  }

  private static class Disabled implements IJavetLibLoadingListener {
    @Override
    public boolean isLibInSystemPath(JSRuntimeType jsRuntimeType) {
      return true;
    }
  }

}
