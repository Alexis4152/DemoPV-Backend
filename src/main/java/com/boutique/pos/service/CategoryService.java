package com.boutique.pos.service;

import com.boutique.pos.dto.CategoryRequest;
import com.boutique.pos.model.Category;
import com.boutique.pos.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<Category> findAll() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada: " + id));
    }

    public Category create(CategoryRequest req) {
        Category cat = new Category();
        cat.setName(req.getName());
        cat.setDescription(req.getDescription());
        return categoryRepository.save(cat);
    }

    public Category update(Long id, CategoryRequest req) {
        Category cat = findById(id);
        cat.setName(req.getName());
        cat.setDescription(req.getDescription());
        return categoryRepository.save(cat);
    }

    public void delete(Long id) {
        Category cat = findById(id);
        categoryRepository.delete(cat);
    }
}
