package codechicken.asm.transformers;

import codechicken.asm.ASMBlock;
import codechicken.asm.InsnComparator;
import codechicken.asm.InsnListSection;
import codechicken.asm.ObfMapping;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

import static codechicken.asm.ModularASMTransformer.LEVEL;

/**
 * Injects a call before or after the needle.
 * If needle is null it will inject at the start or end of the method.
 */
public class MethodInjector extends MethodTransformer {

    private static final Logger logger = LogManager.getLogger();

    @Nullable
    public ASMBlock needle;
    @Nonnull
    public ASMBlock injection;
    public boolean before;

    /**
     * Injects the provided injection instructions at the location of the needle.
     *
     * @param method    The method to inject to.
     * @param needle    Where to inject.
     * @param injection The injection to apply.
     * @param before    Weather to apply before or after the provided needle.
     */
    public MethodInjector(@Nonnull ObfMapping method, @Nullable ASMBlock needle, @Nonnull ASMBlock injection, boolean before) {
        super(method);
        this.needle = needle;
        this.injection = injection;
        this.before = before;
    }

    /**
     * Injects the provided injection to the beginning or end of the method.
     *
     * @param method    The method to inject to.
     * @param injection The injection to apply.
     * @param before    Weather to apply at the start of the method or the end.
     */
    public MethodInjector(@Nonnull ObfMapping method, @Nonnull ASMBlock injection, boolean before) {
        this(method, null, injection, before);
    }

    /**
     * Injects the provided injection instructions at the location of the needle.
     *
     * @param method    The method to inject to.
     * @param needle    Where to inject.
     * @param injection The injection to apply.
     * @param before    Weather to apply before or after the provided needle.
     */
    public MethodInjector(@Nonnull ObfMapping method, @Nonnull InsnList needle, @Nonnull InsnList injection, boolean before) {
        this(method, new ASMBlock(needle), new ASMBlock(injection), before);
    }

    /**
     * Injects the provided injection to the beginning or end of the method.
     *
     * @param method    The method to inject to.
     * @param injection The injection to apply.
     * @param before    Weather to apply at the start of the method or the end.
     */
    public MethodInjector(@Nonnull ObfMapping method, @Nonnull InsnList injection, boolean before) {
        this(method, null, new ASMBlock(injection), before);
    }

    @Override
    public void addMethodsToSort(Set<ObfMapping> set) {
        set.add(method);
    }

    @Override
    public void transform(MethodNode mv) {
        if (needle == null) {
            logger.log(LEVEL, "Injecting {} method '{}'", before ? "before" : "after", method);
            if (before) {
                mv.instructions.insert(injection.rawListCopy());
            } else {
                mv.instructions.add(injection.rawListCopy());
            }
        } else {
            for (InsnListSection key : InsnComparator.findN(mv.instructions, needle.list)) {
                logger.log(LEVEL, "Injecting {} method '{}' @ {} - {}", before ? "before" : "after", method, key.start, key.end);
                ASMBlock injectBlock = injection.copy().mergeLabels(needle.applyLabels(key));

                if (before) {
                    key.insertBefore(injectBlock.list.list);
                } else {
                    key.insert(injectBlock.list.list);
                }
            }
        }
    }
}
