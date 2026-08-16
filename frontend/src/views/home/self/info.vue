<template>
  <div class="card">
    <button class="mail" @click="$emit('mail-click')">
      <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none"
        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect width="20" height="16" x="2" y="4" rx="2"></rect>
        <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"></path>
      </svg>
    </button>

    <div class="profile-pic">
      <img v-if="configStore.configData?.blogavatar" :src="configStore.configData.blogavatar"
        :alt="configStore.configData.blogname" />
      <div v-else class="avatar-placeholder">{{ configStore.configData?.blogname?.charAt(0) || 'U' }}</div>
    </div>

    <div class="bottom">
      <div class="content">
        <span class="name">{{ configStore.configData?.blogname || 'Loading...' }}</span>
        <span class="about-me">{{ configStore.configData?.blogbio || '' }}</span>
      </div>
      <div class="bottom-bottom">
        <div class="social-links-container">
          <slot name="social-links">
            <el-icon>
              <Pear />
            </el-icon>
            <el-icon>
              <Watermelon />
            </el-icon>
            <el-icon>
              <MilkTea />
            </el-icon>
          </slot>
        </div>
        <button class="button" @click=connectme>联系我</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useConfigStore } from '@/stores/config'

const configStore = useConfigStore()


function connectme() {

}


</script>

<style scoped lang="scss">
.card {
  background: rgba(220, 220, 220, 0.75); // 浅灰色半透明
  backdrop-filter: blur(8px);
  border-radius: 32px;
  padding: 3px;
  position: relative;
  box-shadow: rgba(96, 75, 74, 0.1) 0px 70px 30px -50px;
  transition: all 0.5s ease-in-out;
  box-sizing: border-box;
  height: 280px;
  width: 280px;
}

.mail {
  position: absolute;
  right: 16px;
  top: 12px;
  background: transparent;
  border: none;
  cursor: pointer;
  z-index: 4;
  padding: 4px;

  svg {
    width: 20px;
    height: 20px;
    stroke: rgba(160, 160, 160, 0.8); // 浅灰色
    stroke-width: 2;
    transition: stroke 0.2s;

    &:hover {
      stroke: rgba(120, 120, 120, 0.9);
    }
  }
}

.profile-pic {
  position: absolute;
  width: calc(100% - 6px);
  height: calc(100% - 6px);
  top: 3px;
  left: 3px;
  border-radius: 29px;
  z-index: 1;
  overflow: hidden;
  transition: all 0.5s ease-in-out 0.2s, z-index 0.5s ease-in-out 0.2s;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: all 0.5s ease-in-out 0s;
  }

  .avatar-placeholder {
    width: 100%;
    height: 100%;
    background: rgba(200, 200, 200, 0.8); // 浅灰色半透明
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 48px;
    font-weight: bold;
    color: rgba(100, 100, 100, 0.9);
  }
}

.bottom {
  position: absolute;
  bottom: 3px;
  left: 3px;
  right: 3px;
  background: rgba(200, 200, 200, 0.7); // 浅灰色半透明
  backdrop-filter: blur(4px);
  top: 80%;
  border-radius: 29px;
  z-index: 2;
  box-shadow: rgba(96, 75, 74, 0.1) 0px 5px 5px 0px inset;
  overflow: hidden;
  transition: all 0.5s cubic-bezier(0.645, 0.045, 0.355, 1) 0s;

  .content {
    position: absolute;
    bottom: 0;
    left: 20px;
    right: 20px;
    height: 160px;

    .name {
      display: block;
      font-size: 1.2rem;
      color: rgba(80, 80, 80, 0.95); // 深灰色文字
      font-weight: bold;
    }

    .about-me {
      display: block;
      font-size: 0.9rem;
      color: rgba(100, 100, 100, 0.85); // 中灰色文字
      margin-top: 12px;
      line-height: 1.3;
    }
  }

  .bottom-bottom {
    position: absolute;
    bottom: 12px;
    left: 20px;
    right: 20px;
    display: flex;
    align-items: center;
    justify-content: space-between;

    .social-links-container {
      display: flex;
      gap: 12px;

      svg {
        height: 20px;
        fill: rgba(120, 120, 120, 0.85); // 浅灰色图标
        cursor: pointer;
        transition: transform 0.2s, fill 0.2s;

        &:hover {
          fill: rgba(100, 100, 100, 0.95);
          transform: scale(1.2);
        }
      }
    }

    .button {
      background: rgba(240, 240, 240, 0.9); // 浅灰色背景
      color: rgba(120, 120, 120, 0.9);
      border: none;
      border-radius: 20px;
      font-size: 0.7rem;
      padding: 6px 12px;
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        background: rgba(160, 160, 160, 0.9);
        color: rgba(255, 255, 255, 0.95);
      }
    }
  }
}

// Hover 动画
.card:hover {
  border-top-left-radius: 55px;

  .bottom {
    top: 20%;
    border-radius: 80px 29px 29px 29px;
    transition: all 0.5s cubic-bezier(0.645, 0.045, 0.355, 1) 0.2s;
  }

  .profile-pic {
    width: 100px;
    height: 100px;
    top: 10px;
    left: 10px;
    border-radius: 50%;
    z-index: 3;
    border: 7px solid rgba(180, 180, 180, 0.7); // 浅灰色边框
    box-shadow: rgba(96, 75, 74, 0.1) 0px 5px 5px 0px;
    transition: all 0.5s ease-in-out, z-index 0.5s ease-in-out 0.1s;

    &:hover {
      transform: scale(1.3);
      border-radius: 0;
    }

    img {
      transform: scale(2.5);
      object-position: 0px 25px;
      transition: all 0.5s ease-in-out 0.5s;
    }

    .avatar-placeholder {
      transform: scale(1.5);
      transition: all 0.5s ease-in-out 0.5s;
    }
  }
}
</style>