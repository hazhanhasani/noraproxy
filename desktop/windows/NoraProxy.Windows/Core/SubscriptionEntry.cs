namespace NoraProxy.Windows.Core;

/// <summary>A local import. Entries do not imply a server is connected or verified.</summary>
public sealed record SubscriptionEntry(
    Guid Id, string Kind, string DisplayName, string Value, DateTimeOffset ImportedAt);

