"use client";

import { useState, useEffect, useRef } from "react";
import Image from "next/image";
import { motion, AnimatePresence } from "framer-motion";
import { MessageCircle, Heart, Send, Reply, ChevronDown, ChevronUp } from "lucide-react";
import {
  getPostComments,
  createComment,
  likeComment,
  type CommentItem,
} from "@/app/api/comments";

function flattenReplies(
  replies: CommentItem[],
  parentMap?: Map<number, string>
): (CommentItem & { replyToUser?: string })[] {
  const map = parentMap ?? new Map<number, string>();
  const result: (CommentItem & { replyToUser?: string })[] = [];
  for (const r of replies) {
    const replyToUser = map.get(r.parent_id!);
    result.push(replyToUser ? { ...r, replyToUser } : r);
    if (r.replies?.length) {
      map.set(r.id, r.github_user?.login ?? "匿名用户");
      result.push(...flattenReplies(r.replies, map));
    }
  }
  return result;
}

function relativeTime(dateStr: string): string {
  const d = new Date(dateStr);
  const now = new Date();
  const diff = now.getTime() - d.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);
  if (days >= 3) {
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  }
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  return `${days}天前`;
}

export default function PostComments({ postId }: { postId: number }) {
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [expandedReplies, setExpandedReplies] = useState<Set<number>>(new Set());
  const [inputValue, setInputValue] = useState("");
  const [replyTo, setReplyTo] = useState<CommentItem | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState("");
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const [likedIds, setLikedIds] = useState<Set<number>>(() => {
    if (typeof window === "undefined") return new Set();
    const saved = localStorage.getItem("liked_comments");
    return saved ? new Set(JSON.parse(saved)) : new Set();
  });

  useEffect(() => {
    let active = true;
    getPostComments(postId).then((data) => {
      if (active) {
        setComments(data);
        setLoading(false);
      }
    }).catch(() => {
      if (active) {
        setLoading(false);
        setLoadError(true);
      }
    });
    return () => { active = false; };
  }, [postId]);

  function toggleReplies(id: number) {
    setExpandedReplies((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function startReply(comment: CommentItem) {
    setReplyTo(comment);
    setInputValue("");
    setTimeout(() => inputRef.current?.focus(), 100);
  }

  function cancelReply() {
    setReplyTo(null);
    setInputValue("");
  }

  async function handleSubmit() {
    if (!inputValue.trim() || submitting) return;
    setSubmitting(true);
    try {
      await createComment({
        post_id: postId,
        content: inputValue.trim(),
        parent_id: replyTo?.id,
      });
      setNotice("评论已提交，站长审核通过后展示");
      setInputValue("");
      setReplyTo(null);
      setTimeout(() => setNotice(""), 4000);
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : "发送失败");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleLike(commentId: number) {
    const alreadyLiked = likedIds.has(commentId);
    try {
      const updated = await likeComment(commentId, alreadyLiked);
      setLikedIds((prev) => {
        const next = new Set(prev);
        if (alreadyLiked) next.delete(commentId);
        else next.add(commentId);
        localStorage.setItem("liked_comments", JSON.stringify([...next]));
        return next;
      });
      setComments((prev) =>
        prev.map((c) => {
          if (c.id === commentId) return { ...c, likes: updated.likes };
          return {
            ...c,
            replies: c.replies.map((r) =>
              r.id === commentId ? { ...r, likes: updated.likes } : r
            ),
          };
        })
      );
    } catch {}
  }

  return (
    <div className="mt-8 md:mt-12">
      <div className="flex items-center gap-2 md:gap-3 mb-4 md:mb-6">
        <MessageCircle className="w-5 h-5 md:w-6 md:h-6 text-sky-500" />
        <h2 className="text-lg md:text-xl font-bold text-slate-800 dark:text-slate-100">
          评论
        </h2>
      </div>

      {/* 匿名评论输入（提交后待站长审核） */}
      <div className="mb-4 rounded-2xl bg-white/50 dark:bg-slate-800/60 backdrop-blur-xl border border-white/30 dark:border-white/10 overflow-hidden">
        <AnimatePresence>
          {replyTo && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: "auto", opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              className="overflow-hidden"
            >
              <div className="flex items-center gap-1.5 md:gap-2 px-3 pt-2 md:px-5 md:pt-3 pb-0 text-[10px] md:text-xs text-slate-500 dark:text-slate-400">
                <Reply className="w-3 h-3 md:w-3.5 md:h-3.5" />
                <span>
                  回复{" "}
                  <span className="font-medium text-sky-600 dark:text-sky-400">
                    {replyTo.github_user?.login ?? "匿名用户"}
                  </span>
                </span>
                <span className="truncate flex-1 opacity-60">
                  {replyTo.content.slice(0, 50)}
                </span>
                <button
                  type="button"
                  onClick={cancelReply}
                  className="text-slate-400 hover:text-red-500 transition-colors ml-1 md:ml-2"
                >
                  ✕
                </button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        <div className="p-3 md:p-4">
          <textarea
            ref={inputRef}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder={
              replyTo ? "写下你的回复..." : "匿名评论，审核通过后展示..."
            }
            rows={3}
            onKeyDown={(e) => {
              if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
                handleSubmit();
              }
            }}
            className="w-full bg-transparent text-xs md:text-sm text-slate-700 dark:text-slate-200 placeholder:text-slate-400 dark:placeholder:text-slate-500 resize-none outline-none"
          />
          <div className="flex items-center justify-between mt-2 pt-2 md:mt-3 md:pt-3 border-t border-slate-200/50 dark:border-white/5">
            <span className="text-[10px] md:text-xs text-slate-400">
              匿名发言 · 提交后需站长审核
            </span>
            <button
              type="button"
              onClick={handleSubmit}
              disabled={!inputValue.trim() || submitting}
              className="flex items-center gap-1 md:gap-1.5 px-3 py-1 md:px-4 md:py-1.5 rounded-full bg-sky-500 text-white text-[10px] md:text-xs font-medium hover:bg-sky-600 disabled:opacity-40 disabled:cursor-not-allowed transition-all"
            >
              <Send className="w-3 h-3 md:w-3.5 md:h-3.5" />
              {submitting ? "发送中..." : replyTo ? "回复" : "发送"}
            </button>
          </div>
        </div>
      </div>

      {notice && (
        <div className="mb-4 px-3 py-2 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-[11px] md:text-xs text-emerald-600 dark:text-emerald-400">
          {notice}
        </div>
      )}

      {/* 加载出错 */}
      {loadError && (
        <div className="text-center py-8 md:py-12 text-slate-400">
          <MessageCircle className="w-8 h-8 md:w-10 md:h-10 mx-auto mb-2 md:mb-3 opacity-40" />
          <p className="text-xs md:text-sm mb-3">评论加载失败</p>
          <button
            type="button"
            onClick={() => {
              setLoadError(false);
              setLoading(true);
              getPostComments(postId).then((data) => {
                setComments(data);
                setLoading(false);
              }).catch(() => {
                setLoading(false);
                setLoadError(true);
              });
            }}
            className="text-[10px] md:text-xs text-sky-500 hover:text-sky-600 transition-colors underline underline-offset-2"
          >
            重新加载
          </button>
        </div>
      )}

      {/* 空状态 */}
      {!loading && !loadError && comments.length === 0 && (
        <div className="text-center py-8 md:py-12 text-slate-400">
          <MessageCircle className="w-8 h-8 md:w-10 md:h-10 mx-auto mb-2 md:mb-3 opacity-40" />
          <p className="text-xs md:text-sm">暂无评论，来抢沙发吧~</p>
        </div>
      )}

      {/* 加载中 */}
      {loading && (
        <div className="space-y-3">
          {[1, 2].map((i) => (
            <div key={i} className="h-16 md:h-20 rounded-2xl bg-white/40 dark:bg-slate-800/40 animate-pulse" />
          ))}
        </div>
      )}

      {/* 评论列表 */}
      <div className="space-y-3 md:space-y-4">
        {comments.map((comment, idx) => (
          <motion.div
            key={comment.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: idx * 0.05 }}
          >
            <CommentCard
              comment={comment}
              expandedReplies={expandedReplies}
              onReply={startReply}
              onToggleReplies={toggleReplies}
              likedIds={likedIds}
              onLike={handleLike}
            />
          </motion.div>
        ))}
      </div>
    </div>
  );
}

function CommentCard({
  comment,
  expandedReplies,
  onReply,
  onToggleReplies,
  likedIds,
  onLike,
}: {
  comment: CommentItem;
  expandedReplies: Set<number>;
  onReply: (comment: CommentItem) => void;
  onToggleReplies: (id: number) => void;
  likedIds: Set<number>;
  onLike: (id: number) => void;
}) {
  const isExpanded = expandedReplies.has(comment.id);
  const flat = flattenReplies(comment.replies ?? []);
  const replyCount = flat.length;

  return (
    <div className="rounded-2xl bg-white/50 dark:bg-slate-800/60 backdrop-blur-xl border border-white/30 dark:border-white/10 shadow-sm overflow-hidden hover:shadow-md transition-shadow duration-300">
      <div className="p-3 md:p-5">
        <div className="flex items-center gap-2 md:gap-3 mb-2 md:mb-3">
          {comment.github_user ? (
            <Image
              src={comment.github_user.avatar}
              alt={comment.github_user.login}
              width={32}
              height={32}
              className="rounded-full md:w-9 md:h-9"
            />
          ) : (
            <div className="w-8 h-8 md:w-9 md:h-9 rounded-full bg-gradient-to-br from-slate-300 to-slate-400 dark:from-slate-600 dark:to-slate-700 flex items-center justify-center text-white text-xs md:text-sm font-bold">
              ?
            </div>
          )}
          <div className="flex-1 min-w-0">
            <span className="text-xs md:text-sm font-semibold text-slate-800 dark:text-slate-200">
              {comment.github_user?.login ?? "匿名用户"}
            </span>
            {comment.github_user?.bio && (
              <p className="text-[10px] md:text-xs text-slate-500 dark:text-slate-400 truncate">
                {comment.github_user.bio}
              </p>
            )}
          </div>
          <span className="text-[10px] md:text-xs text-slate-400 dark:text-slate-500 shrink-0">
            {relativeTime(comment.created_at)}
          </span>
        </div>

        <p className="text-xs md:text-sm text-slate-700 dark:text-slate-300 leading-relaxed whitespace-pre-wrap mb-3 md:mb-4">
          {comment.content}
        </p>

        <div className="flex items-center gap-2 md:gap-4 pt-2 md:pt-3 border-t border-slate-200/50 dark:border-white/5">
          <button
            type="button"
            onClick={() => onLike(comment.id)}
            className={`flex items-center gap-1 md:gap-1.5 text-[10px] md:text-xs transition-colors ${
              likedIds.has(comment.id)
                ? "text-pink-500"
                : "text-slate-400 hover:text-pink-500"
            }`}
          >
            <Heart
              className={`w-3.5 h-3.5 md:w-4 md:h-4 transition-all duration-300 ${
                likedIds.has(comment.id) ? "fill-pink-500 scale-110" : ""
              }`}
            />
            <span>{comment.likes}</span>
          </button>

          <button
            type="button"
            onClick={() => onReply(comment)}
            className="flex items-center gap-1 md:gap-1.5 text-[10px] md:text-xs text-slate-400 hover:text-sky-500 transition-colors"
          >
            <MessageCircle className="w-3.5 h-3.5 md:w-4 md:h-4" />
            <span>回复</span>
          </button>

          {replyCount > 0 && (
            <button
              type="button"
              onClick={() => onToggleReplies(comment.id)}
              className="flex items-center gap-1 md:gap-1.5 text-[10px] md:text-xs text-slate-400 hover:text-blue-500 transition-colors ml-auto"
            >
              {isExpanded ? (
                <ChevronUp className="w-3 h-3 md:w-3.5 md:h-3.5" />
              ) : (
                <ChevronDown className="w-3 h-3 md:w-3.5 md:h-3.5" />
              )}
              <span>{replyCount} 条回复</span>
            </button>
          )}
        </div>
      </div>

      <AnimatePresence>
        {isExpanded && replyCount > 0 && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.3 }}
            className="overflow-hidden"
          >
            <div className="border-t border-slate-200/50 dark:border-white/5 bg-slate-50/50 dark:bg-slate-900/30">
              {flat.map((reply) => (
                <ReplyCard
                  key={reply.id}
                  reply={reply}
                  likedIds={likedIds}
                  onLike={onLike}
                  onReply={onReply}
                />
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function ReplyCard({
  reply,
  likedIds,
  onLike,
  onReply,
}: {
  reply: CommentItem & { replyToUser?: string };
  likedIds: Set<number>;
  onLike: (id: number) => void;
  onReply: (comment: CommentItem) => void;
}) {
  return (
    <div className="px-3 py-2 md:px-5 md:py-3 border-b border-slate-200/30 dark:border-white/5 last:border-0">
      <div className="flex items-start gap-2 md:gap-3">
        {reply.github_user ? (
          <Image
            src={reply.github_user.avatar}
            alt={reply.github_user.login}
            width={24}
            height={24}
            className="rounded-full mt-0.5 md:w-7 md:h-7"
          />
        ) : (
          <div className="w-6 h-6 md:w-7 md:h-7 rounded-full bg-gradient-to-br from-slate-300 to-slate-400 dark:from-slate-600 dark:to-slate-700 flex items-center justify-center text-white text-[10px] md:text-xs font-bold mt-0.5">
            ?
          </div>
        )}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 md:gap-2 mb-0.5 md:mb-1">
            <span className="text-[10px] md:text-xs font-semibold text-slate-700 dark:text-slate-300">
              {reply.github_user?.login ?? "匿名用户"}
            </span>
            <span className="text-[10px] md:text-xs text-slate-400">
              {relativeTime(reply.created_at)}
            </span>
          </div>
          <p className="text-xs md:text-sm text-slate-600 dark:text-slate-400 leading-relaxed whitespace-pre-wrap">
            {reply.replyToUser && (
              <span className="text-sky-500 dark:text-sky-400 mr-1">
                回复 @{reply.replyToUser}：
              </span>
            )}
            {reply.content}
          </p>
          <div className="flex items-center gap-2 md:gap-3 mt-1.5 md:mt-2">
            <button
              type="button"
              onClick={() => onLike(reply.id)}
              className={`flex items-center gap-0.5 md:gap-1 text-[10px] md:text-xs transition-colors ${
                likedIds.has(reply.id)
                  ? "text-pink-500"
                  : "text-slate-400 hover:text-pink-500"
              }`}
            >
              <Heart
                className={`w-3 h-3 md:w-3.5 md:h-3.5 ${
                  likedIds.has(reply.id) ? "fill-pink-500" : ""
                }`}
              />
              <span>{reply.likes}</span>
            </button>
            <button
              type="button"
              onClick={() => onReply(reply)}
              className="flex items-center gap-0.5 md:gap-1 text-[10px] md:text-xs text-slate-400 hover:text-sky-500 transition-colors"
            >
              <Reply className="w-3 h-3 md:w-3.5 md:h-3.5" />
              <span>回复</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
