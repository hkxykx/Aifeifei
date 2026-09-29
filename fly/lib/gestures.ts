"use client";

import { useEffect, useRef } from "react";

/** 可滚动祖先（overflow auto/scroll 且内容溢出） */
function scrollableAncestor(node: Element | null): HTMLElement | null {
  let el = node as HTMLElement | null;
  while (el && el !== document.body && el !== document.documentElement) {
    const style = window.getComputedStyle(el);
    if (
      (style.overflowY === "auto" || style.overflowY === "scroll") &&
      el.scrollHeight > el.clientHeight + 1
    ) {
      return el;
    }
    el = el.parentElement;
  }
  return null;
}

/** 页面级滚动容器（视口高度的 el-scrollbar wrap 等），区别于局部小列表 */
function isPageLevel(el: HTMLElement): boolean {
  return el.getBoundingClientRect().height > window.innerHeight * 0.65;
}

const INTERACTIVE =
  "a,button,input,textarea,select,option,label,[contenteditable],[role=button],[data-no-drag]";

/**
 * 滚轮平滑滚动（接近原生速度的手感，一次约一行）。
 * - factor：单事件位移系数（120×0.3≈36px≈一行）；LEAD_CAP 限制 target 超前量，
 *   防高速/无极滚轮连续事件把 target 推得过远造成"一次滑一大片"
 * - rAF 每帧向目标插值，消除一格一格的跳步感
 * - 命中内嵌滚动容器时放行原生行为；已被其它 wheel 监听 preventDefault 时跳过
 * - 动画期间若发生外部滚动（触摸/滚动条拖拽）立即让出控制权
 */
export function useSmoothWheel(factor = 0.3) {
  useEffect(() => {
    let raf = 0;
    let running = false;
    let targetY = 0;
    let targetX = 0;
    let lastAppliedY = -1;
    let lastAppliedX = -1;
    const LERP = 0.4;
    const LEAD_CAP = 360;

    const stop = () => {
      running = false;
      cancelAnimationFrame(raf);
    };

    const tick = () => {
      if (lastAppliedY >= 0) {
        // 外部滚动（触摸/拖滚动条/键盘）打断：尊重外部，放弃动画
        if (Math.abs(window.scrollY - lastAppliedY) > 2 || Math.abs(window.scrollX - lastAppliedX) > 2) {
          stop();
          return;
        }
      }
      const curY = window.scrollY;
      const curX = window.scrollX;
      const maxY = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
      const maxX = Math.max(0, document.documentElement.scrollWidth - window.innerWidth);
      const ny = curY + (Math.min(targetY, maxY) - curY) * LERP;
      const nx = curX + (Math.min(targetX, maxX) - curX) * LERP;
      if (Math.abs(targetY - ny) < 0.6 && Math.abs(targetX - nx) < 0.6) {
        window.scrollTo({ top: targetY, left: targetX, behavior: "instant" });
        lastAppliedY = -1;
        running = false;
        return;
      }
      window.scrollTo({ top: ny, left: nx, behavior: "instant" });
      lastAppliedY = ny;
      lastAppliedX = nx;
      raf = requestAnimationFrame(tick);
    };

    const onWheel = (e: WheelEvent) => {
      if (e.ctrlKey || e.defaultPrevented) return;
      const anc = scrollableAncestor(e.target as Element);
      // 局部小列表（目录/搜索结果等）放行原生滚动
      if (anc && !isPageLevel(anc)) return;
      e.preventDefault();
      const unit = e.deltaMode === 1 ? 16 : e.deltaMode === 2 ? window.innerHeight : 1;
      const dy = e.deltaY * unit * factor;
      const dx = e.deltaX * unit * factor;
      if (anc) {
        anc.scrollTop -= dy;
        anc.scrollLeft -= dx;
        return;
      }
      const baseY = running && lastAppliedY >= 0 ? lastAppliedY : window.scrollY;
      const baseX = running && lastAppliedX >= 0 ? lastAppliedX : window.scrollX;
      if (!running) {
        targetY = window.scrollY;
        targetX = window.scrollX;
        lastAppliedY = -1;
        running = true;
        raf = requestAnimationFrame(tick);
      }
      // 限制 target 超前当前滚动位置的距离：惯性滚轮甩动时丢弃过量增量
      targetY = Math.min(Math.max(targetY + dy, baseY - LEAD_CAP), baseY + LEAD_CAP);
      targetX = Math.min(Math.max(targetX + dx, baseX - LEAD_CAP), baseX + LEAD_CAP);
    };

    document.addEventListener("wheel", onWheel, { passive: false });
    return () => {
      document.removeEventListener("wheel", onWheel);
      stop();
    };
  }, [factor]);
}

