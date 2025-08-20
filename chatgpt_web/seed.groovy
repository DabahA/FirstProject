// === Jenkins Job DSL ===
// Creates 3 Pipeline jobs + a view for convenience.
// Requirements on the Jenkins agent running the pipelines:
// - Docker CLI with access to the local Docker daemon
// - Git installed
// - (Optional) 'docker' label on an agent, or edit "agent any"

def DEFAULTS = [
  DOCKERHUB_NAMESPACE  : 'yourdockerhub',     // e.g. "acmeinc"
  APP_IMAGE_NAME       : 'flask-containers-app',
  NGINX_IMAGE_NAME     : 'flask-nginx-proxy',
  GIT_REPO             : 'https://github.com/your-org/your-repo.git',
  GIT_BRANCH           : 'main',
  DOCKERHUB_CREDS_ID   : 'dockerhub-creds',   // Jenkins Username/Password creds
  GIT_CREDS_ID         : ''                   // optional; leave empty for public repo
]

// ---------- Job 1: Build & Push Flask App ----------
pipelineJob('01-build-app-image') {
  description('Pulls your Git repo, builds a Flask image that lists running Docker containers, pushes to Docker Hub.')
  parameters {
    stringParam('GIT_REPO',         DEFAULTS.GIT_REPO,       'Git repository URL containing the Flask app (or leave, job will bootstrap a minimal app if missing).')
    stringParam('GIT_BRANCH',       DEFAULTS.GIT_BRANCH,     'Git branch to build.')
    stringParam('DOCKERHUB_NAMESPACE', DEFAULTS.DOCKERHUB_NAMESPACE, 'Docker Hub namespace (org/user).')
    stringParam('APP_IMAGE_NAME',   DEFAULTS.APP_IMAGE_NAME, 'App image name.')
    stringParam('DOCKERHUB_CREDS_ID', DEFAULTS.DOCKERHUB_CREDS_ID, 'Credentials ID for Docker Hub (Username/Password).')
    stringParam('GIT_CREDS_ID',     DEFAULTS.GIT_CREDS_ID,   'Optional Git credentials ID for private repos.')
  }
  definition {
    cps {
      script("""
pipeline {
  agent { label 'docker' }  // change to 'any' if you prefer
  options { timestamps(); ansiColor('xterm') }
  environment {
    IMAGE_TAG_LONG = "\${DOCKERHUB_NAMESPACE}/\${APP_IMAGE_NAME}:\${env.BUILD_NUMBER}"
    IMAGE_TAG_LATEST = "\${DOCKERHUB_NAMESPACE}/\${APP_IMAGE_NAME}:latest"
  }
  stages {
    stage('Checkout') {
      steps {
        script {
          if (params.GIT_CREDS_ID?.trim()) {
            checkout([$class: 'GitSCM', branches: [[name: "*/\${params.GIT_BRANCH}"]],
              userRemoteConfigs: [[url: params.GIT_REPO, credentialsId: params.GIT_CREDS_ID]]])
          } else {
            git branch: params.GIT_BRANCH, url: params.GIT_REPO
          }
        }
      }
    }

    stage('Bootstrap minimal Flask app if missing') {
      steps {
        sh '''
          set -eux
          # If repo doesn't contain a Dockerfile/app, create a minimal one that lists running containers
          if [ ! -f Dockerfile ]; then
            cat > app.py <<'PY'
from flask import Flask, jsonify
import docker
app = Flask(__name__)
@app.route("/")
def root():
    return "OK"
@app.route("/containers")
def containers():
    client = docker.from_env()
    return jsonify([c.name for c in client.containers.list()])
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
PY
            cat > requirements.txt <<'REQ'
flask
docker
REQ
            cat > Dockerfile <<'DOCKER'
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .
# Allow talking to the local Docker socket at runtime (mount in docker run)
EXPOSE 5000
CMD ["python", "app.py"]
DOCKER
          fi
        '''
      }
    }

    stage('Build') {
      steps {
        sh '''
          set -eux
          docker build -t "$IMAGE_TAG_LONG" -t "$IMAGE_TAG_LATEST" .
        '''
      }
    }

    stage('Login & Push') {
      steps {
        withCredentials([usernamePassword(credentialsId: params.DOCKERHUB_CREDS_ID, usernameVariable: 'DOCKERHUB_USER', passwordVariable: 'DOCKERHUB_PASS')]) {
          sh '''
            set -eux
            echo "$DOCKERHUB_PASS" | docker login -u "$DOCKERHUB_USER" --password-stdin
            docker push "$IMAGE_TAG_LONG"
            docker push "$IMAGE_TAG_LATEST"
          '''
        }
      }
    }
  }
  post {
    success {
      echo "Pushed: \${IMAGE_TAG_LONG} and \${IMAGE_TAG_LATEST}"
    }
  }
}
      """.stripIndent())
      sandbox()
    }
  }
}

