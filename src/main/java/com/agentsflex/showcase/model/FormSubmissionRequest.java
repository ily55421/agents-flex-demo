package com.agentsflex.showcase.model;

import javax.validation.constraints.NotEmpty;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 承载根据 Runtime 下发 JSON Schema 渲染并提交的动态表单值。
 */
public class FormSubmissionRequest {

    @NotEmpty
    private Map<String, Object> values = new LinkedHashMap<>();

    /**
     * @return 字段名到用户输入值的映射，必须至少包含一个字段
     */
    public Map<String, Object> getValues() {
        return values;
    }

    /**
     * @param values 动态表单提交值；具体字段由当前 Suspension Schema 决定
     */
    public void setValues(Map<String, Object> values) {
        this.values = values;
    }
}
