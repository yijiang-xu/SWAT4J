package com.sspku.jtracer.bytecode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class GetJarPaths {
//    public static String groupId= "org.apache.tomcat.maven";
//    public static String artifactId= "tomcat7-maven-plugin";
    public static String groupId = "org.elasticsearch";
    public static String artifactId = "elasticsearch";
//    public static String groupId = "org.neo4j";
//    public static String artifactId = "neo4j";

    public static void main(String[] args) throws IOException {
        String containerName = groupId + ":" + artifactId;
        String command = "mvn dependency:tree";
        Process process = Runtime.getRuntime().exec(command);

        InputStream inputStream = process.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        // 是否找到当前要分析的Jar包
        Boolean flag = false;
        // 记录:出现的位置
        int index;
        while ((line = reader.readLine()) != null) {

            // 统计空格数量，如果大于2，则说明为子目录; 小于等于2，则说明为新的依赖，当前分析结束
            // (经验分析) 只有初始目录包含2个空格，子目录空格都大于2
            if (flag && (line.split(" ").length - 1) <= 2) {
                flag = false;
                break;
            }

            if (line.contains(containerName)) {
                flag = true;
            }

            if (flag) {
                // 如果分析完所有的依赖,则不存在:
                if (!line.contains(":")) {
                    break;
                }

                index = line.indexOf(":");
                // 拼接Jar包名
                String jarName, jarPath;
                String pre, post;
                // [INFO] - org.elasticsearch:elasticsearch:jar:7.5.2:compile中
                // 冒号前部分[INFO] - org.elasticsearch => org/elasticsearch
                pre = line.substring(0, index);
                pre = pre.replace("[INFO] ", "")
                        .replace("\\- ", "")
                        .replace("+-", "")
                        .replace("|", "")
                        .replaceAll(" ", "")
                        .replaceAll("\\.", "/");
//                System.out.println(pre);

                //冒号后部分:elasticsearch:jar:7.5.2:compile => /elasticsearch/7.5.2/
                post = line.substring(index, line.length());
                post = post.replace(":jar", "")
                        .replace(":compile", "")
                        .replace(":", "/");
//                System.out.println(post);

                // 存储按/拆分后的post
                String[] list = post.split("/");
                jarName = "/" + list[list.length - 2] + "-" + list[list.length - 1];
                jarName = jarName.concat(".jar");
//                System.out.println(jarName);

                // 拼接后 org/elasticsearch/elasticsearch/7.5.2/elasticsearch-7.5.2.jar
                // 前缀为 /home/mrx/.m2/repository/
                jarPath = pre + post + jarName;
                System.out.println(jarPath);
            }

            // 当前依赖分析结尾的格式为  [INFO]    \- org.elasticsearch:jna:jar:4.5.1:compile
            if (line.contains("\\-") && !line.contains("|") && line.contains("  ")) {
                flag = false;
                break;
            }

//            System.out.println(line);
        }
    }
}
