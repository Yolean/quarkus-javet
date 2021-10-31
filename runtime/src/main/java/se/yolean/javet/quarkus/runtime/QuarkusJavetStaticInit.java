package se.yolean.javet.quarkus.runtime;

import java.util.logging.Logger;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class QuarkusJavetStaticInit {

  private static final Logger LOGGER = Logger.getLogger(QuarkusJavetStaticInit.class.getSimpleName());

  public void disableBuiltInLibLoading() {
    LOGGER.info("Calling lib loading setup");
    JavetLibLoadingSetup.forNative();
  }

}
