/**
 * Manager Portal Application Logic
 * Spring Boot Rube Goldberg Restaurant Platform
 */

// --- Global State ---
let keycloak = null;
let currentRestaurantId = null;
let restaurantsList = [];
let allReservations = [];
let analyticsIntervalId = null;
let pendingCancelReservationId = null;

// --- DOM References ---
const authLoadingView = document.getElementById('authLoadingView');
const unauthenticatedView = document.getElementById('unauthenticatedView');
const unauthorizedView = document.getElementById('unauthorizedView');
const managerDashboardView = document.getElementById('managerDashboardView');
const keycloakErrorBanner = document.getElementById('keycloakErrorBanner');
const keycloakErrorMsg = document.getElementById('keycloakErrorMsg');

// --- 1. Keycloak OIDC Initialization ---
function resolveKeycloakUrl() {
    if (window.KEYCLOAK_URL) return window.KEYCLOAK_URL;
    const origin = window.location.origin;
    if (origin.includes(':8080')) {
        return origin.replace(':8080', ':8081');
    }
    return 'http://localhost:8081';
}

function showAuthLoading() {
    if (authLoadingView) {
        authLoadingView.classList.remove('d-none');
        authLoadingView.classList.add('d-flex');
        authLoadingView.style.display = 'flex';
    }
}

function hideAuthLoading() {
    if (authLoadingView) {
        authLoadingView.classList.remove('d-flex');
        authLoadingView.classList.add('d-none');
        authLoadingView.style.display = 'none';
    }
}

function initKeycloak() {
    showAuthLoading();
    unauthenticatedView.style.display = 'none';
    unauthorizedView.style.display = 'none';
    managerDashboardView.style.display = 'none';
    keycloakErrorBanner.classList.add('d-none');

    const keycloakUrl = resolveKeycloakUrl();

    if (typeof Keycloak === 'undefined') {
        console.error('Keycloak JS library is not loaded');
        hideAuthLoading();
        unauthenticatedView.style.display = 'block';
        keycloakErrorMsg.innerText = 'Keycloak authentication library failed to load. Please verify Keycloak library is accessible.';
        keycloakErrorBanner.classList.remove('d-none');
        return;
    }

    try {
        keycloak = new Keycloak({
            url: keycloakUrl,
            realm: 'rube-goldberg',
            clientId: 'rube-goldberg-app'
        });

        keycloak.init({
            onLoad: 'check-sso',
            pkceMethod: 'S256',
            checkLoginIframe: false
        }).then(authenticated => {
            hideAuthLoading();
            if (!authenticated) {
                showUnauthenticated();
            } else {
                handleAuthenticatedUser();
            }
        }).catch(err => {
            console.error('Keycloak initialization failed:', err);
            hideAuthLoading();
            unauthenticatedView.style.display = 'block';
            keycloakErrorMsg.innerText = `Authentication service unavailable at ${keycloakUrl}. Please ensure Keycloak is running.`;
            keycloakErrorBanner.classList.remove('d-none');
        });
    } catch (err) {
        console.error('Error instantiating Keycloak client:', err);
        hideAuthLoading();
        unauthenticatedView.style.display = 'block';
        keycloakErrorMsg.innerText = `Authentication initialization error: ${err.message}`;
        keycloakErrorBanner.classList.remove('d-none');
    }
}

function showUnauthenticated() {
    hideAuthLoading();
    unauthenticatedView.style.display = 'block';
    unauthorizedView.style.display = 'none';
    managerDashboardView.style.display = 'none';
}

function handleAuthenticatedUser() {
    hideAuthLoading();
    const roles = keycloak.tokenParsed?.realm_access?.roles || [];
    const isManager = roles.includes('RESTAURANT_MANAGER') || roles.includes('ADMIN');

    if (!isManager) {
        // Authenticated as non-manager (e.g., ROLE_CUSTOMER)
        unauthenticatedView.style.display = 'none';
        managerDashboardView.style.display = 'none';
        unauthorizedView.style.display = 'block';

        document.getElementById('unauthUsername').innerText = keycloak.tokenParsed?.preferred_username || 'Unknown';
        document.getElementById('unauthEmail').innerText = keycloak.tokenParsed?.email || 'N/A';
        document.getElementById('unauthRoles').innerText = roles.join(', ') || 'None';
        return;
    }

    // Authorized Manager / Admin
    unauthenticatedView.style.display = 'none';
    unauthorizedView.style.display = 'none';
    managerDashboardView.style.display = 'block';

    const username = keycloak.tokenParsed?.preferred_username || keycloak.tokenParsed?.name || 'Manager';
    const primaryRole = roles.includes('ADMIN') ? 'ADMIN' : 'RESTAURANT_MANAGER';
    document.getElementById('userBadge').innerText = `${username} (${primaryRole})`;

    // Initialize manager dashboard data
    loadRestaurants();
}

// --- 2. Authenticated HTTP Fetch Wrapper ---
async function authFetch(url, options = {}) {
    if (!keycloak || !keycloak.authenticated) {
        showUnauthenticated();
        throw new Error('Not authenticated');
    }

    try {
        await keycloak.updateToken(10);
    } catch (err) {
        console.warn('Token refresh failed, redirecting to login:', err);
        keycloak.login();
        throw err;
    }

    options.headers = options.headers || {};
    options.headers['Authorization'] = `Bearer ${keycloak.token}`;
    if (options.body && typeof options.body === 'string' && !options.headers['Content-Type']) {
        options.headers['Content-Type'] = 'application/json';
    }

    const res = await fetch(url, options);

    if (res.status === 401) {
        console.warn('Received 401 from API, refreshing session');
        keycloak.login();
        throw new Error('Session expired');
    }

    return res;
}

