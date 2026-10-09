// 단일 파일 설치본(AttendanceSetup.exe)이 압축을 푸는 동안 보여주는 "설치 프로그램 준비 중..." 창.
// build_setup.ps1이 Windows에 기본으로 들어 있는 .NET Framework 컴파일러(csc.exe)로 AttendanceLoading.exe를 만든다.
// (PowerShell로 같은 창을 띄우면 PowerShell이 뜨는 데만 수 초가 걸려 압축 풀기가 끝난 뒤에야 보였음 - 실측)
//
// 사용: AttendanceLoading.exe <READY 파일>   -> 그 파일이 생기면 닫힘 (설치 프로그램(Java)이 자기 준비 창을 띄우며 만듦,
//                                             설치 프로그램이 끝나면 run.cmd도 만듦). 3분이 지나도 닫힘
//       AttendanceLoading.exe --extract-failed -> 압축 풀기 실패 안내만 띄움 (run.cmd의 콘솔은 숨겨져 있어 직접 못 보여줌)
// 모양은 setup\src\setup\LoadingWindow.java와 맞춤.
using System;
using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;
using System.Windows.Forms;

static class Loading
{
    const string Title = "출석 관리 프로그램 설치";

    [DllImport("user32.dll")]
    static extern bool SetProcessDPIAware();

    [STAThread]
    static int Main(string[] args)
    {
        try { SetProcessDPIAware(); } catch (Exception) { }
        Application.EnableVisualStyles();

        if (args.Length > 0 && args[0] == "--extract-failed")
        {
            MessageBox.Show("설치 파일의 압축을 풀지 못했습니다.\r\n디스크(C:) 여유 공간을 확인한 뒤 다시 실행해주세요.",
                Title, MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }

        string readyFile = args.Length > 0 ? args[0] : null;
        if (IsReady(readyFile)) return 0; // 이미 설치 프로그램이 떠 있으면 창을 띄우지 않음
        Application.Run(new LoadingForm(readyFile));
        return 0;
    }

    static bool IsReady(string readyFile)
    {
        return !string.IsNullOrEmpty(readyFile) && File.Exists(readyFile);
    }

    sealed class LoadingForm : Form
    {
        readonly string readyFile;
        readonly DateTime started = DateTime.Now;
        readonly Timer timer = new Timer();

        public LoadingForm(string readyFile)
        {
            this.readyFile = readyFile;
            Text = Title;
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            ShowInTaskbar = true;
            TopMost = true;
            AutoScaleMode = AutoScaleMode.Dpi;
            AutoScaleDimensions = new SizeF(96F, 96F);
            BackColor = Color.FromArgb(209, 213, 219); // 테두리 색
            Padding = new Padding(1);
            ClientSize = new Size(282, 92);

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

            ProgressBar bar = new ProgressBar();
            bar.Style = ProgressBarStyle.Marquee;
            bar.MarqueeAnimationSpeed = 30;
            bar.SetBounds(30, 56, 220, 8);
            inner.Controls.Add(bar);

            timer.Interval = 150;
            timer.Tick += delegate
            {
                if (IsReady(readyFile) || (DateTime.Now - started).TotalMinutes >= 3)
                {
                    timer.Stop();
                    Close();
                    try { if (IsReady(readyFile)) File.Delete(readyFile); } catch (Exception) { }
                }
            };
            Shown += delegate { timer.Start(); };
        }
    }
}
