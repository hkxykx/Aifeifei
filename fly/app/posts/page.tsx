"use client";

import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { BookOpen, Loader2, Search, X } from "lucide-react";
import PostCard, { type PostOut } from "@/components/posts/PostCard";
import {
  getCategories,
  getPosts,
  getTags,
  type CategoryItem,
  type TagItem,
} from "@/app/api";

export default function PostsPage() {
  const [categories, setCategories] = useState<CategoryItem[]>([]);
  const [posts, setPosts] = useState<PostOut[]>([]);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const pageSize = 12;

  // 标题搜索 + 标签筛选
  const [searchOpen, setSearchOpen] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [tagList, setTagList] = useState<TagItem[]>([]);
  const [activeTag, setActiveTag] = useState<string | null>(null);
  const [searchState, setSearchState] = useState<{
    key: string;
    results: PostOut[] | null;
  }>({ key: "", results: null });
  const searching = keyword.trim().length > 0 || activeTag !== null;
  const searchKey = `${activeTag ?? ""}|${activeCategory ?? ""}|${keyword.trim().toLowerCase()}`;
  const searchLoading =
    searching && searchState.key !== searchKey;
  const searchResults =
    searchState.key === searchKey ? searchState.results : null;

  // 获取分类与标签
  useEffect(() => {
    getCategories()
      .then((data) => {
        const sorted = [...data].sort((a, b) => a.sort - b.sort);
        setCategories(sorted);
      })
      .catch(() => {});
    getTags().then(setTagList).catch(() => {});
  }, []);

  // 搜索模式：标签走后端过滤，关键字走标题客户端过滤（取前 200 篇）
  useEffect(() => {
    if (!searching) return;
    const key = searchKey;
    const k = keyword.trim().toLowerCase();
    getPosts({
      status: "published",
      page: 1,
      size: 200,
      ...(activeCategory ? { category: activeCategory } : {}),
      ...(activeTag ? { tag: activeTag } : {}),
    })
      .then((data) => {
        setSearchState({
          key,
          results: k ? data.filter((p) => p.title.toLowerCase().includes(k)) : data,
        });
      })
      .catch(() => setSearchState({ key, results: [] }));
  }, [searching, searchKey, keyword, activeCategory, activeTag]);

  // 获取文章
  useEffect(() => {
    queueMicrotask(() => setLoading(true));
    getPosts({
      status: "published",
      page: 1,
      size: pageSize,
      ...(activeCategory ? { category: activeCategory } : {}),
    })
      .then((data) => {
        setPosts(data);
        setHasMore(data.length === pageSize);
      })
      .catch(() => { setPosts([]); })
      .finally(() => { setLoading(false); });
  }, [activeCategory, pageSize]);

  const handleLoadMore = () => {
    const next = page + 1;
    setPage(next);
    setLoading(true);
    getPosts({
      status: "published",
      page: next,
      size: pageSize,
      ...(activeCategory ? { category: activeCategory } : {}),
    })
      .then((data) => {
        setPosts((prev) => [...prev, ...data]);
        setHasMore(data.length === pageSize);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-6 md:py-12">
      {/* 页头 */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="mb-6 md:mb-10"
      >
        <div className="flex items-center gap-2 md:gap-3 mb-1 md:mb-2">
          <BookOpen className="w-5 h-5 md:w-7 md:h-7 text-sky-500" />
          <h1 className="text-xl md:text-3xl font-bold text-slate-800 dark:text-slate-100">
            文章
          </h1>
        </div>
        <p className="text-sm md:text-base text-slate-500 dark:text-slate-400 ml-7 md:ml-10">
          记录技术探索、学术研究与生活感悟
        </p>
      </motion.div>

      {/* 搜索：标题关键字 + 可点击标签筛选 */}
      <motion.div
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.1 }}
        className="mb-4 flex flex-wrap items-center gap-2"
      >
        <button
          type="button"
          onClick={() => setSearchOpen((v) => !v)}
          className={`flex items-center gap-1.5 px-4 py-2 rounded-2xl text-xs md:text-sm font-medium transition-all duration-300 ${
            searchOpen
              ? "text-white shadow-lg shadow-sky-500/20 bg-gradient-to-r from-sky-500 to-cyan-400"
              : "text-slate-600 dark:text-slate-400 bg-white/10 dark:bg-white/[0.05] backdrop-blur-xl border border-white/20 hover:bg-white/20 dark:hover:bg-white/[0.1]"
          }`}
        >
          <Search className="w-4 h-4" />
          搜索
        </button>
        {searchOpen && (
          <div className="flex-1 min-w-[220px] relative">
            <input
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="输入标题关键字，或点击下方标签筛选..."
              autoComplete="off"
              className="w-full pl-4 pr-9 py-2 rounded-2xl text-sm bg-white/60 dark:bg-slate-800/60 backdrop-blur-xl border border-white/40 dark:border-white/10 text-slate-700 dark:text-slate-200 placeholder-slate-400 outline-none focus:border-sky-400 transition-colors"
            />
            {(keyword || activeTag) && (
              <button
                type="button"
                title="清除搜索条件"
                onClick={() => {
                  setKeyword("");
                  setActiveTag(null);
                }}
                className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded-full text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>
        )}
      </motion.div>

      {/* 可点击标签（搜索打开时显示） */}
      {searchOpen && tagList.length > 0 && (
        <div className="mb-4 flex flex-wrap items-center gap-1.5 md:gap-2">
          <span className="text-xs text-slate-400">标签：</span>
          {tagList.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() =>
                setActiveTag((prev) => (prev === t.name ? null : t.name))
              }
              className={`px-3 py-1.5 rounded-2xl text-xs md:text-sm font-medium transition-all duration-300 ${
                activeTag === t.name
                  ? "text-white shadow-lg shadow-sky-500/20 bg-gradient-to-r from-sky-500 to-cyan-400"
                  : "text-slate-600 dark:text-slate-400 bg-white/10 dark:bg-white/[0.05] backdrop-blur-xl border border-white/20 hover:bg-white/20 dark:hover:bg-white/[0.1]"
              }`}
            >
              {t.name}
              <span className="ml-1 text-[10px] opacity-70">
                {t.post_count}
              </span>
            </button>
          ))}
        </div>
      )}

      {/* 分类筛选 */}
      <motion.div
        initial={{ opacity: 0, y: -10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, delay: 0.15 }}
        className="mb-5 md:mb-8 flex flex-wrap gap-1.5 md:gap-2"
      >
        <FilterTab
          label="全部"
          count={null}
          active={activeCategory === null}
          onClick={() => setActiveCategory(null)}
        />
        {categories.map((cat) => (
          <FilterTab
            key={cat.id}
            label={cat.name}
            count={cat.post_count}
            active={activeCategory === cat.slug}
            onClick={() => setActiveCategory(cat.slug)}
          />
        ))}
      </motion.div>

      {/* 文章网格（搜索态 / 浏览态） */}
      {searching ? (
        searchLoading ? (
          <div className="flex items-center justify-center py-32">
            <Loader2 className="w-8 h-8 text-sky-500 animate-spin" />
          </div>
        ) : !searchResults || searchResults.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-32 text-slate-400">
            <BookOpen className="w-12 h-12 mb-4 opacity-40" />
            <p>没有匹配的文章</p>
          </div>
        ) : (
          <AnimatePresence mode="wait">
            <motion.div
              key={`${activeTag ?? ""}|${activeCategory ?? ""}|${keyword}`}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.3 }}
            >
              <p className="mb-3 text-xs text-slate-400">
                共 {searchResults.length} 篇匹配
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 md:gap-6">
                {searchResults.map((post, i) => (
                  <div key={post.id}>
                    <PostCard post={post} index={i} />
                  </div>
                ))}
              </div>
            </motion.div>
          </AnimatePresence>
        )
      ) : loading && posts.length === 0 ? (
        <div className="flex items-center justify-center py-32">
          <Loader2 className="w-8 h-8 text-sky-500 animate-spin" />
        </div>
      ) : posts.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-32 text-slate-400">
          <BookOpen className="w-12 h-12 mb-4 opacity-40" />
          <p>暂无文章</p>
        </div>
      ) : (
        <AnimatePresence mode="wait">
          <motion.div
            key={activeCategory ?? "all"}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 md:gap-6"
          >
            {posts.map((post, i) => (
              <div key={post.id}>
                <PostCard post={post} index={i} />
              </div>
            ))}
          </motion.div>
        </AnimatePresence>
      )}

      {/* 加载更多 */}
      {!searching && hasMore && posts.length > 0 && !loading && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="flex justify-center mt-10"
        >
          <button
            type="button"
            onClick={handleLoadMore}
            className="px-5 py-2 md:px-8 md:py-3 rounded-2xl bg-white/10 dark:bg-white/[0.05] backdrop-blur-xl border border-white/20 text-sm md:text-base text-slate-700 dark:text-slate-300 hover:bg-white/20 dark:hover:bg-white/[0.1] transition-all duration-300 hover:-translate-y-0.5"
          >
            加载更多
          </button>
        </motion.div>
      )}

      {/* 加载中指示器（加载更多时） */}
      {!searching && loading && posts.length > 0 && (
        <div className="flex justify-center mt-10">
          <Loader2 className="w-6 h-6 text-sky-500 animate-spin" />
        </div>
      )}
    </div>
  );
}

/* ---------- 分类标签组件 ---------- */

function FilterTab({
  label,
  count,
  active,
  onClick,
}: {
  label: string;
  count: number | null;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`relative px-3 py-1.5 md:px-5 md:py-2 rounded-2xl text-xs md:text-sm font-medium transition-all duration-300 ${
        active
          ? "text-white shadow-lg shadow-sky-500/20"
          : "text-slate-600 dark:text-slate-400 bg-white/10 dark:bg-white/[0.05] backdrop-blur-xl border border-white/20 hover:bg-white/20 dark:hover:bg-white/[0.1]"
      }`}
    >
      {active && (
        <motion.div
          layoutId="activeCategoryBg"
          className="absolute inset-0 rounded-2xl bg-gradient-to-r from-sky-500 to-cyan-400"
          transition={{ type: "spring", stiffness: 400, damping: 30 }}
        />
      )}
      <span className="relative z-10">
        {label}
        {count !== null && count > 0 && (
          <span
            className={`ml-1.5 text-xs ${
              active ? "text-white/70" : "text-slate-400 dark:text-slate-500"
            }`}
          >
            {count}
          </span>
        )}
      </span>
    </button>
  );
}
