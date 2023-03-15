package com.dorck.hprof.bitmap;

import com.dorck.hprof.bitmap.util.InstanceUtil;
import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.Instance;

import java.util.Arrays;

public class AnalysisResult {
    private int hashCode;
    private String classInstance;
    private int width;
    private int height;
    private int bufferSize;

    public static AnalysisResult from(Instance instance) {
        AnalysisResult result = new AnalysisResult();
        ArrayInstance bitmapBuffer = InstanceUtil.obtainFieldValue(instance, "mBuffer");
        result.setClassInstance(bitmapBuffer.toString());
        result.setWidth(InstanceUtil.<Integer>obtainFieldValue(instance, "mWidth"));
        result.setHeight(InstanceUtil.<Integer>obtainFieldValue(instance, "mHeight"));
        result.setHashCode(Arrays.hashCode(bitmapBuffer.getValues()));
        result.setBufferSize(bitmapBuffer.getValues().length);
        return result;
    }

    @Override
    public String toString() {
        return "{" + "\n"
                + "\t" + "bufferHashCode:" + this.hashCode + ",\n"
                + "\t" + "width:" + this.width + ",\n"
                + "\t" + "height:" + this.height + ",\n"
                + "\t" + "bufferSize:" + this.bufferSize + ",\n"
                + "}";
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public int getHashCode() {
        return hashCode;
    }

    public void setHashCode(int hashCode) {
        this.hashCode = hashCode;
    }

    public String getClassInstance() {
        return classInstance;
    }

    public void setClassInstance(String classInstance) {
        this.classInstance = classInstance;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }
}
