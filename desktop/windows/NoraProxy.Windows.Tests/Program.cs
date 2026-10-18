using NoraProxy.Windows.Core;
using System.Text;

static void Assert(bool value, string message)
{
    if (!value) throw new Exception(message);
}

static void Reject(string input)
{
    try { SubscriptionImport.Parse(input); }
    catch (ArgumentException) { return; }
    throw new Exception($"Invalid import was accepted: {input[..Math.Min(16, input.Length)]}");
}

var parsed = SubscriptionImport.Parse(
    "https://service.example/path?access_token=PRIVATE\n" +
    "vless://uuid@edge.example:443?security=tls#Germany\n" +
    "trojan://password@edge.example:443#Netherlands\n" +
    "https://service.example/path?access_token=PRIVATE");
Assert(parsed.Count == 3, "Duplicate removal and mixed import");
Assert(parsed[0].Kind == "subscription", "HTTPS subscription classification");
Assert(parsed[1].Kind == "vless" && parsed[2].Kind == "trojan", "Share link classification");
Assert(!parsed[0].DisplayName.Contains("PRIVATE"), "Secret leaked in display name");
Reject("http://insecure.example/sub");
Reject("file:///C:/secret");
Reject("https://localhost/private");
Reject("https://user:pass@service.example/file");
Reject("bad input");
Reject(new string('a', 65_537));
Assert(!new UnavailableVpnEngine().IsAvailable, "Unimplemented engine must fail closed");

var folder = Path.Combine(Path.GetTempPath(), "noraproxy-test-" + Guid.NewGuid().ToString("N"));
try
{
    var path = Path.Combine(folder, "profiles.dpapi");
    var store = new UserSecretStore(path);
    Assert(store.Load().Count == 0, "New local encrypted store must be empty");
    store.Save(parsed);
    var ciphertext = File.ReadAllBytes(path);
    Assert(!Encoding.UTF8.GetString(ciphertext).Contains("PRIVATE"), "Subscription token exposed in disk bytes");
    var restored = store.Load();
    Assert(restored.Count == 3 && restored[0].Value.Contains("PRIVATE"), "DPAPI roundtrip failed");
    ciphertext[^1] ^= 0x7f;
    File.WriteAllBytes(path, ciphertext);
    var tamperRejected = false;
    try { store.Load(); } catch (Exception) { tamperRejected = true; }
    Assert(tamperRejected, "Tampered profile was not rejected");
}
finally
{
    if (Directory.Exists(folder)) Directory.Delete(folder, recursive: true);
}
// Installer updates must come only from the Windows preview release namespace.
// Android releases and unexpected third-party download URLs are ignored.
string Fixture(string tag, bool withInstaller = true, bool withHash = true, bool tampered = false)
{
    var file = $"NoraProxy-Setup-{tag.Replace("windows-preview-v", "")}-win-x64.exe";
    var origin = $"https://github.com/hazhanhasani/noraproxy/releases/download/{tag}/";
    var assets = new List<object>();
    if (withInstaller)
        assets.Add(new { name = file, browser_download_url =
            (tampered ? "https://untrusted.example/installer.exe" : origin + file) });
    if (withHash)
        assets.Add(new { name = file + ".sha256", browser_download_url = origin + file + ".sha256" });
    return System.Text.Json.JsonSerializer.Serialize(new[]
    {
        new { tag_name = tag, draft = false, prerelease = true, assets }
    });
}
var installed = WindowsUpdateService.CurrentVersion;
Assert(installed == new Version(0, 1, 5), "Windows updater and application version must match.");
Assert(WindowsUpdateService.FindLatest(Fixture("windows-preview-v0.1.6"), installed)?.Version
    == new Version(0, 1, 6), "Discover a newer Windows-only release.");
Assert(WindowsUpdateService.FindLatest(Fixture("windows-preview-v0.1.1"), installed) is null,
    "Never downgrade.");
Assert(WindowsUpdateService.FindLatest(Fixture("v1.0.0"), installed) is null,
    "Never install an Android APK as a Windows update.");
Assert(WindowsUpdateService.FindLatest(Fixture("windows-preview-v0.1.6", tampered: true), installed) is null,
    "Reject a release asset that points away from the official GitHub repository.");
Assert(WindowsUpdateService.FindLatest(Fixture("windows-preview-v0.1.6", withHash: false), installed) is null,
    "Reject a release without an integrity manifest.");
var official = WindowsUpdateService.FindLatest(Fixture("windows-preview-v0.1.6"), installed)!;
Assert(WindowsUpdateService.IsOfficialDownload(official), "Official release download locator.");
var sha = new string('a', 64);
Assert(WindowsUpdateService.ParseChecksum(sha + "  " + official.FileName + "\n",
    official.FileName) == sha, "Parse official SHA-256 manifest.");
Assert(WindowsUpdateService.MatchesChecksum(sha, sha.ToUpperInvariant()), "Equal hashes compare.");
Assert(!WindowsUpdateService.MatchesChecksum(sha, new string('b', 64)), "Different hashes rejected.");
var malformedRejected = false;
try { WindowsUpdateService.ParseChecksum(sha + "  other-installer.exe\n", official.FileName); }
catch (InvalidDataException) { malformedRejected = true; }
Assert(malformedRejected, "Do not verify a different installer file.");
Console.WriteLine("NoraProxy Windows UI/update and DPAPI/import integration tests passed.");


