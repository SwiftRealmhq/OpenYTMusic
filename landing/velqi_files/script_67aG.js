/**
 * OpenYTMusic — Main JavaScript
 * Versión: 2.0.0
 * Descripción: Script principal de la landing de OpenYTMusic
 */

const APP_CONFIG = {
  repositories: {
    android: {
      repo: 'root-leo/OpenYTMusic',
      currentVersion: '0.5.0',
      downloadFormat: 'OpenYTMusic-0.5.0.apk',
      elements: {
        version: 'android-version-badge',
        download: 'android-download-btn',
        text: 'android-download-text',
        changelog: 'changelog-dialog',
        versions: 'versions-dialog',
        changelogContent: 'changelog-content',
        versionsList: 'versions-list'
      }
    },
    windows: {
      repo: 'root-leo/OpenYTMusic',
      currentVersion: '1.9.0',
      downloadFormat: 'OpenYTMusic-{version}.apk',
      elements: {
        version: 'currentVersionWindows',
        download: 'downloadBtnWindows',
        text: 'downloadTextWindows',
        changelog: 'changelogWindows',
        versions: 'versionsWindows',
        changelogContent: 'changelogContentWindows',
        versionsList: 'versionsListWindows'
      }
    }
  },
  urls: {
    api: { github: 'https://api.github.com/repos/' },
    demo: 'https://appetize.io/app/b_yb62tcjuqzqjvctnswv3krpnmm'
  },
  elements: {
    theme: { selector: 'themeSelector', icon: 'themeIcon' },
    language: { selector: 'language-btn', dialog: 'languageDialog', text: 'languageText' },
    warning: { button: 'triggerDialogButton', dialog: 'warningDialog', overlay: 'dialogOverlay', dismiss: 'dismissButton', proceed: 'proceedButton' },
    logo: 'logo'
  },
  checkInterval: 60 * 60 * 1000
};

class OpenYTMusicApp {
  constructor(config) {
    this.config = config;
    this.themeManager = new ThemeManager(config.elements.theme);
    this.languageManager = new LanguageManager(config.elements.language);
    this.dialogManager = new DialogManager(config.elements.warning, config.urls.demo);
    this.logoManager = new LogoManager(config.elements.logo);
    this.versionManager = new VersionManager(config.repositories);
    this.carouselManager = null;
  }

  init() {
    this.themeManager.init();
    this.languageManager.init();
    this.dialogManager.init();
    this.logoManager.init();
    this.versionManager.init();
    this.configureMarkdown();
    console.log('OpenYTMusic — landing inicializada correctamente');
  }

  configureMarkdown() {
    if (typeof marked !== 'undefined') {
      marked.setOptions({ breaks: true, gfm: true, headerIds: false, sanitize: false });
    }
  }
}

class ThemeManager {
  constructor(elements) {
    this.selectorId = elements.selector;
    this.iconId = elements.icon;
  }

  init() {
    const selector = document.getElementById(this.selectorId);
    const icon = document.getElementById(this.iconId);
    if (!selector || !icon) return;

    this.selector = selector;
    this.icon = icon;

    // Detectar tema actual basado en las clases del root
    const isLightMode = document.documentElement.classList.contains('light-mode');
    this.updateIcon(isLightMode ? 'light' : 'dark');

    selector.addEventListener('click', () => this.toggleTheme());
  }

  applyTheme(theme) {
    const root = document.documentElement;

    if (theme === 'light') {
      root.classList.add('light-mode');
      localStorage.setItem('theme', 'light');
    } else {
      root.classList.remove('light-mode');
      localStorage.setItem('theme', 'dark');
    }

    this.updateIcon(theme);

    // Disparar evento para que otros componentes se actualicen si es necesario
    window.dispatchEvent(new CustomEvent('themeChanged', { detail: { theme } }));
  }

  updateIcon(theme) {
    if (this.icon) {
      this.icon.textContent = theme === 'dark' ? 'light_mode' : 'dark_mode';
    }
  }

  toggleTheme() {
    const isLightMode = document.documentElement.classList.contains('light-mode');
    this.applyTheme(isLightMode ? 'dark' : 'light');
  }
}

class LanguageManager {
  constructor(elements) {
    this.selectorId = elements.selector;
    this.dialogId = elements.dialog;
    this.textId = elements.text;
  }

  init() {
    const selector = document.getElementById(this.selectorId);
    const dialog = document.getElementById(this.dialogId);
    const text = document.getElementById(this.textId);

    if (!selector || !dialog) return;

    // Abrir diálogo al hacer clic en el botón
    selector.addEventListener('click', () => {
      dialog.showModal();
    });

    // Cerrar diálogo con el botón X o el botón Cancelar
    const closeButtons = dialog.querySelectorAll('[data-close]');
    closeButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        dialog.close();
      });
    });

    // Cerrar al hacer clic fuera del diálogo
    dialog.addEventListener('click', (e) => {
      if (e.target === dialog) {
        dialog.close();
      }
    });

    // Configurar funciones globales para cambio de idioma (si existen)
    window.closeDialog = () => dialog.close();
    window.setLanguage = (language, url) => {
      if (text) text.textContent = language;
      dialog.close();
      window.location.href = url;
    };
  }
}

class DialogManager {
  constructor(elements, demoUrl) {
    this.elements = elements;
    this.demoUrl = demoUrl;
  }

  init() {
    const button = document.getElementById(this.elements.button);
    const dialog = document.getElementById(this.elements.dialog);
    const overlay = document.getElementById(this.elements.overlay);
    const dismissButton = document.getElementById(this.elements.dismiss);
    const proceedButton = document.getElementById(this.elements.proceed);
    if (!button || !dialog || !overlay) return;

    button.addEventListener('click', () => {
      dialog.showModal();
      overlay.style.display = 'block';
    });

    if (dismissButton) {
      dismissButton.addEventListener('click', () => {
        dialog.close();
        overlay.style.display = 'none';
      });
    }

    if (proceedButton) {
      proceedButton.addEventListener('click', () => {
        window.location.href = this.demoUrl;
      });
    }
  }
}

class LogoManager {
  constructor(element) {
    this.elementId = element;
  }

  init() {
    const logo = document.getElementById(this.elementId);
    if (!logo) return;
    logo.addEventListener('load', () => this.applyLogoEffects(logo));
  }

  applyLogoEffects(logo) {
    try {
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');
      const img = new Image();
      img.crossOrigin = 'Anonymous';
      img.src = logo.src;
      img.onload = () => {
        canvas.width = img.width;
        canvas.height = img.height;
        ctx.drawImage(img, 0, 0);
        try {
          const pixels = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
          let r = 0, g = 0, b = 0, total = 0;
          for (let i = 0; i < pixels.length; i += 4) { r += pixels[i]; g += pixels[i + 1]; b += pixels[i + 2]; total++; }
          logo.style.setProperty('--logo-glow-color', `rgb(${Math.floor(r / total)},${Math.floor(g / total)},${Math.floor(b / total)})`);
        } catch (e) {
          logo.style.setProperty('--logo-glow-color', 'rgba(255,255,255,0.5)');
        }
      };
    } catch (e) {
      console.error('Error al inicializar efectos del logo:', e);
    }
  }
}

