package io.github.aw1y2z.sesame.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.github.aw1y2z.sesame.data.ViewAppInfo;

/**
 * 完整备份 / 恢复。
 *
 * <p>背景：原先的「导出」只备份当前账号的 {@code config_v2.json}（即模块配置），
 * 全局设置（界面风格、深色模式、日志开关、语言）、其它账号的配置、以及需要用户
 * 长期"养"出来的状态（自动拉黑的学习结果、令牌配置）都不在内 —— 换机导入后
 * 设置页会整片回到默认，用户以为的"完整备份"其实只覆盖了一小块。
 *
 * <p>这里把 {@code sesame-M} 目录下**除日志与历史备份之外**的全部文件打成一个 zip，
 * 恢复时按原目录结构还原，从而做到"换手机能整份搬过去"。
 *
 * <h3>排除项</h3>
 * <ul>
 *   <li>{@code log/}：按天滚动的运行日志，体积大、恢复无意义；</li>
 *   <li>{@code bak/}：模块自身轮转出来的 {@code config_v2} 历史副本，属于派生物
 *       （同时它也是"恢复前自动快照"的存放处，必须排除，否则会自我嵌套）。</li>
 * </ul>
 *
 * <h3>恢复的安全约定</h3>
 * <ol>
 *   <li>先校验包内清单，格式不符直接拒绝（避免误选普通 zip 把目录解乱）；</li>
 *   <li>条目路径逐段清洗，绝对路径 / {@code ..} / 排除目录一律丢弃（防 Zip-Slip）；</li>
 *   <li>真正落盘前先把当前配置整份快照到 {@code bak/restore-<时间>/}，只保留最近一份 ——
 *       导错了还能退回去；</li>
 *   <li>只覆盖、不删除：包里没有的本地文件保持原样（合并语义），
 *       多账号场景下不会因为导入一份单账号备份而抹掉别的账号。</li>
 * </ol>
 */
public final class BackupUtil {

    private static final String TAG = BackupUtil.class.getSimpleName();

    /** 备份包格式标识：写在包内清单里，恢复前先校验 */
    public static final String FORMAT = "sesame-M-backup";
    /** 格式版本：将来结构变化时用于兼容判断 */
    public static final int FORMAT_VERSION = 1;
    /** 清单在包内的条目名 */
    public static final String MANIFEST_ENTRY = "sesame-backup.json";

    /** 不纳入备份的子目录，理由见类注释 */
    private static final String[] EXCLUDED_DIRS = {"log", "bak"};

    /** 恢复前自动快照的目录前缀（落在 bak/ 下，天然不参与备份） */
    public static final String PRE_RESTORE_PREFIX = "restore-";

    /** 落盘中间态后缀：先写临时文件再改名，避免写一半被中断留下半个配置 */
    private static final String TMP_SUFFIX = ".restoring";

    private static final int BUFFER_SIZE = 8 * 1024;

    private BackupUtil() {
    }

    /* ───────────────────────── 结果模型 ───────────────────────── */

    /** 导出 / 恢复的结果 */
    public static final class Result {
        public final boolean ok;
        public final int fileCount;
        public final long bytes;
        public final String message;

        private Result(boolean ok, int fileCount, long bytes, String message) {
            this.ok = ok;
            this.fileCount = fileCount;
            this.bytes = bytes;
            this.message = message;
        }

        static Result ok(int fileCount, long bytes, String message) {
            return new Result(true, fileCount, bytes, message);
        }

        static Result fail(String message) {
            return new Result(false, 0, 0, message);
        }
    }

    /** 备份包的自述信息，用于「恢复前先让用户看一眼」 */
    public static final class Info {
        public final boolean ok;
        public final String message;
        public final String createdAt;
        public final String appVersion;
        public final int fileCount;
        public final List<String> accountDirs;

        private Info(boolean ok, String message, String createdAt, String appVersion,
                     int fileCount, List<String> accountDirs) {
            this.ok = ok;
            this.message = message;
            this.createdAt = createdAt;
            this.appVersion = appVersion;
            this.fileCount = fileCount;
            this.accountDirs = accountDirs;
        }

