<template>
  <div class="order-list-container">
    <!-- 搜索与筛选 -->
    <el-form inline class="search-form">
      <el-form-item label="订单状态">
        <el-select v-model="queryStatus" placeholder="全部状态" clearable @change="handleSearch">
          <el-option label="待支付" :value="0" />
          <el-option label="已支付" :value="1" />
          <el-option label="已取消" :value="2" />
          <el-option label="已超时" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </el-form-item>
    </el-form>

    <!-- 订单表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="orderNo" label="订单编号" min-width="180" show-overflow-tooltip />
      <el-table-column prop="goodsName" label="商品名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="orderPrice" label="订单金额(元)" width="130" align="right">
        <template #default="{ row }">{{ Number(row.orderPrice).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="下单时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="支付剩余" width="120" align="center">
        <template #default="{ row }">
          <span v-if="row.status === 0 && countdownMap[row.id]" class="countdown-text">
            {{ countdownMap[row.id] }}
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-popconfirm
            v-if="row.status === 0"
            title="确认取消该订单？库存将释放"
            @confirm="handleCancel(row.id)"
          >
            <template #reference>
              <el-button link type="danger" size="small">取消订单</el-button>
            </template>
          </el-popconfirm>
          <span v-else class="no-action">-</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页器 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pageParams.pageNum"
        v-model:page-size="pageParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserOrders, cancelOrder } from '@/api/order'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryStatus = ref(undefined)
const countdownMap = ref({}) // 存储每个待支付订单的倒计时文本
let timer = null

// 分页参数
const pageParams = reactive({ pageNum: 1, pageSize: 10 })

// 格式化时间戳（秒级）
const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  const d = new Date(timestamp * 1000)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// 状态映射
const statusText = (s) => ({ 0: '待支付', 1: '已支付', 2: '已取消', 3: '已超时' })[s] || '未知'
const statusTagType = (s) => ({ 0: 'warning', 1: 'success', 2: 'info', 3: 'danger' })[s] || 'info'

// 计算所有待支付订单的倒计时
const updateCountdown = () => {
  const now = Math.floor(Date.now() / 1000)
  const map = {}
  tableData.value.forEach((item) => {
    if (item.status === 0 && item.payExpireTime) {
      const diff = item.payExpireTime - now
      if (diff > 0) {
        const m = Math.floor(diff / 60)
        const s = diff % 60
        map[item.id] = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
      } else {
        map[item.id] = '已超时'
      }
    }
  })
  countdownMap.value = map
}

// 获取订单列表
const fetchList = async () => {
  loading.value = true
  try {
    const res = await getUserOrders(pageParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
    updateCountdown()
  } catch (err) {
    console.error('获取订单列表失败:', err)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pageParams.pageNum = 1
  fetchList()
}

const handleCancel = async (id) => {
  try {
    await cancelOrder(id)
    ElMessage.success('订单已取消')
    fetchList()
  } catch {
    ElMessage.error('取消失败')
  }
}

onMounted(() => {
  fetchList()
  // 每秒刷新倒计时
  timer = setInterval(updateCountdown, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.order-list-container {
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
.countdown-text {
  color: #e6a23c;
  font-weight: 600;
  font-family: monospace;
}
.no-action {
  color: #c0c4cc;
}
</style>
