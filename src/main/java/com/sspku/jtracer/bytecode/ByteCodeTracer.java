package com.sspku.jtracer.bytecode;// import com.alibaba.fastjson.JSONArray;
import org.objectweb.asm.ClassReader;

import java.io.*;
import java.util.List;

public class ByteCodeTracer {
    public static String jarName = "org.apache";
    public static String nativeFile = "/home/mrx/Desktop/native_methods/tomcat.txt";
    public static void main(String[] args) throws IOException {
        // ClassReader作为字节码生产者，dClassPrinter作为字节码消费者
        // 获取org.apache下所有class的名称
        List<String> jars = Recursor.getJarNames(jarName);
        Util.println();
        System.out.println("There are " + jars.size() + " classes over all\n");
        System.out.println("Now we start to analyze called native methods");
        Util.println();
        ByteCodeTracer bct = new ByteCodeTracer();
        bct.trace(jars);
        bct.prettyPrint();
        bct.writeToFile();
    }

    public void trace(String className) throws IOException {
        ClassReader cr = new ClassReader(className);
        ClassPrinter cp = new ClassPrinter();
        cr.accept(cp, 0);
    }

    public void trace(List<String> classes) throws IOException {
        for (String className : classes) {
            trace(className);
        }
    }

    public void writeToFile() {
        try {
            String content = "";
            for (String m : Util.visitedNative) {
                content += m + "\n";
            }
            File file =new File(nativeFile);
            if(!file.exists()){
                file.createNewFile();
            }
            FileWriter fw = new FileWriter(file.getAbsolutePath());
            fw.write(content);
            fw.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public void prettyPrint() {
        Util.println();
        System.out.println("Count of visited methods: " + Util.visitedMethod.size() +
                "\n\nCount of called native methods: " + Util.visitedNative.size() +
                "\n\nWe write all of them into a .txt file for binary analyse");
        Util.println();
    }
}