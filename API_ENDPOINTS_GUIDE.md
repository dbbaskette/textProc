# TextProc Processing Control API

This document describes the API endpoints for controlling the processing state of the textProc application.

## Overview

The API allows you to control whether the textProc application processes messages from the RabbitMQ queue for text extraction and file processing. The application defaults to **stopped** state for controlled processing startup.

## Base URL

When running with default settings:
```
http://localhost:8080/api
```

## Core Processing Control Endpoints

### 1. GET /api/processing/state

**Purpose**: Get the current processing state

**Example Request**:
```bash
curl -X GET http://localhost:8080/api/processing/state
```

**Example Response**:
```json
{
  "enabled": true,
  "status": "STARTED",
  "consumerStatus": "CONSUMING",
  "lastChanged": "2025-01-01T12:34:56.789Z",
  "lastChangeReason": "Application startup",
  "timestamp": "2025-01-01T12:35:00.123Z"
}
```

### 2. POST /api/processing/start

**Purpose**: Start/enable file processing

**Example Request**:
```bash
curl -X POST http://localhost:8080/api/processing/start
```

**Example Response**:
```json
{
  "success": true,
  "message": "Processing started successfully",
  "stateChanged": true,
  "enabled": true,
  "status": "STARTED",
  "consumerStatus": "CONSUMING",
  "lastChanged": "2025-01-01T12:35:10.456Z",
  "timestamp": "2025-01-01T12:35:10.456Z"
}
```

### 3. POST /api/processing/stop

**Purpose**: Stop/disable file processing

**Example Request**:
```bash
curl -X POST http://localhost:8080/api/processing/stop
```

**Example Response**:
```json
{
  "success": true,
  "message": "Processing stopped successfully. Messages will remain in queue.",
  "stateChanged": true,
  "enabled": false,
  "status": "STOPPED",
  "consumerStatus": "IDLE",
  "lastChanged": "2025-01-01T12:35:20.789Z",
  "timestamp": "2025-01-01T12:35:20.789Z"
}
```

### 4. POST /api/processing/toggle

**Purpose**: Toggle processing state (if enabled → disable, if disabled → enable)

**Example Request**:
```bash
curl -X POST http://localhost:8080/api/processing/toggle
```

**Example Response**:
```json
{
  "success": true,
  "message": "Processing started successfully. Previous state: disabled, Current state: enabled. Now consuming messages from queue.",
  "action": "started",
  "previousState": {
    "enabled": false,
    "status": "STOPPED"
  },
  "currentState": {
    "enabled": true,
    "status": "STARTED",
    "consumerStatus": "CONSUMING"
  },
  "lastChanged": "2025-01-01T12:35:30.123Z",
  "timestamp": "2025-01-01T12:35:30.123Z"
}
```

### 5. POST /api/processing/reset

**Purpose**: Reset processing (stop processing and clear processed files)

**Example Request**:
```bash
curl -X POST http://localhost:8080/api/processing/reset
```

**Example Response**:
```json
{
  "success": true,
  "message": "Reset completed successfully. Processing stopped and files cleared.",
  "stateChanged": true,
  "enabled": false,
  "status": "STOPPED",
  "consumerStatus": "IDLE",
  "hdfsCleared": true,
  "directoryRecreated": true,
  "lastChanged": "2025-01-01T12:35:40.123Z",
  "timestamp": "2025-01-01T12:35:40.123Z"
}
```

## File Management Endpoints

### 6. GET /api/files/processed

**Purpose**: Get list of all processed files

**Example Request**:
```bash
curl -X GET http://localhost:8080/api/files/processed
```

**Example Response**:
```json
[
  {
    "fileUrl": "http://example.com/files/document1.pdf",
    "processedAt": "2025-01-01T12:34:56.789Z",
    "textLength": 15420,
    "chunkCount": 8,
    "status": "SUCCESS",
    "processingTimeMs": 2340
  },
  {
    "fileUrl": "http://example.com/files/document2.pdf", 
    "processedAt": "2025-01-01T12:35:12.456Z",
    "textLength": 8760,
    "chunkCount": 5,
    "status": "SUCCESS",
    "processingTimeMs": 1890
  }
]
```

### 7. GET /api/files/pending

**Purpose**: Get information about pending files/tasks

**Example Request**:
```bash
curl -X GET http://localhost:8080/api/files/pending
```

**Example Response**:
```json
{
  "pendingCount": 3,
  "queueDepth": 3,
  "estimatedProcessingTime": "45 seconds",
  "nextFileUrl": "http://example.com/files/document3.pdf"
}
```

## Behavior Details

### When Processing is STARTED
- **Status**: `"STARTED"`
- **Enabled**: `true`
- **Consumer Status**: `"CONSUMING"`
- **Behavior**: Application consumes messages from RabbitMQ queue and processes files
- **Queue**: Messages are consumed and processed for text extraction

### When Processing is STOPPED
- **Status**: `"STOPPED"`
- **Enabled**: `false`
- **Consumer Status**: `"IDLE"`
- **Behavior**: Application ignores messages, leaves them in queue
- **Queue**: Messages remain untouched, ready for when processing is re-enabled

## Response Field Explanations

| Field | Description |
|-------|-------------|
| `enabled` | Boolean indicating if processing is enabled |
| `status` | String status: `"STARTED"` or `"STOPPED"` |
| `consumerStatus` | String indicating queue behavior: `"CONSUMING"` or `"IDLE"` |
| `lastChanged` | ISO 8601 timestamp of last state change |
| `lastChangeReason` | Human-readable reason for the last state change |
| `timestamp` | ISO 8601 timestamp of the API response |
| `stateChanged` | Boolean indicating if the API call changed the state |
| `action` | (toggle only) Action performed: `"started"` or `"stopped"` |
| `previousState` | (toggle only) State before the toggle |
| `currentState` | (toggle only) State after the toggle |
| `hdfsCleared` | Boolean indicating if HDFS processed directory was cleared |
| `directoryRecreated` | Boolean indicating if HDFS directory was recreated |
| `textLength` | Number of characters extracted from the file |
| `chunkCount` | Number of text chunks created from the file |
| `processingTimeMs` | Time taken to process the file in milliseconds |

## Key Features

1. **Event-Driven**: Uses Spring events to notify components of state changes
2. **Consumer Lifecycle**: Manages RabbitMQ consumer start/stop based on processing state
3. **File Tracking**: Maintains history of processed files with detailed metadata
4. **HDFS Integration**: Can clear and recreate HDFS processed files directory
5. **Pending Queue**: Tracks pending files and queue depth
6. **Atomic Operations**: Thread-safe processing state management

## Service Architecture

The textProc service includes:
- **ProcessingStateService**: Manages processing enabled/disabled state
- **ConsumerLifecycleService**: Controls RabbitMQ consumer lifecycle
- **FileProcessingService**: Handles file processing and tracking
- **HdfsService**: Manages HDFS operations for processed files
- **PendingFilesService**: Tracks pending files and queue status

## Error Handling

All endpoints return appropriate HTTP status codes:
- `200 OK`: Successful operation
- `400 Bad Request`: Invalid request data  
- `500 Internal Server Error`: Server-side error

Error responses follow standard format:
```json
{
  "status": "error",
  "message": "Description of the error",
  "timestamp": "2025-01-01T12:34:56.789Z"
}
```

## Integration Notes

- **Spring Cloud Stream**: Uses binding-based message consumption
- **HDFS**: Manages processed files directory in HDFS
- **Event Publishing**: Publishes processing start/stop events for other components
- **Thread Safety**: Uses `AtomicBoolean` for state management
