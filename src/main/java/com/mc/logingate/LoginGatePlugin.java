package com.mc.logingate;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class LoginGatePlugin extends JavaPlugin implements Listener {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter STORE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy年MM月dd日-HH时mm分ss秒").withZone(ZoneId.systemDefault());
    private static final String GITHUB_REPOSITORY_URL = "https://github.com/MAXStreng/LoginGate";
    private static final String DEFAULT_UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/MAXStreng/LoginGate/main/update.json";
    private static final String LEGACY_PROXY_CHANNEL = "BungeeCord";
    private static final String MODERN_PROXY_CHANNEL = "bungeecord:main";

    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<String, PlayerRecord> records = new HashMap<>();
    private final Map<String, Long> emailCooldowns = new HashMap<>();
    private final Map<String, FileConfiguration> languageConfigs = new HashMap<>();
    private final Map<UUID, String> languagePreferences = new HashMap<>();
    private final Set<UUID> authenticated = new HashSet<>();
    private final Set<UUID> transferring = new HashSet<>();
    private final Map<UUID, Long> verificationHoldUntil = new HashMap<>();

    private File recordsFile;
    private FileConfiguration recordsConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupLanguages();
        setupRecords();
        loadRecords();
        setupWorlds();
        applyLoginWorldRules();

        Bukkit.getPluginManager().registerEvents(this, this);
        bindCommand("register");
        bindCommand("login");
        bindCommand("changepwd");
        bindCommand("bindemail");
        bindCommand("lang");
        setupProxyChannels();
        startPersistentTitleTask();
        startLoginWorldRuleTask();

        startStartupBanner();
    }

    @Override
    public void onDisable() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this);
        saveRecords();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("lang".equals(name)) {
            handleLanguageCommand(player, args);
            return true;
        }
        if ("bindemail".equals(name)) {
            if (!isVerified(player)) {
                player.sendMessage(message(player, "bindemail-verified-only", "&c请先完成登录后再修改绑定邮箱。"));
                return true;
            }
        }

        if (!isInLoginWorld(player) && !("bindemail".equals(name) && isVerified(player))) {
            player.sendMessage(message(player, "already-logged-in", "&a您已经以 &b%player% &a的身份登录成功了！",
                    "%player%", player.getName()));
            return true;
        }

        if ("register".equals(name)) {
            handleRegisterCommand(player, args);
            return true;
        }
        if ("login".equals(name)) {
            handleLoginCommand(player, args);
            return true;
        }
        if ("changepwd".equals(name)) {
            handleChangePasswordCommand(player);
            return true;
        }
        if ("bindemail".equals(name)) {
            handleBindEmailCommand(player, args);
            return true;
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        authenticated.remove(player.getUniqueId());
        transferring.remove(player.getUniqueId());

        if (player.hasPermission("logingate.bypass")) {
            authenticated.add(player.getUniqueId());
            return;
        }

        teleportToLogin(player);
        prepareLoginPlayer(player);
        PlayerRecord record = getRecord(player);
        if (isLocked(player)) {
            sendConfiguredMessages(player, "state-messages.locked.messages");
            showLocked(player);
        } else if (record == null) {
            sendConfiguredMessages(player, "state-messages.first-enter.messages");
            player.sendMessage(message(player, "register-help", "&d&lPureblock &8| &7欢迎来到 &b纯境方块&7。请输入 &e/register &7开始邮箱注册。"));
        } else {
            sendConfiguredMessages(player, "state-messages.logging-in.messages");
            player.sendMessage(message(player, "login-help", "&d&lPureblock &8| &7欢迎回来，&b%player%&7。请输入 &e/login &7完成身份验证。"));
        }
        sendConfiguredMessages(player, "custom-messages.on-join.messages");
        showGatePrompt(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        authenticated.remove(uuid);
        transferring.remove(uuid);
        sessions.remove(uuid);
        verificationHoldUntil.remove(uuid);
        showAllPlayers(event.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isVerified(player) || !isInLoginWorld(player) || !getConfig().getBoolean("login-world-settings.freeze-player", true)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && isInLoginWorld(player)
                && getConfig().getBoolean("login-world-settings.invulnerable", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (getConfig().getBoolean("login-world-settings.disable-monster-spawn", true)
                && event.getEntity() instanceof Monster
                && isLoginWorld(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isInLoginWorld(player)) {
            prepareLoginPlayer(player);
            return;
        }
        if (!isVerified(player) && !player.hasPermission("logingate.bypass")) {
            player.kickPlayer(message(player, "unverified-kick", "&c未完成 Pureblock 身份验证，无法进入主世界。"));
        } else {
            showAllPlayers(player);
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isVerified(player)) {
            return;
        }

        String command = event.getMessage().split(" ")[0].toLowerCase(Locale.ROOT);
        if (isInLoginWorld(player) && (command.equals("/register") || command.equals("/login") || command.equals("/changepwd"))) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(message(player, "command-blocked", "&c验证前只能在登录世界使用 &e/register&c、&e/login &c或 &e/changepwd&c。"));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());

        if (!isVerified(player) || session != null) {
            event.setCancelled(true);
        }

        if (session == null) {
            if (!isVerified(player)) {
                player.sendMessage(message(player, "chat-blocked", "&c验证前不能聊天。请使用 &e/register &c或 &e/login&c。"));
            }
            return;
        }

        String message = event.getMessage().trim();
        Bukkit.getScheduler().runTask(this, () -> handleSessionInput(player, message));
    }

    private void handleRegisterCommand(Player player, String[] args) {
        if (getRecord(player) != null) {
            player.sendMessage(message(player, "already-registered", "&a你已经完成注册。请使用 &e/login &a登录。"));
            return;
        }

        if (args.length >= 1 && EMAIL_PATTERN.matcher(args[0]).matches()) {
            beginRegisterEmail(player, args[0]);
            return;
        }

        sessions.put(player.getUniqueId(), new Session(SessionMode.REGISTER_EMAIL));
        player.sendMessage(message(player, "input-email", "&d&lPureblock &8| &7请在聊天栏输入你的 &b邮箱地址&7，该内容不会广播。"));
        showGatePrompt(player);
    }

    private void startStartupBanner() {
        String[] art = {
                "",
                "██╗      ██████╗  ██████╗ ██╗███╗   ██╗ ██████╗  █████╗ ████████╗███████╗",
                "██║     ██╔═══██╗██╔════╝ ██║████╗  ██║██╔════╝ ██╔══██╗╚══██╔══╝██╔════╝",
                "██║     ██║   ██║██║  ███╗██║██╔██╗ ██║██║  ███╗███████║   ██║   █████╗  ",
                "██║     ██║   ██║██║   ██║██║██║╚██╗██║██║   ██║██╔══██║   ██║   ██╔══╝  ",
                "███████╗╚██████╔╝╚██████╔╝██║██║ ╚████║╚██████╔╝██║  ██║   ██║   ███████╗",
                "╚══════╝ ╚═════╝  ╚═════╝ ╚═╝╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═╝   ╚══╝   ╚══════╝",
                ""
        };
        for (String line : art) {
            startupInfo(line);
        }

        String[] lines = {
                "感谢您使用“LoginGate”，反馈或获取优质资源请前往QQ群：768646780",
                "MoonMiut正在搭建卷闸门：LoginGate......",
                "MoonMiut正为生锈的卷闸门通电......",
                "MoonMiut正在生成看守者并读取门禁信息......",
                "MoonMiut正在咨询供应商是否有更新的产品......"
        };

        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                if (index >= lines.length) {
                    cancel();
                    checkUpdateAndPrint();
                    return;
                }
                startupInfo(lines[index]);
                index++;
            }
        }.runTaskTimer(this, 0L, 30L);
    }

    private void checkUpdateAndPrint() {
        if (!getConfig().getBoolean("update-check.enabled", true)) {
            startupInfo("当前版本" + withVersionPrefix(getDescription().getVersion()) + "，更新检查已关闭，前往" + GITHUB_REPOSITORY_URL + "仓库链接下载最新版本");
            startupInfo("大功告成！目前生锈的门还能撑一段时间......");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            UpdateInfo updateInfo = fetchLatestRelease();
            Bukkit.getScheduler().runTask(this, () -> {
                startupInfo(buildUpdateMessage(updateInfo));
                startupInfo("大功告成！目前生锈的门还能撑一段时间......");
            });
        });
    }

    private String buildUpdateMessage(UpdateInfo updateInfo) {
        String current = withVersionPrefix(getDescription().getVersion());
        if (updateInfo == null || updateInfo.version == null || updateInfo.version.isBlank()) {
            return "当前版本" + current + "，暂未检测到GitHub最新版本，前往" + GITHUB_REPOSITORY_URL + "仓库链接下载最新版本";
        }

        String latest = withVersionPrefix(updateInfo.version);
        if (normalizeVersion(current).equals(normalizeVersion(latest))) {
            return "当前版本" + current + "，最新版本" + latest + "，当前使用的是最新版本，前往" + updateInfo.url + "仓库链接下载最新版本";
        }

        return "当前版本" + current + "，最新版本" + latest + "，检测到新版本" + latest
                + "，更新内容为" + summarizeReleaseBody(updateInfo.body)
                + "，前往" + updateInfo.url + "仓库链接下载最新版本";
    }

    private UpdateInfo fetchLatestRelease() {
        HttpURLConnection connection = null;
        try {
            String manifestUrl = getConfig().getString("update-check.manifest-url", DEFAULT_UPDATE_MANIFEST_URL);
            if (manifestUrl == null || manifestUrl.isBlank()) {
                manifestUrl = DEFAULT_UPDATE_MANIFEST_URL;
            }

            URL url = new URL(manifestUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
            connection.setRequestProperty("User-Agent", "LoginGate/" + getDescription().getVersion());

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                return null;
            }

            String json;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                json = builder.toString();
            }

            UpdateInfo info = new UpdateInfo();
            info.version = jsonValue(json, "version");
            if (info.version == null || info.version.isBlank()) {
                info.version = jsonValue(json, "tag_name");
            }
            if (info.version == null || info.version.isBlank()) {
                info.version = jsonValue(json, "name");
            }
            info.url = jsonValue(json, "html_url");
            if (info.url == null || info.url.isBlank()) {
                info.url = jsonValue(json, "url");
            }
            if (info.url == null || info.url.isBlank()) {
                info.url = GITHUB_REPOSITORY_URL;
            }
            info.body = jsonValue(json, "body");
            if (info.body == null || info.body.isBlank()) {
                info.body = jsonValue(json, "description");
            }
            return info;
        } catch (IOException ex) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String jsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + pattern.length());
        if (colon < 0) {
            return null;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length() || json.charAt(start) != '"') {
            return null;
        }
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                value.append(switch (c) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            try {
                                i += 4;
                                yield (char) Integer.parseInt(hex, 16);
                            } catch (NumberFormatException ignored) {
                                yield 'u';
                            }
                        }
                        yield 'u';
                    }
                    default -> c;
                });
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        return null;
    }

    private String summarizeReleaseBody(String body) {
        if (body == null || body.isBlank()) {
            return "暂无更新描述";
        }
        String summary = body.replace("\r", "\n")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return summary.length() > 180 ? summary.substring(0, 177) + "..." : summary;
    }

    private String withVersionPrefix(String version) {
        if (version == null || version.isBlank()) {
            return "v未知";
        }
        return version.toLowerCase(Locale.ROOT).startsWith("v") ? version : "v" + version;
    }

    private String normalizeVersion(String version) {
        return version == null ? "" : version.toLowerCase(Locale.ROOT).replaceFirst("^v", "").trim();
    }

    private void startupInfo(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + message);
    }

    private void handleLoginCommand(Player player, String[] args) {
        PlayerRecord record = getRecord(player);
        if (record == null) {
            player.sendMessage(message(player, "not-registered", "&c你还没有注册。请先使用 &e/register&c。"));
            return;
        }
        if (isLocked(player)) {
            showLocked(player);
            return;
        }
        if (args.length >= 1) {
            verifyLoginPassword(player, args[0]);
            return;
        }

        sessions.put(player.getUniqueId(), new Session(SessionMode.LOGIN_PASSWORD));
        player.sendMessage(message(player, "input-password", "&d&lPureblock &8| &7请在聊天栏输入 &e登录密码&7，该内容不会广播。"));
        showGatePrompt(player);
    }

    private void handleChangePasswordCommand(Player player) {
        PlayerRecord record = getRecord(player);
        if (record == null) {
            player.sendMessage(message(player, "not-registered", "&c你还没有注册。请先使用 &e/register&c。"));
            return;
        }
        if (isLocked(player)) {
            showLocked(player);
            return;
        }
        if (!checkEmailCooldown(player, record.email)) {
            return;
        }

        Session session = new Session(SessionMode.CHANGE_CODE);
        session.email = record.email;
        session.code = createCode();
        session.expiresAt = System.currentTimeMillis() + getCodeExpireMillis();
        sessions.put(player.getUniqueId(), session);
        sendCodeAsync(player, record.email, session.code, mailSubject(player, "change-subject", "Pureblock 修改密码验证码"));
        player.sendMessage(message(player, "change-code-sent", "&b验证码已发送到你的注册邮箱。&7请在聊天栏输入验证码。"));
        showGatePrompt(player);
    }

    private void handleBindEmailCommand(Player player, String[] args) {
        PlayerRecord record = getRecord(player);
        if (record == null) {
            player.sendMessage(message(player, "not-registered", "&c你还没有注册。请先使用 &e/register&c。"));
            return;
        }

        if (args.length >= 1 && EMAIL_PATTERN.matcher(args[0]).matches()) {
            beginBindEmail(player, args[0]);
            return;
        }

        Session session = new Session(SessionMode.BIND_EMAIL);
        sessions.put(player.getUniqueId(), session);
        player.sendMessage(message(player, "input-bind-email", "&d&lPureblock &8| &7请输入你要绑定的新 &b邮箱地址&7。"));
        showGatePrompt(player);
    }

    private void handleLanguageCommand(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(message(player, "lang-usage", "&e用法：/lang zh 或 /lang en"));
            return;
        }

        String language = normalizeLanguage(args[0]);
        if (!languageConfigs.containsKey(language)) {
            player.sendMessage(message(player, "lang-unknown", "&c暂不支持该语言。可用语言：&e/lang zh &7或 &e/lang en",
                    "%lang%", args[0]));
            return;
        }

        languagePreferences.put(player.getUniqueId(), language);
        PlayerRecord record = getRecord(player);
        if (record != null) {
            record.language = language;
            saveRecords();
        }
        player.sendMessage(message(player, "lang-changed", "&a语言已切换为：&e中文", "%lang%", language));
        showGatePrompt(player);
    }

    private void handleSessionInput(Player player, String input) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        switch (session.mode) {
            case REGISTER_EMAIL -> {
                if (!EMAIL_PATTERN.matcher(input).matches()) {
                    player.sendMessage(message(player, "invalid-email", "&c邮箱格式不正确，请重新输入。"));
                    return;
                }
                beginRegisterEmail(player, input);
            }
            case REGISTER_CODE -> {
                boolean expired = session.expiresAt > 0 && session.expiresAt < System.currentTimeMillis();
                if (!checkCode(session, input)) {
                    showVerificationFailure(player, session, expired);
                    sessions.remove(player.getUniqueId());
                    return;
                }
                session.mode = SessionMode.REGISTER_PASSWORD;
                player.sendMessage(message(player, "code-ok-set-password", "&a邮箱验证成功。&7请设置登录密码。"));
                showGatePrompt(player);
            }
            case REGISTER_PASSWORD -> finishRegistration(player, input, session);
            case LOGIN_PASSWORD -> verifyLoginPassword(player, input);
            case CHANGE_CODE -> {
                if (!checkCode(session, input)) {
                    showVerificationFailure(player, session, false);
                    sessions.remove(player.getUniqueId());
                    return;
                }
                session.mode = SessionMode.CHANGE_PASSWORD;
                player.sendMessage(message(player, "code-ok-reset-password", "&a邮箱验证成功。&7请设置新密码。"));
                showGatePrompt(player);
            }
            case CHANGE_PASSWORD -> finishPasswordChange(player, input);
            case BIND_EMAIL -> {
                if (!EMAIL_PATTERN.matcher(input).matches()) {
                    player.sendMessage(message(player, "invalid-email", "&c邮箱格式不正确，请重新输入。"));
                    return;
                }
                beginBindEmail(player, input);
            }
            case BIND_CODE -> {
                if (!checkCode(session, input)) {
                    showVerificationFailure(player, session, false);
                    sessions.remove(player.getUniqueId());
                    return;
                }
                finishBindEmail(player, session);
            }
        }
    }

    private void beginRegisterEmail(Player player, String email) {
        if (!checkEmailCooldown(player, email)) {
            return;
        }

        Session session = new Session(SessionMode.REGISTER_CODE);
        session.email = email;
        session.code = createCode();
        session.expiresAt = System.currentTimeMillis() + getCodeExpireMillis();
        sessions.put(player.getUniqueId(), session);

        sendCodeAsync(player, email, session.code, mailSubject(player, "register-subject", "Pureblock 注册验证码"));
        player.sendMessage(message(player, "register-code-sent", "&b验证码已发送到 &e%email%&b。&7请在聊天栏输入验证码。",
                "%email%", email));
        showGatePrompt(player);
    }

    private void finishRegistration(Player player, String password, Session session) {
        if (!validatePassword(player, password)) {
            return;
        }

        String now = STORE_TIME.format(Instant.now());
        PlayerRecord record = new PlayerRecord();
        record.gameName = player.getName();
        record.email = session.email;
        record.generatedUuid = UUID.randomUUID().toString();
        record.registeredAt = now;
        record.lastLoginAt = now;
        record.lastIp = getAddress(player);
        record.language = getPlayerLanguage(player);

        PasswordHash hash = hashPassword(password);
        record.passwordHash = hash.hash;
        record.passwordSalt = hash.salt;

        records.put(player.getName().toLowerCase(Locale.ROOT), record);
        saveRecords();
        sessions.remove(player.getUniqueId());
        verificationHoldUntil.remove(player.getUniqueId());

        player.sendMessage(message(player, "register-success", "&a注册成功。&7你的 Pureblock UUID 为 &d%uuid%&7。",
                "%uuid%", record.generatedUuid));
        sendConfiguredMessages(player, "state-messages.verification-success.messages");
        sendConfiguredMessages(player, "custom-messages.after-register.messages");
        startTransferCountdown(player);
    }

    private void verifyLoginPassword(Player player, String password) {
        PlayerRecord record = getRecord(player);
        if (record == null) {
            player.sendMessage(message(player, "not-registered", "&c你还没有注册。请先使用 &e/register&c。"));
            return;
        }
        if (isLocked(player)) {
            showLocked(player);
            return;
        }

        if (verifyPassword(password, record.passwordSalt, record.passwordHash)) {
            String previousLogin = formatStoredTime(record.lastLoginAt);
            String currentIp = getAddress(player);
            if (record.lastIp != null && !record.lastIp.isBlank() && !record.lastIp.equals(currentIp)) {
                player.sendMessage(message(player, "suspicious-login",
                        "&e检测到新的登录环境：&6%ip%&e。若这不是你本人操作，请尽快修改密码。",
                        "%ip%", currentIp));
            }
            record.lastIp = currentIp;
            record.lastLoginAt = STORE_TIME.format(Instant.now());
            record.lockedUntil = 0L;
            saveRecords();
            sessions.remove(player.getUniqueId());
            verificationHoldUntil.remove(player.getUniqueId());

            if (previousLogin == null || previousLogin.isBlank()) {
                player.sendMessage(message(player, "first-login", "&d&lPureblock &8| &a欢迎回来 &b%player%&a！这是你第一次完成登录验证。"));
            } else {
                player.sendMessage(message(player, "welcome-back", "&d&lPureblock &8| &a欢迎回来 &b%player%&a！&7您上次成功登录的时间为：&e%time%",
                        "%time%", previousLogin));
            }
            sendConfiguredMessages(player, "state-messages.verification-success.messages");
            sendConfiguredMessages(player, "custom-messages.after-login.messages");
            startTransferCountdown(player);
            return;
        }

        Session session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new Session(SessionMode.LOGIN_PASSWORD));
        session.failedAttempts++;
        int maxAttempts = getConfig().getInt("max-login-attempts", 3);
        if (session.failedAttempts >= maxAttempts) {
            session.failedAttempts = 0;
            long lockedUntil = System.currentTimeMillis() + getConfig().getInt("login-lock-minutes", 3) * 60_000L;
            session.lockedUntil = lockedUntil;
            record.lockedUntil = lockedUntil;
            saveRecords();
            player.sendMessage(message(player, "login-locked", "&c密码错误次数过多，登录已锁定。"));
            showLocked(player);
        } else {
            player.sendMessage(message(player, "password-wrong", "&c密码错误。剩余尝试次数：&e%left%",
                    "%left%", String.valueOf(maxAttempts - session.failedAttempts)));
        }
    }

    private void finishPasswordChange(Player player, String password) {
        if (!validatePassword(player, password)) {
            return;
        }
        PlayerRecord record = getRecord(player);
        if (record == null) {
            player.sendMessage(message(player, "not-registered", "&c你还没有注册。请先使用 &e/register&c。"));
            sessions.remove(player.getUniqueId());
            return;
        }

        PasswordHash hash = hashPassword(password);
        record.passwordHash = hash.hash;
        record.passwordSalt = hash.salt;
        record.lastIp = getAddress(player);
        saveRecords();
        sessions.remove(player.getUniqueId());
        player.sendMessage(message(player, "password-changed", "&a密码修改成功。请使用 &e/login &a重新登录。"));
        showGatePrompt(player);
    }

    private void beginBindEmail(Player player, String email) {
        PlayerRecord record = getRecord(player);
        if (record == null) {
            player.sendMessage(message(player, "not-registered", "&c你还没有注册。请先使用 &e/register&c。"));
            return;
        }
        if (!checkEmailCooldown(player, email)) {
            return;
        }

        Session session = new Session(SessionMode.BIND_CODE);
        session.email = email;
        session.code = createCode();
        session.expiresAt = System.currentTimeMillis() + getCodeExpireMillis();
        sessions.put(player.getUniqueId(), session);

        sendCodeAsync(player, email, session.code, mailSubject(player, "bind-subject", "Pureblock 绑定邮箱验证码"));
        player.sendMessage(message(player, "bind-code-sent", "&b验证码已发送到 &e%email%&b。&7请输入验证码完成绑定。", "%email%", email));
        showGatePrompt(player);
    }

    private void finishBindEmail(Player player, Session session) {
        PlayerRecord record = getRecord(player);
        if (record == null) {
            player.sendMessage(message(player, "not-registered", "&c你还没有注册。请先使用 &e/register&c。"));
            sessions.remove(player.getUniqueId());
            return;
        }

        String oldEmail = record.email;
        record.email = session.email;
        saveRecords();
        sessions.remove(player.getUniqueId());
        verificationHoldUntil.remove(player.getUniqueId());
        sendConfiguredMessages(player, "state-messages.verification-success.messages");
        sendConfiguredMessages(player, "state-messages.bind-success.messages");
        player.sendMessage(message(player, "bind-success", "&a邮箱绑定成功。&7已从 &e%old% &7更新为 &b%new%&7。",
                "%old%", oldEmail == null ? "无" : oldEmail,
                "%new%", session.email == null ? "" : session.email));
        showGatePrompt(player);
    }

    private void showVerificationFailure(Player player, Session session, boolean expired) {
        verificationHoldUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
        String title = message(player, "verification-failed-title", "&c&l验证码失败");
        String subtitle = expired
                ? message(player, "verification-expired-subtitle", "&7验证码已过期，请重新开始流程。")
                : message(player, "verification-invalid-subtitle", "&7验证码不正确，请重新开始流程。");
        player.sendTitle(title, subtitle, 0, 40, 20);
        player.sendMessage(expired
                ? message(player, "verification-expired-chat", "&c验证码已过期，请重新开始流程。")
                : message(player, "verification-invalid-chat", "&c验证码不正确，请重新开始流程。"));
    }

    private boolean validatePassword(Player player, String password) {
        int minLength = getConfig().getInt("password-min-length", 8);
        if (password.length() < minLength) {
            player.sendMessage(message(player, "password-too-short", "&c密码太短，至少需要 &e%min% &c位。",
                    "%min%", String.valueOf(minLength)));
            return false;
        }
        return true;
    }

    private void startTransferCountdown(Player player) {
        UUID uuid = player.getUniqueId();
        transferring.add(uuid);
        int seconds = Math.max(1, getConfig().getInt("post-login-countdown-seconds", 3));
        String target = getTransferTargetName();

        new BukkitRunnable() {
            private int left = seconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    transferring.remove(uuid);
                    cancel();
                    return;
                }
                if (left <= 0) {
                    transferring.remove(uuid);
                    authenticateNow(player);
                    cancel();
                    return;
                }
                String title = getConfig().getString("messages.transfer-title", "&a&l身份验证完成");
                String subtitle = rawMessage(player, "transfer-subtitle", "&b%seconds% &7秒后传送至 &d%target%",
                        "%seconds%", String.valueOf(left),
                        "%target%", target);
                title = rawMessage(player, "transfer-title", title);
                player.sendTitle(color(title), color(subtitle), 0, 25, 0);
                left--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void authenticateNow(Player player) {
        authenticated.add(player.getUniqueId());
        showAllPlayers(player);
        transferToVerifiedDestination(player);
    }

    private boolean isVerified(Player player) {
        return authenticated.contains(player.getUniqueId()) || player.hasPermission("logingate.bypass");
    }

    private PlayerRecord getRecord(Player player) {
        return records.get(player.getName().toLowerCase(Locale.ROOT));
    }

    private boolean isLocked(Player player) {
        return getLockedUntil(player) > System.currentTimeMillis();
    }

    private void showLocked(Player player) {
        long seconds = getLockedSeconds(player);
        String subtitle = rawMessage(player, "locked-subtitle", "&7请等待 &e%seconds% &7秒",
                "%seconds%", String.valueOf(seconds));
        player.sendTitle(message(player, "locked-title", "&c&l验证锁定"), color(subtitle), 0, 30, 0);
    }

    private void showGatePrompt(Player player) {
        if (!player.isOnline() || isVerified(player) || transferring.contains(player.getUniqueId())) {
            return;
        }
        Long holdUntil = verificationHoldUntil.get(player.getUniqueId());
        if (holdUntil != null && holdUntil > System.currentTimeMillis()) {
            return;
        }
        verificationHoldUntil.remove(player.getUniqueId());
        Session session = sessions.get(player.getUniqueId());
        String title;
        String subtitle;
        if (isLocked(player)) {
            showLocked(player);
            return;
        }
        if (session == null) {
            title = rawMessage(player, "welcome-title", "&d&lPureblock &8| &b纯境方块");
            subtitle = getRecord(player) == null
                    ? rawMessage(player, "welcome-register-subtitle", "&7欢迎抵达身份验证大厅，输入 &e/register &7创建通行证")
                    : rawMessage(player, "welcome-login-subtitle", "&7欢迎回来，输入 &e/login &7进入主世界");
        } else {
            title = switch (session.mode) {
                case REGISTER_EMAIL -> rawMessage(player, "title-register-email", "&d&l邮箱注册");
                case REGISTER_CODE -> rawMessage(player, "title-register-code", "&b&l验证码确认");
                case REGISTER_PASSWORD -> rawMessage(player, "title-register-password", "&a&l设置密码");
                case LOGIN_PASSWORD -> rawMessage(player, "title-login-password", "&b&l身份登录");
                case CHANGE_CODE -> rawMessage(player, "title-change-code", "&e&l修改密码");
                case CHANGE_PASSWORD -> rawMessage(player, "title-change-password", "&a&l设置新密码");
                case BIND_EMAIL -> rawMessage(player, "title-bind-email", "&d&l绑定邮箱");
                case BIND_CODE -> rawMessage(player, "title-bind-code", "&b&l邮箱确认");
            };
            subtitle = switch (session.mode) {
                case REGISTER_EMAIL -> rawMessage(player, "subtitle-register-email", "&7请在聊天栏输入 &b邮箱地址");
                case REGISTER_CODE -> rawMessage(player, "subtitle-register-code", "&7请输入邮箱收到的 &e验证码");
                case REGISTER_PASSWORD -> rawMessage(player, "subtitle-register-password", "&7请设置至少 &e%min% &7位的登录密码",
                        "%min%", String.valueOf(getConfig().getInt("password-min-length", 8)));
                case LOGIN_PASSWORD -> rawMessage(player, "subtitle-login-password", "&7请输入你的 &e登录密码");
                case CHANGE_CODE -> rawMessage(player, "subtitle-change-code", "&7请输入邮箱收到的 &e改密验证码");
                case CHANGE_PASSWORD -> rawMessage(player, "subtitle-change-password", "&7请设置新的 &e登录密码");
                case BIND_EMAIL -> rawMessage(player, "subtitle-bind-email", "&7请在聊天栏输入新的 &b邮箱地址");
                case BIND_CODE -> rawMessage(player, "subtitle-bind-code", "&7请输入发送到新邮箱的 &e验证码");
            };
        }
        player.sendTitle(color(title), color(subtitle), 0, 50, 0);
    }

    private long getLockedSeconds(Player player) {
        return Math.max(0, (getLockedUntil(player) - System.currentTimeMillis() + 999) / 1000);
    }

    private long getLockedUntil(Player player) {
        long lockedUntil = 0L;
        Session session = sessions.get(player.getUniqueId());
        if (session != null) {
            lockedUntil = Math.max(lockedUntil, session.lockedUntil);
        }
        PlayerRecord record = getRecord(player);
        if (record != null) {
            lockedUntil = Math.max(lockedUntil, record.lockedUntil);
        }
        return lockedUntil;
    }

    private boolean checkCode(Session session, String input) {
        return session.code != null && session.code.equals(input.trim()) && session.expiresAt >= System.currentTimeMillis();
    }

    private String createCode() {
        int length = getConfig().getInt("verification-code-length", 6);
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    private long getCodeExpireMillis() {
        return getConfig().getInt("verification-expire-minutes", 10) * 60_000L;
    }

    private boolean checkEmailCooldown(Player player, String email) {
        int cooldownSeconds = getConfig().getInt("email-code-cooldown-seconds", 60);
        if (cooldownSeconds <= 0) {
            return true;
        }

        String key = email.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        long until = emailCooldowns.getOrDefault(key, 0L);
        if (until > now) {
            long left = Math.max(1L, (until - now + 999L) / 1000L);
            player.sendMessage(message(player, "email-cooldown",
                    "&c&l短时间内获取多次验证码，请等待 &a%seconds% &c&l秒后重新操作！",
                    "%seconds%", String.valueOf(left)));
            return false;
        }

        emailCooldowns.put(key, now + cooldownSeconds * 1000L);
        return true;
    }

    private void sendCodeAsync(Player player, String email, String code, String subject) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            boolean sent = false;
            String error = null;
            try {
                sent = sendEmail(email, subject, "欢迎来到 Pureblock | 纯境方块。\n\n你的验证码是：" + code + "\n验证码将在 "
                        + getConfig().getInt("verification-expire-minutes", 10) + " 分钟后过期。\n\n如果这不是你本人操作，请忽略这封邮件。");
            } catch (Exception ex) {
                error = ex.getMessage();
                getLogger().warning("Failed to send email to " + email + ": " + error);
            }

            final boolean finalSent = sent;
            final String finalError = error;
            Bukkit.getScheduler().runTask(this, () -> {
                if (finalSent) {
                    player.sendMessage(message(player, "mail-sent", "&a验证码邮件已发送，请查收邮箱。"));
                } else {
                    getLogger().warning("Email is not sent. Verification code for " + player.getName() + " is " + code);
                    player.sendMessage(message(player, "mail-failed", "&c邮件发送失败，请让管理员检查 SMTP 配置。测试验证码已写入后台日志。"));
                    if (finalError != null) {
                        player.sendMessage(color("&7错误: " + finalError));
                    }
                }
            });
        });
    }

    private boolean sendEmail(String to, String subject, String body) throws IOException {
        FileConfiguration config = getConfig();
        if (!config.getBoolean("smtp.enabled", false)) {
            return false;
        }

        String host = config.getString("smtp.host", "");
        int port = config.getInt("smtp.port", 465);
        boolean ssl = config.getBoolean("smtp.ssl", true);
        boolean startTls = config.getBoolean("smtp.starttls", false);
        String username = config.getString("smtp.username", "");
        String password = config.getString("smtp.password", "");
        String from = config.getString("smtp.from", username);
        String fromName = config.getString("smtp.from-name", "Pureblock");

        Socket socket = ssl ? SSLSocketFactory.getDefault().createSocket(host, port) : new Socket(host, port);

        try {
            SmtpClient smtp = new SmtpClient(socket);
            smtp.expect(220);
            smtp.command("EHLO localhost", 250);

            if (startTls && !ssl) {
                smtp.command("STARTTLS", 220);
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket tlsSocket = (SSLSocket) factory.createSocket(socket, host, port, true);
                tlsSocket.startHandshake();
                smtp = new SmtpClient(tlsSocket);
                smtp.command("EHLO localhost", 250);
            }

            if (username != null && !username.isBlank()) {
                smtp.command("AUTH LOGIN", 334);
                smtp.command(base64(username), 334);
                smtp.command(base64(password == null ? "" : password), 235);
            }

            smtp.command("MAIL FROM:<" + from + ">", 250);
            smtp.command("RCPT TO:<" + to + ">", 250);
            smtp.command("DATA", 354);
            smtp.writeData(buildMessage(from, fromName, to, subject, body));
            smtp.expect(250);
            smtp.command("QUIT", 221);
            return true;
        } finally {
            socket.close();
        }
    }

    private String buildMessage(String from, String fromName, String to, String subject, String body) {
        String encodedSubject = "=?UTF-8?B?" + base64(subject) + "?=";
        String encodedFromName = "=?UTF-8?B?" + base64(fromName) + "?=";
        String escapedBody = body.replace("\r\n", "\n").replace("\r", "\n").replace("\n.", "\n..");
        return "From: " + encodedFromName + " <" + from + ">\r\n"
                + "To: <" + to + ">\r\n"
                + "Subject: " + encodedSubject + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: 8bit\r\n"
                + "\r\n"
                + escapedBody
                + "\r\n.";
    }

    private void teleportToLogin(Player player) {
        World world = Bukkit.getWorld(getLoginWorldName());
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
        }
        player.teleport(world.getSpawnLocation().add(0.5, 0, 0.5));
    }

    private void teleportToMain(Player player) {
        World world = Bukkit.getWorld(getConfig().getString("main-world", "world"));
        if (world == null) {
            player.kickPlayer(message(player, "main-world-missing", "&c主世界不存在，无法完成登录。"));
            return;
        }
        player.teleport(world.getSpawnLocation().add(0.5, 0, 0.5));
    }

    private void transferToVerifiedDestination(Player player) {
        if (isProxyTransferEnabled()) {
            connectToProxyServer(player);
            return;
        }
        teleportToMain(player);
    }

    private boolean isProxyTransferEnabled() {
        boolean enabled = getConfig().getBoolean("multi-server.enabled", false);
        String mode = getConfig().getString("multi-server.transfer-mode", "local");
        return enabled && mode != null && !"local".equals(mode.toLowerCase(Locale.ROOT));
    }

    private String getTransferTargetName() {
        if (isProxyTransferEnabled()) {
            String targetServer = getConfig().getString("multi-server.target-server", "");
            if (targetServer != null && !targetServer.isBlank()) {
                return targetServer;
            }
        }
        return getConfig().getString("transfer-target-name", "Pureblock");
    }

    private void connectToProxyServer(Player player) {
        String targetServer = getConfig().getString("multi-server.target-server", getConfig().getString("transfer-target-name", "Pureblock"));
        if (targetServer == null || targetServer.isBlank()) {
            handleProxyTransferFailure(player, "target server is blank");
            return;
        }

        String channel = normalizeProxyChannel(getConfig().getString("multi-server.plugin-message-channel", LEGACY_PROXY_CHANNEL));
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeUTF("Connect");
            output.writeUTF(targetServer);
            player.sendPluginMessage(this, channel, buffer.toByteArray());
            player.sendMessage(message(player, "proxy-transfer-started",
                    "&a身份验证通过，正在连接至 &d%server%&a。",
                    "%server%", targetServer));
        } catch (IOException | IllegalArgumentException ex) {
            handleProxyTransferFailure(player, ex.getMessage());
        }
    }

    private void handleProxyTransferFailure(Player player, String reason) {
        getLogger().warning("Could not transfer " + player.getName() + " through proxy mode: " + reason);
        if (getConfig().getBoolean("multi-server.fallback-to-local-world", true)) {
            teleportToMain(player);
            return;
        }
        player.kickPlayer(message(player, "proxy-transfer-failed", "&c代理转服失败，请稍后重试。"));
    }

    private String normalizeProxyChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return LEGACY_PROXY_CHANNEL;
        }
        if ("velocity".equalsIgnoreCase(channel)) {
            return MODERN_PROXY_CHANNEL;
        }
        return channel;
    }

    private void setupProxyChannels() {
        registerOutgoingProxyChannel(LEGACY_PROXY_CHANNEL);
        registerOutgoingProxyChannel(MODERN_PROXY_CHANNEL);
    }

    private void registerOutgoingProxyChannel(String channel) {
        try {
            Bukkit.getMessenger().registerOutgoingPluginChannel(this, channel);
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Could not register proxy plugin message channel " + channel + ": " + ex.getMessage());
        }
    }

    private void setupWorlds() {
        String loginWorld = getLoginWorldName();
        if (Bukkit.getWorld(loginWorld) == null) {
            WorldCreator creator = new WorldCreator(loginWorld);
            creator.generateStructures(false);
            String terrain = getLoginTerrainType();
            if ("flat".equals(terrain)) {
                creator.type(WorldType.FLAT);
            } else if ("void".equals(terrain) || getConfig().getBoolean("login-world-settings.void-world", false)) {
                creator.generator(new VoidWorldGenerator());
            }
            Bukkit.createWorld(creator);
        }
    }

    private void applyLoginWorldRules() {
        World world = Bukkit.getWorld(getLoginWorldName());
        if (world == null) {
            return;
        }
        if (getConfig().getBoolean("login-world-settings.disable-weather-cycle", true)) {
            world.setStorm(false);
            world.setThundering(false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        }
        if (getConfig().getBoolean("login-world-settings.lock-time", true)) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setTime(getConfig().getLong("login-world-settings.time", 18000L));
        }
        if (getConfig().getBoolean("login-world-settings.disable-monster-spawn", true)) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.getLivingEntities().stream()
                    .filter(entity -> entity instanceof Monster)
                    .forEach(entity -> entity.remove());
        }
    }

    private void prepareLoginPlayer(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        if (getConfig().getBoolean("login-world-settings.hide-other-players", true)) {
            hideOtherPlayers(player);
        }
    }

    private void hideOtherPlayers(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                player.hidePlayer(this, other);
                other.hidePlayer(this, player);
            }
        }
    }

    private void showAllPlayers(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            player.showPlayer(this, other);
            other.showPlayer(this, player);
        }
    }

    private boolean isInLoginWorld(Player player) {
        return isLoginWorld(player.getWorld());
    }

    private boolean isLoginWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(getLoginWorldName());
    }

    private String getLoginWorldName() {
        String configured = getConfig().getString("loginworld", getConfig().getString("login-world", ""));
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return "login_" + getLoginTerrainType();
    }

    private String getLoginTerrainType() {
        String terrain = getConfig().getString("login-world-generation.type", "");
        if (terrain == null || terrain.isBlank()) {
            terrain = getConfig().getBoolean("login-world-settings.void-world", true) ? "void" : "normal";
        }
        terrain = terrain.toLowerCase(Locale.ROOT).trim();
        if ("superflat".equals(terrain) || "flatland".equals(terrain)) {
            terrain = "flat";
        }
        if (!terrain.equals("void") && !terrain.equals("flat") && !terrain.equals("normal")) {
            terrain = "void";
        }
        return terrain;
    }

    private void setupLanguages() {
        saveLanguageResource("zh");
        saveLanguageResource("en");
        languageConfigs.clear();

        File langFolder = new File(getDataFolder(), "lang");
        File[] files = langFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            String language = normalizeLanguage(name.substring(0, name.length() - 4));
            languageConfigs.put(language, YamlConfiguration.loadConfiguration(file));
        }
    }

    private void saveLanguageResource(String language) {
        File file = new File(getDataFolder(), "lang/" + language + ".yml");
        if (!file.exists()) {
            saveResource("lang/" + language + ".yml", false);
        }
    }

    private void setupRecords() {
        File dataFolder = new File(getDataFolder(), "PlayerInfo");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getLogger().warning("Could not create PlayerInfo folder.");
        }
        recordsFile = new File(dataFolder, "players.yml");
        recordsConfig = YamlConfiguration.loadConfiguration(recordsFile);
    }

    private void loadRecords() {
        records.clear();
        if (!recordsConfig.isConfigurationSection("players")) {
            return;
        }
        for (String key : recordsConfig.getConfigurationSection("players").getKeys(false)) {
            String path = "players." + key + ".";
            PlayerRecord record = new PlayerRecord();
            record.gameName = recordsConfig.getString(path + "gameName", key);
            record.email = recordsConfig.getString(path + "email", "");
            record.passwordHash = recordsConfig.getString(path + "passwordHash", "");
            record.passwordSalt = recordsConfig.getString(path + "passwordSalt", "");
            record.registeredAt = recordsConfig.getString(path + "registeredAt", "");
            record.lastLoginAt = recordsConfig.getString(path + "lastLoginAt", "");
            record.lastIp = recordsConfig.getString(path + "ip", "");
            record.generatedUuid = recordsConfig.getString(path + "generatedUuid", "");
            record.lockedUntil = recordsConfig.getLong(path + "lockedUntil", 0L);
            record.language = normalizeLanguage(recordsConfig.getString(path + "language", getDefaultLanguage()));
            records.put(key.toLowerCase(Locale.ROOT), record);
        }
    }

    private void saveRecords() {
        recordsConfig.set("players", null);
        for (Map.Entry<String, PlayerRecord> entry : records.entrySet()) {
            PlayerRecord record = entry.getValue();
            String path = "players." + entry.getKey() + ".";
            recordsConfig.set(path + "email", record.email);
            recordsConfig.set(path + "gameName", record.gameName);
            recordsConfig.set(path + "passwordHash", record.passwordHash);
            recordsConfig.set(path + "passwordSalt", record.passwordSalt);
            recordsConfig.set(path + "registeredAt", record.registeredAt);
            recordsConfig.set(path + "lastLoginAt", record.lastLoginAt);
            recordsConfig.set(path + "ip", record.lastIp);
            recordsConfig.set(path + "generatedUuid", record.generatedUuid);
            recordsConfig.set(path + "lockedUntil", record.lockedUntil);
            recordsConfig.set(path + "language", normalizeLanguage(record.language));
        }
        try {
            recordsConfig.save(recordsFile);
        } catch (IOException ex) {
            getLogger().severe("Could not save PlayerInfo: " + ex.getMessage());
        }
    }

    private void bindCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(this);
        }
    }

    private void startPersistentTitleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isVerified(player) && isInLoginWorld(player)) {
                        Long holdUntil = verificationHoldUntil.get(player.getUniqueId());
                        if (holdUntil != null && holdUntil > System.currentTimeMillis()) {
                            continue;
                        }
                        showGatePrompt(player);
                    }
                }
            }
        }.runTaskTimer(this, 20L, 40L);
    }

    private void startLoginWorldRuleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                applyLoginWorldRules();
            }
        }.runTaskTimer(this, 40L, 200L);
    }

    private PasswordHash hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            byte[] hash = pbkdf2(password, salt);
            return new PasswordHash(base64(hash), base64(salt));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash password", ex);
        }
    }

    private boolean verifyPassword(String password, String saltBase64, String hashBase64) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            byte[] expected = Base64.getDecoder().decode(hashBase64);
            byte[] actual = pbkdf2(password, salt);
            if (actual.length != expected.length) {
                return false;
            }
            int diff = 0;
            for (int i = 0; i < actual.length; i++) {
                diff |= actual[i] ^ expected[i];
            }
            return diff == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private byte[] pbkdf2(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120_000, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    private String getAddress(Player player) {
        return player.getAddress() == null ? "" : player.getAddress().getAddress().getHostAddress();
    }

    private String formatStoredTime(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return DISPLAY_TIME.format(ZonedDateTime.parse(stored));
        } catch (DateTimeParseException ex) {
            return stored;
        }
    }

    private void sendConfiguredMessages(Player player, String path) {
        String enabledPath = path.endsWith(".messages")
                ? path.substring(0, path.length() - ".messages".length()) + ".enabled"
                : path + ".enabled";
        if (!getConfig().getBoolean(enabledPath, false)) {
            return;
        }
        FileConfiguration language = languageConfigs.get(getPlayerLanguage(player));
        java.util.List<String> messages = language != null && language.isList(path)
                ? language.getStringList(path)
                : getConfig().getStringList(path);
        for (String raw : messages) {
            player.sendMessage(color(applyPlaceholders(player, raw)));
        }
    }

    private String applyPlaceholders(Player player, String text) {
        return (text == null ? "" : text)
                .replace("%player%", player.getName())
                .replace("%server%", getConfig().getString("server-name", "Pureblock | 纯境方块"))
                .replace("%time%", DISPLAY_TIME.format(Instant.now()));
    }

    private String message(Player player, String key, String fallback, String... replacements) {
        String text = localizedRaw(player, key, fallback);
        text = applyPlaceholders(player, text);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return color(text);
    }

    private String rawMessage(Player player, String key, String fallback, String... replacements) {
        String text = localizedRaw(player, key, fallback);
        text = applyPlaceholders(player, text);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    private String mailSubject(Player player, String key, String fallback) {
        FileConfiguration language = languageConfigs.get(getPlayerLanguage(player));
        if (language != null && language.isString("mail." + key)) {
            return language.getString("mail." + key, fallback);
        }
        FileConfiguration defaultLanguage = languageConfigs.get(getDefaultLanguage());
        if (defaultLanguage != null && defaultLanguage.isString("mail." + key)) {
            return defaultLanguage.getString("mail." + key, fallback);
        }
        return getConfig().getString("mail." + key, fallback);
    }

    private String localizedRaw(Player player, String key, String fallback) {
        FileConfiguration language = languageConfigs.get(getPlayerLanguage(player));
        if (language != null && language.isString("messages." + key)) {
            return language.getString("messages." + key, fallback);
        }
        FileConfiguration defaultLanguage = languageConfigs.get(getDefaultLanguage());
        if (defaultLanguage != null && defaultLanguage.isString("messages." + key)) {
            return defaultLanguage.getString("messages." + key, fallback);
        }
        return getConfig().getString("messages." + key, fallback);
    }

    private String getPlayerLanguage(Player player) {
        String language = languagePreferences.get(player.getUniqueId());
        if (language != null) {
            return normalizeLanguage(language);
        }
        PlayerRecord record = getRecord(player);
        if (record != null && record.language != null && !record.language.isBlank()) {
            return normalizeLanguage(record.language);
        }
        return getDefaultLanguage();
    }

    private String getDefaultLanguage() {
        String language = normalizeLanguage(getConfig().getString("default-language", "zh"));
        return languageConfigs.containsKey(language) ? language : "zh";
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "zh";
        }
        return language.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String base64(String text) {
        return base64(text.getBytes(StandardCharsets.UTF_8));
    }

    private String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private enum SessionMode {
        REGISTER_EMAIL,
        REGISTER_CODE,
        REGISTER_PASSWORD,
        LOGIN_PASSWORD,
        CHANGE_CODE,
        CHANGE_PASSWORD,
        BIND_EMAIL,
        BIND_CODE
    }

    private static final class Session {
        private SessionMode mode;
        private String email;
        private String code;
        private long expiresAt;
        private int failedAttempts;
        private long lockedUntil;

        private Session(SessionMode mode) {
            this.mode = mode;
        }
    }

    private static final class PlayerRecord {
        private String email;
        private String gameName;
        private String passwordHash;
        private String passwordSalt;
        private String registeredAt;
        private String lastLoginAt;
        private String lastIp;
        private String generatedUuid;
        private long lockedUntil;
        private String language;
    }

    private static final class UpdateInfo {
        private String version;
        private String url;
        private String body;
    }

    private static final class PasswordHash {
        private final String hash;
        private final String salt;

        private PasswordHash(String hash, String salt) {
            this.hash = hash;
            this.salt = salt;
        }
    }

    private static final class SmtpClient {
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private SmtpClient(Socket socket) throws IOException {
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        private void command(String command, int expectedCode) throws IOException {
            writer.write(command + "\r\n");
            writer.flush();
            expect(expectedCode);
        }

        private void writeData(String data) throws IOException {
            writer.write(data + "\r\n");
            writer.flush();
        }

        private void expect(int expectedCode) throws IOException {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("SMTP server closed connection");
            }
            String last = line;
            while (line.length() >= 4 && line.charAt(3) == '-') {
                line = reader.readLine();
                if (line == null) {
                    throw new IOException("SMTP server closed connection");
                }
                last = line;
            }
            if (!last.startsWith(String.valueOf(expectedCode))) {
                throw new IOException("Unexpected SMTP response, expected " + expectedCode + ": " + last);
            }
        }
    }

    private static final class VoidWorldGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
            return createChunkData(world);
        }
    }
}
