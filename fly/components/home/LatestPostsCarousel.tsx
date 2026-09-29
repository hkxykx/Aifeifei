"use client";

import { useState, useEffect, useMemo } from "react";
import Link from "next/link";
import Image from "next/image";
import { BookOpen, Clock, Eye, Heart, Search } from "lucide-react";
import { getPosts, type PostItem } from "@/app/api";
import { coverOrRandom, randomImage } from "@/imageLibrary";

function relativeDate(dateStr: string | null): string {
  if (!dateStr) return "";
  const now = new Date();
  const d = new Date(dateStr);
  const diff = now.getTime() - d.getTime();
  const days = Math.floor(diff / 86400000);
  if (days < 1) return "今天";
  if (days < 2) return "昨天";
  if (days < 7) return `${days}天前`;
  return d.toLocaleDateString("zh-CN", { month: "short", day: "numeric" });
}

export default function LatestPostsCarousel() {
  const [posts, setPosts] = useState<PostItem[]>([]);
  const [keyword, setKeyword] = useState("");

  useEffect(() => {
    getPosts({ status: "published", page: 1, size: 100 })
      .then(setPosts)
      .catch(() => { });
  }, []);

  const trimmed = keyword.trim().toLowerCase();
  const filtered = trimmed
    ? posts.filter((p) => p.title.toLowerCase().includes(trimmed))
    : posts;

  // 无文章时的占位背景：每次进入随机取一张项目图片
  const emptyCover = useMemo(() => randomImage(), []);

  if (!posts.length) {
    return (
      <div className="relative rounded-3xl overflow-hidden border border-white/40 dark:border-white/10 shadow-xl min-h-[220px] group">
        <Image
          src={emptyCover}
          alt=""
          fill
          className="object-cover"
          sizes="(max-width: 768px) 100vw, 66vw"
          priority
        />
        <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-[2px]" />
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 text-white/85">
          <BookOpen className="w-10 h-10 opacity-70" />
          <span className="text-sm drop-shadow">暂无文章</span>
        </div>
      </div>
    );
  }

  const hero = posts[0];
  const rest = posts.slice(1);

  return (
    <div className="flex flex-col gap-3 h-full">
      {/* 标题 + 标题搜索 */}
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-slate-700 dark:text-slate-200">
          <BookOpen className="w-4 h-4 text-sky-500" />
          <span className="text-sm font-semibold">文章</span>
        </div>
        <div className="relative">
          <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
          <input
            type="text"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="搜标题关键字"
            autoComplete="off"
            className="w-36 md:w-44 pl-8 pr-3 py-1.5 text-xs rounded-full bg-white/60 dark:bg-slate-800/60 border border-white/40 dark:border-white/10 text-slate-700 dark:text-slate-200 placeholder-slate-400 outline-none focus:border-sky-400 transition-colors"
          />
        </div>
      </div>

      {trimmed ? (
        /* 搜索结果（按标题过滤） */
        <div className="rounded-3xl bg-white/40 dark:bg-slate-800/50 backdrop-blur-md border border-white/40 dark:border-white/10 shadow-xl p-2 max-h-[340px] overflow-y-auto">
          {filtered.length ? (
            filtered.map((post) => (
              <Link
                key={post.id}
                href={`/posts/${post.slug}`}
                className="flex items-center justify-between gap-3 px-3 py-2.5 rounded-xl hover:bg-sky-50/70 dark:hover:bg-slate-700/50 group"
              >
                <span className="text-sm text-slate-700 dark:text-slate-200 truncate group-hover:text-sky-600 transition-colors">
                  {post.title}
                </span>
                <span className="text-xs text-slate-400 flex-shrink-0">
                  {relativeDate(post.published_at)}
                </span>
              </Link>
            ))
          ) : (
            <p className="py-8 text-center text-sm text-slate-400">无匹配文章</p>
          )}
        </div>
      ) : (
        <>
          {/* Hero 大图 */}
          <Link
            href={`/posts/${hero.slug}`}
            className="relative flex-1 min-h-[160px] md:min-h-[160px] rounded-3xl overflow-hidden group cursor-pointer"
          >
            <Image
              src={coverOrRandom(hero.cover, hero.id)}
              alt={hero.title}
              fill
              className="object-cover transition-transform duration-700 group-hover:scale-105"
              sizes="(max-width: 768px) 100vw, 66vw"
              priority
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/30 to-transparent" />

            {/* 底部信息 */}
            <div className="absolute bottom-0 left-0 right-0 p-5">
              <h3 className="text-xl md:text-2xl font-bold text-white mb-1.5 line-clamp-1">
                {hero.title}
              </h3>
              <p className="text-white/70 text-sm line-clamp-1 mb-2">
                {hero.description}
              </p>
              <div className="flex items-center gap-4 text-white/50 text-xs">
                <span>{relativeDate(hero.published_at)}</span>
                <span className="flex items-center gap-1">
                  <Clock className="w-3 h-3" /> {hero.reading_time} 分钟
                </span>
                <span className="flex items-center gap-1">
                  <Eye className="w-3 h-3" /> {hero.views}
                </span>
                <span className="flex items-center gap-1">
                  <Heart className="w-3 h-3" /> {hero.likes}
                </span>
              </div>
            </div>
          </Link>

          {/* 小卡片行 */}
          {rest.length > 0 && (
            <div className="grid grid-cols-3 gap-3">
              {rest.map((post) => (
                <Link
                  key={post.id}
                  href={`/posts/${post.slug}`}
                  className="relative rounded-2xl overflow-hidden group cursor-pointer h-[80px]"
                >
                  <Image
                    src={coverOrRandom(post.cover, post.id)}
                    alt={post.title}
                    fill
                    className="object-cover transition-transform duration-500 group-hover:scale-105"
                    sizes="200px"
                  />
                  <div className="absolute inset-0 bg-black/40 group-hover:bg-black/30 transition-colors duration-300" />
                  <div className="absolute inset-0 p-3 flex flex-col justify-end">
                    <h4 className="text-xs font-bold text-white line-clamp-1">
                      {post.title}
                    </h4>
                    <span className="text-[10px] text-white/50">
                      {relativeDate(post.published_at)}
                    </span>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
