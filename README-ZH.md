# LoginGate 中文说明

当前版本：`v1.8.1`

LoginGate 是一个 Bukkit/Paper 登录大厅身份验证插件。玩家进入服务器后会先进入专用登录世界，完成邮箱注册、密码登录、改密等验证流程后，才会被放行到主世界或主服务器。

`1.8.0` 新增了不依赖 Velocity/BungeeCord 的 Paper 原生转服方案；`1.8.1` 优化了默认配置文件结构和注释，尤其明确了玩家数据存储与桥接门票目录的区别：

- Login 作为登录验证服。
- MC 作为大厅/主服务器。
- 不需要 Velocity 或 BungeeCord。
- 玩家在 Login 完成验证后，LoginGate 会签发 HMAC 桥接票据。
- LoginGate 使用 Paper 原生 transfer 把玩家转到 MC。
- MC 在 pre-login 阶段校验票据。
- 没有有效票据而直接连接 MC 的玩家会被踢出：

```text
未完成身份验证，请先登录！
```

`storage.type: yaml` 只影响玩家账号数据；`bridge-verification.directory` 仍可自定义 file/local 门票目录。

## 兼容范围

- 旧版目标：Mohist `1.20.1`。
- 当前迁移测试目标：Paper `26.1.2`。
- Java：旧版至少 Java `17`；Paper `26.1.2` 测试使用 Java `25`。
- Paper 原生 transfer 需要服务端支持 `Player#transfer(String, int)`。
- BungeeCord/Velocity 代理模式仍保留。

## 主要功能

- 专用登录世界，可选择 `void`、`flat`、`normal`。
- `/register` 邮箱注册。
- `/login` 密码登录。
- `/changepwd` 邮箱验证码改密。
- `/bindemail` 登录后改绑邮箱。
- 可关闭邮箱验证码。
- PBKDF2 加盐密码哈希，不保存明文密码。
- 密码错误锁定、验证码错误锁定。
- 邮箱唯一绑定。
- 可配置密码强度。
- 记录上次登录时间和 IP。
- 新登录环境提醒。
- `/rememberlogin on|off` 短期免登录。
- 登录世界隔离背包、装备、副手、经验、药水效果、血量、饥饿值、游戏模式。
- 登录前快照保存和恢复，支持异常关闭后的恢复。
- 可配置上线、注册后、登录后的自定义消息。
- 支持 `/lang zh` 和 `/lang en`。
- 玩家数据支持 YAML、SQLite、MySQL/MariaDB。
- 安全日志和安全提醒邮件。
- `/logingate` 管理命令。
- 启动时自动补齐新版本缺失配置，不覆盖已有值。

## 指令

玩家指令：

```text
/register
/login
/changepwd
/bindemail [邮箱]
/rememberlogin on
/rememberlogin off
/lang zh
/lang en
```

管理指令：

```text
/logingate reload
/logingate info <玩家>
/logingate unlock <玩家>
/logingate resetpwd <玩家> <新密码>
```

权限：

```text
logingate.admin
```

玩家管理员也必须先完成 LoginGate 验证，才能执行管理命令。控制台不需要验证。

## 单服模式

LoginGate 和主世界在同一个服务端时使用：

```yaml
multi-server:
  server-role: "standalone"
  enabled: false
  transfer-mode: "local"
```

常用本地传送配置：

```yaml
main-world: world

post-login-location:
  enabled: true
  mode: last-location
  save-last-location: true
```

`mode: spawn` 表示登录后传送到主世界出生点。
`mode: last-location` 表示登录后回到玩家上次离线位置。

## Paper 原生 Transfer 双服模式

这是当前推荐的无 Velocity 架构：

- 玩家先连接 Login。
- Login 只负责验证。
- 验证成功后原生 transfer 到 MC。
- MC 校验 Login 签发的桥接票据。
- 直连 MC 且无票据的玩家会被拒绝。

Login 服务器：

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
  secret: "Login 和 MC 保持一致"
```

MC 服务器：

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
  secret: "Login 和 MC 保持一致"
  require-ip-match: true
  require-uuid-match: true
  consume-on-success: true
  illegal-bypass-kick: "&c未完成身份验证，请先登录！"
```

