package se.yolean.javet.quarkus.runtime;

import java.io.File;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.loader.JavetLibLoader;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

// https://github.com/quarkusio/quarkus/blob/2.4.0.Final/docs/src/main/asciidoc/writing-extensions.adoc#21911-replacing-classes-in-the-native-image
@TargetClass(JavetLibLoader.class)
public final class JavetLibLoaderSubstitutions {

  @Substitute
  public boolean isLoaded() {
    return true;
  }

  @Substitute
  public void load() throws JavetException {
    System.out.println("Javet's built-in lib loading has been disabled");
  }

  @Substitute
  private void purge(File rootLibPath) {
    System.out.println("Javet's lib purge unsupported in native build");
  }

}
