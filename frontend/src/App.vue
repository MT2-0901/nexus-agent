<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'

const CONFIG_STORAGE_KEY = 'nexus-agent-config-v2'
const SESSIONS_STORAGE_KEY = 'nexus-agent-sessions-v2'

const modes = ref([])
const models = ref([])
const skills = ref([])
const loadingOptions = ref(false)
const loadingHistory = ref(false)
const running = ref(false)
const runStatus = ref('Idle')
const runError = ref('')

const inputText = ref('')
const pendingImages = ref([])

const sessions = ref([])
const activeSessionId = ref('')
const assistantDraftId = ref('')

const config = reactive({
  mode: 'SINGLE',
  model: '',
  userId: 'local-user',
  selectedSkillNames: []
})

const activeSession = computed(() => sessions.value.find((item) => item.id === activeSessionId.value) || null)

onMounted(async () => {
  loadLocalConfig()
  loadLocalSessions()
  await loadOptions()
})

watch(
  () => ({
    mode: config.mode,
    model: config.model,
    userId: config.userId,
    selectedSkillNames: [...config.selectedSkillNames]
  }),
  (value) => {
    localStorage.setItem(CONFIG_STORAGE_KEY, JSON.stringify(value))
  },
  { deep: true }
)

watch(
  sessions,
  (value) => {
    localStorage.setItem(SESSIONS_STORAGE_KEY, JSON.stringify(value))
  },
  { deep: true }
)

async function loadOptions() {
  loadingOptions.value = true
  try {
    const [modeRes, modelRes, skillRes] = await Promise.all([
      fetch(`${apiBaseUrl}/modes`),
      fetch(`${apiBaseUrl}/models`),
      fetch(`${apiBaseUrl}/skills`)
    ])

    if (modeRes.ok) {
      modes.value = await modeRes.json()
    }
    if (modelRes.ok) {
      models.value = await modelRes.json()
    }
    if (skillRes.ok) {
      skills.value = await skillRes.json()
    }

    if (!modes.value.length) {
      modes.value = ['SINGLE', 'MASTER_SUB', 'MULTI_WORKFLOW']
    }
    if (!models.value.length) {
      models.value = ['gemini-2.0-flash']
    }

    if (!modes.value.includes(config.mode)) {
      config.mode = modes.value[0]
    }
    if (!config.model || !models.value.includes(config.model)) {
      config.model = models.value[0]
    }

    const enabledSkillNames = skills.value.filter((item) => item.enabled).map((item) => item.name)
    if (!config.selectedSkillNames.length) {
      config.selectedSkillNames = enabledSkillNames
    } else {
      config.selectedSkillNames = config.selectedSkillNames.filter((name) =>
        enabledSkillNames.includes(name)
      )
    }
  } catch (error) {
    runError.value = `Load options failed: ${error.message}`
  } finally {
    loadingOptions.value = false
  }
}

function loadLocalConfig() {
  try {
    const raw = localStorage.getItem(CONFIG_STORAGE_KEY)
    if (!raw) {
      return
    }
    const parsed = JSON.parse(raw)
    config.mode = parsed.mode || config.mode
    config.model = parsed.model || config.model
    config.userId = parsed.userId || config.userId
    config.selectedSkillNames = Array.isArray(parsed.selectedSkillNames) ? parsed.selectedSkillNames : []
  } catch {
    // Ignore corrupted local config.
  }
}

