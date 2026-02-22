<script setup>
import { ref } from 'vue'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

const mode = ref('SINGLE')
const message = ref('')
const output = ref('')
const loading = ref(false)

const modes = ['SINGLE', 'MASTER_SUB', 'MULTI_WORKFLOW']

async function send() {
  if (!message.value.trim()) {
    output.value = 'Please input a message.'
    return
  }

  loading.value = true
  output.value = ''

  try {
    const response = await fetch(`${apiBaseUrl}/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        mode: mode.value,
        message: message.value
      })
    })

    const payload = await response.json()
    output.value = payload.response || JSON.stringify(payload, null, 2)
  } catch (error) {
    output.value = `Request failed: ${error.message}`
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="shell">
    <section class="panel hero">
      <h1>Nexus Agent</h1>
      <p>Vue3 placeholder UI for future multi-agent console design.</p>
    </section>

    <section class="panel controls">
      <label>
        Mode
        <select v-model="mode">
          <option v-for="item in modes" :key="item" :value="item">{{ item }}</option>
        </select>
      </label>

      <label>
        Message
        <textarea v-model="message" rows="6" placeholder="Type your task..."></textarea>
      </label>

      <button :disabled="loading" @click="send">
        {{ loading ? 'Running...' : 'Send' }}
      </button>
    </section>

    <section class="panel output">
      <h2>Output</h2>
      <pre>{{ output }}</pre>
    </section>
  </main>
</template>
