import { describe, expect, it } from 'vitest';
import { sha256Of } from './sha256';

describe('sha256Of（分片/秒传的内容指纹）', () => {
  it('对已知输入产出标准小写十六进制摘要', async () => {
    // "abc" 的 SHA-256 是公开标准值，用来确认算法与编码没有悄悄变
    const digest = await sha256Of(new Blob(['abc']));
    expect(digest).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  });

  it('空内容也有固定摘要（不能返回空串，否则秒传会互相命中）', async () => {
    const digest = await sha256Of(new Blob([]));
    expect(digest).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
  });

  it('内容不同则摘要不同', async () => {
    const a = await sha256Of(new Blob(['a']));
    const b = await sha256Of(new Blob(['b']));
    expect(a).not.toBe(b);
  });
});
