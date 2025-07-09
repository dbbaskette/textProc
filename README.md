# 📝 textProc - Document Text Processing Pipeline

![Java](https://img.shields.io/badge/Java-21-blue?logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.5-brightgreen?logo=springboot)
![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2024.0.1-orange?logo=spring)
![Apache Tika](https://img.shields.io/badge/Apache_Tika-2.9.2-yellow?logo=apache)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.12.0-orange?logo=rabbitmq)
![HDFS](https://img.shields.io/badge/HDFS-3.3.0-lightblue?logo=apache)

A powerful Spring Boot application for extracting and processing text from documents using Apache Tika. Features event-driven architecture, Spring Cloud Data Flow (SCDF) integration, and real-time processing controls for demo management.

## 🚀 Quick Start

### Prerequisites
- **Java 21+**
- **Maven 3.8+**
- **RabbitMQ** (for SCDF mode)
- **HDFS** (for HDFS file processing)

### Build & Run

```bash
# Build the application
mvn clean package

# Run in standalone mode (processes local files)
mvn spring-boot:run -Dspring-boot.run.profiles=standalone

# Run in SCDF mode (processes files from RabbitMQ queue)
mvn spring-boot:run -Dspring-boot.run.profiles=scdf
```

## 🏗️ Architecture

### Event-Driven Design
The application uses Spring's event publishing mechanism for loose coupling between services:

```java
// ProcessingStateService publishes events
public void startProcessing() {
    isProcessingEnabled.set(true);
    eventPublisher.publishEvent(new ProcessingStartedEvent(this));
}

// ConsumerLifecycleService listens to events
@EventListener
public void handleProcessingStarted(ProcessingStartedEvent event) {
    resumeConsumers();
}
```

### Key Benefits
- **No Circular Dependencies**: Services communicate through events
- **Loose Coupling**: Services don't directly depend on each other
- **Testability**: Each service can be tested independently
- **Extensibility**: Easy to add new event listeners
- **Single Responsibility**: Each service has a clear, focused purpose

## 📋 Features

### 🔍 **Document Processing**
- **Multi-format Support**: PDF, DOC, DOCX, TXT, RTF, and more
- **Apache Tika Integration**: Robust text extraction from complex documents
- **Large File Handling**: Efficient processing of large documents with chunking
- **Error Recovery**: Graceful handling of corrupted or unsupported files

### 🔄 **Processing Modes**

#### **Standalone Mode** (`standalone` profile)
- Processes files from local directories
- Moves processed files to output directories
- Handles errors with dedicated error directory
- Simple file-by-file processing

#### **SCDF Mode** (`scdf` profile) 
- Processes files from RabbitMQ message queue
- Downloads files from HDFS/S3
- Writes processed text back to HDFS
- Sends processed file locations to output queue
- Web UI for monitoring processed files

### 🎛️ **Processing Controls**
- **Start/Stop Processing**: Control when files are processed
- **Consumer Management**: Pause/resume RabbitMQ consumers
- **Reset Functionality**: Clear processed files and restart fresh
- **Real-time Status**: Monitor processing state and consumer status
- **Demo Management**: Perfect for controlled demonstrations

### 🌐 **Web Interface**
- **Real-time Monitoring**: View all processed files with details
- **Text Preview**: Click filenames to view extracted text content
- **Processing Statistics**: File sizes, chunk counts, processing times
- **Stream Information**: Input/output stream configuration
- **Processing Controls**: Start/stop/reset buttons with status display

## ⚙️ Configuration

### Environment Variables

#### **Required for SCDF Mode**
```bash
# RabbitMQ Configuration (auto-configured by Cloud Foundry service binding)
SPRING_RABBITMQ_HOST=localhost
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest

# HDFS Configuration (for file processing)
HDFS_NAMENODE_URL=http://namenode:50070/webhdfs/v1

# S3/MinIO Configuration (optional)
S3_ENDPOINT=http://localhost:9000
S3_ACCESS_KEY=your-access-key
S3_SECRET_KEY=your-secret-key
```

#### **Standalone Mode Configuration**
```properties
# application-standalone.properties
app.processor.standalone.input-directory=./data/input_files
app.processor.standalone.output-directory=./data/output_text
app.processor.standalone.error-directory=./data/error_files
app.processor.standalone.processed-directory=./data/processed_files
```

### Message Format

#### **Input Message (SCDF Mode)**
```json
{
  "type": "HDFS",
  "url": "http://35.196.56.130:9870/webhdfs/v1/documents/document.pdf",
  "inputStream": "hdfswatcher-textproc",
  "outputStream": "textproc-embedproc"
}
```

#### **Output Message (SCDF Mode)**
```json
{
  "type": "HDFS",
  "url": "http://35.196.56.130:9870/webhdfs/v1/processed_files/document.pdf.txt",
  "inputStream": "hdfswatcher-textproc", 
  "outputStream": "textproc-embedproc",
  "processed": true,
  "originalFile": "http://35.196.56.130:9870/webhdfs/v1/documents/document.pdf"
}
```

## 🐳 Docker Deployment

### Build Docker Image
```bash
# Build the application
mvn clean package

# Build Docker image
docker build -t textproc:latest .
```

### Run with Docker Compose
```yaml
# docker-compose.yml
version: '3.8'
services:
  textproc:
    image: textproc:latest
    environment:
      - SPRING_PROFILES_ACTIVE=scdf
      - SPRING_RABBITMQ_HOST=rabbitmq
      - HDFS_NAMENODE_URL=http://namenode:50070/webhdfs/v1
    ports:
      - "8080:8080"
    depends_on:
      - rabbitmq
      
  rabbitmq:
    image: rabbitmq:3.12-management
    ports:
      - "5672:5672"
      - "15672:15672"
```

## ☁️ Cloud Foundry Deployment

### Deploy to Cloud Foundry
```bash
# Build the application
mvn clean package

# Deploy to Cloud Foundry
cf push textProc -p target/textProc-1.9.4.jar

# Set environment variables
cf set-env textProc SPRING_PROFILES_ACTIVE scdf
cf set-env textProc HDFS_NAMENODE_URL http://your-namenode:50070/webhdfs/v1
```

### Cloud Foundry Manifest
```yaml
# manifest.yml
applications:
- name: textProc
  memory: 1G
  instances: 1
  buildpacks:
    - java_buildpack
  env:
    SPRING_PROFILES_ACTIVE: scdf
    HDFS_NAMENODE_URL: http://your-namenode:50070/webhdfs/v1
```

## 🔧 Development

### Project Structure
```
textProc/
├── src/main/java/com/baskettecase/textProc/
│   ├── config/           # Configuration classes
│   ├── controller/       # Web controllers
│   ├── model/           # Data models
│   ├── processor/       # Processing logic
│   └── service/         # Business services
├── src/main/resources/
│   ├── templates/       # Thymeleaf templates
│   └── application-*.properties
└── target/              # Build output
```

### Key Components

#### **ProcessingStateService**
- Manages application processing state (started/stopped)
- Publishes events when state changes
- Uses atomic boolean for thread-safe state management

#### **ConsumerLifecycleService**
- Manages RabbitMQ consumer lifecycle
- Listens to processing state events
- Pauses/resumes consumers based on processing state

#### **ScdfStreamProcessor**
- Handles RabbitMQ message processing
- Downloads files from HDFS/S3
- Extracts text using Apache Tika
- Writes processed files to HDFS
- Sends processed file locations to output queue

#### **ExtractionService**
- Core text extraction logic
- Supports chunked processing for large files
- Handles multiple document formats
- Writes processed text to temporary files for UI

#### **FileProcessingService**
- Tracks processed files
- Maintains processing statistics
- Provides data for web interface

### Running Tests
```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=ExtractionServiceTest

# Run with coverage
mvn test jacoco:report
```

## 📊 Monitoring & Logging

### Web Interface
- **URL**: `http://localhost:8080/` or `http://your-app-url/`
- **Features**: 
  - View all processed files
  - Click filenames to preview extracted text
  - Monitor processing statistics
  - Check stream configuration
  - Control processing state

### REST API Endpoints

#### **Processing Controls**
- `POST /api/processing/start` - Start processing
- `POST /api/processing/stop` - Stop processing
- `POST /api/processing/reset` - Reset (stop + clear files)
- `GET /api/processing/state` - Get current state

#### **Health & Monitoring**
- `GET /actuator/health` - Application health check
- `GET /actuator/info` - Application information
- `GET /actuator/metrics` - Application metrics

### Logging
```bash
# View application logs
cf logs textProc --recent

# Follow logs in real-time
cf logs textProc

# Filter for processing messages
cf logs textProc --recent | grep "Processing"
```

## 🔄 Processing Workflow

### SCDF Mode Workflow
1. **Receive Message**: RabbitMQ message with file URL
2. **Check Processing State**: Only process if enabled
3. **Download File**: Download from HDFS/S3 to local temp storage
4. **Extract Text**: Use Apache Tika to extract text content
5. **Write to HDFS**: Save processed text to `/processed_files/` directory
6. **Send Message**: Send processed file location to output queue
7. **Update UI**: Update web interface with processing status
8. **Cleanup**: Remove temporary files

### Standalone Mode Workflow
1. **Scan Directory**: Monitor input directory for new files
2. **Process Files**: Extract text from each file
3. **Write Output**: Save extracted text to output directory
4. **Move Files**: Move processed files to processed directory
5. **Handle Errors**: Move failed files to error directory

## 🛠️ Troubleshooting

### Common Issues

#### **Files Not Appearing in UI**
- Check if processing is enabled (default: STOPPED)
- Verify RabbitMQ connection
- Ensure HDFS URLs are accessible

#### **Processing Failures**
- Check file format support
- Verify HDFS/S3 connectivity
- Review error logs for specific issues

#### **Performance Issues**
- Monitor memory usage
- Check chunk size configuration
- Verify network connectivity to HDFS/S3

### Debug Mode
```bash
# Enable debug logging
cf set-env textProc LOGGING_LEVEL_COM_BASKETTECASE DEBUG

# Restart application
cf restart textProc
```

## 📚 Documentation

- **[CONTROLS.md](CONTROLS.md)** - Processing controls and demo management
- **[gotchas.md](gotchas.md)** - Known issues and solutions
- **[implementation_details.md](implementation_details.md)** - Technical implementation details

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

- **Issues**: Report bugs and feature requests on GitHub
- **Documentation**: Check the [gotchas.md](gotchas.md) for known issues
- **Logs**: Review application logs for troubleshooting

---

**Version**: 1.9.4  
**Last Updated**: July 2024
