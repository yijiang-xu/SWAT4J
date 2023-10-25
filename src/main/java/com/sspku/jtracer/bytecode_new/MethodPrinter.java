package com.sspku.jtracer.bytecode_new;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;

public class MethodPrinter extends MethodVisitor {

    public MethodPrinter() {
        super(Opcodes.ASM8);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        String className = owner.replace('/', '.');
        if (Util.visitedMethod.contains(className+name+descriptor)) {
            return;
        }

        Util.visitedMethod.add(className+name+descriptor);
        try {
            ClassReader cr = new ClassReader(className);
            ClassFilter cf = new ClassFilter(name+descriptor);
            cr.accept(cf, 0);
        } catch (IOException e) {
            // System.out.println("oops exception.. " + className + " " + name + " " + descriptor);
            // e.printStackTrace();
        }

        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
}