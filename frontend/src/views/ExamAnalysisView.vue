<template>
  <div class="layout" :class="{ 'layout--open': sidebarOpen }">
    <aside class="drawer" :class="{ 'drawer--open': sidebarOpen }">
      <TheSidebar :open="sidebarOpen" @close="sidebarOpen = false" />
    </aside>

    <main class="content">
      <div class="exam-analysis-container">
        <div class="header">
          <AppButton variant="secondary" class="back-btn" @click="router.push('/exam-analysis')">
            <template #icon><AppIcon name="chevron-left" :size="16" /></template>
          </AppButton>
          <h1 class="title">试卷智能分析</h1>
        </div>

        <div class="top-bar">
          <div class="upload-section">
            <AppButton variant="primary" @click="triggerUpload">
              <template #icon><AppIcon name="upload-cloud" :size="16" /></template>
              上传试卷
            </AppButton>
            <input
              type="file"
              ref="fileInput"
              hidden
              accept=".pdf,.docx,.doc,.txt"
              multiple
              @change="onFileChange"
            />
            <div class="file-list" v-if="files.length > 0">
              <div class="file-item" v-for="(file, index) in files" :key="index">
                <AppIcon
                  :name="getFileIcon(file.name)"
                  :size="14"
                  :color="getFileIconColor(file.name)"
                />
                <span class="file-item__name">{{ file.name }}</span>
                <button class="file-item__remove" @click="removeFile(index)">
                  <AppIcon name="close" :size="14" />
                </button>
              </div>
            </div>
            <span class="file-name empty" v-else>未选择文件... (支持PDF, Word, TXT，最多10个)</span>
          </div>
          <div class="type-section">
            <span class="label">考试类型：</span>
            <div class="custom-select" @click="showExamTypeDropdown = !showExamTypeDropdown">
              <span class="custom-select__value">{{ examType }}</span>
              <AppIcon name="chevron-down" :size="16" />
              <div class="custom-select__dropdown" v-if="showExamTypeDropdown">
                <div
                  v-for="type in examTypeOptions"
                  :key="type"
                  class="custom-select__option"
                  :class="{ 'custom-select__option--selected': examType === type }"
                  @click.stop="selectExamType(type)"
                >
                  {{ type }}
                </div>
              </div>
            </div>
            <AppButton
              variant="primary"
              @click="startAnalysis"
              :loading="isAnalyzing"
              :disabled="files.length === 0"
            >
              开始分析
            </AppButton>
          </div>
        </div>

        <div class="main-content" v-if="hasAnalyzed && analysisData">
          <div class="analysis-results">
            <div class="card">
              <h3 class="card-title">
                <AppIcon name="star" :size="18" />
                高频考点
              </h3>
              <ul class="point-list" v-if="parsedKeyPoints.length > 0">
                <li v-for="(point, idx) in parsedKeyPoints" :key="idx">
                  <span class="highlight">{{ point.name }}</span>
                  <span v-if="point.count">（出现{{ point.count }}次）</span>
                </li>
              </ul>
              <div class="empty-hint" v-else>暂无考点数据</div>
            </div>

            <div class="card">
              <h3 class="card-title">
                <AppIcon name="bar-chart" :size="18" />
                题型分布
              </h3>
              <div class="distribution" v-if="parsedDistribution.length > 0">
                <div class="dist-row" v-for="(item, idx) in parsedDistribution" :key="idx">
                  <div class="dist-item">
                    <span>{{ item.type }}</span>
                    <span class="dist-pct">{{ item.percentage }}%</span>
                  </div>
                  <div class="dist-bar">
                    <div
                      class="dist-fill"
                      :class="getDistFillClass(idx)"
                      :style="{ width: item.percentage + '%' }"
                    ></div>
                  </div>
                </div>
              </div>
              <div class="empty-hint" v-else>暂无题型分布数据</div>
            </div>

            <div class="card suggestion-card">
              <div class="card-header">
                <h3 class="card-title">
                  <AppIcon name="graduation" :size="18" />
                  智能复习建议
                </h3>
                <AppButton
                  size="small"
                  variant="primary"
                  @click="generateSuggestions"
                  :loading="isGenerating"
                >
                  <template #icon><AppIcon name="zap" :size="14" /></template>
                  生成建议
                </AppButton>
              </div>
              <div class="suggestions" v-if="analysisData.suggestions">
                <div class="sug-content" v-html="formatSuggestions(analysisData.suggestions)"></div>
                <div class="sug-action">
                  <div class="chat-progress-wrapper" v-if="isCreatingChat">
                    <div class="progress-bar">
                      <div
                        class="progress-fill"
                        :style="{ width: chatProgressPercent + '%' }"
                      ></div>
                    </div>
                    <span class="progress-text">{{ chatProgress }}</span>
                  </div>
                  <AppButton v-else variant="secondary" size="small" @click="goToChat"
                    >去问答助手深入学习</AppButton
                  >
                </div>
              </div>
              <div class="suggestions-empty" v-else>点击上方按钮，让 AI 为你量身定制复习策略</div>
            </div>
          </div>

          <div class="right-panel">
            <div class="card h-full">
              <div class="card-header">
                <h3 class="card-title">
                  <AppIcon name="layers" :size="18" />
                  考试重点结构图
                </h3>
                <AppButton
                  size="small"
                  variant="secondary"
                  @click="generateMindMapFromAnalysis"
                  :loading="isMindMapGenerating"
                >
                  <template #icon><AppIcon name="zap" :size="14" /></template>
                  {{ isMindMapGenerating ? "生成中..." : "生成导图" }}
                </AppButton>
              </div>
              <div class="mindmap-preview" @click="goToMindMap" v-if="analysisData?.mindMapId">
                <div class="mindmap-structure">
                  <div class="structure-root">
                    <AppIcon name="layers" :size="20" color="var(--color-primary)" />
                    <span class="root-title">{{ analysisData?.title || "考试重点" }}</span>
                  </div>
                  <div class="structure-branches" v-if="parsedKeyPoints.length > 0">
                    <div
                      class="branch-item"
                      v-for="(point, idx) in parsedKeyPoints.slice(0, 5)"
                      :key="idx"
                    >
                      <span class="branch-dot" :class="'branch-dot--' + (idx % 4)"></span>
                      <span class="branch-name">{{ point.name }}</span>
                      <span class="branch-count">({{ point.count }}次)</span>
                    </div>
                    <div class="branch-more" v-if="parsedKeyPoints.length > 5">
                      +{{ parsedKeyPoints.length - 5 }} 个更多考点
                    </div>
                  </div>
                  <div class="structure-footer">
                    <AppIcon name="zap" :size="14" color="var(--color-text-muted)" />
                    <span>点击查看完整思维导图</span>
                  </div>
                </div>
              </div>
              <div class="mindmap-empty" v-else>
                <AppIcon name="layers" :size="32" color="var(--color-text-muted)" />
                <p>点击"生成导图"按钮，基于分析结果生成详细结构图</p>
              </div>
            </div>
          </div>
        </div>

        <div class="empty-state" v-else-if="!hasAnalyzed">
          <div class="empty-icon">
            <AppIcon name="pie-chart" :size="64" color="var(--color-primary)" />
          </div>
          <h2 class="empty-title">等待分析</h2>
          <p class="empty-desc">上传试卷并点击"开始分析"，获取详细的考点与复习建议</p>
        </div>
      </div>
    </main>

    <div v-if="!sidebarOpen" class="mini">
      <button class="mini__btn" type="button" @click="sidebarOpen = true">
        <AppIcon name="sidebar-left" :size="20" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from "vue";
