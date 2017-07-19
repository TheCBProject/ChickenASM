package codechicken.asm;

import codechicken.asm.transformers.ClassNodeTransformer;
import org.apache.logging.log4j.Level;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.tree.ClassNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import static codechicken.asm.ASMHelper.createBytes;
import static codechicken.asm.ASMHelper.dump;
import static codechicken.asm.ASMHelper.logger;

public class ModularASMTransformer {

    //debug will print all transformations to console by default.
    public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("ccl.asm.debug", "false"));
    //The format in which to dump the transformed classes.
    public static final boolean DUMP_RAW = Boolean.parseBoolean(System.getProperty("ccl.asm.debug.dump_raw", "false")) && DEBUG;
    public static final boolean DUMP_TEXT = Boolean.parseBoolean(System.getProperty("ccl.asm.debug.dump_text", "false")) && DEBUG;
    public static final File dumpFolderBase = new File("asm/ccl_modular/");

    public File dumpFolder;

    public HashMap<String, ClassNodeTransformerList> transformers = new HashMap<>();
    public String name;

    public ModularASMTransformer(String name) {
        this.name = name;
        dumpFolder = new File(dumpFolderBase, name);
    }

    /**
     * Adds a ClassNodeTransformer to this transformer.
     *
     * @param t Transformer to add.
     */
    public void add(@Nonnull ClassNodeTransformer t) {
        ClassNodeTransformerList list = transformers.computeIfAbsent(t.className(), k -> new ClassNodeTransformerList());
        list.add(t);
    }

    /**
     * Runs the transform.
     *
     * @param name  name of the class being loaded.
     * @param bytes Class bytes.
     * @return Returns null if the class passed is null, returns original class if there are no transformers for a given class.
     * Otherwise returns transformed class.
     */
    @Nullable
    public byte[] transform(@Nonnull String name, @Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        ClassNodeTransformerList list = transformers.get(name);
        return list == null ? bytes : list.transform(bytes);
    }

    public static void log(Object object) {
        log(String.valueOf(object), "");
    }

    public static void log(String format, Object... params) {
        Level level = DEBUG ? Level.INFO : Level.DEBUG;
        logger.log(level, String.format(format, params));
    }

    /**
     * Contains a list of transformers for a given class.
     * Also contains some basic logic for doing the actual transform.
     */
    public class ClassNodeTransformerList {

        List<ClassNodeTransformer> transformers = new LinkedList<>();
        HashSet<ObfMapping> methodsToSort = new HashSet<>();

        public void add(ClassNodeTransformer t) {
            transformers.add(t);
            t.addMethodsToSort(methodsToSort);
        }

        public byte[] transform(byte[] bytes) {
            ClassNode cnode = new ClassNode();
            ClassReader reader = new ClassReader(bytes);
            ClassVisitor cv = cnode;
            if (!methodsToSort.isEmpty()) {
                cv = new LocalVariablesSorterVisitor(methodsToSort, cv);
            }
            reader.accept(cv, ClassReader.EXPAND_FRAMES);

            try {
                int writeFlags = 0;
                for (ClassNodeTransformer t : transformers) {
                    t.transform(cnode);
                    writeFlags |= t.writeFlags;
                }

                bytes = createBytes(cnode, writeFlags);
                if (DUMP_RAW) {
                    File file = new File(dumpFolder, cnode.name.replace('/', '#') + ".class");
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(bytes);
                    fos.flush();
                    fos.close();
                } else if (DUMP_TEXT) {
                    dump(bytes, new File(dumpFolder, cnode.name.replace('/', '#') + ".txt"), false, false);
                }
                return bytes;
            } catch (Exception e) {
                dump(bytes, new File(dumpFolder, cnode.name.replace('/', '#') + ".txt"), false, false);
                throw new RuntimeException(e);
            }
        }
    }
}
