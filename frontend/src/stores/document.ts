// @ts-nocheck
import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as docApi from '@/api/document'
import { useKnowledgeBaseStore } from './knowledgeBase'

export const useDocumentStore = defineStore('document', () => {
  const documents = ref<docApi.Document[]>([])
  const uploading = ref(false)

  async function fetchByKnowledgeBaseId(knowledgeBaseId: number) {
    documents.value = await docApi.getDocuments(knowledgeBaseId)
  }

  async function getDocuments(knowledgeBaseId: number) {
    documents.value = await docApi.getDocuments(knowledgeBaseId)
    return documents.value
  }

  async function upload(knowledgeBaseId: number, file: File) {
    uploading.value = true
    try {
      const doc = await docApi.uploadDocument(knowledgeBaseId, file)
      documents.value.unshift(doc)
      
      // 更新知识库 store 中的文档数量
      const kbStore = useKnowledgeBaseStore()
      const kb = kbStore.list.find(x => x.id === knowledgeBaseId)
      try {
        if (kb) {
          kb.documentCount = (kb.documentCount || 0) + 1
          kbStore.saveToStorage()
        }
      } catch (e) {
        console.warn('Failed to save kbStore', e)
      }
      
      return doc
    } finally {
      uploading.value = false
    }
  }

  async function remove(id: number) {
    const doc = documents.value.find(x => x.id === id)
    if (!doc) return

    await docApi.deleteDocument(id)
    documents.value = documents.value.filter((x) => x.id !== id)

    // 更新知识库 store 中的文档数量
    const kbStore = useKnowledgeBaseStore()
    const kb = kbStore.list.find(x => x.id === doc.knowledgeBaseId)
    if (kb) {
      kb.documentCount = Math.max(0, (kb.documentCount || 0) - 1)
      kbStore.saveToStorage()
    }
  }

  async function pollStatus(id: number) {
    const status = await docApi.getDocumentStatus(id)
    const doc = documents.value.find((x) => x.id === id)
    if (doc) {
      doc.status = status.status
      doc.errorMsg = status.errorMsg
      if (status.chunkCount !== undefined) {
        doc.chunkCount = status.chunkCount
      }
    }
    return status.status
  }

  async function download(id: number, fileName: string) {
    await docApi.downloadDocument(id, fileName)
  }

  return {
    documents,
    uploading,
    fetchByKnowledgeBaseId,
    getDocuments,
    upload,
    remove,
    pollStatus,
    download,
  }
})
