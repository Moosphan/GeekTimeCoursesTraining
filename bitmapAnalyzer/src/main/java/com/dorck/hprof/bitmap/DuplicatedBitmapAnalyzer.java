package com.dorck.hprof.bitmap;

import com.dorck.hprof.bitmap.util.InstanceUtil;
import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.ClassObj;
import com.squareup.haha.perflib.Heap;
import com.squareup.haha.perflib.HprofParser;
import com.squareup.haha.perflib.Instance;
import com.squareup.haha.perflib.Snapshot;
import com.squareup.haha.perflib.io.MemoryMappedFileBuffer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Analyse duplicated bitmap with Haha lib.
 * Note: 需要使用8.0以下的机器，因为8.0以后Bitmap中的buffer已经放到native内存中了.
 * Todo: LeakCanary源码分析
 * @author dorck
 * @date 2023/03/15
 */
public class DuplicatedBitmapAnalyzer implements BitmapAnalyzer {
    private long startTime = 0L;

    @Override
    public void analyse() {
        // Open hprof file.
        File dumpFile = obtainHprofFile();
        final MemoryMappedFileBuffer buffer;
        try {
            buffer = new MemoryMappedFileBuffer(dumpFile);
            HprofParser hprofParser = new HprofParser(buffer);
            // Parse snapshot from buffer and compute.
            Snapshot snapshot = hprofParser.parse();
            snapshot.computeDominators();
            // Find bitmap clz from snapshot data.
            Collection<ClassObj> bitmapClasses = snapshot.findClasses("android.graphics.Bitmap");
            // Get heap data.
            Collection<Heap> heaps = snapshot.getHeaps();
            print("Bitmap classes count in snapshot => " + bitmapClasses.size());
            print("Heap count in snapshot => " + heaps.size());
            startTrack();
            List<ArrayInstance> arrayInstances = new ArrayList<>();
            for (Heap heap: heaps) {
                if (heap.getName().equals("app")) {
                    for (ClassObj clzObj: bitmapClasses) {
                        // get all bitmap instances from heap.
                        List<Instance> instances = clzObj.getHeapInstances(heap.getId());
                        for (int i = 0; i < instances.size(); i++) {
                            //从GcRoot开始遍历搜索，Integer.MAX_VALUE代表无法被搜索到，说明对象没被引用可以被回收
                            if (instances.get(i).getDistanceToGcRoot() == Integer.MAX_VALUE) {
                                continue;
                            }
                            int curHashCode = getHashCodeByInstance(instances.get(i));
                            for (int j = i + 1; j < instances.size(); j++) {
                                int nextHashCode = getHashCodeByInstance(instances.get(j));
                                if (curHashCode == nextHashCode) {
                                    print("============== Analysis result ===================");
                                    AnalysisResult result = AnalysisResult.from(instances.get(i));
                                    print(result.toString());
                                    print("******* stack info start *********");
                                    getStackInfo(instances.get(i));
                                    print("******* stack info end ********");
                                    print("=======================================================================");
//                                    if (i == instances.size() - 2 && j == instances.size() - 1) {
//                                        print("* stacks info");
//                                        print(result.toString());
//                                        getStackInfo(instances.get(j));
//                                        print("=======================================================================");
//                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            endTrack();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Print Stack info.
     * @param instance
     */
    private void getStackInfo(Instance instance) {
        if (instance.getDistanceToGcRoot() != 0 && instance.getDistanceToGcRoot() != Integer.MAX_VALUE) {
            getStackInfo(instance.getNextInstanceToGcRoot());
        }
        if (instance.getNextInstanceToGcRoot() != null) {
            print("" + instance.getNextInstanceToGcRoot());
        }
    }

    private int getHashCodeByInstance(Instance instance) {
        ArrayInstance bitmapBuffer = InstanceUtil.obtainFieldValue(instance, "mBuffer");
        return Arrays.hashCode(bitmapBuffer.getValues());
    }

    private void print(String msg) {
        System.out.println(msg);
    }

    private File obtainHprofFile() {
        return new File("/Users/dorck/github/GeekTimeCoursesTraining/bitmapAnalyzer/src/main/assets/bitmap.hprof");
    }

    private void startTrack() {
        startTime = System.currentTimeMillis();
    }

    private void endTrack() {
        long endTime = System.currentTimeMillis();
        if (endTime > startTime) {
            print("Duplicated bitmap analyzer time cost: " + (endTime - startTime) + "ms");
        }
    }
}
