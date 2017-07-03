package codechicken.asm.transformers;

import codechicken.asm.ObfMapping;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.Set;

/**
 * Parent to all transformer's.
 */
public abstract class ClassNodeTransformer {

    public int writeFlags;

    public ClassNodeTransformer(int writeFlags) {
        this.writeFlags = writeFlags;
    }

    public ClassNodeTransformer() {
        this(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    }

    public abstract String className();

    public abstract void transform(ClassNode cnode);

    public void addMethodsToSort(Set<ObfMapping> set) {
    }
}
