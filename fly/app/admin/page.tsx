"use client";

import { useState, useEffect, useRef } from "react";
import { motion } from "framer-motion";
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  arrayMove,
  rectSortingStrategy,
} from "@dnd-kit/sortable";
import {
  ShieldCheck,
  LogOut,
  MessageSquare,
  MessageCircle,
  BookOpen,
  Settings,
  Search,
  Trash2,
  Check,
  X,
  Loader2,
  Save,
  Eye,
} from "lucide-react";
import {
  adminGetMessages,
  adminUpdateMessageStatus,
  adminDeleteMessage,
  type MessageItem,
} from "@/app/api/messages";
import {
  adminGetComments,
  adminUpdateCommentStatus,
  adminDeleteComment,
} from "@/app/api/comments";
import {
  adminGetChatterComments,
  adminUpdateChatterCommentStatus,
  adminDeleteChatterComment,
} from "@/app/api/chatters";
import {
  adminListSiteConfig,
  adminUpdateSiteConfig,
  type SiteConfigItem,
} from "@/app/api/site-config";
import {
  adminLogin,
  adminFetch,
  getAdminToken,
  clearAdminToken,
} from "@/app/api/adminAuth";
import {
  getBookshelf,
  addToBookshelf,
  removeFromBookshelf,
  searchBookMultiSSEUrl,
  getChapterList,
  getBookContent,
} from "@/app/api/novel/novel-api";
import type { Book, SearchBook, Chapter } from "@/app/novel/_lib/utils";
import { proxyCover, sortShelfBooks, BOOK_ORDER_KEY } from "@/app/novel/_lib/utils";
import SortableBookCard from "@/app/novel/_lib/SortableBookCard";
import { useSmoothWheel, useDragScroll } from "@/lib/gestures";

type Section = "messages" | "comments" | "shelf" | "config";
type MsgTab = "pending" | "approved" | "rejected";

const SECTION_LABELS: Record<Section, { label: string; icon: typeof MessageSquare }> = {
  messages: { label: "留言审核", icon: MessageSquare },
  comments: { label: "评论审核", icon: MessageCircle },
  shelf: { label: "小说书架", icon: BookOpen },
  config: { label: "站点配置", icon: Settings },
};

const MSG_TABS: { value: MsgTab; label: string }[] = [
  { value: "pending", label: "待审核" },
  { value: "approved", label: "已通过" },
  { value: "rejected", label: "已拒绝" },
];

