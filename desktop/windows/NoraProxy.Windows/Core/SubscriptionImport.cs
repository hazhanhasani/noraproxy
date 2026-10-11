namespace NoraProxy.Windows.Core;

/// <summary>Strict offline importer: no network or DNS resolution is performed.</summary>
public static class SubscriptionImport
{
    private static readonly HashSet<string> Protocols = new(StringComparer.OrdinalIgnoreCase)
    {
        "vless", "vmess", "trojan", "ss", "hysteria2", "hy2", "tuic"
    };

    public static IReadOnlyList<SubscriptionEntry> Parse(string input)
    {
        if (string.IsNullOrWhiteSpace(input))
            throw new ArgumentException("لینک اشتراک یا کانفیگ را وارد کنید.");
        if (input.Length > 65_536)
            throw new ArgumentException("متن ورودی بیش از حد طولانی است.");

        var lines = input.Split('\n', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);
        if (lines.Length > 500)
            throw new ArgumentException("در هر بار حداکثر ۵۰۰ ورودی پشتیبانی می‌شود.");

        var entries = new List<SubscriptionEntry>();
        var unique = new HashSet<string>(StringComparer.Ordinal);
        foreach (var raw in lines)
        {
            var value = raw.TrimEnd('\r').Trim();
            if (value.Any(char.IsControl))
                throw new ArgumentException("متن دارای کاراکتر نامعتبر است.");
            if (!Uri.TryCreate(value, UriKind.Absolute, out var uri))
                throw new ArgumentException("آدرس اشتراک یا کانفیگ معتبر نیست.");

            var scheme = uri.Scheme.ToLowerInvariant();
            var isHttps = scheme == Uri.UriSchemeHttps;
            if (!isHttps && !Protocols.Contains(scheme))
                throw new ArgumentException("فقط اشتراک HTTPS و کانفیگ‌های شناخته‌شده قابل افزودن‌اند.");

            if (isHttps && (string.IsNullOrWhiteSpace(uri.Host) ||
                            !string.IsNullOrEmpty(uri.UserInfo) ||
                            uri.Host.Equals("localhost", StringComparison.OrdinalIgnoreCase) ||
                            uri.Host.EndsWith(".localhost", StringComparison.OrdinalIgnoreCase)))
                throw new ArgumentException("آدرس اشتراک HTTPS معتبر نیست.");

            if (!unique.Add(value)) continue;
            var count = entries.Count + 1;
            var kind = isHttps ? "subscription" : scheme;
            var display = isHttps ? $"اشتراک {count}" : $"کانفیگ {scheme.ToUpperInvariant()} {count}";
            entries.Add(new SubscriptionEntry(Guid.NewGuid(), kind, display, value, DateTimeOffset.UtcNow));
        }
        if (entries.Count == 0) throw new ArgumentException("ورودی قابل افزودن پیدا نشد.");
        return entries;
    }
}

