/**
 * CertChain — Institute Dashboard JavaScript
 *
 * Handles:
 * - Single certificate issuance (multipart upload)
 * - Batch certificate issuance (Merkle tree)
 * - Certificate management table (load, display, revoke)
 * - UI state management (tabs, toasts, modals)
 */

const API_BASE = '';
let certificates = [];
let revokeTargetHash = null;

// ═══════════════════════════════════════════════════════════
//  Initialization
// ═══════════════════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', () => {
  initTabs();
  initFileUploads();
  initForms();
  loadCertificates();

  // Set default issue date to today
  const dateInput = document.getElementById('issue-date');
  if (dateInput) {
    dateInput.value = new Date().toISOString().split('T')[0];
  }
});

// ═══════════════════════════════════════════════════════════
//  Tab Navigation
// ═══════════════════════════════════════════════════════════

function initTabs() {
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const tab = btn.dataset.tab;

      // Update active button
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      // Update active panel
      document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
      const panel = document.getElementById(`panel-${tab}`);
      if (panel) panel.classList.add('active');

      // Load certificates if switching to manage tab
      if (tab === 'manage') {
        loadCertificates();
      }
    });
  });
}

// ═══════════════════════════════════════════════════════════
//  File Upload UX
// ═══════════════════════════════════════════════════════════

function initFileUploads() {
  setupUploadZone('upload-zone-single', 'file-single', 'filename-single', false);
  setupUploadZone('upload-zone-batch', 'files-batch', 'filename-batch', true);
}

function setupUploadZone(zoneId, inputId, filenameId, isMultiple) {
  const zone = document.getElementById(zoneId);
  const input = document.getElementById(inputId);
  const filename = document.getElementById(filenameId);

  if (!zone || !input || !filename) return;

  // Drag events
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
    updateFilenameDisplay(zone, filename, files, isMultiple);
  });

  input.addEventListener('change', () => {
    updateFilenameDisplay(zone, filename, input.files, isMultiple);
  });
}

function updateFilenameDisplay(zone, filenameEl, files, isMultiple) {
  if (files.length > 0) {
    zone.classList.add('has-file');
    if (isMultiple) {
      filenameEl.textContent = `${files.length} file${files.length > 1 ? 's' : ''} selected`;
    } else {
      filenameEl.textContent = files[0].name;
    }
  } else {
    zone.classList.remove('has-file');
    filenameEl.textContent = '';
  }
}

// ═══════════════════════════════════════════════════════════
//  Forms
// ═══════════════════════════════════════════════════════════

function initForms() {
  // Single issue
  const issueForm = document.getElementById('issue-form');
  if (issueForm) {
    issueForm.addEventListener('submit', handleSingleIssue);
  }

  // Batch issue
  const batchForm = document.getElementById('batch-form');
  if (batchForm) {
    batchForm.addEventListener('submit', handleBatchIssue);
  }
}

// ── Single Certificate Issuance ──────────────────────────

async function handleSingleIssue(e) {
  e.preventDefault();
  const btn = document.getElementById('btn-issue');
  const resultPanel = document.getElementById('issue-result');

  const file = document.getElementById('file-single').files[0];
  if (!file) {
    showToast('error', 'Missing File', 'Please select a PDF file.');
    return;
  }

  const metadata = {
    studentName: document.getElementById('student-name').value.trim(),
    universityName: document.getElementById('university-name').value.trim(),
    courseName: document.getElementById('course-name').value.trim(),
    issueDate: document.getElementById('issue-date').value
  };

  if (!metadata.studentName || !metadata.universityName || !metadata.courseName || !metadata.issueDate) {
    showToast('error', 'Missing Fields', 'Please fill in all required fields.');
    return;
  }

  setLoading(btn, true);
  hideResult(resultPanel);

  try {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));

    const response = await fetch(`${API_BASE}/api/admin/certificates`, {
      method: 'POST',
      body: formData
    });

    if (response.status === 409) {
      showResult(resultPanel, 'error', '⚠️', 'Duplicate Certificate',
        'This certificate has already been issued.');
      showToast('error', 'Duplicate', 'Certificate already exists on-chain.');
      return;
    }

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.detail || `Server returned ${response.status}`);
    }

    const data = await response.json();

    showResult(resultPanel, 'success', '✅', 'Certificate Issued Successfully', '', [
      { label: 'Student', value: data.studentName },
      { label: 'Course', value: data.courseName },
      { label: 'File Hash', value: data.fileHash },
      { label: 'TX Hash', value: data.txHash },
      { label: 'Status', value: data.status }
    ]);

    showToast('success', 'Certificate Issued', `${data.studentName}'s certificate is now on-chain.`);

    // Reset form
    e.target.reset();
    document.getElementById('upload-zone-single').classList.remove('has-file');
    document.getElementById('filename-single').textContent = '';
    document.getElementById('issue-date').value = new Date().toISOString().split('T')[0];

    // Update stats
    loadCertificates();

  } catch (error) {
    showResult(resultPanel, 'error', '❌', 'Issuance Failed', error.message);
    showToast('error', 'Error', error.message);
  } finally {
    setLoading(btn, false);
  }
}

