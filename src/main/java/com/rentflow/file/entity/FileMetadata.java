package com.rentflow.file.entity;

import com.rentflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "files")
@Getter
@Setter
public class FileMetadata extends BaseEntity {

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FilePurpose purpose;

    @Column(nullable = false, length = 120)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;

    @Column(name = "external_url", columnDefinition = "TEXT")
    private String externalUrl;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(length = 128)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileVisibility visibility = FileVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileStatus status = FileStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
