<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { message } from "@/utils/message";
import { deviceDetection } from "@pureadmin/utils";
import {
  listAdminUsers,
  createAdminUser,
  updateAdminUser,
  deleteAdminUser,
  errMsg,
  type AdminUserItem
} from "@/api/admin-user";

defineOptions({
  name: "AccountManagement"
});

/** 管理员账户上限 */
const MAX_ADMINS = 3;

const loading = ref(false);
const list = ref<AdminUserItem[]>([]);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const saving = ref(false);
const form = ref({ username: "", password: "" });

const isEdit = computed(() => editingId.value !== null);
const canCreate = computed(
  () => !loading.value && list.value.length < MAX_ADMINS
);

async function load() {
  loading.value = true;
  try {
    const res = await listAdminUsers();
    if (res.code === 0) list.value = res.data;
  } catch {
    message("加载管理员账户失败", { type: "error" });
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  load();
});

function openCreate() {
  if (!canCreate.value) {
    message(`最多支持 ${MAX_ADMINS} 个管理员账户`, { type: "warning" });
    return;
  }
  editingId.value = null;
  form.value = { username: "", password: "" };
  dialogVisible.value = true;
}

function openEdit(row: AdminUserItem) {
  editingId.value = row.id;
  form.value = { username: row.username, password: "" };
  dialogVisible.value = true;
}

async function save() {
  const username = form.value.username.trim();
  if (username.length < 2) {
    message("用户名至少 2 位", { type: "warning" });
    return;
  }
  if (form.value.password && form.value.password.length < 6) {
    message("密码至少 6 位", { type: "warning" });
    return;
  }
  if (!isEdit.value && !form.value.password) {
    message("请输入密码", { type: "warning" });
    return;
  }
  saving.value = true;
  try {
    if (isEdit.value) {
      const data: { username: string; password?: string } = { username };
      if (form.value.password) data.password = form.value.password;
      await updateAdminUser(editingId.value!, data);
      message("修改成功", { type: "success" });
    } else {
      await createAdminUser({ username, password: form.value.password });
      message("创建成功", { type: "success" });
    }
    dialogVisible.value = false;
    await load();
  } catch (e) {
    message(errMsg(e, "操作失败"), { type: "error" });
  } finally {
    saving.value = false;
  }
}

async function remove(row: AdminUserItem) {
  if (!window.confirm(`删除管理员账户「${row.username}」？`)) return;
  try {
    await deleteAdminUser(row.id);
    message("已删除", { type: "success" });
    await load();
  } catch (e) {
    message(errMsg(e, "删除失败"), { type: "error" });
  }
}
</script>

<template>
  <div :class="['min-w-45', deviceDetection() ? 'max-w-full' : 'max-w-[70%]']">
    <div class="flex-bc my-8!">
      <div>
        <h3 class="m-0!">账户管理</h3>
        <el-text class="mt-1" type="info">
          仅保留账户与密码 · 最多 {{ MAX_ADMINS }} 个管理员 · 当前
          {{ list.length }} 个
        </el-text>
      </div>
      <el-button type="primary" :disabled="!canCreate" @click="openCreate">
        新增管理员
      </el-button>
    </div>

    <pure-table
      v-loading="loading"
      row-key="id"
      table-layout="auto"
      :data="list"
      :columns="[
        { label: '账户', prop: 'username', minWidth: 160 },
        { label: '操作', minWidth: 140, slot: 'operation' }
      ]"
    >
      <template #operation="{ scope }">
        <el-button type="primary" text @click="openEdit(scope.row)">
          修改
        </el-button>
        <el-button type="danger" text @click="remove(scope.row)">
          删除
        </el-button>
      </template>
    </pure-table>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '修改管理员账户' : '新增管理员账户'"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form label-width="60px" @submit.prevent>
        <el-form-item label="账户">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            maxlength="50"
            autocomplete="off"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '留空则不修改密码' : '至少 6 位'"
            maxlength="128"
            autocomplete="new-password"
            @keydown.enter="save"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
