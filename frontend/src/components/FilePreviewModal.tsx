import React from 'react';
import { Modal, Button, Space, Typography } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import type { FileAttachment } from '@/types/fileAttachment';
import { getFilePreviewUrl, getFileDownloadUrl, formatFileSize } from '@/api/fileApi';

const { Text } = Typography;

interface FilePreviewModalProps {
  file: FileAttachment | null;
  open: boolean;
  onClose: () => void;
}

const FilePreviewModal: React.FC<FilePreviewModalProps> = ({ file, open, onClose }) => {
  if (!file) return null;

  const previewUrl = getFilePreviewUrl(file.id);
  const downloadUrl = getFileDownloadUrl(file.id);
  const isImage = file.mimeType.startsWith('image/');
  const isPdf = file.mimeType === 'application/pdf';
  const isDocx = file.mimeType === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    || file.originalName.toLowerCase().endsWith('.docx');
  // DOCX files are converted to PDF on backend, so treat as PDF for preview
  const showAsDocument = isPdf || isDocx;

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={file.originalName}
      width={isImage ? 700 : 900}
      footer={
        <Space>
          <Text type="secondary">{formatFileSize(file.fileSize)}</Text>
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            href={downloadUrl}
            target="_blank"
          >
            Завантажити
          </Button>
        </Space>
      }
      styles={{ body: { padding: 0, minHeight: 400 } }}
    >
      {showAsDocument && (
        <iframe
          src={previewUrl}
          style={{ width: '100%', height: '75vh', border: 'none' }}
          title={file.originalName}
        />
      )}
      {isImage && (
        <div style={{ textAlign: 'center', padding: 16 }}>
          <img
            src={previewUrl}
            alt={file.originalName}
            style={{ maxWidth: '100%', maxHeight: '70vh', objectFit: 'contain' }}
          />
        </div>
      )}
      {!showAsDocument && !isImage && (
        <div style={{ padding: 24, textAlign: 'center' }}>
          <Text>Попередній перегляд недоступний для цього типу файлу.</Text>
        </div>
      )}
    </Modal>
  );
};

export default FilePreviewModal;
