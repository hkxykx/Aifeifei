"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import type { Application } from "pixi.js";
import type { Live2DModel } from "pixi-live2d-display";

const CORE_SRC = "/live2d/live2dcubismcore.min.js";
const MODEL_URL = "/live2d/model/fense/fense.model3.json";
const WIDGET_WIDTH = 300;
const WIDGET_HEIGHT = 460;

/** 点击各模块时按路由给出的随机台词 */
const MODULE_LINES: Record<string, string[]> = {
  "/": ["欢迎回来~", "今天想看点什么？", "嘿嘿，又见面啦"],
  "/posts": ["点进去看看吧~", "这篇文章好像很有趣", "写得可好了，去看看？"],
  "/moments": ["看看大家在聊什么~", "今日份的说说来啦", "这条有点好笑，快看"],
  "/messages": ["想说点什么就留言吧~", "给我留句话嘛", "留言区等你哦"],
  "/novel": ["这本看起来不错~", "追更时间到！", "找个好故事读读吧"],
  "/bookmark": ["收藏夹满满当当~", "都是精挑细选的哦", "看看宝藏收藏"],
  "/projects": ["这可是他的得意之作", "项目展示时间~", "进去瞻仰一下？"],
  "/friends": ["去串个门吧~", "朋友们都很有趣哦", "友链逛一逛？"],
  "/photowall": ["都是很好看的照片！", "咔嚓~回忆时刻", "这张拍得真好，进去看看"],
  "/timeline": ["翻翻过去的足迹~", "时间线走一遍", "回忆满满呀"],
  "/music": ["听听歌放松一下~", "这首超好听的", "点开音乐模式"],
  "/about": ["想了解他吗？", "关于我的故事~", "进去认识一下吧"]
};

/** 兜底台词（未匹配到模块 / 点击看板娘自己时） */
const FALLBACK_LINES = [
  "点进去看看吧~",
  "别愣着呀，随便逛逛~",
  "嗯……想去哪？",
  "我在这儿陪你~",
  "随便点点看嘛"
];

/** 看板娘自言自语 */
const IDLE_LINES = [
  "呼……",
  "有点无聊呀~",
  "主人，记得多喝水哦",
  "晒晒太阳真舒服",
  "我一直在哦",
  "……啊，走神了"
];

const pick = <T,>(arr: T[]): T => arr[Math.floor(Math.random() * arr.length)];

function lineForPath(href: string): string {
  const seg = "/" + (href.split("/")[1] || "");
  const pool = MODULE_LINES[seg];
  return pool ? pick(pool) : pick(FALLBACK_LINES);
}

function loadScriptOnce(src: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const core = (window as unknown as { Live2DCubismCore?: unknown })
      .Live2DCubismCore;
    if (core) {
      resolve();
      return;
    }
    const existing = document.querySelector<HTMLScriptElement>(
      `script[src="${src}"]`
    );
    if (existing) {
      existing.addEventListener("load", () => resolve());
      existing.addEventListener("error", () =>
        reject(new Error(`脚本加载失败: ${src}`))
      );
      return;
    }
    const script = document.createElement("script");
    script.src = src;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error(`脚本加载失败: ${src}`));
    document.head.appendChild(script);
  });
}

