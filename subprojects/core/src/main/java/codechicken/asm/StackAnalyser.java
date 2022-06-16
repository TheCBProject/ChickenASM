package codechicken.asm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;
import static org.objectweb.asm.tree.AbstractInsnNode.*;

/**
 * Created by covers1624 on 21/5/20.
 */
public class StackAnalyser {

    private static final boolean DEBUG = Boolean.getBoolean("codechicken.mixin.StackAnalyser.debug");
    private static final boolean DEBUG_FRAMES = Boolean.getBoolean("codechicken.mixin.StackAnalyser.debug_frames");
    private static final Logger logger = LogManager.getLogger();

    public static int width(Type type) {
        return type.getSize();
    }

    public static int width(String type) {
        return width(getType(type));
    }

    public static int width(Iterable<Type> it) {
        return width(StreamSupport.stream(it.spliterator(), false));
    }

    public static int width(Type[] it) {
        return width(Arrays.stream(it));
    }

    public static int width(Stream<Type> stream) {
        return stream//
                .mapToInt(StackAnalyser::width)//
                .reduce(Integer::sum)//
                .orElse(0);
    }

    public final Type owner;
    public final MethodNode mNode;

    private final List<StackEntry> stack = new LinkedList<>();
    private final List<LocalEntry> locals = new LinkedList<>();
    private final Map<LabelNode, TryCatchBlockNode> catchHandlers = new HashMap<>();

    public StackAnalyser(Type owner, MethodNode mNode) {
        this.owner = owner;
        this.mNode = mNode;

        if ((mNode.access & ACC_STATIC) == 0) {
            pushL(new This(owner));
        }

        Type[] pTypes = getArgumentTypes(mNode.desc);
        for (int i = 0; i < pTypes.length; i++) {
            pushL(new Param(pTypes[i], i));
        }

        for (TryCatchBlockNode node : mNode.tryCatchBlocks) {
            catchHandlers.put(node.handler, node);
        }
    }

    public void pushL(LocalEntry entry) {
        locals.add(entry);//To the end
    }

    public void setL(int i, LocalEntry entry) {
        while (i + entry.type.getSize() > locals.size()) {
            locals.add(null);//Ensure room
        }
        locals.set(i, entry);
        if (entry.type.getSize() == 2) {
            locals.set(i + 1, entry);
        }
    }

    public void push(StackEntry entry) {
        insert(0, entry);
    }

    public StackEntry _pop() {
        return _pop(0);
    }

    public StackEntry _pop(int i) {
        return stack.remove(stack.size() - i - 1);
    }

    public StackEntry pop() {
        return pop(0);
    }

    public StackEntry pop(int i) {
        StackEntry e = _pop(i);
        if (e.type.getSize() == 2) {
            if (peek(i) != e) {
                throw new IllegalStateException("Wide stack entry elems don't match (" + e + ", " + peek(i) + ")");
            }
            _pop(i);
        }
        return e;
    }

    public StackEntry peek() {
        return peek(0);
    }

    public StackEntry peek(int i) {
        return stack.get(stack.size() - i - 1);
    }

    public void insert(int i, StackEntry entry) {
        if (entry.type.getSize() == 0) {
            return;
        }
        stack.add(stack.size() - i, entry);
        if (entry.type.getSize() == 2) {
            stack.add(stack.size() - i, entry);
        }
    }

    public List<StackEntry> popArgs(String desc) {
        int len = getArgumentTypes(desc).length;
        StackEntry[] args = new StackEntry[len];
        for (int i = 0; i < len; i++) {
            args[len - i - 1] = pop();
        }
        return Arrays.asList(args);
    }

