# Contributing to amade-mail

## Initial Setup - Google OAuth2

Gmail accounts log in with OAuth2 (SASL XOAUTH2), not a password. To sign in, the Mailer needs an
OAuth client registered with Google. Each developer, and each user, creates their own client once.
The Mailer has no built-in client ID.

### 1. Create the OAuth client in Google Cloud

Open the [Google Cloud console](https://console.cloud.google.com/) and sign in with your Google
account. You don't need the free trial or a billing account; OAuth clients are free. Labels are
given in English, with the German console labels in parentheses.

**1.1 Create a project**

1. Click the project picker at the top left, next to the "Google Cloud" logo. It shows the current
   project; Google may already have created one, e.g. "Gemini API" (`gen-lang-client-…`) for AI Studio.
2. In the dialog, click **New project** (*Neues Projekt*).
3. Project name `amade-mail`. Leave organisation and location as they are, then click **Create**
   (*Erstellen*).
4. Select `amade-mail` in the project picker. The picker now shows "amade-mail".

**1.2 Configure the consent screen**

1. On the welcome page, click **APIs & Services** (*APIs und Dienste*). In the left menu, click
   **OAuth consent screen** (*OAuth-Zustimmungsbildschirm*). In newer consoles this opens the
   **Google Auth Platform**. Click **Get started** (*Jetzt starten*).
2. Fill in the wizard:
   - App information (*App-Informationen*): app name `amade-mail`, your Gmail address as user
     support email.
   - Audience (*Zielgruppe*): **External** (*Extern*).
   - Contact information (*Kontaktdaten*): your Gmail address.
   - Accept the Google API Services User Data Policy, then click **Create** (*Erstellen*).

**1.3 Add the Gmail scope**

1. In the left menu of Google Auth Platform, click **Data access** (*Datenzugriff*), then
   **Add or remove scopes** (*Bereiche hinzufügen oder entfernen*).
2. At the bottom, under *Manually add scopes* (*Bereiche manuell hinzufügen*), enter
   `https://mail.google.com/` (full IMAP and SMTP access).
3. Click **Add to table** (*Zur Tabelle hinzufügen*), then **Update** (*Aktualisieren*), then
   **Save** (*Speichern*).

**1.4 Add test users**

1. Click **Audience** (*Zielgruppe*). Under *Test users* (*Testnutzer*), click **Add users**
   (*Nutzer hinzufügen*).
2. Enter the Gmail addresses you want to sign in with, then save.

**1.5 Create the client**

1. Click **Clients**, then **Create client** (*Client erstellen*). In older consoles the path is
   *APIs & Services → Credentials → Create credentials → OAuth client ID*.
2. Application type **Desktop app** (*Desktop-App*), name `amade-mail`, then click **Create**
   (*Erstellen*).
3. Copy the **Client ID** (*Client-ID*) and the **Client secret** (*Clientschlüssel*), or download
   the JSON file. The Mailer asks for both on the first sign-in.

**1.6 Optional: publish the app**

While the app is in *Testing* status, Google expires refresh tokens after 7 days, so you have to
sign in again every week. To avoid this:

1. Click **Audience** (*Zielgruppe*), then **Publish app** (*App veröffentlichen*). The status
   changes to "In production".
2. You don't need to submit the app for verification. The sign-in page then shows an "unverified
   app" warning; continue with *Advanced → Go to amade-mail* (*Erweitert → Weiter zu amade-mail*).

For a desktop client, the client secret is not confidential (RFC 8252). PKCE protects the sign-in.
Still, don't commit your client ID or secret to this repository.

### 2. Add the Gmail account in the Mailer

1. Start the Mailer with `./gradlew run`.
2. Open *Mailbox → Mail Options… → Accounts → Add…*.
3. Enter your Gmail address and press *Guess settings*. Check that:
   - *Authentication* is **OAuth2 (Google)**;
   - *User name* is your full Gmail address;
   - IMAP is `imap.gmail.com:993` SSL/TLS and SMTP is `smtp.gmail.com:465` SSL/TLS.
4. Press *Test connection*:
   - The Mailer asks for the client ID and secret once, then saves them in
     `~/.config/amadeos/mail/oauth2.conf`.
   - Your web browser opens. Sign in and allow access; the page then says "Signed in".
   - Mail Options shows "Connection OK: signed in with Google, IMAP and SMTP login succeeded."
5. Press **OK**.

The refresh token is stored in the keyring under the key `<account id>-oauth2`. You can find it with
`secret-tool search service amade-mail`. Access tokens are kept in memory only and renewed
automatically.

### Troubleshooting

| Message | Cause and fix |
|---|---|
| `invalid_client` | Wrong client ID or secret. Fix them in `~/.config/amadeos/mail/oauth2.conf`, or delete the file to be asked again. |
| `access_denied`, "app blocked" | Your address is not a test user, or the scope `https://mail.google.com/` is missing on the consent screen. |
| "Login with the Google access token failed" | The user name does not match the Google account you signed in with. The Mailer asks you to sign in again after the second rejection. |
| Sign-in needed again after a week | The app is still in *Testing* status; see step 1.6. |
| No browser opens | Copy the address from the "Sign in" dialog into a browser on the same machine. The redirect goes to `127.0.0.1`. |

To sign in from scratch, remove the token with
`secret-tool clear service amade-mail account <account id>-oauth2`. The account id is in
`~/.config/amadeos/mail/accounts.conf`.
