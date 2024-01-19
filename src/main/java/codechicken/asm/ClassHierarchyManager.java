package codechicken.asm;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;

public class ClassHierarchyManager {

    public static class SuperCache {

        String superclass;
        public Set<String> parents = new HashSet<>();
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
    private static List<Function<String, byte @Nullable []>> CLASS_BYTE_LOOKUPS = new ArrayList<>(0);

    /**
     * Add a factory to find class bytes with.
     *
     * @param func The function.
     */
    public static void addByteLookupFunc(Function<String, byte @Nullable []> func) {
        CLASS_BYTE_LOOKUPS.add(func);
    }

    /**
     * @param name       The class in question
     * @param superclass The class being extended
     * @return true if clazz extends, either directly or indirectly, superclass.
     */
    public static boolean classExtends(String name, String superclass) {

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
        SuperCache cache = superclasses.get(name);

        if (cache != null) {
            return cache;
        }

        for (Function<String, byte[]> func : CLASS_BYTE_LOOKUPS) {
            try {
                byte[] bytes = func.apply(name);
                if (bytes != null) {
                    cache = declareASM(bytes);
                    break;
                }
            } catch(Throwable ignored) {
            }
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
            cache.superclass = aclass.getSuperclass().getName();
        }

        cache.add(cache.superclass);
        for (Class<?> iclass : aclass.getInterfaces()) {
            cache.add(iclass.getName());
        }

        return cache;
    }

    private static SuperCache declareASM(@Nonnull byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        String name = reader.getClassName();

        SuperCache cache = getOrCreateCache(name);
        cache.superclass = reader.getSuperName().replace('/', '.');
        cache.add(cache.superclass);
        for (String iclass : reader.getInterfaces()) {
            cache.add(iclass.replace('/', '.'));
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

    public static String getSuperClass(@Nonnull String name) {
        SuperCache cache = declareClass(name);
        if (cache == null) {
            return "java.lang.Object";
        }

        cache.flatten();
        return cache.superclass;
    }
}
