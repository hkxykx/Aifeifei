<script setup lang="ts">
import { ref, onMounted } from "vue";
import { message } from "@/utils/message";
import {
  getLocalMusics,
  uploadMusic,
  updateMusic,
  deleteMusic,
  scanLocalMusic
} from "@/api/music";
import type { MusicItem } from "@/api/music";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";

defineOptions({ name: "MusicIndex" });

const loading = ref(false);
const dataList = ref<MusicItem[]>([]);
const uploading = ref(false);
const scanning = ref(false);

// 编辑对话框
const dialogVisible = ref(false);
const dialogTitle = ref("编辑音乐");
const formRef = ref();
const form = ref({ id: "", title: "", artist: "" });
const rules = {
  title: [{ required: true, message: "请输入歌曲标题", trigger: "blur" }]
};

function formatDuration(sec?: number) {
  if (!sec || sec <= 0) return "-";
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

const columns: TableColumnList = [
  { label: "ID", prop: "id", width: 70 },
  { label: "标题", prop: "title", minWidth: 180 },
  { label: "歌手", prop: "artist", minWidth: 110 },
  {
    label: "来源",
    prop: "source",
    width: 90,
    slot: "source"
  },
  {
    label: "时长",
    prop: "duration",
    width: 80,
    formatter: ({ duration }) => formatDuration(duration)
  },
  {
    label: "试听",
    prop: "src",
    minWidth: 260,
    slot: "player"
  },
  {
    label: "上传时间",
    prop: "created_at",
    minWidth: 160,
    formatter: ({ created_at }) =>
      created_at?.replace("T", " ").slice(0, 19) ?? ""
  },
  { label: "操作", fixed: "right", width: 160, slot: "operation" }
];

async function onSearch() {
  loading.value = true;
  try {
    dataList.value = await getLocalMusics();
  } finally {
    loading.value = false;
  }
}

async function handleScan() {
  scanning.value = true;
  try {
    const res = await scanLocalMusic();
    if (res.message) {
      message(res.message, { type: "warning" });
    } else {
      const missing =
        res.missing?.length > 0
          ? `；未找到目录: ${res.missing.join("、")}`
          : "";
      message(
        `扫描完成：新增 ${res.added} 首，跳过 ${res.skipped} 首（已导入）${missing}`,
        { type: res.added > 0 ? "success" : "info" }
      );
    }
    await onSearch();
  } catch (e: any) {
    message(e?.message ?? "扫描失败", { type: "error" });
  } finally {
    scanning.value = false;
  }
}

async function handleUpload(uploadFile: any) {
  const raw: File | undefined = uploadFile?.raw;
  if (!raw) return;
  uploading.value = true;
  try {
    const res = await uploadMusic(raw);
    message(`「${res.title}」上传成功`, { type: "success" });
    await onSearch();
  } catch (e: any) {
    message(e?.message ?? "上传失败", { type: "error" });
  } finally {
    uploading.value = false;
  }
}

function openDialog(row: MusicItem) {
  dialogTitle.value = "编辑音乐";
  form.value = { id: String(row.id), title: row.title, artist: row.artist };
  dialogVisible.value = true;
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  try {
    await updateMusic(form.value.id, {
      title: form.value.title,
      artist: form.value.artist
    });
    message("音乐更新成功", { type: "success" });
    dialogVisible.value = false;
    onSearch();
  } catch (e: any) {
    message(e?.message ?? "操作失败", { type: "error" });
  }
}

async function handleDelete(row: MusicItem) {
  try {
    await deleteMusic(String(row.id));
    message("已删除", { type: "success" });
    onSearch();
  } catch (e: any) {
    message(e?.message ?? "删除失败", { type: "error" });
  }
}

onMounted(() => onSearch());
</script>

<template>
  <div class="p-2">
    <el-card shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="font-bold">音乐管理（本地音乐库）</span>
          <div class="flex gap-2">
            <el-button
              :icon="useRenderIcon('ri:folder-add-line')"
              :loading="scanning"
              @click="handleScan"
            >
              扫描本地目录
            </el-button>
            <el-button
              :icon="useRenderIcon('ri:refresh-line')"
              @click="onSearch"
            >
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 上传区域 -->
      <el-upload
        drag
        multiple
        :show-file-list="false"
        :http-request="() => {}"
        :before-upload="() => false"
        :on-change="handleUpload"
        accept="audio/*,.mp3,.flac,.wav,.m4a,.aac,.ogg,.wma"
        class="mb-4"
      >
        <div class="flex flex-col items-center justify-center py-4">
          <el-icon
            v-if="!uploading"
            class="text-3xl text-gray-400 mb-2"
          >
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path
                d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"
              />
            </svg>
          </el-icon>
          <el-icon v-else class="text-3xl text-blue-500 mb-2 is-loading">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path
                d="M12 4V2A10 10 0 0 0 2 12h2a8 8 0 0 1 8-8z"
              />
            </svg>
          </el-icon>
          <span class="text-sm text-gray-500">
            {{ uploading ? "上传中..." : "将音频拖到此处，或点击上传" }}
          </span>
          <span class="text-xs text-gray-400 mt-1">
            支持 mp3 / flac / wav / m4a / aac / ogg / wma，单个最大 100MB
          </span>
        </div>
      </el-upload>

      <pure-table
        :data="dataList"
        :columns="columns"
        :loading="loading"
        align-whole="center"
        row-key="id"
        table-layout="auto"
      >
        <template #source="{ row }">
          <el-tag
            :type="row.source === 'dir' ? 'primary' : 'success'"
            size="small"
          >
            {{ row.source === "dir" ? "本地目录" : "后台上传" }}
          </el-tag>
        </template>

        <template #player="{ row }">
          <audio
            v-if="row.src"
            :src="row.src"
            controls
            preload="none"
            class="h-8 w-full max-w-64"
          />
          <span v-else class="text-gray-400 text-xs">无链接</span>
        </template>

        <template #operation="{ row }">
          <el-button
            link
            type="primary"
            :icon="useRenderIcon('ri:edit-line')"
            @click="openDialog(row)"
          >
            编辑
          </el-button>
          <el-popconfirm
            :title="
              row.source === 'dir'
                ? `确认移除「${row.title}」？（仅移除记录，源文件保留）`
                : `确认删除「${row.title}」？（同时删除文件）`
            "
            @confirm="handleDelete(row)"
          >
            <template #reference>
              <el-button
                link
                type="danger"
                :icon="useRenderIcon('ri:delete-bin-line')"
              >
                删除
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </pure-table>

      <div class="mt-3 text-xs text-gray-400">
        共 {{ dataList.length }} 首 ·
        「本地目录」来自服务器配置的音乐目录（扫描导入，不复制文件）·
        「后台上传」复制到 uploads/music/ ·
        全部会出现在网站「音乐」页播放列表中，与平台歌单合并展示
      </div>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
      >
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="歌曲标题" />
        </el-form-item>
        <el-form-item label="歌手" prop="artist">
          <el-input v-model="form.artist" placeholder="歌手名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
