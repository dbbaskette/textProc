# Processing Controls

This application exposes headless management endpoints for orchestration by an external app.

## Controls Overview

### Start Processing
- REST: `POST /api/processing/start`
- Effect: Enables processing (SCDF bindings STARTED)

### Stop Processing
- REST: `POST /api/processing/stop`
- Effect: Disables processing (SCDF bindings STOPPED)

### Reset
- **Button**: Red "Reset" button with refresh icon
- **Action**: Stops processing and clears all processed files
- **Effect**: 
  - Stops file processing
  - Clears in-memory processed files data
  - Deletes the `/processed_files` directory from HDFS
  - Recreates the empty `/processed_files` directory
  - Refreshes the UI to show the cleared state

## Usage for Demos

1. **Initial State**: Application starts with processing **STOPPED**
2. **Start Demo**: Click "Start Processing" when ready to begin
3. **Control Timing**: Use "Stop Processing" to pause during explanations
4. **Reset Demo**: Use "Reset" to clear everything and start fresh

## Technical Details

### Backend Services
- `ProcessingStateService`: Manages start/stop state using atomic boolean
- `ConsumerLifecycleService`: Manages RabbitMQ consumer lifecycle (pause/resume)
- `HdfsService`: Handles HDFS operations for reset functionality
- REST endpoints: `/api/processing/start`, `/api/processing/stop`, `/api/processing/reset`, `/api/processing/state`, `/api/files/processed`, `/api/files/pending`

### Frontend
- The built-in UI has been removed. Integrate with these APIs from your external app.

### SCDF Integration
- The `ConsumerLifecycleService` manages Spring Cloud Stream binding lifecycle
- When stopped, the function binding is stopped (no messages consumed from queue)
- When started, the function binding is resumed and normal processing continues
- Messages remain in the queue when processing is disabled
- Uses Spring Cloud Stream's BindingsEndpoint for proper lifecycle control

## Configuration

The processing state is maintained in memory and resets to **STOPPED** when the application restarts. This ensures demos always start in a controlled state. 