package com.sspku.jtracer.bytecode_new;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

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
        return mv;
    }
}