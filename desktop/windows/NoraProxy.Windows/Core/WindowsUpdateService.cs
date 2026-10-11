using System;
using System.Buffers;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using System.Diagnostics;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace NoraProxy.Windows.Core;

/// <summary>
/// Manual per-user upgrade from this repository's Windows PREVIEW releases.
/// GitHub checksums detect corrupt downloads; unsigned installers are not
/// publisher-authenticated. Never silently execute untrusted or remote code.
/// </summary>
public sealed record WindowsUpdate(Version Version, string FileName, Uri InstallerUrl, Uri ChecksumUrl);

public sealed class WindowsUpdateService : IDisposable
{
    public static readonly Version CurrentVersion = new(0, 1, 2);
    private const string Repository = "hazhanhasani/noraproxy";
    private const string Api = "https://api.github.com/repos/" + Repository + "/releases?per_page=50";
    private const long MaxInstallerBytes = 250L * 1024 * 1024;
    private readonly HttpClient _http;
    private readonly bool _ownsClient;

    public WindowsUpdateService(HttpClient? client = null)
    {
        _ownsClient = client is null;
        _http = client ?? new HttpClient(new HttpClientHandler
        {
            AllowAutoRedirect = true,
            MaxAutomaticRedirections = 5
        });
        _http.Timeout = TimeSpan.FromMinutes(8);
        _http.DefaultRequestHeaders.UserAgent.ParseAdd("NoraProxy-Windows-Updater/0.1.2");
        _http.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/vnd.github+json"));
    }

    /// <summary>Only Windows preview tags with matching installer AND hash files.</summary>
    public static WindowsUpdate? FindLatest(string json, Version installed)
    {
        using var doc = JsonDocument.Parse(json);
        if (doc.RootElement.ValueKind != JsonValueKind.Array)
            throw new InvalidDataException("Invalid GitHub releases response.");
        WindowsUpdate? latest = null;
        foreach (var release in doc.RootElement.EnumerateArray())
        {
            if (release.ValueKind != JsonValueKind.Object
                || !release.TryGetProperty("tag_name", out var tagNode)
                || tagNode.ValueKind != JsonValueKind.String) continue;
            var tag = tagNode.GetString() ?? "";
            var match = Regex.Match(tag, @"^windows-preview-v(\d+)\.(\d+)\.(\d+)$",
                RegexOptions.CultureInvariant);
            if (!match.Success || !Version.TryParse(match.Groups[1].Value + "."
                + match.Groups[2].Value + "." + match.Groups[3].Value, out var version)
                || version <= installed || (latest is not null && version <= latest.Version)) continue;
            if (release.TryGetProperty("draft", out var draft) && draft.ValueKind == JsonValueKind.True) continue;
            if (!release.TryGetProperty("assets", out var assets) || assets.ValueKind != JsonValueKind.Array) continue;

            string installer = $"NoraProxy-Setup-{version}-win-x64.exe";
            string checksum = installer + ".sha256";
            string baseUrl = $"https://github.com/{Repository}/releases/download/{tag}/";
            var seenInstaller = false;
            var seenChecksum = false;
            foreach (var asset in assets.EnumerateArray())
            {
                if (!asset.TryGetProperty("name", out var nameValue)
                    || !asset.TryGetProperty("browser_download_url", out var urlValue)
                    || nameValue.ValueKind != JsonValueKind.String
                    || urlValue.ValueKind != JsonValueKind.String) continue;
                var name = nameValue.GetString();
                var uri = urlValue.GetString();
                if (name == installer && uri == baseUrl + installer) seenInstaller = true;
                if (name == checksum && uri == baseUrl + checksum) seenChecksum = true;
            }
            if (seenInstaller && seenChecksum)
                latest = new WindowsUpdate(version, installer,
                    new Uri(baseUrl + installer), new Uri(baseUrl + checksum));
        }
        return latest;
    }

    public async Task<WindowsUpdate?> CheckAsync(CancellationToken ct = default)
    {
        using var response = await _http.GetAsync(Api, HttpCompletionOption.ResponseHeadersRead, ct);
        response.EnsureSuccessStatusCode();
        if (response.Content.Headers.ContentLength is > 2_000_000)
            throw new InvalidDataException("GitHub releases list is unexpectedly large.");
        var body = await response.Content.ReadAsStringAsync(ct);
        if (body.Length > 2_000_000) throw new InvalidDataException("GitHub response too long.");
        return FindLatest(body, CurrentVersion);
    }

    public static string ParseChecksum(string text, string installerName)
    {
        var pattern = @"\A([0-9a-fA-F]{64})[ \t]+\*?" + Regex.Escape(installerName) + @"\r?\n?\z";
        var match = Regex.Match(text, pattern, RegexOptions.CultureInvariant);
        // Explicitly avoid accepting the checksum of a different file or extra lines.
        if (!match.Success) throw new InvalidDataException("Invalid release SHA-256 manifest.");
        return match.Groups[1].Value.ToLowerInvariant();
    }

