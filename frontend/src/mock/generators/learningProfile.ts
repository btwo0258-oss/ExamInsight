import type {
  LearningConfirmationRequest,
  LearningProfileData,
  LearningProfileRequest,
  LearningProfileResult,
} from '@/types/contracts/learning'

export function emptyMockLearningProfile(): LearningProfileData {
  return {
    goal: '待识别',
    subject: '待识别',
    foundation: '基础一般',
    weakPoints: [],
    period: '待确认',
    dailyTime: '每天 60 分钟',
    preferences: [],
    source: '无',
    extra: '',
  }
}

export function inferMockLearningProfile(input: LearningProfileRequest): LearningProfileData {
  const text = input.text
  const current = input.currentProfile ?? emptyMockLearningProfile()
  const next: LearningProfileData = {
    ...current,
    weakPoints: [...current.weakPoints],
    preferences: [...current.preferences],
    source: input.source || current.source,
    subject: input.subject || current.subject,
    extra: input.supplementalRequirement ?? current.extra,
  }

  if (/面试|秋招|春招|offer|职业/i.test(text)) next.goal = '职业技能'
  else if (/作业|实验|报告|论文|课程设计|科研/i.test(text)) next.goal = '作业 / 科研'
  else if (/项目|实战|开发|作品/i.test(text)) next.goal = '项目实践'
  else if (/考|复习|期末|期中|测验|四六级|cet/i.test(text)) next.goal = '考试备考'
  else if (next.goal === '待识别') next.goal = '系统学习'

  if (/零基础|从零|完全不会/i.test(text)) next.foundation = '尚未接触'
  else if (/基础差|不懂|不会|分不清|薄弱|混淆/i.test(text)) next.foundation = '基础薄弱'
  else if (/熟悉|掌握|有基础/i.test(text)) next.foundation = '有一定基础'

  const period = text.match(/(\d+)\s*(天|周|个月|月)/)
  if (period) next.period = `${period[1]} ${period[2]}`
  else if (/下周/i.test(text)) next.period = '1 周'
  else if (/明天/i.test(text)) next.period = '1 天'

  const daily = text.match(/每天.{0,8}?(\d+)\s*(分钟|小时)/)
  if (daily) next.dailyTime = `每天 ${daily[1]} ${daily[2]}`
  else if (/周末/i.test(text)) next.dailyTime = '仅周末'

  const preferences = new Set(next.preferences)
  if (/刷题|题海|错题|练习/i.test(text)) preferences.add('练习驱动')
  if (/项目|实战|开发/i.test(text)) preferences.add('项目实操')
  if (/先讲|讲解|概念|理论/i.test(text)) preferences.add('概念讲解')
  if (/案例|示例|例子/i.test(text)) preferences.add('案例演示')
  if (/图|导图|框架|结构/i.test(text)) preferences.add('图表梳理')
  if (/阅读|总结|笔记/i.test(text)) preferences.add('阅读总结')
  next.preferences = [...preferences]

  const matchedTags = input.knowledgeTags?.filter((tag) => text.includes(tag)) ?? []
  if (matchedTags.length) next.weakPoints = [...new Set([...next.weakPoints, ...matchedTags])]
  else {
    const weakMatch = text.match(/(?:薄弱|不会|不懂|分不清|复习)(?:的|是|一下)?[：:]?([^，。！？\n]{2,28})/)
    if (weakMatch?.[1]) {
      next.weakPoints = [...new Set([...next.weakPoints, ...weakMatch[1].split(/[、,，和与]/).map((item) => item.trim()).filter(Boolean)])]
    }
  }
  if (next.subject === '待识别' && next.weakPoints.length) next.subject = next.weakPoints.join('、')
  return next
}

export function buildMockLearningConfirmation(input: LearningConfirmationRequest): string {
  const profile = input.profile
  const weakPoints = profile.weakPoints.length ? profile.weakPoints.map((item) => `- ${item}`).join('\n') : '- 在第一阶段进一步识别'
  const strategy = profile.foundation.includes('零基础') || profile.foundation.includes('薄弱') ? '基础理解为主，练习循序渐进' : '概念梳理与综合练习并行'
  return [
    '# 个性化学习方案确认稿', '',
    '## 1. 学习目标', input.goal || profile.goal, '',
    '## 2. 学习画像',
    `- 目标类型：${profile.goal}`,
    `- 学习内容：${profile.subject}`,
    `- 当前基础：${profile.foundation}`,
    `- 学习周期：${profile.period}`,
    `- 每日时间：${profile.dailyTime}`,
    `- 学习偏好：${profile.preferences.join(' · ') || '暂无特殊要求'}`,
    `- 资料来源：${profile.source}`,
    input.uploadedFileNames?.length ? `- 上传材料：${input.uploadedFileNames.join('、')}` : '- 上传材料：暂无',
    input.relatedProjectName ? `- 关联项目：${input.relatedProjectName}` : '- 关联项目：无', '',
    '## 3. 重点知识模块', weakPoints, '',
    '## 4. 学习路径',
    '### 阶段一：基础确认', '- 建立核心概念框架，通过小型理解检查定位薄弱环节。',
    '### 阶段二：专项强化', '- 围绕薄弱知识点完成例题拆解、专项练习和即时纠错。',
    '### 阶段三：综合复盘', '- 用综合任务检查迁移能力，归纳错题原因并完成巩固。', '',
    '## 5. 练习建议', `- 建议策略：${input.difficultyStrategy || strategy}`,
    input.questionCount ? `- 计划练习总量：${input.questionCount} 题` : '- 具体题量根据阶段检查结果动态调整。', '',
    '## 6. 预计产出', '- 分阶段学习路径', '- 专项练习与错题巩固', '- 知识结构思维导图',
  ].join('\n')
}

export function createMockLearningProfileResult(input: LearningProfileRequest): LearningProfileResult {
  const profile = inferMockLearningProfile(input)
  return {
    profile,
    confirmationDocument: buildMockLearningConfirmation({
      libraryId: input.libraryId,
      goal: input.text,
      profile,
    }),
  }
}
