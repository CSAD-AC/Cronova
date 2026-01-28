import { get } from '@/utils/request'

export interface LogFileInfo {
  bucket: string
  objectName: string
  fileName: string
  signedUrl?: string
}

export interface MinioFileInfo {
  bucket: string
  objectName: string
  fileName: string
  signedUrl?: string
}

/**
 * MinIO服务
 * 提供文件下载功能
 */
export const minioApi = {
  /**
   * 从MinIO直接下载文件
   * @param fileInfo 文件信息
   * @returns Promise<Blob>
   */
  downloadFile: async (fileInfo: MinioFileInfo): Promise<Blob> => {
    // 优先使用预签名URL
    if (fileInfo.signedUrl) {
      try {
        console.log('使用预签名URL下载:', fileInfo.signedUrl)
        const response = await fetch(fileInfo.signedUrl)
        if (!response.ok) {
          throw new Error(`预签名URL访问失败: ${response.status} ${response.statusText}`)
        }
        return response.blob()
      } catch (error) {
        console.error('预签名URL下载失败:', error)
        // 如果预签名URL失败，继续尝试其他方式
      }
    }

    // 如果没有预签名URL或预签名URL失败，尝试其他方式
    console.warn('预签名URL不可用，请确保后端配置正确')
    throw new Error('MinIO文件访问失败，请检查后端配置')
  },

  /**
   * 从MinIO直接获取文件文本内容
   * @param fileInfo 文件信息
   * @returns Promise<string>
   */
  downloadTextFile: async (fileInfo: MinioFileInfo): Promise<string> => {
    const blob = await minioApi.downloadFile(fileInfo)
    return blob.text()
  },

  /**
   * 检查文件是否存在
   * 注意：使用预签名URL方式，此功能不再需要
   * @param fileInfo 文件信息
   * @returns Promise<boolean>
   */
  fileExists: async (fileInfo: MinioFileInfo): Promise<boolean> => {
    console.warn('fileExists功能已不再需要，因为使用预签名URL')
    return true // 假设文件存在，由实际下载操作验证
  },
}
