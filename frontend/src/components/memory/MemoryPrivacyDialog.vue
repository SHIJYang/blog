<!--
  记忆功能组件 5：MemoryPrivacyDialog
  - 设置记忆隐私等级：0-公开 / 1-私密 / 2-封存
  - 保存后 emit('saved')
-->
<template>
  <el-dialog v-model="visible" title="设置记忆隐私" width="460px">
    <el-radio-group v-model="privacy">
      <el-radio :value="0">公开（可联动博客、可随意召回）</el-radio>
      <el-radio :value="1">私密（仅本人可见，AI 温和关联）</el-radio>
      <el-radio :value="2">封存（AI 永不主动提及，仅存档）</el-radio>
    </el-radio-group>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { setMemoryPrivacy } from '@/api/memory'

const props = defineProps<{ modelValue: boolean; memoryId: number | null; current?: number }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'saved'): void }>()

const visible = ref(false)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => emit('update:modelValue', v))

const privacy = ref(1)
const saving = ref(false)

watch(() => props.modelValue, (v) => {
  if (v) privacy.value = props.current ?? 1
})

// 保存
async function save() {
  if (props.memoryId == null) return
  saving.value = true
  try {
    await setMemoryPrivacy(props.memoryId, privacy.value)
    ElMessage.success('隐私已更新')
    visible.value = false
    emit('saved')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally { saving.value = false }
}
</script>
