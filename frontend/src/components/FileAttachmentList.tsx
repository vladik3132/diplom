import { useEffect, useState, useCallback, useImperativeHandle, forwardRef } from 'react';
import { Upload, Button, List, Space, Typography, message, Popconfirm, Tooltip, Tag } from 'antd';
import {
  UploadOutlined, DeleteOutlined, EyeOutlined, DownloadOutlined,
  FilePdfOutlined, FileImageOutlined, FileOutlined, FileWordOutlined,
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import type { FileAttachment } from '@/types/fileAttachment';
import { uploadFile, getFiles, deleteFile, getFileDownloadUrl, formatFileSize } from '@/api/fileApi';
import FilePreviewModal from './FilePreviewModal';

const { Text } = Typography;

export interface PendingFile {
  file: File;
  id: string; // temp client-side id
}

export interface FileAttachmentListRef {
  /** Get pending (not yet uploaded) files */
  getPendingFiles: () => PendingFile[];
  /** Upload all pending files to the server. Returns uploaded attachments. */
  uploadPendingFiles: (entityType: string, entityId: number, teacherId?: number) => Promise<FileAttachment[]>;
  /** Clear pending files */
  clearPending: () => void;
}

interface FileAttachmentListProps {
  entityType: string;
  entityId?: number | null;
  teacherId?: number;
  editable?: boolean;
  maxCount?: number;
}

const FileAttachmentList = forwardRef<FileAttachmentListRef, FileAttachmentListProps>(({
  entityType, entityId, teacherId, editable = true, maxCount,
}, ref) => {
  const [files, setFiles] = useState<FileAttachment[]>([]);
  const [pendingFiles, setPendingFiles] = useState<PendingFile[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [previewFile, setPreviewFile] = useState<FileAttachment | null>(null);

  const isSaved = !!entityId;

  const loadFiles = useCallback(async () => {
    if (!entityId) return;
    setLoading(true);
    try {
      const data = await getFiles(entityType, entityId);
      setFiles(data);
    } catch {
      // silent
    } finally {
      setLoading(false);
    }
  }, [entityType, entityId]);

  // Reset state when entityId changes (e.g. switching from edit to create)
  useEffect(() => {
    setFiles([]);
    setPendingFiles([]);
    if (isSaved) loadFiles();
  }, [entityId]); // eslint-disable-line react-hooks/exhaustive-deps

  // Expose ref methods for parent
  useImperativeHandle(ref, () => ({
    getPendingFiles: () => pendingFiles,
    uploadPendingFiles: async (et: string, eid: number, tid?: number) => {
      const uploaded: FileAttachment[] = [];
      for (const pf of pendingFiles) {
        try {
          const saved = await uploadFile(pf.file, et, eid, tid);
          uploaded.push(saved);
        } catch (e: unknown) {
          const err = e as { response?: { data?: { message?: string } } };
          message.error(`${pf.file.name}: ${err.response?.data?.message || 'Помилка завантаження'}`);
        }
      }
      setPendingFiles([]);
      return uploaded;
    },
    clearPending: () => setPendingFiles([]),
  }));

  const validateFile = (file: File): boolean => {
    const MAX_FILE_SIZE_MB = 60;
    if (file.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
      message.error(`Файл занадто великий. Максимум: ${MAX_FILE_SIZE_MB} МБ`);
      return false;
    }
    const allowed = [
      'application/pdf', 'image/jpeg', 'image/png',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    ];
    if (!allowed.includes(file.type) && !file.name.match(/\.(pdf|jpg|jpeg|png|docx)$/i)) {
      message.error('Непідтримуваний тип файлу. Дозволено: PDF, JPEG, PNG, DOCX');
      return false;
    }
    return true;
  };

  // Upload directly (when entity already saved)
  const handleUpload: UploadProps['customRequest'] = async (options) => {
    const file = options.file as File;
    if (!validateFile(file)) {
      options.onError?.(new Error('Validation failed'));
      return;
    }

    setUploading(true);
    try {
      const saved = await uploadFile(file, entityType, entityId!, teacherId);
      setFiles(prev => [...prev, saved]);
      message.success(`${file.name} завантажено`);
      options.onSuccess?.(saved);
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      message.error(err.response?.data?.message || 'Помилка завантаження');
      options.onError?.(new Error('Upload failed'));
    } finally {
      setUploading(false);
    }
  };

  // Add to pending (when entity not yet saved)
  const handleAddPending: UploadProps['customRequest'] = (options) => {
    const file = options.file as File;
    if (!validateFile(file)) {
      options.onError?.(new Error('Validation failed'));
      return;
    }
    const id = `pending_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    setPendingFiles(prev => [...prev, { file, id }]);
    message.success(`${file.name} додано (буде завантажено після збереження)`);
    options.onSuccess?.({});
  };

  const handleDelete = async (fa: FileAttachment) => {
    try {
      await deleteFile(fa.id);
      setFiles(prev => prev.filter(f => f.id !== fa.id));
      message.success('Файл видалено');
    } catch {
      message.error('Помилка видалення');
    }
  };

  const handleDeletePending = (id: string) => {
    setPendingFiles(prev => prev.filter(f => f.id !== id));
  };

  const getFileIcon = (mimeType: string) => {
    if (mimeType === 'application/pdf') return <FilePdfOutlined style={{ color: '#e74c3c' }} />;
    if (mimeType.startsWith('image/')) return <FileImageOutlined style={{ color: '#3498db' }} />;
    if (mimeType.includes('wordprocessingml') || mimeType.includes('docx'))
      return <FileWordOutlined style={{ color: '#2b579a' }} />;
    return <FileOutlined />;
  };

  const totalCount = files.length + pendingFiles.length;
  const canUploadMore = maxCount === undefined || totalCount < maxCount;

  return (
    <div style={{ marginTop: 8 }}>
      {/* Saved files */}
      {files.length > 0 && (
        <List
          size="small"
          loading={loading}
          dataSource={files}
          renderItem={(fa) => (
            <List.Item
              style={{ padding: '4px 0' }}
              actions={[
                <Tooltip key="preview" title="Переглянути">
                  <Button
                    type="text"
                    size="small"
                    icon={<EyeOutlined />}
                    onClick={() => setPreviewFile(fa)}
                  />
                </Tooltip>,
                <Tooltip key="download" title="Завантажити">
                  <Button
                    type="text"
                    size="small"
                    icon={<DownloadOutlined />}
                    href={getFileDownloadUrl(fa.id)}
                    target="_blank"
                  />
                </Tooltip>,
                ...(editable ? [
                  <Popconfirm
                    key="delete"
                    title="Видалити файл?"
                    onConfirm={() => handleDelete(fa)}
                  >
                    <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>,
                ] : []),
              ]}
            >
              <Space size="small">
                {getFileIcon(fa.mimeType)}
                <Text
                  style={{ cursor: 'pointer', maxWidth: 250 }}
                  ellipsis={{ tooltip: fa.originalName }}
                  onClick={() => setPreviewFile(fa)}
                >
                  {fa.originalName}
                </Text>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  {formatFileSize(fa.fileSize)}
                </Text>
              </Space>
            </List.Item>
          )}
        />
      )}

      {/* Pending files (not yet uploaded) */}
      {pendingFiles.length > 0 && (
        <List
          size="small"
          dataSource={pendingFiles}
          renderItem={(pf) => (
            <List.Item
              style={{ padding: '4px 0' }}
              actions={[
                <Button
                  key="remove"
                  type="text"
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => handleDeletePending(pf.id)}
                />,
              ]}
            >
              <Space size="small">
                {getFileIcon(pf.file.type)}
                <Text style={{ maxWidth: 250 }} ellipsis={{ tooltip: pf.file.name }}>
                  {pf.file.name}
                </Text>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  {formatFileSize(pf.file.size)}
                </Text>
                <Tag color="orange" style={{ fontSize: 10, lineHeight: '16px' }}>очікує</Tag>
              </Space>
            </List.Item>
          )}
        />
      )}

      {editable && canUploadMore && (
        <Upload
          customRequest={isSaved ? handleUpload : handleAddPending}
          showUploadList={false}
          accept=".pdf,.jpg,.jpeg,.png,.docx"
          multiple
        >
          <Button
            size="small"
            icon={<UploadOutlined />}
            loading={uploading}
            style={{ marginTop: totalCount > 0 ? 4 : 0 }}
          >
            Додати файл
          </Button>
        </Upload>
      )}

      <FilePreviewModal
        file={previewFile}
        open={!!previewFile}
        onClose={() => setPreviewFile(null)}
      />
    </div>
  );
});

FileAttachmentList.displayName = 'FileAttachmentList';

export default FileAttachmentList;
