using System.Collections.ObjectModel;
using System.Windows.Media;
using System.Windows;
using System.Windows.Controls;
using NoraProxy.Windows.Core;

namespace NoraProxy.Windows;

public partial class MainWindow : Window
{
    private readonly UserSecretStore _store = new();
    private readonly IVpnEngine _engine = new UnavailableVpnEngine();
    private readonly WindowsUpdateService _updates = new();
    private readonly CancellationTokenSource _updateCancellation = new();
    private WindowsUpdate? _availableUpdate;
    private string? _verifiedInstaller;
    private bool _updateBusy;
    private bool _storageAvailable = true;
    public ObservableCollection<SubscriptionEntry> Entries { get; } = new();

    public MainWindow()
    {
        InitializeComponent();
        DataContext = this;
        ConnectButton.IsEnabled = _engine.IsAvailable;
        CurrentWinVersion.Text = "نسخه نصب‌شده: " + WindowsUpdateService.CurrentVersion;
        ShowPage(HomePage);
        Closed += (_, _) =>
        {
            _updateCancellation.Cancel();
            _updates.Dispose();
            _updateCancellation.Dispose();
        };
        try
        {
            foreach (var entry in _store.Load()) Entries.Add(entry);
        }
        catch (Exception)
        {
            _storageAvailable = false;
            ImportButton.IsEnabled = false;
            ImportFeedback.Text = "خواندن اطلاعات رمزگذاری‌شده ممکن نیست. برای جلوگیری از حذف داده‌ها، ذخیره غیرفعال شد.";
        }
        UpdateCounts();
    }

    private void ShowPage(FrameworkElement page)
    {
        HomePage.Visibility = Visibility.Collapsed;
        LocationsPage.Visibility = Visibility.Collapsed;
        SubscriptionsPage.Visibility = Visibility.Collapsed;
        SettingsPage.Visibility = Visibility.Collapsed;
        page.Visibility = Visibility.Visible;
        UpdateCounts();
        SetNavigationState();
    }

    private void SetNavigationState()
    {
        HomeNavButton.Background = HomePage.IsVisible
            ? new SolidColorBrush(Color.FromRgb(42, 78, 118))
            : new SolidColorBrush(Color.FromRgb(22, 40, 62));
        LocationsNavButton.Background = LocationsPage.IsVisible
            ? new SolidColorBrush(Color.FromRgb(42, 78, 118))
            : new SolidColorBrush(Color.FromRgb(22, 40, 62));
        SubscriptionsNavButton.Background = SubscriptionsPage.IsVisible
            ? new SolidColorBrush(Color.FromRgb(42, 78, 118))
            : new SolidColorBrush(Color.FromRgb(22, 40, 62));
        SettingsNavButton.Background = SettingsPage.IsVisible
            ? new SolidColorBrush(Color.FromRgb(42, 78, 118))
            : new SolidColorBrush(Color.FromRgb(22, 40, 62));
    }

    private void Home_Click(object sender, RoutedEventArgs e) => ShowPage(HomePage);
    private void Locations_Click(object sender, RoutedEventArgs e) => ShowPage(LocationsPage);
    private void Subscriptions_Click(object sender, RoutedEventArgs e) => ShowPage(SubscriptionsPage);
    private void Settings_Click(object sender, RoutedEventArgs e) => ShowPage(SettingsPage);

    private void Import_Click(object sender, RoutedEventArgs e)
    {
        if (!_storageAvailable) return;
        try
        {
            var items = SubscriptionImport.Parse(ImportInput.Text);
            var newItems = items.Where(item => !Entries.Any(existing =>
                existing.Kind == item.Kind && existing.Value == item.Value)).ToList();
            if (newItems.Count == 0)
            {
                ImportFeedback.Text = "این ورودی‌ها قبلاً ذخیره شده‌اند.";
                return;
            }
            var updated = Entries.Concat(newItems).ToList();
            _store.Save(updated); // Disk commit FIRST; never claim an unsaved import.
            foreach (var item in newItems) Entries.Add(item);
            ImportInput.Clear();
            ImportFeedback.Text = $"{newItems.Count} مورد رمزگذاری و ذخیره شد؛ اتصال هنوز فعال نیست.";
            UpdateCounts();
        }
        catch (ArgumentException ex) { ImportFeedback.Text = ex.Message; }
        catch (Exception)
        {
            ImportFeedback.Text = "ذخیره امن ناموفق بود؛ هیچ تغییری در فهرست ایجاد نشد.";
        }
    }

