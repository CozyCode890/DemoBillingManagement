// =====================================================================
//  WindowsLauncher.cs -- mã nguồn của Setup.exe (launcher 1-click Windows)
// ---------------------------------------------------------------------
//  Biên dịch:  powershell -ExecutionPolicy Bypass -File scripts\ps1\build-launcher.ps1
//
//  Setup.exe chỉ lo đúng một việc: có JDK 21 để chạy SetupApp.jar. Mọi việc
//  còn lại (thư viện, MySQL, database, biên dịch, mở app) do SetupApp.jar làm.
//
//    1. Tìm JDK >= 21 (phải có javac.exe -- SetupApp biên dịch app billing):
//       JDK portable của chính công cụ, JAVA_HOME, PATH, registry, thư mục
//       cài của các hãng.
//    2. Không có thì TỰ TẢI Temurin 21 bản ZIP (~190 MB) về
//       <home>\jdk, có cửa sổ tiến độ và nút Huỷ. Không cần winget (Windows
//       LTSC không có), không cần quyền Administrator, không đụng PATH hệ thống.
//    3. Chép SetupApp.jar sang <home>\app rồi chạy bản chép đó. Lý do: Java
//       khoá file jar đang chạy trên Windows, nên nếu chạy thẳng file trong repo
//       thì "git switch" sang nhánh có SetupApp.jar khác sẽ báo
//       "unable to unlink 'SetupApp.jar'". Thư mục repo truyền qua
//       -Drb.project.root.
//
//  <home> = %RETAIL_BILLING_HOME% nếu có, không thì %USERPROFILE%\.retail-billing;
//  nếu đường dẫn đó có ký tự ngoài ASCII (tên tài khoản có dấu) thì dùng
//  %ProgramData%\RetailBilling. Quy tắc này PHẢI giống hệt
//  envsetup.core.DevHome trong SetupApp.
//
//  Lưu ý khi sửa file: csc.exe của .NET Framework chỉ hiểu C# 5.0 --
//  không dùng string interpolation ($"..."), toán tử ?. hay nameof.
// =====================================================================

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

internal static class SetupLauncher
{
    /// <summary>SetupApp.jar được biên dịch với --release 21 nên cần Java >= 21.</summary>
    private const int MinJavaMajor = 21;

    private const string Title = "Retail Billing Setup";
    private const string DownloadPage = "https://adoptium.net/temurin/releases/?version=21&os=windows";

    [STAThread]
    private static int Main(string[] args)
    {
        try
        {
            return Run(args);
        }
        catch (Exception ex)
        {
            Alert("Lỗi không mong đợi khi khởi động Setup App.", ex.ToString(), IconError);
            return 1;
        }
    }

    private static int Run(string[] args)
    {
        string exeDir = GetExeDir();
        string root = ResolveProjectRoot(exeDir);

        string jar = Path.Combine(root, "SetupApp.jar");
        if (!File.Exists(jar))
        {
            jar = Path.Combine(exeDir, "SetupApp.jar");
        }
        if (!File.Exists(jar))
        {
            Alert("Không tìm thấy SetupApp.jar.",
                "Setup.exe phải nằm trong thư mục gốc của repo, cạnh SetupApp.jar và config.properties.\n\n"
                    + "Đã tìm trong:\n  " + root + "\n  " + exeDir,
                IconError);
            return 1;
        }

        string home = ResolveHome();
        JavaRuntime java = FindBestJdk(home);
        if (java == null)
        {
            java = DownloadJdk(home);
            if (java == null)
            {
                return 1;
            }
        }

        Launch(java, ShadowCopy(jar, home), root, args);
        return 0;
    }

    // ------------------------------------------------------------------
    //  Thư mục
    // ------------------------------------------------------------------

    private static string GetExeDir()
    {
        using (Process me = Process.GetCurrentProcess())
        {
            return Path.GetDirectoryName(me.MainModule.FileName);
        }
    }

