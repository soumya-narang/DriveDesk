# DriveDesk: deploying on Render

The `Dockerfile` compiles the Java sources and runs `app.WebApp`. Render sets the `PORT` variable and the app picks it up on its own, so there is nothing to configure in code.

## 1. Push to GitHub

```powershell
cd C:\DriveDesk
git init
git add .
git commit -m "DriveDesk: donation drive manager"
git branch -M main
git remote add origin https://github.com/<your-username>/drivedesk.git
git push -u origin main
```

Create the empty repository on github.com first (no README, no .gitignore, no licence), then paste its URL above.

## 2. Create the service on Render

1. render.com > **New** > **Web Service** > connect GitHub > pick the `drivedesk` repo.
2. **Language:** Docker (Render detects the `Dockerfile`). **Branch:** `main`. **Instance type:** Free.
3. Leave the build and start commands empty. Press **Deploy**. The first build takes a few minutes.
4. The site is live at `https://<name>.onrender.com`.

## 3. Keep it awake (optional)

The free tier sleeps after about 15 minutes without traffic, and the first request afterwards takes about a minute. On uptimerobot.com, add an **HTTP(s)** monitor for the Render URL with a 5-minute interval.

## Things to know

- **Data does not persist on the free tier.** `data/` is not in the repo, so a fresh deploy or a restart starts again from the demo data, dated today. That suits a demo. Real long-term storage needs a paid disk.
- **There is no login.** Anyone with the link can read and change the data. Share the link only with people you trust, or use the laptop for the live demo.
- Locally nothing changes: `.\run.ps1` still serves `http://localhost:8090/`, on localhost only.
