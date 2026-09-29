"use client";

import { useState, useEffect } from "react";
import { useParams, useSearchParams, useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { ArrowLeft, BookOpen } from "lucide-react";
import { getChapterList, getBookshelf } from "@/app/api/novel/novel-api";
import { Chapter, decodeBookUrl, Book } from "../_lib/utils";
import { useSmoothWheel, useDragScroll } from "@/lib/gestures";
import LoadingTips from "../_lib/LoadingTips";

export default function ChapterListPage() {
  useSmoothWheel();
  useDragScroll();
  const params = useParams();
  const searchParams = useSearchParams();
  const router = useRouter();

  const bookUrl = decodeBookUrl(params.bookUrl as string);
  const bookSourceUrl = searchParams.get("source") || "";
  const bookName = searchParams.get("name") || "";
  const from = searchParams.get("from") || "bookshelf";

  const [chapters, setChapters] = useState<Chapter[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [resume, setResume] = useState<Book | null>(null);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError("");
      try {
        const res = await getChapterList(bookUrl, bookSourceUrl);
        if (res.isSuccess) {
          setChapters(res.data);
        } else {
          setError(res.errorMsg || "获取目录失败");
        }
      } catch {
        setError("网络错误");
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [bookUrl, bookSourceUrl]);

  // 书架里这本书的进度标签（全局记录，无需登录）
  useEffect(() => {
    let alive = true;
    getBookshelf()
      .then((res) => {
        if (!alive || !res.isSuccess || !Array.isArray(res.data)) return;
        setResume(res.data.find((b: Book) => b.bookUrl === bookUrl) || null);
      })
      .catch(() => {});
    return () => { alive = false; };
  }, [bookUrl]);

  const goToChapter = (index: number) => {
    router.push(`/novel/${params.bookUrl}/${index}?source=${encodeURIComponent(bookSourceUrl)}`);
  };

  return (
    <div className="mx-auto px-4 sm:px-6 lg:px-8 py-6 md:py-12" style={{ maxWidth: "56rem" }}>
      <button
        type="button"
        onClick={() => from === "search" ? router.push("/novel/search?q=" + encodeURIComponent(searchParams.get("q") || "")) : router.push("/novel")}
        className="flex items-center gap-2 text-slate-500 hover:text-sky-500 mb-6"
      >
        <ArrowLeft className="w-4 h-4" />
        {from === "search" ? "搜索结果" : "书架"}
      </button>

      {loading && <LoadingTips />}

      {error && (
        <div className="text-center py-20 text-red-500">{error}</div>
      )}

      {!loading && !error && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-2">
            {bookName || "目录"}
          </h2>
          <div className="flex items-center justify-between gap-3 mb-6">
            <p className="text-sm text-slate-500">共 {chapters.length} 章</p>
          </div>
          {resume && !resume.finished && typeof resume.durChapterIndex === "number" && resume.durChapterTitle && (
            <button
              type="button"
              onClick={() => goToChapter(resume.durChapterIndex as number)}
              className="w-full mb-4 flex items-center gap-3 px-4 py-3 rounded-xl bg-sky-50/80 dark:bg-sky-900/30 border border-sky-200/70 dark:border-sky-800/60 text-left hover:bg-sky-100 dark:hover:bg-sky-900/50 transition-colors"
            >
              <BookOpen className="w-5 h-5 text-sky-500 flex-shrink-0" />
              <span className="min-w-0">
                <span className="block text-sm font-semibold text-sky-600 dark:text-sky-300">继续上次阅读</span>
                <span className="block text-xs text-slate-500 dark:text-slate-400 truncate">
                  {resume.durChapterTitle}
                  {resume.pageIndex ? `（第 ${resume.pageIndex + 1} 页）` : ""}
                </span>
              </span>
            </button>
          )}
          {resume && resume.finished && (
            <div className="mb-4 flex items-center gap-3 px-4 py-3 rounded-xl bg-emerald-50/80 dark:bg-emerald-900/30 border border-emerald-200/70 dark:border-emerald-800/60">
              <BookOpen className="w-5 h-5 text-emerald-500 flex-shrink-0" />
              <span className="text-sm text-emerald-600 dark:text-emerald-300">
                已看完{resume.finishedChapterTitle ? `：${resume.finishedChapterTitle}` : ""}
              </span>
            </div>
          )}
          <div className="bg-white/60 dark:bg-slate-800/60 backdrop-blur-md rounded-2xl border border-slate-200/50 dark:border-slate-700/50 p-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2 max-h-[60vh] overflow-y-auto">
              {chapters.map((chapter) => (
                <button
                  type="button"
                  key={chapter.index}
                  onClick={() => goToChapter(chapter.index)}
                  className="text-left px-3 py-2 rounded-lg text-sm text-slate-600 dark:text-slate-400 hover:bg-sky-100 dark:hover:bg-sky-900/30 hover:text-sky-600 dark:hover:text-sky-400 transition-colors truncate"
                >
                  {chapter.title}
                </button>
              ))}
            </div>
          </div>
        </motion.div>
      )}
    </div>
  );
}