function loadLocalSessions() {
  try {
    const raw = localStorage.getItem(SESSIONS_STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    sessions.value = Array.isArray(parsed) ? parsed : []
  } catch {
    sessions.value = []
  }

  if (!sessions.value.length) {
    const first = createSession()
    sessions.value = [first]
  }
  activeSessionId.value = sessions.value[0].id
}

function createSession() {
  const id = createId('sess')
  return {
    id,
    title: 'New Session',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    messages: []
  }
}

function createId(prefix) {
  if (globalThis.crypto?.randomUUID) {
    return `${prefix}-${globalThis.crypto.randomUUID()}`
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

function startNewSession() {
  const next = createSession()
  sessions.value.unshift(next)
  activeSessionId.value = next.id
  inputText.value = ''
  pendingImages.value = []
}

function chooseSession(id) {
  activeSessionId.value = id
  runError.value = ''
}

async function refreshSessionHistory() {
  if (!activeSession.value) {
    return
  }
  loadingHistory.value = true
  runError.value = ''
  try {
    const response = await fetch(
      `${apiBaseUrl}/chat/history?sessionId=${encodeURIComponent(activeSession.value.id)}&limit=100`
    )
    if (!response.ok) {
      throw new Error(`history request failed: ${response.status}`)
    }
    const rows = await response.json()
    const ordered = [...rows].sort((a, b) => (a.id || 0) - (b.id || 0))
    activeSession.value.messages = ordered.flatMap((row) => [
      {
        id: `u-${row.id}`,
        role: 'user',
        text: row.requestMessage,
        images: [],
        timestamp: row.timestamp
      },
      {
        id: `a-${row.id}`,
        role: 'assistant',
        text: row.responseMessage,
        images: [],
        timestamp: row.timestamp
      }
    ])
    activeSession.value.updatedAt = new Date().toISOString()
    syncSessionTitle(activeSession.value)
  } catch (error) {
    runError.value = `Load history failed: ${error.message}`
  } finally {
    loadingHistory.value = false
  }
}

function toggleSkill(name) {
  if (config.selectedSkillNames.includes(name)) {
    config.selectedSkillNames = config.selectedSkillNames.filter((item) => item !== name)
  } else {
    config.selectedSkillNames = [...config.selectedSkillNames, name]
  }
}

function addMessage(role, text, images = []) {
  if (!activeSession.value) {
    return
  }
  activeSession.value.messages.push({
    id: createId(role === 'assistant' ? 'a' : 'u'),
    role,
    text,
    images,
    timestamp: new Date().toISOString()
  })
  activeSession.value.updatedAt = new Date().toISOString()
  syncSessionTitle(activeSession.value)
}

function syncSessionTitle(session) {
  const firstUser = session.messages.find((item) => item.role === 'user' && item.text && item.text.trim())
  if (firstUser) {
    session.title = firstUser.text.slice(0, 28)
  }
}

async function onPickImages(event) {
  const files = Array.from(event.target.files || [])
  for (const file of files) {
    if (!file.type.startsWith('image/')) {
      continue
    }
    const dataUrl = await readAsDataUrl(file)
    const comma = dataUrl.indexOf(',')
    if (comma < 0) {
      continue
    }
    pendingImages.value.push({
      id: createId('img'),
      name: file.name,
      mimeType: file.type || 'image/png',
      size: file.size,
      base64: dataUrl.slice(comma + 1),
      previewUrl: dataUrl
    })
  }
  event.target.value = ''
}

function removePendingImage(id) {
  pendingImages.value = pendingImages.value.filter((item) => item.id !== id)
}

function readAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error(`Failed to read ${file.name}`))
    reader.readAsDataURL(file)
  })
}

async function sendMessage() {
  if (running.value || !activeSession.value) {
    return
  }
  const text = inputText.value.trim()
  if (!text && !pendingImages.value.length) {
    return
  }

  runError.value = ''
  runStatus.value = 'Running'
  running.value = true

  const imagePayload = pendingImages.value.map((item) => ({
    name: item.name,
    mimeType: item.mimeType,
    size: item.size
  }))
  addMessage('user', text, imagePayload)

  const payload = {
    threadId: activeSession.value.id,
    runId: createId('run'),
    messages: [
      {
        id: createId('input'),
        role: 'user',
        content: [
          ...(text ? [{ type: 'text', text }] : []),
          ...pendingImages.value.map((image) => ({
            type: 'image',
            name: image.name,
            mimeType: image.mimeType,
            data: image.base64
          }))
        ]
      }
    ],
    forwardedProps: {
      mode: config.mode,
      model: config.model,
      userId: config.userId,
      sessionId: activeSession.value.id,
      skillNames: config.selectedSkillNames
    }
  }

  addMessage('assistant', '')
  assistantDraftId.value = activeSession.value.messages[activeSession.value.messages.length - 1].id

  inputText.value = ''
  pendingImages.value = []

  try {
    await streamAgUi(payload)
    runStatus.value = 'Completed'
  } catch (error) {
    if (!getAssistantDraftText()) {
      appendAssistantDelta(`[Error] ${error.message}`)
    }
    runError.value = error.message
    runStatus.value = 'Failed'
  } finally {
    assistantDraftId.value = ''
    running.value = false
  }
}

