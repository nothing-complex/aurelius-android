# Room Schema Export

This directory contains Room database schema exports for migration tracking.

Schema version 5 (current):
- ChatEntity: id, title, preview, createdAt, updatedAt, parentBranchId
- MessageEntity: id, chatId, role, content, imageUrl, audioUrl, videoUrl, attachmentName, attachmentType, timestamp, parentMessageId