    public static bool MatchesChecksum(string hashFromManifest, string localSha256)
    {
        if (!Regex.IsMatch(hashFromManifest, @"\A[0-9a-fA-F]{64}\z")
            || !Regex.IsMatch(localSha256, @"\A[0-9a-fA-F]{64}\z"))
            return false;
        return CryptographicOperations.FixedTimeEquals(
            Convert.FromHexString(hashFromManifest), Convert.FromHexString(localSha256));
    }

    public async Task<string> DownloadVerifiedAsync(WindowsUpdate update,
        IProgress<int>? progress = null, CancellationToken ct = default)
    {
        if (update.Version <= CurrentVersion || !IsOfficialDownload(update))
            throw new InvalidDataException("Untrusted or older Windows update rejected.");
        using var hashResponse = await _http.GetAsync(update.ChecksumUrl,
            HttpCompletionOption.ResponseHeadersRead, ct);
        hashResponse.EnsureSuccessStatusCode();
        if (hashResponse.Content.Headers.ContentLength is > 4096)
            throw new InvalidDataException("Invalid SHA-256 file size.");
        var hashBody = await hashResponse.Content.ReadAsStringAsync(ct);
        if (hashBody.Length > 4096) throw new InvalidDataException("Invalid SHA-256 file length.");
        var expected = ParseChecksum(hashBody, update.FileName);

        var destination = Path.Combine(Environment.GetFolderPath(
            Environment.SpecialFolder.LocalApplicationData), "NoraProxy", "updates");
        Directory.CreateDirectory(destination);
        var finalPath = Path.Combine(destination, update.FileName);
        var tempPath = finalPath + "." + Guid.NewGuid().ToString("N") + ".part";

        try
        {
            using var response = await _http.GetAsync(update.InstallerUrl,
                HttpCompletionOption.ResponseHeadersRead, ct);
            response.EnsureSuccessStatusCode();
            var length = response.Content.Headers.ContentLength;
            if (length is > MaxInstallerBytes)
                throw new InvalidDataException("Installer exceeds the expected size limit.");
            await using (var output = new FileStream(tempPath, FileMode.CreateNew,
                FileAccess.Write, FileShare.None, 1024 * 128, FileOptions.Asynchronous))
            await using (var input = await response.Content.ReadAsStreamAsync(ct))
            {
                var buffer = ArrayPool<byte>.Shared.Rent(128 * 1024);
                long received = 0;
                try
                {
                    while (true)
                    {
                        var read = await input.ReadAsync(buffer.AsMemory(), ct);
                        if (read == 0) break;
                        received += read;
                        if (received > MaxInstallerBytes)
                            throw new InvalidDataException("Installer exceeds the size limit.");
                        await output.WriteAsync(buffer.AsMemory(0, read), ct);
                        if (length is > 0)
                            progress?.Report((int)Math.Min(100, received * 100 / length.Value));
                    }
                }
                finally { ArrayPool<byte>.Shared.Return(buffer); }
                if (received < 1_000_000 || (length.HasValue && received != length.Value))
                    throw new InvalidDataException("Incomplete Windows installer download.");
            }
            await using (var input = File.OpenRead(tempPath))
            {
                var actual = Convert.ToHexString(await SHA256.HashDataAsync(input, ct));
                if (!MatchesChecksum(expected, actual))
                    throw new InvalidDataException("Installer SHA-256 verification failed.");
            }
            File.Move(tempPath, finalPath, overwrite: true);
            progress?.Report(100);
            return finalPath;
        }
        finally { if (File.Exists(tempPath)) File.Delete(tempPath); }
    }

    public static bool IsOfficialDownload(WindowsUpdate item)
    {
        var tag = $"windows-preview-v{item.Version}";
        var name = $"NoraProxy-Setup-{item.Version}-win-x64.exe";
        var prefix = $"https://github.com/{Repository}/releases/download/{tag}/";
        return item.Version > new Version(0, 0, 0)
            && item.FileName == name
            && item.InstallerUrl.AbsoluteUri == prefix + name
            && item.ChecksumUrl.AbsoluteUri == prefix + name + ".sha256";
    }

    /// <summary>Called only from the Install button after a user's confirmation.</summary>
    public static void OpenInstaller(string verifiedPath, WindowsUpdate update)
    {
        var directory = Path.Combine(Environment.GetFolderPath(
            Environment.SpecialFolder.LocalApplicationData), "NoraProxy", "updates");
        if (!IsOfficialDownload(update)
            || !string.Equals(Path.GetFullPath(verifiedPath),
                Path.Combine(directory, update.FileName), StringComparison.OrdinalIgnoreCase)
            || !File.Exists(verifiedPath))
            throw new InvalidDataException("The verified installer is unavailable.");
        if (Process.Start(new ProcessStartInfo(verifiedPath) { UseShellExecute = true }) is null)
            throw new InvalidOperationException("Windows did not start the installer.");
    }

    public void Dispose() { if (_ownsClient) _http.Dispose(); }
}
