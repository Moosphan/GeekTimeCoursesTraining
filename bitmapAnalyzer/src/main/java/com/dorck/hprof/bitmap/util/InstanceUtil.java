package com.dorck.hprof.bitmap.util;

import com.squareup.haha.perflib.ClassInstance;
import com.squareup.haha.perflib.Instance;

import java.util.List;

public class InstanceUtil {
    public static <T> T obtainFieldValue(Instance instance, String fieldName) {
        List<ClassInstance.FieldValue> values = ((ClassInstance) instance).getValues();
        for (ClassInstance.FieldValue fieldValue : values) {
            System.out.println("field name: " + fieldValue.getField().getName());
            if (fieldValue.getField().getName().equals(fieldName)) {
                return (T) fieldValue.getValue();
            }
        }
        throw new IllegalArgumentException("Field " + fieldName + " does not exists");
    }
}
