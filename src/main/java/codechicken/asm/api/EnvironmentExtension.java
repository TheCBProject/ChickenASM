package codechicken.asm.api;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.commons.Remapper;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Created by covers1624 on 12/5/20.
 */
public interface EnvironmentExtension {

    Remapper getRemapper();

    byte[] getClassBytes(String name);

    class ExtensionLoader {

        private static final Logger logger = LogManager.getLogger();
        private static final boolean DEBUG = Boolean.getBoolean("codechicken.asm.ext.debug");
        private static final String forced = System.getProperty("codechicken.asm.ext.force");
        public static final EnvironmentExtension EXTENSION;

        static {
            if (DEBUG && forced != null) {
                logger.info("Forcing use of '{}' extension if available.", forced);
            }
            EnvironmentExtension ex = null;
            ServiceLoader<EnvironmentExtension> loader = ServiceLoader.load(EnvironmentExtension.class, Thread.currentThread().getContextClassLoader());
            Iterator<EnvironmentExtension> itr = loader.iterator();

            //It absolutely is not replaceable by that..
            //noinspection WhileLoopReplaceableByForEach
            while (itr.hasNext()) {
                try {
                    ex = itr.next();
                    if (forced != null) {
                        if (ex.getClass().getName().equals(forced)) {
                            logger.debug("Successfully forced extension class: {}", ex.getClass());
                        } else {
                            continue;
                        }
                    }
                    logger.log(DEBUG ? Level.INFO : Level.DEBUG, "Successfully loaded extension class: {}", ex.getClass());
                    break;
                } catch (Throwable e) {
                    if (DEBUG) {
                        logger.info("Failed to load extension.", e);
                    }
                }
            }
            EXTENSION = ex;
        }
    }
}
