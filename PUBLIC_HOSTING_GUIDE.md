# Public Hosting Guide (Recommended: Render)

## 1. Security first

Before public hosting, do not use default credentials.

Use environment variables:

- `OUR_STORY_USER_ONE`
- `OUR_STORY_PASS_ONE`
- `OUR_STORY_USER_TWO`
- `OUR_STORY_PASS_TWO`

## 2. Deploy to Render (Docker)

1. Push this project to GitHub.
2. In Render, create **New Web Service**.
3. Connect your repository.
4. Render will detect `Dockerfile` automatically.
5. Set environment variables from `.env.example` (with your real values).
6. Deploy.

Render sets `PORT` automatically, and this app now reads `PORT` from environment.

## 3. Verify after deploy

1. Open your Render URL.
2. Login with your private credentials.
3. Upload one test image in birthday or trip year page.
4. Verify:
   - It appears in the year feed
   - It appears in dashboard latest updates
   - Delete works for Hemanth user only

## 4. Optional domain

In Render service settings:

1. Add custom domain
2. Update DNS records at your domain provider
3. Enable HTTPS (Render handles certificates)

## 5. Important note for shared uploads

Current uploads are file-based. On some cloud plans, filesystem may reset on redeploy.
For permanent storage, next step is moving images to object storage (Cloudinary, S3, etc.).
