# LoginGate

LoginGate is a Mohist/Bukkit authentication lobby plugin for Minecraft 1.20.1.

It sends players to a dedicated login world first, handles email registration, password login, password reset, and only then transfers verified players to the main world.

## Features

- Dedicated login world with configurable terrain: `void`, `flat`, or `normal`.
- Automatic login world name when `loginworld` is empty: `login_void`, `login_flat`, or `login_normal`.
- Email verification for `/register` and `/changepwd`.
- Passwords stored as PBKDF2 hashes with random salts.
- Persistent login lock after too many failed password attempts.
- Per-email verification code cooldown.
- Persistent title/subtitle prompts while the player is not verified.
- Configurable join and post-login custom messages.
- Language files with `/lang zh` and `/lang en`.
- Login world controls: no monsters, no weather cycle, locked time, invulnerability, freeze, and player hiding.

## Commands

- `/register`: start email registration.
- `/login`: enter the account password and continue to the main world.
- `/changepwd`: reset the password through email verification.
- `/lang zh`: switch LoginGate text to Chinese.
- `/lang en`: switch LoginGate text to English.

## Configuration

Edit:

`plugins/LoginGate/config.yml`

Important entries:

- `loginworld`: fixed login world name. Leave it empty to let the plugin create `login_<type>`.
- `login-world-generation.type`: login world terrain type, one of `void`, `flat`, or `normal`.
- `main-world`: destination world after successful verification.
- `default-language`: default language, `zh` or `en`.
- `email-code-cooldown-seconds`: cooldown for requesting a new email code for the same email address.
- `custom-messages`: configurable messages shown on join and after login.
- `smtp`: mail server settings.

## Language Files

Language files are copied to:

`plugins/LoginGate/lang/zh.yml`

`plugins/LoginGate/lang/en.yml`

Players can switch language with `/lang zh` or `/lang en`. Registered players keep their chosen language in `PlayerInfo/players.yml`.

## SMTP

Set `smtp.enabled: true` and fill the SMTP host, port, username, password, sender address, and sender name.

Many email providers require an app-specific SMTP authorization code instead of the normal mailbox password.

## Player Data

Player records are stored in:

`plugins/LoginGate/PlayerInfo/players.yml`

Stored fields include email, game name, hashed password, registration time, last login time, IP, generated Pureblock UUID, language, and persistent lock time.
