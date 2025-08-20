# Flask Docker Monitor - Basic Project

A simple Flask application that monitors Docker containers with an Nginx proxy that injects source IP headers.

## 📋 What This Does

1. **Flask App**: Lists running Docker containers via web interface
2. **Nginx Proxy**: Forwards requests to Flask and adds source IP headers
3. **Jenkins Pipeline**: Pulls code from Git, builds Docker images, and tests the application

## 🚀 Quick Start

### Prerequisites
- Docker and Docker Compose
- Jenkins (for CI/CD)
- Git repository

### Local Development

1. **Clone the repository**
   ```bash
   git clone <your-repo-url>
   cd FirstProject
   ```

2. **Run locally**
   ```bash
   docker-compose up -d --build
   ```

3. **Access the application**
   - Main app: http://localhost:8080
   - Flask direct: http://localhost:5000
   - Health check: http://localhost:5000/health

4. **Stop the application**
   ```bash
   docker-compose down
   ```

### Jenkins Setup

1. **Create a new Pipeline job in Jenkins**

2. **Configure the job**:
   - Pipeline → Definition: "Pipeline script from SCM"
   - SCM: Git
   - Repository URL: `<your-git-repo-url>`
   - Branch: `*/main`
   - Script Path: `Jenkinsfile`

3. **Add Docker Hub credentials** (optional):
   - Go to Jenkins → Manage Jenkins → Manage Credentials
   - Add Username/Password credential with ID: `docker-hub-credentials`

4. **Run the pipeline**
   - The pipeline will automatically pull code from Git
   - Build Docker images
   - Test the application
   - Push to Docker Hub (if credentials are configured)

## 📁 Project Structure

```
FirstProject/
├── README.md              # This file
├── Jenkinsfile            # Jenkins pipeline configuration
├── docker-compose.yml     # Local Docker Compose setup
├── app.py                 # Flask application
├── requirements.txt       # Python dependencies
├── Dockerfile             # Flask app Docker image
└── nginx/
    ├── Dockerfile         # Nginx Docker image
    └── nginx.conf         # Nginx configuration
```

## 🔧 Configuration

### Environment Variables
- The Flask app runs on port 5000
- Nginx proxy runs on port 8080
- Docker socket is mounted for container listing

### Jenkins Pipeline
- Builds both Flask and Nginx images
- Tests the application locally
- Pushes images to Docker Hub (on main branch)
- Cleans up after each run

## 🧪 Testing

The Jenkins pipeline includes automatic testing:
1. Health check on Flask app (http://localhost:5000/health)
2. End-to-end test through Nginx proxy (http://localhost:8080)

## 📝 Notes

- **Docker Socket**: The Flask app needs access to `/var/run/docker.sock` to list containers
- **Source IP Headers**: Nginx adds `X-Real-IP` and `X-Source-IP` headers
- **Simple Setup**: This is a basic version focused on core functionality

## 🤝 Usage

1. Push code to your Git repository
2. Jenkins will automatically trigger the pipeline
3. View the build progress in Jenkins
4. Access the application locally using Docker Compose

---

**Note**: Update the Docker Hub repository names in `Jenkinsfile` with your actual Docker Hub username before running the pipeline.