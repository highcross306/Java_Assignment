// 단일 파일 설치본 AttendanceSetup.exe 자체. build_setup.ps1이 Windows에 기본으로 들어 있는 .NET Framework 컴파일러(csc.exe)로
// 이 파일을 컴파일하면서 설치 프로그램 폴더(dist\AttendanceSetup)를 묶은 setup.zip을 리소스로 넣는다.
//
// 실행하면: 1) "설치 프로그램 준비 중..." 창을 먼저 띄우고  2) 임시 폴더에 setup.zip을 풀면서 진행 막대로 보여주고
//           3) 설치 프로그램(Java, AttendanceSetup\AttendanceSetup.exe)을 실행  4) 설치 프로그램이 자기 준비 창을 띄우며
//           READY 파일(환경 변수 ATTENDANCE_SETUP_READY)을 만들면 이 창을 닫음 (setup\src\setup\LoadingWindow.java)
//           5) 설치 프로그램이 끝나면 임시 폴더를 지움
//
// 예전 구조(IExpress 자동 압축 풀기 + run.cmd)는 Windows가 111MB를 다 풀고 나서야 우리 코드가 실행돼서,
// 준비 창이 실행 후 6초쯤 지나서야 보였음 (그 전에는 검은 cmd 창). 이 구조는 창이 1초 안에 뜸.
// 명령줄 옵션(--silent 등)은 설치 프로그램에 그대로 넘김. --silent면 창을 띄우지 않음.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

static class Bootstrap
{
    const string Title = "출석 관리 프로그램 설치";
    const string Payload = "setup.zip";
    const string TempPrefix = "AttendanceSetup_";

    [DllImport("user32.dll")]
    static extern bool SetProcessDPIAware();