class VersionManager {
  constructor(repositories) {
    this.repositories = repositories;
    this.handlers = {};
  }

  init() {
    Object.entries(this.repositories).forEach(([platform, config]) => {
      if (platform === 'android') this.handlers[platform] = new AndroidVersionHandler(config);
      else if (platform === 'windows') this.handlers[platform] = new WindowsVersionHandler(config);
      if (this.handlers[platform]) this.handlers[platform].init();
    });
  }
}

class BaseVersionHandler {
  constructor(config) {
    this.repo = config.repo;
    this.currentVersion = config.currentVersion;
    this.latestVersion = config.currentVersion;
    this.downloadFormat = config.downloadFormat;
    this.elements = config.elements;
  }

  async init() {
    const versionEl = document.getElementById(this.elements.version);
    const downloadBtn = document.getElementById(this.elements.download);
    if (!versionEl && !downloadBtn) return;
    await this.checkNewVersion();
    setInterval(() => this.checkNewVersion(), APP_CONFIG.checkInterval);
  }

  async checkNewVersion() {
    const versionEl = document.getElementById(this.elements.version);
    const downloadText = document.getElementById(this.elements.text);
    // Versión local — sin dependencia de GitHub API
    this.latestVersion = this.currentVersion;
    if (versionEl) versionEl.textContent = this.currentVersion;
    this.updateDownloadButton();
    return this.currentVersion;
  }

  updateDownloadButton() {
    const downloadBtn = document.getElementById(this.elements.download);
    const downloadText = document.getElementById(this.elements.text);
    if (!downloadBtn) return;
    const version = this.latestVersion || this.currentVersion;
    const url = this.downloadFormat.includes('{version}')
      ? `https://github.com/${this.repo}/releases/download/${this.downloadFormat.replace(/\{version\}/g, version)}`
      : this.downloadFormat;
    downloadBtn.href = url;
    if (downloadText && this.latestVersion && this.latestVersion !== this.currentVersion) {
      downloadText.textContent = `Nueva versión (${this.latestVersion})`;
    }
  }
}

class AndroidVersionHandler extends BaseVersionHandler {
  constructor(config) {
    super(config);
    this.initDialogs();
  }

  initDialogs() {
    const changelogDialog = document.getElementById(this.elements.changelog);
    const versionsDialog = document.getElementById(this.elements.versions);

    if (changelogDialog) {
      window.changelog = {
        show: () => { changelogDialog.showModal(); this.loadChangelog(); },
        close: () => changelogDialog.close()
      };
    }

    if (versionsDialog) {
      window.versions = {
        show: () => { versionsDialog.showModal(); this.loadVersions(); },
        close: () => versionsDialog.close()
      };
    }
  }

