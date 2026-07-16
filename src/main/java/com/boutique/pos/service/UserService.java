package com.boutique.pos.service;

import com.boutique.pos.dto.UserRequest;
import com.boutique.pos.model.Role;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> findAll() {
        return userRepository.findAllByOrderByNameAsc();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
    }

    public User create(UserRequest req) {
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new IllegalArgumentException("El correo ya está registrado");
        }
        User u = new User();
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setRole(req.getRole());
        u.setIsActive(true);
        return userRepository.save(u);
    }

    public User update(Long id, UserRequest req) {
        User u = findById(id);
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            u.setPassword(passwordEncoder.encode(req.getPassword()));
        }
        if (req.getRole() != null) {
            u.setRole(req.getRole());
        }
        return userRepository.save(u);
    }

    public void deactivate(Long id) {
        User u = findById(id);
        u.setIsActive(false);
        userRepository.save(u);
    }
}