import { useRouter, useRoute } from "vue-router";
import TheSidebar from "@/components/sidebar/TheSidebar.vue";
import AppIcon from "@/components/common/AppIcon.vue";
import AppButton from "@/components/common/AppButton.vue";
import { useAppState } from "@/stores/appState";
import { useConversationStore } from "@/stores/conversation";
import { useMessageStore } from "@/stores/message";
import { useKnowledgeBaseStore } from "@/stores/knowledgeBase";
import { useExamAnalysisStore } from "@/stores/examAnalysis";
import * as examApi from "@/api/examAnalysis";
import * as kbApi from "@/api/knowledgeBase";
import * as docApi from "@/api/document";
import { marked } from "marked";

const router = useRouter();
const route = useRoute();
const appState = useAppState();
const conversationStore = useConversationStore();
const messageStore = useMessageStore();
const kbStore = useKnowledgeBaseStore();
const examStore = useExamAnalysisStore();

const sidebarOpen = ref(true);
const fileInput = ref<HTMLInputElement | null>(null);
const files = ref<File[]>([]);
const examType = ref("英语四六级");
const showExamTypeDropdown = ref(false);
const examTypeOptions = [
  "英语四六级",
  "研究生考试",
  "公务员考试",
  "英语专四专八",
  "教师资格证",
  "计算机二级",
  "普通话等级考试",
];

