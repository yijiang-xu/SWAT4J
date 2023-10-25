# coding=utf-8

import subprocess
import os
import time

native_methods = "/home/mrx/Desktop/native_methods/tomcat.txt"
tmp_file_name = "new_tmp"


def get_dependent_libs(binary_file_path):
    # 列出动态库依赖关系
    command = "ldd " + binary_file_path
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE)
    res = []
    # e.g 'libstdc++.so.6 => /usr/lib/x86_64-linux-gnu/libstdc++.so.6 (0x00007f9c50d19000)'
    lines = p.stdout.readlines()
    for line in lines:
        line = line.decode("utf-8").replace("\t", "").strip()
        if "/" not in line:
            continue
        res.append(line.split(" ")[-2])
    # 返回依赖库的路径集合
    # i.e
    # /lib/x86_64-linux-gnu/libc.so.6
    # /lib64/ld-linux-x86-64.so.2
    return res


def get_all_dependent_libs(binary_file_paths):
    # 类似层序遍历，返回所有依赖的库路径
    res = []
    for path in binary_file_paths:
        res.extend(get_dependent_libs(path))
    q = set(res)
    while len(q) > 0:
        file_path = q.pop()
        libs = get_dependent_libs(file_path)
        for lib in libs:
            res.append(lib)
            q.add(lib)
    res.extend(binary_file_paths)
    return list(set(res))


def disassemble_libs_to_file(libs):
    #     tmp文件的内容 i.e
    #     0000000000001162 <print>:
    #     1162:	f3 0f 1e fa          	endbr64
    #     1166:	55                   	push   %rbp
    #     1167:	48 89 e5             	mov    %rsp,%rbp
    #     116a:	48 8d 3d 93 0e 00 00 	lea    0xe93(%rip),%rdi        # 2004 <_IO_stdin_used+0x4>
    #     1171:	e8 da fe ff ff       	callq  1050 <puts@plt>
    #     ...
    #
    # 0000000000001180 <__libc_csu_init>:
    #     1180:	f3 0f 1e fa          	endbr64
    #     1184:	41 57                	push   %r15
    #     1186:	4c 8d 3d 2b 2c 00 00 	lea    0x2c2b(%rip),%r15
    #     ...
    for lib in libs:
        os.system("objdump -dj .text " + lib + " >> " + tmp_file_name)
        time.sleep(1)


def get_func_content(lines, start):
    # 把函数体放进map里
    #     1162:	f3 0f 1e fa          	endbr64
    #     1166:	55                   	push   %rbp
    #     1167:	48 89 e5             	mov    %rsp,%rbp
    #     116a:	48 8d 3d 93 0e 00 00 	lea    0xe93(%rip),%rdi        # 2004 <_IO_stdin_used+0x4>
    #     1171:	e8 da fe ff ff       	callq  1050 <puts@plt>
    #     1176:	90                   	nop
    #     1177:	5d                   	pop    %rbp
    #     1178:	c3                   	retq
    #     1179:	0f 1f 80 00 00 00 00 	nopl   0x0(%rax)
    func_content = []
    while start < len(lines):
        line = lines[start]
        tmp = line.split(" ")
        if len(tmp) < 2:
            return func_content
        else:
            # 删除反编译后的注释部分
            if "#" in line:
                idx = line.index("#")
                line = line[:idx]
            line = line.strip(" ").strip("\n")
            func_content.append(line)
            start += 1
    return []


def build_func_map():
    f = open(tmp_file_name, 'r')
    lines = f.readlines()
    funcs = []
    func_map = {}
    for i in range(len(lines)):
        line = lines[i]
        line = line.split(" ")
        # 仅保留函数名一行，例如，<print>:
        # e.g 0000000000001c80 <__snprintf_chk@plt-0x10>:
        if len(line) == 2:
            line = line[1]
            line = line.strip("\n")
        else:
            continue
        if "<" in line and ">" in line and ":" in line:
            if "plt" in line:
                continue
            # e.g. snprintfchk@plt-0x10
            line = line.strip(":").strip("<").strip(">").replace("_", "")
            if "@" in line:
                idx = line.index("@")
                # e.g. snprintfchk
                line = line[:idx]
            # 将该函数的函数体内容放到map里
            if line not in func_map:
                func_map[line] = get_func_content(lines, i + 1)
            else:
                content = get_func_content(lines, i + 1)
                func_map[line].extend(content)
            funcs.append(line)
    return list(set(funcs)), func_map


