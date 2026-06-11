# LoginGate

Current version: `v1.8.1`

LoginGate is a Bukkit/Paper authentication lobby plugin. It sends players to a dedicated login world, handles registration and password verification, then releases verified players to the real server world or to another backend server.

Version `1.8.0` adds a no-proxy Paper deployment mode. Version `1.8.1` reorganizes and clarifies the default config, especially the difference between player-data storage and bridge-ticket storage:

- Login runs as the authentication server.
- MC runs as the lobby/main backend server.
- Velocity/BungeeCord is not required.
- After successful verification, LoginGate issues a signed bridge ticket and uses Paper native transfer.
- MC validates the bridge ticket during pre-login.
- Direct MC connections without a valid ticket are rejected with:

```text
未完成身份验证，请先登录！
```

`storage.type: yaml` only controls player account records; `bridge-verification.directory` can still customize the file/local bridge-ticket directory.

## Compatibility

- Tested legacy target: Mohist `1.20.1`.
- Migration test target: Paper `26.1.2`.
- Java: `17` or newer for legacy builds; Paper `26.1.2` testing used Java `25`.
- Bukkit/Paper API: compiled with the existing Bukkit-compatible API and uses reflection for Paper native transfer.
- Proxy mode is still available for BungeeCord/Velocity networks.
- Paper native transfer requires a Minecraft/Paper version that supports `Player#transfer(String, int)`.

## Main Features

- Dedicated login world with configurable terrain: `void`, `flat`, or `normal`.
- Email registration through `/register`.
- Password login through `/login`.
- Email-based password reset through `/changepwd`.
- Email rebinding through `/bindemail`.
- Optional email verification.
- PBKDF2 password hashes with random salts.
- Login attempt lockouts and verification-code lockouts.
- Unique email binding.
- Configurable password strength rules.
- Last login time and IP tracking.
- Suspicious login warnings.
- Remembered login sessions through `/rememberlogin on|off`.
- Login-world player isolation for inventory, armor, offhand, experience, potion effects, health, hunger, and game mode.
- Snapshot restoration after login, reload, or crash recovery.
- Configurable join, post-register, and post-login messages.
- Language files with `/lang zh` and `/lang en`.
- YAML, SQLite, and MySQL/MariaDB player-data storage.
- Security logs and optional security notice emails.
- Admin command group: `/logingate`.
- Automatic default config migration while keeping existing values.

## Commands

Player commands:

```text
/register
/login
/changepwd
/bindemail [email]
/rememberlogin on
/rememberlogin off
/lang zh
/lang en
```

Admin commands:

```text
/logingate reload
/logingate info <player>
/logingate unlock <player>
/logingate resetpwd <player> <newPassword>
```

Permission:

```text
logingate.admin
```

Player admins must complete LoginGate verification before using admin commands. Console commands are allowed directly.

## Single-Server Mode

Use this when LoginGate and the main world are on the same server.

```yaml
multi-server:
  server-role: "standalone"
  enabled: false
  transfer-mode: "local"
```

Important local settings:

```yaml
main-world: world

post-login-location:
  enabled: true
  mode: last-location
  save-last-location: true
```

`mode: spawn` sends players to the main-world spawn.
`mode: last-location` sends players to their last saved logout location.

## Paper Native Transfer Mode

Use this when you want no Velocity/BungeeCord:

- `Login` server: authentication only.
- `MC` server: lobby/main server.
- Both servers run Paper.
- Both servers share the same `bridge-verification.secret`.
- Both servers use the same bridge storage backend.
- MC must not have BungeeCord/Velocity forwarding enabled.

Login server:

```yaml
multi-server:
  server-role: "login"
  enabled: true
  transfer-mode: "native-transfer"
  native-transfer:
    host: "127.0.0.1"
    port: 25567
  fallback-to-local-world: false

bridge-verification:
  enabled: true
  type: "file"
  directory: "C:/Users/ohhhh/Desktop/Server/MigrationTest/LoginGateBridge"
  secret: "same value on Login and MC"
```

MC backend server:

