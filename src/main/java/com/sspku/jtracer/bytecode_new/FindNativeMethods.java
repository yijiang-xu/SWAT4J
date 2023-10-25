package com.sspku.jtracer.bytecode_new;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class FindNativeMethods {

    // 定义一个公共方法，用于分析 Jar 文件中的 native 方法
    public Set<String> findNativeMethods(String jarFileName) throws IOException {
        // 创建一个 HashSet，用于存储找到的 native 方法的名称
        Set<String> nativeMethods = new HashSet<>();

        // 创建一个 JarFile 对象，用于读取 Jar 文件中的内容
        JarFile jarFile = new JarFile(jarFileName);

        // 遍历 Jar 文件中的每个 entry
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();

            // 如果 entry 是一个 class 文件
            if (entry.getName().endsWith(".class")) {
                try (InputStream inputStream = jarFile.getInputStream(entry)) {
                    // 使用 ASM 的 ClassReader 来解析 class 文件并创建一个 ClassVisitor
                    ClassReader classReader = new ClassReader(inputStream);
                    classReader.accept(new NativeMethodVisitor(entry.getName(), nativeMethods), 0);
                } catch (Exception e) {
                    System.err.println("Error processing class " + entry.getName() + ": " + e.getMessage());
                }
            }
        }

        // 关闭 JarFile 对象，释放资源，并返回找到的 native 方法的名称集合
        jarFile.close();
        return nativeMethods;
    }

    // 实现一个 ClassVisitor，用于解析 class 文件中的方法
    private static class NativeMethodVisitor extends ClassVisitor {

        private final String className;
        private final Set<String> nativeMethods;

        public NativeMethodVisitor(String className, Set<String> nativeMethods) {
            // 调用父类的构造函数来初始化 ClassVisitor
            super(Opcodes.ASM8);
            this.className = className;
            this.nativeMethods = nativeMethods;
        }

        // 实现 visitMethod 方法，用于解析 class 文件中的方法
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            // 如果方法是 native 方法，则将方法名称和类名称拼接成一个字符串，然后添加到 nativeMethods 集合中
            if ((access & Opcodes.ACC_NATIVE) != 0) {
//                String nativeMethod = className + ":" + name;
                String jniFunction = className.replace(".class", "")
                        .replace("/", ".")+ "." + name + "---" + descriptor;
                nativeMethods.add(jniFunction);
            }
            // 返回 null，表示不需要解析方法的代码
            return null;
        }
    }
}