// ── Batch Certificate Issuance ───────────────────────────

async function handleBatchIssue(e) {
  e.preventDefault();
  const btn = document.getElementById('btn-batch');
  const resultPanel = document.getElementById('batch-result');

  const files = document.getElementById('files-batch').files;
  const metadataText = document.getElementById('batch-metadata').value.trim();

  if (files.length === 0) {
    showToast('error', 'Missing Files', 'Please select PDF files.');
    return;
  }

  let metadataArray;
  try {
    metadataArray = JSON.parse(metadataText);
    if (!Array.isArray(metadataArray)) throw new Error('Must be a JSON array');
  } catch (err) {
    showToast('error', 'Invalid JSON', 'Metadata must be a valid JSON array. ' + err.message);
    return;
  }

  if (files.length !== metadataArray.length) {
    showToast('error', 'Mismatch',
      `You selected ${files.length} files but provided ${metadataArray.length} metadata entries.`);
    return;
  }

  setLoading(btn, true);
  hideResult(resultPanel);

  try {
    const formData = new FormData();
    for (let i = 0; i < files.length; i++) {
      formData.append('files', files[i]);
    }
    formData.append('metadata', new Blob([JSON.stringify(metadataArray)], { type: 'application/json' }));

    const response = await fetch(`${API_BASE}/api/admin/certificates/batch`, {
      method: 'POST',
      body: formData
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.detail || `Server returned ${response.status}`);
    }

    const data = await response.json();

    showResult(resultPanel, 'success', '🌳', `Batch Issued: ${data.length} Certificates`, '', [
      { label: 'Certificates', value: `${data.length} certificates issued` },
      { label: 'TX Hash', value: data[0]?.txHash || '—' },
      { label: 'Method', value: 'Merkle Tree (single on-chain TX)' }
    ]);

    showToast('success', 'Batch Issued', `${data.length} certificates issued with Merkle tree.`);

    // Reset
    e.target.reset();
    document.getElementById('upload-zone-batch').classList.remove('has-file');
    document.getElementById('filename-batch').textContent = '';

    loadCertificates();

  } catch (error) {
    showResult(resultPanel, 'error', '❌', 'Batch Issuance Failed', error.message);
    showToast('error', 'Error', error.message);
  } finally {
    setLoading(btn, false);
  }
}

// ═══════════════════════════════════════════════════════════
//  Certificate Management
// ═══════════════════════════════════════════════════════════

async function loadCertificates() {
  try {
    const response = await fetch(`${API_BASE}/api/admin/certificates`);
    if (response.ok) {
      certificates = await response.json();
    } else {
      // If the endpoint doesn't exist yet, use the local list
      certificates = certificates || [];
    }
  } catch (err) {
    // API might not be running
    console.warn('Could not load certificates:', err.message);
  }

  renderCertificateTable();
  updateStats();
}

function renderCertificateTable() {
  const tbody = document.getElementById('cert-table-body');
  const emptyState = document.getElementById('empty-state');
  const tableWrapper = document.getElementById('table-wrapper');

  if (!tbody) return;

  if (certificates.length === 0) {
    tableWrapper.style.display = 'none';
    emptyState.style.display = 'block';
    return;
  }

  tableWrapper.style.display = 'block';
  emptyState.style.display = 'none';

  tbody.innerHTML = certificates.map(cert => `
    <tr>
      <td style="font-weight: 600; color: var(--text-primary);">${escapeHtml(cert.studentName)}</td>
      <td>${escapeHtml(cert.courseName)}</td>
      <td>${escapeHtml(cert.universityName)}</td>
      <td>
        <div class="hash-display" style="font-size: 0.75rem; padding: 4px 8px;">
          <span>${truncateHash(cert.fileHash)}</span>
          <button class="copy-btn" onclick="copyToClipboard('${cert.fileHash}')" title="Copy full hash">📋</button>
        </div>
      </td>
      <td>
        ${cert.status === 'ISSUED'
          ? '<span class="badge badge-success"><span class="badge-dot"></span>Issued</span>'
          : '<span class="badge badge-error"><span class="badge-dot"></span>Revoked</span>'
        }
      </td>
      <td>
        ${cert.status === 'ISSUED'
          ? `<button class="btn btn-ghost" style="font-size: 0.8rem; color: var(--status-error);" onclick="openRevokeModal('${cert.fileHash}')">Revoke</button>`
          : '<span style="color: var(--text-muted); font-size: 0.8rem;">—</span>'
        }
      </td>
    </tr>
  `).join('');
}

