package codechicken.asm.api;

import org.objectweb.asm.commons.Remapper;

/**
 * Created by covers1624 on 12/5/20.
 */
public interface EnvironmentExtension {

    Remapper getRemapper();

    byte[] getClassBytes(String name);

    static void setExtension(EnvironmentExtension extension) {
        if (ExtensionLoader.EXTENSION != null) {
            throw new IllegalStateException("Extension already set.");
        }
        ExtensionLoader.EXTENSION = extension;
    }

    class ExtensionLoader {

        public static EnvironmentExtension EXTENSION;
    }
}