function formatTime(dateStr?: string): string {
  if (!dateStr) return "";
  const d = new Date(dateStr);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 把存储的 JSON 字符串还原为可编辑的展示文本 */
function displayValue(raw: string): string {
  try {
    const parsed = JSON.parse(raw);
    if (typeof parsed === "object" && parsed !== null) return JSON.stringify(parsed);
    return String(parsed);
  } catch {
    return raw;
  }
}

export default function AdminPage() {
  useSmoothWheel();
  useDragScroll();
  const [authed, setAuthed] = useState<boolean | null>(null);
  const [loginUser, setLoginUser] = useState("");
  const [loginPass, setLoginPass] = useState("");
  const [loginBusy, setLoginBusy] = useState(false);
  const [loginError, setLoginError] = useState("");
  const [section, setSection] = useState<Section>("messages");
  const [actionError, setActionError] = useState("");

  // 令牌校验
  useEffect(() => {
    let active = true;
    if (!getAdminToken()) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setAuthed(false);
      return;
    }
    adminFetch("/api/auth/me")
      .then((res) => {
        if (!active) return;
        setAuthed(res.ok);
        if (!res.ok) setLoginError("登录已过期，请重新登录");
      })
      .catch(() => {
        if (active) setAuthed(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!loginUser.trim() || !loginPass || loginBusy) return;
    setLoginBusy(true);
    setLoginError("");
    try {
      await adminLogin(loginUser.trim(), loginPass);
      setAuthed(true);
    } catch (err) {
      setLoginError(err instanceof Error ? err.message : "登录失败");
    } finally {
      setLoginBusy(false);
    }
  };

  const handleLogout = () => {
    clearAdminToken();
    setAuthed(false);
    setLoginPass("");
  };

  const failToLogin = (status: number) => {
    if (status === 401 || status === 403) {
      setAuthed(false);
      setLoginError("登录已过期，请重新登录");
      return true;
    }
    return false;
  };

  if (authed === null) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Loader2 className="w-8 h-8 text-sky-500 animate-spin" />
      </div>
    );
  }

  if (!authed) {
    return (
      <div className="max-w-sm mx-auto px-4 py-16 md:py-24">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="rounded-3xl bg-white/60 dark:bg-slate-800/60 backdrop-blur-md border border-white/40 dark:border-white/10 shadow-xl p-6 md:p-8"
        >
          <div className="flex items-center gap-3 mb-6">
            <div className="w-10 h-10 rounded-xl bg-sky-500/15 text-sky-500 flex items-center justify-center">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-lg font-bold text-slate-800 dark:text-white">站长后台</h1>
              <p className="text-xs text-slate-500 dark:text-slate-400">请登录后管理站点</p>
            </div>
          </div>
          <form onSubmit={handleLogin} className="space-y-3">
            <input
              type="text"
              value={loginUser}
              onChange={(e) => setLoginUser(e.target.value)}
              placeholder="用户名"
              autoComplete="username"
              className="w-full px-4 py-2.5 rounded-xl bg-white/70 dark:bg-slate-900/50 border border-slate-200/60 dark:border-slate-700/60 text-sm text-slate-800 dark:text-slate-200 placeholder-slate-400 outline-none focus:border-sky-400 transition-colors"
            />
            <input
              type="password"
              value={loginPass}
              onChange={(e) => setLoginPass(e.target.value)}
              placeholder="密码"
              autoComplete="current-password"
              className="w-full px-4 py-2.5 rounded-xl bg-white/70 dark:bg-slate-900/50 border border-slate-200/60 dark:border-slate-700/60 text-sm text-slate-800 dark:text-slate-200 placeholder-slate-400 outline-none focus:border-sky-400 transition-colors"
            />
            {loginError && (
              <p className="text-xs text-red-500">{loginError}</p>
            )}
            <button
              type="submit"
              disabled={loginBusy || !loginUser.trim() || !loginPass}
              className="w-full px-4 py-2.5 rounded-xl bg-sky-500 text-white text-sm font-medium hover:bg-sky-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {loginBusy ? "登录中..." : "登 录"}
            </button>
          </form>
        </motion.div>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-6 md:py-10">
      {/* 顶栏 */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-sky-500/15 text-sky-500 flex items-center justify-center">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-lg md:text-xl font-bold text-slate-800 dark:text-white">站长后台</h1>
            <p className="text-[11px] text-slate-500 dark:text-slate-400">留言审核 · 评论审核 · 书架管理 · 站点配置</p>
          </div>
        </div>
        <button
          type="button"
          onClick={handleLogout}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs text-slate-500 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
        >
          <LogOut className="w-3.5 h-3.5" />
          退出
        </button>
      </div>

      {/* 分区导航 */}
      <div className="flex gap-2 mb-6 flex-wrap">
        {(Object.keys(SECTION_LABELS) as Section[]).map((key) => {
          const { label, icon: Icon } = SECTION_LABELS[key];
          const active = section === key;
          return (
            <button
              key={key}
              type="button"
              onClick={() => setSection(key)}
              className={`flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-medium transition-all ${
                active
                  ? "bg-sky-500 text-white shadow-md shadow-sky-500/25"
                  : "bg-white/60 dark:bg-slate-800/60 text-slate-600 dark:text-slate-300 hover:bg-white dark:hover:bg-slate-700/60"
              }`}
            >
              <Icon className="w-4 h-4" />
              {label}
            </button>
          );
        })}
      </div>

      {actionError && (
        <div className="mb-4 px-4 py-2.5 rounded-xl bg-red-500/10 border border-red-300/40 text-red-600 dark:text-red-400 text-sm">
          {actionError}
        </div>
      )}

      {section === "messages" && <MessageSection failToLogin={failToLogin} onError={setActionError} />}
      {section === "comments" && <CommentsSection failToLogin={failToLogin} onError={setActionError} />}
      {section === "shelf" && <ShelfSection failToLogin={failToLogin} onError={setActionError} />}
      {section === "config" && <ConfigSection failToLogin={failToLogin} onError={setActionError} />}
    </div>
  );
}

/* ---------------- 留言审核 ---------------- */

function MessageSection({
  failToLogin,
  onError,
}: {
  failToLogin: (status: number) => boolean;
  onError: (msg: string) => void;
}) {
  const [tab, setTab] = useState<MsgTab>("pending");
  const [list, setList] = useState<MessageItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = async (target: MsgTab) => {
    setLoading(true);
    onError("");
    try {
      const data = await adminGetMessages(target);
      setList(data);
    } catch (err) {
      const status = err instanceof Error && /API Error: (\d+)/.test(err.message)
        ? Number(err.message.match(/API Error: (\d+)/)![1])
        : 0;
      if (!failToLogin(status)) onError("加载留言失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load(tab);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab]);

  const act = async (msgId: number, action: "approve" | "reject" | "delete") => {
    setBusyId(msgId);
    onError("");
    try {
      if (action === "delete") {
        await adminDeleteMessage(msgId);
      } else {
        await adminUpdateMessageStatus(msgId, action === "approve" ? "approved" : "rejected");
      }
      setList((prev) => prev.filter((m) => m.id !== msgId));
    } catch (err) {
      const status = err instanceof Error && /API Error: (\d+)/.test(err.message)
        ? Number(err.message.match(/API Error: (\d+)/)![1])
        : 0;
      if (!failToLogin(status)) onError("操作失败，请重试");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <div className="flex gap-2 mb-4">
        {MSG_TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            onClick={() => setTab(t.value)}
            className={`px-4 py-1.5 rounded-full text-xs font-medium transition-colors ${
              tab === t.value
                ? "bg-sky-500 text-white"
                : "bg-white/60 dark:bg-slate-800/60 text-slate-500 dark:text-slate-400 hover:text-sky-500"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="flex justify-center py-16 text-slate-400">
          <Loader2 className="w-6 h-6 animate-spin" />
        </div>
      ) : list.length === 0 ? (
        <div className="text-center py-16 text-slate-400 text-sm">暂无{MSG_TABS.find((t) => t.value === tab)?.label}的留言</div>
      ) : (
        <div className="space-y-3">
          {list.map((msg) => (
            <motion.div
              key={msg.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className="rounded-2xl bg-white/60 dark:bg-slate-800/60 backdrop-blur-md border border-white/40 dark:border-white/10 p-4"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-slate-700 dark:text-slate-200 whitespace-pre-wrap break-words">
                    {msg.content}
                  </p>
                  <p className="mt-2 text-[11px] text-slate-400">
                    {formatTime(msg.created_at)}
                    {msg.ip ? ` · IP ${msg.ip}` : ""}
                    {msg.github_user ? ` · GitHub @${msg.github_user.login}` : ""}
                    {msg.replies?.length ? ` · ${msg.replies.length} 条回复` : ""}
                  </p>
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                  {tab !== "approved" && (
                    <button
                      type="button"
                      title="通过"
                      disabled={busyId === msg.id}
                      onClick={() => act(msg.id, "approve")}
                      className="p-1.5 rounded-lg text-emerald-600 hover:bg-emerald-50 dark:hover:bg-emerald-900/20 disabled:opacity-50 transition-colors"
                    >
                      <Check className="w-4 h-4" />
                    </button>
                  )}
                  {tab !== "rejected" && (
                    <button
                      type="button"
                      title="拒绝"
                      disabled={busyId === msg.id}
                      onClick={() => act(msg.id, "reject")}
                      className="p-1.5 rounded-lg text-amber-600 hover:bg-amber-50 dark:hover:bg-amber-900/20 disabled:opacity-50 transition-colors"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  )}
                  <button
                    type="button"
                    title="删除"
                    disabled={busyId === msg.id}
                    onClick={() => {
                      if (window.confirm("确定删除这条留言？")) act(msg.id, "delete");
                    }}
                    className="p-1.5 rounded-lg text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 disabled:opacity-50 transition-colors"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ---------------- 评论审核（文章评论 / 说说评论） ---------------- */

type CommentKind = "post" | "chatter";

type AnyComment = {
  id: number;
  content: string;
  status: string;
  created_at: string;
  github_user: { login: string } | null;
  post_id?: number;
  chatter_id?: number;
};

function CommentsSection({
  failToLogin,
  onError,
}: {
  failToLogin: (status: number) => boolean;
  onError: (msg: string) => void;
}) {
  const [kind, setKind] = useState<CommentKind>("post");
  const [tab, setTab] = useState<MsgTab>("pending");
  const [list, setList] = useState<AnyComment[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = async (k: CommentKind, t: MsgTab) => {
    setLoading(true);
    onError("");
    try {
      const data: AnyComment[] =
        k === "post" ? await adminGetComments(t) : await adminGetChatterComments(t);
      setList(data);
    } catch (err) {
      const status = err instanceof Error && /API Error: (\d+)/.test(err.message)
        ? Number(err.message.match(/API Error: (\d+)/)![1])
        : 0;
      if (!failToLogin(status)) onError("加载评论失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load(kind, tab);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kind, tab]);

  const act = async (commentId: number, action: "approve" | "reject" | "delete") => {
    setBusyId(commentId);
    onError("");
    try {
      if (action === "delete") {
        if (kind === "post") await adminDeleteComment(commentId);
        else await adminDeleteChatterComment(commentId);
      } else {
        const status = action === "approve" ? "approved" : "rejected";
        if (kind === "post") await adminUpdateCommentStatus(commentId, status);
        else await adminUpdateChatterCommentStatus(commentId, status);
      }
      setList((prev) => prev.filter((c) => c.id !== commentId));
    } catch (err) {
      const status = err instanceof Error && /API Error: (\d+)/.test(err.message)
        ? Number(err.message.match(/API Error: (\d+)/)![1])
        : 0;
      if (!failToLogin(status)) onError("操作失败，请重试");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div>
      <div className="flex flex-wrap items-center gap-2 mb-3">
        {([["post", "文章评论"], ["chatter", "说说评论"]] as const).map(([val, label]) => (
          <button
            key={val}
            type="button"
            onClick={() => setKind(val)}
            className={`px-4 py-1.5 rounded-full text-xs font-medium transition-colors ${
              kind === val
                ? "bg-slate-700 dark:bg-slate-200 text-white dark:text-slate-900"
                : "bg-white/60 dark:bg-slate-800/60 text-slate-500 hover:bg-white dark:hover:bg-slate-700/60"
            }`}
          >
            {label}
          </button>
        ))}
      </div>
      <div className="flex gap-2 mb-4">
        {MSG_TABS.map((t) => (
          <button
            key={t.value}
            type="button"
            onClick={() => setTab(t.value)}
            className={`px-4 py-1.5 rounded-full text-xs font-medium transition-colors ${
              tab === t.value
                ? "bg-sky-500 text-white"
                : "bg-white/60 dark:bg-slate-800/60 text-slate-500 dark:text-slate-400 hover:text-sky-500"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="flex justify-center py-16 text-slate-400">
          <Loader2 className="w-6 h-6 animate-spin" />
        </div>
      ) : list.length === 0 ? (
        <div className="text-center py-16 text-slate-400 text-sm">
          暂无{MSG_TABS.find((t) => t.value === tab)?.label}的评论
        </div>
      ) : (
        <div className="space-y-3">
          {list.map((c) => (
            <motion.div
              key={c.id}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className="rounded-2xl bg-white/60 dark:bg-slate-800/60 backdrop-blur-md border border-white/40 dark:border-white/10 p-4"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-slate-700 dark:text-slate-200 whitespace-pre-wrap break-words">
                    {c.content}
                  </p>
                  <p className="mt-2 text-[11px] text-slate-400">
                    {formatTime(c.created_at)}
                    {kind === "post" && typeof c.post_id === "number" ? ` · 文章 #${c.post_id}` : ""}
                    {kind === "chatter" && typeof c.chatter_id === "number" ? ` · 说说 #${c.chatter_id}` : ""}
                    {c.github_user?.login ? ` · GitHub @${c.github_user.login}` : " · 匿名"}
                  </p>
                </div>
                <div className="flex items-center gap-1.5 shrink-0">
                  {tab !== "approved" && (
                    <button
                      type="button"
                      title="通过"
                      disabled={busyId === c.id}
                      onClick={() => act(c.id, "approve")}
                      className="p-1.5 rounded-lg text-emerald-600 hover:bg-emerald-50 dark:hover:bg-emerald-900/20 disabled:opacity-50 transition-colors"
                    >
                      <Check className="w-4 h-4" />
                    </button>
                  )}
                  {tab !== "rejected" && (
                    <button
                      type="button"
                      title="拒绝"
                      disabled={busyId === c.id}
                      onClick={() => act(c.id, "reject")}
                      className="p-1.5 rounded-lg text-amber-600 hover:bg-amber-50 dark:hover:bg-amber-900/20 disabled:opacity-50 transition-colors"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  )}
                  <button
                    type="button"
                    title="删除"
                    disabled={busyId === c.id}
                    onClick={() => {
                      if (window.confirm("确定删除这条评论？")) act(c.id, "delete");
                    }}
                    className="p-1.5 rounded-lg text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 disabled:opacity-50 transition-colors"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ---------------- 小说书架 ---------------- */

function ShelfSection({
  failToLogin,
  onError,
}: {
  failToLogin: (status: number) => boolean;
  onError: (msg: string) => void;
}) {
  const [shelf, setShelf] = useState<Book[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [searching, setSearching] = useState(false);
  const [results, setResults] = useState<SearchBook[]>([]);
  const [busyUrl, setBusyUrl] = useState("");
  const esRef = useRef<EventSource | null>(null);
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 8 } }));

  // 卡片拖拽排序：与前台书架共用同一份 localStorage，两边顺序一致
  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (over && active.id !== over.id) {
      setShelf((items) => {
        const oldIndex = items.findIndex((b) => b.bookUrl === active.id);
        const newIndex = items.findIndex((b) => b.bookUrl === over.id);
        const newOrder = arrayMove(items, oldIndex, newIndex);
        localStorage.setItem(BOOK_ORDER_KEY, JSON.stringify(newOrder.map((b) => b.bookUrl)));
        return newOrder;
      });
    }
  };

  // 预览弹层：目录 + 章节内容
  const [preview, setPreview] = useState<Book | null>(null);
  const [previewChapters, setPreviewChapters] = useState<Chapter[]>([]);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewChapter, setPreviewChapter] = useState<number | null>(null);
  const [previewContent, setPreviewContent] = useState("");
  const [previewContentLoading, setPreviewContentLoading] = useState(false);

  const openPreview = async (book: Book) => {
    setPreview(book);
    setPreviewChapters([]);
    setPreviewChapter(null);
    setPreviewContent("");
    setPreviewLoading(true);
    onError("");
    try {
      const res = await getChapterList(book.bookUrl, book.bookSourceUrl);
      if (res.isSuccess) setPreviewChapters(res.data);
      else onError(res.errorMsg || "获取目录失败");
    } catch {
      onError("获取目录失败");
    } finally {
      setPreviewLoading(false);
    }
  };

  const closePreview = () => {
    setPreview(null);
    setPreviewChapter(null);
    setPreviewContent("");
    setPreviewChapters([]);
  };

  const openPreviewChapter = async (index: number) => {
    if (!preview) return;
    setPreviewChapter(index);
    setPreviewContent("");
    setPreviewContentLoading(true);
    onError("");
    try {
      const res = await getBookContent(preview.bookUrl, index);
      if (res.isSuccess) setPreviewContent(res.data);
      else onError(res.errorMsg || "获取内容失败");
    } catch {
      onError("获取内容失败");
    } finally {
      setPreviewContentLoading(false);
    }
  };

  const loadShelf = async () => {
    try {
      const res = await getBookshelf();
      if (res.isSuccess) setShelf(sortShelfBooks([...res.data]));
    } catch {
      onError("加载书架失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadShelf();
    return () => {
      esRef.current?.close();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const shelfSet = new Set(shelf.map((b) => b.bookUrl));

  const handleSearch = () => {
    const key = query.trim();
    if (!key) return;
    esRef.current?.close();
    setResults([]);
    setSearching(true);
    const es = new EventSource(searchBookMultiSSEUrl(key));
    esRef.current = es;
    es.onmessage = (e) => {
      if (!e.data) return;
      try {
        const parsed = JSON.parse(e.data);
        if (parsed.data) {
          setResults((prev) => {
            const seen = new Set(prev.map((b) => b.bookUrl));
            return [...prev, ...(parsed.data as SearchBook[]).filter((b) => !seen.has(b.bookUrl))];
          });
        }
      } catch {
        /* ignore */
      }
    };
    const stop = () => {
      setSearching(false);
      es.close();
      if (esRef.current === es) esRef.current = null;
    };
    es.addEventListener("end", stop);
    es.onerror = stop;
  };

  const previewFromSearch = (book: SearchBook): Book => ({
    bookUrl: book.bookUrl,
    name: book.name,
    author: book.author,
    coverUrl: book.coverUrl,
    bookSourceUrl: book.origin || ""
  });

  const handleAdd = async (bookUrl: string) => {
    if (shelfSet.has(bookUrl) || busyUrl) return;
    setBusyUrl(bookUrl);
    onError("");
    try {
      const res = await addToBookshelf(bookUrl);
      if (res.isSuccess) {
        await loadShelf();
      } else {
        onError(res.errorMsg || "加入书架失败");
      }
    } catch (err) {
      const status = err instanceof Error && /API Error: (\d+)/.test(err.message)
        ? Number(err.message.match(/API Error: (\d+)/)![1])
        : 0;
      if (!failToLogin(status)) onError("加入书架失败");
    } finally {
      setBusyUrl("");
    }
  };

  const handleRemove = async (book: Book) => {
    if (busyUrl) return;
    if (!window.confirm(`从书架移除《${book.name}》？`)) return;
    setBusyUrl(book.bookUrl);
    onError("");
    try {
      const res = await removeFromBookshelf(book.bookUrl);
      if (res.isSuccess) {
        setShelf((prev) => prev.filter((b) => b.bookUrl !== book.bookUrl));
      } else {
        onError(res.errorMsg || "移除失败");
      }
    } catch (err) {
      const status = err instanceof Error && /API Error: (\d+)/.test(err.message)
        ? Number(err.message.match(/API Error: (\d+)/)![1])
        : 0;
      if (!failToLogin(status)) onError("移除失败");
    } finally {
      setBusyUrl("");
    }
  };

  return (
    <div>
      {/* 搜索加入 */}
      <div className="rounded-2xl bg-white/60 dark:bg-slate-800/60 backdrop-blur-md border border-white/40 dark:border-white/10 p-4 mb-5">
        <p className="text-xs text-slate-500 dark:text-slate-400 mb-2">搜索小说并加入书架</p>
        <div className="flex gap-2">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
            placeholder="输入书名或作者..."
            className="flex-1 px-4 py-2 rounded-xl bg-white/70 dark:bg-slate-900/50 border border-slate-200/60 dark:border-slate-700/60 text-sm text-slate-800 dark:text-slate-200 placeholder-slate-400 outline-none focus:border-sky-400 transition-colors"
          />
          <button
            type="button"
            onClick={handleSearch}
            disabled={searching || !query.trim()}
            className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-sky-500 text-white text-sm hover:bg-sky-600 disabled:opacity-50 transition-colors"
          >
            <Search className="w-4 h-4" />
            搜索
          </button>
        </div>

        {searching && results.length === 0 && (
          <p className="mt-3 text-xs text-slate-400 flex items-center gap-1.5">
            <Loader2 className="w-3.5 h-3.5 animate-spin" /> 搜索中...
          </p>
        )}
        {results.length > 0 && (
          <div className="mt-3 space-y-2 max-h-72 overflow-y-auto">
            {results.map((book) => (
              <div
                key={book.bookUrl}
                onClick={() => openPreview(previewFromSearch(book))}
                className="flex items-center gap-3 py-1.5 px-2 rounded-lg hover:bg-sky-50/60 dark:hover:bg-slate-700/40 cursor-pointer"
              >
                <div className="flex-1 min-w-0">
                  <span className="text-sm text-slate-700 dark:text-slate-200 font-medium">{book.name}</span>
                  <span className="ml-2 text-xs text-slate-400">{book.author}</span>
                </div>
                <button
                  type="button"
                  title="预览（目录/内容）"
                  onClick={(e) => {
                    e.stopPropagation();
                    openPreview(previewFromSearch(book));
                  }}
                  className="p-1.5 rounded-lg text-sky-500 hover:bg-sky-50 dark:hover:bg-sky-900/20 transition-colors"
                >
                  <Eye className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  disabled={shelfSet.has(book.bookUrl) || !!busyUrl}
                  onClick={(e) => {
                    e.stopPropagation();
                    handleAdd(book.bookUrl);
                  }}
                  className={`px-3 py-1 rounded-full text-xs font-medium transition-all ${
                    shelfSet.has(book.bookUrl)
                      ? "bg-emerald-500/10 text-emerald-600 cursor-default"
                      : "bg-sky-500/10 text-sky-600 hover:bg-sky-500 hover:text-white disabled:opacity-50"
                  }`}
                >
                  {busyUrl === book.bookUrl ? "添加中..." : shelfSet.has(book.bookUrl) ? "已在书架" : "加入书架"}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 书架列表 */}
      <p className="text-xs text-slate-500 dark:text-slate-400 mb-2">
        当前书架（{shelf.length} 本）
      </p>
      {loading ? (
        <div className="flex justify-center py-10 text-slate-400">
          <Loader2 className="w-6 h-6 animate-spin" />
        </div>
      ) : shelf.length === 0 ? (
        <div className="text-center py-10 text-slate-400 text-sm">书架为空</div>
      ) : (
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={shelf.map((b) => b.bookUrl)} strategy={rectSortingStrategy}>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
              {shelf.map((book) => (
                <SortableBookCard
                  key={book.bookUrl}
                  book={book}
                  onClick={() => openPreview(book)}
                  extraActions={
                    <>
                      <button
                        type="button"
                        title="预览（目录/内容）"
                        onClick={() => openPreview(book)}
                        className="p-1.5 rounded-lg bg-white/80 dark:bg-slate-900/80 text-sky-500 hover:bg-sky-50 dark:hover:bg-sky-900/40 shadow-sm transition-colors"
                      >
                        <Eye className="w-4 h-4" />
                      </button>
                      <button
                        type="button"
                        disabled={busyUrl === book.bookUrl}
                        onClick={() => handleRemove(book)}
                        className="p-1.5 rounded-lg bg-white/80 dark:bg-slate-900/80 text-red-500 hover:bg-red-50 dark:hover:bg-red-900/40 shadow-sm disabled:opacity-50 transition-colors"
                        title="从书架移除"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </>
                  }
                />
              ))}
            </div>
          </SortableContext>
        </DndContext>
      )}

      {/* 预览弹层：目录 + 章节内容 */}
      {preview && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4"
          data-no-drag="true"
          onClick={(e) => {
            if (e.target === e.currentTarget) closePreview();
          }}
        >
          <div className="w-full max-w-3xl max-h-[85vh] flex flex-col rounded-2xl bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 shadow-2xl overflow-hidden">
            <div className="flex items-center gap-3 px-4 py-3 border-b border-slate-200/70 dark:border-slate-700/70">
              {preview.coverUrl || preview.customCoverUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={proxyCover(preview.customCoverUrl || preview.coverUrl)}
                  alt={preview.name}
                  width={36}
                  height={50}
                  className="rounded object-cover flex-shrink-0"
                />
              ) : null}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-slate-800 dark:text-slate-100 truncate">{preview.name}</p>
                <p className="text-xs text-slate-400 truncate">{preview.author}</p>
              </div>
              {shelfSet.has(preview.bookUrl) ? (
                <span className="px-3 py-1 rounded-lg text-xs font-medium bg-emerald-500/10 text-emerald-600">
                  已在书架
                </span>
              ) : (
                <button
                  type="button"
                  disabled={!!busyUrl}
                  onClick={() => handleAdd(preview.bookUrl)}
                  className="px-3 py-1 rounded-lg text-xs font-medium bg-sky-500/10 text-sky-600 hover:bg-sky-500 hover:text-white disabled:opacity-50 transition-all"
                >
                  {busyUrl === preview.bookUrl ? "添加中..." : "加入书架"}
                </button>
              )}
              {previewChapter !== null && (
                <button
                  type="button"
                  onClick={() => setPreviewChapter(null)}
                  className="px-3 py-1 rounded-lg text-xs font-medium bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
                >
                  返回目录
                </button>
              )}
              <button
                type="button"
                onClick={closePreview}
                title="关闭"
                className="p-1.5 rounded-lg text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-4">
              {previewChapter === null ? (
                previewLoading ? (
                  <div className="flex justify-center py-12 text-slate-400">
                    <Loader2 className="w-6 h-6 animate-spin" />
                  </div>
                ) : previewChapters.length === 0 ? (
                  <div className="text-center py-12 text-slate-400 text-sm">目录为空</div>
                ) : (
                  <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2">
                    {previewChapters.map((chapter) => (
                      <button
                        type="button"
                        key={chapter.index}
                        onClick={() => openPreviewChapter(chapter.index)}
                        className="text-left px-3 py-2 rounded-lg text-sm text-slate-600 dark:text-slate-400 hover:bg-sky-100 dark:hover:bg-sky-900/30 hover:text-sky-600 dark:hover:text-sky-400 transition-colors truncate"
                      >
                        {chapter.title}
                      </button>
                    ))}
                  </div>
                )
              ) : previewContentLoading ? (
                <div className="flex justify-center py-12 text-slate-400">
                  <Loader2 className="w-6 h-6 animate-spin" />
                </div>
              ) : (
                <div>
                  <p className="text-sm font-semibold text-slate-800 dark:text-slate-100 mb-3">
                    {previewChapters[previewChapter]?.title || `第 ${previewChapter + 1} 章`}
                  </p>
                  <div className="text-sm text-slate-600 dark:text-slate-300 whitespace-pre-wrap break-words leading-relaxed">
                    {previewContent || "（无内容）"}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/* ---------------- 站点配置 ---------------- */

function ConfigSection({
  failToLogin,
  onError,
}: {
  failToLogin: (status: number) => boolean;
  onError: (msg: string) => void;
}) {
  const [rows, setRows] = useState<SiteConfigItem[]>([]);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const load = async () => {
    setLoading(true);
    onError("");
    try {
      const data = await adminListSiteConfig();
      setRows(data);
      const init: Record<string, string> = {};
      for (const r of data) init[r.key] = displayValue(r.value);
      setDrafts(init);
    } catch (err) {
      const status = err instanceof Error && /API Error: (\d+)/.test(err.message)
        ? Number(err.message.match(/API Error: (\d+)/)![1])
        : 0;
      if (!failToLogin(status)) onError("加载配置失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const dirty = rows.some((r) => drafts[r.key] !== undefined && drafts[r.key] !== displayValue(r.value));

  const save = async () => {
    if (saving || !dirty) return;
    setSaving(true);
    onError("");
    const changes: Record<string, string> = {};
    for (const r of rows) {
      if (drafts[r.key] !== undefined && drafts[r.key] !== displayValue(r.value)) {
        changes[r.key] = drafts[r.key];
      }
    }
    try {
      await adminUpdateSiteConfig(changes);
      await load();
    } catch (err) {
      const status = err instanceof Error && /API Error: (\d+)/.test(err.message)
        ? Number(err.message.match(/API Error: (\d+)/)![1])
        : 0;
      if (!failToLogin(status)) onError("保存失败，请重试");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center py-16 text-slate-400">
        <Loader2 className="w-6 h-6 animate-spin" />
      </div>
    );
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <p className="text-xs text-slate-500 dark:text-slate-400">修改站点标题、描述、备案信息等</p>
        <button
          type="button"
          onClick={save}
          disabled={saving || !dirty}
          className="flex items-center gap-1.5 px-4 py-1.5 rounded-xl bg-sky-500 text-white text-xs font-medium hover:bg-sky-600 disabled:opacity-50 transition-colors"
        >
          <Save className="w-3.5 h-3.5" />
          {saving ? "保存中..." : "保存修改"}
        </button>
      </div>
      <div className="space-y-3">
        {rows.map((row) => (
          <div
            key={row.key}
            className="rounded-2xl bg-white/60 dark:bg-slate-800/60 backdrop-blur-md border border-white/40 dark:border-white/10 p-4"
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-semibold text-slate-700 dark:text-slate-200">
                {row.description || row.key}
              </span>
              <span className="text-[11px] text-slate-400 font-mono">{row.key}</span>
            </div>
            <input
              type="text"
              value={drafts[row.key] ?? displayValue(row.value)}
              onChange={(e) => setDrafts((prev) => ({ ...prev, [row.key]: e.target.value }))}
              className="w-full px-3 py-2 rounded-lg bg-white/70 dark:bg-slate-900/50 border border-slate-200/60 dark:border-slate-700/60 text-sm text-slate-800 dark:text-slate-200 outline-none focus:border-sky-400 transition-colors"
            />
          </div>
        ))}
      </div>
    </div>
  );
}