// --- 3. Restaurant Catalog & Scoping ---
async function loadRestaurants() {
    const select = document.getElementById('restaurantSelect');
    try {
        const res = await authFetch('/api/v1/restaurants?size=100');
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        restaurantsList = data.content || data || [];

        select.innerHTML = '';
        if (restaurantsList.length === 0) {
            select.innerHTML = '<option value="">No Restaurants Registered</option>';
            currentRestaurantId = null;
        } else {
            // Populate individual restaurants
            restaurantsList.forEach((r, idx) => {
                const opt = document.createElement('option');
                opt.value = r.id;
                opt.textContent = `${r.name} (${r.address || r.timezone})`;
                select.appendChild(opt);
            });

            // Add Platform Total option
            const globalOpt = document.createElement('option');
            globalOpt.value = 'ALL';
            globalOpt.textContent = '🌐 All Restaurants (Platform Total)';
            select.appendChild(globalOpt);

            // Default to first restaurant per Clarification Q2
            currentRestaurantId = restaurantsList[0].id;
            select.value = currentRestaurantId;
        }

        onRestaurantChanged();
    } catch (err) {
        console.error('Failed to load restaurants:', err);
        select.innerHTML = '<option value="">Error Loading Restaurants</option>';
    }
}

function onRestaurantChanged() {
    const select = document.getElementById('restaurantSelect');
    currentRestaurantId = select.value;

    const isGlobal = currentRestaurantId === 'ALL';
    const label = document.getElementById('analyticsScopeLabel');
    if (isGlobal) {
        label.innerText = 'Showing platform-wide aggregated metrics across all restaurants';
    } else {
        const r = restaurantsList.find(x => x.id === currentRestaurantId);
        label.innerText = r ? `Showing metrics for ${r.name}` : 'Showing scoped restaurant metrics';
    }

    // Refresh active tab
    loadAnalytics();
    if (!isGlobal && currentRestaurantId) {
        loadReservations();
        loadTablesAndHours(currentRestaurantId);
        loadWaitingList();
    } else {
        document.getElementById('reservationsTableBody').innerHTML = '<tr><td colspan="7" class="text-center py-4 text-muted">Select an individual restaurant to inspect reservations.</td></tr>';
        document.getElementById('tablesTableBody').innerHTML = '<tr><td colspan="5" class="text-center py-3 text-muted">Select an individual restaurant to view floor tables.</td></tr>';
        document.getElementById('combinationsTableBody').innerHTML = '<tr><td colspan="5" class="text-center py-3 text-muted">Select an individual restaurant to view table combinations.</td></tr>';
        document.getElementById('waitingListTableBody').innerHTML = '<tr><td colspan="6" class="text-center py-4 text-muted">Select an individual restaurant to inspect waiting list queue.</td></tr>';
        document.getElementById('restaurantProfileDetails').innerHTML = 'Select a specific restaurant to view establishment profile.';
    }
}

// --- 4. Live Business Analytics ---
async function loadAnalytics() {
    const errorAlert = document.getElementById('analyticsErrorAlert');
    errorAlert.classList.add('d-none');

    let url = '/api/v1/analytics/summary';
    if (currentRestaurantId && currentRestaurantId !== 'ALL') {
        url += `?restaurantId=${currentRestaurantId}`;
    }

    try {
        const res = await authFetch(url);
        if (!res.ok) {
            throw new Error(`Analytics service returned HTTP ${res.status}`);
        }
        const data = await res.json();

        document.getElementById('mTotal').innerText = data.totalReservations ?? 0;
        document.getElementById('mCancelled').innerText = data.cancelledReservations ?? 0;
        document.getElementById('mCancelRate').innerText = `${(data.cancellationRate ?? 0).toFixed(1)}% Rate`;
        document.getElementById('mNoShow').innerText = data.noShows ?? 0;
        document.getElementById('mWait').innerText = data.waitingListEntries ?? 0;
        document.getElementById('mConv').innerText = `${(data.waitingListConversionRate ?? 0).toFixed(1)}%`;
        document.getElementById('mAvgParty').innerText = (data.averagePartySize ?? 0).toFixed(1);

        // Party Size Distribution
        const partyDist = data.partySizeDistribution || {};
        const partyEntries = Object.entries(partyDist);
        const partyContainer = document.getElementById('partySizeContainer');
        if (partyEntries.length === 0) {
            partyContainer.innerHTML = '<span class="text-muted">No reservations recorded yet.</span>';
        } else {
            partyContainer.innerHTML = partyEntries.map(([size, count]) => `
                <div class="d-flex justify-content-between align-items-center mb-1">
                    <span>${size} Guests</span>
                    <span class="badge bg-secondary">${count} bookings</span>
                </div>
            `).join('');
        }

        // Cancellation Category Breakdown
        const cancelCats = data.cancellationCategoryBreakdown || {};
        const cancelEntries = Object.entries(cancelCats);
        const cancelContainer = document.getElementById('cancellationReasonsContainer');
        if (cancelEntries.length === 0) {
            cancelContainer.innerHTML = '<span class="text-muted">No cancellations recorded.</span>';
        } else {
            cancelContainer.innerHTML = cancelEntries.map(([cat, count]) => `
                <div class="d-flex justify-content-between align-items-center mb-1">
                    <span>${cat.replace(/_/g, ' ')}</span>
                    <span class="badge bg-danger">${count}</span>
                </div>
            `).join('');
        }
    } catch (err) {
        console.error('Failed to load analytics:', err);
        errorAlert.classList.remove('d-none');
        document.getElementById('analyticsErrorMsg').innerText = `Error loading analytics: ${err.message}`;
    }
}

