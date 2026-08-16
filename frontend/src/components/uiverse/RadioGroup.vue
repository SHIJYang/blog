<template>
  <div class="radio-group-wrapper">
    <div class="nav">
      <div class="container">
        <div v-for="option in options" :key="option.value" class="btn" :class="{ active: modelValue === option.value }"
          @click="updateValue(option.value)">
          {{ option.label }}
        </div>
        <svg class="outline" overflow="visible" :width="width" :height="height" :viewBox="`0 0 ${width} ${height}`"
          xmlns="http://www.w3.org/2000/svg">
          <rect class="rect" pathLength="100" x="0" y="0" :width="width" :height="height" fill="transparent"
            stroke-width="5" />
        </svg>
      </div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  modelValue: {
    type: [String, Number],
    required: true
  },
  options: {
    type: Array,
    required: true,
    validator: (value) => {
      return value.every(item => 'label' in item && 'value' in item)
    }
  },
  width: {
    type: Number,
    default: 400
  },
  height: {
    type: Number,
    default: 60
  },
  marginTop: {
    type: String,
    default: '20px'
  }
})

const emit = defineEmits(['update:modelValue'])

const updateValue = (value) => {
  emit('update:modelValue', value)
}
</script>

<style scoped>
.radio-group-wrapper {
  display: flex;
  justify-content: center;
  width: 100%;
  margin-top: v-bind(marginTop);
}

.nav {
  position: relative;
  width: v-bind(width + 'px');
  height: v-bind(height + 'px');
}

.container {
  position: absolute;
  inset: 0;

  display: flex;
  flex-direction: row;
  justify-content: space-around;
  align-items: center;
  padding: 0.5em;
}

.outline {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.rect {
  stroke-dashoffset: 5;
  stroke-dasharray: 0 0 10 40 10 40;
  transition: 0.5s;
  stroke: #141414;
}

.container:hover .outline .rect {
  transition: 999999s;
  stroke-dashoffset: 1;
  stroke-dasharray: 0;
}

.btn {
  padding: 0.5em 1.5em;
  color: #505050ec;
  cursor: pointer;
  transition: 0.1s;
  user-select: none;
  position: relative;
  z-index: 1;
}

.btn:hover {
  background: rgba(216, 216, 216, 0.2);
}

/* 动态生成每个选项的悬停样式 */
.btn:nth-child(1):hover~.outline .rect {
  stroke-dashoffset: 0;
  stroke-dasharray: 0 2 8 73.3 8 10.7;
}

.btn:nth-child(2):hover~.outline .rect {
  stroke-dashoffset: 0;
  stroke-dasharray: 0 12.6 9.5 49.3 9.5 31.6;
}

.btn:nth-child(3):hover~.outline .rect {
  stroke-dashoffset: 0;
  stroke-dasharray: 0 24.5 8.5 27.5 8.5 55.5;
}

.btn:nth-child(4):hover~.outline .rect {
  stroke-dashoffset: 0;
  stroke-dasharray: 0 34.7 6.9 10.2 6.9 76;
}

.btn:hover~.outline .rect {
  stroke-dashoffset: 0;
  stroke-dasharray: 0 0 10 40 10 40;
  transition: 0.5s !important;
}

.btn.active {
  background: rgba(255, 255, 255, 0.267);
  font-weight: bold;
}
</style>