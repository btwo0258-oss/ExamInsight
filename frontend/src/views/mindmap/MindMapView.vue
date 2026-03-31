<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, shallowRef, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/common/AppIcon.vue'
import AppButton from '@/components/common/AppButton.vue'
import { useMindMapStore } from '@/stores/mindmap'

// simple-mind-map
import MindMap from 'simple-mind-map'
// @ts-ignore
import KeyboardNavigation from 'simple-mind-map/src/plugins/KeyboardNavigation.js'
// @ts-ignore
import Drag from 'simple-mind-map/src/plugins/Drag.js'
// @ts-ignore
import Export from 'simple-mind-map/src/plugins/Export.js'
// @ts-ignore
import Select from 'simple-mind-map/src/plugins/Select.js'
// @ts-ignore
import AssociativeLine from 'simple-mind-map/src/plugins/AssociativeLine.js'
import ContextMenu from '@/components/common/ContextMenu.vue'
import type { MenuItem } from '@/components/common/ContextMenu.vue'

// CSS
import 'simple-mind-map/dist/simpleMindMap.esm.css'

// Register Plugins
MindMap.usePlugin(KeyboardNavigation)
MindMap.usePlugin(Drag)
MindMap.usePlugin(Export)
MindMap.usePlugin(Select)
MindMap.usePlugin(AssociativeLine)

const route = useRoute()
const router = useRouter()
const store = useMindMapStore()

const mindMapContainer = ref<HTMLElement | null>(null)
const mindMap = shallowRef<any>(null)
const isSaving = ref(false)
const isRelMode = ref(false)
const toastMsg = ref('')

// 监听 store.mapTitle 变化并同步到 mindmap 中心主题
watch(() => store.mapTitle, (newTitle) => {
  if (mindMap.value && mindMap.value.renderer.root) {
    const root = mindMap.value.renderer.root
    if (root.nodeData.data.text !== newTitle) {
      mindMap.value.execCommand('SET_NODE_TEXT', root, newTitle)
    }
  }
})

const selectedNodes = ref<any[]>([])
const showMenu = ref(false)
const menuX = ref(0)
const menuY = ref(0)

// Initialize MindMap
const initMindMap = (data: any) => {
  if (!mindMapContainer.value) return

  // 深度克隆数据，防止引用问题导致引擎出错
  const renderData = JSON.parse(JSON.stringify(data || store.treeData))
  
  // 确保数据结构完整
  if (!renderData.data) {
    renderData.data = { text: store.mapTitle || '中心主题' }
  }
  if (!renderData.children) {
    renderData.children = []
  }

  mindMap.value = new MindMap({
    el: mindMapContainer.value,
    data: renderData,
    theme: 'classic',
    layout: 'logicalStructure',
    readonly: false,
    enableFreeDrag: true,
    mousewheelAction: 'zoom',
    initRootNodePosition: ['center', 'center'],
    themeConfig: {
      backgroundColor: '#f8fafc',
      lineColor: '#64748b', // 节点连线颜色：深灰色
      lineWidth: 2,
      // 关联线（联系线）样式
      associativeLineColor: '#64748b',
      associativeLineWidth: 2,
      // 默认节点边框颜色
      borderColor: '#94a3b8', 
      borderWidth: 1,
      // 中心主题样式强制透明
      rootFillColor: 'transparent',
      rootBorderWidth: 0,
      rootFontSize: 32,
      rootFontWeight: 'bold',
      rootColor: '#1e293b'
    }
  } as any)

  // 注册事件
  let isApplyingStyles = false
  mindMap.value.on('node_tree_render_end', () => {
    if (isApplyingStyles) return
    isApplyingStyles = true
    applyCustomNodeStyles()
    setTimeout(() => {
      isApplyingStyles = false
    }, 500) // 增加冷却期到 500ms
  })

  mindMap.value.on('data_change', (newData: any) => {
    store.treeData = newData
  })

  mindMap.value.on('node_active', (node: any, activeNodeList: any[]) => {
    selectedNodes.value = activeNodeList
  })

  mindMap.value.on('node_contextmenu', (e: MouseEvent, node: any) => {
    e.preventDefault()
    e.stopPropagation()
    menuX.value = e.clientX
    menuY.value = e.clientY
    showMenu.value = true
  })

  // Add custom shortcuts
  mindMap.value.keyCommand.addShortcut('Control+d', () => {
    const data = mindMap.value.renderer.copyNode()
    if (data) mindMap.value.execCommand('INSERT_MULTI_CHILD_NODE', [], data)
  })

  mindMap.value.keyCommand.addShortcut('Control+Backspace', () => {
    mindMap.value.execCommand('REMOVE_CURRENT_NODE')
  })

  // Handle draw click for relationship mode
  mindMap.value.on('draw_click', (e: MouseEvent) => {
    if (isRelMode.value && selectedNodes.value.length > 0) {
      handleDrawClick(e)
    }
  })

  // 监听节点点击，用于联系模式下的已有主题连接
  mindMap.value.on('node_click', (node: any) => {
    if (isRelMode.value && selectedNodes.value.length > 0) {
      const sourceNode = selectedNodes.value[0]
      if (sourceNode !== node) {
        mindMap.value.execCommand('ADD_ASSOCIATIVE_LINE', sourceNode, node)
        isRelMode.value = false
        showToast('已建立联系')
      }
    }
  })

  // 监听联系线创建，自动退出模式
  mindMap.value.on('associative_line_click', () => {
    isRelMode.value = false
  })

  mindMap.value.on('back_to_normal_mode', () => {
    isRelMode.value = false
  })

  // 初始应用样式
  setTimeout(() => {
    applyCustomNodeStyles()
    // 强制中心主题居中
    if (mindMap.value.renderer.root) {
      mindMap.value.renderer.moveNodeToCenter(mindMap.value.renderer.root)
    }
  }, 200)
}