// --- 5. Reservations Management & Back-Filling ---
async function loadReservations() {
    if (!currentRestaurantId || currentRestaurantId === 'ALL') return;

    const tbody = document.getElementById('reservationsTableBody');
    tbody.innerHTML = '<tr><td colspan="7" class="text-center py-3"><span class="spinner-border spinner-border-sm"></span> Loading reservations...</td></tr>';

    try {
        const res = await authFetch(`/api/v1/reservations?restaurantId=${currentRestaurantId}&size=100`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        allReservations = data.content || data || [];

        filterReservationsTable();
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center text-danger py-3">Failed to load reservations: ${err.message}</td></tr>`;
    }
}

function filterReservationsTable() {
    const tbody = document.getElementById('reservationsTableBody');
    const statusFilter = document.getElementById('resStatusFilter').value;
    const searchFilter = (document.getElementById('resSearchInput').value || '').toLowerCase().trim();

    let filtered = allReservations.filter(r => {
        if (statusFilter !== 'ALL' && r.status !== statusFilter) return false;
        if (searchFilter) {
            const name = (r.customerName || '').toLowerCase();
            const email = (r.customerEmail || '').toLowerCase();
            if (!name.includes(searchFilter) && !email.includes(searchFilter)) return false;
        }
        return true;
    });

    if (filtered.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">No reservations matching filters.</td></tr>';
        return;
    }

    tbody.innerHTML = filtered.map(r => {
        const startTimeStr = new Date(r.startTime).toLocaleString();
        const statusBadge = getStatusBadge(r.status);
        const tablesStr = (r.allocatedTableLabels && r.allocatedTableLabels.length > 0)
            ? escapeHtml(r.allocatedTableLabels.join(', '))
            : ((r.allocatedTables && r.allocatedTables.length > 0) ? `${r.allocatedTables.length} table(s)` : '-');

        let statusContent = statusBadge;
        if (r.status === 'CANCELLED' && r.cancellationReason) {
            statusContent += `<div class="mt-1"><small class="text-danger-emphasis fst-italic"><i class="bi bi-info-circle me-1"></i>${escapeHtml(r.cancellationReason)}</small></div>`;
        }

        let actionButtons = '';
        if (r.status === 'CONFIRMED') {
            actionButtons = `
                <button class="btn btn-sm btn-outline-success py-0 px-1 me-1" onclick="updateReservationStatus('${r.id}', 'ARRIVED')" title="Mark as Arrived">Arrived</button>
                <button class="btn btn-sm btn-outline-warning py-0 px-1 me-1" onclick="updateReservationStatus('${r.id}', 'NO_SHOW')" title="Mark No-Show">No-Show</button>
                <button class="btn btn-sm btn-outline-danger py-0 px-1" onclick="openCancelModal('${r.id}', '${escapeHtml(r.customerName)}', '${startTimeStr}', ${r.partySize})" title="Cancel Reservation">Cancel</button>
            `;
        } else if (r.status === 'ARRIVED') {
            actionButtons = `
                <button class="btn btn-sm btn-success py-0 px-2" onclick="updateReservationStatus('${r.id}', 'COMPLETED')" title="Mark Completed">Complete</button>
            `;
        } else {
            actionButtons = `<span class="text-muted small">Terminal</span>`;
        }

        return `
            <tr>
                <td><strong>${escapeHtml(r.customerName || 'Guest')}</strong></td>
                <td><small class="text-muted">${escapeHtml(r.customerEmail || '-')}</small></td>
                <td><span class="badge bg-light text-dark border">${r.partySize} guests</span></td>
                <td><small>${startTimeStr}</small></td>
                <td><small class="fw-semibold text-primary">${tablesStr}</small></td>
                <td>${statusContent}</td>
                <td class="text-end">${actionButtons}</td>
            </tr>
        `;
    }).join('');
}

function getStatusBadge(status) {
    switch (status) {
        case 'CONFIRMED': return '<span class="badge bg-primary status-badge">CONFIRMED</span>';
        case 'ARRIVED': return '<span class="badge bg-info text-dark status-badge">ARRIVED</span>';
        case 'COMPLETED': return '<span class="badge bg-success status-badge">COMPLETED</span>';
        case 'NO_SHOW': return '<span class="badge bg-warning text-dark status-badge">NO_SHOW</span>';
        case 'CANCELLED': return '<span class="badge bg-danger status-badge">CANCELLED</span>';
        default: return `<span class="badge bg-secondary status-badge">${status}</span>`;
    }
}

async function updateReservationStatus(id, newStatus) {
    try {
        const res = await authFetch(`/api/v1/reservations/${id}/status`, {
            method: 'PATCH',
            body: JSON.stringify({ status: newStatus })
        });
        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.detail || errData.title || `HTTP ${res.status}`);
        }
        loadReservations();
        loadAnalytics();
    } catch (err) {
        alert(`Failed to update status: ${err.message}`);
    }
}

function openCancelModal(id, guestName, startTime, partySize) {
    pendingCancelReservationId = id;
    document.getElementById('cancelModalId').innerText = id;
    document.getElementById('cancelModalGuest').innerText = guestName;
    document.getElementById('cancelModalTime').innerText = startTime;
    document.getElementById('cancelModalParty').innerText = `${partySize} guests`;
    document.getElementById('cancelReasonInput').value = '';

    const modal = new bootstrap.Modal(document.getElementById('cancelReservationModal'));
    modal.show();
}

async function confirmCancelReservation() {
    if (!pendingCancelReservationId) return;
    const reason = document.getElementById('cancelReasonInput').value || 'Manager cancelled';

    try {
        const res = await authFetch(`/api/v1/reservations/${pendingCancelReservationId}?reason=${encodeURIComponent(reason)}`, {
            method: 'DELETE'
        });
        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.detail || errData.title || `HTTP ${res.status}`);
        }

        const modalEl = document.getElementById('cancelReservationModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();

        loadReservations();
        loadAnalytics();
    } catch (err) {
        alert(`Failed to cancel reservation: ${err.message}`);
    } finally {
        pendingCancelReservationId = null;
    }
}