  async loadChangelog() {
    const content = document.getElementById(this.elements.changelogContent);
    if (!content) return;
    content.innerHTML = '<div class="loading-indicator" role="status" aria-label="Cargando"><div class="m3e-loading-indicator"><span class="m3e-loading-indicator__dot"></span><span class="m3e-loading-indicator__dot"></span><span class="m3e-loading-indicator__dot"></span></div></div>';
    try {
      const response = await fetch(`${APP_CONFIG.urls.api.github}${this.repo}/releases/latest`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      if (!data.body) throw new Error('Sin notas de versión');
      const date = new Date(data.published_at).toLocaleDateString(OYT_LOCALES[OYT_LANG] || 'es-ES', { year: 'numeric', month: 'long', day: 'numeric' });
      content.innerHTML = `
        <div style="padding:4px 0">
          <p style="font-size:13px;opacity:.6;margin-bottom:16px">${date}</p>
          <div class="markdown-body">${marked.parse(data.body)}</div>
        </div>`;
    } catch (error) {
      content.innerHTML = `<p style="color:var(--md-sys-color-error);padding:16px">Error: ${error.message}</p>`;
    }
  }

  async loadVersions() {
    const list = document.getElementById(this.elements.versionsList);
    if (!list) return;
    list.innerHTML = '<div class="loading-indicator" role="status" aria-label="Cargando"><div class="m3e-loading-indicator"><span class="m3e-loading-indicator__dot"></span><span class="m3e-loading-indicator__dot"></span><span class="m3e-loading-indicator__dot"></span></div></div>';
    try {
      const response = await fetch(`${APP_CONFIG.urls.api.github}${this.repo}/releases`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const releases = await response.json();
      list.innerHTML = releases.map((release, i) => {
        const type = release.prerelease ? 'beta' : (release.tag_name.toLowerCase().includes('alpha') ? 'alpha' : 'stable');
        const label = type === 'stable' ? 'Estable' : (type === 'beta' ? 'Beta' : 'Alpha');
        const date = new Date(release.published_at).toLocaleDateString(OYT_LOCALES[OYT_LANG] || 'es-ES', { year: 'numeric', month: 'long', day: 'numeric' });
        const downloadUrl = release.assets[0]?.browser_download_url || '#';
        return `
          <div class="version-list-item">
            <div class="version-list-info">
              <span class="version-tag">${release.tag_name}</span>
              ${i === 0 ? '<span class="version-badge latest">Más reciente</span>' : ''}
              <span class="version-badge ${type}">${label}</span>
              <span class="version-date">${date}</span>
            </div>
            <a href="${downloadUrl}" class="primary-button small">
              <span class="material-symbols-rounded">download</span>
              Descargar
            </a>
          </div>`;
      }).join('');
    } catch (error) {
      list.innerHTML = `<p style="color:var(--md-sys-color-error);padding:16px">Error: ${error.message}</p>`;
    }
  }
}

class WindowsVersionHandler extends BaseVersionHandler {
  constructor(config) {
    super(config);
    this.initDialogs();
  }

  initDialogs() {
    const changelogDialog = document.getElementById(this.elements.changelog);
    const versionsDialog = document.getElementById(this.elements.versions);

    if (changelogDialog) {
      window.showChangelogWindows = () => { changelogDialog.showModal(); this.loadChangelog(); };
      window.closeChangelogWindows = () => changelogDialog.close();
    }
    if (versionsDialog) {
      window.showVersionsWindows = () => { versionsDialog.showModal(); this.loadVersions(); };
      window.closeVersionsWindows = () => versionsDialog.close();
    }
  }

  async loadChangelog() {
    const content = document.getElementById(this.elements.changelogContent);
    if (!content) return;
    content.innerHTML = '<div class="loading-indicator" role="status" aria-label="Cargando"><div class="m3e-loading-indicator"><span class="m3e-loading-indicator__dot"></span><span class="m3e-loading-indicator__dot"></span><span class="m3e-loading-indicator__dot"></span></div></div>';
    try {
      const response = await fetch(`${APP_CONFIG.urls.api.github}${this.repo}/releases/latest`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      content.innerHTML = `<div class="markdown-body">${marked.parse(data.body || 'Sin notas disponibles.')}</div>`;
    } catch (error) {
      content.innerHTML = `<p style="color:var(--md-sys-color-error);padding:16px">Error: ${error.message}</p>`;
    }
  }

  async loadVersions() {
    const list = document.getElementById(this.elements.versionsList);
    if (!list) return;
    list.innerHTML = '<div class="loading-indicator" role="status" aria-label="Cargando"><div class="m3e-loading-indicator"><span class="m3e-loading-indicator__dot"></span><span class="m3e-loading-indicator__dot"></span><span class="m3e-loading-indicator__dot"></span></div></div>';
    try {
      const response = await fetch(`${APP_CONFIG.urls.api.github}${this.repo}/releases`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const releases = await response.json();
      list.innerHTML = releases.map(release => `
        <div class="version-list-item">
          <div class="version-list-info">
            <span class="version-tag">${release.tag_name}</span>
            <span class="version-date">${new Date(release.published_at).toLocaleDateString()}</span>
          </div>
          <a href="${release.assets[0]?.browser_download_url || '#'}" class="primary-button small">
            <span class="material-symbols-rounded">download</span>
            Descargar
          </a>
        </div>`).join('');
    } catch (error) {
      list.innerHTML = `<p style="color:var(--md-sys-color-error);padding:16px">Error: ${error.message}</p>`;
    }
  }
}

class CarouselManager {
  constructor() {
    this.images = document.querySelectorAll('.carousel-image');
    this.popupOverlay = document.getElementById('popupOverlay');
    this.popupImage = document.getElementById('popupImage');
    this.startX = 0;
  }

  init() {
    if (!this.images.length || !this.popupOverlay) return;
    this.images.forEach(img => {
      img.addEventListener('click', e => {
        this.popupImage.src = e.target.src;
        this.popupOverlay.style.display = 'flex';
      });
    });
    this.popupOverlay.addEventListener('click', e => {
      if (e.target === this.popupOverlay) this.popupOverlay.style.display = 'none';
    });
    const closeBtn = document.getElementById('closePopup');
    if (closeBtn) closeBtn.addEventListener('click', () => { this.popupOverlay.style.display = 'none'; });
    this.popupOverlay.addEventListener('touchstart', e => { this.startX = e.touches[0].clientX; });
    this.popupOverlay.addEventListener('touchend', e => {
      const diff = this.startX - e.changedTouches[0].clientX;
      if (Math.abs(diff) > 50) diff > 0 ? this.nextImage() : this.prevImage();
    });
  }

  nextImage() {
    const idx = Array.from(this.images).findIndex(img => img.src === this.popupImage.src);
    this.popupImage.src = this.images[(idx + 1) % this.images.length].src;
  }

  prevImage() {
    const idx = Array.from(this.images).findIndex(img => img.src === this.popupImage.src);
    this.popupImage.src = this.images[(idx - 1 + this.images.length) % this.images.length].src;
  }
}

/* ── Carrusel de capturas (En acción) ── */
class ScreenshotsCarousel {
  constructor(rootId) {
    this.root = document.getElementById(rootId);
    if (!this.root) return;

    this.track = this.root.querySelector('#shots-track');
    this.slides = this.root.querySelectorAll('.screenshots-slide');
    this.indicators = Array.from(this.root.querySelectorAll('.screenshots-indicator'));
    this.prevBtn = this.root.querySelector('#shots-prev');
    this.nextBtn = this.root.querySelector('#shots-next');

    if (!this.track || this.slides.length < 2) return;

    this.index = 0;
    this.timer = null;
    this.delay = 3000;
    this.reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    this.init();
  }

  init() {
    this.prevBtn?.addEventListener('click', () => this.go(this.index - 1, true));
    this.nextBtn?.addEventListener('click', () => this.go(this.index + 1, true));
    this.indicators.forEach((dot, i) => dot.addEventListener('click', () => this.go(i, true)));

    // Pausa el avance automático mientras el usuario interactúa
    this.root.addEventListener('mouseenter', () => this.stop());
    this.root.addEventListener('mouseleave', () => this.start());
    this.root.addEventListener('focusin', () => this.stop());
    this.root.addEventListener('focusout', () => this.start());
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) this.stop(); else this.start();
    });

    this.render();
    this.start();
  }

  go(target, fromUser) {
    const total = this.slides.length;
    this.index = ((target % total) + total) % total;
    this.render();
    if (fromUser) {
      this.stop();
      this.start();
    }
  }

  render() {
    this.track.style.transform = `translateX(-${this.index * 100}%)`;
    this.indicators.forEach((dot, i) => dot.classList.toggle('is-active', i === this.index));
  }

  start() {
    if (this.reducedMotion || this.timer) return;
    this.timer = setInterval(() => this.go(this.index + 1, false), this.delay);
  }

  stop() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}

/* ── Glifos cambiantes (firma del fundador) ── */
class GlyphScrambler {
  constructor(el) {
    this.el = el;
    this.length = parseInt(el.dataset.glyphs || '6', 10);
    this.speed = parseInt(el.dataset.speed || '75', 10);
    this.set = 'ΞΨΩΔΣΦΘΛΠЖЩДЦФЭЮЯ◈◇◊○●□■△▽▷◁⬡⬢⌘☉☽☾†‡§';

    this.paint();
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    this.timer = setInterval(() => this.paint(), this.speed);
  }

  paint() {
    let out = '';
    for (let i = 0; i < this.length; i++) {
      out += this.set.charAt(Math.floor(Math.random() * this.set.length));
    }
    this.el.textContent = out;
  }
}

/* ── Revelado de secciones al hacer scroll ── */
function initScrollReveal() {
  const items = document.querySelectorAll('[data-reveal]');
  if (!items.length) return;

  if (!('IntersectionObserver' in window)) {
    items.forEach(el => el.classList.add('is-in'));
    return;
  }

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('is-in');
      observer.unobserve(entry.target);
    });
  }, { threshold: 0, rootMargin: '0px 0px -12% 0px' });

  requestAnimationFrame(() => items.forEach(el => observer.observe(el)));
}

/* ── Nav con más presencia al hacer scroll ── */
function initNavScrollState() {
  const nav = document.getElementById('main-nav');
  if (!nav) return;

  const update = () => nav.classList.toggle('is-scrolled', window.scrollY > 24);
  update();
  window.addEventListener('scroll', update, { passive: true });
}

document.addEventListener('DOMContentLoaded', () => {
  const app = new OpenYTMusicApp(APP_CONFIG);
  app.init();

  if (document.querySelectorAll('.carousel-image').length && document.getElementById('popupOverlay')) {
    app.carouselManager = new CarouselManager();
    app.carouselManager.init();
  }

  app.screenshotsCarousel = new ScreenshotsCarousel('shots-carousel');

  app.glyphScramblers = Array.from(document.querySelectorAll('.glyph-scramble'))
    .map(el => new GlyphScrambler(el));

  app.i18nManager = new I18nManager();
  app.i18nManager.init();

  initScrollReveal();
  initNavScrollState();
});


