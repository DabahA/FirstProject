#!/usr/bin/env python3
"""
Simple Flask application that lists Docker containers.
"""

from flask import Flask, jsonify, render_template_string, request
import docker
import logging
from datetime import datetime

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Simple HTML template
HTML_TEMPLATE = """
<!DOCTYPE html>
<html>
<head>
    <title>Docker Container Monitor</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; }
        .container { max-width: 800px; margin: 0 auto; }
        .header { background: #f0f0f0; padding: 20px; border-radius: 5px; margin-bottom: 20px; }
        .container-item { border: 1px solid #ddd; padding: 15px; margin: 10px 0; border-radius: 5px; }
        .status-running { background-color: #d4edda; }
        .status-exited { background-color: #f8d7da; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🐳 Docker Container Monitor</h1>
        
        <div class="header">
            <strong>Request Headers:</strong><br>
            {% for header, value in headers.items() %}
                <strong>{{ header }}:</strong> {{ value }}<br>
            {% endfor %}
        </div>
        
        <h2>Containers ({{ containers|length }})</h2>
        
        {% if error %}
            <div style="color: red; padding: 10px; border: 1px solid red;">
                <strong>Error:</strong> {{ error }}
            </div>
        {% else %}
            {% for container in containers %}
                <div class="container-item status-{{ container.status.lower() }}">
                    <strong>{{ container.name }}</strong> - {{ container.status }}<br>
                    <small>Image: {{ container.image }} | ID: {{ container.id[:12] }}</small>
                </div>
            {% endfor %}
        {% endif %}
        
        <p><small>Last updated: {{ timestamp }}</small></p>
    </div>
</body>
</html>
"""

def get_containers():
    """Get list of Docker containers."""
    try:
        client = docker.from_env()
        containers = client.containers.list(all=True)
        
        result = []
        for container in containers:
            result.append({
                'id': container.id,
                'name': container.name.lstrip('/'),
                'image': container.image.tags[0] if container.image.tags else container.image.id[:12],
                'status': container.status
            })
        
        return result, None
    except Exception as e:
        logger.error(f"Error getting containers: {e}")
        return [], str(e)

@app.route('/')
def index():
    """Main route."""
    containers, error = get_containers()
    headers = dict(request.headers)
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    
    return render_template_string(
        HTML_TEMPLATE,
        containers=containers,
        error=error,
        headers=headers,
        timestamp=timestamp
    )

@app.route('/api/containers')
def api_containers():
    """API endpoint for container data."""
    containers, error = get_containers()
    
    return jsonify({
        'containers': containers,
        'error': error,
        'timestamp': datetime.now().isoformat()
    })

@app.route('/health')
def health():
    """Health check endpoint."""
    return jsonify({
        'status': 'healthy',
        'timestamp': datetime.now().isoformat()
    })

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False)