export default function Live2D() {
  const hostRef = useRef<HTMLDivElement>(null);
  const [hidden, setHidden] = useState(
    () => typeof window !== "undefined" && window.innerWidth < 768
  );
  const [bubble, setBubble] = useState<{ text: string; id: number } | null>(
    null
  );
  const bubbleTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastBubbleAt = useRef(0);

  const showBubble = useCallback((text: string) => {
    const now = Date.now();
    if (now - lastBubbleAt.current < 2000) return;
    lastBubbleAt.current = now;
    setBubble({ text, id: now });
    if (bubbleTimer.current) clearTimeout(bubbleTimer.current);
    bubbleTimer.current = setTimeout(() => setBubble(null), 3600);
  }, []);

  useEffect(() => {
    return () => {
      if (bubbleTimer.current) clearTimeout(bubbleTimer.current);
    };
  }, []);

  useEffect(() => {
    const host = hostRef.current;
    if (!host || hidden) return;

    let disposed = false;
    let app: Application | null = null;
    let model: Live2DModel | null = null;
    let resizeObserver: ResizeObserver | null = null;
    let onClick: (() => void) | null = null;
    let naturalW = 0;
    let naturalH = 0;
    let idleTimer: ReturnType<typeof setTimeout> | null = null;
    let talkTimer: ReturnType<typeof setTimeout> | null = null;

    const fit = () => {
      if (!app || !model || naturalW === 0) return;
      const scale = Math.min(
        app.screen.width / naturalW,
        app.screen.height / naturalH
      );
      model.anchor.set(0, 0);
      model.scale.set(scale);
      model.position.set(
        (app.screen.width - naturalW * scale) / 2,
        app.screen.height - naturalH * scale
      );
    };

    // 点击站内模块链接 → 头顶随机弹一句
    const onDocClick = (event: MouseEvent) => {
      const target = event.target as HTMLElement | null;
      const anchor = target?.closest?.("a[href]") as HTMLAnchorElement | null;
      if (!anchor) return;
      const href = anchor.getAttribute("href") || "";
      if (!href.startsWith("/") || href.startsWith("/admin")) return;
      showBubble(lineForPath(href));
    };

    // 自主行动：随机做动作 / 换表情
    const scheduleIdle = () => {
      idleTimer = setTimeout(
        () => {
          if (!disposed) {
            if (!document.hidden && model) {
              if (Math.random() < 0.25) {
                void model.expression().catch(() => undefined);
              } else {
                void model.motion("").catch(() => undefined);
              }
            }
            scheduleIdle();
          }
        },
        6000 + Math.random() * 8000
      );
    };

    // 偶尔自言自语
    const scheduleTalk = () => {
      talkTimer = setTimeout(
        () => {
          if (!disposed) {
            if (!document.hidden) showBubble(pick(IDLE_LINES));
            scheduleTalk();
          }
        },
        45000 + Math.random() * 45000
      );
    };

    (async () => {
      try {
        await loadScriptOnce(CORE_SRC);
        if (disposed) return;
        const [
          { Application: PixiApplication, Ticker },
          { Live2DModel: Model }
        ] = await Promise.all([
          import("pixi.js"),
          import("pixi-live2d-display/cubism4")
        ]);
        if (disposed) return;

        Model.registerTicker(Ticker);

        const width = host.clientWidth || WIDGET_WIDTH;
        const height = host.clientHeight || WIDGET_HEIGHT;
        const view = document.createElement("canvas");
        view.style.display = "block";
        view.style.width = "100%";
        view.style.height = "100%";
        view.style.pointerEvents = "auto";
        app = new PixiApplication({
          view,
          width,
          height,
          backgroundAlpha: 0,
          antialias: true,
          autoDensity: true,
          resolution: Math.min(window.devicePixelRatio || 1, 2)
        });
        host.appendChild(view);

        model = await Model.from(MODEL_URL, {
          autoInteract: false,
          autoUpdate: true
        });
        if (disposed) {
          model.destroy();
          return;
        }

        naturalW = model.width;
        naturalH = model.height;
        app.stage.addChild(model);
        fit();
        (
          window as unknown as Record<string, unknown>
        ).__flyLive2d = { app, model };

        resizeObserver = new ResizeObserver(() => {
          if (!app) return;
          app.renderer.resize(host.clientWidth, host.clientHeight);
          fit();
        });
        resizeObserver.observe(host);

        onClick = () => {
          void model?.motion("").catch(() => undefined);
          showBubble(pick(FALLBACK_LINES));
        };
        view.addEventListener("click", onClick);

        document.addEventListener("click", onDocClick, true);
        scheduleIdle();
        scheduleTalk();
      } catch (err) {
        console.warn("[Live2D] 看板娘加载失败:", err);
        if (!disposed) setHidden(true);
      }
    })();

    return () => {
      disposed = true;
      if (idleTimer) clearTimeout(idleTimer);
      if (talkTimer) clearTimeout(talkTimer);
      document.removeEventListener("click", onDocClick, true);
      resizeObserver?.disconnect();
      if (app && onClick) {
        const view = app.view as HTMLCanvasElement;
        view.removeEventListener("click", onClick);
      }
      if (app) {
        app.destroy(false, {
          children: true,
          texture: true,
          baseTexture: true
        });
      }
      app = null;
      model = null;
    };
  }, [hidden, showBubble]);

  if (hidden) return null;

  return (
    <>
      <AnimatePresence>
        {bubble && (
          <motion.div
            key={bubble.id}
            initial={{ opacity: 0, y: 10, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 6, scale: 0.95 }}
            transition={{ duration: 0.22, ease: "easeOut" }}
            data-mascot-bubble="1"
            style={{
              position: "fixed",
              right: 36,
              bottom: WIDGET_HEIGHT - 40,
              zIndex: 1001,
              maxWidth: 230,
              pointerEvents: "none"
            }}
          >
            <div className="relative rounded-2xl bg-white/90 dark:bg-slate-800/90 backdrop-blur-md border border-white/60 dark:border-white/15 shadow-xl px-3.5 py-2.5 text-xs leading-relaxed text-slate-700 dark:text-slate-200">
              {bubble.text}
              <span
                className="absolute -bottom-1.5 right-6 w-3 h-3 rotate-45 bg-white/90 dark:bg-slate-800/90 border-b border-r border-white/60 dark:border-white/15"
                aria-hidden
              />
            </div>
          </motion.div>
        )}
      </AnimatePresence>
      <div
        ref={hostRef}
        aria-hidden
        style={{
          position: "fixed",
          right: 12,
          bottom: 0,
          width: WIDGET_WIDTH,
          height: WIDGET_HEIGHT,
          zIndex: 1000,
          pointerEvents: "none",
          overflow: "hidden"
        }}
      />
    </>
  );
}