def get_syscall_id_by_reg(content, reg, start):
    id = ""
    while start >= 0:
        line = content[start]
        instr = line.split("\t")[-1]
        instr = instr.split()
        if "mov" in instr:
            operand = instr[1].split(",")[0]
            dest_reg = instr[1].split(",")[1]
            if reg == dest_reg and "$" in operand:
                id = operand[1:]
                break
        start -= 1
    return id

# 查看反汇编后代码的系统调用编号的方法：
# 从函数体中查找syscall关键字，关键字前一行是否是mov，将后面字段按逗号拆分:
# 如果后面字段包含eax，前面字段包含$且(如['mov', '$0x37,%eax'])，则系统调用编号为$之后的16进制数字
# 如果后面字段包含eax，但前面字段没有$(如 ['mov', '%r9d,%eax'])，则从该函数体继续往上寻找，后面字段 = 当前前面字段 且包含$，则$后的数字为系统调用编号(e.g. Q&A word)
def get_syscall_ids_in_func(content):
    i = len(content) - 1
    ids = []
    while i >= 0:
        # 可能一段函数体调用多次syscall，所以不会直接返回
        # 类似mov的操作符对应的位置，会存在syscall
        if "syscall" not in content[i]:
            i -= 1
            continue
        else:
            # 可以 j = --i
            j = i - 1
            i -= 1
            while j >= 0:
                line = content[j]
                j -= 1
                instr = line.split("\t")[-1]
                # e.g. ['mov', '$0x37,%eax']  ///// ['mov', '%r9d,%eax']
                instr = instr.split()
                if "mov" in instr:
                    operand = instr[1].split(",")[0]
                    dest_reg = instr[1].split(",")[1]
                    if "eax" in dest_reg:
                        if "$" in operand:
                            ids.append(operand[1:])
                            break
                        else:
                            # e.g. ['mov', '%r9d,%eax']
                            id = get_syscall_id_by_reg(content, operand, j)
                            if id != "":
                                ids.append(id)
                            break
    ids = list(set(ids))
    return ids


def get_funcs_in_func(content):
    #     入参 i.e.
    #     1162:	f3 0f 1e fa          	endbr64
    #     1166:	55                   	push   %rbp
    #     1167:	48 89 e5             	mov    %rsp,%rbp
    #     116a:	48 8d 3d 93 0e 00 00 	lea    0xe93(%rip),%rdi
    #     1171:	e8 da fe ff ff       	callq  1050 <puts@plt>
    funcs = []
    for line in content:
        instr = line.split("\t")[-1]
        if "<" in instr and ">" in instr:
            # print(instr)
            start_idx = instr.index("<")
            end_idx = len(instr)
            if "+" in instr:
                # i.e <__do_global_dtors_aux+0x27>
                end_idx = instr.index("+")
            if "@" in instr:
                # i.e puts@plt
                end_idx = instr.index("@")

            instr = instr[start_idx:end_idx]
            func = instr.strip("<").strip(">").replace("_", "")
            funcs.append(func)
    # 返回函数体里调用的函数名称
    return list(set(funcs))


def get_all_funcs(content, func_map):
    # content：函数体
    # 函数体里出现的所有函数名
    all_funcs = get_funcs_in_func(content)
    q = set(all_funcs)
    history = [q]
    while len(q) > 0:
        func = q.pop()
        if func not in func_map:
            ends_funcs = get_endswith_funcs(func_map, func)
            for ends_func in ends_funcs:
                if ends_func not in history:
                    q.add(ends_func)
            continue
        history.append(func)
        func_content = func_map[func]
        funcs = get_funcs_in_func(func_content)
        all_funcs.extend(funcs)
        for f in funcs:
            if f not in history:
                q.add(f)
                print("111")
    all_funcs.sort()
    return list(set(all_funcs))