    public void visitInsn(AbstractInsnNode aInsn) {
        switch (aInsn.getType()) {
            case INSN:
                handleInsnNode((InsnNode) aInsn);
                break;
            case INT_INSN: {
                IntInsnNode insn = (IntInsnNode) aInsn;
                switch (insn.getOpcode()) {
                    //@formatter:off
                    case BIPUSH: push(new Const(insn, ((byte)  insn.operand))); break;
                    case SIPUSH: push(new Const(insn, ((short) insn.operand))); break;
                    //@formatter:on
                    default: {
                        if (DEBUG) {
                            logger.warn("Unhandled Opcode for IntInsnNode: {}", insn.getOpcode());
                        }
                    }
                }
                break;
            }
            case LDC_INSN: {
                LdcInsnNode insn = (LdcInsnNode) aInsn;
                if (insn.getOpcode() == LDC) {
                    push(new Const(insn, insn.cst));
                } else if (DEBUG) {
                    logger.warn("Unhandled Opcode for LdcInsnNode: {}", insn.getOpcode());
                }
                break;
            }
            case VAR_INSN: {
                VarInsnNode insn = (VarInsnNode) aInsn;
                switch (insn.getOpcode()) {
                    case ILOAD:
                    case LLOAD:
                    case FLOAD:
                    case DLOAD:
                    case ALOAD:
                        push(new Load(insn, locals.get(insn.var)));
                        break;
                    case ISTORE:
                    case LSTORE:
                    case FSTORE:
                    case DSTORE:
                    case ASTORE:
                        setL(insn.var, new Store(pop()));
                        break;
                    default: {
                        if (DEBUG) {
                            logger.warn("Unhandled Opcode for VarInsnNode: {}", insn.getOpcode());
                        }
                    }
                }
                break;
            }
            case IINC_INSN: {
                IincInsnNode insn = (IincInsnNode) aInsn;
                if (insn.getOpcode() == IINC) {
                    setL(insn.var, new Store(new BinaryOp(insn, new Const(insn, insn.incr), new Load(insn, locals.get(insn.var)))));
                } else if (DEBUG) {
                    logger.warn("Unhandled Opcode for IincInsnNode: {}", insn.getOpcode());
                }
                break;
            }
            case JUMP_INSN: {
                JumpInsnNode insn = (JumpInsnNode) aInsn;
                switch (insn.getOpcode()) {
                    case IFEQ:
                    case IFNE:
                    case IFLT:
                    case IFGE:
                    case IFGT:
                    case IFLE:
                        pop();
                        break;
                    case IF_ICMPEQ:
                    case IF_ICMPNE:
                    case IF_ICMPLT:
                    case IF_ICMPGE:
                    case IF_ICMPGT:
                    case IF_ICMPLE:
                    case IF_ACMPEQ:
                    case IF_ACMPNE:
                        pop();
                        pop();
                        break;
                    case JSR:
                        push(new ReturnAddress(insn));
                        break;
                    case IFNULL:
                    case IFNONNULL:
                        pop();
                        break;
                    case GOTO:
                        break;
                    default: {
                        if (DEBUG) {
                            logger.warn("Unhandled Opcode for JumpInsnNode: {}", insn.getOpcode());
                        }
                    }
                }
                break;
            }
            case TABLESWITCH_INSN:
            case LOOKUPSWITCH_INSN:
                pop();
                break;
            case FIELD_INSN: {
                FieldInsnNode insn = (FieldInsnNode) aInsn;
                switch (aInsn.getOpcode()) {
                    case GETSTATIC:
                        push(new GetField(insn, null));
                        break;
                    case PUTSTATIC:
                        pop();
                        break;
                    case GETFIELD:
                        push(new GetField(insn, pop()));
                        break;
                    case PUTFIELD:
                        pop();
                        pop();
                        break;
                    default:
                        if (DEBUG) {
                            logger.warn("Unhandled Opcode for FieldInsnNode: {}", insn.getOpcode());
                        }
                }
                break;
            }
            case METHOD_INSN: {
                MethodInsnNode insn = (MethodInsnNode) aInsn;
                switch (insn.getOpcode()) {
                    case INVOKEVIRTUAL:
                    case INVOKESPECIAL:
                    case INVOKEINTERFACE:
                        push(new Invoke(insn, popArgs(insn.desc), pop()));
                        break;
                    case INVOKESTATIC: {
                        push(new Invoke(insn, popArgs(insn.desc), null));
                        break;
                    }
                    default:
                        if (DEBUG) {
                            logger.warn("Unhandled Opcode for MethodInsnNode: {}", insn.getOpcode());
                        }
                }
                break;
            }
            case INVOKE_DYNAMIC_INSN: {
                InvokeDynamicInsnNode insn = (InvokeDynamicInsnNode) aInsn;
                push(new InvokeDynamic(insn, popArgs(insn.desc)));
                break;
            }
            case TYPE_INSN: {
                TypeInsnNode insn = (TypeInsnNode) aInsn;
                switch (insn.getOpcode()) {
                    case NEW:
                        push(new New(insn, getObjectType(insn.desc)));
                        break;
                    case NEWARRAY:
                        push(new NewArray(insn, getObjectType(insn.desc), pop()));
                        break;
                    case ANEWARRAY:
                        push(new NewArray(insn, getType("[" + insn.desc), pop()));
                        break;
                    case CHECKCAST:
                        push(new Cast(insn, getObjectType(insn.desc), pop()));
                        break;
                    case INSTANCEOF:
                        push(new UnaryOp(insn, pop()));
                        break;
                    default:
                        if (DEBUG) {
                            logger.warn("Unhandled Opcode for TypeInsnNode: {}", insn.getOpcode());
                        }
                }
                break;
            }
            case MULTIANEWARRAY_INSN: {
                MultiANewArrayInsnNode insn = (MultiANewArrayInsnNode) aInsn;
                List<StackEntry> sizes = new ArrayList<>(insn.dims);
                for (int i = 0; i < insn.dims; i++) {
                    sizes.add(pop());
                }
                push(new NewMultiArray(insn, getType(insn.desc), sizes));
                break;
            }
            case FRAME:
                if (DEBUG_FRAMES) {
                    FrameNode insn = (FrameNode) aInsn;
                    switch (insn.type) {
                        case F_NEW:
                        case F_FULL:
                            logger.info("Reset stacks/locals.");
                            break;
                        case F_APPEND:
                            logger.info("Add locals.");
                            break;
                        case F_CHOP:
                            logger.info("Remove locals.");
                            break;
                        case F_SAME:
                            logger.info("Reset.");
                            break;
                        case F_SAME1:
                            logger.info("Reset locals and all but bottom stack.");
                            break;
                        default:
                            logger.info("Unhandled frame type: {}", insn.type);
                    }
                }
                break;
            case LABEL: {
                LabelNode insn = (LabelNode) aInsn;
                TryCatchBlockNode handlerNode = catchHandlers.get(insn);
                if (handlerNode != null && handlerNode.type != null) {
                    push(new CaughtException(insn, getObjectType(handlerNode.type)));
                }
                break;
            }
            default:
                if (DEBUG) {
                    logger.warn("Unhandled AbstractInsnNode type: {}", aInsn.getType());
                }
        }

    }

