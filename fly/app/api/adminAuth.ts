import { BASE_URL } from "./client";

const ADMIN_TOKEN_KEY = "admin_token";

export function getAdminToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(ADMIN_TOKEN_KEY);
}

export function setAdminToken(token: string) {
  localStorage.setItem(ADMIN_TOKEN_KEY, token);
}

export function clearAdminToken() {
  localStorage.removeItem(ADMIN_TOKEN_KEY);
}

export function adminHeaders(): Record<string, string> {
  const token = getAdminToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** 后台统一请求：自动携带管理员令牌；401/403 时清除令牌 */
export async function adminFetch(path: string, init?: RequestInit): Promise<Response> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...adminHeaders(),
      ...(init?.headers || {}),
    },
  });
  if (res.status === 401 || res.status === 403) {
    clearAdminToken();
  }
  return res;
}

/** 管理员登录（POST /api/auth/login） */
export async function adminLogin(username: string, password: string): Promise<string> {
  const res = await fetch(`${BASE_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) throw new Error("用户名或密码错误");
  const body = await res.json();
  if (body.code !== 0 || !body.data?.accessToken) {
    throw new Error(body.message || "用户名或密码错误");
  }
  setAdminToken(body.data.accessToken);
  return body.data.accessToken;
}