```yaml
multi-server:
  server-role: "backend"
  enabled: true
  transfer-mode: "local"
  backend-verified-window-seconds: 300

bridge-verification:
  enabled: true
  type: "file"
  directory: "C:/Users/ohhhh/Desktop/Server/MigrationTest/LoginGateBridge"
  secret: "same value on Login and MC"
  require-ip-match: true
  require-uuid-match: true
  consume-on-success: true
  illegal-bypass-kick: "&c未完成身份验证，请先登录！"
```

Paper/Spigot proxy leftovers must be disabled when no proxy is used:

```yaml
# spigot.yml
settings:
  bungeecord: false
```

```yaml
# config/paper-global.yml
proxies:
  velocity:
    enabled: false
  proxy-protocol: false
```

Also firewall or bind MC so public players cannot bypass Login.

## Proxy Transfer Mode

Use this for BungeeCord/Velocity networks:

```yaml
multi-server:
  server-role: "login"
  enabled: true
  transfer-mode: "proxy"
  target-server: "Pureblock"
  plugin-message-channel: "BungeeCord"
```

Velocity networks can use `bungeecord:main` if the proxy is configured for that channel.

## Bridge Verification Storage

Bridge verification signs short-lived HMAC tickets on the login server and verifies them on the backend server.

Supported modes:

```yaml
bridge-verification:
  enabled: true
  type: "file" # file/local or database/db
```

### File / Local Mode

Best for Login and MC on the same machine.

```yaml
bridge-verification:
  enabled: true
  type: "file"
  directory: "C:/Path/To/LoginGateBridge"
  secret: "same value on every server"
```

`directory` can be an absolute path. Login and MC must use the same path.

### Database Mode

Best when Login and MC are on different machines or when you prefer shared database state.

```yaml
bridge-verification:
  enabled: true
  type: "database"
  database:
    ticket-table: "logingate_bridge_tickets"
    remember-table: "logingate_bridge_remember"
  secret: "same value on every server"
```

Database mode reuses the existing `storage` settings:

```yaml
storage:
  type: "sqlite" # yaml, sqlite, mysql, mariadb
  sqlite:
    file: PlayerInfo/players.db
  mysql:
    host: localhost
    port: 3306
    database: logingate
    username: root
    password: ""
    jdbc-url: ""
```

If `storage.type` is `yaml`, database bridge mode still works by using `storage.sqlite.file` for the bridge tables. Player records can remain YAML.

## Remembered Login Bridge

`/rememberlogin on|off` can synchronize across Login and backend servers through the same bridge backend.

- In file mode, remembered-login state is written to the bridge directory.
- In database mode, remembered-login state is written to `bridge-verification.database.remember-table`.
- The same `secret` must be used on every server.

## Storage

Player records support:

```yaml
storage:
  type: "yaml" # yaml, sqlite, mysql, mariadb
```

YAML stores records under:

```text
plugins/LoginGate/PlayerInfo/players.yml
```

SQLite/MySQL/MariaDB store records in `logingate_players`.

## SMTP

Enable SMTP for email codes:

```yaml
smtp:
  enabled: true
  host: smtp.example.com
  port: 465
  ssl: true
  starttls: false
  username: user@example.com
  password: "app-specific-password"
  from: user@example.com
  from-name: LoginGate
```

Many email providers require an SMTP authorization code or app password instead of the normal mailbox password.

## Configuration Migration

On startup, LoginGate fills missing default options in:

```text
plugins/LoginGate/config.yml
plugins/LoginGate/lang/zh.yml
plugins/LoginGate/lang/en.yml
```

Existing values are kept. A backup such as `config.yml.backup-<timestamp>` is created before migration.

## Paper 26.1.2 Migration Notes

- Test on cloned server folders first.
- Do not upgrade production worlds without backups.
- Paper 26.1 world upgrades should be treated as one-way for practical rollback planning.
- Upgrade Login and MC together.
- Use a Minecraft client matching the target server version.
- Disable old proxy forwarding settings if Velocity/BungeeCord is removed.
- Keep MC private or firewalled so players cannot bypass Login.
- Validate registration, login, remembered login, native transfer, direct-MC rejection, bans, economy, daily rewards, AFK, holograms, MOTD, leaderboards, protection rules, and common commands before production cutover.

## Build

The current local test jar is:

```text
build/LoginGate-1.8.1.jar
```

The project currently uses a local javac argument file:

```text
build/javac-1.8.1.args
```
