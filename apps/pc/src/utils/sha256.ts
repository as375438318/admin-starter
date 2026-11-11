/**
 * 计算文件 SHA-256（小写十六进制）。分片/秒传需要先拿到内容指纹。
 * 浏览器环境使用 Web Crypto；文件较大时读取整个文件会产生一次性内存峰值。
 */
export async function sha256Of(file: Blob): Promise<string> {
  const buffer = await file.arrayBuffer();
  const digest = await crypto.subtle.digest('SHA-256', buffer);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}
