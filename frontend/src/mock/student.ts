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
  updatedAt: string
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
    updatedAt: '3 分',
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
  {
    id: 2,
    title: '人工智能导论阶段学习方案',
    goal: '梳理搜索算法、机器学习基础和知识表示，完成课堂复盘',
    updatedAt: '昨天',
    libraryId: 2,
    profile: [
      { label: '专业方向', value: '人工智能方向' },
      { label: '知识基础', value: '数学基础一般，算法概念需补齐' },
      { label: '学习目标', value: '完成导论课程阶段复习' },
      { label: '认知偏好', value: '图解 + 案例推演' },
      { label: '易错点', value: '搜索策略、启发式函数、过拟合' },
      { label: '学习节奏', value: '按章节逐步推进' },
    ],
    stages: [
      {
        id: 1,
        title: '搜索算法梳理',
        duration: '2 天',
        goal: '理解 BFS、DFS、A* 的适用条件和差异',
        resources: ['对比讲义', '流程图'],
        status: 'active',
      },
      {
        id: 2,
        title: '机器学习基础',
        duration: '3 天',
        goal: '掌握监督学习、训练集、测试集和模型评估',
        resources: ['概念卡片', '练习题'],
        status: 'pending',
      },
      {
        id: 3,
        title: '知识表示复盘',
        duration: '2 天',
        goal: '整理谓词逻辑、语义网络和产生式系统',
        resources: ['思维导图', '拓展阅读'],
        status: 'pending',
      },
    ],
    resources: [
      {
        id: 1,
        group: '文档',
        title: '搜索算法个性化讲义',
        desc: '把典型搜索算法按适用场景重排讲解',
        action: '查看',
      },
      {
        id: 2,
        group: '结构图',
        title: 'AI 导论知识结构图',
        desc: '串联搜索、学习和知识表示三类内容',
        action: '打开导图',
      },
      {
        id: 3,
        group: '练习',
        title: '导论阶段练习 24 道',
        desc: '覆盖概念判断、算法选择和简答题',
        action: '开始练习',
      },
      {
        id: 4,
        group: '实操',
        title: 'A* 搜索路径案例',
        desc: '用网格路径规划理解启发式搜索',
        action: '查看案例',
      },
    ],
    agents: [
      { name: '画像分析', desc: '已识别导论课学习目标', status: 'done' },
      { name: '资料理解', desc: '已分析 8 个文件和 214 个知识片段', status: 'done' },
      { name: '资源生成', desc: '讲义、导图和练习已生成', status: 'done' },
      { name: '路径规划', desc: '已生成 3 阶段学习路径', status: 'done' },
      { name: '内容审核', desc: '已完成引用和安全检查', status: 'done' },
    ],
  },
  {
    id: 3,
    title: '英语四级阅读强化方案',
    goal: '提升阅读定位、长难句拆解和主旨题判断能力',
    updatedAt: '1 周',
    libraryId: 3,
    profile: [
      { label: '专业方向', value: '大学英语四级' },
      { label: '知识基础', value: '词汇量中等，长难句薄弱' },
      { label: '学习目标', value: '四周内提升阅读正确率' },
      { label: '认知偏好', value: '例题拆解 + 错题复盘' },
      { label: '易错点', value: '主旨题、推断题、细节定位' },
      { label: '学习节奏', value: '每天一篇精读训练' },
    ],
    stages: [
      {
        id: 1,
        title: '词汇与句法补齐',
        duration: '5 天',
        goal: '补齐高频词和长难句拆解方法',
        resources: ['词汇卡片', '句法讲义'],
        status: 'done',
      },
      {
        id: 2,
        title: '题型策略训练',
        duration: '10 天',
        goal: '分别训练细节题、主旨题和推断题',
        resources: ['题型讲义', '分层练习'],
        status: 'active',
      },
      {
        id: 3,
        title: '真题套练复盘',
        duration: '12 天',
        goal: '完成真题练习并沉淀错题原因',
        resources: ['真题训练', '错题清单'],
        status: 'pending',
      },
    ],
    resources: [
      {
        id: 1,
        group: '文档',
        title: '阅读题型策略讲义',
        desc: '按题型总结定位、排除和复盘方法',
        action: '查看',
      },
      {
        id: 2,
        group: '结构图',
        title: '长难句拆解流程图',
        desc: '把主干识别和修饰成分拆成步骤',
        action: '打开导图',
      },
      {
        id: 3,
        group: '练习',
        title: '阅读强化练习 40 道',
        desc: '覆盖细节题、推断题和主旨题',
        action: '开始练习',
      },
      {
        id: 4,
        group: '实操',
        title: '真题精读复盘案例',
        desc: '示范如何从错题反推阅读策略',
        action: '查看案例',
      },
    ],
    agents: [
      { name: '画像分析', desc: '已识别阅读薄弱点', status: 'done' },
      { name: '资料理解', desc: '已分析 36 个文件和 690 个知识片段', status: 'done' },
      { name: '资源生成', desc: '讲义、导图和练习已生成', status: 'done' },
      { name: '路径规划', desc: '已生成 3 阶段学习路径', status: 'done' },
      { name: '内容审核', desc: '已完成引用和安全检查', status: 'done' },
    ],
  },
]
