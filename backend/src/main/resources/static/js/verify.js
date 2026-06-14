/**
 * CertChain — Employer Verification Portal JavaScript
 *
 * Handles:
 * - PDF file upload verification
 * - Hash-based verification
 * - Animated verification progress
 * - Result display with detailed status
 */

const API_BASE = '';

// ═══════════════════════════════════════════════════════════
//  Initialization
// ═══════════════════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', () => {
  initTabs();
  initFileUpload();
  initForms();
});

// ═══════════════════════════════════════════════════════════
//  Tab Navigation
// ═══════════════════════════════════════════════════════════

function initTabs() {
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const tab = btn.dataset.tab;

      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
      const panel = document.getElementById(`panel-${tab}`);
      if (panel) panel.classList.add('active');

      // Hide result when switching tabs
      hideResult();
    });
  });
}

// ═══════════════════════════════════════════════════════════
//  File Upload UX
// ═══════════════════════════════════════════════════════════

function initFileUpload() {
  const zone = document.getElementById('upload-zone-verify');
  const input = document.getElementById('file-verify');
  const filename = document.getElementById('filename-verify');

  if (!zone || !input || !filename) return;

  ['dragenter', 'dragover'].forEach(evt => {
    zone.addEventListener(evt, e => {
      e.preventDefault();
      zone.classList.add('drag-over');
    });
  });

  ['dragleave', 'drop'].forEach(evt => {
    zone.addEventListener(evt, e => {
      e.preventDefault();
      zone.classList.remove('drag-over');
    });
  });

  zone.addEventListener('drop', e => {
    const files = e.dataTransfer.files;
    input.files = files;
    if (files.length > 0) {
      zone.classList.add('has-file');
      filename.textContent = files[0].name;
    }
  });

  input.addEventListener('change', () => {
    if (input.files.length > 0) {
      zone.classList.add('has-file');
      filename.textContent = input.files[0].name;
    } else {
      zone.classList.remove('has-file');
      filename.textContent = '';
    }
  });
}

// ═══════════════════════════════════════════════════════════
//  Forms
// ═══════════════════════════════════════════════════════════

function initForms() {
  const fileForm = document.getElementById('verify-file-form');
  if (fileForm) fileForm.addEventListener('submit', handleVerifyByFile);

  const hashForm = document.getElementById('verify-hash-form');
  if (hashForm) hashForm.addEventListener('submit', handleVerifyByHash);
}

// ── Verify by File Upload ────────────────────────────────

async function handleVerifyByFile(e) {
  e.preventDefault();
  const btn = document.getElementById('btn-verify-file');

  const file = document.getElementById('file-verify').files[0];
  if (!file) {
    showToast('error', 'No File', 'Please select a PDF file to verify.');
    return;
  }

  setLoading(btn, true);
  hideResult();
  showVerifyAnimation();

  try {
    // Step 1: Show hashing status
    updateVerifyStatus('Computing SHA-256 hash of the document...');
    await delay(600); // Brief pause for UX

    // Step 2: Send to server
    updateVerifyStatus('Querying the Ethereum blockchain...');

    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch(`${API_BASE}/api/verify`, {
      method: 'POST',
      body: formData
    });

    const data = await response.json();

    // Step 3: Show result
    await delay(400);
    hideVerifyAnimation();

    if (data.verified) {
      showVerificationResult('success', data);
    } else {
      showVerificationResult('error', data);
    }

  } catch (error) {
    hideVerifyAnimation();
    showVerificationResult('error', {
      verified: false,
      message: 'Connection error: ' + error.message,
      fileHash: '—',
      timestamp: new Date().toISOString()
    });
    showToast('error', 'Verification Failed', error.message);
  } finally {
    setLoading(btn, false);
  }
}

// ── Verify by Hash ───────────────────────────────────────

