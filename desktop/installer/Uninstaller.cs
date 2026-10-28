// "Uninstall Ivy Wallet UAE.exe", installed next to "Ivy Wallet UAE.exe". It asks Windows Installer to remove the app,
// like "Uninstall Ivy Wallet UAE" in the Start menu and Windows Settings → Apps do.
//
// Built on Windows by the uninstaller task in build.gradle.kts, with the C# compiler that comes with Windows, so it
// only uses what C# 5 has. That task also writes UpgradeCode.cs, with the upgrade code from build.gradle.kts.
using System;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Forms;

internal static partial class Uninstaller
{
    private const string Title = "Uninstall Ivy Wallet UAE";

    // a product code is 38 characters with its { }, and Windows adds a 0 at the end
    private const int ProductCodeLength = 39;

    [DllImport("msi.dll", CharSet = CharSet.Unicode)]
    private static extern int MsiEnumRelatedProducts(
        string upgradeCode, int reserved, int index, StringBuilder product);

    [STAThread]
    private static int Main(string[] args)
    {
        Application.EnableVisualStyles();
        // removes it without asking or showing anything, for testing
        bool quiet = Array.Exists(args, arg => string.Equals(arg, "/quiet", StringComparison.OrdinalIgnoreCase));

        // whichever version is installed: there's only ever one, as each new version replaces the one before
        StringBuilder product = new StringBuilder(ProductCodeLength);
        if (MsiEnumRelatedProducts(UpgradeCode, 0, 0, product) != 0)
        {
            if (!quiet)
            {
                MessageBox.Show("Ivy Wallet UAE isn't installed on this computer.", Title, MessageBoxButtons.OK,
                    MessageBoxIcon.Information);
            }
            return 1;
        }

        // Windows Installer asks if you're sure, then removes the app and this file with it, so this doesn't wait
        string msiexec = Path.Combine(Environment.SystemDirectory, "msiexec.exe");
        Process.Start(msiexec, "/x " + product + (quiet ? " /qn" : ""));
        return 0;
    }
}
