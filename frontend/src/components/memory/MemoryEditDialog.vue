<!--
  记忆功能组件 4：MemoryEditDialog
  - 编辑记忆：标题 / 情绪标签（多选+自定义）/ 正文
  - 打开时拉取详情填充；保存后 emit('saved')
-->
<template>
  <el-dialog v-model="visible" title="编辑记忆" width="560px">
    <el-form label-width="80px">
      <el-form-item label="标题">
        <el-input v-model="form.autoTitle" placeholder="记忆标题" maxlength="100" />
      </el-form-item>
      <el-form-item label="情绪标签">
        <el-select v-model="form.emotions" multiple filterable allow-create default-first-option
          placeholder="选择或输入情绪标签" style="width: 100%">
          <el-option v-for="e in emotionOptions" :key="e" :label="e" :value="e" />
        </el-select>
      </el-form-item>
      <el-form-item label="正文">
        <el-input v-model="form.finalDiary" type="textarea" :rows="6" placeholder="这段记忆的正文（日记）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getMemoryDetail, updateMemory, getMemoryEmotionStats } from '@/api/memory'

const props = defineProps<{ modelValue: boolean; memoryId: number | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'saved'): void }>()

// 用 computed 桥接 v-model
const visible = ref(false)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => emit('update:modelValue', v))

const saving = ref(false)
const form = reactive<{ memoryId: number | null; autoTitle: string; emotions: string[]; finalDiary: string }>({
  memoryId: null, autoTitle: '', emotions: [], finalDiary: ''
})
const emotionOptions = ref<string[]>([])

// 打开时填充数据 + 加载可选情绪
watch(() => props.modelValue, async (v) => {
  if (!v || props.memoryId == null) return
  loadEmotionOptions()
  form.memoryId = props.memoryId
  form.autoTitle = ''
  form.emotions = []
  form.finalDiary = ''
  try {
    const detail = await getMemoryDetail(props.memoryId)
    const note = (detail as any)?.note || {}
    form.autoTitle = note?.autoTitle || ''
    form.emotions = note?.emotions ? String(note.emotions).split(',').map((s: string) => s.trim()).filter(Boolean) : []
    form.finalDiary = note?.finalDiary || ''
  } catch (e) { /* 拉详情失败也允许编辑当前项 */ }
})

// 可选情绪标签（已有 + 预置）
async function loadEmotionOptions() {
  try {
    const res = await getMemoryEmotionStats()
    const list = Array.isArray(res) ? res : []
    const seen = new Set<string>()
    const arr = list.map((i: any) => i?.emotion).filter((e: any) => e && !seen.has(e) && seen.add(e))
    const presets = ['松弛', '治愈', '疲惫', '开心', '平淡', '想念', '充实']
    presets.forEach((p) => { if (!seen.has(p)) { seen.add(p); arr.push(p) } })
    emotionOptions.value = arr
  } catch { emotionOptions.value = ['松弛', '治愈', '疲惫', '开心', '平淡', '想念', '充实'] }
}

// 保存
async function save() {
  if (form.memoryId == null) return
  saving.value = true
  try {
    await updateMemory({
      memoryId: form.memoryId,
      autoTitle: form.autoTitle.trim() || undefined,
      emotions: form.emotions,
      finalDiary: form.finalDiary
    })
    ElMessage.success('已保存')
    visible.value = false
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally { saving.value = false }
}
</script>
