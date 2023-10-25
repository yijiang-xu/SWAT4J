package com.sspku.jtracer.srccode;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.List;

public class ResolveSpecificMD extends VoidVisitorAdapter<List<MethodDeclaration>> {

    private String name;

    public ResolveSpecificMD(String name) {
        this.name = name;
    }

    public void visit(MethodDeclaration md, List<MethodDeclaration> list) {
        super.visit(md, list);
        if (name.equals("all") || md.getSignature().asString().equals(name))
            list.add(md);
    }
}