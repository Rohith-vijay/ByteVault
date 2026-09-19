package com.bytevault.product;

import com.bytevault.common.exception.DuplicateResourceException;
import com.bytevault.common.exception.ResourceNotFoundException;
import com.bytevault.product.dto.CategoryResponse;
import com.bytevault.product.dto.CreateCategoryRequest;
import com.bytevault.product.entity.Category;
import com.bytevault.product.repository.CategoryRepository;
import com.bytevault.product.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("Create category successfully")
    void testCreateCategory() {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("E-Books")
                .description("Digital downloadable books")
                .build();

        when(categoryRepository.findByNameIgnoreCase("E-Books")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> {
            Category c = i.getArgument(0);
            c.setId(1L);
            return c;
        });

        CategoryResponse response = categoryService.createCategory(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("E-Books", response.getName());
    }

    @Test
    @DisplayName("Duplicate category name -> Throws DuplicateResourceException")
    void testCreateCategory_Duplicate_ThrowsException() {
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("E-Books")
                .build();

        when(categoryRepository.findByNameIgnoreCase("E-Books"))
                .thenReturn(Optional.of(Category.builder().id(1L).name("E-Books").build()));

        assertThrows(DuplicateResourceException.class, () -> categoryService.createCategory(request));
    }

    @Test
    @DisplayName("Get all categories")
    void testGetAllCategories() {
        Category c1 = Category.builder().id(1L).name("E-Books").build();
        Category c2 = Category.builder().id(2L).name("Software").build();

        when(categoryRepository.findAll()).thenReturn(List.of(c1, c2));

        List<CategoryResponse> categories = categoryService.getAllCategories();

        assertEquals(2, categories.size());
    }
}
