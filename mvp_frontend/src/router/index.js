import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Layout from '../views/Layout.vue'
import SeckillGoods from '../views/SeckillGoods.vue'
import Orders from '../views/Orders.vue'
import Goods from '../views/Goods.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: Login,
    },
    {
      path: '/goods',
      name: 'goods',
      component: SeckillGoods,
    },
    {
      path: '/layout',
      name: 'layout',
      component: Layout,
      redirect: '/goods',
      children: [
        {
          path: '/goods',
          name: 'goods',
          component: SeckillGoods,
        },
        {
          path: '/orders',
          name: 'orders',
          component: Orders,
        },
        {
          path: '/goods-management',
          name: 'goods-management',
          component: Goods,
        },
      ],
    },
  ],
})

export default router
