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
Console.WriteLine("NoraProxy Windows bootstrap: ALL 13 assertions/groups PASSED.");