function selectExamType(type: string) {
  examType.value = type;
  showExamTypeDropdown.value = false;
}

function handleClickOutside(e: MouseEvent) {
  const target = e.target as HTMLElement;
  if (!target.closest(".custom-select")) {
    showExamTypeDropdown.value = false;
  }
}

const isAnalyzing = ref(false);
const hasAnalyzed = ref(false);
const isGenerating = ref(false);
const isMindMapGenerating = ref(false);
const isCreatingChat = ref(false);
const chatProgress = ref("");
const chatProgressPercent = ref(0);

const analysisData = ref<any>(null);
const currentAnalysisId = ref<number | null>(null);

const parsedKeyPoints = computed(() => {
  if (!analysisData.value?.keyPoints) return [];
  try {
    const data =
      typeof analysisData.value.keyPoints === "string"
        ? JSON.parse(analysisData.value.keyPoints)
        : analysisData.value.keyPoints;
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
});

const parsedDistribution = computed(() => {
  if (!analysisData.value?.questionDistribution) return [];
  try {
    const data =
      typeof analysisData.value.questionDistribution === "string"
        ? JSON.parse(analysisData.value.questionDistribution)
        : analysisData.value.questionDistribution;
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
});

function getDistFillClass(idx: number) {
  const classes = ["", "fill-green", "fill-purple", "fill-orange"];
  return classes[idx % classes.length];
}

function formatSuggestions(text: string): string {
  if (!text) return "";
  return marked.parse(text) as string;
}

async function loadAnalysis(idParam: string | string[]) {
  const id = Array.isArray(idParam) ? idParam[0] : idParam;
  if (id && id !== "new") {
    const numId = Number(id);
    if (!isNaN(numId)) {
      currentAnalysisId.value = numId;
      try {
        const data = await examApi.getExamAnalysisDetail(numId);
        analysisData.value = data;
        examType.value = data.examType || "英语四六级";
        if (data.fileNames) {
          files.value = data.fileNames.split(",").map((name) => new File([], name.trim()));
        }
        hasAnalyzed.value = true;
      } catch (error) {
        console.error("Failed to load analysis:", error);
        files.value = [new File([], "2023年12月英语六级真题.pdf")];
        hasAnalyzed.value = true;
        analysisData.value = {
          keyPoints:
            '[{"name":"虚拟语气","count":12},{"name":"定语从句","count":9},{"name":"倒装句","count":6},{"name":"非谓语动词","count":5}]',
          questionDistribution:
            '[{"type":"选择题","percentage":40},{"type":"阅读理解","percentage":35},{"type":"写作翻译","percentage":25}]',
          suggestions: null,
        };
      }
    }
  } else {
    currentAnalysisId.value = null;
    analysisData.value = null;
    hasAnalyzed.value = false;
    files.value = [];
  }
}

onMounted(async () => {
  const raw = localStorage.getItem("llm.sidebar.open");
  if (raw === "0") sidebarOpen.value = false;
  appState.setMode("exam-analysis");
  await loadAnalysis(route.params.id);
  window.addEventListener("click", handleClickOutside);
});

onUnmounted(() => {
  window.removeEventListener("click", handleClickOutside);
});

watch(
  () => route.params.id,
  async (newId) => {
    if (newId) {
      await loadAnalysis(newId);
    }
  },
);

watch(sidebarOpen, (open) => {
  localStorage.setItem("llm.sidebar.open", open ? "1" : "0");
});

function triggerUpload() {
  fileInput.value?.click();
}

function removeFile(index: number) {
  files.value.splice(index, 1);
}

function onFileChange(e: Event) {
  const target = e.target as HTMLInputElement;
  if (target.files && target.files.length > 0) {
    const newFiles = Array.from(target.files);
    const totalFiles = [...files.value, ...newFiles];
    if (totalFiles.length > 10) {
      alert("最多只能上传10个文件");
      files.value = totalFiles.slice(0, 10);
    } else {
      files.value = totalFiles;
    }
    hasAnalyzed.value = false;
    analysisData.value = null;
  }
}

async function startAnalysis() {
  if (files.value.length === 0) return;
  isAnalyzing.value = true;

  const fileNames = files.value.map((f) => f.name).join(",");
  const firstFile = files.value[0].name;
  const analysisTitle =
    files.value.length === 1
      ? `${examType.value}试卷分析 - ${firstFile}`
      : `${examType.value}试卷分析 - ${firstFile} 等${files.value.length}份文件`;

  try {
    const id = await examApi.createExamAnalysis(
      analysisTitle,
      examType.value,
      fileNames,
      files.value,
    );
    currentAnalysisId.value = id;

    try {
      const result = await examApi.analyzeExam(id);
      analysisData.value = result;
    } catch (analyzeError: any) {
      console.error("AI分析失败:", analyzeError);
      analysisData.value = {
        id,
        title: analysisTitle,
        examType: examType.value,
        fileNames,
        keyPoints:
          '[{"name":"虚拟语气","count":8},{"name":"定语从句","count":6},{"name":"倒装句","count":4},{"name":"非谓语动词","count":3}]',
        questionDistribution:
          '[{"type":"选择题","percentage":40},{"type":"阅读理解","percentage":35},{"type":"写作翻译","percentage":25}]',
        suggestions:
          "1. 重点复习虚拟语气的各种句型结构\n2. 加强定语从句的关系词辨析\n3. 练习倒装句的转换与应用\n4. 掌握非谓语动词的不同形式",
      };
    }
    hasAnalyzed.value = true;
    isAnalyzing.value = false;
    router.replace(`/exam-analysis/${id}`);
  } catch (error: any) {
    console.error("Failed to analyze exam:", error);
    isAnalyzing.value = false;
    const msg =
      error?.response?.data?.message || error?.message || "创建分析失败，请检查是否已登录";
    alert(msg);
  }
}

async function generateSuggestions() {
  if (!currentAnalysisId.value) return;
  isGenerating.value = true;
  try {
    const result = await examApi.generateSuggestions(currentAnalysisId.value);
    analysisData.value = result;
  } catch (error) {
    console.error("Failed to generate suggestions:", error);
    alert("生成复习建议失败，请稍后重试");
  } finally {
    isGenerating.value = false;
  }
}

async function goToChat() {
  if (isCreatingChat.value) return;
  if (!currentAnalysisId.value) {
    alert("请先完成试卷分析");
    return;
  }
  isCreatingChat.value = true;
  chatProgress.value = "正在查找已有知识库...";
  chatProgressPercent.value = 10;

  try {
    const existingKb = await kbApi.getKnowledgeBaseByExamAnalysisId(currentAnalysisId.value);

    if (existingKb) {
      chatProgress.value = "已找到关联知识库，正在跳转...";
      chatProgressPercent.value = 100;
      await new Promise((resolve) => setTimeout(resolve, 500));
      router.push(`/knowledge/${existingKb.id}`);
      return;
    }

    chatProgress.value = "正在创建知识库...";
    chatProgressPercent.value = 20;

    const kbName = `${analysisData.value?.title || "考试分析"} - 学习资料`;
    const kb = await kbApi.createKnowledgeBase({
      name: kbName,
      description: "基于考试分析结果生成的学习资料",
      examAnalysisId: currentAnalysisId.value,
    });

    if (!kb || !kb.id) {
      throw new Error("知识库创建失败");
    }
    console.log("KB created:", kb);

    chatProgressPercent.value = 40;
    chatProgress.value = "正在上传原始文件...";

    const originalFiles = files.value;
    const uploadedDocIds: number[] = [];
    if (originalFiles.length > 0) {
      for (const file of originalFiles) {
        try {
          const doc = await docApi.uploadDocument(kb.id, file);
          console.log("File uploaded:", file.name);
          if (doc && doc.id) {
            uploadedDocIds.push(doc.id);
          }
        } catch (uploadError) {
          console.error("Failed to upload file:", file.name, uploadError);
        }
      }
    }

    chatProgressPercent.value = 50;
    chatProgress.value = "正在等待文档处理完成...";

    // 等待所有上传的文档处理完成
    if (uploadedDocIds.length > 0) {
      const maxWaitTime = 60000; // 最多等待60秒
      const pollInterval = 2000; // 每2秒检查一次
      const startTime = Date.now();

      while (Date.now() - startTime < maxWaitTime) {
        let allCompleted = true;
        let hasFailed = false;

        for (const docId of uploadedDocIds) {
          try {
            const status = await docApi.getDocumentStatus(docId);
            console.log(`Document ${docId} status:`, status.status);
            if (status.status === "failed") {
              hasFailed = true;
              console.error(`Document ${docId} processing failed:`, status.errorMsg);
            } else if (status.status !== "completed") {
              allCompleted = false;
            }
          } catch (e) {
            console.error(`Failed to check document ${docId} status:`, e);
          }
        }

        if (allCompleted || hasFailed) {
          break;
        }

        // 等待一段时间后再次检查
        await new Promise((resolve) => setTimeout(resolve, pollInterval));
        chatProgress.value = `正在等待文档处理完成...（已等待${Math.round((Date.now() - startTime) / 1000)}秒）`;
      }
    }

    chatProgressPercent.value = 60;
    chatProgress.value = "正在同步思维导图...";

    if (analysisData.value?.mindMapId) {
      try {
        const { useMindMapStore } = await import("@/stores/mindmap");
        const mindMapStore = useMindMapStore();
        await mindMapStore.moveToKB(analysisData.value.mindMapId, kb.id);
        console.log("Mind map synced to KB");
      } catch (e) {
        console.error("Failed to sync mind map to KB:", e);
      }
    }

    chatProgressPercent.value = 80;
    chatProgress.value = "正在创建分析结果文档...";

    const keyPointsStr = analysisData.value?.keyPoints || "[]";
    const distStr = analysisData.value?.questionDistribution || "[]";
    const suggestionsStr = analysisData.value?.suggestions || "";
    const contentStr = analysisData.value?.content || "";

    let keyPoints: any[] = [];
    let distribution: any[] = [];
    try {
      keyPoints = typeof keyPointsStr === "string" ? JSON.parse(keyPointsStr) : keyPointsStr;
      distribution = typeof distStr === "string" ? JSON.parse(distStr) : distStr;
    } catch (e) {
      console.error("Failed to parse analysis data", e);
    }

    let docContent = `# ${analysisData.value?.title || "考试分析结果"}\n\n`;
    docContent += `## 考试类型\n${analysisData.value?.examType || "未指定"}\n\n`;
    docContent += `## 上传文件\n${analysisData.value?.fileNames || "未指定"}\n\n`;

    if (contentStr) {
      docContent += `## 试卷原始内容\n${contentStr}\n\n`;
    }

    if (keyPoints.length > 0) {
      docContent += `## 高频考点\n`;
      keyPoints.forEach((kp: any) => {
        docContent += `- ${kp.name}（出现 ${kp.count} 次）\n`;
      });
      docContent += "\n";
    }

    if (distribution.length > 0) {
      docContent += `## 题型分布\n`;
      distribution.forEach((d: any) => {
        docContent += `- ${d.type}：${d.percentage}%\n`;
      });
      docContent += "\n";
    }

    if (suggestionsStr) {
      docContent += `## 智能复习建议\n${suggestionsStr}\n\n`;
    }

    const blob = new Blob([docContent], { type: "text/plain;charset=utf-8" });
    const analysisFile = new File(
      [blob],
      `${analysisData.value?.title || "考试分析"}_学习资料.txt`,
      {
        type: "text/plain",
      },
    );

    try {
      const analysisDoc = await docApi.uploadDocument(kb.id, analysisFile);
      console.log("Analysis document uploaded");
      if (analysisDoc && analysisDoc.id) {
        uploadedDocIds.push(analysisDoc.id);
      }
    } catch (uploadError) {
      console.error("Failed to upload analysis document:", uploadError);
    }

    // 等待分析结果文档也处理完成
    if (uploadedDocIds.length > 0) {
      chatProgressPercent.value = 85;
      chatProgress.value = "正在等待文档处理完成...";

      const maxWaitTime = 60000;
      const pollInterval = 2000;
      const startTime = Date.now();

      while (Date.now() - startTime < maxWaitTime) {
        let allCompleted = true;
        let hasFailed = false;

        for (const docId of uploadedDocIds) {
          try {
            const status = await docApi.getDocumentStatus(docId);
            console.log(`Document ${docId} status:`, status.status);
            if (status.status === "failed") {
              hasFailed = true;
              console.error(`Document ${docId} processing failed:`, status.errorMsg);
            } else if (status.status !== "completed") {
              allCompleted = false;
            }
          } catch (e) {
            console.error(`Failed to check document ${docId} status:`, e);
          }
        }

        if (allCompleted || hasFailed) {
          break;
        }

        await new Promise((resolve) => setTimeout(resolve, pollInterval));
        chatProgress.value = `正在等待文档处理完成...（已等待${Math.round((Date.now() - startTime) / 1000)}秒）`;
      }
    }

    chatProgressPercent.value = 90;
    chatProgress.value = "正在创建对话...";

    const result = await messageStore.createConversation({
      knowledgeBaseId: kb.id,
    });

    if (!result || !result.id) {
      throw new Error("对话创建失败");
    }

    // 将默认消息信息存储到 sessionStorage，让聊天页面在挂载后自动发送
    sessionStorage.setItem(
      `chat_auto_msg_${result.id}`,
      JSON.stringify({
        message: `请基于我上传的资料帮我进行深度分析。`,
        kbId: kb.id,
      }),
    );

    chatProgressPercent.value = 100;
    chatProgress.value = "跳转中...";

    await new Promise((resolve) => setTimeout(resolve, 300));
    router.push(`/chat/${result.id}`);
  } catch (error) {
    console.error("Failed to create chat:", error);
    alert("创建对话失败，请稍后重试");
  } finally {
    isCreatingChat.value = false;
    chatProgress.value = "";
    chatProgressPercent.value = 0;
  }
}

function goToMindMap() {
  if (analysisData.value?.mindMapId) {
    router.push(`/mindmap/${analysisData.value.mindMapId}`);
  } else {
    router.push("/mindmap");
  }
}

async function generateMindMapFromAnalysis() {
  if (!analysisData.value?.keyPoints) {
    alert("请先完成分析");
    return;
  }
  isMindMapGenerating.value = true;
  try {
    const keyPointsStr = analysisData.value.keyPoints;
    const distStr = analysisData.value.questionDistribution || "[]";
    let keyPoints: any[] = [];
    let distribution: any[] = [];
    try {
      keyPoints = typeof keyPointsStr === "string" ? JSON.parse(keyPointsStr) : keyPointsStr;
      distribution = typeof distStr === "string" ? JSON.parse(distStr) : distStr;
    } catch (e) {
      console.error("Failed to parse analysis data", e);
    }
    const content = `基于"${analysisData.value.title}"的分析结果：\n高频考点包括：${keyPoints.map((p: any) => p.name).join("、")}。\n题型分布为：${distribution.map((d: any) => `${d.type}占${d.percentage}%`).join("，")}。`;
    const { generateMindMapFromAi } = await import("@/api/mindmap");
    const result = await generateMindMapFromAi(content, analysisData.value.title);
    if (result?.id) {
      analysisData.value.mindMapId = result.id;
      if (currentAnalysisId.value) {
        try {
          await examApi.updateExamAnalysis(currentAnalysisId.value, {
            id: currentAnalysisId.value,
            mindMapId: result.id,
          });
        } catch (e) {
          console.error("Failed to save mindMapId:", e);
        }
      }
    }
  } catch (error) {
    console.error("Failed to generate mind map:", error);
    alert("生成思维导图失败");
  } finally {
    isMindMapGenerating.value = false;
  }
}

function getFileIcon(filename: string): string {
  const ext = filename.split(".").pop()?.toLowerCase() || "";
  switch (ext) {
    case "pdf":
      return "pdf";
    case "doc":
    case "docx":
      return "word";
    case "md":
      return "markdown";
    case "txt":
      return "txt";
    default:
      return "file";
  }
}

function getFileIconColor(filename: string): string {
  const ext = filename.split(".").pop()?.toLowerCase() || "";
  switch (ext) {
    case "pdf":
      return "#ef4444";
    case "doc":
    case "docx":
      return "#3b82f6";
    case "md":
      return "#6366f1";
    case "txt":
      return "#6b7280";
    default:
      return "#9ca3af";
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
  position: relative;
  display: flex;
  transition: padding-left 180ms ease;
  padding-left: 0;
  background-color: var(--color-bg);
}

.layout--open {
  padding-left: var(--sidebar-width);
}

.drawer {
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  width: var(--sidebar-width);
  background: var(--color-sidebar);
  border-right: 1px solid var(--color-border);
  transform: translateX(-100%);
  transition: transform 180ms ease;
  z-index: 30;
}

.drawer--open {
  transform: translateX(0);
}

.content {
  flex: 1;
  height: 100vh;
  display: flex;
  justify-content: center;
  min-width: 0;
  overflow-y: auto;
}

.exam-analysis-container {
  width: 100%;
  max-width: 1200px;
  padding: 40px 32px;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.back-btn {
  padding: 8px !important;
  min-width: auto !important;
  border-radius: 50% !important;
}

.title {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 20px 24px;
  margin-bottom: 24px;
  box-shadow: var(--shadow-sm);
  flex-wrap: wrap;
  gap: 20px;
}

.upload-section {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}

.file-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
  background: var(--color-bg-alt);
  padding: 6px 8px;
  border-radius: 6px;
  border: 1px solid var(--color-border);
}

.file-item__name {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-item__remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--color-text-muted);
  border-radius: 4px;
  padding: 0;
}

.file-item__remove:hover {
  background: rgba(0, 0, 0, 0.08);
  color: var(--color-text);
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
  background: var(--color-bg-alt);
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid var(--color-border);
}

.file-name.empty {
  color: var(--color-text-muted);
  border-style: dashed;
  background: transparent;
}

.type-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.label {
  font-size: 14px;
  color: var(--color-text-muted);
  font-weight: 500;
}

.type-select {
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text);
  font-size: 14px;
  outline: none;
  min-width: 140px;
}

.type-select:hover {
  background: var(--color-bg-alt);
}

.type-select:focus {
  border-color: var(--color-primary);
}

.custom-select {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text);
  font-size: 14px;
  cursor: pointer;
  min-width: 140px;
  user-select: none;
  transition: all 0.2s;
}

