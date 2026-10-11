// =====================================================================
//  WindowsLauncher.cs -- mã nguồn của Setup.exe (launcher 1-click Windows)
// ---------------------------------------------------------------------
//  Biên dịch:  powershell -ExecutionPolicy Bypass -File scripts\ps1\build-launcher.ps1
//
//  Vì sao cần file .exe riêng mà không dùng Setup.bat?
//    1. Bấm đúp .bat luôn nháy một cửa sổ cmd đen -- trông như lỗi.
//    2. .bat chỉ tìm javaw trong PATH và JAVA_HOME. Vừa cài JDK bằng winget
//       mà chưa mở lại cửa sổ thì PATH chưa có -> báo "không tìm thấy Java"
//       dù máy đã có.
//    3. .bat không làm được hộp thoại đồ hoạ để mời người dùng cài Java.
//
//  File .exe này xử lý cả ba: không cửa sổ đen, dò Java ở 4 nguồn (biến môi
//  trường, PATH, registry, thư mục cài của từng hãng), và nếu thiếu thì hiện
//  hộp thoại cài tự động bằng winget. Sau khi cài, nó nhồi đường dẫn JDK vào
//  PATH của tiến trình con nên script PowerShell trong GUI gọi được javac/java
//  ngay, KHÔNG cần đăng xuất hay mở lại cửa sổ.
//
//  Lưu ý khi sửa file: csc.exe của .NET Framework chỉ hiểu C# 5.0 --
//  không dùng string interpolation ($"..."), toán tử ?. hay nameof.
// =====================================================================

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using Microsoft.Win32;

internal static class SetupLauncher
{
    /// <summary>SetupApp.jar được biên dịch với --release 21 nên cần Java >= 21.</summary>
    private const int MinJavaMajor = 21;

    private const string Title = "Retail Billing Setup";
    private const string WingetPackage = "EclipseAdoptium.Temurin.21.JDK";
    private const string DownloadPage = "https://adoptium.net/temurin/releases/?version=21&os=windows";

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

        string jar = FindJar(root, exeDir);
        if (jar == null)
        {
            Alert("Không tìm thấy SetupApp.jar.",
                "Setup.exe phải nằm cùng thư mục với SetupApp.jar (thư mục gốc dự án, "
                    + "cạnh config.properties).\n\nĐã tìm trong:\n  " + root
                    + "\n  " + exeDir
                    + "\n\nNếu bạn vừa xoá file jar, đóng gói lại bằng:\n"
                    + "  powershell -ExecutionPolicy Bypass -File scripts\\ps1\\build-setup.ps1",
                IconError);
            return 1;
        }

        JavaRuntime java = FindBestJava();
        if (java == null || (java.Major > 0 && java.Major < MinJavaMajor))
        {
            java = AskToInstallJava(java);
            if (java == null)
            {
                return 1;
            }
        }

