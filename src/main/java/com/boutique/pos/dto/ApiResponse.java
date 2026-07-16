package com.boutique.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data)                  { return new ApiResponse<>(true,  null, data); }
    public static <T> ApiResponse<T> ok(T data, String msg)      { return new ApiResponse<>(true,  msg,  data); }
    public static <T> ApiResponse<T> error(String msg)           { return new ApiResponse<>(false, msg,  null); }
}
