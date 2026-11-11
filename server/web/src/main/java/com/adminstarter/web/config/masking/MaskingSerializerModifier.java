package com.adminstarter.web.config.masking;

import com.adminstarter.common.masking.MaskedField;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 把带 @MaskedField 的响应字段替换为三态写出的 writer。
 * 注解可以标在私有字段上（Lombok 生成的 getter 不带注解时按字段名回查）。
 */
public class MaskingSerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        List<BeanPropertyWriter> result = new ArrayList<>(beanProperties.size());
        for (BeanPropertyWriter writer : beanProperties) {
            MaskedField maskedField = findMaskedField(writer, beanDesc.getBeanClass());
            result.add(maskedField == null ? writer : new MaskedPropertyWriter(writer, maskedField));
        }
        return result;
    }

    private MaskedField findMaskedField(BeanPropertyWriter writer, Class<?> beanClass) {
        if (!writer.getType().isTypeOrSubTypeOf(String.class)) {
            return null;
        }
        MaskedField direct = writer.getAnnotation(MaskedField.class);
        if (direct != null) {
            return direct;
        }
        Class<?> current = beanClass;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(writer.getName());
                MaskedField annotation = field.getAnnotation(MaskedField.class);
                if (annotation != null) {
                    return annotation;
                }
            } catch (NoSuchFieldException ignored) {
                // 继续向父类查找
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