// Mostrar versión en el badge OSS
(async function updateOssVersion() {
  const badge = document.getElementById('oss-version-badge');
  if (!badge) return;
  badge.textContent = '0.5.0';
})();


// ============================================
// CONFIGURACIÓN DE DIÁLOGOS ANDROID
// ============================================
document.addEventListener('DOMContentLoaded', function () {
  // Elementos de la tarjeta Android
  const changelogTrigger = document.getElementById('changelog-trigger');
  const versionsTrigger = document.getElementById('versions-trigger');
  const changelogDialog = document.getElementById('changelog-dialog');
  const versionsDialog = document.getElementById('versions-dialog');
  const closeChangelogBtn = document.getElementById('close-changelog-btn');
  const closeVersionsBtn = document.getElementById('close-versions-btn');

  // Función para abrir changelog
  if (changelogTrigger && changelogDialog) {
    changelogTrigger.addEventListener('click', function () {
      changelogDialog.showModal();
      // Cargar el changelog automáticamente
      loadChangelogContent();
    });
  }

  // Función para abrir versiones
  if (versionsTrigger && versionsDialog) {
    versionsTrigger.addEventListener('click', function () {
      versionsDialog.showModal();
      // Cargar las versiones automáticamente
      loadVersionsList();
    });
  }

  // Cerrar diálogos con los botones X
  if (closeChangelogBtn && changelogDialog) {
    closeChangelogBtn.addEventListener('click', function () {
      changelogDialog.close();
    });
  }

  if (closeVersionsBtn && versionsDialog) {
    closeVersionsBtn.addEventListener('click', function () {
      versionsDialog.close();
    });
  }

  // Cerrar diálogo haciendo clic fuera (opcional)
  if (changelogDialog) {
    changelogDialog.addEventListener('click', function (e) {
      if (e.target === changelogDialog) {
        changelogDialog.close();
      }
    });
  }

  if (versionsDialog) {
    versionsDialog.addEventListener('click', function (e) {
      if (e.target === versionsDialog) {
        versionsDialog.close();
      }
    });
  }
});

/* ══════════════════════════════════════════════════════════
   INTERNACIONALIZACIÓN — español / inglés / portugués
   ══════════════════════════════════════════════════════════ */
