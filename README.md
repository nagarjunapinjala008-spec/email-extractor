# Email Extractor (Android)

Type a keyword (e.g. `btech college professors CSE Hyderabad`) or paste a page URL.
The app finds pages, extracts the email addresses it can see, removes duplicates,
and lets you copy them or export a CSV.

## Get the APK (pick one)

**A. GitHub (no installs, works from a phone/browser)**
1. Create a new GitHub repo and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab -> **Build APK** -> wait ~3-5 min.
3. Open the finished run -> **Artifacts** -> download `email-extractor-apk` -> unzip -> install `app-debug.apk`
   (allow "Install unknown apps" for your file manager/browser when prompted).

**B. Android Studio**
1. File -> Open -> select this folder, let Gradle sync.
2. Build -> Build Bundle(s)/APK(s) -> Build APK(s).
3. APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## How it works
- Keyword: searches DuckDuckGo (3 query variants), takes the result pages.
- URL: scans that page and, optionally, same-site faculty/contact/staff pages.
- Finds plain emails, `mailto:` links, `name [at] domain [dot] edu`, `&#64;` entities
  and Cloudflare-protected addresses.
- Tap an email to copy it. "Share" a page from your browser to the app to pre-fill the URL.

## Limits
- Only reads what is in a page's HTML. Emails loaded by JavaScript or hidden in images/PDFs are missed.
- Search engines may rate-limit or block automated queries; pasting URLs always works.
- Use responsibly: collect addresses for genuine, relevant outreach, not bulk spam.
  Check the site's terms and applicable law (e.g. India's DPDP Act, 2023).
