import { request } from "./client";

export interface TagItem {
  id: number;
  name: string;
  slug: string;
  post_count: number;
}

export function getTags() {
  return request<TagItem[]>("/api/tags");
}
