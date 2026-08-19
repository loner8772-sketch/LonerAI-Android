# Lone AI

A minimal Android chat app that talks to Groq's API. Includes a basic local
pre-filter (see `ContentFilter.kt`) — the real safety floor comes from the
underlying model itself, which will still refuse things like weapons
instructions, malicious code, or exploitation content no matter what the
system prompt says.

## How to build the APK (no Android Studio needed)

1. Create a new **public or private GitHub repository**.
2. Upload every file in this folder to the repo, keeping the exact folder
   structure (including the hidden `.github/workflows/build.yml` file).
   Easiest way: on github.com, use "Add file > Upload files" and drag the
   whole extracted folder in, or use `git push` from the command line:
   ```
   git init
   git add .
   git commit -m "initial commit"
   git branch -M main
   git remote add origin <your-repo-url>
   git push -u origin main
   ```
3. Go to the **Actions** tab of your repo. A workflow called "Build APK"
   will run automatically (takes ~3-5 minutes).
4. When it finishes (green checkmark), click into the run, scroll to
   **Artifacts**, and download `lone-ai-debug-apk`. That's a zip
   containing `app-debug.apk`.
5. Transfer the `.apk` to your Android phone (email it to yourself, use
   Google Drive, USB, etc.) and tap it to install. You'll need to allow
   "install from unknown sources" in Android settings the first time.

## First run

On first launch, tap the gear icon (top right) and paste your Groq API key
(get one free at console.groq.com). It's saved only on your device.

## Notes

- The model used is `llama-3.3-70b-versatile`. Check
  https://console.groq.com/docs/models if this has been retired and swap
  the `MODEL` constant in `GroqClient.kt`.
- This is a **debug build** — fine for personal use, but not signed for
  Play Store distribution.
- The content filter is intentionally simple. Don't treat it as a complete
  safety system — it's a first-pass filter, and final refusals still come
  from the model itself.

## "Build App" tab — apps that build other apps

The second tab lets you describe an app, and the pipeline will:
1. Ask Groq to generate a full Android project as structured code
2. Push it to the same GitHub repo (triggers the Actions workflow automatically)
3. Poll GitHub until the build finishes
4. If it fails, pull the error log and ask Groq to fix the specific error, then push again (up to 5 attempts)
5. On success, download the APK artifact and offer an "Install" button

### One-time setup for this tab
Tap the gear icon on the Build App tab and fill in:
- **Groq API key** — same one used for chat
- **GitHub username/org** and **repo name** — the same repo you set up above
- **GitHub Personal Access Token** — create one at
  github.com/settings/tokens (classic token, scopes: `repo` and `workflow`).
  This token can write to your repo, so:
  - Prefer a **fine-grained token scoped to just this one repo** if possible
  - Never share it or commit it anywhere
  - Revoke it any time from the same GitHub settings page
  - **Token expiry**: GitHub tokens are often issued with an expiration
    (e.g. 1 year). Groq API keys don't expire. When your GitHub token
    expires, the Build App tab will fail with a clear "GitHub rejected the
    token" message — just generate a new one and paste it into Settings.

### Things to know before using it
- Every attempt is a real push + a real GitHub Actions run — it consumes
  your GitHub Actions minutes (free tier gives 2,000 min/month on private
  repos, unlimited on public repos) and Groq API usage.
- Capped at 5 attempts per app so a stubborn bug can't loop forever and burn
  through your usage. If it still fails after 5, check the repo's Actions
  tab yourself — the log there will show exactly what broke.
- Keep requested apps simple (a single-screen calculator, converter,
  tracker, small game). Multi-screen apps with lots of moving parts are
  much more likely to need fix attempts and are more likely to exhaust the
  5-attempt cap.
- The same refusal categories apply here too — the generator is instructed
  to decline building anything whose primary purpose is malware, unauthorized
  system access, or sexual content involving minors.
