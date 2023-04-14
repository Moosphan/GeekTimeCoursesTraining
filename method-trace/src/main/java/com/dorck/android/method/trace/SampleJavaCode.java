package com.dorck.android.method.trace;

import android.util.Log;

public class SampleJavaCode {

    public void trackTimeCost() {
        long startTime = System.currentTimeMillis();
        long timeCost = System.currentTimeMillis() - startTime;
        System.out.println("Time of method calling: " + timeCost + ", name[ddddd]");
    }

    public static void main(String[] args) {
        
    }
}
