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

export type LearningResourceStatus = '已生成' | '未选择' | '生成中'
export type LearningProjectStatus = '进行中' | '已生成' | '已完成' | '待完善'

export type LearningTask = {
  id: number
  title: string
  duration: string
  done: boolean
  type: '讲解' | '资料' | '练习' | '复盘' | '测验' | '案例'
}

export type LearningDay = {
  id: number
  title: string
  desc: string
  tasks: LearningTask[]
}

export type LearningResource = {
  id: number
  group: '讲义' | 'PPT' | '练习题' | '思维导图' | '代码案例' | '拓展阅读' | '导出文件'
  title: string
  desc: string
  status: LearningResourceStatus
  action: string
  fileName?: string
}

export type Exercise = {
  id: number
  title: string
  knowledge: string
  difficulty: '基础' | '中等' | '提高'
  type: '单选题' | '代码题' | '判断题'
  code?: string
  options: string[]
  answer: string
  userAnswer?: string
  explanation: string
}

export type WrongQuestion = {
  id: number
  title: string
  knowledge: string[]
  userAnswer: string
  correctAnswer: string
  reason: string
  synced: boolean
}

export type LearningPlan = {
  id: number
  title: string
  goal: string
  updatedAt: string
  libraryId: number
  status: LearningProjectStatus
  period: string
  targetType: string
  progress: number
  taskDone: number
  totalTasks: number
  exerciseDone: number
  totalExercises: number
  correctRate: number
  weeklyHours: string
  profile: Array<{ label: string; value: string }>
  days: LearningDay[]
  resources: LearningResource[]
  exercises: Exercise[]
  wrongQuestions: WrongQuestion[]
  dashboard: Array<{ label: string; value: number }>
  agents: Array<{
    name: string
    desc: string
    status: AgentStatus
  }>
}

export const recentConversations: RecentConversation[] = [
  { id: 101, title: 'Java 多态问题', desc: '父类引用与动态绑定' },
  { id: 102, title: '接口和抽象类区别', desc: '适用场景对比' },
  { id: 103, title: '实验报告内容与步骤', desc: '根据模板整理报告' },
]

