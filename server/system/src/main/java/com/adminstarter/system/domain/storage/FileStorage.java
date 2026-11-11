package com.adminstarter.system.domain.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

/**
 * 文件存储端口。逻辑键（key）由应用层生成，物理位置对调用方不可见。
 * <p>本轮只提供本地磁盘实现 {@code LocalFileStorage}；对象存储需新增实现类并注册为 Bean，
 * 不得在未实现前对外声称已支持。</p>
 */
public interface FileStorage {

    /** 将本地暂存文件写入指定逻辑键；同一键已存在时覆盖。 */
    void store(Path source, String key);

    /** 按逻辑键读取为可下载资源。 */
    Resource loadAsResource(String key);

    boolean exists(String key);

    void delete(String key);

    /** 删除子树（分片临时目录）。 */
    void deleteRecursively(String key);

    /** 清理超过指定时长的临时文件与临时目录，返回删除的文件数。 */
    long cleanupTemp(Duration age);

    /** 清理未被引用且超过指定时长的物理文件，返回删除的文件数。 */
    long cleanupOrphans(Set<String> referencedKeys, Duration age);
}
