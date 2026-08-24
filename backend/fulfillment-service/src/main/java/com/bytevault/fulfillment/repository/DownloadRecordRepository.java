package com.bytevault.fulfillment.repository;

import com.bytevault.fulfillment.entity.DownloadRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DownloadRecordRepository extends JpaRepository<DownloadRecord, UUID> {
}