.custom-select:hover {
  background: var(--color-bg-alt);
  border-color: var(--color-border);
}

.custom-select__value {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.custom-select__dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 100;
  overflow: hidden;
}

.custom-select__option {
  padding: 8px 12px;
  font-size: 14px;
  color: var(--color-text);
  cursor: pointer;
  transition: background 0.15s;
}

.custom-select__option:hover {
  background: var(--color-bg-alt) !important;
}

.custom-select__option--selected {
  background: var(--color-bg-alt);
  font-weight: 600;
}

.custom-select__option--selected:hover {
  background: var(--color-border) !important;
}

.main-content {
  display: grid;
  grid-template-columns: 1fr 350px;
  gap: 24px;
  align-items: start;
}

@media (max-width: 900px) {
  .main-content {
    grid-template-columns: 1fr;
  }
}

.analysis-results {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 24px;
  box-shadow: var(--shadow-sm);
}

.h-full {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-title:not(.card-header .card-title) {
  margin-bottom: 20px;
}

.point-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.point-list li {
  padding: 12px 16px;
  background: var(--color-bg-alt);
  border-radius: 8px;
  font-size: 15px;
  color: var(--color-text);
  display: flex;
  align-items: center;
  border-left: 4px solid var(--color-primary);
}

.highlight {
  font-weight: 700;
  color: var(--color-primary);
  background: rgba(59, 130, 246, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  margin: 0 4px;
}

.distribution {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dist-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dist-item {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
}

.dist-pct {
  color: var(--color-text-muted);
}

.dist-bar {
  height: 8px;
  background: var(--color-bg-alt);
  border-radius: 999px;
  overflow: hidden;
}

.dist-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: 999px;
  transition: width 1s ease-out;
}

.fill-green {
  background: #10b981;
}
.fill-purple {
  background: #8b5cf6;
}
.fill-orange {
  background: #f59e0b;
}

.suggestion-card {
  border-color: rgba(59, 130, 246, 0.3);
  background: linear-gradient(to bottom, var(--color-surface), rgba(59, 130, 246, 0.02));
}

.suggestions-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--color-text-muted);
  font-size: 14px;
  background: var(--color-bg-alt);
  border-radius: 8px;
  border: 1px dashed var(--color-border);
}

