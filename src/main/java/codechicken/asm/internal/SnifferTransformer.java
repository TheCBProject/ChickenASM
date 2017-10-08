package codechicken.asm.internal;

import codechicken.asm.ClassHierarchyManager;
import net.minecraft.launchwrapper.IClassTransformer;

/**
 * Created by covers1624 on 8/10/2017.
 */
public final class SnifferTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass != null) {
            ClassHierarchyManager.declare(name, basicClass);
        }
        return basicClass;
    }
}
