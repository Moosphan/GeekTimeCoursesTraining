package com.dorck.hprof.bitmap;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        BitmapAnalyzer bitmapAnalyzer = new DuplicatedBitmapAnalyzer();
        bitmapAnalyzer.analyse();
    }
}