const I18N = {
  es: {
    'meta.title': 'OpenYTMusic',
    'meta.description': 'OpenYTMusic — cliente de YouTube Music sin anuncios para Android. Material Design 3, letras, descargas offline y anti-bloqueos.',
    'nav.download': 'Descargar',
    'hero.lead': 'Un cliente de YouTube Music para Android: reproducción en segundo plano, descargas offline e importa tus playlists de YouTube Music. Sin anuncios.',
    'cta.download': 'Descargar',
    'section.action': 'En acción',
    'section.frictionless': 'Música sin fricción',
    'os.lead': 'OpenYTMusic es un cliente alternativo y ligero de YouTube Music, construido sobre Material Design 3 y pensado para el usuario común. Hecho por un fan de la música.',
    'os.chip1': 'Cliente alternativo',
    'os.chip2': 'API privada',
    'os.role': 'Fundador y Desarrollador',
    'os.version': 'Última versión estable',
    'support.title': '¿Dudas o sugerencias?',
    'support.lead': 'Escríbeme por Discord y te respondo al momento.',
    'support.cta': 'Contactar por Discord',
    'section.downloads': 'Descargas',
    'dl.lead': 'Descarga la versión más reciente de OpenYTMusic para tu plataforma.',
    'dl.androidSub': 'La última versión de OpenYTMusic.',
    'dl.chipRelease': 'Release',
    'dl.requires': 'Requiere Android 7.0+ (minSdk 24)',
    'dl.windowsSub': 'El desarrollo está cerrado actualmente.',
    'dl.chipClosed': 'Cerrado',
    'dl.chipUnavailable': 'No disponible',
    'dl.seeChanges': 'Ver cambios',
    'dl.prevVersions': 'Versiones anteriores',
    'dl.unavailable': 'No disponible',
    'footer.action': 'En acción',
    'footer.downloads': 'Descargas',
    'footer.rights': '. Todos los derechos reservados.',
    'footer.disclaimer': 'OpenYTMusic y su contenido no están afiliados con YouTube, Google LLC ni sus afiliados. Las marcas pertenecen a sus respectivos dueños.',
    'dialog.changelog': 'Registro de cambios',
    'dialog.versions': 'Versiones anteriores',
    'dialog.latest': 'Más reciente',
    'dialog.download': 'Descargar',
    'dialog.noVersions': 'No hay versiones disponibles.',
    'dialog.noNotes': 'Sin notas disponibles.',
    'dialog.errorChanges': 'Error al cargar cambios',
    'dialog.errorVersions': 'Error al cargar versiones',
    'loading': 'Cargando',
    'changelog.body': '### OpenYTMusic 0.5.0\n\n- Rescate anti-bot con PoToken: la música ya no se corta cuando YouTube pide verificación\n- Aviso en el reproductor cuando aparece una verificación, con acceso directo al inicio de sesión\n- Inicio de sesión de YouTube Music destacado en Ajustes: importa tus playlists y «Me gusta»\n- La sesión firmada (SAPISIDHASH) también cuenta en la resolución de streams\n- Resolución de streams con reintento acotado y sin estado global compartido\n- Bucle de reproducción garantizado en cualquier cola y lista\n- Inicialización del PoToken en segundo plano, sin bloquear la canción\n- Correcciones varias y compilación de escritorio restaurada',
    'rel.body.050': 'Anti-bot con PoToken, aviso de verificación, login destacado para importar playlists, bucle de colas garantizado y más.',
    'rel.body.030': 'Reproductor rediseñado, visor de portada, miniplayer flotante con onda, estadísticas reales y más.',
    'rel.body.020': 'Preview oficial con miniplayer flotante, RPC de Discord y branding nuevo.'
  },
  en: {
    'meta.title': 'OpenYTMusic',
    'meta.description': 'OpenYTMusic — an ad-free YouTube Music client for Android. Material Design 3, lyrics, offline downloads and anti-bot handling.',
    'nav.download': 'Download',
    'hero.lead': 'A YouTube Music client for Android: background playback, offline downloads and import your YouTube Music playlists. No ads.',
    'cta.download': 'Download',
    'section.action': 'In action',
    'section.frictionless': 'Music without friction',
    'os.lead': 'OpenYTMusic is a lightweight alternative client for YouTube Music, built on Material Design 3 and made for the everyday user. Built by a music fan.',
    'os.chip1': 'Alternative client',
    'os.chip2': 'Private API',
    'os.role': 'Founder and Developer',
    'os.version': 'Latest stable version',
    'support.title': 'Questions or suggestions?',
    'support.lead': 'Message me on Discord and I will reply right away.',
    'support.cta': 'Contact on Discord',
    'section.downloads': 'Downloads',
    'dl.lead': 'Download the latest version of OpenYTMusic for your platform.',
    'dl.androidSub': 'The latest version of OpenYTMusic.',
    'dl.chipRelease': 'Release',
    'dl.requires': 'Requires Android 7.0+ (minSdk 24)',
    'dl.windowsSub': 'Development is currently closed.',
    'dl.chipClosed': 'Closed',
    'dl.chipUnavailable': 'Unavailable',
    'dl.seeChanges': 'See changes',
    'dl.prevVersions': 'Previous versions',
    'dl.unavailable': 'Unavailable',
    'footer.action': 'In action',
    'footer.downloads': 'Downloads',
    'footer.rights': '. All rights reserved.',
    'footer.disclaimer': 'OpenYTMusic and its content are not affiliated with YouTube, Google LLC or their affiliates. Trademarks belong to their respective owners.',
    'dialog.changelog': 'Changelog',
    'dialog.versions': 'Previous versions',
    'dialog.latest': 'Latest',
    'dialog.download': 'Download',
    'dialog.noVersions': 'No versions available.',
    'dialog.noNotes': 'No release notes available.',
    'dialog.errorChanges': 'Error loading changes',
    'dialog.errorVersions': 'Error loading versions',
    'loading': 'Loading',
    'changelog.body': '### OpenYTMusic 0.5.0\n\n- PoToken anti-bot rescue: music no longer stops when YouTube asks for verification\n- In-player notice when a verification appears, with a shortcut to sign in\n- YouTube Music sign-in highlighted in Settings: import your playlists and Likes\n- Signed session (SAPISIDHASH) now also counts when resolving streams\n- Stream resolution with bounded retries and no shared global state\n- Playback loop guaranteed in any queue or playlist\n- PoToken warm-up in the background, without blocking the song\n- Misc fixes and desktop build restored',
    'rel.body.050': 'PoToken anti-bot, verification notice, highlighted sign-in to import playlists, guaranteed queue loop and more.',
    'rel.body.030': 'Redesigned player, full-screen artwork viewer, floating miniplayer with a wave, real stats and more.',
    'rel.body.020': 'Official preview with a floating miniplayer, Discord RPC and new branding.'
  },
  pt: {
    'meta.title': 'OpenYTMusic',
    'meta.description': 'OpenYTMusic — cliente de YouTube Music sem anúncios para Android. Material Design 3, letras, downloads offline e proteção anti-bloqueio.',
    'nav.download': 'Baixar',
    'hero.lead': 'Um cliente de YouTube Music para Android: reprodução em segundo plano, downloads offline e importe suas playlists do YouTube Music. Sem anúncios.',
    'cta.download': 'Baixar',
    'section.action': 'Em ação',
    'section.frictionless': 'Música sem atrito',
    'os.lead': 'OpenYTMusic é um cliente alternativo e leve do YouTube Music, construído sobre Material Design 3 e pensado para o usuário comum. Feito por um fã de música.',
    'os.chip1': 'Cliente alternativo',
    'os.chip2': 'API privada',
    'os.role': 'Fundador e Desenvolvedor',
    'os.version': 'Última versão estável',
    'support.title': 'Dúvidas ou sugestões?',
    'support.lead': 'Me chame no Discord e eu respondo na hora.',
    'support.cta': 'Falar no Discord',
    'section.downloads': 'Downloads',
    'dl.lead': 'Baixe a versão mais recente do OpenYTMusic para a sua plataforma.',
    'dl.androidSub': 'A versão mais recente do OpenYTMusic.',
    'dl.chipRelease': 'Release',
    'dl.requires': 'Requer Android 7.0+ (minSdk 24)',
    'dl.windowsSub': 'O desenvolvimento está fechado no momento.',
    'dl.chipClosed': 'Fechado',
    'dl.chipUnavailable': 'Indisponível',
    'dl.seeChanges': 'Ver mudanças',
    'dl.prevVersions': 'Versões anteriores',
    'dl.unavailable': 'Indisponível',
    'footer.action': 'Em ação',
    'footer.downloads': 'Downloads',
    'footer.rights': '. Todos os direitos reservados.',
    'footer.disclaimer': 'OpenYTMusic e seu conteúdo não são afiliados ao YouTube, à Google LLC nem às suas afiliadas. As marcas pertencem aos seus respectivos donos.',
    'dialog.changelog': 'Registro de alterações',
    'dialog.versions': 'Versões anteriores',
    'dialog.latest': 'Mais recente',
    'dialog.download': 'Baixar',
    'dialog.noVersions': 'Nenhuma versão disponível.',
    'dialog.noNotes': 'Sem notas de versão disponíveis.',
    'dialog.errorChanges': 'Erro ao carregar as mudanças',
    'dialog.errorVersions': 'Erro ao carregar as versões',
    'loading': 'Carregando',
    'changelog.body': '### OpenYTMusic 0.5.0\n\n- Resgate anti-bot com PoToken: a música não para mais quando o YouTube pede verificação\n- Aviso no reprodutor quando aparece uma verificação, com atalho para entrar na conta\n- Login do YouTube Music em destaque nas Configurações: importe suas playlists e Curtidas\n- A sessão assinada (SAPISIDHASH) também passa a valer na resolução dos streams\n- Resolução de streams com retentativa limitada e sem estado global compartilhado\n- Repetição garantida em qualquer fila ou playlist\n- Aquecimento do PoToken em segundo plano, sem travar a música\n- Correções diversas e compilação para desktop restaurada',
    'rel.body.050': 'Anti-bot com PoToken, aviso de verificação, login em destaque para importar playlists, repetição de fila garantida e mais.',
    'rel.body.030': 'Reprodutor redesenhado, visualizador de capa em tela cheia, miniplayer flutuante com onda, estatísticas reais e mais.',
    'rel.body.020': 'Preview oficial com miniplayer flutuante, RPC do Discord e nova identidade.'
  }
};

const OYT_LANGS = ['es', 'en', 'pt'];
const OYT_LOCALES = { es: 'es-ES', en: 'en-US', pt: 'pt-BR' };
let OYT_LANG = 'es';

function t(key) {
  const table = I18N[OYT_LANG] || I18N.es;
  return (table && table[key]) || I18N.es[key] || key;
}

class I18nManager {
  constructor() {
    this.buttons = Array.from(document.querySelectorAll('.lang-btn'));
    this.lang = this.detect();
  }

  detect() {
    try {
      const saved = localStorage.getItem('oyt-lang');
      if (saved && OYT_LANGS.includes(saved)) return saved;
    } catch (e) { /* modo privado */ }

    const nav = String(navigator.language || navigator.userLanguage || 'es').slice(0, 2).toLowerCase();
    return OYT_LANGS.includes(nav) ? nav : 'es';
  }

  init() {
    this.buttons.forEach(btn => {
      btn.addEventListener('click', () => this.set(btn.dataset.lang));
    });
    this.set(this.lang);
  }

