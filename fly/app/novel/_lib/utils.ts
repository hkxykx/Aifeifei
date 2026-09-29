"use client";

export interface Book {
  bookUrl: string;
  name: string;
  author: string;
  coverUrl?: string;
  customCoverUrl?: string;
  intro?: string;
  durChapterTitle?: string;
  durChapterIndex?: number;
  pageIndex?: number;
  finished?: boolean;
  progressAt?: string;
  finishedChapterTitle?: string;
  totalChapterNum?: number;
  bookSourceUrl?: string;
}

export interface Chapter {
  title: string;
  index: number;
}

export interface SearchBook {
  bookUrl: string;
  name: string;
  author: string;
  coverUrl?: string;
  intro?: string;
  latestChapterTitle?: string;
  origin?: string;
  originName?: string;
}

export function proxyCover(url?: string): string {
  if (!url) return "";
  return `/reader3/cover?path=${encodeURIComponent(url)}`;
}

export function encodeBookUrl(url: string): string {
  return btoa(unescape(encodeURIComponent(url)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

export function decodeBookUrl(encoded: string): string {
  try {
    let str = encoded.replace(/-/g, "+").replace(/_/g, "/");
    while (str.length % 4) str += "=";
    return decodeURIComponent(escape(atob(str)));
  } catch {
    return "";
  }
}

export const READING_SETTINGS_KEY = "novel_reading_settings";

/** 书架自定义排序（前台与后台管理共用同一份 localStorage，排序体验一致） */
export const BOOK_ORDER_KEY = "novel_book_order";

/** 书架排序：本地保存的手动顺序在前，读过进度的书排最前（最近阅读优先） */
export function sortShelfBooks(bookList: Book[]): Book[] {
  const savedOrder = localStorage.getItem(BOOK_ORDER_KEY);
  if (savedOrder) {
    try {
      const order: string[] = JSON.parse(savedOrder);
      bookList.sort((a, b) => {
        const ia = order.indexOf(a.bookUrl);
        const ib = order.indexOf(b.bookUrl);
        return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib);
      });
    } catch { /* ignore */ }
  }
  const withProgress = bookList.filter((b) => !!b.durChapterTitle || b.finished);
  const withoutProgress = bookList.filter((b) => !b.durChapterTitle && !b.finished);
  withProgress.sort((a, b) => String(b.progressAt || "").localeCompare(String(a.progressAt || "")));
  return [...withProgress, ...withoutProgress];
}

export const defaultSettings = {
  fontSize: 18,
  lineHeight: 1.8,
  paragraphSpacing: 16,
  fontFamily: "serif",
  theme: "default",
  customColor: "#f5f0e8",
  contentWidth: "normal",
  clickPaging: false,
};

export type ReadingSettings = typeof defaultSettings;

export function loadSettings(): ReadingSettings {
  if (typeof window !== "undefined") {
    const saved = localStorage.getItem(READING_SETTINGS_KEY);
    if (saved) {
      try { return { ...defaultSettings, ...JSON.parse(saved) }; } catch { /* */ }
    }
  }
  return defaultSettings;
}

export function saveSettings(s: ReadingSettings) {
  localStorage.setItem(READING_SETTINGS_KEY, JSON.stringify(s));
}
