package codechicken.asm.transformers;

import codechicken.asm.ModularASMTransformer;
import codechicken.asm.ObfMapping;
import org.objectweb.asm.tree.ClassNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Writes a field to a class.
 * ObfMapping contains the class to put the field.
 */
public class FieldWriter extends ClassNodeTransformer {

    public final ObfMapping field;
    public final int access;
    public final Object value;

    /**
     * Writes a field to the provided class with a default value.
     *
     * @param access The access modifiers the field will have.
     * @param field  The name and class for the field.
     * @param value  The default value for the field.
     */
    public FieldWriter(@Nonnull int access, @Nonnull ObfMapping field, @Nullable Object value) {
        this.field = field.toClassloading();
        this.access = access;
        this.value = value;
    }

    /**
     * Writes a field to the provided class.
     *
     * @param access The access modifiers the field will have.
     * @param field  The name and class for the field.
     */
    public FieldWriter(@Nonnull int access, @Nonnull ObfMapping field) {
        this(access, field, null);
    }

    @Override
    public String className() {
        return field.javaClass();
    }

    @Override
    public void transform(ClassNode cNode) {
        ModularASMTransformer.log("Writing field %s to class %s", field.toString(), cNode.name);
        field.visitField(cNode, access, value);
    }
}
