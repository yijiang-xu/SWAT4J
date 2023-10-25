package com.sspku.jtracer.bytecode_new;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class FindJarClass {

    // 定义一个公共方法，用于分析 Jar 文件中的 native 方法
    public List<String> findClass(String jarFileName) throws IOException {
        // 创建一个 HashSet，用于存储找到的 native 方法的名称
        List<String> classPaths = new ArrayList<>();

        // 创建一个 JarFile 对象，用于读取 Jar 文件中的内容
        JarFile jarFile = new JarFile(jarFileName);

        // 遍历 Jar 文件中的每个 entry
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();

            // 如果 entry 是一个 class 文件
            if (entry.getName().endsWith(".class")) {
                classPaths.add(entry.getName().replace(".class", "")
                        .replace("/", "."));
            }
        }

        // 关闭 JarFile 对象，释放资源，并返回找到的 native 方法的名称集合
        jarFile.close();
        return classPaths;
    }
}