def get_endswith_funcs(func_map, func):
    # glibc中函数的前缀并无明显规律，这里会在func_map中搜索所有以func结尾的函数
    # 给定一个func，找到以该func结尾的funcs
    ends_funcs = []
    for k in func_map.keys():
        if k.endswith(func):
            ends_funcs.append(k)
    return ends_funcs


def analyze(file2funcs):
    syscall_ids = set()
    # 返回所有依赖的库路径
    libs = get_all_dependent_libs(file2funcs.keys())
    # libs = file2funcs.keys()
    print("\n[ * Start binary analyse and there are %d dependent libs * ]" % (len(libs)))
    time.sleep(0.5)
    disassemble_libs_to_file(libs)
    # 得到每个函数所对应的  函数体(content)  的map
    _, func_map = build_func_map()

    # 从函数体content中，找调用的所有函数，一同分析，得到系统调用编号集合
    for funcs in file2funcs.values():
        # print funcs
        for func in funcs:
            all_funcs = []
            func = func.strip(":").strip("<").strip(">").replace("_", "")
            if "@" in func:
                idx = func.index("@")
                func = func[:idx]
            if func in func_map.keys():
                func_content = func_map[func]
                # 得到函数中所调用的函数集合
                all_funcs.extend(get_all_funcs(func_content, func_map))
            else:
                # 如果func在func_map中找不到, func还可能是某个glibc函数的别名，真正的函数名称与func相比多了一些前缀
                ends_funcs = get_endswith_funcs(func_map, func)
                for ends_func in ends_funcs:
                    all_funcs.extend(get_all_funcs(func_map, ends_func))
            all_funcs = list(set(all_funcs))
            # print(len(all_funcs))
            # cur_syscall = []
            for f in all_funcs:
                if f in func_map:
                    content = func_map[f]
                    ids = get_syscall_ids_in_func(content)
                    # cur_syscall.extend(ids)
                    syscall_ids.update(tuple(ids))

    os.system("rm " + tmp_file_name)

    print("[ * Complete binary analyse successfully * ]")
    time.sleep(0.5)
    return syscall_ids


def id2name(ids):
    # 返回系统调用号-系统调用名之间的映射
    command = "ausyscall --dump"
    p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE)
    syscalls = []
    res = []
    for line in p.stdout.readlines():
        syscalls.append(line.decode("utf-8").strip('\n').split('\t')[-1])
    for n in ids:
        # change!!!!!
        idx = int(n, 16) + 1
        if 0 < idx <= len(syscalls):
            res.append(syscalls[idx])
    return res


# 建立Tomcat自带native方法到JNI函数名映射
def build_netty_map():
    res = {}
    command = 'objdump -dj .text /home/mrx/Desktop/so/libnetty-transport-native-epoll.so | grep \'netty_.*>:\' | awk \'{print $2}\''
    lines = os.popen(command).readlines()
    for line in lines:
        # <Java_org_apache_tomcat_jni_Local_connect>:
        line = line.strip('\n')[1:-2]
        native_method = line.replace('_', '.')
        res[native_method] = line + ' ' + '/home/mrx/Desktop/so/libnetty-transport-native-epoll.so'
    print("[ * Build netty-native-methods to JNI-functions mapping successfully * ]")
    time.sleep(0.5)
    return res


def build_jffi_map():
    res = {}
    command = 'objdump -dj .text /home/mrx/Desktop/so/libjffi-1.2.so | grep \'Java_com.*>:\' | awk \'{print $2}\''
    lines = os.popen(command).readlines()
    for line in lines:
        # <Java_org_apache_tomcat_jni_Local_connect>:
        line = line.strip('\n')[1:-2]
        tc_native_method = line[5:].replace('_', '.')
        res[tc_native_method] = line + ' ' + '/home/mrx/Desktop/so/libjffi-1.2.so'
    print("[ * Build jffi-native-methods to JNI-functions mapping successfully * ]")
    time.sleep(0.5)
    return res


