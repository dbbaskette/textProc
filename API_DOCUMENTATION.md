# TextProc API Documentation

This document provides documentation for the TextProc API.

## Base Path

The base path for all API endpoints is `/api`.

## Endpoints

### Get Processed Files

*   **Method**: `GET`
*   **Path**: `/files/processed`
*   **Description**: Retrieves a list of all files that have been processed.
*   **Response**: A JSON array of `FileProcessingInfo` objects.

### Get Pending Files

*   **Method**: `GET`
*   **Path**: `/files/pending`
*   **Description**: Retrieves a list of all files that are currently pending processing.
*   **Response**: A JSON object containing a list of pending items.

### Start Processing

*   **Method**: `POST`
*   **Path**: `/processing/start`
*   **Description**: Starts the file processing service.
*   **Response**: A JSON object confirming that processing has started.

### Stop Processing

*   **Method**: `POST`
*   **Path**: `/processing/stop`
*   **Description**: Stops the file processing service.
*   **Response**: A JSON object confirming that processing has stopped.

### Toggle Processing

*   **Method**: `POST`
*   **Path**: `/processing/toggle`
*   **Description**: Toggles the file processing service on or off.
*   **Response**: A JSON object confirming the new state of the processing service.

### Reset Processing

*   **Method**: `POST`
*   **Path**: `/processing/reset`
*   **Description**: Resets the processing state, clearing all processed files and stopping the processing service.
*   **Response**: A JSON object confirming that the processing state has been reset.

### Get Processing State

*   **Method**: `GET`
*   **Path**: `/processing/state`
*   **Description**: Retrieves the current state of the processing service.
*   **Response**: A JSON object containing the current state of the processing service.
