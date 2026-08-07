package com.boutique.pos.controller;

import com.boutique.pos.dto.ApiResponse;
import com.boutique.pos.dto.UserRequest;
import com.boutique.pos.model.User;
import com.boutique.pos.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("@sectionAccess.check('USERS')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<User>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Boolean isActive,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findAll(from, to, name, email, roleId, isActive, actor), null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> get(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findById(id, actor), null));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<User>> create(@Valid @RequestBody UserRequest req, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(userService.create(req, actor), "Usuario creado"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<User>> update(@PathVariable Long id,
                                                     @Valid @RequestBody UserRequest req,
                                                     @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(userService.update(id, req, actor), "Usuario actualizado"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id, @AuthenticationPrincipal User actor) {
        userService.deactivate(id, actor);
        return ResponseEntity.ok(ApiResponse.ok(null, "Usuario desactivado"));
    }
}
