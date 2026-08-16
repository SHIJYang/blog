<!-- NeomorphicTextarea.vue -->
<template>
  <textarea :value="modelValue" :placeholder="placeholder" :rows="rows" :disabled="disabled" class="neomorphic-textarea"
    @input="$emit('update:modelValue', $event.target.value)" @focus="focused = true" @blur="focused = false" />
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: 'Type here...' },
  rows: { type: Number, default: 4 },
  disabled: { type: Boolean, default: false }
})
defineEmits(['update:modelValue'])
</script>

<style scoped>
.neomorphic-textarea {
  border: none;
  padding: 1rem;
  border-radius: 1rem;
  background: var(--bg, #e8e8e8);
  box-shadow: 20px 20px 60px var(--shadow-dark, #c5c5c5),
    -20px -20px 60px var(--shadow-light, #ffffff);
  transition: 0.3s;
  width: 100%;
  box-sizing: border-box;
  font-family: inherit;
  font-size: 0.9rem;
  color: var(--text-primary, #333);
  resize: vertical;
  min-height: 80px;
}

.neomorphic-textarea:focus {
  outline: none;
  background: var(--bg, #e8e8e8);
  box-shadow: inset 20px 20px 60px var(--shadow-dark, #c5c5c5),
    inset -20px -20px 60px var(--shadow-light, #ffffff);
}

.neomorphic-textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>