    private void handleInsnNode(InsnNode insn) {
        switch (insn.getOpcode()) {
            //@formatter:off
            case ACONST_NULL: push(new Const(insn, null)); break;
            case ICONST_M1:   push(new Const(insn, -1));   break;
            case ICONST_0:    push(new Const(insn, 0));    break;
            case ICONST_1:    push(new Const(insn, 1));    break;
            case ICONST_2:    push(new Const(insn, 2));    break;
            case ICONST_3:    push(new Const(insn, 3));    break;
            case ICONST_4:    push(new Const(insn, 4));    break;
            case ICONST_5:    push(new Const(insn, 5));    break;
            case LCONST_0:    push(new Const(insn, 0L));   break;
            case LCONST_1:    push(new Const(insn, 1L));   break;
            case FCONST_0:    push(new Const(insn, 0F));   break;
            case FCONST_1:    push(new Const(insn, 1F));   break;
            case FCONST_2:    push(new Const(insn, 2F));   break;
            case DCONST_0:    push(new Const(insn, 0D));   break;
            case DCONST_1:    push(new Const(insn, 1D));   break;
            case IALOAD:
            case LALOAD:
            case FALOAD:
            case DALOAD:
            case AALOAD:
            case BALOAD:
            case CALOAD:
            case SALOAD: push(new ArrayLoad(insn, pop(), pop())); break;
            case IASTORE:
            case LASTORE:
            case FASTORE:
            case DASTORE:
            case AASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE: pop(); pop(); pop(); break;
            case POP:     pop(); break;
            case POP2:    _pop(); _pop(); break;
            case DUP:     push(peek()); break;
            case DUP_X1:  insert(2, peek()); break;
            case DUP_X2:  insert(3, peek()); break;
            case DUP2:    push(peek(1)); push(peek(1)); break;
            case DUP2_X1: insert(3, peek(1)); insert(3, peek()); break;
            case DUP2_X2: insert(4, peek(1)); insert(4, peek()); break;
            case SWAP:    push(pop(1)); break;
            case IADD:
            case LADD:
            case FADD:
            case DADD:
            case ISUB:
            case LSUB:
            case FSUB:
            case DSUB:
            case IMUL:
            case LMUL:
            case FMUL:
            case DMUL:
            case IDIV:
            case LDIV:
            case FDIV:
            case DDIV:
            case IREM:
            case LREM:
            case FREM:
            case DREM: push(new BinaryOp(insn, pop(), pop())); break;
            case INEG:
            case LNEG:
            case FNEG:
            case DNEG: push(new UnaryOp(insn, pop())); break;
            case ISHL:
            case LSHL:
            case ISHR:
            case LSHR:
            case IUSHR:
            case LUSHR:
            case IAND:
            case LAND:
            case IOR:
            case LOR:
            case IXOR:
            case LXOR: push(new BinaryOp(insn, pop(), pop())); break;
            case L2I:
            case F2I:
            case D2I: push(new PrimitiveCast(insn, INT_TYPE, pop())); break;
            case I2L:
            case F2L:
            case D2L: push(new PrimitiveCast(insn, LONG_TYPE, pop())); break;
            case I2F:
            case L2F:
            case D2F: push(new PrimitiveCast(insn, FLOAT_TYPE, pop())); break;
            case I2D:
            case L2D:
            case F2D: push(new PrimitiveCast(insn, DOUBLE_TYPE, pop())); break;
            case I2B: push(new PrimitiveCast(insn, BYTE_TYPE, pop())); break;
            case I2C: push(new PrimitiveCast(insn, CHAR_TYPE, pop())); break;
            case I2S: push(new PrimitiveCast(insn, SHORT_TYPE, pop())); break;
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG: push(new BinaryOp(insn, pop(), pop())); break;
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN: pop(); break;
            case ARRAYLENGTH: push(new ArrayLength(insn, pop())); break;
            case ATHROW: pop(); break;
            case MONITORENTER:
            case MONITOREXIT: pop(); break;
            //@formatter:on
            default:
                if (DEBUG) {
                    logger.warn("Unhandled Opcode for InsnNode: {}", insn.getOpcode());
                }
        }
    }