  set(lang) {
    if (!OYT_LANGS.includes(lang)) return;

    OYT_LANG = lang;
    this.lang = lang;

    try { localStorage.setItem('oyt-lang', lang); } catch (e) { /* ignora */ }

    document.documentElement.lang = lang;
    document.title = t('meta.title');

    const meta = document.querySelector('meta[name="description"]');
    if (meta) meta.setAttribute('content', t('meta.description'));

    document.querySelectorAll('[data-i18n]').forEach(el => {
      const value = I18N[lang][el.dataset.i18n];
      if (typeof value === 'string') el.textContent = value;
    });

    this.buttons.forEach(btn => btn.classList.toggle('is-active', btn.dataset.lang === lang));

    const changelog = document.getElementById('changelog-dialog');
    if (changelog && changelog.open) loadChangelogContent();

    const versions = document.getElementById('versions-dialog');
    if (versions && versions.open) loadVersionsList();
  }
}

// Función para cargar el changelog
async function loadChangelogContent() {
  const content = document.getElementById('changelog-content');
  if (!content) return;

  content.innerHTML = `<div class="loading-indicator" role="status" aria-label="${t('loading')}"><div class="m3e-loading-indicator"><span class="m3e-loading-indicator__dot"></span><span class="m3e-loading-indicator__dot"></span><span class="m3e-loading-indicator__dot"></span></div></div>`;

  try {
    const data = { published_at: new Date().toISOString(), body: t('changelog.body') };
    const date = new Date(data.published_at).toLocaleDateString(OYT_LOCALES[OYT_LANG] || 'es-ES', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });

    // Verificar si marked está disponible
    const notes = data.body || t('dialog.noNotes');
    const markdownContent = typeof marked !== 'undefined' ? marked.parse(notes) : notes;

    content.innerHTML = `
            <div class="changelog-meta">
                <span class="changelog-date">📅 ${date}</span>
            </div>
            <div class="markdown-body">${markdownContent}</div>
        `;
  } catch (error) {
    content.innerHTML = `<p style="color: #cf6679; padding: 16px;">${t('dialog.errorChanges')}: ${error.message}</p>`;
  }
}

// Función para cargar lista de versiones
async function loadVersionsList() {
  const list = document.getElementById('versions-list');
  if (!list) return;

  list.innerHTML = `<div class="loading-indicator" role="status" aria-label="${t('loading')}"><div class="m3e-loading-indicator"><span class="m3e-loading-indicator__dot"></span><span class="m3e-loading-indicator__dot"></span><span class="m3e-loading-indicator__dot"></span></div></div>`;

  try {
    const releases = [{
      tag_name: '0.5.0',
      published_at: new Date().toISOString(),
      name: 'OpenYTMusic 0.5.0',
      body: t('rel.body.050'),
      assets: [{ browser_download_url: 'OpenYTMusic-0.5.0.apk' }]
    }, {
      // La 0.3.0 salio de la lista a proposito: su APK ya no esta en el sitio, asi que
      // ofrecerla solo daba un boton de descarga roto. Si se vuelve a subir el archivo,
      // se restaura esta fila.
      tag_name: '0.2.0',
      published_at: '2026-09-08T12:00:00Z',
      name: 'OpenYTMusic 0.2.0',
      body: t('rel.body.020'),
      assets: [{ browser_download_url: 'OpenYTMusic-0.2.0.apk' }]
    }];

    if (releases.length === 0) {
      list.innerHTML = `<p class="text-on-surface-variant" style="padding: 16px;">${t('dialog.noVersions')}</p>`;
      return;
    }

    list.innerHTML = releases.map((release, index) => {
      const date = new Date(release.published_at).toLocaleDateString(OYT_LOCALES[OYT_LANG] || 'es-ES', {
        year: 'numeric',
        month: 'long',
        day: 'numeric'
      });

      const downloadUrl = release.assets[0]?.browser_download_url || '#';
      const isLatest = index === 0;

      return `
                <div class="version-list-item">
                    <div class="version-list-info">
                        <div class="version-meta-row">
                            <span class="version-tag">${release.tag_name}</span>
                            ${isLatest ? `<span class="version-chip version-chip--latest">${t('dialog.latest')}</span>` : ''}
                        </div>
                        <span class="version-date">${date}</span>
                    </div>
                    <a href="${downloadUrl}" class="version-dl-btn" download>
                        <span class="material-symbols-outlined" style="font-size: 18px;">download</span>
                        ${t('dialog.download')}
                    </a>
                </div>
            `;
    }).join('');

  } catch (error) {
    list.innerHTML = `<p style="color: #cf6679; padding: 16px;">${t('dialog.errorVersions')}: ${error.message}</p>`;
  }
}

// ============================================
// OBTENER DATOS REALES DE GITHUB API (VERSIÓN MEJORADA) (Contribuidores.html)
// ============================================
const REPO_OWNER = 'root-leo';
const REPO_NAME = 'OpenYTMusic';

async function fetchGitHubStats() {
  try {
    // Obtener datos del repositorio
    const repoResponse = await fetch(`https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}`);
    if (!repoResponse.ok) throw new Error('Error al obtener repositorio');
    const repoData = await repoResponse.json();

    // Stars
    document.querySelector('#stats-stars .font-headline-md').textContent = formatNumber(repoData.stargazers_count);

    // Forks
    document.querySelector('#stats-forks .font-headline-md').textContent = formatNumber(repoData.forks_count);

    // Issues
    document.querySelector('#stats-issues .font-headline-md').textContent = formatNumber(repoData.open_issues_count);

    // Total Commits - usando el endpoint de commits
    await fetchTotalCommits();

  } catch (error) {
    console.error('Error:', error);
    document.querySelectorAll('.stats-number').forEach(el => el.textContent = 'Error');
  }
}

async function fetchTotalCommits() {
  try {
    // Método: obtener la página 1 con per_page=1 y leer el header Link
    const response = await fetch(`https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/commits?per_page=1`);
    const linkHeader = response.headers.get('Link');

    let totalCommits = 'N/A';
    if (linkHeader) {
      const lastPageMatch = linkHeader.match(/page=(\d+)>; rel="last"/);
      if (lastPageMatch) {
        totalCommits = formatNumber(parseInt(lastPageMatch[1]));
      }
    }

    document.querySelector('#stats-commits .font-headline-md').textContent = totalCommits;
  } catch (error) {
    console.error('Error al obtener commits:', error);
    document.querySelector('#stats-commits .font-headline-md').textContent = 'N/A';
  }
}

function formatNumber(num) {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k';
  return num.toString();
}

// Ejecutar (sección de stats removida de la landing)
document.addEventListener('DOMContentLoaded', () => {
  if (document.getElementById('stats-stars')) fetchGitHubStats();
});

// ============================================
// OBTENER DATOS REALES DE GITHUB API
// ============================================

function formatNumber(num) {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k';
  return num.toString();
}

