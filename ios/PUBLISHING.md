# Publishing the ASU Bank iOS app (from Windows, no Mac)

The app is built on GitHub's macOS machines by `.github/workflows/ios.yml`.
You do the Apple account steps once; after that, every release is one click.

## 1. Join the Apple Developer Program (99 USD / year)

1. Go to https://developer.apple.com/programs/enroll/ and sign in with your Apple ID (turn on two-factor authentication first).
2. Enroll as **Individual** (fastest; your name shows as the seller) or **Organization** (needs a D-U-N-S number; the store shows "ASU Bank").
3. Pay and wait for the "Welcome to the Apple Developer Program" email (minutes to 2 days).
4. Open https://developer.apple.com/account → **Membership details** and copy your **Team ID** (10 characters).

## 2. Create the app in App Store Connect

1. https://appstoreconnect.apple.com → **Apps** → **+** → **New App**.
2. Platform **iOS**, Name **ASU Bank** (or "ASU Bank – Study Materials" if taken), language English, SKU `asubank-ios`.
3. Bundle ID: if `org.asubank.app` is not in the list yet, open https://developer.apple.com/account/resources/identifiers → **+** → App IDs → App → Explicit bundle ID `org.asubank.app` → Continue → Register, then come back and pick it.
4. User access: Full access → **Create**.

## 3. Create an App Store Connect API key (lets GitHub sign and upload)

1. App Store Connect → **Users and Access** → **Integrations** → **App Store Connect API** → **Team Keys** → **+**.
2. Name `GitHub`, access **Admin** (needed so it can create the signing certificate) → Generate.
3. Copy the **Issuer ID** (top of the page) and the **Key ID**.
4. **Download API Key** — the `AuthKey_XXXXXXXXXX.p8` file downloads only once; keep it safe.

## 4. Add the secrets to GitHub (only you do this — never paste keys into chat)

https://github.com/asubank1999/asubank.org/settings/secrets/actions → **New repository secret**, four times:

| Name | Value |
| --- | --- |
| `APPLE_TEAM_ID` | Team ID from step 1 |
| `ASC_KEY_ID` | Key ID from step 3 |
| `ASC_ISSUER_ID` | Issuer ID from step 3 |
| `ASC_KEY_P8` | open the `.p8` file in Notepad and paste **all** of it, including the BEGIN/END lines |

Secrets are encrypted; they are never shown in logs, even though the repository is public.

## 5. Build and send to TestFlight

1. https://github.com/asubank1999/asubank.org/actions → **iOS app** → **Run workflow** → tick **Send to TestFlight** → Run.
2. About 10–15 minutes later the build appears in App Store Connect → your app → **TestFlight** (Apple processes it for another 5–30 minutes).
3. The signed `.ipa` is also attached to the run under **Artifacts**.
4. Testing on your iPhone: install **TestFlight** from the App Store, add yourself under TestFlight → **Internal Testing**, and open the invite.

## 6. Submit to the App Store

In App Store Connect → your app → **App Store** tab:

- **Screenshots**: 6.9" iPhone (1320×2868) and 13" iPad (2064×2752) at least. Take them from TestFlight on a phone/iPad, or ask Claude to make them.
- **Description, keywords, support URL** (`https://asubank.org/guides/contact`), **privacy policy URL** (e.g. `https://asubank.org/legal/uni_tos`).
- **App Privacy**: "Data not collected" for tracking; uploads are "User Content – not linked to identity, used for app functionality".
- **Age rating** questionnaire (no objectionable content → 4+). Category **Education**.
- **Build**: pick the TestFlight build → **Add for Review** → **Submit**.

Review usually takes 1–3 days. Tips that avoid rejection:
- The app is native (tabs, search, saved files, uploads with scanner), not a website wrapper — mention that in the review notes.
- Say in **Review notes**: "Study materials are shared by students; uploads go to a moderated Google Drive folder. No login required."
- Make sure you have the right to distribute the materials linked in the app (Apple can reject for copyright complaints).

## Updating the app later

Change code under `ios/`, bump `MARKETING_VERSION` in `ios/project.yml` (e.g. 1.0.1), push, then run the workflow with **Send to TestFlight** again. The build number goes up automatically.
