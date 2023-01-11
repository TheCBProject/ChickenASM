package codechicken.asm.transformers;

import codechicken.asm.*;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Replaces a specific needle with a specific replacement.
 * Can replace more than one needle.
 */
public class MethodReplacer extends MethodTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodReplacer.class);

    public ASMBlock needle;
    public ASMBlock replacement;

    /**
     * Replaces the provided needle with the provided replacement.
     *
     * @param method      The method to replace in.
     * @param needle      The needle to replace.
     * @param replacement The replacement to apply.
     */
    public MethodReplacer(@Nonnull ObfMapping method, @Nonnull ASMBlock needle, @Nonnull ASMBlock replacement) {
        super(method);
        this.needle = needle;
        this.replacement = replacement;
    }

    /**
     * Replaces the provided needle with the provided replacement.
     *
     * @param method      The method to replace in.
     * @param needle      The needle to replace.
     * @param replacement The replacement to apply.
     */
    public MethodReplacer(@Nonnull ObfMapping method, @Nonnull InsnList needle, @Nonnull InsnList replacement) {
        this(method, new ASMBlock(needle), new ASMBlock(replacement));
    }

    @Override
    public void addMethodsToSort(Set<ObfMapping> set) {
        set.add(method);
    }

    @Override
    public void transform(MethodNode mv) {
        for (InsnListSection key : InsnComparator.findN(mv.instructions, needle.list)) {
            if (ModularASMTransformer.DEBUG) {
                LOGGER.info("Replacing method '{}' @ {} - {}.", method, key.start, key.end);
            } else {
                LOGGER.debug("Replacing method '{}' @ {} - {}.", method, key.start, key.end);
            }
            ASMBlock replaceBlock = replacement.copy().pullLabels(needle.applyLabels(key));
            key.insert(replaceBlock.list.list);
        }
    }
}
