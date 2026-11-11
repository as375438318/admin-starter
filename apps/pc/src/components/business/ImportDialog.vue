<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElMessage, type UploadFile, type UploadInstance } from 'element-plus';
import { ApiError, saveBlob, type FileDownload } from '@/api/http';
import type { ImportErrorData, ImportResult } from '@/api/types';

/**
 * 通用导入对话框：文件选择/拖拽、体积与后缀前置提示、单一诚实处理提示、
 * 三个数字的结果文案、错误清单表格、模板下载入口。
 * 业务页面只传模板下载与导入接口，不得在页面内重新实现错误清单或结果文案。
 */
const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    title: string;
    /** 业务名称，用于提示文案，如「岗位」 */
    subject: string;
    /** 模板下载接口 */
    loadTemplate: () => Promise<FileDownload>;
    /** 导入接口（同步返回结果，422 时错误在 ApiError.importData） */
    importFile: (file: File) => Promise<ImportResult>;
    /** 模板文件名兜底 */
    templateFileName: string;
  }>(),
  { templateFileName: '导入模板.xlsx' },
);

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  imported: [result: ImportResult];
}>();

const uploadRef = ref<UploadInstance>();
const selectedFile = ref<File | null>(null);
const templateLoading = ref(false);
const importing = ref(false);
const importErrors = ref<ImportErrorData | null>(null);
const importResult = ref<ImportResult | null>(null);

function count(value: number): string {
  return value.toLocaleString('zh-CN');
}

function reset(): void {
  selectedFile.value = null;
  importErrors.value = null;
  importResult.value = null;
  uploadRef.value?.clearFiles();
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      reset();
    }
  },
);

function close(): void {
  if (!importing.value) {
    emit('update:modelValue', false);
  }
}

async function downloadTemplate(): Promise<void> {
  if (templateLoading.value) {
    return;
  }
  templateLoading.value = true;
  try {
    const file = await props.loadTemplate();
    saveBlob(file.blob, file.fileName ?? props.templateFileName);
  } catch (error) {
    if (error instanceof ApiError) {
      ElMessage.error(error.message);
    }
  } finally {
    templateLoading.value = false;
  }
}

function handleFileChange(file: UploadFile): void {
  selectedFile.value = file.raw ?? null;
  importErrors.value = null;
  importResult.value = null;
}

function handleFileRemove(): void {
  selectedFile.value = null;
}

/** 供页面工具栏复用同一个模板下载实现与加载态。 */
defineExpose({ downloadTemplate, templateLoading });

async function submit(): Promise<void> {
  if (!selectedFile.value || importing.value) {
    return;
  }
  importing.value = true;
  importErrors.value = null;
  importResult.value = null;
  try {
    const result = await props.importFile(selectedFile.value);
    importResult.value = result;
    ElMessage.success(`成功导入 ${count(result.imported)} 行`);
    emit('imported', result);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.importData) {
        importErrors.value = error.importData;
      } else if (error.status === 422) {
        ElMessage.error(error.message);
      }
    }
  } finally {
    importing.value = false;
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="640px"
    :close-on-click-modal="false"
    :close-on-press-escape="!importing"
    :show-close="!importing"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert class="import-alert" type="info" :closable="false" show-icon>
      <template #title>
        请使用本页「下载模板」生成的 .xlsx 文件填写：单文件不超过 50MB，数据不超过 20,000 行。
      </template>
      <template #default>
        <p class="import-note">
          还没有模板？
          <el-button link type="primary" :loading="templateLoading" :disabled="templateLoading" @click="downloadTemplate">
            下载{{ subject }}导入模板
          </el-button>
        </p>
      </template>
    </el-alert>
    <el-upload
      ref="uploadRef"
      class="import-upload"
      drag
      :auto-upload="false"
      :limit="1"
      accept=".xlsx"
      :on-change="handleFileChange"
      :on-remove="handleFileRemove"
      :disabled="importing"
    >
      <div class="import-upload-text">将 .xlsx 文件拖到此处，或<em>点击选择</em></div>
    </el-upload>

    <el-alert v-if="importing" class="import-alert" type="info" :closable="false" show-icon>
      <template #title>正在校验并写入，请勿关闭页面，预计 10 秒内完成</template>
    </el-alert>

    <el-alert v-else-if="importResult" class="import-alert" type="success" :closable="false" show-icon>
      <template #title>
        导入成功：共 {{ count(importResult.totalRows) }} 行，已写入 {{ count(importResult.imported) }} 行。
      </template>
    </el-alert>

    <template v-else-if="importErrors">
      <el-alert class="import-alert" type="error" :closable="false" show-icon>
        <template #title>
          共 {{ count(importErrors.totalRows) }} 行，其中 {{ count(importErrors.errorRows) }} 行有误（{{
            count(importErrors.errorCount)
          }} 处），以下列出前 {{ count(importErrors.errors.length) }} 条。
        </template>
        <template #default>
          <p class="import-note">
            整批未写入，请按下列清单修改后重新上传。
            <span v-if="importErrors.remainingErrors > 0">
              另有 {{ count(importErrors.remainingErrors) }} 条未显示。
            </span>
          </p>
        </template>
      </el-alert>
      <el-table :data="importErrors.errors" size="small" max-height="280" class="import-errors">
        <el-table-column prop="field" label="位置" width="200" show-overflow-tooltip />
        <el-table-column prop="message" label="原因" min-width="240" show-overflow-tooltip />
      </el-table>
    </template>

    <template #footer>
      <el-button :disabled="importing" @click="close">关闭</el-button>
      <el-button type="primary" :loading="importing" :disabled="!selectedFile || importing" @click="submit">
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.import-alert {
  margin-bottom: var(--space-3);
}

.import-note {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-sm);
}

.import-upload {
  margin-bottom: var(--space-3);
}

.import-upload-text {
  padding: var(--space-6) 0;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
}

.import-upload-text em {
  color: var(--color-primary);
  font-style: normal;
}

.import-errors {
  margin-top: var(--space-3);
}
</style>