def build_tc_map():
    res = {}
    command = 'objdump -dj .text /usr/local/apr/lib/libtcnative-1.so.0 | grep \'Java_org.*>:\' | awk \'{print $2}\''
    lines = os.popen(command).readlines()
    for line in lines:
        # <Java_org_apache_tomcat_jni_Local_connect>:
        line = line.strip('\n')[1:-2]
        tc_native_method = line[5:].replace('_', '.')
        res[tc_native_method] = line + ' ' + '/usr/local/apr/lib/libtcnative-1.so.0'
    print("[ * Build tomcat-native-methods to JNI-functions mapping successfully * ]")
    time.sleep(0.5)
    return res


def build_org_map():
    res = {}
    command = 'objdump -dj .text /home/mrx/Desktop/so/libsigar-x86-linux.so | grep \'Java_org.*>:\' | awk \'{print $2}\''
    lines = os.popen(command).readlines()
    for line in lines:
        # <Java_org_apache_tomcat_jni_Local_connect>:
        line = line.strip('\n')[1:-2]
        tc_native_method = line[5:].replace('_', '.')
        res[tc_native_method] = line + ' ' + '/home/mrx/Desktop/so/libsigar-x86-linux.so'
    print("[ * Build org-native-methods to JNI-functions mapping successfully * ]")
    time.sleep(0.5)
    return res


# 建立JVM_格式的JNI函数名映射
def build_JVM_map():
    FILE_SRC = '/home/mrx/Desktop/native'
    # e.g java.nio.System.nanoTime() - JVM_NanoTime@libjvm.so
    jvm_dict = {}
    # openjdk8源代码所在的绝对路径
    for home, dirs, files in os.walk(FILE_SRC):
        for file in files:
            if file[-2:] == '.c':
                path = home + '/' + file
                pkg = home[len(FILE_SRC) + 1:] + '/' + file[:-2] + '/'
                pkg = pkg.replace('/', '.')
                text = str(os.popen('cat ' + path + ' | grep \'&JVM_\'').readlines())
                idx = 0
                while idx < len(text):
                    if text[idx] == '{':
                        idx += 1
                        tmp = idx
                        while idx < len(text) and text[idx] != '}':
                            idx += 1
                            item = text[tmp:idx]
                            # print(item)

                        # process item
                        parts = item.split(',')
                        native = parts[0].strip('\"')
                        jni = parts[-1]
                        tmp = -1
                        while jni[tmp] != '&':
                            tmp -= 1
                        jni = jni[tmp + 1:]
                        # print(pkg + native, jni)
                        jvm_dict[
                            pkg + native] = jni + ' /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/libjvm.so'
                    idx += 1
    print("[ * Build JDK-native-methods (JVM_XXX) to JNI-functions mapping successfully * ]")
    time.sleep(0.5)
    return jvm_dict


# 建立Java_格式的JNI函数名映射
def build_map():
    # 首先执行命令行命令，生成各个.so文件的符号表
    command = "sh generate_all_jni_names.sh"
    subprocess.Popen(command, shell=True, stdout=subprocess.PIPE)
    # jdk所在绝对路径
    FILE_SRC = "/home/mrx/Desktop/soname"
    native_map = {}
    for home, dirs, files in os.walk(FILE_SRC):
        for filename in files:
            native_map = read_from_so_file(native_map, filename)
    print("\n\n[ * Build JDK-native-methods (Java_XXX) to JNI-functions mapping successfully * ]")
    time.sleep(0.5)

    return native_map


def read_from_so_file(native_map, filename):
    # e.g. <Java_java_lang_System_setOut0@@SUNWprivate_1.1>: -> java_lang_System_setOut0 -> java.lang.System.setOut0
    FILE_SRC = "/home/mrx/Desktop/soname"
    soname = filename[0:-4]
    with open(FILE_SRC + '/' + filename, 'r') as file_object:
        lines = file_object.readlines()

    for line in lines:
        idx = line.index('@')
        line = line[6:idx]
        original_jni = line.replace('_', '.')
        native_map[original_jni] = line + ' /usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/' + soname
    # jni函数的命名规则
    return native_map