async function fetchGitHubStats() {
  try {
    const response = await fetch(`https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}`);
    if (!response.ok) throw new Error('Error al obtener repositorio');
    const data = await response.json();

    document.querySelector('#stats-stars .font-headline-md').textContent = formatNumber(data.stargazers_count);
    document.querySelector('#stats-forks .font-headline-md').textContent = formatNumber(data.forks_count);
    document.querySelector('#stats-issues .font-headline-md').textContent = formatNumber(data.open_issues_count);

    // Total Commits
    const commitsResponse = await fetch(`https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/commits?per_page=1`);
    const linkHeader = commitsResponse.headers.get('Link');
    let totalCommits = 'N/A';
    if (linkHeader) {
      const lastPageMatch = linkHeader.match(/page=(\d+)>; rel="last"/);
      if (lastPageMatch) totalCommits = formatNumber(parseInt(lastPageMatch[1]));
    }
    document.querySelector('#stats-commits .font-headline-md').textContent = totalCommits;

  } catch (error) {
    console.error('Error:', error);
  }
}

function getContributorRole(contributions, login) {
  if (login === 'root-leo') return 'Lead Developer';
  if (contributions > 100) return 'Core Contributor';
  if (contributions > 30) return 'Major Contributor';
  if (contributions > 10) return 'Contributor';
  return 'Supporter';
}

async function fetchContributors() {
  const contributorsGrid = document.getElementById('contributors-grid');
  if (!contributorsGrid) return;

  try {
    const response = await fetch(`https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/contributors?per_page=100`);
    if (!response.ok) throw new Error('Error al obtener contribuidores');
    const contributors = await response.json();

    if (contributors.length === 0) return;

    contributorsGrid.innerHTML = contributors.map(contributor => {
      const role = getContributorRole(contributor.contributions, contributor.login);
      return `
            <div class="group bg-surface-container-low p-4 rounded-lg flex items-center gap-4 border border-white/5 hover:bg-surface-container-high transition-all hover:scale-[1.02] duration-200">
              <img alt="${contributor.login}"
                class="w-16 h-16 rounded-full object-cover grayscale group-hover:grayscale-0 transition-all ring-2 ring-transparent group-hover:ring-primary/50"
                src="${contributor.avatar_url}&s=64" />
              <div class="flex-1">
                <div class="flex items-center justify-between flex-wrap gap-2">
                  <h3 class="font-title-md text-title-md text-primary group-hover:text-primary-fixed-dim transition-colors">${contributor.login}</h3>
                  <span class="text-on-surface-variant font-label-sm text-label-sm bg-surface-container-high px-2 py-1 rounded-full">${contributor.contributions} commits</span>
                </div>
                <div class="mt-2 flex gap-1 flex-wrap">
                  <span class="bg-primary/10 text-primary-fixed-dim px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider">${role}</span>
                </div>
              </div>
              <a href="${contributor.html_url}" target="_blank" class="opacity-0 group-hover:opacity-100 transition-all">
                <span class="material-symbols-outlined text-on-surface-variant hover:text-primary text-[18px]">open_in_new</span>
              </a>
            </div>
          `;
    }).join('');

  } catch (error) {
    console.error('Error al cargar contribuidores:', error);
  }
}

async function fetchLatestActivity() {
  const activityContainer = document.getElementById('latest-activity');
  if (!activityContainer) return;

  try {
    const response = await fetch(`https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/commits?per_page=4`);
    if (!response.ok) throw new Error('Error al obtener actividad');
    const commits = await response.json();

    activityContainer.innerHTML = commits.map(commit => `
          <div class="flex gap-4">
            <div class="mt-1">
              <div class="bg-primary/20 p-2 rounded-full">
                <span class="material-symbols-outlined text-primary text-[20px]">commit</span>
              </div>
            </div>
            <div>
              <p class="font-title-md text-title-md text-on-surface">${commit.commit.message.split('\n')[0].substring(0, 60)}</p>
              <p class="font-label-sm text-label-sm text-on-surface-variant">by <span class="text-primary-fixed-dim">${commit.author?.login || commit.commit.author.name}</span> • ${new Date(commit.commit.author.date).toLocaleDateString()}</p>
            </div>
          </div>
        `).join('');

  } catch (error) {
    console.error('Error al cargar actividad:', error);
    activityContainer.innerHTML = '<p class="text-on-surface-variant text-center">Error loading activity</p>';
  }
}

document.addEventListener('DOMContentLoaded', () => {
  // Sección de stats/contribuidores/actividad removida de la landing OpenYTMusic
  if (document.getElementById('stats-stars')) fetchGitHubStats();
  if (document.getElementById('contributors-grid')) fetchContributors();
  if (document.getElementById('latest-activity')) fetchLatestActivity();
});



// ============================================
// FORMULARIO DE CONTACTO - ENVÍO A FORMSPREE
// ============================================

// Esperar a que el DOM esté listo
document.addEventListener('DOMContentLoaded', function () {

  // Seleccionar el formulario
  const contactForm = document.querySelector('form');
  const submitButton = contactForm?.querySelector('button[type="submit"]');

  // Crear elemento para mensaje de éxito
  const successMessage = document.createElement('div');
  successMessage.id = 'successMessage';
  successMessage.style.cssText = `
            display: none;
            text-align: center;
            padding: 40px 20px;
            background: rgba(208, 188, 255, 0.1);
            border-radius: 24px;
            border: 1px solid rgba(208, 188, 255, 0.3);
            margin-top: 20px;
        `;
  successMessage.innerHTML = `
            <span class="material-symbols-outlined" style="font-size: 48px; color: #d0bcff; margin-bottom: 16px;">check_circle</span>
            <h3 style="color: #e9ddff; font-family: Epilogue; font-size: 24px; margin-bottom: 8px;">¡Mensaje enviado!</h3>
            <p style="color: #cac4d0; margin-bottom: 24px;">Gracias por contactarnos. Te responderemos pronto.</p>
            <button onclick="location.reload()" style="background: #d0bcff; color: #37265e; border: none; padding: 12px 24px; border-radius: 9999px; font-weight: 600; cursor: pointer;">Enviar otro mensaje</button>
        `;

  // Insertar mensaje de éxito después del formulario
  if (contactForm) {
    contactForm.parentNode.appendChild(successMessage);
  }

  // Función para mostrar loading
  function setLoading(isLoading) {
    if (!submitButton) return;
    if (isLoading) {
      submitButton.disabled = true;
      submitButton.style.opacity = '0.7';
      submitButton.style.cursor = 'not-allowed';
      const originalText = submitButton.innerHTML;
      submitButton.setAttribute('data-original-text', originalText);
      submitButton.innerHTML = '<span></span><span>Enviando...</span><span class="material-symbols-outlined" style="animation: spin 1s linear infinite;">hourglass_empty</span>';

      // Agregar animación spin si no existe
      if (!document.querySelector('#spin-style')) {
        const style = document.createElement('style');
        style.id = 'spin-style';
        style.textContent = '@keyframes spin { to { transform: rotate(360deg); } }';
        document.head.appendChild(style);
      }
    } else {
      submitButton.disabled = false;
      submitButton.style.opacity = '1';
      submitButton.style.cursor = 'pointer';
      const originalText = submitButton.getAttribute('data-original-text');
      if (originalText) {
        submitButton.innerHTML = originalText;
      }
    }
  }

  // Manejar envío del formulario
  if (contactForm) {
    contactForm.addEventListener('submit', async function (e) {
      e.preventDefault();

      // Obtener valores
      const nombreInput = document.querySelector('input[placeholder="Escribe tu nombre aquí"]');
      const emailInput = document.querySelector('input[type="email"]');
      const descripcionTextarea = document.querySelector('textarea');
      const tipoMensajeSelected = document.querySelector('input[name="message_type"]:checked');

      const nombre = nombreInput?.value || '';
      const email = emailInput?.value || '';
      const descripcion = descripcionTextarea?.value || '';
      const tipo_mensaje = tipoMensajeSelected?.closest('label')?.querySelector('.font-title-md')?.innerText || 'Comentario';

      // Validar
      if (!nombre || !email || !descripcion) {
        alert('Por favor, completa todos los campos.');
        return;
      }

      if (!email.includes('@')) {
        alert('Por favor, ingresa un email válido.');
        return;
      }

      // Mostrar loading
      setLoading(true);

      try {
        const formData = new FormData();
        formData.append('nombre', nombre);
        formData.append('email', email);
        formData.append('tipo_mensaje', tipo_mensaje);
        formData.append('descripcion', descripcion);

        const response = await fetch('https://formspree.io/f/xgvallrq', {
          method: 'POST',
          body: formData,
          headers: {
            'Accept': 'application/json'
          }
        });

        if (response.ok) {
          // Ocultar formulario, mostrar éxito
          contactForm.style.display = 'none';
          successMessage.style.display = 'block';
        } else {
          throw new Error('Error en el envío');
        }
      } catch (error) {
        console.error('Error:', error);
        alert('Hubo un error al enviar tu mensaje. Por favor, inténtalo de nuevo.');
      } finally {
        setLoading(false);
      }
    });
  }
});


