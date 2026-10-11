namespace NoraProxy.Windows.Core;

/// <summary>
/// Boundary for the FUTURE Windows Xray/TUN integration. Avoid spawning a
/// downloaded executable or manipulating Windows routes in the UI code.
/// </summary>
public interface IVpnEngine : IAsyncDisposable
{
    bool IsAvailable { get; }
    Task ConnectAsync(SubscriptionEntry profile, CancellationToken cancellationToken);
    Task DisconnectAsync(CancellationToken cancellationToken);
}

/// <summary>Fail closed until a native Windows engine and verified TUN driver exist.</summary>
public sealed class UnavailableVpnEngine : IVpnEngine
{
    public bool IsAvailable => false;
    public Task ConnectAsync(SubscriptionEntry profile, CancellationToken cancellationToken) =>
        Task.FromException(new NotSupportedException("Windows VPN engine is not implemented yet."));
    public Task DisconnectAsync(CancellationToken cancellationToken) => Task.CompletedTask;
    public ValueTask DisposeAsync() => ValueTask.CompletedTask;
}

