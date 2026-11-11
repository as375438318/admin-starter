import { dictApi } from '@/api/dict';

export interface StatusOption {
  label: string;
  value: boolean;
}

const FALLBACK: StatusOption[] = [
  { label: '启用', value: true },
  { label: '停用', value: false },
];

export async function loadStatusOptions(): Promise<StatusOption[]> {
  try {
    const bundles = await dictApi.bundles(['user_status']);
    const items = bundles.find((b) => b.type === 'user_status')?.items ?? [];
    const options = items.map((item) => ({ label: item.label, value: item.value === '1' }));
    return options.length ? options : FALLBACK;
  } catch {
    return FALLBACK;
  }
}
