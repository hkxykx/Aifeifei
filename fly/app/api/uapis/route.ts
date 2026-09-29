import { NextRequest, NextResponse } from "next/server";

const BASE = "https://uapis.cn/api/v1";

/**
 * 子路径白名单（防开放代理刷 uapis.cn 配额）：
 * 仅允许工具箱实际用到的端点，精确匹配。
 */
const ALLOWED_PATHS = new Set([
  "misc/worldtime",
  "misc/phoneinfo",
  "misc/movie-box-office",
  "misc/tracking/query"
]);

/** 内存缓存：path+query → { data, expires }，防高频重复外呼 */
const CACHE_TTL: Record<string, number> = {
  "misc/movie-box-office": 6 * 3600 * 1000, // 票房日更
  "misc/worldtime": 5 * 60 * 1000,
  "misc/phoneinfo": 60 * 1000,
  "misc/tracking/query": 60 * 1000
};
const cache = new Map<string, { data: unknown; expires: number }>();
const MAX_CACHE = 200;

function pruneCache() {
  if (cache.size <= MAX_CACHE) return;
  const now = Date.now();
  for (const [k, v] of cache) {
    if (v.expires <= now) cache.delete(k);
  }
  while (cache.size > MAX_CACHE) {
    const first = cache.keys().next().value;
    if (first === undefined) break;
    cache.delete(first);
  }
}

export async function GET(req: NextRequest) {
  const subPath = req.nextUrl.searchParams.get("path");
  if (!subPath || !ALLOWED_PATHS.has(subPath)) {
    return NextResponse.json({ message: "Forbidden path." }, { status: 403 });
  }
  const params = new URLSearchParams(req.nextUrl.searchParams);
  params.delete("path");
  const qs = params.toString();
  const url = `${BASE}/${subPath}${qs ? `?${qs}` : ""}`;
  const cacheKey = `${subPath}?${qs}`;

  const hit = cache.get(cacheKey);
  if (hit && hit.expires > Date.now()) {
    return NextResponse.json(hit.data);
  }

  try {
    const res = await fetch(url);
    const data = await res.json();
    if (res.ok) {
      cache.set(cacheKey, { data, expires: Date.now() + (CACHE_TTL[subPath] ?? 60_000) });
      pruneCache();
    }
    return NextResponse.json(data, { status: res.status });
  } catch {
    return NextResponse.json({ message: "请求外部API失败" }, { status: 502 });
  }
}
