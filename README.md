# LoginGate

Current version: `v1.5.0`

LoginGate is a Mohist/Bukkit authentication lobby plugin for Minecraft 1.20.1.

It sends players to a dedicated login world first, handles email registration, password login, password reset, and only then transfers verified players to the main world.

## Compatibility

- Recommended server: Mohist `1.20.1`
- Bukkit API: `1.20`
- Java: `17` or newer
- Proxy compatibility: BungeeCord / Velocity, through the multi-server transfer mode
- Other Bukkit / Spigot / Paper-derived servers are not the primary test target and may work, but full compatibility is not guaranteed.

## Features

- Dedicated login world with configurable terrain: `void`, `flat`, or `normal`.
- Automatic login world name when `loginworld` is empty: `login_void`, `login_flat`, or `login_normal`.
- Email verification for `/register` and `/changepwd`.
- `/bindemail` lets verified players rebind their email address through email verification.
- Passwords stored as PBKDF2 hashes with random salts.
- Persistent login lock after too many failed password attempts.
- Per-email verification code cooldown.
- Separate verification failure page for wrong or expired email codes.
- Last login time and IP tracking, with a warning when the login environment changes.
- Persistent title/subtitle prompts while the player is not verified.
- Configurable join, post-register, and post-login custom messages.
- Fine-grained state messages for first enter, logging in, verification success, locked, and email bind success.
- New player guide messages after successful registration.
- Startup update check through a public `update.json` manifest, without requiring a GitHub API token.
- Multi-server compatibility mode for transferring verified players through BungeeCord / Velocity plugin messages.
- Automatic configuration migration fills missing new default options while keeping existing values.
- Player-controlled remembered login sessions through `/rememberlogin on|off`, disabled per player by default.
- Unique email binding, verification code attempt lockouts, and configurable password strength rules.
- Security logs for registration, login, failures, lockouts, password changes, email rebinding, and suspicious logins.
- Security notice emails for password changes, email rebinding, and suspicious logins.
- `/logingate` admin commands; player admins must complete LoginGate verification before using them.
- YAML, SQLite, and MySQL/MariaDB player data storage.
- Backend servers can validate recently verified players for proxy-based networks.
- Language files with `/lang zh` and `/lang en`.
- Login world controls: no monsters, no weather cycle, locked time, invulnerability, freeze, and player hiding.

## Commands

- `/register`: start email registration.
- `/login`: enter the account password and continue to the main world.
- `/changepwd`: reset the password through email verification.
- `/bindemail [email]`: rebind the account email after login. If no email is provided, the plugin asks for it in chat.
- `/rememberlogin on`: enable short remembered login for yourself after login.
- `/rememberlogin off`: disable remembered login.
- `/logingate reload`: reload config and language files.
- `/logingate info <player>`: show a player account summary.
- `/logingate unlock <player>`: clear login and verification-code locks.
- `/logingate resetpwd <player> <newPassword>`: reset a player password as an admin.
- `/lang zh`: switch LoginGate text to Chinese.
- `/lang en`: switch LoginGate text to English.

## Configuration

Edit:

`plugins/LoginGate/config.yml`

Important entries:

- `loginworld`: fixed login world name. Leave it empty to let the plugin create `login_<type>`.
- `login-world-generation.type`: login world terrain type, one of `void`, `flat`, or `normal`.
- `main-world`: destination world after successful verification.
- `multi-server`: multi-server compatibility settings. Disabled by default; when enabled, verified players can be sent to a proxy target server.
- `remember-session`: player-controlled short remembered login settings.
- `verification-code`: maximum wrong code attempts and lock duration.
- `email-unique`: one-email-per-account policy.
- `password-strength`: configurable password complexity requirements.
- `security-log`: security log switch.
- `security-mail`: security notice mail switch.
- `storage`: player data storage, one of `yaml`, `sqlite`, `mysql`, or `mariadb`.
- `default-language`: default language, `zh` or `en`.
- `message-source`: normal text source. Use `lang` for language files first or `config` for config.yml first.
- `email-code-cooldown-seconds`: cooldown for requesting a new email code for the same email address.
- `state-messages`: separate configurable message groups for first enter, logging in, verification success, locked, and email bind success.
- `custom-messages`: configurable messages shown on join, after registration, and after login.
- `update-check.enabled`: whether to check for updates during plugin startup.
- `update-check.manifest-url`: public update manifest URL. By default it reads `update.json` from this repository.
- `smtp`: mail server settings.

## Message Customization

Normal messages can be maintained in two ways:

- `message-source: "lang"`: default mode. LoginGate reads `plugins/LoginGate/lang/zh.yml` or `plugins/LoginGate/lang/en.yml` first, which keeps `/lang zh` and `/lang en` useful.
- `message-source: "config"`: single-file mode. LoginGate reads `messages` and `mail` from `config.yml` first, useful for servers that only want to edit one file.

`state-messages` and `custom-messages` are server-specific message groups and remain in `config.yml`. Existing legacy `messages` and `mail` entries in older configs are kept compatible.

## Multi-Server Mode

LoginGate still uses local world teleport by default. For BungeeCord / Velocity setups, enable proxy transfer in `config.yml`:

```yaml
multi-server:
  enabled: true
  transfer-mode: "proxy"
  target-server: "Pureblock"
  plugin-message-channel: "BungeeCord"
```

`target-server` must match the server name configured in the proxy. Velocity setups can change `plugin-message-channel` to `bungeecord:main` when needed.

Backend servers can use:

```yaml
multi-server:
  server-role: "backend"
  backend-verified-window-seconds: 300
```

For shared verification state across servers, use `storage.type: "mysql"` or `storage.type: "mariadb"` so the login server and backend servers read the same player data.

## Admin Commands

`/logingate` admin commands require `logingate.admin`. Player admins must complete LoginGate verification first; the console can run them directly.

```text
/logingate reload
/logingate info <player>
/logingate unlock <player>
/logingate resetpwd <player> <newPassword>
```

## Configuration Migration

On startup, LoginGate checks `config.yml` and `lang/*.yml`, then fills any missing options that exist in the bundled defaults.

- Existing values are kept and are not overwritten by defaults.
- Player data in `PlayerInfo/players.yml` is not part of configuration migration and will not be cleared.
- A backup is created before migration, such as `config.yml.backup-timestamp`.
- Old custom options removed from newer defaults are not actively deleted, so custom server-side entries are not lost.

## Language Files

Language files are copied to:

`plugins/LoginGate/lang/zh.yml`

`plugins/LoginGate/lang/en.yml`

Players can switch language with `/lang zh` or `/lang en`. Registered players keep their chosen language in `PlayerInfo/players.yml`.

When `message-source` is `lang`, edit regular titles, subtitles, chat prompts, and mail subjects in the language files. When it is `config`, `config.yml` entries under `messages` and `mail` take priority.

## SMTP

Set `smtp.enabled: true` and fill the SMTP host, port, username, password, sender address, and sender name.

Many email providers require an app-specific SMTP authorization code instead of the normal mailbox password.

## Player Data

Player records are stored in:

`plugins/LoginGate/PlayerInfo/players.yml`

Stored fields include email, game name, hashed password, registration time, last login time, IP, generated Pureblock UUID, language, persistent lock time, verification-code lock time, remembered-login preference, and last verified time.

The login environment warning is based on server-visible connection data such as IP address. LoginGate does not read hardware device identifiers.
