package com.ekszz.ezzpatcher;

import java.util.List;

public class ClassMethodDefine {
    private String mode;
    private String method;
    private List<String> paramType;
    private Integer insertAt;
    private String code;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<String> getParamType() {
        return paramType;
    }

    public void setParamType(List<String> paramType) {
        this.paramType = paramType;
    }

    public Integer getInsertAt() {
        return insertAt;
    }

    public void setInsertAt(Integer insertAt) {
        this.insertAt = insertAt;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return "ClassMethodDefine{" +
                "mode='" + mode + '\'' +
                ", method='" + method + '\'' +
                ", paramType=" + paramType +
                ", code='" + code + '\'' +
                '}';
    }
}
