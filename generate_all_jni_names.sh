# 从Java源码中的.so文件中，将二进制文件反汇编得到汇编代码，筛选出Java_开头的函数名，记录保存
files=`ls /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64 | grep .so`
for file in ${files}; do
  `objdump -dj .text /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/${file} | grep "\bJava_.*>:" | awk '{print $2}' > ~/Desktop/soname/${file}.txt`
  # echo ${file}
  # 记得创建soname文件夹
done