async function streamAgUi(payload) {
  const response = await fetch(`${apiBaseUrl}/agui/run`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream'
    },
    body: JSON.stringify(payload)
  })

  if (!response.ok || !response.body) {
    throw new Error(`stream request failed: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })

    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const block = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      handleSseBlock(block)
      boundary = buffer.indexOf('\n\n')
    }
  }

  if (buffer.trim()) {
    handleSseBlock(buffer.trim())
  }
}

function handleSseBlock(block) {
  const lines = block
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.startsWith('data:'))

  if (!lines.length) {
    return
  }
  const payload = lines.map((line) => line.slice(5).trim()).join('\n')
  if (!payload) {
    return
  }

  const event = JSON.parse(payload)
  const type = event.type || ''

  if (type === 'TEXT_MESSAGE_CONTENT') {
    appendAssistantDelta(event.delta || '')
    return
  }
  if (type === 'RUN_ERROR') {
    throw new Error(event.message || 'run failed')
  }
  if (type === 'RUN_FINISHED' && !getAssistantDraftText()) {
    appendAssistantDelta(event?.result?.response || '')
  }
}

function appendAssistantDelta(delta) {
  if (!activeSession.value || !assistantDraftId.value || !delta) {
    return
  }
  const target = activeSession.value.messages.find((item) => item.id === assistantDraftId.value)
  if (!target) {
    return
  }
  target.text += delta
  activeSession.value.updatedAt = new Date().toISOString()
}

function getAssistantDraftText() {
  if (!activeSession.value || !assistantDraftId.value) {
    return ''
  }
  const target = activeSession.value.messages.find((item) => item.id === assistantDraftId.value)
  return target?.text || ''
}
</script>

<template>
  <main class="app">
    <aside class="sidebar">
      <section class="card">
        <h1>Nexus Agent Console</h1>
        <p class="sub">AG-UI stream / multimodal chat / agent runtime config</p>
      </section>

      <section class="card">
        <div class="section-head">
          <h2>Agent Config</h2>
          <span v-if="loadingOptions" class="tag">Loading</span>
        </div>
        <label>
          Mode
          <select v-model="config.mode">
            <option v-for="item in modes" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label>
          Model
          <select v-model="config.model">
            <option v-for="item in models" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label>
          User Id
          <input v-model.trim="config.userId" placeholder="local-user" />
        </label>
      </section>

      <section class="card">
        <div class="section-head">
          <h2>Sessions</h2>
          <button type="button" @click="startNewSession">New</button>
        </div>
        <div class="session-list">
          <button
            v-for="item in sessions"
            :key="item.id"
            type="button"
            class="session-item"
            :class="{ active: item.id === activeSessionId }"
            @click="chooseSession(item.id)"
          >
            <strong>{{ item.title }}</strong>
            <small>{{ item.id }}</small>
          </button>
        </div>
        <button type="button" :disabled="loadingHistory" @click="refreshSessionHistory">
          {{ loadingHistory ? 'Refreshing...' : 'Sync History From DB' }}
        </button>
      </section>

      <section class="card">
        <h2>Skills</h2>
        <div class="skill-list">
          <label v-for="skill in skills" :key="skill.name" class="skill-item">
            <input
              type="checkbox"
              :checked="config.selectedSkillNames.includes(skill.name)"
              :disabled="!skill.enabled"
              @change="toggleSkill(skill.name)"
            />
            <span>
              <strong>{{ skill.name }}</strong>
              <small>{{ skill.description || 'No description' }}</small>
            </span>
          </label>
        </div>
      </section>
    </aside>

    <section class="chat">
      <header class="chat-head card">
        <div>
          <h2>Streaming Chat</h2>
          <p>{{ runStatus }} <span v-if="runError" class="error">{{ runError }}</span></p>
        </div>
        <div class="tag-row">
          <span class="tag">{{ config.mode }}</span>
          <span class="tag">{{ config.model }}</span>
          <span class="tag">{{ activeSessionId }}</span>
        </div>
      </header>

      <section class="messages card">
        <article
          v-for="item in activeSession?.messages || []"
          :key="item.id"
          class="bubble"
          :class="item.role"
        >
          <header>
            <strong>{{ item.role === 'assistant' ? 'Assistant' : 'You' }}</strong>
            <small>{{ new Date(item.timestamp).toLocaleTimeString() }}</small>
          </header>
          <p>{{ item.text || '...' }}</p>
          <ul v-if="item.images?.length" class="image-meta">
            <li v-for="image in item.images" :key="image.name">{{ image.name }} ({{ image.mimeType }})</li>
          </ul>
        </article>
      </section>

      <section class="composer card">
        <textarea
          v-model="inputText"
          :disabled="running"
          rows="5"
          placeholder="Enter task details. Images are sent as multimodal AG-UI blocks."
        />

        <div v-if="pendingImages.length" class="pending-grid">
          <figure v-for="image in pendingImages" :key="image.id" class="pending-item">
            <img :src="image.previewUrl" :alt="image.name" />
            <figcaption>
              <span>{{ image.name }}</span>
              <button type="button" @click="removePendingImage(image.id)">Remove</button>
            </figcaption>
          </figure>
        </div>

        <div class="composer-actions">
          <label class="upload">
            Upload Images
            <input type="file" accept="image/*" multiple @change="onPickImages" />
          </label>
          <button type="button" :disabled="running" @click="sendMessage">
            {{ running ? 'Streaming...' : 'Send (AG-UI)' }}
          </button>
        </div>
      </section>
    </section>
  </main>
</template>
