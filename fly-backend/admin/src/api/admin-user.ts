import { http } from "@/utils/http";

export type AdminUserItem = {
  id: number;
  username: string;
};

type Result<T> = {
  code: number;
  message: string;
  data: T;
};

/** 管理员账户列表（仅 id+用户名） */
export const listAdminUsers = () => {
  return http.request<Result<AdminUserItem[]>>("get", "/api/admin/users");
};

/** 新增管理员（最多 3 个，后端校验） */
export const createAdminUser = (data: {
  username: string;
  password: string;
}) => {
  return http.request<Result<AdminUserItem>>("post", "/api/admin/users", {
    data
  });
};

/** 修改管理员：username 必填语义由前端保证；password 留空表示不修改 */
export const updateAdminUser = (
  id: number,
  data: { username?: string; password?: string }
) => {
  return http.request<Result<AdminUserItem>>("put", `/api/admin/users/${id}`, {
    data
  });
};

/** 删除管理员（至少保留 1 个、不能删除自己，后端校验） */
export const deleteAdminUser = (id: number) => {
  return http.request<Result<null>>("delete", `/api/admin/users/${id}`);
};

/** 从 http 工具的错误对象里取出后端 {"detail": "..."} 提示 */
export function errMsg(e: unknown, fallback: string): string {
  const err = e as {
    response?: { data?: { detail?: string } };
    detail?: string;
  };
  return err?.response?.data?.detail || err?.detail || fallback;
}