        Launch(java, jar, root, args);
        return 0;
    }

    // ------------------------------------------------------------------
    //  Tìm thư mục gốc dự án và file jar
    // ------------------------------------------------------------------

    private static string GetExeDir()
    {
        using (Process me = Process.GetCurrentProcess())
        {
            return Path.GetDirectoryName(me.MainModule.FileName);
        }
    }

    /// <summary>
    /// Đi ngược lên tối đa 5 cấp để tìm thư mục có scripts/ hoặc config.properties --
    /// giống envsetup.runner.PlatformUtil.climbToRoot() để hai bên luôn ra cùng kết quả.
    /// </summary>
    private static string ResolveProjectRoot(string exeDir)
    {
        DirectoryInfo dir = new DirectoryInfo(exeDir);
        for (int i = 0; dir != null && i < 5; i++)
        {
            if (LooksLikeProjectRoot(dir.FullName))
            {
                return dir.FullName;
            }
            dir = dir.Parent;
        }
        return exeDir;
    }

    private static bool LooksLikeProjectRoot(string dir)
    {
        return Directory.Exists(Path.Combine(dir, "scripts\\ps1"))
            || Directory.Exists(Path.Combine(dir, "scripts\\bash"))
            || File.Exists(Path.Combine(dir, "config.properties"));
    }

    private static string FindJar(string root, string exeDir)
    {
        string[] candidates = { Path.Combine(root, "SetupApp.jar"), Path.Combine(exeDir, "SetupApp.jar") };
        foreach (string c in candidates)
        {
            if (File.Exists(c))
            {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Dò Java trên máy
    // ------------------------------------------------------------------

    /// <summary>Một bản Java tìm thấy trên máy.</summary>
    private sealed class JavaRuntime
    {
        public string JavawPath;
        /// <summary>Thư mục JAVA_HOME, null nếu javaw.exe không nằm trong .../bin.</summary>
        public string Home;
        /// <summary>Phiên bản chính (21, 17, 8...); 0 nếu không đọc được.</summary>
        public int Major;
        public string Version;
    }

    /// <summary>Trả về bản Java mới nhất tìm được, hoặc null nếu máy chưa có Java.</summary>
    private static JavaRuntime FindBestJava()
    {
        List<string> candidates = new List<string>();
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
            if (found == null)
            {
                continue;
            }
            // Bản đọc được phiên bản luôn thắng bản không đọc được, sau đó mới so số.
            if (best == null || found.Major > best.Major)
            {
                best = found;
            }
        }
        return best;
    }

    private static void AddFromEnvironment(List<string> list)
    {
        string[] vars = { "JAVA_HOME", "JDK_HOME", "JRE_HOME" };
        foreach (string v in vars)
        {
            string home = Environment.GetEnvironmentVariable(v);
            if (!string.IsNullOrEmpty(home))
            {
                list.Add(Path.Combine(home.Trim('"'), "bin\\javaw.exe"));
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
                // Bỏ qua phần tử PATH có ký tự không hợp lệ.
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
                        // Chuẩn cũ của Sun/Oracle, Temurin cũng ghi vào đây.
                        string[] javaSoft =
                        {
                            "SOFTWARE\\JavaSoft\\JDK",
                            "SOFTWARE\\JavaSoft\\Java Development Kit",
                            "SOFTWARE\\JavaSoft\\JRE",
                            "SOFTWARE\\JavaSoft\\Java Runtime Environment"
                        };
                        foreach (string key in javaSoft)
                        {
                            ReadJavaSoftKey(baseKey, key, list);
                        }

                        // Temurin / Semeru ghi đường dẫn vào ...\<version>\<vm>\MSI\Path
                        string[] msiRoots =
                        {
                            "SOFTWARE\\Eclipse Adoptium\\JDK",
                            "SOFTWARE\\Eclipse Adoptium\\JRE",
                            "SOFTWARE\\Eclipse Foundation\\JDK",
                            "SOFTWARE\\Semeru\\JDK",
                            "SOFTWARE\\IBM\\Semeru Runtime Open Edition"
                        };
                        foreach (string key in msiRoots)
                        {
                            ReadMsiKey(baseKey, key, list);
                        }
                    }
                }
                catch (Exception)
                {
                    // Registry bị chặn quyền -- bỏ qua, còn 3 nguồn khác.
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
                    if (sub == null)
                    {
                        continue;
                    }
                    AddHome(list, sub.GetValue("JavaHome") as string);
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
        if (!string.IsNullOrEmpty(home))
        {
            try
            {
                list.Add(Path.Combine(home, "bin\\javaw.exe"));
            }
            catch (ArgumentException)
            {
                // Giá trị registry hỏng -- bỏ qua.
            }
        }
    }

    /// <summary>
    /// Quét thư mục cài mặc định của các hãng. Đây là lưới an toàn cuối cùng:
    /// bắt được cả bản JDK giải nén tay hoặc vừa cài xong chưa kịp vào PATH.
    /// </summary>
    private static void AddFromVendorDirs(List<string> list)
    {
        List<string> bases = new List<string>();
        AddIfSet(bases, Environment.GetEnvironmentVariable("ProgramW6432"));
        AddIfSet(bases, Environment.GetEnvironmentVariable("ProgramFiles"));
        AddIfSet(bases, Environment.GetEnvironmentVariable("ProgramFiles(x86)"));
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
                // Bản thân thư mục hãng có thể đã là một JDK (ví dụ ...\Android Studio\jbr).
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
                    // Không đọc được thư mục -- bỏ qua.
                }
            }
        }
    }

    private static void AddIfSet(List<string> list, string value)
    {
        if (!string.IsNullOrEmpty(value))
        {
            list.Add(value);
        }
    }

    /// <summary>Thêm dir, dir\jre và dir\jbr nếu có javaw.exe (IDE nhúng JDK vào jbr).</summary>
    private static void AddIfJavaDir(List<string> list, string dir)
    {
        string[] layouts = { "bin\\javaw.exe", "jre\\bin\\javaw.exe", "jbr\\bin\\javaw.exe" };
        foreach (string layout in layouts)
        {
            string javaw = Path.Combine(dir, layout);
            if (File.Exists(javaw))
            {
                list.Add(javaw);
            }
        }
    }

    /// <summary>Kiểm tra javaw.exe có thật và đọc phiên bản của nó.</summary>
    private static JavaRuntime Inspect(string javawPath)
    {
        if (!File.Exists(javawPath))
        {
            return null;
        }

        JavaRuntime java = new JavaRuntime();
        java.JavawPath = javawPath;

        string binDir = Path.GetDirectoryName(javawPath);
        if (string.Equals(Path.GetFileName(binDir), "bin", StringComparison.OrdinalIgnoreCase))
        {
            java.Home = Path.GetDirectoryName(binDir);
        }

        java.Version = ReadVersionFromReleaseFile(java.Home);
        if (java.Version == null)
        {
            java.Version = ReadVersionByRunningJava(binDir);
        }
        java.Major = ParseMajor(java.Version);
        if (java.Version == null)
        {
            java.Version = "không rõ phiên bản";
        }
        return java;
    }

    /// <summary>
    /// Mọi bản JDK/JRE từ Java 9 trở lên đều có file "release" ở gốc chứa
    /// JAVA_VERSION="21.0.12". Đọc file nhanh hơn và chắc hơn gọi java -version.
    /// </summary>
    private static string ReadVersionFromReleaseFile(string home)
    {
        if (string.IsNullOrEmpty(home))
        {
            return null;
        }
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
            // File release hỏng -- để hàm gọi thử cách khác.
        }
        return null;
    }

    /// <summary>Dự phòng cho Java 8 (không có file release): gọi java.exe -version.</summary>
    private static string ReadVersionByRunningJava(string binDir)
    {
        string javaExe = Path.Combine(binDir, "java.exe");
        if (!File.Exists(javaExe))
        {
            return null;
        }
        try
        {
            ProcessStartInfo psi = new ProcessStartInfo(javaExe, "-version");
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.RedirectStandardError = true;
            psi.RedirectStandardOutput = true;

            using (Process p = Process.Start(psi))
            {
                string output = p.StandardError.ReadToEnd() + p.StandardOutput.ReadToEnd();
                if (!p.WaitForExit(5000))
                {
                    return null;
                }
                int open = output.IndexOf('"');
                int close = open < 0 ? -1 : output.IndexOf('"', open + 1);
                if (open >= 0 && close > open)
                {
                    return output.Substring(open + 1, close - open - 1);
                }
            }
        }
        catch (Exception)
        {
            // java.exe hỏng hoặc bị chặn -- coi như không đọc được phiên bản.
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
        if (parts.Length == 0 || !int.TryParse(parts[0], NumberStyles.Integer, CultureInfo.InvariantCulture, out first))
        {
            return 0;
        }
        if (first == 1 && parts.Length > 1)
        {
            int second;
            if (int.TryParse(parts[1], NumberStyles.Integer, CultureInfo.InvariantCulture, out second))
            {
                return second;
            }
        }
        return first;
    }

    // ------------------------------------------------------------------
    //  Khởi chạy SetupApp.jar
    // ------------------------------------------------------------------

    private static void Launch(JavaRuntime java, string jar, string root, string[] args)
    {
        ProcessStartInfo psi = new ProcessStartInfo(java.JavawPath);

        StringBuilder cmd = new StringBuilder();
        cmd.Append("-Dfile.encoding=UTF-8 -jar \"").Append(jar).Append('"');
        foreach (string a in args)
        {
            cmd.Append(" \"").Append(a).Append('"');
        }
        psi.Arguments = cmd.ToString();
        psi.WorkingDirectory = root;
        psi.UseShellExecute = false;
        psi.CreateNoWindow = true;

        // Nhồi JDK vào môi trường của tiến trình con: GUI gọi script PowerShell,
        // script đó gọi javac/java. Nếu người dùng vừa cài JDK mà chưa mở lại
        // cửa sổ thì PATH của Explorer vẫn cũ -- không làm bước này sẽ gặp
        // "javac không phải là lệnh" dù Java đã nằm ngay trên máy.
        string binDir = Path.GetDirectoryName(java.JavawPath);
        if (!string.IsNullOrEmpty(java.Home))
        {
            psi.EnvironmentVariables["JAVA_HOME"] = java.Home;
        }
        string path = Environment.GetEnvironmentVariable("PATH");
        psi.EnvironmentVariables["PATH"] = string.IsNullOrEmpty(path) ? binDir : binDir + ";" + path;

        Process.Start(psi);
    }

    // ------------------------------------------------------------------
    //  Thiếu Java -- mời cài
    // ------------------------------------------------------------------

    /// <summary>Hỏi người dùng rồi cài Java. Trả về bản Java dùng được, null nếu bỏ qua.</summary>
    private static JavaRuntime AskToInstallJava(JavaRuntime current)
    {
        string content = current == null
            ? "Setup App chạy bằng Java nhưng máy này chưa có bản nào.\n\n"
                + "Bản khuyên dùng là Eclipse Temurin " + MinJavaMajor + " (JDK mã nguồn mở, miễn phí)."
            : "Máy này đang có Java " + current.Version + " nhưng Setup App cần Java "
                + MinJavaMajor + " trở lên.\n\n" + current.JavawPath;

        int choice = AskThreeWay(
            current == null ? "Chưa có Java trên máy" : "Java trên máy quá cũ",
            content,
            "Cài tự động bằng winget\nTải và cài Temurin " + MinJavaMajor + " (~190 MB, cần mạng, khoảng 2-3 phút)",
            "Mở trang tải về\nTự tải bộ cài Temurin " + MinJavaMajor + " từ adoptium.net rồi chạy lại Setup");

        if (choice == ButtonInstall)
        {
            RunWinget();
            JavaRuntime retry = FindBestJava();
            if (retry != null && (retry.Major == 0 || retry.Major >= MinJavaMajor))
            {
                return retry;
            }
            Alert("Cài xong nhưng vẫn chưa thấy Java " + MinJavaMajor + " trở lên.",
                "Nếu winget báo thành công, hãy bấm đúp Setup.exe lần nữa.\n\n"
                    + "Nếu không, tải tay tại:\n" + DownloadPage,
                IconWarning);
            return null;
        }

        if (choice == ButtonDownload)
        {
            OpenUrl(DownloadPage);
        }
        return null;
    }

    /// <summary>
    /// Chạy winget trong cửa sổ riêng để người dùng thấy tiến độ tải.
    /// Không xét mã thoát vì winget trả mã khác 0 cho cả trường hợp vô hại
    /// (ví dụ "đã cài sẵn") -- cứ dò lại Java sau khi nó kết thúc là chắc nhất.
    /// </summary>
    private static void RunWinget()
    {
        string winget = FindOnPath("winget.exe");
        if (winget == null)
        {
            Alert("Máy này không có winget.",
                "winget đi kèm App Installer của Microsoft Store. Bạn hãy tải bộ cài Temurin "
                    + MinJavaMajor + " trực tiếp tại:\n" + DownloadPage,
                IconWarning);
            OpenUrl(DownloadPage);
            return;
        }

        try
        {
            ProcessStartInfo psi = new ProcessStartInfo(winget,
                "install --id " + WingetPackage + " --exact --source winget"
                    + " --accept-source-agreements --accept-package-agreements");
            psi.UseShellExecute = true; // mở cửa sổ console để thấy tiến độ winget
            using (Process p = Process.Start(psi))
            {
                p.WaitForExit();
            }
        }
        catch (Exception ex)
        {
            Alert("Không chạy được winget.", ex.Message, IconError);
        }
    }

    private static string FindOnPath(string exeName)
    {
        string path = Environment.GetEnvironmentVariable("PATH");
        if (string.IsNullOrEmpty(path))
        {
            return null;
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
                string full = Path.Combine(trimmed, exeName);
                if (File.Exists(full))
                {
                    return full;
                }
            }
            catch (ArgumentException)
            {
                // Phần tử PATH không hợp lệ -- bỏ qua.
            }
        }
        return null;
    }

    private static void OpenUrl(string url)
    {
        try
        {
            ProcessStartInfo psi = new ProcessStartInfo(url);
            psi.UseShellExecute = true;
            Process.Start(psi);
        }
        catch (Exception)
        {
            // Không mở được trình duyệt -- người dùng vẫn đọc được URL trong hộp thoại.
        }
    }

    // ------------------------------------------------------------------
    //  Hộp thoại (TaskDialog của Windows, tự lùi về MessageBox nếu lỗi)
    // ------------------------------------------------------------------

    private const int ButtonInstall = 1001;
    private const int ButtonDownload = 1002;
    private const int ButtonCancel = 2; // IDCANCEL

    private const uint IconWarning = 0xFFFF; // TD_WARNING_ICON
    private const uint IconError = 0xFFFE;   // TD_ERROR_ICON

    private static void Alert(string heading, string content, uint icon)
    {
        try
        {
            TaskDialogConfig config = NewConfig(heading, content, icon);
            config.dwCommonButtons = 1; // TDCBF_OK_BUTTON
            int button;
            if (TaskDialogIndirect(ref config, out button, IntPtr.Zero, IntPtr.Zero) == 0)
            {
                return;
            }
        }
        catch (Exception)
        {
            // Windows quá cũ hoặc comctl32 v6 không nạp được -- dùng MessageBox.
        }
        uint style = icon == IconError ? MbIconError : MbIconWarning;
        MessageBoxW(IntPtr.Zero, heading + "\n\n" + content, Title, MbOk | style | MbSetForeground | MbTopMost);
    }

    /// <summary>
    /// Hộp thoại hai lựa chọn + Thoát. Trả về ButtonInstall / ButtonDownload / ButtonCancel.
    /// </summary>
    private static int AskThreeWay(string heading, string content, string installText, string downloadText)
    {
        IntPtr buttonArray = IntPtr.Zero;
        TaskDialogButton[] buttons =
        {
            NewButton(ButtonInstall, installText),
            NewButton(ButtonDownload, downloadText)
        };
        try
        {
            int stride = Marshal.SizeOf(typeof(TaskDialogButton));
            buttonArray = Marshal.AllocHGlobal(stride * buttons.Length);
            for (int i = 0; i < buttons.Length; i++)
            {
                Marshal.StructureToPtr(buttons[i], (IntPtr)(buttonArray.ToInt64() + i * stride), false);
            }

            TaskDialogConfig config = NewConfig(heading, content, IconWarning);
            config.dwFlags |= TdfUseCommandLinks;
            config.dwCommonButtons = 8; // TDCBF_CANCEL_BUTTON
            config.cButtons = (uint)buttons.Length;
            config.pButtons = buttonArray;
            config.nDefaultButton = ButtonInstall;

            int pressed;
            if (TaskDialogIndirect(ref config, out pressed, IntPtr.Zero, IntPtr.Zero) == 0)
            {
                return pressed;
            }
        }
        catch (Exception)
        {
            // Lùi về MessageBox ở dưới.
        }
        finally
        {
            if (buttonArray != IntPtr.Zero)
            {
                int stride = Marshal.SizeOf(typeof(TaskDialogButton));
                for (int i = 0; i < buttons.Length; i++)
                {
                    Marshal.DestroyStructure((IntPtr)(buttonArray.ToInt64() + i * stride), typeof(TaskDialogButton));
                }
                Marshal.FreeHGlobal(buttonArray);
            }
        }

        // Dự phòng: MessageBox không cho đặt tên nút nên phải chú thích trong nội dung.
        int rc = MessageBoxW(IntPtr.Zero,
            heading + "\n\n" + content
                + "\n\n[Yes / Có]\tCài tự động bằng winget"
                + "\n[No / Không]\tMở trang tải về"
                + "\n[Cancel / Huỷ]\tThoát",
            Title, MbYesNoCancel | MbIconWarning | MbSetForeground | MbTopMost);
        if (rc == 6)
        {
            return ButtonInstall;
        }
        if (rc == 7)
        {
            return ButtonDownload;
        }
        return ButtonCancel;
    }

    private static TaskDialogConfig NewConfig(string heading, string content, uint icon)
    {
        TaskDialogConfig config = new TaskDialogConfig();
        config.cbSize = (uint)Marshal.SizeOf(typeof(TaskDialogConfig));
        config.hwndParent = IntPtr.Zero;
        config.hInstance = IntPtr.Zero;
        config.dwFlags = TdfAllowDialogCancellation | TdfSizeToContent;
        config.pszWindowTitle = Title;
        config.hMainIcon = new IntPtr(unchecked((int)icon));
        config.pszMainInstruction = heading;
        config.pszContent = content;
        config.nDefaultButton = 0;
        config.nDefaultRadioButton = 0;
        return config;
    }

    private static TaskDialogButton NewButton(int id, string text)
    {
        TaskDialogButton button = new TaskDialogButton();
        button.nButtonID = id;
        button.pszButtonText = text;
        return button;
    }

    private const uint TdfAllowDialogCancellation = 0x0008;
    private const uint TdfUseCommandLinks = 0x0010;
    private const uint TdfSizeToContent = 0x1000000;

    private const uint MbOk = 0x0;
    private const uint MbYesNoCancel = 0x3;
    private const uint MbIconError = 0x10;
    private const uint MbIconWarning = 0x30;
    private const uint MbSetForeground = 0x10000;
    private const uint MbTopMost = 0x40000;

    // Pack = 1 là BẮT BUỘC, không phải tuỳ chọn: commctrl.h khai báo hai struct
    // này nằm giữa #include <pshpack1.h> và <poppack.h> nên bản gốc bị nén sát,
    // không có byte đệm. Bỏ Pack thì CLR canh lề tự nhiên -> struct phình từ
    // 160 lên 176 byte trên x64, cbSize sai và TaskDialogIndirect trả về
    // E_INVALIDARG (0x80070057) trong im lặng, rơi xuống MessageBox dự phòng.
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

    [StructLayout(LayoutKind.Sequential, Pack = 1, CharSet = CharSet.Unicode)]
    private struct TaskDialogButton
    {
        public int nButtonID;
        [MarshalAs(UnmanagedType.LPWStr)] public string pszButtonText;
    }

    [DllImport("comctl32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern int TaskDialogIndirect(
        ref TaskDialogConfig config, out int button, IntPtr radioButton, IntPtr verificationFlagChecked);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern int MessageBoxW(IntPtr hWnd, string text, string caption, uint type);
}
