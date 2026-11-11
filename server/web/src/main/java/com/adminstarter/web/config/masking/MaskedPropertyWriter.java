package com.adminstarter.web.config.masking;

import com.adminstarter.common.masking.MaskResolver;
import com.adminstarter.common.masking.MaskedField;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

/**
 * 响应字段写出前的三态替换：明文原样、掩码写掩码串、隐藏写 null。
 * 只在专用 Web ObjectMapper 中生效，不污染缓存与审计使用的全局 ObjectMapper。
 */
public class MaskedPropertyWriter extends BeanPropertyWriter {

    private final MaskedField maskedField;

    public MaskedPropertyWriter(BeanPropertyWriter base, MaskedField maskedField) {
        super(base);
        this.maskedField = maskedField;
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
        Object raw = get(bean);
        String text = raw == null ? null : raw.toString();
        MaskResolver.Resolution resolution = MaskResolver.resolve(maskedField.value(), maskedField.format(), text);
        if (resolution.state() == null) {
            super.serializeAsField(bean, gen, prov);
            return;
        }
        gen.writeFieldName(getName());
        if (resolution.value() == null) {
            gen.writeNull();
        } else {
            gen.writeString(resolution.value());
        }
    }
}
