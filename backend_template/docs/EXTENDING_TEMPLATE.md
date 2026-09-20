# Extending the Template

This guide provides a step-by-step walkthrough on how to add new business modules (e.g. Products, Orders, Bookings) on top of this backend template.

---

## Guide: Adding a new module

### Step 1: Create the Entity
Create your JPA entity extending `BaseAuditableEntity` (from `com.example.platform.common`) to automatically inherit audit timestamps. 

Apply `SensitiveDataEncryptionConverter` to any field containing PII or sensitive values.

*Example (`Product.java`):*
```java
package com.example.platform.product;

import com.example.platform.common.BaseAuditableEntity;
import com.example.platform.common.crypto.SensitiveDataEncryptionConverter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Convert(converter = SensitiveDataEncryptionConverter.class)
    @Column(name = "secret_serial_number")
    private String secretSerialNumber; // Transparently encrypted in DB
}
```

---

### Step 2: Create the Repository
Create a standard repository extending `JpaRepository`.

*Example (`ProductRepository.java`):*
```java
package com.example.platform.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
}
```

---

### Step 3: Create the Service and Apply Auditing
Annotate service methods with `@AuditAction` to capture administrative or mutation logs.

*Example (`ProductService.java`):*
```java
package com.example.platform.product;

import com.example.platform.audit.AuditAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    @AuditAction("CREATE_PRODUCT") // Automatically logs action name, actor, and arguments
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }
}
```

---

### Step 4: Create the Controller & Apply PreAuthorize Checks
Secure endpoints using Spring Security `@PreAuthorize` annotations referencing Roles or Permissions.

*Example (`ProductController.java`):*
```java
package com.example.platform.product;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')") // Restrict to admin users
    public Product addProduct(@RequestBody Product product) {
        return productService.createProduct(product);
    }
}
```

---

### Step 5: Add Flyway Migrations
Create a migration script under `src/main/resources/db/migration/` naming it sequentially (e.g. `V2__create_products_table.sql`).

```sql
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    secret_serial_number VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);
```

---

### Step 6: Define Custom Roles/Permissions (Optional)
To expand user groups, edit the enums:
1. `Permission.java` (e.g., add `MANAGE_PRODUCTS`).
2. `Role.java` (e.g., add `MANAGER(Set.of(Permission.READ_CONTENT, Permission.MANAGE_PRODUCTS))`).
Then apply the security configuration or annotations.
