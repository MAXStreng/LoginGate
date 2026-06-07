# LoginGate 中文说明

当前版本：`v1.6.0`

LoginGate 是为 Minecraft 1.20.1 Mohist/Bukkit 服务端制作的登录大厅身份验证插件。

玩家进入服务器后会先被送入专用登录世界，完成邮箱注册、密码登录或邮箱验证码改密后，才会被传送到主世界。

## 适配范围

- 推荐服务端：Mohist `1.20.1`
- Bukkit API：`1.20`
- Java：`17` 或更高版本
- 代理兼容：BungeeCord / Velocity，可通过多服兼容模式转发到目标子服
- 其他 Bukkit / Spigot / Paper 派生端未作为主要目标测试，可能可用但不保证完整兼容

## 功能

- 专用登录世界，支持 `void` 虚空、`flat` 超平坦、`normal` 正常世界。
- `loginworld` 留空时自动生成登录世界名：`login_void`、`login_flat` 或 `login_normal`。
- `/register` 邮箱注册，发送验证码后设置密码。
- `/login` 密码登录。
- `/changepwd` 通过邮箱验证码重置密码。
- `/bindemail` 允许已登录玩家通过邮箱验证码改绑邮箱，减少找服主手动处理的成本。
- 可关闭邮箱验证码验证，关闭后注册、改密、改绑会跳过验证码环节。
- 登录后传送位置可配置为主世界出生大厅或玩家上次离开服务器的位置。
- 登录并传送到主世界后可自动执行服主配置的命令。
- 密码使用 PBKDF2 加随机盐保存，不保存明文。
- 密码输错过多后的锁定会写入玩家档案，退出重连后不会消失。
- 同一个邮箱重复获取验证码有冷却时间。
- 验证码错误或过期时会显示独立失败提示页，避免玩家在原流程里反复试错。
- 登录成功后记录上次登录时间和 IP，检测到新的登录环境时提醒玩家及时确认账号安全。
- 未完成验证时持续显示 title/subtitle 提示。
- 可配置玩家上线、注册成功、登录成功后的自定义消息。
- 可按状态分别配置“首次进入”“登录中”“验证成功”“锁定中”“改绑成功”等提示消息。
- 普通提示文案支持逐条启用或关闭。
- 新玩家注册成功后可自动发送服务器规则或新手指引。
- 启动时从公开 `update.json` 检查最新版本，不依赖 GitHub API Token。
- 多服兼容模式，可在登录成功后通过 BungeeCord / Velocity 插件消息连接到目标子服。
- 旧版配置会在启动时自动补齐新版本缺失的默认配置项，已有配置值不会被覆盖。
- 玩家可自行使用 `/rememberlogin on|off` 开关短时免登录，默认不开启。
- 支持邮箱唯一性、验证码尝试次数锁定、可配置密码强度策略。
- 支持注册/登录/失败/锁定/改密/改绑/异常登录安全日志。
- 支持改密、改绑邮箱、异常登录安全提醒邮件。
- 支持 `/logingate` 管理命令，玩家管理员也必须先完成登录验证。
- 支持 YAML、SQLite、MySQL/MariaDB 玩家数据存储。
- 多服后端可按最近验证记录校验玩家是否已通过登录服验证。
- 支持语言文件，玩家可使用 `/lang zh` 或 `/lang en` 切换中英文。
- 登录世界可禁止怪物、关闭天气更替、锁定时间、玩家无敌、冻结玩家、隐藏其他玩家。

## 指令

- `/register`：开始邮箱注册流程。
- `/login`：输入密码并进入主世界。
- `/changepwd`：通过邮箱验证码修改密码。
- `/bindemail [邮箱]`：登录后改绑邮箱；不写邮箱时会提示在聊天栏输入。
- `/rememberlogin on`：登录后开启短时免登录。
- `/rememberlogin off`：关闭短时免登录。
- `/logingate reload`：重载配置和语言文件。
- `/logingate info <玩家>`：查看玩家档案摘要。
- `/logingate unlock <玩家>`：解除登录和验证码锁定。
- `/logingate resetpwd <玩家> <新密码>`：管理员重置玩家密码。
- `/lang zh`：切换为中文。
- `/lang en`：切换为英文。

## 配置文件

主配置文件位置：

`plugins/LoginGate/config.yml`

常用配置：

- `loginworld`：绑定登录世界名称。留空时插件按地形类型自动创建 `login_<type>`。
- `login-world-generation.type`：登录世界地形，可选 `void`、`flat`、`normal`。
- `main-world`：验证完成后传送到的主世界名称。
- `post-login-location`：登录后传送位置策略，可选择出生大厅或上次离开位置。
- `post-login-commands`：登录并传送到主世界后执行的命令列表。
- `multi-server`：多服兼容模式配置。默认关闭，开启后可通过代理转服到目标子服。
- `remember-session`：玩家自选短时免登录配置，默认需要玩家手动开启。
- `verification-code`：验证码最大错误次数和锁定时间。
- `email-unique`：邮箱唯一绑定开关。
- `password-strength`：密码强度要求。
- `security-log`：安全日志开关。
- `security-mail`：安全提醒邮件开关。
- `storage`：玩家数据存储方式，可选 `yaml`、`sqlite`、`mysql`、`mariadb`。
- `default-language`：默认语言，可选 `zh` 或 `en`。
- `message-source`：普通提示文案读取来源，`lang` 为语言文件优先，`config` 为主配置文件优先。
- `message-toggles`：普通提示文案的逐条开关，未单独设置的 key 使用 `default`。
- `email-code-cooldown-seconds`：同邮箱重复获取验证码的冷却时间。
- `email-verification.enabled`：是否启用邮箱验证码验证。
- `state-messages`：按状态拆分的提示消息，可分别配置首次进入、登录中、验证成功、锁定中、改绑成功。
- `custom-messages`：玩家上线、注册成功、登录成功后的自定义消息。
- `update-check.enabled`：是否在启动时检查最新版本。
- `update-check.manifest-url`：公开更新清单地址，默认读取仓库根目录的 `update.json`。
- `smtp`：邮箱服务器配置。

