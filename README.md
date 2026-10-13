# amade-mail

**amade-mail** is the Mailer of the [amadeOS](https://github.com/datazuul/amadeos) desktop: an
IMAP/SMTP mail client in Java Swing, modelled on the CDE Mailer (`dtmail`) and extended with what a
mail client needs today: a folder column for IMAP, several accounts and safe display of HTML mail.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![Debian](https://img.shields.io/badge/Debian-package-red)

> Status: early development (0.1.0). Reading, deleting, moving and sending work; see *Roadmap*.

---

## Window layout

```
┌ Mailer - Privat - INBOX ─────────────────────────────────────────────────┐
│ Mailbox  Message  Edit  Attachments  View  Compose  Move            Help │
├──────────────────┬───────────────────────────────────────────────────────┤
│ Folders          │     Sender        Subject            Date and time  │
│ All inboxes (5)  │ N   Alice         Front panel        08:12          │
│ ● Privat (3)     │ N@  Bob           Screenshot         07:55          │
│   Inbox (2)      │     root          Cron report        Mon Oct 12 23:00│
│   Drafts         ├───────────────────────────────────────────────────────┤
│   Sent           │ [Delete] [Next] [Previous] [Reply to Sender] [Forward]│
│   Trash          ├───────────────────────────────────────────────────────┤
│   ▾ Projekte     │ From: Bob <bob@example.org>                          │
│     amadeOS (1)  │ Subject: Screenshot                                  │
│ ● datazuul (2)   │ 2 remote image(s) blocked  [Load images] [Always …]  │
│ ● Vereine (offl.)│ Hi Ralf, …                                           │
│                  │ Attachments: [switcher.png (88k)]                     │
├──────────────────┴───────────────────────────────────────────────────────┤
│ Loaded 4 messages, 2 new                                                 │
└──────────────────────────────────────────────────────────────────────────┘
```

- **Folder column** (*View → Show Folders*): one node per account with its IMAP folder hierarchy and
  unread counts; special folders (Inbox, Drafts, Sent, Junk, Trash, Archive) first, detected via IMAP
  SPECIAL-USE or common names. With more than one account, *All inboxes* merges the inboxes.
- **Header list** with dtmail status letters: `N` new, `A` answered, `!` flagged, `@` attachment.
  In *All inboxes* a coloured dot shows the account.
- **Message view**: plain text or HTML (*View → Plain Text / HTML*), attachment bar with
  *Open* and *Save As…*, *Message → Show Source*.
- **Move menu** lists the folders of the selected message's account, as in dtmail.
- **Mail Options** (*Mailbox → Mail Options…*) is one dialog with a *Category* menu:
  - *Accounts*: list with Add / Remove / Move up / Move down; name, sender, IMAP and SMTP server
    (host, port, SSL/TLS or STARTTLS), user name, password, signature, colour;
    *Guess settings* from the address and *Test connection*.
  - *Message View*: plain text or HTML by default, remote images, check interval.
- **Compose window**: From (account with colour), To, Cc, Bcc (*Format → Show Bcc Field*), Subject,
  attachments; *Reply to Sender*, *Reply to All*, *Forward*.

## Security and privacy

- **HTML mail** is cleaned with [jsoup](https://jsoup.org/) (no scripts, frames, forms, event
  handlers or external style sheets) and rendered with
  [Flying Saucer](https://github.com/flyingsaucerproject/flyingsaucer) (XHTML + CSS 2.1, no JavaScript).
- **Remote images are blocked** by default (tracking pixels). A bar offers *Load images* once or
  *Always for this sender*. Allowed images are downloaded in the background; the renderer itself
  never opens network connections. Embedded images (`cid:`) are always shown.
- **Links** are never followed inside the mailer; a click shows the real target and asks before
  opening the web browser. `mailto:` links open a compose window.
- **Attachments** that could run a program (`.desktop`, `.sh`, `.jar`, ...) are not opened directly.
- **Passwords** are not stored in the configuration file but in the freedesktop Secret Service
  (gnome-keyring, KeePassXC) via `secret-tool` (Debian package `libsecret-tools`). Without a keyring,
  passwords are kept for the running session only.
- **TLS**: STARTTLS is *required* when selected (no silent fallback to plain text) and server
  certificates are checked against the host name.

## Configuration

`~/.config/amadeos/mail/` (or `$XDG_CONFIG_HOME/amadeos/mail/`), readable only by the user:

| File | Content |
|---|---|
| `accounts.conf` | accounts in display order, without passwords |
| `mail.conf` | Message View settings and check interval |

## Architecture

Java 21, Swing, Guice; same structure as [amade-pad](https://github.com/datazuul/amade-pad). Theme, Motif
look, start-up, keyring and XDG helpers come from
[amade-commons](https://github.com/datazuul/amade-commons), so the Mailer follows the desktop theme
chosen in the Style Manager immediately, including *Dark Slate*.

| Package | Content |
|---|---|
| `domain` | `Account`, `ServerSettings`, `MailFolder`, `FolderRole`, `MessageSummary`, `MessageContent`, `OutgoingMessage`, `MailPreferences` |
| `repository` | `FileAccountRepository`, `PreferencesRepository` (passwords: `PasswordStore` from amade-commons) |
| `service` | `MailGateway` / `JakartaMailGateway` (IMAP + SMTP on Jakarta Mail / Eclipse Angus), `MessageParser`, `MessageComposer`, `HtmlSanitizer`, `RemoteImageLoader`, `ServerGuesser`, `MailtoLink` |
| `ui.main` | `MainWindow`, `MainMenuBar`, `MainPresenter` (all behaviour, testable without Swing windows) |
| `ui.folders`, `ui.messages`, `ui.view`, `ui.options`, `ui.compose` | folder column, header list, message view, Mail Options, compose window |

`JakartaMailGateway` keeps one IMAP connection per account. Operations on one account are
serialised, different accounts run in parallel on virtual threads, so a slow or unreachable server
never blocks the others; its node in the folder column shows *(offline)*. Results that arrive after
the user selected something else are dropped.

## Building and testing

Requirements: Java 21 JDK; `dpkg-deb` for the Debian package.

`amade-commons` is taken from JitPack. If it is checked out next to this repository
(`../amade-commons`), Gradle builds that checkout instead, so changes to both can be tested together.

```bash
./gradlew test                 # unit tests (presenter tests need a display or Xvfb)
./gradlew run                  # start the mailer
./gradlew run --args="mailto:someone@example.org?subject=Hello"
./gradlew deb                  # build/distributions/amade-mail_0.1.0_all.deb
./gradlew installToAmadeOs     # copy the .deb into ../amadeos/config/packages.chroot/
```

The package installs `amade-mail.desktop` (`Categories=Network;Email;`, `x-scheme-handler/mailto`),
so the amadeOS front panel lists it in the Mail drawer.

## Roadmap

- [x] Accounts, keyring, IMAP folders, header list, plain and HTML view, attachments
- [x] Delete to Trash, Move, Mark as Unread, Compose / Reply / Forward with attachments, Sent copy
- [ ] IMAP IDLE for instant new-mail notification (and the full in-tray in the front panel)
- [ ] Autoconfig (Thunderbird ISPDB, DNS SRV per RFC 6186) in *Add account*
- [ ] Drag and drop of messages onto folders; folder management (new, rename, delete)
- [ ] Drafts, templates, aliases / several identities per account, vacation message
- [ ] Search; threading; printing
- [ ] OAuth2 for Gmail and Microsoft 365
- [ ] Offline cache of headers and messages

## License

amade-mail is free software, licensed under the
[European Union Public Licence v. 1.2](LICENSE) (EUPL-1.2, SPDX identifier `EUPL-1.2`), like amadeOS.

Copyright © 2026 datazuul.com and contributors.

Third-party libraries keep their licences: Jakarta Mail / Eclipse Angus (EPL-2.0 or GPL-2.0 with
Classpath Exception), jsoup (MIT), Flying Saucer (LGPL-2.1), Guice (Apache-2.0), SLF4J (MIT),
Logback (EPL-1.0 / LGPL-2.1).
