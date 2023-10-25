package com.sspku.jtracer.bytecode_new;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ClassFilter extends ClassVisitor {

    private String identifier;

    private String className;

    public ClassFilter(String identifier) {
        super(Opcodes.ASM8);
        this.identifier = identifier;
    }

    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name.replace('/', '.');
        // System.out.println("[ Filter pure visit.. ] " + name + " " + signature);
    }

    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (!this.identifier.equals(name+descriptor)) {
            return null;
        }
        if ((access & 256) > 0) {
            System.out.println("[ * Find a native method * ] " + className + '.' +name);
            Util.visitedNative.add(className + '.' +name);
        }
        // System.out.println(" [* Filter visitMethod *] " + access + " " + name + " " + descriptor + " | " + signature);

        MethodVisitor mv = new MethodPrinter();
        return mv;
    }
}