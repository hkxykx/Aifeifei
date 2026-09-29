import { http } from "@/utils/http";

export type MusicItem = {
  id: string;
  title: string;
  artist: string;
  album?: string;
  cover?: string;
  src: string;
  duration?: number;
  created_at?: string;
};

/** 本地音乐列表 */
export const getLocalMusics = () => {
  return http.request<MusicItem[]>("get", "/api/music/local");
};

/** 扫描本地音乐目录并导入（不复制文件） */
export const scanLocalMusic = () => {
  return http.request<{
    added: number;
    skipped: number;
    dirs: number;
    missing?: string[];
    message?: string;
  }>("post", "/api/music/scan");
};

/** 上传音频 */
export const uploadMusic = (file: File, title?: string, artist?: string) => {
  const formData = new FormData();
  formData.append("file", file);
  if (title) formData.append("title", title);
  if (artist) formData.append("artist", artist);
  return http.request<MusicItem>("post", "/api/music/upload", {
    data: formData,
    headers: { "Content-Type": false }
  });
};

/** 编辑音乐 */
export const updateMusic = (
  id: string,
  data: { title?: string; artist?: string }
) => {
  return http.request<MusicItem>("put", `/api/music/${id}`, { data });
};

/** 删除音乐 */
export const deleteMusic = (id: string) => {
  return http.request<{ ok: boolean }>("delete", `/api/music/${id}`);
};
