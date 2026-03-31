<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import StatsCard from '@/components/admin/admin/StatsCard.vue'
import AppIcon from '@/components/admin/admin/AppIcon.vue'
import { getStats, getTrends, getTypeDistribution } from '@/api/adminadmindashboard'

const stats = ref<any[]>([])
const trends = ref<any[]>([])
const types = ref<any[]>([])
const loading = ref(false)

async function fetchDashboardData() {
  loading.value = true
  try {
    const [statsData, trendsData, typesData] = await Promise.all([
      getStats(),
      getTrends(),
      getTypeDistribution()
    ])
    stats.value = statsData
    trends.value = trendsData
    types.value = typesData
  } catch (err) {
    console.error('Failed to fetch dashboard data:', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDashboardData()
})

const generateLinePath = computed(() => {
  if (trends.value.length === 0) return ''
  const max = Math.max(...trends.value.map(t => t.value as number)) || 1
  const width = 400
  const height = 100
  const step = width / (trends.value.length - 1 || 1)
  
  return trends.value.map((t, i) => {
    const x = i * step
    const y = height - ((t.value as number) / max * height)
    return `${i === 0 ? 'M' : 'L'}${x},${y}`
  }).join(' ')
})

const totalDocs = computed(() => types.value.reduce((acc, t) => acc + (t.value as number), 0) || 1)

const calculatePercentage = (value: number) => {
  return Math.round((value / totalDocs.value) * 100)
}

const calculateDashArray = (value: number) => {
  const circumference = 2 * Math.PI * 40
  const segment = (value / totalDocs.value) * circumference
  return `${segment} ${circumference}`
}

const calculateDashOffset = (index: number) => {
  const circumference = 2 * Math.PI * 40
  let offset = 0
  for (let i = 0; i < index; i++) {
    offset -= (types.value[i].value as number / totalDocs.value) * circumference
  }
  return offset
}
</script>

<template>
  <div class="dashboard-page">
    <header class="page-header">
      <h2 class="page-title">数据概览</h2>
      <p class="page-subtitle">实时监控系统核心运行指标</p>
    </header>
    
    <div class="stats-grid">
      <StatsCard 
        v-for="stat in stats" 
        :key="stat.title"
        v-bind="stat"
      />
    </div>
    
    <div class="charts-grid">
      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">注册趋势 (�?7 �?</h3>
          <AppIcon name="bar-chart" :size="16" />
        </div>
        <div class="chart-placeholder">
          <!-- Mock Line Chart SVG -->
          <svg viewBox="0 0 400 120" class="line-chart-svg">
            <path 
              :d="generateLinePath" 
              fill="none" 
              stroke="var(--color-primary)" 
              stroke-width="3" 
            />
          </svg>
          <div class="chart-labels">
            <span v-for="t in trends" :key="t.date">{{ t.date }}</span>
          </div>
        </div>
      </div>
      
      <div class="chart-card">
        <div class="chart-header">
          <h3 class="chart-title">文档类型分布</h3>
          <AppIcon name="pie-chart" :size="16" />
        </div>
        <div class="chart-placeholder flex-center">
          <!-- Mock Pie Chart SVG -->
          <div class="pie-wrap">
            <svg viewBox="0 0 100 100" class="pie-chart-svg">
              <circle cx="50" cy="50" r="40" stroke="#eee" stroke-width="20" fill="none" />
              <circle 
                v-for="(type, index) in types" 
                :key="type.type"
                cx="50" 
                cy="50" 
                r="40" 
                :stroke="type.color" 
                stroke-width="20" 
                fill="none" 
                :stroke-dasharray="calculateDashArray(type.value)" 
                :stroke-dashoffset="calculateDashOffset(index)"
              />
            </svg>
            <div class="pie-legend">
              <div v-for="type in types" :key="type.type" class="legend-item">
                <span class="dot" :style="{ backgroundColor: type.color }"></span> 
                {{ type.type }} {{ calculatePercentage(type.value) }}%
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.page-header {
  margin-bottom: 8px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 4px;
}

.page-subtitle {
  font-size: 14px;
  color: var(--color-text-muted);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
}

.charts-grid {
  display: grid;
  grid-template-columns: 2fr 1.2fr;
  gap: 20px;
}

.chart-card {
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 24px;
  box-shadow: var(--shadow-sm);
}

.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
}

.chart-placeholder {
  height: 240px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.line-chart-svg {
  width: 100%;
  height: 160px;
}

.chart-labels {
  display: flex;
  justify-content: space-between;
  padding-top: 12px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.pie-wrap {
  display: flex;
  align-items: center;
  gap: 40px;
}

.pie-chart-svg {
  width: 160px;
  height: 160px;
  transform: rotate(-90deg);
}

.pie-legend {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--color-text-muted);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot.primary { background-color: var(--color-primary); }
.dot.purple { background-color: #8b5cf6; }
.dot.gray { background-color: #eee; }

@media (max-width: 1024px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
}
</style>
