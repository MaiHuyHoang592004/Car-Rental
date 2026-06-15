package com.rentflow.file.repository;

import com.rentflow.file.entity.FileMetadata;
import com.rentflow.file.entity.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {

    Optional<FileMetadata> findByIdAndStatus(UUID id, FileStatus status);

    Optional<FileMetadata> findByBucketAndObjectKey(String bucket, String objectKey);
}
