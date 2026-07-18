import { mockSession } from '@/mock/storage'

const DATABASE_NAME = 'examinsight-mock-files-v1'
const STORE_NAME = 'resource-files'
const memoryFiles = new Map<string, Blob>()
let databasePromise: Promise<IDBDatabase | null> | null = null

function storageKey(resourceId: string) {
  return mockSession.key(`resource-file.${resourceId}`)
}

function openDatabase() {
  if (databasePromise) return databasePromise
  if (!globalThis.indexedDB) return Promise.resolve(null)
  databasePromise = new Promise((resolve) => {
    const request = globalThis.indexedDB.open(DATABASE_NAME, 1)
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE_NAME)) {
        request.result.createObjectStore(STORE_NAME)
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => resolve(null)
    request.onblocked = () => resolve(null)
  })
  return databasePromise
}

async function runTransaction<T>(
  mode: IDBTransactionMode,
  operation: (store: IDBObjectStore) => IDBRequest<T>,
) {
  const database = await openDatabase()
  if (!database) return undefined
  return new Promise<T | undefined>((resolve) => {
    try {
      const transaction = database.transaction(STORE_NAME, mode)
      const request = operation(transaction.objectStore(STORE_NAME))
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => resolve(undefined)
      transaction.onabort = () => resolve(undefined)
    } catch {
      resolve(undefined)
    }
  })
}

export async function saveMockResourceFile(resourceId: string, file: Blob) {
  const key = storageKey(resourceId)
  memoryFiles.set(key, file)
  await runTransaction('readwrite', (store) => store.put(file, key))
}

export async function readMockResourceFile(resourceId: string) {
  const key = storageKey(resourceId)
  const memoryFile = memoryFiles.get(key)
  if (memoryFile) return memoryFile
  const persisted = await runTransaction<Blob>('readonly', (store) => store.get(key))
  if (persisted instanceof Blob) {
    memoryFiles.set(key, persisted)
    return persisted
  }
  return undefined
}

export async function deleteMockResourceFile(resourceId: string) {
  const key = storageKey(resourceId)
  memoryFiles.delete(key)
  await runTransaction('readwrite', (store) => store.delete(key))
}
