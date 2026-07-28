package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.RoleRequest;
import com.boutique.pos.model.Role;
import com.boutique.pos.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.check('ROLES')")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Role>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.findAll(), null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Role>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.findById(id), null));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Role>> create(@Valid @RequestBody RoleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.create(req), "Rol creado"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Role>> update(@PathVariable Long id, @Valid @RequestBody RoleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.update(id, req), "Rol actualizado"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Rol eliminado"));
    }
}
