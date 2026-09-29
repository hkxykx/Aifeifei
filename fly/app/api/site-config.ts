import { request } from "./client";
import { adminFetch } from "./adminAuth";

export interface SiteConfigItem {
  id: number;
  key: string;
  value: string;
  description: string;
}

export function getSiteConfig() {
  return request<Record<string, string>>("/api/site-config");
}

export function getSiteConfigByKey(key: string) {
  return request<SiteConfigItem>(`/api/site-config/${key}`);
}

// ---- 后台 ----

/** 管理端完整列表（value 为原始 JSON 字符串） */
export function adminListSiteConfig() {
  return adminFetch("/api/site-config/list").then(async (res) => {
    if (!res.ok) throw new Error(`API Error: ${res.status}`);
    return res.json() as Promise<SiteConfigItem[]>;
  });
}

/** 批量更新：body 形如 { site_title: "新标题", ... }（服务端序列化存储） */
export function adminUpdateSiteConfig(configs: Record<string, unknown>) {
  return adminFetch("/api/site-config", {
    method: "PUT",
    body: JSON.stringify(configs),
  }).then(async (res) => {
    if (!res.ok) throw new Error(`API Error: ${res.status}`);
    return res.json() as Promise<Record<string, string>>;
  });
}
