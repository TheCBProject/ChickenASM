package codechicken.asm.modlauncher;

import codechicken.asm.api.EnvironmentExtension;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.INameMappingService;
import org.objectweb.asm.commons.Remapper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Created by covers1624 on 12/5/20.
 */
public class ModLauncherExtension implements EnvironmentExtension {

    private static final TransformingClassLoader cl = (TransformingClassLoader) Thread.currentThread().getContextClassLoader();
    private static final MethodHandle m_buildTransformedClassNodeFor;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Method m = TransformingClassLoader.class.getDeclaredMethod("buildTransformedClassNodeFor", String.class, String.class);
            m.setAccessible(true);
            m_buildTransformedClassNodeFor = lookup.unreflect(m);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Remapper getRemapper() {
        return new FMLRemapper();
    }

    @Override
    public byte[] getClassBytes(String name) {
        try {
            return (byte[]) m_buildTransformedClassNodeFor.invoke(cl, name, "ChickenASM");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static class FMLRemapper extends Remapper {

        private static final Optional<BiFunction<INameMappingService.Domain, String, String>> nameFunction =//
                Launcher.INSTANCE.environment().findNameMapping("mcp");

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            return nameFunction.map(f -> f.apply(INameMappingService.Domain.METHOD, name)).orElse(name);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            return nameFunction.map(f -> f.apply(INameMappingService.Domain.FIELD, name)).orElse(name);
        }

        @Override
        public String map(String typeName) {
            return nameFunction.map(f -> f.apply(INameMappingService.Domain.CLASS, typeName)).orElse(typeName);
        }
    }
}
