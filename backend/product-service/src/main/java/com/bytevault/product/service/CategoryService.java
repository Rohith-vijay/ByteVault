package com.bytevault.product.service;

import com.bytevault.common.exception.DuplicateResourceException;
import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.product.dto.CategoryResponse;
import com.bytevault.product.dto.CreateCategoryRequest;
import com.bytevault.product.entity.Category;
import com.bytevault.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));
        return mapToResponse(category);
    }

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.findByNameIgnoreCase(request.getName()).isPresent()) {
            throw new DuplicateResourceException("Category with name '" + request.getName() + "' already exists");
        }

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        Category saved = categoryRepository.save(category);
        log.info("[CategoryService] Created category: id={}, name={}", saved.getId(), saved.getName());
        return mapToResponse(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CreateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with ID: " + id));

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        Category updated = categoryRepository.save(category);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found with ID: " + id);
        }
        categoryRepository.deleteById(id);
        log.info("[CategoryService] Deleted category: id={}", id);
    }

    private CategoryResponse mapToResponse(Category c) {
        return CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .build();
    }
}
