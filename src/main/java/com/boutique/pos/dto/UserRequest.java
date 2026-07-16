package com.boutique.pos.dto;

import com.boutique.pos.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserRequest {
    @NotBlank
    private String name;
    @Email @NotBlank
    private String email;
    private String password;
    @NotNull
    private Role role;
    private Boolean isActive;
}
