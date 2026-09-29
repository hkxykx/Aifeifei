"use client";

import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto px-4 py-24 md:py-32 text-center" style={{ maxWidth: "42rem" }}>
      <p className="text-6xl md:text-7xl font-bold text-sky-500/80">404</p>
      <h1 className="mt-4 text-xl md:text-2xl font-semibold text-slate-800 dark:text-slate-100">
        页面不存在
      </h1>
      <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
        你要找的页面可能已被移动或删除，换个地方看看吧。
      </p>
      <div className="mt-8 flex items-center justify-center gap-3">
        <Link
          href="/"
          className="px-5 py-2.5 rounded-xl bg-sky-500 text-white text-sm font-medium hover:bg-sky-600 transition-colors"
        >
          回到首页
        </Link>
        <Link
          href="/timeline"
          className="px-5 py-2.5 rounded-xl bg-white/70 dark:bg-slate-800/70 border border-slate-200/70 dark:border-slate-700/70 text-slate-600 dark:text-slate-300 text-sm font-medium hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
        >
          时间线
        </Link>
      </div>
    </div>
  );
}
