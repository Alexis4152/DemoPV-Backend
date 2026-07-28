package com.boutique.pos.dto;

import com.boutique.pos.model.AppSection;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class RoleRequest {
    @NotBlank
    private String name;
    private String description;
    private Set<AppSection> sections;
}
