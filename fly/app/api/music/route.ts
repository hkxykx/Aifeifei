import { NextRequest, NextResponse } from "next/server";
import Meting from "@meting/core";

interface SongData {
  id: string;
  title: string;
  artist: string;
  cover: string;
  src: string;
  lrcUrl: string;
}

/** 歌单/批量上限：防止单次请求放大发上游调用（网易云配额保护） */
const MAX_PLAYLIST_TRACKS = 200;
const MAX_IDS = 100;
/** URL/封面批量获取的并发度（原为无上限 Promise.all） */
const URL_BATCH_SIZE = 10;
/** 结果缓存：同参数 1 小时内只打一次上游 */
const CACHE_TTL = 60 * 60 * 1000;
const cache = new Map<string, { data: SongData[]; expires: number }>();
const MAX_CACHE_KEYS = 50;

async function mapBatched<T, R>(items: T[], size: number, fn: (item: T) => Promise<R>): Promise<R[]> {
  const out: R[] = [];
  for (let i = 0; i < items.length; i += size) {
    const batch = items.slice(i, i + size);
    out.push(...(await Promise.all(batch.map(fn))));
  }
  return out;
}

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const playlistId = searchParams.get("id");
  const songIds = searchParams.get("ids");
  const keyword = searchParams.get("search");

  if (!playlistId && !songIds && !keyword) {
    return NextResponse.json(
      { error: "需要提供 id (歌单ID)、ids (歌曲ID) 或 search (搜索关键词)" },
      { status: 400 }
    );
  }

  const cacheKey = searchParams.toString();
  const hit = cache.get(cacheKey);
  if (hit && hit.expires > Date.now()) {
    return NextResponse.json(hit.data);
  }

  const meting = new Meting("netease");
  meting.format(true);

  try {
    let tracks: { id: string; name: string; artist: string[]; pic_id: string; url_id: string; lyric_id: string }[] = [];

    if (keyword) {
      const raw = await meting.search(keyword);
      const parsed = JSON.parse(raw as string);
      tracks = (Array.isArray(parsed) ? parsed : []).slice(0, 20);
    } else if (playlistId) {
      const raw = await meting.playlist(playlistId);
      const parsed = JSON.parse(raw as string);
      tracks = (Array.isArray(parsed) ? parsed : []).slice(0, MAX_PLAYLIST_TRACKS);
    } else if (songIds) {
      const ids = songIds
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean)
        .slice(0, MAX_IDS);
      const results = await mapBatched(ids, URL_BATCH_SIZE, async (id) => {
        try {
          const raw = await meting.song(id);
          const parsed = JSON.parse(raw as string);
          return Array.isArray(parsed) ? parsed : [parsed];
        } catch {
          return [];
        }
      });
      tracks = results.flat();
    }

    // 分批获取 URL/封面（并发受控）
    const songs: SongData[] = await mapBatched(tracks, URL_BATCH_SIZE, async (track) => {
      let src = "";
      try {
        const urlRaw = await meting.url(track.url_id, 320);
        const urlData = JSON.parse(urlRaw as string);
        src = (urlData.url || "").replace(/^http:\/\//, "https://");
      } catch {
        // ignore
      }

      let cover = "";
      try {
        const picRaw = await meting.pic(track.pic_id, 300);
        const picData = JSON.parse(picRaw as string);
        cover = (picData.url || "").replace(/^http:\/\//, "https://");
      } catch {
        // ignore
      }

      return {
        id: String(track.id),
        title: track.name || "未知歌曲",
        artist: Array.isArray(track.artist) ? track.artist.join(", ") : String(track.artist || "未知歌手"),
        cover,
        src,
        lrcUrl: track.lyric_id ? `https://api.injahow.cn/meting/?server=netease&type=lrc&id=${track.lyric_id}` : "",
      };
    });

    const result = songs.filter((s) => s.src);
    if (result.length > 0) {
      if (cache.size >= MAX_CACHE_KEYS) {
        const oldest = cache.keys().next().value;
        if (oldest !== undefined) cache.delete(oldest);
      }
      cache.set(cacheKey, { data: result, expires: Date.now() + CACHE_TTL });
    }
    return NextResponse.json(result);
  } catch (err) {
    console.error("Meting error:", err);
    return NextResponse.json(
      { error: "获取音乐数据失败" },
      { status: 500 }
    );
  }
}
