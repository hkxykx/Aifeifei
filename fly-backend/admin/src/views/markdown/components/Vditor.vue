<script setup lang="ts">
import "vditor/dist/index.css";
import Vditor from "vditor";
import { useDark } from "@pureadmin/utils";
import { useIntervalFn } from "@vueuse/core";
import { onMounted, ref, watch, toRaw, onUnmounted } from "vue";
import { getToken, formatToken } from "@/utils/auth";

const emit = defineEmits([
  "update:modelValue",
  "after",
  "focus",
  "blur",
  "esc",
  "ctrlEnter",
  "select"
]);

const props = defineProps({
  options: {
    type: Object,
    default() {
      return {};
    }
  },
  modelValue: {
    type: String,
    default: ""
  }
});

const { isDark } = useDark();
const editor = ref<Vditor | null>(null);
const markdownRef = ref<HTMLElement | null>(null);
const editorReady = ref(false);
let pendingValue: string | null = null;

// 图片上传：对接后端 POST /api/upload/image（@RequiresAuth，需 Bearer 令牌）。
// 后端返回 {url, orientation}，Vditor 默认要求 {data:{succMap:{文件名:url}}}，用 format 转换。
const uploadOptions = {
  url: "/api/upload/image",
  fieldName: "file",
  accept: "image/*,.jpg,.jpeg,.png,.gif,.webp",
  max: 10 * 1024 * 1024,
  setHeaders(): Record<string, string> {
    const token = getToken()?.accessToken;
    return token ? { Authorization: formatToken(token) } : {};
  },
  format(files: File[], responseText: string): string {
    try {
      const res = JSON.parse(responseText);
      if (res?.url) {
        const name = files?.[0]?.name ?? "image.png";
        return JSON.stringify({
          code: 0,
          data: { succMap: { [name]: res.url } }
        });
      }
      return JSON.stringify({
        code: 1,
        msg: res?.detail ?? "上传失败",
        data: { succMap: {}, errFiles: [] }
      });
    } catch {
      return JSON.stringify({
        code: 1,
        msg: "上传响应解析失败",
        data: { succMap: {}, errFiles: [] }
      });
    }
  }
};

onMounted(() => {
  editor.value = new Vditor(markdownRef.value as HTMLElement, {
    ...props.options,
    upload: uploadOptions,
    value: props.modelValue,
    cache: {
      enable: false
    },
    fullscreen: {
      index: 10000
    },
    after() {
      editorReady.value = true;
      if (pendingValue !== null) {
        editor.value?.setValue(pendingValue);
        pendingValue = null;
      }
      emit("after", toRaw(editor.value));
    },
    input(value: string) {
      emit("update:modelValue", value);
    },
    focus(value: string) {
      emit("focus", value);
    },
    blur(value: string) {
      emit("blur", value);
    },
    esc(value: string) {
      emit("esc", value);
    },
    ctrlEnter(value: string) {
      emit("ctrlEnter", value);
    },
    select(value: string) {
      emit("select", value);
    }
  });
});

watch(
  () => props.modelValue,
  newVal => {
    if (!editorReady.value) {
      pendingValue = newVal;
      return;
    }
    if (newVal !== editor.value?.getValue()) {
      editor.value?.setValue(newVal);
    }
  }
);

watch(
  () => isDark.value,
  newVal => {
    const { pause } = useIntervalFn(() => {
      if (editor.value.vditor) {
        newVal
          ? editor.value.setTheme("dark", "dark", "rose-pine")
          : editor.value.setTheme("classic", "light", "github");
        pause();
      }
    }, 20);
  }
);

onUnmounted(() => {
  const editorInstance = editor.value;
  if (!editorInstance) return;
  try {
    editorInstance?.destroy?.();
  } catch (error) {
    console.log(error);
  }
});

defineExpose({
  getVditor: () => editor.value
});
</script>

<template>
  <div ref="markdownRef" />
</template>