async function handleCreateReservation(e) {
    e.preventDefault();
    if (!currentRestaurantId || currentRestaurantId === 'ALL') {
        alert('Please select a specific restaurant before booking a reservation.');
        return;
    }

    const errAlert = document.getElementById('newResErrorAlert');
    errAlert.classList.add('d-none');

    const name = document.getElementById('newResName').value;
    const email = document.getElementById('newResEmail').value;
    const party = parseInt(document.getElementById('newResParty').value);
    const duration = parseInt(document.getElementById('newResDuration').value);
    const dt = document.getElementById('newResDateTime').value;

    const payload = {
        restaurantId: currentRestaurantId,
        customerName: name,
        customerEmail: email,
        partySize: party,
        durationMinutes: duration,
        startTime: new Date(dt).toISOString()
    };

    try {
        const res = await authFetch('/api/v1/reservations', {
            method: 'POST',
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.detail || errData.title || `HTTP ${res.status}`);
        }

        const modalEl = document.getElementById('newReservationModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
        document.getElementById('newReservationForm').reset();

        loadReservations();
        loadAnalytics();
    } catch (err) {
        errAlert.innerText = `Booking failed: ${err.message}`;
        errAlert.classList.remove('d-none');
    }
}

// --- 6. Floor Layout & Tables Management ---
async function loadTablesAndHours(restaurantId) {
    if (!restaurantId || restaurantId === 'ALL') return;

    const profileDiv = document.getElementById('restaurantProfileDetails');
    const tbody = document.getElementById('tablesTableBody');
    const combTbody = document.getElementById('combinationsTableBody');

    try {
        // 1. Fetch Restaurant Details
        const rRes = await authFetch(`/api/v1/restaurants/${restaurantId}`);
        if (rRes.ok) {
            const r = await rRes.json();
            profileDiv.innerHTML = `
                <div class="mb-2"><strong>Name:</strong> ${escapeHtml(r.name)}</div>
                <div class="mb-2"><strong>Address:</strong> ${escapeHtml(r.address || 'N/A')}</div>
                <div class="mb-2"><strong>Timezone:</strong> <code>${escapeHtml(r.timezone || 'Europe/Amsterdam')}</code></div>
                <div class="mb-2"><strong>Min / Default / Max Duration:</strong> ${r.minReservationDurationMinutes || 45} / ${r.defaultReservationDurationMinutes} / ${r.maxReservationDurationMinutes || 180} mins</div>
                <div class="mb-2"><strong>Min Lead Time:</strong> ${r.minBookingAdvanceMinutes} mins</div>
                <div class="mb-2"><strong>Max Advance Horizon:</strong> ${r.maxBookingHorizonDays} days</div>
                <div><strong>Cancellation Window:</strong> ${r.cancellationWindowHours} hours</div>
            `;
        }

        // 2. Fetch Tables
        const tRes = await authFetch(`/api/v1/restaurants/${restaurantId}/tables`);
        if (tRes.ok) {
            const tables = await tRes.json();
            window.currentTables = tables;
            if (tables.length === 0) {
                tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">No tables registered yet.</td></tr>';
            } else {
                tbody.innerHTML = tables.map(t => `
                    <tr>
                        <td><strong>${escapeHtml(t.tableNumber)}</strong></td>
                        <td><span class="badge bg-light text-dark border">${t.capacity} seats</span></td>
                        <td><span class="badge bg-secondary-subtle text-secondary border">${escapeHtml(t.zone || 'Main Dining Room')}</span></td>
                        <td><small class="text-muted font-monospace">${t.id}</small></td>
                        <td>
                            <button class="btn btn-outline-primary btn-sm py-0 px-2 me-1" onclick="openEditTableModal('${t.id}')">
                                <i class="bi bi-pencil"></i> Edit
                            </button>
                            <button class="btn btn-outline-danger btn-sm py-0 px-2" onclick="openDeleteTableModal('${t.id}', '${escapeHtml(t.tableNumber)}')">
                                <i class="bi bi-trash"></i> Delete
                            </button>
                        </td>
                    </tr>
                `).join('');
            }
            populateZoneSelector();
        }

        // 3. Fetch Table Combinations
        if (combTbody) {
            const cRes = await authFetch(`/api/v1/restaurants/${restaurantId}/table-combinations`);
            if (cRes.ok) {
                const combinations = await cRes.json();
                window.currentCombinations = combinations;
                if (combinations.length === 0) {
                    combTbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">No table combinations registered yet.</td></tr>';
                } else {
                    combTbody.innerHTML = combinations.map(c => `
                        <tr>
                            <td><strong>${escapeHtml(c.name)}</strong></td>
                            <td><span class="badge bg-secondary-subtle text-secondary border">${escapeHtml(c.zone || 'Main Dining Room')}</span></td>
                            <td><span class="badge bg-light text-dark border">${escapeHtml((c.tableNumbers || []).join(' + '))}</span></td>
                            <td><strong>${c.combinedCapacity}</strong> seats</td>
                            <td>
                                <button class="btn btn-outline-primary btn-sm py-0 px-2 me-1" onclick="openEditCombinationModal('${c.id}')">
                                    <i class="bi bi-pencil"></i> Edit
                                </button>
                                <button class="btn btn-outline-danger btn-sm py-0 px-2" onclick="handleDeleteCombination('${c.id}', '${escapeHtml(c.name)}')">
                                    <i class="bi bi-trash"></i> Delete
                                </button>
                            </td>
                        </tr>
                    `).join('');
                }
            } else {
                combTbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">No table combinations registered yet.</td></tr>';
            }
        }

        // 4. Load existing hours
        loadExistingHours(restaurantId);
    } catch (err) {
        console.error('Failed to load floor tables and combinations:', err);
    }
}

function populateZoneSelector() {
    const select = document.getElementById('combZoneSelect');
    if (!select) return;
    const tables = window.currentTables || [];
    const zones = Array.from(new Set(tables.map(t => (t.zone || 'Main Dining Room').trim()))).sort();

    select.innerHTML = '<option value="">-- Select Zone --</option>' +
        zones.map(z => `<option value="${escapeHtml(z)}">${escapeHtml(z)}</option>`).join('');

    if (zones.length === 1) {
        select.value = zones[0];
    }
    filterCombinationTablesByZone();
}

function filterCombinationTablesByZone() {
    const select = document.getElementById('combZoneSelect');
    const comboContainer = document.getElementById('combinationTablesChecklist');
    if (!comboContainer) return;
    const selectedZone = select ? select.value : '';

    if (!selectedZone) {
        comboContainer.innerHTML = '<span class="small text-muted">Select a zone first...</span>';
        updateCombinationDefaults();
        return;
    }

    const tables = (window.currentTables || []).filter(t => (t.zone || 'Main Dining Room').trim().toLowerCase() === selectedZone.toLowerCase());
    if (tables.length === 0) {
        comboContainer.innerHTML = '<span class="small text-muted">No tables available in this zone.</span>';
    } else {
        comboContainer.innerHTML = tables.map(t => `
            <div class="form-check">
                <input class="form-check-input combo-table-check" type="checkbox" value="${t.id}" data-capacity="${t.capacity}" data-number="${escapeHtml(t.tableNumber)}" id="chk_${t.id}" onchange="updateCombinationDefaults()">
                <label class="form-check-label small" for="chk_${t.id}">
                    Table <strong>${escapeHtml(t.tableNumber)}</strong> (${t.capacity} seats)
                </label>
            </div>
        `).join('');
    }
    updateCombinationDefaults();
}

function updateCombinationDefaults() {
    const checkedBoxes = document.querySelectorAll('.combo-table-check:checked');
    let totalCap = 0;
    const tableNums = [];
    checkedBoxes.forEach(cb => {
        totalCap += parseInt(cb.getAttribute('data-capacity') || 0);
        tableNums.push(cb.getAttribute('data-number'));
    });
    const nameInput = document.getElementById('newCombName');
    const capInput = document.getElementById('newCombCapacity');
    const capHelp = document.getElementById('combCapacityHelp');

    if (nameInput) {
        nameInput.placeholder = tableNums.length > 0 ? ('Combo: ' + tableNums.join(' + ')) : 'e.g. Combo: T1 + T2';
    }
    if (capInput) {
        capInput.max = totalCap > 0 ? totalCap : 100;
        capInput.placeholder = totalCap > 0 ? totalCap : 'Auto-calculated sum';
        if (tableNums.length >= 2 && (!capInput.value || parseInt(capInput.value) > totalCap)) {
            capInput.value = totalCap;
        }
    }
    if (capHelp) {
        if (totalCap > 0) {
            capHelp.innerText = `Constituent table sum: ${totalCap} seats. Custom capacity cannot exceed ${totalCap}.`;
        } else {
            capHelp.innerText = 'Defaults to sum of constituent table capacities. Can be reduced down.';
        }
    }
}

function openEditTableModal(tableId) {
    const table = (window.currentTables || []).find(t => t.id === tableId);
    if (!table) return;
    document.getElementById('editTableId').value = table.id;
    document.getElementById('editTableNumber').value = table.tableNumber;
    document.getElementById('editTableCapacity').value = table.capacity;
    document.getElementById('editTableZone').value = table.zone || 'Main Dining';
    document.getElementById('editTableErrorAlert').classList.add('d-none');
    const modal = new bootstrap.Modal(document.getElementById('editTableModal'));
    modal.show();
}

async function handleEditTable(e) {
    e.preventDefault();
    if (!currentRestaurantId || currentRestaurantId === 'ALL') return;
    const tableId = document.getElementById('editTableId').value;
    const tableNumber = document.getElementById('editTableNumber').value;
    const capacity = parseInt(document.getElementById('editTableCapacity').value);
    const zone = document.getElementById('editTableZone').value;
    const errAlert = document.getElementById('editTableErrorAlert');
    errAlert.classList.add('d-none');

    try {
        const res = await authFetch(`/api/v1/restaurants/${currentRestaurantId}/tables/${tableId}`, {
            method: 'PUT',
            body: JSON.stringify({ tableNumber, capacity, zone })
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.detail || err.title || `HTTP ${res.status}`);
        }
        const modalEl = document.getElementById('editTableModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
        loadTablesAndHours(currentRestaurantId);
    } catch (err) {
        errAlert.innerText = `Failed to update table: ${err.message}`;
        errAlert.classList.remove('d-none');
    }
}

function openEditCombinationModal(combId) {
    const comb = (window.currentCombinations || []).find(c => c.id === combId);
    if (!comb) return;
    document.getElementById('editCombId').value = comb.id;
    document.getElementById('editCombName').value = comb.name || '';
    document.getElementById('editCombZone').innerText = comb.zone || 'Main Dining Room';
    document.getElementById('editCombTables').innerText = (comb.tableNumbers || []).join(' + ');

    const tables = window.currentTables || [];
    const memberTables = tables.filter(t => (comb.tableIds || []).includes(t.id));
    const physicalSum = memberTables.reduce((acc, t) => acc + (t.capacity || 0), 0) || comb.combinedCapacity;

    const capInput = document.getElementById('editCombCapacity');
    capInput.value = comb.combinedCapacity;
    capInput.max = physicalSum;
    document.getElementById('editCombCapacityHelp').innerText = `Max physical capacity: ${physicalSum} seats.`;
    document.getElementById('editCombErrorAlert').classList.add('d-none');

    const modal = new bootstrap.Modal(document.getElementById('editCombinationModal'));
    modal.show();
}
window.openEditCombinationModal = openEditCombinationModal;

async function handleEditCombination(e) {
    e.preventDefault();
    if (!currentRestaurantId || currentRestaurantId === 'ALL') return;

    const combId = document.getElementById('editCombId').value;
    const name = document.getElementById('editCombName').value;
    const capVal = document.getElementById('editCombCapacity').value;
    const capacity = parseInt(capVal, 10);
    const errAlert = document.getElementById('editCombErrorAlert');
    errAlert.classList.add('d-none');

    const maxCap = parseInt(document.getElementById('editCombCapacity').max, 10) || 100;
    if (capacity > maxCap) {
        errAlert.innerText = `Custom capacity (${capacity}) cannot exceed constituent table sum (${maxCap}).`;
        errAlert.classList.remove('d-none');
        return;
    }

    try {
        const res = await authFetch(`/api/v1/restaurants/${currentRestaurantId}/table-combinations/${combId}`, {
            method: 'PUT',
            body: JSON.stringify({
                name: name.trim(),
                combinedCapacity: capacity
            })
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.detail || errData.title || `HTTP ${res.status}`);
        }

        const modalEl = document.getElementById('editCombinationModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();

        loadTablesAndHours(currentRestaurantId);
    } catch (err) {
        errAlert.innerText = `Failed to update combination: ${err.message}`;
        errAlert.classList.remove('d-none');
    }
}

let pendingDeleteTableId = null;

function openDeleteTableModal(tableId, tableNumber) {
    pendingDeleteTableId = tableId;
    document.getElementById('deleteTableNumberSpan').innerText = tableNumber;
    const modal = new bootstrap.Modal(document.getElementById('deleteTableModal'));
    modal.show();
}

async function confirmDeleteTable() {
    if (!pendingDeleteTableId || !currentRestaurantId) return;
    try {
        const res = await authFetch(`/api/v1/restaurants/${currentRestaurantId}/tables/${pendingDeleteTableId}`, {
            method: 'DELETE'
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.detail || err.title || `HTTP ${res.status}`);
        }
        const modalEl = document.getElementById('deleteTableModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
        loadTablesAndHours(currentRestaurantId);
    } catch (err) {
        alert(`Failed to delete table: ${err.message}`);
    } finally {
        pendingDeleteTableId = null;
    }
}

function openEditSettingsModal() {
    if (!currentRestaurantId || currentRestaurantId === 'ALL') return;
    const r = (restaurantsList || []).find(rest => rest.id === currentRestaurantId);
    if (!r) return;
    document.getElementById('editSettingsName').value = r.name || '';
    document.getElementById('editSettingsAddress').value = r.address || '';
    document.getElementById('editSettingsTimezone').value = r.timezone || 'Europe/Amsterdam';
    document.getElementById('editSettingsMinDuration').value = r.minReservationDurationMinutes || 45;
    document.getElementById('editSettingsDefaultDuration').value = r.defaultReservationDurationMinutes || 90;
    document.getElementById('editSettingsMaxDuration').value = r.maxReservationDurationMinutes || 180;
    document.getElementById('editSettingsMinAdvance').value = r.minBookingAdvanceMinutes || 30;
    document.getElementById('editSettingsMaxHorizon').value = r.maxBookingHorizonDays || 60;
    document.getElementById('editSettingsCancelWindow').value = r.cancellationWindowHours || 2;
    document.getElementById('editSettingsErrorAlert').classList.add('d-none');
    const modal = bootstrap.Modal.getOrCreateInstance(document.getElementById('editRestaurantSettingsModal'));
    modal.show();
}

async function handleSaveSettings(e) {
    e.preventDefault();
    if (!currentRestaurantId || currentRestaurantId === 'ALL') return;
    const errAlert = document.getElementById('editSettingsErrorAlert');
    errAlert.classList.add('d-none');

    const payload = {
        name: document.getElementById('editSettingsName').value,
        address: document.getElementById('editSettingsAddress').value,
        timezone: document.getElementById('editSettingsTimezone').value,
        minReservationDurationMinutes: parseInt(document.getElementById('editSettingsMinDuration').value),
        defaultReservationDurationMinutes: parseInt(document.getElementById('editSettingsDefaultDuration').value),
        maxReservationDurationMinutes: parseInt(document.getElementById('editSettingsMaxDuration').value),
        minBookingAdvanceMinutes: parseInt(document.getElementById('editSettingsMinAdvance').value),
        maxBookingHorizonDays: parseInt(document.getElementById('editSettingsMaxHorizon').value),
        cancellationWindowHours: parseInt(document.getElementById('editSettingsCancelWindow').value)
    };

    try {
        const res = await authFetch(`/api/v1/restaurants/${currentRestaurantId}`, {
            method: 'PUT',
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({}));
            throw new Error(err.detail || err.title || `HTTP ${res.status}`);
        }
        const updated = await res.json();
        const idx = restaurantsList.findIndex(r => r.id === currentRestaurantId);
        if (idx >= 0) restaurantsList[idx] = updated;

        const modalEl = document.getElementById('editRestaurantSettingsModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();

        loadTablesAndHours(currentRestaurantId);
    } catch (err) {
        errAlert.innerText = `Failed to save settings: ${err.message}`;
        errAlert.classList.remove('d-none');
    }
}

async function handleAddTable(e) {
    e.preventDefault();
    if (!currentRestaurantId || currentRestaurantId === 'ALL') return;

    const errAlert = document.getElementById('addTableErrorAlert');
    errAlert.classList.add('d-none');

    const num = document.getElementById('newTableNumber').value;
    const cap = parseInt(document.getElementById('newTableCapacity').value);
    const zone = document.getElementById('newTableZone').value;

    try {
        const res = await authFetch(`/api/v1/restaurants/${currentRestaurantId}/tables`, {
            method: 'POST',
            body: JSON.stringify({
                tableNumber: num,
                capacity: cap,
                zone: zone
            })
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.detail || errData.title || `HTTP ${res.status}`);
        }

        const modalEl = document.getElementById('addTableModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
        document.getElementById('addTableForm').reset();

        loadTablesAndHours(currentRestaurantId);
    } catch (err) {
        errAlert.innerText = `Failed to add table: ${err.message}`;
        errAlert.classList.remove('d-none');
    }
}

async function handleAddCombination(e) {
    e.preventDefault();
    if (!currentRestaurantId || currentRestaurantId === 'ALL') return;

    const errAlert = document.getElementById('addCombErrorAlert');
    errAlert.classList.add('d-none');

    const name = document.getElementById('newCombName')?.value || null;
    const checkedBoxes = document.querySelectorAll('.combo-table-check:checked');
    const tableIds = Array.from(checkedBoxes).map(cb => cb.value);
    const capacityVal = document.getElementById('newCombCapacity').value;
    const capacity = capacityVal ? parseInt(capacityVal, 10) : null;

    if (tableIds.length < 2) {
        errAlert.innerText = 'Please select at least 2 tables to create a combination.';
        errAlert.classList.remove('d-none');
        return;
    }

    let totalCap = 0;
    checkedBoxes.forEach(cb => {
        totalCap += parseInt(cb.dataset.capacity) || 0;
    });

    if (capacity !== null && capacity > totalCap) {
        errAlert.innerText = `Custom capacity (${capacity}) cannot exceed the sum of constituent table capacities (${totalCap}).`;
        errAlert.classList.remove('d-none');
        return;
    }

    try {
        const payload = { tableIds };
        if (name && name.trim()) payload.name = name.trim();
        if (capacity && capacity > 0) payload.combinedCapacity = capacity;

        const res = await authFetch(`/api/v1/restaurants/${currentRestaurantId}/table-combinations`, {
            method: 'POST',
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.detail || errData.title || `HTTP ${res.status}`);
        }

        const modalEl = document.getElementById('addCombinationModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
        document.getElementById('addCombinationForm').reset();

        loadTablesAndHours(currentRestaurantId);
    } catch (err) {
        errAlert.innerText = `Failed to create combination: ${err.message}`;
        errAlert.classList.remove('d-none');
    }
}

async function handleDeleteCombination(combinationId, combinationName) {
    if (!currentRestaurantId || currentRestaurantId === 'ALL') return;

    if (!confirm(`Are you sure you want to delete table combination "${combinationName}"?\n\nThis will immediately unpublish it from future reservation availability. Any confirmed reservations will remain intact.`)) {
        return;
    }

    try {
        const res = await authFetch(`/api/v1/restaurants/${currentRestaurantId}/table-combinations/${combinationId}`, {
            method: 'DELETE'
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.detail || errData.title || `HTTP ${res.status}`);
        }

        loadTablesAndHours(currentRestaurantId);
    } catch (err) {
        alert(`Failed to delete combination: ${err.message}`);
    }
}
window.handleDeleteCombination = handleDeleteCombination;

function initHoursEditor() {
    const tbody = document.getElementById('hoursEditorTableBody');
    const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
    tbody.innerHTML = days.map((day, idx) => `
        <tr>
            <td><strong>${day}</strong></td>
            <td>
                <div class="form-check form-switch">
                    <input class="form-check-input hours-closed-toggle" type="checkbox" id="closed_toggle_${idx + 1}" data-day="${idx + 1}" onchange="toggleDayClosed(${idx + 1})">
                    <label class="form-check-label small" for="closed_toggle_${idx + 1}">Closed</label>
                </div>
            </td>
            <td><input type="time" class="form-control form-control-sm hours-open" id="hours_open_${idx + 1}" data-day="${idx + 1}" value="11:00"></td>
            <td><input type="time" class="form-control form-control-sm hours-close" id="hours_close_${idx + 1}" data-day="${idx + 1}" value="23:00"></td>
        </tr>
    `).join('');
}

function toggleDayClosed(day) {
    const isClosed = document.getElementById(`closed_toggle_${day}`).checked;
    const openInp = document.getElementById(`hours_open_${day}`);
    const closeInp = document.getElementById(`hours_close_${day}`);
    if (openInp && closeInp) {
        openInp.disabled = isClosed;
        closeInp.disabled = isClosed;
    }
}

async function loadExistingHours(restaurantId) {
    try {
        const res = await authFetch(`/api/v1/restaurants/${restaurantId}/opening-hours`);
        if (res.ok) {
            const schedules = await res.json();
            schedules.forEach(s => {
                if (s.dayOfWeek) {
                    const day = s.dayOfWeek;
                    const closedToggle = document.getElementById(`closed_toggle_${day}`);
                    const openInp = document.getElementById(`hours_open_${day}`);
                    const closeInp = document.getElementById(`hours_close_${day}`);
                    if (closedToggle && openInp && closeInp) {
                        closedToggle.checked = !!s.isClosed;
                        if (s.openTime) openInp.value = s.openTime.substring(0, 5);
                        if (s.closeTime) closeInp.value = s.closeTime.substring(0, 5);
                        openInp.disabled = !!s.isClosed;
                        closeInp.disabled = !!s.isClosed;
                    }
                }
            });
        }
    } catch (err) {
        console.warn('Failed to load existing hours:', err);
    }
}

async function handleSaveOperatingHours(e) {
    e.preventDefault();
    if (!currentRestaurantId || currentRestaurantId === 'ALL') return;

    const errAlert = document.getElementById('editHoursErrorAlert');
    errAlert.classList.add('d-none');

    const openInputs = document.querySelectorAll('.hours-open');
    const closeInputs = document.querySelectorAll('.hours-close');
    const closedToggles = document.querySelectorAll('.hours-closed-toggle');

    const schedule = [];
    openInputs.forEach((inp, idx) => {
        const day = parseInt(inp.getAttribute('data-day'));
        const isClosed = closedToggles[idx]?.checked || false;
        schedule.push({
            dayOfWeek: day,
            isClosed: isClosed,
            openTime: isClosed ? null : (inp.value ? inp.value + ':00' : '11:00:00'),
            closeTime: isClosed ? null : (closeInputs[idx].value ? closeInputs[idx].value + ':00' : '23:00:00')
        });
    });

    try {
        const res = await authFetch(`/api/v1/restaurants/${currentRestaurantId}/opening-hours`, {
            method: 'PUT',
            body: JSON.stringify({ schedules: schedule })
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.detail || errData.title || `HTTP ${res.status}`);
        }

        const modalEl = document.getElementById('editHoursModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();

        alert('Operating hours updated successfully.');
    } catch (err) {
        errAlert.innerText = `Failed to update hours: ${err.message}`;
        errAlert.classList.remove('d-none');
    }
}

async function handleRegisterRestaurant(e) {
    e.preventDefault();
    const errAlert = document.getElementById('regErrorAlert');
    errAlert.classList.add('d-none');

    const name = document.getElementById('regName').value;
    const address = document.getElementById('regAddress').value;
    const tz = document.getElementById('regTimezone').value;
    const minDuration = parseInt(document.getElementById('regMinDuration').value) || 45;
    const duration = parseInt(document.getElementById('regDuration').value);
    const maxDuration = parseInt(document.getElementById('regMaxDuration').value) || 180;
    const advance = parseInt(document.getElementById('regAdvance').value);
    const horizon = parseInt(document.getElementById('regHorizon').value);
    const cancelWindow = parseInt(document.getElementById('regCancelWindow').value);

    try {
        const res = await authFetch('/api/v1/restaurants', {
            method: 'POST',
            body: JSON.stringify({
                name: name,
                address: address,
                timezone: tz,
                minReservationDurationMinutes: minDuration,
                defaultReservationDurationMinutes: duration,
                maxReservationDurationMinutes: maxDuration,
                minBookingAdvanceMinutes: advance,
                maxBookingHorizonDays: horizon,
                cancellationWindowHours: cancelWindow
            })
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.detail || errData.title || `HTTP ${res.status}`);
        }

        const created = await res.json();
        const modalEl = document.getElementById('registerRestaurantModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
        document.getElementById('registerRestaurantForm').reset();

        await loadRestaurants();
        document.getElementById('restaurantSelect').value = created.id;
        onRestaurantChanged();
    } catch (err) {
        errAlert.innerText = `Registration failed: ${err.message}`;
        errAlert.classList.remove('d-none');
    }
}

// --- 7. Availability & Waiting List Oversight ---
async function handleCheckAvailability(e) {
    e.preventDefault();
    if (!currentRestaurantId || currentRestaurantId === 'ALL') {
        alert('Please select a specific restaurant to check table availability.');
        return;
    }

    const container = document.getElementById('availResultContainer');
    container.innerHTML = '<div class="text-center py-2"><span class="spinner-border spinner-border-sm"></span> Checking availability...</div>';

    const date = document.getElementById('availDate').value;
    const time = document.getElementById('availTime').value + ':00';
    const party = document.getElementById('availParty').value;

    try {
        const res = await authFetch(`/api/v1/availability?restaurantId=${currentRestaurantId}&date=${date}&time=${time}&partySize=${party}`);
        const data = await res.json();

        if (res.ok && data.isAvailable) {
            const slots = data.availableSlots && data.availableSlots.length > 0 ? data.availableSlots.join(', ') : 'Requested slot';
            container.innerHTML = `
                <div class="alert alert-success py-2 small mb-0">
                    <i class="bi bi-check-circle-fill me-1"></i> <strong>Available!</strong> Candidate slots: ${slots}
                </div>
            `;
        } else if (res.ok) {
            container.innerHTML = `
                <div class="alert alert-warning py-2 small mb-0">
                    <i class="bi bi-x-circle me-1"></i> <strong>No tables available</strong> for ${party} guests at ${time}.
                </div>
            `;
        } else {
            container.innerHTML = `
                <div class="alert alert-danger py-2 small mb-0">
                    <i class="bi bi-exclamation-triangle me-1"></i> ${data.detail || data.title || 'Check failed'}
                </div>
            `;
        }
    } catch (err) {
        container.innerHTML = `
            <div class="alert alert-danger py-2 small mb-0">
                <i class="bi bi-exclamation-triangle me-1"></i> Error: ${err.message}
            </div>
        `;
    }
}

async function loadWaitingList() {
    if (!currentRestaurantId || currentRestaurantId === 'ALL') return;

    const tbody = document.getElementById('waitingListTableBody');
    tbody.innerHTML = '<tr><td colspan="6" class="text-center py-3"><span class="spinner-border spinner-border-sm"></span> Loading queue...</td></tr>';

    try {
        const res = await authFetch(`/api/v1/waiting-list?restaurantId=${currentRestaurantId}`);
        if (!res.ok) {
            throw new Error(`HTTP ${res.status}`);
        }
        const entries = await res.json();

        if (entries.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">No guests currently waiting in queue.</td></tr>';
            return;
        }

        tbody.innerHTML = entries.map(e => {
            const queuedAt = new Date(e.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
            return `
                <tr>
                    <td><strong>${escapeHtml(e.customerEmail)}</strong></td>
                    <td>${e.targetDate}</td>
                    <td><small>${e.earliestTime} - ${e.latestTime}</small></td>
                    <td><span class="badge bg-light text-dark border">${e.partySize} guests</span></td>
                    <td><small class="text-muted">${queuedAt}</small></td>
                    <td><span class="badge bg-warning text-dark status-badge">${escapeHtml(e.status)}</span></td>
                </tr>
            `;
        }).join('');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-3">Waiting list endpoint: ${err.message}</td></tr>`;
    }
}

// --- Utility Helpers ---
function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/[&<>'"]/g, tag => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        "'": '&#39;',
        '"': '&quot;'
    }[tag] || tag));
}

// --- Event Listeners Setup ---
document.addEventListener('DOMContentLoaded', () => {
    // Auth triggers
    document.getElementById('loginBtn')?.addEventListener('click', () => {
        if (keycloak) keycloak.login();
    });
    document.getElementById('signOutBtn')?.addEventListener('click', () => {
        if (keycloak) keycloak.logout();
    });
    document.getElementById('unauthorizedSignOutBtn')?.addEventListener('click', () => {
        if (keycloak) keycloak.logout();
    });

    // Restaurant dropdown change
    document.getElementById('restaurantSelect')?.addEventListener('change', onRestaurantChanged);

    // Reservation handlers
    document.getElementById('confirmCancelReservationBtn')?.addEventListener('click', confirmCancelReservation);
    document.getElementById('newReservationForm')?.addEventListener('submit', handleCreateReservation);

    // Floor & Table handlers
    document.getElementById('addTableForm')?.addEventListener('submit', handleAddTable);
    document.getElementById('editTableForm')?.addEventListener('submit', handleEditTable);
    document.getElementById('addCombinationForm')?.addEventListener('submit', handleAddCombination);
    document.getElementById('editCombinationForm')?.addEventListener('submit', handleEditCombination);
    document.getElementById('editHoursForm')?.addEventListener('submit', handleSaveOperatingHours);
    document.getElementById('registerRestaurantForm')?.addEventListener('submit', handleRegisterRestaurant);
    document.getElementById('editRestaurantSettingsForm')?.addEventListener('submit', handleSaveSettings);

    // Floor & Tables tab switch
    document.getElementById('tab-floor')?.addEventListener('shown.bs.tab', () => {
        if (currentRestaurantId && currentRestaurantId !== 'ALL') {
            loadTablesAndHours(currentRestaurantId);
        }
    });

    // Availability form
    document.getElementById('managerAvailabilityForm')?.addEventListener('submit', handleCheckAvailability);

    // Default dates
    const today = new Date().toISOString().split('T')[0];
    const availDate = document.getElementById('availDate');
    if (availDate) availDate.value = today;
    const availTime = document.getElementById('availTime');
    if (availTime) availTime.value = '19:00';

    initHoursEditor();

    // Start Keycloak OIDC flow
    initKeycloak();

    // Setup periodic polling for analytics (every 10s)
    analyticsIntervalId = setInterval(() => {
        if (keycloak && keycloak.authenticated && managerDashboardView.style.display !== 'none') {
            loadAnalytics();
        }
    }, 10000);
});
