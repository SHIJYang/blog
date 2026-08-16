<template>
  <input :type="type" :value="modelValue" :placeholder="placeholder" :disabled="disabled" class="neomorphic-input"
    @input="$emit('update:modelValue', $event.target.value)" @focus="focused = true" @blur="focused = false" />
</template>

<script setup>
defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  type: {
    type: String,
    default: 'text'
  },
  placeholder: {
    type: String,
    default: 'Type here...'
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:modelValue'])
</script>

<style scoped>
.neomorphic-input {
  border: none;
  padding: 1rem;
  border-radius: 1rem;
  background: #e8e8e8;
  box-shadow: 20px 20px 60px #c5c5c5,
    -20px -20px 60px #ffffff;
  transition: 0.3s;
  width: 100%;
  box-sizing: border-box;
  font-family: inherit;
  font-size: 1rem;
  color: #333;
}

.neomorphic-input:focus {
  outline: none;
  background: #e8e8e8;
  box-shadow: inset 20px 20px 60px #c5c5c5,
    inset -20px -20px 60px #ffffff;
  transition: 0.3s;
}

.neomorphic-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>