"use client";

import { useState } from "react";

const AVATARS = Array.from({ length: 6 }, (_, i) => `/images/avatar-${i + 1}.jpg`);

/** 随机头像：每次挂载从 avatar-1~6 中随机取一张（与首页一致） */
export default function RandomAvatar({
  className,
  alt = "avatar",
}: {
  className?: string;
  alt?: string;
}) {
  const [src] = useState(
    () => AVATARS[Math.floor(Math.random() * AVATARS.length)]
  );
  return <img src={src} alt={alt} className={className} suppressHydrationWarning />;
}
