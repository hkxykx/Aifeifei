<script setup lang="ts">
import { getTopMenu } from "@/router/utils";

defineProps({
  collapse: Boolean
});

const AVATAR_COUNT = 6;
const logoUrl = `${import.meta.env.BASE_URL}avatar-${
  Math.floor(Math.random() * AVATAR_COUNT) + 1
}.jpg`;
</script>

<template>
  <div class="sidebar-logo-container" :class="{ collapses: collapse }">
    <transition name="sidebarLogoFade">
      <router-link
        v-if="collapse"
        key="collapse"
        title="Aifeifei"
        class="sidebar-logo-link"
        :to="getTopMenu()?.path ?? '/'"
      >
        <img :src="logoUrl" alt="Aifeifei" />
        <span class="sidebar-title">Aifeifei</span>
      </router-link>
      <router-link
        v-else
        key="expand"
        title="Aifeifei"
        class="sidebar-logo-link"
        :to="getTopMenu()?.path ?? '/'"
      >
        <img :src="logoUrl" alt="Aifeifei" />
        <span class="sidebar-title">Aifeifei</span>
      </router-link>
    </transition>
  </div>
</template>

<style lang="scss" scoped>
.sidebar-logo-container {
  position: relative;
  width: 100%;
  height: 48px;
  overflow: hidden;

  .sidebar-logo-link {
    display: flex;
    flex-wrap: nowrap;
    align-items: center;
    height: 100%;
    padding-left: 10px;

    img {
      display: inline-block;
      height: 32px;
    }

    .sidebar-title {
      display: inline-block;
      height: 32px;
      margin: 2px 0 0 12px;
      overflow: hidden;
      text-overflow: ellipsis;
      font-size: 18px;
      font-weight: 600;
      line-height: 32px;
      color: var(--pure-theme-sub-menu-active-text);
      white-space: nowrap;
    }
  }
}
</style>
