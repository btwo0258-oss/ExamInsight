export type AgentStatus = 'done' | 'running' | 'pending'

export type RecentConversation = {
  id: number
  title: string
  desc: string
  sourceLabel: string
  sourceType: 'chat' | 'learning'
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

export type LearningResourceStatus = '已生成' | '未选择' | '生成中' | '生成失败'
export type LearningProjectStatus = '待开启' | '进行中' | '已生成' | '已完成' | '待完善'

export type LearningTask = {
  id: number
  title: string
  duration: string
  done: boolean
  type: '讲解' | '资料' | '练习' | '测验' | '案例'
  resourceId?: number
  exerciseIds?: number[]
  status?: '未开始' | '进行中' | '已完成'
  completionMode?: 'content' | 'resource' | 'exercise' | 'assessment' | 'case' | 'manual'
  completionSource?: string
  readProgress?: number
  validStudySeconds?: number
  completedActions?: string[]
}

export type LearningStage = {
  id: number
  title: string
  desc: string
  scheduleLabel?: string
  tasks: LearningTask[]
}

export type LearningResource = {
  id: number
  group: '学习方案' | '个性化学习手册' | 'PPT' | '思维导图' | '代码案例' | '图片'
  title: string
  desc: string
  status: LearningResourceStatus
  action: string
  fileName?: string
  content?: string
  previewUrl?: string
  mindMapId?: number
  mindMapTreeData?: unknown
  presentationId?: string
  errorMessage?: string
}

export type ExerciseType = '单选题' | '多选题' | '判断题' | '填空题' | '简答题' | '代码题'
export type CodeLanguageKey = 'java' | 'python' | 'javascript' | 'c' | 'cpp' | 'csharp' | 'go'

export type CodeLanguageOption = {
  key: CodeLanguageKey
  label: string
  runtime: string
  starterCode: string
  referenceAnswer: string
  requiredCodePatterns: string[]
}

export type Exercise = {
  id: number
  title: string
  knowledge: string
  difficulty: '基础' | '中等' | '提高' | '进阶' | '挑战'
  type: ExerciseType
  code?: string
  options: string[]
  answer: string
  acceptedAnswers?: string[]
  gradingKeywords?: string[]
  gradingRubric?: string[]
  passingScore?: number
  language?: string
  runtime?: string
  starterCode?: string
  requiredCodePatterns?: string[]
  sampleTests?: Array<{ input: string; expected: string }>
  codeLanguages?: CodeLanguageOption[]
  selectedLanguage?: CodeLanguageKey
  codeDrafts?: Partial<Record<CodeLanguageKey, string>>
  userAnswer?: string
  draftAnswer?: string
  submitted?: boolean
  gradingCorrect?: boolean
  gradingScore?: number
  gradingFeedback?: string
  explanation: string
  scene?: 'checkpoint' | 'practice' | 'assessment'
  sourceExerciseId?: number
  cognitiveLevel?: '概念理解' | '直接应用' | '综合迁移'
  purpose?: '随堂检查' | '阶段练习' | '阶段测验' | '备用题' | '错题巩固' | '追加练习'
  generationBatch?: string
  sourceTaskId?: number
}

export function createCodeLanguageOptions(task: 'verify' | 'countDogs' = 'verify', variant = 1): CodeLanguageOption[] {
  if (task === 'countDogs') {
    return [
      { key: 'java', label: 'Java', runtime: 'Java 17', starterCode: 'class Solution {\n  int countDogs(Animal[] animals) {\n    // 在这里编写\n    return 0;\n  }\n}', referenceAnswer: 'class Solution { int countDogs(Animal[] animals) { int count = 0; for (Animal animal : animals) if (animal instanceof Dog) count++; return count; } }', requiredCodePatterns: ['instanceof Dog', 'return count'] },
      { key: 'python', label: 'Python', runtime: 'Python 3.12', starterCode: 'class Solution:\n    def count_dogs(self, animals):\n        # 在这里编写\n        return 0', referenceAnswer: 'class Solution:\n    def count_dogs(self, animals):\n        count = sum(1 for animal in animals if isinstance(animal, Dog))\n        return count', requiredCodePatterns: ['isinstance(', 'Dog', 'return'] },
      { key: 'javascript', label: 'JavaScript', runtime: 'Node.js 22', starterCode: 'class Solution {\n  countDogs(animals) {\n    // 在这里编写\n    return 0;\n  }\n}', referenceAnswer: 'class Solution { countDogs(animals) { return animals.filter(animal => animal instanceof Dog).length; } }', requiredCodePatterns: ['instanceof Dog', 'return'] },
      { key: 'c', label: 'C', runtime: 'C 11', starterCode: 'int count_dogs(const Animal *animals, int size) {\n  /* 在这里编写 */\n  return 0;\n}', referenceAnswer: 'int count_dogs(const Animal *animals, int size) { int count = 0; for (int i = 0; i < size; i++) if (animals[i].type == DOG) count++; return count; }', requiredCodePatterns: ['DOG', 'return count'] },
      { key: 'cpp', label: 'C++', runtime: 'C++ 20', starterCode: 'class Solution {\npublic:\n  int countDogs(const std::vector<Animal*>& animals) {\n    // 在这里编写\n    return 0;\n  }\n};', referenceAnswer: 'class Solution { public: int countDogs(const std::vector<Animal*>& animals) { int count = 0; for (auto animal : animals) if (dynamic_cast<Dog*>(animal)) count++; return count; } };', requiredCodePatterns: ['dynamic_cast<Dog*>', 'return count'] },
      { key: 'csharp', label: 'C#', runtime: '.NET 8', starterCode: 'class Solution {\n  public int CountDogs(Animal[] animals) {\n    // 在这里编写\n    return 0;\n  }\n}', referenceAnswer: 'class Solution { public int CountDogs(Animal[] animals) { int count = 0; foreach (var animal in animals) if (animal is Dog) count++; return count; } }', requiredCodePatterns: ['is Dog', 'return count'] },
      { key: 'go', label: 'Go', runtime: 'Go 1.23', starterCode: 'func countDogs(animals []Animal) int {\n    // 在这里编写\n    return 0\n}', referenceAnswer: 'func countDogs(animals []Animal) int { count := 0; for _, animal := range animals { if _, ok := animal.(Dog); ok { count++ } }; return count }', requiredCodePatterns: ['animal.(Dog)', 'return count'] },
    ]
  }

  return [
    { key: 'java', label: 'Java', runtime: 'Java 17', starterCode: `class Solution {\n  boolean verify${variant}(Object value) {\n    // 在这里编写\n    return false;\n  }\n}`, referenceAnswer: `class Solution { boolean verify${variant}(Object value) { return value != null; } }`, requiredCodePatterns: ['value != null', 'return'] },
    { key: 'python', label: 'Python', runtime: 'Python 3.12', starterCode: `class Solution:\n    def verify_${variant}(self, value):\n        # 在这里编写\n        return False`, referenceAnswer: `class Solution:\n    def verify_${variant}(self, value):\n        return value is not None`, requiredCodePatterns: ['is not None', 'return'] },
    { key: 'javascript', label: 'JavaScript', runtime: 'Node.js 22', starterCode: `class Solution {\n  verify${variant}(value) {\n    // 在这里编写\n    return false;\n  }\n}`, referenceAnswer: `class Solution { verify${variant}(value) { return value !== null; } }`, requiredCodePatterns: ['value !== null', 'return'] },
    { key: 'c', label: 'C', runtime: 'C 11', starterCode: `bool verify_${variant}(const void *value) {\n  /* 在这里编写 */\n  return false;\n}`, referenceAnswer: `bool verify_${variant}(const void *value) { return value != NULL; }`, requiredCodePatterns: ['value != NULL', 'return'] },
    { key: 'cpp', label: 'C++', runtime: 'C++ 20', starterCode: `class Solution {\npublic:\n  bool verify${variant}(const void* value) {\n    // 在这里编写\n    return false;\n  }\n};`, referenceAnswer: `class Solution { public: bool verify${variant}(const void* value) { return value != nullptr; } };`, requiredCodePatterns: ['value != nullptr', 'return'] },
    { key: 'csharp', label: 'C#', runtime: '.NET 8', starterCode: `class Solution {\n  public bool Verify${variant}(object? value) {\n    // 在这里编写\n    return false;\n  }\n}`, referenceAnswer: `class Solution { public bool Verify${variant}(object? value) { return value != null; } }`, requiredCodePatterns: ['value != null', 'return'] },
    { key: 'go', label: 'Go', runtime: 'Go 1.23', starterCode: `func verify${variant}(value any) bool {\n    // 在这里编写\n    return false\n}`, referenceAnswer: `func verify${variant}(value any) bool { return value != nil }`, requiredCodePatterns: ['value != nil', 'return'] },
  ]
}

export type QuestionBankConfig = {
  targetCount: number
  initialCount: number
  generatedCount: number
  difficultyStrategy: '基础为主' | '均衡' | '强化提高'
  difficultyCounts: { basic: number; advanced: number; challenge: number }
  typeCounts?: Partial<Record<ExerciseType, number>>
  generatedAt: string
}

export type TrainingSet = {
  id: number
  title: string
  exerciseIds: number[]
  status: '答题中' | '已交卷' | '待练习'
  source: '专项训练' | '错题巩固'
  knowledge: string
  difficulty: string
  questionType: string
  createdAt: string
}

export type WrongQuestion = {
  id: number
  title: string
  knowledge: string[]
  userAnswer: string
  correctAnswer: string
  answerLanguage?: CodeLanguageKey
  reason: string
  synced: boolean
  status?: '需巩固' | '已掌握'
  errorCount?: number
  reviewCount?: number
  correctStreak?: number
  lastWrongAt?: string
  reviewHistory?: Array<{
    date: string
    correct: boolean
    answer: string
  }>
}

export type WrongReviewSet = {
  id: number
  title: string
  exerciseIds: number[]
  sourceWrongIds: number[]
  status: '待作答' | '作答中' | '已完成'
  createdAt: string
  difficultyMode: '保持难度' | '逐步提升'
  correctRate?: number
}

export type LearningPlan = {
  id: number
  relatedProjectId?: number | null
  title: string
  icon?: string
  iconColor?: string
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
  stages: LearningStage[]
  resources: LearningResource[]
  exercises: Exercise[]
  questionBank?: QuestionBankConfig
  trainingSets?: TrainingSet[]
  wrongQuestions: WrongQuestion[]
  wrongReviewSets?: WrongReviewSet[]
  dashboard: Array<{ label: string; value: number }>
  agents: Array<{
    name: string
    desc: string
    status: AgentStatus
  }>
}

export const recentConversations: RecentConversation[] = [
  { id: 101, title: 'Java 多态问题', desc: '父类引用与动态绑定', sourceLabel: 'Java OOP 继承与多态强化计划', sourceType: 'learning' },
  { id: 102, title: '接口和抽象类区别', desc: '适用场景对比', sourceLabel: 'Java OOP 继承与多态强化计划', sourceType: 'learning' },
  { id: 103, title: '实验报告内容与步骤', desc: '根据模板整理报告', sourceLabel: '普通对话', sourceType: 'chat' },
  { id: 104, title: '二叉树遍历复习', desc: '前序、中序、后序对比', sourceLabel: '数据结构期末复习', sourceType: 'learning' },
  { id: 105, title: 'PPT 制作方案调整', desc: '汇报结构与页面标题', sourceLabel: '普通对话', sourceType: 'chat' },
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
    scene: 'checkpoint',
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
    scene: 'practice',
    title: '关于方法重写，下列说法正确的是？',
    knowledge: '方法重写',
    difficulty: '基础',
    type: '单选题',
    options: ['A. 子类方法名可以不同', 'B. 参数列表必须一致', 'C. 返回值必须完全不同', 'D. private 方法可以被重写'],
    answer: 'B. 参数列表必须一致',
    explanation: '方法重写要求方法名和参数列表一致，返回值类型需满足兼容规则。',
  },
  {
    id: 3,
    scene: 'practice',
    title: '子类构造方法执行前，通常会先发生什么？',
    knowledge: '继承',
    difficulty: '基础',
    type: '单选题',
    options: ['A. 调用父类构造方法', 'B. 销毁父类对象', 'C. 跳过字段初始化', 'D. 自动变成接口'],
    answer: 'A. 调用父类构造方法',
    explanation: '创建子类对象时，会先完成父类部分的初始化，再执行子类构造逻辑。',
  },
  {
    id: 4,
    scene: 'practice',
    title: '向上转型后，实例方法调用主要由什么决定？',
    knowledge: '动态绑定',
    difficulty: '中等',
    type: '单选题',
    options: ['A. 引用变量名', 'B. 运行时对象类型', 'C. 文件名', 'D. 构造方法数量'],
    answer: 'B. 运行时对象类型',
    explanation: '被重写的实例方法通过动态绑定，根据运行时对象类型选择具体实现。',
  },
  {
    id: 5,
    scene: 'practice',
    title: '接口的主要职责是什么？',
    knowledge: '接口',
    difficulty: '基础',
    type: '单选题',
    options: ['A. 保存所有对象状态', 'B. 定义能力契约', 'C. 替代所有类', 'D. 直接创建实例'],
    answer: 'B. 定义能力契约',
    explanation: '接口用于描述实现者应提供的能力和行为契约。',
  },
  {
    id: 6,
    scene: 'assessment',
    title: '关于继承和组合，下列说法更合理的是？',
    knowledge: '继承',
    difficulty: '中等',
    type: '单选题',
    options: ['A. 所有复用都使用继承', 'B. is-a 关系适合继承', 'C. 组合不能复用代码', 'D. 两者完全相同'],
    answer: 'B. is-a 关系适合继承',
    explanation: '继承更适合稳定的 is-a 关系；一般复用场景应同时评估组合。',
  },
  {
    id: 7,
    scene: 'assessment',
    title: '父类引用指向子类对象体现了什么能力？',
    knowledge: '多态',
    difficulty: '中等',
    type: '单选题',
    options: ['A. 封装', 'B. 多态', 'C. 序列化', 'D. 重载'],
    answer: 'B. 多态',
    explanation: '父类引用可以指向不同子类对象，并通过统一接口表现不同运行时行为。',
  },
  {
    id: 8,
    scene: 'assessment',
    title: 'Java 接口可以直接通过 new 创建对象。',
    knowledge: '接口',
    difficulty: '基础',
    type: '判断题',
    options: ['正确', '错误'],
    answer: '错误',
    explanation: '接口不能直接实例化，需要由实现类创建对象。',
  },
  {
    id: 9,
    scene: 'practice',
    title: '下面哪些描述符合Java多态的特征？',
    knowledge: '多态',
    difficulty: '进阶',
    type: '多选题',
    options: ['A. 父类引用可以指向子类对象', 'B. 重写方法在运行时动态绑定', 'C. private方法可以被子类重写', 'D. 同一引用可表现不同行为'],
    answer: 'A. 父类引用可以指向子类对象||B. 重写方法在运行时动态绑定||D. 同一引用可表现不同行为',
    explanation: '多态依赖向上转型和动态绑定；private方法不会被继承，因此不能被重写。',
  },
  {
    id: 10,
    scene: 'practice',
    title: 'Java中用于声明类继承关系的关键字是____。',
    knowledge: '继承',
    difficulty: '基础',
    type: '填空题',
    options: [],
    answer: 'extends',
    acceptedAnswers: ['extends'],
    explanation: 'Java使用extends声明类继承关系。',
  },
  {
    id: 11,
    scene: 'assessment',
    title: '请简要说明为什么Animal引用调用sound方法时可能执行Dog中的实现。',
    knowledge: '动态绑定',
    difficulty: '进阶',
    type: '简答题',
    options: [],
    answer: '引用的编译类型是Animal，运行时对象是Dog；被重写的实例方法会根据运行时对象进行动态绑定。',
    gradingKeywords: ['运行时对象', '重写', '动态绑定'],
    gradingRubric: ['说明引用类型与对象类型的区别', '指出方法已经被子类重写', '说明运行时动态绑定机制'],
    passingScore: 80,
    explanation: '关键是区分编译期引用类型与运行时对象类型，并说明重写方法的动态绑定。',
  },
  {
    id: 12,
    scene: 'assessment',
    title: '补全方法：返回Animal数组中运行时类型为Dog的对象数量。',
    knowledge: '多态',
    difficulty: '挑战',
    type: '代码题',
    options: [],
    answer: `class Solution {
  int countDogs(Animal[] animals) {
    int count = 0;
    for (Animal animal : animals) {
      if (animal instanceof Dog) count++;
    }
    return count;
  }
}`,
    language: 'Java',
    runtime: 'Java 17',
    starterCode: `class Solution {
  int countDogs(Animal[] animals) {
    // 在这里编写
    return 0;
  }
}`,
    requiredCodePatterns: ['instanceof Dog', 'return count'],
    codeLanguages: createCodeLanguageOptions('countDogs'),
    selectedLanguage: 'java',
    codeDrafts: {},
    sampleTests: [
      { input: '[new Dog(), new Animal(), new Dog()]', expected: '2' },
      { input: '[]', expected: '0' },
    ],
    explanation: '遍历数组并使用instanceof检查对象的运行时类型，累计Dog对象数量。',
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
      { label: '学习目标', value: '考试复习' },
      { label: '当前基础', value: '基础一般' },
      { label: '重点知识', value: '继承 / 多态 / 接口' },
      { label: '时间安排', value: '3 天，每天 60-90 分钟' },
      { label: '学习方式', value: '图文讲解 + 练习驱动' },
    ],
    stages: [
      {
        id: 1,
        title: '基础概念补齐',
        desc: '分清类、对象、继承、构造方法的职责',
        scheduleLabel: '建议第 1 天完成',
        tasks: [
          { id: 1, title: '理解继承的语法与职责', duration: '30 分钟', done: true, type: '讲解' },
          { id: 2, title: '阅读个性化学习手册', duration: '25 分钟', done: true, type: '资料' },
          { id: 3, title: '完成 8 道基础题', duration: '30 分钟', done: false, type: '练习' },
        ],
      },
      {
        id: 2,
        title: '继承与多态专项',
        desc: '理解方法重写、向上转型和动态绑定',
        scheduleLabel: '建议第 1～2 天完成',
        tasks: [
          { id: 4, title: '动态绑定讲解', duration: '30 分钟', done: false, type: '讲解' },
          { id: 5, title: '代码案例练习', duration: '40 分钟', done: false, type: '案例' },
          { id: 6, title: '专项练习 12 题', duration: '35 分钟', done: false, type: '练习' },
        ],
      },
      {
        id: 3,
        title: '综合测验',
        desc: '通过综合训练检验核心知识掌握情况',
        scheduleLabel: '建议第 3 天完成',
        tasks: [
          { id: 7, title: '综合测验', duration: '35 分钟', done: false, type: '测验' },
        ],
      },
    ],
    resources: [
      {
        id: 1,
        group: '个性化学习手册',
        title: '继承与多态个性化学习手册',
        desc: '按你的薄弱点重排继承、多态、接口的讲解顺序。',
        status: '已生成',
        action: '查看',
        fileName: '继承与多态个性化学习手册.md',
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
    ],
    exercises: javaExercises,
    wrongQuestions: [
      {
        id: 1,
        title: '下面代码的输出结果是？',
        knowledge: ['动态绑定', '方法重写', '向上转型'],
        userAnswer: 'A. Animal',
        correctAnswer: 'B. Dog',
        reason: '把变量的编译类型和对象的运行时类型混淆，忽略了方法调用会在运行时绑定到子类实现。',
        synced: false,
        status: '需巩固',
        errorCount: 3,
        reviewCount: 2,
        correctStreak: 0,
        lastWrongAt: '今天 10:30',
        reviewHistory: [
          { date: '今天 10:30', correct: false, answer: 'A. Animal' },
          { date: '昨天 19:20', correct: false, answer: 'C. 编译错误' },
        ],
      },
      {
        id: 8,
        title: '接口能否直接实例化',
        knowledge: ['接口实现', '抽象类型'],
        userAnswer: '正确',
        correctAnswer: '错误',
        reason: '没有区分接口作为能力契约与实现类作为具体对象的职责；接口本身不能通过 new 创建实例。',
        synced: false,
        status: '需巩固',
        errorCount: 1,
        reviewCount: 0,
        correctStreak: 0,
        lastWrongAt: '今天 09:45',
        reviewHistory: [],
      },
      {
        id: 3,
        title: '子类构造方法执行前，通常会先发生什么？',
        knowledge: ['继承', '构造方法'],
        userAnswer: 'C. 跳过字段初始化',
        correctAnswer: 'A. 调用父类构造方法',
        reason: '把子类的构造方法当成了完全独立的初始化过程，忽略了创建子类对象时需要先完成父类部分初始化。',
        synced: false,
        status: '需巩固',
        errorCount: 2,
        reviewCount: 1,
        correctStreak: 1,
        lastWrongAt: '昨天 21:10',
        reviewHistory: [{ date: '今天 08:20', correct: true, answer: 'A. 调用父类构造方法' }],
      },
      {
        id: 2,
        title: '关于方法重写，下列说法正确的是？',
        knowledge: ['方法重写'],
        userAnswer: 'D. private 方法可以被重写',
        correctAnswer: 'B. 参数列表必须一致',
        reason: '混淆了重写、重载和方法可见性。private 方法不会被子类继承，因此不存在对它的重写。',
        synced: false,
        status: '已掌握',
        errorCount: 1,
        reviewCount: 2,
        correctStreak: 2,
        lastWrongAt: '3 天前',
        reviewHistory: [
          { date: '昨天 18:30', correct: true, answer: 'B. 参数列表必须一致' },
          { date: '2 天前', correct: true, answer: 'B. 参数列表必须一致' },
        ],
      },
      {
        id: 4,
        title: '向上转型后，实例方法调用主要由什么决定？',
        knowledge: ['向上转型', '动态绑定'],
        userAnswer: 'A. 引用变量名',
        correctAnswer: 'B. 运行时对象类型',
        reason: '过度关注引用的声明形式，没有根据运行时对象判断被重写实例方法的实际调用目标。',
        synced: false,
        status: '需巩固',
        errorCount: 1,
        reviewCount: 0,
        correctStreak: 0,
        lastWrongAt: '昨天 16:40',
        reviewHistory: [],
      },
      {
        id: 6,
        title: '关于继承和组合，下列说法更合理的是？',
        knowledge: ['继承', '组合', '面向对象设计'],
        userAnswer: 'A. 所有复用都使用继承',
        correctAnswer: 'B. is-a 关系适合继承',
        reason: '把代码复用等同于继承，忽略了继承会引入更强的类型耦合。当关系不是稳定的 is-a 时，组合通常更容易维护和替换实现。',
        synced: false,
        status: '需巩固',
        errorCount: 2,
        reviewCount: 1,
        correctStreak: 1,
        lastWrongAt: '2 天前',
        reviewHistory: [{ date: '昨天 14:15', correct: true, answer: 'B. is-a 关系适合继承' }],
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
      { name: '资源生成', desc: '生成个性化学习手册、练习题、导图和案例', status: 'done' },
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
    stages: [],
    resources: [
      { id: 1, group: '个性化学习手册', title: '数据结构个性化学习手册', desc: '按章节整理核心概念。', status: '已生成', action: '查看' },
      { id: 2, group: 'PPT', title: '期末复习 PPT', desc: '适合快速串讲。', status: '已生成', action: '查看' },
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
    goal: '基于实验资料生成文件处理实验报告、代码案例和导出报告。',
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
    stages: [],
    resources: [
      { id: 1, group: '代码案例', title: '文件处理代码案例', desc: '读写 CSV 与异常处理。', status: '已生成', action: '查看' },
      { id: 2, group: '个性化学习手册', title: '实验报告个性化学习手册', desc: '报告结构和步骤说明。', status: '已生成', action: '查看' },
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
    stages: [],
    resources: [
      { id: 2, group: '个性化学习手册', title: '薄弱点个性化学习手册', desc: '整理 TCP/IP 常见误区。', status: '未选择', action: '生成' },
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
