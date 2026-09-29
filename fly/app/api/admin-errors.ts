import { adminFetch } from "./adminAuth";

// ---- 监控方案 A：后端异常日志 ----

export interface ErrorLogItem {
  id: number;
  path: string;
  method: string;
  status: number;
  message: string;
  stack: string;
  created_at: string;
}

export function adminGetErrors(limit = 100) {
  return adminFetch(`/api/admin/errors?limit=${limit}`).then(async (res) => {
    if (!res.ok) throw new Error(`API Error: ${res.status}`);
    const json = await res.json();
    if (!json?.isSuccess) throw new Error(json?.errorMsg || "获取异常日志失败");
    return json.data as ErrorLogItem[];
  });
}

export function adminClearErrors() {
  return adminFetch("/api/admin/errors", { method: "DELETE" }).then(async (res) => {
    if (!res.ok) throw new Error(`API Error: ${res.status}`);
    return true;
  });
}

export function adminDeleteError(id: number) {
  return adminFetch(`/api/admin/errors/${id}`, { method: "DELETE" }).then(async (res) => {
    if (!res.ok) throw new Error(`API Error: ${res.status}`);
    return true;
  });
}
