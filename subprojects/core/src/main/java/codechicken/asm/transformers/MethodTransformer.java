package codechicken.asm.transformers;

import codechicken.asm.ObfMapping;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static codechicken.asm.ASMHelper.findMethod;

/**
 * Base Method transformer.
 */
public abstract class MethodTransformer extends ClassNodeTransformer {

    public final ObfMapping method;

    public MethodTransformer(ObfMapping method) {
        this.method = method;
    }

    @Override
    public String className() {
        return method.javaClass();
    }

    @Override
    public void transform(ClassNode cnode) {
        MethodNode mv = findMethod(method, cnode);
        if (mv == null) {
            throw new RuntimeException("Method not found: " + method);
        }

        try {
            transform(mv);
        } catch (Exception e) {
            throw new RuntimeException("Error transforming method: " + method, e);
        }
    }

    public abstract void transform(MethodNode mv);
}
