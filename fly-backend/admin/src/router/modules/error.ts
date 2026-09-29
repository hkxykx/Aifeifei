import { $t } from "@/plugins/i18n";
import { error } from "@/router/enums";

export default {
  path: "/error",
  redirect: "/error/403",
  meta: {
    icon: "ri/information-line",
    // 隐藏侧边栏"异常页面"菜单（路由保留：无权限仍会跳 /error/403）
    showLink: false,
    title: $t("menus.pureAbnormal"),
    rank: error
  },
  children: [
    {
      path: "/error/403",
      name: "403",
      component: () => import("@/views/error/403.vue"),
      meta: {
        title: $t("menus.pureAccessDenied")
      }
    },
    {
      path: "/error/404",
      name: "404",
      component: () => import("@/views/error/404.vue"),
      meta: {
        title: $t("menus.purePageNotFound")
      }
    },
    {
      path: "/error/500",
      name: "500",
      component: () => import("@/views/error/500.vue"),
      meta: {
        title: $t("menus.pureServerError")
      }
    }
  ]
} satisfies RouteConfigsTable;
