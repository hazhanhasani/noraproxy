using System.IO;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text.Json;

namespace NoraProxy.Windows.Core;

/// <summary>
/// Encrypt subscription bearer URLs and profile secrets with Windows DPAPI,
/// CurrentUser scope. Never store these in repository, logs or unencrypted JSON.
/// </summary>
public sealed class UserSecretStore
{
    private readonly string _path;

    public UserSecretStore(string? path = null) => _path = path ?? Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "NoraProxy", "profiles.dpapi");

    public IReadOnlyList<SubscriptionEntry> Load()
    {
        if (!File.Exists(_path)) return Array.Empty<SubscriptionEntry>();
        var blob = File.ReadAllBytes(_path);
        var bytes = Unprotect(blob);
        try
        {
            return JsonSerializer.Deserialize<List<SubscriptionEntry>>(bytes)
                ?? new List<SubscriptionEntry>();
        }
        finally { CryptographicOperations.ZeroMemory(bytes); }
    }

    public void Save(IEnumerable<SubscriptionEntry> profiles)
    {
        var serialized = JsonSerializer.SerializeToUtf8Bytes(profiles);
        byte[] encrypted;
        try { encrypted = Protect(serialized); }
        finally { CryptographicOperations.ZeroMemory(serialized); }

        var parent = Path.GetDirectoryName(_path)
            ?? throw new InvalidOperationException("Invalid local store path");
        Directory.CreateDirectory(parent);
        var temp = _path + ".tmp-" + Guid.NewGuid().ToString("N");
        try
        {
            File.WriteAllBytes(temp, encrypted);
            File.Move(temp, _path, overwrite: true);
        }
        finally
        {
            CryptographicOperations.ZeroMemory(encrypted);
            if (File.Exists(temp)) File.Delete(temp);
        }
    }

    private const int UiForbidden = 0x1;

    [StructLayout(LayoutKind.Sequential)]
    private struct DataBlob
    {
        public int Size;
        public IntPtr Data;
    }

    [DllImport("crypt32.dll", EntryPoint = "CryptProtectData", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CryptProtectData(
        ref DataBlob input, string? description, IntPtr entropy, IntPtr reserved,
        IntPtr prompt, int flags, out DataBlob output);

    [DllImport("crypt32.dll", EntryPoint = "CryptUnprotectData", CharSet = CharSet.Unicode, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CryptUnprotectData(
        ref DataBlob input, IntPtr description, IntPtr entropy, IntPtr reserved,
        IntPtr prompt, int flags, out DataBlob output);

    [DllImport("kernel32.dll", EntryPoint = "LocalFree", SetLastError = true)]
    private static extern IntPtr LocalFree(IntPtr handle);

    private static byte[] Protect(byte[] data) => Transform(data, decrypt: false);
    private static byte[] Unprotect(byte[] data) => Transform(data, decrypt: true);

    private static byte[] Transform(byte[] inputBytes, bool decrypt)
    {
        if (!OperatingSystem.IsWindows())
            throw new PlatformNotSupportedException("DPAPI requires Windows");
        var memory = Marshal.AllocHGlobal(inputBytes.Length);
        try
        {
            Marshal.Copy(inputBytes, 0, memory, inputBytes.Length);
            var input = new DataBlob { Size = inputBytes.Length, Data = memory };
            DataBlob output;
            bool ok = decrypt
                ? CryptUnprotectData(ref input, IntPtr.Zero, IntPtr.Zero,
                    IntPtr.Zero, IntPtr.Zero, UiForbidden, out output)
                : CryptProtectData(ref input, "NoraProxy user subscriptions", IntPtr.Zero,
                    IntPtr.Zero, IntPtr.Zero, UiForbidden, out output);
            if (!ok) throw new Win32Exception(Marshal.GetLastWin32Error(),
                "Unable to process Windows-encrypted NoraProxy profiles");
            try
            {
                var bytes = new byte[output.Size];
                Marshal.Copy(output.Data, bytes, 0, output.Size);
                return bytes;
            }
            finally { LocalFree(output.Data); }
        }
        finally
        {
            // Erase the unmanaged plaintext before releasing the memory.
            for (int i = 0; i < inputBytes.Length; i++) Marshal.WriteByte(memory, i, 0);
            Marshal.FreeHGlobal(memory);
        }
    }
}