export const courseLibraries: CourseLibrary[] = [
  {
    id: 1,
    name: 'Java 面向对象资料库',
    course: 'Java 面向对象程序设计',
    description: '课程讲义、代码案例、期末复习资料和错题整理',
    fileCount: 12,
    chunkCount: 328,
    status: 'ready',
    updatedAt: '今天 14:20',
    tags: ['类与对象', '继承', '多态', '接口'],
  },
  {
    id: 2,
    name: '数据结构资料库',
    course: '数据结构',
    description: '线性表、树、图、查找与排序相关课件和习题',
    fileCount: 18,
    chunkCount: 476,
    status: 'ready',
    updatedAt: '今天 09:12',
    tags: ['树', '图', '排序', '查找'],
  },
  {
    id: 3,
    name: 'Python 文件处理资料库',
    course: 'Python 程序设计',
    description: '实验报告模板、文件读写案例和项目实操材料',
    fileCount: 9,
    chunkCount: 188,
    status: 'processing',
    updatedAt: '昨天 18:45',
    tags: ['文件处理', '异常', '实验报告'],
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
  { id: 303, name: '接口练习题.md', type: 'Markdown', status: '等待解析', updatedAt: '今天 14:11' },
]

const javaExercises: Exercise[] = [
  {
    id: 1,
    title: '下面代码的输出结果是？',
    knowledge: '动态绑定',
    difficulty: '中等',
    type: '单选题',
    code: `class Animal {
  void sound() { System.out.println("Animal"); }
}

class Dog extends Animal {
  @Override
  void sound() { System.out.println("Dog"); }
}

Animal a = new Dog();
a.sound();`,
    options: ['A. Animal', 'B. Dog', 'C. 编译错误', 'D. 运行时异常'],
    answer: 'B. Dog',
    userAnswer: 'A. Animal',
    explanation: '变量 a 的编译类型是 Animal，但运行时对象是 Dog。JVM 会根据运行时对象类型调用被重写后的 sound 方法。',
  },
  {
    id: 2,
    title: '关于方法重写，下列说法正确的是？',
    knowledge: '方法重写',
    difficulty: '基础',
    type: '单选题',
    options: ['A. 子类方法名可以不同', 'B. 参数列表必须一致', 'C. 返回值必须完全不同', 'D. private 方法可以被重写'],
    answer: 'B. 参数列表必须一致',
    explanation: '方法重写要求方法名和参数列表一致，返回值类型需满足兼容规则。',
  },
]

export const learningPlans: LearningPlan[] = [
  {
    id: 1,
    title: 'Java OOP 继承与多态强化计划',
    goal: '3 天内补齐继承、多态、接口相关薄弱点，完成考试前专项复习。',
    updatedAt: '今天 10:30',
    libraryId: 1,
    status: '进行中',
    period: '3 天复习计划',
    targetType: '考试复习',
    progress: 35,
    taskDone: 3,
    totalTasks: 9,
    exerciseDone: 7,
    totalExercises: 20,
    correctRate: 68,
    weeklyHours: '6.5h',
    profile: [
      { label: '专业方向', value: '计算机科学与技术' },
      { label: '知识基础', value: '中等' },
      { label: '薄弱点', value: '继承 / 接口' },
      { label: '学习偏好', value: '图文 + 代码' },
      { label: '目标', value: '考试复习' },
      { label: '节奏', value: '每天 60-90 分钟' },
    ],
    days: [
      {
        id: 1,
        title: '基础概念补齐',
        desc: '分清类、对象、继承、构造方法的职责',
        tasks: [
          { id: 1, title: '理解继承的语法与职责', duration: '30 分钟', done: true, type: '讲解' },
          { id: 2, title: '阅读个性化讲义', duration: '25 分钟', done: true, type: '资料' },
          { id: 3, title: '完成 8 道基础题', duration: '30 分钟', done: false, type: '练习' },
        ],
      },
      {
        id: 2,
        title: '继承与多态专项',
        desc: '理解方法重写、向上转型和动态绑定',
        tasks: [
          { id: 4, title: '动态绑定讲解', duration: '30 分钟', done: false, type: '讲解' },
          { id: 5, title: '代码案例练习', duration: '40 分钟', done: false, type: '案例' },
          { id: 6, title: '专项练习 12 题', duration: '35 分钟', done: false, type: '练习' },
        ],
      },
      {
        id: 3,
        title: '综合测验与错题复盘',
        desc: '综合训练、错题整理与强化巩固',
        tasks: [
          { id: 7, title: '综合测验', duration: '35 分钟', done: false, type: '测验' },
          { id: 8, title: '错题整理', duration: '25 分钟', done: false, type: '复盘' },
          { id: 9, title: '生成复盘报告', duration: '15 分钟', done: false, type: '复盘' },
        ],
      },
    ],
    resources: [
      {
        id: 1,
        group: '讲义',
        title: '继承与多态个性化讲义',
        desc: '按你的薄弱点重排继承、多态、接口的讲解顺序。',
        status: '已生成',
        action: '查看',
        fileName: '继承与多态个性化讲义.docx',
      },
      {
        id: 2,
        group: 'PPT',
        title: '继承与多态考前复习 PPT',
        desc: '适合课前快速过一遍概念、代码和常见陷阱。',
        status: '未选择',
        action: '生成',
        fileName: '继承与多态考前复习.pptx',
      },
      {
        id: 3,
        group: '练习题',
        title: '继承与多态专项练习',
        desc: '包含单选、判断、代码阅读和综合应用题。',
        status: '已生成',
        action: '练习',
        fileName: '继承与多态专项练习.docx',
      },
      {
        id: 4,
        group: '思维导图',
        title: 'OOP 知识点结构图',
        desc: '把继承、多态、接口和抽象类关系整理为可视化结构。',
        status: '已生成',
        action: '打开',
        fileName: 'OOP知识点结构图.xmind',
      },
      {
        id: 5,
        group: '代码案例',
        title: '动态绑定代码案例',
        desc: '通过 Animal/Dog 示例理解运行时方法绑定。',
        status: '已生成',
        action: '查看',
        fileName: '动态绑定代码案例.zip',
      },
      {
        id: 6,
        group: '拓展阅读',
        title: 'Java 多态深入阅读',
        desc: '补充 JVM 调用机制、重写和重载对比。',
        status: '未选择',
        action: '生成',
        fileName: 'Java多态深入阅读.pdf',
      },
      {
        id: 7,
        group: '导出文件',
        title: '复习计划与资源汇总',
        desc: '一键导出当前学习计划、资源清单和掌握度报告。',
        status: '未选择',
        action: '导出',
        fileName: 'Java OOP 强化计划.md',
      },
    ],
    exercises: javaExercises,
    wrongQuestions: [
      {
        id: 1,
        title: 'Animal a = new Dog(); a.sound(); 的输出结果',
        knowledge: ['动态绑定', '方法重写', '向上转型'],
        userAnswer: 'A. Animal',
        correctAnswer: 'B. Dog',
        reason: '把变量的编译类型和对象的运行时类型混淆，忽略了方法调用会在运行时绑定到子类实现。',
        synced: true,
      },
      {
        id: 2,
        title: '接口能否直接实例化',
        knowledge: ['接口实现', '抽象类型'],
        userAnswer: '可以直接 new 接口',
        correctAnswer: '接口不能直接实例化，需要由实现类创建对象。',
        reason: '没有区分接口作为规范与实现类作为具体对象的职责。',
        synced: false,
      },
    ],
    dashboard: [
      { label: '继承', value: 72 },
      { label: '多态', value: 54 },
      { label: '接口', value: 61 },
    ],
    agents: [
      { name: '画像分析', desc: '识别学习目标和薄弱点', status: 'done' },
      { name: '资料理解', desc: '解析资料库和上传笔记', status: 'done' },
      { name: '路径规划', desc: '生成 3 天学习路径', status: 'done' },
      { name: '资源生成', desc: '生成讲义、练习题、导图和案例', status: 'done' },
      { name: '效果评估', desc: '根据练习表现动态更新', status: 'running' },
    ],
  },
  {
    id: 2,
    title: '数据结构期末复习',
    goal: '7 天梳理树、图、排序与查找，完成期末考试前系统复习。',
    updatedAt: '今天 09:12',
    libraryId: 2,
    status: '已生成',
    period: '7 天计划',
    targetType: '期末考试',
    progress: 0,
    taskDone: 0,
    totalTasks: 12,
    exerciseDone: 0,
    totalExercises: 30,
    correctRate: 0,
    weeklyHours: '0h',
    profile: [
      { label: '知识基础', value: '中等' },
      { label: '薄弱点', value: '树 / 图' },
      { label: '学习偏好', value: '题目驱动' },
    ],
    days: [],
    resources: [
      { id: 1, group: '讲义', title: '数据结构速查讲义', desc: '按章节整理核心概念。', status: '已生成', action: '查看' },
      { id: 2, group: 'PPT', title: '期末复习 PPT', desc: '适合快速串讲。', status: '已生成', action: '查看' },
      { id: 3, group: '练习题', title: '数据结构题库', desc: '30 道专项题。', status: '已生成', action: '练习' },
    ],
    exercises: [],
    wrongQuestions: [],
    dashboard: [
      { label: '树', value: 0 },
      { label: '图', value: 0 },
      { label: '排序', value: 0 },
    ],
    agents: [],
  },
  {
    id: 3,
    title: 'Python 实验报告辅助',
    goal: '基于实验资料生成文件处理实验报告、代码案例和导出文件。',
    updatedAt: '昨天 18:45',
    libraryId: 3,
    status: '已完成',
    period: '项目实战',
    targetType: '课程作业',
    progress: 100,
    taskDone: 10,
    totalTasks: 10,
    exerciseDone: 15,
    totalExercises: 15,
    correctRate: 92,
    weeklyHours: '2.5h',
    profile: [
      { label: '学习偏好', value: '案例 + 报告模板' },
      { label: '目标', value: '课程作业' },
    ],
    days: [],
    resources: [
      { id: 1, group: '代码案例', title: '文件处理代码案例', desc: '读写 CSV 与异常处理。', status: '已生成', action: '查看' },
      { id: 2, group: '讲义', title: '实验报告说明', desc: '报告结构和步骤说明。', status: '已生成', action: '查看' },
      { id: 3, group: '导出文件', title: '实验报告导出包', desc: '含 docx 和 markdown。', status: '已生成', action: '下载' },
    ],
    exercises: [],
    wrongQuestions: [],
    dashboard: [
      { label: '文件读写', value: 96 },
      { label: '异常处理', value: 88 },
    ],
    agents: [],
  },
  {
    id: 4,
    title: '计算机网络错题强化',
    goal: '基于错题诊断网络层、传输层薄弱点，生成同类题和复习路径。',
    updatedAt: '昨天 16:20',
    libraryId: 2,
    status: '待完善',
    period: '错题诊断',
    targetType: '补弱',
    progress: 12,
    taskDone: 1,
    totalTasks: 8,
    exerciseDone: 3,
    totalExercises: 25,
    correctRate: 45,
    weeklyHours: '1.2h',
    profile: [
      { label: '薄弱点', value: '网络层 / 传输层' },
      { label: '目标', value: '错题补强' },
    ],
    days: [],
    resources: [
      { id: 1, group: '练习题', title: '网络层同类题', desc: '基于错题生成。', status: '生成中', action: '查看' },
      { id: 2, group: '讲义', title: '薄弱点讲义', desc: '整理 TCP/IP 常见误区。', status: '未选择', action: '生成' },
    ],
    exercises: [],
    wrongQuestions: [],
    dashboard: [
      { label: '网络层', value: 48 },
      { label: '传输层', value: 42 },
    ],
    agents: [],
  },
]