// ============================================
// DEMO - DIÁLOGO DE ADVERTENCIA
// ============================================
document.addEventListener('DOMContentLoaded', function() {
    // Elementos
    const demoBtn = document.getElementById('hero-demo-btn');
    const warningDialog = document.getElementById('warning-dialog');
    const proceedBtn = document.getElementById('proceedBtn');
    const dismissBtn = document.getElementById('dismissBtn');
    
    // Abrir diálogo al hacer clic en "Probar Demo"
    if (demoBtn && warningDialog) {
        demoBtn.addEventListener('click', function(e) {
            e.preventDefault();
            warningDialog.showModal();
        });
    }
    
    // Ir a la demo al hacer clic en "Continuar"
    if (proceedBtn) {
        proceedBtn.addEventListener('click', function() {
            window.location.href = 'https://appetize.io/app/b_yb62tcjuqzqjvctnswv3krpnmm';
        });
    }
    
    // Cerrar diálogo al hacer clic en "Cancelar"
    if (dismissBtn) {
        dismissBtn.addEventListener('click', function() {
            warningDialog.close();
        });
    }
    
    // Cerrar diálogo al hacer clic fuera de él
    if (warningDialog) {
        warningDialog.addEventListener('click', function(e) {
            if (e.target === warningDialog) {
                warningDialog.close();
            }
        });
    }
    
    // Cerrar con la tecla ESC (por defecto funciona, pero lo aseguramos)
    if (warningDialog) {
        warningDialog.addEventListener('cancel', function(e) {
            warningDialog.close();
        });
    }
});


// ============================================
// M3E COMPONENTS — SplitButton, FAB menu, View Transitions
// ============================================

(function initM3EComponents() {
  'use strict';

  /* ─── SplitButton (Android downloads) ─── */
  function initSplitButton() {
    const root = document.getElementById('android-split-button');
    if (!root) return;
    const toggle = document.getElementById('android-split-toggle');
    const menu = document.getElementById('android-version-menu');
    if (!toggle || !menu) return;

    const setOpen = (open) => {
      root.dataset.open = open ? 'true' : 'false';
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
      if (open) {
        menu.hidden = false;
        // doble rAF para que la transición dispare
        requestAnimationFrame(() => requestAnimationFrame(() => {
          menu.dataset.open = 'true';
        }));
      } else {
        menu.dataset.open = 'false';
        // esperar fin de animación antes de ocultar
        setTimeout(() => { if (root.dataset.open !== 'true') menu.hidden = true; }, 220);
      }
    };

    toggle.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      setOpen(root.dataset.open !== 'true');
    });

    // Click fuera cierra
    document.addEventListener('click', (e) => {
      if (root.dataset.open === 'true' && !root.contains(e.target)) setOpen(false);
    });

    // ESC cierra
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && root.dataset.open === 'true') {
        setOpen(false);
        toggle.focus();
      }
    });

    // Click en un item del menú (estable/alpha) → actualiza el botón primario
    menu.querySelectorAll('[data-version-channel]').forEach((item) => {
      item.addEventListener('click', (e) => {
        const channel = item.dataset.versionChannel;
        if (channel === 'beta') return; // ya tiene su propio href
        e.preventDefault();
        const label = item.querySelector('span:last-child')?.textContent || 'Descargar APK';
        const text = document.getElementById('android-download-text');
        if (text) text.textContent = label;
        setOpen(false);
      });
    });
  }

  /* ─── FAB menu ─── */
  function initFabMenu() {
    const fab = document.getElementById('m3e-fab');
    const toggle = document.getElementById('m3e-fab-toggle');
    if (!fab || !toggle) return;

    const setOpen = (open) => {
      fab.dataset.open = open ? 'true' : 'false';
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
      toggle.setAttribute('aria-label', open ? 'Cerrar menú de acciones' : 'Abrir menú de acciones');
    };

    toggle.addEventListener('click', (e) => {
      e.stopPropagation();
      setOpen(fab.dataset.open !== 'true');
    });

    // Click fuera cierra
    document.addEventListener('click', (e) => {
      if (fab.dataset.open === 'true' && !fab.contains(e.target)) setOpen(false);
    });

    // ESC cierra
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && fab.dataset.open === 'true') {
        setOpen(false);
        toggle.focus();
      }
    });

    // Click en un item cierra
    fab.querySelectorAll('.m3e-fab__item').forEach((item) => {
      item.addEventListener('click', () => setOpen(false));
    });
  }

  /* ─── View Transitions API para cambio de idioma ─── */
  function initViewTransitions() {
    if (!('startViewTransition' in document)) return;

    // Interceptar navegación a otros idiomas
    const langLinks = document.querySelectorAll('a[href$=".html"]');
    langLinks.forEach((link) => {
      const href = link.getAttribute('href');
      if (!href) return;
      // Solo idiomas: index.html, en.html, pt_BR.html, beta.html (mismo origen, navegación interna)
      if (!/^(index|en|pt_BR|beta|from|404|PdP)\.html/i.test(href)) return;
      link.addEventListener('click', (e) => {
        if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
        e.preventDefault();
        document.startViewTransition(() => {
          window.location.href = href;
        });
      });
    });
  }

  /* ─── Init ─── */
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      initSplitButton();
      initFabMenu();
      initViewTransitions();
    });
  } else {
    initSplitButton();
    initFabMenu();
    initViewTransitions();
  }
})();