    private static Type computeConstType(Object obj) {
        if (obj instanceof Byte) {
            return BYTE_TYPE;
        } else if (obj instanceof Short) {
            return SHORT_TYPE;
        } else if (obj instanceof Integer) {
            return INT_TYPE;
        } else if (obj instanceof Long) {
            return LONG_TYPE;
        } else if (obj instanceof Float) {
            return FLOAT_TYPE;
        } else if (obj instanceof Double) {
            return DOUBLE_TYPE;
        } else if (obj instanceof Character) {
            return CHAR_TYPE;
        } else if (obj instanceof Boolean) {
            return BOOLEAN_TYPE;
        } else if (obj instanceof String) {
            return getObjectType("java/lang/String");
        } else if (obj == null) {
            return getObjectType("java/lang/Object");
        } else if (obj instanceof Type) {
            int sort = ((Type) obj).getSort();
            if (sort == OBJECT || sort == ARRAY) {
                return getObjectType("java/lang/Class");
            } else if (sort == METHOD) {
                return getObjectType("java/lang/invoke/MethodType");
            } else {
                throw new IllegalArgumentException("Invalid Type const: " + obj);
            }
        } else if (obj instanceof Handle) {
            return getObjectType("java/lang/invoke/MethodHandle");
        } else if (obj instanceof ConstantDynamic) {
            throw new IllegalArgumentException("ConstantDynamic currently not supported.");
        }

        throw new IllegalArgumentException("Unknown const: " + obj);
    }

    public static abstract class Entry {

        public final Type type;

        public Entry(Type type) {
            this.type = type;
        }
    }

    public static abstract class StackEntry extends Entry {

        public final AbstractInsnNode insn;

        public StackEntry(AbstractInsnNode insn, Type type) {
            super(type);
            this.insn = insn;
        }
    }

    public static abstract class LocalEntry extends Entry {

        public LocalEntry(Type type) {
            super(type);
        }
    }

    public static class This extends LocalEntry {

        public This(Type type) {
            super(type);
        }
    }

    public static class Param extends LocalEntry {

        public final int i;

        public Param(Type type, int i) {
            super(type);
            this.i = i;
        }
    }

    public static class Store extends LocalEntry {

        public final StackEntry e;

        public Store(StackEntry e) {
            super(e.type);
            this.e = e;
        }
    }

    public static class Const extends StackEntry {

