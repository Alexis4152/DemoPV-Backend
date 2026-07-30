package com.boutique.pos.service;

import com.boutique.pos.dto.CategoryRequest;
import com.boutique.pos.model.Category;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.CategoryRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TenantScope tenantScope;

    public List<Category> findAll(User actor) {
        return categoryRepository.findAllForTienda(tenantScope.scopeId(actor));
    }

    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada: " + id));
    }

    public Category findById(Long id, User actor) {
        Category c = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (c.getTienda() == null || !scope.equals(c.getTienda().getId()))) {
            throw new IllegalArgumentException("Categoría no encontrada: " + id);
        }
        return c;
    }

    public Category create(CategoryRequest req, User actor) {
        Category cat = new Category();
        cat.setName(req.getName());
        cat.setDescription(req.getDescription());
        cat.setTienda(actor.getTienda());
        return categoryRepository.save(cat);
    }

    public Category update(Long id, CategoryRequest req, User actor) {
        Category cat = findById(id, actor);
        cat.setName(req.getName());
        cat.setDescription(req.getDescription());
        return categoryRepository.save(cat);
    }

    public void delete(Long id, User actor) {
        Category cat = findById(id, actor);
        categoryRepository.delete(cat);
    }
}
