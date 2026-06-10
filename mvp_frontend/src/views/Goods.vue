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
        />
        <span style="margin: 0 8px; color: #999">-</span>
        <el-input-number
          v-model="queryForm.maxPrice"
          :min="0"
          :precision="2"
          placeholder="最高价"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryForm.status" placeholder="全部" clearable>
          <el-option label="未开始" :value="0" />
          <el-option label="进行中" :value="1" />
          <el-option label="已结束" :value="2" />
          <el-option label="已下架" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button type="success" icon="Plus" @click="openDialog()">添加商品</el-button>
      </el-form-item>
    </el-form>

    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="goodsName" label="商品名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="seckillPrice" label="秒杀价(元)" width="120" align="right">
        <template #default="{ row }">{{ Number(row.seckillPrice).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="stockCount" label="库存" width="100" align="center" />
      <el-table-column label="活动时间" min-width="200">
        <template #default="{ row }">
          {{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <!-- ✅ 编辑按钮传入当前行数据 -->
          <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除该商品？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button link type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
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
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </div>

    <!-- ✅ 添加/编辑复用弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑商品' : '添加商品'"
      width="600px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="商品名称" prop="goodsName">
          <el-input
            v-model="formData.goodsName"
            placeholder="请输入商品名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="秒杀价格" prop="seckillPrice">
          <el-input-number
            v-model="formData.seckillPrice"
            :min="0.01"
            :precision="2"
            :step="0.01"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="库存数量" prop="stockCount">
          <el-input-number
            v-model="formData.stockCount"
            :min="1"
            :step="1"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="活动开始时间" prop="startTime">
          <el-date-picker
            v-model="formData.startTime"
            type="datetime"
            placeholder="选择开始时间"
            value-format="X"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="活动结束时间" prop="endTime">
          <el-date-picker
            v-model="formData.endTime"
            type="datetime"
            placeholder="选择结束时间"
            value-format="X"
            style="width: 100%"
          />
        </el-form-item>
        <!-- ⚠️ 已移除 status 字段，后端 ProductDto 不包含此字段 -->
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
// ✅ 导入路径修正为 goods.js，并引入 updateProduct
import { queryProducts, deleteProduct, addProduct, updateProduct } from '@/api/goods.js'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

// ==================== 列表查询逻辑 ====================
const queryForm = reactive({
  goodsName: '',
  minPrice: undefined,
  maxPrice: undefined,
  status: undefined,
  pageNum: 1,
  pageSize: 10,
})

const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp * 1000)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const statusText = (status) =>
  ({ 0: '未开始', 1: '进行中', 2: '已结束', 3: '已下架' })[status] || '未知'
const statusTagType = (status) =>
  ({ 0: 'info', 1: 'success', 2: 'warning', 3: 'danger' })[status] || 'danger'

const fetchList = async () => {
  loading.value = true
  try {
    const res = await queryProducts(queryForm)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (err) {
    console.error('查询商品列表失败:', err)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryForm.pageNum = 1
  fetchList()
}
const handleReset = () => {
  Object.assign(queryForm, {
    goodsName: '',
    minPrice: undefined,
    maxPrice: undefined,
    status: undefined,
    pageNum: 1,
    pageSize: 10,
  })
  fetchList()
}
const handleDelete = async (id) => {
  try {
    await deleteProduct(id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    ElMessage.error('删除失败')
  }
}

// ==================== ✅ 添加/编辑弹窗逻辑 ====================
const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const isEdit = ref(false) // 标识当前是新增还是编辑

// ⚠️ 严格对齐后端 ProductDto，不包含 status 和 id（id 仅在编辑时动态注入）
const defaultFormData = {
  goodsName: '',
  seckillPrice: undefined,
  stockCount: undefined,
  startTime: undefined,
  endTime: undefined,
}
const formData = reactive({ ...defaultFormData })

const formRules = {
  goodsName: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  seckillPrice: [{ required: true, message: '请输入秒杀价格', trigger: 'blur' }],
  stockCount: [{ required: true, message: '请输入库存数量', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择活动开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择活动结束时间', trigger: 'change' }],
}

/**
 * 打开弹窗
 * @param {Object} [row] - 传入行数据则为编辑模式，不传则为新增模式
 */
const openDialog = (row) => {
  if (row) {
    // 编辑模式：回填数据（包含 id）
    isEdit.value = true
    Object.assign(formData, {
      id: row.id,
      goodsName: row.goodsName,
      seckillPrice: row.seckillPrice,
      stockCount: row.stockCount,
      startTime: row.startTime,
      endTime: row.endTime,
    })
  } else {
    // 新增模式：重置表单
    isEdit.value = false
    Object.assign(formData, { ...defaultFormData })
  }
  dialogVisible.value = true
}

// 提交新增/编辑
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value) {
        // ✅ 编辑：调用 updateProduct，传递 id + 表单数据
        await updateProduct({ ...formData })
        ElMessage.success('更新商品成功')
      } else {
        // ✅ 新增：调用 addProduct，仅传递表单数据（不含 id）
        await addProduct({ ...formData })
        ElMessage.success('添加商品成功')
      }
      dialogVisible.value = false
      fetchList()
    } catch (err) {
      const msg =
        err.response?.data?.message || (isEdit.value ? '更新失败' : '添加失败') + '，请重试'
      ElMessage.error(msg)
    } finally {
      submitLoading.value = false
    }
  })
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
</style>
