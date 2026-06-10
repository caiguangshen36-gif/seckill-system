<template>
  <div class="product-list-container">
    <!-- 搜索区域 -->
    <el-form :model="queryForm" inline class="search-form">
      <el-form-item label="商品名称">
        <el-input v-model="queryForm.goodsName" placeholder="请输入商品名称" clearable />
      </el-form-item>
      <el-form-item label="价格区间">
        <el-input-number
          v-model="queryForm.minPrice"
          :min="0"
          :precision="2"
          placeholder="最低价"
          controls-position="right"
          style="width: 130px"
        />
        <span style="margin: 0 8px; color: #999">-</span>
        <el-input-number
          v-model="queryForm.maxPrice"
          :min="0"
          :precision="2"
          placeholder="最高价"
          controls-position="right"
          style="width: 130px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="goodsName" label="商品名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="seckillPrice" label="秒杀价(元)" width="120" align="right">
        <template #default="{ row }">{{ Number(row.seckillPrice).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="stockCount" label="库存" width="100" align="center">
        <template #default="{ row }">
          <span :class="{ 'low-stock': row.stockCount > 0 && row.stockCount <= 10 }">
            {{ row.stockCount }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="活动时间" min-width="200">
        <template #default="{ row }">
          {{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}
        </template>
      </el-table-column>

      <!-- 操作栏 -->
      <el-table-column label="操作" width="130" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            type="danger"
            size="small"
            :loading="buyingId === row.id"
            :disabled="row.stockCount <= 0"
            @click="handleBuy(row)"
          >
            {{ buyingId === row.id ? '抢购中...' : row.stockCount <= 0 ? '已售罄' : '立即抢购' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页器 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="queryForm.pageNum"
        v-model:page-size="queryForm.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { queryProducts } from '@/api/goods.js'
import { createOrder } from '@/api/order.js'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const buyingId = ref(null)

//  查询参数
const queryForm = reactive({
  pageNum: 1,
  pageSize: 10,
  goodsName: '',
  minPrice: undefined,
  maxPrice: undefined,
})

// 格式化时间戳（秒级）
const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp * 1000)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

//  获取列表数据
const fetchList = async () => {
  loading.value = true
  try {
    const res = await queryProducts(queryForm)
    console.log('查询商品列表:', res)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (err) {
    console.error('查询商品列表失败:', err)
    ElMessage.error('查询商品列表失败')
  } finally {
    loading.value = false
  }
}

// 查询：重置到第一页
const handleSearch = () => {
  queryForm.pageNum = 1
  fetchList()
}

// 重置：清空所有条件并回到第一页
const handleReset = () => {
  Object.assign(queryForm, {
    goodsName: '',
    minPrice: undefined,
    maxPrice: undefined,
    pageNum: 1,
    pageSize: 10,
  })
  fetchList()
}

//  切换每页条数时，必须回到第一页
const handleSizeChange = () => {
  queryForm.pageNum = 1
  fetchList()
}

// 立即抢购
const handleBuy = async (row) => {
  if (row.stockCount <= 0) {
    ElMessage.warning('商品已售罄')
    return
  }

  buyingId.value = row.id
  try {
    await createOrder({
      goodsId: row.id,
      orderPrice: row.seckillPrice,
    })
    ElMessage.success('🎉 抢购成功！请在15分钟内完成支付')
    router.push('/orders')
  } catch (err) {
    const msg = err.response?.data?.message || err.message || '抢购失败，请重试'
    ElMessage.error(msg)
  } finally {
    buyingId.value = null
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.product-list-container {
  padding: 20px;
  background: #fff;
  border-radius: 4px;
}
.search-form {
  margin-bottom: 16px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.low-stock {
  color: #f56c6c;
  font-weight: 700;
}
</style>
