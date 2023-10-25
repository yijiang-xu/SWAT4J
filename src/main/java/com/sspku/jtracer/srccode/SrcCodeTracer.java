package com.sspku.jtracer.srccode;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.*;
import java.util.*;

public class SrcCodeTracer {

    // pkgs.txt文件怎么来的？？？？？？？？？？？？？？
    final String PKGS_SRC = "src/main/resources/pkgs.txt";

    String ROOT_SRC;

    Set<String> pkgSet = new HashSet<String>();

    Set<MethodDeclaration> dupSet = new HashSet<MethodDeclaration>();

    Set<MethodDeclaration> nativeSet = new HashSet<MethodDeclaration>();

    public SrcCodeTracer(String root_src) {
        this.ROOT_SRC = root_src;
        configureSolver();
        initPkgSet();
    }

    /**
     * init javaparser configuration
     */
    public void configureSolver() {
        TypeSolver reflectionTypeSolver = new ReflectionTypeSolver();
        TypeSolver javaParserTypeSolver = new JavaParserTypeSolver(new File(ROOT_SRC));
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(reflectionTypeSolver);
        combinedTypeSolver.add(javaParserTypeSolver);
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
    }

    /**
     * init jdk packages set
     */
    public void initPkgSet() {
        try {
            File file = new File(PKGS_SRC);
            BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
            String strLine = null;
            // int lineCount = 1;
            while(null != (strLine = bufferedReader.readLine())){
                pkgSet.add(strLine);
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * to extract the package and method detail from method signature
     * @param signature
     * @return
     */
    /*
    java.io.PrintStream.println(java.lang.String) => ROOT_SRC + java/io/PrintStream.java | println(String)
     */
    public String processMethodSignature(String signature) {
        StringBuilder sbParams = new StringBuilder();
        int idx = signature.length() - 2;
        while (idx > 0 && signature.charAt(idx) != '(') {
            sbParams.insert(0, signature.charAt(idx--));
        }
        StringBuilder sbMethodName = new StringBuilder();
        while (idx > 0 && signature.charAt(idx) != '.') {
            sbMethodName.insert(0, signature.charAt(idx--));
        }
        String pkg = signature.substring(0, idx);
        if (pkgSet.contains(pkg)) {
            pkg = pkg.replace('.', '/') + ".java";
        } else {
            pkg = ROOT_SRC + pkg.replace('.', '/') + ".java";
        }
        String params = sbParams.toString();
        String methodName = sbMethodName.toString();
        String[] paramArr = params.split(",");
        for (int i = 0; i < paramArr.length; i++) {
            String str = paramArr[i];
            String[] tmp = str.split("\\.");
            if (i < paramArr.length-1) {
                if (paramArr[i].contains(".") && i > 0)
                    methodName += " " + tmp[tmp.length-1] + ",";
                else
                    methodName += tmp[tmp.length-1] + ",";
            } else {
                methodName += tmp[tmp.length-1] + ")";
            }
        }
        return pkg + "|" + methodName;
    }

    /**
     * get the method declaration from a given .java file
     * @param path
     * @return
     * @throws FileNotFoundException
     */
    public MethodDeclaration getMethodDeclaration(String path) throws FileNotFoundException {

        String[] res = processMethodSignature(path).split("\\|");
        String filePath = res[0];
        String methodName = res[1];

        CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
        List<MethodDeclaration> list = new ArrayList<MethodDeclaration>();
        ResolveSpecificMD visitor = new ResolveSpecificMD(methodName);
        visitor.visit(cu, list);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * depth-first search
     * @param md
     * @throws FileNotFoundException
     */
    public void dfs(MethodDeclaration md) throws FileNotFoundException {
        if (md == null || dupSet.contains(md)) {
            if (md != null)
                // System.out.println(md.getSignature() + "#########");
            return;
        }
        dupSet.add(md);
        if (md.isNative())
            nativeSet.add(md);

        ResolveMCE collector = new ResolveMCE();
        List<MethodCallExpr> mceList = new ArrayList<MethodCallExpr>();
        collector.visit(md, mceList);
        for (MethodCallExpr mce : mceList) {
            try {
                ResolvedMethodDeclaration rmd = mce.resolve();
                MethodDeclaration mcemd = getMethodDeclaration(rmd.getQualifiedSignature());
                dfs(mcemd);
            } catch (Exception e) {
                // System.out.println("Exception..");
                continue;
            }
        }
    }

    /**
     * the entrance of tracer
     * @param path
     * @throws FileNotFoundException
     */
    public void trace(String path) throws FileNotFoundException {
        CompilationUnit cu = StaticJavaParser.parse(new File(path));
        List<MethodDeclaration> list = new ArrayList<MethodDeclaration>();
        // 查找指定path的MD节点
        ResolveSpecificMD visitor = new ResolveSpecificMD("all");
        visitor.visit(cu, list);
        for (MethodDeclaration md : list) {
            dfs(md);
        }
    }

    /**
     * pretty print of result
     */
    public void prettyPrint() {
        System.out.println("************ Native Method ************\n");
        System.out.println("count: " + nativeSet.size());
        for (MethodDeclaration md : nativeSet) {
            String signature = md.resolve().getQualifiedSignature();
            int idx = 0;
            while (idx < signature.length() && signature.charAt(idx) != '(') { idx++; }
            String method = signature.substring(0, idx);
            System.out.println("Signature:\t" + signature);
        }
    }

    public static void main(String[] args) throws IOException {
        SrcCodeTracer srcCodeTracer = new SrcCodeTracer("");
        // 这是一个简单的示例
        srcCodeTracer.trace("/home/mrx/Desktop/JTracer/src/main/java/com/sspku/jtracer/srccode/Foo.java");
        srcCodeTracer.prettyPrint();
    }
}