// 颜色工具：生成浅色版本
const getLightColor = (hex: string) => {
  if (!hex || hex === 'transparent') return 'transparent'
  // 简单的色值变浅逻辑
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r}, ${g}, ${b}, 0.2)`
}

const colors = ['#4f46e5', '#ef4444', '#f59e0b', '#10b981', '#3b82f6', '#8b5cf6', '#ec4899']

const applyCustomNodeStyles = () => {
  if (!mindMap.value || !mindMap.value.renderer.root) return
  
  // 1. 设置全局配置（仅在必要时）
  const config = {
    lineColor: '#64748b',
    borderColor: '#94a3b8',
    associativeLineColor: '#64748b',
    rootFillColor: 'transparent',
    rootBorderWidth: 0
  }
  
  mindMap.value.setThemeConfig(config)

  // 2. 严格的样式设置辅助函数，避免无意义的重渲染触发
  const safeSetStyle = (node: any, prop: string, value: any) => {
    if (node.getStyle(prop) !== value) {
      node.setStyle(prop, value)
    }
  }

  // 确保所有现有节点的样式一致
  const walk = (node: any) => {
    if (!node) return
    const layer = node.layerIndex
    const data = node.nodeData.data
    
    if (layer === 0) { // H1 (Root)
      safeSetStyle(node, 'fillColor', 'transparent')
      safeSetStyle(node, 'borderColor', 'transparent')
      safeSetStyle(node, 'borderWidth', 0)
      safeSetStyle(node, 'fontSize', 32)
      safeSetStyle(node, 'fontWeight', 'bold')
      safeSetStyle(node, 'color', '#1e293b')
      safeSetStyle(node, 'shape', 'rectangle')
    } else if (layer === 1) { // H2 (Main Topics)
      if (!data.fillColor || data.fillColor === 'transparent') {
        const parentChildren = node.parent?.children || []
        const colorIndex = (parentChildren.indexOf(node) || 0) % colors.length
        const stableColor = colors[colorIndex]
        safeSetStyle(node, 'fillColor', stableColor)
        safeSetStyle(node, 'color', '#ffffff')
        safeSetStyle(node, 'borderRadius', 8)
        safeSetStyle(node, 'borderColor', '#94a3b8')
        safeSetStyle(node, 'borderWidth', 1)
      }
    } else if (layer === 2) { // H3 (Sub Topics)
      const parentColor = node.parent?.nodeData?.data?.fillColor || colors[0]
      const lightColor = getLightColor(parentColor)
      safeSetStyle(node, 'fillColor', lightColor)
      safeSetStyle(node, 'color', parentColor)
      safeSetStyle(node, 'borderRadius', 6)
      safeSetStyle(node, 'borderColor', '#94a3b8')
      safeSetStyle(node, 'borderWidth', 1)
    } else { // Others
      safeSetStyle(node, 'fillColor', 'transparent')
      safeSetStyle(node, 'color', 'var(--color-text)')
      safeSetStyle(node, 'borderColor', '#94a3b8')
      safeSetStyle(node, 'borderWidth', 1)
    }

    if (node.children && node.children.length > 0) {
      node.children.forEach(walk)
    }
  }
  walk(mindMap.value.renderer.root)
}

onMounted(async () => {
  const id = route.params.id
  if (id === 'new') {
    // 已经在 createMap 时设置好了 store 状态
    initMindMap(store.treeData)
  } else if (id) {
    try {
      await store.getMapById(Number(id))
      initMindMap(store.treeData)
    } catch (error) {
      showToast('加载失败')
      store.initEmptyMap()
      initMindMap(store.treeData)
    }
  }
})

onBeforeUnmount(() => {
  if (mindMap.value) {
    mindMap.value.destroy()
  }
})

// Watch for route changes (e.g. switching between maps)
watch(() => route.params.id, async (newId) => {
  if (mindMap.value) {
    mindMap.value.destroy()
  }
  if (newId === 'new') {
    initMindMap(store.treeData)
  } else if (newId) {
    try {
      await store.getMapById(Number(newId))
      initMindMap(store.treeData)
    } catch {
      showToast('加载失败')
    }
  }
})

// Toolbar Actions
const addSiblingNode = () => mindMap.value?.execCommand('INSERT_NODE')
const addChildNode = () => mindMap.value?.execCommand('INSERT_CHILD_NODE')

const addRelLine = () => {
  if (selectedNodes.value.length === 0) return
  
  // 进入联系模式
  isRelMode.value = true
  
  // 开启简单脑图内置的联系模式（显示虚线）
  // 注意：这个命令会进入一个特殊的交互状态
  mindMap.value?.execCommand('ADD_ASSOCIATIVE_LINE')
  
  showToast('联系模式：点击现有主题连接，或点击空白处新建并连接')
}

// 辅助函数：在指定位置创建新节点并建立联系
function handleDrawClick(e: MouseEvent) {
  if (!isRelMode.value || selectedNodes.value.length === 0) return
  
  const sourceNode = selectedNodes.value[0]
  
  // 1. 在点击位置创建新节点
  // 我们先插入一个子节点，简单脑图会自动处理位置或我们可以后续调整
  mindMap.value.execCommand('INSERT_CHILD_NODE')
  
  // 2. 建立联系
  setTimeout(() => {
    const activeNodes = mindMap.value.renderer.activeNodeList
    if (activeNodes.length > 0) {
      const newNode = activeNodes[0]
      // 建立关联线
      mindMap.value.execCommand('ADD_ASSOCIATIVE_LINE', sourceNode, newNode)
      // 退出联系模式
      isRelMode.value = false
      showToast('已创建新主题并建立联系')
    }
  }, 200)
}

const locateNode = () => {
  if (selectedNodes.value.length > 0) {
    mindMap.value?.renderer.moveNodeToCenter(selectedNodes.value[0])
  } else {
    mindMap.value?.view.reset()
  }
}

// Context Menu Items
const menuItems = computed<MenuItem[]>(() => {
  return [
    { label: '拷贝', action: () => mindMap.value?.renderer.copy(), icon: 'copy', shortcut: 'Ctrl C' },
    { label: '剪切', action: () => mindMap.value?.renderer.cut(), icon: 'edit', shortcut: 'Ctrl X' },
    { label: '粘贴', action: () => mindMap.value?.renderer.paste(), icon: 'paperclip', shortcut: 'Ctrl V' },
    { label: '复制', action: () => {
      const data = mindMap.value?.renderer.copyNode()
      if (data) mindMap.value?.execCommand('INSERT_MULTI_CHILD_NODE', [], data)
    }, icon: 'layers', shortcut: 'Ctrl D' },
    { label: '', divided: true },
    { label: '删除', action: () => mindMap.value?.execCommand('REMOVE_NODE'), icon: 'trash', danger: true, shortcut: 'Backspace' },
    { label: '删除单个主题', action: () => mindMap.value?.execCommand('REMOVE_CURRENT_NODE'), icon: 'close', danger: true, shortcut: 'Ctrl Backspace' },
  ]
})

const handleSave = async () => {
  if (!mindMap.value) return
  isSaving.value = true
  try {
    const data = mindMap.value.getData()
    // 强制获取最新标题，防止用户修改了 input 但 store 没同步
    const title = store.mapTitle || '未命名思维导图'
    if (store.currentMapId) {
      await store.updateMap(store.currentMapId, title, JSON.stringify(data))
      showToast('已保存')
    }
  } catch (error) {
    showToast('❌ 保存失败')
  } finally {
    isSaving.value = false
  }
}

function showToast(msg: string) {
  toastMsg.value = msg
  setTimeout(() => toastMsg.value = '', 3000)
}
</script>

<template>
  <div class="mindmap-view">
    <!-- Toolbar -->
    <div class="toolbar">
      <div class="toolbar__left">
        <AppButton variant="ghost" @click="router.back()" class="back-btn">
          <AppIcon name="chevron-left" :size="20" />
        </AppButton>
        <div class="title-container">
          <input v-model="store.mapTitle" class="title-input" placeholder="输入导图名称..." />
        </div>
      </div>
      
      <div class="toolbar__center">
        <div class="tool-group">
          <button class="tool-btn" @click="addSiblingNode" title="同级节点 (Enter)">
            <AppIcon name="mind-topic" :size="20" />
            <span>主题</span>
          </button>
          <button class="tool-btn" @click="addChildNode" title="子节点 (Tab)">
            <AppIcon name="mind-subtopic" :size="20" />
            <span>子主题</span>
          </button>
          <button 
            class="tool-btn" 
            :class="{ disabled: selectedNodes.length === 0, active: isRelMode }" 
            @click="addRelLine" 
            title="添加联系"
          >
            <AppIcon name="mind-rel" :size="20" />
            <span>联系</span>
          </button>
        </div>

        <div class="divider-v" />

        <div class="tool-group">
          <button class="tool-btn" @click="locateNode" title="居中当前主题">
            <AppIcon name="target" :size="20" />
            <span>居中</span>
          </button>
        </div>
      </div>

      <div class="toolbar__right">
        <AppButton 
          class="save-btn"
          :loading="isSaving" 
          @click="handleSave"
        >
          <template #icon><AppIcon name="upload-cloud" :size="16" /></template>
          保存
        </AppButton>
      </div>
    </div>

    <div v-if="toastMsg" class="toast">{{ toastMsg }}</div>

    <!-- Canvas -->
    <div id="mindMapContainer" ref="mindMapContainer" class="mind-map-body"></div>

    <ContextMenu
      v-if="showMenu"
      :x="menuX"
      :y="menuY"
      :items="menuItems"
      @close="showMenu = false"
    />
  </div>
</template>

<style scoped>
.mindmap-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f8fafc;
}

.toolbar {
  height: 64px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: white;
  border-bottom: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  z-index: 100;
}

.toolbar__left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
}

.title-container {
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;
}

.title-container:hover {
  background: #f1f5f9;
}

.title-input {
  border: none;
  background: transparent;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  outline: none;
  width: 180px;
}

.toolbar__center {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #f1f5f9;
  padding: 6px 12px;
  border-radius: 12px;
}

.tool-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.tool-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 4px 12px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  color: #64748b;
  transition: all 0.2s;
}

.tool-btn span {
  font-size: 10px;
  font-weight: 500;
}

.tool-btn:hover {
  background: white;
  color: var(--color-primary);
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.tool-btn.active {
  color: var(--color-primary);
  background: white;
  box-shadow: inset 0 1px 3px rgba(0,0,0,0.1);
  border: 1px solid var(--color-primary-light);
}

.tool-btn.disabled {
  opacity: 0.3;
  cursor: not-allowed;
  pointer-events: none;
}

.divider-v {
  width: 1px;
  height: 24px;
  background: #e2e8f0;
}

.toolbar__right {
  display: flex;
  gap: 12px;
  flex: 1;
  justify-content: flex-end;
}

.ai-btn {
  background: linear-gradient(135deg, #4f46e5 0%, #8b5cf6 100%) !important;
  border: none !important;
}

.save-btn {
  background-color: #333333 !important;
  color: #ffffff !important;
  border-radius: 20px !important;
  border: none !important;
  padding: 6px 16px !important;
  font-size: 14px;
  font-weight: 500;
}
.save-btn:hover {
  background-color: #444444 !important;
}

.mind-map-body {
  flex: 1;
  background-color: #ffffff;
  position: relative;
  width: 100%;
  height: 100%;
}

/* Override simple-mind-map styles if needed */
:deep(.simple-mind-map-container) {
  width: 100%;
  height: 100%;
}

.toast {
  position: absolute;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  background: #1e293b;
  color: white;
  padding: 10px 24px;
  border-radius: 24px;
  font-size: 14px;
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
  animation: fadeInDown 0.3s ease-out;
}

@keyframes fadeInDown {
  from { opacity: 0; transform: translate(-50%, -10px); }
  to { opacity: 1; transform: translate(-50%, 0); }
}

.back-btn {
  padding: 8px !important;
  min-width: auto !important;
  border-radius: 50% !important;
}

:root[data-theme='dark'] .mindmap-view {
  background-color: #0f172a;
}

:root[data-theme='dark'] .toolbar {
  background-color: #1e293b;
  border-bottom-color: #334155;
}

:root[data-theme='dark'] .toolbar__center {
  background-color: #334155;
}

:root[data-theme='dark'] .title-input {
  color: #f1f5f9;
}

:root[data-theme='dark'] .mind-map-body {
  background-color: #0f172a;
}
</style>