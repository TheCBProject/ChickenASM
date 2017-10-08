package codechicken.asm;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.objectweb.asm.ClassReader;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ClassHierarchyManager {

    private static boolean obfuscated = !((Boolean)Launch.blackboard.get("fml.deobfuscatedEnvironment"));

    public static class SuperCache {

        String superclass;
        public HashSet<String> parents = new HashSet<>();
        private boolean flattened;

        public void add(String parent) {
            parents.add(parent);
        }

        public void flatten() {
            if (flattened) {
                return;
            }

            for (String s : new ArrayList<>(parents)) {
                SuperCache c = declareClass(s);
                if (c != null) {
                    c.flatten();
                    parents.addAll(c.parents);
                }
            }
            flattened = true;
        }
    }

    public static HashMap<String, SuperCache> superclasses = new HashMap<>();
    private static LaunchClassLoader cl = Launch.classLoader;

    public static String toKey(String name) {
        if (obfuscated) {
            name = FMLDeobfuscatingRemapper.INSTANCE.map(name.replace('.', '/')).replace('/', '.');
        }
        return name;
    }

    public static String unKey(String name) {
        if (obfuscated) {
            name = FMLDeobfuscatingRemapper.INSTANCE.unmap(name.replace('.', '/')).replace('/', '.');
        }
        return name;
    }

    /**
     * @param name       The class in question
     * @param superclass The class being extended
     * @return true if clazz extends, either directly or indirectly, superclass.
     */
    public static boolean classExtends(String name, String superclass) {
        name = toKey(name);
        superclass = toKey(superclass);

        if (name.equals(superclass)) {
            return true;
        }

        SuperCache cache = declareClass(name);
        if (cache == null)//just can't handle this
        {
            return false;
        }

        cache.flatten();
        return cache.parents.contains(superclass);
    }

    private static SuperCache declareClass(String name) {
        name = toKey(name);
        SuperCache cache = superclasses.get(name);

        if (cache != null) {
            return cache;
        }

        try {
            byte[] bytes = cl.getClassBytes(unKey(name));
            if (bytes != null) {
                cache = declareASM(bytes);
            }
        } catch (Exception ignored) {
        }

        if (cache != null) {
            return cache;
        }

        try {
            cache = declareReflection(name);
        } catch (ClassNotFoundException ignored) {
        }

        return cache;
    }

    private static SuperCache declareReflection(String name) throws ClassNotFoundException {
        Class<?> aclass = Class.forName(name);

        SuperCache cache = getOrCreateCache(name);
        if (aclass.isInterface()) {
            cache.superclass = "java.lang.Object";
        } else if (name.equals("java.lang.Object")) {
            return cache;
        } else {
            cache.superclass = toKey(aclass.getSuperclass().getName());
        }

        cache.add(cache.superclass);
        for (Class<?> iclass : aclass.getInterfaces()) {
            cache.add(toKey(iclass.getName()));
        }

        return cache;
    }

    private static SuperCache declareASM(@Nonnull byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        String name = toKey(reader.getClassName());

        SuperCache cache = getOrCreateCache(name);
        cache.superclass = toKey(reader.getSuperName().replace('/', '.'));
        cache.add(cache.superclass);
        for (String iclass : reader.getInterfaces()) {
            cache.add(toKey(iclass.replace('/', '.')));
        }

        return cache;
    }

    public static void declare(@Nonnull String name, @Nonnull byte[] bytes) {
        if (!superclasses.containsKey(name)) {
            declareASM(bytes);
        }
    }

    @Nonnull
    public static SuperCache getOrCreateCache(@Nonnull String name) {
        return superclasses.computeIfAbsent(name, k -> new SuperCache());
    }

    public static String getSuperClass(@Nonnull String name, boolean runtime) {
        name = toKey(name);
        SuperCache cache = declareClass(name);
        if (cache == null) {
            return "java.lang.Object";
        }

        cache.flatten();
        String s = cache.superclass;
        if (!runtime) {
            s = FMLDeobfuscatingRemapper.INSTANCE.unmap(s);
        }
        return s;
    }
}
