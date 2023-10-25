package com.sspku.jtracer.bytecode_new;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class GetJarPaths {

    public List<String> getJarPaths(String groupId, String artifactId) throws IOException {

        ArrayList<String> result = new ArrayList<>();

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

                post = line.substring(index, line.length());
                // 冒号后部分: 处理特殊情况     io.netty:netty-transport-native-epoll:jar:linux-aarch_64:4.1.86.Final:compile
                // => /netty-transport-native-epoll/4.1.86.Final/netty-transport-native-epoll-4.1.86.Final-linux-aarch_64.jar
                if (post.split("jar")[1].split(":").length == 4){
                    post = post.split("jar")[0].replace(":", "/")
                            + post.split("jar")[1].split(":")[2]
                            + "/" + post.split("jar")[0].replace(":", "")
                            + "-" + post.split("jar")[1].split(":")[2]
                            + "-" + post.split("jar")[1].split(":")[1]
                            + ".jar";
                    jarPath = pre + post;
                    result.add(jarPath);

                } else {
                    //正常情况:elasticsearch:jar:7.5.2:compile => /elasticsearch/7.5.2/
                    post = post.replace(":jar", "")
                            .replace(":compile", "")
                            .replace(":runtime", "")
                            .replace(":", "/");
//                  System.out.println(post);

                    // 存储按"/"拆分后的post
                    String[] list = post.split("/");
                    jarName = "/" + list[list.length - 2] + "-" + list[list.length - 1];
                    jarName = jarName.concat(".jar");
//                  System.out.println(jarName);

                    // 拼接后 org/elasticsearch/elasticsearch/7.5.2/elasticsearch-7.5.2.jar
                    // 前缀为 /home/mrx/.m2/repository/
                    jarPath = pre + post + jarName;
                    result.add(jarPath);
                }
                System.out.println(jarPath);


            }

//            System.out.println(line);
        }
        return result;
    }
}
