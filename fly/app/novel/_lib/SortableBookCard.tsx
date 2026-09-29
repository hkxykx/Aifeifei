"use client";

import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { proxyCover, type Book } from "./utils";

/**
 * 书架可拖拽排序卡片（前台书架与后台管理书架共用，保证 UI 一致）。
 * - 整卡点击 → onClick（由调用方决定：后台=打开预览看书，前台书架=进入阅读）
 * - extraActions：管理操作按钮组（预览/移除等），悬于卡片右上角，不触发卡片点击与拖拽
 * - data-no-drag：按住卡片用于拖拽排序，不触发页面/容器的抓住拖动滚动
 */
export default function SortableBookCard({
  book,
  onClick,
  extraActions,
}: {
  book: Book;
  onClick: () => void;
  extraActions?: React.ReactNode;
}) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useSortable({
    id: book.bookUrl,
  });

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition: isDragging ? "none" : undefined,
    zIndex: isDragging ? 50 : undefined,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...attributes}
      {...listeners}
      data-no-drag="true"
      onClick={onClick}
      className="relative bg-white/60 dark:bg-slate-800/60 backdrop-blur-md rounded-2xl border border-slate-200/50 dark:border-slate-700/50 overflow-hidden hover:shadow-2xl hover:scale-[1.02] active:scale-95 transition-all duration-700 cursor-grab active:cursor-grabbing group"
    >
      {extraActions && (
        <div
          className="absolute top-2 right-2 z-10 flex gap-1"
          onClick={(e) => e.stopPropagation()}
          onPointerDown={(e) => e.stopPropagation()}
          data-no-drag="true"
        >
          {extraActions}
        </div>
      )}
      <div className="p-5">
        <div className="flex gap-4">
          {book.coverUrl || book.customCoverUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={proxyCover(book.customCoverUrl || book.coverUrl)}
              alt={book.name}
              width={80}
              height={112}
              className="rounded-lg object-cover flex-shrink-0"
            />
          ) : (
            <div className="w-20 h-28 bg-gradient-to-br from-sky-400 to-indigo-500 rounded-lg flex items-center justify-center flex-shrink-0 p-1">
              <span className="text-white text-xs font-medium text-center leading-tight line-clamp-3">{book.name}</span>
            </div>
          )}
          <div className="flex-1 min-w-0">
            <h3 className="text-lg font-semibold text-slate-900 dark:text-white truncate">
              {book.name}
            </h3>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              {book.author}
            </p>
            {book.finished ? (
              <p className="text-xs mt-2 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-100 dark:bg-emerald-900/40 text-emerald-600 dark:text-emerald-300">
                已看完{book.finishedChapterTitle ? `：${book.finishedChapterTitle}` : ""}
              </p>
            ) : book.durChapterTitle ? (
              <p className="text-xs mt-2 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-sky-100 dark:bg-sky-900/40 text-sky-600 dark:text-sky-300 truncate max-w-full">
                <span className="flex-shrink-0">进行中：</span>
                <span className="truncate">
                  {book.durChapterTitle}
                  {book.pageIndex ? `（第 ${book.pageIndex + 1} 页）` : ""}
                </span>
              </p>
            ) : null}
            {book.totalChapterNum && (
              <p className="text-xs text-sky-500 mt-1">
                共 {book.totalChapterNum} 章
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
