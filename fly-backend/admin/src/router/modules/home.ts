import { home } from "@/router/enums";
const Layout = () => import("@/layout/index.vue");

export default {
  path: "/",
  name: "Home",
  component: Layout,
  redirect: "/dashboard/index",
  meta: {
    title: "首页",
    rank: home,
    showLink: false
  },
  children: []
} satisfies RouteConfigsTable;
