import { api } from './http';
import type { MenuItem } from './types';

export interface MenuSavePayload {
  parentId: number;
  name: string;
  menuType: 'M' | 'C' | 'F';
  path?: string;
  component?: string;
  permissionCode?: string;
  icon?: string;
  sort?: number;
  isVisible?: boolean;
  isEnabled?: boolean;
}

export const menuApi = {
  tree() {
    return api.get<MenuItem[]>('/menus');
  },
  create(payload: MenuSavePayload) {
    return api.post<MenuItem>('/menus', payload);
  },
  update(id: number, payload: MenuSavePayload) {
    return api.put<MenuItem>(`/menus/${id}`, payload);
  },
  remove(id: number) {
    return api.delete<void>(`/menus/${id}`);
  },
};
