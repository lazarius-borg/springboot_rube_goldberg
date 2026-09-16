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
        document.getElementById('tablesTableBody').innerHTML = '<tr><td colspan="4" class="text-center py-3 text-muted">Select an individual restaurant to view floor tables.</td></tr>';
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
        const tablesStr = (r.allocatedTables && r.allocatedTables.length > 0) ? `${r.allocatedTables.length} table(s)` : '-';

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
                <td><small>${tablesStr}</small></td>
                <td>${statusBadge}</td>
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
    const comboContainer = document.getElementById('combinationTablesChecklist');

    try {
        // 1. Fetch Restaurant Details
        const rRes = await authFetch(`/api/v1/restaurants/${restaurantId}`);
        if (rRes.ok) {
            const r = await rRes.json();
            profileDiv.innerHTML = `
                <div class="mb-2"><strong>Name:</strong> ${escapeHtml(r.name)}</div>
                <div class="mb-2"><strong>Address:</strong> ${escapeHtml(r.address || 'N/A')}</div>
                <div class="mb-2"><strong>Timezone:</strong> <code>${escapeHtml(r.timezone || 'Europe/Amsterdam')}</code></div>
                <div class="mb-2"><strong>Default Dining Duration:</strong> ${r.defaultReservationDurationMinutes} mins</div>
                <div class="mb-2"><strong>Min Lead Time:</strong> ${r.minBookingAdvanceMinutes} mins</div>
                <div class="mb-2"><strong>Max Advance Horizon:</strong> ${r.maxBookingHorizonDays} days</div>
                <div><strong>Cancellation Window:</strong> ${r.cancellationWindowHours} hours</div>
            `;
        }

        // 2. Fetch Tables
        const tRes = await authFetch(`/api/v1/restaurants/${restaurantId}/tables`);
        if (tRes.ok) {
            const tables = await tRes.json();
            if (tables.length === 0) {
                tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">No tables registered yet.</td></tr>';
                comboContainer.innerHTML = '<span class="text-muted small">No tables available to combine.</span>';
            } else {
                tbody.innerHTML = tables.map(t => `
                    <tr>
                        <td><strong>${escapeHtml(t.tableNumber)}</strong></td>
                        <td><span class="badge bg-light text-dark border">${t.capacity} seats</span></td>
                        <td>${escapeHtml(t.zone || 'Main Dining')}</td>
                        <td><small class="text-muted font-monospace">${t.id}</small></td>
                    </tr>
                `).join('');

                comboContainer.innerHTML = tables.map(t => `
                    <div class="form-check">
                        <input class="form-check-input combo-table-check" type="checkbox" value="${t.id}" id="chk_${t.id}">
                        <label class="form-check-label small" for="chk_${t.id}">
                            Table ${escapeHtml(t.tableNumber)} (${t.capacity} seats, ${escapeHtml(t.zone || 'Main')})
                        </label>
                    </div>
                `).join('');
            }
        }
    } catch (err) {
        console.error('Failed to load floor tables:', err);
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

    const checkedBoxes = document.querySelectorAll('.combo-table-check:checked');
    const tableIds = Array.from(checkedBoxes).map(cb => cb.value);
    const capacity = parseInt(document.getElementById('newCombCapacity').value);

    if (tableIds.length < 2) {
        errAlert.innerText = 'Please select at least 2 tables to create a combination.';
        errAlert.classList.remove('d-none');
        return;
    }

    try {
        const res = await authFetch(`/api/v1/restaurants/${currentRestaurantId}/table-combinations`, {
            method: 'POST',
            body: JSON.stringify({
                tableIds: tableIds,
                combinedCapacity: capacity
            })
        });

        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            throw new Error(errData.detail || errData.title || `HTTP ${res.status}`);
        }

        const modalEl = document.getElementById('addCombinationModal');
        const modal = bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
        document.getElementById('addCombinationForm').reset();

        alert('Table combination created successfully.');
    } catch (err) {
        errAlert.innerText = `Failed to create combination: ${err.message}`;
        errAlert.classList.remove('d-none');
    }
}

function initHoursEditor() {
    const tbody = document.getElementById('hoursEditorTableBody');
    const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
    tbody.innerHTML = days.map((day, idx) => `
        <tr>
            <td><strong>${day}</strong></td>
            <td><input type="time" class="form-control form-control-sm hours-open" data-day="${idx + 1}" value="11:00"></td>
            <td><input type="time" class="form-control form-control-sm hours-close" data-day="${idx + 1}" value="23:00"></td>
        </tr>
    `).join('');
}

async function handleSaveOperatingHours(e) {
    e.preventDefault();
    if (!currentRestaurantId || currentRestaurantId === 'ALL') return;

    const errAlert = document.getElementById('editHoursErrorAlert');
    errAlert.classList.add('d-none');

    const openInputs = document.querySelectorAll('.hours-open');
    const closeInputs = document.querySelectorAll('.hours-close');

    const schedule = [];
    openInputs.forEach((inp, idx) => {
        const day = parseInt(inp.getAttribute('data-day'));
        schedule.push({
            dayOfWeek: day,
            openTime: inp.value + ':00',
            closeTime: closeInputs[idx].value + ':00'
        });
    });

    try {
        const res = await authFetch(`/api/v1/restaurants/${currentRestaurantId}/opening-hours`, {
            method: 'PUT',
            body: JSON.stringify(schedule)
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
    const duration = parseInt(document.getElementById('regDuration').value);
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
                defaultReservationDurationMinutes: duration,
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
    document.getElementById('addCombinationForm')?.addEventListener('submit', handleAddCombination);
    document.getElementById('editHoursForm')?.addEventListener('submit', handleSaveOperatingHours);
    document.getElementById('registerRestaurantForm')?.addEventListener('submit', handleRegisterRestaurant);

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
