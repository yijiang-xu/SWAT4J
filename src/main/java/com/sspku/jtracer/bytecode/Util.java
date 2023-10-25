package com.sspku.jtracer.bytecode;

import java.util.HashSet;
import java.util.Set;

public class Util {

    public static Set<String> visitedMethod = new HashSet<>();

    public static Set<String> visitedNative = new HashSet<>();

    public static void println() {
        System.out.println("--------------------------------");
    }
}