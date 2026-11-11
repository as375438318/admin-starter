package com.adminstarter.system.infrastructure.storage;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.system.domain.storage.FileStorage;
import com.adminstarter.system.domain.storage.FileStorageKeys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 本地磁盘实现：根目录由 {@code app.file.upload-dir} 指定，正式文件按日期分片。
 * 历史文件使用平铺 UUID 键，读取兼容，不做迁移。
 */
@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    @Value("${app.file.upload-dir:./data/uploads}")
    private String uploadDir;

    private Path root;

    @PostConstruct
    public void init() {
        root = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + root, e);
        }
    }

    @Override
    public void store(Path source, String key) {
        Path target = requireInside(key);
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件写入失败");
        }
    }

    @Override
    public Resource loadAsResource(String key) {
        Path path = requireInside(key);
        if (!Files.isRegularFile(path)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
        return new FileSystemResource(path);
    }

    @Override
    public boolean exists(String key) {
        try {
            return Files.isRegularFile(requireInside(key));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(requireInside(key));
        } catch (IOException | RuntimeException e) {
            log.warn("磁盘文件删除失败: {}", key, e);
        }
    }

    @Override
    public void deleteRecursively(String key) {
        Path path = requireInside(key);
        if (!Files.exists(path)) {
            return;
        }
        deleteTree(path);
    }

    @Override
    public long cleanupTemp(Duration age) {
        Instant cutoff = Instant.now().minus(age);
        long deleted = 0;
        for (String sub : List.of(FileStorageKeys.TEMP_ROOT, FileStorageKeys.CHUNK_ROOT)) {
            Path dir = root.resolve(sub);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> children = Files.list(dir)) {
                for (Path child : children.toList()) {
                    if (olderThan(child, cutoff)) {
                        deleted += Files.isDirectory(child) ? deleteTree(child) : deleteFile(child);
                    }
                }
            } catch (IOException e) {
                log.warn("临时目录清理失败: {}", dir, e);
            }
        }
        return deleted;
    }

    @Override
    public long cleanupOrphans(Set<String> referencedKeys, Duration age) {
        Instant cutoff = Instant.now().minus(age);
        Set<String> referenced = referencedKeys == null ? Set.of() : new HashSet<>(referencedKeys);
        long deleted = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                if (!Files.isRegularFile(path) || !olderThan(path, cutoff)) {
                    continue;
                }
                String key = relativeKey(path);
                if (key.startsWith(FileStorageKeys.CHUNK_ROOT + "/")
                        || key.startsWith(FileStorageKeys.TEMP_ROOT + "/")
                        || referenced.contains(key)) {
                    continue;
                }
                deleted += deleteFile(path);
            }
        } catch (IOException e) {
            log.warn("孤儿文件扫描失败: {}", root, e);
        }
        return deleted;
    }

    private String relativeKey(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private long deleteTree(Path path) {
        long deleted = 0;
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isDirectory(item)) {
                    Files.deleteIfExists(item);
                } else {
                    deleted += deleteFile(item);
                }
            }
        } catch (IOException e) {
            log.warn("目录清理失败: {}", path, e);
        }
        return deleted;
    }

    private long deleteFile(Path path) {
        try {
            return Files.deleteIfExists(path) ? 1 : 0;
        } catch (IOException e) {
            log.warn("文件清理失败: {}", path, e);
            return 0;
        }
    }

    private boolean olderThan(Path path, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
        } catch (IOException e) {
            return false;
        }
    }

    private Path requireInside(String key) {
        if (key == null || key.isBlank() || key.contains("..")) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "非法文件路径");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "非法文件路径");
        }
        return path;
    }
}
