package com.sspku.jtracer.bytecode_new;

// import com.sspku.jtracer.bytecode.ClassPrinter;
// import com.sspku.jtracer.bytecode.Util;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class NewByteCodeTracer {
    public static String nativeFile = "/home/mrx/Desktop/native_methods/tomcat.txt";
    public static String pathPrefix = "/home/mrx/.m2/repository/";

    public static String groupId= "org.apache.tomcat.maven";
    public static String artifactId= "tomcat7-maven-plugin";

//    public static String groupId = "org.eclipse.jetty";
//    public static String artifactId = "jetty-server";

//    public static String groupId = "com.orientechnologies";
//    public static String artifactId = "orientdb-core";

//    public static String groupId = "org.apache.cassandra";
//    public static String artifactId = "cassandra-all";

//    public static String groupId = "org.elasticsearch";
//    public static String artifactId = "elasticsearch";

//    public static String groupId = "org.apache.solr";
//    public static String artifactId = "solr-core";

//    public static String groupId = "org.apache.zookeeper";
//    public static String artifactId = "zookeeper";

//    public static String groupId = "org.apache.storm";
//    public static String artifactId = "storm-core";

//    public static String groupId = "org.apache.groovy";
//    public static String artifactId = "groovy";

//    public static String groupId = "org.jruby";
//    public static String artifactId = "jruby-complete";

    public static void main(String[] args) throws IOException {

        long startTime = System.currentTimeMillis();
        List<String> jarPathResult = new ArrayList<>();
        Set<String> nativeMethodResult = new HashSet<>();
        GetJarPaths getJarPaths = new GetJarPaths();

        List<String> classPathResult = new ArrayList<>();
        FindJarClass findJarClass = new FindJarClass();

//        没用到的方法，用于生成对应native修饰的方法名称。
        FindNativeMethods findNativeMethods = new FindNativeMethods();
        try {
            jarPathResult = getJarPaths.getJarPaths(groupId, artifactId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
//        System.out.println(jarPathResult);

        for(String jarPath : jarPathResult){
            jarPath = pathPrefix + jarPath;
            File file = new File(jarPath);
            // 目录分析错误，则跳过
            if (!file.exists() || !file.isFile()){
                System.out.println("Opps!");
                continue;
            }
            classPathResult.addAll(findJarClass.findClass(jarPath));
            nativeMethodResult.addAll(findNativeMethods.findNativeMethods(jarPath));
        }
//        System.out.println(classPathResult);
//        System.out.println(classPathResult.size());
        System.out.println("There are " + classPathResult.size() + " classes over all\n");
        System.out.println("Now we start to analyze called native methods!");
        Util.println();
        NewByteCodeTracer bct = new NewByteCodeTracer();
        bct.trace(classPathResult);
        bct.prettyPrint();
        bct.writeToFile();
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double totalTimeInSeconds = (double) totalTime / 1000;
        System.out.println("程序运行时间：" + totalTimeInSeconds + "秒");

    }
    public void trace(String className) throws IOException {
        ClassReader cr = new ClassReader(className);
        com.sspku.jtracer.bytecode_new.ClassPrinter cp = new ClassPrinter();
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
