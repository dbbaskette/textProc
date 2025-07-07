# 📝 textProc - Document Text Processing Pipeline

![Java](https://img.shields.io/badge/Java-21-blue?logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.5-brightgreen?logo=springboot)
![Apache Tika](https://img.shields.io/badge/Apache_Tika-2.9.2-yellow?logo=apache)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.12.0-orange?logo=rabbitmq)
![HDFS](https://img.shields.io/badge/HDFS-3.3.0-lightblue?logo=apache)

A powerful Spring Boot application for extracting and processing text from documents using Apache Tika. Supports both standalone file processing and Spring Cloud Data Flow (SCDF) stream processing with RabbitMQ integration.

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

### 🌐 **Web Interface**
- **Real-time Monitoring**: View all processed files with details
- **Text Preview**: Click filenames to view extracted text content
- **Processing Statistics**: File sizes, chunk counts, processing times
- **Stream Information**: Input/output stream configuration

## ⚙️ Configuration

### Environment Variables

#### **Required for SCDF Mode**
```bash
# RabbitMQ Configuration
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
  "url": "http://namenode:50070/webhdfs/v1/input/document.pdf",
  "inputStream": "file-input",
  "outputStream": "text-output"
}
```

#### **Output Message (SCDF Mode)**
```json
{
  "type": "HDFS",
  "url": "http://namenode:50070/webhdfs/v1/processed_files/document.pdf.txt",
  "inputStream": "file-input", 
  "outputStream": "text-output",
  "processed": true,
  "originalFile": "http://namenode:50070/webhdfs/v1/input/document.pdf"
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
cf push textProc -p target/textProc-1.2.0.jar

# Set environment variables
cf set-env textProc SPRING_PROFILES_ACTIVE scdf
cf set-env textProc SPRING_RABBITMQ_HOST your-rabbitmq-host
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
    SPRING_RABBITMQ_HOST: your-rabbitmq-host
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

### Logging
```bash
# View application logs
cf logs textProc --recent

# Follow logs in real-time
cf logs textProc

# Filter for processing messages
cf logs textProc --recent | grep "Processing"
```

### Health Checks
- **Health Endpoint**: `GET /actuator/health`
- **Info Endpoint**: `GET /actuator/info`
- **Metrics**: `GET /actuator/metrics`

## 🔄 Processing Workflow

### SCDF Mode Workflow
1. **Receive Message**: RabbitMQ message with file URL
2. **Download File**: Download from HDFS/S3 to local temp storage
3. **Extract Text**: Use Apache Tika to extract text content
4. **Write to HDFS**: Save processed text to `/processed_files/` directory
5. **Send Message**: Send processed file location to output queue
6. **Update UI**: Update web interface with processing status
7. **Cleanup**: Remove temporary files

### Standalone Mode Workflow
1. **Scan Directory**: Monitor input directory for new files
2. **Process Files**: Extract text from each file
3. **Write Output**: Save extracted text to output directory
4. **Move Files**: Move processed files to processed directory
5. **Handle Errors**: Move failed files to error directory

## 🛠️ Troubleshooting

### Common Issues

#### **Files Not Appearing in UI**
- Check if files are being processed (check logs)
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

## 📚 API Reference

### REST Endpoints

#### **GET /** or **GET /files**
- **Description**: Web interface for viewing processed files
- **Response**: HTML page with file list and details

#### **GET /processed-text/{filename}**
- **Description**: Download processed text content
- **Parameters**: `filename` - Name of the processed file
- **Response**: Plain text content

#### **GET /actuator/health**
- **Description**: Application health check
- **Response**: JSON with health status

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

**Version**: 1.2.0  
**Last Updated**: July 2024