    [STAThread]
    static int Main(string[] args)
    {
        bool silent = Array.IndexOf(args, "--silent") >= 0;
        if (!silent)
        {
            try { SetProcessDPIAware(); } catch (Exception) { }
            Application.EnableVisualStyles();
        }

        CleanOldTempFolders();
        string dest = Path.Combine(Path.GetTempPath(), TempPrefix + Guid.NewGuid().ToString("N").Substring(0, 10));
        string readyFile = dest + ".ready";

        Process setup = null;
        string error = null;
        if (silent)
        {
            try
            {
                Extract(dest, null);
                setup = Launch(dest, readyFile, args);
            }
            catch (Exception e) { error = e.Message; }
        }
        else
        {
            LoadingForm form = new LoadingForm(dest, readyFile, args);
            Application.Run(form);
            setup = form.Setup;
            error = form.Error;
        }

        int exitCode = 1;
        if (error != null)
        {
            if (silent) Console.Error.WriteLine("설치 파일의 압축을 풀지 못했습니다: " + error);
            else MessageBox.Show("설치 파일의 압축을 풀지 못했습니다.\r\n디스크(C:) 여유 공간을 확인한 뒤 다시 실행해주세요.\r\n\r\n(" + error + ")",
                Title, MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
        else if (setup != null)
        {
            setup.WaitForExit();
            exitCode = setup.ExitCode;
        }
        Cleanup(dest, readyFile);
        return exitCode;
    }

    // ------------------------------------------------------------------ 준비 창

    sealed class LoadingForm : Form
    {
        readonly string dest, readyFile;
        readonly string[] args;
        readonly ProgressBar bar = new ProgressBar();
        readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer();
        public Process Setup;
        public volatile string Error;
        volatile int percent = 0;
        volatile bool extracted = false;
        DateTime launchedAt;

        public LoadingForm(string dest, string readyFile, string[] args)
        {
            this.dest = dest;
            this.readyFile = readyFile;
            this.args = args;

            Text = Title;
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            ShowInTaskbar = true;
            TopMost = true;
            AutoScaleMode = AutoScaleMode.Dpi;
            AutoScaleDimensions = new SizeF(96F, 96F);
            BackColor = Color.FromArgb(209, 213, 219); // 테두리 색 (LoadingWindow.java와 같은 모양)
            Padding = new Padding(1);
            ClientSize = new Size(282, 92);
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch (Exception) { }

            Panel inner = new Panel();
            inner.Dock = DockStyle.Fill;
            inner.BackColor = SystemColors.Control;
            Controls.Add(inner);

            Label label = new Label();
            label.Text = "설치 프로그램 준비 중...";
            label.Font = new Font("맑은 고딕", 10F);
            label.TextAlign = ContentAlignment.MiddleCenter;
            label.SetBounds(0, 16, 280, 26);
            inner.Controls.Add(label);

            bar.Style = ProgressBarStyle.Continuous;
            bar.SetBounds(30, 56, 220, 8);
            inner.Controls.Add(bar);

            timer.Interval = 100;
            timer.Tick += OnTick;
            // 창이 화면에 그려진 뒤에 압축 풀기를 시작 (풀기는 별도 스레드, 창은 계속 반응)
            Shown += delegate
            {
                timer.Start();
                Thread t = new Thread(Work);
                t.IsBackground = true;
                t.Start();
            };
        }

        void Work()
        {
            try
            {
                Extract(dest, p => percent = p);
                extracted = true;
            }
            catch (Exception e)
            {
                Error = e.Message;
            }
        }

        void OnTick(object sender, EventArgs e)
        {
            if (Error != null) { timer.Stop(); Close(); return; }
            if (!extracted)
            {
                bar.Value = Math.Max(0, Math.Min(100, percent));
                return;
            }
            if (Setup == null)
            {
                // 압축 풀기 끝 -> 설치 프로그램 실행. Java가 뜨는 몇 초 동안은 움직이는 막대
                bar.Style = ProgressBarStyle.Marquee;
                bar.MarqueeAnimationSpeed = 30;
                try { Setup = Launch(dest, readyFile, args); }
                catch (Exception ex) { Error = ex.Message; timer.Stop(); Close(); return; }
                launchedAt = DateTime.Now;
                return;
            }
            // 설치 프로그램이 자기 준비 창을 띄웠거나(READY), 그 전에 끝났거나, 너무 오래 걸리면 이 창은 닫음
            if (File.Exists(readyFile) || Setup.HasExited || (DateTime.Now - launchedAt).TotalMinutes >= 3)
            {
                timer.Stop();
                Close();
            }
        }
    }

    // ------------------------------------------------------------------ 압축 풀기 / 실행 / 정리

    // 리소스의 setup.zip을 dest에 풂. progress: 0~100 (압축된 크기 기준).
    // 한 줄로 풀면 압축 해제와 백신 검사(파일을 닫을 때마다)가 차례로 기다려서 느림 -> 파일을 몇 묶음으로 나눠
    // 여러 스레드가 동시에 풂 (스레드마다 리소스를 따로 열어서 각자 읽음)
    static void Extract(string dest, Action<int> progress)
    {
        Directory.CreateDirectory(dest);
        string root = Path.GetFullPath(dest) + Path.DirectorySeparatorChar;

        // 1) 목록만 읽어서, 큰 파일부터 가장 덜 맡은 스레드에 배정
        int workers = Math.Max(1, Math.Min(4, Environment.ProcessorCount));
        List<int>[] assigned = new List<int>[workers];
        long[] load = new long[workers];
        for (int w = 0; w < workers; w++) assigned[w] = new List<int>();
        long total = 0;
        using (ZipArchive zip = OpenPayload())
        {
            List<int> order = new List<int>();
            for (int i = 0; i < zip.Entries.Count; i++) { order.Add(i); total += zip.Entries[i].CompressedLength; }
            SortBySizeDesc(order, zip);
            foreach (int i in order)
            {
                int best = 0;
                for (int w = 1; w < workers; w++) if (load[w] < load[best]) best = w;
                assigned[best].Add(i);
                load[best] += zip.Entries[i].CompressedLength;
            }
        }

        // 2) 스레드별로 풀기
        long done = 0;
        Exception failure = null;
        Thread[] threads = new Thread[workers];
        for (int w = 0; w < workers; w++)
        {
            List<int> mine = assigned[w];
            threads[w] = new Thread(delegate ()
            {
                try
                {
                    byte[] buffer = new byte[1 << 18];
                    using (ZipArchive zip = OpenPayload())
                    {
                        foreach (int i in mine)
                        {
                            if (failure != null) return;
                            ZipArchiveEntry en = zip.Entries[i];
                            string target = Path.GetFullPath(Path.Combine(dest, en.FullName));
                            if (!target.StartsWith(root, StringComparison.OrdinalIgnoreCase)) continue; // 폴더 밖으로 나가는 경로는 무시
                            if (en.FullName.EndsWith("/") || en.FullName.EndsWith("\\"))
                            {
                                Directory.CreateDirectory(target);
                                continue;
                            }
                            Directory.CreateDirectory(Path.GetDirectoryName(target));
                            using (Stream input = en.Open())
                            using (FileStream output = new FileStream(target, FileMode.Create, FileAccess.Write, FileShare.None, 1 << 16))
                            {
                                int n;
                                while ((n = input.Read(buffer, 0, buffer.Length)) > 0) output.Write(buffer, 0, n);
                            }
                            long now = Interlocked.Add(ref done, en.CompressedLength);
                            if (progress != null && total > 0) progress((int)(now * 100 / total));
                        }
                    }
                }
                catch (Exception e)
                {
                    Interlocked.CompareExchange(ref failure, e, null);
                }
            });
            threads[w].IsBackground = true;
            threads[w].Start();
        }
        foreach (Thread t in threads) t.Join();
        if (failure != null) throw failure;
    }

    static ZipArchive OpenPayload()
    {
        Stream res = Assembly.GetExecutingAssembly().GetManifestResourceStream(Payload);
        if (res == null) throw new InvalidOperationException("설치 파일에 " + Payload + "이(가) 없습니다");
        return new ZipArchive(res, ZipArchiveMode.Read); // 닫으면 res도 닫힘
    }

    // 큰 파일(압축된 크기)부터
    static void SortBySizeDesc(List<int> order, ZipArchive zip)
    {
        order.Sort(delegate (int a, int b) { return zip.Entries[b].CompressedLength.CompareTo(zip.Entries[a].CompressedLength); });
    }

    static Process Launch(string dest, string readyFile, string[] args)
    {
        string exe = Path.Combine(dest, "AttendanceSetup", "AttendanceSetup.exe");
        if (!File.Exists(exe)) throw new FileNotFoundException("설치 프로그램 파일이 없습니다", exe);
        ProcessStartInfo psi = new ProcessStartInfo(exe, JoinArgs(args));
        psi.UseShellExecute = false;
        psi.WorkingDirectory = Path.GetDirectoryName(exe);
        psi.EnvironmentVariables["ATTENDANCE_SETUP_READY"] = readyFile;
        return Process.Start(psi);
    }

    static string JoinArgs(string[] args)
    {
        StringBuilder sb = new StringBuilder();
        foreach (string a in args)
        {
            if (sb.Length > 0) sb.Append(' ');
            if (a.Length > 0 && a.IndexOfAny(new char[] { ' ', '\t', '"' }) < 0) sb.Append(a);
            else sb.Append('"').Append(a.Replace("\"", "\\\"")).Append('"');
        }
        return sb.ToString();
    }

    // 설치 프로그램 런처가 끝난 직후에는 파일이 잠깐 잠겨 있을 수 있어서 몇 번 다시 시도
    static void Cleanup(string dest, string readyFile)
    {
        try { File.Delete(readyFile); } catch (Exception) { }
        for (int i = 0; i < 10 && Directory.Exists(dest); i++)
        {
            try { Directory.Delete(dest, true); }
            catch (Exception) { Thread.Sleep(500); }
        }
    }

    // 강제 종료 등으로 지우지 못하고 남은 예전 임시 폴더(하루 넘은 것)를 정리
    static void CleanOldTempFolders()
    {
        try
        {
            foreach (string d in Directory.GetDirectories(Path.GetTempPath(), TempPrefix + "*"))
            {
                if ((DateTime.Now - Directory.GetLastWriteTime(d)).TotalDays >= 1)
                {
                    try { Directory.Delete(d, true); } catch (Exception) { }
                }
            }
            foreach (string f in Directory.GetFiles(Path.GetTempPath(), TempPrefix + "*.ready"))
            {
                if ((DateTime.Now - File.GetLastWriteTime(f)).TotalDays >= 1)
                {
                    try { File.Delete(f); } catch (Exception) { }
                }
            }
        }
        catch (Exception) { }
    }
}