async function handleVerifyByHash(e) {
  e.preventDefault();
  const btn = document.getElementById('btn-verify-hash');

  const hash = document.getElementById('hash-input').value.trim().toLowerCase();
  if (!hash || hash.length !== 64) {
    showToast('error', 'Invalid Hash', 'Please enter a valid 64-character hex SHA-256 hash.');
    return;
  }

  setLoading(btn, true);
  hideResult();
  showVerifyAnimation();

  try {
    updateVerifyStatus('Querying the Ethereum blockchain...');

    const response = await fetch(`${API_BASE}/api/verify/${hash}`);
    const data = await response.json();

    await delay(600);
    hideVerifyAnimation();

    if (data.verified) {
      showVerificationResult('success', data);
    } else {
      showVerificationResult('error', data);
    }

  } catch (error) {
    hideVerifyAnimation();
    showVerificationResult('error', {
      verified: false,
      message: 'Connection error: ' + error.message,
      fileHash: hash,
      timestamp: new Date().toISOString()
    });
    showToast('error', 'Verification Failed', error.message);
  } finally {
    setLoading(btn, false);
  }
}

// ═══════════════════════════════════════════════════════════
//  Result Display
// ═══════════════════════════════════════════════════════════

function showVerificationResult(type, data) {
  const panel = document.getElementById('verify-result');
  const iconEl = document.getElementById('verify-result-icon');
  const titleEl = document.getElementById('verify-result-title');
  const messageEl = document.getElementById('verify-result-message');
  const detailsEl = document.getElementById('verify-result-details');

  if (!panel) return;

  panel.className = `result-panel visible ${type}`;

  if (type === 'success') {
    iconEl.textContent = '✅';
    titleEl.textContent = 'Certificate Verified';
    messageEl.textContent = data.message || 'This certificate is authentic and has been verified on the blockchain.';
  } else {
    iconEl.textContent = '❌';
    titleEl.textContent = 'Verification Failed';
    messageEl.textContent = data.message || 'This certificate could not be verified.';
  }

  const timestamp = data.timestamp
    ? new Date(data.timestamp).toLocaleString('en-US', {
        dateStyle: 'medium',
        timeStyle: 'short'
      })
    : '—';

  detailsEl.innerHTML = `
    <div class="result-detail">
      <span class="result-detail-label">Status</span>
      <span class="result-detail-value">
        ${data.verified
          ? '<span class="badge badge-success"><span class="badge-dot"></span>Verified</span>'
          : '<span class="badge badge-error"><span class="badge-dot"></span>Not Verified</span>'
        }
      </span>
    </div>
    <div class="result-detail">
      <span class="result-detail-label">File Hash</span>
      <span class="result-detail-value">${escapeHtml(data.fileHash || '—')}</span>
    </div>
    <div class="result-detail">
      <span class="result-detail-label">Checked At</span>
      <span class="result-detail-value">${timestamp}</span>
    </div>
    <div class="result-detail">
      <span class="result-detail-label">Blockchain</span>
      <span class="result-detail-value">Ethereum (Local Hardhat Network)</span>
    </div>
  `;
}

function hideResult() {
  const panel = document.getElementById('verify-result');
  if (panel) panel.classList.remove('visible');
}

// ═══════════════════════════════════════════════════════════
//  Verification Animation
// ═══════════════════════════════════════════════════════════

function showVerifyAnimation() {
  const anim = document.getElementById('verify-animation');
  if (anim) anim.classList.add('visible');
}

function hideVerifyAnimation() {
  const anim = document.getElementById('verify-animation');
  if (anim) anim.classList.remove('visible');
}

function updateVerifyStatus(text) {
  const el = document.getElementById('verify-status-text');
  if (el) el.textContent = text;
}

// ═══════════════════════════════════════════════════════════
//  UI Utilities
// ═══════════════════════════════════════════════════════════

function setLoading(btn, isLoading) {
  if (!btn) return;
  btn.classList.toggle('loading', isLoading);
  btn.disabled = isLoading;
}

function showToast(type, title, message) {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const icons = { success: '✅', error: '❌', info: 'ℹ️' };

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `
    <span class="toast-icon">${icons[type] || 'ℹ️'}</span>
    <div class="toast-body">
      <div class="toast-title">${escapeHtml(title)}</div>
      <div class="toast-message">${escapeHtml(message)}</div>
    </div>
    <button class="toast-close" onclick="this.parentElement.remove()">✕</button>
  `;

  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 5000);
}

function escapeHtml(text) {
  if (!text) return '';
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