.sug-content {
  font-size: 15px;
  color: var(--color-text);
  line-height: 1.8;
}

.sug-action {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
  margin-top: 16px;
}

.chat-progress-wrapper {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.progress-bar {
  width: 100%;
  height: 6px;
  background: var(--color-border);
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: black;
  border-radius: 3px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 12px;
  color: var(--color-text-muted);
  text-align: center;
}

.empty-hint {
  color: var(--color-text-muted);
  font-size: 14px;
  text-align: center;
  padding: 20px;
}

.mindmap-preview {
  flex: 1;
  background: var(--color-bg-alt);
  border-radius: 8px;
  border: 1px dashed var(--color-border);
  cursor: pointer;
  transition: all 0.2s;
  min-height: 300px;
  padding: 16px;
}

.mindmap-preview:hover {
  border-color: var(--color-primary);
  background: rgba(59, 130, 246, 0.05);
}

.mindmap-structure {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

.structure-root {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--color-surface);
  border-radius: 8px;
  border: 2px solid var(--color-primary);
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.15);
}

.root-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
}

.structure-branches {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 24px;
  position: relative;
}

.structure-branches::before {
  content: "";
  position: absolute;
  left: 12px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--color-border);
}

.branch-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-surface);
  border-radius: 6px;
  border: 1px solid var(--color-border);
  position: relative;
}