## 消息配置

普通提示文案支持两种维护方式：

- `message-source: "lang"`：默认模式。优先读取 `plugins/LoginGate/lang/zh.yml` 或 `plugins/LoginGate/lang/en.yml`，适合保留 `/lang zh`、`/lang en` 中英文切换。
- `message-source: "config"`：单文件模式。优先读取 `config.yml` 里的 `messages` 和 `mail`，适合只想改一个配置文件的服务器。

`state-messages` 和 `custom-messages` 属于服务器自定义消息组，仍保留在 `config.yml` 中配置。旧版本 `config.yml` 中已有的 `messages`、`mail` 不会被删除，插件会继续兼容读取。

每条普通提示都可以在 `message-toggles` 中单独开关，例如：

```yaml
message-toggles:
  default: true
  register-help: false
  transfer-subtitle: false
```

`state-messages` 和 `custom-messages` 使用各自分组下的 `enabled` 控制是否发送。

## 登录后行为

登录成功后的本地传送位置可在 `post-login-location` 中选择：

```yaml
post-login-location:
  enabled: true
  mode: "last-location"
  save-last-location: true
```

`mode: "spawn"` 会传送到 `main-world` 出生点，`mode: "last-location"` 会传送到玩家上次离开服务器的位置。代理转服模式下，登录服只负责连接目标子服。

传送到本地主世界后可执行命令：

```yaml
post-login-commands:
  enabled: true
  executor: "console"
  commands:
    - "say %player% 已通过 LoginGate 验证"
```

命令支持 `%player%`、`%server%`、`%uuid%`、`%time%` 占位符。

## 多服兼容模式

默认仍使用单服本地世界传送。接入 BungeeCord / Velocity 后，可在 `config.yml` 中开启：

```yaml
multi-server:
  enabled: true
  transfer-mode: "proxy"
  target-server: "Pureblock"
  plugin-message-channel: "BungeeCord"
```

`target-server` 必须和代理端配置的子服名称一致。Velocity 可按代理设置把 `plugin-message-channel` 改为 `bungeecord:main`。

后端子服可设置：

```yaml
multi-server:
  server-role: "backend"
  backend-verified-window-seconds: 300
```

多服共享玩家验证状态时，建议把 `storage.type` 改为 `mysql` 或 `mariadb`，让登录服和后端服读取同一份玩家数据。

## 管理命令

`/logingate` 管理命令需要 `logingate.admin` 权限。玩家即使是 OP，也必须先完成 LoginGate 登录验证后才能执行；控制台可直接执行。

```text
/logingate reload
/logingate info <玩家>
/logingate unlock <玩家>
/logingate resetpwd <玩家> <新密码>
```

## 配置自动迁移

插件启动时会检查 `config.yml` 和 `lang/*.yml`，自动补齐新版本新增但旧文件缺失的配置项。

- 已存在的配置值会保留，不会被默认值覆盖。
- 玩家数据 `PlayerInfo/players.yml` 不参与配置迁移，不会被清空。
- 迁移前会生成备份文件，例如 `config.yml.backup-时间戳`。
- 已从新版默认配置中移除的旧配置项不会被主动删除，避免误删服主自定义内容。

## 语言文件

语言文件会生成在：

`plugins/LoginGate/lang/zh.yml`

`plugins/LoginGate/lang/en.yml`

玩家使用 `/lang zh` 或 `/lang en` 切换语言。已注册玩家的语言选择会保存在 `PlayerInfo/players.yml`。

如果 `message-source` 设置为 `lang`，普通 title、subtitle、聊天提示、邮件标题都建议在语言文件里修改；如果设置为 `config`，则优先读取 `config.yml` 的 `messages` 和 `mail`。

## SMTP 邮件

在 `plugins/LoginGate/config.yml` 中设置：

`smtp.enabled: true`

然后填写 SMTP 地址、端口、用户名、密码、发件地址和发件人名称。

很多邮箱服务商需要填写“SMTP 授权码”或“应用专用密码”，不是邮箱登录密码。

## 玩家数据

玩家档案存放在：

`plugins/LoginGate/PlayerInfo/players.yml`

记录内容包括邮箱、游戏名、加密密码、注册时间、上次登录时间、IP、生成的 Pureblock UUID、语言选择、锁定到期时间、验证码锁定时间、免登录偏好和最近验证时间。

IP 会用于新的登录环境提醒。插件不会读取玩家设备硬件信息，异常登录判断基于服务端可获得的连接信息。