// ---------- Job 2: Build & Push Nginx Proxy ----------
pipelineJob('02-build-nginx-proxy-image') {
  description('Builds an Nginx image that proxies to the Flask app, injects source IP header, pushes to Docker Hub.')
  parameters {
    stringParam('DOCKERHUB_NAMESPACE', DEFAULTS.DOCKERHUB_NAMESPACE, 'Docker Hub namespace.')
    stringParam('NGINX_IMAGE_NAME',    DEFAULTS.NGINX_IMAGE_NAME,    'Nginx proxy image name.')
    stringParam('APP_UPSTREAM',        "${DEFAULTS.APP_IMAGE_NAME}", 'The upstream container name or host (default: container name "flask-containers-app").')
    stringParam('APP_UPSTREAM_PORT',   '5000',                       'Upstream port.')
    stringParam('DOCKERHUB_CREDS_ID',  DEFAULTS.DOCKERHUB_CREDS_ID,  'Credentials ID for Docker Hub.')
  }
  definition {
    cps {
      script("""
pipeline {
  agent { label 'docker' }
  options { timestamps(); ansiColor('xterm') }
  environment {
    IMAGE_TAG_LONG = "\${DOCKERHUB_NAMESPACE}/\${NGINX_IMAGE_NAME}:\${env.BUILD_NUMBER}"
    IMAGE_TAG_LATEST = "\${DOCKERHUB_NAMESPACE}/\${NGINX_IMAGE_NAME}:latest"
  }
  stages {
    stage('Generate Nginx Docker context') {
      steps {
        sh '''
          set -eux
          rm -rf nginx-build && mkdir -p nginx-build
          cat > nginx-build/nginx.conf <<CONF
worker_processes  1;
events { worker_connections 1024; }
http {
  sendfile        on;
  server {
    listen 80;
    location / {
      proxy_pass         http://$APP_UPSTREAM:$APP_UPSTREAM_PORT;
      proxy_set_header   Host              $host;
      proxy_set_header   X-Real-IP         $remote_addr;
      proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
      proxy_set_header   X-Source-IP       $remote_addr;  # explicit header as requested
    }
  }
}
CONF
          cat > nginx-build/Dockerfile <<'DOCKER'
FROM nginx:alpine
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
STOPSIGNAL SIGQUIT
CMD ["nginx", "-g", "daemon off;"]
DOCKER
        '''
      }
    }

    stage('Build') {
      steps {
        sh '''
          set -eux
          docker build -t "$IMAGE_TAG_LONG" -t "$IMAGE_TAG_LATEST" nginx-build
        '''
      }
    }

    stage('Login & Push') {
      steps {
        withCredentials([usernamePassword(credentialsId: params.DOCKERHUB_CREDS_ID, usernameVariable: 'DOCKERHUB_USER', passwordVariable: 'DOCKERHUB_PASS')]) {
          sh '''
            set -eux
            echo "$DOCKERHUB_PASS" | docker login -u "$DOCKERHUB_USER" --password-stdin
            docker push "$IMAGE_TAG_LONG"
            docker push "$IMAGE_TAG_LATEST"
          '''
        }
      }
    }
  }
  post {
    success {
      echo "Pushed: \${IMAGE_TAG_LONG} and \${IMAGE_TAG_LATEST}"
    }
  }
}
      """.stripIndent())
      sandbox()
    }
  }
}