        static Info fail(String message) {
            return new Info(false, message, null, null, 0, Collections.emptyList());
        }
    }

    /* ───────────────────────── 导出 ───────────────────────── */

    /**
     * 把全部配置类文件导出为 zip 写到 [out]。调用方负责关闭流。
     */
    public static Result export(OutputStream out) {
        File root = FileUtil.MAIN_DIRECTORY_FILE;
        if (root == null || !root.isDirectory()) {
            return Result.fail("没有找到配置目录");
        }
        List<File> files = collectFiles(root);
        if (files.isEmpty()) {
            return Result.fail("没有可备份的配置");
        }
        long bytes = 0;
        int written = 0;
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(out))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);

            // 清单放最前：即便将来改成流式读取也能第一时间判格式
            byte[] manifest = buildManifest(files.size(), accountsOf(root)).getBytes(StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            zos.write(manifest);
            zos.closeEntry();

            byte[] buf = new byte[BUFFER_SIZE];
            for (File f : files) {
                String name = relativeName(root, f);
                if (name == null) {
                    continue;
                }
                zos.putNextEntry(new ZipEntry(name));
                try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        zos.write(buf, 0, n);
                        bytes += n;
                    }
                } catch (IOException e) {
                    // 单个文件读失败（例如正被注入进程改写）不应让整包作废
                    Log.printStackTrace(TAG, e);
                }
                zos.closeEntry();
                written++;
            }
            zos.finish();
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return Result.fail("导出失败：" + t.getClass().getSimpleName());
        }
        Log.record("完整备份导出#文件数:" + written + "#大小:" + bytes + "字节");
        return Result.ok(written, bytes, "已导出 " + written + " 个配置文件");
    }

    /* ───────────────────────── 预检 ───────────────────────── */

    /**
     * 只读地看一眼备份包，取出清单信息（不改动任何文件）。
     */
    public static Info inspect(File zipFile) {
        if (zipFile == null || !zipFile.isFile()) {
            return Info.fail("备份文件不存在");
        }
        try (ZipFile zf = new ZipFile(zipFile)) {
            ZipEntry entry = zf.getEntry(MANIFEST_ENTRY);
            if (entry == null) {
                return Info.fail("这不是 Sesame-M 的备份包（缺少清单）");
            }
            String json;
            try (InputStream in = zf.getInputStream(entry)) {
                json = readAll(in);
            }
            Map<?, ?> map = JsonUtil.parseObject(json, Map.class);
            if (map == null) {
                return Info.fail("备份包清单无法解析");
            }
            Object format = map.get("format");
            if (format == null || !FORMAT.equals(String.valueOf(format))) {
                return Info.fail("这不是 Sesame-M 的备份包");
            }
            Object version = map.get("version");
            int v = version instanceof Number ? ((Number) version).intValue() : 0;
            if (v > FORMAT_VERSION) {
                return Info.fail("备份包版本（v" + v + "）比当前版本新，请先升级再恢复");
            }
            List<String> accountDirs = new ArrayList<>();
            Object accounts = map.get("accountDirs");
            if (accounts instanceof Iterable) {
                for (Object o : (Iterable<?>) accounts) {
                    if (o != null) {
                        accountDirs.add(String.valueOf(o));
                    }
                }
            }
            return new Info(
                    true,
                    "OK",
                    str(map.get("createdAt")),
                    str(map.get("appVersion")),
                    map.get("fileCount") instanceof Number ? ((Number) map.get("fileCount")).intValue() : 0,
                    accountDirs
            );
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return Info.fail("备份包无法读取，可能已损坏");
        }
    }

    /** 判断一个文件是不是 zip（用于「导入」时自动分辨 完整备份 / 单账号配置） */
    public static boolean looksLikeZip(File file) {
        if (file == null || !file.isFile() || file.length() < 4) {
            return false;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] head = new byte[4];
            if (in.read(head) != 4) {
                return false;
            }
            // PK\x03\x04 = 本地文件头；PK\x05\x06 = 空包
            return head[0] == 0x50 && head[1] == 0x4B
                    && (head[2] == 0x03 || head[2] == 0x05);
        } catch (IOException e) {
            return false;
        }
    }

    /* ───────────────────────── 恢复 ───────────────────────── */

    /**
     * 用 [zipFile] 覆盖还原全部配置。
     *
     * <p>调用方在成功后需要自行刷新内存态（{@code AppConfig.load()} /
     * {@code Model.initAllModel()} / {@code ConfigPreload.prepare()}）并通知注入进程重载。
     */
    public static Result restore(File zipFile) {
        File root = FileUtil.MAIN_DIRECTORY_FILE;
        if (root == null) {
            return Result.fail("没有找到配置目录");
        }
        Info info = inspect(zipFile);
        if (!info.ok) {
            return Result.fail(info.message);
        }
        if (!root.exists() && !root.mkdirs()) {
            return Result.fail("无法创建配置目录");
        }

        // 先给当前配置留一份快照：导入本来就是"覆盖"，留个后悔药
        boolean snapshotOk = snapshotBeforeRestore(root);
        if (!snapshotOk) {
            return Result.fail("恢复中止：无法为当前配置创建快照");
        }

        int restored = 0;
        long bytes = 0;
        try (ZipFile zf = new ZipFile(zipFile)) {
            byte[] buf = new byte[BUFFER_SIZE];
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String safeName = safeEntryName(entry.getName());
                if (safeName == null) {
                    continue;
                }
                File target = new File(root, safeName);
                try (InputStream in = zf.getInputStream(entry)) {
                    bytes += writeAtomically(in, target, buf);
                } catch (IOException e) {
                    Log.printStackTrace(TAG, e);
                }
                restored++;
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return Result.fail("恢复失败：" + t.getClass().getSimpleName());
        }
        Log.record("完整备份恢复#文件数:" + restored + "#大小:" + bytes + "字节");
        return Result.ok(restored, bytes, "已恢复 " + restored + " 个配置文件");
    }

    /**
     * 把当前配置整份拷到 {@code bak/restore-<时间>/}，只保留最近一份（旧的先删掉，
     * 避免 bak 目录无限膨胀）。
     *
     * @return 是否成功建立快照；失败时调用方应中止恢复 —— 没有后悔药的覆盖不能做。
     */
    private static boolean snapshotBeforeRestore(File root) {
        try {
            File bakDir = new File(root, FileUtil.BACKUP_DIR_NAME);
            if (!bakDir.exists() && !bakDir.mkdirs()) {
                return false;
            }
            File[] olds = bakDir.listFiles((dir, name) -> name.startsWith(PRE_RESTORE_PREFIX));
            if (olds != null) {
                for (File old : olds) {
                    deleteRecursively(old);
                }
            }
            File dest = new File(bakDir, PRE_RESTORE_PREFIX + fileStamp());
            List<File> files = collectFiles(root);
            byte[] buf = new byte[BUFFER_SIZE];
            for (File f : files) {
                String rel = relativeName(root, f);
                if (rel == null) {
                    continue;
                }
                File target = new File(dest, rel);
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    return false;
                }
                try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
                    writeAtomically(in, target, buf);
                }
            }
            Log.record("恢复前快照已建立#" + dest.getPath());
            return true;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    /** 写临时文件再改名：中途失败也不会在目标位置留下半个配置 */
    private static long writeAtomically(InputStream in, File target, byte[] buf) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File tmp = new File(target.getAbsolutePath() + TMP_SUFFIX);
        long total = 0;
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp))) {
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                total += n;
            }
        }
        if (target.exists() && !target.delete()) {
            // 删不掉就退化为直接覆盖，不影响最终内容
            Log.i(TAG, "目标文件无法删除，改为直接覆盖：" + target.getName());
        }
        if (!tmp.renameTo(target)) {
            try (InputStream src = new BufferedInputStream(new FileInputStream(tmp));
                 OutputStream dst = new BufferedOutputStream(new FileOutputStream(target))) {
                int n;
                while ((n = src.read(buf)) > 0) {
                    dst.write(buf, 0, n);
                }
            } finally {
                if (!tmp.delete()) {
                    Log.i(TAG, "临时文件清理失败：" + tmp.getName());
                }
            }
        }
        return total;
    }

    /* ───────────────────────── 路径处理 ───────────────────────── */

    /** 递归收集要备份的文件（排除 log / bak / 临时文件） */
    private static List<File> collectFiles(File root) {
        List<File> out = new ArrayList<>();
        walk(root, root, out, 0);
        return out;
    }

    private static void walk(File root, File dir, List<File> out, int depth) {
        // 目录层级兜底，防止异常结构把递归拖死
        if (depth > 8) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                if (isExcludedDir(root, child)) {
                    continue;
                }
                walk(root, child, out, depth + 1);
            } else if (child.isFile() && isBackupable(child.getName())) {
                out.add(child);
            }
        }
    }

    private static boolean isExcludedDir(File root, File dir) {
        String rel = relativeName(root, dir);
        if (rel == null) {
            return true;
        }
        for (String excluded : EXCLUDED_DIRS) {
            if (excluded.equals(rel)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBackupable(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return !name.endsWith(TMP_SUFFIX) && !name.endsWith(".tmp") && !name.endsWith(".part");
    }

    /** 相对根目录的 zip 条目名（统一用 `/`），不在根目录下时返回 null */
    private static String relativeName(File root, File file) {
        try {
            String rootPath = root.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.equals(rootPath) || !filePath.startsWith(rootPath + File.separator)) {
                return null;
            }
            return filePath.substring(rootPath.length() + 1).replace(File.separatorChar, '/');
        } catch (IOException e) {
            Log.printStackTrace(TAG, e);
            return null;
        }
    }

    /**
     * 清洗 zip 条目名，得到一个「可以安全拼在配置目录下」的相对路径。
     *
     * <p>拒绝的情况：空、绝对路径、含 {@code ..} 的跳级、排除目录（log / bak）、
     * 清单本身。这种包可能来自别人，必须当成不可信输入。
     */
    private static String safeEntryName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.replace('\\', '/').trim();
        if (name.isEmpty() || name.startsWith("/") || name.indexOf('\0') >= 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String seg : name.split("/")) {
            if (seg.isEmpty() || ".".equals(seg)) {
                continue;
            }
            if ("..".equals(seg)) {
                return null;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(seg);
        }
        if (sb.length() == 0) {
            return null;
        }
        String safe = sb.toString();
        if (MANIFEST_ENTRY.equals(safe)) {
            return null;
        }
        int slash = safe.indexOf('/');
        String top = slash < 0 ? safe : safe.substring(0, slash);
        for (String excluded : EXCLUDED_DIRS) {
            if (excluded.equals(top)) {
                return null;
            }
        }
        return safe;
    }

    /* ───────────────────────── 清单 ───────────────────────── */

    private static String buildManifest(int fileCount, List<String> accountDirs) {
        Map<String, Object> map = new HashMap<>();
        map.put("format", FORMAT);
        map.put("version", FORMAT_VERSION);
        map.put("createdAt", dateStamp());
        map.put("appVersion", appVersion());
        map.put("fileCount", fileCount);
        map.put("accountDirs", accountDirs);
        String json;
        try {
            json = JsonUtil.toFormatJsonString(map);
        } catch (Throwable t) {
            json = null;
        }
        if (json == null) {
            json = "{\"format\":\"" + FORMAT + "\",\"version\":" + FORMAT_VERSION
                    + ",\"fileCount\":" + fileCount + "}";
        }
        return json;
    }

    /** `config/` 下的账号目录名（多账号备份时用来说明"这份包里带了几个账号"） */
    private static List<String> accountsOf(File root) {
        List<String> out = new ArrayList<>();
        File configDir = new File(root, "config");
        File[] children = configDir.listFiles();
        if (children == null) {
            return out;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                out.add(child.getName());
            }
        }
        return out;
    }

    /* ───────────────────────── 小工具 ───────────────────────── */

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[BUFFER_SIZE];
        int n;
        while ((n = in.read(buf)) > 0) {
            sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            Log.i(TAG, "删除失败：" + file.getPath());
        }
    }

    private static String dateStamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private static String fileStamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(new Date());
    }

    private static String appVersion() {
        try {
            String v = ViewAppInfo.getAppVersion();
            return v == null ? "" : v;
        } catch (Throwable t) {
            return "";
        }
    }
}
