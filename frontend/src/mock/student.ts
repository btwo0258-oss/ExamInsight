export type AgentStatus = 'done' | 'running' | 'pending'

export type RecentConversation = {
  id: number
  title: string
  desc: string
}

export type CourseLibrary = {
  id: number
  name: string
  course: string
  description: string
  fileCount: number
  chunkCount: number
  status: 'ready' | 'processing'
  updatedAt: string
  tags: string[]
}

export type PublicResource = {
  id: number
  title: string
  type: string
  category: string
  desc: string
}

export type UploadedFile = {
  id: number
  name: string
  type: string
  status: '解析完成' | '向量化中' | '等待解析'
  updatedAt: string
}

export type LearningPlan = {
  id: number
  title: string
  goal: string
  libraryId: number
  profile: Array<{ label: string; value: string }>
  stages: Array<{
    id: number
    title: string
    duration: string
    goal: string
    resources: string[]
    status: 'done' | 'active' | 'pending'
  }>
  resources: Array<{
    id: number
    group: '文档' | '结构图' | '练习' | '实操'
    title: string
    desc: string
    action: string
  }>
  agents: Array<{
    name: string
    desc: string
    status: AgentStatus
  }>
}

export const recentConversations: RecentConversation[] = [
  { id: 101, title: 'Java 多态问题', desc: '父类引用与动态绑定' },
  { id: 102, title: '接口和抽象类区别', desc: '适用场景对比' },
  { id: 103, title: '英语阅读长难句', desc: '定位主旨题方法' },
]

export const courseLibraries: CourseLibrary[] = [
  {
    id: 1,
    name: 'Java 面向对象资料库',
    course: 'Java 面向对象程序设计',
    description: '课程讲义、代码案例、期末复习资料',
    fileCount: 12,
    chunkCount: 328,
    status: 'ready',
    updatedAt: '今天 14:20',
    tags: ['类与对象', '继承', '多态', '接口'],
  },
  {
    id: 2,
    name: '人工智能导论资料库',
    course: '人工智能导论',
    description: '搜索算法、机器学习基础、课堂 PPT',
    fileCount: 8,
    chunkCount: 214,
    status: 'processing',
    updatedAt: '昨天 21:10',
    tags: ['搜索', '机器学习', '知识表示'],
  },
  {
    id: 3,
    name: '英语四级阅读资料库',
    course: '大学英语四级',
    description: '阅读真题、词汇讲义、错题整理',
    fileCount: 36,
    chunkCount: 690,
    status: 'ready',
    updatedAt: '周一 09:30',
    tags: ['阅读', '长难句', '词汇'],
  },
]

export const publicResources: PublicResource[] = [
  {
    id: 201,
    title: 'Java OOP 课程讲义',
    type: 'PDF',
    category: '计算机',
    desc: '覆盖封装、继承、多态、接口等核心内容',
  },
  {
    id: 202,
    title: '继承与多态练习题',
    type: '题库',
    category: '计算机',
    desc: '选择题、判断题、代码阅读题混合练习',
  },
  {
    id: 203,
    title: '接口设计案例',
    type: '代码',
    category: '计算机',
    desc: '以支付接口和通知接口为例讲解抽象设计',
  },
]

export const recentUploads: UploadedFile[] = [
  { id: 301, name: 'Java继承讲义.pdf', type: 'PDF', status: '解析完成', updatedAt: '今天 14:20' },
  { id: 302, name: '多态代码案例.docx', type: 'Word', status: '向量化中', updatedAt: '今天 14:18' },
  { id: 303, name: '接口练习题.txt', type: 'TXT', status: '等待解析', updatedAt: '今天 14:11' },
]

export const learningPlans: LearningPlan[] = [
  {
    id: 1,
    title: 'Java 面向对象期末复习方案',
    goal: '两周内掌握继承、多态、接口，并能完成代码实操题',
    libraryId: 1,
    profile: [
      { label: '专业方向', value: '计算机相关' },
      { label: '知识基础', value: 'Java 基础一般，OOP 概念不稳定' },
      { label: '学习目标', value: '两周内完成期末复习' },
      { label: '认知偏好', value: '图解 + 代码案例' },
      { label: '易错点', value: '继承、多态、接口、抽象类' },
      { label: '学习节奏', value: '需要分阶段推进' },
    ],
    stages: [
      {
        id: 1,
        title: '基础概念补齐',
        duration: '2 天',
        goal: '分清类、对象、封装、构造方法的职责',
        resources: ['个性化讲义', '概念速查卡'],
        status: 'done',
      },
      {
        id: 2,
        title: '继承与多态',
        duration: '4 天',
        goal: '理解方法重写、向上转型和动态绑定',
        resources: ['思维导图', '代码案例', '分层练习题'],
        status: 'active',
      },
      {
        id: 3,
        title: '接口与抽象类',
        duration: '3 天',
        goal: '掌握接口设计、抽象类复用和适用场景',
        resources: ['对比文档', '代码阅读题'],
        status: 'pending',
      },
      {
        id: 4,
        title: '综合项目练习',
        duration: '5 天',
        goal: '完成学生成绩管理案例并复盘错题',
        resources: ['实践项目', '阶段测评'],
        status: 'pending',
      },
    ],
    resources: [
      {
        id: 1,
        group: '文档',
        title: 'Java OOP 个性化讲义',
        desc: '按薄弱点重排继承、多态、接口的讲解顺序',
        action: '查看',
      },
      {
        id: 2,
        group: '结构图',
        title: '继承与多态知识点思维导图',
        desc: '把 OOP 核心概念整理为可视化结构',
        action: '打开导图',
      },
      {
        id: 3,
        group: '练习',
        title: '分层练习题 30 道',
        desc: '选择题、判断题、代码阅读题和编程题',
        action: '开始练习',
      },
      {
        id: 4,
        group: '实操',
        title: '学生成绩管理代码案例',
        desc: '覆盖继承、接口、多态和异常处理',
        action: '查看案例',
      },
    ],
    agents: [
      { name: '画像分析', desc: '已识别学习目标和薄弱点', status: 'done' },
      { name: '资料理解', desc: '已分析 12 个文件和 328 个知识片段', status: 'done' },
      { name: '资源生成', desc: '讲义、导图、题库和案例已生成', status: 'done' },
      { name: '路径规划', desc: '已生成 4 阶段学习路径', status: 'done' },
      { name: '内容审核', desc: '已完成引用和安全检查', status: 'done' },
    ],
  },
]

