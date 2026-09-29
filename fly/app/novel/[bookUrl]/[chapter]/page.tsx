"use client";

import { useState, useEffect, useRef } from "react";
import { useParams, useSearchParams, useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { ArrowLeft, ChevronLeft, ChevronRight, Settings, Minus, Plus } from "lucide-react";
import { getBookContent, getChapterList, saveBookProgress } from "@/app/api/novel/novel-api";
import { Chapter, decodeBookUrl, loadSettings, saveSettings, ReadingSettings, defaultSettings } from "../../_lib/utils";
import { useSmoothWheel, useDragScroll } from "@/lib/gestures";
import LoadingTips from "../../_lib/LoadingTips";

export default function ReadingPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const router = useRouter();

  const bookUrl = decodeBookUrl(params.bookUrl as string);
  const chapterIndex = Number(params.chapter);
  const bookSourceUrl = searchParams.get("source") || "";

  const [chapters, setChapters] = useState<Chapter[]>([]);
  const [content, setContent] = useState("");
  const [chapterTitle, setChapterTitle] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [settings, setSettings] = useState<ReadingSettings>(defaultSettings);
  const [showSettings, setShowSettings] = useState(false);
  const settingsRef = useRef<HTMLDivElement>(null);

  const pagingEnabled = settings.clickPaging;
  useSmoothWheel(0.3);
  useDragScroll();
  const boxRef = useRef<HTMLDivElement>(null);
  const [pageIndex, setPageIndex] = useState(0);
  const [pageCount, setPageCount] = useState(1);
  const pendingEndRef = useRef(false);

  /** 一屏页高（与原分页视口一致）：连续滚动下用于派生页码/恢复进度 */
  function getPageSize() {
    if (typeof window === "undefined") return 800;
    const chrome = window.innerWidth >= 768 ? 352 : 272;
    return Math.max(320, window.innerHeight - chrome);
  }

  // 阅读进度：章节 + 页码 + 是否看完（登录时写入该用户的独立进度标签）
  const pageIndexRef = useRef(0);
  const pageCountRef = useRef(1);
  const persistRef = useRef<(ch: number, pg: number, force?: boolean) => void>(() => {});
  const lastPersistAtRef = useRef(0);
  /** 进度上报最小间隔：翻页连点不再每次都打 POST（离开/换章时强制保存兜底） */
  const PERSIST_MIN_INTERVAL_MS = 3000;

  function isScrollBottom() {
    if (typeof window === "undefined") return false;
    return window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 120;
  }

  useEffect(() => {
    pageIndexRef.current = pageIndex;
    pageCountRef.current = pageCount;
    persistRef.current = (ch: number, pg: number, force = false) => {
      if (!bookUrl || Number.isNaN(ch)) return;
      const now = Date.now();
      if (!force && now - lastPersistAtRef.current < PERSIST_MIN_INTERVAL_MS) return;
      lastPersistAtRef.current = now;
      const isLast = chapters.length > 0 && ch >= chapters.length - 1;
      const finished = isLast && isScrollBottom();
      saveBookProgress(bookUrl, ch, pg, finished).catch(() => {});
    };
  });

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setSettings(loadSettings());
  }, []);

  useEffect(() => {
    if (!showSettings) return;
    const handleClick = (e: MouseEvent) => {
      if (settingsRef.current && !settingsRef.current.contains(e.target as Node)) setShowSettings(false);
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [showSettings]);

  const updateSetting = <K extends keyof ReadingSettings>(key: K, value: ReadingSettings[K]) => {
    setSettings((prev) => {
      const next = { ...prev, [key]: value };
      saveSettings(next);
      return next;
    });
  };

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError("");
      try {
        const [chapterRes, contentRes] = await Promise.all([
          getChapterList(bookUrl, bookSourceUrl),
          getBookContent(bookUrl, chapterIndex),
        ]);
        if (chapterRes.isSuccess) {
          setChapters(chapterRes.data);
          setChapterTitle(chapterRes.data[chapterIndex]?.title || "");
        }
        if (contentRes.isSuccess) {
          setContent(contentRes.data);
          setPageIndex(0);
          pageIndexRef.current = 0;
          window.scrollTo(0, 0);
          persistRef.current(chapterIndex, 0, true);
          if (pendingEndRef.current) {
            pendingEndRef.current = false;
            setTimeout(() => window.scrollTo(0, document.documentElement.scrollHeight), 120);
          }
        } else {
          setError(contentRes.errorMsg || "获取内容失败");
        }
      } catch {
        setError("网络错误");
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [bookUrl, chapterIndex, bookSourceUrl]);

  // 进度兜底：离开页面/翻页滚动时保存当前页码（关闭/刷新也能记住位置）
  useEffect(() => {
    const onUnload = () => persistRef.current(chapterIndex, pageIndexRef.current, true);
    window.addEventListener("beforeunload", onUnload);
    return () => window.removeEventListener("beforeunload", onUnload);
  }, [chapterIndex]);

  // 连续滚动：滚动时节流保存进度 + 按视口派生页码（滚动翻页：滚过页边界页码自动进位）
  useEffect(() => {
    if (loading || error) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const onScroll = () => {
      const size = getPageSize();
      const total = Math.max(1, Math.ceil(document.documentElement.scrollHeight / size));
      const idx = Math.min(total - 1, Math.max(0, Math.floor((window.scrollY + 80) / size)));
      setPageCount(total);
      setPageIndex((prev) => {
        if (prev !== idx) pageIndexRef.current = idx;
        return idx;
      });
      clearTimeout(timer);
      timer = setTimeout(() => persistRef.current(chapterIndex, pageIndexRef.current), 1200);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
    return () => {
      window.removeEventListener("scroll", onScroll);
      clearTimeout(timer);
    };
  }, [loading, error, content, chapterIndex]);

  const goToChapter = (index: number) => {
    router.push(`/novel/${params.bookUrl}/${index}?source=${encodeURIComponent(bookSourceUrl)}`);
  };

  const goChapter = (dir: -1 | 1) => {
    const newIndex = chapterIndex + dir;
    if (newIndex >= 0 && newIndex < chapters.length) {
      // 上一章：跳到末页附近；下一章：从头开始
      pendingEndRef.current = dir === -1;
      goToChapter(newIndex);
    }
  };

  /** 滚动到指定页（点击翻页用）：连续滚动内平滑定位，无整页跳变 */
  const scrollToPage = (n: number) => {
    const size = getPageSize();
    window.scrollTo({ top: n * size, behavior: "smooth" });
    setPageIndex(n);
    pageIndexRef.current = n;
    persistRef.current(chapterIndex, n);
  };

  const flipPage = (dir: -1 | 1) => {
    if (!pagingEnabled) return;
    const n = pageIndex + dir;
    if (n < 0) {
      goChapter(-1);
      return;
    }
    if (n >= pageCount) {
      goChapter(1);
      return;
    }
    scrollToPage(n);
  };

  const handleContentClick = (e: React.MouseEvent) => {
    if (!settings.clickPaging) return;
    if (window.getSelection()?.toString()) return;
    const box = boxRef.current;
    if (!box) return;
    const rect = box.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    if (x < 0.3) flipPage(-1);
    else if (x > 0.7) flipPage(1);
  };

  const themes: Record<string, { bg: string; text: string }> = {
    default: { bg: "bg-white/60 dark:bg-slate-800/60", text: "text-slate-800 dark:text-slate-200" },
    sepia: { bg: "bg-amber-50/80 dark:bg-amber-900/30", text: "text-amber-900 dark:text-amber-100" },
    green: { bg: "bg-emerald-50/80 dark:bg-emerald-900/30", text: "text-emerald-900 dark:text-emerald-100" },
  };
  const t = themes[settings.theme] || themes.default;

  return (
    <div
      className="mx-auto px-4 sm:px-6 lg:px-8 py-6 md:py-12"
      style={{ maxWidth: settings.contentWidth === "narrow" ? "42rem" : settings.contentWidth === "wide" ? "72rem" : settings.contentWidth === "full" ? "100%" : "56rem" }}
    >
      <button type="button" onClick={() => router.push(`/novel/${params.bookUrl}?source=${encodeURIComponent(bookSourceUrl)}`)} className="flex items-center gap-2 text-slate-500 hover:text-sky-500 mb-4">
        <ArrowLeft className="w-4 h-4" /> 目录
      </button>

      {loading && <LoadingTips />}

      {error && (
        <div className="text-center py-20 text-red-500">{error}</div>
      )}

      {!loading && !error && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="relative">
          <div className="flex items-center justify-between mb-4">
            <div className="w-10" />
            <h2 className="text-lg font-semibold text-slate-900 dark:text-white text-center flex-1 px-4 truncate">{chapterTitle}</h2>
            <div className="flex gap-2">
              <button type="button" onClick={() => goChapter(-1)} disabled={chapterIndex <= 0} title="上一章"
                className="p-2 rounded-lg bg-white/60 dark:bg-slate-800/60 border border-slate-200/50 dark:border-slate-700/50 disabled:opacity-50">
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button type="button" onClick={() => goChapter(1)} disabled={chapterIndex >= chapters.length - 1} title="下一章"
                className="p-2 rounded-lg bg-white/60 dark:bg-slate-800/60 border border-slate-200/50 dark:border-slate-700/50 disabled:opacity-50">
                <ChevronRight className="w-4 h-4" />
              </button>
              <button type="button" onMouseDown={(e) => { e.stopPropagation(); setShowSettings((v) => !v); }} title="阅读设置"
                className={`p-2 rounded-lg border border-slate-200/50 dark:border-slate-700/50 transition-colors ${showSettings ? "bg-sky-500 text-white" : "bg-white/60 dark:bg-slate-800/60 text-slate-500 hover:text-sky-500"}`}>
                <Settings className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* 设置面板 */}
          {showSettings && (
            <div ref={settingsRef} className="absolute right-4 top-16 z-50 w-80 p-4 rounded-xl bg-white/95 dark:bg-slate-800/95 backdrop-blur-md border border-slate-200/50 dark:border-slate-700/50 shadow-xl space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-sm text-slate-600 dark:text-slate-400">字体大小</span>
                <div className="flex items-center gap-3">
                  <button onClick={() => updateSetting("fontSize", Math.max(12, settings.fontSize - 1))} className="p-1.5 rounded-lg bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600"><Minus className="w-3.5 h-3.5" /></button>
                  <span className="text-sm w-8 text-center">{settings.fontSize}</span>
                  <button onClick={() => updateSetting("fontSize", Math.min(32, settings.fontSize + 1))} className="p-1.5 rounded-lg bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600"><Plus className="w-3.5 h-3.5" /></button>
                </div>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-slate-600 dark:text-slate-400">行距</span>
                <div className="flex items-center gap-3">
                  <button onClick={() => updateSetting("lineHeight", Math.max(1.2, +(settings.lineHeight - 0.1).toFixed(1)))} className="p-1.5 rounded-lg bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600"><Minus className="w-3.5 h-3.5" /></button>
                  <span className="text-sm w-8 text-center">{settings.lineHeight}</span>
                  <button onClick={() => updateSetting("lineHeight", Math.min(3, +(settings.lineHeight + 0.1).toFixed(1)))} className="p-1.5 rounded-lg bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600"><Plus className="w-3.5 h-3.5" /></button>
                </div>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-slate-600 dark:text-slate-400">段距</span>
                <div className="flex items-center gap-3">
                  <button onClick={() => updateSetting("paragraphSpacing", Math.max(0, settings.paragraphSpacing - 4))} className="p-1.5 rounded-lg bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600"><Minus className="w-3.5 h-3.5" /></button>
                  <span className="text-sm w-8 text-center">{settings.paragraphSpacing}</span>
                  <button onClick={() => updateSetting("paragraphSpacing", Math.min(48, settings.paragraphSpacing + 4))} className="p-1.5 rounded-lg bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600"><Plus className="w-3.5 h-3.5" /></button>
                </div>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-slate-600 dark:text-slate-400">字体</span>
                <div className="flex gap-2">
                  {[["serif", "宋体"], ["sans-serif", "黑体"], ["system-ui", "系统"]].map(([val, label]) => (
                    <button key={val} onClick={() => updateSetting("fontFamily", val)}
                      className={`px-3 py-1 rounded-lg text-xs transition-colors ${settings.fontFamily === val ? "bg-sky-500 text-white" : "bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-600"}`}>
                      {label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-600 dark:text-slate-400">背景</span>
                  <div className="flex gap-2">
                    {[["default", "默认"], ["sepia", "护眼"], ["green", "绿意"], ["custom", "自定义"]].map(([val, label]) => (
                      <button key={val} onClick={() => updateSetting("theme", val)}
                        className={`px-3 py-1 rounded-lg text-xs transition-colors ${settings.theme === val ? "bg-sky-500 text-white" : "bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-600"}`}>
                        {label}
                      </button>
                    ))}
                  </div>
                </div>
                {settings.theme === "custom" && (
                  <div className="flex items-center justify-end gap-2">
                    <input
                      type="color"
                      title="自定义背景色"
                      value={settings.customColor}
                      onChange={(e) => updateSetting("customColor", e.target.value)}
                      className="w-8 h-8 rounded-lg border border-slate-200 dark:border-slate-600 cursor-pointer"
                    />
                    <span className="text-xs text-slate-500 dark:text-slate-400">{settings.customColor}</span>
                  </div>
                )}
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-slate-600 dark:text-slate-400">宽度</span>
                <div className="flex gap-2">
                  {[["narrow", "窄"], ["normal", "标准"], ["wide", "宽"], ["full", "全屏"]].map(([val, label]) => (
                    <button key={val} onClick={() => updateSetting("contentWidth", val)}
                      className={`px-3 py-1 rounded-lg text-xs transition-colors ${settings.contentWidth === val ? "bg-sky-500 text-white" : "bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-600"}`}>
                      {label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-600 dark:text-slate-400">翻页方式</span>
                  <div className="flex gap-2">
                    <button onClick={() => updateSetting("clickPaging", !settings.clickPaging)}
                      className={`px-3 py-1 rounded-lg text-xs transition-colors ${settings.clickPaging ? "bg-sky-500 text-white" : "bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-600"}`}>
                      点击跳页
                    </button>
                  </div>
                </div>
                <p className="text-[11px] text-slate-400 dark:text-slate-500 text-right">
                  滚轮/拖拽为连续滚动{settings.clickPaging ? "，点左侧/右侧跳页" : ""}
                </p>
              </div>
            </div>
          )}

          <div
            className={`${settings.theme === "custom" ? "" : t.bg} backdrop-blur-md rounded-2xl border border-slate-200/50 dark:border-slate-700/50 p-6 md:p-10`}
            style={settings.theme === "custom" ? { backgroundColor: settings.customColor } : undefined}
          >
            <div
              ref={boxRef}
              onClick={handleContentClick}
              className={settings.clickPaging ? "cursor-pointer" : undefined}
            >
              <div
                className={`${t.text} max-w-none whitespace-pre-wrap`}
                style={{
                  fontSize: `${settings.fontSize}px`,
                  lineHeight: settings.lineHeight,
                  fontFamily: settings.fontFamily,
                }}
              >
                {content.split("\n").map((para, i) => (
                  <p key={i} style={{ marginBottom: `${settings.paragraphSpacing}px` }}>{para.trimStart()}</p>
                ))}
              </div>
            </div>
          </div>
          <div className="flex justify-between mt-6">
            <button type="button" onClick={() => goChapter(-1)} disabled={chapterIndex <= 0}
              className="px-4 py-2 rounded-lg bg-white/60 dark:bg-slate-800/60 border border-slate-200/50 dark:border-slate-700/50 disabled:opacity-50 text-sm">上一章</button>
            <span className="text-sm text-slate-500">
              {chapterIndex + 1} / {chapters.length}
              {pageCount > 1 ? `（${pageIndex + 1}/${pageCount}页）` : ""}
            </span>
            <button type="button" onClick={() => goChapter(1)} disabled={chapterIndex >= chapters.length - 1}
              className="px-4 py-2 rounded-lg bg-white/60 dark:bg-slate-800/60 border border-slate-200/50 dark:border-slate-700/50 disabled:opacity-50 text-sm">下一章</button>
          </div>
        </motion.div>
      )}
    </div>
  );
}
