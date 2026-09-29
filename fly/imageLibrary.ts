// 项目图片库：public/images 下的图片（不含博主头像 hong.jpg），用于缺省封面/匿名头像
export const PROJECT_IMAGES: string[] = [
  "/images/1.webp",
  "/images/2.webp",
  "/images/20.webp",
  "/images/34.webp",
  "/images/36.webp",
  "/images/39.webp",
  "/images/41.webp",
  "/images/42.webp",
  "/images/55.webp",
  "/images/57.webp",
  "/images/58.webp",
  "/images/60.webp",
  "/images/61.webp",
  "/images/63.webp",
  "/images/119.webp",
  "/images/121.webp",
  "/images/127.webp",
  "/images/130.webp",
  "/images/133.webp",
  "/images/134.webp",
];

/** 按 id 稳定取一张：同一 id 每次结果一致，不同 id 分布随机 */
export function imageForId(id: number): string {
  return PROJECT_IMAGES[Math.abs(Math.imul(id, 2654435761)) % PROJECT_IMAGES.length];
}

/** 文章封面：后台传了封面就用封面，否则按文章 id 从项目图片里随机取 */
export function coverOrRandom(cover: string | null | undefined, id: number): string {
  const value = cover?.trim();
  return value ? value : imageForId(id);
}

/** 客户端随机取一张（仅用于无数据的占位状态，ssr:false 组件内使用） */
export function randomImage(): string {
  return PROJECT_IMAGES[Math.floor(Math.random() * PROJECT_IMAGES.length)];
}

