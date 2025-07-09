# Processing Controls

This application now includes processing controls to help manage demo timing and file processing operations.

## Controls Overview

### Start Processing
- **Button**: Green "Start Processing" button with play icon
- **Action**: Enables file processing
- **Effect**: The application will begin processing incoming files from the stream
- **Default State**: Processing is **STOPPED** by default

### Stop Processing
- **Button**: Yellow "Stop Processing" button with pause icon
- **Action**: Disables file processing
- **Effect**: The application will stop processing incoming files (messages are still received but ignored)

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
- `HdfsService`: Handles HDFS operations for reset functionality
- REST endpoints: `/api/processing/start`, `/api/processing/stop`, `/api/processing/reset`

### Frontend Features
- Real-time status display with color-coded badges
- Confirmation dialog for reset operation
- Toast notifications for operation feedback
- Auto-refresh after reset operations

### SCDF Integration
- The `ScdfStreamProcessor` checks processing state before processing files
- When stopped, messages are received but ignored (empty response returned)
- When started, normal processing continues

## Configuration

The processing state is maintained in memory and resets to **STOPPED** when the application restarts. This ensures demos always start in a controlled state. 