    /// <summary>
    /// Đi ngược lên tối đa 5 cấp tìm thư mục có config.properties hoặc SetupApp.jar --
    /// giống envsetup.core.Project để hai bên luôn ra cùng kết quả.
    /// </summary>
    private static string ResolveProjectRoot(string exeDir)
    {
        DirectoryInfo dir = new DirectoryInfo(exeDir);
        for (int i = 0; dir != null && i < 5; i++)
        {
            if (File.Exists(Path.Combine(dir.FullName, "config.properties"))
                || File.Exists(Path.Combine(dir.FullName, "SetupApp.jar")))
            {
                return dir.FullName;
            }
            dir = dir.Parent;
        }
        return exeDir;
    }

    private static string ResolveHome()
    {
        string over = Environment.GetEnvironmentVariable("RETAIL_BILLING_HOME");
        if (!string.IsNullOrEmpty(over) && over.Trim().Length > 0)
        {
            return Path.GetFullPath(over.Trim());
        }
        string home = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".retail-billing");
        if (!IsAscii(home))
        {
            string programData = Environment.GetEnvironmentVariable("ProgramData");
            if (!string.IsNullOrEmpty(programData))
            {
                home = Path.Combine(programData, "RetailBilling");
            }
        }
        return home;
    }

    private static bool IsAscii(string s)
    {
        foreach (char c in s)
        {
            if (c > 0x7E)
            {
                return false;
            }
        }
        return true;
    }

    /// <summary>Chép jar sang home\app (tên theo SHA-1 nội dung) để file trong repo không bị khoá.</summary>
    private static string ShadowCopy(string jar, string home)
    {
        try
        {
            string dir = Path.Combine(home, "app");
            Directory.CreateDirectory(dir);
            string target = Path.Combine(dir, "SetupApp-" + Sha1Hex(jar).Substring(0, 12) + ".jar");
            if (!File.Exists(target))
            {
                string tmp = target + "." + Process.GetCurrentProcess().Id + ".tmp";
                File.Copy(jar, tmp, true);
                if (!File.Exists(target))
                {
                    File.Move(tmp, target);
                }
                else
                {
                    File.Delete(tmp);
                }
            }
            // Dọn các bản cũ; bản nào đang chạy thì Windows không cho xoá -- bỏ qua.
            foreach (string old in Directory.GetFiles(dir, "SetupApp-*.jar"))
            {
                if (!string.Equals(old, target, StringComparison.OrdinalIgnoreCase))
                {
                    try { File.Delete(old); } catch (Exception) { }
                }
            }
            return target;
        }
        catch (Exception)
        {
            return jar;     // không chép được thì chạy thẳng bản trong repo
        }
    }

    private static string Sha1Hex(string file)
    {
        using (SHA1 sha = SHA1.Create())
        using (FileStream fs = File.OpenRead(file))
        {
            StringBuilder sb = new StringBuilder();
            foreach (byte b in sha.ComputeHash(fs))
            {
                sb.Append(b.ToString("x2"));
            }
            return sb.ToString();
        }
    }

    // ------------------------------------------------------------------
    //  Dò JDK trên máy
    // ------------------------------------------------------------------

    /// <summary>Một bản Java tìm thấy trên máy.</summary>
    private sealed class JavaRuntime
    {
        public string JavawPath;
        public string Home;
        public int Major;
        public bool IsJdk;
    }

    /// <summary>JDK >= 21 mới nhất trên máy (bản portable của công cụ được ưu tiên khi bằng nhau).</summary>
    private static JavaRuntime FindBestJdk(string home)
    {
        List<string> candidates = new List<string>();
        candidates.Add(Path.Combine(home, "jdk\\bin\\javaw.exe"));
        AddFromEnvironment(candidates);
        AddFromPath(candidates);
        AddFromRegistry(candidates);
        AddFromVendorDirs(candidates);

        HashSet<string> seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        JavaRuntime best = null;
        foreach (string javaw in candidates)
        {
            if (string.IsNullOrEmpty(javaw) || !seen.Add(javaw))
            {
                continue;
            }
            JavaRuntime found = Inspect(javaw);
            if (found == null || !found.IsJdk || found.Major < MinJavaMajor)
            {
                continue;
            }
            if (best == null || found.Major > best.Major)
            {
                best = found;
            }
        }
        return best;
    }

    private static void AddFromEnvironment(List<string> list)
    {
        foreach (string v in new[] { "JAVA_HOME", "JDK_HOME" })
        {
            string home = Environment.GetEnvironmentVariable(v);
            if (!string.IsNullOrEmpty(home))
            {
                AddHome(list, home.Trim('"'));
            }
        }
    }

    private static void AddFromPath(List<string> list)
    {
        string path = Environment.GetEnvironmentVariable("PATH");
        if (string.IsNullOrEmpty(path))
        {
            return;
        }
        foreach (string dir in path.Split(';'))
        {
            string trimmed = dir.Trim().Trim('"');
            if (trimmed.Length == 0)
            {
                continue;
            }
            try
            {
                list.Add(Path.Combine(trimmed, "javaw.exe"));
            }
            catch (ArgumentException)
            {
                // Phần tử PATH có ký tự không hợp lệ.
            }
        }
    }

    private static void AddFromRegistry(List<string> list)
    {
        RegistryView[] views = { RegistryView.Registry64, RegistryView.Registry32 };
        RegistryHive[] hives = { RegistryHive.LocalMachine, RegistryHive.CurrentUser };
        foreach (RegistryView view in views)
        {
            foreach (RegistryHive hive in hives)
            {
                try
                {
                    using (RegistryKey baseKey = RegistryKey.OpenBaseKey(hive, view))
                    {
                        foreach (string key in new[] { "SOFTWARE\\JavaSoft\\JDK", "SOFTWARE\\JavaSoft\\Java Development Kit" })
                        {
                            ReadJavaSoftKey(baseKey, key, list);
                        }
                        foreach (string key in new[] { "SOFTWARE\\Eclipse Adoptium\\JDK", "SOFTWARE\\Eclipse Foundation\\JDK", "SOFTWARE\\Semeru\\JDK" })
                        {
                            ReadMsiKey(baseKey, key, list);
                        }
                    }
                }
                catch (Exception)
                {
                    // Registry bị chặn quyền -- còn các nguồn khác.
                }
            }
        }
    }

    private static void ReadJavaSoftKey(RegistryKey baseKey, string path, List<string> list)
    {
        using (RegistryKey key = baseKey.OpenSubKey(path))
        {
            if (key == null)
            {
                return;
            }
            foreach (string version in key.GetSubKeyNames())
            {
                using (RegistryKey sub = key.OpenSubKey(version))
                {
                    if (sub != null)
                    {
                        AddHome(list, sub.GetValue("JavaHome") as string);
                    }
                }
            }
        }
    }

    private static void ReadMsiKey(RegistryKey baseKey, string path, List<string> list)
    {
        using (RegistryKey key = baseKey.OpenSubKey(path))
        {
            if (key == null)
            {
                return;
            }
            foreach (string version in key.GetSubKeyNames())
            {
                using (RegistryKey versionKey = key.OpenSubKey(version))
                {
                    if (versionKey == null)
                    {
                        continue;
                    }
                    foreach (string vm in versionKey.GetSubKeyNames())
                    {
                        using (RegistryKey msi = versionKey.OpenSubKey(vm + "\\MSI"))
                        {
                            if (msi != null)
                            {
                                AddHome(list, msi.GetValue("Path") as string);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void AddHome(List<string> list, string home)
    {
        if (string.IsNullOrEmpty(home))
        {
            return;
        }
        try
        {
            list.Add(Path.Combine(home, "bin\\javaw.exe"));
        }
        catch (ArgumentException)
        {
            // Giá trị hỏng.
        }
    }

    /// <summary>Quét thư mục cài mặc định của các hãng -- bắt cả JDK vừa cài chưa kịp vào PATH.</summary>
    private static void AddFromVendorDirs(List<string> list)
    {
        List<string> bases = new List<string>();
        foreach (string v in new[] { "ProgramW6432", "ProgramFiles", "ProgramFiles(x86)" })
        {
            string value = Environment.GetEnvironmentVariable(v);
            if (!string.IsNullOrEmpty(value))
            {
                bases.Add(value);
            }
        }
        string localAppData = Environment.GetEnvironmentVariable("LOCALAPPDATA");
        if (!string.IsNullOrEmpty(localAppData))
        {
            bases.Add(Path.Combine(localAppData, "Programs"));
        }
        string[] vendors =
        {
            "Eclipse Adoptium", "Eclipse Foundation", "Java", "Microsoft", "Zulu",
            "Amazon Corretto", "BellSoft", "RedHat", "IBM", "Semeru", "JetBrains", "Android\\Android Studio"
        };
        foreach (string baseDir in bases)
        {
            foreach (string vendor in vendors)
            {
                string vendorDir = Path.Combine(baseDir, vendor);
                if (!Directory.Exists(vendorDir))
                {
                    continue;
                }
                AddIfJavaDir(list, vendorDir);
                try
                {
                    foreach (string child in Directory.GetDirectories(vendorDir))
                    {
                        AddIfJavaDir(list, child);
                    }
                }
                catch (Exception)
                {
                    // Không đọc được thư mục.
                }
            }
        }
    }

    private static void AddIfJavaDir(List<string> list, string dir)
    {
        foreach (string layout in new[] { "bin\\javaw.exe", "jbr\\bin\\javaw.exe" })
        {
            string javaw = Path.Combine(dir, layout);
            if (File.Exists(javaw))
            {
                list.Add(javaw);
            }
        }
    }

    /// <summary>Kiểm tra javaw.exe, đọc phiên bản từ file "release" và xem có javac.exe không.</summary>
    private static JavaRuntime Inspect(string javawPath)
    {
        if (!File.Exists(javawPath))
        {
            return null;
        }
        string binDir = Path.GetDirectoryName(javawPath);
        JavaRuntime java = new JavaRuntime();
        java.JavawPath = javawPath;
        java.Home = Path.GetDirectoryName(binDir);
        java.IsJdk = File.Exists(Path.Combine(binDir, "javac.exe"));
        java.Major = ParseMajor(ReadReleaseVersion(java.Home));
        return java;
    }

    private static string ReadReleaseVersion(string home)
    {
        try
        {
            string releaseFile = Path.Combine(home, "release");
            if (!File.Exists(releaseFile))
            {
                return null;
            }
            foreach (string line in File.ReadAllLines(releaseFile))
            {
                if (line.StartsWith("JAVA_VERSION=", StringComparison.OrdinalIgnoreCase))
                {
                    return line.Substring("JAVA_VERSION=".Length).Trim().Trim('"');
                }
            }
        }
        catch (Exception)
        {
            // File release hỏng.
        }
        return null;
    }

    /// <summary>"21.0.12" -> 21; "1.8.0_412" -> 8; không đọc được -> 0.</summary>
    private static int ParseMajor(string version)
    {
        if (string.IsNullOrEmpty(version))
        {
            return 0;
        }
        string[] parts = version.Split('.', '_', '-', '+');
        int first;
        if (!int.TryParse(parts[0], out first))
        {
            return 0;
        }
        int second;
        if (first == 1 && parts.Length > 1 && int.TryParse(parts[1], out second))
        {
            return second;
        }
        return first;
    }

    // ------------------------------------------------------------------
    //  Tải JDK portable
    // ------------------------------------------------------------------

    private static JavaRuntime DownloadJdk(string home)
    {
        string arch = IsArm64() ? "aarch64" : "x64";
        string url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/" + arch
            + "/jdk/hotspot/normal/eclipse?project=jdk";
        string downloads = Path.Combine(home, "downloads");
        string zip = Path.Combine(downloads, "temurin-21-jdk-windows-" + arch + ".zip");

        ProgressForm form = new ProgressForm();
        Exception error = null;
        form.Shown += delegate
        {
            Thread worker = new Thread(delegate()
            {
                try
                {
                    Directory.CreateDirectory(downloads);
                    if (!File.Exists(zip))
                    {
                        Fetch(url, zip, form);
                    }
                    form.Report("Đang giải nén Java...", "Khoảng nửa phút", -1);
                    InstallJdk(zip, home);
                }
                catch (Exception ex)
                {
                    error = ex;
                }
                finally
                {
                    form.Finish();
                }
            });
            worker.IsBackground = true;
            worker.Start();
        };
        Application.EnableVisualStyles();
        Application.Run(form);

        if (form.Cancelled)
        {
            return null;
        }
        if (error != null)
        {
            Alert("Không tải được Java 21.",
                error.Message + "\n\nKiểm tra kết nối mạng rồi bấm đúp Setup.exe lần nữa -- phần đã tải được giữ lại.\n\n"
                    + "Hoặc tự cài Temurin 21 (bản JDK) tại:\n" + DownloadPage,
                IconError);
            return null;
        }
        JavaRuntime java = Inspect(Path.Combine(home, "jdk\\bin\\javaw.exe"));
        if (java == null || !java.IsJdk)
        {
            Alert("Đã tải Java nhưng không dùng được.", "Thiếu " + Path.Combine(home, "jdk\\bin\\javac.exe"), IconError);
            return null;
        }
        return java;
    }

    private static bool IsArm64()
    {
        string a = Environment.GetEnvironmentVariable("PROCESSOR_ARCHITEW6432");
        if (string.IsNullOrEmpty(a))
        {
            a = Environment.GetEnvironmentVariable("PROCESSOR_ARCHITECTURE");
        }
        return string.Equals(a, "ARM64", StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>Tải về file .part (tải tiếp được nếu lần trước bị ngắt) rồi đổi tên.</summary>
    private static void Fetch(string url, string dest, ProgressForm form)
    {
        ServicePointManager.SecurityProtocol |= (SecurityProtocolType)3072;   // TLS 1.2
        string part = dest + ".part";
        long have = File.Exists(part) ? new FileInfo(part).Length : 0;

        HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
        req.UserAgent = "RetailBillingSetup/2";
        req.AllowAutoRedirect = true;
        req.Timeout = 30000;
        req.ReadWriteTimeout = 60000;
        if (have > 0)
        {
            req.AddRange(have);
        }
        form.OnCancel = delegate { try { req.Abort(); } catch (Exception) { } };

        using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
        using (Stream input = resp.GetResponseStream())
        {
            bool append = resp.StatusCode == HttpStatusCode.PartialContent;
            if (!append)
            {
                have = 0;
            }
            long total = resp.ContentLength < 0 ? -1 : resp.ContentLength + have;
            using (FileStream output = new FileStream(part, append ? FileMode.Append : FileMode.Create, FileAccess.Write))
            {
                byte[] buf = new byte[128 * 1024];
                long done = have;
                int n;
                DateTime last = DateTime.MinValue;
                while ((n = input.Read(buf, 0, buf.Length)) > 0)
                {
                    if (form.Cancelled)
                    {
                        throw new OperationCanceledException();
                    }
                    output.Write(buf, 0, n);
                    done += n;
                    if ((DateTime.Now - last).TotalMilliseconds > 200)
                    {
                        last = DateTime.Now;
                        form.Report("Đang tải Java 21 (Eclipse Temurin)...",
                            Mb(done) + (total > 0 ? " / " + Mb(total) : "") + "  --  chỉ tải một lần",
                            total > 0 ? (int)(done * 1000 / total) : -1);
                    }
                }
                if (total > 0 && done < total)
                {
                    throw new IOException("Mất kết nối giữa chừng (" + Mb(done) + " / " + Mb(total) + ").");
                }
            }
        }
        form.OnCancel = null;
        if (File.Exists(dest))
        {
            File.Delete(dest);
        }
        File.Move(part, dest);
    }

    private static void InstallJdk(string zip, string home)
    {
        string tmp = Path.Combine(home, "jdk.tmp");
        string target = Path.Combine(home, "jdk");
        if (Directory.Exists(tmp))
        {
            Directory.Delete(tmp, true);
        }
        try
        {
            ZipFile.ExtractToDirectory(zip, tmp);
        }
        catch (InvalidDataException)
        {
            File.Delete(zip);   // file hỏng -- lần sau tải lại từ đầu
            throw new IOException("File Java tải về bị hỏng, đã xoá. Bấm đúp Setup.exe để tải lại.");
        }
        string inner = null;
        foreach (string d in Directory.GetDirectories(tmp))
        {
            if (File.Exists(Path.Combine(d, "bin\\javac.exe")))
            {
                inner = d;
            }
        }
        if (inner == null)
        {
            throw new IOException("File ZIP không chứa JDK như mong đợi.");
        }
        if (Directory.Exists(target))
        {
            Directory.Delete(target, true);
        }
        Directory.Move(inner, target);
        Directory.Delete(tmp, true);
        File.Delete(zip);
    }

    private static string Mb(long bytes)
    {
        return (bytes / (1024.0 * 1024.0)).ToString("0.0") + " MB";
    }

    /// <summary>Cửa sổ nhỏ hiện tiến độ tải, có nút Huỷ.</summary>
    private sealed class ProgressForm : Form
    {
        private readonly Label head = new Label();
        private readonly Label detail = new Label();
        private readonly ProgressBar bar = new ProgressBar();
        private readonly Button cancel = new Button();
        private volatile bool finished;

        public volatile bool Cancelled;
        public volatile MethodInvoker OnCancel;

        public ProgressForm()
        {
            Text = Title;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            AutoScaleDimensions = new SizeF(96F, 96F);
            AutoScaleMode = AutoScaleMode.Dpi;
            Font = new Font("Segoe UI", 9F);
            ClientSize = new Size(480, 150);
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch (Exception) { }

            head.Text = "Đang chuẩn bị Java 21...";
            head.Font = new Font("Segoe UI", 11F, FontStyle.Bold);
            head.SetBounds(20, 16, 440, 26);
            detail.Text = "Setup App cần Java. Máy chưa có nên đang tự tải bản portable, không cần cài đặt.";
            detail.SetBounds(20, 44, 440, 20);
            bar.SetBounds(20, 72, 440, 20);
            bar.Maximum = 1000;
            bar.Style = ProgressBarStyle.Marquee;
            cancel.Text = "Huỷ";
            cancel.SetBounds(380, 108, 80, 28);
            cancel.Click += delegate { RequestCancel(); };

            Controls.Add(head);
            Controls.Add(detail);
            Controls.Add(bar);
            Controls.Add(cancel);
            CancelButton = cancel;
        }

        /// <param name="permille">0..1000, hoặc âm để chạy vô định</param>
        public void Report(string title, string text, int permille)
        {
            if (!IsHandleCreated)
            {
                return;
            }
            BeginInvoke((MethodInvoker)delegate
            {
                head.Text = title;
                detail.Text = text;
                if (permille < 0)
                {
                    bar.Style = ProgressBarStyle.Marquee;
                }
                else
                {
                    bar.Style = ProgressBarStyle.Continuous;
                    bar.Value = Math.Max(0, Math.Min(1000, permille));
                }
            });
        }

        public void Finish()
        {
            finished = true;
            if (IsHandleCreated)
            {
                BeginInvoke((MethodInvoker)Close);
            }
        }

        private void RequestCancel()
        {
            Cancelled = true;
            cancel.Enabled = false;
            head.Text = "Đang huỷ...";
            MethodInvoker hook = OnCancel;
            if (hook != null)
            {
                hook();
            }
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (!finished)
            {
                // Bấm X khi đang tải = Huỷ; cửa sổ tự đóng khi luồng tải dừng hẳn.
                e.Cancel = true;
                if (!Cancelled)
                {
                    RequestCancel();
                }
                return;
            }
            base.OnFormClosing(e);
        }
    }

    // ------------------------------------------------------------------
    //  Khởi chạy SetupApp.jar
    // ------------------------------------------------------------------

    private static void Launch(JavaRuntime java, string jar, string root, string[] args)
    {
        StringBuilder cmd = new StringBuilder();
        cmd.Append("-Dfile.encoding=UTF-8 ");
        cmd.Append(Quote("-Drb.project.root=" + root)).Append(' ');
        cmd.Append("-jar ").Append(Quote(jar));
        foreach (string a in args)
        {
            cmd.Append(' ').Append(Quote(a));
        }

        ProcessStartInfo psi = new ProcessStartInfo(java.JavawPath, cmd.ToString());
        psi.WorkingDirectory = root;
        psi.UseShellExecute = false;
        psi.CreateNoWindow = true;
        // Nhồi JDK vào PATH của tiến trình con cho ai cần gọi java/javac từ đó.
        psi.EnvironmentVariables["JAVA_HOME"] = java.Home;
        string path = Environment.GetEnvironmentVariable("PATH");
        string bin = Path.GetDirectoryName(java.JavawPath);
        psi.EnvironmentVariables["PATH"] = string.IsNullOrEmpty(path) ? bin : bin + ";" + path;
        Process.Start(psi);
    }

    /// <summary>Đặt một tham số trong dấu nháy theo luật CommandLineToArgvW (nhân đôi \ trước ").</summary>
    private static string Quote(string arg)
    {
        StringBuilder sb = new StringBuilder("\"");
        int backslashes = 0;
        foreach (char c in arg)
        {
            if (c == '\\')
            {
                backslashes++;
                continue;
            }
            if (c == '"')
            {
                sb.Append('\\', backslashes * 2 + 1);
            }
            else
            {
                sb.Append('\\', backslashes);
            }
            backslashes = 0;
            sb.Append(c);
        }
        sb.Append('\\', backslashes * 2);
        return sb.Append('"').ToString();
    }

    // ------------------------------------------------------------------
    //  Hộp thoại báo lỗi (TaskDialog của Windows, tự lùi về MessageBox)
    // ------------------------------------------------------------------

    private const uint IconError = 0xFFFE;   // TD_ERROR_ICON

    private static void Alert(string heading, string content, uint icon)
    {
        try
        {
            TaskDialogConfig config = new TaskDialogConfig();
            config.cbSize = (uint)Marshal.SizeOf(typeof(TaskDialogConfig));
            config.dwFlags = 0x0008 | 0x1000000;   // ALLOW_DIALOG_CANCELLATION | SIZE_TO_CONTENT
            config.dwCommonButtons = 1;             // OK
            config.pszWindowTitle = Title;
            config.hMainIcon = new IntPtr(unchecked((int)icon));
            config.pszMainInstruction = heading;
            config.pszContent = content;
            int button;
            if (TaskDialogIndirect(ref config, out button, IntPtr.Zero, IntPtr.Zero) == 0)
            {
                return;
            }
        }
        catch (Exception)
        {
            // comctl32 v6 không nạp được -- dùng MessageBox.
        }
        MessageBoxW(IntPtr.Zero, heading + "\n\n" + content, Title, 0x10 | 0x10000 | 0x40000);
    }

    // Pack = 1 là BẮT BUỘC: commctrl.h khai báo struct này trong pshpack1.h. Bỏ
    // Pack thì struct phình từ 160 lên 176 byte trên x64, cbSize sai và
    // TaskDialogIndirect trả về E_INVALIDARG trong im lặng.
    [StructLayout(LayoutKind.Sequential, Pack = 1, CharSet = CharSet.Unicode)]
    private struct TaskDialogConfig
    {
        public uint cbSize;
        public IntPtr hwndParent;
        public IntPtr hInstance;
        public uint dwFlags;
        public uint dwCommonButtons;
        [MarshalAs(UnmanagedType.LPWStr)] public string pszWindowTitle;
        public IntPtr hMainIcon;
        [MarshalAs(UnmanagedType.LPWStr)] public string pszMainInstruction;
        [MarshalAs(UnmanagedType.LPWStr)] public string pszContent;
        public uint cButtons;
        public IntPtr pButtons;
        public int nDefaultButton;
        public uint cRadioButtons;
        public IntPtr pRadioButtons;
        public int nDefaultRadioButton;
        [MarshalAs(UnmanagedType.LPWStr)] public string pszVerificationText;
        [MarshalAs(UnmanagedType.LPWStr)] public string pszExpandedInformation;
        [MarshalAs(UnmanagedType.LPWStr)] public string pszExpandedControlText;
        [MarshalAs(UnmanagedType.LPWStr)] public string pszCollapsedControlText;
        public IntPtr hFooterIcon;
        [MarshalAs(UnmanagedType.LPWStr)] public string pszFooter;
        public IntPtr pfCallback;
        public IntPtr lpCallbackData;
        public uint cxWidth;
    }

    [DllImport("comctl32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern int TaskDialogIndirect(
        ref TaskDialogConfig config, out int button, IntPtr radioButton, IntPtr verificationFlagChecked);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern int MessageBoxW(IntPtr hWnd, string text, string caption, uint type);
}
