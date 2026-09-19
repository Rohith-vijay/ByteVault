package com.bytevault.warehouse.repository;

import com.bytevault.warehouse.entity.PickListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PickListItemRepository extends JpaRepository<PickListItem, UUID> {
    List<PickListItem> findByPickListId(UUID pickListId);
}
