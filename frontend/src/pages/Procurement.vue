<template>
  <div class="space-y-6">
    <section v-if="isSupplierRole && supplierDashboard" class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">供应商待办看板</h3>
          <p class="mt-1 text-xs text-on-surface-variant">仅展示与当前供应商账号相关的待处理采购单与推荐原材料。</p>
        </div>
        <button class="text-xs text-primary font-semibold" @click="loadDashboard">刷新</button>
      </div>
      <div class="p-5 space-y-5">
        <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div class="rounded-xl border border-outline-variant/20 bg-slate-50 p-4">
            <div class="text-xs text-on-surface-variant">供应商</div>
            <div class="mt-2 text-lg font-bold">{{ supplierDashboard.supplierName || '-' }}</div>
          </div>
          <div class="rounded-xl border border-outline-variant/20 bg-amber-50 p-4">
            <div class="text-xs text-on-surface-variant">待确认订单</div>
            <div class="mt-2 text-lg font-bold text-amber-700">{{ supplierDashboard.pendingConfirmCount || 0 }}</div>
          </div>
          <div class="rounded-xl border border-outline-variant/20 bg-blue-50 p-4">
            <div class="text-xs text-on-surface-variant">待发货订单</div>
            <div class="mt-2 text-lg font-bold text-blue-700">{{ supplierDashboard.acceptedPendingShipCount || 0 }}</div>
          </div>
          <div class="rounded-xl border border-outline-variant/20 bg-emerald-50 p-4">
            <div class="text-xs text-on-surface-variant">进行中订单</div>
            <div class="mt-2 text-lg font-bold text-emerald-700">{{ supplierDashboard.totalOpenOrders || 0 }}</div>
          </div>
        </div>

        <div class="grid grid-cols-1 xl:grid-cols-2 gap-5">
          <div class="rounded-xl border border-outline-variant/20 p-4">
            <div class="flex items-center justify-between gap-3">
              <h4 class="text-sm font-bold tracking-tight">{{ isSupplierRole ? '待处理供货单' : '待办采购单' }}</h4>
              <span class="text-xs text-on-surface-variant">共 {{ supplierDashboard.todoOrders?.length || 0 }} 条</span>
            </div>
            <div v-if="!(supplierDashboard.todoOrders || []).length" class="mt-4 text-sm text-on-surface-variant">暂无待处理单据。</div>
            <ul v-else class="mt-4 space-y-3 text-sm">
              <li v-for="todo in supplierDashboard.todoOrders" :key="todo.id" class="rounded-lg border border-outline-variant/20 px-3 py-3 bg-slate-50">
                <div class="flex items-center justify-between gap-3">
                  <span class="font-semibold">{{ todo.poNo }}</span>
                  <span class="text-xs text-primary">{{ todo.status }}</span>
                </div>
                <div class="mt-2 text-xs text-on-surface-variant">{{ todo.itemsSummary }}</div>
                <div class="mt-2 text-xs text-on-surface-variant">总额：¥{{ formatAmount(todo.totalAmount) }}</div>
              </li>
            </ul>
          </div>

          <div class="rounded-xl border border-outline-variant/20 p-4">
            <div class="flex items-center justify-between gap-3">
              <h4 class="text-sm font-bold tracking-tight">我的原材料</h4>
              <span class="text-xs text-on-surface-variant">仅展示当前供应商可供货原材料</span>
            </div>
            <div v-if="!(supplierDashboard.recommendedMaterials || []).length" class="mt-4 text-sm text-on-surface-variant">暂无可供货原材料。</div>
            <table v-else class="mt-4 w-full text-sm">
              <thead class="text-xs text-on-surface-variant">
                <tr class="text-left">
                  <th class="pb-2">SKU</th>
                  <th class="pb-2">名称</th>
                  <th class="pb-2">规格</th>
                  <th class="pb-2">安全库存</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="material in supplierDashboard.recommendedMaterials"
                  :key="material.id"
                  class="border-t border-outline-variant/20 cursor-pointer hover:bg-slate-50"
                  @click="selectRawMaterial(material.id)"
                >
                  <td class="py-3 font-semibold">{{ material.sku }}</td>
                  <td class="py-3">{{ material.name }}</td>
                  <td class="py-3">{{ material.specification || '-' }}</td>
                  <td class="py-3">{{ formatNumber(material.safetyStock) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">{{ isSupplierRole ? '我的原材料' : '原材料档案' }}</h3>
          <p class="mt-1 text-xs text-on-surface-variant">{{ isSupplierRole ? '仅展示当前供应商名下原材料，并支持维护自己的原材料数据。' : '统一展示原材料详情，并支持按角色进行手工维护与 Excel 批量导入。' }}</p>
        </div>
        <div class="flex items-center gap-3">
          <button class="text-xs text-primary font-semibold" @click="downloadTemplate">下载 Excel 模板</button>
          <button class="text-xs text-primary font-semibold" @click="loadRawMaterials">刷新</button>
        </div>
      </div>
      <div class="p-5">
        <div class="mb-4 grid grid-cols-1 md:grid-cols-5 gap-3">
          <input v-model="rawMaterialFilter.keyword" placeholder="搜索 SKU / 名称 / 分类 / 供应商 / 规格" class="rounded border border-outline-variant/40 px-3 py-2 text-sm md:col-span-2" @keyup.enter="loadRawMaterials" />
          <input v-model="rawMaterialFilter.startDate" type="date" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
          <input v-model="rawMaterialFilter.endDate" type="date" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
          <div class="flex items-center gap-3">
            <button class="rounded border border-primary text-primary px-3 py-2 text-sm font-semibold" @click="loadRawMaterials">筛选</button>
            <button class="text-xs text-on-surface-variant" @click="resetRawMaterialFilter">重置</button>
          </div>
        </div>
        <div v-if="showRawMaterialError" class="mb-3 text-xs text-error">{{ rawMaterialError }}</div>
        <div v-if="rawMaterials.length === 0" class="text-sm text-on-surface-variant">{{ rawMaterialEmptyText }}</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">SKU</th>
              <th class="pb-2">名称</th>
              <th class="pb-2">分类</th>
              <th class="pb-2">规格</th>
              <th class="pb-2">单位</th>
              <th class="pb-2">单价</th>
              <th class="pb-2">首选供应商</th>
              <th class="pb-2">创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="material in rawMaterials"
              :key="material.id"
              class="border-t border-outline-variant/20 cursor-pointer hover:bg-slate-50"
              @click="selectRawMaterial(material.id)"
            >
              <td class="py-3 font-semibold">{{ material.sku }}</td>
              <td class="py-3">{{ material.name }}</td>
              <td class="py-3">{{ material.materialCategory || '-' }}</td>
              <td class="py-3">{{ material.specification || '-' }}</td>
              <td class="py-3">{{ material.unit || '-' }}</td>
              <td class="py-3">¥{{ formatAmount(material.unitPrice) }}</td>
              <td class="py-3">{{ material.preferredSupplier || '-' }}</td>
              <td class="py-3">{{ formatDate(material.createdAt) }}</td>
            </tr>
          </tbody>
        </table>

        <div v-if="selectedMaterial" class="mt-5 rounded-xl border border-outline-variant/20 bg-slate-50 p-4">
          <div class="flex items-center justify-between gap-3">
            <div>
              <h4 class="text-sm font-bold tracking-tight">原材料详情</h4>
              <p class="mt-1 text-xs text-on-surface-variant">当前查看：{{ selectedMaterial.name }}（{{ selectedMaterial.sku }}）</p>
            </div>
            <button class="text-xs text-primary font-semibold" @click="selectRawMaterial(selectedMaterial.id)">刷新详情</button>
          </div>
          <div class="mt-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3 text-sm">
            <div><span class="text-on-surface-variant">SKU：</span>{{ selectedMaterial.sku || '-' }}</div>
            <div><span class="text-on-surface-variant">名称：</span>{{ selectedMaterial.name || '-' }}</div>
            <div><span class="text-on-surface-variant">分类：</span>{{ selectedMaterial.materialCategory || '-' }}</div>
            <div><span class="text-on-surface-variant">规格：</span>{{ selectedMaterial.specification || '-' }}</div>
            <div><span class="text-on-surface-variant">单位：</span>{{ selectedMaterial.unit || '-' }}</div>
            <div><span class="text-on-surface-variant">单价：</span>¥{{ formatAmount(selectedMaterial.unitPrice) }}</div>
            <div><span class="text-on-surface-variant">安全库存：</span>{{ formatNumber(selectedMaterial.safetyStock) }}</div>
            <div><span class="text-on-surface-variant">供货周期：</span>{{ selectedMaterial.leadTimeDays ?? 0 }} 天</div>
            <div><span class="text-on-surface-variant">原产地：</span>{{ selectedMaterial.origin || '-' }}</div>
            <div class="md:col-span-2 xl:col-span-3"><span class="text-on-surface-variant">首选供应商：</span>{{ selectedMaterial.preferredSupplier || '-' }}</div>
            <div class="md:col-span-2 xl:col-span-3"><span class="text-on-surface-variant">描述：</span>{{ selectedMaterial.description || '-' }}</div>
          </div>
        </div>
      </div>
    </section>


    <section v-if="canCreateProcurement" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h3 class="text-sm font-bold tracking-tight">库存预警采购申请</h3>
      <div class="mt-3 text-xs text-slate-500">仓库管理员发起的原材料补采申请会在此展示，采购管理员可一键带入采购单；带入并下单后，来源申请会自动从待处理队列中移除。</div>
      <div class="mt-4" v-if="purchaseRequests.length === 0">
        <div class="text-sm text-on-surface-variant">当前没有待处理采购申请。</div>
      </div>
      <table v-else class="mt-4 w-full text-sm">
        <thead class="text-xs text-on-surface-variant">
          <tr class="text-left">
            <th class="pb-2">申请单号</th>
            <th class="pb-2">原材料</th>
            <th class="pb-2">申请数量</th>
            <th class="pb-2">仓库管理员</th>
            <th class="pb-2">状态</th>
            <th class="pb-2">备注</th>
            <th class="pb-2">时间</th>
            <th class="pb-2">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="request in purchaseRequests" :key="request.id" class="border-t border-outline-variant/20">
            <td class="py-3 font-semibold">{{ request.requestNo }}</td>
            <td class="py-3">{{ request.product?.name || request.product?.sku || '-' }}</td>
            <td class="py-3">{{ formatNumber(request.requestedQuantity) }}</td>
            <td class="py-3">{{ request.requestedByName || request.requestedBy || '-' }}</td>
            <td class="py-3">
              <span class="rounded-full bg-amber-50 px-2 py-1 text-[11px] font-semibold text-amber-700">{{ request.status || 'OPEN' }}</span>
            </td>
            <td class="py-3">{{ request.notes || '-' }}</td>
            <td class="py-3">{{ formatDate(request.requestDate) }}</td>
            <td class="py-3">
              <button class="text-xs text-primary" @click="applyPurchaseRequest(request)">
                {{ selectedPurchaseRequestIds.includes(request.id) ? '已带入采购单' : '带入采购单' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="canCreateProcurement" class="bg-white rounded-lg border border-outline-variant/10 p-5">
      <h3 class="text-sm font-bold tracking-tight">采购下单</h3>
      <form class="mt-4 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4" @submit.prevent="handleCreateOrder">
        <div class="rounded border border-dashed border-outline-variant/40 px-3 py-2 text-sm text-on-surface-variant bg-slate-50">
          采购单号与创建人将于提交后自动生成/绑定当前账号
        </div>
        <select v-model="orderForm.supplierId" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required>
          <option value="">{{ availableSuppliers.length ? '请选择供应商' : '暂无可选供应商' }}</option>
          <option v-for="supplier in availableSuppliers" :key="supplier.id" :value="String(supplier.id)">{{ supplier.name }}（{{ supplier.code }}）</option>
        </select>
        <div class="rounded border border-dashed border-outline-variant/40 px-3 py-2 text-sm text-on-surface-variant bg-slate-50">当前创建人：系统自动记录</div>
        <input v-model="orderForm.procurementNote" placeholder="采购备注（可选）" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />

        <select v-model="itemForm.productId" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required>
          <option value="">请选择原材料</option>
          <option v-for="material in rawMaterials" :key="material.id" :value="String(material.id)">{{ material.name }}（{{ material.sku }}）</option>
        </select>
        <input v-model.number="itemForm.quantity" type="number" min="0.01" step="0.01" placeholder="采购数量" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <input v-model.number="itemForm.unitPrice" type="number" min="0" step="0.01" placeholder="采购单价" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" required />
        <div class="flex gap-3">
          <button type="button" class="rounded border border-primary text-primary px-3 py-2 text-sm font-semibold" @click="addItem">添加明细</button>
          <button class="rounded bg-primary text-white px-3 py-2 text-sm font-semibold">提交采购单</button>
        </div>
      </form>
      <div v-if="selectedRequestTags.length" class="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
        <p class="text-xs font-semibold text-amber-800">当前已关联采购申请（{{ selectedRequestTags.length }} 条）</p>
        <div class="mt-2 flex flex-wrap gap-2 text-xs">
          <span v-for="request in selectedRequestTags" :key="request.id" class="inline-flex items-center gap-2 rounded-full bg-white px-3 py-1 text-amber-800 border border-amber-200">
            {{ request.requestNo }} / {{ request.product?.name || request.product?.sku || '-' }} × {{ formatNumber(request.requestedQuantity) }}
            <button type="button" class="text-amber-600" @click="removeSelectedPurchaseRequest(request.id)">移除</button>
          </span>
        </div>
      </div>
      <div class="mt-4" v-if="formItems.length">
        <p class="text-xs text-on-surface-variant">当前采购明细：</p>
        <ul class="text-xs mt-2 space-y-1">
          <li v-for="(item, index) in formItems" :key="`${item.product.id}-${index}`">
            <div class="flex flex-wrap items-center gap-2">
              <span>{{ item.product.name }}（{{ item.product.sku }}） | 供应商 {{ item.preferredSupplier || '-' }} | 数量 {{ formatNumber(item.quantity) }} | 单价 ¥{{ formatAmount(item.unitPrice) }}</span>
              <button type="button" class="text-red-600" @click="removeItem(index)">移除</button>
            </div>
          </li>
        </ul>
      </div>
      <div v-if="!availableSuppliers.length" class="mt-3 text-xs text-amber-600">{{ supplierHint }}</div>
      <div v-if="orderMessage" class="mt-3 text-xs text-emerald-600">{{ orderMessage }}</div>
      <div v-if="orderError" class="mt-3 text-xs text-error">{{ orderError }}</div>
    </section>

    <section class="bg-white rounded-lg border border-outline-variant/10">
      <div class="p-5 border-b border-surface-container-low flex items-center justify-between gap-4">
        <div>
          <h3 class="text-sm font-bold tracking-tight">{{ isSupplierRole ? '供应记录' : '采购记录' }}</h3>
          <p class="mt-1 text-xs text-on-surface-variant">{{ isSupplierRole ? '展示当前供应商的供货单进度与处理状态。' : '按角色展示采购单进度：采购管理员跟踪全流程，供应商处理接单与发货。' }}</p>
        </div>
        <div class="flex items-center gap-3">
          <button v-if="canExportProcurement" class="text-xs text-emerald-700 font-semibold" @click="exportOrders('csv')">导出 CSV</button>
          <button v-if="canExportProcurement" class="text-xs text-emerald-700 font-semibold" @click="exportOrders('xlsx')">导出 Excel</button>
          <button class="text-xs text-primary font-semibold" @click="loadOrders">刷新</button>
        </div>
      </div>
      <div class="p-5">
        <div class="mb-4 grid grid-cols-1 md:grid-cols-5 gap-3">
          <input v-model="orderFilter.keyword" :placeholder="isSupplierRole ? '搜索供货单号 / 原材料 / 状态' : '搜索采购单号 / 供应商 / 原材料 / 状态'" class="rounded border border-outline-variant/40 px-3 py-2 text-sm md:col-span-2" @keyup.enter="loadOrders" />
          <select v-model="orderFilter.status" class="rounded border border-outline-variant/40 px-3 py-2 text-sm">
            <option value="">全部状态</option>
            <option v-for="status in purchaseStatuses" :key="status" :value="status">{{ status }}</option>
          </select>
          <input v-model="orderFilter.startDate" type="date" class="rounded border border-outline-variant/40 px-3 py-2 text-sm" />
          <div class="flex items-center gap-3">
            <button class="rounded border border-primary text-primary px-3 py-2 text-sm font-semibold" @click="loadOrders">筛选</button>
            <button class="text-xs text-on-surface-variant" @click="resetOrderFilter">重置</button>
          </div>
        </div>
        <div v-if="showPurchaseError" class="mb-3 text-xs text-error">{{ purchaseError }}</div>
        <div v-if="purchaseOrders.length === 0" class="text-sm text-on-surface-variant">{{ purchaseOrderEmptyText }}</div>
        <table v-else class="w-full text-sm">
          <thead class="text-xs text-on-surface-variant">
            <tr class="text-left">
              <th class="pb-2">采购单号</th>
              <th class="pb-2">供应商</th>
              <th class="pb-2">状态</th>
              <th class="pb-2">原材料</th>
              <th class="pb-2">总额</th>
              <th class="pb-2">采购管理员ID</th>
              <th class="pb-2">采购管理员</th>
              <th class="pb-2">下单时间</th>
              <th class="pb-2">发货时间</th>
              <th class="pb-2">入库时间</th>
              <th class="pb-2">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in purchaseOrders" :key="order.id" class="border-t border-outline-variant/20 align-top">
              <td class="py-3 font-semibold">{{ order.poNo }}</td>
              <td class="py-3">{{ order.supplier?.name || '-' }}</td>
              <td class="py-3">{{ order.status }}</td>
              <td class="py-3">{{ summarizeItems(order.items) }}</td>
              <td class="py-3">¥{{ formatAmount(order.totalAmount) }}</td>
              <td class="py-3">{{ order.createdBy || '-' }}</td>
              <td class="py-3">{{ order.createdByName || '-' }}</td>
              <td class="py-3">{{ formatDate(order.orderDate) }}</td>
              <td class="py-3">{{ formatDate(order.shippedAt) }}</td>
              <td class="py-3">{{ formatDate(order.receivedAt) }}</td>
              <td class="py-3">
                <div class="flex flex-wrap gap-2">
                  <button
                    v-if="canSupplierAct && order.status === '待供应商确认'"
                    class="text-xs text-emerald-700"
                    @click="handleSupplierDecision(order.id, 'ACCEPT')"
                  >
                    接受
                  </button>
                  <button
                    v-if="canSupplierAct && order.status === '待供应商确认'"
                    class="text-xs text-red-600"
                    @click="handleSupplierDecision(order.id, 'REJECT')"
                  >
                    拒绝
                  </button>
                  <button
                    v-if="canSupplierAct && order.status === '供应商已接单'"
                    class="text-xs text-primary"
                    @click="handleSupplierShip(order.id)"
                  >
                    发货
                  </button>
                  <button
                    v-if="canNotifyWarehouse && order.status === '供应商已发货'"
                    class="text-xs text-primary"
                    @click="handleNotifyWarehouse(order.id)"
                  >
                    通知仓库管理员
                  </button>
                  <span v-if="!canSupplierAct && !canNotifyWarehouse" class="text-xs text-on-surface-variant">只读</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="workflowMessage" class="px-5 pb-4 text-xs text-emerald-600">{{ workflowMessage }}</div>
      <div v-if="workflowError" class="px-5 pb-4 text-xs text-error">{{ workflowError }}</div>
    </section>

    <WeeklyGantt
      v-if="!isSupplierRole"
      title="采购周甘特概览"
      :items="procurementGanttItems"
      empty-text="当前没有可用于统计的采购记录。"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { procurementApi } from '../api/services.js';
import { useAuthStore } from '../store/auth.js';
import { useRealtimeStore } from '../store/realtime.js';
import WeeklyGantt from '../components/WeeklyGantt.vue';

const auth = useAuthStore();
const realtime = useRealtimeStore();

const isSupplierRole = computed(() => auth.state.role === 'ROLE_SUPPLIER');
const canCreateProcurement = computed(() => auth.hasPermission('procurement:create'));
const canManageRawMaterials = computed(() => auth.hasPermission('procurement:raw-material-manage'));
const canSupplierAct = computed(() => auth.hasPermission('procurement:supplier-act'));
const canNotifyWarehouse = computed(() => auth.hasPermission('procurement:notify-warehouse'));
const canExportProcurement = computed(() => auth.hasPermission('procurement:view'));
const rawMaterialEmptyText = computed(() => (isSupplierRole.value ? '当前没有原材料。' : '当前没有原材料记录。'));
const purchaseOrderEmptyText = computed(() => (isSupplierRole.value ? '当前没有供应记录。' : '当前没有采购记录。'));
const showRawMaterialError = computed(() => Boolean(rawMaterialError.value) && !isSupplierRole.value);
const showPurchaseError = computed(() => Boolean(purchaseError.value) && !isSupplierRole.value);

const templateColumns = [
  { name: 'sku', label: '选填，留空时由系统自动生成原材料编号；如需按编号更新原材料，可填写已有编号' },
  { name: 'name', label: '必填，原材料名称' },
  { name: 'materialCategory', label: '可选，原材料分类，如钢材/涂料/辅料' },
  { name: 'specification', label: '可选，规格型号' },
  { name: 'unit', label: '可选，单位，如 kg / m / 件' },
  { name: 'unitPrice', label: '可选，默认采购单价' },
  { name: 'preferredSupplier', label: '可选，首选供应商名称；供应商账号导入时会自动绑定为当前供应商' },
  { name: 'origin', label: '可选，原产地或供货地区' },
  { name: 'safetyStock', label: '可选，安全库存' },
  { name: 'leadTimeDays', label: '可选，供货周期（天）' },
  { name: 'description', label: '可选，补充描述说明' }
];

const purchaseStatuses = [
  '待供应商确认',
  '供应商已接单',
  '供应商已拒绝',
  '供应商已发货',
  '待仓库收货',
  '已入库'
];

const rawMaterials = ref([]);
const rawMaterialError = ref('');
const selectedMaterial = ref(null);
const suppliers = ref([]);
const purchaseOrders = ref([]);
const purchaseError = ref('');
const rawMaterialMessage = ref('');
const rawMaterialCreateError = ref('');
const orderMessage = ref('');
const orderError = ref('');
const workflowMessage = ref('');
const workflowError = ref('');
const importFile = ref(null);
const importing = ref(false);
const importMessage = ref('');
const importError = ref('');
const importErrors = ref([]);
const supplierDashboard = ref(null);
const purchaseRequests = ref([]);
const selectedPurchaseRequestIds = ref([]);

const rawMaterialFilter = reactive({ keyword: '', startDate: '', endDate: '' });
const orderFilter = reactive({ keyword: '', status: '', startDate: '', endDate: '' });

const rawMaterialForm = reactive({
  name: '',
  materialCategory: '',
  specification: '',
  unit: '',
  unitPrice: null,
  preferredSupplier: '',
  origin: '',
  safetyStock: null,
  leadTimeDays: null,
  description: ''
});

const orderForm = reactive({
  supplierId: '',
  procurementNote: ''
});

const itemForm = reactive({
  productId: '',
  quantity: null,
  unitPrice: null
});

const formItems = ref([]);

const normalizeText = (value) => String(value || '').trim().toLowerCase();

const materialMatchesSupplier = (material, supplier) => {
  const preferredSupplier = normalizeText(material?.preferredSupplier);
  if (!preferredSupplier) {
    return false;
  }
  return [supplier?.name, supplier?.code, supplier?.email]
    .map(normalizeText)
    .filter(Boolean)
    .some((token) => preferredSupplier.includes(token));
};

const procurementScopeMaterials = computed(() => {
  const scoped = formItems.value.map((item) => ({ preferredSupplier: item.preferredSupplier }));
  const currentMaterial = rawMaterials.value.find((item) => String(item.id) === String(itemForm.productId));
  if (currentMaterial) {
    scoped.push(currentMaterial);
  }
  return scoped;
});

const availableSuppliers = computed(() => {
  if (!suppliers.value.length) {
    return [];
  }
  if (!procurementScopeMaterials.value.length) {
    return suppliers.value;
  }

  let intersection = new Set(suppliers.value.map((supplier) => String(supplier.id)));
  for (const material of procurementScopeMaterials.value) {
    const matchedIds = new Set(
      suppliers.value
        .filter((supplier) => materialMatchesSupplier(material, supplier))
        .map((supplier) => String(supplier.id))
    );
    intersection = new Set([...intersection].filter((id) => matchedIds.has(id)));
  }

  return suppliers.value.filter((supplier) => intersection.has(String(supplier.id)));
});

const supplierHint = computed(() => {
  const currentMaterial = rawMaterials.value.find((item) => String(item.id) === String(itemForm.productId));
  if (currentMaterial && !normalizeText(currentMaterial.preferredSupplier)) {
    return '当前原材料未维护供应商，暂时无法加入采购单。';
  }
  if (procurementScopeMaterials.value.length && !availableSuppliers.value.length) {
    return '当前采购明细没有共同供应商，请调整原材料后再下单。';
  }
  return '';
});

const selectedRequestTags = computed(() => purchaseRequests.value.filter((request) => selectedPurchaseRequestIds.value.includes(request.id)));

const loadDashboard = async () => {
  if (!isSupplierRole.value) {
    supplierDashboard.value = null;
    return;
  }
  try {
    const response = await procurementApi.getDashboard();
    supplierDashboard.value = response.data || null;
  } catch (error) {
    supplierDashboard.value = null;
  }
};

const loadRawMaterials = async () => {
  rawMaterialError.value = '';
  try {
    const response = await procurementApi.listRawMaterials({
      keyword: rawMaterialFilter.keyword || undefined,
      startDate: rawMaterialFilter.startDate || undefined,
      endDate: rawMaterialFilter.endDate || undefined
    });
    rawMaterials.value = response.data || [];
    if (selectedMaterial.value && !rawMaterials.value.some((item) => item.id === selectedMaterial.value.id)) {
      selectedMaterial.value = null;
    }
    if (!selectedMaterial.value && rawMaterials.value.length) {
      await selectRawMaterial(rawMaterials.value[0].id);
    }
  } catch (error) {
    rawMaterials.value = [];
    rawMaterialError.value = error?.response?.data?.message || error?.response?.data || '原材料列表加载失败。';
  }
};

const selectRawMaterial = async (id) => {
  if (!id) {
    selectedMaterial.value = null;
    return;
  }
  try {
    const response = await procurementApi.getRawMaterial(id);
    selectedMaterial.value = response.data || null;
  } catch (error) {
    selectedMaterial.value = null;
  }
};

const loadSuppliers = async () => {
  if (!canCreateProcurement.value) {
    suppliers.value = [];
    return;
  }
  try {
    const response = await procurementApi.listSuppliers();
    suppliers.value = response.data || [];
  } catch (error) {
    suppliers.value = [];
  }
};

const loadOrders = async () => {
  purchaseError.value = '';
  try {
    const response = await procurementApi.listOrders({
      keyword: orderFilter.keyword || undefined,
      status: orderFilter.status || undefined,
      startDate: orderFilter.startDate || undefined,
      endDate: orderFilter.endDate || undefined
    });
    purchaseOrders.value = response.data || [];
  } catch (error) {
    purchaseOrders.value = [];
    purchaseError.value = error?.response?.data?.message || error?.response?.data || '采购记录加载失败。';
  }
};

const loadPurchaseRequests = async () => {
  if (!canCreateProcurement.value) {
    purchaseRequests.value = [];
    return;
  }
  try {
    const response = await procurementApi.listRequests();
    purchaseRequests.value = response.data || [];
  } catch (error) {
    purchaseRequests.value = [];
  }
};

const exportOrders = async (format) => {
  purchaseError.value = '';
  try {
    const exporter = format === 'xlsx' ? procurementApi.exportOrdersExcel : procurementApi.exportOrdersCsv;
    const response = await exporter({
      keyword: orderFilter.keyword || undefined,
      status: orderFilter.status || undefined,
      startDate: orderFilter.startDate || undefined,
      endDate: orderFilter.endDate || undefined
    });
    const blob = new Blob([response.data], {
      type: format === 'xlsx'
        ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        : 'text/csv;charset=utf-8;'
    });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `procurement-records-${new Date().toISOString().slice(0, 10)}.${format}`;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
  } catch (error) {
    purchaseError.value = error?.response?.data?.message || error?.response?.data || '采购记录导出失败。';
  }
};

const resetRawMaterialFilter = async () => {
  rawMaterialFilter.keyword = '';
  rawMaterialFilter.startDate = '';
  rawMaterialFilter.endDate = '';
  await loadRawMaterials();
};

const resetOrderFilter = async () => {
  orderFilter.keyword = '';
  orderFilter.status = '';
  orderFilter.startDate = '';
  orderFilter.endDate = '';
  await loadOrders();
};

const resetRawMaterialForm = () => {
  rawMaterialForm.name = '';
  rawMaterialForm.materialCategory = '';
  rawMaterialForm.specification = '';
  rawMaterialForm.unit = '';
  rawMaterialForm.unitPrice = null;
  rawMaterialForm.preferredSupplier = '';
  rawMaterialForm.origin = '';
  rawMaterialForm.safetyStock = null;
  rawMaterialForm.leadTimeDays = null;
  rawMaterialForm.description = '';
};

const resetOrderForm = () => {
  orderForm.supplierId = '';
  orderForm.procurementNote = '';
  itemForm.productId = '';
  itemForm.quantity = null;
  itemForm.unitPrice = null;
  formItems.value = [];
  selectedPurchaseRequestIds.value = [];
};

const applyPurchaseRequest = (request) => {
  if (!request?.product?.id) {
    return;
  }
  mergeFormItem({
    product: {
      id: request.product.id,
      name: request.product.name,
      sku: request.product.sku
    },
    preferredSupplier: request.product?.preferredSupplier || '',
    quantity: Number(request.requestedQuantity || 0),
    unitPrice: Number(request.product?.unitPrice || 0)
  });
  if (!selectedPurchaseRequestIds.value.includes(request.id)) {
    selectedPurchaseRequestIds.value = [...selectedPurchaseRequestIds.value, request.id];
  }
  updateProcurementNoteFromRequests();
  itemForm.productId = String(request.product.id);
  itemForm.quantity = Number(request.requestedQuantity || 0);
  itemForm.unitPrice = Number(request.product?.unitPrice || 0);
};

const updateProcurementNoteFromRequests = () => {
  const requestNos = selectedRequestTags.value.map((item) => item.requestNo).filter(Boolean);
  if (!requestNos.length) {
    return;
  }
  const sourcePrefix = `来源采购申请：${requestNos.join('、')}`;
  const currentNote = String(orderForm.procurementNote || '').trim();
  if (!currentNote) {
    orderForm.procurementNote = sourcePrefix;
    return;
  }
  const sanitized = currentNote.replace(/^来源采购申请：[^；;]+[；;]?\s*/u, '').trim();
  orderForm.procurementNote = sanitized ? `${sourcePrefix}；${sanitized}` : sourcePrefix;
};

const mergeFormItem = (nextItem) => {
  if (!nextItem?.product?.id) {
    return;
  }
  const existingItem = formItems.value.find((item) => String(item.product.id) === String(nextItem.product.id));
  if (existingItem) {
    existingItem.quantity = Number(existingItem.quantity || 0) + Number(nextItem.quantity || 0);
    existingItem.unitPrice = Number(nextItem.unitPrice ?? existingItem.unitPrice ?? 0);
    existingItem.preferredSupplier = nextItem.preferredSupplier || existingItem.preferredSupplier || '';
    return;
  }
  formItems.value.push({
    product: nextItem.product,
    preferredSupplier: nextItem.preferredSupplier || '',
    quantity: Number(nextItem.quantity || 0),
    unitPrice: Number(nextItem.unitPrice || 0)
  });
};

const removeSelectedPurchaseRequest = (requestId) => {
  selectedPurchaseRequestIds.value = selectedPurchaseRequestIds.value.filter((id) => id !== requestId);
  if (!selectedPurchaseRequestIds.value.length) {
    orderForm.procurementNote = String(orderForm.procurementNote || '').replace(/^来源采购申请：[^；;]+[；;]?\s*/u, '').trim();
    return;
  }
  updateProcurementNoteFromRequests();
};

const handleCreateRawMaterial = async () => {
  rawMaterialMessage.value = '';
  rawMaterialCreateError.value = '';
  try {
    const response = await procurementApi.createRawMaterial({
      name: rawMaterialForm.name,
      materialCategory: rawMaterialForm.materialCategory || null,
      specification: rawMaterialForm.specification || null,
      unit: rawMaterialForm.unit || null,
      unitPrice: rawMaterialForm.unitPrice == null ? 0 : Number(rawMaterialForm.unitPrice),
      preferredSupplier: rawMaterialForm.preferredSupplier || null,
      origin: rawMaterialForm.origin || null,
      safetyStock: rawMaterialForm.safetyStock == null ? 0 : Number(rawMaterialForm.safetyStock),
      leadTimeDays: rawMaterialForm.leadTimeDays == null ? 0 : Number(rawMaterialForm.leadTimeDays),
      description: rawMaterialForm.description || null
    });
    rawMaterialMessage.value = '原材料保存成功。';
    resetRawMaterialForm();
    await Promise.all([loadRawMaterials(), loadSuppliers()]);
    if (response?.data?.id) {
      await selectRawMaterial(response.data.id);
    }
  } catch (error) {
    rawMaterialCreateError.value = '原材料保存失败。';
  }
};

const addItem = () => {
  orderError.value = '';
  const material = rawMaterials.value.find((item) => String(item.id) === String(itemForm.productId));
  if (!material || !itemForm.quantity || itemForm.quantity <= 0 || itemForm.unitPrice == null || itemForm.unitPrice < 0) {
    return;
  }
  if (!normalizeText(material.preferredSupplier)) {
    orderError.value = '当前原材料未维护供应商，无法加入采购单。';
    return;
  }
  if (!availableSuppliers.value.length) {
    orderError.value = '当前采购明细没有共同供应商，请调整原材料。';
    return;
  }
  if (!orderForm.supplierId && availableSuppliers.value.length === 1) {
    orderForm.supplierId = String(availableSuppliers.value[0].id);
  }
  if (orderForm.supplierId && !availableSuppliers.value.some((supplier) => String(supplier.id) === String(orderForm.supplierId))) {
    orderError.value = '所选供应商与当前原材料不匹配，请重新选择。';
    return;
  }
  mergeFormItem({
    product: { id: material.id, name: material.name, sku: material.sku },
    preferredSupplier: material.preferredSupplier || '',
    quantity: Number(itemForm.quantity),
    unitPrice: Number(itemForm.unitPrice)
  });
  itemForm.productId = '';
  itemForm.quantity = null;
  itemForm.unitPrice = null;
};

const removeItem = (index) => {
  formItems.value = formItems.value.filter((_, itemIndex) => itemIndex !== index);
};

const handleCreateOrder = async () => {
  orderMessage.value = '';
  orderError.value = '';
  if (!formItems.value.length) {
    orderError.value = '请至少添加一条采购明细。';
    return;
  }
  if (!orderForm.supplierId) {
    orderError.value = availableSuppliers.value.length ? '请选择供应商。' : '当前没有可匹配的供应商，请先调整原材料。';
    return;
  }
  try {
    const payload = {
      supplier: { id: Number(orderForm.supplierId) },
      procurementNote: orderForm.procurementNote || null,
      sourceRequestIds: selectedPurchaseRequestIds.value,
      items: formItems.value.map((item) => ({
        product: { id: item.product.id },
        quantity: Number(item.quantity),
        unitPrice: Number(item.unitPrice)
      }))
    };
    const response = await procurementApi.createOrder(payload);
    orderMessage.value = `采购单 ${response?.data?.poNo || ''} 已提交，并已通知供应商。`;
    resetOrderForm();
    await Promise.all([loadOrders(), loadPurchaseRequests()]);
    await loadDashboard();
  } catch (error) {
    orderError.value = error?.response?.data?.message || error?.response?.data || '采购单创建失败。';
  }
};

const handleSupplierDecision = async (orderId, decision) => {
  workflowMessage.value = '';
  workflowError.value = '';
  try {
    await procurementApi.supplierDecision(orderId, decision, {});
    workflowMessage.value = decision === 'ACCEPT' ? `采购单 ${orderId} 已接单。` : `采购单 ${orderId} 已拒绝。`;
    await Promise.all([loadOrders(), loadDashboard()]);
  } catch (error) {
    workflowError.value = error?.response?.data?.message || error?.response?.data || '供应商处理失败。';
  }
};

const handleSupplierShip = async (orderId) => {
  workflowMessage.value = '';
  workflowError.value = '';
  try {
    await procurementApi.supplierShip(orderId, {});
    workflowMessage.value = `采购单 ${orderId} 已发货，并已通知采购管理员。`;
    await Promise.all([loadOrders(), loadDashboard()]);
  } catch (error) {
    workflowError.value = error?.response?.data?.message || error?.response?.data || '发货失败。';
  }
};

const handleNotifyWarehouse = async (orderId) => {
  workflowMessage.value = '';
  workflowError.value = '';
  try {
    await procurementApi.notifyWarehouse(orderId, {});
    workflowMessage.value = `采购单 ${orderId} 已通知仓库管理员确认入库。`;
    await loadOrders();
  } catch (error) {
    workflowError.value = error?.response?.data?.message || error?.response?.data || '通知仓库失败。';
  }
};

const downloadTemplate = async () => {
  importMessage.value = '';
  importError.value = '';
  try {
    const response = await procurementApi.downloadRawMaterialTemplate();
    const blob = new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'raw-material-template.xlsx';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
  } catch (error) {
    importError.value = '模板下载失败。';
  }
};

const handleFileChange = (event) => {
  importFile.value = event?.target?.files?.[0] || null;
};

const handleImportRawMaterials = async () => {
  if (!importFile.value) {
    importError.value = '请先选择 Excel 文件。';
    return;
  }
  importing.value = true;
  importMessage.value = '';
  importError.value = '';
  importErrors.value = [];
  try {
    const formData = new FormData();
    formData.append('file', importFile.value);
    const response = await procurementApi.importRawMaterials(formData);
    const result = response.data || {};
    importMessage.value = `导入完成：新增 ${result.createdCount || 0} 条，更新 ${result.updatedCount || 0} 条。`;
    importErrors.value = result.errors || [];
    await Promise.all([loadRawMaterials(), loadSuppliers()]);
    await loadDashboard();
  } catch (error) {
    importError.value = error?.response?.data?.message || error?.response?.data || 'Excel 导入失败。';
  } finally {
    importing.value = false;
  }
};

const summarizeItems = (items) => (items || []).map((item) => `${item.product?.name || item.product?.sku || '-'} x ${formatNumber(item.quantity)}`).join('；') || '-';
const formatAmount = (value) => Number(value || 0).toFixed(2);
const formatNumber = (value) => Number(value || 0).toFixed(2);
const formatDate = (value) => (value ? new Date(value).toLocaleString() : '-');
const procurementGanttItems = computed(() => purchaseOrders.value.map((order) => ({
  id: order.id,
  label: `${order.poNo} / ${order.supplier?.name || '-'}`,
  meta: `${order.createdByName || '采购管理员'} · ¥${formatAmount(order.totalAmount)}`,
  start: order.orderDate,
  end: order.receivedAt || order.shippedAt || order.notifiedWarehouseAt || order.orderDate,
  shortText: order.status || '采购',
  color: '#059669'
})));

watch(
  () => realtime.state.lastMessage,
  (message) => {
    if (message?.topic?.startsWith('/topic/procurement')) {
      loadOrders();
      loadPurchaseRequests();
      loadRawMaterials();
      loadDashboard();
      if (selectedMaterial.value?.id) {
        selectRawMaterial(selectedMaterial.value.id);
      }
    }
  }
);

watch(
  availableSuppliers,
  (nextSuppliers) => {
    if (!orderForm.supplierId) {
      if (nextSuppliers.length === 1) {
        orderForm.supplierId = String(nextSuppliers[0].id);
      }
      return;
    }
    const stillAvailable = nextSuppliers.some((supplier) => String(supplier.id) === String(orderForm.supplierId));
    if (!stillAvailable) {
      orderForm.supplierId = nextSuppliers.length === 1 ? String(nextSuppliers[0].id) : '';
    }
  },
  { immediate: true }
);

watch(
  () => itemForm.productId,
  (productId) => {
    const material = rawMaterials.value.find((item) => String(item.id) === String(productId));
    if (material) {
      itemForm.unitPrice = Number(material.unitPrice || 0);
    }
  }
);

onMounted(async () => {
  await Promise.all([loadRawMaterials(), loadOrders(), loadPurchaseRequests(), loadSuppliers(), loadDashboard()]);
});
</script>