function updateStats() {
  const total = certificates.length;
  const active = certificates.filter(c => c.status === 'ISSUED').length;
  const revoked = certificates.filter(c => c.status === 'REVOKED').length;

  animateCounter('stat-total', total);
  animateCounter('stat-active', active);
  animateCounter('stat-revoked', revoked);
}

function animateCounter(elementId, targetValue) {
  const el = document.getElementById(elementId);
  if (!el) return;

  const current = parseInt(el.textContent) || 0;
  if (current === targetValue) return;

  const duration = 500;
  const startTime = performance.now();

  function update(currentTime) {
    const elapsed = currentTime - startTime;
    const progress = Math.min(elapsed / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3); // ease-out cubic
    el.textContent = Math.round(current + (targetValue - current) * eased);
    if (progress < 1) requestAnimationFrame(update);
  }

  requestAnimationFrame(update);
}

// ═══════════════════════════════════════════════════════════
//  Revocation
// ═══════════════════════════════════════════════════════════

function openRevokeModal(hash) {
  revokeTargetHash = hash;
  document.getElementById('revoke-hash-text').textContent = hash;
  document.getElementById('revoke-modal').classList.add('visible');
}

function closeRevokeModal() {
  revokeTargetHash = null;
  document.getElementById('revoke-modal').classList.remove('visible');
}

async function confirmRevoke() {
  if (!revokeTargetHash) return;

  const btn = document.getElementById('btn-confirm-revoke');
  setLoading(btn, true);

  try {
    const response = await fetch(`${API_BASE}/api/admin/certificates/${revokeTargetHash}/revoke`, {
      method: 'PUT'
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.detail || `Server returned ${response.status}`);
    }

    showToast('success', 'Certificate Revoked', 'The certificate has been revoked on the blockchain.');
    closeRevokeModal();
    loadCertificates();

  } catch (error) {
    showToast('error', 'Revocation Failed', error.message);
  } finally {
    setLoading(btn, false);
  }
}

// Close modal on overlay click
document.getElementById('revoke-modal')?.addEventListener('click', (e) => {
  if (e.target === e.currentTarget) closeRevokeModal();
});

// ═══════════════════════════════════════════════════════════
//  UI Utilities
// ═══════════════════════════════════════════════════════════

function setLoading(btn, isLoading) {
  if (!btn) return;
  btn.classList.toggle('loading', isLoading);
  btn.disabled = isLoading;
}

function showResult(panel, type, icon, title, message, details) {
  if (!panel) return;

  panel.className = `result-panel visible ${type}`;

  const iconEl = panel.querySelector('.result-icon');
  const titleEl = panel.querySelector('.result-title');
  const detailsEl = panel.querySelector('.result-details');

  if (iconEl) iconEl.textContent = icon;
  if (titleEl) titleEl.textContent = title;

  if (detailsEl && details) {
    detailsEl.innerHTML = details.map(d => `
      <div class="result-detail">
        <span class="result-detail-label">${d.label}</span>
        <span class="result-detail-value">${escapeHtml(d.value || '—')}</span>
      </div>
    `).join('');
  } else if (detailsEl && message) {
    detailsEl.innerHTML = `<p style="color: var(--text-secondary);">${escapeHtml(message)}</p>`;
  }
}

function hideResult(panel) {
  if (panel) panel.classList.remove('visible');
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

  // Auto-remove after 5s
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 5000);
}

function truncateHash(hash) {
  if (!hash || hash.length < 16) return hash || '—';
  return hash.substring(0, 8) + '…' + hash.substring(hash.length - 8);
}

function copyToClipboard(text) {
  navigator.clipboard.writeText(text).then(() => {
    showToast('success', 'Copied', 'Hash copied to clipboard.');
  }).catch(() => {
    // Fallback
    const textarea = document.createElement('textarea');
    textarea.value = text;
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
    showToast('success', 'Copied', 'Hash copied to clipboard.');
  });
}

function escapeHtml(text) {
  if (!text) return '';
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}
