package com.sspku.jtracer.bytecode;

import org.objectweb.asm.*;

public class ClassPrinter extends ClassVisitor {

    private String className;

    public ClassPrinter() {
        super(Opcodes.ASM8);
    }

    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name.replace('/', '.');
        // System.out.println("[ Printer pure visit.. ] " + name + " " + signature);
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return null;
    }

    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        // 0x0100：native，本地方法。此行使用按位与判断是否为native方法
        if ((access & 256) > 0) {
            System.out.println("[ * Find a native method * ] " + className + '.' +name);
            Util.visitedNative.add(className + '.' +name);
        }
        // System.out.println(" [* Printer visitMethod *] " + access + " " + name + " " + descriptor + " | " + signature);

        MethodVisitor mv = new MethodPrinter();
        return(mv);
//        return new ReflectionMethodInsnVisitor(mv);
    }
}

//class ReflectionMethodInsnVisitor extends MethodVisitor {
//
//    public ReflectionMethodInsnVisitor(MethodVisitor methodVisitor) {
//        super(Opcodes.ASM8, methodVisitor);
//    }
//
//    @Override
//    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
//        // 检查方法调用是否使用了反射
//        if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/lang/reflect/Method")) {
//            // 获取反射调用的方法名和参数类型
//            String methodName = name;
//            Type[] argumentTypes = Type.getArgumentTypes(descriptor);
//            // 输出反射调用的方法信息
//            System.out.println("Reflection method call detected: " + methodName);
//            System.out.println("Argument types:");
//            for (Type argumentType : argumentTypes) {
//                System.out.println(argumentType.getClassName());
//            }
//        }
//        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
//    }
//}