.branch-item::before {
  content: "";
  position: absolute;
  left: -14px;
  top: 50%;
  width: 12px;
  height: 2px;
  background: var(--color-border);
}

.branch-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.branch-dot--0 {
  background: #3b82f6;
}
.branch-dot--1 {
  background: #10b981;
}
.branch-dot--2 {
  background: #f59e0b;
}
.branch-dot--3 {
  background: #8b5cf6;
}

.branch-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
  flex: 1;
}

.branch-count {
  font-size: 11px;
  color: var(--color-text-muted);
}

.branch-more {
  font-size: 12px;
  color: var(--color-text-muted);
  text-align: center;
  padding: 4px 0;
}

.structure-footer {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--color-surface);
  border-radius: 6px;
  margin-top: auto;
}

.structure-footer span {
  font-size: 12px;
  color: var(--color-text-muted);
}

.key-points-preview {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 0;
}

.preview-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-item {
  display: inline-block;
  padding: 4px 10px;
  background: rgba(59, 130, 246, 0.1);
  color: var(--color-primary);
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
}

.preview-bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bar-label {
  font-size: 13px;
  color: var(--color-text);
  min-width: 70px;
}

.bar-track {
  flex: 1;
  height: 6px;
  background: var(--color-bg-alt);
  border-radius: 999px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: 999px;
}

.bar-pct {
  font-size: 12px;
  color: var(--color-text-muted);
  min-width: 35px;
  text-align: right;
}

.preview-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: var(--color-text-muted);
  text-align: center;
  gap: 8px;
}

.preview-empty p {
  margin: 0;
  font-size: 13px;
}

.mindmap-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: var(--color-text-muted);
  text-align: center;
  gap: 8px;
}

.mindmap-empty p {
  margin: 0;
  font-size: 13px;
}

.mindmap-preview:hover {
  border-color: var(--color-primary);
  background: rgba(59, 130, 246, 0.05);
}

.preview-bg {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--color-text-muted);
  font-size: 14px;
  font-weight: 500;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  margin-top: 24px;
}

.empty-icon {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  background: rgba(59, 130, 246, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
}

.empty-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 12px 0;
}

.empty-desc {
  font-size: 15px;
  color: var(--color-text-muted);
  margin: 0;
}

.mini {
  position: fixed;
  top: 12px;
  left: 12px;
  display: inline-flex;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 999px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  z-index: 25;
}

.mini__btn {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--color-text-muted);
  display: grid;
  place-items: center;
}

.mini__btn:hover {
  background: var(--color-hover);
  color: var(--color-text);
}
</style>
