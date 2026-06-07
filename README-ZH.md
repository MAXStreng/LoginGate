# LoginGate 中文说明

LoginGate 是为 Minecraft 1.20.1 Mohist/Bukkit 服务端制作的登录大厅身份验证插件。

玩家进入服务器后会先被送入专用登录世界，完成邮箱注册、密码登录或邮箱验证码改密后，才会被传送到主世界。

## 功能

- 专用登录世界，支持 `void` 虚空、`flat` 超平坦、`normal` 正常世界。
- `loginworld` 留空时自动生成登录世界名：`login_void`、`login_flat` 或 `login_normal`。
- `/register` 邮箱注册，发送验证码后设置密码。
- `/login` 密码登录。
- `/changepwd` 通过邮箱验证码重置密码。
- 密码使用 PBKDF2 加随机盐保存，不保存明文。
- 密码输错过多后的锁定会写入玩家档案，退出重连后不会消失。
- 同一个邮箱重复获取验证码有冷却时间。
- 未完成验证时持续显示 title/subtitle 提示。
- 可配置玩家上线消息、登录成功后的自定义消息。
- 支持语言文件，玩家可使用 `/lang zh` 或 `/lang en` 切换中英文。
- 登录世界可禁止怪物、关闭天气更替、锁定时间、玩家无敌、冻结玩家、隐藏其他玩家。

## 指令

- `/register`：开始邮箱注册流程。
- `/login`：输入密码并进入主世界。
- `/changepwd`：通过邮箱验证码修改密码。
- `/lang zh`：切换为中文。
- `/lang en`：切换为英文。

## 配置文件

主配置文件位置：

`plugins/LoginGate/config.yml`

常用配置：

- `loginworld`：绑定登录世界名称。留空时插件按地形类型自动创建 `login_<type>`。
- `login-world-generation.type`：登录世界地形，可选 `void`、`flat`、`normal`。
- `main-world`：验证完成后传送到的主世界名称。
- `default-language`：默认语言，可选 `zh` 或 `en`。
- `email-code-cooldown-seconds`：同邮箱重复获取验证码的冷却时间。
- `custom-messages`：玩家上线和登录成功后的自定义消息。
- `smtp`：邮箱服务器配置。

## 语言文件

语言文件会生成在：

`plugins/LoginGate/lang/zh.yml`

`plugins/LoginGate/lang/en.yml`

玩家使用 `/lang zh` 或 `/lang en` 切换语言。已注册玩家的语言选择会保存在 `PlayerInfo/players.yml`。

## SMTP 邮件

在 `plugins/LoginGate/config.yml` 中设置：

`smtp.enabled: true`

然后填写 SMTP 地址、端口、用户名、密码、发件地址和发件人名称。

很多邮箱服务商需要填写“SMTP 授权码”或“应用专用密码”，不是邮箱登录密码。

## 玩家数据

玩家档案存放在：

`plugins/LoginGate/PlayerInfo/players.yml`

记录内容包括邮箱、游戏名、加密密码、注册时间、上次登录时间、IP、生成的 Pureblock UUID、语言选择和锁定到期时间。
