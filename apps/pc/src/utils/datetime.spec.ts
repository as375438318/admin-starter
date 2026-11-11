import { describe, expect, it } from 'vitest';
import { formatDateTime } from './datetime';

describe('formatDateTime（列表时间列统一格式）', () => {
  it('空值统一显示占位符', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime(undefined)).toBe('—');
    expect(formatDateTime('')).toBe('—');
  });

  it('无法解析的字符串原样返回，不吞掉后端给的原始值', () => {
    expect(formatDateTime('not-a-date')).toBe('not-a-date');
  });

  it('按本地时间格式化为 YYYY-MM-DD HH:mm，个位数补零', () => {
    expect(formatDateTime('2026-01-02T03:04:00')).toBe('2026-01-02 03:04');
    expect(formatDateTime('2026-11-11T15:47:37')).toBe('2026-11-11 15:47');
  });
});
