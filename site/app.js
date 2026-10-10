/* NoraProxy official site: progressive enhancement only, no analytics or cookies. */
(() => {
  "use strict";

  const repo = "hazhanhasani/noraproxy";
  const releasePage = "https://github.com/" + repo + "/releases/latest";
  const releasePrefix = "https://github.com/" + repo + "/releases/download/";
  const menuButton = document.querySelector(".menu-toggle");
  const mobileMenu = document.getElementById("mobile-menu");
  const versionEl = document.getElementById("release-version");
  const sizeEl = document.getElementById("release-size");
  const dateEl = document.getElementById("release-date");
  const downloadLinks = [
    document.getElementById("hero-download"),
    document.getElementById("release-download")
  ];
  const noteEl = document.getElementById("release-note");

  function setMenu(open) {
    if (!menuButton || !mobileMenu) return;
    menuButton.setAttribute("aria-expanded", String(open));
    menuButton.setAttribute("aria-label", open ? "بستن منو" : "بازکردن منو");
    mobileMenu.hidden = !open;
  }

  if (menuButton && mobileMenu) {
    menuButton.addEventListener("click", () => {
      setMenu(menuButton.getAttribute("aria-expanded") !== "true");
    });

    mobileMenu.querySelectorAll("a").forEach((link) => {
      link.addEventListener("click", () => setMenu(false));
    });

    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") setMenu(false);
    });

    document.addEventListener("click", (event) => {
      if (menuButton.getAttribute("aria-expanded") !== "true") return;
      if (!event.target.closest(".site-header")) setMenu(false);
    });

    const desktopQuery = window.matchMedia("(min-width: 901px)");
    desktopQuery.addEventListener?.("change", (event) => {
      if (event.matches) setMenu(false);
    });
  }

  const yearEl = document.getElementById("year");
  if (yearEl) {
    yearEl.textContent = new Intl.NumberFormat("fa-IR", {
      useGrouping: false
    }).format(new Date().getFullYear());
  }

  function safeGithubAsset(asset) {
    return asset &&
      typeof asset.browser_download_url === "string" &&
      asset.browser_download_url.startsWith(releasePrefix) &&
      typeof asset.name === "string";
  }

  async function loadLatestRelease() {
    if (!versionEl || !sizeEl) return;

    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 7000);
    let data;
    try {
      const response = await fetch(
        "https://api.github.com/repos/" + repo + "/releases/latest",
        {
          headers: { "Accept": "application/vnd.github+json" },
          signal: controller.signal,
          cache: "no-store"
        }
      );
      if (!response.ok) return;
      data = await response.json();
    } catch (_) {
      // Fallback links remain functional if API is blocked or rate-limited.
      return;
    } finally {
      window.clearTimeout(timeout);
    }

    if (!data || data.draft || data.prerelease ||
        typeof data.tag_name !== "string" ||
        !/^v?\d+\.\d+\.\d+$/.test(data.tag_name)) return;

    const assets = Array.isArray(data.assets) ? data.assets : [];
    const apk = assets.find((asset) =>
      safeGithubAsset(asset) && /\.apk$/i.test(asset.name));
    const checksum = assets.find((asset) =>
      safeGithubAsset(asset) && /\.apk\.sha256$/i.test(asset.name));

    versionEl.textContent = "نسخه " + data.tag_name;

    if (apk) {
      const fileSizeMb = (Number(apk.size) || 0) / (1024 * 1024);
      sizeEl.textContent = fileSizeMb > 0
        ? new Intl.NumberFormat("fa-IR", { maximumFractionDigits: 1 }).format(fileSizeMb) + " مگابایت · APK"
        : "فایل رسمی APK";

      downloadLinks.forEach((link) => {
        if (!link) return;
        link.href = apk.browser_download_url;
        link.title = "دانلود نسخه رسمی " + data.tag_name;
      });
    } else {
      sizeEl.textContent = "مشاهده فایل‌های انتشار در GitHub";
      downloadLinks.forEach((link) => {
        if (link) link.href = releasePage;
      });
    }

    if (dateEl && data.published_at) {
      const date = new Date(data.published_at);
      if (!Number.isNaN(date.getTime())) {
        dateEl.textContent = "انتشار: " +
          new Intl.DateTimeFormat("fa-IR", {
            year: "numeric", month: "long", day: "numeric"
          }).format(date);
      }
    }

    if (checksum && noteEl) {
      const separator = document.createTextNode(" · ");
      const checkLink = document.createElement("a");
      checkLink.href = checksum.browser_download_url;
      checkLink.target = "_blank";
      checkLink.rel = "noopener noreferrer";
      checkLink.textContent = "دریافت SHA-256";
      checkLink.className = "checksum-link";
      noteEl.append(separator, checkLink);
    }
  }

  loadLatestRelease();
})();