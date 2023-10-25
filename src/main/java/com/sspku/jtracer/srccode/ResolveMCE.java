package com.sspku.jtracer.srccode;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.List;

public class ResolveMCE extends VoidVisitorAdapter<List<MethodCallExpr>> {

    @Override
    public void visit(MethodCallExpr mce, List<MethodCallExpr> list) {
        super.visit(mce, list);
        list.add(mce);
    }
}