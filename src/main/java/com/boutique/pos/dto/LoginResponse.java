package com.boutique.pos.dto;

import com.boutique.pos.model.Tienda;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long   userId;
    private String name;
    private String email;
    private String role;
    private List<String> sections;
    private Tienda tienda; // null si es SUPER_ADMIN — el frontend usa tienda.primaryColor para el tema
}
