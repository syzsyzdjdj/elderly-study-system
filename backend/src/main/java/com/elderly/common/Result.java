package com.elderly.common;

import lombok.Data;

/**
 * 统一返回结果
 * code=200 成功；code=500 业务/系统异常（HTTP 状态仍为 200，前端以 body.code 判断）；
 * 401/403 由安全处理器返回（HTTP 状态 401/403 + 相同结构的 body）
 */
@Data
public class Result<T> {

    /** 状态码：200 成功，500 失败 */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 数据 */
    private T data;

    /** 成功（无数据） */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 成功（带数据） */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    /** 失败（指定状态码与信息） */
    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }

    /** 失败（默认 500） */
    public static <T> Result<T> error(String msg) {
        return error(500, msg);
    }
}
