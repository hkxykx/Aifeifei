"use client";

import { useEffect } from "react";
import Link from "next/link";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // 真实错误信息上报到控制台，便于排查（服务端 500 详情同时记录在后端异常日志）
    console.error("[app-error]", error);
  }, [error]);

  return (
    <div className="mx-auto px-4 py-24 md:py-32 text-center" style={{ maxWidth: "42rem" }}>
      <p className="text-6xl md:text-7xl font-bold text-red-500/80">500</p>
      <h1 className="mt-4 text-xl md:text-2xl font-semibold text-slate-800 dark:text-slate-100">
        页面出错了
      </h1>
      <p className="mt-3 text-sm text-slate-500 dark:text-slate-400 break-all">
        {error.message || "发生了未知错误"}
        {error.digest ? `（引用 ${error.digest}）` : ""}
      </p>
      <div className="mt-8 flex items-center justify-center gap-3">
        <button
          type="button"
          onClick={reset}
          className="px-5 py-2.5 rounded-xl bg-sky-500 text-white text-sm font-medium hover:bg-sky-600 transition-colors"
        >
          重试
        </button>
        <Link
          href="/"
          className="px-5 py-2.5 rounded-xl bg-white/70 dark:bg-slate-800/70 border border-slate-200/70 dark:border-slate-700/70 text-slate-600 dark:text-slate-300 text-sm font-medium hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
        >
          回到首页
        </Link>
      </div>
    </div>
  );
}