// ---------- Job 3: Run & Verify Locally ----------
pipelineJob('03-run-and-verify') {
  description('Runs the two containers (on a private network), exposes only Nginx on localhost, verifies via HTTP request, then cleans up.')
  parameters {
    stringParam('DOCKERHUB_NAMESPACE', DEFAULTS.DOCKERHUB_NAMESPACE, 'Docker Hub namespace.')
    stringParam('APP_IMAGE_NAME',      DEFAULTS.APP_IMAGE_NAME,      'App image name (expects ":latest" tag unless overridden).')
    stringParam('NGINX_IMAGE_NAME',    DEFAULTS.NGINX_IMAGE_NAME,    'Nginx image name (expects ":latest" tag unless overridden).')
    stringParam('APP_TAG',             'latest',                     'Tag for the app image.')
    stringParam('NGINX_TAG',           'latest',                     'Tag for the Nginx image.')
    stringParam('APP_CONTAINER_NAME',  'flask-app',                  'Runtime name for app container.')
    stringParam('NGINX_CONTAINER_NAME','flask-nginx',                'Runtime name for nginx container.')
    stringParam('NETWORK_NAME',        'flaskdemo',                  'Docker network to create/use.')
    stringParam('HOST_PORT',           '8080',                       'Localhost port to expose Nginx.')
    stringParam('UPSTREAM_PORT',       '5000',                       'App port exposed inside the container.')
  }
  definition {
    cps {
      script("""
pipeline {
  agent { label 'docker' }
  options { timestamps(); ansiColor('xterm') }
  environment {
    APP_IMAGE   = "\${DOCKERHUB_NAMESPACE}/\${APP_IMAGE_NAME}:\${APP_TAG}"
    NGINX_IMAGE = "\${DOCKERHUB_NAMESPACE}/\${NGINX_IMAGE_NAME}:\${NGINX_TAG}"
  }
  stages {
    stage('Prepare network') {
      steps {
        sh '''
          set -eux
          docker network inspect "$NETWORK_NAME" >/dev/null 2>&1 || docker network create "$NETWORK_NAME"
        '''
      }
    }

    stage('Run App') {
      steps {
        sh '''
          set -eux
          # Stop if already running
          docker rm -f "$APP_CONTAINER_NAME" >/dev/null 2>&1 || true
          # Mount docker socket so the app can list containers
          docker run -d --rm \\
            --name "$APP_CONTAINER_NAME" \\
            --network "$NETWORK_NAME" \\
            -v /var/run/docker.sock:/var/run/docker.sock \\
            "$APP_IMAGE"
        '''
      }
    }

    stage('Run Nginx (localhost only)') {
      steps {
        sh '''
          set -eux
          docker rm -f "$NGINX_CONTAINER_NAME" >/dev/null 2>&1 || true
          docker run -d --rm \\
            --name "$NGINX_CONTAINER_NAME" \\
            --network "$NETWORK_NAME" \\
            -p 127.0.0.1:$HOST_PORT:80 \\
            "$NGINX_IMAGE"
        '''
      }
    }

    stage('Verify') {
      steps {
        sh '''
          set -eux
          # give services a moment to boot
          sleep 3
          code=$(curl -sS -o /tmp/resp.txt -w "%{http_code}" -H "X-Debug: 1" "http://127.0.0.1:$HOST_PORT/containers" || true)
          echo "HTTP status: $code"
          echo "Body:"
          cat /tmp/resp.txt || true
          test "$code" = "200"
        '''
      }
    }
  }
  post {
    always {
      sh '''
        set +e
        docker logs "$APP_CONTAINER_NAME" || true
        docker logs "$NGINX_CONTAINER_NAME" || true
      '''
    }
    cleanup {
      sh '''
        set +e
        docker rm -f "$NGINX_CONTAINER_NAME" >/dev/null 2>&1 || true
        docker rm -f "$APP_CONTAINER_NAME" >/dev/null 2>&1 || true
      '''
    }
  }
}
      """.stripIndent())
      sandbox()
    }
  }
}

// ---------- (Optional) List View ----------
listView('Demo – App+Proxy Pipelines') {
  description('Quick access to the three demo jobs.')
  jobs {
    names('01-build-app-image', '02-build-nginx-proxy-image', '03-run-and-verify')
  }
  columns {
    status(); weather(); name(); lastSuccess(); lastFailure(); lastDuration(); buildButton()
  }
}