不用 Velocity/BungeeCord 时，要关闭旧代理转发配置：

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

同时建议让 MC 只监听内网地址，或用防火墙阻止玩家公网直连 MC。

## 代理转服模式

如果仍然使用 BungeeCord/Velocity，可以使用代理模式：

```yaml
multi-server:
  server-role: "login"
  enabled: true
  transfer-mode: "proxy"
  target-server: "Pureblock"
  plugin-message-channel: "BungeeCord"
```

Velocity 可按代理配置把 `plugin-message-channel` 改成 `bungeecord:main`。

## 桥接票据存储模式

桥接验证用于 Login 和 MC 之间传递“已完成登录验证”的短期票据。票据带 HMAC 签名，MC 校验通过后才放行。

支持两种模式：

```yaml
bridge-verification:
  enabled: true
  type: "file" # file/local 或 database/db
```

### 本地文件模式

适合同一台机器上的 Login 和 MC。

```yaml
bridge-verification:
  enabled: true
  type: "file"
  directory: "C:/Path/To/LoginGateBridge"
  secret: "所有服务器保持一致"
```

`directory` 可以写绝对路径。Login 和 MC 必须使用同一个目录。

### 数据库模式

适合 Login 和 MC 不在同一台机器，或希望统一通过数据库同步票据。

```yaml
bridge-verification:
  enabled: true
  type: "database"
  database:
    ticket-table: "logingate_bridge_tickets"
    remember-table: "logingate_bridge_remember"
  secret: "所有服务器保持一致"
```

数据库模式复用 `storage` 配置：

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

如果 `storage.type` 仍然是 `yaml`，数据库桥接模式也可以用，会默认使用 `storage.sqlite.file` 创建桥接票据表；玩家账号数据仍可继续保存在 YAML。

## 记住登录桥接

`/rememberlogin on|off` 可以通过同一个桥接后端同步状态：

- file 模式：写入 `bridge-verification.directory`。
- database 模式：写入 `bridge-verification.database.remember-table`。
- Login 和 MC 必须使用相同 `secret`。

## 玩家数据存储

玩家数据存储方式：

```yaml
storage:
  type: "yaml" # yaml, sqlite, mysql, mariadb
```

YAML 文件路径：

```text
plugins/LoginGate/PlayerInfo/players.yml
```

SQLite/MySQL/MariaDB 使用表：

```text
logingate_players
```

桥接数据库模式会额外使用：

```text
logingate_bridge_tickets
logingate_bridge_remember
```

表名可在 `bridge-verification.database` 中自定义。

## SMTP 邮件

开启邮箱验证码：

```yaml
smtp:
  enabled: true
  host: smtp.example.com
  port: 465
  ssl: true
  starttls: false
  username: user@example.com
  password: "邮箱授权码或应用专用密码"
  from: user@example.com
  from-name: LoginGate
```

很多邮箱服务商要求填写 SMTP 授权码或应用专用密码，不是邮箱登录密码。

## 配置自动迁移

插件启动时会补齐缺失的默认配置：

```text
plugins/LoginGate/config.yml
plugins/LoginGate/lang/zh.yml
plugins/LoginGate/lang/en.yml
```

已有值不会被覆盖。迁移前会创建类似 `config.yml.backup-<时间戳>` 的备份。

## Paper 26.1.2 迁移注意事项

- 先在克隆目录测试，不能直接改正式服。
- 主世界升级前必须备份。
- Paper 26.1 世界升级后，应按不可轻易降级处理。
- Login 和 MC 要一起升级到同一版本。
- 玩家客户端也要使用匹配版本。
- 删除 Velocity 后，要关闭旧的 BungeeCord/Velocity 转发配置。
- MC 必须内网绑定或防火墙限制，避免玩家绕过 Login。
- 正式切换前必须测试：注册、登录、免登录、native transfer、MC 直连拦截、封禁、经济、每日奖励、AFK、悬浮字、MOTD、排行榜变量、保护规则和常用命令。

## 构建

当前本地测试 jar：

```text
build/LoginGate-1.8.1.jar
```

当前本地编译参数文件：

```text
build/javac-1.8.1.args
```
