import { request } from "./client";
import { adminFetch } from "./adminAuth";
import type { GitHubUser } from "./types";

export interface CommentItem {
  id: number;
  post_id: number;
  parent_id: number | null;
  content: string;
  likes: number;
  status: string;
  created_at: string;
  github_user: GitHubUser | null;
  replies: CommentItem[];
}

export function getPostComments(postId: number) {
  return request<CommentItem[]>(`/api/comments/post/${postId}`);
}

export function createComment(data: {
  post_id: number;
  parent_id?: number;
  content: string;
}) {
  return request<CommentItem>("/api/comments", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export function likeComment(commentId: number, unlike = false) {
  return request<CommentItem>(
    `/api/comments/${commentId}/${unlike ? "unlike" : "like"}`,
    { method: "POST" }
  );
}

// ---- 后台：评论审核 ----

export function adminGetComments(status: string, page = 1, size = 50) {
  return adminFetch(
    `/api/comments/admin?status=${encodeURIComponent(status)}&page=${page}&size=${size}`
  ).then(async (res) => {
    if (!res.ok) throw new Error(`API Error: ${res.status}`);
    return res.json() as Promise<CommentItem[]>;
  });
}

export function adminUpdateCommentStatus(commentId: number, status: string) {
  return adminFetch(`/api/comments/${commentId}/status`, {
    method: "PUT",
    body: JSON.stringify({ status }),
  }).then(async (res) => {
    if (!res.ok) throw new Error(`API Error: ${res.status}`);
    return res.json() as Promise<CommentItem>;
  });
}

export function adminDeleteComment(commentId: number) {
  return adminFetch(`/api/comments/${commentId}`, { method: "DELETE" }).then(async (res) => {
    if (!res.ok) throw new Error(`API Error: ${res.status}`);
    return res.json() as Promise<{ ok: boolean }>;
  });
}
