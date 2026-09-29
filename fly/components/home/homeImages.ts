// 首页展示用的项目本地图片（public/images/）
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
  "/images/hong.jpg",
];

/** 首页左侧竖版照片墙预览：只随机这 6 张（想换图改这里，路径对应 public/images/） */
export const PREVIEW_IMAGES: string[] = [
  "/images/1.webp",
  "/images/2.webp",
  "/images/20.webp",
  "/images/34.webp",
  "/images/36.webp",
  "/images/39.webp",
];

export function randomOtherIndex(current: number, length: number): number {
  if (length <= 1) return 0;
  let next = current;
  while (next === current) next = Math.floor(Math.random() * length);
  return next;
}