    private void Remove_Click(object sender, RoutedEventArgs e)
    {
        if (!_storageAvailable || sender is not Button { Tag: SubscriptionEntry target }) return;
        var choice = MessageBox.Show(this, "این ورودی از حافظه امن NoraProxy حذف شود؟",
            "تأیید حذف", MessageBoxButton.YesNo, MessageBoxImage.Question);
        if (choice != MessageBoxResult.Yes) return;
        try
        {
            var remaining = Entries.Where(item => item.Id != target.Id).ToList();
            _store.Save(remaining);
            Entries.Remove(target);
            ImportFeedback.Text = "ورودی انتخاب‌شده حذف شد.";
            UpdateCounts();
        }
        catch (Exception) { ImportFeedback.Text = "حذف امن ناموفق بود؛ اطلاعات حفظ شد."; }
    }

    private void SetUpdateBusy(bool busy)
    {
        _updateBusy = busy;
        CheckUpdateButton.IsEnabled = !busy;
        DownloadUpdateButton.IsEnabled = !busy && _availableUpdate is not null
            && _verifiedInstaller is null;
        InstallUpdateButton.IsEnabled = !busy && _availableUpdate is not null
            && _verifiedInstaller is not null;
    }

    private async void CheckUpdate_Click(object sender, RoutedEventArgs e)
    {
        if (_updateBusy) return;
        _availableUpdate = null;
        _verifiedInstaller = null;
        UpdateStatus.Text = "در حال بررسی آخرین نسخه رسمی ویندوز…";
        UpdateProgress.Visibility = Visibility.Collapsed;
        SetUpdateBusy(true);
        try
        {
            _availableUpdate = await _updates.CheckAsync(_updateCancellation.Token);
            UpdateStatus.Text = _availableUpdate is null
                ? "نسخه ویندوز شما به‌روز است."
                : "نسخه جدید Windows Preview " + _availableUpdate.Version +
                  " آماده دانلود است.";
        }
        catch (OperationCanceledException) { UpdateStatus.Text = "بررسی متوقف شد."; }
        catch (Exception)
        {
            UpdateStatus.Text = "بررسی نسخه جدید ناموفق بود. اینترنت و دسترسی به GitHub را بررسی کنید.";
        }
        finally { SetUpdateBusy(false); }
    }

    private async void DownloadUpdate_Click(object sender, RoutedEventArgs e)
    {
        if (_updateBusy || _availableUpdate is null) return;
        var selected = _availableUpdate;
        _verifiedInstaller = null;
        UpdateProgress.Value = 0;
        UpdateProgress.Visibility = Visibility.Visible;
        UpdateStatus.Text = "در حال دریافت فایل نصب از انتشار رسمی ویندوز…";
        SetUpdateBusy(true);
        try
        {
            var progress = new Progress<int>(value =>
            {
                UpdateProgress.Value = value;
                UpdateStatus.Text = "در حال دانلود و بررسی فایل نصب: " + value + "٪";
            });
            _verifiedInstaller = await _updates.DownloadVerifiedAsync(
                selected, progress, _updateCancellation.Token);
            UpdateStatus.Text = "دانلود کامل شد و SHA-256 مطابقت دارد. نصب نیاز به تأیید شما دارد.";
        }
        catch (OperationCanceledException) { UpdateStatus.Text = "دانلود متوقف شد."; }
        catch (Exception)
        {
            _verifiedInstaller = null;
            UpdateStatus.Text = "دانلود یا بررسی فایل ناموفق بود؛ نصب غیرفعال است.";
        }
        finally { SetUpdateBusy(false); }
    }

    private void InstallUpdate_Click(object sender, RoutedEventArgs e)
    {
        if (_updateBusy || _availableUpdate is null || _verifiedInstaller is null) return;
        var answer = MessageBox.Show(this,
            "نسخه جدید NoraProxy Windows Preview " + _availableUpdate.Version +
            " نصب شود؟\n\nفایل با SHA-256 منتشرشده در GitHub تطبیق داده شده، " +
            "اما هنوز امضای دیجیتال ناشر ویندوز ندارد.\n" +
            "نصب‌کننده باز می‌شود و داده‌های محلی شما حفظ می‌شوند.",
            "تأیید نصب نسخه جدید", MessageBoxButton.YesNo, MessageBoxImage.Warning);
        if (answer != MessageBoxResult.Yes) return;
        try
        {
            WindowsUpdateService.OpenInstaller(_verifiedInstaller, _availableUpdate);
            // Windows must close the old executable before Inno Setup replaces it.
            Application.Current.Shutdown();
        }
        catch (Exception)
        {
            UpdateStatus.Text = "اجرای نصب‌کننده ناموفق بود. فایل اجرایی را بررسی کنید.";
        }
    }

    private void UpdateCounts()
    {
        GroupCounter.Text = Entries.Count == 0 ? "هنوز اشتراکی افزوده نشده" : $"{Entries.Count} ورودی ذخیره‌شده";
    }
}

