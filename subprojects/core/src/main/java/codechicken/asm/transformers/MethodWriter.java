package codechicken.asm.transformers;

import codechicken.asm.ASMBlock;
import codechicken.asm.ModularASMTransformer;
import codechicken.asm.ObfMapping;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static codechicken.asm.ASMHelper.findMethod;

/**
 * Writes a method containing the provided InsnList with the ObfMapping as the method name and desc.
 */
public class MethodWriter extends ClassNodeTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodWriter.class);

    public final int access;
    public final ObfMapping method;
    public final String[] exceptions;
    public InsnList list;

    /**
     * Write a new method with no instructions or exceptions.
     *
     * @param access Access flags.
     * @param method The method name and desc.
     */
    public MethodWriter(int access, @Nonnull ObfMapping method) {
        this(access, method, null, (InsnList) null);
    }

    /**
     * Write a new method with the provided instructions.
     *
     * @param access Access flags.
     * @param method The method name and desc.
     * @param list   The instructions to use.
     */
    public MethodWriter(int access, @Nonnull ObfMapping method, @Nullable InsnList list) {
        this(access, method, null, list);
    }

    /**
     * Write a new method with the provided instructions.
     *
     * @param access Access flags.
     * @param method The method name and desc.
     * @param block  The instructions to use.
     */
    public MethodWriter(int access, @Nonnull ObfMapping method, @Nonnull ASMBlock block) {
        this(access, method, null, block);
    }

    /**
     * Write a new method with no instructions and the provided exceptions.
     *
     * @param access     Access flags.
     * @param method     The method name and desc.
     * @param exceptions The exceptions this method can throw.
     */
    public MethodWriter(int access, @Nonnull ObfMapping method, @Nullable String[] exceptions) {
        this(access, method, exceptions, (InsnList) null);
    }

    /**
     * Write a new method with the provided instructions and the provided exceptions.
     *
     * @param access     Access flags.
     * @param method     The method name and desc.
     * @param exceptions The exceptions this method can throw.
     * @param list       The instructions to use.
     */
    public MethodWriter(int access, @Nonnull ObfMapping method, @Nullable String[] exceptions, @Nullable InsnList list) {
        this.access = access;
        this.method = method;
        this.exceptions = exceptions;
        this.list = list;
    }

    /**
     * Write a new method with the provided instructions and the provided exceptions.
     *
     * @param access     Access flags.
     * @param method     The method name and desc.
     * @param exceptions The exceptions this method can throw.
     * @param block      The instructions to use.
     */
    public MethodWriter(int access, @Nonnull ObfMapping method, @Nullable String[] exceptions, @Nonnull ASMBlock block) {
        this(access, method, exceptions, block.rawListCopy());
    }

    @Override
    public String className() {
        return method.javaClass();
    }

    @Override
    public void transform(ClassNode cnode) {
        MethodNode mv = findMethod(method, cnode);
        if (mv == null) {
            mv = (MethodNode) method.visitMethod(cnode, access, exceptions);
        } else {
            mv.access = access;
            mv.instructions.clear();
            if (mv.localVariables != null) {
                mv.localVariables.clear();
            }
            if (mv.tryCatchBlocks != null) {
                mv.tryCatchBlocks.clear();
            }
        }

        write(mv);
    }

    public void write(MethodNode mv) {
        if (ModularASMTransformer.DEBUG) {
            LOGGER.info("Writing method '{}'.", method);
        } else {
            LOGGER.debug("Writing method '{}'.", method);
        }
        list.accept(mv);
    }
}