# 经过映射得到JNI函数名，按文件合并，形成二进制分析的输入
def aggregate(native_map, native_methods):
    print("[ * Convert native-methods to JNI-functions with its located lib successfully * ]")
    time.sleep(0.5)
    # 该变量仅用于控制打印提示
    cnt = 0
    res = {}
    for m in native_methods:
        if m in native_map:
            val = native_map[m].split(' ')
            jni_name = val[0]
            libpath = val[1]
            if cnt < 1:
                println()
                print("Here is an example:\n\n%s -> %s (%s)" % (str(m), jni_name, libpath))
                println()
                time.sleep(0.5)
                cnt += 1
            if libpath not in res:
                res[libpath] = []
            # 筛选出Java_java开头的本地方法，在聚合后进行拼接。其他的不变
            if jni_name.startswith("JVM") or jni_name.startswith("sun") or "org" in jni_name:
                res[libpath].append(jni_name)
            else:
                res[libpath].append("Java_" + jni_name)

        # netty func select
        if m.startswith("io.netty"):
            end = m.split(".")[-1]
            for name in native_map:
                if name.startswith("netty") and name.endswith(end):
                    val = native_map[name].split(' ')
                    jni_name = val[0]
                    libpath = val[1]
                    if libpath not in res:
                        res[libpath] = []
                    res[libpath].append(jni_name)
    return res


def println():
    print("------------------------------------------------------------------------------------------------------------------------------------------------")


if __name__ == '__main__':
    T1 = time.time()
    # JNI映射模块
    native_map = build_map()
    native_JVM_map = build_JVM_map()
    build_jffi_map = build_jffi_map()
    native_netty_map = build_netty_map()
    build_tc_map = build_tc_map()
    build_org_map = build_org_map()

    native_map.update(native_JVM_map)
    native_map.update(build_jffi_map)
    native_map.update(native_netty_map)
    native_map.update(build_tc_map)
    native_map.update(build_org_map)

    native_methods = []
    with open(native_methods, 'r') as f:
        lines = f.readlines()
    for m in lines:
        m = m.strip('\n')
        native_methods.append(m)
    # 合并java源码和被分析项目的native方法map
    # e.g '/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/libjvm.so' -> ['JVM_IsThreadAlive', 'JVM_GetClassInterfaces', 'JVM_MonitorNotify', 'JVM_MonitorNotifyAll', 'JVM_Interrupt', 'JVM_GetEnclosingMethodInfo', 'JVM_DesiredAssertionStatus', 'JVM_GetClassDeclaredMethods', 'JVM_CurrentThread', 'JVM_DumpThreads', 'JVM_GetDeclaringClass', 'JVM_GetClassSigners', 'JVM_StopThread', 'JVM_ArrayCopy', 'JVM_GetClassName', 'JVM_CurrentTimeMillis', 'JVM_Yield', 'JVM_GetProtectionDomain', 'JVM_Clone', 'JVM_IsPrimitiveClass', 'JVM_IsInterrupted', 'JVM_IHashCode', 'JVM_Sleep', 'JVM_MonitorWait', 'JVM_GetComponentType', 'JVM_IsArrayClass', 'JVM_IsInterface', 'JVM_GetClassDeclaredConstructors', 'JVM_GetClassAnnotations', 'JVM_ResumeThread', 'JVM_SetNativeThreadName', 'JVM_HoldsLock', 'JVM_NanoTime', 'JVM_SetThreadPriority', 'JVM_GetClassConstantPool', 'JVM_GetClassSignature', 'JVM_GetClassModifiers', 'JVM_StartThread', 'JVM_GetClassDeclaredFields']
    jni_methods = aggregate(native_map, native_methods)

    # 二进制分析模块
    syscall_ids = analyze(jni_methods)
    print("[ * There are %d system calls, and ids are listed below * ]\n" % (len(syscall_ids)))
    print(syscall_ids)
    print('\n')
    time.sleep(0.5)
    print("[ * Convert ids to real names and list below * ]\n")
    time.sleep(0.5)
    syscalls = id2name(syscall_ids)
    syscalls = sorted(syscalls)
    for syscall in syscalls:
        print("\"%s\"" % syscall)
    T2 = time.time()
    print("Time:%s" % (T2 - T1))
