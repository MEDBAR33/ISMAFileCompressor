# Deployment Guide for Render

This guide will help you deploy ISMA FileCompressor to Render.

## Prerequisites

1. A GitHub account
2. Your code pushed to a GitHub repository
3. A Render account (sign up at https://render.com)

## Deployment Steps

### 1. Push Your Code to GitHub

Make sure all your code is committed and pushed to GitHub:

```bash
git add .
git commit -m "Prepare for Render deployment"
git push origin main
```

### 2. Deploy on Render

#### Option A: Using render.yaml (Recommended)

1. Go to [Render Dashboard](https://dashboard.render.com)
2. Click **"New +"** → **"Blueprint"**
3. Connect your GitHub repository
4. Render will automatically detect the `render.yaml` file
5. Click **"Apply"** to deploy

#### Option B: Manual Setup

1. Go to [Render Dashboard](https://dashboard.render.com)
2. Click **"New +"** → **"Web Service"**
3. Connect your GitHub repository
4. Configure the service:
   - **Name**: `isma-filecompressor` (or your preferred name)
   - **Environment**: `Docker`
   - **Dockerfile Path**: `./Dockerfile` (this is what Render is asking for!)
   - **Docker Context**: `.` (root directory)
   - **Plan**: `Free` (or choose a paid plan)
5. Click **"Create Web Service"**

### 3. Environment Variables

Render automatically sets the `PORT` environment variable. The application will read it automatically.

Optional environment variables you can set:
- `JAVA_OPTS`: Java runtime options (default: `-Xmx512m -Xms256m`)

### 4. Wait for Deployment

Render will:
1. Build your Docker image
2. Start your application
3. Provide you with a public URL (e.g., `https://isma-filecompressor.onrender.com`)

## Important Notes

### File Storage

⚠️ **Important**: Render's free tier uses **ephemeral storage**. This means:
- Uploaded files and compressed outputs will be **deleted** when the service restarts
- For production use, consider:
  - Using a paid Render plan with persistent storage
  - Integrating with cloud storage (AWS S3, Google Cloud Storage, etc.)
  - Using Render's disk storage addon

### Resource Limits (Free Tier)

- **512 MB RAM**
- **0.5 CPU cores**
- **Ephemeral disk storage**

For better performance, consider upgrading to a paid plan.

### Port Configuration

The application automatically reads the `PORT` environment variable that Render provides. You don't need to set it manually.

## Troubleshooting

### Build Fails

1. Check the build logs in Render dashboard
2. Ensure `pom.xml` is in the root directory
3. Verify Java 21 is being used (check Dockerfile)

### Application Won't Start

1. Check the runtime logs
2. Verify the PORT environment variable is being read
3. Check if there are any port conflicts

### Files Not Persisting

This is expected on the free tier. Consider:
- Using cloud storage for file uploads
- Upgrading to a paid plan
- Implementing file cleanup after download

## Testing Locally with Docker

Before deploying, you can test the Docker image locally:

```bash
# Build the image
docker build -t isma-filecompressor .

# Run the container
docker run -p 8080:8080 -e PORT=8080 isma-filecompressor

# Access at http://localhost:8080
```

## Support

If you encounter issues:
1. Check Render's documentation: https://render.com/docs
2. Review the application logs in Render dashboard
3. Check the GitHub repository for issues

