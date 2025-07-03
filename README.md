# 📝 textProc

![Java](https://img.shields.io/badge/Java-21-blue?logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.5-brightgreen?logo=springboot)
![Apache Tika](https://img.shields.io/badge/Apache_Tika-2.9.2-yellow?logo=apache)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.12.0-orange?logo=rabbitmq)

A Spring Boot application for document text extraction and processing with Apache Tika and Spring Cloud Stream.

## ✨ Features

- 📄 Document text extraction using Apache Tika
- 🔄 Spring Cloud Stream integration with RabbitMQ
- 📂 Processes files from HDFS or local filesystem
- 🚀 Profile-based configuration (standalone, scdf)
- 📝 Chunked text processing for large documents

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+
- RabbitMQ (for SCDF mode)
- HDFS (optional, for HDFS file processing)

### Build
```sh
mvn clean install
```

### Run in Standalone Mode
```sh
mvn spring-boot:run -Dspring-boot.run.profiles=standalone
```

### Run in SCDF Mode
```sh
mvn spring-boot:run -Dspring-boot.run.profiles=scdf
```

## ⚙️ Configuration

### Standalone Mode
```properties
# application-standalone.properties
app.processor.mode=standalone
app.processor.standalone.input-directory=./data/input_files
app.processor.standalone.output-directory=./data/output_text
app.processor.standalone.error-directory=./data/error_files
app.processor.standalone.processed-directory=./data/processed_files
```

### SCDF Mode
```properties
# application-scdf.properties
app.processor.mode=scdf
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

## 📦 Docker

Build and run with Docker:

```sh
# Build the application
mvn clean package

# Build Docker image
docker build -t textproc .

# Run with RabbitMQ
docker run -p 8080:8080 --name textproc --network host textproc
```

## 📚 Documentation

- [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream)
- [Apache Tika](https://tika.apache.org/)
- [RabbitMQ](https://www.rabbitmq.com/)
- [Release Process](docs/release-process.md) - Complete guide to releasing new versions

Example for Spring Cloud Data Flow (SCDF) mode:

```properties
# src/main/resources/application-scdf.properties
app.processor.mode=scdf
app.processor.scdf.input-channel=textProcInput
app.processor.scdf.output-channel=textProcOutput
```

Switch profiles with:
```sh
-Dspring.profiles.active=standalone   # or scdf
```

---

## 📦 Usage
- Drop files into the input directory (e.g., `./data/input_files`)
- Processed text will appear in the output directory
- Errors and processed originals are moved to their respective directories

---

## 🚀 Releases

This project uses a generic automated release script that works with any Maven project. The script automatically detects project details and handles the complete release process.

### Quick Release
```bash
./release.sh
```

The script will:
1. Auto-detect project name from `pom.xml`
2. Update version numbers in `VERSION` and `pom.xml`
3. Build the JAR file using Maven
4. Create Git tags and push changes
5. Create GitHub releases with JAR attachments

**🔗 Generic Script**: [Maven Release Script Gist](https://gist.github.com/dbbaskette/e3c3b0c7ff90c715c6b11ca1e45bb3a6) - Reusable for any Maven project!

For detailed information, see the [Release Process Documentation](docs/release-process.md).

---

## 🤝 Contributing
Pull requests are welcome! For major changes, please open an issue first to discuss what you would like to change.

---

## 📝 License
[MIT](LICENSE)

---

> Made with ❤️ using Spring Boot & Apache Tika