        public final Object constObj;

        public Const(AbstractInsnNode insn, Object constObj) {
            super(insn, computeConstType(constObj));
            this.constObj = constObj;
        }
    }

    public static class Load extends StackEntry {

        public final LocalEntry e;

        public Load(AbstractInsnNode insn, LocalEntry e) {
            super(insn, e.type);
            this.e = e;
        }
    }

    public static class UnaryOp extends StackEntry {

        public final StackEntry e;
        public final int op;

        public UnaryOp(AbstractInsnNode insn, StackEntry e) {
            this(insn, e, insn.getOpcode());
        }

        public UnaryOp(AbstractInsnNode insn, StackEntry e, int op) {
            super(insn, e.type);
            this.e = e;
            this.op = op;
        }
    }

    public static class BinaryOp extends StackEntry {

        public final StackEntry e2;
        public final StackEntry e1;
        public final int op;

        public BinaryOp(AbstractInsnNode insn, StackEntry e2, StackEntry e1) {
            this(insn, e2, e1, insn.getOpcode());
        }

        public BinaryOp(AbstractInsnNode insn, StackEntry e2, StackEntry e1, int op) {
            super(insn, e1.type);
            this.e2 = e2;
            this.e1 = e1;
            this.op = op;
        }
    }

    public static class PrimitiveCast extends StackEntry {

        public final StackEntry e;

        public PrimitiveCast(AbstractInsnNode insn, Type type, StackEntry e) {
            super(insn, type);
            this.e = e;
        }
    }

    public static class ReturnAddress extends StackEntry {

        public ReturnAddress(AbstractInsnNode insn) {
            super(insn, INT_TYPE);
        }
    }

    public static class GetField extends StackEntry {

        public final FieldInsnNode field;
        public final StackEntry obj;

        public GetField(FieldInsnNode field, StackEntry obj) {
            super(field, getType(field.desc));
            this.obj = obj;
            this.field = field;
        }
    }

    public static class Invoke extends StackEntry {

        public final int op;
        public final List<StackEntry> params;
        public final StackEntry obj;

        public Invoke(MethodInsnNode method, List<StackEntry> params, StackEntry obj) {
            this(method, method.getOpcode(), params, obj);
        }

        public Invoke(MethodInsnNode method, int op, List<StackEntry> params, StackEntry obj) {
            super(method, getReturnType(method.desc));
            this.op = op;
            this.params = params;
            this.obj = obj;
        }
    }

    public static class InvokeDynamic extends StackEntry {

        public final int op;
        public final List<StackEntry> params;

        public InvokeDynamic(InvokeDynamicInsnNode method, List<StackEntry> params) {
            this(method, method.getOpcode(), params);
        }

        public InvokeDynamic(InvokeDynamicInsnNode method, int op, List<StackEntry> params) {
            super(method, getReturnType(method.desc));
            this.op = op;
            this.params = params;
        }
    }

    public static class New extends StackEntry {

        public New(AbstractInsnNode insn, Type type) {
            super(insn, type);
        }
    }

    public static class NewArray extends StackEntry {

        public final StackEntry len;

        public NewArray(AbstractInsnNode insn, Type type, StackEntry len) {
            super(insn, type);
            this.len = len;
        }
    }

    public static class ArrayLength extends StackEntry {

        public final StackEntry array;

        public ArrayLength(AbstractInsnNode insn, StackEntry array) {
            super(insn, INT_TYPE);
            this.array = array;
        }
    }

    public static class ArrayLoad extends StackEntry {

        public final StackEntry index;
        public final StackEntry array;

        public ArrayLoad(AbstractInsnNode insn, StackEntry index, StackEntry array) {
            super(insn, array.type.getElementType());
            this.index = index;
            this.array = array;
        }
    }

    public static class Cast extends StackEntry {

        public final StackEntry obj;

        public Cast(AbstractInsnNode insn, Type type, StackEntry obj) {
            super(insn, type);
            this.obj = obj;
        }
    }

    public static class NewMultiArray extends StackEntry {

        public final List<StackEntry> sizes;

        public NewMultiArray(AbstractInsnNode insn, Type type, List<StackEntry> sizes) {
            super(insn, type);
            this.sizes = sizes;
        }
    }

    public static class CaughtException extends StackEntry {

        public CaughtException(AbstractInsnNode insn, Type type) {
            super(insn, type);
        }
    }

}
