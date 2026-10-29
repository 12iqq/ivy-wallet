# Setting up "Connect Google Drive" for the Windows app

The Windows app can read the phone's backup straight from Google Drive: you click **Connect Google Drive**, sign in,
choose the backup, and it gets each new one by itself. For that, Google needs the app registered in a Google Cloud
project. This is done once, by whoever builds the releases, and takes about 15 minutes. It's free.

The app asks Google for the `drive.file` permission only: it can open the files you choose in it, and nothing else in
your Google Drive. Because that's all it asks for, Google doesn't need to review the app.

Until the four values at the end are in the repository's secrets, the app is built without the button, and works as
before.

## 1. A project

1. Open [console.cloud.google.com](https://console.cloud.google.com/) and sign in with your Google account.
2. Click the project picker at the top, then **New project**. Name it `Ivy Wallet UAE` and click **Create**.
3. Make sure the new project is the one selected at the top of the page for the steps below.

## 2. Turn on Google Drive and the file picker

1. Open [Google Drive API](https://console.cloud.google.com/apis/library/drive.googleapis.com) and click **Enable**.
2. Open [Google Picker API](https://console.cloud.google.com/apis/library/picker.googleapis.com) and click **Enable**.

## 3. What people see when they sign in

1. Open [Google Auth Platform](https://console.cloud.google.com/auth/overview) and click **Get started**.
2. **App name:** `Ivy Wallet UAE`. **User support email:** your email. Click **Next**.
3. **Audience:** **External**. Click **Next**.
4. **Contact information:** your email. Click **Next**, agree to the policy, and click **Create**.
5. Open **Data access** (on the left), click **Add or remove scopes**, tick
   `.../auth/drive.file` (Google Drive API: "See, edit, create, and delete only the specific Google Drive files you use
   with this app"), click **Update**, then **Save**.
6. Open **Audience** and click **Publish app**, then **Confirm**. Without this, only test users you add can sign in,
   and they have to sign in again every week.

Leave the logo empty: with a logo, Google has to review the app first.

## 4. The app's ID

1. Open **Clients** (on the left) and click **Create client**.
2. **Application type:** **Desktop app**. **Name:** `Ivy Wallet UAE for Windows`. Click **Create**.
3. Copy the **Client ID** and the **Client secret** that it shows.

Google gives desktop apps a client secret, but it can't actually be kept secret in an app people download. That's
expected: the sign-in is protected in other ways (PKCE).

## 5. A key for the file picker

1. Open [Credentials](https://console.cloud.google.com/apis/credentials), click **Create credentials** and choose
   **API key**. Copy the key.
2. Click the key's name to edit it. Under **API restrictions**, choose **Restrict key**, tick **Google Picker API** and
   click **Save**.

## 6. The project number

Open the [dashboard](https://console.cloud.google.com/home/dashboard). The **Project info** card shows the
**Project number**: a long number, not the project ID.

## 7. Into GitHub

Open the repository's [Actions secrets](https://github.com/12iqq/ivy-wallet/settings/secrets/actions) and add four
with **New repository secret**:

| Name | Value |
|---|---|
| `GOOGLE_CLIENT_ID` | the Client ID, ending in `.apps.googleusercontent.com` |
| `GOOGLE_CLIENT_SECRET` | the Client secret, starting with `GOCSPX-` |
| `GOOGLE_API_KEY` | the API key, starting with `AIza` |
| `GOOGLE_PROJECT_NUMBER` | the project number |

The next release's installer has **Connect Google Drive**. To try it before a release, run the
[Desktop workflow](https://github.com/12iqq/ivy-wallet/actions/workflows/desktop.yml) and install the installer from
the run's **Artifacts**.

## If something doesn't work

- **"Error 400: redirect_uri_mismatch"**: the client in step 4 isn't a **Desktop app**.
- **"Error 401: invalid_client"**: the Client ID or secret in GitHub isn't the one from step 4.
- **"Error 403: access_denied", "The app is currently being tested"**: the app wasn't published (step 3, 6).
- **"Google hasn't verified this app"**: the app asks for more than `drive.file`, or has a logo. Check step 3.
- **The file picker says the API key isn't valid**: the key from step 5 isn't allowed to use the Google Picker API,
  or the Picker API isn't turned on (step 2).

People can see and remove the app's access in their Google account, under
[Third-party apps & services](https://myaccount.google.com/connections).