/**
 * 鼠标抓住页面/内嵌容器拖拽滚动（手机触摸为原生滚动，无需处理）。
 * - 排除可交互元素（链接/按钮/表单/role=button 等，dnd-kit 卡片带 role=button）
 * - 命中内嵌滚动容器时改为拖该容器
 * - 拖动超过阈值后抑制随后的 click，避免误触跳转
 */
export function useDragScroll(disabled?: () => boolean) {
  const disabledRef = useRef(disabled);

  useEffect(() => {
    disabledRef.current = disabled;
  });

  useEffect(() => {
    let active = false;
    let moved = false;
    let startX = 0;
    let startY = 0;
    let startTop = 0;
    let startLeft = 0;
    let container: HTMLElement | null = null;
    let blockClickCleanup: (() => void) | undefined;

    const down = (e: MouseEvent) => {
      if (e.button !== 0 || disabledRef.current?.()) return;
      const target = e.target as Element | null;
      if (!target) return;
      const noDrag = target.closest("[data-no-drag]");
      if (noDrag && !noDrag.contains(scrollableAncestor(target))) return;
      const anc = scrollableAncestor(target);
      // 内嵌可滚容器内任意位置（含按钮）都可拖容器；窗口拖拽排除交互元素
      if (!anc) {
        if (target.closest(INTERACTIVE)) return;
      }
      container = anc;
      startX = e.clientX;
      startY = e.clientY;
      startTop = anc ? anc.scrollTop : window.scrollY;
      startLeft = anc ? anc.scrollLeft : window.scrollX;
      active = true;
      moved = false;
    };

    const move = (e: MouseEvent) => {
      if (!active) return;
      const dx = e.clientX - startX;
      const dy = e.clientY - startY;
      if (!moved) {
        if (Math.abs(dx) < 6 && Math.abs(dy) < 6) return;
        moved = true;
        document.body.classList.add("drag-scrolling");
        window.getSelection()?.removeAllRanges();
      }
      if (container) {
        container.scrollTop = startTop - dy;
        container.scrollLeft = startLeft - dx;
      } else {
        window.scrollTo(startLeft - dx, startTop - dy);
      }
    };

    const blockNextClick = () => {
      const block = (ev: Event) => {
        ev.stopPropagation();
        ev.preventDefault();
      };
      document.addEventListener("click", block, { capture: true });
      const timer = window.setTimeout(() => {
        document.removeEventListener("click", block, { capture: true });
      }, 120);
      blockClickCleanup = () => {
        document.removeEventListener("click", block, { capture: true });
        window.clearTimeout(timer);
      };
    };

    const up = () => {
      if (!active) return;
      active = false;
      if (moved) {
        document.body.classList.remove("drag-scrolling");
        blockNextClick();
      }
      moved = false;
    };

    document.addEventListener("mousedown", down);
    document.addEventListener("mousemove", move);
    document.addEventListener("mouseup", up);
    return () => {
      document.removeEventListener("mousedown", down);
      document.removeEventListener("mousemove", move);
      document.removeEventListener("mouseup", up);
      document.body.classList.remove("drag-scrolling");
      blockClickCleanup?.();
    };
  }, []);
}
