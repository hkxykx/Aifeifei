"use client";

import { useEffect, useState } from "react";
import { siteConfig } from "@/siteConfig";

// 构建时间戳（next.config.ts 构建时烘焙），未注入时兜底为 0，在 effect 内取当前时间
const BUILD_TS = Number(process.env.NEXT_PUBLIC_BUILD_TS) || 0;

export default function SiteDashboard() {
  const [timeStr, setTimeStr] = useState("");
  const [dateStr, setDateStr] = useState("");
  const [uptimeStr, setUptimeStr] = useState("");

  useEffect(() => {
    const startTs = BUILD_TS || Date.now();
    const updateTime = () => {
      const now = new Date();
      setTimeStr(
        now.toLocaleTimeString("zh-CN", {
          hour12: false,
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
        })
      );
      const weekdays = ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"];
      setDateStr(
        now.getFullYear() + "年" + (now.getMonth() + 1) + "月" + now.getDate() + "日 " + weekdays[now.getDay()]
      );

      const diff = Math.max(0, now.getTime() - startTs);
      const days = Math.floor(diff / (1000 * 60 * 60 * 24));
      const hours = Math.floor((diff / (1000 * 60 * 60)) % 24);
      const minutes = Math.floor((diff / (1000 * 60)) % 60);
      const seconds = Math.floor((diff / 1000) % 60);
      setUptimeStr(`${days}天 ${hours}时${minutes}分${seconds}秒`);
    };

    updateTime();
    const timer = setInterval(updateTime, 1000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="md:col-span-12 rounded-3xl bg-white/40 dark:bg-slate-800/50 backdrop-blur-md border border-white/40 dark:border-white/10 shadow-xl overflow-hidden flex flex-col md:flex-row items-stretch transition-colors duration-700 h-auto md:h-20 group">
      <div className="bg-slate-900 dark:bg-black text-white px-8 py-4 md:py-0 flex items-center justify-center font-mono text-2xl md:text-3xl font-black tracking-widest shadow-inner relative overflow-hidden group-hover:text-indigo-400 transition-colors">
        <div className="absolute inset-0 bg-gradient-to-b from-white/10 to-transparent pointer-events-none" />
        <div className="flex flex-col items-center leading-none">
          <span>{timeStr || "00:00:00"}</span>
          <span className="mt-1 text-[11px] md:text-sm font-bold tracking-widest text-indigo-200">
            {dateStr}
          </span>
        </div>
        <div className="absolute left-0 right-0 top-1/2 h-px bg-black/50" />
      </div>

      <div className="flex-1 px-6 py-4 md:py-0 flex flex-wrap items-center justify-between gap-4 text-xs md:text-sm font-bold text-slate-600 dark:text-slate-300">
        <div className="flex items-center gap-2 w-full md:w-auto justify-center md:justify-start">
          <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
          <span>
            系统已稳定运行：
            <span className="text-indigo-600 dark:text-indigo-400 font-black">
              {uptimeStr}
            </span>
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-3 w-full md:w-auto justify-center md:justify-end">
          {siteConfig.icpConfig?.name && (
            <a
              href={siteConfig.icpConfig.link}
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-indigo-500 transition-colors border-b border-dashed border-slate-400 dark:border-slate-500 pb-0.5"
            >
              {siteConfig.icpConfig.name}
            </a>
          )}
        </div>
      </div>
    </div>
  );
}
