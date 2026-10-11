using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Controls;
using NoraProxy.Windows.Core;

namespace NoraProxy.Windows;

public partial class MainWindow : Window
{
    private readonly UserSecretStore _store = new();
    private readonly IVpnEngine _engine = new UnavailableVpnEngine();
    private bool _storageAvailable = true;
    public ObservableCollection<SubscriptionEntry> Entries { get; } = new();

    public MainWindow()
    {
        InitializeComponent();
        DataContext = this;
        ConnectButton.IsEnabled = _engine.IsAvailable;
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

    private void UpdateCounts()
    {
        HomeSubscriptionCount.Text = Entries.Count.ToString("N0", System.Globalization.CultureInfo.GetCultureInfo("fa-IR"));
        GroupCounter.Text = Entries.Count == 0 ? "هنوز اشتراکی افزوده نشده" : $"{Entries.Count} ورودی ذخیره‌شده";
    }
}

